package app.tacit

import android.app.Activity
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.sources.ContactInfo
import app.tacit.ui.ContactAction
import app.tacit.ui.ResultAdapter
import app.tacit.ui.RowCallbacks
import app.tacit.ui.SearchActivity
import app.tacit.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NarrowScreenTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private class Recorder : RowCallbacks {
        @Volatile
        var action: ContactAction? = null

        @Volatile
        var item: SearchItem? = null
        override fun onPrimary(item: SearchItem, view: View) = Unit
        override fun onSecondary(item: SearchItem, view: View) = Unit
        override fun onContactAction(item: SearchItem, action: ContactAction, view: View) {
            this.item = item
            this.action = action
        }
    }

    private val number = "+20 100 123 4567"
    private val contact = SearchItem("contact:Asmaa Gouda", ItemKind.CONTACT, "Asmaa Gouda", number,
        extra = ContactInfo("Asmaa Gouda", 1L, "Asmaa Gouda", listOf(number), number, 7L, 9L, false))

    @Before
    fun setUp() {
        Graph.prefs.store.edit().clear().commit()
        Graph.applyPrefs()
        Idle.await()
        shell("wm size 720x1280")
        shell("wm density 360")
        waitFor("the display did not change size") { device.displayWidth == 720 }
    }

    @After
    fun tearDown() {
        shell("wm size reset")
        shell("wm density reset")
        Graph.prefs.store.edit().clear().commit()
        Graph.applyPrefs()
        Idle.await()
    }

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(message, condition())
    }

    private fun walk(view: View, visit: (View) -> Unit) {
        visit(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), visit)
    }

    private fun views(root: View): List<View> = ArrayList<View>().also { list -> walk(root) { list.add(it) } }

    private fun shown(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return true
    }

    private fun host(activity: Activity, recorder: Recorder): ListView {
        val adapter = ResultAdapter(activity, recorder, Ui.palette(activity))
        adapter.installedMessengers = setOf(ContactAction.WHATSAPP, ContactAction.WHATSAPP_BUSINESS)
        adapter.submit(listOf(contact))
        val list = ListView(activity).apply {
            this.adapter = adapter
            divider = null
            dividerHeight = 0
            val side = Ui.dp(activity, 8)
            setPadding(side, 0, side, 0)
            clipToPadding = false
        }
        activity.setContentView(android.widget.FrameLayout(activity).apply {
            fitsSystemWindows = true
            addView(list, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })
        return list
    }

    private fun withRow(density: String, recorder: Recorder = Recorder(), whileOpen: () -> Unit = {}, check: (Activity, View) -> Unit) {
        Graph.prefs.density = density
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            var list: ListView? = null
            scenario.onActivity { list = host(it, recorder) }
            instrumentation.waitForIdleSync()
            device.waitForIdle()
            waitFor("the contact row was not laid out") {
                var ready = false
                scenario.onActivity { ready = list!!.childCount > 0 && list!!.getChildAt(0).height > 0 }
                ready
            }
            scenario.onActivity { activity ->
                val widthDp = activity.resources.configuration.screenWidthDp
                assertTrue("the screen is $widthDp dp wide", widthDp in 319..321)
                check(activity, list!!.getChildAt(0))
            }
            whileOpen()
        }
    }

    private fun titleOf(row: View): TextView = views(row).filterIsInstance<TextView>().first { it.text == "Asmaa Gouda" }

    private fun directButtons(row: View): List<ImageView> =
        views(row).filterIsInstance<ImageView>().filter { it.tag is ContactAction && shown(it) }

    private fun moreOf(row: View): ImageView =
        views(row).filterIsInstance<ImageView>().first { it.contentDescription == context.getString(R.string.action_more) }

    private fun assertNameIsReadable(activity: Activity, row: View) {
        val title = titleOf(row)
        val minimum = Ui.dp(activity, 140)
        assertTrue("title is ${title.width} px, needs $minimum", title.width >= minimum)
        assertEquals("the name is cut", 0, title.layout.getEllipsisCount(0))
    }

    @Test
    fun comfortableKeepsTheNameReadableWithBothWhatsApps() {
        withRow("comfortable") { activity, row ->
            assertNameIsReadable(activity, row)
            assertEquals(0, directButtons(row).size)
            assertTrue(shown(moreOf(row)))
        }
    }

    @Test
    fun compactKeepsTheNameReadableWithBothWhatsApps() {
        withRow("compact") { activity, row ->
            assertNameIsReadable(activity, row)
            assertEquals(1, directButtons(row).size)
            assertTrue(shown(moreOf(row)))
        }
    }

    @Test
    fun everyContactButtonStaysAtLeast48DpTall() {
        for (density in listOf("comfortable", "compact")) {
            withRow(density) { activity, row ->
                val minimum = Ui.dp(activity, 48)
                for (button in directButtons(row) + moreOf(row)) {
                    assertTrue("$density button ${button.width}x${button.height}", button.width >= minimum && button.height >= minimum)
                }
            }
        }
    }

    private fun openMoreAndPick(density: String, hidden: List<Int>, pick: Int, expected: ContactAction) {
        val recorder = Recorder()
        val whileOpen = {
            for (label in hidden) {
                assertNotNull("missing ${context.getString(label)}", device.wait(Until.findObject(By.text(context.getString(label))), 5000))
            }
            device.findObject(By.text(context.getString(pick))).click()
            waitFor("the action did not run") { recorder.action != null }
            assertEquals(expected, recorder.action)
            assertSame(contact, recorder.item)
        }
        withRow(density, recorder, whileOpen) { _, row -> moreOf(row).performClick() }
    }

    @Test
    fun moreListsEveryHiddenActionInComfortable() {
        openMoreAndPick(
            "comfortable",
            listOf(R.string.action_call, R.string.action_message, R.string.action_whatsapp, R.string.action_whatsapp_business),
            R.string.action_whatsapp,
            ContactAction.WHATSAPP
        )
    }

    @Test
    fun moreListsTheRestAfterTheFirstButtonInCompact() {
        openMoreAndPick(
            "compact",
            listOf(R.string.action_message, R.string.action_whatsapp, R.string.action_whatsapp_business),
            R.string.action_whatsapp_business,
            ContactAction.WHATSAPP_BUSINESS
        )
    }
}
