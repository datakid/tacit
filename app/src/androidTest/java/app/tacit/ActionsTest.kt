package app.tacit

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.ListView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtraWithKey
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.tacit.actions.ExportActivity
import app.tacit.actions.QuickLauncher
import app.tacit.core.ExportContact
import app.tacit.core.ExportKind
import app.tacit.core.ExportNumber
import app.tacit.core.ItemKind
import app.tacit.core.QuickResult
import app.tacit.core.SearchItem
import app.tacit.data.ContactExport
import app.tacit.ui.SearchActivity
import app.tacit.ui.SecondaryMenu
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ActionsTest {

    @get:Rule
    val contacts: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS)

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var target: File

    @Before
    fun setUp() {
        val prefs = Graph.prefs
        prefs.barAtBottom = false
        prefs.closeAfterAction = false
        prefs.contactsEnabled = false
        prefs.clipboardEnabled = false
        prefs.resultCount = 8
        Graph.applyPrefs()
        target = File(context.getExternalFilesDir(null)!!, "m4-export.out").apply { delete() }
        Graph.useItemsForTesting(listOf(SearchItem("app:m4/photos", ItemKind.APP, "Photos"), SearchItem("app:m4/flipkart", ItemKind.APP, "Flipkart")))
        Intents.init()
        intending(ExternalIntents.matcher()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        ContactExport.reader = { ContactExport.readContacts(it) }
        Graph.useItemsForTesting(null)
        target.delete()
    }

    private fun type(text: String) {
        onView(withId(R.id.search_input)).perform(replaceText(text))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun enter() {
        onView(withId(R.id.search_input)).perform(pressImeActionButton())
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

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(message, condition())
    }

    private fun stubDestination() {
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(target))))
    }

    private fun sampleContacts() {
        ContactExport.reader = {
            sequenceOf(
                ExportContact("=cmd", listOf(ExportNumber("+201001234567", "Mobile", "CELL"))),
                ExportContact("أسماء جودة", listOf(ExportNumber("+966501234567", "Mobile", "CELL"), ExportNumber("0225551234", "Home", "HOME"))),
                ExportContact("Zed Tail", listOf(ExportNumber("999", "Other", null)))
            )
        }
    }

    @Test
    fun timerRowStartsTheClockTimer() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("timer 10 tea")
            val first = rows(scenario).first()
            assertEquals("act:timer", first.key)
            assertEquals("timer|600", first.payload)
            enter()
            intended(allOf(hasAction(AlarmClock.ACTION_SET_TIMER), hasExtra(AlarmClock.EXTRA_LENGTH, 600), hasExtra(AlarmClock.EXTRA_MESSAGE, "tea")))
        }
    }

    @Test
    fun alarmRowSetsTheAlarm() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("alarm 7:30 pm gym")
            assertEquals("act:alarm", rows(scenario).first().key)
            enter()
            intended(allOf(
                hasAction(AlarmClock.ACTION_SET_ALARM),
                hasExtra(AlarmClock.EXTRA_HOUR, 19),
                hasExtra(AlarmClock.EXTRA_MINUTES, 30),
                hasExtra(AlarmClock.EXTRA_MESSAGE, "gym")
            ))
        }
    }

    @Test
    fun eventRowOpensTheCalendarEditor() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("event dentist 14:30")
            assertEquals("act:event", rows(scenario).first().key)
            enter()
            intended(allOf(
                hasAction(Intent.ACTION_INSERT),
                hasData(CalendarContract.Events.CONTENT_URI),
                hasExtra(CalendarContract.Events.TITLE, "dentist"),
                hasExtraWithKey(CalendarContract.EXTRA_EVENT_BEGIN_TIME),
                hasExtraWithKey(CalendarContract.EXTRA_EVENT_END_TIME)
            ))
        }
    }

    @Test
    fun eventIntentsCarryTheRightTimes() {
        val timed = QuickLauncher.eventIntent(QuickResult.Event("x", LocalDate.of(2026, 10, 16), LocalTime.of(15, 0)))
        val begin = timed.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1)
        assertEquals(3_600_000L, timed.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1) - begin)
        assertFalse(timed.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        val allDay = QuickLauncher.eventIntent(QuickResult.Event("x", LocalDate.of(2026, 10, 16), null))
        val midnight = LocalDate.of(2026, 10, 16).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(midnight, allDay.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
        assertEquals(midnight + 86_400_000L, allDay.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1))
        assertTrue(allDay.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        val bare = QuickLauncher.eventIntent(QuickResult.Event("", null, null))
        assertFalse(bare.hasExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME))
        assertFalse(bare.hasExtra(CalendarContract.Events.TITLE))
    }

    @Test
    fun uuidRowShowsAValidUuidThatCopies() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("uuid")
            val first = rows(scenario).first()
            assertEquals("act:uuid", first.key)
            assertTrue(first.label, Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(first.label))
            assertEquals("copy|${first.label}", first.payload)
        }
    }

    @Test
    fun passwordRowIsCopiedAsHidden() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("pass 24")
            val first = rows(scenario).first()
            assertEquals("act:pass", first.key)
            assertEquals(24, first.label.length)
            assertEquals("copyhidden|${first.label}", first.payload)
        }
    }

    @Test
    fun transformRowShowsTheResult() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("upper hello مرحبا")
            val first = rows(scenario).first()
            assertEquals("HELLO مرحبا", first.label)
            assertEquals("copy|HELLO مرحبا", first.payload)
            type("b64 hello")
            assertEquals("aGVsbG8=", rows(scenario).first().label)
        }
    }

    @Test
    fun aKeywordAloneOffersToTransformTheClipboard() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("upper")
            val first = rows(scenario).first()
            assertEquals("act:clipxform:upper", first.key)
            assertEquals("xform|upper", first.payload)
        }
    }

    @Test
    fun diceCoinAndRandomGiveRows() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("roll 2d6")
            val dice = rows(scenario).first()
            assertEquals("act:dice", dice.key)
            assertTrue(dice.label.toInt() in 2..12)
            type("flip")
            assertTrue(rows(scenario).first().key == "act:coin")
            type("rand 5 7")
            assertTrue(rows(scenario).first().label.toInt() in 5..7)
        }
    }

    @Test
    fun exportListsTheThreeFormats() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("export")
            assertEquals(listOf("act:export:vcard", "act:export:csv", "act:export:numbers"), rows(scenario).take(3).map { it.key })
            type("export csv")
            assertEquals(listOf("act:export:csv"), rows(scenario).filter { it.key.startsWith("act:") }.map { it.key })
        }
    }

    @Test
    fun aBareKeywordWaitsBehindARealApp() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("flip")
            assertEquals(listOf("app:m4/flipkart", "act:coin"), rows(scenario).take(2).map { it.key })
        }
    }

    @Test
    fun ordinarySearchesAddNoActionRows() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("photos")
            assertTrue(rows(scenario).none { it.key.startsWith("act:") })
        }
    }

    @Test
    fun actionRowsOfferNoAliasOrPinMenu() {
        val item = SearchItem("act:uuid", ItemKind.ACTION, "x", "UUID", payload = "copy|x")
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { SecondaryMenu.show(it, item) {} }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            onView(withText(R.string.menu_alias)).check(doesNotExist())
            onView(withText(R.string.menu_pin)).check(doesNotExist())
        }
    }

    @Test
    fun csvExportWritesBomHeaderAndSafeRows() {
        sampleContacts()
        stubDestination()
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            type("export csv")
            assertEquals("act:export:csv", rows(scenario).first().key)
            enter()
            waitFor("export never finished") { target.exists() && target.readText().contains("Zed Tail") }
        }
        val text = target.readText(Charsets.UTF_8)
        assertTrue(text.startsWith("﻿\"name\",\"number\",\"type\"\r\n"))
        assertTrue(text.contains("\"'=cmd\",\"+201001234567\",\"Mobile\"\r\n"))
        assertTrue(text.contains("\"أسماء جودة\",\"+966501234567\",\"Mobile\"\r\n"))
        assertTrue(text.contains("\"أسماء جودة\",\"0225551234\",\"Home\"\r\n"))
    }

    @Test
    fun vcardExportWritesFoldedCardsWithArabicNames() {
        sampleContacts()
        stubDestination()
        ActivityScenario.launch(SearchActivity::class.java).use {
            type("export vcard")
            enter()
            waitFor("export never finished") { target.exists() && target.readText().contains("Zed Tail") }
        }
        val text = target.readText(Charsets.UTF_8)
        assertEquals(3, Regex("BEGIN:VCARD").findAll(text).count())
        assertTrue(text.contains("FN:أسماء جودة\r\n"))
        assertTrue(text.contains("TEL;TYPE=CELL:+966501234567\r\n"))
        assertTrue(text.contains("TEL;TYPE=HOME:0225551234\r\n"))
        assertTrue(text.contains("TEL:999\r\n"))
    }

    @Test
    fun numbersExportIsOneNumberPerLine() {
        sampleContacts()
        val count = ContactExport.write(context, Uri.fromFile(target), ExportKind.NUMBERS)
        assertEquals("+201001234567\n+966501234567\n0225551234\n999\n", target.readText(Charsets.UTF_8))
        assertEquals(3, count.contacts)
        assertEquals(4, count.numbers)
    }

    @Test
    fun cancellingThePickerWritesNothing() {
        sampleContacts()
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        ActivityScenario.launch(SearchActivity::class.java).use {
            type("export csv")
            enter()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(1500)
        }
        assertFalse(target.exists())
    }

    @Test
    fun exportActivityIgnoresAnUnknownKind() {
        val intent = Intent(context, ExportActivity::class.java).putExtra(ExportActivity.EXTRA_KIND, "xlsx")
        ActivityScenario.launch<ExportActivity>(intent).use { scenario ->
            waitFor("activity stayed open") { scenario.state == Lifecycle.State.DESTROYED }
        }
    }
}
