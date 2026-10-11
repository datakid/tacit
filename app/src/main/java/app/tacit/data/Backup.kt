package app.tacit.data

import app.tacit.Graph
import app.tacit.core.QuicklinkType
import org.json.JSONArray
import org.json.JSONObject

object Backup {

    private const val VERSION = 1

    fun export(): String {
        val root = JSONObject()
        root.put("tacit_backup", VERSION)
        val prefs = JSONObject()
        for ((key, value) in Graph.prefs.store.all) {
            when (value) {
                is Set<*> -> prefs.put(key, JSONArray(value.map { it.toString() }))
                else -> prefs.put(key, value)
            }
        }
        root.put("prefs", prefs)
        val aliases = JSONObject()
        for ((key, list) in Graph.aliases.all()) aliases.put(key, JSONArray(list))
        root.put("aliases", aliases)
        val snippets = JSONArray()
        for (snippet in Graph.snippets.all()) {
            snippets.put(JSONObject().put("id", snippet.id).put("trigger", snippet.trigger).put("title", snippet.title).put("body", snippet.body))
        }
        root.put("snippets", snippets)
        val quicklinks = JSONArray()
        for (link in Graph.quicklinks.all()) {
            quicklinks.put(JSONObject().put("id", link.id).put("name", link.name).put("type", link.type.name)
                .put("target", link.target).put("detail", link.detail))
        }
        root.put("quicklinks", quicklinks)
        return root.toString(2)
    }

    fun import(text: String) {
        val root = JSONObject(text)
        if (!root.has("tacit_backup")) return
        root.optJSONObject("prefs")?.let { prefs ->
            val editor = Graph.prefs.store.edit()
            for (key in prefs.keys()) {
                if (key == "clip_last_ts" || key == "granted_trees") continue
                when (val value = prefs.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is JSONArray -> editor.putStringSet(key, (0 until value.length()).map { value.getString(it) }.toSet())
                    is Double -> editor.putInt(key, value.toInt())
                }
            }
            editor.apply()
        }
        root.optJSONObject("aliases")?.let { aliases ->
            val map = HashMap<String, List<String>>()
            for (key in aliases.keys()) {
                val array = aliases.getJSONArray(key)
                map[key] = (0 until array.length()).map { array.getString(it) }
            }
            Graph.aliases.setMany(map)
        }
        root.optJSONArray("snippets")?.let { array ->
            val list = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Snippet(item.optString("id").ifEmpty { Graph.snippets.newId() }, item.optString("trigger"), item.optString("title"), item.optString("body"))
            }
            Graph.snippets.replaceAll(list)
        }
        root.optJSONArray("quicklinks")?.let { array ->
            val list = (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val type = runCatching { QuicklinkType.valueOf(item.optString("type")) }.getOrNull() ?: return@mapNotNull null
                Quicklink(item.optString("id").ifEmpty { Graph.quicklinks.newId() }, item.optString("name"), type,
                    item.optString("target"), item.optString("detail"))
            }
            Graph.quicklinks.replaceAll(list)
        }
    }
}
