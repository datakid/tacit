package app.tacit.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.tacit.Graph
import app.tacit.R
import app.tacit.actions.Actions
import app.tacit.actions.IntentSanitizer
import app.tacit.actions.LegacyShortcuts
import app.tacit.actions.PinShortcut
import app.tacit.core.ItemKind
import app.tacit.core.QuicklinkIcon
import app.tacit.core.QuicklinkRules
import app.tacit.core.QuicklinkType
import app.tacit.core.RefreshSources
import app.tacit.core.RowDensity
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
    private var legacyPending: ((Intent) -> Unit)? = null
    private var editor: AlertDialog? = null

    private class Choice(val label: Int, val type: QuicklinkType?, val preset: String? = null)

    private val typeLabels = listOf(
        QuicklinkType.URL to R.string.ql_type_url,
        QuicklinkType.APP to R.string.ql_type_app,
        QuicklinkType.ACTIVITY to R.string.ql_type_activity,
        QuicklinkType.APP_SHORTCUT to R.string.ql_type_shortcut,
        QuicklinkType.SETTING to R.string.ql_type_setting,
        QuicklinkType.FILE to R.string.ql_type_file,
        QuicklinkType.FOLDER to R.string.ql_type_folder,
        QuicklinkType.CONTACT to R.string.ql_type_contact,
        QuicklinkType.INTENT to R.string.ql_type_intent
    )

    private val choices = listOf(
        Choice(R.string.ql_type_url, QuicklinkType.URL),
        Choice(R.string.ql_type_app, QuicklinkType.APP),
        Choice(R.string.ql_type_activity, QuicklinkType.ACTIVITY),
        Choice(R.string.ql_type_shortcut, QuicklinkType.APP_SHORTCUT),
        Choice(R.string.ql_type_setting, QuicklinkType.SETTING),
        Choice(R.string.ql_choice_settings_explorer, QuicklinkType.ACTIVITY, ActivityExplorer.SETTINGS_PACKAGE),
        Choice(R.string.ql_type_file, QuicklinkType.FILE),
        Choice(R.string.ql_type_folder, QuicklinkType.FOLDER),
        Choice(R.string.ql_type_contact, QuicklinkType.CONTACT),
        Choice(R.string.ql_choice_legacy, null),
        Choice(R.string.ql_type_intent, QuicklinkType.INTENT)
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
        val density = RowDensity.of(Graph.prefs.density)
        val rowPad = Ui.dp(this, density.listPadDp)
        val rowMin = Ui.dp(this, density.listRowDp)
        adapter = object : BaseAdapter() {
            override fun getCount() = links.size
            override fun getItem(position: Int) = links[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val link = links[position]
                val column = Ui.column(this@QuicklinksActivity)
                column.setPadding(0, rowPad, 0, rowPad)
                column.minimumHeight = rowMin
                column.gravity = Gravity.CENTER_VERTICAL
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
        if (requestCode == REQUEST_LEGACY) {
            val action = legacyPending
            legacyPending = null
            if (resultCode == RESULT_OK && data != null) action?.invoke(data)
            return
        }
        val callback = pending
        pending = null
        val uri = data?.data
        if (requestCode == REQUEST_DOCUMENT && resultCode == RESULT_OK && uri != null) callback?.invoke(uri)
    }

    private fun legacyShortcut() {
        Graph.worker.post {
            val handlers = LegacyShortcuts.handlers(this)
            Graph.main.post {
                if (isFinishing || isDestroyed) return@post
                if (handlers.isEmpty()) {
                    Toast.makeText(this, R.string.ql_nothing_found, Toast.LENGTH_LONG).show()
                    return@post
                }
                ItemPicker.show(this, palette, getString(R.string.ql_choice_legacy), handlers) { handler ->
                    val request = LegacyShortcuts.createIntent(handler.payload)
                    legacyPending = { data -> finishLegacy(data, handler.subtitle) }
                    try {
                        if (request == null) throw ActivityNotFoundException()
                        startActivityForResult(request, REQUEST_LEGACY)
                    } catch (error: ActivityNotFoundException) {
                        legacyPending = null
                        Toast.makeText(this, R.string.ql_cannot_open, Toast.LENGTH_LONG).show()
                    } catch (error: SecurityException) {
                        legacyPending = null
                        Toast.makeText(this, R.string.ql_cannot_open, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun finishLegacy(data: Intent, detail: String) {
        if (LegacyShortcuts.accept(this, data)) {
            Graph.requestRefresh(RefreshSources.APPS)
            Toast.makeText(this, R.string.ql_legacy_added, Toast.LENGTH_SHORT).show()
            editor?.dismiss()
            return
        }
        val draft = LegacyShortcuts.draft(this, data, detail)
        if (draft == null) {
            Toast.makeText(this, R.string.ql_cannot_open, Toast.LENGTH_LONG).show()
            return
        }
        QuicklinkMaker.save(this, draft)
        editor?.dismiss()
        reload()
    }

    private fun pick(type: QuicklinkType, name: EditText, preset: String?, onPicked: (String, String) -> Unit) {
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
            QuicklinkType.ACTIVITY -> ActivityExplorer.start(this, palette, typeLabel(type), preset) { screen ->
                fillName(screen.name)
                onPicked(screen.target, screen.label)
            }
            QuicklinkType.URL, QuicklinkType.INTENT -> Unit
        }
    }

    private fun chooseLabel(type: QuicklinkType): Int = when (type) {
        QuicklinkType.FILE -> R.string.ql_pick_file
        QuicklinkType.FOLDER -> R.string.ql_pick_folder
        QuicklinkType.ACTIVITY -> R.string.ql_choose_screen
        else -> R.string.ql_choose
    }

    private fun edit(existing: Quicklink?) {
        val form = Ui.column(this)
        val pad = Ui.dp(this, 20)
        val gap = Ui.dp(this, 8)
        val own = packageName
        form.setPadding(pad, pad / 2, pad, 0)
        val name = EditText(this).apply { hint = getString(R.string.ql_name); setText(existing?.name); isSingleLine = true }
        val startType = existing?.type ?: QuicklinkType.URL
        var type = startType
        var picked = if (existing != null && existing.type != QuicklinkType.URL) existing.target else ""
        var pickedLabel = if (existing != null && existing.type != QuicklinkType.URL) existing.detail else ""
        val originalPicked = picked
        val originalLabel = pickedLabel
        var icon = existing?.icon ?: QuicklinkIcon.GLYPH
        var preset: String? = null
        var verified = false
        val typeChip = Ui.chip(this, palette, typeLabel(type)) {}
        val url = EditText(this).apply {
            hint = getString(R.string.ql_url_hint)
            setText(if (existing?.type == QuicklinkType.URL) existing.target else "")
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val help = Ui.text(this, getString(R.string.ql_url_help), 12f, palette.muted).apply { maxLines = 3 }
        val choose = Ui.chip(this, palette, getString(chooseLabel(type))) {}
        val intentBox = Ui.column(this)
        var intentForm: IntentForm? = null
        fun intent(): IntentForm = intentForm ?: IntentForm(this, palette, existing?.takeIf { it.type == QuicklinkType.INTENT }?.let {
            IntentSanitizer.specOfTarget(it.target, own)
        }) { verified = false }.also {
            intentForm = it
            intentBox.addView(it.view)
        }
        val tryChip = Ui.chip(this, palette, getString(R.string.ql_try)) {}
        val iconChip = Ui.chip(this, palette, "") {}
        val state = if (existing != null) states[existing.id] ?: TargetState.OK else TargetState.OK
        val note = Ui.text(this, getString(if (state == TargetState.REPICK) R.string.ql_repick else R.string.ql_missing_here), 12f, palette.muted)
            .apply { maxLines = 2 }
        val aliases = EditText(this).apply {
            hint = getString(R.string.ql_aliases)
            setText(if (existing != null) AliasText.join(Graph.aliases.get("ql:${existing.id}")) else "")
            isSingleLine = true
        }
        fun refresh() {
            icon = QuicklinkIcon.normalize(type, icon)
            val label = typeLabel(type)
            typeChip.text = label
            typeChip.contentDescription = label
            Ui.gone(url, type != QuicklinkType.URL)
            Ui.gone(help, type != QuicklinkType.URL)
            Ui.gone(choose, type == QuicklinkType.URL || type == QuicklinkType.INTENT)
            if (type == QuicklinkType.INTENT) intent()
            Ui.gone(intentBox, type != QuicklinkType.INTENT)
            Ui.gone(tryChip, !(type == QuicklinkType.INTENT || (type == QuicklinkType.ACTIVITY && picked.isNotEmpty())))
            val shown = if (picked.isEmpty() || pickedLabel.isEmpty()) getString(chooseLabel(type)) else pickedLabel
            choose.text = shown
            choose.contentDescription = shown
            val iconText = getString(R.string.ql_icon_value, iconLabel(icon))
            iconChip.text = iconText
            iconChip.contentDescription = iconText
            val flagged = state != TargetState.OK && type == startType && picked == originalPicked &&
                type != QuicklinkType.URL && type != QuicklinkType.INTENT
            Ui.gone(note, !flagged)
        }
        choose.setOnClickListener {
            pick(type, name, preset) { target, label ->
                picked = target
                pickedLabel = label
                refresh()
            }
        }
        tryChip.setOnClickListener {
            val intent = if (type == QuicklinkType.INTENT) {
                val spec = intent().spec()
                val problems = spec.validate(own)
                if (problems.isNotEmpty()) {
                    Toast.makeText(this, IntentForm.message(problems[0]), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                IntentSanitizer.build(spec, own)?.let { IntentSanitizer.launchable(it) }
            } else {
                IntentSanitizer.componentIntent(picked, own)
            }
            if (intent != null && Actions.start(this, intent)) {
                verified = true
            } else {
                Toast.makeText(this, R.string.ql_cannot_open, Toast.LENGTH_LONG).show()
            }
        }
        iconChip.setOnClickListener {
            val options = ArrayList<Pair<String, () -> Unit>>()
            options.add(getString(R.string.ql_icon_glyph) to {
                icon = QuicklinkIcon.GLYPH
                refresh()
            })
            if (QuicklinkIcon.supportsApp(type)) {
                options.add(getString(R.string.ql_icon_app) to {
                    icon = QuicklinkIcon.APP
                    refresh()
                })
            }
            options.add(getString(R.string.ql_icon_letters) to {
                askLetters(name.text.toString(), icon) {
                    icon = it
                    refresh()
                }
            })
            AlertDialog.Builder(this)
                .setTitle(R.string.ql_icon)
                .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
                .show()
        }
        typeChip.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.ql_type)
                .setItems(choices.map { getString(it.label) }.toTypedArray()) { _, which ->
                    val choice = choices[which]
                    val newType = choice.type
                    if (newType == null) {
                        legacyShortcut()
                    } else if (newType != type || choice.preset != preset) {
                        type = newType
                        preset = choice.preset
                        if (type == startType && existing != null && preset == null) {
                            picked = originalPicked
                            pickedLabel = originalLabel
                        } else {
                            picked = ""
                            pickedLabel = ""
                        }
                        refresh()
                        if (preset != null) choose.performClick()
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
        form.addView(intentBox)
        form.addView(choose, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = gap
        })
        form.addView(tryChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = gap
        })
        form.addView(note)
        form.addView(aliases)
        form.addView(iconChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = gap
        })
        if (existing != null && PinShortcut.supported(this)) {
            form.addView(Ui.chip(this, palette, getString(R.string.menu_add_home)) { pin(existing) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
        }
        refresh()
        val builder = AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) builder.setNeutralButton(R.string.menu_delete) { _, _ ->
            Graph.quicklinks.delete(existing.id)
            PinShortcut.disable(this, existing.id)
            Graph.aliases.set("ql:${existing.id}", emptyList())
            Graph.reloadQuicklinks()
            reload()
        }
        val dialog = builder.create()
        dialog.show()
        editor = dialog
        dialog.setOnDismissListener { if (editor === dialog) editor = null }
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
            } else if (type == QuicklinkType.INTENT) {
                val spec = intent().spec()
                val problems = spec.validate(own)
                if (problems.isNotEmpty()) {
                    Toast.makeText(this, IntentForm.message(problems[0]), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val built = IntentSanitizer.build(spec, own)
                if (built == null) {
                    Toast.makeText(this, R.string.ql_intent_bad_field, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val encoded = IntentSanitizer.encode(built)
                val unchanged = existing?.type == QuicklinkType.INTENT && existing.target == encoded
                if (!verified && !unchanged && !IntentSanitizer.resolves(this, built)) {
                    Toast.makeText(this, R.string.ql_intent_unresolved, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                target = encoded
                detail = spec.summary()
            } else {
                if (picked.isEmpty()) {
                    Toast.makeText(this, R.string.ql_pick_first, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                target = picked
                detail = pickedLabel
            }
            val id = existing?.id ?: Graph.quicklinks.newId()
            val saved = Quicklink(id, title, type, target, detail, QuicklinkIcon.normalize(type, icon))
            Graph.quicklinks.upsert(saved)
            if (existing != null && (existing.name != title || existing.type != type || existing.icon != saved.icon)) {
                PinShortcut.update(this, saved)
            }
            Graph.aliases.set("ql:$id", AliasText.split(aliases.text.toString()))
            Graph.reloadQuicklinks()
            reload()
            dialog.dismiss()
        }
    }

    private fun iconLabel(icon: String): String {
        val letters = QuicklinkIcon.lettersOf(icon)
        return when {
            icon == QuicklinkIcon.APP -> getString(R.string.ql_icon_app)
            letters != null -> getString(R.string.ql_icon_letters) + " " + letters
            else -> getString(R.string.ql_icon_glyph)
        }
    }

    private fun askLetters(name: String, current: String, onChosen: (String) -> Unit) {
        val field = EditText(this).apply {
            hint = getString(R.string.ql_icon_letters_hint)
            isSingleLine = true
            setText(QuicklinkIcon.lettersOf(current) ?: QuicklinkIcon.suggestedLetters(name))
            setSelection(text.length)
            val inset = Ui.dp(context, 20)
            setPadding(inset, inset, inset, inset)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ql_icon_letters)
            .setView(field)
            .setPositiveButton(R.string.save) { _, _ -> QuicklinkIcon.letter(field.text.toString())?.let(onChosen) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pin(link: Quicklink) {
        if (!PinShortcut.request(this, link)) Toast.makeText(this, R.string.ql_pin_unsupported, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_EDIT = "quicklink_edit"
        private const val REQUEST_DOCUMENT = 51
        private const val REQUEST_LEGACY = 52
    }
}
