package app.tacit.golden

import java.io.File
import java.security.MessageDigest

object GoldenFile {

    private fun moduleDir(): File {
        val here = File("").absoluteFile
        return if (File(here, "src/main").exists()) here else File(here, "app")
    }

    private fun normalize(text: String): String = text.replace("\r\n", "\n").trimEnd('\n')

    fun label(query: String): String = if (query.isEmpty() || query != query.trim()) "<" + query + ">" else query

    fun check(name: String, actual: String) {
        val expectedFile = File(moduleDir(), "src/test/resources/golden/" + name)
        val expected = if (expectedFile.exists()) expectedFile.readText() else ""
        val expectedLines = normalize(expected).split('\n')
        val actualLines = normalize(actual).split('\n')
        if (expectedLines == actualLines) return
        val out = File(moduleDir(), "build/golden-actual/" + name)
        out.parentFile.mkdirs()
        out.writeText(normalize(actual) + "\n")
        val firstDifference = (0 until maxOf(expectedLines.size, actualLines.size)).firstOrNull { index ->
            expectedLines.getOrNull(index) != actualLines.getOrNull(index)
        } ?: 0
        val digest = MessageDigest.getInstance("SHA-256").digest((normalize(actual) + "\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val message = StringBuilder()
        message.append("Golden file ").append(name).append(" differs. ")
        message.append("Expected ").append(expectedLines.size).append(" lines, got ").append(actualLines.size).append(". ")
        message.append("First difference at line ").append(firstDifference + 1).append(".\n")
        message.append("expected: ").append(expectedLines.getOrNull(firstDifference) ?: "(none)").append("\n")
        message.append("actual:   ").append(actualLines.getOrNull(firstDifference) ?: "(none)").append("\n")
        message.append("TACIT_GOLDEN_ACTUAL_BEGIN ").append(name).append(" sha256=").append(digest).append("\n")
        message.append(normalize(actual)).append("\n")
        message.append("TACIT_GOLDEN_ACTUAL_END ").append(name)
        throw AssertionError(message.toString())
    }
}
