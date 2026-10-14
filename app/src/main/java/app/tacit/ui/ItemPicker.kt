package app.tacit.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import app.tacit.R
import app.tacit.core.ItemKind
import app.tacit.core.Normalizer
import app.tacit.core.SearchItem

object ItemPicker {

    fun show(activity: Activity, palette: Palette, title: String, items: List<SearchItem>, onPick: (SearchItem) -> Unit) {
        val sorted = items.sortedBy { it.label.lowercase() }
        val haystacks = sorted.map { Normalizer.normalize(it.label + " " + it.subtitle) }
        val pad = Ui.dp(activity, 16)
        val rowPad = Ui.dp(activity, 12)
        var shown: List<SearchItem> = sorted
        val container = Ui.column(activity)
        container.setPadding(pad, rowPad / 2, pad, 0)
        val search = EditText(activity).apply {
            hint = activity.getString(R.string.search_hint)
            isSingleLine = true
        }
        val empty = Ui.text(activity, activity.getString(R.string.ql_nothing_found), 13f, palette.muted).apply {
            setPadding(0, rowPad, 0, rowPad)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        val list = ListView(activity)
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView as? TextView ?: TextView(activity).apply {
                    setPadding(0, rowPad, 0, rowPad)
                    textSize = 15f
                    setTextColor(palette.foreground)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                val item = shown[position]
                view.text = if (item.subtitle.isNotEmpty() && item.kind != ItemKind.SETTING) "${item.label} · ${item.subtitle}" else item.label
                return view
            }
        }
        list.adapter = adapter
        list.divider = android.graphics.drawable.ColorDrawable(palette.line)
        list.dividerHeight = 1
        container.addView(search, Ui.matchWrap())
        container.addView(empty, Ui.matchWrap())
        container.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 320)))
        Ui.gone(empty, sorted.isNotEmpty())
        Ui.gone(list, sorted.isEmpty())
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            val item = shown[position]
            dialog.dismiss()
            onPick(item)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val needle = Normalizer.normalize(s?.toString().orEmpty()).trim()
                shown = if (needle.isEmpty()) sorted else sorted.filterIndexed { index, _ -> haystacks[index].contains(needle) }
                adapter.notifyDataSetChanged()
                Ui.gone(empty, shown.isNotEmpty())
                Ui.gone(list, shown.isEmpty())
            }
        })
        dialog.show()
    }
}
