package app.tacit

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.tacit.ui.SearchActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FirstRunTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val marker = File(context.filesDir, Welcome.MARKER)
    private var scenario: ActivityScenario<SearchActivity>? = null

    @Before
    fun setUp() {
        syncWorker()
        Graph.prefs.store.edit().clear().commit()
        Graph.prefs.barAtBottom = false
        marker.delete()
        Welcome.forgetForTesting()
    }

    @After
    fun tearDown() {
        scenario?.close()
        syncWorker()
        marker.createNewFile()
        Welcome.forgetForTesting()
        Graph.prefs.store.edit().clear().commit()
    }

    private fun syncWorker() {
        val latch = CountDownLatch(1)
        Graph.worker.post { latch.countDown() }
        assertTrue("worker did not respond", latch.await(5, TimeUnit.SECONDS))
    }

    private fun launchFresh() {
        scenario?.close()
        scenario = null
        syncWorker()
        marker.delete()
        Welcome.forgetForTesting()
        launch()
    }

    private fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(SearchActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    private fun card(): View? {
        var found: View? = null
        scenario!!.onActivity { found = it.findViewById(R.id.welcome_card) }
        return found
    }

    private fun cardShown(): Boolean {
        var shown = false
        scenario!!.onActivity { shown = it.findViewById<View>(R.id.welcome_card)?.visibility == View.VISIBLE }
        return shown
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(message, condition())
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == context.packageName
    }

    @Test
    fun cardShowsOnTheFirstLaunch() {
        launch()
        assertTrue("card missing", cardShown())
        assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.welcome_title))), 5000))
        assertNotNull(device.findObject(By.text(context.getString(R.string.welcome_ok))))
    }

    @Test
    fun cardDoesNotShowWhenTheMarkerExists() {
        marker.createNewFile()
        Welcome.forgetForTesting()
        launch()
        assertNull(card())
        assertFalse(device.hasObject(By.text(context.getString(R.string.welcome_title))))
    }

    @Test
    fun gotItHidesTheCardAndRemembersIt() {
        launch()
        val button = device.wait(Until.findObject(By.text(context.getString(R.string.welcome_ok))), 5000)
        assertNotNull("button missing", button)
        button.click()
        waitFor("card stayed") { !cardShown() }
        waitFor("marker not written") { marker.exists() }
        Welcome.forgetForTesting()
        launch()
        assertNull(card())
    }

    @Test
    fun typingHidesTheCardAndRemembersIt() {
        launch()
        assertTrue(cardShown())
        scenario!!.onActivity { it.findViewById<EditText>(R.id.search_input).setText("a") }
        instrumentation.waitForIdleSync()
        waitFor("card stayed") { !cardShown() }
        waitFor("marker not written") { marker.exists() }
    }

    @Test
    fun leavingTheScreenCountsAsSeen() {
        launch()
        assertTrue(cardShown())
        scenario!!.close()
        scenario = null
        waitFor("marker not written") { marker.exists() }
        Welcome.forgetForTesting()
        launch()
        assertNull(card())
    }

    @Test
    fun cardSitsInsideTheListAreaAndLeavesTheSearchBarFree() {
        for (bottom in listOf(false, true)) {
            Graph.prefs.barAtBottom = bottom
            launchFresh()
            assertTrue("card missing, bar at bottom=$bottom", cardShown())
            val cardBounds = Rect()
            val inputBounds = Rect()
            scenario!!.onActivity {
                assertTrue(it.findViewById<View>(R.id.welcome_card).getGlobalVisibleRect(cardBounds))
                assertTrue(it.findViewById<View>(R.id.search_input).getGlobalVisibleRect(inputBounds))
            }
            assertFalse("card overlaps the search field, bar at bottom=$bottom", Rect.intersects(cardBounds, inputBounds))
            assertTrue(inputBounds.height() > 0)
        }
    }

    @Test
    fun homeButtonIsOfferedOnlyWhenTacitIsNotTheHomeApp() {
        launch()
        var present = false
        scenario!!.onActivity { present = it.findViewById<View>(R.id.welcome_home) != null }
        assertEquals(!isDefaultHome(), present)
    }

    @Test
    fun homeButtonOpensHomeSettingsAndHidesTheCard() {
        if (isDefaultHome()) return
        Intents.init()
        try {
            intending(ExternalIntents.matcher()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            launch()
            val button = device.wait(Until.findObject(By.text(context.getString(R.string.welcome_home))), 5000)
            assertNotNull("home button missing", button)
            button.click()
            intended(hasAction(android.provider.Settings.ACTION_HOME_SETTINGS))
            waitFor("card stayed") { !cardShown() }
            waitFor("marker not written") { marker.exists() }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun cardStaysGoneAfterTheScreenIsRecreated() {
        launch()
        assertTrue(cardShown())
        scenario!!.onActivity { it.findViewById<EditText>(R.id.search_input).setText("a") }
        waitFor("marker not written") { marker.exists() }
        scenario!!.recreate()
        instrumentation.waitForIdleSync()
        assertNull(card())
    }
}
