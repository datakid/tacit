package app.tacit.core

import app.tacit.fixtures.FileFixtures
import app.tacit.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer as JavaNormalizer
import java.util.Random

private object ReferenceNormalizer {

    private val combiningMarks = Regex("\\p{Mn}+")

    fun normalize(input: String): String {
        val builder = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> builder.append('ا')
                'ة' -> builder.append('ه')
                'ى' -> builder.append('ي')
                'ؤ' -> builder.append('و')
                'ئ' -> builder.append('ي')
                'ـ' -> Unit
                in 'ً'..'ٟ', 'ٰ' -> Unit
                in '٠'..'٩' -> builder.append('0' + (ch - '٠'))
                in '۰'..'۹' -> builder.append('0' + (ch - '۰'))
                else -> builder.append(ch)
            }
        }
        val decomposed = JavaNormalizer.normalize(builder, JavaNormalizer.Form.NFD)
        return combiningMarks.replace(decomposed, "").lowercase()
    }

    fun splitCamel(raw: String): String =
        raw.replace(Regex("(?<=\\p{Ll})(?=\\p{Lu})"), " ")
}

class NormalizerEquivalenceTest {

    private val mixedPool: List<String> = listOf(
        "a", "b", "Z", "Q", "x", "y", "e", "o", "0", "7", "9", " ", " ", "-", "_", ".", "/", "+", "(", ")", "'", "&",
        "أ", "إ", "آ", "ٱ", "ة", "ى", "ؤ", "ئ", "ـ", "ا", "ب", "م", "ح", "د", "ي", "و", "ر", "س",
        "ً", "َ", "ّ", "ٟ", "ٰ",
        "٠", "٣", "٩", "۰", "۴", "۹",
        "é", "è", "ï", "ñ", "ç", "ü", "ö", "å", "É", "À", "ß", "İ", "ı", "Σ", "ς",
        "́", "̈", "̧",
        "中", "日", "😀", "\uD83D"
    )

    private fun randomAscii(random: Random): String {
        val length = random.nextInt(25)
        val builder = StringBuilder(length)
        repeat(length) { builder.append(random.nextInt(128).toChar()) }
        return builder.toString()
    }

    private fun randomMixed(random: Random): String {
        val length = random.nextInt(25)
        val builder = StringBuilder()
        repeat(length) { builder.append(mixedPool[random.nextInt(mixedPool.size)]) }
        return builder.toString()
    }

    private fun randomStrings(): List<String> {
        val random = Random(7L)
        val result = ArrayList<String>(10_000)
        repeat(5_000) { result.add(randomAscii(random)) }
        repeat(5_000) { result.add(randomMixed(random)) }
        return result
    }

    private fun fixtureNames(): List<String> {
        val names = ArrayList<String>()
        for (item in Fixtures.all()) {
            names.add(item.label)
            names.addAll(item.extraNames)
        }
        names.addAll(Fixtures.namedContacts)
        names.addAll(Fixtures.namedApps)
        names.addAll(Fixtures.typingScript)
        names.addAll(FileFixtures.englishWords)
        names.addAll(FileFixtures.arabicWords)
        names.addAll(FileFixtures.extensions)
        val table = FileFixtures.table(FileFixtures.NOW, 5_000)
        names.addAll(table.names)
        names.addAll(table.directories)
        return names
    }

    private fun assertEquivalent(input: String) {
        assertEquals("normalize differs for [" + input.map { it.code.toString(16) } + "]", ReferenceNormalizer.normalize(input), Normalizer.normalize(input))
        assertEquals("splitCamel differs for [" + input.map { it.code.toString(16) } + "]", ReferenceNormalizer.splitCamel(input), Normalizer.splitCamel(input))
        val composed = Normalizer.normalize(Normalizer.splitCamel(input))
        assertEquals(ReferenceNormalizer.normalize(ReferenceNormalizer.splitCamel(input)), composed)
    }

    @Test
    fun everyFixtureNameNormalizesIdentically() {
        val names = fixtureNames()
        assertTrue("fixtures are empty", names.size > 5_000)
        for (name in names) assertEquivalent(name)
    }

    @Test
    fun tenThousandRandomStringsNormalizeIdentically() {
        val strings = randomStrings()
        assertEquals(10_000, strings.size)
        for (text in strings) assertEquivalent(text)
    }

    @Test
    fun camelCaseNamesStillSplit() {
        assertEquals("You Tube", Normalizer.splitCamel("YouTube"))
        assertEquals("i Phone", Normalizer.splitCamel("iPhone"))
        assertEquals("whats app", Normalizer.normalize(Normalizer.splitCamel("WhatsApp")))
        assertEquals("ABC", Normalizer.splitCamel("ABC"))
    }

    @Test
    fun asciiInputOnlyLowercases() {
        assertEquals("hello world 42", Normalizer.normalize("Hello World 42"))
        assertEquals("", Normalizer.normalize(""))
    }

    @Test
    fun nonAsciiInputStillTakesTheFullPath() {
        assertEquals("istanbul", Normalizer.normalize("İstanbul"))
        assertEquals("resume", Normalizer.normalize("Résumé"))
        assertEquals("اسماء", Normalizer.normalize("أسماء"))
        assertEquals("123", Normalizer.normalize("١٢٣"))
    }
}
