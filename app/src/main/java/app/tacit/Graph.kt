package app.tacit

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import app.tacit.calc.Calculator
import app.tacit.clip.ClipboardStore
import app.tacit.core.AliasEntry
import app.tacit.core.AliasRules
import app.tacit.core.IndexOptions
import app.tacit.core.PrefixGrammar
import app.tacit.core.SearchEngine
import app.tacit.core.SearchItem
import app.tacit.data.AliasStore
import app.tacit.data.QuicklinkStore
import app.tacit.data.SnippetStore
import app.tacit.files.FileIndex
import app.tacit.sources.AppSource
import app.tacit.sources.ContactSource
import app.tacit.sources.QuicklinkSource
import app.tacit.sources.SettingSource
import app.tacit.sources.SnippetSource
import app.tacit.ui.IconCache
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("StaticFieldLeak")
object Graph {

    lateinit var context: Context
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var worker: Handler
        private set
    val main = Handler(Looper.getMainLooper())

    val engine = SearchEngine()
    val grammar = PrefixGrammar()
    val calculator = Calculator()

    val aliases by lazy { AliasStore(context) }
    val snippets by lazy { SnippetStore(context) }
    val quicklinks by lazy { QuicklinkStore(context) }
    val clipboard by lazy { ClipboardStore(context, prefs) }
    val files by lazy { FileIndex(context, prefs) }
    val icons by lazy { IconCache(context, prefs) }

    val apps by lazy { AppSource(context) }
    val contacts by lazy { ContactSource(context, prefs) }
    val settings by lazy { SettingSource(context, prefs) }
    val snippetSource by lazy { SnippetSource(snippets) }
    val quicklinkSource by lazy { QuicklinkSource(quicklinks) }

    private val listeners = ArrayList<() -> Unit>()
    private val rebuildQueued = AtomicBoolean(false)

    fun init(appContext: Context) {
        context = appContext
        prefs = Prefs(appContext)
        val thread = HandlerThread("tacit-worker", android.os.Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        worker = Handler(thread.looper)
        applyPrefs()
        worker.post {
            val cached = IndexCache.read(appContext)
            if (cached != null) {
                engine.replaceItems(cached)
                main.post { listeners.forEach { it() } }
            }
            refreshAll()
        }
    }

    fun applyPrefs() {
        engine.setKindPriority(prefs.kinds())
        engine.setStrictness(prefs.strictnessValue())
        IndexOptions.transliterate = prefs.transliterate
        grammar.calculator = prefs.prefixCalculator
        grammar.clipboard = prefs.prefixClipboard
        grammar.settings = prefs.prefixSettings
        grammar.contacts = prefs.prefixContacts
        grammar.files = prefs.prefixFiles
        grammar.snippets = prefs.prefixSnippets
        grammar.engines = prefs.engines()
        calculator.precision = prefs.precision.coerceIn(8, 64)
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun refreshAll() {
        apps.reload()
        if (prefs.contactsEnabled) contacts.reload() else contacts.clear()
        settings.reload(apps.items())
        snippetSource.reload()
        quicklinkSource.reload()
        rebuild()
    }

    @Volatile
    private var fileAliasEntries: List<AliasEntry> = emptyList()

    @Volatile
    private var fileAliasVersion = -1

    fun aliasedFiles(): List<AliasEntry> {
        val current = aliases.version
        if (current != fileAliasVersion) {
            fileAliasEntries = aliases.all().filterKeys { it.startsWith("file:") }
                .map { AliasEntry(it.key, it.value.map(AliasRules::key)) }
            fileAliasVersion = current
        }
        return fileAliasEntries
    }

    fun reloadQuicklinks() {
        worker.post {
            quicklinkSource.reload()
            rebuild()
        }
    }

    fun requestRebuild() {
        if (rebuildQueued.compareAndSet(false, true)) {
            worker.post {
                rebuildQueued.set(false)
                rebuild()
            }
        }
    }

    fun requestRefresh() {
        worker.post { refreshAll() }
    }

    @Volatile
    private var overrideItems: List<SearchItem>? = null

    fun useItemsForTesting(items: List<SearchItem>?) {
        overrideItems = items
        if (items != null) {
            engine.replaceItems(items)
            main.post { listeners.forEach { it() } }
        } else {
            requestRefresh()
        }
    }

    private fun rebuild() {
        overrideItems?.let {
            engine.replaceItems(it)
            main.post { listeners.forEach { listener -> listener() } }
            return
        }
        val aliasMap = aliases.all()
        val pins = prefs.pins
        val hidden = prefs.hidden
        val combined = ArrayList<SearchItem>(4096)
        val sourceItems = apps.items() + contacts.items() + settings.items() + snippetSource.items() + quicklinkSource.items()
        for (item in sourceItems) {
            if (item.key in hidden) continue
            item.withAliases(aliasMap[item.key].orEmpty())
            item.pinned = item.key in pins || (prefs.starredAsPins && contacts.isStarred(item.key))
            combined.add(item)
        }
        engine.replaceItems(combined)
        IndexCache.write(context, combined)
        main.post { listeners.forEach { it() } }
    }
}
