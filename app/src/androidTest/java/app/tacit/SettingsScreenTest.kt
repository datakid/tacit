package app.tacit

import android.app.Activity
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.tacit.ui.SearchActivity
import app.tacit.ui.SettingsActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        Graph.prefs.store.edit().clear().commit()
        Graph.applyPrefs()
    }

    @After
    fun tearDown() {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        }
        Graph.prefs.store.edit().clear().commit()
        Graph.applyPrefs()
    }

    private fun rowCount(activity: Activity): Int {
        val scroll = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        return (scroll.getChildAt(0) as ViewGroup).childCount
    }

    private fun opensCleanly() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(rowCount(it) > 10) }
            scenario.recreate()
            scenario.onActivity { assertTrue(rowCount(it) > 10) }
        }
    }

    @Test
    fun freshInstall() = opensCleanly()

    @Test
    fun contactsEnabled() {
        Graph.prefs.contactsEnabled = true
        opensCleanly()
    }

    @Test
    fun filesEnabled() {
        Graph.prefs.filesEnabled = true
        Graph.prefs.filesHidden = true
        opensCleanly()
    }

    @Test
    fun clipboardEnabled() {
        Graph.prefs.clipboardEnabled = true
        opensCleanly()
    }

    @Test
    fun clipboardLockEnabled() {
        Graph.prefs.clipboardEnabled = true
        Graph.prefs.clipBiometric = true
        opensCleanly()
    }

    @Test
    fun everythingEnabled() {
        Graph.prefs.contactsEnabled = true
        Graph.prefs.filesEnabled = true
        Graph.prefs.clipboardEnabled = true
        Graph.prefs.clipBiometric = true
        Graph.prefs.starredAsPins = true
        Graph.prefs.hidden = setOf("app:a/b:0", "app:c/d:0")
        Graph.prefs.customEngines = "x|X|https://x.example/?q=%s"
        opensCleanly()
    }

    @Test
    fun arabicLocale() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ar")
        Graph.prefs.filesEnabled = true
        Graph.prefs.clipboardEnabled = true
        opensCleanly()
    }

    @Test
    fun openedFromSearchButton() {
        val device = UiDevice.getInstance(instrumentation)
        val marker = By.text(context.getString(R.string.show_clock)).pkg(context.packageName)
        val searchButton = By.desc(context.getString(R.string.settings)).pkg(context.packageName)
        ActivityScenario.launch(SearchActivity::class.java).use {
            var opened = false
            var clicks = 0
            while (!opened && clicks < 2) {
                onView(withContentDescription(context.getString(R.string.settings))).perform(click())
                clicks++
                opened = device.wait(Until.hasObject(marker), 6_000)
            }
            assertTrue(
                "settings did not open after $clicks clicks (package=${device.currentPackageName}, searchButtonShown=${device.hasObject(searchButton)})",
                opened
            )
        }
    }
}
