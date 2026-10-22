package app.tacit.files

object RefreshPolicy {

    const val MIN_GAP_MS = 120_000L
    const val MAX_AGE_MS = 6L * 3_600_000L
    const val FULL_SCAN_MS = 24L * 3_600_000L

    fun shouldRefresh(header: IndexHeader?, stamps: List<VolumeStamp>, now: Long, finishedAt: Long): Boolean {
        if (header == null) return finishedAt == 0L || now - finishedAt > MIN_GAP_MS
        val since = now - maxOf(header.createdAt, finishedAt)
        if (stamps.isNotEmpty()) return stampsDiffer(header.volumes, stamps) && since > MIN_GAP_MS
        return since > MAX_AGE_MS
    }

    fun stampsDiffer(stored: List<VolumeStamp>, current: List<VolumeStamp>): Boolean {
        if (stored.size != current.size) return true
        for (i in stored.indices) {
            val a = stored[i]
            val b = current[i]
            if (a.name != b.name || a.version != b.version || a.generation != b.generation) return true
        }
        return false
    }

    fun needsFullWalk(previous: IndexHeader?, now: Long): Boolean = previous == null || now - previous.verifiedAt > FULL_SCAN_MS
}
