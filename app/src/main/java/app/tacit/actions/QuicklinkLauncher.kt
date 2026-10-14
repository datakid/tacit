package app.tacit.actions

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.provider.DocumentsContract
import app.tacit.Graph
import app.tacit.core.ItemKind
import app.tacit.core.QuicklinkRules
import app.tacit.core.QuicklinkType
import app.tacit.core.SearchItem
import app.tacit.core.TargetState
import app.tacit.data.Quicklink
import app.tacit.sources.QuicklinkTargets
import app.tacit.ui.QuicklinksActivity

object QuicklinkLauncher {

    private const val CLIPBOARD_LIMIT = 2000

    fun launch(activity: Activity, spec: String, bounds: Rect?): Boolean {
        val id = spec.substringBefore('|')
        val query = if (spec.contains('|')) spec.substringAfter('|') else ""
        val link = Graph.quicklinks.get(id) ?: return false
        if (QuicklinkTargets.state(link) != TargetState.OK) return edit(activity, link)
        return when (link.type) {
            QuicklinkType.URL -> openUrl(activity, link, query)
            QuicklinkType.SETTING -> Actions.openSetting(activity, SearchItem("ql:setting", ItemKind.SETTING, link.name, payload = link.target))
            QuicklinkType.APP -> runCatching { Graph.apps.launch(link.target, bounds) }.isSuccess
            QuicklinkType.APP_SHORTCUT -> Graph.apps.launchShortcut(link.target, bounds)
            QuicklinkType.FILE -> openDocument(activity, link) { Actions.openFile(activity, link.target) }
            QuicklinkType.FOLDER -> openDocument(activity, link) { openFolder(activity, link.target) }
            QuicklinkType.CONTACT -> openContact(activity, link.target)
        }
    }

    private fun edit(activity: Activity, link: Quicklink): Boolean {
        activity.startActivity(Intent(activity, QuicklinksActivity::class.java).putExtra(QuicklinksActivity.EXTRA_EDIT, link.id))
        return true
    }

    private fun openUrl(activity: Activity, link: Quicklink, query: String): Boolean {
        val clipboard = if (QuicklinkRules.usesClipboard(link.target)) clipboardText(activity) else ""
        val url = QuicklinkRules.normalizeUrl(QuicklinkRules.fill(link.target, query, clipboard)) ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (QuicklinkRules.isDeepLink(url)) intent.addCategory(Intent.CATEGORY_BROWSABLE)
        return Actions.start(activity, intent)
    }

    private fun clipboardText(activity: Activity): String {
        val manager = activity.getSystemService(ClipboardManager::class.java) ?: return ""
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return ""
        if (clip.itemCount == 0) return ""
        return runCatching { clip.getItemAt(0).coerceToText(activity).toString() }.getOrDefault("").take(CLIPBOARD_LIMIT)
    }

    private inline fun openDocument(activity: Activity, link: Quicklink, open: () -> Boolean): Boolean {
        if (!QuicklinkTargets.existsNow(activity, link)) return edit(activity, link)
        return open()
    }

    private fun openFolder(activity: Activity, target: String): Boolean {
        if (!target.startsWith("content://")) return Actions.openFolder(activity, target)
        val uri = Uri.parse(target)
        val document = if (DocumentsContract.isTreeUri(uri)) {
            runCatching { DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)) }.getOrNull() ?: return false
        } else uri
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(document, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Actions.start(activity, intent)
    }

    private fun openContact(activity: Activity, target: String): Boolean {
        val contact = QuicklinkRules.parseContact(target) ?: return false
        return when (contact.action) {
            "call" -> Actions.dial(activity, contact.number)
            "sms" -> Actions.sms(activity, contact.number)
            "wa" -> Actions.whatsappNumber(activity, contact.number, Actions.WHATSAPP)
            "wab" -> Actions.whatsappNumber(activity, contact.number, Actions.WHATSAPP_BUSINESS)
            else -> false
        }
    }
}
