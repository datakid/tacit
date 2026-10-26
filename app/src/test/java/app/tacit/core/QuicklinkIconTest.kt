package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuicklinkIconTest {

    @Test
    fun glyphIsKeptForEveryType() {
        for (type in QuicklinkType.entries) assertEquals(QuicklinkIcon.GLYPH, QuicklinkIcon.normalize(type, "glyph"))
    }

    @Test
    fun theAppIconIsOnlyKeptForTypesThatHaveAnApp() {
        val withApp = setOf(QuicklinkType.APP, QuicklinkType.APP_SHORTCUT, QuicklinkType.ACTIVITY)
        for (type in QuicklinkType.entries) {
            assertEquals(type in withApp, QuicklinkIcon.supportsApp(type))
            assertEquals(if (type in withApp) "app" else "glyph", QuicklinkIcon.normalize(type, "app"))
        }
    }

    @Test
    fun letterIconsMustBeOneOrTwoLettersOrDigits() {
        assertEquals("letter:AB", QuicklinkIcon.normalize(QuicklinkType.URL, "letter:AB"))
        assertEquals("letter:7", QuicklinkIcon.normalize(QuicklinkType.URL, "letter:7"))
        assertEquals("letter:عم", QuicklinkIcon.normalize(QuicklinkType.URL, "letter:عم"))
        for (bad in listOf("letter:", "letter:ABC", "letter:A-", "letter: A", "letter:A B", "Letter:AB", "", "x")) {
            assertEquals(bad, "glyph", QuicklinkIcon.normalize(QuicklinkType.URL, bad))
        }
    }

    @Test
    fun lettersAreTakenFromTheFirstTwoLettersAndUppercased() {
        assertEquals("AB", QuicklinkIcon.letters("abc"))
        assertEquals("X", QuicklinkIcon.letters("x"))
        assertEquals("AB", QuicklinkIcon.letters("  a-b-c "))
        assertEquals("", QuicklinkIcon.letters("--"))
        assertEquals("عم", QuicklinkIcon.letters("عمر"))
        assertEquals("12", QuicklinkIcon.letters("123"))
    }

    @Test
    fun theLetterValueHasThePrefix() {
        assertEquals("letter:AB", QuicklinkIcon.letter("ab"))
        assertNull(QuicklinkIcon.letter("   "))
        assertNull(QuicklinkIcon.letter("!!"))
    }

    @Test
    fun lettersOfReadsOnlyLetterValues() {
        assertEquals("AB", QuicklinkIcon.lettersOf("letter:AB"))
        assertNull(QuicklinkIcon.lettersOf("app"))
        assertNull(QuicklinkIcon.lettersOf("letter:ABC"))
    }

    @Test
    fun suggestionsUseTheInitialsOfTheFirstTwoWords() {
        assertEquals("MA", QuicklinkIcon.suggestedLetters("Maps"))
        assertEquals("WS", QuicklinkIcon.suggestedLetters("Wi Settings"))
        assertEquals("WF", QuicklinkIcon.suggestedLetters("Wi-Fi Settings"))
        assertEquals("CT", QuicklinkIcon.suggestedLetters("Call Tarek"))
        assertEquals("", QuicklinkIcon.suggestedLetters(""))
        assertEquals("", QuicklinkIcon.suggestedLetters("   --  "))
        assertEquals("عم", QuicklinkIcon.suggestedLetters("عمر"))
        assertEquals("عا", QuicklinkIcon.suggestedLetters("عمر ابن"))
    }

    @Test
    fun thePackageComesFromTheTargetOfAppLikeTypes() {
        assertEquals("com.x", QuicklinkIcon.packageOf(QuicklinkType.APP, "com.x/com.x.Main|0"))
        assertEquals("com.x", QuicklinkIcon.packageOf(QuicklinkType.ACTIVITY, "com.x/com.x.Other"))
        assertEquals("com.x", QuicklinkIcon.packageOf(QuicklinkType.APP_SHORTCUT, "com.x|id|0"))
        assertNull(QuicklinkIcon.packageOf(QuicklinkType.APP, ""))
        assertNull(QuicklinkIcon.packageOf(QuicklinkType.APP, "nothing"))
        assertNull(QuicklinkIcon.packageOf(QuicklinkType.APP_SHORTCUT, "nothing"))
        assertNull(QuicklinkIcon.packageOf(QuicklinkType.URL, "https://a.example/b"))
        assertNull(QuicklinkIcon.packageOf(QuicklinkType.INTENT, "intent:#Intent;end"))
        assertNull(QuicklinkIcon.packageOf(QuicklinkType.FILE, "/a/b"))
        assertEquals("a", QuicklinkIcon.packageOf(QuicklinkType.ACTIVITY, "a/b"))
    }
}
