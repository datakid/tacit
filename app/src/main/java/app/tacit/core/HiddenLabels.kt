package app.tacit.core

object HiddenLabels {

    fun resolve(keys: List<String>, items: List<SearchItem>): List<String> {
        val labels = HashMap<String, String>(items.size * 2)
        for (item in items) if (item.label.isNotBlank()) labels.putIfAbsent(item.key, item.label)
        return keys.map { labels[it] ?: fallback(it) }
    }

    fun fallback(key: String): String = key.substringAfter(':').substringBefore('/')
}
