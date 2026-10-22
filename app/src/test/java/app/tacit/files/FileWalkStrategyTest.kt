package app.tacit.files

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileWalkStrategyTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("tacit-walk").toFile()
        write("Download/report.pdf", 10)
        write("Download/sub/notes.txt", 5)
        write("Download/sub/deeper/ملف.txt", 3)
        write(".hidden/secret.txt", 1)
        write(".dot.txt", 1)
        write("Android/data/app/x.bin", 1)
        write("Android/obb/y.obb", 1)
        write("Android/media/z.jpg", 2)
        File(root, "empty").mkdirs()
        File(root, "Download/report.pdf").setLastModified(1_700_000_000_000L)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(path: String, size: Int) {
        val file = File(root, path)
        file.parentFile.mkdirs()
        file.writeBytes(ByteArray(size))
    }

    private fun scan(strategy: ScanStrategy, builder: TableBuilder = TableBuilder()): FileTable {
        strategy.scan(builder)
        return builder.snapshot()
    }

    private fun entries(table: FileTable): Set<String> =
        (0 until table.count).map { "${table.pathOf(it)}|${table.sizes[it]}|${table.modified[it]}" }.toSet()

    @Test
    fun bothWalkersFindTheSameEntriesWithTheSameSizesAndTimes() {
        val files = entries(scan(FileWalkStrategy(listOf(root), false)))
        val nio = entries(scan(NioWalkStrategy(listOf(root), false)))
        assertEquals(files, nio)
        assertTrue(files.any { it.startsWith("$root/Download/report.pdf|10|1700000000000") })
    }

    @Test
    fun hiddenEntriesAreSkippedUnlessAsked() {
        for (factory in listOf<(Boolean) -> ScanStrategy>({ FileWalkStrategy(listOf(root), it) }, { NioWalkStrategy(listOf(root), it) })) {
            val without = entries(scan(factory(false))).map { it.substringBefore('|') }
            assertFalse(without.any { it.contains("/.hidden") || it.endsWith("/.dot.txt") })
            val with = entries(scan(factory(true))).map { it.substringBefore('|') }
            assertTrue(with.contains("$root/.hidden/secret.txt"))
            assertTrue(with.contains("$root/.dot.txt"))
        }
    }

    @Test
    fun androidDataAndObbAreLeftOutEntirelyButMediaIsKept() {
        for (strategy in listOf(FileWalkStrategy(listOf(root), false), NioWalkStrategy(listOf(root), false))) {
            val paths = entries(scan(strategy)).map { it.substringBefore('|') }
            assertFalse(paths.any { it.contains("/Android/data/") || it.contains("/Android/obb/") })
            assertTrue(paths.contains("$root/Android/media/z.jpg"))
            assertFalse(paths.contains("$root/Android/data/"))
            assertTrue(paths.contains("$root/Android/"))
        }
    }

    @Test
    fun foldersAreStoredWithATrailingSlashAndEmptyFoldersAreListed() {
        for (strategy in listOf(FileWalkStrategy(listOf(root), false), NioWalkStrategy(listOf(root), false))) {
            val paths = entries(scan(strategy)).map { it.substringBefore('|') }
            assertTrue(paths.contains("$root/empty/"))
            assertTrue(paths.contains("$root/Download/sub/deeper/"))
            assertTrue(paths.contains("$root/Download/sub/deeper/ملف.txt"))
        }
    }

    @Test
    fun theLimitStopsTheWalkAndMarksTheTableTruncated() {
        for (strategy in listOf(FileWalkStrategy(listOf(root), false), NioWalkStrategy(listOf(root), false))) {
            val builder = TableBuilder(limit = 4)
            val table = scan(strategy, builder)
            assertEquals(4, table.count)
            assertTrue(builder.truncated)
        }
    }

    @Test
    fun aSupersededScanStopsWithoutWalkingTheRest() {
        for (strategy in listOf(FileWalkStrategy(listOf(root), false), NioWalkStrategy(listOf(root), false))) {
            val builder = TableBuilder(current = { false })
            strategy.scan(builder)
            assertTrue(builder.count <= 1024)
        }
        val stopped = TableBuilder(limit = 1)
        stopped.add("x", stopped.directory("/a"), 0L, 0L)
        val table = scan(NioWalkStrategy(listOf(root), false), stopped)
        assertEquals(1, table.count)
    }

    @Test
    fun theNioWalkerSkipsSymbolicLinks() {
        val target = File(root, "Download")
        val fileLink = File(root, "link-to-report.pdf").toPath()
        val dirLink = File(root, "link-to-download").toPath()
        try {
            Files.createSymbolicLink(fileLink, File(root, "Download/report.pdf").toPath())
            Files.createSymbolicLink(dirLink, target.toPath())
        } catch (error: UnsupportedOperationException) {
            return
        }
        val paths = entries(scan(NioWalkStrategy(listOf(root), false))).map { it.substringBefore('|') }
        assertFalse(paths.any { it.contains("link-to-") })
        assertEquals(1, paths.count { it.endsWith("/report.pdf") })
    }

    @Test
    fun anUnreadableOrMissingRootIsSkipped() {
        val missing = File(root, "nope")
        for (strategy in listOf(FileWalkStrategy(listOf(missing), false), NioWalkStrategy(listOf(missing), false))) {
            assertEquals(0, scan(strategy).count)
        }
    }

    @Test
    fun severalRootsAreWalkedInOrder() {
        val other = Files.createTempDirectory("tacit-walk-other").toFile()
        try {
            File(other, "second.txt").writeText("x")
            for (strategy in listOf(FileWalkStrategy(listOf(root, other), false), NioWalkStrategy(listOf(root, other), false))) {
                val paths = entries(scan(strategy)).map { it.substringBefore('|') }
                assertTrue(paths.contains("$root/Download/report.pdf"))
                assertTrue(paths.contains("$other/second.txt"))
            }
        } finally {
            other.deleteRecursively()
        }
    }
}
