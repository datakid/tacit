package app.tacit

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.fixtures.Fixtures
import app.tacit.ui.LetterStrip
import app.tacit.ui.SearchActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityLabelsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val prefs get() = Graph.prefs
    private val apps = Fixtures.apps(60)
    private val systemSettings = listOf("Wi-Fi", "Bluetooth", "Display").map {
        SearchItem("setting:fixture/$it", ItemKind.SETTING, it, "Settings", payload = "action|android.settings.SETTINGS")
    }
    private var scenario: ActivityScenario<SearchActivity>? = null

    @Before
    fun setUp() {
        prefs.store.edit().clear().commit()
        prefs.barAtBottom = false
        prefs.closeAfterAction = false
        prefs.drawerEnabled = true
        prefs.drawerSettings = true
        Graph.applyPrefs()
        Idle.await()
        Graph.useItemsForTesting(apps + Fixtures.contacts(10) + systemSettings)
        scenario = ActivityScenario.launch(SearchActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        scenario?.close()
        prefs.store.edit().clear().commit()
        Graph.applyPrefs()
        Graph.useItemsForTesting(null)
        Idle.await()
        Graph.icons.clear()
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

    private fun shown(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return true
    }

    private fun unlabelled(activity: Activity): List<String> {
        val missing = ArrayList<String>()
        for (view in views(activity)) {
            if (!shown(view)) continue
            val described = !view.contentDescription.isNullOrEmpty()
            if (view is ImageView && view.isClickable && !described) {
                missing.add("clickable ImageView ${activity.resources.getResourceEntryNameOrId(view)}")
            }
            if (view is TextView && view.text.isNullOrEmpty() && !described) {
                missing.add("empty TextView ${activity.resources.getResourceEntryNameOrId(view)}")
            }
        }
        return missing
    }

    private fun android.content.res.Resources.getResourceEntryNameOrId(view: View): String =
        if (view.id == View.NO_ID) view.javaClass.simpleName else runCatching { getResourceEntryName(view.id) }.getOrDefault(view.javaClass.simpleName)

    private fun read(block: (Activity) -> List<String>): List<String> {
        var out: List<String> = emptyList()
        scenario!!.onActivity { out = block(it) }
        return out
    }

    private fun openDrawer() {
        val button = device.wait(Until.findObject(By.desc(context.getString(R.string.drawer_button))), 8000)
        assertNotNull("apps button missing", button)
        button.click()
        val deadline = System.currentTimeMillis() + 15_000
        var open = false
        while (!open && System.currentTimeMillis() < deadline) {
            scenario!!.onActivity { activity -> open = views(activity).filterIsInstance<LetterStrip>().any { it.visibility == View.VISIBLE } }
            if (!open) Thread.sleep(100)
        }
        assertTrue("drawer did not open", open)
    }

    @Test
    fun theSearchScreenLabelsEverythingYouCanTouchWhileTheDrawerIsClosed() {
        assertEquals(emptyList<String>(), read { unlabelled(it) })
    }

    @Test
    fun theSearchScreenLabelsEverythingYouCanTouchWithTheTypedQueryShown() {
        scenario!!.onActivity { it.findViewById<EditText>(R.id.search_input).setText("a") }
        instrumentation.waitForIdleSync()
        assertEquals(emptyList<String>(), read { unlabelled(it) })
    }

    @Test
    fun theSearchScreenLabelsEverythingYouCanTouchWhileTheDrawerIsOpen() {
        openDrawer()
        assertEquals(emptyList<String>(), read { unlabelled(it) })
    }

    @Test
    fun theLetterStripTheTabsAndTheBarButtonsAreNamed() {
        openDrawer()
        var strip: String? = null
        val labels = HashSet<String>()
        scenario!!.onActivity { activity ->
            strip = views(activity).filterIsInstance<LetterStrip>().first().contentDescription?.toString()
            for (view in views(activity)) if (shown(view)) view.contentDescription?.toString()?.let { labels.add(it) }
        }
        assertEquals(context.getString(R.string.drawer_index), strip)
        for (id in listOf(R.string.drawer_button, R.string.drawer_tab_settings, R.string.settings, R.string.search_hint)) {
            assertTrue("no view is named ${context.getString(id)}", labels.contains(context.getString(id)))
        }
    }

    @Test
    fun theClearButtonIsNamedOnceTextIsTyped() {
        scenario!!.onActivity { it.findViewById<EditText>(R.id.search_input).setText("a") }
        instrumentation.waitForIdleSync()
        var found = false
        scenario!!.onActivity { activity ->
            found = views(activity).any { shown(it) && it is ImageView && it.contentDescription == activity.getString(R.string.clear) }
        }
        assertTrue(found)
    }

    @Test
    fun theChosenDrawerTabIsMarkedSelected() {
        openDrawer()
        var appsSelected = false
        var settingsSelected = true
        scenario!!.onActivity { activity ->
            val tabs = views(activity).filterIsInstance<TextView>().filter {
                it.isClickable && (it.text == activity.getString(R.string.drawer_button) || it.text == activity.getString(R.string.drawer_tab_settings))
            }
            appsSelected = tabs.first { it.text == activity.getString(R.string.drawer_button) }.isSelected
            settingsSelected = tabs.first { it.text == activity.getString(R.string.drawer_tab_settings) }.isSelected
        }
        assertTrue(appsSelected)
        assertFalse(settingsSelected)
    }
}
