package app.tacit.data

import app.tacit.core.QuicklinkIcon
import app.tacit.core.QuicklinkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuicklinkStoreCsvTest {

    @Test
    fun fiveColumnFilesFromEarlierVersionsStillLoad() {
        val text = "a1,Maps,URL,https://maps.example/?q={query},\nb2,Wi-Fi,SETTING,action|android.settings.WIFI_SETTINGS,Wi-Fi\n"
        val links = QuicklinkCsv.decode(text)
        assertEquals(2, links.size)
        assertEquals(Quicklink("a1", "Maps", QuicklinkType.URL, "https://maps.example/?q={query}", ""), links[0])
        assertEquals(QuicklinkIcon.GLYPH, links[0].icon)
        assertEquals(QuicklinkIcon.GLYPH, links[1].icon)
        assertEquals("Wi-Fi", links[1].detail)
    }

    @Test
    fun sixColumnRowsKeepTheirIcon() {
        val text = "a1,Mail,APP,com.x/com.x.Main|0,Mail,app\nb2,Notes,URL,https://n.example,,letter:NO\n"
        val links = QuicklinkCsv.decode(text)
        assertEquals("app", links[0].icon)
        assertEquals("letter:NO", links[1].icon)
    }

    @Test
    fun iconsRoundTripThroughEncodeAndDecode() {
        val links = listOf(
            Quicklink("1", "Plain", QuicklinkType.URL, "https://a.example", ""),
            Quicklink("2", "App, with comma", QuicklinkType.APP, "com.a/com.a.Main|0", "App", QuicklinkIcon.APP),
            Quicklink("3", "Letters \"quoted\"", QuicklinkType.FILE, "/sdcard/x.txt", "x.txt", "letter:XY"),
            Quicklink("4", "Screen", QuicklinkType.ACTIVITY, "com.android.settings/com.android.settings.wifi.WifiSettings", "Settings · Wi-Fi", QuicklinkIcon.APP),
            Quicklink("5", "Custom", QuicklinkType.INTENT, "intent:#Intent;action=android.intent.action.VIEW;S.k=a%2Cb;end", "android.intent.action.VIEW", "letter:ع")
        )
        assertEquals(links, QuicklinkCsv.decode(QuicklinkCsv.encode(links)))
    }

    @Test
    fun encodedRowsHaveSixColumns() {
        val line = QuicklinkCsv.encode(listOf(Quicklink("1", "Plain", QuicklinkType.URL, "https://a.example", "")))
        assertEquals("1,Plain,URL,https://a.example,,glyph", line)
    }

    @Test
    fun anUnknownOrMismatchedIconBecomesTheGlyph() {
        val text = "1,A,URL,https://a.example,,app\n2,B,APP,com.a/com.a.M|0,,letter:\n3,C,APP,com.a/com.a.M|0,,sparkles\n4,D,URL,https://a.example,,letter:ABC\n"
        assertEquals(listOf("glyph", "glyph", "glyph", "glyph"), QuicklinkCsv.decode(text).map { it.icon })
    }

    @Test
    fun rowsWithTooFewColumnsOrAnUnknownTypeAreSkipped() {
        val text = "1,Short,URL,https://a.example\n2,Odd,TELEPORT,x,y\n3,Fine,URL,https://b.example,,glyph\n"
        assertEquals(listOf("3"), QuicklinkCsv.decode(text).map { it.id })
    }

    @Test
    fun theNewTypesLoadByName() {
        val text = "1,Screen,ACTIVITY,com.a/com.a.X,Screen,app\n2,Custom,INTENT,intent:#Intent;action=a.b;end,a.b,glyph\n"
        assertEquals(listOf(QuicklinkType.ACTIVITY, QuicklinkType.INTENT), QuicklinkCsv.decode(text).map { it.type })
    }

    @Test
    fun anEmptyFileGivesNoLinks() {
        assertTrue(QuicklinkCsv.decode("").isEmpty())
        assertEquals("", QuicklinkCsv.encode(emptyList()))
    }

    @Test
    fun targetsWithNewlinesSurviveTheRoundTrip() {
        val link = Quicklink("1", "Multi\nline", QuicklinkType.INTENT, "intent:#Intent;S.t=a%0Ab;end", "d\r\ne", "letter:A1")
        assertEquals(listOf(link), QuicklinkCsv.decode(QuicklinkCsv.encode(listOf(link))))
    }
}
