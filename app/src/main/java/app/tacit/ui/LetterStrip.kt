package app.tacit.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

class LetterStrip(context: Context, ink: Int) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val minCell = Ui.dp(context, 15).toFloat()
    private var letters = CharArray(0)
    private var last = -1

    var onPick: (Int) -> Unit = {}
    var onActive: (Boolean) -> Unit = {}

    init {
        paint.color = ink
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics)
        paint.typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setLetters(value: CharArray) {
        letters = value
        last = -1
        invalidate()
    }

    fun letterCount(): Int = letters.size

    fun indexAt(y: Float): Int {
        val count = letters.size
        if (count == 0 || height == 0) return -1
        return (y / height * count).toInt().coerceIn(0, count - 1)
    }

    override fun onDraw(canvas: Canvas) {
        val count = letters.size
        if (count == 0 || height == 0) return
        val cell = height.toFloat() / count
        var step = 1
        while (cell * step < minCell) step++
        val centre = width / 2f
        val baseline = -(paint.ascent() + paint.descent()) / 2f
        var index = 0
        while (index < count) {
            canvas.drawText(letters, index, 1, centre, (index + 0.5f) * cell + baseline, paint)
            index += step
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onActive(true)
                pick(event.y)
            }
            MotionEvent.ACTION_MOVE -> pick(event.y)
            MotionEvent.ACTION_UP -> {
                last = -1
                onActive(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                last = -1
                onActive(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun pick(y: Float) {
        val index = indexAt(y)
        if (index >= 0 && index != last) {
            last = index
            onPick(index)
        }
    }
}
