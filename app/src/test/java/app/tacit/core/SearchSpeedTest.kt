package app.tacit.core

import app.tacit.fixtures.Fixtures
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchSpeedTest {

    @Before
    fun setUp() {
        IndexOptions.transliterate = true
    }

    private val items = Fixtures.all()

    private val queries: List<String> = Fixtures.typingScript.filter { it.isNotBlank() } + listOf(
        "yotube", "settin", "calcul", "camer", "whatsa", "mohamed sal", "abdel rah", "nour el", "telegra", "spotif",
        "bitward", "termu", "simple not", "quick sca", "pro vau", "فاطم", "عبدالعز", "youssef", "alvarez jo", "lefevr"
    )

    private fun engine(prefilter: Boolean): SearchEngine =
        SearchEngine(prefilter = prefilter).also { it.replaceItems(items) }

    private fun runAll(engine: SearchEngine): Long {
        val start = System.nanoTime()
        var sink = 0
        for (query in queries) sink += engine.search(query, 8).size
        if (sink < 0) throw IllegalStateException()
        return System.nanoTime() - start
    }

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    @Test
    fun theTypingScriptTakesAtMostEightTenthsOfTheTimeWithThePrefilter() {
        val on = engine(true)
        val off = engine(false)
        repeat(5) {
            runAll(off)
            runAll(on)
        }
        val onTimes = ArrayList<Long>()
        val offTimes = ArrayList<Long>()
        repeat(5) {
            offTimes.add(runAll(off))
            onTimes.add(runAll(on))
        }
        val onMedian = median(onTimes)
        val offMedian = median(offTimes)
        println("TACIT_SEARCH_PREFILTER_JVM on_ms=${"%.2f".format(onMedian / 1_000_000.0)} off_ms=${"%.2f".format(offMedian / 1_000_000.0)} queries=${queries.size} items=${items.size}")
        assertTrue(
            "with the prefilter ${onMedian / 1000} us against ${offMedian / 1000} us without, for ${queries.size} queries",
            onMedian * 10 <= offMedian * 8
        )
    }
}
