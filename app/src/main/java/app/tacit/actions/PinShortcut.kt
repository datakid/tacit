package app.tacit.actions

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import app.tacit.data.Quicklink
import app.tacit.ui.IconSet
import app.tacit.ui.QuicklinkIcons

object PinShortcut {

    private const val SIZE = 432
    private const val INSET = 130

    fun supported(context: Context): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    fun info(context: Context, link: Quicklink): ShortcutInfo {
        val intent = Intent(context, QuicklinkRunActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(QuicklinkRunActivity.EXTRA_ID, link.id)
        return ShortcutInfo.Builder(context, "ql-${link.id}")
            .setShortLabel(link.name)
            .setLongLabel(link.name)
            .setIcon(icon(context, link))
            .setIntent(intent)
            .build()
    }

    fun request(context: Context, link: Quicklink): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        return manager.requestPinShortcut(info(context, link), null)
    }

    private fun icon(context: Context, link: Quicklink): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val glyph = IconSet(context, Color.WHITE).make(QuicklinkIcons.res(link.type))
        glyph.setBounds(INSET, INSET, SIZE - INSET, SIZE - INSET)
        glyph.draw(canvas)
        return Icon.createWithAdaptiveBitmap(bitmap)
    }
}
