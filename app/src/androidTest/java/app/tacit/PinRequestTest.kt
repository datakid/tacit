package app.tacit

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.ParcelFileDescriptor
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.tacit.actions.PinRequestActivity
import app.tacit.actions.PinShortcut
import app.tacit.core.ItemKind
import app.tacit.core.RefreshSources
import app.tacit.core.SearchItem
import app.tacit.ui.SearchActivity
import app.tacit.ui.SecondaryMenu
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class PinRequestTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val shortcutId = "pinrequest-test"
    private val manager: ShortcutManager get() = context.getSystemService(ShortcutManager::class.java)
    private lateinit var originalHome: String

    @Before
    fun setUp() {
        originalHome = homePackage()
        if (originalHome != context.packageName) {
            val output = shell("cmd package set-home-activity --user 0 ${context.packageName}/app.tacit.ui.SearchActivity")
            waitFor("Tacit did not become the home app: $output") { homePackage() == context.packageName }
        }
        refresh()
    }

    @After
    fun tearDown() {
        Graph.apps.items().firstOrNull { it.payload.startsWith("${context.packageName}|$shortcutId|") }?.let {
            Graph.apps.unpinShortcut(it.payload)
        }
        Idle.await()
        if (originalHome != context.packageName) {
            val output = shell("cmd package set-home-activity --user 0 $originalHome")
            waitFor("the home app was not restored: $output") { homePackage() == originalHome }
        }
        Graph.requestRefresh(RefreshSources.APPS)
        Idle.await()
    }

    private fun homePackage(): String {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName.orEmpty()
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { String(it.readBytes()) }
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(message, condition())
    }

    private fun refresh() {
        Graph.requestRefresh(RefreshSources.APPS)
        Idle.await()
    }

    private fun pinned(): SearchItem? =
        Graph.apps.items().firstOrNull { it.kind == ItemKind.ACTION && it.payload.startsWith("${context.packageName}|$shortcutId|") }

    private fun request(): Boolean {
        val info = ShortcutInfo.Builder(context, shortcutId)
            .setShortLabel("Pin request test")
            .setIntent(Intent(Intent.ACTION_MAIN).setClassName(context, SearchActivity::class.java.name))
            .build()
        return manager.requestPinShortcut(info, null)
    }

    private fun button(label: Int) = By.text(Pattern.compile(Pattern.quote(context.getString(label)), Pattern.CASE_INSENSITIVE))

    private fun choose(label: Int) {
        val found = device.wait(Until.findObject(button(label)), 10_000)
        assertNotNull("button ${context.getString(label)} missing", found)
        found.click()
        assertTrue(device.wait(Until.gone(button(label)), 10_000))
    }

    private fun pinThroughTheDialog(): SearchItem {
        assertTrue("the launcher refused the request", request())
        choose(R.string.pin_add)
        waitFor("the pinned shortcut never appeared") {
            refresh()
            pinned() != null
        }
        return pinned()!!
    }

    @Test
    fun theLauncherOffersTacitTheRequestWhileItIsHome() {
        assertTrue(manager.isRequestPinShortcutSupported)
        assertTrue(context.getSystemService(LauncherApps::class.java).hasShortcutHostPermission())
    }

    @Test
    fun addHomeScreenIsHiddenWhileTacitIsTheHomeApp() {
        assertTrue(manager.isRequestPinShortcutSupported)
        assertFalse(PinShortcut.supported(context))
    }

    @Test
    fun acceptingAddsTheShortcutToTheSearchableList() {
        val item = pinThroughTheDialog()
        assertEquals(ItemKind.ACTION, item.kind)
        assertEquals("Pin request test", item.label)
        assertTrue(Graph.apps.isPinnedShortcut(item.key))
        assertTrue(manager.pinnedShortcuts.any { it.id == shortcutId })
    }

    @Test
    fun cancellingKeepsTheShortcutOut() {
        assertTrue("the launcher refused the request", request())
        choose(R.string.cancel)
        refresh()
        assertEquals(null, pinned())
        assertFalse(manager.pinnedShortcuts.any { it.id == shortcutId })
    }

    @Test
    fun theDialogNamesTheShortcutAndItsApp() {
        assertTrue("the launcher refused the request", request())
        try {
            assertNotNull(device.wait(Until.findObject(By.text("Pin request test")), 10_000))
            assertNotNull(device.findObject(By.text(Graph.apps.labelFor(context.packageName))))
        } finally {
            choose(R.string.cancel)
        }
    }

    @Test
    fun theMenuRemovesAPinnedShortcut() {
        val item = pinThroughTheDialog()
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { SecondaryMenu.show(it, item) {} }
            val entry = device.wait(Until.findObject(By.text(context.getString(R.string.menu_unpin_shortcut))), 10_000)
            assertNotNull("Remove from Tacit missing", entry)
            entry.click()
            waitFor("the shortcut was not unpinned") {
                refresh()
                pinned() == null
            }
        }
        assertFalse(manager.pinnedShortcuts.any { it.id == shortcutId })
    }

    @Test
    fun shortcutsDeclaredByAnAppCannotBeRemoved() {
        fun declared() = Graph.apps.items().filter { it.kind == ItemKind.ACTION && it.payload.startsWith("${context.packageName}|settings|") }
        waitFor("manifest shortcut missing") {
            refresh()
            declared().isNotEmpty()
        }
        for (item in declared()) assertFalse(Graph.apps.isPinnedShortcut(item.key))
    }

    @Test
    fun aRequestWithoutAnItemFinishesQuietly() {
        val intent = Intent("android.content.pm.action.CONFIRM_PIN_SHORTCUT").setClass(context, PinRequestActivity::class.java)
        ActivityScenario.launch<PinRequestActivity>(intent).use { scenario ->
            waitFor("the activity stayed open") { scenario.state == Lifecycle.State.DESTROYED }
        }
        assertFalse(device.hasObject(button(R.string.pin_add)))
    }
}
