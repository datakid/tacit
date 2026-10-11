package app.tacit.core

import java.net.URLEncoder

enum class QuicklinkType { URL, SETTING, APP }

class QuicklinkNames(val id: String, val names: List<String>)

class QuicklinkMatch(val id: String, val query: String)

object QuicklinkRules {

    const val PLACEHOLDER = "{query}"

    private val allowedSchemes = setOf("http", "https", "mailto", "tel", "geo")
    private val schemePrefix = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val space = Regex("\\s+")

    fun takesQuery(target: String): Boolean = target.contains(PLACEHOLDER)

    fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    fun fill(template: String, query: String): String = template.replace(PLACEHOLDER, encode(query))

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
            rest.firstOrNull()?.isDigit() == true -> finishWebUrl("https://$trimmed")
            else -> null
        }
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
