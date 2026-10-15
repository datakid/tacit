package app.tacit.actions

import android.content.ClipboardManager
import android.content.Context

object ClipText {

    fun read(context: Context, limit: Int): String {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return ""
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return ""
        if (clip.itemCount == 0) return ""
        return runCatching { clip.getItemAt(0).coerceToText(context).toString() }.getOrDefault("").take(limit)
    }
}
