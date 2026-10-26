package app.tacit.sources

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import app.tacit.Graph
import app.tacit.core.ItemKind
import app.tacit.core.RefreshSources
import app.tacit.core.SearchItem

class AppSource(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    @Volatile
    private var cached: List<SearchItem> = emptyList()

    @Volatile
    private var shortcuts: List<SearchItem> = emptyList()

    @Volatile
    private var pinnedKeys: Set<String> = emptySet()

    private var registered = false

    private fun packagesChanged(names: Array<out String>) {
        Graph.packagesChanged()
        Graph.main.post { for (name in names) Graph.icons.evictPackage(name) }
        Graph.requestRefresh(RefreshSources.APPS)
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = packagesChanged(arrayOf(packageName))
        override fun onPackageAdded(packageName: String, user: UserHandle) = packagesChanged(arrayOf(packageName))
        override fun onPackageChanged(packageName: String, user: UserHandle) = packagesChanged(arrayOf(packageName))
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = packagesChanged(packageNames)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = packagesChanged(packageNames)
    }

    fun items(): List<SearchItem> = cached + shortcuts

    fun appItems(): List<SearchItem> = cached

    fun hasApp(payload: String): Boolean = cached.any { it.payload == payload }

    fun hasShortcut(payload: String): Boolean? {
        val current = shortcuts
        return if (current.isEmpty()) null else current.any { it.payload == payload }
    }

    fun mainProfileApps(): List<SearchItem> {
        val serial = userManager.getSerialNumberForUser(Process.myUserHandle()).toString()
        return cached.filter { it.payload.substringAfter('|', "") == serial }
    }

    fun labelFor(packageName: String): String =
        cached.firstOrNull { it.payload.startsWith("$packageName/") }?.label ?: packageName

    fun isPinnedShortcut(key: String): Boolean = key in pinnedKeys

    fun unpinShortcut(payload: String) {
        val parts = payload.split('|')
        if (parts.size < 3) return
        val pkg = parts[0]
        val id = parts[1]
        val user = userFor(parts[2].toLongOrNull())
        Graph.worker.post {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(pkg)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val pinned = runCatching { launcherApps.getShortcuts(query, user) }.getOrNull()
            if (pinned != null) {
                runCatching { launcherApps.pinShortcuts(pkg, pinned.map { it.id }.filter { it != id }, user) }
                Graph.requestRefresh(RefreshSources.APPS)
            }
        }
    }

    fun reload() {
        if (!registered) {
            launcherApps.registerCallback(callback, Graph.worker)
            registered = true
        }
        val result = ArrayList<SearchItem>(512)
        val mainUser = Process.myUserHandle()
        for (user in launcherApps.profiles) {
            if (userManager.isQuietModeEnabled(user)) continue
            val serial = userManager.getSerialNumberForUser(user)
            val badge = if (user == mainUser) "" else profileLabel(user)
            for (activity in launcherApps.getActivityList(null, user)) {
                val component = activity.componentName
                val label = activity.label?.toString()?.trim().orEmpty().ifEmpty { component.packageName }
                val key = "app:${component.flattenToShortString()}:$serial"
                val tail = component.packageName.substringAfterLast('.')
                val extra = if (label.contains(tail, ignoreCase = true)) emptyList() else listOf(tail)
                result.add(SearchItem(key, ItemKind.APP, label, badge, extra, "${component.flattenToString()}|$serial"))
            }
        }
        cached = result.sortedBy { it.label.lowercase() }
        loadShortcuts()
    }

    private fun profileLabel(user: UserHandle): String {
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching {
                val info = launcherApps.getLauncherUserInfo(user)
                if (info?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE) return "Private"
            }
        }
        return "Work"
    }

    private fun loadShortcuts() {
        val items = ArrayList<SearchItem>()
        val pinned = HashSet<String>()
        if (launcherApps.hasShortcutHostPermission()) {
            val query = LauncherApps.ShortcutQuery().setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
            for (user in launcherApps.profiles) {
                if (userManager.isQuietModeEnabled(user)) continue
                val serial = userManager.getSerialNumberForUser(user)
                val list = runCatching { launcherApps.getShortcuts(query, user) }.getOrNull() ?: continue
                for (shortcut in list) {
                    if (!shortcut.isEnabled) continue
                    val label = (shortcut.longLabel ?: shortcut.shortLabel)?.toString() ?: continue
                    val key = "shortcut:${shortcut.`package`}:${shortcut.id}:$serial"
                    items.add(SearchItem(key, ItemKind.ACTION, label, labelFor(shortcut.`package`), listOfNotNull(shortcut.shortLabel?.toString()),
                        "${shortcut.`package`}|${shortcut.id}|$serial"))
                    if (shortcut.isPinned && !shortcut.isDynamic && !shortcut.isDeclaredInManifest) pinned.add(key)
                }
            }
        }
        shortcuts = items
        pinnedKeys = pinned
    }

    fun launch(payload: String, bounds: android.graphics.Rect?) {
        val parts = payload.split('|')
        val component = ComponentName.unflattenFromString(parts[0]) ?: return
        val user = userFor(parts.getOrNull(1)?.toLongOrNull())
        launcherApps.startMainActivity(component, user, bounds, null)
    }

    fun launchShortcut(payload: String, bounds: android.graphics.Rect?): Boolean {
        val parts = payload.split('|')
        if (parts.size < 3) return false
        val user = userFor(parts[2].toLongOrNull())
        return runCatching { launcherApps.startShortcut(parts[0], parts[1], bounds, null, user) }.isSuccess
    }

    fun openAppInfo(payload: String) {
        val parts = payload.split('|')
        val component = ComponentName.unflattenFromString(parts[0]) ?: return
        launcherApps.startAppDetailsActivity(component, userFor(parts.getOrNull(1)?.toLongOrNull()), null, null)
    }

    fun userFor(serial: Long?): UserHandle =
        serial?.let { userManager.getUserForSerialNumber(it) } ?: Process.myUserHandle()

    fun packageOf(payload: String): String = payload.substringBefore('/')
}
