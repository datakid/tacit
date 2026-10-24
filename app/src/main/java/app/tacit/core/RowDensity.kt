package app.tacit.core

enum class RowDensity(
    val key: String,
    val rowDp: Int,
    val slotDp: Int,
    val iconDp: Int,
    val kindPadDp: Int,
    val initialsSp: Float,
    val titleSp: Float,
    val subtitleSp: Float,
    val inlineSubtitle: Boolean,
    val listRowDp: Int,
    val listPadDp: Int,
    val fieldPadDp: Int,
    val stripSp: Float
) {
    COMFORTABLE("comfortable", 64, 48, 40, 10, 15f, 17f, 14f, false, 56, 12, 14, 11f),
    COMPACT("compact", 48, 32, 28, 6, 12f, 15f, 12f, true, 48, 6, 10, 10f);

    companion object {
        fun of(key: String?): RowDensity = entries.firstOrNull { it.key == key } ?: COMFORTABLE
    }
}
