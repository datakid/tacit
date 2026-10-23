package app.tacit

import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.files.FileHit
import app.tacit.files.FileIndex
import app.tacit.files.IndexFormat
import app.tacit.files.IndexHeader
import app.tacit.files.ScanState
import app.tacit.files.ScanStrategyIds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FileIndexTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val folder = File(Environment.getExternalStorageDirectory(), "TacitIndexTest")
    private var savedEnabled = false
    private var savedHidden = false

    @Before
    fun setUp() {
        savedEnabled = Graph.prefs.filesEnabled
        savedHidden = Graph.prefs.filesHidden
        shell("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        assertTrue("All files access was not granted", Environment.isExternalStorageManager())
        folder.deleteRecursively()
        File(folder, "Quarterly").mkdirs()
        File(folder, "Quarterly/quarterly-report-2026.txt").writeText("one")
        File(folder, "Quarterly/budget-sheet.txt").writeText("two")
        File(folder, ".secret").mkdirs()
        File(folder, ".secret/hidden-plan.txt").writeText("three")
        File(folder, "ملف-الميزانية.txt").writeText("four")
        Graph.prefs.filesEnabled = true
        Graph.prefs.filesHidden = false
        Graph.files.clear()
    }

    @After
    fun tearDown() {
        Graph.prefs.filesEnabled = false
        Graph.files.clear()
        finish(Graph.files)
        Graph.prefs.filesEnabled = savedEnabled
        Graph.prefs.filesHidden = savedHidden
        folder.deleteRecursively()
    }

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun finish(index: FileIndex) {
        val done = CountDownLatch(1)
        index.rescan { done.countDown() }
        assertTrue("the scan thread did not answer", done.await(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    private fun search(index: FileIndex, query: String): List<FileHit> {
        val found = CountDownLatch(1)
        var hits: List<FileHit> = emptyList()
        index.search(query, 10) {
            hits = it
            found.countDown()
        }
        assertTrue("the search did not answer", found.await(WAIT_SECONDS, TimeUnit.SECONDS))
        return hits
    }

    private fun names(hits: List<FileHit>): List<String> = hits.map { it.name }

    private fun storedHeader(): IndexHeader =
        DataInputStream(File(context.filesDir, "files.bin").inputStream().buffered()).use { IndexFormat.readHeader(it) }!!

    @Test
    fun theFirstScanShowsMediaStoreThenWalksAndARescanWalksAgain() {
        finish(Graph.files)
        val first = storedHeader()
        assertEquals(ScanStrategyIds.HYBRID, first.strategy)
        assertTrue(first.verifiedAt > 0L)
        assertTrue(first.volumes.isNotEmpty())
        finish(Graph.files)
        val second = storedHeader()
        assertEquals(ScanStrategyIds.NIO_WALK, second.strategy)
        assertTrue(second.verifiedAt >= first.verifiedAt)
    }

    @Test
    fun aRescanIndexesTheFilesAndSearchFindsThem() {
        finish(Graph.files)
        val state = Graph.files.state
        assertTrue("state was $state", state is ScanState.Done)
        assertTrue((state as ScanState.Done).count >= 4)
        assertEquals(state.count, Graph.files.count())
        assertTrue(names(search(Graph.files, "quarterly report")).contains("quarterly-report-2026.txt"))
        assertTrue(names(search(Graph.files, "الميزانية")).contains("ملف-الميزانية.txt"))
        assertTrue(names(search(Graph.files, "quarterly")).contains("Quarterly"))
    }

    @Test
    fun hiddenFilesAreLeftOutUnlessTheSettingIsOn() {
        finish(Graph.files)
        assertFalse(names(search(Graph.files, "hidden plan")).contains("hidden-plan.txt"))
        Graph.prefs.filesHidden = true
        finish(Graph.files)
        assertTrue(names(search(Graph.files, "hidden plan")).contains("hidden-plan.txt"))
    }

    @Test
    fun aFreshInstanceReadsTheCountFromTheHeaderAndTheEntriesFromDisk() {
        finish(Graph.files)
        val expected = (Graph.files.state as ScanState.Done).count
        val fresh = FileIndex(context, Graph.prefs)
        val summary = CountDownLatch(1)
        instrumentation.runOnMainSync {
            fresh.addStateListener { if (it is ScanState.Done) summary.countDown() }
        }
        fresh.summarize()
        assertTrue("no summary arrived", summary.await(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(expected, (fresh.state as ScanState.Done).count)
        assertEquals(expected, fresh.count())
        assertTrue(names(search(fresh, "budget sheet")).contains("budget-sheet.txt"))
    }

    @Test
    fun clearEmptiesTheIndexAndRemovesTheFile() {
        finish(Graph.files)
        assertTrue(Graph.files.count() > 0)
        Graph.prefs.filesEnabled = false
        Graph.files.clear()
        assertEquals(0, Graph.files.count())
        assertEquals(ScanState.Idle, Graph.files.state)
        finish(Graph.files)
        assertFalse(File(context.filesDir, "files.bin").exists())
        val fresh = FileIndex(context, Graph.prefs)
        assertTrue(search(fresh, "quarterly report").isEmpty())
    }

    @Test
    fun listenersHearTheScanEnd() {
        val seen = ArrayList<ScanState>()
        val listener: (ScanState) -> Unit = { seen.add(it) }
        instrumentation.runOnMainSync { Graph.files.addStateListener(listener) }
        try {
            finish(Graph.files)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { }
        } finally {
            instrumentation.runOnMainSync { Graph.files.removeStateListener(listener) }
        }
        assertTrue("no state was delivered", seen.isNotEmpty())
        assertTrue("last state was ${seen.last()}", seen.last() is ScanState.Done)
    }

    @Test
    fun aBurstOfRescansEndsWithOneConsistentIndex() {
        val finished = CountDownLatch(5)
        repeat(5) { Graph.files.rescan { finished.countDown() } }
        assertTrue("callbacks were lost", finished.await(WAIT_SECONDS, TimeUnit.SECONDS))
        assertFalse(Graph.files.busy)
        assertTrue(Graph.files.state is ScanState.Done)
        assertTrue(names(search(Graph.files, "budget sheet")).contains("budget-sheet.txt"))
    }

    private companion object {
        const val WAIT_SECONDS = 90L
    }
}
