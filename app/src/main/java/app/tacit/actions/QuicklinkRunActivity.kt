package app.tacit.actions

import android.app.Activity
import android.os.Bundle

class QuicklinkRunActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_ID)?.let { QuicklinkLauncher.launch(this, it, null) }
        finish()
    }

    companion object {
        const val EXTRA_ID = "quicklink_id"
    }
}
