package app.tacit.core

import java.net.URLDecoder

enum class ExtraType { STRING, INT, LONG, BOOLEAN }

data class IntentExtra(val key: String, val type: ExtraType, val value: String)

data class IntentSpec(
    val action: String = "",
    val data: String = "",
    val mime: String = "",
    val categories: List<String> = emptyList(),
    val component: String = "",
    val extras: List<IntentExtra> = emptyList()
) {

    enum class Problem {
        NOTHING_TO_LAUNCH,
        BAD_ACTION,
        BAD_DATA,
        BLOCKED_DATA,
        BAD_MIME,
        BAD_CATEGORY,
        BAD_COMPONENT,
        OWN_APP,
        TOO_MANY_EXTRAS,
        BAD_EXTRA_KEY,
        EXTRA_KEY_TOO_LONG,
        EXTRA_TEXT_TOO_LONG,
        BAD_EXTRA_NUMBER,
        BAD_EXTRA_BOOLEAN
    }

    fun validate(ownPackage: String): List<Problem> {
        val found = LinkedHashSet<Problem>()
        if (action.isEmpty() && component.isEmpty()) found.add(Problem.NOTHING_TO_LAUNCH)
        if (action.isNotEmpty() && !wordShape.matches(action)) found.add(Problem.BAD_ACTION)
        if (data.isNotEmpty()) checkData(ownPackage, found)
        if (mime.isNotEmpty() && !mimeShape.matches(mime)) found.add(Problem.BAD_MIME)
        if (categories.any { !wordShape.matches(it) || it.contains(',') }) found.add(Problem.BAD_CATEGORY)
        if (component.isNotEmpty()) checkComponent(ownPackage, found)
        checkExtras(found)
        return found.toList()
    }

    fun summary(): String = action.ifEmpty { component }

    private fun checkData(ownPackage: String, found: MutableSet<Problem>) {
        val scheme = schemePrefix.find(data)
        if (scheme == null || data.any { it.isWhitespace() || it.isISOControl() } || data.length == scheme.value.length) {
            found.add(Problem.BAD_DATA)
            return
        }
        val name = scheme.value.dropLast(1).lowercase()
        if (name == "file") {
            found.add(Problem.BLOCKED_DATA)
        } else if (name == "content" && authorityOf(data.substring(scheme.value.length)) == "$ownPackage.files".lowercase()) {
            found.add(Problem.BLOCKED_DATA)
        }
    }

    private fun authorityOf(rest: String): String {
        val raw = rest.trimStart('/').substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return decoded.lowercase()
    }

    private fun checkComponent(ownPackage: String, found: MutableSet<Problem>) {
        val match = componentShape.matchEntire(component)
        if (match == null) {
            found.add(Problem.BAD_COMPONENT)
        } else if (match.groupValues[1] == ownPackage) {
            found.add(Problem.OWN_APP)
        }
    }

    private fun checkExtras(found: MutableSet<Problem>) {
        if (extras.size > MAX_EXTRAS) found.add(Problem.TOO_MANY_EXTRAS)
        for (extra in extras) {
            if (extra.key.isEmpty() || extra.key.any { it.isISOControl() }) found.add(Problem.BAD_EXTRA_KEY)
            if (extra.key.length > MAX_KEY) found.add(Problem.EXTRA_KEY_TOO_LONG)
            when (extra.type) {
                ExtraType.STRING -> if (extra.value.length > MAX_TEXT) found.add(Problem.EXTRA_TEXT_TOO_LONG)
                ExtraType.INT -> if (extra.value.toIntOrNull() == null) found.add(Problem.BAD_EXTRA_NUMBER)
                ExtraType.LONG -> if (extra.value.toLongOrNull() == null) found.add(Problem.BAD_EXTRA_NUMBER)
                ExtraType.BOOLEAN -> if (!extra.value.equals("true", true) && !extra.value.equals("false", true)) found.add(Problem.BAD_EXTRA_BOOLEAN)
            }
        }
    }

    companion object {
        const val MAX_EXTRAS = 20
        const val MAX_KEY = 100
        const val MAX_TEXT = 1000

        private val wordShape = Regex("^[^\\s\\p{Cntrl}]{1,200}$")
        private val schemePrefix = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
        private val mimePart = "(?:\\*|[A-Za-z0-9!#$&^_.+-]+)"
        private val mimeShape = Regex("^$mimePart/$mimePart$")
        private val componentShape = Regex("^([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*)/(\\.?[A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)*)$")

        fun parseCategories(text: String): List<String> =
            text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        fun categoriesText(categories: List<String>): String = categories.joinToString(", ")
    }
}
