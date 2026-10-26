package app.tacit.core

import app.tacit.fixtures.Fixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.Random

class SearchPrefilterEquivalenceTest {

    private var savedLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ROOT)
        IndexOptions.transliterate = true
    }

    @After
    fun tearDown() {
        Locale.setDefault(savedLocale)
    }

    private val items: List<SearchItem> by lazy {
        val all = Fixtures.all()
        all.first { it.key == "app:fixture/Calculator" }.withAliases(listOf("zq9", "pocket calc"))
        all
    }

    private fun engines(strictness: Strictness): Pair<SearchEngine, SearchEngine> {
        val on = SearchEngine(prefilter = true)
        val off = SearchEngine(prefilter = false)
        for (engine in listOf(on, off)) {
            engine.replaceItems(items)
            engine.setStrictness(strictness)
        }
        return on to off
    }

    private fun unlabel(label: String): String =
        if (label.length >= 2 && label.startsWith("<") && label.endsWith(">")) label.substring(1, label.length - 1) else label

    private fun goldenQueries(): List<String> {
        val text = javaClass.getResourceAsStream("/golden/ranking.tsv")!!.bufferedReader(Charsets.UTF_8).readText()
        return text.lines().filter { it.isNotEmpty() }
            .map { unlabel(it.substringBefore('\t').removePrefix("compose:")) }
            .distinct()
    }

    private val pool = "abcdefghijklmnopqrstuvwxyz0123456789   -éüøاأبتثجحخدذرزسشصضطظعغفقكلمنهوي"

    private fun randomQueries(): List<String> {
        val random = Random(11)
        return List(500) {
            val length = 1 + random.nextInt(12)
            String(CharArray(length) { pool[random.nextInt(pool.length)] })
        }
    }

    private fun nearMisses(): List<String> {
        val random = Random(12)
        val labels = items.map { it.label }
        return List(500) {
            val label = Normalizer.normalize(labels[random.nextInt(labels.size)])
            val start = random.nextInt(label.length)
            val length = 1 + random.nextInt(minOf(12, label.length - start))
            val builder = StringBuilder(label.substring(start, start + length))
            repeat(random.nextInt(4)) {
                if (builder.isEmpty()) return@repeat
                val at = random.nextInt(builder.length)
                when (random.nextInt(4)) {
                    0 -> builder.deleteCharAt(at)
                    1 -> builder.setCharAt(at, pool[random.nextInt(pool.length)])
                    2 -> builder.insert(at, pool[random.nextInt(pool.length)])
                    else -> if (at + 1 < builder.length) {
                        val swapped = builder[at]
                        builder.setCharAt(at, builder[at + 1])
                        builder.setCharAt(at + 1, swapped)
                    }
                }
            }
            builder.toString().take(12)
        }
    }

    private fun render(results: List<ScoredItem>): String = results.joinToString("|") { it.item.key + "@" + it.score }

    private fun assertSame(queries: List<String>, limit: Int, kinds: Set<ItemKind>?, strictness: Strictness) {
        val (on, off) = engines(strictness)
        val differences = ArrayList<String>()
        for (query in queries) {
            val withFilter = render(on.search(query, limit, kinds))
            val without = render(off.search(query, limit, kinds))
            if (withFilter != without) differences.add("[" + query + "] on=" + withFilter + " off=" + without)
        }
        assertTrue(strictness.name + ": " + differences.size + " queries differ, first: " + differences.take(2), differences.isEmpty())
    }

    @Test
    fun goldenQueriesGiveIdenticalResults() {
        val queries = goldenQueries()
        assertTrue("golden queries found: " + queries.size, queries.size >= 120)
        for (strictness in Strictness.entries) assertSame(queries, 8, null, strictness)
    }

    @Test
    fun goldenQueriesGiveIdenticalResultsWithALongerListAndAKindFilter() {
        val queries = goldenQueries()
        assertSame(queries, 50, null, Strictness.NORMAL)
        assertSame(queries, 8, setOf(ItemKind.APP), Strictness.NORMAL)
    }

    @Test
    fun fiveHundredRandomShortQueriesGiveIdenticalResults() {
        val queries = randomQueries()
        assertEquals(500, queries.size)
        assertTrue(queries.all { it.length in 1..12 })
        for (strictness in Strictness.entries) assertSame(queries, 8, null, strictness)
    }

    @Test
    fun fiveHundredMisspeltNamesGiveIdenticalResults() {
        val queries = nearMisses()
        assertEquals(500, queries.size)
        for (strictness in Strictness.entries) assertSame(queries, 8, null, strictness)
    }

    @Test
    fun prefilterSkipsItemsThatCannotMatch() {
        val item = SearchItem("app:x", ItemKind.APP, "Maps")
        val queryMask = CharMask.of("zz")
        assertTrue(java.lang.Long.bitCount(queryMask and item.charMask.inv()) == 1)
        assertTrue(java.lang.Long.bitCount(CharMask.of("map") and item.charMask.inv()) == 0)
    }

    @Test
    fun theMaskCoversEveryNameAndAliasAddedLater() {
        val item = SearchItem("app:y", ItemKind.APP, "Maps", extraNames = listOf("navigation"))
        val names = item.charMask
        assertTrue(names and CharMask.of("mapsnvigton") == CharMask.of("mapsnvigton"))
        assertTrue(names and CharMask.bit('z') == 0L)
        item.withAliases(listOf("zq9"))
        assertTrue(item.charMask and CharMask.of("zq9") == CharMask.of("zq9"))
        assertTrue(item.charMask and names == names)
        item.withAliases(emptyList())
        assertEquals(names, item.charMask)
    }

    @Test
    fun theMaskKeepsTheSchemeOfTheFileMatcher() {
        assertEquals(1L, CharMask.bit('a'))
        assertEquals(1L shl 25, CharMask.bit('z'))
        assertEquals(1L shl 26, CharMask.bit('0'))
        assertEquals(1L shl 35, CharMask.bit('9'))
        assertEquals(1L shl (36 + 'ا'.code % 28), CharMask.bit('ا'))
    }
}
