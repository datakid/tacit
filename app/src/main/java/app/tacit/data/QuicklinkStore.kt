package app.tacit.data

import android.content.Context
import java.io.File
import java.util.UUID

class QuicklinkStore(context: Context) {

    private val file = File(context.filesDir, "quicklinks.csv")
    private val lock = Any()
    private var cache: MutableList<Quicklink>? = null

    fun all(): List<Quicklink> = synchronized(lock) { ArrayList(load()) }

    fun get(id: String): Quicklink? = synchronized(lock) { load().firstOrNull { it.id == id } }

    fun upsert(link: Quicklink) = synchronized(lock) {
        val list = load()
        val index = list.indexOfFirst { it.id == link.id }
        if (index >= 0) list[index] = link else list.add(link)
        save(list)
    }

    fun replaceAll(links: List<Quicklink>) = synchronized(lock) {
        val list = load()
        list.clear()
        list.addAll(links)
        save(list)
    }

    fun delete(id: String) = synchronized(lock) {
        val list = load()
        list.removeAll { it.id == id }
        save(list)
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    private fun load(): MutableList<Quicklink> {
        cache?.let { return it }
        val loaded = if (file.exists()) QuicklinkCsv.decode(file.readText()) else ArrayList()
        cache = loaded
        return loaded
    }

    private fun save(list: List<Quicklink>) {
        val temp = File(file.parentFile, "quicklinks.csv.tmp")
        temp.writeText(QuicklinkCsv.encode(list))
        temp.renameTo(file)
    }
}
