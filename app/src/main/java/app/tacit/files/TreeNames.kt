package app.tacit.files

import app.tacit.core.Normalizer

object TreeNames {
    const val PREFIX = "tree:"
    const val SEPARATOR = "\u0000"

    fun displayName(stored: String): String = stored.substringBefore(SEPARATOR)

    fun documentUriString(stored: String): String? =
        if (stored.contains(SEPARATOR)) stored.substringAfter(SEPARATOR) else null

    fun normalizedName(stored: String): String = Normalizer.normalize(displayName(stored))

    fun stored(display: String, documentUri: String): String = display + SEPARATOR + documentUri
}
