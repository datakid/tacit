package app.tacit.actions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.tacit.core.ExtraType
import app.tacit.core.IntentExtra
import app.tacit.core.IntentSpec
import app.tacit.core.QuicklinkRules

object IntentSanitizer {

    fun build(spec: IntentSpec, own: String): Intent? {
        if (spec.validate(own).isNotEmpty()) return null
        val intent = Intent()
        if (spec.action.isNotEmpty()) intent.action = spec.action
        val data = spec.data.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
        val mime = spec.mime.takeIf { it.isNotEmpty() }
        when {
            data != null && mime != null -> intent.setDataAndType(data, mime)
            data != null -> intent.data = data
            mime != null -> intent.type = mime
        }
        for (category in spec.categories) intent.addCategory(category)
        if (spec.component.isNotEmpty()) intent.component = ComponentName.unflattenFromString(spec.component) ?: return null
        for (extra in spec.extras) {
            when (extra.type) {
                ExtraType.STRING -> intent.putExtra(extra.key, extra.value)
                ExtraType.INT -> intent.putExtra(extra.key, extra.value.toInt())
                ExtraType.LONG -> intent.putExtra(extra.key, extra.value.toLong())
                ExtraType.BOOLEAN -> intent.putExtra(extra.key, extra.value.equals("true", true))
            }
        }
        return intent
    }

    @Suppress("DEPRECATION")
    fun specOf(intent: Intent): IntentSpec? = runCatching {
        val extras = ArrayList<IntentExtra>()
        val bundle = intent.extras
        if (bundle != null) {
            for (key in bundle.keySet().sorted()) {
                when (val value = bundle.get(key)) {
                    is String -> extras.add(IntentExtra(key, ExtraType.STRING, value))
                    is Int -> extras.add(IntentExtra(key, ExtraType.INT, value.toString()))
                    is Long -> extras.add(IntentExtra(key, ExtraType.LONG, value.toString()))
                    is Boolean -> extras.add(IntentExtra(key, ExtraType.BOOLEAN, value.toString()))
                }
            }
        }
        IntentSpec(
            action = intent.action.orEmpty(),
            data = intent.dataString.orEmpty(),
            mime = intent.type.orEmpty(),
            categories = intent.categories.orEmpty().sorted(),
            component = intent.component?.flattenToString().orEmpty(),
            extras = extras
        )
    }.getOrNull()

    fun sanitize(source: Intent, own: String): Intent? {
        val spec = specOf(source) ?: return null
        if (source.`package` == own) return null
        return build(spec, own)
    }

    fun specOfTarget(target: String, own: String): IntentSpec? = parse(target, own)?.let { specOf(it) }

    fun encode(intent: Intent): String = intent.toUri(Intent.URI_INTENT_SCHEME)

    fun parse(target: String, own: String): Intent? {
        if (!target.startsWith(QuicklinkRules.INTENT_PREFIX)) return null
        val parsed = runCatching { Intent.parseUri(target, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return null
        if (!hasExplicitAction(target)) parsed.action = null
        return sanitize(parsed, own)
    }

    private fun hasExplicitAction(target: String): Boolean {
        val start = target.lastIndexOf('#')
        if (start < 0 || !target.startsWith("#Intent;", start)) return false
        val body = target.substring(start + "#Intent;".length).substringBefore("SEL;")
        return body.startsWith("action=") || body.contains(";action=")
    }

    fun canonical(target: String, own: String): String? = parse(target, own)?.let { encode(it) }

    fun launchable(target: String, own: String): Intent? =
        parse(target, own)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun launchable(intent: Intent): Intent = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun componentIntent(target: String, own: String): Intent? {
        val component = ComponentName.unflattenFromString(target) ?: return null
        if (component.packageName == own) return null
        return Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun resolves(context: Context, intent: Intent): Boolean =
        runCatching { context.packageManager.resolveActivity(intent, 0) != null }.getOrDefault(false)
}
