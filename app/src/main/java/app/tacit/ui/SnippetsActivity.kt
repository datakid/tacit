package app.tacit.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import app.tacit.Graph
import app.tacit.R
import app.tacit.data.Csv
import app.tacit.data.Snippet

class SnippetsActivity : Activity() {

    private lateinit var palette: Palette
    private var snippets: List<Snippet> = emptyList()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.applyTheme(this)
        super.onCreate(savedInstanceState)
        palette = Ui.palette(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.background))
        val root = Ui.column(this)
        val pad = Ui.dp(this, 16)
        val gap = Ui.dp(this, 12)
        root.setPadding(pad, pad, pad, pad)
        root.addView(Ui.text(this, getString(R.string.snippets), 22f, palette.foreground, bold = true))
        val tools = Ui.row(this).apply { setPadding(0, gap, 0, gap) }
        tools.addView(Ui.chip(this, palette, getString(R.string.add)) { edit(null) })
        tools.addView(View(this), LinearLayout.LayoutParams(gap, 1))
        tools.addView(Ui.chip(this, palette, getString(R.string.export_csv)) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv").putExtra(Intent.EXTRA_TITLE, "tacit-snippets.csv"), 51)
        })
        tools.addView(View(this), LinearLayout.LayoutParams(gap, 1))
        tools.addView(Ui.chip(this, palette, getString(R.string.import_csv)) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 52)
        })
        root.addView(tools)
        root.addView(Ui.text(this, getString(R.string.snippet_placeholders), 12f, palette.muted).apply { maxLines = 3 })
        adapter = object : BaseAdapter() {
            override fun getCount() = snippets.size
            override fun getItem(position: Int) = snippets[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val snippet = snippets[position]
                val column = Ui.column(this@SnippetsActivity)
                column.setPadding(0, gap, 0, gap)
                val head = listOf(snippet.trigger, snippet.title).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "—" }
                column.addView(Ui.text(this@SnippetsActivity, head, 15f, palette.foreground, bold = true))
                column.addView(Ui.text(this@SnippetsActivity, snippet.body.replace('\n', ' '), 13f, palette.muted))
                column.setOnClickListener { edit(snippet) }
                return column
            }
        }
        root.addView(ListView(this).apply {
            adapter = this@SnippetsActivity.adapter
            divider = android.graphics.drawable.ColorDrawable(palette.line)
            dividerHeight = 1
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(Ui.screen(this, root))
        Ui.applyBars(window, Ui.isDark(this))
        reload()
    }

    private fun reload() {
        snippets = Graph.snippets.all()
        adapter.notifyDataSetChanged()
    }

    private fun edit(existing: Snippet?) {
        val form = Ui.column(this)
        val pad = Ui.dp(this, 20)
        form.setPadding(pad, pad / 2, pad, 0)
        val trigger = EditText(this).apply { hint = getString(R.string.snippet_trigger); setText(existing?.trigger); isSingleLine = true }
        val title = EditText(this).apply { hint = getString(R.string.snippet_title); setText(existing?.title); isSingleLine = true }
        val body = EditText(this).apply { hint = getString(R.string.snippet_body); setText(existing?.body); minLines = 3 }
        form.addView(trigger)
        form.addView(title)
        form.addView(body)
        val dialog = AlertDialog.Builder(this)
            .setView(form)
            .setPositiveButton(R.string.save) { _, _ ->
                if (body.text.isNotEmpty()) {
                    Graph.snippets.upsert(Snippet(existing?.id ?: Graph.snippets.newId(), trigger.text.toString().trim(),
                        title.text.toString().trim(), body.text.toString()))
                    Graph.requestRefresh()
                    reload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) dialog.setNeutralButton(R.string.menu_delete) { _, _ ->
            Graph.snippets.delete(existing.id)
            Graph.requestRefresh()
            reload()
        }
        dialog.show()
    }

    @Deprecated("Platform API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        runCatching {
            if (requestCode == 51) {
                val table = listOf(listOf("trigger", "title", "body")) + Graph.snippets.exportRows()
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(Csv.encode(table).toByteArray()) }
            } else if (requestCode == 52) {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
                Graph.snippets.importRows(Csv.decode(text))
                Graph.requestRefresh()
                reload()
            }
        }
    }
}
