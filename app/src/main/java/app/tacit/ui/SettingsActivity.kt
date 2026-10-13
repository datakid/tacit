package app.tacit.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import app.tacit.Graph
import app.tacit.IndexCache
import app.tacit.R
import app.tacit.TacitApp
import app.tacit.data.Backup
import app.tacit.clip.ClipLock
import app.tacit.files.GrantedTrees
import app.tacit.files.StorageAccess
import java.io.File

class SettingsActivity : Activity() {

    private lateinit var palette: Palette
    private lateinit var container: LinearLayout
    private val prefs get() = Graph.prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.applyTheme(this)
        super.onCreate(savedInstanceState)
        container = Ui.column(this)
        val pad = Ui.dp(this, 16)
        container.setPadding(pad, pad, pad, pad * 2)
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(container)
        })
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        palette = Ui.palette(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.background))
        Ui.applyBars(window, Ui.isDark(this))
        container.removeAllViews()
        header(getString(R.string.settings))

        section(R.string.sec_look)
        choice(R.string.theme, listOf("system", "dark", "light"), listOf(R.string.theme_system, R.string.theme_dark, R.string.theme_light),
            prefs.theme, onPick = { prefs.theme = it }, after = { recreate() })
        choice(R.string.icons, listOf("mono", "color", "none"), listOf(R.string.icons_mono, R.string.icons_color, R.string.icons_none),
            prefs.iconMode) { prefs.iconMode = it; Graph.icons.clear() }
        toggle(R.string.bar_bottom, prefs.barAtBottom) { prefs.barAtBottom = it }
        toggle(R.string.show_clock, prefs.showClock) { prefs.showClock = it }
        toggle(R.string.show_date, prefs.showDate) { prefs.showDate = it }
        toggle(R.string.show_battery, prefs.showBattery) { prefs.showBattery = it }
        toggle(R.string.wallpaper, prefs.showWallpaper) { prefs.showWallpaper = it }
        number(R.string.result_count, prefs.resultCount, 3, 30) { prefs.resultCount = it }

        section(R.string.sec_sources)
        toggle(R.string.contacts, prefs.contactsEnabled) { enabled -> setContacts(enabled) }
        toggle(R.string.files, prefs.filesEnabled) { enabled -> setFiles(enabled) }
        if (prefs.filesEnabled) {
            fileSourceRows()
            toggle(R.string.files_hidden, prefs.filesHidden) { prefs.filesHidden = it; Graph.files.rescan() }
            action(getString(R.string.rescan_files, Graph.files.count())) { Graph.files.rescan { Graph.main.post { build() } } }
        }
        toggle(R.string.clipboard, prefs.clipboardEnabled) { enabled ->
            prefs.clipboardEnabled = enabled
            if (!enabled) Graph.clipboard.clear()
            rebuildSoon()
        }
        if (prefs.clipboardEnabled) {
            note(R.string.clipboard_limit)
            number(R.string.clip_days, prefs.clipRetentionDays, 0, 3650) { prefs.clipRetentionDays = it }
            number(R.string.clip_max, prefs.clipMaxItems, 0, 100000) { prefs.clipMaxItems = it }
            choice(R.string.sensitive_policy, listOf("skip", "expire", "hide"),
                listOf(R.string.sensitive_skip, R.string.sensitive_expire, R.string.sensitive_hide), prefs.sensitivePolicy) { prefs.sensitivePolicy = it }
            if (ClipLock.canAuthenticate(this)) {
                toggle(R.string.clip_biometric, prefs.clipBiometric) { prefs.clipBiometric = it }
            } else {
                note(R.string.clip_biometric_unavailable)
            }
            action(getString(R.string.clear_clipboard)) {
                AlertDialog.Builder(this).setMessage(R.string.clear_clipboard_confirm)
                    .setPositiveButton(R.string.ok) { _, _ -> Graph.clipboard.clear() }
                    .setNegativeButton(R.string.cancel, null).show()
            }
        }

        section(R.string.sec_search)
        choice(R.string.fuzzy, listOf("OFF", "NORMAL", "LOOSE"), listOf(R.string.fuzzy_off, R.string.fuzzy_normal, R.string.fuzzy_loose),
            prefs.strictness) { prefs.strictness = it; Graph.applyPrefs() }
        toggle(R.string.transliterate, prefs.transliterate) { prefs.transliterate = it; Graph.applyPrefs(); Graph.requestRefresh() }
        toggle(R.string.visible_password, prefs.visiblePasswordInput) { prefs.visiblePasswordInput = it }
        toggle(R.string.starred_pins, prefs.starredAsPins) { prefs.starredAsPins = it; Graph.requestRebuild() }
        textPref(R.string.kind_order, prefs.kindOrder) { prefs.kindOrder = it; Graph.applyPrefs() }
        action(getString(R.string.aliases)) { startActivity(Intent(this, AliasesActivity::class.java)) }
        action(getString(R.string.snippets)) { startActivity(Intent(this, SnippetsActivity::class.java)) }
        action(getString(R.string.quicklinks)) { startActivity(Intent(this, QuicklinksActivity::class.java)) }
        action(getString(R.string.hidden_items, prefs.hidden.size)) { showHidden() }

        section(R.string.sec_actions)
        textPref(R.string.country_code, prefs.countryCode) { prefs.countryCode = it.filter { c -> c.isDigit() } }
        toggle(R.string.direct_call, prefs.directCall) { enabled ->
            prefs.directCall = enabled
            if (enabled && checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL)
            }
        }
        textPref(R.string.default_engine, prefs.defaultEngine) { prefs.defaultEngine = it.trim() }
        textPref(R.string.engines, prefs.customEngines.ifEmpty { Graph.grammar.engines.joinToString("\n") { "${it.prefix}|${it.name}|${it.template}" } },
            multiline = true) { prefs.customEngines = it; Graph.applyPrefs() }
        toggle(R.string.close_after, prefs.closeAfterAction) { prefs.closeAfterAction = it }
        toggle(R.string.exclude_recents, prefs.excludeFromRecents) { prefs.excludeFromRecents = it }
        number(R.string.precision, prefs.precision, 8, 64) { prefs.precision = it; Graph.applyPrefs() }

        section(R.string.sec_prefixes)
        textPref(R.string.p_calc, prefs.prefixCalculator) { prefs.prefixCalculator = it; Graph.applyPrefs() }
        textPref(R.string.p_clip, prefs.prefixClipboard) { prefs.prefixClipboard = it; Graph.applyPrefs() }
        textPref(R.string.p_settings, prefs.prefixSettings) { prefs.prefixSettings = it; Graph.applyPrefs() }
        textPref(R.string.p_contacts, prefs.prefixContacts) { prefs.prefixContacts = it; Graph.applyPrefs() }
        textPref(R.string.p_files, prefs.prefixFiles) { prefs.prefixFiles = it; Graph.applyPrefs() }
        textPref(R.string.p_snippets, prefs.prefixSnippets) { prefs.prefixSnippets = it; Graph.applyPrefs() }

        section(R.string.sec_system)
        action(getString(R.string.set_home)) { safely { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }
        action(getString(R.string.set_assistant)) { safely { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } }
        action(getString(R.string.keys)) { showKeys() }
        action(getString(R.string.export_all)) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "tacit-backup.json"), REQ_EXPORT)
        }
        action(getString(R.string.import_all)) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT)
        }
        val crash = File(filesDir, TacitApp.CRASH_FILE)
        if (crash.exists()) action(getString(R.string.share_crash)) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, crash.readText())
            startActivity(Intent.createChooser(send, null))
        }
        action(getString(R.string.rebuild_index)) {
            IndexCache.clear(this)
            Graph.requestRefresh()
        }
        note(R.string.about_line)
    }

    private fun rebuildSoon() {
        container.post { build() }
    }

    private inline fun safely(block: () -> Unit) {
        runCatching { block() }
    }

    private fun setContacts(enabled: Boolean) {
        if (enabled && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
            return
        }
        prefs.contactsEnabled = enabled
        Graph.requestRefresh()
    }

    private fun setFiles(enabled: Boolean) {
        if (!enabled) {
            prefs.filesEnabled = false
            Graph.files.clear()
            build()
            return
        }
        if (Build.VERSION.SDK_INT < 30) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_FILES)
                return
            }
        } else if (!StorageAccess.hasAllFiles() && GrantedTrees.list(this).isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.files_permission_explain)
                .setPositiveButton(R.string.files_all_access) { _, _ ->
                    prefs.filesEnabled = true
                    requestAllFiles()
                }
                .setNeutralButton(R.string.files_choose_folders) { _, _ ->
                    prefs.filesEnabled = true
                    pickFolder()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> build() }
                .show()
            return
        }
        prefs.filesEnabled = true
        Graph.files.rescan { Graph.main.post { build() } }
        build()
    }

    private fun requestAllFiles() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        runCatching { startActivityForResult(intent, REQ_TREE) }
    }

    private fun fileSourceRows() {
        val full = Graph.files.hasFullAccess()
        note(if (full) R.string.files_mode_all else R.string.files_mode_folders)
        if (!full) {
            for (tree in GrantedTrees.list(this)) {
                action(getString(R.string.files_folder_item, GrantedTrees.label(tree))) {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.files_folder_remove, GrantedTrees.label(tree)))
                        .setPositiveButton(R.string.menu_delete) { _, _ ->
                            GrantedTrees.remove(this, tree)
                            Graph.files.rescan { Graph.main.post { build() } }
                            build()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            action(getString(R.string.files_add_folder)) { pickFolder() }
            if (Build.VERSION.SDK_INT >= 30) action(getString(R.string.files_all_access)) { requestAllFiles() }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQ_CONTACTS -> {
                prefs.contactsEnabled = granted
                Graph.requestRefresh()
            }
            REQ_CALL -> if (!granted) prefs.directCall = false
            REQ_FILES -> if (granted) {
                prefs.filesEnabled = true
                Graph.files.rescan { Graph.main.post { build() } }
            }
        }
        build()
    }

    @Deprecated("Platform API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        runCatching {
            if (requestCode == REQ_TREE) {
                GrantedTrees.add(this, uri)
                prefs.filesEnabled = true
                Graph.files.rescan { Graph.main.post { build() } }
                build()
            } else if (requestCode == REQ_EXPORT) {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(Backup.export().toByteArray()) }
            } else if (requestCode == REQ_IMPORT) {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
                Backup.import(text)
                Graph.applyPrefs()
                Graph.requestRefresh()
                build()
            }
        }
    }

    private fun showHidden() {
        val keys = prefs.hidden.toList().sorted()
        if (keys.isEmpty()) return
        val labels = keys.map { it.substringAfter(':').substringBefore('/') }.toTypedArray()
        val checked = BooleanArray(keys.size) { true }
        AlertDialog.Builder(this)
            .setTitle(R.string.hidden_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.hidden = keys.filterIndexed { index, _ -> checked[index] }.toSet()
                Graph.requestRebuild()
                build()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showKeys() {
        val text = getString(R.string.keys_body, prefs.prefixCalculator, prefs.prefixClipboard.trim(), prefs.prefixSettings,
            prefs.prefixContacts, prefs.prefixFiles, prefs.prefixSnippets,
            Graph.grammar.engines.joinToString("\n") { "${it.prefix} ⎵  ${it.name}" })
        AlertDialog.Builder(this).setTitle(R.string.keys).setMessage(text).setPositiveButton(R.string.ok, null).show()
    }

    private fun header(value: String) {
        container.addView(Ui.text(this, value, 26f, palette.foreground, bold = true).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(0, 0, 0, Ui.dp(context, 8))
        })
    }

    private fun section(label: Int) {
        container.addView(Ui.text(this, getString(label).uppercase(), 12f, palette.muted, bold = true).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(0, Ui.dp(context, 24), 0, Ui.dp(context, 6))
        })
    }

    private fun note(label: Int) {
        container.addView(TextView(this).apply {
            text = getString(label)
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 8))
        })
    }

    private fun rowView(label: String, value: String?): LinearLayout {
        val row = Ui.row(this)
        row.minimumHeight = Ui.dp(this, 52)
        row.isClickable = true
        row.isFocusable = true
        val texts = Ui.column(this)
        texts.addView(Ui.text(this, label, 16f, palette.foreground).apply {
            maxLines = 2
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        })
        if (value != null) {
            texts.addView(Ui.text(this, value, 13f, palette.muted).apply {
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
            })
        }
        row.addView(texts, Ui.weight())
        container.addView(row, Ui.matchWrap())
        return row
    }

    private fun toggle(label: Int, value: Boolean, onChange: (Boolean) -> Unit) {
        val row = rowView(getString(label), null)
        val switch = Switch(this).apply {
            isChecked = value
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        row.addView(switch)
        row.setOnClickListener { switch.toggle() }
    }

    private fun action(label: String, onClick: () -> Unit) {
        rowView(label, null).setOnClickListener { onClick() }
    }

    private fun choice(
        label: Int,
        values: List<String>,
        labels: List<Int>,
        current: String,
        after: () -> Unit = { build() },
        onPick: (String) -> Unit
    ) {
        val index = values.indexOf(current).coerceAtLeast(0)
        rowView(getString(label), getString(labels[index])).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(label)
                .setSingleChoiceItems(labels.map { getString(it) }.toTypedArray(), index) { dialog, which ->
                    onPick(values[which])
                    dialog.dismiss()
                    after()
                }
                .show()
        }
    }

    private fun number(label: Int, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
        val shown = if (value == 0 && min == 0) getString(R.string.unlimited) else value.toString()
        rowView(getString(label), shown).setOnClickListener {
            val field = EditText(this).apply {
                setText(value.toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            AlertDialog.Builder(this).setTitle(label).setView(field)
                .setPositiveButton(R.string.save) { _, _ ->
                    field.text.toString().toIntOrNull()?.let { onChange(it.coerceIn(min, max)) }
                    build()
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun textPref(label: Int, value: String, multiline: Boolean = false, onChange: (String) -> Unit) {
        rowView(getString(label), value.lines().first().ifEmpty { "—" }).setOnClickListener {
            val field = EditText(this).apply {
                setText(value)
                if (!multiline) isSingleLine = true else minLines = 4
            }
            AlertDialog.Builder(this).setTitle(label).setView(field)
                .setPositiveButton(R.string.save) { _, _ ->
                    onChange(field.text.toString())
                    build()
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
    }

    companion object {
        private const val REQ_CONTACTS = 1
        private const val REQ_CALL = 2
        private const val REQ_EXPORT = 3
        private const val REQ_IMPORT = 4
        private const val REQ_FILES = 5
        private const val REQ_TREE = 6
    }
}
