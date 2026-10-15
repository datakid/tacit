package app.tacit.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.widget.Toast
import app.tacit.Graph
import app.tacit.R
import app.tacit.core.ContactFormats
import app.tacit.core.ExportContact
import app.tacit.core.ExportCount
import app.tacit.core.ExportKind
import app.tacit.core.ExportNumber
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter

object ContactExport {

    private const val BUFFER = 64 * 1024

    @Volatile
    var reader: (Context) -> Sequence<ExportContact> = ::readContacts

    fun run(context: Context, uri: Uri, kind: ExportKind) {
        val app = context.applicationContext
        toast(app, app.getString(R.string.export_started))
        Graph.worker.post {
            val message = try {
                val count = write(app, uri, kind)
                app.getString(R.string.export_done, count.contacts, count.numbers)
            } catch (error: Exception) {
                app.getString(R.string.export_failed)
            }
            toast(app, message)
        }
    }

    fun write(context: Context, uri: Uri, kind: ExportKind): ExportCount {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
        BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8), BUFFER).use { writer ->
            return ContactFormats.write(kind, reader(context), writer)
        }
    }

    private fun toast(context: Context, message: String) {
        Graph.main.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    private fun vcardType(type: Int): String? = when (type) {
        Phone.TYPE_MOBILE -> "CELL"
        Phone.TYPE_HOME -> "HOME"
        Phone.TYPE_WORK -> "WORK"
        else -> null
    }

    fun readContacts(context: Context): Sequence<ExportContact> = sequence {
        val projection = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL)
        val order = "${ContactsContract.Contacts.SORT_KEY_PRIMARY} ASC, ${Phone.CONTACT_ID} ASC"
        val cursor = context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, order)
        if (cursor != null) {
            cursor.use {
                var currentId = -1L
                var currentName = ""
                var numbers = ArrayList<ExportNumber>()
                val seen = HashSet<String>()
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    if (id != currentId) {
                        if (currentId != -1L && numbers.isNotEmpty()) yield(ExportContact(currentName, numbers))
                        currentId = id
                        currentName = it.getString(1).orEmpty()
                        numbers = ArrayList()
                        seen.clear()
                    }
                    val number = ContactFormats.normalizeNumber(it.getString(2).orEmpty())
                    if (number.isEmpty() || !seen.add(number)) continue
                    val type = it.getInt(3)
                    val label = Phone.getTypeLabel(context.resources, type, it.getString(4)).toString()
                    numbers.add(ExportNumber(number, label, vcardType(type)))
                }
                if (currentId != -1L && numbers.isNotEmpty()) yield(ExportContact(currentName, numbers))
            }
        }
    }
}
