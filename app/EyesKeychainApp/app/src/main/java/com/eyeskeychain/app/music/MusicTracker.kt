package com.eyeskeychain.app.music

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.eyeskeychain.app.AppSettings
import com.eyeskeychain.app.DisplayText
import com.eyeskeychain.app.ble.Ble
import com.eyeskeychain.app.ble.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

/**
 * Telefonda çalan müziği (Spotify, YouTube Music...) izler ve anahtarlığa gönderir:
 * şarkı adı, çalıyor/durdu (otomatik Rock modu) ve zamanlı söz satırları.
 * Bildirim erişimi açık olmalı (MusicListenerService).
 */
object MusicTracker {
    data class Track(val title: String, val artist: String, val album: String, val durationMs: Long) {
        val display: String get() = if (artist.isBlank()) title else "$artist - $title"
    }

    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing
    private val _lyricsInfo = MutableStateFlow("")
    val lyricsInfo: StateFlow<String> = _lyricsInfo
    private val _currentLine = MutableStateFlow("")
    val currentLine: StateFlow<String> = _currentLine
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var msm: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null
    private var state: PlaybackState? = null
    private var lyrics: List<LyricLine> = emptyList()
    private var lastSentText: String? = null
    private var lastBleState = Ble.State.DISCONNECTED
    private var lastPlayingSent: Boolean? = null
    private var beatTick = 0

    fun start(context: Context) {
        if (_running.value) return
        CoverArt.init(context)
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        val comp = ComponentName(context, MusicListenerService::class.java)
        msm = m
        component = comp
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, comp, main)
            pickController(m.getActiveSessions(comp))
        } catch (_: SecurityException) {
            return   // bildirim erişimi yok
        }
        _running.value = true
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    fun stop() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) { }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        main.removeCallbacks(ticker)
        _running.value = false
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list) }

    private fun pickController(list: List<MediaController>?) {
        val l = list ?: emptyList()
        val chosen = l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: l.firstOrNull()
        if (chosen?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(controllerCallback)
        controller = chosen
        if (chosen == null) {
            updateState(null)
            return
        }
        chosen.registerCallback(controllerCallback, main)
        onMetadata(chosen.metadata)
        updateState(chosen.playbackState)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = onMetadata(metadata)
        override fun onPlaybackStateChanged(s: PlaybackState?) = updateState(s)
        override fun onSessionDestroyed() {
            val m = msm; val c = component
            if (m != null && c != null) {
                try { pickController(m.getActiveSessions(c)) } catch (_: SecurityException) { }
            }
        }
    }

    private fun onMetadata(md: MediaMetadata?) {
        if (md == null) return
        CoverArt.onMetadata(md)                       // kapak her güncellemede kontrol edilir (bazen sonradan gelir)
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        if (title.isEmpty()) return
        val artist = (md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty().trim()
        val album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty().trim()
        val dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val t = Track(title, artist, album, dur)
        val old = _track.value
        if (old != null && old.title == t.title && old.artist == t.artist) return
        _track.value = t
        lyrics = emptyList()
        BeatSync.onTrack(t) { if (_playing.value) BeatSync.sync(positionMs()) }
        _currentLine.value = ""
        if (AppSettings.showLyrics.value) {
            _lyricsInfo.value = "Söz aranıyor..."
            io.execute {
                val res = Lyrics.fetch(t.title, t.artist, t.album, t.durationMs)
                main.post {
                    if (_track.value != t) return@post
                    lyrics = res ?: emptyList()
                    _lyricsInfo.value = if (res.isNullOrEmpty()) "Zamanlı söz bulunamadı, şarkı adı gösteriliyor" else "Söz bulundu (${res.size} satır)"
                            }
            }
        } else {
            _lyricsInfo.value = ""
        }
        refreshDisplay()
    }

    private fun updateState(s: PlaybackState?) {
        state = s
        val p = s?.state == PlaybackState.STATE_PLAYING
        _playing.value = p
        if (lastPlayingSent != p) {
            lastPlayingSent = p
            Ble.send(Protocol.music(p, AppSettings.autoRock.value))
            if (p) BeatSync.sync(positionMs())
            CoverArt.onPlaying(p)
        }
        sendProgress()
        refreshDisplay()
    }

    private fun sendProgress() {
        val t = _track.value ?: return
        CoverArt.progress(positionMs(), t.durationMs, _playing.value)
    }

    /** Çalan şarkı varsa o anki konumu (ritme dokun için). */
    fun positionOrNull(): Long? = if (_playing.value && _track.value != null) positionMs() else null

    /** Çalma konumu: son bildirilen konum + geçen süre x hız. */
    private fun positionMs(): Long {
        val s = state ?: return 0
        if (s.state != PlaybackState.STATE_PLAYING) return s.position
        val elapsed = SystemClock.elapsedRealtime() - s.lastPositionUpdateTime
        return s.position + (elapsed * s.playbackSpeed).toLong()
    }

    /** Ekrana ne gideceğine karar verir; sadece değiştiyse gönderir. */
    private fun refreshDisplay() {
        val t = _track.value
        val text: String? = when {
            t == null || !_playing.value -> null
            AppSettings.showLyrics.value && lyrics.isNotEmpty() -> {
                val pos = positionMs() + 250           // küçük öne alma: satır sözle birlikte gelsin
                var idx = -1
                for (i in lyrics.indices) { if (lyrics[i].timeMs <= pos) idx = i else break }
                val line = if (idx < 0) "" else lyrics[idx].text
                _currentLine.value = line
                when {
                    idx < 0 -> if (AppSettings.showTitle.value) t.display else "♪"
                    line.isBlank() -> "♪"
                    else -> line
                }
            }
            AppSettings.showTitle.value -> t.display
            else -> null
        }
        // Yazı tipinde olmayan alfabeler: Latin harfe çevir; kapalıysa şarkı adı ya da ♪
        val shown: String? = text?.let { raw ->
            DisplayText.prepare(raw)
                ?: t?.let { DisplayText.prepare(it.display) }.takeIf { AppSettings.showTitle.value }
                ?: "♪"
        }
        if (shown == lastSentText) return
        lastSentText = shown
        if (shown == null) Ble.send(Protocol.clearText()) else Ble.send(Protocol.text(shown))
    }

    /** Ayar değişince ya da anahtarlık yeniden bağlanınca her şeyi tekrar gönder. */
    fun resendAll() {
        lastSentText = "\u0000"          // hiçbir metne eşit olmasın: tekrar gönderilir
        lastPlayingSent = null
        CoverArt.resend()
        updateState(state)
    }

    private val ticker = object : Runnable {
        override fun run() {
            val bs = Ble.state.value
            if (bs == Ble.State.CONNECTED && lastBleState != Ble.State.CONNECTED) {
                main.postDelayed({ resendAll() }, 600)
            }
            lastBleState = bs
            if (_playing.value && lyrics.isNotEmpty()) refreshDisplay()
            if (_playing.value && ++beatTick >= 25) { beatTick = 0; BeatSync.sync(positionMs()); sendProgress() }   // ~5 sn
            main.postDelayed(this, 200)
        }
    }
}
