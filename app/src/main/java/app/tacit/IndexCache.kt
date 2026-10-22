package app.tacit

import android.content.Context
import app.tacit.core.IndexCodec
import app.tacit.core.SearchItem
import java.io.File

object IndexCache {

    private const val FILE = "index.bin"

    fun write(context: Context, items: List<SearchItem>) {
        runCatching { IndexCodec.writeFile(File(context.cacheDir, FILE), items) }
    }

    fun read(context: Context): List<SearchItem>? =
        runCatching { IndexCodec.readFile(File(context.cacheDir, FILE)) }.getOrNull()

    fun clear(context: Context) {
        File(context.cacheDir, FILE).delete()
    }
}
