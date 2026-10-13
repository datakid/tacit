package app.tacit

import android.app.Activity
import android.app.LocaleManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.sources.ContactInfo
import app.tacit.ui.ContactAction
import app.tacit.ui.ResultAdapter
import app.tacit.ui.RowCallbacks
import app.tacit.ui.SearchActivity
import app.tacit.ui.Ui
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val folder = "/sdcard/tacit-shots"

    private val noop = object : RowCallbacks {
        override fun onPrimary(item: SearchItem, view: View) = Unit
        override fun onSecondary(item: SearchItem, view: View) = Unit
        override fun onContactAction(item: SearchItem, action: ContactAction, view: View) = Unit
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { String(it.readBytes()) }
    }

    private fun contact(name: String, number: String) =
        SearchItem("contact:$name", ItemKind.CONTACT, name, number,
            extra = ContactInfo(name, 1L, name, listOf(number), number, 7L, 9L, false))

    private fun items(): List<SearchItem> {
        val rows = ArrayList<SearchItem>()
        rows.add(contact("Asmaa Gouda", "+20 100 123 4567"))
        rows.add(contact("أسماء جودة", "+20 111 765 4321"))
        rows.add(contact("Ahmed Galal", "+20 122 000 1122"))
        rows.add(SearchItem("set:wifi", ItemKind.SETTING, "Wi-Fi settings", "Settings", payload = "x"))
        rows.add(SearchItem("file:/r", ItemKind.FILE, "Q3 report.pdf", "~/Documents"))
        rows.add(SearchItem("file:/d", ItemKind.FILE, "Documents", "~/"))
        rows.add(SearchItem("snip:1", ItemKind.SNIPPET, "Email signature", "Snippet"))
        rows.add(SearchItem("clip:1", ItemKind.CLIPBOARD, "Meeting link", "2 min ago"))
        rows.add(SearchItem("calc", ItemKind.CALCULATOR, "1,250", "15% of 8,333", payload = "1250"))
        rows.add(SearchItem("web:d", ItemKind.WEB, "tacit launcher", "DuckDuckGo", payload = "x"))
        rows.add(SearchItem("ql:1", ItemKind.ACTION, "GitHub", "github.com", payload = "ql|1"))
        rows.add(SearchItem("num:call", ItemKind.ACTION, "Call +20 100 123 4567", "Phone", payload = "dial|1"))
        rows.addAll(Graph.apps.items().take(2))
        rows.forEach { it.pinned = true }
        return rows
    }

    @Before
    fun setUp() {
        Graph.prefs.store.edit().clear().commit()
        Graph.prefs.contactsEnabled = false
        Graph.prefs.clipboardEnabled = false
        Graph.prefs.showClock = true
        Graph.prefs.showDate = true
        Graph.prefs.closeAfterAction = false
        Graph.prefs.resultCount = 14
        Graph.applyPrefs()
        Graph.useItemsForTesting(items())
        shell("mkdir -p $folder")
    }

    @After
    fun tearDown() {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        }
        Graph.prefs.store.edit().clear().commit()
        Graph.applyPrefs()
        Graph.useItemsForTesting(null)
    }

    private fun settle() {
        instrumentation.waitForIdleSync()
        device.waitForIdle()
        Thread.sleep(900)
    }

    private fun shoot(name: String) {
        shell("screencap -p $folder/$name.png")
        val size = shell("stat -c %s $folder/$name.png").trim().toLongOrNull() ?: 0L
        assertTrue("screenshot $name is empty", size > 2000)
    }

    private fun gallery(activity: Activity): View {
        val palette = Ui.palette(activity)
        val adapter = ResultAdapter(activity, noop, palette)
        adapter.installedMessengers = setOf(ContactAction.WHATSAPP, ContactAction.WHATSAPP_BUSINESS)
        adapter.submit(items())
        adapter.selected = 1
        val list = ListView(activity).apply {
            this.adapter = adapter
            divider = null
            dividerHeight = 0
            selector = ColorDrawable(Color.TRANSPARENT)
            val side = Ui.dp(activity, 8)
            setPadding(side, 0, side, 0)
            clipToPadding = false
        }
        return Ui.column(activity).apply {
            setBackgroundColor(palette.background)
            fitsSystemWindows = true
            addView(list, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun capture(label: String, theme: String, arabic: Boolean, typed: Boolean) {
        Graph.prefs.theme = theme
        if (arabic) {
            assumeTrue(Build.VERSION.SDK_INT >= 33)
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ar")
        }
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            settle()
            shoot("search-$label")
            if (typed) {
                onView(withId(R.id.search_input)).perform(replaceText("as"))
                settle()
                shoot("typing-$label")
            }
            scenario.onActivity { activity ->
                activity.window.insetsController?.hide(WindowInsets.Type.ime())
                activity.setContentView(gallery(activity))
            }
            settle()
            shoot("rows-$label")
        }
    }

    @Test
    fun light() = capture("light", "light", arabic = false, typed = true)

    @Test
    fun dark() = capture("dark", "dark", arabic = false, typed = false)

    @Test
    fun arabicLight() = capture("rtl-light", "light", arabic = true, typed = true)

    @Test
    fun arabicDark() = capture("rtl-dark", "dark", arabic = true, typed = false)
}
