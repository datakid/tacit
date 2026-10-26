package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerPrewarmTest {

    @Test
    fun aScreenAskForTheRowsThatFillIt() {
        assertEquals(15, DrawerPrewarm.rows(1920, 128))
        assertEquals(16, DrawerPrewarm.rows(2000, 128))
        assertEquals(8, DrawerPrewarm.rows(1000, 126))
    }

    @Test
    fun aPartialRowAtTheBottomCounts() {
        assertEquals(2, DrawerPrewarm.rows(101, 100))
        assertEquals(1, DrawerPrewarm.rows(100, 100))
        assertEquals(1, DrawerPrewarm.rows(1, 100))
    }

    @Test
    fun nothingIsRequestedWithoutASize() {
        assertEquals(0, DrawerPrewarm.rows(0, 100))
        assertEquals(0, DrawerPrewarm.rows(1920, 0))
        assertEquals(0, DrawerPrewarm.rows(-5, 100))
        assertEquals(0, DrawerPrewarm.rows(1920, -1))
    }
}
