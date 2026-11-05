package com.eyeskeychain.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import com.eyeskeychain.app.ble.Ble
import com.eyeskeychain.app.ble.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Göz şekli. Değerler anahtarlıktakiyle aynı birimde:
 * w/h/r/gap piksel (240x240 ekranda), kapaklar 0-100 (%).
 */
data class EyeParams(
    val w: Int = 70,
    val h: Int = 92,
    val r: Int = 22,
    val gap: Int = 28,
    val tired: Int = 0,
    val angry: Int = 0,
    val sad: Int = 0,
    val happy: Int = 0,
    val bottom: Int = 0,
    val rgb: Int = 0x00E6FF,
) {
    val color: Color get() = Color(0xFF000000.toInt() or rgb)

    fun toBytes(): List<Int> = listOf(
        w, h, r, gap, tired, angry, sad, happy, bottom,
        (rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF,
    )

    fun encode(): String = toBytes().joinToString(",")

    companion object {
        fun decode(s: String?): EyeParams? {
            val v = s?.split(",")?.mapNotNull { it.toIntOrNull() } ?: return null
            if (v.size != 12) return null
            return EyeParams(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], (v[9] shl 16) or (v[10] shl 8) or v[11])
        }
    }
}

data class ModeInfo(val name: String, val eye: EyeParams) {
    val color: Color get() = eye.color
}

/** Anahtarlıktaki modlar (eyes_keychain.ino -> MODES ile aynı sıra ve şekil). 7 = Özel. */
val MODES = listOf(
    ModeInfo("Normal", EyeParams(70, 92, 22, 28, rgb = 0x00E6FF)),
    ModeInfo("Relax", EyeParams(72, 86, 24, 28, tired = 42, rgb = 0xAA96FF)),
    ModeInfo("Hareketli", EyeParams(66, 94, 22, 30, rgb = 0x78FF50)),
    ModeInfo("Üzgün", EyeParams(70, 84, 22, 28, sad = 55, rgb = 0x4682FF)),
    ModeInfo("Odak", EyeParams(76, 78, 20, 26, tired = 28, bottom = 28, rgb = 0xFFAA28)),
    ModeInfo("Rock", EyeParams(58, 84, 20, 30, happy = 50, rgb = 0xFF3CC8)),
    ModeInfo("Saat", EyeParams(50, 58, 16, 30, rgb = 0xEBEBF5)),
)
const val MODE_CUSTOM = 7

/** Tasarlayıcıdaki hazır şekiller. */
val SHAPE_PRESETS = listOf(
    "Klasik" to EyeParams(70, 92, 22, 28),
    "Robot" to EyeParams(84, 56, 4, 24),
    "Sevimli" to EyeParams(62, 70, 30, 34, happy = 40),
    "Kedi" to EyeParams(44, 110, 22, 40, angry = 25),
    "Kızgın" to EyeParams(74, 84, 18, 22, angry = 65),
    "Uykulu" to EyeParams(74, 84, 24, 28, tired = 70, bottom = 10),
    "Şüpheli" to EyeParams(76, 84, 20, 26, tired = 45, bottom = 40),
    "Hüzünlü" to EyeParams(68, 88, 26, 30, sad = 60),
    "Kocaman" to EyeParams(100, 130, 40, 12),
    "Nokta" to EyeParams(36, 36, 18, 50),
)

val COLOR_PRESETS = listOf(
    0x00E6FF, 0xAA96FF, 0x78FF50, 0x4682FF, 0xFFAA28,
    0xFF3CC8, 0xFF4040, 0xFFE640, 0xFFFFFF, 0x40FFC0,
)

object EyeDesign {
    private val _params = MutableStateFlow(EyeParams())
    val params: StateFlow<EyeParams> = _params
    private val _live = MutableStateFlow(true)
    val live: StateFlow<Boolean> = _live

    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private var lastSend = 0L
    private var pending = false

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("design", Context.MODE_PRIVATE)
        EyeParams.decode(prefs.getString("eye", null))?.let { _params.value = it }
        _live.value = prefs.getBoolean("live", true)
    }

    fun setLive(on: Boolean) {
        _live.value = on
        prefs.edit().putBoolean("live", on).apply()
        if (on) sendNow()
    }

    fun update(p: EyeParams) {
        _params.value = p
        prefs.edit().putString("eye", p.encode()).apply()
        if (_live.value) sendThrottled()
    }

    /** Kaydırırken saniyede en fazla ~8 kez gönder, en son hali mutlaka gitsin. */
    private fun sendThrottled() {
        val now = SystemClock.elapsedRealtime()
        val wait = 120 - (now - lastSend)
        if (wait <= 0) {
            sendNow()
        } else if (!pending) {
            pending = true
            main.postDelayed({ pending = false; sendNow() }, wait)
        }
    }

    fun sendNow() {
        lastSend = SystemClock.elapsedRealtime()
        Ble.send(Protocol.custom(_params.value.toBytes()))
    }
}
