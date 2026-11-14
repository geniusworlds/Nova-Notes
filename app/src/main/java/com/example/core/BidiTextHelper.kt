package com.example.core

enum class TextDirectionMode {
    AUTO,
    LTR,
    RTL
}

object BidiTextHelper {
    private val rtlCharRange = listOf(
        0x0590..0x08FF,
        0xFB50..0xFDFF,
        0xFE70..0xFEFF
    )

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

    fun resolveIsRtl(mode: String, sampleText: String): Boolean {
        return when (mode.uppercase()) {
            "RTL" -> true
            "LTR" -> false
            else -> isRtlText(sampleText)
        }
    }

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
