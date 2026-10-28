package app.tacit.core

object IconBudget {

    const val FLOOR_BYTES = 12 * 1024 * 1024
    const val CEILING_BYTES = 24 * 1024 * 1024

    fun bytes(maxMemory: Long): Int = (maxMemory / 8).coerceIn(FLOOR_BYTES.toLong(), CEILING_BYTES.toLong()).toInt()
}
