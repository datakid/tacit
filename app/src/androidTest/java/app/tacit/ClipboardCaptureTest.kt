package app.tacit

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.clip.ClipReader
import app.tacit.clip.ClipSnapshot
import app.tacit.clip.ClipboardStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardCaptureTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: ClipboardStore

    @Before
    fun setUp() {
        Graph.prefs.clipboardEnabled = true
        Graph.prefs.sensitivePolicy = "skip"
        Graph.prefs.clipLastTimestamp = 0L
        store = ClipboardStore(context, Graph.prefs)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
        Graph.prefs.clipboardEnabled = false
        Graph.prefs.clipLastTimestamp = 0L
    }

    @Test
    fun clipDeniedFirstThenGrantedIsCaptured() {
        var granted = false
        val reader = ClipReader { ClipSnapshot(1000L, false) { if (granted) "hello world" else null } }
        assertFalse(store.captureIfNew(reader))
        granted = true
        assertTrue("clip stayed uncaptured after the read became possible", store.captureIfNew(reader))
        assertEquals("hello world", store.all().first().text)
    }

    @Test
    fun sameClipIsCapturedOnce() {
        val reader = ClipReader { ClipSnapshot(2000L, false) { "same text" } }
        assertTrue(store.captureIfNew(reader))
        assertFalse(store.captureIfNew(reader))
        assertEquals(1, store.all().size)
    }

    @Test
    fun newStampWithSameTextKeepsOneEntry() {
        assertTrue(store.captureIfNew(ClipReader { ClipSnapshot(3000L, false) { "again" } }))
        assertTrue(store.captureIfNew(ClipReader { ClipSnapshot(3001L, false) { "again" } }))
        assertEquals(1, store.all().size)
    }

    @Test
    fun disabledCapturesNothing() {
        Graph.prefs.clipboardEnabled = false
        assertFalse(store.captureIfNew(ClipReader { ClipSnapshot(4000L, false) { "ignored" } }))
        assertTrue(store.all().isEmpty())
    }
}
