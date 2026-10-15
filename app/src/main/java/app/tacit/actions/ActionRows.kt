package app.tacit.actions

import android.content.Context
import app.tacit.Graph
import app.tacit.R
import app.tacit.core.ExportKind
import app.tacit.core.ItemKind
import app.tacit.core.QuickActions
import app.tacit.core.QuickResult
import app.tacit.core.SearchItem
import app.tacit.core.Transform
import app.tacit.core.ValueKind
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object ActionRows {

    private val random by lazy { SecureRandom() }

    fun rows(text: String): List<SearchItem> {
        val result = QuickActions.evaluate(text, { LocalDateTime.now() }, { random }) ?: return emptyList()
        return build(Graph.context, result)
    }

    fun build(context: Context, result: QuickResult): List<SearchItem> = when (result) {
        is QuickResult.Timer -> listOf(
            SearchItem("act:timer", ItemKind.ACTION, context.getString(R.string.act_timer, duration(context, result.seconds)),
                result.label.ifEmpty { context.getString(R.string.act_clock) }, payload = "timer|${result.seconds}", extra = result)
        )
        is QuickResult.Alarm -> listOf(
            SearchItem("act:alarm", ItemKind.ACTION, context.getString(R.string.act_alarm, "%d:%02d".format(result.hour, result.minute)),
                result.label.ifEmpty { context.getString(R.string.act_clock) }, payload = "alarm|${result.hour}|${result.minute}", extra = result)
        )
        is QuickResult.Event -> listOf(
            SearchItem("act:event", ItemKind.ACTION,
                if (result.title.isEmpty()) context.getString(R.string.act_event_new) else context.getString(R.string.act_event, result.title),
                eventWhen(context, result), payload = "event|${result.date ?: ""}|${result.time ?: ""}", extra = result)
        )
        is QuickResult.Value -> listOf(valueRow(context, result))
        is QuickResult.Coin -> {
            val face = context.getString(if (result.heads) R.string.act_heads else R.string.act_tails)
            listOf(SearchItem("act:coin", ItemKind.ACTION, face, context.getString(R.string.act_coin), payload = "copy|$face"))
        }
        is QuickResult.ClipboardTransform -> listOf(
            SearchItem("act:clipxform:${result.transform.key}", ItemKind.ACTION, transformName(context, result.transform),
                context.getString(R.string.act_clipboard), payload = "xform|${result.transform.key}")
        )
        is QuickResult.Export -> result.kinds.map { kind ->
            SearchItem("act:export:${kind.key}", ItemKind.ACTION, exportName(context, kind),
                context.getString(R.string.act_export_sub), payload = "export|${kind.key}")
        }
    }

    private fun valueRow(context: Context, value: QuickResult.Value): SearchItem = when (value.kind) {
        ValueKind.UUID -> SearchItem("act:uuid", ItemKind.ACTION, value.text, context.getString(R.string.act_uuid), payload = "copy|${value.text}")
        ValueKind.PASSWORD -> SearchItem("act:pass", ItemKind.ACTION, value.text,
            context.getString(R.string.act_password, value.spec.toInt()), payload = "copyhidden|${value.text}")
        ValueKind.DICE -> SearchItem("act:dice", ItemKind.ACTION, value.text, context.getString(R.string.act_dice, value.spec), payload = "copy|${value.text}")
        ValueKind.RANDOM -> SearchItem("act:rand", ItemKind.ACTION, value.text, context.getString(R.string.act_random, value.spec), payload = "copy|${value.text}")
        ValueKind.TRANSFORM -> SearchItem("act:xform:${value.spec}", ItemKind.ACTION, value.text,
            value.transform?.let { transformName(context, it) }.orEmpty(), payload = "copy|${value.text}")
    }

    fun duration(context: Context, seconds: Int): String {
        val parts = ArrayList<String>(3)
        if (seconds >= 3600) parts.add(context.getString(R.string.act_unit_h, seconds / 3600))
        if (seconds % 3600 >= 60) parts.add(context.getString(R.string.act_unit_m, seconds % 3600 / 60))
        if (seconds % 60 > 0) parts.add(context.getString(R.string.act_unit_s, seconds % 60))
        return parts.joinToString(" ")
    }

    private fun eventWhen(context: Context, event: QuickResult.Event): String {
        val date = event.date ?: return context.getString(R.string.act_calendar)
        val day = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val time = event.time ?: return day
        return day + " " + "%d:%02d".format(time.hour, time.minute)
    }

    fun transformName(context: Context, transform: Transform): String = context.getString(
        when (transform) {
            Transform.UPPER -> R.string.act_t_upper
            Transform.LOWER -> R.string.act_t_lower
            Transform.TITLE -> R.string.act_t_title
            Transform.REVERSE -> R.string.act_t_reverse
            Transform.SLUG -> R.string.act_t_slug
            Transform.BASE64 -> R.string.act_t_b64
            Transform.UNBASE64 -> R.string.act_t_unb64
            Transform.URLENC -> R.string.act_t_urlenc
            Transform.URLDEC -> R.string.act_t_urldec
        }
    )

    private fun exportName(context: Context, kind: ExportKind): String = context.getString(
        when (kind) {
            ExportKind.VCARD -> R.string.act_export_vcard
            ExportKind.CSV -> R.string.act_export_csv
            ExportKind.NUMBERS -> R.string.act_export_numbers
        }
    )
}
