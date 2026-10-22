package app.tacit.core

import app.tacit.sources.ContactInfo
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

object IndexCodec {

    const val VERSION = 4

    private const val EXTRA_NONE = 0
    private const val EXTRA_CONTACT = 1
    private const val EXTRA_QUICKLINK = 2
    private const val MAX_ITEMS = 5_000_000
    private const val MAX_PAYLOAD_BYTES = 64 * 1024 * 1024
    private const val MAX_NUMBERS = 1_000
    private const val NO_ID = -1L

    fun write(out: DataOutput, items: List<SearchItem>) {
        out.writeInt(VERSION)
        out.writeInt(items.size)
        for (item in items) {
            out.writeUTF(item.key)
            out.writeByte(item.kind.ordinal)
            out.writeUTF(item.label.take(4000))
            out.writeUTF(item.subtitle.take(4000))
            val payload = item.payload.toByteArray(Charsets.UTF_8)
            out.writeInt(payload.size)
            out.write(payload)
            out.writeBoolean(item.pinned)
            out.writeShort(item.extraNames.size.coerceAtMost(64))
            for (name in item.extraNames.take(64)) out.writeUTF(name.take(1000))
            out.writeShort(item.aliases.size.coerceAtMost(64))
            for (alias in item.aliases.take(64)) out.writeUTF(alias.take(200))
            writeExtra(out, item)
        }
    }

    fun read(input: DataInput): List<SearchItem>? {
        if (input.readInt() != VERSION) return null
        val count = input.readInt()
        if (count < 0 || count > MAX_ITEMS) throw IOException("bad item count")
        val kinds = ItemKind.entries
        val result = ArrayList<SearchItem>(minOf(count, 65_536))
        repeat(count) {
            val key = input.readUTF()
            val kind = kinds[input.readByte().toInt()]
            val label = input.readUTF()
            val subtitle = input.readUTF()
            val payloadLength = input.readInt()
            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES) throw IOException("bad payload length")
            val payloadBytes = ByteArray(payloadLength)
            input.readFully(payloadBytes)
            val payload = String(payloadBytes, Charsets.UTF_8)
            val pinned = input.readBoolean()
            val extraNames = List(input.readShort().toInt()) { input.readUTF() }
            val aliases = List(input.readShort().toInt()) { input.readUTF() }
            val extra = readExtra(input, label)
            val item = SearchItem(key, kind, label, subtitle, extraNames, payload, extra).withAliases(aliases)
            item.pinned = pinned
            result.add(item)
        }
        return result
    }

    fun writeFile(file: File, items: List<SearchItem>) {
        val temp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(temp.outputStream(), 1 shl 16)).use { write(it, items) }
        temp.renameTo(file)
    }

    fun readFile(file: File): List<SearchItem>? {
        if (!file.exists()) return null
        return DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use { read(it) }
    }

    private fun writeExtra(out: DataOutput, item: SearchItem) {
        val extra = item.extra
        when {
            item.kind == ItemKind.CONTACT && extra is ContactInfo -> {
                out.writeByte(EXTRA_CONTACT)
                out.writeUTF(extra.lookupKey)
                out.writeLong(extra.contactId)
                out.writeInt(extra.numbers.size)
                for (number in extra.numbers) out.writeUTF(number)
                out.writeUTF(extra.primary.orEmpty())
                out.writeLong(extra.whatsappDataId ?: NO_ID)
                out.writeLong(extra.whatsappBusinessDataId ?: NO_ID)
                out.writeBoolean(extra.starred)
            }
            item.kind == ItemKind.ACTION && extra is QuicklinkType -> {
                out.writeByte(EXTRA_QUICKLINK)
                out.writeUTF(extra.name)
            }
            else -> out.writeByte(EXTRA_NONE)
        }
    }

    private fun readExtra(input: DataInput, label: String): Any? {
        when (input.readByte().toInt()) {
            EXTRA_NONE -> return null
            EXTRA_CONTACT -> {
                val lookupKey = input.readUTF()
                val contactId = input.readLong()
                val numberCount = input.readInt()
                if (numberCount < 0 || numberCount > MAX_NUMBERS) throw IOException("bad number count")
                val numbers = List(numberCount) { input.readUTF() }
                val primary = input.readUTF().ifEmpty { null }
                val whatsapp = input.readLong().takeIf { it != NO_ID }
                val business = input.readLong().takeIf { it != NO_ID }
                val starred = input.readBoolean()
                return ContactInfo(lookupKey, contactId, label, numbers, primary, whatsapp, business, starred)
            }
            EXTRA_QUICKLINK -> {
                val name = input.readUTF()
                return runCatching { QuicklinkType.valueOf(name) }.getOrNull()
            }
            else -> throw IOException("bad extra marker")
        }
    }
}
