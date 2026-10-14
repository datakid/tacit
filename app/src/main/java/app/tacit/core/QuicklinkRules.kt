package app.tacit.core

import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class QuicklinkType { URL, SETTING, APP, APP_SHORTCUT, FILE, FOLDER, CONTACT }

enum class TargetState { OK, MISSING, REPICK }

class QuicklinkNames(val id: String, val names: List<String>)

class QuicklinkMatch(val id: String, val query: String)

class ContactTarget(val action: String, val number: String)

object QuicklinkRules {

    const val PLACEHOLDER = "{query}"
    const val CLIPBOARD = "{clipboard}"

    val contactActions = listOf("call", "sms", "wa", "wab")

    private val allowedSchemes = setOf("http", "https", "mailto", "tel", "sms", "smsto", "geo")
    private val blockedSchemes = setOf(
        "file", "javascript", "content", "intent", "android-app", "data", "vbscript", "about", "blob", "filesystem", "view-source"
    )
    private val customScheme = Regex("^[a-z][a-z0-9+.-]{1,31}$")
    private val schemePrefix = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val space = Regex("\\s+")
    private val token = Regex("\\{(query|clipboard|date)(?::([^{}]{1,32}))?\\}")
    private val numberShape = Regex("^[0-9+*#() .-]{1,32}$")

    fun takesQuery(target: String): Boolean = target.contains(PLACEHOLDER)

    fun usesClipboard(target: String): Boolean = target.contains(CLIPBOARD)

    fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    fun fill(template: String, query: String, clipboard: String = "", now: LocalDateTime = LocalDateTime.now()): String =
        token.replace(template) { match ->
            val name = match.groupValues[1]
            val format = match.groups[2]?.value
            when {
                name == "query" && format == null -> encode(query)
                name == "clipboard" && format == null -> encode(clipboard)
                name == "date" -> encode(dateFormatter(format).format(now))
                else -> match.value
            }
        }

    fun placeholdersValid(template: String): Boolean =
        token.findAll(template).all { match ->
            val format = match.groups[2]?.value
            when (match.groupValues[1]) {
                "date" -> format == null || runCatching { DateTimeFormatter.ofPattern(format) }.isSuccess
                else -> format == null
            }
        }

    private fun dateFormatter(pattern: String?): DateTimeFormatter =
        if (pattern == null) DateTimeFormatter.ISO_LOCAL_DATE
        else runCatching { DateTimeFormatter.ofPattern(pattern, Locale.ROOT) }.getOrDefault(DateTimeFormatter.ISO_LOCAL_DATE)

    fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.isISOControl() }) return null
        val prefix = schemePrefix.find(trimmed)
        if (prefix == null) return finishWebUrl("https://$trimmed")
        val scheme = prefix.value.dropLast(1).lowercase()
        val rest = trimmed.substring(prefix.value.length)
        return when {
            scheme == "http" || scheme == "https" -> finishWebUrl(scheme + ":" + rest)
            scheme in allowedSchemes -> if (rest.isEmpty()) null else scheme + ":" + rest
            scheme in blockedSchemes -> null
            rest.firstOrNull()?.isDigit() == true -> finishWebUrl("https://$trimmed")
            rest.startsWith("//") && rest.length > 2 && customScheme.matches(scheme) -> scheme + ":" + rest
            else -> null
        }
    }

    fun isDeepLink(url: String): Boolean {
        val scheme = url.substringBefore(':').lowercase()
        return scheme !in allowedSchemes && customScheme.matches(scheme)
    }

    private fun finishWebUrl(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return if (host.isEmpty() || !url.contains("://")) null else url
    }

    fun host(target: String): String {
        if (!target.contains("://")) return target.substringAfter(':').substringBefore('?')
        val afterScheme = target.substringAfter("://")
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        return host.removePrefix("www.")
    }

    fun contactTarget(action: String, number: String): String? {
        val cleaned = number.trim()
        if (action !in contactActions || !numberShape.matches(cleaned) || cleaned.none { it.isDigit() }) return null
        return "$action|$cleaned"
    }

    fun parseContact(target: String): ContactTarget? {
        val action = target.substringBefore('|', "")
        val number = target.substringAfter('|', "")
        val encoded = contactTarget(action, number) ?: return null
        return ContactTarget(action, encoded.substringAfter('|'))
    }

    fun holdsGrant(type: QuicklinkType, target: String): Boolean =
        (type == QuicklinkType.FILE || type == QuicklinkType.FOLDER) && target.startsWith("content://")

    fun exportTarget(type: QuicklinkType, target: String): String = if (holdsGrant(type, target)) "" else target

    fun needsRepick(type: QuicklinkType, target: String): Boolean =
        (type == QuicklinkType.FILE || type == QuicklinkType.FOLDER) && target.isEmpty()

    fun importTarget(type: QuicklinkType, target: String): String? = when (type) {
        QuicklinkType.URL -> if (placeholdersValid(target) && normalizeUrl(target) != null) target else null
        QuicklinkType.CONTACT -> if (parseContact(target) != null) target else null
        QuicklinkType.FILE, QuicklinkType.FOLDER -> when {
            target.isEmpty() || target.startsWith("/") -> target
            target.startsWith("content://") -> ""
            else -> null
        }
        QuicklinkType.SETTING, QuicklinkType.APP, QuicklinkType.APP_SHORTCUT -> target.takeIf { it.isNotEmpty() }
    }

    fun match(text: String, candidates: List<QuicklinkNames>): QuicklinkMatch? {
        val tokens = text.trim().split(space).filter { it.isNotEmpty() }
        if (tokens.size < 2) return null
        val normalized = tokens.map { Normalizer.normalize(it).trim() }
        var best: QuicklinkMatch? = null
        var bestLength = 0
        for (candidate in candidates) {
            for (name in candidate.names) {
                val nameTokens = Normalizer.normalize(name).trim().split(space).filter { it.isNotEmpty() }
                val count = nameTokens.size
                if (count == 0 || count >= tokens.size || count <= bestLength) continue
                if (normalized.take(count) == nameTokens) {
                    best = QuicklinkMatch(candidate.id, tokens.drop(count).joinToString(" "))
                    bestLength = count
                }
            }
        }
        return best
    }
}
