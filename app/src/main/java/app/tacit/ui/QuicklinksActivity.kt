package app.tacit.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import app.tacit.Graph
import app.tacit.R
import app.tacit.core.QuicklinkRules
import app.tacit.core.QuicklinkType
import app.tacit.data.AliasText
import app.tacit.data.Quicklink

class QuicklinksActivity : Activity() {

    private lateinit var palette: Palette
    private var links: List<Quicklink> = emptyList()
    private lateinit var adapter: BaseAdapter
    private lateinit var emptyView: TextView

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
                column.addView(Ui.text(this@QuicklinksActivity, summary(link), 13f, palette.muted))
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
    }

    private fun summary(link: Quicklink): String = when (link.type) {
        QuicklinkType.URL -> link.target
        QuicklinkType.SETTING -> getString(R.string.ql_type_setting) + " · " + link.detail
        QuicklinkType.APP -> getString(R.string.ql_type_app) + " · " + link.detail
    }

    private fun reload() {
        links = Graph.quicklinks.all()
        Ui.gone(emptyView, links.isNotEmpty())
        adapter.notifyDataSetChanged()
    }

    private fun pick(type: QuicklinkType, onPicked: (String, String) -> Unit) {
        val items = (if (type == QuicklinkType.APP) Graph.apps.appItems() else Graph.settings.items())
            .sortedBy { it.label.lowercase() }
        AlertDialog.Builder(this)
            .setItems(items.map { it.label }.toTypedArray()) { _, which -> onPicked(items[which].payload, items[which].label) }
            .show()
    }

    private fun edit(existing: Quicklink?) {
        val form = Ui.column(this)
        val pad = Ui.dp(this, 20)
        form.setPadding(pad, pad / 2, pad, 0)
        val name = EditText(this).apply { hint = getString(R.string.ql_name); setText(existing?.name); isSingleLine = true }
        val labels = listOf(
            QuicklinkType.URL to R.string.ql_type_url,
            QuicklinkType.SETTING to R.string.ql_type_setting,
            QuicklinkType.APP to R.string.ql_type_app
        )
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val buttons = labels.map { (type, label) ->
            RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(label)
                tag = type
            }
        }
        buttons.forEach { group.addView(it) }
        val startType = existing?.type ?: QuicklinkType.URL
        var type = startType
        var picked = if (existing != null && existing.type != QuicklinkType.URL) existing.target else ""
        var pickedLabel = if (existing != null && existing.type != QuicklinkType.URL) existing.detail else ""
        val originalPicked = picked
        val originalLabel = pickedLabel
        val url = EditText(this).apply {
            hint = getString(R.string.ql_url_hint)
            setText(if (existing?.type == QuicklinkType.URL) existing.target else "")
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val help = Ui.text(this, getString(R.string.ql_url_help), 12f, palette.muted).apply { maxLines = 3 }
        val choose = Ui.chip(this, palette, getString(R.string.ql_choose)) {}
        val aliases = EditText(this).apply {
            hint = getString(R.string.ql_aliases)
            setText(if (existing != null) AliasText.join(Graph.aliases.get("ql:${existing.id}")) else "")
            isSingleLine = true
        }
        fun refresh() {
            Ui.gone(url, type != QuicklinkType.URL)
            Ui.gone(help, type != QuicklinkType.URL)
            Ui.gone(choose, type == QuicklinkType.URL)
            choose.text = pickedLabel.ifEmpty { getString(R.string.ql_choose) }
        }
        choose.setOnClickListener {
            pick(type) { target, label ->
                picked = target
                pickedLabel = label
                if (name.text.isBlank()) name.setText(label)
                refresh()
            }
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val newType = buttons.first { it.id == checkedId }.tag as QuicklinkType
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
        form.addView(name)
        form.addView(group)
        form.addView(url)
        form.addView(help)
        form.addView(choose, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@QuicklinksActivity, 8)
        })
        form.addView(aliases)
        group.check(buttons.first { it.tag == type }.id)
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
                val normalized = QuicklinkRules.normalizeUrl(url.text.toString())
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
}
