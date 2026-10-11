package app.tacit

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.tacit.ui.SearchActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryDisplayTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun setUp() {
        Graph.prefs.showClock = false
        Graph.prefs.showDate = false
        Graph.prefs.showBattery = true
        Graph.prefs.contactsEnabled = false
    }

    @After
    fun tearDown() {
        Graph.prefs.showBattery = false
    }

    @Test
    fun batteryLevelIsShownWhenEnabled() {
        ActivityScenario.launch(SearchActivity::class.java).use {
            val shown = device.wait(Until.hasObject(By.textContains("%")), 8000)
            assertTrue("battery text not shown", shown)
        }
    }

    @Test
    fun batteryLevelIsHiddenWhenDisabled() {
        Graph.prefs.showBattery = false
        ActivityScenario.launch(SearchActivity::class.java).use {
            instrumentation.waitForIdleSync()
            assertFalse("battery text shown although disabled", device.wait(Until.hasObject(By.textContains("%")), 1500))
        }
    }
}
