package app.tacit.actions

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.LauncherApps
import android.os.Bundle
import app.tacit.Graph
import app.tacit.R
import app.tacit.core.RefreshSources

class PinRequestActivity : Activity() {

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = pending()
        val info = request?.shortcutInfo
        if (request == null || info == null) {
            finish()
            return
        }
        val pkg = info.`package`
        val label = (info.shortLabel ?: info.longLabel)?.toString().orEmpty().ifEmpty { pkg }
        dialog = AlertDialog.Builder(this)
            .setTitle(label)
            .setMessage(Graph.apps.labelFor(pkg))
            .setPositiveButton(R.string.pin_add) { _, _ -> accept(request) }
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    private fun pending(): LauncherApps.PinItemRequest? {
        val apps = getSystemService(LauncherApps::class.java) ?: return null
        val request = runCatching { apps.getPinItemRequest(intent) }.getOrNull() ?: return null
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return null
        return if (runCatching { request.isValid }.getOrDefault(false)) request else null
    }

    private fun accept(request: LauncherApps.PinItemRequest) {
        if (runCatching { request.accept() }.getOrDefault(false)) Graph.requestRefresh(RefreshSources.APPS)
        finish()
    }
}
