package app.tacit.actions

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem

object ActivityCatalog {

    fun load(context: Context, packageName: String): List<SearchItem> {
        val manager = context.packageManager
        val info = packageInfo(manager, packageName) ?: return emptyList()
        val result = ArrayList<SearchItem>()
        for (activity in info.activities.orEmpty()) {
            if (!openable(activity)) continue
            val simple = activity.name.substringAfterLast('.')
            val named = activity.labelRes != 0 || activity.nonLocalizedLabel != null
            val label = if (named) activity.loadLabel(manager).toString().trim().ifEmpty { simple } else simple
            val component = ComponentName(activity.packageName, activity.name).flattenToString()
            result.add(SearchItem("activity:$component", ItemKind.ACTION, label, if (named) simple else "", payload = component))
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(manager: PackageManager, packageName: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
        } else {
            manager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        }
    }.getOrNull()

    fun openable(activity: ActivityInfo): Boolean = activity.exported && activity.enabled && activity.permission == null
}
