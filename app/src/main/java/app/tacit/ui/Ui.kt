package app.tacit.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView
import app.tacit.Graph
import app.tacit.R

class Palette(val background: Int, val foreground: Int, val muted: Int, val line: Int, val surface: Int)

data class Look(
    val theme: String,
    val dark: Boolean,
    val wallpaper: Boolean,
    val barBottom: Boolean,
    val clock: Boolean,
    val date: Boolean,
    val battery: Boolean,
    val visiblePassword: Boolean,
    val iconMode: String,
    val resultCount: Int,
    val rules: String
) {
    fun sameViews(other: Look): Boolean = copy(rules = "") == other.copy(rules = "")
}

object Ui {

    fun look(context: Context): Look {
        val prefs = Graph.prefs
        return Look(
            theme = prefs.theme,
            dark = isDark(context),
            wallpaper = prefs.showWallpaper,
            barBottom = prefs.barAtBottom,
            clock = prefs.showClock,
            date = prefs.showDate,
            battery = prefs.showBattery,
            visiblePassword = prefs.visiblePasswordInput,
            iconMode = prefs.iconMode,
            resultCount = prefs.resultCount,
            rules = listOf(
                prefs.kindOrder, prefs.strictness, prefs.transliterate, prefs.starredAsPins, prefs.defaultEngine, prefs.customEngines,
                prefs.prefixCalculator, prefs.prefixClipboard, prefs.prefixSettings, prefs.prefixContacts, prefs.prefixFiles,
                prefs.prefixSnippets, prefs.precision, prefs.contactsEnabled, prefs.filesEnabled, prefs.clipboardEnabled
            ).joinToString("\u0001")
        )
    }

    fun applyTheme(activity: Activity) {
        when (Graph.prefs.theme) {
            "dark" -> activity.setTheme(R.style.Theme_Tacit_Dark)
            "light" -> activity.setTheme(R.style.Theme_Tacit_Light)
        }
    }

    fun applyBars(window: Window, dark: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
        } else {
            val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val decor = window.decorView
            @Suppress("DEPRECATION")
            decor.systemUiVisibility = if (dark) decor.systemUiVisibility and light.inv() else decor.systemUiVisibility or light
        }
    }

    fun isDark(context: Context): Boolean = when (Graph.prefs.theme) {
        "dark" -> true
        "light" -> false
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun palette(context: Context): Palette = if (isDark(context)) {
        Palette(Color.BLACK, Color.WHITE, 0xFF8A8A8A.toInt(), 0xFF262626.toInt(), 0xFF111111.toInt())
    } else {
        Palette(Color.WHITE, Color.BLACK, 0xFF6E6E6E.toInt(), 0xFFE2E2E2.toInt(), 0xFFF3F3F3.toInt())
    }

    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    fun text(context: Context, value: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

    fun outline(context: Context, palette: Palette, radiusDp: Int = 12): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(context, radiusDp).toFloat()
        setColor(palette.surface)
        setStroke(dp(context, 1), palette.line)
    }

    fun ripple(context: Context, palette: Palette, radiusDp: Int = 0, oval: Boolean = false): RippleDrawable {
        val mask = GradientDrawable().apply {
            if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(context, radiusDp).toFloat()
            setColor(Color.BLACK)
        }
        val color = (palette.muted and 0x00FFFFFF) or 0x33000000
        return RippleDrawable(ColorStateList.valueOf(color), null, mask)
    }

    fun chip(context: Context, palette: Palette, label: String, onClick: () -> Unit): TextView =
        text(context, label, 13f, palette.foreground).apply {
            val padH = dp(context, 12)
            val padV = dp(context, 6)
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 16).toFloat()
                setStroke(dp(context, 1), palette.line)
            }
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
        }

    fun row(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun column(context: Context): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    fun weight(value: Float = 1f) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, value)

    fun gone(view: View, hidden: Boolean) {
        view.visibility = if (hidden) View.GONE else View.VISIBLE
    }
}
