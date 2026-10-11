package app.tacit.system

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import app.tacit.R
import java.text.NumberFormat

object BatteryStatus {

    enum class State { DISCHARGING, CHARGING, FULL }

    fun percent(level: Int, scale: Int): Int =
        if (level < 0 || scale <= 0) -1 else (level * 100 / scale).coerceIn(0, 100)

    fun state(status: Int): State = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> State.CHARGING
        BatteryManager.BATTERY_STATUS_FULL -> State.FULL
        else -> State.DISCHARGING
    }

    fun text(context: Context, intent: Intent?): String? {
        if (intent == null) return null
        val percent = percent(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
        if (percent < 0) return null
        val level = NumberFormat.getPercentInstance().format(percent / 100.0)
        return when (state(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1))) {
            State.CHARGING -> level + SEPARATOR + context.getString(R.string.battery_charging)
            State.FULL -> level + SEPARATOR + context.getString(R.string.battery_full)
            State.DISCHARGING -> level
        }
    }

    private const val SEPARATOR = " · "
}
