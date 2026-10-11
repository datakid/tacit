package app.tacit.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.EditText
import app.tacit.Graph
import app.tacit.R
import app.tacit.actions.Actions
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.data.AliasText
import app.tacit.data.Snippet

class MenuEntry(val label: String, val run: () -> Unit)

object SecondaryMenu {

    fun show(activity: Activity, item: SearchItem, changed: () -> Unit) {
        val entries = ArrayList<MenuEntry>()
        fun add(labelRes: Int, run: () -> Unit) {
            entries.add(MenuEntry(activity.getString(labelRes), run))
        }
        val persistent = item.kind in setOf(ItemKind.APP, ItemKind.CONTACT, ItemKind.SETTING, ItemKind.SNIPPET, ItemKind.ACTION) &&
            !item.key.startsWith("num:") && item.key != "url" && item.key != "mail" && item.key != "crash" &&
            !(item.key.startsWith("ql:") && item.key.endsWith(":q"))

        if (persistent) {
            add(R.string.menu_alias) { editAlias(activity, item, changed) }
            val pinned = item.key in Graph.prefs.pins
            add(if (pinned) R.string.menu_unpin else R.string.menu_pin) {
                Graph.prefs.pins = if (pinned) Graph.prefs.pins - item.key else Graph.prefs.pins + item.key
                Graph.requestRebuild()
            }
        }
        when (item.kind) {
            ItemKind.APP -> {
                val pkg = item.payload.substringBefore('/')
                add(R.string.menu_app_info) { Graph.apps.openAppInfo(item.payload) }
                add(R.string.menu_notifications) {
                    Actions.start(activity, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
                }
                add(R.string.menu_uninstall) {
                    Actions.start(activity, Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)))
                }
                add(R.string.menu_hide) {
                    Graph.prefs.hidden = Graph.prefs.hidden + item.key
                    Graph.requestRebuild()
                }
            }
            ItemKind.CONTACT -> add(R.string.menu_open_contact) { Actions.openContact(activity, item) }
            ItemKind.FILE -> {
                add(R.string.menu_share) { Actions.shareFile(activity, item.payload) }
                add(R.string.menu_open_folder) { Actions.openFolder(activity, item.payload) }
                add(R.string.menu_copy_path) { Graph.clipboard.copy(item.payload, false) }
            }
            ItemKind.CLIPBOARD -> {
                val id = item.key.removePrefix("clip:").toLongOrNull()
                add(R.string.menu_save_snippet) {
                    Graph.snippets.upsert(Snippet(Graph.snippets.newId(), "", "", item.payload))
                    Graph.requestRefresh()
                }
                if (id != null) add(R.string.menu_delete) {
                    Graph.clipboard.delete(id)
                    changed()
                }
            }
            ItemKind.SNIPPET -> add(R.string.menu_edit) { activity.startActivity(Intent(activity, SnippetsActivity::class.java)) }
            ItemKind.ACTION -> if (item.key.startsWith("ql:")) add(R.string.menu_edit) {
                activity.startActivity(Intent(activity, QuicklinksActivity::class.java))
            }
            ItemKind.CALCULATOR, ItemKind.WEB -> add(R.string.menu_copy) {
                Graph.clipboard.copy(if (item.kind == ItemKind.WEB) item.label else item.payload, false)
            }
            else -> Unit
        }
        if (entries.isEmpty()) return
        AlertDialog.Builder(activity)
            .setTitle(item.label)
            .setItems(entries.map { it.label }.toTypedArray()) { _, which -> entries[which].run() }
            .show()
    }

    fun editAlias(activity: Activity, item: SearchItem, changed: () -> Unit) {
        val field = EditText(activity).apply {
            setText(AliasText.join(Graph.aliases.get(item.key)))
            hint = activity.getString(R.string.alias_hint)
            isSingleLine = true
            val pad = Ui.dp(activity, 20)
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(activity)
            .setTitle(item.label)
            .setView(field)
            .setPositiveButton(R.string.save) { _, _ ->
                Graph.aliases.set(item.key, AliasText.split(field.text.toString()))
                Graph.requestRebuild()
                changed()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
