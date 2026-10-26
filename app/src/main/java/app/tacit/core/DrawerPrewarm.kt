package app.tacit.core

object DrawerPrewarm {

    fun rows(screenHeightPx: Int, rowHeightPx: Int): Int =
        if (screenHeightPx <= 0 || rowHeightPx <= 0) 0 else (screenHeightPx + rowHeightPx - 1) / rowHeightPx
}
