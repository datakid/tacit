package app.tacit.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextClock
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import app.tacit.Graph
import app.tacit.R
import app.tacit.TacitApp
import app.tacit.system.BatteryStatus
import app.tacit.actions.Actions
import app.tacit.clip.ClipLock
import app.tacit.core.AliasRules
import app.tacit.core.ComposeInput
import app.tacit.core.ItemKind
import app.tacit.core.ParsedQuery
import app.tacit.core.PathNames
import app.tacit.core.ResultComposer
import app.tacit.core.Scope
import app.tacit.core.SearchItem
import app.tacit.files.FileHit
import app.tacit.sources.ContactInfo
import java.io.File

class SearchActivity : Activity(), RowCallbacks {

    private lateinit var input: EditText
    private lateinit var list: ListView
    private lateinit var adapter: ResultAdapter
    private lateinit var root: LinearLayout
    private lateinit var clockBlock: LinearLayout
    private lateinit var emptyHint: TextView
    private val composer by lazy { ResultComposer(Graph.engine, Graph.grammar, Graph.calculator) }
    private val clipLock by lazy { ClipLock(this, Graph.prefs) { runQuery(input.text.toString()) } }
    private var baseRows: List<SearchItem> = emptyList()
    private var fileRows: List<SearchItem> = emptyList()
    private var aliasedKeys: Set<String> = emptySet()
    private lateinit var batteryText: TextView
    private var batteryRegistered = false
    private var currentQuery = ""
    private var currentScope = Scope.ALL
    private var backCallback: Any? = null
    private var look: Look? = null
    private val refreshListener: () -> Unit = { if (::input.isInitialized) runQuery(input.text.toString()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (!Graph.prefs.animations) overridePendingTransitionCompat()
        buildViews()
        Graph.addListener(refreshListener)
        registerBack()
        runQuery("")
        reportFullyDrawn()
    }

    private fun overridePendingTransitionCompat() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun rebuildViews() {
        val typed = input.text.toString()
        buildViews()
        if (typed.isEmpty()) {
            runQuery("")
        } else {
            input.setText(typed)
            input.setSelection(typed.length)
        }
        if (batteryRegistered) showBattery(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
    }

    private fun refreshLook(): Boolean {
        val before = look ?: return false
        val now = Ui.look(this)
        if (now == before) return false
        if (now.theme != before.theme || now.dark != before.dark) {
            recreate()
            return true
        }
        if (now.sameViews(before)) {
            look = now
            runQuery(input.text.toString())
        } else {
            rebuildViews()
        }
        return false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (look != null) refreshLook()
    }

    private fun buildViews() {
        val current = Ui.look(this)
        look = current
        val palette = Ui.palette(this)
        if (current.wallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setFormat(PixelFormat.TRANSLUCENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable((palette.background and 0x00FFFFFF) or 0x99000000.toInt()))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setFormat(PixelFormat.OPAQUE)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.background))
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        root = Ui.column(this)
        root.fitsSystemWindows = false
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }

        clockBlock = Ui.column(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, Ui.dp(context, 32), 0, Ui.dp(context, 8))
        }
        val clock = TextClock(this).apply {
            textSize = 48f
            setTextColor(palette.foreground)
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            gravity = Gravity.CENTER
        }
        val date = TextClock(this).apply {
            textSize = 14f
            setTextColor(palette.muted)
            format12Hour = "EEEE d MMMM"
            format24Hour = "EEEE d MMMM"
            gravity = Gravity.CENTER
        }
        batteryText = Ui.text(this, "", 14f, palette.muted).apply {
            gravity = Gravity.CENTER
            setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) } }
        }
        clockBlock.addView(clock)
        clockBlock.addView(date)
        clockBlock.addView(batteryText)
        Ui.gone(clock, !current.clock)
        Ui.gone(date, !current.date)
        Ui.gone(batteryText, !current.battery)
        Ui.gone(clockBlock, !current.clock && !current.date && !current.battery)

        adapter = ResultAdapter(this, this, palette)
        list = ListView(this).apply {
            this.adapter = this@SearchActivity.adapter
            divider = null
            dividerHeight = 0
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            isFocusable = false
            itemsCanFocus = false
            setStackFromBottom(current.barBottom)
            setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0)
            clipToPadding = false
        }

        emptyHint = Ui.text(this, "", 13f, palette.muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8))
            visibility = View.GONE
        }

        val mutedIcons = IconSet(this, palette.muted)
        val strongIcons = IconSet(this, palette.foreground)
        val searchIcon = ImageView(this).apply {
            setImageDrawable(mutedIcons.make(R.drawable.ic_bar_search))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val clearButton = ImageView(this).apply {
            setImageDrawable(mutedIcons.make(R.drawable.ic_bar_clear))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = getString(R.string.clear)
            background = Ui.ripple(context, palette, oval = true)
            isClickable = true
            visibility = View.GONE
            setOnClickListener { input.setText("") }
        }

        input = EditText(this).apply {
            id = R.id.search_input
            hint = getString(R.string.search_hint)
            setHintTextColor(palette.muted)
            setTextColor(palette.foreground)
            textSize = 18f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            inputType = if (current.visiblePassword) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            background = null
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPaddingRelative(Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 14))
            contentDescription = getString(R.string.search_hint)
            setOnEditorActionListener { _, actionId, event ->
                val enter = actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event?.action == KeyEvent.ACTION_DOWN)
                if (enter) activateSelected()
                enter
            }
            setOnKeyListener { _, keyCode, event -> handleKey(keyCode, event) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    Ui.gone(clearButton, s.isNullOrEmpty())
                    runQuery(s?.toString().orEmpty())
                }
            })
        }

        val field = Ui.row(this).apply {
            background = Ui.outline(context, palette, 14)
            setPaddingRelative(Ui.dp(context, 14), 0, Ui.dp(context, 4), 0)
            addView(searchIcon, LinearLayout.LayoutParams(Ui.dp(context, 24), Ui.dp(context, 24)))
            addView(input, Ui.weight())
            addView(clearButton, LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)))
        }

        val settingsButton = ImageView(this).apply {
            setImageDrawable(strongIcons.make(R.drawable.ic_bar_settings))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = getString(R.string.settings)
            background = Ui.ripple(context, palette, oval = true)
            isClickable = true
            setOnClickListener { startActivity(Intent(context, SettingsActivity::class.java)) }
        }
        val bar = Ui.row(this).apply {
            val pad = Ui.dp(context, 12)
            setPaddingRelative(pad, pad / 2, pad / 2, pad)
            addView(field, Ui.weight())
            addView(settingsButton, LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)))
        }

        val listParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        if (current.barBottom) {
            root.addView(clockBlock, Ui.matchWrap())
            root.addView(list, listParams)
            root.addView(emptyHint, Ui.matchWrap())
            root.addView(bar, Ui.matchWrap())
        } else {
            root.addView(bar, Ui.matchWrap())
            root.addView(clockBlock, Ui.matchWrap())
            root.addView(emptyHint, Ui.matchWrap())
            root.addView(list, listParams)
        }
        setContentView(FrameLayout(this).apply { addView(root) })
        Ui.applyBars(window, current.dark)
        refreshMessengers()
    }

    private fun refreshMessengers() {
        val installed = HashSet<ContactAction>()
        if (Actions.isInstalled(this, Actions.WHATSAPP)) installed.add(ContactAction.WHATSAPP)
        if (Actions.isInstalled(this, Actions.WHATSAPP_BUSINESS)) installed.add(ContactAction.WHATSAPP_BUSINESS)
        adapter.installedMessengers = installed
    }

    private fun registerBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = OnBackInvokedCallback { handleBack() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        }
    }

    @Deprecated("Pre-33 fallback")
    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        if (input.text.isNotEmpty()) {
            input.setText("")
        } else if (!isDefaultHome()) {
            moveTaskToBack(true)
        }
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(intent, 0)?.activityInfo
        return resolved?.packageName == packageName
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        input.setText("")
        list.setSelection(0)
        showKeyboard()
    }

    override fun onResume() {
        super.onResume()
        if (refreshLook()) return
        applyRecentsPreference()
        showKeyboard()
        if (Graph.prefs.showBattery) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = if (Build.VERSION.SDK_INT >= 33) registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(batteryReceiver, filter)
            batteryRegistered = true
            showBattery(sticky)
        }
    }

    override fun onPause() {
        if (batteryRegistered) {
            unregisterReceiver(batteryReceiver)
            batteryRegistered = false
        }
        super.onPause()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = showBattery(intent)
    }

    private fun showBattery(intent: Intent?) {
        BatteryStatus.text(this, intent)?.let { batteryText.text = it }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        showKeyboard()
        if (Graph.prefs.clipboardEnabled) {
            Graph.worker.post(captureClip)
            Graph.worker.postDelayed(captureClip, CLIP_RETRY_MS)
        }
    }

    private val captureClip = Runnable {
        if (Graph.clipboard.captureIfNew()) Graph.main.post { if (currentQuery.isNotEmpty()) runQuery(input.text.toString()) }
    }

    override fun onStop() {
        super.onStop()
        Graph.files.cancel()
        clipLock.lock()
        if (Graph.prefs.closeAfterAction) input.setText("")
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onDestroy() {
        Graph.removeListener(refreshListener)
        if (Build.VERSION.SDK_INT >= 33) (backCallback as? OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        super.onDestroy()
    }

    private fun applyRecentsPreference() {
        val manager = getSystemService(android.app.ActivityManager::class.java)
        manager?.appTasks?.firstOrNull()?.setExcludeFromRecents(Graph.prefs.excludeFromRecents)
    }

    private fun showKeyboard() {
        input.requestFocus()
        input.post {
            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.show(WindowInsets.Type.ime())
            }
            getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun runQuery(raw: String) {
        android.os.Trace.beginSection("tacit.query")
        try {
            runQueryTraced(raw)
        } finally {
            android.os.Trace.endSection()
        }
    }

    private fun runQueryTraced(raw: String) {
        currentQuery = raw
        val limit = Graph.prefs.resultCount.coerceIn(3, 30)
        val output = composer.compose(ComposeInput(raw, limit, defaultEngine(), Graph.quicklinkSource::queryRows, ::clipboardItems))
        currentScope = output.parsed.scope
        val sensitiveView = output.parsed.scope == Scope.CLIPBOARD
        val aliased = aliasedFileRows(output.parsed)
        aliasedKeys = if (aliased.isEmpty()) emptySet() else aliased.mapTo(HashSet()) { it.key }
        baseRows = when {
            sensitiveView && clipLock.isLocked() -> listOf(clipLock.lockedRow())
            aliased.isEmpty() -> crashRows(raw) + output.rows
            else -> crashRows(raw) + aliased + output.rows
        }
        fileRows = emptyList()
        if (sensitiveView) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        publish()
        if (sensitiveView && clipLock.isLocked()) clipLock.requestUnlock()
        if (output.wantsFiles && Graph.prefs.filesEnabled) {
            val queryAtStart = raw
            Graph.files.search(output.parsed.text, limit) { hits ->
                Graph.main.post {
                    if (currentQuery == queryAtStart) {
                        fileRows = hits.map(::fileItem).filter { it.key !in aliasedKeys }
                        publish()
                    }
                }
            }
        } else {
            Graph.files.cancel()
        }
    }

    private fun aliasedFileRows(parsed: ParsedQuery): List<SearchItem> {
        if (parsed.scope != Scope.ALL && parsed.scope != Scope.FILES) return emptyList()
        val files = Graph.aliasedFiles()
        if (files.isEmpty()) return emptyList()
        val present = AliasRules.matching(parsed.text, files).filter { it.key.startsWith("file:content://") || File(it.key.removePrefix("file:")).exists() }
        return present.take(3).map { entry ->
            val path = entry.key.removePrefix("file:")
            SearchItem(entry.key, ItemKind.FILE, PathNames.name(path), PathNames.folder(path), payload = path)
        }
    }

    private fun crashRows(raw: String): List<SearchItem> {
        if (!raw.trim().equals("crash", ignoreCase = true)) return emptyList()
        if (!File(filesDir, TacitApp.CRASH_FILE).exists()) return emptyList()
        return listOf(SearchItem("crash", ItemKind.ACTION, getString(R.string.share_crash), "", payload = "crash|share"))
    }

    private fun publish() {
        val rows = ArrayList<SearchItem>(baseRows.size + fileRows.size)
        val web = baseRows.lastOrNull()?.takeIf { it.kind == ItemKind.WEB }
        rows.addAll(if (web != null) baseRows.dropLast(1) else baseRows)
        rows.addAll(fileRows)
        if (web != null) rows.add(web)
        val display = if (Graph.prefs.barAtBottom) rows.asReversed() else rows
        adapter.selected = if (Graph.prefs.barAtBottom) (display.size - 1).coerceAtLeast(0) else 0
        adapter.submit(display)
        if (currentScope == Scope.CLIPBOARD && rows.isEmpty()) {
            emptyHint.text = getString(if (Graph.prefs.clipboardEnabled) R.string.clip_empty else R.string.clip_off)
            emptyHint.visibility = View.VISIBLE
        } else {
            emptyHint.visibility = View.GONE
        }
        if (Graph.prefs.barAtBottom) list.setSelection(display.size - 1)
    }

    private fun fileItem(hit: FileHit): SearchItem =
        SearchItem("file:${hit.path}", ItemKind.FILE, hit.name, hit.folder.replace("/storage/emulated/0", "~"), payload = hit.path)

    private fun clipboardItems(): List<SearchItem> =
        Graph.clipboard.all().map { entry ->
            val preview = if (entry.hidden) "••••••" else entry.text.replace('\n', ' ').take(120)
            SearchItem("clip:${entry.id}", ItemKind.CLIPBOARD, preview,
                if (entry.hidden) "hidden" else android.text.format.DateUtils.getRelativeTimeSpanString(entry.time).toString(),
                payload = entry.text)
        }

    private fun defaultEngine() = Graph.grammar.engines.firstOrNull { it.prefix == Graph.prefs.defaultEngine }
        ?: Graph.grammar.engines.first()

    private fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val count = adapter.count
        if (count == 0) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                adapter.selected = (adapter.selected + 1).coerceIn(0, count - 1)
                list.smoothScrollToPosition(adapter.selected)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                adapter.selected = (adapter.selected - 1).coerceIn(0, count - 1)
                list.smoothScrollToPosition(adapter.selected)
                true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                handleBack()
                true
            }
            KeyEvent.KEYCODE_TAB -> {
                adapter.itemAt(adapter.selected)?.let { onSecondary(it, input) }
                true
            }
            else -> false
        }
    }

    private fun activateSelected() {
        val item = adapter.itemAt(adapter.selected) ?: return
        onPrimary(item, input)
    }

    override fun onPrimary(item: SearchItem, view: View) {
        if (item.payload == ClipLock.UNLOCK_PAYLOAD) {
            clipLock.requestUnlock()
            return
        }
        val bounds = Rect().also { view.getGlobalVisibleRect(it) }
        val ok = Actions.primary(this, item, bounds)
        if (!ok) return
        val leavesApp = item.kind !in setOf(ItemKind.CALCULATOR, ItemKind.SNIPPET, ItemKind.CLIPBOARD) &&
            !(item.kind == ItemKind.SETTING && item.payload.startsWith("toggle|")) && !item.payload.startsWith("copy|")
        if (leavesApp && Graph.prefs.closeAfterAction) input.setText("")
        if (!leavesApp) {
            emptyHint.text = getString(R.string.copied)
            emptyHint.visibility = View.VISIBLE
            emptyHint.postDelayed({ emptyHint.visibility = View.GONE }, 1200)
        }
    }

    override fun onSecondary(item: SearchItem, view: View) {
        SecondaryMenu.show(this, item) { runQuery(input.text.toString()) }
    }

    override fun onContactAction(item: SearchItem, action: ContactAction, view: View) {
        val info = item.extra as? ContactInfo ?: return
        val numbers = info.numbers
        if (numbers.isEmpty()) return
        val primary = info.primary
        if (primary != null || numbers.size == 1) {
            performContact(info, action, primary ?: numbers[0])
            return
        }
        AlertDialog.Builder(this)
            .setTitle(info.name)
            .setItems(numbers.toTypedArray()) { _, which -> performContact(info, action, numbers[which]) }
            .show()
    }

    private fun performContact(info: ContactInfo, action: ContactAction, number: String) {
        val ok = when (action) {
            ContactAction.CALL -> Actions.dial(this, number)
            ContactAction.MESSAGE -> Actions.sms(this, number)
            ContactAction.WHATSAPP -> Actions.whatsappContact(this, info, number, business = false)
            ContactAction.WHATSAPP_BUSINESS -> Actions.whatsappContact(this, info, number, business = true)
            ContactAction.SIGNAL -> Actions.messengerNumber(this, number, Actions.SIGNAL)
            ContactAction.TELEGRAM -> Actions.messengerNumber(this, number, Actions.TELEGRAM)
        }
        if (ok && Graph.prefs.closeAfterAction) input.setText("")
    }

    private companion object {
        const val CLIP_RETRY_MS = 600L
    }
}
