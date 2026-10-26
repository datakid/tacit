package app.tacit.core

class ScoredItem(val item: SearchItem, val score: Int)

class SearchEngine(private val scorer: Scorer = Scorer(), private val prefilter: Boolean = true) {

    @Volatile
    private var items: Array<SearchItem> = emptyArray()
    private var kindOrder: IntArray = IntArray(ItemKind.entries.size) { it }

    fun replaceItems(newItems: List<SearchItem>) {
        items = newItems.toTypedArray()
    }

    fun setKindPriority(order: List<ItemKind>) {
        val ranks = IntArray(ItemKind.entries.size) { ItemKind.entries.size }
        order.forEachIndexed { index, kind -> ranks[kind.ordinal] = index }
        kindOrder = ranks
    }

    fun setStrictness(value: Strictness) {
        scorer.strictness = value
    }

    fun search(rawQuery: String, limit: Int, kinds: Set<ItemKind>? = null): List<ScoredItem> {
        val query = Normalizer.normalize(rawQuery).trim()
        if (query.isEmpty()) return pinnedOnly(limit, kinds)
        val queryWords = Normalizer.words(query)
        val single = if (queryWords.size == 1) queryWords[0] else query
        val heap = TopK(limit)
        val filter = prefilter
        val queryMask = if (filter) maskOf(single, queryWords) else 0L
        val allowedMisses = if (filter) missesAllowed(single, queryWords) else 0
        for (item in items) {
            if (kinds != null && item.kind !in kinds) continue
            if (filter && java.lang.Long.bitCount(queryMask and item.charMask.inv()) > allowedMisses) continue
            val score = scorer.score(single, queryWords, item)
            if (score > 0) heap.offer(item, adjust(score, item))
        }
        return heap.sortedDescending()
    }

    private fun maskOf(single: String, queryWords: List<String>): Long {
        var mask = 0L
        if (queryWords.size > 1) {
            for (word in queryWords) mask = mask or CharMask.of(word)
        } else {
            for (c in single) if (c != ' ') mask = mask or CharMask.bit(c)
        }
        return mask
    }

    private fun missesAllowed(single: String, queryWords: List<String>): Int {
        if (queryWords.size <= 1) return scorer.typoAllowed(single)
        var sum = 0
        for (word in queryWords) sum += scorer.typoAllowed(word)
        return sum
    }

    private fun adjust(score: Int, item: SearchItem): Int {
        val pinBonus = if (item.pinned) 500 else 0
        val kindBonus = (ItemKind.entries.size - kindOrder[item.kind.ordinal]) * 20
        return score + pinBonus + kindBonus
    }

    private fun pinnedOnly(limit: Int, kinds: Set<ItemKind>?): List<ScoredItem> =
        items.asSequence()
            .filter { it.pinned && (kinds == null || it.kind in kinds) }
            .take(limit)
            .map { ScoredItem(it, 1) }
            .toList()

    fun all(): Array<SearchItem> = items
}

class TopK(private val capacity: Int) {
    private val itemsBuffer = arrayOfNulls<SearchItem>(capacity)
    private val scores = IntArray(capacity)
    private var size = 0

    fun offer(item: SearchItem, score: Int) {
        if (capacity == 0) return
        if (size < capacity) {
            itemsBuffer[size] = item
            scores[size] = score
            size++
            return
        }
        var minIndex = 0
        for (i in 1 until size) if (scores[i] < scores[minIndex]) minIndex = i
        if (score > scores[minIndex] || (score == scores[minIndex] && better(item, itemsBuffer[minIndex]!!))) {
            itemsBuffer[minIndex] = item
            scores[minIndex] = score
        }
    }

    private fun better(a: SearchItem, b: SearchItem): Boolean =
        a.label.length < b.label.length || (a.label.length == b.label.length && a.label < b.label)

    fun sortedDescending(): List<ScoredItem> {
        val result = ArrayList<ScoredItem>(size)
        for (i in 0 until size) result.add(ScoredItem(itemsBuffer[i]!!, scores[i]))
        result.sortWith(compareByDescending<ScoredItem> { it.score }
            .thenBy { it.item.label.length }
            .thenBy { it.item.label.lowercase() })
        return result
    }
}
