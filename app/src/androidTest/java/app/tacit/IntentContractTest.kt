package app.tacit

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasPackage
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tacit.actions.Actions
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.ui.SearchActivity
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentContractTest {

    private lateinit var scenario: ActivityScenario<SearchActivity>

    @Before
    fun setUp() {
        Graph.prefs.directCall = false
        Graph.prefs.countryCode = "20"
        Intents.init()
        intending(ExternalIntents.matcher()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        scenario = ActivityScenario.launch(SearchActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        Intents.release()
    }

    private fun onActivity(block: (Activity) -> Unit) = scenario.onActivity { block(it) }

    @Test
    fun dialUsesActionDial() {
        onActivity { Actions.dial(it, "0100") }
        intended(allOf(hasAction(Intent.ACTION_DIAL), hasData(Uri.parse("tel:0100"))))
    }

    @Test
    fun smsUsesSendTo() {
        onActivity { Actions.sms(it, "0100") }
        intended(allOf(hasAction(Intent.ACTION_SENDTO), hasData(Uri.parse("smsto:0100"))))
    }

    @Test
    fun whatsappUsesWaMeWithCountryCode() {
        onActivity { Actions.whatsappNumber(it, "01001234567", Actions.WHATSAPP) }
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(Uri.parse("https://wa.me/201001234567")), hasPackage(Actions.WHATSAPP)))
    }

    @Test
    fun whatsappBusinessTargetsW4b() {
        onActivity { Actions.whatsappNumber(it, "+201001234567", Actions.WHATSAPP_BUSINESS) }
        intended(allOf(hasData(Uri.parse("https://wa.me/201001234567")), hasPackage(Actions.WHATSAPP_BUSINESS)))
    }

    @Test
    fun urlActionOpensView() {
        val item = SearchItem("url", ItemKind.ACTION, "Open", payload = "url|https://example.org")
        onActivity { Actions.primary(it, item, null) }
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(Uri.parse("https://example.org"))))
    }

    @Test
    fun mailActionUsesMailto() {
        val item = SearchItem("mail", ItemKind.ACTION, "Mail", payload = "mail|a@b.com")
        onActivity { Actions.primary(it, item, null) }
        intended(allOf(hasAction(Intent.ACTION_SENDTO), hasData(Uri.parse("mailto:a@b.com"))))
    }

    @Test
    fun settingActionOpensSettings() {
        val item = SearchItem("setting:wifi", ItemKind.SETTING, "Wi-Fi", payload = "action|android.settings.WIFI_SETTINGS")
        onActivity { Actions.primary(it, item, null) }
        intended(hasAction("android.settings.WIFI_SETTINGS"))
    }

    @Test
    fun appInfoOpensDetails() {
        val item = SearchItem("appinfo:x", ItemKind.SETTING, "x info", payload = "appinfo|com.example")
        onActivity { Actions.primary(it, item, null) }
        intended(allOf(hasAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS), hasData(Uri.parse("package:com.example"))))
    }
}
