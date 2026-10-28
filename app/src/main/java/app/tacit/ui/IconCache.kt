package app.tacit.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.LruCache
import app.tacit.Graph
import app.tacit.Prefs
import app.tacit.core.IconBudget
import app.tacit.core.RowDensity

class IconCache(private val context: Context, private val prefs: Prefs) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val memory = object : LruCache<String, Bitmap>(IconBudget.bytes(Runtime.getRuntime().maxMemory())) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val renderer: Handler by lazy {
        val thread = HandlerThread("tacit-icons", Process.THREAD_PRIORITY_DEFAULT)
        thread.start()
        Handler(thread.looper)
    }
    private val packageOfKey = HashMap<String, String>()
    private var seenMode = ""
    private var seenDark = false
    private var seenDensity = ""
    @Volatile
    private var generation = 0

    fun cached(key: String): Bitmap? {
        sync()
        return memory.get(key)
    }

    fun load(key: String, payload: String, foreground: Int, wanted: () -> Boolean, done: (Bitmap?) -> Unit) {
        if (prefs.iconMode == "none") return done(null)
        sync()
        memory.get(key)?.let { return done(it) }
        val born = generation
        val size = Ui.dp(context, RowDensity.of(prefs.density).iconDp)
        renderer.post {
            if (!wanted() || born != generation) return@post
            val bitmap = runCatching { render(payload, foreground, size) }.getOrNull()
            Graph.main.post {
                if (born == generation) {
                    if (bitmap != null) {
                        memory.put(key, bitmap)
                        packageOfKey[key] = Graph.apps.packageOf(payload)
                    }
                    done(bitmap)
                }
            }
        }
    }

    fun clear() {
        generation++
        memory.evictAll()
        packageOfKey.clear()
    }

    fun evictPackage(packageName: String) {
        val entries = packageOfKey.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            if (entry.value == packageName) {
                memory.remove(entry.key)
                entries.remove()
            }
        }
    }

    private fun sync() {
        val mode = prefs.iconMode
        val dark = Ui.isDark(context)
        val density = prefs.density
        if (mode != seenMode || dark != seenDark || density != seenDensity) {
            seenMode = mode
            seenDark = dark
            seenDensity = density
            clear()
        }
    }

    private fun render(payload: String, foreground: Int, size: Int): Bitmap? {
        val parts = payload.split('|')
        val component = ComponentName.unflattenFromString(parts[0]) ?: return null
        val user = Graph.apps.userFor(parts.getOrNull(1)?.toLongOrNull())
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
        val info = launcherApps.resolveActivity(intent, user) ?: return null
        var drawable: Drawable = info.getBadgedIcon(0)
        val mono = prefs.iconMode == "mono"
        if (mono && Build.VERSION.SDK_INT >= 33) {
            val raw = info.getIcon(0)
            val monochrome = (raw as? AdaptiveIconDrawable)?.monochrome
            if (monochrome != null) {
                val mutated = monochrome.mutate()
                mutated.setTint(foreground)
                drawable = mutated
                return draw(drawable, 1.5f, size)
            }
        }
        if (mono) {
            drawable = drawable.mutate()
            drawable.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        return draw(drawable, 1f, size)
    }

    private fun draw(drawable: Drawable, scale: Float, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = ((size * scale - size) / 2).toInt()
        drawable.setBounds(-inset, -inset, size + inset, size + inset)
        drawable.draw(canvas)
        return bitmap
    }
}
