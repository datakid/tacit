package app.tacit

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextClock
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.tacit.ui.SearchActivity
import app.tacit.ui.SettingsActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SettingsApplyTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val prefs get() = Graph.prefs

    @Before
    fun setUp() {
        prefs.store.edit().clear().commit()
        Graph.applyPrefs()
    }

    @After
    fun tearDown() {
        prefs.store.edit().clear().commit()
        Graph.applyPrefs()
    }

    private fun views(activity: Activity): List<View> {
        val out = ArrayList<View>()
        fun walk(view: View) {
            out.add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(activity.window.decorView)
        return out
    }

    private fun shownClocks(activity: Activity) = views(activity).count { it is TextClock && it.isShown }

    private fun away(scenario: ActivityScenario<*>) = scenario.moveToState(Lifecycle.State.CREATED)

    private fun back(scenario: ActivityScenario<*>) = scenario.moveToState(Lifecycle.State.RESUMED)

    private fun backdrop(): Int {
        val shot = instrumentation.uiAutomation.takeScreenshot()
        val soft = if (shot.config == Bitmap.Config.HARDWARE) shot.copy(Bitmap.Config.ARGB_8888, false) else shot
        return soft.getPixel(3, soft.height / 4) or 0xFF000000.toInt()
    }

    private fun near(actual: Int, expected: Int): Boolean =
        abs(Color.red(actual) - Color.red(expected)) <= 4 &&
            abs(Color.green(actual) - Color.green(expected)) <= 4 &&
            abs(Color.blue(actual) - Color.blue(expected)) <= 4

    private fun waitForBackdrop(expected: Int, millis: Long = 8000): Boolean {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            if (near(backdrop(), expected)) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun waitForShownClocks(scenario: ActivityScenario<*>, expected: Int, millis: Long = 8000): Int {
        val end = System.currentTimeMillis() + millis
        var seen = -1
        while (System.currentTimeMillis() < end) {
            scenario.onActivity { seen = shownClocks(it) }
            if (seen == expected) return seen
            Thread.sleep(250)
        }
        return seen
    }

    @Test
    fun clockAndDateAppearWhenEnabledWhileAway() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals(0, shownClocks(it)) }
            away(scenario)
            prefs.showClock = true
            prefs.showDate = true
            back(scenario)
            scenario.onActivity { assertEquals(2, shownClocks(it)) }
            away(scenario)
            prefs.showClock = false
            back(scenario)
            scenario.onActivity { assertEquals(1, shownClocks(it)) }
            away(scenario)
            prefs.showDate = false
            back(scenario)
            scenario.onActivity { assertEquals(0, shownClocks(it)) }
        }
    }

    @Test
    fun batteryAppearsAndDisappearsWhileAway() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            assertFalse(device.wait(Until.hasObject(By.textContains("%")), 1500))
            away(scenario)
            prefs.showBattery = true
            back(scenario)
            assertTrue("battery not shown", device.wait(Until.hasObject(By.textContains("%")), 8000))
            away(scenario)
            prefs.showBattery = false
            back(scenario)
            assertTrue("battery still shown", device.wait(Until.gone(By.textContains("%")), 8000))
        }
    }

    @Test
    fun barMovesWhenPreferenceChangesWhileAway() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(views(activity).filterIsInstance<ListView>().single().isStackFromBottom)
            }
            away(scenario)
            prefs.barAtBottom = false
            back(scenario)
            scenario.onActivity { activity ->
                assertFalse(views(activity).filterIsInstance<ListView>().single().isStackFromBottom)
            }
        }
    }

    @Test
    fun typedTextSurvivesALookChange() {
        prefs.closeAfterAction = false
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<EditText>(R.id.search_input).setText("abc") }
            away(scenario)
            prefs.showClock = true
            back(scenario)
            scenario.onActivity {
                assertEquals("abc", it.findViewById<EditText>(R.id.search_input).text.toString())
                assertEquals(1, shownClocks(it))
            }
        }
    }

    @Test
    fun wallpaperFlagFollowsPreference() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            fun flagged(activity: Activity) = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0
            scenario.onActivity { assertFalse(flagged(it)) }
            away(scenario)
            prefs.showWallpaper = true
            back(scenario)
            scenario.onActivity { assertTrue(flagged(it)) }
            away(scenario)
            prefs.showWallpaper = false
            back(scenario)
            scenario.onActivity { assertFalse(flagged(it)) }
        }
    }

    @Test
    fun wallpaperShowsThroughOnlyWhenEnabled() {
        prefs.theme = "dark"
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            assertTrue("plain background is not black", waitForBackdrop(Color.BLACK))
            away(scenario)
            prefs.showWallpaper = true
            back(scenario)
            val end = System.currentTimeMillis() + 8000
            var lit = false
            while (System.currentTimeMillis() < end && !lit) {
                val pixel = backdrop()
                lit = Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) > 24
                if (!lit) Thread.sleep(250)
            }
            assertTrue("wallpaper does not show through", lit)
            away(scenario)
            prefs.showWallpaper = false
            back(scenario)
            assertTrue("background did not return to black", waitForBackdrop(Color.BLACK))
        }
    }

    @Test
    fun themeChangeWhileAwayRecoloursSearch() {
        prefs.theme = "light"
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            assertTrue("light theme is not white", waitForBackdrop(Color.WHITE))
            away(scenario)
            prefs.theme = "dark"
            back(scenario)
            assertTrue("dark theme is not black", waitForBackdrop(Color.BLACK))
            device.waitForIdle()
            away(scenario)
            prefs.theme = "light"
            back(scenario)
            assertTrue("light theme did not return", waitForBackdrop(Color.WHITE))
        }
    }

    private fun scroll(activity: Activity) = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView

    private fun rows(activity: Activity) = (scroll(activity).getChildAt(0) as ViewGroup).childCount

    private fun switchFor(activity: Activity, label: Int): Switch {
        val text = views(activity).filterIsInstance<TextView>().first { it.text.toString() == context.getString(label) }
        val row = (text.parent as View).parent as ViewGroup
        return (0 until row.childCount).map { row.getChildAt(it) }.filterIsInstance<Switch>().single()
    }

    @Test
    fun flippingASwitchKeepsTheScrollPosition() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            var before = 0
            scenario.onActivity { activity ->
                val scroll = scroll(activity)
                scroll.scrollTo(0, 600)
                before = scroll.scrollY
                switchFor(activity, R.string.show_clock).performClick()
            }
            assertTrue("settings are too short to scroll", before > 0)
            assertTrue(prefs.showClock)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertEquals(before, scroll(it).scrollY) }
        }
    }

    @Test
    fun clipboardSwitchRevealsItsOptions() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            var closed = 0
            scenario.onActivity { activity ->
                closed = rows(activity)
                switchFor(activity, R.string.clipboard).performClick()
            }
            assertTrue(prefs.clipboardEnabled)
            val end = System.currentTimeMillis() + 5000
            var opened = closed
            while (System.currentTimeMillis() < end && opened <= closed) {
                scenario.onActivity { opened = rows(it) }
                if (opened <= closed) Thread.sleep(200)
            }
            assertTrue("clipboard options did not appear ($closed -> $opened)", opened > closed)
        }
    }

    private fun clickText(text: String) {
        val found = device.wait(Until.findObject(By.text(text)), 5000)
        assertNotNull("'$text' not on screen", found)
        found.click()
    }

    @Test
    fun themeChoiceRecoloursSettingsAtOnce() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            clickText(context.getString(R.string.theme))
            clickText(context.getString(R.string.theme_dark))
            assertTrue("settings did not turn black", waitForBackdrop(Color.BLACK))
            assertEquals("dark", prefs.theme)
            clickText(context.getString(R.string.theme))
            clickText(context.getString(R.string.theme_light))
            assertTrue("settings did not turn white", waitForBackdrop(Color.WHITE))
            assertEquals("light", prefs.theme)
        }
    }

    private fun backToSearch(): Boolean {
        val searchMarker = By.desc(context.getString(R.string.settings)).pkg(context.packageName)
        val settingsMarker = By.text(context.getString(R.string.show_clock)).pkg(context.packageName)
        repeat(3) {
            device.pressBack()
            if (device.wait(Until.hasObject(searchMarker), 4000)) return true
            if (!device.hasObject(settingsMarker)) return device.wait(Until.hasObject(searchMarker), 4000)
        }
        return false
    }

    @Test
    fun changesMadeInSettingsShowOnSearchAfterBack() {
        prefs.theme = "dark"
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            assertTrue("search did not start black", waitForBackdrop(Color.BLACK))
            scenario.onActivity { assertEquals(0, shownClocks(it)) }
            val button = device.wait(Until.findObject(By.desc(context.getString(R.string.settings))), 5000)
            assertNotNull("settings button missing", button)
            button.click()
            clickText(context.getString(R.string.show_clock))
            clickText(context.getString(R.string.theme))
            clickText(context.getString(R.string.theme_light))
            assertTrue("settings did not turn white", waitForBackdrop(Color.WHITE))
            assertTrue("did not get back to search", backToSearch())
            val shown = waitForShownClocks(scenario, 1)
            var state = ""
            scenario.onActivity {
                state = "destroyed=${it.isDestroyed} finishing=${it.isFinishing} id=${System.identityHashCode(it)} clocks=${views(it).count { v -> v is TextClock }}"
            }
            assertEquals("clock did not appear on search (showClock=${prefs.showClock}, $state)", 1, shown)
            assertTrue("search did not turn white", waitForBackdrop(Color.WHITE))
            assertTrue(prefs.showClock)
        }
    }
}
