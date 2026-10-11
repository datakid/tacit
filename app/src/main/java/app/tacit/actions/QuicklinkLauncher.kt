package app.tacit.actions

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import app.tacit.Graph
import app.tacit.core.ItemKind
import app.tacit.core.QuicklinkRules
import app.tacit.core.SearchItem
import app.tacit.core.QuicklinkType

object QuicklinkLauncher {

    fun launch(activity: Activity, spec: String, bounds: Rect?): Boolean {
        val id = spec.substringBefore('|')
        val query = if (spec.contains('|')) spec.substringAfter('|') else ""
        val link = Graph.quicklinks.get(id) ?: return false
        return when (link.type) {
            QuicklinkType.URL -> {
                val url = QuicklinkRules.normalizeUrl(QuicklinkRules.fill(link.target, query)) ?: return false
                Actions.start(activity, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            QuicklinkType.SETTING -> Actions.openSetting(activity, SearchItem("ql:setting", ItemKind.SETTING, link.name, payload = link.target))
            QuicklinkType.APP -> runCatching { Graph.apps.launch(link.target, bounds) }.isSuccess
        }
    }
}
