package app.tacit

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.core.QuicklinkType
import app.tacit.data.Backup
import app.tacit.data.Quicklink
import app.tacit.data.Snippet
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
}
