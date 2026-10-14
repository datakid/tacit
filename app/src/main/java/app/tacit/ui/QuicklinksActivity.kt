package app.tacit.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import app.tacit.Graph
import app.tacit.R
import app.tacit.actions.PinShortcut
import app.tacit.core.ItemKind
import app.tacit.core.QuicklinkRules
import app.tacit.core.QuicklinkType
import app.tacit.core.TargetState
import app.tacit.data.AliasText
import app.tacit.data.Quicklink
import app.tacit.sources.QuicklinkTargets

class QuicklinksActivity : Activity() {

    private lateinit var palette: Palette
    private var links: List<Quicklink> = emptyList()
    private var states: Map<String, TargetState> = emptyMap()
    private lateinit var adapter: BaseAdapter
    private lateinit var emptyView: TextView
    private var pending: ((Uri) -> Unit)? = null

    private val typeLabels = listOf(
        QuicklinkType.URL to R.string.ql_type_url,
        QuicklinkType.APP to R.string.ql_type_app,
        QuicklinkType.APP_SHORTCUT to R.string.ql_type_shortcut,
        QuicklinkType.SETTING to R.string.ql_type_setting,
        QuicklinkType.FILE to R.string.ql_type_file,
        QuicklinkType.FOLDER to R.string.ql_type_folder,
        QuicklinkType.CONTACT to R.string.ql_type_contact
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.applyTheme(this)
        super.onCreate(savedInstanceState)
        palette = Ui.palette(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.background))
        val root = Ui.column(this)
        val pad = Ui.dp(this, 16)
        val gap = Ui.dp(this, 12)
        root.setPadding(pad, pad, pad, pad)
        root.addView(Ui.text(this, getString(R.string.quicklinks), 22f, palette.foreground, bold = true))
        val tools = Ui.row(this).apply { setPadding(0, gap, 0, gap) }
        tools.addView(Ui.chip(this, palette, getString(R.string.add)) { edit(null) })
        root.addView(tools)
        emptyView = Ui.text(this, getString(R.string.ql_empty), 13f, palette.muted)
        root.addView(emptyView)
        adapter = object : BaseAdapter() {
            override fun getCount() = links.size
            override fun getItem(position: Int) = links[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val link = links[position]
                val column = Ui.column(this@QuicklinksActivity)
                column.setPadding(0, gap, 0, gap)
                column.addView(Ui.text(this@QuicklinksActivity, link.name, 15f, palette.foreground, bold = true))
                column.addView(Ui.text(this@QuicklinksActivity, summary(link, states[link.id] ?: TargetState.OK), 13f, palette.muted))
                column.setOnClickListener { edit(link) }
                return column
            }
        }
        root.addView(ListView(this).apply {
            adapter = this@QuicklinksActivity.adapter
            divider = android.graphics.drawable.ColorDrawable(palette.line)
            dividerHeight = 1
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(Ui.screen(this, root))
        Ui.applyBars(window, Ui.isDark(this))
        reload()
        if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_EDIT)?.let { id -> Graph.quicklinks.get(id)?.let { edit(it) } }
        }
    }

    private fun typeLabel(type: QuicklinkType): String = getString(typeLabels.first { it.first == type }.second)

    private fun summary(link: Quicklink, state: TargetState): String = when {
        state == TargetState.MISSING -> getString(R.string.ql_missing)
        state == TargetState.REPICK -> getString(R.string.ql_repick)
        link.type == QuicklinkType.URL -> link.target
        else -> typeLabel(link.type) + " · " + link.detail
    }

    private fun reload() {
        links = Graph.quicklinks.all()
        states = links.associate { it.id to QuicklinkTargets.state(it) }
        Ui.gone(emptyView, links.isNotEmpty())
        adapter.notifyDataSetChanged()
    }

    private fun pickDocument(type: QuicklinkType, onPicked: (String, String) -> Unit) {
        val intent = Intent(if (type == QuicklinkType.FOLDER) Intent.ACTION_OPEN_DOCUMENT_TREE else Intent.ACTION_OPEN_DOCUMENT)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        if (type == QuicklinkType.FILE) intent.addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        pending = { uri ->
            if (QuicklinkTargets.keep(this, uri)) {
                onPicked(uri.toString(), QuicklinkTargets.displayName(this, uri))
            } else {
                Toast.makeText(this, R.string.ql_cant_keep, Toast.LENGTH_LONG).show()
            }
        }
        startActivityForResult(intent, REQUEST_DOCUMENT)
    }

    @Deprecated("Platform API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val callback = pending
        pending = null
        val uri = data?.data
        if (requestCode == REQUEST_DOCUMENT && resultCode == RESULT_OK && uri != null) callback?.invoke(uri)
    }

    private fun pick(type: QuicklinkType, name: EditText, onPicked: (String, String) -> Unit) {
        val fillName: (String) -> Unit = { label -> if (name.text.isBlank()) name.setText(label) }
        when (type) {
            QuicklinkType.SETTING -> ItemPicker.show(this, palette, typeLabel(type), Graph.settings.items()) {
                fillName(it.label)
                onPicked(it.payload, it.label)
            }
            QuicklinkType.APP -> ItemPicker.show(this, palette, typeLabel(type), Graph.apps.appItems()) {
                fillName(it.label)
                onPicked(it.payload, it.label)
            }
            QuicklinkType.APP_SHORTCUT -> {
                val shortcuts = Graph.apps.items().filter { it.kind == ItemKind.ACTION }
                ItemPicker.show(this, palette, typeLabel(type), shortcuts) {
                    fillName(it.label)
                    onPicked(it.payload, "${it.label} · ${it.subtitle}")
                }
            }
            QuicklinkType.CONTACT -> {
                val contacts = Graph.contacts.items()
                if (contacts.isEmpty()) {
                    Toast.makeText(this, R.string.ql_contacts_off, Toast.LENGTH_LONG).show()
                } else {
                    ItemPicker.show(this, palette, typeLabel(type), contacts) { contact ->
                        QuicklinkMaker.chooseContact(this, contact) {
                            fillName(it.name)
                            onPicked(it.target, it.detail)
                        }
                    }
                }
            }
            QuicklinkType.FILE, QuicklinkType.FOLDER -> pickDocument(type) { target, label ->
                fillName(label)
                onPicked(target, label)
            }
            QuicklinkType.URL -> Unit
        }
    }

    private fun chooseLabel(type: QuicklinkType): Int = when (type) {
        QuicklinkType.FILE -> R.string.ql_pick_file
        QuicklinkType.FOLDER -> R.string.ql_pick_folder
        else -> R.string.ql_choose
    }

    private fun edit(existing: Quicklink?) {
        val form = Ui.column(this)
        val pad = Ui.dp(this, 20)
        val gap = Ui.dp(this, 8)
        form.setPadding(pad, pad / 2, pad, 0)
        val name = EditText(this).apply { hint = getString(R.string.ql_name); setText(existing?.name); isSingleLine = true }
        val startType = existing?.type ?: QuicklinkType.URL
        var type = startType
        var picked = if (existing != null && existing.type != QuicklinkType.URL) existing.target else ""
        var pickedLabel = if (existing != null && existing.type != QuicklinkType.URL) existing.detail else ""
        val originalPicked = picked
        val originalLabel = pickedLabel
        val typeChip = Ui.chip(this, palette, typeLabel(type)) {}
        val url = EditText(this).apply {
            hint = getString(R.string.ql_url_hint)
            setText(if (existing?.type == QuicklinkType.URL) existing.target else "")
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val help = Ui.text(this, getString(R.string.ql_url_help), 12f, palette.muted).apply { maxLines = 3 }
        val choose = Ui.chip(this, palette, getString(chooseLabel(type))) {}
        val state = if (existing != null) states[existing.id] ?: TargetState.OK else TargetState.OK
        val note = Ui.text(this, getString(if (state == TargetState.REPICK) R.string.ql_repick else R.string.ql_missing_here), 12f, palette.muted)
            .apply { maxLines = 2 }
        val aliases = EditText(this).apply {
            hint = getString(R.string.ql_aliases)
            setText(if (existing != null) AliasText.join(Graph.aliases.get("ql:${existing.id}")) else "")
            isSingleLine = true
        }
        fun refresh() {
            val label = typeLabel(type)
            typeChip.text = label
            typeChip.contentDescription = label
            Ui.gone(url, type != QuicklinkType.URL)
            Ui.gone(help, type != QuicklinkType.URL)
            Ui.gone(choose, type == QuicklinkType.URL)
            val shown = if (picked.isEmpty() || pickedLabel.isEmpty()) getString(chooseLabel(type)) else pickedLabel
            choose.text = shown
            choose.contentDescription = shown
            val flagged = state != TargetState.OK && type == startType && picked == originalPicked && type != QuicklinkType.URL
            Ui.gone(note, !flagged)
        }
        choose.setOnClickListener {
            pick(type, name) { target, label ->
                picked = target
                pickedLabel = label
                refresh()
            }
        }
        typeChip.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.ql_type)
                .setItems(typeLabels.map { getString(it.second) }.toTypedArray()) { _, which ->
                    val newType = typeLabels[which].first
                    if (newType != type) {
                        type = newType
                        if (type == startType && existing != null) {
                            picked = originalPicked
                            pickedLabel = originalLabel
                        } else {
                            picked = ""
                            pickedLabel = ""
                        }
                        refresh()
                    }
                }
                .show()
        }
        form.addView(name)
        form.addView(typeChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = gap
        })
        form.addView(url)
        form.addView(help)
        form.addView(choose, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = gap
        })
        form.addView(note)
        form.addView(aliases)
        if (existing != null && PinShortcut.supported(this)) {
            form.addView(Ui.chip(this, palette, getString(R.string.menu_add_home)) { pin(existing) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
        }
        refresh()
        val builder = AlertDialog.Builder(this)
            .setView(form)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) builder.setNeutralButton(R.string.menu_delete) { _, _ ->
            Graph.quicklinks.delete(existing.id)
            Graph.aliases.set("ql:${existing.id}", emptyList())
            Graph.reloadQuicklinks()
            reload()
        }
        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = name.text.toString().trim()
            val target: String
            val detail: String
            if (title.isEmpty()) {
                Toast.makeText(this, R.string.ql_name_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (type == QuicklinkType.URL) {
                val raw = url.text.toString().trim()
                if (!QuicklinkRules.placeholdersValid(raw)) {
                    Toast.makeText(this, R.string.ql_bad_placeholder, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val normalized = QuicklinkRules.normalizeUrl(raw)
                if (normalized == null) {
                    Toast.makeText(this, R.string.ql_invalid_url, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                target = normalized
                detail = ""
            } else {
                if (picked.isEmpty()) {
                    Toast.makeText(this, R.string.ql_pick_first, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                target = picked
                detail = pickedLabel
            }
            val id = existing?.id ?: Graph.quicklinks.newId()
            Graph.quicklinks.upsert(Quicklink(id, title, type, target, detail))
            Graph.aliases.set("ql:$id", AliasText.split(aliases.text.toString()))
            Graph.reloadQuicklinks()
            reload()
            dialog.dismiss()
        }
    }

    private fun pin(link: Quicklink) {
        if (!PinShortcut.request(this, link)) Toast.makeText(this, R.string.ql_pin_unsupported, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_EDIT = "quicklink_edit"
        private const val REQUEST_DOCUMENT = 51
    }
}
