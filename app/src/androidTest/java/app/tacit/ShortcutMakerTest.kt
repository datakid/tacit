package app.tacit

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasPackageName
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.actions.ActivityCatalog
import app.tacit.actions.IntentSanitizer
import app.tacit.actions.PinShortcut
import app.tacit.actions.QuicklinkLauncher
import app.tacit.core.ExtraType
import app.tacit.core.IntentExtra
import app.tacit.core.IntentSpec
import app.tacit.core.QuicklinkType
import app.tacit.core.TargetState
import app.tacit.data.Quicklink
import app.tacit.sources.QuicklinkTargets
import app.tacit.ui.ActivityExplorer
import app.tacit.ui.QuicklinksActivity
import app.tacit.ui.SearchActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.hasToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutMakerTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val own: String get() = context.packageName
    private val settingsPackage = ActivityExplorer.SETTINGS_PACKAGE
    private lateinit var baseline: Set<String>

    @Before
    fun setUp() {
        baseline = Graph.quicklinks.all().map { it.id }.toSet()
        Intents.init()
        intending(ExternalIntents.matcher()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        waitFor("apps never loaded") { Graph.apps.mainProfileApps().isNotEmpty() }
    }

    @After
    fun tearDown() {
        Intents.release()
        Graph.quicklinks.all().filter { it.id !in baseline }.forEach {
            Graph.quicklinks.delete(it.id)
            Graph.aliases.set("ql:${it.id}", emptyList())
        }
        Graph.quicklinkSource.reload()
        Idle.await()
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(message, condition())
    }

    private fun created(): List<Quicklink> = Graph.quicklinks.all().filter { it.id !in baseline }

    private fun add(type: QuicklinkType, target: String, detail: String = "", name: String = "mk ${type.name.lowercase()}", icon: String = "glyph"): Quicklink {
        val link = Quicklink(Graph.quicklinks.newId(), name, type, target, detail, icon)
        Graph.quicklinks.upsert(link)
        Graph.quicklinkSource.reload()
        return link
    }

    @Suppress("DEPRECATION")
    private fun settingsActivities() = if (android.os.Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(settingsPackage, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())).activities.orEmpty()
    } else {
        context.packageManager.getPackageInfo(settingsPackage, PackageManager.GET_ACTIVITIES).activities.orEmpty()
    }

    private fun tap(text: Int) {
        onView(withText(text)).perform(scrollTo(), click())
    }

    private fun tapText(text: String) {
        onView(withText(text)).perform(scrollTo(), click())
    }

    private fun openChooser() {
        onView(withText(R.string.add)).perform(click())
        onView(withText(R.string.ql_type_url)).perform(click())
    }

    private fun chooseType(label: Int) {
        onData(hasToString(context.getString(label))).perform(click())
    }

    private fun iconChipText() = context.getString(R.string.ql_icon_value, context.getString(R.string.ql_icon_glyph))

    private fun cancelEditor() {
        onView(withText(R.string.cancel)).perform(click())
    }

    private fun firstListItem() {
        onData(anything()).inAdapterView(isAssignableFrom(ListView::class.java)).atPosition(0).perform(click())
    }

    private fun stubEditor() {
        intending(hasComponent(QuicklinksActivity::class.java.name)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @Test
    fun theCatalogListsOnlyScreensAnotherAppCanOpen() {
        val listed = ActivityCatalog.load(context, settingsPackage)
        assertTrue(listed.isNotEmpty())
        for (item in listed) {
            val info = context.packageManager.getActivityInfo(ComponentName.unflattenFromString(item.payload)!!, 0)
            assertTrue("${item.payload} is not exported", info.exported)
            assertTrue("${item.payload} is disabled", info.enabled)
            assertEquals("${item.payload} needs a permission", null, info.permission)
        }
        val hidden = settingsActivities().first { !it.exported }
        assertFalse(listed.any { it.payload == ComponentName(hidden.packageName, hidden.name).flattenToString() })
    }

    @Test
    fun screensWithoutALabelAreNamedAfterTheirClass() {
        val listed = ActivityCatalog.load(context, settingsPackage)
        assertTrue("no labelled screen", listed.any { it.subtitle.isNotEmpty() })
        for (item in listed) {
            val simple = item.payload.substringAfter('/').substringAfterLast('.')
            assertTrue(item.label.isNotBlank())
            if (item.subtitle.isEmpty()) assertEquals(simple, item.label) else assertEquals(simple, item.subtitle)
        }
    }

    @Test
    fun anUnknownPackageHasNoScreens() {
        assertTrue(ActivityCatalog.load(context, "com.mk.gone").isEmpty())
    }

    @Test
    fun theExplorerOffersEachAppOnceWithoutTacit() {
        val apps = ActivityExplorer.apps(own)
        assertTrue(apps.isNotEmpty())
        val packages = apps.map { Graph.apps.packageOf(it.payload) }
        assertEquals(packages.size, packages.toSet().size)
        assertFalse(own in packages)
        assertTrue(settingsPackage in packages)
    }

    @Test
    fun settingsExplorerSavesTheChosenScreen() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            openChooser()
            chooseType(R.string.ql_choice_settings_explorer)
            Idle.await()
            firstListItem()
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(QuicklinkType.ACTIVITY, saved.type)
        assertTrue(saved.target.startsWith("$settingsPackage/"))
        assertEquals(TargetState.OK, QuicklinkTargets.state(saved))
        assertTrue(saved.name.isNotBlank())
        assertEquals("glyph", saved.icon)
    }

    @Test
    fun appScreenPutsSettingsFirstInTheAppList() {
        val settings = Graph.apps.mainProfileApps().first { Graph.apps.packageOf(it.payload) == settingsPackage }
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            openChooser()
            chooseType(R.string.ql_type_activity)
            onView(withText(R.string.ql_choose_screen)).perform(click())
            onData(anything()).inAdapterView(isAssignableFrom(ListView::class.java)).atPosition(0).check(matches(withText(settings.label)))
            firstListItem()
            Idle.await()
            firstListItem()
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(QuicklinkType.ACTIVITY, saved.type)
        assertTrue(saved.target.startsWith("$settingsPackage/"))
        assertTrue(saved.detail.startsWith(settings.label + " · "))
    }

    @Test
    fun tryOpensTheScreenWithoutSavingIt() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            openChooser()
            chooseType(R.string.ql_choice_settings_explorer)
            Idle.await()
            firstListItem()
            tap(R.string.ql_try)
            intended(hasComponent(hasPackageName(settingsPackage)))
            cancelEditor()
        }
        assertTrue(created().isEmpty())
    }

    @Test
    fun anActivityQuicklinkStartsItsComponent() {
        val screen = ActivityCatalog.load(context, settingsPackage).first()
        val link = add(QuicklinkType.ACTIVITY, screen.payload, "Settings · ${screen.label}")
        assertEquals(TargetState.OK, QuicklinkTargets.state(link))
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, link.id, null)) }
            intended(hasComponent(ComponentName.unflattenFromString(screen.payload)!!.className))
        }
    }

    @Test
    fun aMissingScreenIsFlaggedAndOpensTheEditor() {
        stubEditor()
        val link = add(QuicklinkType.ACTIVITY, "com.mk.gone/com.mk.gone.Main", "gone")
        assertEquals(TargetState.MISSING, QuicklinkTargets.state(link))
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, link.id, null)) }
            intended(allOf(hasComponent(QuicklinksActivity::class.java.name), hasExtra(QuicklinksActivity.EXTRA_EDIT, link.id)))
        }
    }

    @Test
    fun aScreenThatCannotBeOpenedOpensTheEditorInstead() {
        Intents.release()
        Intents.init()
        stubEditor()
        val hidden = settingsActivities().first { !it.exported && it.enabled }
        val link = add(QuicklinkType.ACTIVITY, ComponentName(hidden.packageName, hidden.name).flattenToString(), "hidden")
        assertEquals(TargetState.OK, QuicklinkTargets.state(link))
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, link.id, null)) }
            intended(allOf(hasComponent(QuicklinksActivity::class.java.name), hasExtra(QuicklinksActivity.EXTRA_EDIT, link.id)))
        }
    }

    private fun openIntentForm() {
        openChooser()
        chooseType(R.string.ql_type_intent)
    }

    private fun typeInto(hint: Int, text: String) {
        onView(withHint(hint)).perform(scrollTo(), replaceText(text), closeSoftKeyboard())
    }

    private fun settingsAction() = "android.settings.SETTINGS"

    @Test
    fun aCustomIntentIsSavedWhenSomethingCanOpenIt() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            openIntentForm()
            typeInto(R.string.ql_name, "mk settings")
            typeInto(R.string.ql_intent_action, settingsAction())
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(QuicklinkType.INTENT, saved.type)
        assertEquals(settingsAction(), saved.detail)
        assertEquals(IntentSpec(action = settingsAction()), IntentSanitizer.specOfTarget(saved.target, own))
        assertEquals(TargetState.OK, QuicklinkTargets.state(saved))
    }

    @Test
    fun aCustomIntentKeepsItsTypedExtras() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            openIntentForm()
            typeInto(R.string.ql_name, "mk extras")
            typeInto(R.string.ql_intent_action, settingsAction())
            tap(R.string.ql_extra_add)
            typeInto(R.string.ql_extra_key, "count")
            tap(R.string.ql_extra_text)
            typeInto(R.string.ql_extra_value, "7")
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
        }
        val saved = created().single()
        assertEquals(listOf(IntentExtra("count", ExtraType.INT, "7")), IntentSanitizer.specOfTarget(saved.target, own)!!.extras)
    }

    @Test
    fun tryingACustomIntentLetsItBeSavedEvenWhenTheCheckCannotSeeAHandler() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            openIntentForm()
            typeInto(R.string.ql_name, "mk tried")
            typeInto(R.string.ql_intent_action, "mk.test.action.TRIED")
            tap(R.string.ql_try)
            intended(hasAction("mk.test.action.TRIED"))
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
        }
        val saved = created().single()
        assertEquals("mk.test.action.TRIED", IntentSanitizer.specOfTarget(saved.target, own)!!.action)
    }

    @Test
    fun aCustomIntentAimedAtTacitIsRefused() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            openIntentForm()
            typeInto(R.string.ql_name, "mk self")
            typeInto(R.string.ql_intent_component, "$own/.ui.SettingsActivity")
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
            onView(withText(R.string.save)).check(matches(isDisplayed()))
            cancelEditor()
        }
        assertTrue(created().isEmpty())
    }

    @Test
    fun aCustomIntentOnTacitsOwnFilesIsRefused() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            openIntentForm()
            typeInto(R.string.ql_name, "mk files")
            typeInto(R.string.ql_intent_action, "android.intent.action.VIEW")
            typeInto(R.string.ql_intent_data, "content://$own.files/shared/a.txt")
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
            onView(withText(R.string.save)).check(matches(isDisplayed()))
            cancelEditor()
        }
        assertTrue(created().isEmpty())
    }

    @Test
    fun aCustomIntentNothingCanOpenIsRefusedUntilItIsTried() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            openIntentForm()
            typeInto(R.string.ql_name, "mk nobody")
            typeInto(R.string.ql_intent_action, "mk.test.action.NOBODY_HANDLES_THIS")
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
            onView(withText(R.string.save)).check(matches(isDisplayed()))
            cancelEditor()
        }
        assertTrue(created().isEmpty())
    }

    @Test
    fun aCustomIntentWithNothingToLaunchIsRefused() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            openIntentForm()
            typeInto(R.string.ql_name, "mk empty")
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
            onView(withText(R.string.save)).check(matches(isDisplayed()))
            cancelEditor()
        }
        assertTrue(created().isEmpty())
    }

    @Test
    fun anIntentQuicklinkAimedAtTacitNeverLaunches() {
        stubEditor()
        val link = add(QuicklinkType.INTENT, "intent:#Intent;component=$own/.ui.SettingsActivity;end", "self")
        assertEquals(TargetState.MISSING, QuicklinkTargets.state(link))
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, link.id, null)) }
            intended(allOf(hasComponent(QuicklinksActivity::class.java.name), hasExtra(QuicklinksActivity.EXTRA_EDIT, link.id)))
        }
    }

    @Test
    fun anIntentQuicklinkLaunchesItsIntent() {
        val spec = IntentSpec(action = "android.intent.action.VIEW", data = "https://example.com/mk")
        val link = add(QuicklinkType.INTENT, IntentSanitizer.encode(IntentSanitizer.build(spec, own)!!), spec.summary())
        assertEquals(TargetState.OK, QuicklinkTargets.state(link))
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(QuicklinkLauncher.launch(it, link.id, null)) }
            intended(allOf(hasAction("android.intent.action.VIEW"),
                hasData("https://example.com/mk")))
        }
    }

    @Test
    fun editingACustomIntentShowsItsFields() {
        val spec = IntentSpec(action = "android.intent.action.VIEW", data = "https://example.com/mk", extras = listOf(IntentExtra("k", ExtraType.STRING, "v")))
        val link = add(QuicklinkType.INTENT, IntentSanitizer.encode(IntentSanitizer.build(spec, own)!!), spec.summary())
        val intent = Intent(context, QuicklinksActivity::class.java).putExtra(QuicklinksActivity.EXTRA_EDIT, link.id)
        ActivityScenario.launch<QuicklinksActivity>(intent).use {
            val fields = listOf(
                R.string.ql_intent_action to "android.intent.action.VIEW",
                R.string.ql_intent_data to "https://example.com/mk",
                R.string.ql_extra_key to "k",
                R.string.ql_extra_value to "v"
            )
            for ((hint, text) in fields) {
                onView(allOf(withHint(hint), withText(text))).perform(scrollTo()).check(matches(isDisplayed()))
            }
        }
    }

    @Test
    fun letterIconsAreChosenInTheEditorAndStored() {
        ActivityScenario.launch(QuicklinksActivity::class.java).use {
            onView(withText(R.string.add)).perform(click())
            typeInto(R.string.ql_name, "Zed")
            typeInto(R.string.ql_url_hint, "example.org/mk")
            tapText(iconChipText())
            onView(withText(R.string.ql_icon_app)).check(doesNotExist())
            onView(withText(R.string.ql_icon_letters)).perform(click())
            onView(withText("ZE")).check(matches(isDisplayed()))
            onView(withText(R.string.save)).perform(click())
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
        }
        assertEquals("letter:ZE", created().single().icon)
    }

    @Test
    fun theAppIconIsOfferedForAppLinks() {
        val app = Graph.apps.mainProfileApps().first { Graph.apps.packageOf(it.payload) == settingsPackage }
        val link = add(QuicklinkType.APP, app.payload, app.label)
        val intent = Intent(context, QuicklinksActivity::class.java).putExtra(QuicklinksActivity.EXTRA_EDIT, link.id)
        ActivityScenario.launch<QuicklinksActivity>(intent).use {
            tapText(iconChipText())
            onView(withText(R.string.ql_icon_app)).perform(click())
            onView(withText(R.string.save)).perform(click())
            instrumentation.waitForIdleSync()
        }
        assertEquals("app", Graph.quicklinks.get(link.id)!!.icon)
    }

    private fun render(link: Quicklink): Bitmap {
        val drawable = PinShortcut.icon(context, link).loadDrawable(context)!!
        val bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 216, 216)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun pinnedIconsFollowTheIconChoice() {
        val app = Graph.apps.mainProfileApps().first { Graph.apps.packageOf(it.payload) == settingsPackage }
        val glyph = Quicklink("mkicon01", "Icons", QuicklinkType.APP, app.payload, app.label, "glyph")
        val appIcon = glyph.copy(icon = "app")
        val letters = glyph.copy(icon = "letter:ZQ")
        val other = glyph.copy(icon = "letter:AB")
        val shots = listOf(glyph, appIcon, letters, other).map { render(it) }
        for (i in shots.indices) for (j in i + 1 until shots.size) assertFalse("icons $i and $j are the same", shots[i].sameAs(shots[j]))
    }

    @Test
    fun theAppIconFallsBackToTheGlyphForAMissingApp() {
        val gone = Quicklink("mkicon02", "Gone", QuicklinkType.APP, "com.mk.gone/com.mk.gone.Main|0", "Gone", "app")
        assertTrue(render(gone).sameAs(render(gone.copy(icon = "glyph"))))
    }
}
