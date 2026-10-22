package app.tacit.core

class ExistenceCache {

    @Volatile
    private var known: Map<String, Boolean> = emptyMap()

    fun present(key: String): Boolean = known[key] ?: true

    fun rebuild(keys: Collection<String>, exists: (String) -> Boolean) {
        val next = HashMap<String, Boolean>(keys.size * 2)
        for (key in keys) next[key] = exists(key)
        known = next
    }
}
