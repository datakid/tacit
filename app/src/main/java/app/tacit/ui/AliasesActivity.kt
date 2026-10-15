package app.tacit.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import app.tacit.Graph
import app.tacit.R
import app.tacit.core.AliasRules
import app.tacit.core.ItemKind
import app.tacit.core.Normalizer
import app.tacit.core.PathNames
import app.tacit.core.SearchItem
import app.tacit.data.AliasText
import app.tacit.data.Csv

private class AliasCategory(
    val slug: String,
    val label: Int,
    val accepts: (SearchItem) -> Boolean,
    val source: () -> List<SearchItem>
)

class AliasesActivity : Activity() {

    private val categories = listOf(
        AliasCategory("app", R.string.cat_apps, { it.kind == ItemKind.APP }) { Graph.apps.appItems() },
        AliasCategory("contact", R.string.cat_contacts, { it.kind == ItemKind.CONTACT }) { Graph.contacts.items() },
        AliasCategory("setting", R.string.cat_settings, { it.kind == ItemKind.SETTING }) { Graph.settings.items() },
        AliasCategory("snippet", R.string.cat_snippets, { it.kind == ItemKind.SNIPPET }) { Graph.snippetSource.items() },
        AliasCategory("file", R.string.files, { it.kind == ItemKind.FILE }) { aliasedFileItems() },
        AliasCategory("quicklink", R.string.cat_quicklinks, { it.key.startsWith("ql:") && !it.key.endsWith(":q") }) { Graph.quicklinkSource.items() },
        AliasCategory("action", R.string.cat_shortcuts, { it.kind == ItemKind.ACTION && !it.key.startsWith("ql:") }) {
            Graph.apps.items().filter { it.kind == ItemKind.ACTION }
        }
    )
    private var category = categories[0]
    private var filter = ""
    private var rows: List<SearchItem> = emptyList()
    private val edits = HashMap<String, String>()
    private lateinit var adapter: TableAdapter
    private lateinit var palette: Palette
    private lateinit var tabs: LinearLayout
    private lateinit var conflicts: TextView
    private val labels: Map<String, String> by lazy { Graph.engine.all().associate { it.key to it.label } }

    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.applyTheme(this)
        super.onCreate(savedInstanceState)
        palette = Ui.palette(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.background))
        val root = Ui.column(this)
        val pad = Ui.dp(this, 16)
        val gap = Ui.dp(this, 12)
        root.setPadding(pad, pad, pad, pad)

        root.addView(Ui.text(this, getString(R.string.aliases), 22f, palette.foreground, bold = true))
        tabs = Ui.row(this)
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
            setPadding(0, gap, 0, gap)
        })

        val filterField = EditText(this).apply {
            hint = getString(R.string.filter)
            setHintTextColor(palette.muted)
            setTextColor(palette.foreground)
            isSingleLine = true
            background = Ui.outline(context, palette, 10)
            setPadding(gap, gap, gap, gap)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    filter = AliasRules.key(s?.toString().orEmpty())
                    reload()
                }
            })
        }
        root.addView(filterField, Ui.matchWrap())

        val tools = Ui.row(this).apply { setPadding(0, gap, 0, gap) }
        tools.addView(Ui.chip(this, palette, getString(R.string.save)) { save() })
        tools.addView(space())
        tools.addView(Ui.chip(this, palette, getString(R.string.fill_initials)) { fillInitials() })
        tools.addView(space())
        tools.addView(Ui.chip(this, palette, getString(R.string.export_csv)) { exportCsv() })
        tools.addView(space())
        tools.addView(Ui.chip(this, palette, getString(R.string.import_csv)) { importCsv() })
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tools)
        })

        conflicts = Ui.text(this, "", 12f, palette.muted).apply { maxLines = 3 }
        root.addView(conflicts)

        val header = Ui.row(this).apply { setPadding(0, gap / 2, 0, gap / 2) }
        header.addView(Ui.text(this, getString(R.string.col_item), 12f, palette.muted, bold = true), Ui.weight(1f))
        header.addView(Ui.text(this, getString(R.string.col_aliases), 12f, palette.muted, bold = true), Ui.weight(1.3f))
        root.addView(header)

        adapter = TableAdapter()
        val list = ListView(this).apply {
            adapter = this@AliasesActivity.adapter
            divider = android.graphics.drawable.ColorDrawable(palette.line)
            dividerHeight = 1
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            itemsCanFocus = true
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(Ui.screen(this, root))
        Ui.applyBars(window, Ui.isDark(this))
        buildTabs()
        reload()
    }

    override fun onPause() {
        super.onPause()
        if (edits.isNotEmpty()) save()
    }

    private fun space() = View(this).apply { layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 8), 1) }

    private fun buildTabs() {
        tabs.removeAllViews()
        for (entry in categories) {
            val chip = Ui.chip(this, palette, getString(entry.label)) {
                if (edits.isNotEmpty()) save()
                category = entry
                buildTabs()
                reload()
            }
            if (entry === category) chip.setTypeface(null, android.graphics.Typeface.BOLD)
            tabs.addView(chip)
            tabs.addView(space())
        }
    }

    private fun reload() {
        rows = sourceRows().filter { filter.isEmpty() || Normalizer.normalize(it.label).contains(filter) || AliasRules.key(currentAliases(it)).contains(filter) }
            .sortedBy { it.label.lowercase() }
        adapter.notifyDataSetChanged()
        updateConflicts()
    }

    private fun currentAliases(item: SearchItem): String =
        edits[item.key] ?: AliasText.join(Graph.aliases.get(item.key))

    private fun save() {
        val changes = edits.mapValues { AliasText.split(it.value) }
        Graph.aliases.setMany(changes)
        edits.clear()
        Graph.requestRebuild()
        updateConflicts()
    }

    private fun fillInitials() {
        for (item in rows) {
            if (currentAliases(item).isNotBlank()) continue
            val words = Normalizer.words(Normalizer.normalize(item.label))
            if (words.size < 2) continue
            edits[item.key] = words.joinToString("") { it.take(1) }
        }
        adapter.notifyDataSetChanged()
        updateConflicts()
    }

    private fun aliasedFileItems(): List<SearchItem> = Graph.aliasedFiles().map { entry ->
        val path = entry.key.removePrefix("file:")
        SearchItem(entry.key, ItemKind.FILE, PathNames.name(path), PathNames.folder(path), payload = path)
    }

    private fun nameOf(key: String): String = labels[key] ?: PathNames.name(key.removePrefix("file:"))

    private fun updateConflicts() {
        val all = Graph.aliases.all().toMutableMap()
        for ((key, value) in edits) all[key] = AliasText.split(value)
        val shared = AliasRules.shared(all) { it in labels || it.startsWith("file:") }
        conflicts.text = if (shared.isEmpty()) "" else getString(R.string.shared_aliases, shared.take(4).joinToString("; ") { entry ->
            entry.alias + ": " + entry.owners.take(3).joinToString(", ") { nameOf(it) }
        })
        Ui.gone(conflicts, shared.isEmpty())
    }

    private fun exportCsv() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/csv").putExtra(Intent.EXTRA_TITLE, "tacit-aliases-${category.slug}.csv")
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun importCsv() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    @Deprecated("Platform API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        runCatching {
            when (requestCode) {
                REQUEST_EXPORT -> {
                    val table = listOf(listOf("key", "name", "aliases")) +
                        sourceRows().map { listOf(it.key, it.label, currentAliases(it)) }
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(Csv.encode(table).toByteArray()) }
                }
                REQUEST_IMPORT -> {
                    val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
                    val byLabel = sourceRows().groupBy { Normalizer.normalize(it.label) }
                    val known = sourceRows().associateBy { it.key }
                    var applied = 0
                    var skipped = 0
                    for (row in Csv.decode(text)) {
                        if (row.size < 2 || row[0] == "key") continue
                        val aliases = row.last()
                        val target = known[row[0]]?.key ?: byLabel[Normalizer.normalize(row[if (row.size >= 3) 1 else 0])]?.firstOrNull()?.key
                        if (target != null) {
                            edits[target] = aliases
                            applied++
                        } else {
                            skipped++
                        }
                    }
                    save()
                    reload()
                    val message = if (skipped == 0) getString(R.string.imported_rows, applied) else getString(R.string.imported_rows_skipped, applied, skipped)
                    AlertDialog.Builder(this).setMessage(message).setPositiveButton(R.string.ok, null).show()
                }
            }
        }
    }

    private fun sourceRows(): List<SearchItem> = Graph.engine.all().filter(category.accepts).ifEmpty { category.source() }

    private inner class TableAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = rows[position].key.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder = convertView?.tag as? RowHolder ?: RowHolder()
            holder.bind(rows[position])
            return holder.root
        }
    }

    private inner class RowHolder {
        val root = Ui.row(this@AliasesActivity)
        val name = Ui.text(this@AliasesActivity, "", 14f, palette.foreground)
        val field = EditText(this@AliasesActivity)
        var key: String? = null
        private val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val k = key ?: return
                val value = s?.toString().orEmpty()
                if (value != AliasText.join(Graph.aliases.get(k))) edits[k] = value else edits.remove(k)
            }
        }

        init {
            val pad = Ui.dp(this@AliasesActivity, 10)
            root.setPadding(0, pad / 2, 0, pad / 2)
            root.tag = this
            name.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            field.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            field.isSingleLine = true
            field.textSize = 14f
            field.setTextColor(palette.foreground)
            field.setHintTextColor(palette.muted)
            field.hint = getString(R.string.alias_hint_short)
            field.background = null
            field.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
            field.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            field.addTextChangedListener(watcher)
            root.addView(name, Ui.weight(1f))
            root.addView(field, Ui.weight(1.3f))
        }

        fun bind(item: SearchItem) {
            key = null
            name.text = if (item.subtitle.isNotEmpty() && item.kind != ItemKind.CONTACT) "${item.label} · ${item.subtitle}" else item.label
            field.setText(currentAliases(item))
            field.contentDescription = getString(R.string.aliases_for, item.label)
            key = item.key
        }
    }

    companion object {
        private const val REQUEST_EXPORT = 41
        private const val REQUEST_IMPORT = 42
    }
}
