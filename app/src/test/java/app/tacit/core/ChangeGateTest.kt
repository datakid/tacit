package app.tacit.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeGateTest {

    @Test
    fun theFirstValueCountsAsAChange() {
        assertTrue(ChangeGate().changed(0))
    }

    @Test
    fun theSameValueIsNotAChangeTwice() {
        val gate = ChangeGate()
        assertTrue(gate.changed(5))
        assertFalse(gate.changed(5))
        assertFalse(gate.changed(5))
    }

    @Test
    fun aNewValueIsAChangeOnce() {
        val gate = ChangeGate()
        gate.changed(1)
        assertTrue(gate.changed(2))
        assertFalse(gate.changed(2))
        assertTrue(gate.changed(1))
    }
}
