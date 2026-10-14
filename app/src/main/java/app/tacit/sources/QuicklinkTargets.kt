package app.tacit.sources

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.tacit.Graph
import app.tacit.core.QuicklinkType
import app.tacit.core.TargetState
import app.tacit.data.Quicklink
import java.io.File

object QuicklinkTargets {

    fun state(link: Quicklink): TargetState = when (link.type) {
        QuicklinkType.URL, QuicklinkType.CONTACT -> TargetState.OK
        QuicklinkType.SETTING -> if (Graph.settings.items().any { it.payload == link.target }) TargetState.OK else TargetState.MISSING
        QuicklinkType.APP -> if (Graph.apps.hasApp(link.target)) TargetState.OK else TargetState.MISSING
        QuicklinkType.APP_SHORTCUT -> if (Graph.apps.hasShortcut(link.target) == false) TargetState.MISSING else TargetState.OK
        QuicklinkType.FILE, QuicklinkType.FOLDER -> documentState(Graph.context, link)
    }

    fun documentState(context: Context, link: Quicklink): TargetState {
        val target = link.target
        if (target.isEmpty()) return TargetState.REPICK
        if (target.startsWith("content://")) return if (grantHeld(context, Uri.parse(target))) TargetState.OK else TargetState.MISSING
        val file = File(target)
        val present = if (link.type == QuicklinkType.FOLDER) file.isDirectory else file.exists()
        return if (present) TargetState.OK else TargetState.MISSING
    }

    fun keep(context: Context, uri: Uri): Boolean {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        return grantHeld(context, uri)
    }

    fun displayName(context: Context, uri: Uri): String {
        val document = if (DocumentsContract.isTreeUri(uri)) {
            runCatching { DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)) }.getOrNull() ?: uri
        } else uri
        val name = runCatching {
            context.contentResolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast('/').substringAfterLast(':')
    }

    fun grantHeld(context: Context, uri: Uri): Boolean {
        if (uri.authority == context.packageName + ".files") return true
        val text = uri.toString()
        return context.contentResolver.persistedUriPermissions.any {
            it.isReadPermission && (it.uri == uri || text.startsWith(it.uri.toString() + "/"))
        }
    }

    fun existsNow(context: Context, link: Quicklink): Boolean {
        val target = link.target
        if (!target.startsWith("content://")) return documentState(context, link) == TargetState.OK
        val uri = Uri.parse(target)
        val document = if (link.type == QuicklinkType.FOLDER && DocumentsContract.isTreeUri(uri)) {
            runCatching { DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)) }.getOrNull() ?: return false
        } else uri
        return runCatching {
            context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
    }
}
