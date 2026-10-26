package app.tacit.data

import app.tacit.core.QuicklinkIcon
import app.tacit.core.QuicklinkType

data class Quicklink(
    val id: String,
    val name: String,
    val type: QuicklinkType,
    val target: String,
    val detail: String,
    val icon: String = QuicklinkIcon.GLYPH
)

object QuicklinkCsv {

    fun decode(text: String): MutableList<Quicklink> {
        val loaded = ArrayList<Quicklink>()
        for (row in Csv.decode(text)) {
            if (row.size < 5) continue
            val type = runCatching { QuicklinkType.valueOf(row[2]) }.getOrNull() ?: continue
            val icon = QuicklinkIcon.normalize(type, row.getOrElse(5) { QuicklinkIcon.GLYPH })
            loaded.add(Quicklink(row[0], row[1], type, row[3], row[4], icon))
        }
        return loaded
    }

    fun encode(list: List<Quicklink>): String =
        Csv.encode(list.map { listOf(it.id, it.name, it.type.name, it.target, it.detail, it.icon) })
}
