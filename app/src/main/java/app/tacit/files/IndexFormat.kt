package app.tacit.files

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class VolumeStamp(val name: String, val version: String, val generation: Long)

class IndexHeader(
    val entryCount: Int,
    val createdAt: Long,
    val verifiedAt: Long,
    val strategy: Int,
    val truncated: Boolean,
    val volumes: List<VolumeStamp>
)

class LoadedIndex(val table: FileTable, val header: IndexHeader?, val legacy: Boolean)

object IndexFormat {

    const val MAGIC = 0x54464932
    const val VERSION = 2
    private const val LEGACY_VERSION = 1
    private const val FLAG_NORMALIZED = 1
    private const val FLAG_DIRECTORY = 2
    private const val FLAG_DOCUMENT = 4
    private const val MAX_ENTRIES = TableBuilder.MAX_FILES
    private const val MAX_DIRECTORIES = 2_000_000
    private const val MAX_VOLUMES = 64

    fun write(out: DataOutputStream, table: FileTable, header: IndexHeader) {
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeInt(table.count)
        out.writeLong(header.createdAt)
        out.writeLong(header.verifiedAt)
        out.writeByte(header.strategy)
        out.writeBoolean(header.truncated)
        out.writeInt(header.volumes.size)
        for (volume in header.volumes) {
            out.writeUTF(volume.name)
            out.writeUTF(volume.version)
            out.writeLong(volume.generation)
        }
        out.writeInt(table.directories.size)
        for (directory in table.directories) out.writeUTF(directory)
        for (i in 0 until table.count) {
            val stored = table.names[i]
            val display = TreeNames.displayName(stored)
            val documentUri = TreeNames.documentUriString(stored)
            val directory = display.endsWith("/")
            val normalized = table.normalized[i]
            val keepNormalized = normalized != display.lowercase()
            var flags = 0
            if (keepNormalized) flags = flags or FLAG_NORMALIZED
            if (directory) flags = flags or FLAG_DIRECTORY
            if (documentUri != null) flags = flags or FLAG_DOCUMENT
            out.writeUTF(if (directory) display.dropLast(1) else display)
            out.writeByte(flags)
            if (keepNormalized) out.writeUTF(normalized)
            if (documentUri != null) out.writeUTF(documentUri)
            out.writeInt(table.parent[i])
            out.writeLong(table.sizes[i])
            out.writeLong(table.modified[i])
        }
    }

    fun readHeader(input: DataInputStream): IndexHeader? {
        if (input.readInt() != MAGIC) return null
        if (input.readInt() != VERSION) return null
        return readHeaderFields(input)
    }

    fun read(input: DataInputStream): LoadedIndex? = when (input.readInt()) {
        MAGIC -> if (input.readInt() == VERSION) readCurrent(input) else null
        LEGACY_VERSION -> readLegacy(input)
        else -> null
    }

    private fun readHeaderFields(input: DataInputStream): IndexHeader {
        val entryCount = input.readInt()
        if (entryCount < 0 || entryCount > MAX_ENTRIES) throw IOException("entry count $entryCount")
        val createdAt = input.readLong()
        val verifiedAt = input.readLong()
        val strategy = input.readUnsignedByte()
        val truncated = input.readBoolean()
        val volumeCount = input.readInt()
        if (volumeCount < 0 || volumeCount > MAX_VOLUMES) throw IOException("volume count $volumeCount")
        val volumes = List(volumeCount) { VolumeStamp(input.readUTF(), input.readUTF(), input.readLong()) }
        return IndexHeader(entryCount, createdAt, verifiedAt, strategy, truncated, volumes)
    }

    private fun readDirectories(input: DataInputStream): Array<String> {
        val count = input.readInt()
        if (count < 0 || count > MAX_DIRECTORIES) throw IOException("directory count $count")
        return Array(count) { input.readUTF() }
    }

    private fun checkParent(parent: Int, directories: Int) {
        if (parent < 0 || parent >= directories) throw IOException("parent $parent of $directories")
    }

    private fun readCurrent(input: DataInputStream): LoadedIndex {
        val header = readHeaderFields(input)
        val directories = readDirectories(input)
        val count = header.entryCount
        val names = arrayOfNulls<String>(count)
        val normalized = arrayOfNulls<String>(count)
        val parent = IntArray(count)
        val sizes = LongArray(count)
        val modified = LongArray(count)
        for (i in 0 until count) {
            var display = input.readUTF()
            val flags = input.readUnsignedByte()
            if ((flags and FLAG_DIRECTORY) != 0) display += "/"
            normalized[i] = if ((flags and FLAG_NORMALIZED) != 0) input.readUTF() else display.lowercase()
            names[i] = if ((flags and FLAG_DOCUMENT) != 0) TreeNames.stored(display, input.readUTF()) else display
            parent[i] = input.readInt()
            checkParent(parent[i], directories.size)
            sizes[i] = input.readLong()
            modified[i] = input.readLong()
        }
        val table = FileTable(directories, names.requireNoNulls(), normalized.requireNoNulls(), parent, sizes, modified)
        return LoadedIndex(table, header, false)
    }

    private fun readLegacy(input: DataInputStream): LoadedIndex {
        val directories = readDirectories(input)
        val count = input.readInt()
        if (count < 0 || count > MAX_ENTRIES) throw IOException("entry count $count")
        val names = arrayOfNulls<String>(count)
        val normalized = arrayOfNulls<String>(count)
        val parent = IntArray(count)
        val sizes = LongArray(count)
        val modified = LongArray(count)
        for (i in 0 until count) {
            val name = input.readUTF()
            names[i] = name
            normalized[i] = TreeNames.normalizedName(name)
            parent[i] = input.readInt()
            checkParent(parent[i], directories.size)
            sizes[i] = input.readLong()
            modified[i] = input.readLong()
        }
        val table = FileTable(directories, names.requireNoNulls(), normalized.requireNoNulls(), parent, sizes, modified)
        return LoadedIndex(table, null, true)
    }
}
