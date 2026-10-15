package app.tacit.core

class SharedAlias(val alias: String, val owners: List<String>)

class AliasEntry(val key: String, val aliases: List<String>)

object AliasRules {

    private val spaces = Regex("\\s+")

    fun key(alias: String): String = Normalizer.normalize(alias).trim().replace(spaces, " ")

    fun clean(values: Collection<String>): List<String> {
        val seen = HashSet<String>()
        val result = ArrayList<String>(values.size)
        for (value in values) {
            val trimmed = value.trim()
            if (key(trimmed).let { it.isNotEmpty() && seen.add(it) }) result.add(trimmed)
        }
        return result
    }

    fun shared(map: Map<String, List<String>>, isPresent: (String) -> Boolean): List<SharedAlias> {
        val owners = HashMap<String, MutableList<String>>()
        val shown = HashMap<String, String>()
        for (itemKey in map.keys.sorted()) {
            if (!isPresent(itemKey)) continue
            for (alias in clean(map.getValue(itemKey))) {
                val normalized = key(alias)
                owners.getOrPut(normalized) { ArrayList() }.add(itemKey)
                shown.putIfAbsent(normalized, alias)
            }
        }
        return owners.entries.filter { it.value.size > 1 }.sortedBy { it.key }.map { SharedAlias(shown.getValue(it.key), it.value) }
    }

    fun otherOwners(map: Map<String, List<String>>, itemKey: String, aliases: Collection<String>): List<String> {
        val wanted = aliases.map(::key).filter { it.isNotEmpty() }.toSet()
        if (wanted.isEmpty()) return emptyList()
        return map.entries.filter { entry -> entry.key != itemKey && entry.value.any { key(it) in wanted } }.map { it.key }.sorted()
    }

    fun matching(query: String, entries: List<AliasEntry>): List<AliasEntry> {
        val wanted = key(query)
        if (wanted.isEmpty() || entries.isEmpty()) return emptyList()
        val exact = ArrayList<AliasEntry>()
        val prefix = ArrayList<AliasEntry>()
        for (entry in entries) {
            if (entry.aliases.any { it == wanted }) exact.add(entry) else if (entry.aliases.any { it.startsWith(wanted) }) prefix.add(entry)
        }
        return exact + prefix
    }
}
