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
import app.tacit.core.ItemKind
import app.tacit.core.Normalizer
import app.tacit.core.SearchItem
import app.tacit.data.AliasText
import app.tacit.data.Csv

class AliasesActivity : Activity() {

    private val categories = listOf(
        ItemKind.APP to R.string.cat_apps,
        ItemKind.CONTACT to R.string.cat_contacts,
        ItemKind.SETTING to R.string.cat_settings,
        ItemKind.SNIPPET to R.string.cat_snippets,
        ItemKind.ACTION to R.string.cat_shortcuts
    )
    private var category = ItemKind.APP
    private var filter = ""
    private var rows: List<SearchItem> = emptyList()
    private val edits = HashMap<String, String>()
    private lateinit var adapter: TableAdapter
    private lateinit var palette: Palette
    private lateinit var tabs: LinearLayout
    private lateinit var conflicts: TextView

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
                    filter = Normalizer.normalize(s?.toString().orEmpty())
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
        for ((kind, label) in categories) {
            val chip = Ui.chip(this, palette, getString(label)) {
                if (edits.isNotEmpty()) save()
                category = kind
                buildTabs()
                reload()
            }
            if (kind == category) chip.setTypeface(null, android.graphics.Typeface.BOLD)
            tabs.addView(chip)
            tabs.addView(space())
        }
    }

    private fun reload() {
        val all = Graph.engine.all().filter { it.kind == category }.ifEmpty { sourceItems() }
        rows = all.filter { filter.isEmpty() || Normalizer.normalize(it.label).contains(filter) || currentAliases(it).contains(filter) }
            .sortedBy { it.label.lowercase() }
        adapter.notifyDataSetChanged()
        updateConflicts()
    }

    private fun sourceItems(): List<SearchItem> = when (category) {
        ItemKind.APP -> Graph.apps.appItems()
        ItemKind.CONTACT -> Graph.contacts.items()
        ItemKind.SETTING -> Graph.settings.items()
        ItemKind.SNIPPET -> Graph.snippetSource.items()
        else -> Graph.apps.items().filter { it.kind == ItemKind.ACTION }
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

    private fun updateConflicts() {
        val owners = HashMap<String, MutableList<String>>()
        val all = Graph.aliases.all().toMutableMap()
        for ((key, value) in edits) all[key] = AliasText.split(value)
        for ((key, aliases) in all) for (alias in aliases) owners.getOrPut(alias.lowercase()) { ArrayList() }.add(key)
        val shared = owners.filterValues { it.size > 1 }.keys.sorted()
        conflicts.text = if (shared.isEmpty()) "" else getString(R.string.shared_aliases, shared.take(12).joinToString(", "))
        Ui.gone(conflicts, shared.isEmpty())
    }

    private fun exportCsv() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/csv").putExtra(Intent.EXTRA_TITLE, "tacit-aliases-${category.name.lowercase()}.csv")
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
                    for (row in Csv.decode(text)) {
                        if (row.size < 2 || row[0] == "key") continue
                        val aliases = row.last()
                        val target = known[row[0]]?.key ?: byLabel[Normalizer.normalize(row[if (row.size >= 3) 1 else 0])]?.firstOrNull()?.key
                        if (target != null) {
                            edits[target] = aliases
                            applied++
                        }
                    }
                    save()
                    reload()
                    AlertDialog.Builder(this).setMessage(getString(R.string.imported_rows, applied)).setPositiveButton(R.string.ok, null).show()
                }
            }
        }
    }

    private fun sourceRows(): List<SearchItem> = Graph.engine.all().filter { it.kind == category }.ifEmpty { sourceItems() }

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
