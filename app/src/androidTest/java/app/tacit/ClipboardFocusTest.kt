package app.tacit

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.tacit.ui.SearchActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardFocusTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun setUp() {
        Graph.prefs.clipboardEnabled = true
        Graph.prefs.clipBiometric = false
        Graph.prefs.sensitivePolicy = "skip"
        Graph.prefs.clipLastTimestamp = 0L
        Graph.prefs.contactsEnabled = false
        Graph.clipboard.clear()
    }

    @After
    fun tearDown() {
        Graph.clipboard.clear()
        Graph.prefs.clipboardEnabled = false
        Graph.prefs.clipLastTimestamp = 0L
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    @Test
    fun clipCopiedWhileAwayIsSavedWhenTacitOpens() {
        val marker = "tacit-marker-" + System.currentTimeMillis()
        ActivityScenario.launch(SearchActivity::class.java).use {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("test", marker))
            }
            val description = context.getSystemService(ClipboardManager::class.java).primaryClipDescription
            assertTrue("test could not place a clip", description != null && description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))
            Graph.clipboard.clear()
            Graph.prefs.clipLastTimestamp = 0L
            device.pressHome()
            device.waitForIdle()
            context.startActivity(Intent(context, SearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            device.wait(Until.hasObject(By.res(context.packageName, "search_input")), 8000)
            val saved = waitFor(8000) { Graph.clipboard.all().any { it.text == marker } }
            assertTrue("clip copied while away was not saved on return; saved=${Graph.clipboard.all().map { it.text }}", saved)
            assertEquals(1, Graph.clipboard.all().count { it.text == marker })
        }
    }
}
