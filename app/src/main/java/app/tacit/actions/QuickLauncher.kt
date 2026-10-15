package app.tacit.actions

import android.app.Activity
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import app.tacit.Graph
import app.tacit.R
import app.tacit.core.ExportKind
import app.tacit.core.QuickActions
import app.tacit.core.QuickResult
import app.tacit.core.SearchItem
import app.tacit.core.Transform
import java.time.ZoneId
import java.time.ZoneOffset

object QuickLauncher {

    private const val CLIPBOARD_LIMIT = 100_000
    private const val HOUR_MILLIS = 3_600_000L
    private const val DAY_MILLIS = 86_400_000L

    fun run(activity: Activity, item: SearchItem): Boolean = when (val quick = item.extra) {
        is QuickResult.Timer -> timer(activity, quick)
        is QuickResult.Alarm -> alarm(activity, quick)
        is QuickResult.Event -> event(activity, quick)
        else -> false
    }

    fun timerIntent(timer: QuickResult.Timer): Intent {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, timer.seconds)
        if (timer.label.isNotEmpty()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, timer.label)
        return intent
    }

    fun alarmIntent(alarm: QuickResult.Alarm): Intent {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
        if (alarm.label.isNotEmpty()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, alarm.label)
        return intent
    }

    fun eventIntent(event: QuickResult.Event): Intent {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        if (event.title.isNotEmpty()) intent.putExtra(CalendarContract.Events.TITLE, event.title)
        val date = event.date ?: return intent
        val time = event.time
        if (time == null) {
            val begin = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + DAY_MILLIS)
        } else {
            val begin = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + HOUR_MILLIS)
        }
        return intent
    }

    private fun timer(activity: Activity, timer: QuickResult.Timer): Boolean = Actions.start(activity, timerIntent(timer))

    private fun alarm(activity: Activity, alarm: QuickResult.Alarm): Boolean = Actions.start(activity, alarmIntent(alarm))

    private fun event(activity: Activity, event: QuickResult.Event): Boolean = Actions.start(activity, eventIntent(event))

    fun transformClipboard(activity: Activity, key: String): Boolean {
        val transform = Transform.of(key) ?: return false
        val source = ClipText.read(activity, CLIPBOARD_LIMIT)
        if (source.isEmpty()) {
            Toast.makeText(activity, R.string.act_clip_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        val result = QuickActions.transform(transform, source)
        if (result == null) {
            Toast.makeText(activity, R.string.act_clip_invalid, Toast.LENGTH_SHORT).show()
            return false
        }
        Graph.clipboard.copy(result, false)
        return true
    }

    fun export(activity: Activity, key: String): Boolean {
        val kind = ExportKind.of(key) ?: return false
        activity.startActivity(Intent(activity, ExportActivity::class.java).putExtra(ExportActivity.EXTRA_KIND, kind.key))
        return true
    }
}
