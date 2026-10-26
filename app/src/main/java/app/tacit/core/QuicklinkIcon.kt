package app.tacit.core

import java.util.Locale

object QuicklinkIcon {

    const val GLYPH = "glyph"
    const val APP = "app"
    const val LETTER_PREFIX = "letter:"

    private val letterShape = Regex("^[\\p{L}\\p{N}]{1,2}$")
    private val wordBreak = Regex("[^\\p{L}\\p{N}]+")

    fun supportsApp(type: QuicklinkType): Boolean =
        type == QuicklinkType.APP || type == QuicklinkType.APP_SHORTCUT || type == QuicklinkType.ACTIVITY

    fun letters(text: String): String {
        val trimmed = text.trim()
        val out = StringBuilder()
        var count = 0
        var index = 0
        while (index < trimmed.length && count < 2) {
            val point = trimmed.codePointAt(index)
            index += Character.charCount(point)
            if (Character.isLetterOrDigit(point)) {
                out.appendCodePoint(point)
                count++
            }
        }
        return out.toString().uppercase(Locale.ROOT)
    }

    fun letter(text: String): String? {
        val cleaned = letters(text)
        return if (letterShape.matches(cleaned)) LETTER_PREFIX + cleaned else null
    }

    fun lettersOf(icon: String): String? =
        if (icon.startsWith(LETTER_PREFIX)) icon.removePrefix(LETTER_PREFIX).takeIf { letterShape.matches(it) } else null

    fun normalize(type: QuicklinkType, raw: String): String = when {
        raw == APP && supportsApp(type) -> APP
        lettersOf(raw) != null -> raw
        else -> GLYPH
    }

    fun suggestedLetters(name: String): String {
        val words = name.split(wordBreak).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> ""
            words.size == 1 -> letters(words[0])
            else -> letters(firstOf(words[0]) + firstOf(words[1]))
        }
    }

    private fun firstOf(word: String): String = String(Character.toChars(word.codePointAt(0)))

    fun packageOf(type: QuicklinkType, target: String): String? {
        val pkg = when (type) {
            QuicklinkType.APP, QuicklinkType.ACTIVITY -> if (target.contains('/')) target.substringBefore('/') else ""
            QuicklinkType.APP_SHORTCUT -> if (target.contains('|')) target.substringBefore('|') else ""
            else -> ""
        }
        return pkg.ifEmpty { null }
    }
}
