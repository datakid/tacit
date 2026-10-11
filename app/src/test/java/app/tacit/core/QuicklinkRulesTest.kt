package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuicklinkRulesTest {

    @Test
    fun bareHostGetsHttps() {
        assertEquals("https://example.com", QuicklinkRules.normalizeUrl("example.com"))
        assertEquals("https://example.com/a?b=c:d", QuicklinkRules.normalizeUrl("example.com/a?b=c:d"))
    }

    @Test
    fun hostWithPortGetsHttps() {
        assertEquals("https://localhost:8080/x", QuicklinkRules.normalizeUrl("localhost:8080/x"))
        assertEquals("https://example.com:8080", QuicklinkRules.normalizeUrl("example.com:8080"))
    }

    @Test
    fun allowedSchemesPass() {
        assertEquals("https://maps.google.com/?q={query}", QuicklinkRules.normalizeUrl("https://maps.google.com/?q={query}"))
        assertEquals("http://Example.com/x", QuicklinkRules.normalizeUrl("HTTP://Example.com/x"))
        assertEquals("tel:+201001234567", QuicklinkRules.normalizeUrl("tel:+201001234567"))
        assertEquals("mailto:a@b.com", QuicklinkRules.normalizeUrl("mailto:a@b.com"))
        assertEquals("geo:30.04,31.23?q=cairo", QuicklinkRules.normalizeUrl("geo:30.04,31.23?q=cairo"))
    }

    @Test
    fun dangerousOrBrokenInputIsRejected() {
        assertNull(QuicklinkRules.normalizeUrl("javascript:alert(1)"))
        assertNull(QuicklinkRules.normalizeUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(QuicklinkRules.normalizeUrl("file:///sdcard/x"))
        assertNull(QuicklinkRules.normalizeUrl("content://media/external/file/1"))
        assertNull(QuicklinkRules.normalizeUrl("tel:"))
        assertNull(QuicklinkRules.normalizeUrl("https://"))
        assertNull(QuicklinkRules.normalizeUrl("https:example.com"))
        assertNull(QuicklinkRules.normalizeUrl("exa mple.com"))
        assertNull(QuicklinkRules.normalizeUrl(""))
        assertNull(QuicklinkRules.normalizeUrl("   "))
    }

    @Test
    fun fillEncodesTheQuery() {
        val template = "https://x.com/?q={query}"
        assertEquals("https://x.com/?q=cairo%20city", QuicklinkRules.fill(template, "cairo city"))
        assertEquals("https://x.com/?q=a%26b%3Dc", QuicklinkRules.fill(template, "a&b=c"))
        assertEquals("https://x.com/?q=%D9%82%D8%A7%D9%87%D8%B1%D8%A9", QuicklinkRules.fill(template, "قاهرة"))
        assertEquals("https://x.com/?q=", QuicklinkRules.fill(template, ""))
    }

    @Test
    fun filledQueryCannotChangeTheScheme() {
        val filled = QuicklinkRules.fill("{query}", "javascript:alert(1)")
        assertEquals("javascript%3Aalert%281%29", filled)
        assertEquals("https://javascript%3Aalert%281%29", QuicklinkRules.normalizeUrl(filled))
    }

    @Test
    fun takesQueryOnlyWithPlaceholder() {
        assertTrue(QuicklinkRules.takesQuery("https://x.com/?q={query}"))
        assertTrue(!QuicklinkRules.takesQuery("https://x.com/"))
    }

    @Test
    fun hostIsShownWithoutWww() {
        assertEquals("maps.google.com", QuicklinkRules.host("https://www.maps.google.com/?q={query}"))
        assertEquals("example.com", QuicklinkRules.host("https://example.com:8080/x"))
        assertEquals("+20100", QuicklinkRules.host("tel:+20100"))
    }

    private val candidates = listOf(
        QuicklinkNames("1", listOf("maps", "khareeta")),
        QuicklinkNames("2", listOf("google maps")),
        QuicklinkNames("3", listOf("g")),
        QuicklinkNames("4", listOf("g maps")),
        QuicklinkNames("5", listOf("خريطة"))
    )

    @Test
    fun nameThenTextMatches() {
        val match = QuicklinkRules.match("maps cairo city", candidates)
        assertEquals("1", match?.id)
        assertEquals("cairo city", match?.query)
        assertEquals("1", QuicklinkRules.match("khareeta tahrir", candidates)?.id)
    }

    @Test
    fun nameAloneDoesNotMatch() {
        assertNull(QuicklinkRules.match("maps", candidates))
        assertNull(QuicklinkRules.match("  maps  ", candidates))
    }

    @Test
    fun multiWordNameMatches() {
        val match = QuicklinkRules.match("google maps tahrir square", candidates)
        assertEquals("2", match?.id)
        assertEquals("tahrir square", match?.query)
    }

    @Test
    fun longestNameWins() {
        val match = QuicklinkRules.match("g maps tahrir", candidates)
        assertEquals("4", match?.id)
        assertEquals("tahrir", match?.query)
        assertEquals("3", QuicklinkRules.match("g tahrir", candidates)?.id)
    }

    @Test
    fun caseAndArabicSpellingAreIgnored() {
        assertEquals("1", QuicklinkRules.match("MAPS Cairo", candidates)?.id)
        val match = QuicklinkRules.match("خريطه القاهرة", candidates)
        assertEquals("5", match?.id)
        assertEquals("القاهرة", match?.query)
    }

    @Test
    fun otherWordsDoNotMatch() {
        assertNull(QuicklinkRules.match("mapsx cairo", candidates))
        assertNull(QuicklinkRules.match("cairo maps", candidates))
    }
}
