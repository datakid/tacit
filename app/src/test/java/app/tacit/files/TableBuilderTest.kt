package app.tacit.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableBuilderTest {

    private fun fill(builder: TableBuilder, count: Int): Int {
        val parent = builder.directory("/root")
        var added = 0
        for (i in 0 until count) {
            added++
            if (!builder.add("File $i.txt", parent, i.toLong(), i * 10L)) break
        }
        return added
    }

    @Test
    fun theSameDirectoryPathGivesTheSameIndex() {
        val builder = TableBuilder()
        val first = builder.directory("/a/b")
        val second = builder.directory("/a/c")
        assertEquals(first, builder.directory("/a/b"))
        assertEquals(1, second)
    }

    @Test
    fun theSnapshotMatchesAnIndependentlyBuiltTable() {
        val builder = TableBuilder()
        fill(builder, 3000)
        val snapshot = builder.snapshot()
        val names = Array(3000) { "File $it.txt" }
        val expected = FileTable(arrayOf("/root"), names, Array(3000) { names[it].lowercase() }, IntArray(3000),
            LongArray(3000) { it.toLong() }, LongArray(3000) { it * 10L })
        assertArrayEquals(expected.names, snapshot.names)
        assertArrayEquals(expected.normalized, snapshot.normalized)
        assertArrayEquals(expected.sizes, snapshot.sizes)
        assertArrayEquals(expected.modified, snapshot.modified)
        assertArrayEquals(expected.masks, snapshot.masks)
        assertArrayEquals(expected.directories, snapshot.directories)
    }

    @Test
    fun aSnapshotIsNotChangedByEntriesAddedAfterIt() {
        val builder = TableBuilder()
        val parent = builder.directory("/root")
        builder.add("one.txt", parent, 1L, 1L)
        val snapshot = builder.snapshot()
        builder.add("two.txt", parent, 2L, 2L)
        assertEquals(1, snapshot.count)
        assertEquals(2, builder.snapshot().count)
    }

    @Test
    fun reachingTheLimitStopsTheScanAndMarksItTruncated() {
        val builder = TableBuilder(limit = 2500)
        val added = fill(builder, 10_000)
        assertEquals(2500, added)
        assertEquals(2500, builder.count)
        assertTrue(builder.truncated)
        assertFalse(builder.accepting)
    }

    @Test
    fun aScanBelowTheLimitIsNotTruncated() {
        val builder = TableBuilder(limit = 2500)
        fill(builder, 100)
        assertFalse(builder.truncated)
        assertTrue(builder.accepting)
    }

    @Test
    fun aSupersededScanIsStoppedWithinOneThousandAndTwentyFourEntries() {
        var current = true
        val builder = TableBuilder(current = { current })
        val parent = builder.directory("/root")
        for (i in 0 until 500) assertTrue(builder.add("a$i", parent, 0L, 0L))
        current = false
        var extra = 0
        while (builder.add("b$extra", parent, 0L, 0L)) extra++
        assertTrue("stopped after $extra", extra < 1024)
        assertFalse(builder.accepting)
    }

    @Test
    fun progressIsReportedEveryThousandAndTwentyFourEntries() {
        val seen = ArrayList<Int>()
        val builder = TableBuilder(onProgress = { seen.add(it.count) })
        fill(builder, 3500)
        assertEquals(listOf(1024, 2048, 3072), seen)
    }

    @Test
    fun treeNamesKeepTheirDocumentUriAndNormalizeOnTheDisplayName() {
        val builder = TableBuilder()
        val parent = builder.directory("tree:x")
        builder.add(TreeNames.stored("Plan.PDF", "content://x/1"), parent, 5L, 6L)
        val table = builder.snapshot()
        assertEquals("plan.pdf", table.normalized[0])
        assertEquals("content://x/1", table.pathOf(0))
    }
}
