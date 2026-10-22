package app.tacit.files

import app.tacit.fixtures.FileFixtures
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchSpeedTest {

    private val entries = 200_000
    private val table = FileFixtures.table(FileFixtures.NOW, entries)
    private val queries = FileQueryFixtures.queries(60, seed = 11L)

    private fun runAll(search: (String) -> List<FileHit>): Long {
        val start = System.nanoTime()
        var sink = 0
        for (query in queries) sink += search(query).size
        if (sink < 0) throw IllegalStateException()
        return System.nanoTime() - start
    }

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    @Test
    fun theNewMatcherTakesAtMostSixTenthsOfTheLegacyTime() {
        val legacy = { query: String -> LegacyFileMatcher.search(table, query, 10, FileFixtures.NOW) { false } }
        val current = { query: String -> FileMatcher.search(table, query, 10, FileFixtures.NOW) { false } }
        repeat(2) {
            runAll(legacy)
            runAll(current)
        }
        val legacyTimes = ArrayList<Long>()
        val currentTimes = ArrayList<Long>()
        repeat(5) {
            legacyTimes.add(runAll(legacy))
            currentTimes.add(runAll(current))
        }
        val legacyMedian = median(legacyTimes)
        val currentMedian = median(currentTimes)

        val perQuery = ArrayList<Long>()
        repeat(5) {
            for (query in queries) {
                val start = System.nanoTime()
                current(query)
                perQuery.add(System.nanoTime() - start)
            }
        }
        perQuery.sort()
        val p50 = perQuery[perQuery.size / 2] / 1000
        val p95 = perQuery[perQuery.size * 95 / 100] / 1000
        println("TACIT_FILESEARCH_JVM p50=${p50}us p95=${p95}us entries=$entries legacy_ms=${legacyMedian / 1_000_000} new_ms=${currentMedian / 1_000_000}")

        assertTrue(
            "new ${currentMedian / 1_000_000} ms against legacy ${legacyMedian / 1_000_000} ms for ${queries.size} queries",
            currentMedian * 10 <= legacyMedian * 6
        )
    }
}
