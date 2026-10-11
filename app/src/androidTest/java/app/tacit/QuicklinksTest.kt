package app.tacit

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.core.QuicklinkType
import app.tacit.core.SearchItem
import app.tacit.data.Backup
import app.tacit.data.Quicklink
import app.tacit.fixtures.Fixtures
import app.tacit.ui.QuicklinksActivity
import app.tacit.ui.SearchActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.hasToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuicklinksTest {

    private val link = Quicklink("qltest01", "mapsx", QuicklinkType.URL, "https://example.com/search?q={query}", "")

    @Before
    fun setUp() {
        val prefs = Graph.prefs
        prefs.barAtBottom = false
        prefs.closeAfterAction = false
        prefs.contactsEnabled = false
        prefs.clipboardEnabled = false
        prefs.resultCount = 8
        Graph.applyPrefs()
        Graph.quicklinks.upsert(link)
        Graph.quicklinkSource.reload()
        Graph.useItemsForTesting(Fixtures.all() + Graph.quicklinkSource.items())
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        Graph.quicklinks.all().filter { it.id == link.id || it.name == "uitest" }.forEach { Graph.quicklinks.delete(it.id) }
        Graph.quicklinkSource.reload()
        Graph.useItemsForTesting(null)
    }

    private fun type(text: String) {
        onView(withId(R.id.search_input)).perform(replaceText(text))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun rows(scenario: ActivityScenario<SearchActivity>): List<SearchItem> {
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
    fun nameThenTextShowsRowAndOpensFilledUrl() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("mapsx cairo city")
            val first = rows(scenario).first()
            assertEquals("ql:qltest01:q", first.key)
            assertEquals("mapsx: cairo city", first.label)
            onView(withId(R.id.search_input)).perform(pressImeActionButton())
            intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(hasToString("https://example.com/search?q=cairo%20city"))))
        }
    }

    @Test
    fun nameAloneOpensTheBaseUrl() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("mapsx")
            assertEquals("ql:qltest01", rows(scenario).first().key)
            onView(withId(R.id.search_input)).perform(pressImeActionButton())
            intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(hasToString("https://example.com/search?q="))))
        }
    }

    @Test
    fun backupRoundTripKeepsQuicklinks() {
        val json = Backup.export()
        Graph.quicklinks.delete(link.id)
        assertNull(Graph.quicklinks.get(link.id))
        Backup.import(json)
        assertEquals(link, Graph.quicklinks.get(link.id))
    }

    @Test
    fun makerScreenSavesALink() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            onView(withText(R.string.add)).perform(click())
            onView(withHint(R.string.ql_name)).check(matches(isDisplayed()))
            onView(withHint(R.string.ql_name)).perform(replaceText("uitest"))
            onView(withHint(R.string.ql_url_hint)).perform(replaceText("example.org/?q={query}"), closeSoftKeyboard())
            onView(withText(R.string.save)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val saved = Graph.quicklinks.all().firstOrNull { it.name == "uitest" }
            assertNotNull("quicklink was not saved", saved)
            assertEquals("https://example.org/?q={query}", saved!!.target)
            assertEquals(QuicklinkType.URL, saved.type)
        }
    }

    @Test
    fun makerScreenRejectsUnsafeLink() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            onView(withText(R.string.add)).perform(click())
            onView(withHint(R.string.ql_name)).perform(replaceText("uitest"))
            onView(withHint(R.string.ql_url_hint)).perform(replaceText("javascript:alert(1)"), closeSoftKeyboard())
            onView(withText(R.string.save)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertNull(Graph.quicklinks.all().firstOrNull { it.name == "uitest" })
            onView(withHint(R.string.ql_name)).check(matches(isDisplayed()))
        }
    }
}
