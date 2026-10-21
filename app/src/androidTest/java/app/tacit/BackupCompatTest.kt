package app.tacit

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.core.QuicklinkType
import app.tacit.data.Backup
import app.tacit.data.Quicklink
import app.tacit.data.Snippet
import app.tacit.ui.SettingsActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupCompatTest {

    private lateinit var prefsSnapshot: Map<String, Any?>
    private lateinit var aliasSnapshot: Map<String, List<String>>
    private lateinit var snippetSnapshot: List<Snippet>
    private lateinit var quicklinkSnapshot: List<Quicklink>

    private val fixtureText: String by lazy {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        assets.open("backup-1.0.0.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private val fixture: JSONObject by lazy { JSONObject(fixtureText) }

    @Before
    fun setUp() {
        prefsSnapshot = Graph.prefs.store.all.entries.associate { (key, value) -> key to (if (value is Set<*>) value.toSet() else value) }
        aliasSnapshot = Graph.aliases.all()
        snippetSnapshot = Graph.snippets.all()
        quicklinkSnapshot = Graph.quicklinks.all()
        wipe()
    }

    @After
    fun tearDown() {
        wipe()
        val editor = Graph.prefs.store.edit().clear()
        for ((key, value) in prefsSnapshot) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
            }
        }
        editor.commit()
        Graph.aliases.setMany(aliasSnapshot)
        Graph.snippets.replaceAll(snippetSnapshot)
        Graph.quicklinks.replaceAll(quicklinkSnapshot)
        Graph.quicklinkSource.reload()
    }

    private fun wipe() {
        Graph.prefs.store.edit().clear().commit()
        for (key in Graph.aliases.all().keys) Graph.aliases.set(key, emptyList())
        Graph.snippets.replaceAll(emptyList())
        Graph.quicklinks.replaceAll(emptyList())
    }

    private fun strings(array: JSONArray): List<String> = List(array.length()) { array.getString(it) }

    private fun expectedSnippets(source: JSONObject): List<Snippet> {
        val array = source.getJSONArray("snippets")
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            Snippet(item.getString("id"), item.getString("trigger"), item.getString("title"), item.getString("body"))
        }
    }

    private fun expectedQuicklinks(source: JSONObject): List<Quicklink> {
        val array = source.getJSONArray("quicklinks")
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            Quicklink(item.getString("id"), item.getString("name"), QuicklinkType.valueOf(item.getString("type")),
                item.getString("target"), item.getString("detail"))
        }
    }

    private fun expectedAliases(source: JSONObject): Map<String, List<String>> {
        val aliases = source.getJSONObject("aliases")
        return aliases.keys().asSequence().associateWith { strings(aliases.getJSONArray(it)) }
    }

    @Test
    fun theFixtureHoldsEveryOneZeroZeroShape() {
        assertEquals(1, fixture.getInt("tacit_backup"))
        assertEquals(3, fixture.getJSONObject("aliases").length())
        assertEquals(2, fixture.getJSONArray("snippets").length())
        assertEquals(
            setOf(QuicklinkType.URL, QuicklinkType.SETTING, QuicklinkType.APP, QuicklinkType.APP_SHORTCUT, QuicklinkType.FILE,
                QuicklinkType.FOLDER, QuicklinkType.CONTACT),
            expectedQuicklinks(fixture).map { it.type }.toSet()
        )
        assertEquals(7, fixture.getJSONArray("quicklinks").length())
    }

    @Test
    fun importRestoresEveryEntryOfAOneZeroZeroBackup() {
        Backup.import(fixtureText)

        assertEquals(expectedAliases(fixture), Graph.aliases.all())
        assertEquals(listOf("ql:bkc00001", "setting:android.settings.WIFI_SETTINGS"), Graph.aliases.keysForAlias("net").sorted())
        assertEquals(expectedSnippets(fixture), Graph.snippets.all())
        assertEquals(expectedQuicklinks(fixture), Graph.quicklinks.all())

        val expected = fixture.getJSONObject("prefs")
        val store = Graph.prefs.store
        for (key in expected.keys()) {
            if (key == "clip_last_ts" || key == "granted_trees") {
                assertFalse("$key must not be restored", store.contains(key))
                continue
            }
            when (val value = expected.get(key)) {
                is Boolean -> assertEquals(key, value, store.getBoolean(key, !value))
                is Int -> assertEquals(key, value, store.getInt(key, value + 1))
                is String -> assertEquals(key, value, store.getString(key, null))
                is JSONArray -> assertEquals(key, strings(value).toSet(), store.getStringSet(key, null))
                else -> fail("unexpected value type for $key")
            }
        }
    }

    @Test
    fun exportKeepsTheOneZeroZeroFormat() {
        Backup.import(fixtureText)
        val exported = JSONObject(Backup.export())

        assertEquals(1, exported.getInt("tacit_backup"))
        assertEquals(setOf("tacit_backup", "prefs", "aliases", "snippets", "quicklinks"), exported.keys().asSequence().toSet())
        assertEquals(expectedAliases(fixture), expectedAliases(exported))
        assertEquals(expectedSnippets(fixture), expectedSnippets(exported))
        assertEquals(expectedQuicklinks(fixture), expectedQuicklinks(exported))
        val prefs = exported.getJSONObject("prefs")
        assertEquals("dark", prefs.getString("theme"))
        assertEquals(12, prefs.getInt("result_count"))
        assertEquals(setOf("app:com.example.mail", "app:com.example.notes"), strings(prefs.getJSONArray("pins")).toSet())
    }

    @Test
    fun summaryCountsTheFixtureAndRejectsOtherFiles() {
        val summary = Backup.summarize(fixtureText)!!
        assertEquals(3, summary.aliases)
        assertEquals(2, summary.snippets)
        assertEquals(7, summary.quicklinks)
        assertNull(Backup.summarize("not json at all"))
        assertNull(Backup.summarize("{\"prefs\":{}}"))
        assertEquals(0, Backup.summarize("{\"tacit_backup\":1}")!!.quicklinks)
    }

    private fun withPickedBackup(block: (File) -> Unit) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        val file = File(folder, "backup-ui-test.json").apply { writeText(fixtureText) }
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
            block(file)
        } finally {
            Intents.release()
            file.delete()
        }
    }

    @Test
    fun exportWritesTheBackupToThePickedFile() {
        Backup.import(fixtureText)
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        val file = File(folder, "backup-export-ui-test.json").apply { writeText("x".repeat(100_000)) }
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
            ActivityScenario.launch(SettingsActivity::class.java).use {
                onView(withText(R.string.export_all)).perform(scrollTo(), click())
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                Idle.await()
            }
            val text = file.readText()
            assertFalse("the old content was not replaced", text.contains("xxxx"))
            val exported = JSONObject(text)
            assertEquals(1, exported.getInt("tacit_backup"))
            assertEquals(expectedAliases(fixture), expectedAliases(exported))
            assertEquals(expectedSnippets(fixture), expectedSnippets(exported))
            assertEquals(expectedQuicklinks(fixture), expectedQuicklinks(exported))
        } finally {
            Intents.release()
            file.delete()
        }
    }

    private fun confirmationText(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.import_confirm, 3, 2, 7)

    @Test
    fun importAsksFirstAndThenAppliesEverythingAtOnce() {
        withPickedBackup {
            ActivityScenario.launch(SettingsActivity::class.java).use {
                onView(withText(R.string.import_all)).perform(scrollTo(), click())
                onView(withText(confirmationText())).inRoot(isDialog()).check(matches(isDisplayed()))
                assertEquals(0, Graph.snippets.all().size)
                assertEquals(0, Graph.quicklinks.all().size)
                assertEquals(emptyMap<String, List<String>>(), Graph.aliases.all())
                onView(withText(R.string.import_all)).inRoot(isDialog()).perform(click())
                Idle.await()
                assertEquals(expectedAliases(fixture), Graph.aliases.all())
                assertEquals(expectedSnippets(fixture), Graph.snippets.all())
                assertEquals(expectedQuicklinks(fixture), Graph.quicklinks.all())
                assertEquals("dark", Graph.prefs.theme)
            }
        }
    }

    @Test
    fun cancellingTheImportKeepsEverything() {
        Graph.snippets.replaceAll(listOf(Snippet("keep-1", "k", "keep", "keep body")))
        withPickedBackup {
            ActivityScenario.launch(SettingsActivity::class.java).use {
                onView(withText(R.string.import_all)).perform(scrollTo(), click())
                onView(withText(confirmationText())).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
                Idle.await()
                assertEquals(listOf("keep-1"), Graph.snippets.all().map { it.id })
                assertEquals(emptyMap<String, List<String>>(), Graph.aliases.all())
                assertEquals(0, Graph.quicklinks.all().size)
            }
        }
    }
}
