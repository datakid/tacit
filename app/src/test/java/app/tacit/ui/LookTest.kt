package app.tacit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookTest {

    private val base = Look(
        theme = "system", dark = false, wallpaper = false, barBottom = true, clock = false, date = false,
        battery = false, visiblePassword = false, iconMode = "mono", density = "comfortable", drawer = false, resultCount = 8, rules = "a"
    )

    @Test
    fun identicalLooksAreEqual() {
        assertEquals(base, base.copy())
    }

    @Test
    fun everyVisibleSettingMakesTheLooksDiffer() {
        val changed = listOf(
            base.copy(theme = "dark"),
            base.copy(dark = true),
            base.copy(wallpaper = true),
            base.copy(barBottom = false),
            base.copy(clock = true),
            base.copy(date = true),
            base.copy(battery = true),
            base.copy(visiblePassword = true),
            base.copy(iconMode = "color"),
            base.copy(density = "compact"),
            base.copy(drawer = true),
            base.copy(resultCount = 9)
        )
        for (look in changed) {
            assertNotEquals(base, look)
            assertFalse(base.sameViews(look))
        }
    }

    @Test
    fun searchRulesChangeNeedsNoViewRebuild() {
        val rules = base.copy(rules = "b")
        assertNotEquals(base, rules)
        assertTrue(base.sameViews(rules))
    }
}
