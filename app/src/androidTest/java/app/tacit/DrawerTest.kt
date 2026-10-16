package app.tacit

import android.app.Activity
import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
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
import app.tacit.core.DrawerList
import app.tacit.core.DrawerTab
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.fixtures.Fixtures
import app.tacit.ui.LetterStrip
import app.tacit.ui.SearchActivity
import app.tacit.ui.SettingsActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val prefs get() = Graph.prefs
    private val apps = Fixtures.apps(120)
    private val systemSettings = listOf(
        "Wi-Fi", "Bluetooth", "Display", "Battery", "Sound", "Storage", "Location", "Accessibility", "Date and time", "Language"
    ).map { SearchItem("setting:fixture/$it", ItemKind.SETTING, it, "Settings", payload = "action|android.settings.SETTINGS") }
    private val perAppSettings = apps.take(5).map {
        SearchItem("appinfo:fixture/${it.label}", ItemKind.SETTING, "${it.label} info", "App", payload = "appinfo|fixture")
    }
    private var scenario: ActivityScenario<SearchActivity>? = null

    @Before
    fun setUp() {
        prefs.store.edit().clear().commit()
        prefs.barAtBottom = false
        prefs.closeAfterAction = false
        prefs.drawerEnabled = true
        prefs.resultCount = 8
        Graph.applyPrefs()
        Graph.useItemsForTesting(apps + Fixtures.contacts(40) + systemSettings + perAppSettings)
    }

    @After
    fun tearDown() {
        scenario?.close()
        prefs.store.edit().clear().commit()
        Graph.applyPrefs()
        Graph.useItemsForTesting(null)
        Graph.icons.clear()
    }

    private fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(SearchActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(block: (Activity) -> T): T {
        var out: Any? = null
        scenario!!.onActivity { out = block(it) }
        return out as T
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

    private fun list(activity: Activity): ListView = views(activity).filterIsInstance<ListView>().first()

    private fun strip(activity: Activity): LetterStrip? = views(activity).filterIsInstance<LetterStrip>().firstOrNull()

    private fun appsButton(activity: Activity): View? =
        views(activity).firstOrNull { it.contentDescription?.toString() == activity.getString(R.string.drawer_button) }

    private fun rows(): List<SearchItem> = read { activity ->
        val list = list(activity)
        (0 until list.adapter.count).map { list.adapter.getItem(it) as SearchItem }
    }

    private fun stripShown(): Boolean = read { activity -> strip(activity)?.visibility == View.VISIBLE }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(message, condition())
    }

    private fun settingsTabLabel() = context.getString(R.string.drawer_tab_settings)

    private fun openSettingsTab() {
        prefs.drawerSettings = true
        launch()
        openDrawer()
        val tab = device.wait(Until.findObject(By.text(settingsTabLabel())), 5000)
        assertNotNull("settings tab missing", tab)
        tab.click()
        waitFor("settings list did not show") { rows().isNotEmpty() && rows().all { it.kind == ItemKind.SETTING } }
    }

    private fun openDrawer() {
        val button = device.wait(Until.findObject(By.desc(context.getString(R.string.drawer_button))), 8000)
        assertNotNull("apps button missing", button)
        button.click()
        waitFor("drawer did not open") { stripShown() }
    }

    private fun type(text: String) {
        scenario!!.onActivity { it.findViewById<EditText>(R.id.search_input).setText(text) }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun noButtonOrStripWhileTheDrawerIsOff() {
        prefs.drawerEnabled = false
        launch()
        assertNull(read { appsButton(it) })
        assertNull(read { strip(it) })
    }

    @Test
    fun buttonShowsEveryAppAlphabetically() {
        launch()
        openDrawer()
        val shown = rows()
        assertEquals(apps.size, shown.size)
        assertTrue(shown.all { it.kind == ItemKind.APP })
        val keys = shown.map { DrawerList.sortKey(it) }
        assertEquals(keys.sorted(), keys)
    }

    @Test
    fun letterStripJumpsToItsSection() {
        launch()
        openDrawer()
        val data = DrawerList.build(apps.toTypedArray())
        val visible = read { activity -> list(activity).let { it.lastVisiblePosition - it.firstVisiblePosition + 2 } }
        val index = (1 until data.letters.size).lastOrNull { data.starts[it] + visible <= data.size }
        assertNotNull("no section far enough from the end", index)
        val letter = data.letters[index!!].toString()
        var bubbleWhileTouching = false
        scenario!!.onActivity { activity ->
            val strip = strip(activity)!!
            val y = strip.height * (index + 0.5f) / data.letters.size
            val time = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, strip.width / 2f, y, 0)
            strip.dispatchTouchEvent(down)
            bubbleWhileTouching = views(activity).any { it is TextView && it.isShown && it.text.toString() == letter }
            val up = MotionEvent.obtain(time, time + 10, MotionEvent.ACTION_UP, strip.width / 2f, y, 0)
            strip.dispatchTouchEvent(up)
            down.recycle()
            up.recycle()
        }
        assertTrue("letter bubble was not shown while touching", bubbleWhileTouching)
        waitFor("list did not jump to $letter") { read { list(it).firstVisiblePosition } == data.starts[index] }
        waitFor("letter bubble stayed on screen") {
            !read { activity -> views(activity).any { it is TextView && it.isShown && it.text.toString() == letter } }
        }
    }

    @Test
    fun backClosesTheDrawerFirst() {
        launch()
        openDrawer()
        device.pressBack()
        waitFor("drawer did not close") { !stripShown() }
        assertTrue(rows().size < apps.size)
        assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
    }

    @Test
    fun typingClosesTheDrawerAndSearches() {
        launch()
        openDrawer()
        type("calc")
        waitFor("drawer stayed open") { !stripShown() }
        waitFor("no Calculator row") { rows().any { it.label == "Calculator" } }
        assertTrue(rows().size < apps.size)
    }

    @Test
    fun tappingTheSearchFieldClosesTheDrawer() {
        launch()
        openDrawer()
        val field = device.wait(Until.findObject(By.res(context.packageName, "search_input")), 5000)
        assertNotNull("search field missing", field)
        field.click()
        waitFor("drawer stayed open") { !stripShown() }
    }

    @Test
    fun longPressOpensTheAppMenuAndTheDrawerSurvivesIt() {
        launch()
        openDrawer()
        val first = DrawerList.build(apps.toTypedArray()).items[0].label
        val row = device.wait(Until.findObject(By.text(first)), 5000)
        assertNotNull("first row '$first' not on screen", row)
        row.longClick()
        assertTrue("menu did not open", device.wait(Until.hasObject(By.text(context.getString(R.string.menu_pin))), 5000))
        device.pressBack()
        assertTrue("menu stayed open", device.wait(Until.gone(By.text(context.getString(R.string.menu_pin))), 5000))
        assertTrue(stripShown())
    }

    @Test
    fun drawerStaysOpenWhenTheIndexRefreshes() {
        launch()
        openDrawer()
        Graph.useItemsForTesting(apps.take(40))
        waitFor("list did not shrink") { rows().size == 40 }
        assertTrue(stripShown())
    }

    @Test
    fun settingsChangedElsewhereShowOnReturn() {
        prefs.drawerEnabled = false
        launch()
        assertNull(read { appsButton(it) })
        prefs.drawerEnabled = true
        prefs.density = "compact"
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        waitFor("apps button did not appear") { read { appsButton(it) } != null }
    }

    private fun firstRowHeight(density: String): Int {
        prefs.density = density
        launch()
        type("calc")
        waitFor("no result rows") { read { list(it).childCount } > 0 }
        return read { list(it).getChildAt(0).height }
    }

    @Test
    fun comfortableRowsAreTallerThanCompactOnes() {
        val scale = context.resources.displayMetrics.density
        val roomy = firstRowHeight("comfortable")
        val tight = firstRowHeight("compact")
        assertEquals(64 * scale, roomy.toFloat(), 2f)
        assertEquals(56 * scale, tight.toFloat(), 2f)
    }

    @Test
    fun comfortableIsTheDefault() {
        prefs.store.edit().remove("density").commit()
        launch()
        type("calc")
        waitFor("no result rows") { read { list(it).childCount } > 0 }
        val scale = context.resources.displayMetrics.density
        assertEquals(64 * scale, read { list(it).getChildAt(0).height }.toFloat(), 2f)
    }

    private fun clickText(text: String) {
        val found = device.wait(Until.findObject(By.text(text)), 5000)
        assertNotNull("'$text' not on screen", found)
        found.click()
    }

    @Test
    fun settingsOffersRowSizeAndTheDrawerSwitch() {
        prefs.store.edit().clear().commit()
        ActivityScenario.launch(SettingsActivity::class.java).use {
            clickText(context.getString(R.string.row_size))
            clickText(context.getString(R.string.row_compact))
            waitFor("row size was not saved") { prefs.density == "compact" }
            clickText(context.getString(R.string.app_drawer))
            waitFor("drawer switch was not saved") { prefs.drawerEnabled }
        }
    }

    @Test
    fun noTabsUnlessTheSettingsTabIsOn() {
        launch()
        openDrawer()
        assertTrue(!device.hasObject(By.text(settingsTabLabel())))
    }

    @Test
    fun settingsTabListsSystemSettingsAlphabeticallyWithoutPerAppRows() {
        openSettingsTab()
        val shown = rows()
        assertEquals(systemSettings.size, shown.size)
        assertTrue(shown.none { it.key.startsWith("appinfo:") })
        val keys = shown.map { DrawerList.sortKey(it) }
        assertEquals(keys.sorted(), keys)
        val expected = DrawerList.build((apps + systemSettings + perAppSettings).toTypedArray(), DrawerTab.SETTINGS)
        assertEquals(expected.letters.size, read { activity -> strip(activity)!!.letterCount() })
    }

    @Test
    fun switchingTabsRestoresTheApps() {
        openSettingsTab()
        val back = device.wait(Until.findObject(By.text(context.getString(R.string.drawer_button))), 5000)
        assertNotNull("apps tab missing", back)
        back.click()
        waitFor("apps did not return") { rows().size == apps.size && rows().all { it.kind == ItemKind.APP } }
        assertTrue(stripShown())
    }

    @Test
    fun backFromTheSettingsTabClosesTheDrawer() {
        openSettingsTab()
        device.pressBack()
        waitFor("drawer did not close") { !stripShown() }
        assertTrue(!device.hasObject(By.text(settingsTabLabel())))
    }

    @Test
    fun aSettingsRowOpensItsScreenAndTheDrawerStays() {
        Intents.init()
        try {
            intending(ExternalIntents.matcher()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            openSettingsTab()
            scenario!!.onActivity { list(it).getChildAt(0).performClick() }
            intended(hasAction("android.settings.SETTINGS"))
            assertTrue(stripShown())
        } finally {
            Intents.release()
        }
    }

    @Test
    fun settingsTabSwitchIsSavedFromSettings() {
        prefs.drawerSettings = false
        ActivityScenario.launch(SettingsActivity::class.java).use {
            val toggle = device.wait(Until.findObject(By.text(context.getString(R.string.drawer_settings))), 5000)
            assertNotNull("settings tab switch missing", toggle)
            toggle.click()
            waitFor("switch was not saved") { prefs.drawerSettings }
        }
    }
}
