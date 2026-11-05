package com.eyeskeychain.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import com.eyeskeychain.app.ble.Ble
import com.eyeskeychain.app.ble.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Kablosuz yazılım güncellemesi.
 * Arduino IDE'de Sketch > Export Compiled Binary ile çıkan "eyes_keychain.ino.bin" dosyası
 * Bluetooth üzerinden parça parça anahtarlığa gönderilir; anahtarlık doğrular ve yeniden başlar.
 */
object Ota {
    enum class Phase { IDLE, READY, STARTING, SENDING, FINISHING, DONE, ERROR }

    data class Ui(
        val phase: Phase = Phase.IDLE,
        val progress: Float = 0f,
        val message: String = "",
        val fileName: String = "",
        val fileSize: Int = 0,
    )

    /** Anahtarlıkta kablosuz güncellemeyi destekleyen ilk yazılım sürümü. */
    const val MIN_FW = 7
    private const val MAX_SIZE = 0x140000          // varsayılan 4MB bölümlemede uygulama alanı (1.25 MB)
    private const val MIN_SIZE = 100 * 1024

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    private val main = Handler(Looper.getMainLooper())
    private var bin: ByteArray? = null
    private var offset = 0
    private var cancelled = false
    private var startedAt = 0L

    val busy: Boolean
        get() = _ui.value.phase in setOf(Phase.STARTING, Phase.SENDING, Phase.FINISHING)

    /** Dosyayı oku ve kontrol et. */
    fun load(ctx: Context, uri: Uri) {
        if (busy) return
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "dosya"
        val data = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        bin = null
        val err = when {
            data == null -> "Dosya okunamadı"
            name.contains("merged", true) || isMerged(data) ->
                "Bu birleşik (merged) dosya. Aynı klasördeki \"eyes_keychain.ino.bin\" dosyasını seç."
            name.contains("bootloader", true) || name.contains("partitions", true) ->
                "Bu yanlış dosya. \"eyes_keychain.ino.bin\" dosyasını seç."
            data.size < MIN_SIZE -> "Dosya çok küçük, anahtarlık yazılımı değil gibi."
            data.size > MAX_SIZE -> "Dosya çok büyük (${data.size / 1024} KB). En fazla ${MAX_SIZE / 1024} KB olabilir."
            (data[0].toInt() and 0xFF) != 0xE9 -> "Bu bir ESP32 yazılım dosyası değil (.bin olmalı)."
            chipId(data) != 5 -> "Bu dosya ESP32-C3 için derlenmemiş. Arduino'da kart olarak ESP32C3 seçili olmalı."
            else -> null
        }
        if (err != null) {
            _ui.value = Ui(Phase.ERROR, message = err, fileName = name, fileSize = data?.size ?: 0)
        } else {
            bin = data
            _ui.value = Ui(Phase.READY, message = "Hazır", fileName = name, fileSize = data!!.size)
        }
    }

    private fun isMerged(d: ByteArray) =
        d.size > 0x8002 && (d[0x8000].toInt() and 0xFF) == 0xAA && (d[0x8001].toInt() and 0xFF) == 0x50

    private fun chipId(d: ByteArray): Int =
        if (d.size < 14) -1 else (d[12].toInt() and 0xFF) or ((d[13].toInt() and 0xFF) shl 8)

    fun start() {
        val data = bin ?: return
        if (busy) return
        if (Ble.state.value != Ble.State.CONNECTED) { fail("Önce anahtarlığa bağlan."); return }
        val fw = Ble.status.value?.fwVersion ?: 0
        if (fw < MIN_FW) {
            fail("Anahtarlıktaki yazılım kablosuz güncellemeyi bilmiyor. Bu yeni yazılımı bir kez USB kablo ile yükle, sonrası kablosuz.")
            return
        }
        cancelled = false
        offset = 0
        startedAt = SystemClock.elapsedRealtime()
        set(Phase.STARTING, 0f, "Anahtarlık hazırlanıyor...")
        Ble.setOtaMode(true)
        // bağlantı hızlı moda geçsin diye kısa bekle
        main.postDelayed({
            Ble.otaWrite(Protocol.otaBegin(data.size)) { ok ->
                if (!ok) { fail("Bağlantı hatası, tekrar dene."); return@otaWrite }
                waitForStart(SystemClock.elapsedRealtime())
            }
        }, 400)
    }

    /** Anahtarlık "yazmaya hazırım" (durum = 1) diyene kadar bekle. */
    private fun waitForStart(since: Long) {
        if (cancelled) { abort(); return }
        val st = Ble.status.value?.otaState ?: 0
        val waited = SystemClock.elapsedRealtime() - since
        when {
            st == 1 -> { set(Phase.SENDING, 0f, "Gönderiliyor..."); sendNext() }
            st == 2 && waited > 1500 -> fail(
                "Anahtarlık güncellemeye başlayamadı. Arduino'da Tools > Partition Scheme \"Default 4MB with spiffs\" " +
                    "olmalı (\"Huge APP\" kablosuz güncellemeye izin vermez). Bir kez USB ile o ayarla yükle."
            )
            waited > 6000 -> fail("Anahtarlık cevap vermedi. Kapatıp açıp tekrar dene.")
            Ble.state.value != Ble.State.CONNECTED -> fail("Bağlantı koptu.")
            else -> main.postDelayed({ waitForStart(since) }, 100)
        }
    }

    private fun sendNext() {
        val data = bin ?: return
        if (cancelled) { abort(); return }
        if (Ble.status.value?.otaState == 2) { fail("Anahtarlık yazarken hata verdi. Tekrar dene."); return }
        if (offset >= data.size) { finish(); return }
        val n = minOf(Protocol.OTA_CHUNK, data.size - offset)
        Ble.otaWrite(Protocol.otaData(data, offset, n)) { ok ->
            if (!ok) { fail("Bağlantı koptu (%${(offset * 100L / data.size)}). Tekrar dene, baştan başlar."); return@otaWrite }
            offset += n
            val p = offset.toFloat() / data.size
            val secs = (SystemClock.elapsedRealtime() - startedAt) / 1000f
            val speed = if (secs > 0.5f) offset / 1024f / secs else 0f
            val left = if (speed > 0.1f) ((data.size - offset) / 1024f / speed).toInt() else -1
            _ui.value = _ui.value.copy(
                progress = p,
                message = "%" + (p * 100).toInt() + "  •  " + String.format(java.util.Locale.US, "%.1f", speed) + " KB/sn" +
                    if (left >= 0) "  •  ~${left / 60}:${"%02d".format(left % 60)} kaldı" else "",
            )
            sendNext()
        }
    }

    private fun finish() {
        set(Phase.FINISHING, 1f, "Anahtarlık dosyayı doğruluyor...")
        Ble.otaWrite(Protocol.otaEnd()) { _ ->
            val since = SystemClock.elapsedRealtime()
            fun check() {
                val st = Ble.status.value?.otaState ?: 0
                when {
                    Ble.state.value != Ble.State.CONNECTED -> {
                        Ble.setOtaMode(false)
                        set(Phase.DONE, 1f, "Tamam! Anahtarlık yeni yazılımla yeniden başlıyor, birazdan kendiliğinden bağlanır.")
                    }
                    st == 2 -> fail("Dosya doğrulanamadı, eski yazılım duruyor. Dosyayı yeniden dışa aktarıp dene.")
                    SystemClock.elapsedRealtime() - since > 15000 -> {
                        Ble.setOtaMode(false)
                        set(Phase.DONE, 1f, "Gönderildi. Anahtarlık yeniden başlamadıysa kapatıp aç.")
                    }
                    else -> main.postDelayed({ check() }, 200)
                }
            }
            check()
        }
    }

    fun cancel() {
        if (!busy) return
        cancelled = true
        if (_ui.value.phase == Phase.FINISHING) cancelled = false   // doğrulama başladıysa geri dönüş yok
    }

    private fun abort() {
        Ble.setOtaMode(false)
        Ble.otaWrite(Protocol.otaAbort()) { }
        set(Phase.READY, 0f, "İptal edildi. Anahtarlık eski yazılımla devam ediyor.")
    }

    private fun fail(msg: String) {
        if (Ble.state.value == Ble.State.CONNECTED && _ui.value.phase in setOf(Phase.STARTING, Phase.SENDING)) {
            Ble.otaWrite(Protocol.otaAbort()) { }
        }
        Ble.setOtaMode(false)
        _ui.value = _ui.value.copy(phase = Phase.ERROR, message = msg)
    }

    private fun set(phase: Phase, p: Float, msg: String) {
        _ui.value = _ui.value.copy(phase = phase, progress = p, message = msg)
    }
}
