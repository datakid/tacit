package app.tacit.files

import app.tacit.core.Normalizer

class FileQuery(val terms: List<String>, val extension: String?, val folder: String?)

object FileMatcher {

    fun parse(raw: String): FileQuery {
        var extension: String? = null
        var folder: String? = null
        val terms = ArrayList<String>()
        for (token in Normalizer.normalize(raw).split(' ').filter { it.isNotEmpty() }) {
            when {
                token.startsWith("ext:") -> extension = token.removePrefix("ext:").removePrefix(".").ifEmpty { null }
                token.startsWith("in:") -> folder = token.removePrefix("in:").ifEmpty { null }
                token.startsWith("*.") && token.length > 2 -> extension = token.substring(2)
                else -> terms.add(token)
            }
        }
        return FileQuery(terms, extension, folder)
    }

    fun search(table: FileTable, raw: String, limit: Int, now: Long = System.currentTimeMillis(), cancelled: () -> Boolean): List<FileHit> {
        val query = parse(raw)
        if (query.terms.isEmpty() && query.extension == null) return emptyList()
        val implicitExtension = if (query.extension == null && query.terms.size > 1) query.terms.last().takeIf { it in COMMON_EXTENSIONS } else null
        val terms = (if (implicitExtension != null) query.terms.dropLast(1) else query.terms).toTypedArray()
        val extension = query.extension ?: implicitExtension
        val suffix = if (extension != null) ".$extension" else null
        val allowedDirectories = query.folder?.let { folder ->
            val searchable = table.searchableDirectories
            BooleanArray(searchable.size) { searchable[it].contains(folder) }
        }
        var queryMask = 0L
        for (term in terms) queryMask = queryMask or NameMasks.of(term)
        val masks = table.masks
        val normalized = table.normalized
        val parent = table.parent
        val modified = table.modified
        val count = table.count
        val bestIndex = IntArray(limit) { -1 }
        val bestScore = IntArray(limit)
        var filled = 0
        for (i in 0 until count) {
            if ((i and 4095) == 0 && cancelled()) return emptyList()
            if ((masks[i] and queryMask) != queryMask) continue
            val name = normalized[i]
            if (suffix != null && !name.endsWith(suffix)) continue
            if (allowedDirectories != null && !allowedDirectories[parent[i]]) continue
            val score = scoreName(name, terms)
            if (score <= 0) continue
            val adjusted = score + recency(modified[i], now)
            if (filled < limit) {
                bestIndex[filled] = i
                bestScore[filled] = adjusted
                filled++
            } else {
                var min = 0
                for (k in 1 until limit) if (bestScore[k] < bestScore[min]) min = k
                if (adjusted > bestScore[min]) {
                    bestIndex[min] = i
                    bestScore[min] = adjusted
                }
            }
        }
        return (0 until filled)
            .sortedByDescending { bestScore[it] }
            .map { k ->
                val i = bestIndex[k]
                FileHit(table.pathOf(i).removeSuffix("/"), table.displayNameOf(i).removeSuffix("/"), table.sizes[i], table.modified[i],
                    bestScore[k], table.folderOf(i))
            }
    }

    private fun scoreName(name: String, terms: Array<String>): Int {
        if (terms.isEmpty()) return 1
        var total = 0
        for (term in terms) {
            val index = name.indexOf(term)
            if (index < 0) return 0
            val atStart = index == 0 || !name[index - 1].isLetterOrDigit()
            total += if (atStart) 100 else 40
        }
        if (terms.size == 1) {
            val dot = name.lastIndexOf('.')
            val baseLength = if (dot < 0) name.length else dot
            val only = terms[0]
            if (only.length == baseLength && name.regionMatches(0, only, 0, baseLength)) total += 200
        }
        return total - name.length.coerceAtMost(80) / 4
    }

    private fun recency(modified: Long, now: Long): Int {
        val ageDays = (now - modified) / 86_400_000L
        return when {
            ageDays < 1 -> 20
            ageDays < 7 -> 12
            ageDays < 30 -> 6
            else -> 0
        }
    }

    private val COMMON_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv",
        "jpg", "jpeg", "png", "gif", "webp", "heic", "mp4", "mkv", "mp3", "m4a", "flac", "zip", "apk", "epub", "json")
}
