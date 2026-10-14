package app.tacit.actions

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

class TacitFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun fileOf(uri: Uri): File {
        val path = uri.path ?: throw SecurityException("No path")
        val file = File(path).canonicalFile
        val allowed = file.absolutePath.startsWith("/storage/") || file.absolutePath.startsWith("/sdcard/") ||
            file.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath)
        if (!allowed) throw SecurityException("Outside shared storage")
        return file
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read only")
        return ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = fileOf(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns, 1)
        if (!file.exists()) return cursor
        cursor.addRow(columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        })
        return cursor
    }

    override fun getType(uri: Uri): String = Actions.mimeOf(uri.lastPathSegment.orEmpty())

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        fun uriFor(context: Context, file: File): Uri =
            Uri.Builder().scheme("content").authority("${context.packageName}.files").path(file.absolutePath).build()
    }
}
