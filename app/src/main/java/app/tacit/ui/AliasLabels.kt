package app.tacit.ui

import app.tacit.Graph
import app.tacit.core.PathNames

object AliasLabels {

    fun of(key: String): String {
        if (key.startsWith("file:")) return PathNames.name(key.removePrefix("file:"))
        return Graph.engine.all().firstOrNull { it.key == key }?.label ?: key
    }
}
