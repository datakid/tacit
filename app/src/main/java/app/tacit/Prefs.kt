package app.tacit

import android.content.Context
import android.content.SharedPreferences
import app.tacit.core.ItemKind
import app.tacit.core.Strictness
import app.tacit.core.WebEngine
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Prefs(context: Context) {

    val store: SharedPreferences = context.getSharedPreferences("tacit", Context.MODE_PRIVATE)

    var theme by text("theme", "system")
    var iconMode by text("icon_mode", "mono")
    var density by text("density", "comfortable")
    var drawerEnabled by flag("drawer", false)
    var drawerSettings by flag("drawer_settings", false)
    var showWallpaper by flag("wallpaper", false)
    var barAtBottom by flag("bar_bottom", true)
    var showClock by flag("clock", false)
    var showDate by flag("date", false)
    var showBattery by flag("battery", false)
    var resultCount by number("result_count", 8)
    var kindOrder by text("kind_order", "APP,CONTACT,SETTING,SNIPPET,ACTION,FILE,CALCULATOR,WEB,CLIPBOARD")
    var contactsEnabled by flag("contacts", false)
    var filesEnabled by flag("files", false)
    var filesHidden by flag("files_hidden", false)
    var clipboardEnabled by flag("clipboard", false)
    var strictness by text("strictness", Strictness.NORMAL.name)
    var transliterate by flag("transliterate", true)
    var visiblePasswordInput by flag("visible_password_input", false)
    var defaultEngine by text("default_engine", "ddg")
    var customEngines by text("engines", "")
    var countryCode by text("country_code", "")
    var directCall by flag("direct_call", false)
    var clipRetentionDays by number("clip_days", 0)
    var clipMaxItems by number("clip_max", 0)
    var sensitivePolicy by text("sensitive_policy", "skip")
    var clipBiometric by flag("clip_biometric", false)
    var clipLastTimestamp by long("clip_last_ts", 0L)
    var starredAsPins by flag("starred_pins", false)
    var closeAfterAction by flag("close_after", true)
    var excludeFromRecents by flag("exclude_recents", true)
    var animations by flag("animations", false)
    var precision by number("precision", 20)
    var prefixCalculator by text("p_calc", "=")
    var prefixClipboard by text("p_clip", "cb ")
    var prefixSettings by text("p_settings", ">")
    var prefixContacts by text("p_contacts", "@")
    var prefixFiles by text("p_files", "/")
    var prefixSnippets by text("p_snippets", ";")
    var settingsSearchFallback by flag("settings_fallback", true)

    var pins: Set<String>
        get() = store.getStringSet("pins", emptySet()) ?: emptySet()
        set(value) = store.edit().putStringSet("pins", HashSet(value)).apply()

    var hidden: Set<String>
        get() = store.getStringSet("hidden", emptySet()) ?: emptySet()
        set(value) = store.edit().putStringSet("hidden", HashSet(value)).apply()

    fun kinds(): List<ItemKind> = kindOrder.split(',').mapNotNull { name -> ItemKind.entries.firstOrNull { it.name == name.trim() } }

    fun strictnessValue(): Strictness = Strictness.entries.firstOrNull { it.name == strictness } ?: Strictness.NORMAL

    fun engines(): List<WebEngine> {
        val custom = customEngines.lines().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size == 3 && parts[2].contains("%s")) WebEngine(parts[0].trim(), parts[1].trim(), parts[2].trim()) else null
        }
        return if (custom.isEmpty()) app.tacit.core.PrefixGrammar.defaultEngines else custom
    }

    private fun flag(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = store.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            store.edit().putBoolean(key, value).apply()
        }
    }

    private fun text(key: String, default: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = store.getString(key, default) ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            store.edit().putString(key, value).apply()
        }
    }

    private fun number(key: String, default: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = store.getInt(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            store.edit().putInt(key, value).apply()
        }
    }

    private fun long(key: String, default: Long) = object : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Long = store.getLong(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            store.edit().putLong(key, value).apply()
        }
    }
}
