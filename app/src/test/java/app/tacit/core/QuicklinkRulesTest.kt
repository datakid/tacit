package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

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

    private val moment = LocalDateTime.of(2026, 10, 14, 9, 5)

    @Test
    fun clipboardAndDateAreFilledAndEncoded() {
        assertEquals("https://x.com/?c=a%20b%26c", QuicklinkRules.fill("https://x.com/?c={clipboard}", "", "a b&c", moment))
        assertEquals("https://x.com/2026-10-14", QuicklinkRules.fill("https://x.com/{date}", "", "", moment))
        assertEquals("https://x.com/14%2F10%2F2026", QuicklinkRules.fill("https://x.com/{date:dd/MM/yyyy}", "", "", moment))
        assertEquals("a=hi&b=hi&d=2026-10-14", QuicklinkRules.fill("a={query}&b={query}&d={date}", "hi", "", moment))
    }

    @Test
    fun unknownOrMalformedPlaceholdersStayAsTheyAre() {
        assertEquals("https://x.com/{other}", QuicklinkRules.fill("https://x.com/{other}", "q", "c", moment))
        assertEquals("https://x.com/{query:x}", QuicklinkRules.fill("https://x.com/{query:x}", "q", "c", moment))
        assertEquals("https://x.com/{clipboard:x}", QuicklinkRules.fill("https://x.com/{clipboard:x}", "q", "c", moment))
    }

    @Test
    fun badDatePatternIsReportedAndFallsBackToIso() {
        assertTrue(QuicklinkRules.placeholdersValid("https://x.com/{date:yyyy}"))
        assertTrue(!QuicklinkRules.placeholdersValid("https://x.com/{date:yyyyy-bb}"))
        assertTrue(!QuicklinkRules.placeholdersValid("https://x.com/{query:x}"))
        assertTrue(QuicklinkRules.placeholdersValid("https://x.com/{query}{clipboard}{date}"))
        assertEquals("https://x.com/2026-10-14", QuicklinkRules.fill("https://x.com/{date:yyyyy-bb}", "", "", moment))
    }

    @Test
    fun clipboardAndQueryAreDetected() {
        assertTrue(QuicklinkRules.usesClipboard("https://x.com/{clipboard}"))
        assertTrue(!QuicklinkRules.usesClipboard("https://x.com/{query}"))
        assertTrue(!QuicklinkRules.takesQuery("https://x.com/{clipboard}"))
    }

    @Test
    fun smsAndDeepLinksPass() {
        assertEquals("sms:+201001234567?body=hi", QuicklinkRules.normalizeUrl("sms:+201001234567?body=hi"))
        assertEquals("smsto:+2010", QuicklinkRules.normalizeUrl("smsto:+2010"))
        assertEquals("spotify://track/abc", QuicklinkRules.normalizeUrl("spotify://track/abc"))
        assertEquals("whatsapp://send?phone=2010", QuicklinkRules.normalizeUrl("WhatsApp://send?phone=2010"))
        assertEquals("tg://resolve?domain=x", QuicklinkRules.normalizeUrl("tg://resolve?domain=x"))
    }

    @Test
    fun deepLinksNeedTheDoubleSlashForm() {
        assertNull(QuicklinkRules.normalizeUrl("spotify:track:abc"))
        assertNull(QuicklinkRules.normalizeUrl("spotify://"))
        assertNull(QuicklinkRules.normalizeUrl("x://a"))
    }

    @Test
    fun blockedSchemesAreRejectedInEveryShape() {
        for (scheme in listOf("file", "javascript", "content", "intent", "android-app", "data", "vbscript", "about", "blob", "filesystem", "view-source")) {
            assertNull("$scheme:x", QuicklinkRules.normalizeUrl("$scheme:x"))
            assertNull("$scheme://x", QuicklinkRules.normalizeUrl("$scheme://x"))
            assertNull("$scheme:1", QuicklinkRules.normalizeUrl("$scheme:1"))
            assertNull("${scheme.uppercase()}://x", QuicklinkRules.normalizeUrl("${scheme.uppercase()}://x"))
        }
        assertNull(QuicklinkRules.normalizeUrl("javascript://%0aalert(1)"))
        assertNull(QuicklinkRules.normalizeUrl("intent://x#Intent;component=a.b/.C;end"))
    }

    @Test
    fun deepLinkIsToldApartFromTheAllowlist() {
        assertTrue(QuicklinkRules.isDeepLink("spotify://track/abc"))
        assertTrue(!QuicklinkRules.isDeepLink("https://x.com"))
        assertTrue(!QuicklinkRules.isDeepLink("tel:+2010"))
        assertTrue(!QuicklinkRules.isDeepLink("sms:+2010"))
    }

    @Test
    fun contactTargetsRoundTrip() {
        assertEquals("call|+20 100 123 4567", QuicklinkRules.contactTarget("call", " +20 100 123 4567 "))
        assertEquals("wab|0100", QuicklinkRules.contactTarget("wab", "0100"))
        val parsed = QuicklinkRules.parseContact("sms|+2010-12")
        assertEquals("sms", parsed?.action)
        assertEquals("+2010-12", parsed?.number)
    }

    @Test
    fun badContactTargetsAreRefused() {
        assertNull(QuicklinkRules.contactTarget("signal", "0100"))
        assertNull(QuicklinkRules.contactTarget("call", ""))
        assertNull(QuicklinkRules.contactTarget("call", "+-()"))
        assertNull(QuicklinkRules.contactTarget("call", "0100;rm"))
        assertNull(QuicklinkRules.contactTarget("call", "1".repeat(33)))
        assertNull(QuicklinkRules.parseContact("call"))
        assertNull(QuicklinkRules.parseContact("|0100"))
        assertNull(QuicklinkRules.parseContact("tel|0100"))
    }

    @Test
    fun grantsAreNotExportedAndNeedARepick() {
        assertTrue(QuicklinkRules.holdsGrant(QuicklinkType.FILE, "content://com.android.externalstorage.documents/document/primary%3Ax"))
        assertTrue(QuicklinkRules.holdsGrant(QuicklinkType.FOLDER, "content://com.android.externalstorage.documents/tree/primary%3Ax"))
        assertTrue(!QuicklinkRules.holdsGrant(QuicklinkType.FILE, "/storage/emulated/0/a.txt"))
        assertTrue(!QuicklinkRules.holdsGrant(QuicklinkType.URL, "content://x"))
        assertEquals("", QuicklinkRules.exportTarget(QuicklinkType.FILE, "content://a/b"))
        assertEquals("/storage/emulated/0/a.txt", QuicklinkRules.exportTarget(QuicklinkType.FILE, "/storage/emulated/0/a.txt"))
        assertTrue(QuicklinkRules.needsRepick(QuicklinkType.FOLDER, ""))
        assertTrue(!QuicklinkRules.needsRepick(QuicklinkType.FOLDER, "/storage/emulated/0/Download"))
        assertTrue(!QuicklinkRules.needsRepick(QuicklinkType.URL, ""))
    }

    @Test
    fun importKeepsOnlyTargetsThatPassTheRules() {
        assertEquals("https://x.com/?q={query}", QuicklinkRules.importTarget(QuicklinkType.URL, "https://x.com/?q={query}"))
        assertNull(QuicklinkRules.importTarget(QuicklinkType.URL, "javascript:alert(1)"))
        assertNull(QuicklinkRules.importTarget(QuicklinkType.URL, "https://x.com/{query:x}"))
        assertEquals("call|0100", QuicklinkRules.importTarget(QuicklinkType.CONTACT, "call|0100"))
        assertNull(QuicklinkRules.importTarget(QuicklinkType.CONTACT, "call|x"))
        assertEquals("", QuicklinkRules.importTarget(QuicklinkType.FILE, "content://a/b"))
        assertEquals("", QuicklinkRules.importTarget(QuicklinkType.FOLDER, ""))
        assertEquals("/storage/emulated/0/a.txt", QuicklinkRules.importTarget(QuicklinkType.FILE, "/storage/emulated/0/a.txt"))
        assertNull(QuicklinkRules.importTarget(QuicklinkType.FILE, "file:///x"))
        assertNull(QuicklinkRules.importTarget(QuicklinkType.APP, ""))
        assertEquals("a/b|0", QuicklinkRules.importTarget(QuicklinkType.APP, "a/b|0"))
    }
}
