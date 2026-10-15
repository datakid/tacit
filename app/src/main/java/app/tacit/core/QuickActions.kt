package app.tacit.core

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Base64
import java.util.Random

enum class Transform(val key: String) {
    UPPER("upper"),
    LOWER("lower"),
    TITLE("title"),
    REVERSE("reverse"),
    SLUG("slug"),
    BASE64("b64"),
    UNBASE64("unb64"),
    URLENC("urlenc"),
    URLDEC("urldec");

    companion object {
        fun of(key: String): Transform? = values().firstOrNull { it.key == key }
    }
}

enum class ValueKind { UUID, PASSWORD, DICE, RANDOM, TRANSFORM }

sealed class QuickResult {
    class Timer(val seconds: Int, val label: String) : QuickResult()
    class Alarm(val hour: Int, val minute: Int, val label: String) : QuickResult()
    class Event(val title: String, val date: LocalDate?, val time: LocalTime?) : QuickResult()
    class Value(val kind: ValueKind, val text: String, val spec: String = "", val transform: Transform? = null) : QuickResult()
    class Coin(val heads: Boolean) : QuickResult()
    class ClipboardTransform(val transform: Transform) : QuickResult()
    class Export(val kinds: List<ExportKind>) : QuickResult()
}

object QuickActions {

    private const val MAX_KEYWORD = 9
    private const val MAX_TIMER_SECONDS = 86400
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%&*-_?"
    private const val ALL_CHARACTERS = LOWER + UPPER + DIGITS + SYMBOLS

    private enum class Word { TIMER, ALARM, EVENT, EXPORT, UUID, PASSWORD, DICE, COIN, RANDOM, TRANSFORM }

    private class Entry(val word: Word, val transform: Transform? = null)

    private val spaces = Regex("\\s+")
    private val clock = Regex("^(\\d{1,2})(?::(\\d{2}))?([^\\d]*)$")
    private val timeToken = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm|a\\.m\\.|p\\.m\\.|ص|م|صباحا|مساء)?$")
    private val isoDate = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
    private val dayFirstDate = Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?$")
    private val diceSpec = Regex("^(\\d{1,3})?d(\\d{1,4})$")

    private val words = HashMap<String, Entry>()
    private val units = HashMap<String, Int>()
    private val dayOffsets = HashMap<String, Int>()
    private val weekdays = HashMap<String, DayOfWeek>()
    private val amWords = HashSet<String>()
    private val pmWords = HashSet<String>()
    private val joiners = HashSet<String>()
    private val unitFactors = intArrayOf(3600, 60, 1)

    private fun word(entry: Entry, vararg names: String) {
        for (name in names) words[Normalizer.normalize(name)] = entry
    }

    private fun unit(rank: Int, vararg names: String) {
        for (name in names) units[Normalizer.normalize(name)] = rank
    }

    private fun day(day: DayOfWeek, vararg names: String) {
        for (name in names) weekdays[Normalizer.normalize(name)] = day
    }

    init {
        word(Entry(Word.TIMER), "timer", "مؤقت")
        word(Entry(Word.ALARM), "alarm", "منبه")
        word(Entry(Word.EVENT), "event", "موعد")
        word(Entry(Word.EXPORT), "export", "تصدير")
        word(Entry(Word.UUID), "uuid", "guid")
        word(Entry(Word.PASSWORD), "pass", "password")
        word(Entry(Word.DICE), "roll", "dice")
        word(Entry(Word.COIN), "flip", "coin")
        word(Entry(Word.RANDOM), "rand", "random")
        word(Entry(Word.TRANSFORM, Transform.UPPER), "upper")
        word(Entry(Word.TRANSFORM, Transform.LOWER), "lower")
        word(Entry(Word.TRANSFORM, Transform.TITLE), "title")
        word(Entry(Word.TRANSFORM, Transform.REVERSE), "reverse", "rev")
        word(Entry(Word.TRANSFORM, Transform.SLUG), "slug")
        word(Entry(Word.TRANSFORM, Transform.BASE64), "b64", "base64")
        word(Entry(Word.TRANSFORM, Transform.UNBASE64), "unb64")
        word(Entry(Word.TRANSFORM, Transform.URLENC), "urlenc", "urlencode")
        word(Entry(Word.TRANSFORM, Transform.URLDEC), "urldec", "urldecode")
        unit(0, "h", "hr", "hrs", "hour", "hours", "ساعة", "ساعات")
        unit(1, "m", "min", "mins", "minute", "minutes", "دقيقة", "دقائق")
        unit(2, "s", "sec", "secs", "second", "seconds", "ثانية", "ثواني", "ثوان")
        dayOffsets[Normalizer.normalize("today")] = 0
        dayOffsets[Normalizer.normalize("اليوم")] = 0
        dayOffsets[Normalizer.normalize("tomorrow")] = 1
        dayOffsets[Normalizer.normalize("غدا")] = 1
        dayOffsets[Normalizer.normalize("بكرة")] = 1
        day(DayOfWeek.MONDAY, "mon", "monday", "الاثنين", "الإثنين")
        day(DayOfWeek.TUESDAY, "tue", "tues", "tuesday", "الثلاثاء")
        day(DayOfWeek.WEDNESDAY, "wed", "wednesday", "الأربعاء")
        day(DayOfWeek.THURSDAY, "thu", "thur", "thurs", "thursday", "الخميس")
        day(DayOfWeek.FRIDAY, "fri", "friday", "الجمعة")
        day(DayOfWeek.SATURDAY, "sat", "saturday", "السبت")
        day(DayOfWeek.SUNDAY, "sun", "sunday", "الأحد")
        amWords.addAll(listOf("am", "a.m.", "ص", "صباحا"))
        pmWords.addAll(listOf("pm", "p.m.", "م", "مساء"))
        joiners.addAll(listOf("at", "on", "في"))
    }

    private fun isAscii(text: String): Boolean {
        for (ch in text) if (ch.code >= 128) return false
        return true
    }

    fun evaluate(raw: String, now: () -> LocalDateTime, random: () -> Random): QuickResult? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        var end = 0
        while (end < text.length && !text[end].isWhitespace()) end++
        if (end > MAX_KEYWORD) return null
        val head = text.substring(0, end)
        val entry = words[if (isAscii(head)) head.lowercase() else Normalizer.normalize(head)] ?: return null
        val rest = text.substring(end).trim()
        return when (entry.word) {
            Word.TIMER -> timer(rest)
            Word.ALARM -> alarm(rest)
            Word.EVENT -> event(rest, now())
            Word.EXPORT -> export(rest)
            Word.UUID -> if (rest.isEmpty()) QuickResult.Value(ValueKind.UUID, uuid(random())) else null
            Word.PASSWORD -> password(rest, random())
            Word.DICE -> dice(rest, random())
            Word.COIN -> if (rest.isEmpty()) QuickResult.Coin(random().nextBoolean()) else null
            Word.RANDOM -> rand(rest, random())
            Word.TRANSFORM -> {
                val kind = entry.transform ?: return null
                if (rest.isEmpty()) QuickResult.ClipboardTransform(kind)
                else transform(kind, rest)?.let { QuickResult.Value(ValueKind.TRANSFORM, it, kind.key, kind) }
            }
        }
    }

    private fun export(rest: String): QuickResult? {
        val needle = Normalizer.normalize(rest).trim()
        val kinds = ExportKind.values().filter { it.key.startsWith(needle) }
        return if (kinds.isEmpty()) null else QuickResult.Export(kinds)
    }

    fun uuid(random: Random): String {
        val high = (random.nextLong() and 0xF000L.inv()) or 0x4000L
        val low = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return java.util.UUID(high, low).toString()
    }

    private fun password(rest: String, random: Random): QuickResult? {
        val length = if (rest.isEmpty()) 16 else Normalizer.normalize(rest).toIntOrNull() ?: return null
        if (length !in 4..128) return null
        val classes = arrayOf(LOWER, UPPER, DIGITS, SYMBOLS)
        val chars = CharArray(length)
        for (index in classes.indices) chars[index] = classes[index][random.nextInt(classes[index].length)]
        for (index in classes.size until length) chars[index] = ALL_CHARACTERS[random.nextInt(ALL_CHARACTERS.length)]
        for (index in length - 1 downTo 1) {
            val other = random.nextInt(index + 1)
            val held = chars[index]
            chars[index] = chars[other]
            chars[other] = held
        }
        return QuickResult.Value(ValueKind.PASSWORD, String(chars), length.toString())
    }

    private fun dice(rest: String, random: Random): QuickResult? {
        var count = 1
        var sides = 6
        if (rest.isNotEmpty()) {
            val spec = Normalizer.normalize(rest).replace(" ", "")
            val match = diceSpec.matchEntire(spec)
            if (match != null) {
                count = match.groupValues[1].ifEmpty { "1" }.toInt()
                sides = match.groupValues[2].toInt()
            } else {
                sides = spec.toIntOrNull() ?: return null
            }
        }
        if (count !in 1..100 || sides !in 2..1000) return null
        val rolls = IntArray(count) { random.nextInt(sides) + 1 }
        val detail = if (count == 1) "d$sides" else "${count}d$sides: " + rolls.joinToString(" + ")
        return QuickResult.Value(ValueKind.DICE, rolls.sum().toString(), detail)
    }

    private fun rand(rest: String, random: Random): QuickResult? {
        val numbers: List<Long> = if (rest.isEmpty()) emptyList() else Normalizer.normalize(rest).split(spaces).map { it.toLongOrNull() ?: return null }
        if (numbers.size > 2 || numbers.any { it > 1_000_000_000_000L || it < -1_000_000_000_000L }) return null
        val first = when (numbers.size) {
            0 -> 1L
            1 -> 1L
            else -> numbers[0]
        }
        val second = when (numbers.size) {
            0 -> 100L
            1 -> numbers[0]
            else -> numbers[1]
        }
        val low = minOf(first, second)
        val high = maxOf(first, second)
        val span = high - low + 1
        if (span !in 1..Int.MAX_VALUE.toLong()) return null
        val value = low + random.nextInt(span.toInt())
        return QuickResult.Value(ValueKind.RANDOM, value.toString(), "$low–$high")
    }

    private fun isNumeric(ch: Char): Boolean = ch in '0'..'9' || ch == '.' || ch == ','

    private fun runsOf(token: String): List<String> {
        val runs = ArrayList<String>(3)
        var start = 0
        while (start < token.length) {
            val numeric = isNumeric(token[start])
            var end = start + 1
            while (end < token.length && isNumeric(token[end]) == numeric) end++
            runs.add(token.substring(start, end))
            start = end
        }
        return runs
    }

    private fun timer(rest: String): QuickResult? {
        if (rest.isEmpty()) return null
        val tokens = rest.split(spaces)
        val runs = ArrayList<String>()
        val tokenOf = ArrayList<Int>()
        val endsToken = ArrayList<Boolean>()
        for ((index, token) in tokens.withIndex()) {
            val parts = runsOf(Normalizer.normalize(token))
            for ((position, part) in parts.withIndex()) {
                runs.add(part)
                tokenOf.add(index)
                endsToken.add(position == parts.lastIndex)
            }
        }
        var total = 0.0
        var rank = -1
        var explicit = false
        var index = 0
        var consumed = 0
        while (index < runs.size) {
            if (!isNumeric(runs[index][0])) break
            val value = runs[index].replace(',', '.').toDoubleOrNull() ?: break
            var unitRank = -1
            var used = 1
            if (index + 1 < runs.size && !isNumeric(runs[index + 1][0])) {
                unitRank = units[runs[index + 1]] ?: -1
                if (unitRank >= 0) used = 2
            }
            if (unitRank < 0) {
                unitRank = when {
                    rank < 0 -> 1
                    explicit && rank < 2 -> rank + 1
                    else -> break
                }
            }
            if (unitRank <= rank) break
            total += value * unitFactors[unitRank]
            rank = unitRank
            explicit = used == 2
            index += used
            consumed = index
        }
        if (consumed == 0 || !endsToken[consumed - 1]) return null
        val seconds = Math.round(total).toInt()
        if (seconds < 1 || seconds > MAX_TIMER_SECONDS) return null
        val label = tokens.drop(tokenOf[consumed - 1] + 1).joinToString(" ")
        return QuickResult.Timer(seconds, label)
    }

    private fun clockTime(hourText: String, minuteText: String, meridiem: String?): LocalTime? {
        val hour = hourText.toInt()
        val minute = if (minuteText.isEmpty()) 0 else minuteText.toInt()
        if (minute !in 0..59) return null
        if (meridiem == null) return if (hour in 0..23) LocalTime.of(hour, minute) else null
        val pm = meridiem in pmWords
        if (!pm && meridiem !in amWords) return null
        if (hour !in 1..12) return null
        return LocalTime.of(hour % 12 + if (pm) 12 else 0, minute)
    }

    private fun alarm(rest: String): QuickResult? {
        if (rest.isEmpty()) return null
        val tokens = rest.split(spaces)
        val match = clock.matchEntire(Normalizer.normalize(tokens[0])) ?: return null
        var suffix = match.groupValues[3]
        var consumed = 1
        if (suffix.isEmpty() && tokens.size > 1) {
            val next = Normalizer.normalize(tokens[1])
            if (next in amWords || next in pmWords) {
                suffix = next
                consumed = 2
            }
        }
        val time = clockTime(match.groupValues[1], match.groupValues[2], suffix.ifEmpty { null }) ?: return null
        return QuickResult.Alarm(time.hour, time.minute, tokens.drop(consumed).joinToString(" "))
    }

    private fun takeTime(tokens: MutableList<String>): LocalTime? {
        val last = Normalizer.normalize(tokens.last())
        val own = timeToken.matchEntire(last)
        if (own != null) {
            val suffix = own.groupValues[3]
            if (suffix.isEmpty() && own.groupValues[2].isEmpty()) return null
            val time = clockTime(own.groupValues[1], own.groupValues[2], suffix.ifEmpty { null }) ?: return null
            tokens.removeAt(tokens.lastIndex)
            return time
        }
        if (tokens.size >= 2 && (last in amWords || last in pmWords)) {
            val previous = timeToken.matchEntire(Normalizer.normalize(tokens[tokens.size - 2])) ?: return null
            if (previous.groupValues[3].isNotEmpty()) return null
            val time = clockTime(previous.groupValues[1], previous.groupValues[2], last) ?: return null
            tokens.removeAt(tokens.lastIndex)
            tokens.removeAt(tokens.lastIndex)
            return time
        }
        return null
    }

    private fun dateOf(text: String, today: LocalDate): LocalDate? {
        dayOffsets[text]?.let { return today.plusDays(it.toLong()) }
        weekdays[text]?.let { target ->
            val ahead = (target.value - today.dayOfWeek.value + 7) % 7
            return today.plusDays(if (ahead == 0) 7L else ahead.toLong())
        }
        try {
            isoDate.matchEntire(text)?.let {
                return LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
            }
            dayFirstDate.matchEntire(text)?.let {
                val dayOfMonth = it.groupValues[1].toInt()
                val month = it.groupValues[2].toInt()
                val year = it.groupValues[3]
                if (year.isNotEmpty()) return LocalDate.of(year.toInt(), month, dayOfMonth)
                val thisYear = LocalDate.of(today.year, month, dayOfMonth)
                return if (thisYear.isBefore(today)) thisYear.plusYears(1) else thisYear
            }
        } catch (error: DateTimeException) {
            return null
        }
        return null
    }

    private fun event(rest: String, now: LocalDateTime): QuickResult {
        val tokens: MutableList<String> = if (rest.isEmpty()) ArrayList() else rest.split(spaces).toMutableList()
        val today = now.toLocalDate()
        var date: LocalDate? = null
        var time: LocalTime? = null
        var rounds = 0
        while (tokens.isNotEmpty() && rounds < 2) {
            rounds++
            var taken = false
            if (time == null) {
                val found = takeTime(tokens)
                if (found != null) {
                    time = found
                    taken = true
                }
            }
            if (!taken && date == null) {
                val found = dateOf(Normalizer.normalize(tokens.last()), today)
                if (found != null) {
                    date = found
                    tokens.removeAt(tokens.lastIndex)
                    taken = true
                }
            }
            if (!taken) break
            if (tokens.isNotEmpty() && Normalizer.normalize(tokens.last()) in joiners) tokens.removeAt(tokens.lastIndex)
        }
        val clockTime = time
        val day = date ?: clockTime?.let { if (it.isAfter(now.toLocalTime())) today else today.plusDays(1) }
        return QuickResult.Event(tokens.joinToString(" "), day, clockTime)
    }

    fun transform(transform: Transform, text: String): String? = when (transform) {
        Transform.UPPER -> text.uppercase()
        Transform.LOWER -> text.lowercase()
        Transform.TITLE -> titleCase(text)
        Transform.REVERSE -> StringBuilder(text).reverse().toString()
        Transform.SLUG -> Normalizer.words(Normalizer.normalize(text)).joinToString("-")
        Transform.BASE64 -> Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        Transform.UNBASE64 -> decodeBase64(text)
        Transform.URLENC -> URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        Transform.URLDEC -> decodeUrl(text)
    }

    private fun titleCase(text: String): String {
        val builder = StringBuilder(text.length)
        var start = true
        for (ch in text.lowercase()) {
            builder.append(if (start && ch.isLetter()) Character.toTitleCase(ch) else ch)
            start = if (ch.isLetterOrDigit()) false else start || ch.isWhitespace()
        }
        return builder.toString()
    }

    private fun strictUtf8(bytes: ByteArray): String? {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: CharacterCodingException) {
            null
        }
    }

    private fun decodeBase64(text: String): String? {
        val trimmed = text.trim()
        val bytes = try {
            Base64.getDecoder().decode(trimmed)
        } catch (error: IllegalArgumentException) {
            try {
                Base64.getUrlDecoder().decode(trimmed)
            } catch (second: IllegalArgumentException) {
                return null
            }
        }
        return strictUtf8(bytes)
    }

    private fun hexValue(ch: Char): Int = Character.digit(ch, 16)

    private fun decodeUrl(text: String): String? {
        val out = ByteArrayOutputStream(text.length)
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            when {
                ch == '%' -> {
                    if (index + 2 >= text.length) return null
                    val high = hexValue(text[index + 1])
                    val low = hexValue(text[index + 2])
                    if (high < 0 || low < 0) return null
                    out.write(high * 16 + low)
                    index += 3
                }
                ch == '+' -> {
                    out.write(' '.code)
                    index++
                }
                else -> {
                    val point = text.codePointAt(index)
                    out.write(String(Character.toChars(point)).toByteArray(Charsets.UTF_8))
                    index += Character.charCount(point)
                }
            }
        }
        return strictUtf8(out.toByteArray())
    }
}
