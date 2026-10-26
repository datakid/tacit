package app.tacit.files

import app.tacit.core.CharMask

class FileHit(val path: String, val name: String, val size: Long, val modified: Long, val score: Int, val folder: String = "")

object PathDecoder {
    fun decode(value: String): String = runCatching { java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)
}

object NameMasks {

    fun bit(c: Char): Long = CharMask.bit(c)

    fun of(text: String): Long = CharMask.of(text)

    fun of(normalized: Array<String>): LongArray = LongArray(normalized.size) { of(normalized[it]) }
}

class FileTable(
    val directories: Array<String>,
    val names: Array<String>,
    val normalized: Array<String>,
    val parent: IntArray,
    val sizes: LongArray,
    val modified: LongArray,
    val masks: LongArray
) {
    constructor(
        directories: Array<String>,
        names: Array<String>,
        normalized: Array<String>,
        parent: IntArray,
        sizes: LongArray,
        modified: LongArray
    ) : this(directories, names, normalized, parent, sizes, modified, NameMasks.of(normalized))

    val count: Int get() = names.size

    val searchableDirectories: Array<String> by lazy {
        Array(directories.size) { index ->
            val dir = directories[index]
            (if (dir.contains('%')) PathDecoder.decode(dir) else dir).lowercase()
        }
    }

    fun pathOf(index: Int): String {
        val name = names[index]
        TreeNames.documentUriString(name)?.let { return it }
        return directories[parent[index]] + "/" + name
    }

    fun displayNameOf(index: Int): String = TreeNames.displayName(names[index])

    fun folderOf(index: Int): String {
        val dir = directories[parent[index]]
        if (!dir.startsWith(TreeNames.PREFIX)) return dir
        val encodedId = dir.substringAfterLast("/document/", dir.substringAfterLast("/tree/", ""))
        val id = PathDecoder.decode(encodedId)
        return "~/" + id.substringAfter(':', id)
    }

    companion object {
        val EMPTY = FileTable(emptyArray(), emptyArray(), emptyArray(), IntArray(0), LongArray(0), LongArray(0))
    }
}
