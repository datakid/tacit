package app.tacit.files

import app.tacit.core.Normalizer
import app.tacit.fixtures.FileFixtures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class FileIndexMigrationTest {

    private val treeRoot = "tree:content://com.android.externalstorage.documents/tree/primary%3ADocs/document/primary%3ADocs"
    private val treeFile = "content://com.android.externalstorage.documents/tree/primary%3ADocs/document/primary%3ADocs%2Fa%20plan.pdf"
    private val treeFolder = "content://com.android.externalstorage.documents/tree/primary%3ADocs/document/primary%3ADocs%2FSub"

    private fun legacyBytes(directories: List<String>, names: List<String>, parents: IntArray, sizes: LongArray, modified: LongArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(directories.size)
            for (directory in directories) out.writeUTF(directory)
            out.writeInt(names.size)
            for (i in names.indices) {
                out.writeUTF(names[i])
                out.writeInt(parents[i])
                out.writeLong(sizes[i])
                out.writeLong(modified[i])
            }
        }
        return bytes.toByteArray()
    }

    private fun tableOf(directories: List<String>, names: List<String>, parents: IntArray, sizes: LongArray, modified: LongArray): FileTable =
        FileTable(directories.toTypedArray(), names.toTypedArray(), Array(names.size) { TreeNames.normalizedName(names[it]) }, parents, sizes, modified)

    private fun encode(table: FileTable, header: IndexHeader): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { IndexFormat.write(it, table, header) }
        return bytes.toByteArray()
    }

    private fun decode(bytes: ByteArray): LoadedIndex? = DataInputStream(ByteArrayInputStream(bytes)).use { IndexFormat.read(it) }

    private fun assertSameTable(expected: FileTable, actual: FileTable) {
        assertArrayEquals(expected.directories, actual.directories)
        assertArrayEquals(expected.names, actual.names)
        assertArrayEquals(expected.normalized, actual.normalized)
        assertArrayEquals(expected.parent, actual.parent)
        assertArrayEquals(expected.sizes, actual.sizes)
        assertArrayEquals(expected.modified, actual.modified)
        assertArrayEquals(expected.masks, actual.masks)
    }

    private val header = IndexHeader(0, 1_790_000_000_000L, 1_789_000_000_000L, ScanStrategyIds.HYBRID, true,
        listOf(VolumeStamp("external_primary", "Android-37.0", 4242L), VolumeStamp("1A2B-3C4D", "Android-37.0", 7L)))

    @Test
    fun aVersionOneFileBecomesACorrectTableAndIsMarkedLegacy() {
        val directories = listOf("/storage/emulated/0/Download", "/storage/emulated/0/Documents/Sub Folder")
        val names = listOf("Report Final.PDF", "Photos/", "تقرير الميزانية.docx", "notes.txt")
        val parents = intArrayOf(0, 0, 1, 1)
        val sizes = longArrayOf(2048L, 0L, 99L, 7L)
        val modified = longArrayOf(1_700_000_000_000L, 1_700_000_100_000L, 1_700_000_200_000L, 1_700_000_300_000L)

        val loaded = decode(legacyBytes(directories, names, parents, sizes, modified))!!

        assertTrue(loaded.legacy)
        assertNull(loaded.header)
        assertSameTable(tableOf(directories, names, parents, sizes, modified), loaded.table)
        assertEquals("/storage/emulated/0/Download/Report Final.PDF", loaded.table.pathOf(0))
        assertEquals("report final.pdf", loaded.table.normalized[0])
        assertEquals("Photos/", loaded.table.displayNameOf(1))
    }

    @Test
    fun aVersionOneFileHasNoReadableHeader() {
        val bytes = legacyBytes(listOf("/a"), listOf("x.txt"), intArrayOf(0), longArrayOf(1L), longArrayOf(2L))
        assertNull(DataInputStream(ByteArrayInputStream(bytes)).use { IndexFormat.readHeader(it) })
    }

    @Test
    fun aVersionTwoFileRoundTripsEveryFieldIncludingTreeEntriesAndArabicNames() {
        val directories = listOf("/storage/emulated/0/Download", treeRoot, "/storage/emulated/0/Documents/Sub Folder")
        val names = listOf(
            "Report Final.PDF",
            "Photos/",
            "تقرير الميزانية.docx",
            "إيصال الدفع ٢٠٢٦.pdf",
            TreeNames.stored("a plan.pdf", treeFile),
            TreeNames.stored("Sub/", treeFolder),
            "notes.txt"
        )
        val parents = intArrayOf(0, 0, 2, 2, 1, 1, 2)
        val sizes = longArrayOf(2048L, 0L, 99L, 5L, 123_456_789_012L, 0L, 7L)
        val modified = longArrayOf(1_700_000_000_000L, 1_700_000_100_000L, 1_700_000_200_000L, 1_700_000_250_000L, 1_700_000_300_000L, 0L, 1L)
        val table = tableOf(directories, names, parents, sizes, modified)

        val loaded = decode(encode(table, header))!!

        assertFalse(loaded.legacy)
        assertSameTable(table, loaded.table)
        val read = loaded.header!!
        assertEquals(table.count, read.entryCount)
        assertEquals(header.createdAt, read.createdAt)
        assertEquals(header.verifiedAt, read.verifiedAt)
        assertEquals(ScanStrategyIds.HYBRID, read.strategy)
        assertTrue(read.truncated)
        assertEquals(listOf("external_primary", "1A2B-3C4D"), read.volumes.map { it.name })
        assertEquals(listOf(4242L, 7L), read.volumes.map { it.generation })
        assertEquals(listOf("Android-37.0", "Android-37.0"), read.volumes.map { it.version })
    }

    @Test
    fun treeFilesAndTreeFoldersBothOpenThroughTheirDocumentUri() {
        val names = listOf(TreeNames.stored("a plan.pdf", treeFile), TreeNames.stored("Sub/", treeFolder))
        val table = tableOf(listOf(treeRoot), names, intArrayOf(0, 0), longArrayOf(10L, 0L), longArrayOf(1L, 2L))

        val loaded = decode(encode(table, header))!!.table

        assertEquals(treeFile, loaded.pathOf(0))
        assertEquals(treeFolder, loaded.pathOf(1))
        assertEquals("a plan.pdf", loaded.displayNameOf(0))
        assertEquals("Sub/", loaded.displayNameOf(1))
        assertEquals("~/Docs", loaded.folderOf(1))
        val hits = FileMatcher.search(loaded, "sub", 5, 1L) { false }
        assertEquals(listOf(treeFolder), hits.map { it.path })
        assertEquals(listOf("Sub"), hits.map { it.name })
    }

    @Test
    fun arabicNamesKeepTheNormalizedFormTheMatcherNeeds() {
        val names = listOf("إيصال الدفع.pdf", "مشروع ٢٠٢٦.docx", "Résumé 2026.pdf")
        val table = tableOf(listOf("/storage/emulated/0/Documents"), names, IntArray(3), longArrayOf(1L, 2L, 3L), longArrayOf(4L, 5L, 6L))

        val loaded = decode(encode(table, header))!!.table

        for (i in names.indices) assertEquals(Normalizer.normalize(names[i]), loaded.normalized[i])
        assertEquals(listOf("إيصال الدفع.pdf"), FileMatcher.search(loaded, "ايصال", 5, 9L) { false }.map { it.name })
        assertEquals(listOf("مشروع ٢٠٢٦.docx"), FileMatcher.search(loaded, "مشروع 2026", 5, 9L) { false }.map { it.name })
        assertEquals(listOf("Résumé 2026.pdf"), FileMatcher.search(loaded, "resume", 5, 9L) { false }.map { it.name })
    }

    @Test
    fun theHeaderReadsWithoutTheEntries() {
        val table = tableOf(listOf("/a"), listOf("one.txt", "two.txt"), intArrayOf(0, 0), longArrayOf(1L, 2L), longArrayOf(3L, 4L))
        val bytes = encode(table, header)

        val read = DataInputStream(ByteArrayInputStream(bytes.copyOf(130))).use { IndexFormat.readHeader(it) }

        assertNotNull(read)
        assertEquals(2, read!!.entryCount)
        assertEquals(2, read.volumes.size)
    }

    @Test
    fun aLargeFixtureRoundTrips() {
        val table = FileFixtures.table(FileFixtures.NOW, 20_000)
        assertSameTable(table, decode(encode(table, header))!!.table)
    }

    @Test
    fun anUnknownFileIsRejected() {
        assertNull(decode(byteArrayOf(0, 0, 0, 9, 1, 2, 3, 4)))
    }

    @Test
    fun aCorruptParentIndexIsRejectedBeforeItCanCrashASearch() {
        val table = tableOf(listOf("/a"), listOf("one.txt"), intArrayOf(0), longArrayOf(1L), longArrayOf(2L))
        val bytes = encode(table, header)
        val parentOffset = bytes.size - 8 - 8 - 4
        bytes[parentOffset + 3] = 5
        try {
            decode(bytes)
            fail("a parent index outside the directory list must be rejected")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("parent"))
        }
    }

    @Test
    fun aHugeEntryCountIsRejectedBeforeAnythingIsAllocated() {
        val table = tableOf(listOf("/a"), listOf("one.txt"), intArrayOf(0), longArrayOf(1L), longArrayOf(2L))
        val bytes = encode(table, header)
        bytes[8] = 0x7f
        try {
            decode(bytes)
            fail("an entry count above the limit must be rejected")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("entry count"))
        }
    }
}
