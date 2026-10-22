package app.tacit.files

import java.io.File
import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

object ScanRules {
    fun isRestricted(path: String): Boolean = path.endsWith("/Android/data") || path.endsWith("/Android/obb")
}

class FileWalkStrategy(private val roots: List<File>, private val includeHidden: Boolean) : ScanStrategy {

    override val id: Int = ScanStrategyIds.FILE_WALK

    override fun scan(sink: EntrySink) {
        for (root in roots) {
            if (!sink.accepting) return
            if (!walk(root, sink)) return
        }
    }

    private fun walk(root: File, sink: EntrySink): Boolean {
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            val dirIndex = sink.directory(dir.absolutePath)
            for (child in children) {
                val name = child.name
                if (!includeHidden && name.startsWith('.')) continue
                if (child.isDirectory) {
                    if (ScanRules.isRestricted(child.absolutePath)) continue
                    stack.addLast(child)
                    if (!sink.add("$name/", dirIndex, 0L, child.lastModified())) return false
                } else {
                    if (!sink.add(name, dirIndex, child.length(), child.lastModified())) return false
                }
            }
        }
        return true
    }
}

class NioWalkStrategy(private val roots: List<File>, private val includeHidden: Boolean) : ScanStrategy {

    override val id: Int = ScanStrategyIds.NIO_WALK

    override fun scan(sink: EntrySink) {
        for (root in roots) {
            if (!sink.accepting) return
            if (!walk(root.toPath(), sink)) return
        }
    }

    private fun walk(root: Path, sink: EntrySink): Boolean {
        val stack = ArrayDeque<Path>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val stream = try {
                Files.newDirectoryStream(dir)
            } catch (error: IOException) {
                continue
            } catch (error: SecurityException) {
                continue
            }
            var finished = true
            stream.use { children ->
                val dirIndex = sink.directory(dir.toString())
                try {
                    for (child in children) {
                        val name = child.fileName.toString()
                        if (!includeHidden && name.startsWith('.')) continue
                        val attributes = try {
                            Files.readAttributes(child, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                        } catch (error: IOException) {
                            continue
                        }
                        if (attributes.isSymbolicLink) continue
                        val modified = attributes.lastModifiedTime().toMillis()
                        if (attributes.isDirectory) {
                            if (ScanRules.isRestricted(child.toString())) continue
                            stack.addLast(child)
                            if (!sink.add("$name/", dirIndex, 0L, modified)) {
                                finished = false
                                break
                            }
                        } else if (!sink.add(name, dirIndex, attributes.size(), modified)) {
                            finished = false
                            break
                        }
                    }
                } catch (error: DirectoryIteratorException) {
                    return@use
                }
            }
            if (!finished) return false
        }
        return true
    }
}
