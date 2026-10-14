package app.tacit

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasCategories
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasPackage
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.actions.Actions
import app.tacit.actions.PinShortcut
import app.tacit.actions.QuicklinkLauncher
import app.tacit.actions.QuicklinkRunActivity
import app.tacit.actions.TacitFileProvider
import app.tacit.core.ItemKind
import app.tacit.core.QuicklinkType
import app.tacit.core.SearchItem
import app.tacit.core.TargetState
import app.tacit.data.Backup
import app.tacit.data.Quicklink
import app.tacit.sources.ContactInfo
import app.tacit.sources.QuicklinkTargets
import app.tacit.ui.AliasesActivity
import app.tacit.ui.QuicklinkMaker
import app.tacit.ui.QuicklinksActivity
import app.tacit.ui.SearchActivity
import app.tacit.ui.SecondaryMenu
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasToString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class QuicklinksV2Test {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var baseline: Set<String>
    private lateinit var sample: File

    @Before
    fun setUp() {
        Graph.prefs.directCall = false
        Graph.prefs.countryCode = "20"
        baseline = Graph.quicklinks.all().map { it.id }.toSet()
        val folder = context.getExternalFilesDir(null)!!
        sample = File(folder, "qlv2-sample.txt").apply { writeText("sample") }
        Intents.init()
        intending(ExternalIntents.matcher()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        Graph.quicklinks.all().filter { it.id !in baseline }.forEach {
            Graph.quicklinks.delete(it.id)
            Graph.aliases.set("ql:${it.id}", emptyList())
        }
        Graph.quicklinkSource.reload()
        Graph.useItemsForTesting(null)
        sample.delete()
    }

    private fun add(type: QuicklinkType, target: String, detail: String = "", name: String = "qlv2 ${type.name.lowercase()}"): Quicklink {
        val link = Quicklink(Graph.quicklinks.newId(), name, type, target, detail)
        Graph.quicklinks.upsert(link)
        Graph.quicklinkSource.reload()
        return link
    }

    private fun created(): List<Quicklink> = Graph.quicklinks.all().filter { it.id !in baseline }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("source never loaded", condition())
    }

    private fun stubEditor() {
        intending(hasComponent(QuicklinksActivity::class.java.name)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    private fun gonePath(): String = File(context.getExternalFilesDir(null)!!, "qlv2-gone.txt").absolutePath

    @Test
    fun missingPathShowsEditRow() {
        val link = add(QuicklinkType.FILE, gonePath(), "gone")
        assertEquals(TargetState.MISSING, QuicklinkTargets.state(link))
        val row = Graph.quicklinkSource.items().first { it.key == "ql:${link.id}" }
        assertEquals(context.getString(R.string.ql_missing), row.subtitle)
    }

    @Test
    fun unheldGrantShowsEditRow() {
        val link = add(QuicklinkType.FOLDER, "content://com.android.externalstorage.documents/tree/primary%3Aqlv2-never-granted", "gone")
        assertEquals(TargetState.MISSING, QuicklinkTargets.state(link))
        val row = Graph.quicklinkSource.items().first { it.key == "ql:${link.id}" }
        assertEquals(context.getString(R.string.ql_missing), row.subtitle)
    }

    @Test
    fun emptyTargetAsksForRepick() {
        val link = add(QuicklinkType.FILE, "", "kept name")
        assertEquals(TargetState.REPICK, QuicklinkTargets.state(link))
        val row = Graph.quicklinkSource.items().first { it.key == "ql:${link.id}" }
        assertEquals(context.getString(R.string.ql_repick), row.subtitle)
    }

    @Test
    fun missingAppIsFlagged() {
        val link = add(QuicklinkType.APP, "com.qlv2.gone/com.qlv2.gone.Main|0", "gone")
        assertEquals(TargetState.MISSING, QuicklinkTargets.state(link))
    }

    @Test
    fun launchingAMissingTargetOpensTheEditorInstead() {
        stubEditor()
        val link = add(QuicklinkType.FILE, gonePath(), "gone")
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, link.id, null)) }
            intended(allOf(hasComponent(QuicklinksActivity::class.java.name), hasExtra(QuicklinksActivity.EXTRA_EDIT, link.id)))
        }
    }

    @Test
    fun deletedDocumentWithGrantOpensTheEditorInstead() {
        stubEditor()
        val link = add(QuicklinkType.FILE, TacitFileProvider.uriFor(context, sample).toString(), sample.name)
        assertEquals(TargetState.OK, QuicklinkTargets.state(link))
        sample.delete()
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, link.id, null)) }
            intended(allOf(hasComponent(QuicklinksActivity::class.java.name), hasExtra(QuicklinksActivity.EXTRA_EDIT, link.id)))
        }
    }

    @Test
    fun existingFileOpensWithView() {
        val link = add(QuicklinkType.FILE, sample.absolutePath, sample.name)
        assertEquals(TargetState.OK, QuicklinkTargets.state(link))
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, link.id, null)) }
            intended(anyOf(hasAction(Intent.ACTION_VIEW), hasAction(Intent.ACTION_CHOOSER)))
        }
    }

    @Test
    fun contactLinksLaunchTheRightIntents() {
        val call = add(QuicklinkType.CONTACT, "call|0100", "x", "qlv2 call")
        val sms = add(QuicklinkType.CONTACT, "sms|0100", "x", "qlv2 sms")
        val wa = add(QuicklinkType.CONTACT, "wa|0100", "x", "qlv2 wa")
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, call.id, null)) }
            intended(allOf(hasAction(Intent.ACTION_DIAL), hasData(Uri.parse("tel:0100"))))
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, sms.id, null)) }
            intended(allOf(hasAction(Intent.ACTION_SENDTO), hasData(Uri.parse("smsto:0100"))))
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, wa.id, null)) }
            intended(allOf(hasAction(Intent.ACTION_VIEW), hasPackage(Actions.WHATSAPP)))
        }
    }

    @Test
    fun urlPlaceholdersAreFilled() {
        val link = add(QuicklinkType.URL, "https://example.com/?y={date:yyyy}&q={query}", "", "qlv2 url")
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, "${link.id}|a b", null)) }
            intended(allOf(hasAction(Intent.ACTION_VIEW),
                hasData(hasToString("https://example.com/?y=${LocalDate.now().year}&q=a%20b"))))
        }
    }

    @Test
    fun deepLinksAreBrowsable() {
        val link = add(QuicklinkType.URL, "qlv2app://open/{query}", "", "qlv2 deep")
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, "${link.id}|a b", null)) }
            intended(allOf(hasData(hasToString("qlv2app://open/a%20b")), hasCategories(setOf(Intent.CATEGORY_BROWSABLE))))
        }
    }

    @Test
    fun runActivityLaunchesTheLink() {
        val link = add(QuicklinkType.CONTACT, "call|0100", "x", "qlv2 run")
        val intent = Intent(context, QuicklinkRunActivity::class.java).putExtra(QuicklinkRunActivity.EXTRA_ID, link.id)
        ActivityScenario.launch<QuicklinkRunActivity>(intent).use { }
        intended(allOf(hasAction(Intent.ACTION_DIAL), hasData(Uri.parse("tel:0100"))))
    }

    @Test
    fun pinShortcutPointsAtTheRunActivity() {
        val link = add(QuicklinkType.CONTACT, "call|0100", "x", "qlv2 pin")
        val info = PinShortcut.info(context, link)
        assertEquals("ql-${link.id}", info.id)
        assertEquals("qlv2 pin", info.shortLabel.toString())
        assertEquals(QuicklinkRunActivity::class.java.name, info.intent!!.component!!.className)
        assertEquals(link.id, info.intent!!.getStringExtra(QuicklinkRunActivity.EXTRA_ID))
    }

    @Test
    fun filePickerSavesTheChosenDocument() {
        val uri = TacitFileProvider.uriFor(context, sample)
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            onView(withText(R.string.add)).perform(click())
            onView(withHint(R.string.ql_name)).perform(replaceText("qlv2 file"), closeSoftKeyboard())
            onView(withText(R.string.ql_type_url)).perform(click())
            onView(withText(R.string.ql_type_file)).perform(click())
            onView(withText(R.string.ql_pick_file)).perform(click())
            onView(withText(sample.name)).check(matches(isDisplayed()))
            onView(withText(R.string.save)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(QuicklinkType.FILE, saved.type)
        assertEquals(uri.toString(), saved.target)
        assertEquals(sample.name, saved.detail)
    }

    @Test
    fun folderPickerSavesTheChosenTree() {
        val uri = TacitFileProvider.uriFor(context, sample.parentFile!!)
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT_TREE))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            onView(withText(R.string.add)).perform(click())
            onView(withHint(R.string.ql_name)).perform(replaceText("qlv2 folder"), closeSoftKeyboard())
            onView(withText(R.string.ql_type_url)).perform(click())
            onView(withText(R.string.ql_type_folder)).perform(click())
            onView(withText(R.string.ql_pick_folder)).perform(click())
            onView(withText(R.string.save)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(QuicklinkType.FOLDER, saved.type)
        assertEquals(uri.toString(), saved.target)
    }

    @Test
    fun cancelledPickerKeepsTheFormUnchanged() {
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            onView(withText(R.string.add)).perform(click())
            onView(withHint(R.string.ql_name)).perform(replaceText("qlv2 cancel"), closeSoftKeyboard())
            onView(withText(R.string.ql_type_url)).perform(click())
            onView(withText(R.string.ql_type_file)).perform(click())
            onView(withText(R.string.ql_pick_file)).perform(click())
            onView(withText(R.string.ql_pick_file)).check(matches(isDisplayed()))
            onView(withText(R.string.save)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            onView(withText(R.string.save)).check(matches(isDisplayed()))
        }
        assertTrue(created().isEmpty())
    }

    @Test
    fun settingPickerSavesACatalogEntry() {
        waitFor { Graph.settings.items().isNotEmpty() }
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            onView(withText(R.string.add)).perform(click())
            onView(withText(R.string.ql_type_url)).perform(click())
            onView(withText(R.string.ql_type_setting)).perform(click())
            onView(withText(R.string.ql_choose)).perform(click())
            onData(anything()).inAdapterView(isAssignableFrom(ListView::class.java)).atPosition(0).perform(click())
            onView(withText(R.string.save)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(QuicklinkType.SETTING, saved.type)
        assertTrue(Graph.settings.items().any { it.payload == saved.target })
        assertTrue(saved.name.isNotEmpty())
    }

    @Test
    fun appPickerSavesAnInstalledApp() {
        waitFor { Graph.apps.appItems().isNotEmpty() }
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            onView(withText(R.string.add)).perform(click())
            onView(withText(R.string.ql_type_url)).perform(click())
            onView(withText(R.string.ql_type_app)).perform(click())
            onView(withText(R.string.ql_choose)).perform(click())
            onData(anything()).inAdapterView(isAssignableFrom(ListView::class.java)).atPosition(0).perform(click())
            onView(withText(R.string.save)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(QuicklinkType.APP, saved.type)
        assertTrue(Graph.apps.hasApp(saved.target))
    }

    @Test
    fun shortcutPickerShowsAnEmptyStateWithoutShortcuts() {
        waitFor { Graph.apps.appItems().isNotEmpty() }
        assumeTrue(Graph.apps.items().none { it.kind == ItemKind.ACTION })
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            onView(withText(R.string.add)).perform(click())
            onView(withText(R.string.ql_type_url)).perform(click())
            onView(withText(R.string.ql_type_shortcut)).perform(click())
            onView(withText(R.string.ql_choose)).perform(click())
            onView(withText(R.string.ql_nothing_found)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun editorOpensForTheRequestedLink() {
        val link = add(QuicklinkType.FILE, "", "kept name", "qlv2 repick")
        val intent = Intent(context, QuicklinksActivity::class.java).putExtra(QuicklinksActivity.EXTRA_EDIT, link.id)
        ActivityScenario.launch<QuicklinksActivity>(intent).use {
            onView(withHint(R.string.ql_name)).check(matches(withText("qlv2 repick")))
            onView(withText(R.string.ql_pick_file)).check(matches(isDisplayed()))
            onView(withText(R.string.ql_repick)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun editorExplainsAMissingTarget() {
        val link = add(QuicklinkType.FILE, gonePath(), "gone.txt", "qlv2 missing")
        val intent = Intent(context, QuicklinksActivity::class.java).putExtra(QuicklinksActivity.EXTRA_EDIT, link.id)
        ActivityScenario.launch<QuicklinksActivity>(intent).use {
            onView(withText("gone.txt")).check(matches(isDisplayed()))
            onView(withText(R.string.ql_missing_here)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun longPressMenuCreatesAQuicklinkForASetting() {
        waitFor { Graph.settings.items().isNotEmpty() }
        val item = Graph.settings.items().first()
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { SecondaryMenu.show(it, item) {} }
            onView(withText(R.string.menu_create_quicklink)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(QuicklinkType.SETTING, saved.type)
        assertEquals(item.payload, saved.target)
        assertEquals(item.label, saved.name)
    }

    @Test
    fun longPressMenuCreatesAFileQuicklink() {
        val item = SearchItem("file:${sample.absolutePath}", ItemKind.FILE, sample.name, "~", payload = sample.absolutePath)
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { SecondaryMenu.show(it, item) {} }
            onView(withText(R.string.menu_create_quicklink)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(QuicklinkType.FILE, saved.type)
        assertEquals(sample.absolutePath, saved.target)
        val folder = QuicklinkMaker.draftOf(SearchItem("file:x", ItemKind.FILE, "dir", "~", payload = sample.parentFile!!.absolutePath))
        assertEquals(QuicklinkType.FOLDER, folder!!.type)
    }

    @Test
    fun longPressMenuCreatesAContactActionQuicklink() {
        val info = ContactInfo("lk", 1L, "Qlv2 Mum", listOf("01012345678"), "01012345678", null, null, false)
        val item = SearchItem("contact:lk", ItemKind.CONTACT, "Qlv2 Mum", "01012345678", listOf("01012345678"), "1", info)
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { SecondaryMenu.show(it, item) {} }
            onView(withText(R.string.menu_create_quicklink)).perform(click())
            onView(withText(R.string.action_message)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(QuicklinkType.CONTACT, saved.type)
        assertEquals("sms|01012345678", saved.target)
        assertEquals(context.getString(R.string.action_message) + " Qlv2 Mum", saved.name)
    }

    @Test
    fun creatingTheSameQuicklinkTwiceKeepsOne() {
        val item = SearchItem("file:${sample.absolutePath}", ItemKind.FILE, sample.name, "~", payload = sample.absolutePath)
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity {
                QuicklinkMaker.create(it, item)
                QuicklinkMaker.create(it, item)
            }
        }
        assertEquals(1, created().size)
    }

    @Test
    fun backupDropsGrantsAndKeepsPaths() {
        val path = add(QuicklinkType.FILE, sample.absolutePath, sample.name, "qlv2 path")
        val grant = add(QuicklinkType.FOLDER, "content://com.android.externalstorage.documents/tree/primary%3ADocuments", "Documents", "qlv2 grant")
        val json = Backup.export()
        val exported = JSONObject(json).getJSONArray("quicklinks")
        val byId = (0 until exported.length()).associate { exported.getJSONObject(it).getString("id") to exported.getJSONObject(it) }
        assertEquals(sample.absolutePath, byId.getValue(path.id).getString("target"))
        assertEquals("", byId.getValue(grant.id).getString("target"))
        Graph.quicklinks.delete(path.id)
        Graph.quicklinks.delete(grant.id)
        Backup.import(json)
        assertEquals(path.target, Graph.quicklinks.get(path.id)!!.target)
        val restored = Graph.quicklinks.get(grant.id)!!
        assertEquals("", restored.target)
        assertEquals("Documents", restored.detail)
        assertEquals(TargetState.REPICK, QuicklinkTargets.state(restored))
    }

    @Test
    fun backupImportSkipsUnsafeLinks() {
        val bad = JSONObject().put("id", "qlv2bad1").put("name", "bad").put("type", "URL")
            .put("target", "javascript:alert(1)").put("detail", "")
        val file = JSONObject().put("id", "qlv2bad2").put("name", "file").put("type", "FILE")
            .put("target", "file:///etc/passwd").put("detail", "")
        val good = JSONObject().put("id", "qlv2ok01").put("name", "ok").put("type", "URL")
            .put("target", "https://example.com/{query}").put("detail", "")
        val root = JSONObject().put("tacit_backup", 1).put("quicklinks", JSONArray().put(bad).put(file).put(good))
        Backup.import(root.toString())
        assertNull(Graph.quicklinks.get("qlv2bad1"))
        assertNull(Graph.quicklinks.get("qlv2bad2"))
        assertNotNull(Graph.quicklinks.get("qlv2ok01"))
        assertFalse(Graph.quicklinks.all().any { it.target.startsWith("javascript") })
    }

    @Test
    fun aliasesScreenHasAQuicklinksTab() {
        val link = add(QuicklinkType.URL, "https://example.com/?q={query}", "", "qlv2 alias")
        Graph.useItemsForTesting(Graph.quicklinkSource.items())
        ActivityScenario.launch(AliasesActivity::class.java).use {
            onView(withText(R.string.cat_quicklinks)).perform(scrollTo(), click())
            onView(withText(containsString(link.name))).check(matches(isDisplayed()))
        }
    }
}
