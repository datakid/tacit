package app.tacit

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.ui.AliasesActivity
import app.tacit.ui.QuicklinksActivity
import app.tacit.ui.SettingsActivity
import app.tacit.ui.SnippetsActivity
import app.tacit.ui.Ui
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenPaddingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun findText(view: View, text: String): TextView? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findText(view.getChildAt(i), text)?.let { return it }
        }
        return null
    }

    private fun check(type: Class<out Activity>, title: Int) {
        val expected = context.getString(title)
        val minimum = Ui.dp(context, 12)
        ActivityScenario.launch(type).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val view = findText(activity.window.decorView, expected)
                assertNotNull("no title $expected", view)
                val where = IntArray(2)
                view!!.getLocationInWindow(where)
                assertTrue("$expected starts at ${where[0]} px", where[0] >= minimum)
                val width = activity.window.decorView.width
                assertTrue("$expected ends at ${where[0] + view.width} of $width px", where[0] + view.width <= width - minimum)
            }
        }
    }

    @Test
    fun settings() = check(SettingsActivity::class.java, R.string.settings)

    @Test
    fun quicklinks() = check(QuicklinksActivity::class.java, R.string.quicklinks)

    @Test
    fun snippets() = check(SnippetsActivity::class.java, R.string.snippets)

    @Test
    fun aliases() = check(AliasesActivity::class.java, R.string.aliases)
}
