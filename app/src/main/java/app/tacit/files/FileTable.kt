package app.tacit.files

class FileHit(val path: String, val name: String, val size: Long, val modified: Long, val score: Int, val folder: String = "")

object PathDecoder {
    fun decode(value: String): String = runCatching { java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)
}

object NameMasks {

    fun bit(c: Char): Long = when (c) {
        in 'a'..'z' -> 1L shl (c - 'a')
        in '0'..'9' -> 1L shl (26 + (c - '0'))
        else -> 1L shl (36 + c.code % 28)
    }

    fun of(text: String): Long {
        var mask = 0L
        for (c in text) mask = mask or bit(c)
        return mask
    }

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
