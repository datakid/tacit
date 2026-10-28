package app.tacit.core

enum class Strictness { OFF, NORMAL, LOOSE }

object Tier {
    const val NONE = 0
    const val TYPO = 1
    const val SUBSEQUENCE = 2
    const val INITIALS = 3
    const val WORD_PREFIX = 4
    const val FULL_PREFIX = 5
    const val EXACT = 6
    const val ALIAS_PREFIX = 7
    const val ALIAS_EXACT = 8
    const val SCALE = 100_000
}

class Scorer(var strictness: Strictness = Strictness.NORMAL) {

    private val row0 = IntArray(64)
    private val row1 = IntArray(64)
    private val row2 = IntArray(64)

    fun score(query: String, queryWords: List<String>, item: SearchItem): Int {
        if (query.isEmpty()) return 0
        var best = 0
        for (alias in item.aliases) {
            if (alias == query) return encode(Tier.ALIAS_EXACT, 1000)
            if (alias.startsWith(query)) best = maxOf(best, encode(Tier.ALIAS_PREFIX, 1000 - alias.length))
        }
        for (name in item.names) {
            val value = if (queryWords.size > 1) scoreMulti(queryWords, name) else scoreSingle(query, name)
            if (value > best) best = value
        }
        return best
    }

    fun tierOf(score: Int): Int = score / Tier.SCALE

    private fun encode(tier: Int, bonus: Int): Int = tier * Tier.SCALE + bonus.coerceIn(0, 9_999)

    private fun scoreSingle(query: String, name: IndexedName): Int {
        val full = name.full
        val lengthBonus = 1000 - full.length.coerceAtMost(999)
        if (full == query || name.compact == query) return encode(Tier.EXACT, lengthBonus)
        if (full.startsWith(query) || name.compact.startsWith(query)) return encode(Tier.FULL_PREFIX, lengthBonus)
        for ((index, word) in name.words.withIndex()) {
            if (word.startsWith(query)) return encode(Tier.WORD_PREFIX, lengthBonus - index * 10)
        }
        if (query.length >= 2 && name.initials.startsWith(query)) return encode(Tier.INITIALS, lengthBonus)
        val subsequence = subsequenceScore(query, name)
        if (subsequence > 0) return encode(Tier.SUBSEQUENCE, subsequence)
        val typo = typoScore(query, name.words)
        if (typo > 0) return encode(Tier.TYPO, typo)
        return 0
    }

    private fun scoreMulti(queryWords: List<String>, name: IndexedName): Int {
        val used = BooleanArray(name.words.size)
        var worst = Tier.ALIAS_EXACT
        var sum = 0
        var lastIndex = -1
        var ordered = true
        for (wordIndex in queryWords.indices) {
            val queryWord = queryWords[wordIndex]
            var bestTier = 0
            var bestIndex = -1
            for ((index, word) in name.words.withIndex()) {
                if (used[index]) continue
                val tier = wordTier(queryWord, word)
                if (tier > bestTier) {
                    bestTier = tier
                    bestIndex = index
                }
            }
            if (bestTier == 0) return 0
            used[bestIndex] = true
            if (bestIndex < lastIndex) ordered = false
            lastIndex = bestIndex
            worst = minOf(worst, bestTier)
            sum += bestTier
        }
        val bonus = sum * 100 + (if (ordered) 50 else 0) + (100 - name.full.length.coerceAtMost(99))
        return encode(worst, bonus)
    }

    private fun wordTier(queryWord: String, word: String): Int = when {
        word == queryWord -> Tier.EXACT
        word.startsWith(queryWord) -> Tier.WORD_PREFIX
        typoAllowed(queryWord) > 0 && prefixDistance(queryWord, word, typoAllowed(queryWord)) <= typoAllowed(queryWord) &&
            firstLetterOk(queryWord, word) -> Tier.TYPO
        else -> Tier.NONE
    }

    private fun subsequenceScore(query: String, name: IndexedName): Int {
        if (query.length < 2) return 0
        val text = name.full
        var position = 0
        var score = 0
        var previous = -2
        for (ch in query) {
            if (ch == ' ') continue
            val found = text.indexOf(ch, position)
            if (found < 0) return 0
            val atWordStart = found == 0 || text[found - 1] == ' ' || text[found - 1] == '-'
            score += when {
                found == previous + 1 -> 30
                atWordStart -> 25
                else -> 5 - minOf(4, found - position)
            }
            previous = found
            position = found + 1
        }
        if (text.isNotEmpty() && text[0] != query[0]) score -= 20
        return if (score >= query.length * 12) score.coerceAtLeast(1) else 0
    }

    fun typoAllowed(query: String): Int = when (strictness) {
        Strictness.OFF -> 0
        Strictness.NORMAL -> when {
            query.length < 3 -> 0
            query.length < 6 -> 1
            else -> 2
        }
        Strictness.LOOSE -> when {
            query.length < 3 -> 0
            query.length < 5 -> 1
            else -> 2
        }
    }

    private fun firstLetterOk(query: String, word: String): Boolean =
        word.isNotEmpty() && (query[0] == word[0] || (query.length > 1 && word.length > 1 && query[0] == word[1] && query[1] == word[0]))

    private fun typoScore(query: String, words: Array<String>): Int {
        val allowed = typoAllowed(query)
        if (allowed == 0) return 0
        var best = 0
        for ((index, word) in words.withIndex()) {
            if (!firstLetterOk(query, word)) continue
            val distance = prefixDistance(query, word, allowed)
            if (distance <= allowed) {
                val value = 500 - distance * 100 - index * 10
                if (value > best) best = value.coerceAtLeast(1)
            }
        }
        return best
    }

    fun prefixDistance(query: String, word: String, allowed: Int = 2): Int {
        val n = query.length
        val m = minOf(word.length, n + 2)
        if (n >= 63 || m >= 63) return Int.MAX_VALUE
        val shortestPrefix = maxOf(1, n - allowed + 1)
        if (m < shortestPrefix) return Int.MAX_VALUE
        for (j in 0..m) row1[j] = j
        var prevPrev = row0
        var prev = row1
        var current = row2
        var bestPrefix = Int.MAX_VALUE
        for (i in 1..n) {
            current[0] = i
            for (j in 1..m) {
                val cost = if (query[i - 1] == word[j - 1]) 0 else 1
                var value = minOf(prev[j] + 1, current[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && query[i - 1] == word[j - 2] && query[i - 2] == word[j - 1]) {
                    value = minOf(value, prevPrev[j - 2] + 1)
                }
                current[j] = value
            }
            val swap = prevPrev
            prevPrev = prev
            prev = current
            current = swap
        }
        for (j in shortestPrefix..m) if (prev[j] < bestPrefix) bestPrefix = prev[j]
        return bestPrefix
    }
}
