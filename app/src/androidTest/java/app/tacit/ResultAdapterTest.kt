package app.tacit

import android.database.DataSetObserver
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    private companion object {
        const val WIDE = 1000
    }

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

    private fun adapter(recorder: Recorder, messengers: Set<ContactAction>, widthDp: Int = WIDE): ResultAdapter =
        ResultAdapter(context, recorder, Ui.palette(context), widthDp).also {
            it.installedMessengers = messengers
            it.submit(everything)
        }

    private fun both() = setOf(ContactAction.WHATSAPP, ContactAction.WHATSAPP_BUSINESS)

    private fun <T> withDensity(key: String, block: () -> T): T {
        val saved = Graph.prefs.density
        Graph.prefs.density = key
        try {
            return block()
        } finally {
            Graph.prefs.density = saved
        }
    }

    private fun actionButtons(row: View): List<ImageView> = views(row).filterIsInstance<ImageView>().filter { it.tag is ContactAction }

    private fun moreButton(row: View): ImageView =
        views(row).filterIsInstance<ImageView>().first { it.contentDescription == context.getString(R.string.action_more) }

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
            val buttons = actionButtons(row)
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
    fun contactButtonsOverflowIntoMoreOnNarrowScreens() {
        withDensity("comfortable") {
            val expected = listOf(320 to 0, 360 to 1, 411 to 2, 600 to 4)
            for ((width, direct) in expected) {
                val adapter = adapter(Recorder(), both(), width)
                val parent = FrameLayout(context)
                onMain {
                    val row = adapter.getView(0, null, parent)
                    val shown = actionButtons(row).filter { effectivelyVisible(it) }
                    assertEquals("direct buttons at $width dp", direct, shown.size)
                    assertEquals("More at $width dp", direct < 4, effectivelyVisible(moreButton(row)))
                    assertEquals(listOf(ContactAction.CALL, ContactAction.MESSAGE, ContactAction.WHATSAPP, ContactAction.WHATSAPP_BUSINESS).take(direct),
                        shown.map { it.tag })
                }
            }
        }
    }

    @Test
    fun compactFitsOneMoreButtonThanComfortableOnTheSameScreen() {
        withDensity("compact") {
            val expected = listOf(320 to 1, 360 to 1, 411 to 2, 450 to 4)
            for ((width, direct) in expected) {
                val adapter = adapter(Recorder(), both(), width)
                val parent = FrameLayout(context)
                onMain {
                    val row = adapter.getView(0, null, parent)
                    assertEquals("direct buttons at $width dp", direct, actionButtons(row).count { effectivelyVisible(it) })
                    assertEquals("More at $width dp", direct < 4, effectivelyVisible(moreButton(row)))
                }
            }
        }
    }

    @Test
    fun moreStaysHiddenWhenEverythingFitsAndForRowsWithoutActions() {
        withDensity("compact") {
            val adapter = adapter(Recorder(), emptySet(), 320)
            val parent = FrameLayout(context)
            onMain {
                var row = adapter.getView(0, null, parent)
                val more = moreButton(row)
                assertFalse("two buttons fit on 320 dp in Compact", effectivelyVisible(more))
                assertEquals(2, actionButtons(row).count { effectivelyVisible(it) })
                row = adapter.getView(2, row, parent)
                assertFalse(effectivelyVisible(more))
                assertTrue(more.isClickable)
                assertNotNull(more.drawable)
                assertEquals(Ui.dp(context, 48), more.layoutParams.width)
                assertEquals(Ui.dp(context, 48), more.layoutParams.height)
            }
        }
    }

    @Test
    fun theOverflowFollowsTheScreenWidth() {
        withDensity("comfortable") {
            val adapter = adapter(Recorder(), both(), 600)
            val parent = FrameLayout(context)
            onMain {
                val row = adapter.getView(0, null, parent)
                assertEquals(4, actionButtons(row).count { effectivelyVisible(it) })
                adapter.screenWidthDp = 320
                adapter.getView(0, row, parent)
                assertEquals(0, actionButtons(row).count { effectivelyVisible(it) })
                assertTrue(effectivelyVisible(moreButton(row)))
                adapter.screenWidthDp = 600
                adapter.getView(0, row, parent)
                assertEquals(4, actionButtons(row).count { effectivelyVisible(it) })
                assertFalse(effectivelyVisible(moreButton(row)))
            }
        }
    }

    @Test
    fun everyRowHasASpokenDescription() {
        for (key in listOf("comfortable", "compact")) {
            withDensity(key) {
                val adapter = adapter(Recorder(), both())
                val parent = FrameLayout(context)
                onMain {
                    var row = adapter.getView(0, null, parent)
                    for (i in everything.indices) {
                        row = adapter.getView(i, row, parent)
                        assertTrue("row $i has no description at $key", !row.contentDescription.isNullOrEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun comfortableStacksTheSubtitleAndCompactPutsItInline() {
        for ((key, inline) in listOf("comfortable" to false, "compact" to true)) {
            withDensity(key) {
                val adapter = adapter(Recorder(), emptySet())
                val parent = FrameLayout(context)
                onMain {
                    val row = adapter.getView(3, null, parent)
                    val texts = views(row).filterIsInstance<TextView>().filter { it.text == "report.pdf" || it.text == "~/Documents" }
                    assertEquals(2, texts.size)
                    val title = texts.first { it.text == "report.pdf" }
                    val subtitle = texts.first { it.text == "~/Documents" }
                    assertSame(title.parent, subtitle.parent)
                    val orientation = (title.parent as android.widget.LinearLayout).orientation
                    assertEquals(if (inline) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL, orientation)
                    assertEquals(1, subtitle.maxLines)
                    assertEquals(android.text.TextUtils.TruncateAt.END, subtitle.ellipsize)
                    assertEquals(android.text.TextUtils.TruncateAt.END, title.ellipsize)
                }
            }
        }
    }

    @Test
    fun theInlineSubtitleYieldsToTheContactName() {
        withDensity("compact") {
            val narrow = adapter(Recorder(), both(), 320)
            val wide = adapter(Recorder(), emptySet(), 411)
            val parent = FrameLayout(context)
            onMain {
                val crowded = narrow.getView(0, null, parent)
                val number = views(crowded).filterIsInstance<TextView>().first { it.text == "+201001234567" }
                assertFalse("the number leaves room for the name", effectivelyVisible(number))
                val roomy = wide.getView(3, null, parent)
                val folder = views(roomy).filterIsInstance<TextView>().first { it.text == "~/Documents" }
                assertTrue(effectivelyVisible(folder))
                assertTrue(folder.maxWidth <= Ui.dp(context, 411 * 40 / 100))
                assertTrue(folder.maxWidth > 0)
            }
        }
    }

    @Test
    fun rowsKeepTheirMinimumHeightAndIconsRenderAtTheDensityIconSize() {
        for (density in app.tacit.core.RowDensity.entries) {
            withDensity(density.key) {
                val adapter = adapter(Recorder(), emptySet())
                val parent = FrameLayout(context)
                onMain {
                    val row = adapter.getView(2, null, parent) as android.widget.LinearLayout
                    assertEquals(Ui.dp(context, density.rowDp), row.minimumHeight)
                    val slot = row.getChildAt(0) as FrameLayout
                    assertEquals(Ui.dp(context, density.slotDp), slot.layoutParams.width)
                    val appIcon = slot.getChildAt(2) as ImageView
                    val drawn = slot.layoutParams.width - appIcon.paddingLeft - appIcon.paddingRight
                    assertTrue("icon area $drawn px", kotlin.math.abs(drawn - Ui.dp(context, density.iconDp)) <= 2)
                }
            }
        }
    }

    @Test
    fun itemIdsAreSafeForAnyPosition() {
        val adapter = ResultAdapter(context, Recorder(), Ui.palette(context))
        for (position in -1..3) adapter.getItemId(position)
        adapter.submit(everything)
        for (position in -1..everything.size + 1) adapter.getItemId(position)
    }

    @Test
    fun submitNotifiesTheListOncePerCall() {
        val adapter = adapter(Recorder(), emptySet())
        var changes = 0
        adapter.registerDataSetObserver(object : DataSetObserver() {
            override fun onChanged() {
                changes++
            }
        })
        onMain { adapter.submit(everything.take(5), 3) }
        assertEquals(1, changes)
        assertEquals(3, adapter.selected)
        assertEquals(5, adapter.count)
        onMain { adapter.submit(everything.take(1)) }
        assertEquals(2, changes)
        assertEquals(0, adapter.selected)
        onMain { adapter.selected = 0 }
        assertEquals(3, changes)
    }

    @Test
    fun collidingKeysNeverShareAnItemId() {
        val adapter = ResultAdapter(context, Recorder(), Ui.palette(context))
        adapter.submit(listOf(
            SearchItem("Aa", ItemKind.FILE, "first", "~/"),
            SearchItem("BB", ItemKind.FILE, "second", "~/")
        ))
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(adapter.getItemId(0), adapter.getItemId(1))
        assertFalse(adapter.hasStableIds())
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
