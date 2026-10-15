package app.tacit.actions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import app.tacit.R
import app.tacit.core.ExportKind
import app.tacit.data.ContactExport

class ExportActivity : Activity() {

    private var kind: ExportKind? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chosen = ExportKind.of(intent.getStringExtra(EXTRA_KIND).orEmpty())
        if (chosen == null) {
            finish()
            return
        }
        kind = chosen
        if (savedInstanceState != null) return
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            chooseDestination(chosen)
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_PERMISSION)
        }
    }

    private fun chooseDestination(chosen: ExportKind) {
        val pick = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(chosen.mime)
            .putExtra(Intent.EXTRA_TITLE, chosen.fileName)
        try {
            startActivityForResult(pick, REQ_DESTINATION)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSION) return
        val chosen = kind
        if (chosen != null && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            chooseDestination(chosen)
        } else {
            Toast.makeText(this, R.string.export_no_permission, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Platform API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        val chosen = kind
        if (requestCode == REQ_DESTINATION && resultCode == RESULT_OK && uri != null && chosen != null) {
            ContactExport.run(applicationContext, uri, chosen)
        }
        finish()
    }

    companion object {
        const val EXTRA_KIND = "kind"
        private const val REQ_PERMISSION = 41
        private const val REQ_DESTINATION = 42
    }
}
