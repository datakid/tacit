package app.tacit.core

import java.net.URLDecoder

object PathNames {

    fun name(path: String): String {
        val tail = path.substringAfterLast('/')
        if (!path.startsWith("content://")) return tail
        val decoded = decode(tail)
        return decoded.substringAfterLast('/').substringAfterLast(':').ifEmpty { decoded }
    }

    fun folder(path: String): String {
        if (path.startsWith("content://")) return ""
        return path.substringBeforeLast('/', "").replace("/storage/emulated/0", "~")
    }

    private fun decode(text: String): String =
        runCatching { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)
}
