package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowLayoutTest {

    @Test
    fun comfortableValuesAreTheOnesBeforeTheRedefinition() {
        val d = RowDensity.COMFORTABLE
        assertEquals(64, d.rowDp)
        assertEquals(48, d.slotDp)
        assertEquals(40, d.iconDp)
        assertEquals(10, d.kindPadDp)
        assertEquals(15f, d.initialsSp, 0f)
        assertEquals(17f, d.titleSp, 0f)
        assertEquals(14f, d.subtitleSp, 0f)
        assertFalse(d.inlineSubtitle)
    }

    @Test
    fun compactIsTheDenseInlineRow() {
        val d = RowDensity.COMPACT
        assertEquals(48, d.rowDp)
        assertEquals(32, d.slotDp)
        assertEquals(28, d.iconDp)
        assertEquals(6, d.kindPadDp)
        assertEquals(12f, d.initialsSp, 0f)
        assertEquals(15f, d.titleSp, 0f)
        assertEquals(12f, d.subtitleSp, 0f)
        assertTrue(d.inlineSubtitle)
    }

    @Test
    fun everyTouchTargetIsAtLeast48Dp() {
        for (d in RowDensity.entries) {
            assertTrue(d.rowDp >= 48)
            assertTrue(d.listRowDp >= 48)
            assertTrue(RowLayout.BUTTON_DP >= 48)
        }
    }

    @Test
    fun listRowsAndFieldsFollowTheDensity() {
        assertEquals(56, RowDensity.COMFORTABLE.listRowDp)
        assertEquals(48, RowDensity.COMPACT.listRowDp)
        assertEquals(14, RowDensity.COMFORTABLE.fieldPadDp)
        assertEquals(10, RowDensity.COMPACT.fieldPadDp)
        assertEquals(11f, RowDensity.COMFORTABLE.stripSp, 0f)
        assertEquals(10f, RowDensity.COMPACT.stripSp, 0f)
        assertTrue(RowDensity.COMFORTABLE.listPadDp > RowDensity.COMPACT.listPadDp)
    }

    @Test
    fun buttonsFitFollowsTheWidth() {
        assertEquals(1, RowLayout.buttonsFit(320, 48))
        assertEquals(2, RowLayout.buttonsFit(320, 32))
        assertEquals(2, RowLayout.buttonsFit(360, 48))
        assertEquals(3, RowLayout.buttonsFit(411, 48))
        assertEquals(3, RowLayout.buttonsFit(411, 32))
        assertEquals(4, RowLayout.buttonsFit(450, 32))
        assertEquals(7, RowLayout.buttonsFit(600, 48))
    }

    @Test
    fun buttonsFitNeverDropsBelowOne() {
        for (width in 0..200) {
            for (d in RowDensity.entries) assertEquals(1, RowLayout.buttonsFit(width, d.slotDp))
        }
    }

    @Test
    fun everythingShowsWhenItFits() {
        assertEquals(4, RowLayout.shownButtons(4, 4))
        assertFalse(RowLayout.hasMore(4, 4))
        assertEquals(4, RowLayout.buttonSlots(4, 6))
        assertEquals(0, RowLayout.shownButtons(0, 1))
        assertFalse(RowLayout.hasMore(0, 1))
        assertEquals(2, RowLayout.buttonSlots(2, 2))
    }

    @Test
    fun overflowShowsOneFewerThanFitsAndAMoreButton() {
        assertEquals(2, RowLayout.shownButtons(4, 3))
        assertTrue(RowLayout.hasMore(4, 3))
        assertEquals(3, RowLayout.buttonSlots(4, 3))
        assertEquals(0, RowLayout.shownButtons(4, 1))
        assertEquals(1, RowLayout.buttonSlots(4, 1))
        assertEquals(0, RowLayout.shownButtons(2, 1))
        assertEquals(1, RowLayout.buttonSlots(2, 1))
    }

    @Test
    fun theTitleKeepsAtLeast140DpAtEveryWidthFromAThreeTwentyPhone() {
        for (width in 320..1000) {
            for (d in RowDensity.entries) {
                val fit = RowLayout.buttonsFit(width, d.slotDp)
                for (total in 0..4) {
                    val slots = RowLayout.buttonSlots(total, fit)
                    val title = RowLayout.titleDp(width, d.slotDp, slots)
                    assertTrue("width $width ${d.key} total $total: title $title dp", title >= RowLayout.TITLE_MIN_DP)
                }
            }
        }
    }

    @Test
    fun aThreeTwentyPhoneWithBothWhatsAppsKeepsTheNameReadable() {
        val comfortable = RowLayout.buttonSlots(4, RowLayout.buttonsFit(320, 48))
        assertEquals(1, comfortable)
        assertEquals(176, RowLayout.titleDp(320, 48, comfortable))
        val compact = RowLayout.buttonSlots(4, RowLayout.buttonsFit(320, 32))
        assertEquals(2, compact)
        assertEquals(144, RowLayout.titleDp(320, 32, compact))
    }

    @Test
    fun theInlineSubtitleYieldsToTheTitle() {
        for (width in 320..1000) {
            for (slots in 0..4) {
                val subtitle = RowLayout.subtitleMaxDp(width, 32, slots)
                if (subtitle > 0) {
                    val title = RowLayout.titleDp(width, 32, slots) - subtitle - RowLayout.SUBTITLE_GAP_DP
                    assertTrue("width $width slots $slots: title $title dp", title >= RowLayout.TITLE_MIN_DP)
                    assertTrue(subtitle <= width * RowLayout.SUBTITLE_SHARE_PERCENT / 100)
                    assertTrue(subtitle >= RowLayout.SUBTITLE_MIN_DP)
                }
            }
        }
    }

    @Test
    fun theInlineSubtitleIsDroppedWhenThereIsNoRoomForIt() {
        assertEquals(0, RowLayout.subtitleMaxDp(320, 32, 2))
        assertEquals(0, RowLayout.subtitleMaxDp(411, 32, 4))
        assertTrue(RowLayout.subtitleMaxDp(411, 32, 0) > 0)
    }

    @Test
    fun theInlineSubtitleTakesAtMostFortyPercentOfTheScreen() {
        assertEquals(92, RowLayout.subtitleMaxDp(320, 32, 0))
        assertEquals(164, RowLayout.subtitleMaxDp(411, 32, 0))
        assertEquals(320, RowLayout.subtitleMaxDp(800, 32, 0))
    }
}
