package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class RefreshCoalescerTest {

    private class Harness(delayMs: Long = 400L) {
        val scheduled = Collections.synchronizedList(ArrayList<Pair<Long, Runnable>>())
        val runs = Collections.synchronizedList(ArrayList<Int>())
        var onRun: (Int) -> Unit = {}
        val coalescer = RefreshCoalescer(delayMs, { delay, task -> scheduled.add(delay to task) }, { bits ->
            runs.add(bits)
            onRun(bits)
        })

        fun fire() {
            val next = synchronized(scheduled) { if (scheduled.isEmpty()) null else scheduled.removeAt(0) }
            next?.second?.run()
        }
    }

    @Test
    fun aBurstOfFiftyRequestsProducesOneRunWithTheUnionOfSources() {
        val harness = Harness()
        val order = intArrayOf(RefreshSources.APPS, RefreshSources.CONTACTS, RefreshSources.SNIPPETS)
        repeat(50) { harness.coalescer.request(order[it % order.size]) }
        assertEquals(1, harness.scheduled.size)
        assertEquals(400L, harness.scheduled[0].first)
        assertTrue(harness.runs.isEmpty())
        harness.fire()
        assertEquals(listOf(RefreshSources.APPS or RefreshSources.CONTACTS or RefreshSources.SNIPPETS), harness.runs.toList())
        assertTrue(harness.scheduled.isEmpty())
    }

    @Test
    fun aRequestAfterARunSchedulesAgainAndCarriesOnlyItsOwnSources() {
        val harness = Harness()
        harness.coalescer.request(RefreshSources.APPS)
        harness.fire()
        harness.coalescer.request(RefreshSources.QUICKLINKS)
        assertEquals(1, harness.scheduled.size)
        harness.fire()
        assertEquals(listOf(RefreshSources.APPS, RefreshSources.QUICKLINKS), harness.runs.toList())
    }

    @Test
    fun aRequestMadeDuringARunIsNotLost() {
        val harness = Harness()
        var requested = false
        harness.onRun = {
            if (!requested) {
                requested = true
                harness.coalescer.request(RefreshSources.SETTINGS)
            }
        }
        harness.coalescer.request(RefreshSources.CONTACTS)
        harness.fire()
        assertEquals(1, harness.scheduled.size)
        harness.fire()
        assertEquals(listOf(RefreshSources.CONTACTS, RefreshSources.SETTINGS), harness.runs.toList())
    }

    @Test
    fun flushRunsPendingSourcesAtOnceAndTheLateTaskDoesNothing() {
        val harness = Harness()
        harness.coalescer.request(RefreshSources.ALL)
        harness.coalescer.flush()
        assertEquals(listOf(RefreshSources.ALL), harness.runs.toList())
        harness.fire()
        assertEquals(1, harness.runs.size)
    }

    @Test
    fun flushWithNothingPendingRunsNothing() {
        val harness = Harness()
        harness.coalescer.flush()
        assertTrue(harness.runs.isEmpty())
    }

    @Test
    fun emptyRequestsAreIgnored() {
        val harness = Harness()
        harness.coalescer.request(0)
        assertTrue(harness.scheduled.isEmpty())
    }

    @Test
    fun appsAlwaysBringSettingsWithThem() {
        assertEquals(RefreshSources.APPS or RefreshSources.SETTINGS, RefreshSources.expand(RefreshSources.APPS))
        assertEquals(RefreshSources.CONTACTS, RefreshSources.expand(RefreshSources.CONTACTS))
        assertEquals(RefreshSources.ALL, RefreshSources.expand(RefreshSources.ALL))
    }

    @Test
    fun theMaskCoversExactlyTheFiveSources() {
        val all = RefreshSources.APPS or RefreshSources.CONTACTS or RefreshSources.SETTINGS or RefreshSources.SNIPPETS or RefreshSources.QUICKLINKS
        assertEquals(RefreshSources.ALL, all)
        assertEquals(5, Integer.bitCount(all))
    }

    @Test
    fun concurrentRequestsLoseNoSources() {
        val harness = Harness()
        val bits = intArrayOf(RefreshSources.APPS, RefreshSources.CONTACTS, RefreshSources.SETTINGS, RefreshSources.SNIPPETS, RefreshSources.QUICKLINKS)
        val threads = bits.map { bit ->
            Thread { repeat(2_000) { harness.coalescer.request(bit) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        while (harness.scheduled.isNotEmpty()) harness.fire()
        var union = 0
        for (run in harness.runs.toList()) union = union or run
        assertEquals(RefreshSources.ALL, union)
        assertTrue(harness.runs.size >= 1)
    }
}
