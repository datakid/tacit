package app.tacit.core

import java.io.Writer

class ExportNumber(val number: String, val label: String, val vcardType: String?)

class ExportContact(val name: String, val numbers: List<ExportNumber>)

class ExportCount(val contacts: Int, val numbers: Int)

enum class ExportKind(val key: String, val mime: String, val fileName: String) {
    VCARD("vcard", "text/x-vcard", "tacit-contacts.vcf"),
    CSV("csv", "text/csv", "tacit-contacts.csv"),
    NUMBERS("numbers", "text/plain", "tacit-numbers.txt");

    companion object {
        fun of(key: String): ExportKind? = values().firstOrNull { it.key == key }
    }
}

object ContactFormats {

    const val BOM = '﻿'
    const val CSV_HEADER = "\"name\",\"number\",\"type\"\r\n"
    const val FOLD_OCTETS = 75

    private val phoneShape = Regex("^\\+?[0-9 ()./-]+$")
    private val safeNumber = Regex("^\\+?[0-9][0-9 ()./-]*$")
    private const val FORMULA_STARTS = "=+-@\t\r"

    fun asciiDigits(text: String): String {
        val builder = StringBuilder(text.length)
        for (ch in text) {
            builder.append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    else -> ch
                }
            )
        }
        return builder.toString()
    }

    fun normalizeNumber(raw: String): String {
        val trimmed = raw.trim()
        val ascii = asciiDigits(trimmed)
        if (!phoneShape.matches(ascii)) return trimmed
        val digits = ascii.filter { it in '0'..'9' }
        if (digits.isEmpty()) return trimmed
        return if (ascii.startsWith("+")) "+$digits" else digits
    }

    fun csvField(value: String, guard: Boolean = true): String {
        val safe = if (guard && value.isNotEmpty() && FORMULA_STARTS.indexOf(value[0]) >= 0) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    fun csvRow(name: String, number: String, type: String): String =
        csvField(name, !safeNumber.matches(name)) + "," + csvField(number, !safeNumber.matches(number)) + "," + csvField(type) + "\r\n"

    fun vcardEscape(value: String): String {
        val builder = StringBuilder(value.length + 4)
        for (ch in value) {
            when (ch) {
                '\\' -> builder.append("\\\\")
                ';' -> builder.append("\\;")
                ',' -> builder.append("\\,")
                '\n' -> builder.append("\\n")
                '\r' -> Unit
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    fun utf8Length(text: String): Int {
        var total = 0
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            total += utf8Size(point)
            index += Character.charCount(point)
        }
        return total
    }

    private fun utf8Size(point: Int): Int = when {
        point < 0x80 -> 1
        point < 0x800 -> 2
        point < 0x10000 -> 3
        else -> 4
    }

    fun fold(line: String): String {
        if (utf8Length(line) <= FOLD_OCTETS) return line
        val out = StringBuilder(line.length + 16)
        var used = 0
        var index = 0
        while (index < line.length) {
            val point = line.codePointAt(index)
            val size = utf8Size(point)
            if (used + size > FOLD_OCTETS) {
                out.append("\r\n ")
                used = 1
            }
            out.appendCodePoint(point)
            used += size
            index += Character.charCount(point)
        }
        return out.toString()
    }

    private fun singleLine(value: String): String = value.replace("\r", "").replace('\n', ' ')

    private fun displayName(contact: ExportContact): String =
        contact.name.ifBlank { contact.numbers.firstOrNull()?.number.orEmpty() }

    private fun line(out: Writer, text: String) {
        out.write(fold(text))
        out.write("\r\n")
    }

    fun writeVcard(contact: ExportContact, out: Writer) {
        val name = vcardEscape(displayName(contact))
        line(out, "BEGIN:VCARD")
        line(out, "VERSION:3.0")
        line(out, "FN:$name")
        line(out, "N:;$name;;;")
        for (entry in contact.numbers) {
            val type = entry.vcardType?.let { ";TYPE=$it" }.orEmpty()
            line(out, "TEL$type:" + singleLine(entry.number))
        }
        line(out, "END:VCARD")
    }

    fun write(kind: ExportKind, contacts: Sequence<ExportContact>, out: Writer): ExportCount {
        var people = 0
        var count = 0
        val seen = HashSet<String>()
        if (kind == ExportKind.CSV) {
            out.write(BOM.toString())
            out.write(CSV_HEADER)
        }
        for (contact in contacts) {
            if (contact.numbers.isEmpty()) continue
            when (kind) {
                ExportKind.VCARD -> {
                    writeVcard(contact, out)
                    people++
                    count += contact.numbers.size
                }
                ExportKind.CSV -> {
                    val name = displayName(contact)
                    for (entry in contact.numbers) out.write(csvRow(name, singleLine(entry.number), entry.label))
                    people++
                    count += contact.numbers.size
                }
                ExportKind.NUMBERS -> {
                    var added = 0
                    for (entry in contact.numbers) {
                        if (seen.add(entry.number)) {
                            out.write(singleLine(entry.number))
                            out.write("\n")
                            added++
                        }
                    }
                    if (added > 0) people++
                    count += added
                }
            }
        }
        out.flush()
        return ExportCount(people, count)
    }
}
