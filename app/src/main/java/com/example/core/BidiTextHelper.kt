package com.example.core

enum class TextDirectionMode {
    AUTO,
    LTR,
    RTL
}

object BidiTextHelper {
    private val rtlCharRange = listOf(
        0x0590..0x08FF, // Hebrew, Arabic, Syriac, Thaana, NKo, Samaritan, Mandaic
        0xFB50..0xFDFF, // Arabic Presentation Forms-A
        0xFE70..0xFEFF  // Arabic Presentation Forms-B
    )

    /**
     * Detects whether the first strong directional character in [text] is RTL (e.g. Arabic) or LTR.
     */
    fun isRtlText(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if (rtlCharRange.any { code in it }) {
                return true
            }
            if (ch in 'A'..'Z' || ch in 'a'..'z') {
                return false
            }
        }
        return false
    }

    /**
     * Resolves whether RTL layout direction should be used for a given [mode] and [sampleText].
     */
    fun resolveIsRtl(mode: String, sampleText: String): Boolean {
        return when (mode.uppercase()) {
            "RTL" -> true
            "LTR" -> false
            else -> isRtlText(sampleText)
        }
    }

    /**
     * Converts Western Arabic numerals (0-9) to Eastern Arabic numerals (٠-٩) when requested.
     */
    fun formatNumber(value: Int, useArabicNumerals: Boolean): String {
        val raw = value.toString()
        if (!useArabicNumerals) return raw
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return buildString(raw.length) {
            for (c in raw) {
                if (c in '0'..'9') {
                    append(arabicDigits[c - '0'])
                } else {
                    append(c)
                }
            }
        }
    }
}
