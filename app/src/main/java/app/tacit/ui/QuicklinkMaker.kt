package app.tacit.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import app.tacit.Graph
import app.tacit.R
import app.tacit.actions.Actions
import app.tacit.core.ItemKind
import app.tacit.core.QuicklinkRules
import app.tacit.core.QuicklinkType
import app.tacit.core.SearchItem
import app.tacit.data.Quicklink
import app.tacit.sources.ContactInfo
import java.io.File

class QuicklinkDraft(val type: QuicklinkType, val target: String, val detail: String, val name: String)

object QuicklinkMaker {

    fun canCreate(item: SearchItem): Boolean = item.kind == ItemKind.CONTACT || draftOf(item) != null

    fun draftOf(item: SearchItem): QuicklinkDraft? = when {
        item.kind == ItemKind.APP -> QuicklinkDraft(QuicklinkType.APP, item.payload, item.label, item.label)
        item.kind == ItemKind.SETTING -> QuicklinkDraft(QuicklinkType.SETTING, item.payload, item.label, item.label)
        item.kind == ItemKind.FILE -> {
            val folder = item.payload.startsWith("/") && File(item.payload).isDirectory
            QuicklinkDraft(if (folder) QuicklinkType.FOLDER else QuicklinkType.FILE, item.payload, item.label, item.label)
        }
        item.kind == ItemKind.ACTION && item.key.startsWith("shortcut:") ->
            QuicklinkDraft(QuicklinkType.APP_SHORTCUT, item.payload, "${item.label} · ${item.subtitle}", item.label)
        else -> null
    }

    fun create(activity: Activity, item: SearchItem) {
        if (item.kind == ItemKind.CONTACT) {
            chooseContact(activity, item) { save(activity, it) }
            return
        }
        draftOf(item)?.let { save(activity, it) }
    }

    fun save(activity: Activity, draft: QuicklinkDraft): Boolean {
        val existing = Graph.quicklinks.all().firstOrNull { it.type == draft.type && it.target == draft.target }
        if (existing != null) {
            Toast.makeText(activity, activity.getString(R.string.ql_exists, existing.name), Toast.LENGTH_SHORT).show()
            return false
        }
        val link = Quicklink(Graph.quicklinks.newId(), draft.name, draft.type, draft.target, draft.detail)
        Graph.quicklinks.upsert(link)
        Graph.reloadQuicklinks()
        Toast.makeText(activity, activity.getString(R.string.ql_created, link.name), Toast.LENGTH_SHORT).show()
        return true
    }

    fun chooseContact(activity: Activity, item: SearchItem, onDraft: (QuicklinkDraft) -> Unit) {
        val info = item.extra as? ContactInfo
        val numbers = (info?.numbers.orEmpty()).ifEmpty { listOf(item.subtitle).filter { text -> text.any { it.isDigit() } } }
        if (numbers.isEmpty()) {
            Toast.makeText(activity, R.string.ql_no_numbers, Toast.LENGTH_SHORT).show()
            return
        }
        val primary = info?.primary
        val ordered = if (primary != null && primary in numbers) listOf(primary) + numbers.filter { it != primary } else numbers
        val actions = ArrayList<Pair<String, String>>()
        actions.add("call" to activity.getString(R.string.action_call))
        actions.add("sms" to activity.getString(R.string.action_message))
        if (Actions.isInstalled(activity, Actions.WHATSAPP)) actions.add("wa" to activity.getString(R.string.action_whatsapp))
        if (Actions.isInstalled(activity, Actions.WHATSAPP_BUSINESS)) actions.add("wab" to activity.getString(R.string.action_whatsapp_business))
        fun finish(action: String, label: String, number: String) {
            val target = QuicklinkRules.contactTarget(action, number)
            if (target == null) {
                Toast.makeText(activity, R.string.ql_no_numbers, Toast.LENGTH_SHORT).show()
                return
            }
            onDraft(QuicklinkDraft(QuicklinkType.CONTACT, target, "${item.label} · $number", "$label ${item.label}"))
        }
        AlertDialog.Builder(activity)
            .setTitle(item.label)
            .setItems(actions.map { it.second }.toTypedArray()) { _, which ->
                val (action, label) = actions[which]
                if (ordered.size == 1) {
                    finish(action, label, ordered[0])
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle(item.label)
                        .setItems(ordered.toTypedArray()) { _, index -> finish(action, label, ordered[index]) }
                        .show()
                }
            }
            .show()
    }
}
