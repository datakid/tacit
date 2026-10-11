package app.tacit

import android.os.BatteryManager
import app.tacit.system.BatteryStatus
import app.tacit.system.BatteryStatus.State
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryStatusTest {

    @Test
    fun percentRoundsDownAndClamps() {
        assertEquals(50, BatteryStatus.percent(50, 100))
        assertEquals(33, BatteryStatus.percent(1, 3))
        assertEquals(100, BatteryStatus.percent(120, 100))
        assertEquals(0, BatteryStatus.percent(0, 100))
    }

    @Test
    fun unknownLevelOrScaleGivesMinusOne() {
        assertEquals(-1, BatteryStatus.percent(-1, 100))
        assertEquals(-1, BatteryStatus.percent(50, 0))
        assertEquals(-1, BatteryStatus.percent(50, -1))
    }

    @Test
    fun statusMapsToState() {
        assertEquals(State.CHARGING, BatteryStatus.state(BatteryManager.BATTERY_STATUS_CHARGING))
        assertEquals(State.FULL, BatteryStatus.state(BatteryManager.BATTERY_STATUS_FULL))
        assertEquals(State.DISCHARGING, BatteryStatus.state(BatteryManager.BATTERY_STATUS_DISCHARGING))
        assertEquals(State.DISCHARGING, BatteryStatus.state(BatteryManager.BATTERY_STATUS_NOT_CHARGING))
        assertEquals(State.DISCHARGING, BatteryStatus.state(-1))
    }
}
