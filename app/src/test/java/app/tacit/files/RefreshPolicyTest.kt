package app.tacit.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {

    private val now = 1_790_000_000_000L
    private val minute = 60_000L
    private val hour = 3_600_000L

    private fun header(createdAt: Long, vararg volumes: VolumeStamp, verifiedAt: Long = createdAt) =
        IndexHeader(10, createdAt, verifiedAt, ScanStrategyIds.NIO_WALK, false, volumes.toList())

    private val stamp = VolumeStamp("external_primary", "Android-37.0", 100L)

    @Test
    fun noIndexOnDiskAndNothingScannedYetMeansScanNow() {
        assertTrue(RefreshPolicy.shouldRefresh(null, emptyList(), now, 0L))
    }

    @Test
    fun noIndexOnDiskButAScanJustFinishedWaitsForTheGap() {
        assertFalse(RefreshPolicy.shouldRefresh(null, emptyList(), now, now - 30_000L))
        assertTrue(RefreshPolicy.shouldRefresh(null, emptyList(), now, now - 3 * minute))
    }

    @Test
    fun aChangedMediaStoreGenerationTriggersARefreshAfterTheGap() {
        val stored = header(now - 10 * minute, stamp)
        val changed = listOf(VolumeStamp("external_primary", "Android-37.0", 101L))
        assertTrue(RefreshPolicy.shouldRefresh(stored, changed, now, 0L))
    }

    @Test
    fun aChangedMediaStoreVersionTriggersARefresh() {
        val stored = header(now - 10 * minute, stamp)
        val changed = listOf(VolumeStamp("external_primary", "Android-38.0", 100L))
        assertTrue(RefreshPolicy.shouldRefresh(stored, changed, now, 0L))
    }

    @Test
    fun aChangeRightAfterAScanWaitsForTheGap() {
        val stored = header(now - minute, stamp)
        val changed = listOf(VolumeStamp("external_primary", "Android-37.0", 101L))
        assertFalse(RefreshPolicy.shouldRefresh(stored, changed, now, 0L))
        assertFalse(RefreshPolicy.shouldRefresh(header(now - 10 * minute, stamp), changed, now, now - minute))
    }

    @Test
    fun unchangedStampsNeverRefreshHoweverOldTheIndexIs() {
        val stored = header(now - 48 * hour, stamp)
        assertFalse(RefreshPolicy.shouldRefresh(stored, listOf(stamp), now, 0L))
    }

    @Test
    fun aNewVolumeCountsAsAChange() {
        val stored = header(now - 10 * minute, stamp)
        val both = listOf(stamp, VolumeStamp("1a2b-3c4d", "Android-37.0", 5L))
        assertTrue(RefreshPolicy.shouldRefresh(stored, both, now, 0L))
    }

    @Test
    fun movingFromFolderGrantsToAllFilesAccessCountsAsAChange() {
        val stored = header(now - 10 * minute)
        assertTrue(RefreshPolicy.shouldRefresh(stored, listOf(stamp), now, 0L))
    }

    @Test
    fun withoutMediaStoreStampsTheIndexRefreshesAfterSixHours() {
        assertFalse(RefreshPolicy.shouldRefresh(header(now - 5 * hour), emptyList(), now, 0L))
        assertTrue(RefreshPolicy.shouldRefresh(header(now - 7 * hour), emptyList(), now, 0L))
    }

    @Test
    fun aFullWalkIsNeededOnlyOncePerDay() {
        assertTrue(RefreshPolicy.needsFullWalk(null, now))
        assertFalse(RefreshPolicy.needsFullWalk(header(now - hour, verifiedAt = now - 23 * hour), now))
        assertTrue(RefreshPolicy.needsFullWalk(header(now - hour, verifiedAt = now - 25 * hour), now))
        assertTrue(RefreshPolicy.needsFullWalk(header(now - hour, verifiedAt = 0L), now))
    }
}
