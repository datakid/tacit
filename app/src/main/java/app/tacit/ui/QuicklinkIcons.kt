package app.tacit.ui

import app.tacit.R
import app.tacit.core.QuicklinkType

object QuicklinkIcons {

    fun res(type: QuicklinkType?): Int = when (type) {
        QuicklinkType.FILE -> R.drawable.ic_kind_file
        QuicklinkType.FOLDER -> R.drawable.ic_kind_folder
        QuicklinkType.SETTING -> R.drawable.ic_kind_setting
        QuicklinkType.APP, QuicklinkType.APP_SHORTCUT -> R.drawable.ic_kind_action
        QuicklinkType.CONTACT -> R.drawable.ic_action_call
        QuicklinkType.URL, null -> R.drawable.ic_kind_quicklink
    }
}
