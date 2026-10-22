package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExistenceCacheTest {

    @Test
    fun unknownKeysCountAsPresent() {
        val cache = ExistenceCache()
        assertTrue(cache.present("file:/a"))
    }

    @Test
    fun missingEntriesAreHiddenAfterARebuild() {
        val cache = ExistenceCache()
        cache.rebuild(listOf("file:/here", "file:/gone")) { it == "file:/here" }
        assertTrue(cache.present("file:/here"))
        assertFalse(cache.present("file:/gone"))
        assertTrue(cache.present("file:/never-checked"))
    }

    @Test
    fun aRebuildReplacesWhatWasKnownBefore() {
        val cache = ExistenceCache()
        cache.rebuild(listOf("file:/a")) { false }
        assertFalse(cache.present("file:/a"))
        cache.rebuild(listOf("file:/a")) { true }
        assertTrue(cache.present("file:/a"))
        cache.rebuild(emptyList()) { false }
        assertTrue(cache.present("file:/a"))
    }

    @Test
    fun theCheckRunsOncePerKeyAndNotOnRead() {
        val cache = ExistenceCache()
        var calls = 0
        cache.rebuild(listOf("file:/a", "file:/b", "file:/c")) {
            calls++
            true
        }
        assertEquals(3, calls)
        repeat(100) { cache.present("file:/a") }
        assertEquals(3, calls)
    }
}
