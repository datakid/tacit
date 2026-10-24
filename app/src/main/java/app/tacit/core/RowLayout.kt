package app.tacit.core

object RowLayout {

    const val BUTTON_DP = 48
    const val TITLE_MIN_DP = 140
    const val SUBTITLE_GAP_DP = 8
    const val SUBTITLE_MIN_DP = 48
    const val SUBTITLE_SHARE_PERCENT = 40
    const val LIST_PAD_DP = 16
    const val ROW_START_DP = 12
    const val ROW_END_DP = 4
    const val TEXT_START_DP = 12
    const val TEXT_END_DP = 4

    fun fixedDp(slotDp: Int): Int = LIST_PAD_DP + ROW_START_DP + ROW_END_DP + slotDp + TEXT_START_DP + TEXT_END_DP

    fun buttonsFit(screenWidthDp: Int, slotDp: Int): Int =
        maxOf(1, (screenWidthDp - fixedDp(slotDp) - TITLE_MIN_DP) / BUTTON_DP)

    fun shownButtons(total: Int, fit: Int): Int = if (total <= fit) total else fit - 1

    fun hasMore(total: Int, fit: Int): Boolean = total > fit

    fun buttonSlots(total: Int, fit: Int): Int = shownButtons(total, fit) + if (hasMore(total, fit)) 1 else 0

    fun titleDp(screenWidthDp: Int, slotDp: Int, slots: Int): Int = screenWidthDp - fixedDp(slotDp) - slots * BUTTON_DP

    fun subtitleMaxDp(screenWidthDp: Int, slotDp: Int, slots: Int): Int {
        val room = titleDp(screenWidthDp, slotDp, slots) - TITLE_MIN_DP - SUBTITLE_GAP_DP
        val cap = screenWidthDp * SUBTITLE_SHARE_PERCENT / 100
        val width = minOf(room, cap)
        return if (width < SUBTITLE_MIN_DP) 0 else width
    }
}
