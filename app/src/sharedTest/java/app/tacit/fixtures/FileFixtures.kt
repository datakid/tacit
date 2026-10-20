package app.tacit.fixtures

import app.tacit.core.Normalizer
import app.tacit.files.FileTable
import java.util.Random

object FileFixtures {

    const val NOW = 1_790_845_200_000L
    const val ROOT = "/storage/emulated/0"

    private const val DAY_MS = 86_400_000L
    private const val MINUTE_MS = 60_000L
    private const val MAX_SIZE = 50L * 1024L * 1024L
    private const val DIRECTORY_COUNT = 1200

    val englishWords = listOf(
        "report", "invoice", "holiday", "photo", "budget", "resume", "notes", "screenshot", "contract", "meeting",
        "project", "summary", "draft", "final", "backup", "scan", "receipt", "ticket", "passport", "license",
        "insurance", "statement", "tax", "salary", "presentation", "proposal", "design", "logo", "wallpaper", "camera",
        "video", "song", "album", "playlist", "lecture", "homework", "thesis", "chapter", "manual", "guide",
        "recipe", "travel", "flight", "hotel", "booking", "map", "contact", "calendar", "schedule", "plan",
        "list", "template", "form", "letter", "offer", "quote", "order", "delivery", "payment", "bank",
        "account", "loan", "mortgage", "rent", "lease", "medical", "prescription", "lab", "result", "xray",
        "family", "birthday", "wedding", "graduation", "vacation", "beach", "mountain", "city", "street", "night",
        "morning", "winter", "summer", "spring", "autumn", "archive", "export", "import", "sync", "config",
        "settings", "data", "sample", "test", "demo", "prototype", "sketch", "cairo", "nile", "alexandria",
        "gouda", "salah", "download", "documents", "pictures", "music"
    )

    val arabicWords = listOf(
        "تقرير", "فاتورة", "عقد", "صورة", "ملاحظات", "ميزانية", "اجتماع", "مشروع", "ملخص", "إيصال",
        "تذكرة", "جواز", "رخصة", "شهادة", "محاضرة", "واجب", "كتاب", "وصفة", "رحلة", "عائلة"
    )

    val extensions = listOf(
        "pdf", "jpg", "png", "docx", "txt", "mp4", "mp3", "xlsx", "zip", "apk", "csv", "md",
        "doc", "xls", "ppt", "pptx", "jpeg", "gif", "webp", "heic", "mkv", "m4a", "flac", "epub",
        "json", "xml", "html", "log", "bak", "tmp"
    )

    private val rootFolders = listOf(
        "Download", "Documents", "DCIM", "Pictures", "Music", "Movies", "WhatsApp", "Telegram", "Books", "Android"
    )

    private val separators = listOf(" ", "_", "-")

    private fun capitalize(word: String): String = word.replaceFirstChar { it.uppercase() }

    private fun pickWord(random: Random, arabic: Boolean): String =
        if (arabic) arabicWords[random.nextInt(arabicWords.size)] else englishWords[random.nextInt(englishWords.size)]

    private fun baseName(random: Random): String {
        val arabic = random.nextInt(100) < 15
        val count = 1 + random.nextInt(3)
        val separator = separators[random.nextInt(separators.size)]
        val upper = random.nextBoolean()
        val words = List(count) { index ->
            val word = pickWord(random, arabic)
            if (upper && index == 0 && !arabic) capitalize(word) else word
        }
        val joined = words.joinToString(separator)
        return if (random.nextInt(100) < 35) {
            val number = if (random.nextBoolean()) (2019 + random.nextInt(8)).toString() else (1 + random.nextInt(999)).toString()
            joined + separator + number
        } else {
            joined
        }
    }

    private fun buildDirectories(random: Random): Array<String> {
        val paths = ArrayList<String>(DIRECTORY_COUNT)
        val depths = ArrayList<Int>(DIRECTORY_COUNT)
        for (name in rootFolders) {
            paths.add("$ROOT/$name")
            depths.add(1)
        }
        while (paths.size < DIRECTORY_COUNT) {
            val parent = random.nextInt(paths.size)
            if (depths[parent] >= 6) continue
            val arabic = random.nextInt(100) < 10
            val name = capitalize(pickWord(random, arabic)) + (if (random.nextInt(100) < 25) " " + (2019 + random.nextInt(8)) else "")
            paths.add(paths[parent] + "/" + name)
            depths.add(depths[parent] + 1)
        }
        return paths.toTypedArray()
    }

    private fun ageMillis(random: Random): Long {
        val days = if (random.nextInt(100) < 5) random.nextInt(40) else random.nextInt(1095)
        val minutes = 60 + random.nextInt(22 * 60)
        return days * DAY_MS + minutes * MINUTE_MS
    }

    fun table(now: Long, count: Int = 50_000): FileTable {
        val random = Random(42L)
        val directories = buildDirectories(random)
        val names = Array(count) { "" }
        val normalized = Array(count) { "" }
        val parent = IntArray(count)
        val sizes = LongArray(count)
        val modified = LongArray(count)
        for (i in 0 until count) {
            val folder = random.nextInt(100) < 8
            parent[i] = random.nextInt(directories.size)
            val base = baseName(random)
            val name = if (folder) "$base/" else base + "." + extensions[if (random.nextInt(100) < 45) random.nextInt(12) else random.nextInt(extensions.size)]
            names[i] = name
            normalized[i] = Normalizer.normalize(name)
            sizes[i] = if (folder || random.nextInt(20) == 0) 0L else (random.nextDouble() * MAX_SIZE).toLong()
            modified[i] = now - ageMillis(random)
        }
        return FileTable(directories, names, normalized, parent, sizes, modified)
    }
}
