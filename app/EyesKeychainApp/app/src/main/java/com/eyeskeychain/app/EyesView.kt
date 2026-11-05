package com.eyeskeychain.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.min
import kotlin.math.sin

private val SCREEN_BG = Color.Black

/**
 * Anahtarlık ekranının küçük bir kopyası: gözler anahtarlıktaki çizimle aynı kurallarla çizilir,
 * kendi kendine göz kırpar ve etrafa bakar. Şekil değişince yumuşak geçiş yapar.
 */
@Composable
fun EyesCanvas(eye: EyeParams, modifier: Modifier = Modifier, animate: Boolean = true) {
    val sp = spring<Float>(dampingRatio = 0.8f, stiffness = 300f)
    val w by animateFloatAsState(eye.w.toFloat(), sp, label = "w")
    val h by animateFloatAsState(eye.h.toFloat(), sp, label = "h")
    val r by animateFloatAsState(eye.r.toFloat(), sp, label = "r")
    val gap by animateFloatAsState(eye.gap.toFloat(), sp, label = "gap")
    val tired by animateFloatAsState(eye.tired / 100f, sp, label = "tired")
    val angry by animateFloatAsState(eye.angry / 100f, sp, label = "angry")
    val sad by animateFloatAsState(eye.sad / 100f, sp, label = "sad")
    val happy by animateFloatAsState(eye.happy / 100f, sp, label = "happy")
    val bottom by animateFloatAsState(eye.bottom / 100f, sp, label = "bottom")
    val color by animateColorAsState(eye.color, label = "color")

    val t by produceState(0L, animate) {
        if (!animate) return@produceState
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = it - start }
    }

    Canvas(modifier.fillMaxWidth().aspectRatio(240f / 216f)) {
        val s = size.width / 240f
        drawRoundRect(SCREEN_BG, cornerRadius = CornerRadius(18f * s))
        clipRect {
            // göz kırpma: ~3.6 sn'de bir, kapanış hızlı açılış yavaş (anahtarlıktaki gibi)
            val open = if (!animate) 1f else blink(t)
            // bakış: iki farklı sinüsün toplamı, düzenli görünmesin
            val lookX = if (!animate) 0f else (sin(t / 1900.0) * 0.55 + sin(t / 730.0) * 0.12).toFloat()
            val lookY = if (!animate) 0f else (sin(t / 2600.0) * 4).toFloat()
            val cy = 108f + lookY
            val lx = 120f - (gap / 2 + w / 2) + lookX * 24f
            val rx = 120f + (gap / 2 + w / 2) + lookX * 24f
            drawEye(s, lx, cy, w, h, r, open, true, color, tired, angry, sad, happy, bottom)
            drawEye(s, rx, cy, w, h, r, open, false, color, tired, angry, sad, happy, bottom)
        }
    }
}

private fun blink(t: Long): Float {
    val period = 3600L
    val k = t % period
    val second = (t / period) % 4 == 2L        // arada çift kırpma
    fun one(x: Long): Float = when {
        x < 70 -> 1f - x / 70f
        x < 115 -> 0f
        x < 305 -> (x - 115) / 190f
        else -> 1f
    }
    val a = one(k)
    val b = if (second && k >= 330) one(k - 330) else 1f
    return min(a, b)
}

private fun DrawScope.drawEye(
    s: Float, cx: Float, cy: Float, w: Float, h: Float, r: Float, open: Float, left: Boolean, col: Color,
    tired: Float, angry: Float, sad: Float, happy: Float, bottom: Float,
) {
    val he = h * open
    if (he < 4f) {
        drawRoundRect(col, Offset((cx - w / 2) * s, (cy - 2) * s), Size(w * s, 4 * s), CornerRadius(2 * s))
        return
    }
    val x0 = cx - w / 2
    val y0 = cy - he / 2
    val rr = min(r, he / 2)
    drawRoundRect(col, Offset(x0 * s, y0 * s), Size(w * s, he * s), CornerRadius(rr * s))
    // kapaklar: arka plan rengiyle kesilir
    if (tired > 0.01f) drawRect(SCREEN_BG, Offset((x0 - 2) * s, (y0 - 2) * s), Size((w + 4) * s, (he * 0.55f * tired + 2) * s))
    val xin = if (left) x0 + w + 2 else x0 - 2
    val xout = if (left) x0 - 2 else x0 + w + 2
    if (angry > 0.01f) tri(s, xout, y0 - 2, xin, y0 - 2, xin, y0 + he * 0.6f * angry)
    if (sad > 0.01f) tri(s, xin, y0 - 2, xout, y0 - 2, xout, y0 + he * 0.6f * sad)
    if (bottom > 0.01f) {
        val d = he * 0.5f * bottom
        drawRect(SCREEN_BG, Offset((x0 - 2) * s, (y0 + he - d) * s), Size((w + 4) * s, (d + 3) * s))
    }
    if (happy > 0.02f) {
        val hp = happy.coerceIn(0f, 0.6f)
        val ey = y0 + he * (1.75f - 1.4f * hp)
        val ew = w * 0.75f
        val eh = he * 0.75f
        drawOval(SCREEN_BG, Offset((cx - ew) * s, (ey - eh) * s), Size(ew * 2 * s, eh * 2 * s))
    }
}

private fun DrawScope.tri(s: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
    val p = Path().apply {
        moveTo(x1 * s, y1 * s); lineTo(x2 * s, y2 * s); lineTo(x3 * s, y3 * s); close()
    }
    drawPath(p, SCREEN_BG)
}
