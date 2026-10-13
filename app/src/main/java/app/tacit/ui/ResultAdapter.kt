package app.tacit.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.SparseArray
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.tacit.Graph
import app.tacit.R
import app.tacit.core.ItemKind
import app.tacit.core.SearchItem
import app.tacit.sources.ContactInfo

interface RowCallbacks {
    fun onPrimary(item: SearchItem, view: View)
    fun onSecondary(item: SearchItem, view: View)
    fun onContactAction(item: SearchItem, action: ContactAction, view: View)
}

enum class ContactAction { CALL, MESSAGE, WHATSAPP, WHATSAPP_BUSINESS, SIGNAL, TELEGRAM }

private class ActionSpec(val action: ContactAction, val icon: Int, val label: Int)

class ResultAdapter(
    private val context: Context,
    private val callbacks: RowCallbacks,
    private val palette: Palette = Ui.palette(context)
) : BaseAdapter() {

    private var items: List<SearchItem> = emptyList()
    var selected = 0
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var installedMessengers: Set<ContactAction> = emptySet()

    private val icons = IconSet(context, palette.foreground)
    private val mediumFace: Typeface = Typeface.create(Typeface.DEFAULT, 500, false)
    private val dp1 = Ui.dp(context, 1)
    private val dp4 = Ui.dp(context, 4)
    private val dp8 = Ui.dp(context, 8)
    private val dp12 = Ui.dp(context, 12)
    private val dp32 = Ui.dp(context, 32)
    private val dp40 = Ui.dp(context, 40)
    private val dp48 = Ui.dp(context, 48)
    private val dp56 = Ui.dp(context, 56)
    private val specs = listOf(
        ActionSpec(ContactAction.CALL, R.drawable.ic_action_call, R.string.action_call),
        ActionSpec(ContactAction.MESSAGE, R.drawable.ic_action_message, R.string.action_message),
        ActionSpec(ContactAction.WHATSAPP, R.drawable.ic_action_whatsapp, R.string.action_whatsapp),
        ActionSpec(ContactAction.WHATSAPP_BUSINESS, R.drawable.ic_action_whatsapp_business, R.string.action_whatsapp_business)
    )

    fun submit(newItems: List<SearchItem>) {
        items = newItems
        if (selected >= items.size) selected = 0
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): SearchItem? = items.getOrNull(position)

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = items[position].key.hashCode().toLong()
    override fun hasStableIds(): Boolean = true
    override fun getViewTypeCount(): Int = 1
    override fun getItemViewType(position: Int): Int = 0

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder = (convertView?.tag as? Holder) ?: Holder()
        holder.bind(items[position], position == selected)
        return holder.root
    }

    private inner class Holder {
        val root: LinearLayout = Ui.row(context)
        val slot = FrameLayout(context)
        val initials = TextView(context)
        val kindIcon = ImageView(context)
        val appIcon = ImageView(context)
        val title: TextView = Ui.text(context, "", 16f, palette.foreground)
        val subtitle: TextView = Ui.text(context, "", 13f, palette.muted)
        val actions: LinearLayout = Ui.row(context)
        val actionButtons = ArrayList<ImageView>(specs.size)
        val selectedBackground = GradientDrawable().apply {
            setColor(palette.surface)
            cornerRadius = Ui.dp(context, 10).toFloat()
        }
        val kindDrawables = SparseArray<Drawable>()
        var boundItem: SearchItem? = null

        init {
            root.setPaddingRelative(dp12, dp8, dp4, dp8)
            root.minimumHeight = dp56
            root.tag = this
            root.isFocusable = false
            root.foreground = Ui.ripple(context, palette, 10)
            root.setOnClickListener { view -> boundItem?.let { callbacks.onPrimary(it, view) } }
            root.setOnLongClickListener { view ->
                boundItem?.let { callbacks.onSecondary(it, view) }
                true
            }

            initials.gravity = Gravity.CENTER
            initials.textSize = 13f
            initials.typeface = mediumFace
            initials.setTextColor(palette.foreground)
            initials.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp1, palette.line)
            }
            kindIcon.setPadding(dp8, dp8, dp8, dp8)
            appIcon.setPadding(dp4, dp4, dp4, dp4)
            slot.addView(initials, FrameLayout.LayoutParams(dp32, dp32, Gravity.CENTER))
            slot.addView(kindIcon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            slot.addView(appIcon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            title.typeface = mediumFace
            title.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            subtitle.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            subtitle.textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
            val texts = Ui.column(context)
            texts.setPaddingRelative(dp12, 0, dp4, 0)
            texts.addView(title)
            texts.addView(subtitle)

            val actionClick = View.OnClickListener { view ->
                val item = boundItem
                if (item != null) callbacks.onContactAction(item, view.tag as ContactAction, view)
            }
            for (spec in specs) {
                val button = ImageView(context).apply {
                    setImageDrawable(icons.make(spec.icon))
                    scaleType = ImageView.ScaleType.CENTER
                    contentDescription = context.getString(spec.label)
                    background = Ui.ripple(context, palette, oval = true)
                    isClickable = true
                    tag = spec.action
                    setOnClickListener(actionClick)
                    visibility = View.GONE
                }
                actionButtons.add(button)
                actions.addView(button, LinearLayout.LayoutParams(dp48, dp48))
            }
            actions.visibility = View.GONE

            root.addView(slot, LinearLayout.LayoutParams(dp40, dp40))
            root.addView(texts, Ui.weight())
            root.addView(actions)
        }

        fun bind(item: SearchItem, isSelected: Boolean) {
            boundItem = item
            val sub = subtitleFor(item)
            title.text = item.label
            subtitle.text = sub
            Ui.gone(subtitle, sub.isEmpty())
            root.background = if (isSelected) selectedBackground else null
            bindIcon(item)
            bindActions(item)
            root.contentDescription = if (sub.isEmpty()) item.label else item.label + ", " + sub
        }

        private fun subtitleFor(item: SearchItem): String = when (item.kind) {
            ItemKind.CLIPBOARD -> if (item.subtitle == "hidden") context.getString(R.string.hidden_clip) else item.subtitle
            else -> item.subtitle
        }

        private fun bindIcon(item: SearchItem) {
            if (item.kind == ItemKind.APP) {
                if (Graph.prefs.iconMode == "none") {
                    Ui.gone(slot, true)
                    return
                }
                Ui.gone(slot, false)
                Ui.gone(kindIcon, true)
                Ui.gone(initials, true)
                Ui.gone(appIcon, false)
                val cached = Graph.icons.cached(item.key)
                appIcon.setImageBitmap(cached)
                if (cached == null) {
                    val key = item.key
                    Graph.icons.load(item.key, item.payload, palette.foreground) { bitmap ->
                        if (boundItem?.key == key) appIcon.setImageBitmap(bitmap)
                    }
                }
                return
            }
            Ui.gone(slot, false)
            Ui.gone(appIcon, true)
            val res = kindIconRes(item)
            if (res != 0) {
                Ui.gone(initials, true)
                Ui.gone(kindIcon, false)
                kindIcon.setImageDrawable(drawableFor(res))
            } else {
                Ui.gone(kindIcon, true)
                Ui.gone(initials, false)
                initials.text = initialsOf(item.label)
            }
        }

        private fun drawableFor(res: Int): Drawable {
            val existing = kindDrawables.get(res)
            if (existing != null) return existing
            val made = icons.make(res)
            kindDrawables.put(res, made)
            return made
        }

        private fun bindActions(item: SearchItem) {
            val info = item.extra as? ContactInfo
            if (item.kind != ItemKind.CONTACT || (info != null && info.numbers.isEmpty())) {
                Ui.gone(actions, true)
                return
            }
            Ui.gone(actions, false)
            val hasWa = ContactAction.WHATSAPP in installedMessengers
            val hasWab = ContactAction.WHATSAPP_BUSINESS in installedMessengers
            Ui.gone(actionButtons[0], false)
            Ui.gone(actionButtons[1], false)
            Ui.gone(actionButtons[2], !hasWa)
            Ui.gone(actionButtons[3], !(hasWab && (!hasWa || info?.whatsappBusinessDataId != null)))
        }
    }

    private fun kindIconRes(item: SearchItem): Int = when (item.kind) {
        ItemKind.SETTING -> R.drawable.ic_kind_setting
        ItemKind.FILE -> if (item.subtitle.endsWith("/")) R.drawable.ic_kind_folder else R.drawable.ic_kind_file
        ItemKind.SNIPPET -> R.drawable.ic_kind_snippet
        ItemKind.CLIPBOARD -> R.drawable.ic_kind_clipboard
        ItemKind.CALCULATOR -> R.drawable.ic_kind_calculator
        ItemKind.WEB -> R.drawable.ic_kind_web
        ItemKind.ACTION -> actionIconRes(item)
        ItemKind.CONTACT, ItemKind.APP -> 0
    }

    private fun actionIconRes(item: SearchItem): Int = when {
        item.key.startsWith("ql:") -> R.drawable.ic_kind_quicklink
        item.payload.startsWith("dial|") -> R.drawable.ic_action_call
        item.payload.startsWith("sms|") -> R.drawable.ic_action_message
        item.payload.startsWith("wa|") -> R.drawable.ic_action_whatsapp
        else -> R.drawable.ic_kind_action
    }

    private fun initialsOf(label: String): String {
        val out = StringBuilder(2)
        var i = 0
        while (i < label.length && out.length < 2) {
            while (i < label.length && label[i] == ' ') i++
            if (i < label.length) {
                out.append(label[i])
                while (i < label.length && label[i] != ' ') i++
            }
        }
        return out.toString().uppercase()
    }
}
