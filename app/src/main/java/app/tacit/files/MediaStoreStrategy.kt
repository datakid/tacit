package app.tacit.files

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.provider.MediaStore
import java.io.File

@TargetApi(Build.VERSION_CODES.R)
class MediaStoreStrategy(private val context: Context, private val includeHidden: Boolean) : ScanStrategy {

    override val id: Int = ScanStrategyIds.MEDIA_STORE

    private var stopped = false

    override fun scan(sink: EntrySink) {
        val storage = context.getSystemService(StorageManager::class.java)
        val roots = HashMap<String, File>()
        for (volume in storage.storageVolumes) {
            val name = volume.mediaStoreVolumeName ?: continue
            val directory = volume.directory ?: continue
            roots[name] = directory
        }
        val names = runCatching { MediaStore.getExternalVolumeNames(context) }.getOrDefault(emptySet())
        for (volume in names.sorted()) {
            val root = roots[volume] ?: continue
            scanVolume(volume, root.absolutePath, sink)
            if (stopped) return
        }
    }

    private fun scanVolume(volume: String, root: String, sink: EntrySink) {
        val projection = arrayOf(
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val cursor = runCatching {
            context.contentResolver.query(MediaStore.Files.getContentUri(volume), projection, null, null, null)
        }.getOrNull() ?: return
        val folders = HashMap<String, Int>()
        folders[""] = sink.directory(root)
        cursor.use {
            while (it.moveToNext()) {
                if (!sink.accepting) {
                    stopped = true
                    return
                }
                val relative = (it.getString(0) ?: "").removeSuffix("/")
                val name = it.getString(1) ?: continue
                if (!includeHidden && (name.startsWith('.') || hasHiddenSegment(relative))) continue
                if (isRestricted(relative)) continue
                val parent = folderIndex(relative, root, folders, sink)
                if (stopped) return
                val size = if (it.isNull(2)) 0L else it.getLong(2)
                val modified = if (it.isNull(3)) 0L else it.getLong(3) * 1000L
                if (!sink.add(name, parent, size, modified)) {
                    stopped = true
                    return
                }
            }
        }
    }

    private fun folderIndex(relative: String, root: String, folders: HashMap<String, Int>, sink: EntrySink): Int {
        folders[relative]?.let { return it }
        val cut = relative.lastIndexOf('/')
        val parentRelative = if (cut < 0) "" else relative.substring(0, cut)
        val parentIndex = folderIndex(parentRelative, root, folders, sink)
        if (stopped) return parentIndex
        val index = sink.directory("$root/$relative")
        folders[relative] = index
        if (!sink.add(relative.substring(cut + 1) + "/", parentIndex, 0L, 0L)) stopped = true
        return index
    }

    private fun hasHiddenSegment(relative: String): Boolean {
        if (relative.isEmpty()) return false
        for (segment in relative.split('/')) if (segment.startsWith('.')) return true
        return false
    }

    private fun isRestricted(relative: String): Boolean {
        if (relative.isEmpty()) return false
        val segments = relative.split('/')
        for (i in 0 until segments.size - 1) {
            if (segments[i] == "Android" && (segments[i + 1] == "data" || segments[i + 1] == "obb")) return true
        }
        return false
    }
}
