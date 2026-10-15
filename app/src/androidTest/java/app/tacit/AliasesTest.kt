package app.tacit

import android.app.Activity
import android.app.Instrumentation
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.fixtures.Fixtures
import app.tacit.ui.AliasesActivity
import app.tacit.ui.SearchActivity
import app.tacit.ui.SecondaryMenu
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AliasesTest {

    private val touched = ArrayList<String>()
    private lateinit var sample: File

    private fun fileKey() = "file:${sample.absolutePath}"

    @Before
    fun setUp() {
        val prefs = Graph.prefs
        prefs.barAtBottom = false
        prefs.closeAfterAction = false
        prefs.contactsEnabled = false
        prefs.clipboardEnabled = false
        prefs.resultCount = 8
        Graph.applyPrefs()
        sample = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!, "qlm3-sample.pdf")
            .apply { writeText("sample") }
        Graph.useItemsForTesting(Fixtures.apps())
        Intents.init()
        intending(ExternalIntents.matcher()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        touched.forEach { Graph.aliases.set(it, emptyList()) }
        Graph.aliases.set(fileKey(), emptyList())
        Graph.useItemsForTesting(null)
        sample.delete()
    }

    private fun alias(key: String, vararg values: String) {
        touched.add(key)
        Graph.aliases.set(key, values.toList())
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
    fun storeFoldsCaseAccentsAndDuplicates() {
        alias("test:m3", "Café", "cafe", " tea ", "", "CAFÉ")
        assertEquals(listOf("Café", "tea"), Graph.aliases.get("test:m3"))
    }

    @Test
    fun keysForAliasIgnoresSpelling() {
        alias("test:m3a", "Mama")
        alias("test:m3b", "mama", "chat")
        assertEquals(setOf("test:m3a", "test:m3b"), Graph.aliases.keysForAlias("  MAMA ").toSet())
    }

    @Test
    fun versionChangesWhenAliasesChange() {
        val before = Graph.aliases.version
        alias("test:m3v", "one")
        assertTrue(Graph.aliases.version > before)
    }

    @Test
    fun aliasedFileIsFoundByPrefixAndByExactAlias() {
        alias(fileKey(), "QLM3Taxes")
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("qlm3tax")
            val first = rows(scenario).first()
            assertEquals(fileKey(), first.key)
            assertEquals(sample.name, first.label)
            assertEquals(sample.absolutePath, first.payload)
            type("qlm3taxes")
            assertEquals(fileKey(), rows(scenario).first().key)
        }
    }

    @Test
    fun aliasedFileThatWasDeletedIsNotListed() {
        alias(fileKey(), "qlm3gone")
        sample.delete()
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("qlm3gone")
            assertTrue(rows(scenario).none { it.key == fileKey() })
        }
    }

    @Test
    fun fileMenuOffersAliasesAndSavesThem() {
        val item = SearchItem(fileKey(), ItemKind.FILE, sample.name, "~", payload = sample.absolutePath)
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { SecondaryMenu.show(it, item) {} }
            onView(withText(R.string.menu_alias)).perform(click())
            onView(withHint(R.string.alias_hint)).perform(replaceText("qlm3one, QLM3ONE, qlm3two"), closeSoftKeyboard())
            onView(withText(R.string.save)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        assertEquals(listOf("qlm3one", "qlm3two"), Graph.aliases.get(fileKey()))
    }

    @Test
    fun sharedAliasListsEveryOwnerByName() {
        Graph.useItemsForTesting(listOf(
            SearchItem("app:m3/a", ItemKind.APP, "Qlm3 Alpha"),
            SearchItem("app:m3/b", ItemKind.APP, "Qlm3 Beta"),
            SearchItem("app:m3/c", ItemKind.APP, "Qlm3 Gamma")
        ))
        alias("app:m3/a", "QLM3X")
        alias("app:m3/b", "qlm3x")
        alias("app:m3/c", "qlm3unique")
        ActivityScenario.launch(AliasesActivity::class.java).use {
            val expected = InstrumentationRegistry.getInstrumentation().targetContext
                .getString(R.string.shared_aliases, "QLM3X: Qlm3 Alpha, Qlm3 Beta")
            onView(withText(expected)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun sharedAliasIgnoresItemsThatAreGone() {
        Graph.useItemsForTesting(listOf(SearchItem("app:m3/a", ItemKind.APP, "Qlm3 Alpha")))
        alias("app:m3/a", "qlm3y")
        alias("app:m3/uninstalled", "qlm3y")
        ActivityScenario.launch(AliasesActivity::class.java).use {
            onView(withText(containsString("qlm3y: "))).check { view, error ->
                if (view != null) throw AssertionError("a conflict was shown for an item that is gone")
            }
        }
    }
}
