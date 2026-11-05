package com.eyeskeychain.app.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaMetadata
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.eyeskeychain.app.AppSettings
import com.eyeskeychain.app.DisplayText
import com.eyeskeychain.app.ble.Ble
import com.eyeskeychain.app.ble.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Çalan şarkının albüm kapağını anahtarlığa gönderir.
 * Normal: 150x150, ~10 KB JPEG. Tam ekran (mod 3): 240x240, ~20 KB JPEG. Anahtarlık JPEG'i kendisi açar.
 */
object CoverArt {
    private const val SRC = 240                   // kaynak kare (her iki boyut bundan üretilir)
    /** Kapak modu: 0 kapalı, 1 şarkı değişince, 2 çalarken hep, 3 tam ekran (çalarken hep, her şey gizli). */
    const val MODE_FULL = 3

    private val full get() = AppSettings.coverMode.value == MODE_FULL
    private val size get() = if (full) 240 else 150
    private val maxBytes get() = if (full) 22_000 else 12_000

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview

    private var app: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var trackKey = ""
    private var title = ""
    private var artist = ""
    private var artSig = ""
    private var sentHash = 0                      // gönderilen kapağın piksel özeti (aynısını tekrar yollama)
    private var jpeg: ByteArray? = null
    private var jpegSize = 0                      // gönderilen kapağın kenar boyu (150 / 240)
    private var square: Bitmap? = null            // şarkının kare kapağı (240x240), boyut değişince buradan yeniden üretilir
    private var playing = false
    private var shownForKey = ""
    private var pendingShow = false

    fun init(ctx: Context) { app = ctx.applicationContext }

    private val mode get() = AppSettings.coverMode.value

    fun onMetadata(md: MediaMetadata) {
        val t = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        if (t.isEmpty()) return
        val a = (md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty().trim()
        val key = "$t|$a"
        if (key != trackKey) {
            trackKey = key; title = t; artist = a
            jpeg = null; jpegSize = 0; square = null; artSig = ""; sentHash = 0; _preview.value = null
            sendInfo()
            // yeni kapak 2 sn içinde gelmezse eskisini sil (yanlış kapak kalmasın)
            main.removeCallbacks(clearOld); main.postDelayed(clearOld, 2000)
            if (mode == 1 && playing) scheduleBriefShow()
        }
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp != null) {
            val sig = "$key|${bmp.generationId}|${bmp.width}x${bmp.height}"
            if (sig == artSig) return
            artSig = sig
            val sq = runCatching { cropScale(bmp, SRC) }.getOrNull() ?: return
            square = sq
            val small = fit(sq)
            if (pixelHash(small) == sentHash) return     // Spotify aynı kapağı tekrar bildirdi
            encodeAsync(small, key)
        } else if (artSig.isEmpty()) {
            val uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                ?: return
            artSig = "$key|$uri"
            io.execute {
                val b = runCatching { loadUri(uri) }.getOrNull()?.let { runCatching { cropScale(it, SRC) }.getOrNull() }
                if (b != null) main.post { if (trackKey == key) { square = b; encodeAsync(fit(b), key) } }
            }
        }
    }

    private fun pixelHash(b: Bitmap): Int {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return px.contentHashCode()
    }

    /** Kare kapağı o anki boyuta (150 ya da 240) getir. */
    private fun fit(sq: Bitmap): Bitmap = if (sq.width == size) sq else Bitmap.createScaledBitmap(sq, size, size, true)

    private val clearOld = Runnable { if (jpeg == null) Ble.send(Protocol.coverBegin(0)) }

    /** Ortadan kare kes, out x out yap, şeffaflığı siyaha boya. */
    private fun cropScale(src0: Bitmap, out: Int): Bitmap {
        val src = if (src0.config == Bitmap.Config.HARDWARE) src0.copy(Bitmap.Config.ARGB_8888, false) else src0
        val side = min(src.width, src.height)
        val sx = (src.width - side) / 2
        val sy = (src.height - side) / 2
        val bmp = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.BLACK)
        c.drawBitmap(src, Rect(sx, sy, sx + side, sy + side), Rect(0, 0, out, out),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return bmp
    }

    private fun loadUri(uri: String): Bitmap? {
        if (uri.startsWith("http")) {
            val conn = URL(uri).openConnection() as HttpURLConnection
            return try {
                conn.connectTimeout = 6000; conn.readTimeout = 6000
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally { conn.disconnect() }
        }
        val ctx = app ?: return null
        return ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
    }

    private fun encodeAsync(small: Bitmap, key: String) {
        val limit = maxBytes
        io.execute {
            var q = 85
            var bytes: ByteArray
            while (true) {
                val bo = ByteArrayOutputStream()
                small.compress(Bitmap.CompressFormat.JPEG, q, bo)
                bytes = bo.toByteArray()
                if (bytes.size <= limit || q <= 35) break
                q -= 10
            }
            main.post {
                if (key != trackKey || bytes.size > limit || small.width != size) return@post   // bu arada boyut değiştiyse eskiyi gönderme
                val h = pixelHash(small)
                if (h == sentHash && jpeg != null) return@post
                sentHash = h
                jpeg = bytes
                jpegSize = small.width
                _preview.value = small
                main.removeCallbacks(clearOld)
                sendImage()
                if (pendingShow) showBrief()
            }
        }
    }

    private fun sendImage() {
        val j = jpeg ?: return
        val list = ArrayList<ByteArray>(j.size / Protocol.COVER_CHUNK + 4)
        list.add(Protocol.coverLayout(jpegSize == 240))
        list.add(Protocol.coverBegin(j.size))
        var o = 0
        while (o < j.size) {
            val n = min(Protocol.COVER_CHUNK, j.size - o)
            list.add(Protocol.coverData(j, o, n))
            o += n
        }
        list.add(Protocol.coverEnd())
        Ble.sendBulk(list)
    }

    private fun sendInfo() {
        if (title.isEmpty()) return
        val t = DisplayText.prepare(title) ?: "♪"
        val a = if (artist.isEmpty()) "" else DisplayText.prepare(artist) ?: ""
        Ble.send(Protocol.coverInfo(t, a))
    }

    fun onPlaying(p: Boolean) {
        playing = p
        when (mode) {
            2, MODE_FULL -> Ble.send(Protocol.coverShow(if (p) 1 else 0))
            1 -> if (p && trackKey.isNotEmpty() && shownForKey != trackKey) scheduleBriefShow()
        }
    }

    private val pendingRunnable = Runnable { if (pendingShow) showBrief() }

    private fun scheduleBriefShow() {
        if (jpeg != null) { showBrief(); return }
        pendingShow = true                          // kapak birazdan gelir, en fazla 1.5 sn bekle
        main.removeCallbacks(pendingRunnable)
        main.postDelayed(pendingRunnable, 1500)
    }

    private fun showBrief() {
        pendingShow = false
        main.removeCallbacks(pendingRunnable)
        shownForKey = trackKey
        Ble.send(Protocol.coverShow(AppSettings.coverSecs.value.coerceIn(3, 60)))
    }

    /** Uygulamadan "şimdi göster" düğmesi. */
    fun showNow() {
        if (mode >= 2 && playing) return
        Ble.send(Protocol.coverShow(AppSettings.coverSecs.value.coerceIn(3, 60)))
    }

    fun progress(posMs: Long, durMs: Long, isPlaying: Boolean) {
        if (mode == 0 || durMs <= 0) return
        Ble.send(Protocol.progress((posMs / 1000).toInt(), (durMs / 1000).toInt(), isPlaying))
        // "çalarken hep": ara ara hatırlat (anahtarlık yeniden başladıysa ya da komut kaçtıysa)
        if (mode >= 2 && isPlaying) Ble.send(Protocol.coverShow(1))
    }

    /** Yeniden bağlanınca ya da ayar değişince her şeyi baştan gönder. */
    fun resend() {
        sendInfo()
        if (jpeg != null) sendImage() else { Ble.send(Protocol.coverLayout(full)); Ble.send(Protocol.coverBegin(0)) }
        when (mode) {
            0 -> Ble.send(Protocol.coverShow(0))
            2, MODE_FULL -> Ble.send(Protocol.coverShow(if (playing) 1 else 0))
        }
    }

    /** Kapak modu değişti: tam ekrana geçildiyse/çıkıldıysa kapağı yeni boyutta yeniden hazırla, sonra hepsini gönder. */
    fun onModeChanged() {
        val sq = square
        if (sq != null && (jpeg == null || jpegSize != size)) {
            jpeg = null; sentHash = 0
            Ble.send(Protocol.coverLayout(full))
            encodeAsync(fit(sq), trackKey)            // bitince kendisi gönderir
            sendInfo()
            when (mode) {
                0 -> Ble.send(Protocol.coverShow(0))
                2, MODE_FULL -> Ble.send(Protocol.coverShow(if (playing) 1 else 0))
            }
        } else {
            resend()
        }
    }
}
