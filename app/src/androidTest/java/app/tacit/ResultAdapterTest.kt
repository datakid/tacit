package app.tacit

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.sources.ContactInfo
import app.tacit.ui.ContactAction
import app.tacit.ui.ResultAdapter
import app.tacit.ui.RowCallbacks
import app.tacit.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.IdentityHashMap

@RunWith(AndroidJUnit4::class)
class ResultAdapterTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private class Recorder : RowCallbacks {
        var primary: SearchItem? = null
        var secondary: SearchItem? = null
        var action: ContactAction? = null
        var actionItem: SearchItem? = null
        override fun onPrimary(item: SearchItem, view: View) {
            primary = item
        }
        override fun onSecondary(item: SearchItem, view: View) {
            secondary = item
        }
        override fun onContactAction(item: SearchItem, action: ContactAction, view: View) {
            this.action = action
            actionItem = item
        }
    }

    private fun contact(name: String, numbers: List<String>, business: Long? = null) =
        SearchItem("contact:$name", ItemKind.CONTACT, name, numbers.firstOrNull().orEmpty(),
            extra = ContactInfo(name, 1L, name, numbers, numbers.firstOrNull(), 7L, business, false))

    private val withNumber = contact("Asmaa Gouda", listOf("+201001234567"), business = 9L)
    private val withoutNumber = contact("No Number", emptyList())
    private val everything = listOf(
        withNumber,
        withoutNumber,
        SearchItem("set:wifi", ItemKind.SETTING, "Wi-Fi", "Settings", payload = "x"),
        SearchItem("file:/a", ItemKind.FILE, "report.pdf", "~/Documents"),
        SearchItem("file:/b", ItemKind.FILE, "Documents", "~/"),
        SearchItem("snip:1", ItemKind.SNIPPET, "Signature", "Snippet"),
        SearchItem("clip:1", ItemKind.CLIPBOARD, "copied text", "1 min ago"),
        SearchItem("calc", ItemKind.CALCULATOR, "42", "6 * 7"),
        SearchItem("web:g", ItemKind.WEB, "tacit", "Search"),
        SearchItem("ql:1", ItemKind.ACTION, "GitHub", "github.com", payload = "ql|1"),
        SearchItem("num:call", ItemKind.ACTION, "Call 123", "Phone", payload = "dial|123"),
        SearchItem("misc", ItemKind.ACTION, "Something", "", payload = "x")
    )

    private fun adapter(recorder: Recorder, messengers: Set<ContactAction>): ResultAdapter =
        ResultAdapter(context, recorder, Ui.palette(context)).also {
            it.installedMessengers = messengers
            it.submit(everything)
        }

    private fun walk(view: View, visit: (View) -> Unit) {
        visit(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), visit)
    }

    private fun views(root: View): List<View> = ArrayList<View>().also { list -> walk(root) { list.add(it) } }

    private fun drawables(root: View, into: IdentityHashMap<Drawable, Boolean>) {
        walk(root) { view ->
            view.background?.let { into[it] = true }
            view.foreground?.let { into[it] = true }
            if (view is ImageView) view.drawable?.let { into[it] = true }
        }
    }

    private fun effectivelyVisible(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return true
    }

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            }
        }
        failure?.let { throw it }
    }

    @Test
    fun rebindingNeverCreatesViewsOrDrawables() {
        val adapter = adapter(Recorder(), setOf(ContactAction.WHATSAPP, ContactAction.WHATSAPP_BUSINESS))
        val parent = FrameLayout(context)
        val firstPass = IdentityHashMap<Drawable, Boolean>()
        val laterPass = IdentityHashMap<Drawable, Boolean>()
        var viewsAtStart: List<View> = emptyList()
        var viewsChanged = false
        onMain {
            var row = adapter.getView(0, null, parent)
            viewsAtStart = views(row)
            for (i in everything.indices) {
                adapter.selected = i
                row = adapter.getView(i, row, parent)
            }
            for (i in everything.indices) {
                adapter.selected = i
                row = adapter.getView(i, row, parent)
                drawables(row, firstPass)
            }
            for (round in 0 until 3) {
                for (i in everything.indices) {
                    adapter.selected = i
                    row = adapter.getView(i, row, parent)
                    drawables(row, laterPass)
                    val now = views(row)
                    if (now.size != viewsAtStart.size || now.indices.any { now[it] !== viewsAtStart[it] }) viewsChanged = true
                }
            }
        }
        assertTrue("a view was created or replaced while rebinding", !viewsChanged)
        for (drawable in laterPass.keys) {
            assertTrue("a drawable was created while rebinding: $drawable", firstPass.containsKey(drawable))
        }
    }

    @Test
    fun clickListenersFollowTheBoundItem() {
        val recorder = Recorder()
        val adapter = adapter(recorder, setOf(ContactAction.WHATSAPP))
        val parent = FrameLayout(context)
        onMain {
            var row = adapter.getView(0, null, parent)
            row = adapter.getView(2, row, parent)
            row.performClick()
            assertSame(everything[2], recorder.primary)
            row.performLongClick()
            assertSame(everything[2], recorder.secondary)
            row = adapter.getView(0, row, parent)
            row.performClick()
            assertSame(everything[0], recorder.primary)
        }
    }

    @Test
    fun contactActionsOnlyToggleVisibility() {
        val recorder = Recorder()
        val adapter = adapter(recorder, setOf(ContactAction.WHATSAPP, ContactAction.WHATSAPP_BUSINESS))
        val parent = FrameLayout(context)
        val minimum = Ui.dp(context, 48)
        onMain {
            val row = adapter.getView(0, null, parent)
            val buttons = views(row).filterIsInstance<ImageView>().filter { it.contentDescription != null }
            assertEquals(4, buttons.size)
            buttons.forEach {
                assertTrue("button under 48 dp", it.layoutParams.width >= minimum && it.layoutParams.height >= minimum)
                assertNotNull(it.drawable)
                assertTrue(effectivelyVisible(it))
            }
            val call = buttons.first { it.tag == ContactAction.CALL }
            call.performClick()
            assertEquals(ContactAction.CALL, recorder.action)
            assertSame(withNumber, recorder.actionItem)

            val setting = adapter.getView(2, row, parent)
            assertSame(row, setting)
            assertTrue(buttons.none { effectivelyVisible(it) })

            adapter.getView(1, row, parent)
            assertTrue("a contact without numbers shows actions", buttons.none { effectivelyVisible(it) })

            adapter.getView(0, row, parent)
            assertEquals(4, buttons.count { effectivelyVisible(it) })
        }
    }

    @Test
    fun whatsappButtonsFollowInstalledApps() {
        val adapter = adapter(Recorder(), emptySet())
        val parent = FrameLayout(context)
        onMain {
            val row = adapter.getView(0, null, parent)
            val buttons = views(row).filterIsInstance<ImageView>().filter { it.tag is ContactAction }
            val visible = buttons.filter { effectivelyVisible(it) }.map { it.tag }
            assertEquals(listOf(ContactAction.CALL, ContactAction.MESSAGE), visible)
        }
    }

    @Test
    fun kindsShowIconsAndContactsShowInitials() {
        val adapter = adapter(Recorder(), emptySet())
        val parent = FrameLayout(context)
        onMain {
            var row = adapter.getView(0, null, parent)
            val initials = views(row).filterIsInstance<TextView>().first { it.text == "AG" }
            assertTrue(effectivelyVisible(initials))
            val icons = views(row).filterIsInstance<ImageView>().filter { it.tag !is ContactAction && it.drawable != null }
            assertTrue("a contact row shows a kind icon", icons.none { effectivelyVisible(it) })

            row = adapter.getView(2, row, parent)
            assertTrue("a setting row hides initials", !effectivelyVisible(initials))
            assertEquals(1, views(row).filterIsInstance<ImageView>().count { it.drawable != null && effectivelyVisible(it) })
        }
    }
}
