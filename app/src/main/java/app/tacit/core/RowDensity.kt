package app.tacit.core

enum class RowDensity(
    val key: String,
    val rowDp: Int,
    val slotDp: Int,
    val iconDp: Int,
    val kindPadDp: Int,
    val initialsSp: Float,
    val titleSp: Float,
    val subtitleSp: Float
) {
    COMFORTABLE("comfortable", 64, 48, 40, 10, 15f, 17f, 14f),
    COMPACT("compact", 56, 40, 32, 8, 13f, 16f, 13f);

    companion object {
        fun of(key: String?): RowDensity = entries.firstOrNull { it.key == key } ?: COMFORTABLE
    }
}
