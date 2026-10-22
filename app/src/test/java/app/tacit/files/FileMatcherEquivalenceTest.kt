package app.tacit.files

import app.tacit.fixtures.FileFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileMatcherEquivalenceTest {

    private companion object {
        val table = FileFixtures.table(FileFixtures.NOW, 300_000)
        val queries = FileQueryFixtures.queries(300)
    }

    private fun describe(hit: FileHit): String =
        "${hit.path}|${hit.name}|${hit.size}|${hit.modified}|${hit.score}|${hit.folder}"

    private fun assertSame(query: String, limit: Int) {
        val expected = LegacyFileMatcher.search(table, query, limit, FileFixtures.NOW) { false }.map(::describe)
        val actual = FileMatcher.search(table, query, limit, FileFixtures.NOW) { false }.map(::describe)
        assertEquals("query '$query' limit $limit", expected, actual)
    }

    @Test
    fun threeHundredQueriesGiveTheSameTopTen() {
        for (query in queries) assertSame(query, 10)
    }

    @Test
    fun theSameHoldsForOtherLimits() {
        for (query in queries.filterIndexed { index, _ -> index % 5 == 0 }) {
            assertSame(query, 1)
            assertSame(query, 25)
        }
    }

    @Test
    fun theQuerySetFindsRealMatches() {
        val withHits = queries.count { FileMatcher.search(table, it, 10, FileFixtures.NOW) { false }.isNotEmpty() }
        assertTrue("only $withHits of ${queries.size} queries had hits", withHits > queries.size * 3 / 5)
    }

    @Test
    fun emptyAndBlankQueriesStayEmpty() {
        for (query in listOf("", " ", "in:", "ext:", "in:download")) {
            assertEquals(query, LegacyFileMatcher.search(table, query, 10, FileFixtures.NOW) { false }.size,
                FileMatcher.search(table, query, 10, FileFixtures.NOW) { false }.size)
        }
    }

    @Test
    fun cancellingStopsTheSearch() {
        assertTrue(FileMatcher.search(table, "report", 10, FileFixtures.NOW) { true }.isEmpty())
    }
}
