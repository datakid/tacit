package app.tacit.sources

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.provider.ContactsContract
import app.tacit.Graph
import app.tacit.Prefs
import app.tacit.core.ItemKind
import app.tacit.core.Normalizer
import app.tacit.core.RefreshSources
import app.tacit.core.SearchItem

class ContactInfo(
    val lookupKey: String,
    val contactId: Long,
    val name: String,
    val numbers: List<String>,
    val primary: String?,
    val whatsappDataId: Long?,
    val whatsappBusinessDataId: Long?,
    val starred: Boolean
)

class ContactSource(private val context: Context, private val prefs: Prefs) {

    @Volatile
    private var cached: List<SearchItem> = emptyList()

    @Volatile
    private var starredKeys: Set<String> = emptySet()

    private var observing = false

    private val observer = object : ContentObserver(Graph.worker) {
        override fun onChange(selfChange: Boolean) {
            Graph.requestRefresh(RefreshSources.CONTACTS)
        }
    }

    fun granted(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun items(): List<SearchItem> = cached

    fun isStarred(key: String): Boolean = key in starredKeys

    fun clear() {
        cached = emptyList()
        starredKeys = emptySet()
    }

    fun reload() {
        if (!granted()) {
            clear()
            return
        }
        if (!observing) {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            observing = true
        }
        val contacts = HashMap<Long, MutableContact>()
        val resolver = context.contentResolver
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.STARRED
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(2) ?: continue
                contacts[id] = MutableContact(id, cursor.getString(1) ?: id.toString(), name, cursor.getInt(3) == 1)
            }
        }
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data._ID,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.IS_SUPER_PRIMARY,
                ContactsContract.Data.IS_PRIMARY
            ),
            "${ContactsContract.Data.MIMETYPE} IN (?,?,?,?)",
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
                WHATSAPP_MIME,
                WHATSAPP_BUSINESS_MIME
            ),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contact = contacts[cursor.getLong(0)] ?: continue
                val data = cursor.getString(3) ?: continue
                when (cursor.getString(1)) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val number = Normalizer.digitsOnly(data)
                        if (number.isNotEmpty() && contact.numbers.none { sameNumber(it, number) }) contact.numbers.add(number)
                        if (cursor.getInt(4) == 1 || cursor.getInt(5) == 1) contact.primary = number
                    }
                    ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> contact.nicknames.add(data)
                    WHATSAPP_MIME -> if (contact.whatsapp == null) contact.whatsapp = cursor.getLong(2)
                    WHATSAPP_BUSINESS_MIME -> if (contact.whatsappBusiness == null) contact.whatsappBusiness = cursor.getLong(2)
                }
            }
        }
        val result = ArrayList<SearchItem>(contacts.size)
        val starred = HashSet<String>()
        for (contact in contacts.values) {
            val key = "contact:${contact.lookupKey}"
            if (contact.starred) starred.add(key)
            val info = ContactInfo(contact.lookupKey, contact.id, contact.name, contact.numbers, contact.primary,
                contact.whatsapp, contact.whatsappBusiness, contact.starred)
            val subtitle = contact.primary ?: contact.numbers.firstOrNull().orEmpty()
            result.add(SearchItem(key, ItemKind.CONTACT, contact.name, subtitle,
                contact.nicknames + contact.numbers, contact.id.toString(), info))
        }
        cached = result
        starredKeys = starred
    }

    private fun sameNumber(a: String, b: String): Boolean {
        val tailA = a.takeLast(9)
        val tailB = b.takeLast(9)
        return tailA.length >= 7 && tailA == tailB
    }

    private class MutableContact(val id: Long, val lookupKey: String, val name: String, val starred: Boolean) {
        val numbers = ArrayList<String>()
        val nicknames = ArrayList<String>()
        var primary: String? = null
        var whatsapp: Long? = null
        var whatsappBusiness: Long? = null
    }

    companion object {
        const val WHATSAPP_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
        const val WHATSAPP_BUSINESS_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile"
    }
}
