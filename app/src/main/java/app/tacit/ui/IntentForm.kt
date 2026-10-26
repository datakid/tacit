package app.tacit.ui

import android.app.Activity
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import app.tacit.R
import app.tacit.core.ExtraType
import app.tacit.core.IntentExtra
import app.tacit.core.IntentSpec

class IntentForm(
    private val activity: Activity,
    private val palette: Palette,
    initial: IntentSpec?,
    private val onChange: () -> Unit
) {

    private class ExtraRow(val view: LinearLayout, val key: EditText, val value: EditText, var type: ExtraType)

    val view: LinearLayout = Ui.column(activity)
    private val action = field(R.string.ql_intent_action, initial?.action.orEmpty())
    private val data = field(R.string.ql_intent_data, initial?.data.orEmpty(), InputType.TYPE_TEXT_VARIATION_URI)
    private val mime = field(R.string.ql_intent_mime, initial?.mime.orEmpty())
    private val categories = field(R.string.ql_intent_categories, IntentSpec.categoriesText(initial?.categories.orEmpty()))
    private val component = field(R.string.ql_intent_component, initial?.component.orEmpty())
    private val extrasBox = Ui.column(activity)
    private val rows = ArrayList<ExtraRow>()
    private val addChip = Ui.chip(activity, palette, activity.getString(R.string.ql_extra_add)) { addRow(null) }

    init {
        view.addView(action)
        view.addView(data)
        view.addView(mime)
        view.addView(categories)
        view.addView(component)
        view.addView(extrasBox)
        view.addView(addChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(activity, 8)
        })
        for (extra in initial?.extras.orEmpty()) addRow(extra)
    }

    fun spec(): IntentSpec = IntentSpec(
        action = action.text.toString().trim(),
        data = data.text.toString().trim(),
        mime = mime.text.toString().trim(),
        categories = IntentSpec.parseCategories(categories.text.toString()),
        component = component.text.toString().trim(),
        extras = rows.map { IntentExtra(it.key.text.toString().trim(), it.type, it.value.text.toString()) }
            .filter { it.key.isNotEmpty() || it.value.isNotEmpty() }
    )

    private fun field(hint: Int, text: String, variation: Int = 0): EditText = EditText(activity).apply {
        this.hint = activity.getString(hint)
        setText(text)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or variation
        textDirection = View.TEXT_DIRECTION_LTR
        watch(this)
    }

    private fun watch(edit: EditText) {
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onChange()
        })
    }

    private fun typeLabel(type: ExtraType): String = activity.getString(
        when (type) {
            ExtraType.STRING -> R.string.ql_extra_text
            ExtraType.INT -> R.string.ql_extra_int
            ExtraType.LONG -> R.string.ql_extra_long
            ExtraType.BOOLEAN -> R.string.ql_extra_bool
        }
    )

    private fun next(type: ExtraType): ExtraType = ExtraType.entries[(type.ordinal + 1) % ExtraType.entries.size]

    private fun addRow(extra: IntentExtra?) {
        if (rows.size >= IntentSpec.MAX_EXTRAS) return
        val container = Ui.column(activity).apply { setPadding(0, Ui.dp(activity, 8), 0, 0) }
        val line = Ui.row(activity)
        val key = field(R.string.ql_extra_key, extra?.key.orEmpty())
        val value = field(R.string.ql_extra_value, extra?.value.orEmpty())
        val row = ExtraRow(container, key, value, extra?.type ?: ExtraType.STRING)
        val typeChip = Ui.chip(activity, palette, typeLabel(row.type)) {}
        typeChip.setOnClickListener {
            row.type = next(row.type)
            typeChip.text = typeLabel(row.type)
            typeChip.contentDescription = typeLabel(row.type)
            onChange()
        }
        val remove = Ui.chip(activity, palette, "×") {}
        remove.contentDescription = activity.getString(R.string.ql_extra_remove)
        remove.setOnClickListener {
            rows.remove(row)
            extrasBox.removeView(container)
            Ui.gone(addChip, rows.size >= IntentSpec.MAX_EXTRAS)
            onChange()
        }
        val gap = Ui.dp(activity, 6)
        line.addView(key, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(typeChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = gap })
        line.addView(remove, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = gap })
        container.addView(line, Ui.matchWrap())
        container.addView(value, Ui.matchWrap())
        rows.add(row)
        extrasBox.addView(container, Ui.matchWrap())
        Ui.gone(addChip, rows.size >= IntentSpec.MAX_EXTRAS)
        onChange()
    }

    companion object {
        fun message(problem: IntentSpec.Problem): Int = when (problem) {
            IntentSpec.Problem.NOTHING_TO_LAUNCH -> R.string.ql_intent_need_target
            IntentSpec.Problem.BAD_ACTION,
            IntentSpec.Problem.BAD_DATA,
            IntentSpec.Problem.BAD_MIME,
            IntentSpec.Problem.BAD_CATEGORY,
            IntentSpec.Problem.BAD_COMPONENT -> R.string.ql_intent_bad_field
            IntentSpec.Problem.BLOCKED_DATA,
            IntentSpec.Problem.OWN_APP -> R.string.ql_intent_blocked
            IntentSpec.Problem.TOO_MANY_EXTRAS,
            IntentSpec.Problem.BAD_EXTRA_KEY,
            IntentSpec.Problem.EXTRA_KEY_TOO_LONG,
            IntentSpec.Problem.EXTRA_TEXT_TOO_LONG,
            IntentSpec.Problem.BAD_EXTRA_NUMBER,
            IntentSpec.Problem.BAD_EXTRA_BOOLEAN -> R.string.ql_intent_bad_extra
        }
    }
}
