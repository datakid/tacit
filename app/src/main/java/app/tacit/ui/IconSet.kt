package app.tacit.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.SparseArray

class IconSet(private val context: Context, private val tint: Int) {

    private val states = SparseArray<Drawable.ConstantState>()

    fun make(resId: Int): Drawable {
        var state = states.get(resId)
        if (state == null) {
            val base = context.getDrawable(resId)!!.mutate()
            base.setTint(tint)
            state = base.constantState!!
            states.put(resId, state)
        }
        return state.newDrawable(context.resources).mutate()
    }
}
