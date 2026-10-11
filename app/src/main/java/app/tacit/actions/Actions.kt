package app.tacit.actions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import app.tacit.Graph
import app.tacit.TacitApp
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.core.WebEngine
import app.tacit.data.SnippetExpander
import app.tacit.sources.ContactInfo
import java.io.File
import java.time.LocalDateTime

object Actions {

    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val SIGNAL = "org.thoughtcrime.securesms"
    const val TELEGRAM = "org.telegram.messenger"

    private var torchOn = false

    fun primary(activity: Activity, item: SearchItem, bounds: Rect?): Boolean = when (item.kind) {
        ItemKind.APP -> safe(activity) { Graph.apps.launch(item.payload, bounds) }
        ItemKind.ACTION -> handleAction(activity, item, bounds)
        ItemKind.CONTACT -> openContact(activity, item)
        ItemKind.SETTING -> openSetting(activity, item)
        ItemKind.FILE -> openFile(activity, item.payload)
        ItemKind.SNIPPET -> copySnippet(item.payload)
        ItemKind.CLIPBOARD -> {
            Graph.clipboard.copy(item.payload, item.subtitle == "hidden")
            true
        }
        ItemKind.CALCULATOR -> {
            Graph.clipboard.copy(item.payload, false)
            true
        }
        ItemKind.WEB -> start(activity, Intent(Intent.ACTION_VIEW, Uri.parse(item.payload)))
    }

    private fun handleAction(activity: Activity, item: SearchItem, bounds: Rect?): Boolean {
        val payload = item.payload
        return when {
            item.key.startsWith("shortcut:") -> safe(activity) { Graph.apps.launchShortcut(payload, bounds) }
            payload.startsWith("dial|") -> dial(activity, payload.removePrefix("dial|"))
            payload.startsWith("sms|") -> sms(activity, payload.removePrefix("sms|"))
            payload.startsWith("wa|") -> whatsappNumber(activity, payload.removePrefix("wa|"), WHATSAPP)
            payload.startsWith("url|") -> start(activity, Intent(Intent.ACTION_VIEW, Uri.parse(payload.removePrefix("url|"))))
            payload.startsWith("mail|") -> start(activity, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + payload.removePrefix("mail|"))))
            payload.startsWith("copy|") -> {
                Graph.clipboard.copy(payload.removePrefix("copy|"), false)
                true
            }
            payload == "crash|share" -> shareCrash(activity)
            payload.startsWith("ql|") -> QuicklinkLauncher.launch(activity, payload.removePrefix("ql|"), bounds)
            payload.startsWith("tacit|") -> start(activity, Intent(activity, Class.forName(payload.removePrefix("tacit|"))))
            else -> false
        }
    }

    private fun shareCrash(activity: Activity): Boolean {
        val text = runCatching { File(activity.filesDir, TacitApp.CRASH_FILE).readText() }.getOrNull() ?: return false
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        return start(activity, Intent.createChooser(send, null))
    }

    fun openContact(activity: Activity, item: SearchItem): Boolean {
        val info = item.extra as? ContactInfo
        val uri = if (info != null) ContactsContract.Contacts.getLookupUri(info.contactId, info.lookupKey)
        else ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, item.payload.toLongOrNull() ?: return false)
        return start(activity, Intent(Intent.ACTION_VIEW, uri))
    }

    fun dial(activity: Activity, number: String): Boolean {
        val action = if (Graph.prefs.directCall &&
            activity.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return start(activity, Intent(action, Uri.fromParts("tel", number, null)))
    }

    fun sms(activity: Activity, number: String): Boolean =
        start(activity, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

    fun whatsappContact(activity: Activity, info: ContactInfo, number: String?, business: Boolean): Boolean {
        val dataId = if (business) info.whatsappBusinessDataId else info.whatsappDataId
        val pkg = if (business) WHATSAPP_BUSINESS else WHATSAPP
        if (dataId != null) {
            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri,
                if (business) "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile" else "vnd.android.cursor.item/vnd.com.whatsapp.profile")
                .setPackage(pkg)
            if (start(activity, intent)) return true
        }
        return whatsappNumber(activity, number ?: return false, pkg)
    }

    fun whatsappNumber(activity: Activity, number: String, pkg: String): Boolean {
        val international = PhoneFormat.international(number, Graph.prefs.countryCode)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$international")).setPackage(pkg)
        return start(activity, intent)
    }

    fun messengerNumber(activity: Activity, number: String, pkg: String): Boolean {
        val international = PhoneFormat.international(number, Graph.prefs.countryCode)
        val uri = when (pkg) {
            SIGNAL -> Uri.parse("sgnl://signal.me/#p/+$international")
            TELEGRAM -> Uri.parse("tg://resolve?phone=$international")
            else -> return false
        }
        return start(activity, Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
    }

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun openSetting(activity: Activity, item: SearchItem): Boolean {
        if (item.payload == "toggle|flashlight") return toggleTorch(activity)
        val intent = Graph.settings.intentFor(item.payload) ?: return false
        return start(activity, intent)
    }

    private fun toggleTorch(context: Context): Boolean {
        val camera = context.getSystemService(CameraManager::class.java) ?: return false
        val id = runCatching {
            camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return false
        return runCatching {
            camera.setTorchMode(id, !torchOn)
            torchOn = !torchOn
        }.isSuccess
    }

    private fun contentUri(activity: Activity, path: String): Uri =
        if (path.startsWith("content://")) Uri.parse(path) else TacitFileProvider.uriFor(activity, File(path))

    private fun nameOf(activity: Activity, path: String): String {
        if (!path.startsWith("content://")) return File(path).name
        return runCatching {
            activity.contentResolver.query(Uri.parse(path), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: path.substringAfterLast("%2F").substringAfterLast('/')
    }

    fun openFile(activity: Activity, path: String): Boolean {
        if (!path.startsWith("content://") && File(path).isDirectory) return openFolder(activity, path)
        val uri = contentUri(activity, path)
        val mime = if (path.startsWith("content://")) activity.contentResolver.getType(uri) ?: mimeOf(nameOf(activity, path))
        else mimeOf(File(path).name)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(activity, Intent.createChooser(intent, null).takeIf { activity.packageManager.resolveActivity(intent, 0) == null } ?: intent)
    }

    fun shareFile(activity: Activity, path: String): Boolean {
        val uri = contentUri(activity, path)
        val intent = Intent(Intent.ACTION_SEND).setType(mimeOf(nameOf(activity, path))).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(activity, Intent.createChooser(intent, null))
    }

    fun openFolder(activity: Activity, path: String): Boolean {
        if (path.startsWith("content://")) {
            val documentUri = Uri.parse(path)
            val parentId = runCatching { DocumentsContract.getDocumentId(documentUri).substringBeforeLast('/') }.getOrNull()
            val treeUri = runCatching { DocumentsContract.getTreeDocumentId(documentUri) }.getOrNull()
            if (parentId != null && treeUri != null) {
                val parent = DocumentsContract.buildDocumentUriUsingTree(documentUri, parentId)
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(parent, DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (start(activity, intent)) return true
            }
            return false
        }
        val target = File(path).let { if (it.isDirectory) it else it.parentFile } ?: return false
        val relative = target.absolutePath.substringAfter("/storage/emulated/0", "").trimStart('/')
        val documentId = "primary:$relative"
        val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (start(activity, intent)) return true
        return start(activity, Intent.createChooser(intent, null))
    }

    fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    private fun copySnippet(body: String): Boolean {
        val clip = Graph.clipboard.all().firstOrNull()?.text
        Graph.clipboard.copy(SnippetExpander.expand(body, LocalDateTime.now(), clip), false)
        return true
    }

    fun web(activity: Activity, engine: WebEngine, query: String): Boolean =
        start(activity, Intent(Intent.ACTION_VIEW, Uri.parse(engine.urlFor(query))))

    fun start(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        false
    } catch (error: SecurityException) {
        false
    }

    private inline fun safe(activity: Activity, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (error: Exception) {
        false
    }
}

object PhoneFormat {
    fun international(number: String, defaultCountryCode: String): String {
        val digits = number.filter { it.isDigit() || it == '+' }
        val code = defaultCountryCode.filter { it.isDigit() }
        return when {
            digits.startsWith("+") -> digits.drop(1)
            digits.startsWith("00") -> digits.drop(2)
            code.isNotEmpty() && digits.startsWith("0") -> code + digits.drop(1)
            code.isNotEmpty() && !digits.startsWith(code) -> code + digits
            else -> digits
        }
    }
}
