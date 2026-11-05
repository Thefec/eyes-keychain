package com.eyeskeychain.app.music

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Zamanlı söz satırı: timeMs anında ekrana gelir. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * LRCLIB (https://lrclib.net) ücretsiz, anahtarsız şarkı sözü veritabanı.
 * Önce tam eşleşme (/api/get), bulunamazsa arama (/api/search).
 * Ağ çağrısı yapar: ana iş parçacığında çağırma.
 */
object Lyrics {
    private const val UA = "GozKeychain/0.3 (hobby app; lrclib client)"
    private val cache = HashMap<String, List<LyricLine>?>()

    fun fetch(title: String, artist: String, album: String, durationMs: Long): List<LyricLine>? {
        val key = "$artist|$title|${durationMs / 1000}"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val result = try { fetchInternal(title, artist, album, durationMs) } catch (_: Exception) { null }
        synchronized(cache) { cache[key] = result }
        return result
    }

    private fun fetchInternal(title: String, artist: String, album: String, durationMs: Long): List<LyricLine>? {
        val sec = durationMs / 1000
        val q = StringBuilder("https://lrclib.net/api/get?track_name=").append(enc(title)).append("&artist_name=").append(enc(artist))
        if (album.isNotBlank()) q.append("&album_name=").append(enc(album))
        if (sec > 0) q.append("&duration=").append(sec)
        httpGet(q.toString())?.let { body ->
            val synced = JSONObject(body).optString("syncedLyrics", "")
            if (synced.isNotBlank()) return parseLrc(synced)
        }
        Thread.sleep(300)    // LRCLIB istekler arası kısa bekleme istiyor
        val body = httpGet("https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}") ?: return null
        val arr = JSONArray(body)
        var best: String? = null
        var bestDiff = Long.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val synced = o.optString("syncedLyrics", "")
            if (synced.isBlank() || synced == "null") continue
            val d = (o.optDouble("duration", 0.0)).toLong()
            val diff = if (sec > 0 && d > 0) abs(d - sec) else 0L
            if (diff < bestDiff) { bestDiff = diff; best = synced }
        }
        return if (best != null && bestDiff <= 5) parseLrc(best) else null
    }

    private val TIME = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

    fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in lrc.lines()) {
            val stamps = TIME.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val min = m.groupValues[1].toLong()
                val s = m.groupValues[2].toDouble()
                out.add(LyricLine(min * 60_000 + (s * 1000).toLong(), text))
            }
        }
        out.sortBy { it.timeMs }
        return out
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
