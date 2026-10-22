package app.tacit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tacit.core.SearchItem
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class IconCacheTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val packageName = instrumentation.targetContext.packageName
    private var savedMode = ""

    @Before
    fun setUp() {
        savedMode = Graph.prefs.iconMode
        Graph.prefs.iconMode = "color"
        Idle.await()
        onMain { Graph.icons.clear() }
    }

    @After
    fun tearDown() {
        Graph.prefs.iconMode = savedMode
        onMain { Graph.icons.clear() }
    }

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
    }

    private fun render(item: SearchItem): Bitmap? {
        val done = CountDownLatch(1)
        var result: Bitmap? = null
        onMain {
            Graph.icons.load(item.key, item.payload, Color.BLACK, { true }) { bitmap ->
                result = bitmap
                done.countDown()
            }
        }
        assertTrue("the icon for ${item.key} was not rendered in time", done.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun cachedNow(key: String): Bitmap? {
        var bitmap: Bitmap? = null
        onMain { bitmap = Graph.icons.cached(key) }
        return bitmap
    }

    @Test
    fun evictingAPackageDropsOnlyItsIcons() {
        val apps = Graph.apps.appItems()
        val mine = apps.first { Graph.apps.packageOf(it.payload) == packageName }
        val other = apps.first { Graph.apps.packageOf(it.payload) != packageName }
        assertNotNull(render(mine))
        assertNotNull(render(other))
        assertNotNull(cachedNow(mine.key))
        assertNotNull(cachedNow(other.key))
        onMain { Graph.icons.evictPackage(packageName) }
        assertNull(cachedNow(mine.key))
        assertNotNull(cachedNow(other.key))
    }

    @Test
    fun anEvictedIconRendersAgain() {
        val mine = Graph.apps.appItems().first { Graph.apps.packageOf(it.payload) == packageName }
        assertNotNull(render(mine))
        onMain { Graph.icons.evictPackage(packageName) }
        assertNull(cachedNow(mine.key))
        assertNotNull(render(mine))
        assertNotNull(cachedNow(mine.key))
    }

    @Test
    fun evictingAnUnknownPackageChangesNothing() {
        val mine = Graph.apps.appItems().first { Graph.apps.packageOf(it.payload) == packageName }
        assertNotNull(render(mine))
        onMain { Graph.icons.evictPackage("no.such.package") }
        assertNotNull(cachedNow(mine.key))
    }
}
