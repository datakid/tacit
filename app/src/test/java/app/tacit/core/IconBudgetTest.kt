package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class IconBudgetTest {

    private val mb = 1024L * 1024L

    @Test
    fun aSmallHeapGetsTheTwelveMegabyteFloor() {
        assertEquals(12 * 1024 * 1024, IconBudget.bytes(64 * mb))
        assertEquals(12 * 1024 * 1024, IconBudget.bytes(96 * mb))
        assertEquals(12 * 1024 * 1024, IconBudget.bytes(0L))
    }

    @Test
    fun aMiddleHeapGetsAnEighthOfIt() {
        assertEquals(16 * 1024 * 1024, IconBudget.bytes(128 * mb))
        assertEquals(20 * 1024 * 1024, IconBudget.bytes(160 * mb))
        assertEquals(24 * 1024 * 1024, IconBudget.bytes(192 * mb))
    }

    @Test
    fun aBigHeapStopsAtTwentyFourMegabytes() {
        assertEquals(24 * 1024 * 1024, IconBudget.bytes(256 * mb))
        assertEquals(24 * 1024 * 1024, IconBudget.bytes(512 * mb))
        assertEquals(24 * 1024 * 1024, IconBudget.bytes(Long.MAX_VALUE))
    }

    @Test
    fun theBudgetNeverFallsBelowTheFloorOrRisesAboveTheCeiling() {
        var heap = 0L
        while (heap <= 1024 * mb) {
            val bytes = IconBudget.bytes(heap)
            assertEquals(true, bytes >= IconBudget.FLOOR_BYTES && bytes <= IconBudget.CEILING_BYTES)
            heap += 7 * mb
        }
    }
}
