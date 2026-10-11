package app.tacit.system

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import app.tacit.Graph
import app.tacit.R

class SaveTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        val message = when {
            text.isEmpty() -> null
            !Graph.prefs.clipboardEnabled -> R.string.save_text_off
            text.length > MAX_LENGTH || !Graph.clipboard.add(text) -> R.string.save_text_skipped
            else -> R.string.save_text_saved
        }
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private companion object {
        const val MAX_LENGTH = 100_000
    }
}
