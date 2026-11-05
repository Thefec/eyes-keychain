package com.eyeskeychain.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.eyeskeychain.app.ble.Ble
import com.eyeskeychain.app.ble.Protocol

/**
 * Anahtarlığın ekran ışığı: parlaklık, otomatik kararma, gece uykusu.
 * Ayarlar telefonda saklanır ve her bağlanışta anahtarlığa tekrar gönderilir.
 */
object DisplayLight {
    /** Parlaklık ayarını bilen ilk anahtarlık yazılımı. */
    const val MIN_FW = 12

    /** Saat aç/kapa ve yeni göz hareketlerini bilen ilk anahtarlık yazılımı. */
    const val MOVES_FW = 14

    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var lastSend = 0L
    private var pending = false

    fun start() {
        if (started) return
        started = true
        Ble.addReadyListener { sendAll() }
    }

    fun sendAll() {
        Ble.send(Protocol.brightness(AppSettings.brightness.value))
        sendSettings()
        sendPersonality()
    }

    /** Saat modu aç/kapa + hareket canlılığı (yazılım v14 ve üstü). */
    fun sendPersonality() {
        Ble.send(Protocol.clockLively(AppSettings.clockOn.value, AppSettings.lively.value))
    }

    fun sendSettings() {
        Ble.send(
            Protocol.display(
                AppSettings.dimAfter.value, AppSettings.dimPct.value, AppSettings.nightOn.value,
                AppSettings.nightFrom.value, AppSettings.nightTo.value, AppSettings.nightLevel.value,
            )
        )
    }

    /** Kaydırıcıdan: saniyede en fazla ~15 kez gönder, en son değer mutlaka gitsin. */
    fun setBrightness(v: Int) {
        AppSettings.setBrightness(v)
        val now = SystemClock.elapsedRealtime()
        val wait = 60 - (now - lastSend)
        if (wait <= 0) {
            lastSend = now
            Ble.send(Protocol.brightness(v))
        } else if (!pending) {
            pending = true
            main.postDelayed({
                pending = false
                lastSend = SystemClock.elapsedRealtime()
                Ble.send(Protocol.brightness(AppSettings.brightness.value))
            }, wait)
        }
    }
}
