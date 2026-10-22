package app.tacit.files

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

object GrantedTrees {

    private const val KEY = "granted_trees"

    fun list(context: Context): List<Uri> {
        val persisted = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
        val stored = context.getSharedPreferences("tacit", Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
        return stored.map(Uri::parse).filter { it in persisted }
    }

    fun add(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val prefs = context.getSharedPreferences("tacit", Context.MODE_PRIVATE)
        val set = HashSet(prefs.getStringSet(KEY, emptySet()).orEmpty())
        set.add(uri.toString())
        prefs.edit().putStringSet(KEY, set).apply()
    }

    fun remove(context: Context, uri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val prefs = context.getSharedPreferences("tacit", Context.MODE_PRIVATE)
        val set = HashSet(prefs.getStringSet(KEY, emptySet()).orEmpty())
        set.remove(uri.toString())
        prefs.edit().putStringSet(KEY, set).apply()
    }

    fun label(uri: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrDefault(uri.toString())
        val path = id.substringAfter(':', id)
        return if (path.isEmpty()) id.substringBefore(':') else path
    }
}

class TreeScanner(
    private val resolver: ContentResolver,
    private val includeHidden: Boolean,
    private val sink: (name: String, parentKey: String, size: Long, modified: Long) -> Boolean
) {

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    fun scan(tree: Uri) {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return
        val stack = ArrayDeque<String>()
        stack.addLast(rootId)
        while (stack.isNotEmpty()) {
            val documentId = stack.removeLast()
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
            val parentKey = TREE_PREFIX + parentUri.toString()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            val cursor = runCatching { resolver.query(children, projection, null, null, null) }.getOrNull() ?: continue
            cursor.use {
                while (it.moveToNext()) {
                    val childId = it.getString(0) ?: continue
                    val name = it.getString(1) ?: continue
                    if (!includeHidden && name.startsWith('.')) continue
                    val isDir = it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (it.isNull(3)) 0L else it.getLong(3)
                    val modified = if (it.isNull(4)) 0L else it.getLong(4)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(tree, childId).toString()
                    if (isDir) {
                        stack.addLast(childId)
                        if (!sink(TreeNames.stored("$name/", childUri), parentKey, 0L, modified)) return
                    } else {
                        if (!sink(TreeNames.stored(name, childUri), parentKey, size, modified)) return
                    }
                }
            }
        }
    }

    companion object {
        const val TREE_PREFIX = TreeNames.PREFIX
        const val TREE_SEPARATOR = TreeNames.SEPARATOR

        fun displayName(stored: String): String = TreeNames.displayName(stored)

        fun documentUriString(stored: String): String? = TreeNames.documentUriString(stored)

        fun documentUri(stored: String): Uri? = documentUriString(stored)?.let { Uri.parse(it) }

        fun normalizedName(stored: String): String = TreeNames.normalizedName(stored)
    }
}

class TreeStrategy(
    private val resolver: ContentResolver,
    private val trees: List<Uri>,
    private val includeHidden: Boolean
) : ScanStrategy {

    override val id: Int = ScanStrategyIds.TREE

    override fun scan(sink: EntrySink) {
        for (tree in trees) {
            if (!sink.accepting) return
            var more = true
            TreeScanner(resolver, includeHidden) { name, parentKey, size, modified ->
                more = sink.add(name, sink.directory(parentKey), size, modified)
                more
            }.scan(tree)
            if (!more) return
        }
    }
}
