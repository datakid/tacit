package app.tacit.core

import app.tacit.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Random

class QuickActionsTest {

    private val now = LocalDateTime.of(2026, 10, 15, 10, 0)

    private fun run(text: String, seed: Long = 1L): QuickResult? =
        QuickActions.evaluate(text, { now }, { Random(seed) })

    private fun timer(text: String): QuickResult.Timer? = run(text) as? QuickResult.Timer

    private fun alarm(text: String): QuickResult.Alarm? = run(text) as? QuickResult.Alarm

    private fun event(text: String): QuickResult.Event = run(text) as QuickResult.Event

    private fun value(text: String, seed: Long = 1L): QuickResult.Value? = run(text, seed) as? QuickResult.Value

    @Test
    fun timerReadsDurationsAndLabels() {
        assertEquals(600, timer("timer 10 tea")!!.seconds)
        assertEquals("tea", timer("timer 10 tea")!!.label)
        assertEquals(90, timer("timer 90s")!!.seconds)
        assertEquals(1500, timer("timer 25")!!.seconds)
        assertEquals(7200, timer("timer 2 hours")!!.seconds)
        assertEquals(300, timer("timer 5 min")!!.seconds)
        assertEquals(180, timer("TIMER 3")!!.seconds)
    }

    @Test
    fun timerCombinesPiecesInDescendingUnits() {
        assertEquals(5400, timer("timer 1h30")!!.seconds)
        assertEquals(5400, timer("timer 1h30m")!!.seconds)
        assertEquals(5400, timer("timer 1h 30m soup")!!.seconds)
        assertEquals("soup", timer("timer 1h 30m soup")!!.label)
        assertEquals(5400, timer("timer 1.5h")!!.seconds)
        assertEquals(330, timer("timer 5m 30")!!.seconds)
        assertEquals(330, timer("timer 5,5 m")!!.seconds)
    }

    @Test
    fun timerKeepsExtraNumbersInTheLabel() {
        val result = timer("timer 10 30 tea")!!
        assertEquals(600, result.seconds)
        assertEquals("30 tea", result.label)
        assertEquals("mint tea", timer("timer 5 mint tea")!!.label)
        assertEquals("tea", timer("timer 5 m tea")!!.label)
    }

    @Test
    fun timerRejectsNonsenseAndOutOfRange() {
        assertNull(run("timer"))
        assertNull(run("timer tea"))
        assertNull(run("timer 0"))
        assertNull(run("timer 25h"))
        assertNull(run("timer 10tea"))
        assertNull(run("timer 1:30"))
        assertEquals(86400, timer("timer 24h")!!.seconds)
    }

    @Test
    fun timerUnderstandsArabic() {
        val result = timer("مؤقت ١٠ دقائق شاي")!!
        assertEquals(600, result.seconds)
        assertEquals("شاي", result.label)
        assertEquals(7200, timer("مؤقت 2 ساعة")!!.seconds)
        assertEquals(45, timer("مؤقت 45 ثانية")!!.seconds)
    }

    @Test
    fun alarmReadsTwentyFourAndTwelveHourForms() {
        assertEquals(7 to 0, alarm("alarm 7")!!.let { it.hour to it.minute })
        assertEquals(7 to 30, alarm("alarm 7:30 wake up")!!.let { it.hour to it.minute })
        assertEquals("wake up", alarm("alarm 7:30 wake up")!!.label)
        assertEquals(19 to 30, alarm("alarm 7:30 pm")!!.let { it.hour to it.minute })
        assertEquals(19 to 0, alarm("alarm 7pm")!!.let { it.hour to it.minute })
        assertEquals(0 to 0, alarm("alarm 12 am")!!.let { it.hour to it.minute })
        assertEquals(12 to 0, alarm("alarm 12 pm")!!.let { it.hour to it.minute })
        assertEquals(23 to 59, alarm("alarm 23:59")!!.let { it.hour to it.minute })
    }

    @Test
    fun alarmRejectsInvalidTimes() {
        assertNull(run("alarm"))
        assertNull(run("alarm tea"))
        assertNull(run("alarm 24"))
        assertNull(run("alarm 7:60"))
        assertNull(run("alarm 13 pm"))
        assertNull(run("alarm 0 am"))
        assertNull(run("alarm 7xyz"))
    }

    @Test
    fun alarmUnderstandsArabicMeridiem() {
        assertEquals(19, alarm("منبه 7 م")!!.hour)
        assertEquals(7, alarm("منبه 7 ص")!!.hour)
        assertEquals(6, alarm("منبه ٦:٣٠")!!.hour)
        assertEquals(30, alarm("منبه ٦:٣٠")!!.minute)
    }

    @Test
    fun eventWithoutDateOrTimeKeepsOnlyTheTitle() {
        val result = event("event dentist")
        assertEquals("dentist", result.title)
        assertNull(result.date)
        assertNull(result.time)
        assertEquals("", event("event").title)
    }

    @Test
    fun eventTimeAloneMeansTodayOrTomorrowIfPassed() {
        val later = event("event dentist 14:30")
        assertEquals(LocalDate.of(2026, 10, 15), later.date)
        assertEquals(LocalTime.of(14, 30), later.time)
        assertEquals("dentist", later.title)
        val earlier = event("event dentist 9:30")
        assertEquals(LocalDate.of(2026, 10, 16), earlier.date)
        assertEquals(LocalTime.of(9, 30), earlier.time)
    }

    @Test
    fun eventDayAloneIsAllDay() {
        val result = event("event dentist tomorrow")
        assertEquals(LocalDate.of(2026, 10, 16), result.date)
        assertNull(result.time)
        assertEquals("dentist", result.title)
    }

    @Test
    fun eventDateAndTimeInEitherOrder() {
        val expected = LocalDate.of(2026, 10, 16) to LocalTime.of(15, 0)
        for (text in listOf("event dentist tomorrow 3pm", "event dentist 3pm tomorrow", "event dentist tomorrow at 3 pm", "event dentist tomorrow at 15:00")) {
            val result = event(text)
            assertEquals(text, expected, result.date to result.time)
            assertEquals(text, "dentist", result.title)
        }
    }

    @Test
    fun eventWeekdaysLookForwardAndNeverMeanToday() {
        assertEquals(LocalDate.of(2026, 10, 16), event("event lunch friday 1pm").date)
        assertEquals(LocalTime.of(13, 0), event("event lunch friday 1pm").time)
        assertEquals(LocalDate.of(2026, 10, 22), event("event gym thursday").date)
        assertEquals(LocalDate.of(2026, 10, 19), event("event gym mon").date)
    }

    @Test
    fun eventExplicitDates() {
        assertEquals(LocalDate.of(2026, 12, 24), event("event trip 2026-12-24").date)
        assertEquals(LocalDate.of(2026, 12, 25), event("event party 25/12").date)
        assertEquals(LocalDate.of(2027, 1, 1), event("event old 1/1").date)
        assertEquals(LocalDate.of(2028, 3, 4), event("event far 4/3/2028").date)
    }

    @Test
    fun eventInvalidDatesStayInTheTitle() {
        val result = event("event bad 31/02")
        assertNull(result.date)
        assertEquals("bad 31/02", result.title)
        assertEquals("meeting 5", event("event meeting 5").title)
        assertNull(event("event meeting 5").time)
        assertEquals("x 2026-13-40", event("event x 2026-13-40").title)
    }

    @Test
    fun eventUnderstandsArabic() {
        val result = event("موعد طبيب غدا 5 م")
        assertEquals("طبيب", result.title)
        assertEquals(LocalDate.of(2026, 10, 16), result.date)
        assertEquals(LocalTime.of(17, 0), result.time)
    }

    @Test
    fun uuidIsVersion4AndRepeatableForASeed() {
        val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        for (seed in 1L..50L) {
            val result = value("uuid", seed)!!
            assertEquals(ValueKind.UUID, result.kind)
            assertTrue(result.text, pattern.matches(result.text))
        }
        assertEquals(value("uuid", 7L)!!.text, value("uuid", 7L)!!.text)
        assertNotEquals(value("uuid", 7L)!!.text, value("uuid", 8L)!!.text)
        assertNull(run("uuid now"))
    }

    @Test
    fun passwordHasLengthAndEveryClass() {
        for (seed in 1L..50L) {
            val result = value("pass", seed)!!
            assertEquals(ValueKind.PASSWORD, result.kind)
            assertEquals(16, result.text.length)
            assertTrue(result.text.any { it.isLowerCase() })
            assertTrue(result.text.any { it.isUpperCase() })
            assertTrue(result.text.any { it.isDigit() })
            assertTrue(result.text.any { !it.isLetterOrDigit() })
        }
        assertEquals(24, value("pass 24")!!.text.length)
        assertEquals("24", value("pass 24")!!.spec)
        assertEquals(4, value("password 4")!!.text.length)
        assertEquals(128, value("pass 128")!!.text.length)
        assertEquals(value("pass 20", 3L)!!.text, value("pass 20", 3L)!!.text)
    }

    @Test
    fun passwordRejectsBadLengths() {
        assertNull(run("pass 3"))
        assertNull(run("pass 129"))
        assertNull(run("pass x"))
        assertNull(run("pass 12 13"))
    }

    @Test
    fun diceRollsWithinTheRequestedRange() {
        for (seed in 1L..200L) {
            val single = value("roll", seed)!!
            assertTrue(single.text.toInt() in 1..6)
            assertEquals("d6", single.spec)
            val pair = value("roll 2d6", seed)!!
            val total = pair.text.toInt()
            assertTrue(total in 2..12)
            assertTrue(pair.spec.startsWith("2d6: "))
            assertEquals(total, pair.spec.removePrefix("2d6: ").split(" + ").sumOf { it.toInt() })
        }
        assertEquals("d20", value("dice d20")!!.spec)
        assertEquals("d20", value("roll 20")!!.spec)
        assertTrue(value("roll 3 d 8")!!.spec.startsWith("3d8: "))
    }

    @Test
    fun diceRejectsLimits() {
        assertNull(run("roll 0d6"))
        assertNull(run("roll 101d6"))
        assertNull(run("roll 1d1"))
        assertNull(run("roll 1d1001"))
        assertNull(run("roll abc"))
        assertNotNull(run("roll 100d1000"))
    }

    @Test
    fun coinGivesBothOutcomes() {
        val random = Random(5L)
        val outcomes = (1..60).map { (QuickActions.evaluate("flip", { now }, { random }) as QuickResult.Coin).heads }.toSet()
        assertEquals(setOf(true, false), outcomes)
        assertTrue(run("coin") is QuickResult.Coin)
        assertNull(run("coin toss"))
    }

    @Test
    fun randomStaysInsideTheRange() {
        for (seed in 1L..200L) {
            assertTrue(value("rand", seed)!!.text.toInt() in 1..100)
            assertTrue(value("rand 10", seed)!!.text.toInt() in 1..10)
            assertTrue(value("rand 5 1", seed)!!.text.toInt() in 1..5)
            assertTrue(value("rand -3 3", seed)!!.text.toInt() in -3..3)
        }
        assertEquals("7", value("rand 7 7")!!.text)
        assertEquals("1–10", value("rand 10")!!.spec)
        assertTrue(value("random ١ ٣")!!.text.toInt() in 1..3)
    }

    @Test
    fun randomRejectsBadInput() {
        assertNull(run("rand 1 2 3"))
        assertNull(run("rand x"))
        assertNull(run("rand 1 5000000000"))
        assertNull(run("rand 99999999999999999999"))
    }

    @Test
    fun caseTransforms() {
        assertEquals("HELLO WORLD", value("upper hello world")!!.text)
        assertEquals(Transform.UPPER, value("upper hello")!!.transform)
        assertEquals(ValueKind.TRANSFORM, value("upper hello")!!.kind)
        assertEquals("hello", value("LOWER HeLLo")!!.text)
        assertEquals("Hello World", value("title hello wORLD")!!.text)
        assertEquals("Don't Stop", value("title don't STOP")!!.text)
        assertEquals("cba", value("reverse abc")!!.text)
        assertEquals("cba", value("rev abc")!!.text)
        assertEquals("😀a", value("rev a😀")!!.text)
    }

    @Test
    fun slugDropsAccentsAndPunctuation() {
        assertEquals("hello-world-2026", value("slug Hello, Wörld! 2026")!!.text)
        assertEquals("cafe-creme", value("slug Café   Crème")!!.text)
    }

    @Test
    fun base64RoundTripsIncludingArabic() {
        assertEquals("aGVsbG8=", value("b64 hello")!!.text)
        assertEquals("hello", value("unb64 aGVsbG8=")!!.text)
        val encoded = QuickActions.transform(Transform.BASE64, "مرحبا بالعالم")!!
        assertEquals("مرحبا بالعالم", QuickActions.transform(Transform.UNBASE64, encoded))
        assertEquals("hello", value("unb64 aGVsbG8")?.text ?: "hello")
    }

    @Test
    fun base64RejectsInvalidInput() {
        assertNull(run("unb64 !!!"))
        assertNull(run("unb64 /w=="))
    }

    @Test
    fun urlEncodingUsesUtf8AndPercentTwenty() {
        assertEquals("a%20b%26c", value("urlenc a b&c")!!.text)
        assertEquals("%D9%85%D8%B1%D8%AD%D8%A8%D8%A7", value("urlenc مرحبا")!!.text)
        assertEquals("a b c", value("urldec a%20b+c")!!.text)
        assertEquals("مرحبا", value("urldec %D9%85%D8%B1%D8%AD%D8%A8%D8%A7")!!.text)
        val original = "q=tea & coffee/ö?"
        assertEquals(original, QuickActions.transform(Transform.URLDEC, QuickActions.transform(Transform.URLENC, original)!!))
    }

    @Test
    fun urlDecodingRejectsBrokenInput() {
        assertNull(run("urldec %E0%A4%A"))
        assertNull(run("urldec %ZZ"))
        assertNull(run("urldec %FF"))
        assertNull(run("urldec 100%"))
        assertNull(run("urldec 50%2"))
    }

    @Test
    fun aKeywordAloneAsksForTheClipboard() {
        for (transform in Transform.values()) {
            val result = run(transform.key) as QuickResult.ClipboardTransform
            assertEquals(transform, result.transform)
        }
        assertEquals(Transform.REVERSE, (run("rev ") as QuickResult.ClipboardTransform).transform)
        assertEquals(Transform.URLENC, (run("urlencode") as QuickResult.ClipboardTransform).transform)
        assertEquals(Transform.BASE64, (run("base64") as QuickResult.ClipboardTransform).transform)
    }

    @Test
    fun exportOffersEveryFormatOrTheFilteredOnes() {
        assertEquals(ExportKind.values().toList(), (run("export") as QuickResult.Export).kinds)
        assertEquals(listOf(ExportKind.CSV), (run("export c") as QuickResult.Export).kinds)
        assertEquals(listOf(ExportKind.VCARD), (run("export vcard") as QuickResult.Export).kinds)
        assertNull(run("export x"))
        assertEquals(3, (run("تصدير") as QuickResult.Export).kinds.size)
    }

    @Test
    fun ordinarySearchTextProducesNothing() {
        val texts = listOf(
            "", " ", "hello", "timers 5", "calculator", "g", "uuidx", "passwords", "a very long first word that is not a keyword",
            "٣ + ٤", "2+2", "01001234567", "https://example.com", "me@example.com", "محمد", "ﻣﺮﺣﺒﺎ"
        )
        for (text in texts) assertNull(text, run(text))
        for (query in Fixtures.typingScript) assertNull(query, run(query))
    }

    @Test
    fun lookupCostIsNegligibleOnTheKeystrokePath() {
        val script = Fixtures.typingScript
        val rounds = 20000
        repeat(2000) { for (q in script) run(q) }
        val start = System.nanoTime()
        repeat(rounds) { for (q in script) run(q) }
        val perCallMicros = (System.nanoTime() - start) / 1000.0 / (rounds.toDouble() * script.size)
        println("TACIT_QUICKACTIONS_JVM per-keystroke lookup=${"%.3f".format(perCallMicros)}us")
        assertTrue("lookup took $perCallMicros us", perCallMicros < 20.0)
    }

    @Test
    fun randomSupplierIsOnlyUsedWhenNeeded() {
        var created = 0
        val supplier = { created++; Random(1L) }
        QuickActions.evaluate("timer 5", { now }, supplier)
        QuickActions.evaluate("upper hello", { now }, supplier)
        QuickActions.evaluate("hello", { now }, supplier)
        assertEquals(0, created)
        QuickActions.evaluate("flip", { now }, supplier)
        assertEquals(1, created)
        assertFalse(created == 0)
    }
}
