package app.tacit.core

class DrawerList(val items: List<SearchItem>, val letters: CharArray, val starts: IntArray) {

    val size: Int get() = items.size

    fun sectionOf(position: Int): Int {
        if (letters.isEmpty()) return -1
        var low = 0
        var high = starts.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (starts[mid] <= position) low = mid else high = mid - 1
        }
        return low
    }

    fun positionOf(section: Int): Int = if (starts.isEmpty()) 0 else starts[section.coerceIn(0, starts.size - 1)]

    private class Entry(val item: SearchItem, val key: String)

    companion object {

        const val OTHER = '#'

        fun letterOf(sortKey: String): Char {
            val first = sortKey.firstOrNull() ?: return OTHER
            return if (first.isLetter()) first.uppercaseChar() else OTHER
        }

        fun sortKey(item: SearchItem): String {
            val normalized = item.names.firstOrNull()?.full ?: item.label.lowercase()
            var start = 0
            while (start < normalized.length && !normalized[start].isLetterOrDigit()) start++
            return normalized.substring(start)
        }

        fun build(all: Array<SearchItem>): DrawerList {
            val entries = ArrayList<Entry>(all.size)
            for (item in all) if (item.kind == ItemKind.APP) entries.add(Entry(item, sortKey(item)))
            entries.sortWith { a, b ->
                val byName = a.key.compareTo(b.key)
                if (byName != 0) byName
                else {
                    val byLabel = a.item.label.compareTo(b.item.label)
                    if (byLabel != 0) byLabel else a.item.key.compareTo(b.item.key)
                }
            }
            val letters = StringBuilder()
            val starts = ArrayList<Int>()
            for ((index, entry) in entries.withIndex()) {
                val letter = letterOf(entry.key)
                if (letters.isEmpty() || letters.last() != letter) {
                    letters.append(letter)
                    starts.add(index)
                }
            }
            return DrawerList(entries.map { it.item }, letters.toString().toCharArray(), starts.toIntArray())
        }
    }
}
