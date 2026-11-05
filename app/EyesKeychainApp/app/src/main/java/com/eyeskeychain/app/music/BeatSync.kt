package com.eyeskeychain.app.music

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.eyeskeychain.app.ble.Ble
import com.eyeskeychain.app.ble.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Rock modunda kafa sallamanın temposu ve vuruş anı.
 *  - BPM: Deezer'dan otomatik (ücretsiz, anahtarsız). Bulunamazsa 120 varsayılır.
 *  - Faz (vuruşun ne zaman geldiği): kullanıcı "Ritme dokun" ile ayarlar; şarkı başına saklanır.
 */
object BeatSync {
    private const val LATENCY_MS = 60          // Bluetooth gecikmesi telafisi

    private val _bpm = MutableStateFlow(0f)
    val bpm: StateFlow<Float> = _bpm
    private val _source = MutableStateFlow("")
    val source: StateFlow<String> = _source
    private val _tapCount = MutableStateFlow(0)
    val tapCount: StateFlow<Int> = _tapCount

    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var trackKey: String? = null
    private var offsetMs: Long? = null          // şarkı içinde bir vuruşun konumu
    private val taps = ArrayList<Long>()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("beats", Context.MODE_PRIVATE)
    }

    private fun keyOf(t: MusicTracker.Track) = "${t.artist}|${t.title}".lowercase()

    /** Yeni şarkı: önce kayıtlı ayara bak, yoksa Deezer'dan BPM çek. */
    fun onTrack(t: MusicTracker.Track, onReady: () -> Unit) {
        val key = keyOf(t)
        trackKey = key
        taps.clear(); _tapCount.value = 0
        val saved = if (::prefs.isInitialized) prefs.getString(key, null) else null
        if (saved != null) {
            val p = saved.split(";")
            _bpm.value = p.getOrNull(0)?.toFloatOrNull() ?: 0f
            offsetMs = p.getOrNull(1)?.toLongOrNull()
            _source.value = "kayıtlı ayar"
            onReady()
            return
        }
        _bpm.value = 0f; offsetMs = null; _source.value = "BPM aranıyor..."
        io.execute {
            val found = try { deezerBpm(t.title, t.artist) } catch (_: Exception) { null }
            main.post {
                if (trackKey != key) return@post
                if (found != null && found > 0f) {
                    _bpm.value = found
                    _source.value = "Deezer"
                } else {
                    _bpm.value = 0f
                    _source.value = "bulunamadı, 120 varsayılıyor (dokunarak ayarla)"
                }
                onReady()
            }
        }
    }

    /** "Ritme dokun": son dokunuşlardan tempo ve vuruş anını hesaplar. positionMs = o anki şarkı konumu (yoksa null). */
    fun tap(positionMs: Long?) {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.add(now)
        if (taps.size > 9) taps.removeAt(0)
        _tapCount.value = taps.size
        if (taps.size < 4) return
        val intervals = (1 until taps.size).map { taps[it] - taps[it - 1] }.sorted()
        var period = intervals[intervals.size / 2].toFloat()        // ortanca: tek kaçan dokunuş bozmasın
        var bpm = 60000f / period
        while (bpm > 180f) bpm /= 2f
        while (bpm < 70f) bpm *= 2f
        period = 60000f / bpm
        _bpm.value = bpm
        _source.value = "dokunarak ayarlandı"
        if (positionMs != null) {
            offsetMs = Math.floorMod(positionMs, period.toLong().coerceAtLeast(1))
            trackKey?.let { prefs.edit().putString(it, "$bpm;$offsetMs").apply() }
            sync(positionMs)
        } else {
            // çalan şarkı yok: sadece son dokunuşu vuruş say
            val toNext = (period - (SystemClock.elapsedRealtime() - now) - LATENCY_MS).toInt()
            Ble.send(Protocol.beat(bpm, toNext))
        }
    }

    fun reset() {
        trackKey?.let { prefs.edit().remove(it).apply() }
        taps.clear(); _tapCount.value = 0
        offsetMs = null
    }

    /** Anahtarlığa tempo + bir sonraki vuruşa kalan süreyi gönder. */
    fun sync(positionMs: Long) {
        val bpm = _bpm.value
        if (bpm <= 0f) { Ble.send(Protocol.beat(0f, 0)); return }
        val period = 60000.0 / bpm
        val off = offsetMs ?: 0L
        val phase = ((positionMs - off) % period + period) % period
        val toNext = (period - phase - LATENCY_MS).toInt()
        Ble.send(Protocol.beat(bpm, toNext))
    }

    // ---------------- Deezer ----------------
    private fun clean(s: String): String =
        s.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "")       // (feat. ...), [Remastered]
            .replace(Regex("""\s+-\s+.*$"""), "")             // " - Remastered 2011"
            .trim()

    private fun deezerBpm(title: String, artist: String): Float? {
        val q = "artist:\"${clean(artist)}\" track:\"${clean(title)}\""
        val search = httpGet("https://api.deezer.com/search?limit=3&q=" + URLEncoder.encode(q, "UTF-8")) ?: return null
        val data = JSONObject(search).optJSONArray("data") ?: return null
        for (i in 0 until data.length()) {
            val id = data.getJSONObject(i).optLong("id", 0)
            if (id == 0L) continue
            val tr = httpGet("https://api.deezer.com/track/$id") ?: continue
            val bpm = JSONObject(tr).optDouble("bpm", 0.0)
            if (bpm > 0) return bpm.toFloat()
        }
        return null
    }

    private fun httpGet(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "GozKeychain/0.5")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
