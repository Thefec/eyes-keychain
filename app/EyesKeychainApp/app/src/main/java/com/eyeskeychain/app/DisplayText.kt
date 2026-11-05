package com.eyeskeychain.app

import android.icu.text.Transliterator
import android.os.Build

/**
 * Anahtarlığın yazı tipinde sadece Latin harfler (Türkçe + Batı Avrupa) var.
 * Başka alfabeleri (Kiril, Japonca kana, Korece, Yunanca, Arapça...) göndermeden önce
 * Latin harflere çevirir, yazı tipinde olmayan karakterleri atar. Böylece ekranda kare çıkmaz.
 */
object DisplayText {
    /** Anahtarlıktaki yazı tipinde bulunan karakterler (eyes_keychain.ino -> FONT_TR ile aynı liste). */
    private val SUPPORTED: Set<Int> = buildSet {
        for (c in 32..126) add(c)
        "ÇçĞğİıÖöŞşÜü‘’“”–—…•♪°·".codePoints().forEach { add(it) }
        for (c in 0xC0..0xFF) if (c != 0xD7 && c != 0xF7) add(c)
    }

    private val toLatin: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { Transliterator.getInstance("Any-Latin") }.getOrNull() else null
    }
    private val toAscii: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { Transliterator.getInstance("Latin-ASCII") }.getOrNull() else null
    }

    fun isSupported(s: String): Boolean = s.codePoints().allMatch { it in SUPPORTED }

    /**
     * Ekrana gidecek hali. Metin zaten uygunsa aynen döner.
     * latinize=false ise uygun olmayan metin için null döner (çağıran başka bir şey gösterir).
     */
    fun prepare(s: String, latinize: Boolean = AppSettings.latinize.value): String? {
        if (isSupported(s)) return s
        if (!latinize) return null
        val latin = toLatin?.transliterate(s) ?: s
        val sb = StringBuilder()
        latin.codePoints().forEach { cp ->
            if (cp in SUPPORTED) {
                sb.appendCodePoint(cp)
            } else {
                // örn. ō -> o, ß zaten var, ł -> l
                val a = toAscii?.transliterate(String(Character.toChars(cp))) ?: ""
                a.codePoints().forEach { c -> if (c in SUPPORTED) sb.appendCodePoint(c) }
            }
        }
        val out = sb.toString().replace(Regex("\\s+"), " ").trim()
        return out.ifEmpty { null }
    }
}
