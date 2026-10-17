package app.tacit

import android.content.Context
import java.io.File

object Welcome {

    const val MARKER = "welcomed"

    private const val UNKNOWN = 0
    private const val PENDING = 1
    private const val DONE = 2

    @Volatile
    private var state = UNKNOWN

    fun pending(context: Context): Boolean {
        if (state == UNKNOWN) state = if (File(context.filesDir, MARKER).exists()) DONE else PENDING
        return state == PENDING
    }

    fun finish(context: Context) {
        if (state == DONE) return
        state = DONE
        val file = File(context.filesDir, MARKER)
        Graph.worker.post { runCatching { file.createNewFile() } }
    }

    fun forgetForTesting() {
        state = UNKNOWN
    }
}
