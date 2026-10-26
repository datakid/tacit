package app.tacit.core

object CharMask {

    fun bit(c: Char): Long = when (c) {
        in 'a'..'z' -> 1L shl (c - 'a')
        in '0'..'9' -> 1L shl (26 + (c - '0'))
        else -> 1L shl (36 + c.code % 28)
    }

    fun of(text: String): Long {
        var mask = 0L
        for (c in text) mask = mask or bit(c)
        return mask
    }
}
