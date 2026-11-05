package com.eyeskeychain.app.ble

import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

/** Anahtarlık yazılımıyla aynı komut protokolü (eyes_keychain.ino -> handleCmd).
 *  0x30-0x39 kullanılmaz: anahtarlık bunları "0"-"9" metin mod komutu sanar. */
object Protocol {
    val SERVICE: UUID = UUID.fromString("7a1e0001-5d3c-4b8e-9a53-2f6c9d1e0001")
    val CMD: UUID = UUID.fromString("7a1e0002-5d3c-4b8e-9a53-2f6c9d1e0001")
    val STATUS: UUID = UUID.fromString("7a1e0003-5d3c-4b8e-9a53-2f6c9d1e0001")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private const val MAX_TEXT_BYTES = 170

    fun mode(m: Int) = byteArrayOf(0x01, m.toByte())
    fun rotation(r: Int) = byteArrayOf(0x02, (r and 3).toByte())
    fun text(s: String) = byteArrayOf(0x03) + utf8Trimmed(s)
    fun clearText() = byteArrayOf(0x04)
    fun emotion(e: Int, seconds: Int = 3) = byteArrayOf(0x06, e.toByte(), seconds.coerceIn(1, 255).toByte())
    fun notification(s: String) = byteArrayOf(0x07) + utf8Trimmed(s)
    fun music(playing: Boolean, autoRock: Boolean) =
        byteArrayOf(0x09, if (playing) 1 else 0, if (autoRock) 1 else 0)

    /** Parlaklık 4-255 (anahtarlık yumuşakça kayar). */
    fun brightness(b: Int) = byteArrayOf(0x08, b.coerceIn(4, 255).toByte())

    /** Ekran ışığı ayarları (0x0E): kararma süresi (sn, 0 = hiç), kararınca yüzde, gece uykusu, saat aralığı, gece ışığı 0-255. */
    fun display(dimAfterSec: Int, dimPct: Int, nightOn: Boolean, nightFrom: Int, nightTo: Int, nightLevel: Int): ByteArray {
        val d = dimAfterSec.coerceIn(0, 65535)
        return byteArrayOf(
            0x0E, d.toByte(), (d shr 8).toByte(), dimPct.coerceIn(0, 100).toByte(), if (nightOn) 1 else 0,
            nightFrom.mod(24).toByte(), nightTo.mod(24).toByte(), nightLevel.coerceIn(0, 255).toByte(),
        )
    }

    /** Saat modu açık mı + göz hareketlerinin canlılığı 0 sakin .. 3 çok hareketli (0x47). */
    fun clockLively(clockOn: Boolean, lively: Int) = byteArrayOf(0x47, if (clockOn) 1 else 0, lively.coerceIn(0, 3).toByte())

    /** Bir göz hareketini hemen oynat (0x48): 1-15, sıra EYE_MOVES ile aynı. */
    fun playMove(id: Int) = byteArrayOf(0x48, id.coerceIn(1, 15).toByte())

    fun pomodoro(workMin: Int, breakMin: Int) =
        byteArrayOf(0x0A, workMin.coerceIn(1, 120).toByte(), breakMin.coerceIn(1, 60).toByte())
    fun stopPomodoro() = byteArrayOf(0x0A, 0)

    /** Saat senkronu: epoch saniye + saat dilimi (dakika). */
    fun time(epochSec: Long, tzMinutes: Int): ByteArray {
        val e = epochSec.toInt()
        return byteArrayOf(0x0B, e.toByte(), (e shr 8).toByte(), (e shr 16).toByte(), (e shr 24).toByte(),
            tzMinutes.toByte(), (tzMinutes shr 8).toByte())
    }

    /** Hava: sıcaklık (°C) + kod (0 açık, 1 bulutlu, 2 yağmur, 3 kar, 4 fırtına, 5 sis). */
    fun weather(tempC: Int, code: Int) = byteArrayOf(0x0C, tempC.coerceIn(-60, 60).toByte(), code.toByte())

    /** bpm = 0 -> anahtarlık 120 BPM serbest çalışır. msToNextBeat: bir sonraki vuruşa kalan süre. */
    fun beat(bpm: Float, msToNextBeat: Int): ByteArray {
        val b = (bpm * 10).roundToInt().coerceIn(0, 65535)
        val o = msToNextBeat.coerceIn(-32768, 32767)
        return byteArrayOf(0x05, (b and 0xFF).toByte(), (b shr 8).toByte(), (o and 0xFF).toByte(), (o shr 8).toByte())
    }

    /** Özel göz (0x0D): w h r gap tired angry sad happy bottom R G B (12 bayt). */
    fun custom(p: List<Int>): ByteArray {
        require(p.size == 12)
        return byteArrayOf(0x0D) + ByteArray(12) { p[it].coerceIn(0, 255).toByte() }
    }

    // ---- Albüm kapağı ----
    const val COVER_CHUNK = 180
    fun coverBegin(size: Int) = byteArrayOf(0x40, size.toByte(), (size shr 8).toByte())
    fun coverData(buf: ByteArray, from: Int, len: Int) = byteArrayOf(0x41) + buf.copyOfRange(from, from + len)
    fun coverEnd() = byteArrayOf(0x42)
    /** 0 gizle, 1 süresiz göster, 2-255 o kadar saniye göster. */
    fun coverShow(v: Int) = byteArrayOf(0x43, v.coerceIn(0, 255).toByte())
    fun coverInfo(title: String, artist: String): ByteArray {
        val t = utf8Trimmed(title, 110)
        val a = utf8Trimmed(artist, 60)
        return byteArrayOf(0x44) + t + byteArrayOf(0x1F) + a
    }
    /** Kapak düzeni: tam ekran (240x240, durum çubuğu ve yazılar gizli) ya da normal (150x150). */
    fun coverLayout(full: Boolean) = byteArrayOf(0x46, if (full) 1 else 0)
    fun progress(posSec: Int, durSec: Int, playing: Boolean): ByteArray {
        val p = posSec.coerceIn(0, 65535); val d = durSec.coerceIn(0, 65535)
        return byteArrayOf(0x45, p.toByte(), (p shr 8).toByte(), d.toByte(), (d shr 8).toByte(), if (playing) 1 else 0)
    }

    // ---- Kablosuz güncelleme ----
    const val OTA_CHUNK = 180
    fun otaBegin(size: Int) = byteArrayOf(0x20, size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte())
    fun otaData(buf: ByteArray, from: Int, len: Int) = byteArrayOf(0x21) + buf.copyOfRange(from, from + len)
    fun otaEnd() = byteArrayOf(0x22)
    fun otaAbort() = byteArrayOf(0x23)

    /** UTF-8'e çevirir, sınırı aşarsa harfi ortadan bölmeden keser. */
    private fun utf8Trimmed(s: String, max: Int = MAX_TEXT_BYTES): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        if (b.size <= max) return b
        var n = min(b.size, max)
        while (n > 0 && (b[n].toInt() and 0xC0) == 0x80) n--
        return b.copyOf(n)
    }
}

data class DeviceStatus(
    val mode: Int,
    val rotation: Int,
    val battery: Int,
    val usb: Boolean,
    val playing: Boolean,
    /** Anahtarlık yazılım sürümü (eski yazılımda 0). */
    val fwVersion: Int = 0,
    /** Güncelleme durumu: 0 boşta, 1 yazılıyor, 2 hata. */
    val otaState: Int = 0,
    /** Anahtarlığın parlaklığı 0-255 (v12 öncesi yazılımda -1). */
    val brightness: Int = -1,
    /** Ekran şu an kendiliğinden kararmış mı. */
    val dimmed: Boolean = false,
    /** Gece uykusunda mı. */
    val asleep: Boolean = false,
)
