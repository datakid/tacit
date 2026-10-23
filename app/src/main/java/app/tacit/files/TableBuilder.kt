package app.tacit.files

interface EntrySink {
    val accepting: Boolean
    fun directory(path: String): Int
    fun add(name: String, parent: Int, size: Long, modified: Long): Boolean
}

interface ScanStrategy {
    val id: Int
    fun scan(sink: EntrySink)
}

object ScanStrategyIds {
    const val FILE_WALK = 1
    const val NIO_WALK = 2
    const val MEDIA_STORE = 3
    const val HYBRID = 4
    const val TREE = 5
}

class TableBuilder(
    private val limit: Int = MAX_FILES,
    private val current: () -> Boolean = { true },
    private val onProgress: ((TableBuilder) -> Unit)? = null
) : EntrySink {

    private val directories = ArrayList<String>()
    private val directoryIds = HashMap<String, Int>()
    private var names = arrayOfNulls<String>(INITIAL)
    private var normalized = arrayOfNulls<String>(INITIAL)
    private var parents = IntArray(INITIAL)
    private var sizes = LongArray(INITIAL)
    private var modified = LongArray(INITIAL)
    private var masks = LongArray(INITIAL)

    var count = 0
        private set

    var truncated = false
        private set

    override val accepting: Boolean get() = !truncated && current()

    override fun directory(path: String): Int = directoryIds.getOrPut(path) {
        directories.add(path)
        directories.size - 1
    }

    override fun add(name: String, parent: Int, size: Long, modified: Long): Boolean {
        if (count == names.size) grow()
        val key = TreeNames.normalizedName(name)
        names[count] = name
        normalized[count] = key
        parents[count] = parent
        sizes[count] = size
        this.modified[count] = modified
        masks[count] = NameMasks.of(key)
        count++
        if (count >= limit) {
            truncated = true
            return false
        }
        if ((count and CHECK_MASK) == 0) {
            if (!current()) return false
            onProgress?.invoke(this)
        }
        return true
    }

    fun snapshot(): FileTable = FileTable(
        directories.toTypedArray(),
        names.copyOf(count).requireNoNulls(),
        normalized.copyOf(count).requireNoNulls(),
        parents.copyOf(count),
        sizes.copyOf(count),
        modified.copyOf(count),
        masks.copyOf(count)
    )

    private fun grow() {
        val size = names.size * 2
        names = names.copyOf(size)
        normalized = normalized.copyOf(size)
        parents = parents.copyOf(size)
        sizes = sizes.copyOf(size)
        modified = modified.copyOf(size)
        masks = masks.copyOf(size)
    }

    companion object {
        const val MAX_FILES = 500_000
        private const val INITIAL = 1024
        private const val CHECK_MASK = 1023
    }
}
