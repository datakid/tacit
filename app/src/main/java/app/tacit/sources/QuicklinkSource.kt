package app.tacit.sources

import app.tacit.Graph
import app.tacit.R
import app.tacit.core.ItemKind
import app.tacit.core.QuicklinkNames
import app.tacit.core.QuicklinkRules
import app.tacit.core.QuicklinkType
import app.tacit.core.SearchItem
import app.tacit.core.TargetState
import app.tacit.data.Quicklink
import app.tacit.data.QuicklinkStore

class QuicklinkSource(private val store: QuicklinkStore) {

    @Volatile
    private var links: List<Quicklink> = emptyList()

    @Volatile
    private var cached: List<SearchItem> = emptyList()

    fun items(): List<SearchItem> = cached

    fun reload() {
        val loaded = store.all()
        links = loaded
        cached = loaded.map { link ->
            SearchItem("ql:${link.id}", ItemKind.ACTION, link.name, subtitleOf(link, QuicklinkTargets.state(link)),
                payload = "ql|${link.id}", extra = link.type)
        }
    }

    fun queryRows(text: String): List<SearchItem> {
        val current = links
        if (current.isEmpty()) return emptyList()
        val candidates = current.filter { it.type == QuicklinkType.URL && QuicklinkRules.takesQuery(it.target) }
            .map { QuicklinkNames(it.id, listOf(it.name) + Graph.aliases.get("ql:${it.id}")) }
        if (candidates.isEmpty()) return emptyList()
        val match = QuicklinkRules.match(text, candidates) ?: return emptyList()
        val link = current.firstOrNull { it.id == match.id } ?: return emptyList()
        return listOf(SearchItem("ql:${link.id}:q", ItemKind.ACTION, "${link.name}: ${match.query}", subtitleOf(link, TargetState.OK),
            payload = "ql|${link.id}|${match.query}", extra = link.type))
    }

    private fun subtitleOf(link: Quicklink, state: TargetState): String = when {
        state == TargetState.MISSING -> Graph.context.getString(R.string.ql_missing)
        state == TargetState.REPICK -> Graph.context.getString(R.string.ql_repick)
        link.type == QuicklinkType.URL -> QuicklinkRules.host(link.target)
        else -> link.detail
    }
}
