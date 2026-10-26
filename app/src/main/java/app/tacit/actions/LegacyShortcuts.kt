package app.tacit.actions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import app.tacit.core.ItemKind
import app.tacit.core.QuicklinkType
import app.tacit.core.SearchItem
import app.tacit.ui.QuicklinkDraft

object LegacyShortcuts {

    fun handlers(context: Context): List<SearchItem> {
        val manager = context.packageManager
        val found = runCatching { manager.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0) }.getOrDefault(emptyList())
        val result = ArrayList<SearchItem>()
        for (info in found) {
            val activity = info.activityInfo ?: continue
            val component = ComponentName(activity.packageName, activity.name).flattenToString()
            val label = info.loadLabel(manager).toString().trim().ifEmpty { activity.name.substringAfterLast('.') }
            val app = activity.applicationInfo.loadLabel(manager).toString().trim().ifEmpty { activity.packageName }
            result.add(SearchItem("legacy:$component", ItemKind.ACTION, label, app, payload = component))
        }
        return result
    }

    fun createIntent(payload: String): Intent? {
        val component = ComponentName.unflattenFromString(payload) ?: return null
        return Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(component)
    }

    fun accept(context: Context, data: Intent): Boolean {
        val apps = context.getSystemService(LauncherApps::class.java) ?: return false
        if (!apps.hasShortcutHostPermission()) return false
        val request = runCatching { apps.getPinItemRequest(data) }.getOrNull() ?: return false
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return false
        if (!runCatching { request.isValid }.getOrDefault(false)) return false
        return runCatching { request.accept() }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun shortcutIntent(data: Intent): Intent? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        } else {
            data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
        }
    }.getOrNull()

    fun draft(context: Context, data: Intent, detail: String): QuicklinkDraft? {
        val name = runCatching { data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) }.getOrNull()?.trim().orEmpty()
        val source = shortcutIntent(data) ?: return null
        val intent = IntentSanitizer.sanitize(source, context.packageName) ?: return null
        val spec = IntentSanitizer.specOf(intent) ?: return null
        return QuicklinkDraft(QuicklinkType.INTENT, IntentSanitizer.encode(intent), detail.ifEmpty { spec.summary() }, name.ifEmpty { spec.summary() })
    }
}
