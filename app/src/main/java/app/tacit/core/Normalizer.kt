package app.tacit.core

import java.text.Normalizer as JavaNormalizer

object Normalizer {

    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{Nd}]+")
    private val camelBoundary = Regex("(?<=\\p{Ll})(?=\\p{Lu})")

    private fun isAscii(input: String): Boolean {
        for (ch in input) if (ch.code >= 0x80) return false
        return true
    }

    fun normalize(input: String): String {
        if (isAscii(input)) return input.lowercase()
        val builder = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> builder.append('ا')
                'ة' -> builder.append('ه')
                'ى' -> builder.append('ي')
                'ؤ' -> builder.append('و')
                'ئ' -> builder.append('ي')
                'ـ' -> Unit
                in '\u064B'..'\u065F', '\u0670' -> Unit
                in '٠'..'٩' -> builder.append('0' + (ch - '٠'))
                in '۰'..'۹' -> builder.append('0' + (ch - '۰'))
                else -> builder.append(ch)
            }
        }
        val decomposed = JavaNormalizer.normalize(builder, JavaNormalizer.Form.NFD)
        return combiningMarks.replace(decomposed, "").lowercase()
    }

    fun words(normalized: String): List<String> =
        normalized.split(separators).filter { it.isNotEmpty() }

    fun splitCamel(raw: String): String = camelBoundary.replace(raw, " ")

    fun digitsOnly(input: String): String {
        val normalized = normalize(input)
        val builder = StringBuilder()
        for ((index, ch) in normalized.withIndex()) {
            if (ch.isDigit()) builder.append(ch)
            else if (ch == '+' && index == 0) builder.append(ch)
        }
        return builder.toString()
    }
}
