package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class ContactFormatsTest {

    private fun number(value: String, label: String = "Mobile", type: String? = "CELL") = ExportNumber(value, label, type)

    private fun contact(name: String, vararg numbers: ExportNumber) = ExportContact(name, numbers.toList())

    private fun export(kind: ExportKind, vararg contacts: ExportContact): Pair<String, ExportCount> {
        val out = StringWriter()
        val count = ContactFormats.write(kind, contacts.asSequence(), out)
        return out.toString() to count
    }

    private fun unescape(value: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '\\' && index + 1 < value.length) {
                val next = value[index + 1]
                builder.append(if (next == 'n') '\n' else next)
                index += 2
            } else {
                builder.append(ch)
                index++
            }
        }
        return builder.toString()
    }

    private fun parseVcards(text: String): List<ExportContact> {
        val result = ArrayList<ExportContact>()
        var name = ""
        var numbers = ArrayList<ExportNumber>()
        for (line in text.replace("\r\n ", "").split("\r\n")) {
            when {
                line == "BEGIN:VCARD" -> {
                    name = ""
                    numbers = ArrayList()
                }
                line.startsWith("FN:") -> name = unescape(line.removePrefix("FN:"))
                line.startsWith("TEL") -> numbers.add(ExportNumber(line.substringAfter(':'), "", null))
                line == "END:VCARD" -> result.add(ExportContact(name, numbers))
            }
        }
        return result
    }

    @Test
    fun normalizeKeepsPlusAndStripsPunctuation() {
        assertEquals("+971501234567", ContactFormats.normalizeNumber("+971 50 123 4567"))
        assertEquals("5551234567", ContactFormats.normalizeNumber("(555) 123-4567"))
        assertEquals("01001234567", ContactFormats.normalizeNumber(" 0100 123 4567 "))
    }

    @Test
    fun normalizeConvertsArabicAndPersianDigits() {
        assertEquals("01001234567", ContactFormats.normalizeNumber("٠١٠٠١٢٣٤٥٦٧"))
        assertEquals("+96650", ContactFormats.normalizeNumber("+٩٦٦ ٥٠"))
        assertEquals("0912", ContactFormats.normalizeNumber("۰۹۱۲"))
    }

    @Test
    fun normalizeKeepsTheOriginalWhenInformationWouldBeLost() {
        assertEquals("555-1234 ext 5", ContactFormats.normalizeNumber("555-1234 ext 5"))
        assertEquals("*123#", ContactFormats.normalizeNumber("*123#"))
        assertEquals("1234;5", ContactFormats.normalizeNumber("1234;5"))
        assertEquals("()", ContactFormats.normalizeNumber("()"))
        assertEquals("+", ContactFormats.normalizeNumber("+"))
        assertEquals("", ContactFormats.normalizeNumber("  "))
    }

    @Test
    fun csvFieldDoublesQuotes() {
        assertEquals("\"say \"\"hi\"\"\"", ContactFormats.csvField("say \"hi\""))
        assertEquals("\"\"", ContactFormats.csvField(""))
        assertEquals("\"a,b\nc\"", ContactFormats.csvField("a,b\nc"))
    }

    @Test
    fun csvFieldBlocksFormulas() {
        assertEquals("\"'=1+1\"", ContactFormats.csvField("=1+1"))
        assertEquals("\"'+cmd\"", ContactFormats.csvField("+cmd"))
        assertEquals("\"'-5\"", ContactFormats.csvField("-5"))
        assertEquals("\"'@sum\"", ContactFormats.csvField("@sum"))
        assertEquals("\"'\tx\"", ContactFormats.csvField("\tx"))
        assertEquals("\"=1+1\"", ContactFormats.csvField("=1+1", guard = false))
        assertEquals("\"a=1\"", ContactFormats.csvField("a=1"))
    }

    @Test
    fun csvRowKeepsRealNumbersIntactAndGuardsOddOnes() {
        assertEquals("\"Sam\",\"+971501234567\",\"Mobile\"\r\n", ContactFormats.csvRow("Sam", "+971501234567", "Mobile"))
        assertEquals("\"Sam\",\"01001234567\",\"Home\"\r\n", ContactFormats.csvRow("Sam", "01001234567", "Home"))
        assertEquals("\"'=cmd\",\"'=1+1\",\"x\"\r\n", ContactFormats.csvRow("=cmd", "=1+1", "x"))
        assertEquals("\"Sam\",\"'+1;ext\",\"x\"\r\n", ContactFormats.csvRow("Sam", "+1;ext", "x"))
    }

    @Test
    fun csvStartsWithBomAndHeaderAndCountsRows() {
        val (text, count) = export(
            ExportKind.CSV,
            contact("Asmaa Gouda", number("+201001234567"), number("0225551234", "Home", "HOME")),
            contact("No Numbers"),
            contact("أسماء جودة", number("+966501234567"))
        )
        assertTrue(text.startsWith("﻿\"name\",\"number\",\"type\"\r\n"))
        val lines = text.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(4, lines.size)
        assertEquals("\"أسماء جودة\",\"+966501234567\",\"Mobile\"", lines[3])
        assertEquals(2, count.contacts)
        assertEquals(3, count.numbers)
    }

    @Test
    fun vcardHasTheExpectedStructure() {
        val (text, count) = export(
            ExportKind.VCARD,
            contact("Asmaa Gouda", number("+201001234567"), number("0225551234", "Work", "WORK"), number("999", "Other", null))
        )
        assertEquals(
            listOf(
                "BEGIN:VCARD", "VERSION:3.0", "FN:Asmaa Gouda", "N:;Asmaa Gouda;;;",
                "TEL;TYPE=CELL:+201001234567", "TEL;TYPE=WORK:0225551234", "TEL:999", "END:VCARD"
            ),
            text.split("\r\n").filter { it.isNotEmpty() }
        )
        assertTrue(text.endsWith("END:VCARD\r\n"))
        assertEquals(1, count.contacts)
        assertEquals(3, count.numbers)
    }

    @Test
    fun vcardEscapesSpecialCharacters() {
        val (text, _) = export(ExportKind.VCARD, contact("Smith, John; \"Jr\" \\ x", number("1")))
        assertTrue(text.contains("FN:Smith\\, John\\; \"Jr\" \\\\ x\r\n"))
    }

    @Test
    fun vcardUsesTheNumberWhenThereIsNoName() {
        val (text, _) = export(ExportKind.VCARD, contact("  ", number("+15551234")))
        assertTrue(text.contains("FN:+15551234\r\n"))
        val (csv, _) = export(ExportKind.CSV, contact("", number("+15551234")))
        assertTrue(csv.contains("\"+15551234\",\"+15551234\",\"Mobile\""))
    }

    @Test
    fun foldKeepsEveryLineWithin75OctetsAndLosesNothing() {
        val samples = listOf(
            "FN:" + "a".repeat(200),
            "FN:" + "مرحبا بالعالم ".repeat(12),
            "FN:" + "😀".repeat(40),
            "FN:" + "é".repeat(74)
        )
        for (sample in samples) {
            val folded = ContactFormats.fold(sample)
            val physical = folded.split("\r\n")
            assertTrue(physical.all { ContactFormats.utf8Length(it) <= 75 })
            assertTrue(physical.drop(1).all { it.startsWith(" ") })
            assertEquals(sample, folded.replace("\r\n ", ""))
        }
    }

    @Test
    fun foldLeavesShortLinesAlone() {
        assertEquals("FN:short", ContactFormats.fold("FN:short"))
        val exact = "FN:" + "b".repeat(72)
        assertEquals(exact, ContactFormats.fold(exact))
    }

    @Test
    fun vcardRoundTripsNamesAndNumbers() {
        val original = listOf(
            contact("Asmaa Gouda", number("+201001234567")),
            contact("أسماء جودة المصري " + "طويل ".repeat(20), number("+966501234567"), number("01001234567")),
            contact("Last, First; Jr\\", number("+15551234567")),
            contact("Line\nBreak", number("5551234"))
        )
        val (text, _) = export(ExportKind.VCARD, *original.toTypedArray())
        val parsed = parseVcards(text)
        assertEquals(original.map { it.name }, parsed.map { it.name })
        assertEquals(original.map { c -> c.numbers.map { it.number } }, parsed.map { c -> c.numbers.map { it.number } })
    }

    @Test
    fun numbersOnlyListsEachNumberOnceAndEndsLinesWithNewline() {
        val (text, count) = export(
            ExportKind.NUMBERS,
            contact("A", number("+201001234567"), number("0225551234")),
            contact("B", number("+201001234567")),
            contact("C", number("+966501234567"))
        )
        assertEquals("+201001234567\n0225551234\n+966501234567\n", text)
        assertEquals(2, count.contacts)
        assertEquals(3, count.numbers)
    }

    @Test
    fun emptyInputStillProducesAValidFile() {
        val (csv, csvCount) = export(ExportKind.CSV)
        assertEquals("﻿" + ContactFormats.CSV_HEADER, csv)
        assertEquals(0, csvCount.contacts)
        assertEquals("", export(ExportKind.VCARD).first)
        assertEquals("", export(ExportKind.NUMBERS).first)
    }

    @Test
    fun exportKindsAreLookedUpByKey() {
        assertEquals(ExportKind.CSV, ExportKind.of("csv"))
        assertEquals(ExportKind.VCARD, ExportKind.of("vcard"))
        assertNull(ExportKind.of("xlsx"))
        assertTrue(ExportKind.values().all { it.fileName.contains('.') })
    }
}
