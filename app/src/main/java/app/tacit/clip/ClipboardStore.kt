package app.tacit.clip

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.tacit.Prefs
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ClipEntry(
    val id: Long,
    val text: String,
    val time: Long,
    val sensitivity: Sensitivity,
    val expiresAt: Long
) {
    val hidden: Boolean get() = sensitivity != Sensitivity.NONE
}

class ClipSnapshot(val stamp: Long, val osSensitive: Boolean, val text: () -> String?)

fun interface ClipReader {
    fun read(): ClipSnapshot?
}

class ClipboardStore(private val context: Context, private val prefs: Prefs) {

    private val SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"

    private val file = File(context.filesDir, "clips.bin")
    private val lock = Any()
    private var entries: MutableList<ClipEntry>? = null
    private val manager get() = context.getSystemService(ClipboardManager::class.java)

    fun all(): List<ClipEntry> = synchronized(lock) {
        val list = load()
        if (prune(list)) save(list)
        ArrayList(list)
    }

    private val platformReader = ClipReader { platformRead() }

    fun captureIfNew(reader: ClipReader = platformReader): Boolean {
        if (!prefs.clipboardEnabled) return false
        val snapshot = reader.read() ?: return false
        val stamp = snapshot.stamp
        if (stamp != 0L && stamp == prefs.clipLastTimestamp) return false
        prefs.clipLastTimestamp = stamp
        val text = snapshot.text()?.trim().orEmpty()
        if (text.isEmpty() || text.length > 100_000) return false
        return add(text, snapshot.osSensitive)
    }

    private fun platformRead(): ClipSnapshot? {
        val clipboard = manager ?: return null
        if (!clipboard.hasPrimaryClip()) return null
        val description = clipboard.primaryClipDescription ?: return null
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return null
        return ClipSnapshot(description.timestamp, osSensitive(description)) {
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(context)?.toString()
        }
    }

    private fun osSensitive(description: ClipDescription): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val extras: PersistableBundle = description.extras ?: return false
        return extras.getBoolean(SENSITIVE_KEY, false)
    }

    fun add(text: String, flaggedByOs: Boolean = false): Boolean {
        val sensitivity = SensitiveDetector.classify(text, flaggedByOs)
        if (sensitivity != Sensitivity.NONE && prefs.sensitivePolicy == "skip") return false
        val now = System.currentTimeMillis()
        val expires = if (sensitivity != Sensitivity.NONE && prefs.sensitivePolicy == "expire") now + 60_000L else 0L
        synchronized(lock) {
            val list = load()
            list.removeAll { it.text == text }
            list.add(0, ClipEntry(now, text, now, sensitivity, expires))
            prune(list)
            save(list)
        }
        return true
    }

    fun delete(id: Long) = synchronized(lock) {
        val list = load()
        list.removeAll { it.id == id }
        save(list)
    }

    fun clear() = synchronized(lock) {
        load().clear()
        file.delete()
    }

    fun copy(text: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText("Tacit", text)
        if (sensitive && Build.VERSION.SDK_INT >= 31) {
            val extras = PersistableBundle()
            extras.putBoolean(SENSITIVE_KEY, true)
            clip.description.extras = extras
        }
        manager?.setPrimaryClip(clip)
        manager?.primaryClipDescription?.timestamp?.let { prefs.clipLastTimestamp = it }
    }

    private fun prune(list: MutableList<ClipEntry>): Boolean {
        val before = list.size
        val now = System.currentTimeMillis()
        list.removeAll { it.expiresAt in 1 until now }
        val days = prefs.clipRetentionDays
        if (days > 0) list.removeAll { now - it.time > days * 86_400_000L }
        val max = prefs.clipMaxItems
        if (max > 0) while (list.size > max) list.removeAt(list.size - 1)
        return list.size != before
    }

    private fun load(): MutableList<ClipEntry> {
        entries?.let { return it }
        val loaded = ArrayList<ClipEntry>()
        if (file.exists()) {
            runCatching {
                val plain = Crypto.decrypt(file.readBytes())
                DataInputStream(plain.inputStream()).use { input ->
                    repeat(input.readInt()) {
                        val id = input.readLong()
                        val length = input.readInt()
                        val bytes = ByteArray(length).also { input.readFully(it) }
                        val time = input.readLong()
                        val sensitivity = Sensitivity.entries[input.readByte().toInt()]
                        val expires = input.readLong()
                        loaded.add(ClipEntry(id, String(bytes, Charsets.UTF_8), time, sensitivity, expires))
                    }
                }
            }
        }
        entries = loaded
        return loaded
    }

    private fun save(list: List<ClipEntry>) {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(list.size)
            for (entry in list) {
                val bytes = entry.text.toByteArray(Charsets.UTF_8)
                out.writeLong(entry.id)
                out.writeInt(bytes.size)
                out.write(bytes)
                out.writeLong(entry.time)
                out.writeByte(entry.sensitivity.ordinal)
                out.writeLong(entry.expiresAt)
            }
        }
        val temp = File(file.parentFile, "clips.bin.tmp")
        temp.writeBytes(Crypto.encrypt(buffer.toByteArray()))
        temp.renameTo(file)
    }
}

object Crypto {
    private const val ALIAS = "tacit_clipboard"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + body
    }

    fun decrypt(data: ByteArray): ByteArray {
        val ivLength = data[0].toInt()
        val iv = data.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(data, 1 + ivLength, data.size - 1 - ivLength)
    }
}
