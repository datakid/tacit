package app.tacit.ui

import android.app.Activity
import app.tacit.Graph
import app.tacit.actions.ActivityCatalog
import app.tacit.core.SearchItem

object ActivityExplorer {

    const val SETTINGS_PACKAGE = "com.android.settings"

    class Pick(val target: String, val label: String, val name: String)

    fun apps(own: String): List<SearchItem> =
        Graph.apps.mainProfileApps().filter { Graph.apps.packageOf(it.payload) != own }.distinctBy { Graph.apps.packageOf(it.payload) }

    fun start(activity: Activity, palette: Palette, title: String, preset: String?, onPick: (Pick) -> Unit) {
        val list = apps(activity.packageName)
        val preselected = preset?.let { pkg -> list.firstOrNull { Graph.apps.packageOf(it.payload) == pkg } }
        if (preselected != null) {
            screens(activity, palette, preselected, onPick)
            return
        }
        ItemPicker.show(activity, palette, title, list, { Graph.apps.packageOf(it.payload) == SETTINGS_PACKAGE }) { app ->
            screens(activity, palette, app, onPick)
        }
    }

    private fun screens(activity: Activity, palette: Palette, app: SearchItem, onPick: (Pick) -> Unit) {
        val pkg = Graph.apps.packageOf(app.payload)
        Graph.worker.post {
            val found = ActivityCatalog.load(activity, pkg)
            Graph.main.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                ItemPicker.show(activity, palette, app.label, found, { it.subtitle.isNotEmpty() }) { screen ->
                    onPick(Pick(screen.payload, "${app.label} · ${screen.label}", screen.label))
                }
            }
        }
    }
}
