package app.tacit

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.actions.IntentSanitizer
import app.tacit.actions.LegacyShortcuts
import app.tacit.core.ExtraType
import app.tacit.core.IntentExtra
import app.tacit.core.IntentSpec
import app.tacit.core.QuicklinkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentSanitizerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val own: String get() = context.packageName

    private val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    private fun view(data: String) = Intent(Intent.ACTION_VIEW, Uri.parse(data))

    @Test
    fun aFullSpecSurvivesTheTripThroughTheStoredText() {
        val spec = IntentSpec(
            action = Intent.ACTION_VIEW,
            data = "https://example.com/a?b=c&d=e",
            mime = "text/html",
            categories = listOf("android.intent.category.BROWSABLE", "android.intent.category.DEFAULT"),
            component = "com.example.app/com.example.app.Main",
            extras = listOf(
                IntentExtra("big", ExtraType.LONG, "10000000000"),
                IntentExtra("flag", ExtraType.BOOLEAN, "true"),
                IntentExtra("k", ExtraType.STRING, "a,b;c é ع"),
                IntentExtra("n", ExtraType.INT, "-5")
            )
        )
        val built = IntentSanitizer.build(spec, own)!!
        val stored = IntentSanitizer.encode(built)
        assertTrue(stored.startsWith("intent:"))
        assertEquals(spec, IntentSanitizer.specOfTarget(stored, own))
        assertEquals(stored, IntentSanitizer.canonical(stored, own))
    }

    @Test
    fun anActionOnlyIntentRoundTrips() {
        val spec = IntentSpec(action = "android.settings.WIFI_SETTINGS")
        val stored = IntentSanitizer.encode(IntentSanitizer.build(spec, own)!!)
        assertEquals(spec, IntentSanitizer.specOfTarget(stored, own))
    }

    @Test
    fun buildRefusesASpecThatBreaksTheRules() {
        assertNull(IntentSanitizer.build(IntentSpec(), own))
        assertNull(IntentSanitizer.build(IntentSpec(component = "$own/.ui.SettingsActivity"), own))
        assertNull(IntentSanitizer.build(IntentSpec(action = "a.b", data = "file:///sdcard/x"), own))
        assertNull(IntentSanitizer.build(IntentSpec(action = "a.b", data = "content://$own.files/shared/x"), own))
        assertNotNull(IntentSanitizer.build(IntentSpec(action = "a.b", data = "content://com.android.contacts/contacts/1"), own))
    }

    @Test
    fun grantFlagsAreStrippedAndANewTaskIsAdded() {
        val raw = view("https://example.com").addFlags(grants or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val clean = IntentSanitizer.sanitize(raw, own)!!
        assertEquals(0, clean.flags and grants)
        val launch = IntentSanitizer.launchable(clean)
        assertEquals(0, launch.flags and grants)
        assertTrue(launch.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun storedTextWithGrantFlagsLaunchesWithoutThem() {
        val launch = IntentSanitizer.launchable("intent:#Intent;action=android.intent.action.VIEW;launchFlags=0x3;end", own)!!
        assertEquals(0, launch.flags and grants)
        assertTrue(launch.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun selectorsAreDropped() {
        val raw = Intent(Intent.ACTION_VIEW)
        raw.selector = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org"))
        val clean = IntentSanitizer.sanitize(raw, own)!!
        assertNull(clean.selector)
        assertNull(clean.dataString)
    }

    @Test
    fun packageRestrictionsAndClipDataAreDropped() {
        val raw = view("https://example.com").setPackage("com.example.app")
        raw.clipData = ClipData.newRawUri("x", Uri.parse("https://example.org"))
        val clean = IntentSanitizer.sanitize(raw, own)!!
        assertNull(clean.`package`)
        assertNull(clean.clipData)
        assertEquals("https://example.com", clean.dataString)
    }

    @Test
    fun theOwnAppCannotBeTargetedInAnyWay() {
        assertNull(IntentSanitizer.sanitize(Intent().setComponent(ComponentName(own, "$own.ui.SettingsActivity")), own))
        assertNull(IntentSanitizer.sanitize(view("https://example.com").setPackage(own), own))
        assertNull(IntentSanitizer.sanitize(view("content://$own.files/shared/a.txt"), own))
        assertNull(IntentSanitizer.sanitize(view("file:///sdcard/a.txt"), own))
        assertNull(IntentSanitizer.componentIntent("$own/$own.ui.SettingsActivity", own))
        assertNull(IntentSanitizer.parse("intent:#Intent;component=$own/.ui.SettingsActivity;end", own))
        assertNull(IntentSanitizer.launchable("intent:#Intent;component=$own/.ui.SettingsActivity;end", own))
    }

    @Test
    fun extrasOfOtherTypesAreDropped() {
        val raw = view("https://example.com")
            .putExtra("s", "x").putExtra("i", 1).putExtra("l", 2L).putExtra("b", true)
            .putExtra("f", 1.5f).putExtra("d", 2.0).putExtra("c", 'c').putExtra("bundle", Bundle())
        val clean = IntentSanitizer.sanitize(raw, own)!!
        assertEquals(setOf("s", "i", "l", "b"), clean.extras!!.keySet())
    }

    @Test
    fun twentyExtrasPassAndTwentyOneAreRefused() {
        val twenty = view("https://example.com")
        for (i in 1..20) twenty.putExtra("k$i", "v")
        assertNotNull(IntentSanitizer.sanitize(twenty, own))
        assertNull(IntentSanitizer.sanitize(Intent(twenty).putExtra("k21", "v"), own))
    }

    @Test
    fun longNamesAndValuesAreRefused() {
        assertNotNull(IntentSanitizer.sanitize(view("https://example.com").putExtra("k".repeat(100), "x".repeat(1000)), own))
        assertNull(IntentSanitizer.sanitize(view("https://example.com").putExtra("k".repeat(101), "x"), own))
        assertNull(IntentSanitizer.sanitize(view("https://example.com").putExtra("k", "x".repeat(1001)), own))
    }

    @Test
    fun anIntentWithNothingToLaunchIsRefused() {
        assertNull(IntentSanitizer.sanitize(Intent(), own))
        assertNull(IntentSanitizer.parse("intent:#Intent;end", own))
    }

    @Test
    fun onlyIntentUriTextIsParsed() {
        assertNull(IntentSanitizer.parse("not an intent", own))
        assertNull(IntentSanitizer.parse("https://example.com", own))
        assertNull(IntentSanitizer.parse("android-app://com.example.app", own))
        assertNull(IntentSanitizer.parse("", own))
    }

    @Test
    fun aScreenIntentTargetsOnlyTheComponent() {
        val intent = IntentSanitizer.componentIntent("com.android.settings/com.android.settings.Settings", own)!!
        assertEquals(ComponentName("com.android.settings", "com.android.settings.Settings"), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(intent.action)
        assertNull(IntentSanitizer.componentIntent("garbage", own))
    }

    @Test
    fun resolvesSeesAnActivityThatCanOpenAWebLink() {
        assertTrue(IntentSanitizer.resolves(context, view("https://example.com")))
        assertFalse(IntentSanitizer.resolves(context, Intent("tacit.test.action.NOBODY_HANDLES_THIS")))
    }

    private fun legacyResult(shortcut: Intent?, name: String?): Intent {
        val data = Intent()
        if (shortcut != null) data.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut)
        if (name != null) data.putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
        return data
    }

    @Test
    fun aLegacyShortcutResultBecomesACustomIntentDraft() {
        val draft = LegacyShortcuts.draft(context, legacyResult(view("https://example.com/a"), " Site "), "Tasker")!!
        assertEquals(QuicklinkType.INTENT, draft.type)
        assertEquals("Site", draft.name)
        assertEquals("Tasker", draft.detail)
        assertEquals("https://example.com/a", IntentSanitizer.parse(draft.target, own)!!.dataString)
    }

    @Test
    fun aLegacyShortcutWithoutANameUsesTheIntentSummary() {
        val draft = LegacyShortcuts.draft(context, legacyResult(Intent("a.b.ACTION"), null), "")!!
        assertEquals("a.b.ACTION", draft.name)
        assertEquals("a.b.ACTION", draft.detail)
    }

    @Test
    fun aLegacyShortcutThatBreaksTheRulesIsNotKept() {
        assertNull(LegacyShortcuts.draft(context, legacyResult(null, "Nothing"), ""))
        assertNull(LegacyShortcuts.draft(context, legacyResult(Intent().setComponent(ComponentName(own, "$own.ui.SettingsActivity")), "Self"), ""))
        assertNull(LegacyShortcuts.draft(context, legacyResult(view("file:///sdcard/x"), "File"), ""))
        assertNull(LegacyShortcuts.draft(context, legacyResult(view("https://example.com").addFlags(grants).setPackage(own), "Own"), ""))
    }

    @Test
    fun aLegacyShortcutKeepsNoGrantFlags() {
        val draft = LegacyShortcuts.draft(context, legacyResult(view("https://example.com").addFlags(grants), "Granted"), "")!!
        assertFalse(draft.target.contains("launchFlags=0x1"))
        assertEquals(0, IntentSanitizer.launchable(draft.target, own)!!.flags and grants)
    }

    @Test
    fun withoutAPinRequestNothingIsAccepted() {
        assertFalse(LegacyShortcuts.accept(context, Intent()))
        assertFalse(LegacyShortcuts.accept(context, legacyResult(view("https://example.com"), "Plain")))
    }

    @Test
    fun everyLegacyHandlerPointsAtAnInstalledActivity() {
        for (handler in LegacyShortcuts.handlers(context)) {
            val component = ComponentName.unflattenFromString(handler.payload)!!
            context.packageManager.getActivityInfo(component, 0)
            assertEquals(Intent.ACTION_CREATE_SHORTCUT, LegacyShortcuts.createIntent(handler.payload)!!.action)
        }
    }
}
