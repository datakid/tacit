package app.tacit

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.WindowManager
import android.widget.EditText
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.fixtures.Fixtures
import app.tacit.ui.SearchActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.hasToString
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    private lateinit var scenario: ActivityScenario<SearchActivity>

    @Before
    fun setUp() {
        val prefs = Graph.prefs
        prefs.barAtBottom = false
        prefs.closeAfterAction = false
        prefs.contactsEnabled = false
        prefs.clipboardEnabled = false
        prefs.clipBiometric = false
        prefs.resultCount = 8
        Graph.applyPrefs()
        Idle.await()
        Graph.useItemsForTesting(Fixtures.all())
        Intents.init()
        intending(ExternalIntents.matcher()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        scenario = ActivityScenario.launch(SearchActivity::class.java)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @After
    fun tearDown() {
        scenario.close()
        Intents.release()
        Graph.useItemsForTesting(null)
        Idle.await()
    }

    private fun type(text: String) {
        onView(withId(R.id.search_input)).perform(replaceText(text))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun rows(): List<SearchItem> {
        var result: List<SearchItem> = emptyList()
        scenario.onActivity { activity ->
            val list = findList(activity.window.decorView)
            result = (0 until (list?.adapter?.count ?: 0)).map { list!!.adapter.getItem(it) as SearchItem }
        }
        return result
    }

    private fun findList(view: android.view.View): ListView? {
        if (view is ListView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) findList(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test
    fun searchFieldIsFocused() {
        scenario.onActivity { activity ->
            val field = activity.findViewById<EditText>(R.id.search_input)
            assertTrue(field.hasFocus())
        }
    }

    @Test
    fun searchFieldSuppressesSuggestions() {
        scenario.onActivity { activity ->
            val field = activity.findViewById<EditText>(R.id.search_input)
            assertTrue(field.inputType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
        }
    }

    @Test
    fun typoQueryShowsContact() {
        type("gos")
        assertTrue(rows().any { it.label == "Asmaa Gouda" })
    }

    @Test
    fun arabicContactFromLatin() {
        type("asmaa")
        assertTrue(rows().any { it.label == "أسماء جودة" })
    }

    @Test
    fun calculatorRowFirst() {
        type("2+2")
        val first = rows().first()
        assertEquals(ItemKind.CALCULATOR, first.kind)
        assertEquals("4", first.label)
    }

    @Test
    fun webRowLast() {
        type("lofi beats")
        assertEquals(ItemKind.WEB, rows().last().kind)
    }

    @Test
    fun enterOnWebRowOpensBrowser() {
        type("ddg tacit launcher")
        onView(withId(R.id.search_input)).perform(pressImeActionButton())
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(hasToString(startsWith("https://duckduckgo.com/?q=tacit")))))
    }

    @Test
    fun phoneNumberGivesDialIntent() {
        type("01001234567")
        assertTrue(rows().any { it.payload == "dial|01001234567" })
        onView(withId(R.id.search_input)).perform(pressImeActionButton())
        intended(hasAction(Intent.ACTION_DIAL))
    }

    @Test
    fun emptyQueryShowsNothingButPins() {
        type("")
        assertTrue(rows().all { it.pinned })
    }

    @Test
    fun clipboardScopeSetsFlagSecure() {
        type("cb ")
        scenario.onActivity { activity ->
            val flags = activity.window.attributes.flags
            assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        onView(withId(R.id.search_input)).perform(clearText())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity { activity ->
            assertFalse(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }

    @Test
    fun resultsAreNotCoveredByKeyboard() {
        type("a")
        scenario.onActivity { activity ->
            val list = findList(activity.window.decorView)!!
            val field = activity.findViewById<EditText>(R.id.search_input)
            val listRect = android.graphics.Rect().also { list.getGlobalVisibleRect(it) }
            val fieldRect = android.graphics.Rect().also { field.getGlobalVisibleRect(it) }
            assertTrue(listRect.height() > 0)
            assertTrue(fieldRect.height() > 0)
        }
    }
}
