package app.tacit

import android.os.Environment
import android.os.ParcelFileDescriptor
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.files.FileTable
import app.tacit.files.FileWalkStrategy
import app.tacit.files.MediaStoreStrategy
import app.tacit.files.NioWalkStrategy
import app.tacit.files.ScanStrategy
import app.tacit.files.TableBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FileScanBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val storage = Environment.getExternalStorageDirectory()
    private val bench = File(storage, "TacitBench")
    private val prefix = bench.absolutePath + "/"

    @Before
    fun setUp() {
        shell("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        assertTrue("All files access was not granted", Environment.isExternalStorageManager())
        bench.deleteRecursively()
        val seeded = time { seed() }
        val scanned = time { scanMediaStore() }
        report("seed files=${TOP * SUBS * PER_SUB + NO_MEDIA + HIDDEN} folders=${TOP * SUBS} seed_ms=$seeded mediastore_scan_ms=$scanned")
    }

    @After
    fun tearDown() {
        val cleaned = time { bench.deleteRecursively() }
        report("cleaned ms=$cleaned")
    }

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun scanMediaStore() {
        val done = CountDownLatch(1)
        MediaScannerConnection.scanFile(context, arrayOf(bench.absolutePath), null) { _, _ -> done.countDown() }
        assertTrue("MediaStore did not finish scanning the benchmark tree", done.await(SCAN_WAIT_SECONDS, TimeUnit.SECONDS))
    }

    private fun time(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun report(message: String) {
        Log.i("TACIT_FILESCAN", message)
    }

    private fun write(directory: File, name: String) {
        File(directory, name).writeBytes(byteArrayOf(1))
    }

    private fun seed() {
        (0 until TOP).toList().parallelStream().forEach { top ->
            for (sub in 0 until SUBS) {
                val directory = File(bench, "d$top/s$sub")
                directory.mkdirs()
                for (n in 0 until PER_SUB) write(directory, "report-$top-$sub-$n.txt")
            }
        }
        val noMedia = File(bench, "NoMedia")
        noMedia.mkdirs()
        write(noMedia, ".nomedia")
        for (n in 0 until NO_MEDIA) write(noMedia, "nm-$n.txt")
        val hidden = File(bench, ".cache")
        hidden.mkdirs()
        for (n in 0 until HIDDEN) write(hidden, "hidden-$n.txt")
    }

    private fun createFresh() {
        val fresh = File(bench, "Fresh")
        fresh.mkdirs()
        for (n in 0 until FRESH) write(fresh, "fresh-$n.txt")
    }

    private fun run(strategy: ScanStrategy): Pair<Long, FileTable> {
        val builder = TableBuilder()
        val start = System.nanoTime()
        strategy.scan(builder)
        val table = builder.snapshot()
        return (System.nanoTime() - start) / 1_000_000 to table
    }

    private fun benchPaths(table: FileTable): Set<String> {
        val paths = HashSet<String>()
        for (i in 0 until table.count) {
            val path = table.pathOf(i)
            if (path.startsWith(prefix)) paths.add(path)
        }
        return paths
    }

    private fun hiddenCount(table: FileTable): Int =
        benchPaths(table).count { it.contains("/.cache/") && !it.endsWith("/") }

    private fun category(path: String): String = when {
        path.contains("/NoMedia/") -> "nomedia"
        path.contains("/Fresh/") -> "fresh"
        path.endsWith("/") -> "folders"
        else -> "other"
    }

    private fun summarize(label: String, missing: Set<String>) {
        val counts = missing.groupingBy { category(it) }.eachCount().toSortedMap()
        report("diff $label count=${missing.size} categories=$counts sample=${missing.sorted().take(3)}")
    }

    @Test
    fun theWalkersAgreeAndMediaStoreIsMeasuredAgainstThem() {
        createFresh()
        val immediate = run(MediaStoreStrategy(context, false)).second
        val freshSeen = benchPaths(immediate).count { it.contains("/Fresh/") && !it.endsWith("/") }
        report("fresh created=$FRESH seen_by_mediastore_immediately=$freshSeen")

        val roots = listOf(storage)
        val timings = HashMap<String, ArrayList<Long>>()
        val tables = HashMap<String, FileTable>()
        repeat(ROUNDS) {
            for ((name, strategy) in listOf<Pair<String, ScanStrategy>>(
                "file" to FileWalkStrategy(roots, false),
                "nio" to NioWalkStrategy(roots, false),
                "mediastore" to MediaStoreStrategy(context, false)
            )) {
                val (millis, table) = run(strategy)
                timings.getOrPut(name) { ArrayList() }.add(millis)
                tables[name] = table
            }
        }
        for ((name, values) in timings) {
            report("strategy=$name ms=${values.joinToString(",")} best=${values.min()} entries=${tables.getValue(name).count} bench=${benchPaths(tables.getValue(name)).size}")
        }

        val file = benchPaths(tables.getValue("file"))
        val nio = benchPaths(tables.getValue("nio"))
        val media = benchPaths(tables.getValue("mediastore"))
        summarize("nio_not_in_mediastore", nio - media)
        summarize("mediastore_not_in_nio", media - nio)
        summarize("file_not_in_nio", file - nio)
        summarize("nio_not_in_file", nio - file)

        val hiddenNio = run(NioWalkStrategy(roots, true)).second
        val hiddenMedia = run(MediaStoreStrategy(context, true)).second
        report("hidden files nio=${hiddenCount(hiddenNio)} mediastore=${hiddenCount(hiddenMedia)} expected=$HIDDEN")
        report("rows ${describeRows()}")
        assertEquals("the NIO walker must find exactly what the File walker finds", file, nio)
        report("finished")
    }

    private fun describeRows(): String {
        val projection = arrayOf(
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.SIZE
        )
        val names = arrayOf("d0", "NoMedia", "report-0-0-0.txt", "fresh-0.txt", ".nomedia")
        val rows = ArrayList<String>()
        for (name in names) {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external_primary"), projection,
                MediaStore.MediaColumns.DISPLAY_NAME + " = ?", arrayOf(name), null
            )?.use {
                while (it.moveToNext() && rows.size < 8) {
                    rows.add("${it.getString(0)}|${it.getString(1)}|mime=${it.getString(2)}|type=${it.getString(3)}|size=${it.getString(4)}")
                }
            }
        }
        return rows.joinToString(" ; ")
    }

    private companion object {
        const val TOP = 30
        const val SUBS = 100
        const val PER_SUB = 10
        const val NO_MEDIA = 200
        const val HIDDEN = 100
        const val FRESH = 50
        const val ROUNDS = 3
        const val SCAN_WAIT_SECONDS = 150L
    }
}
