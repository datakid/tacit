package app.tacit.files

import app.tacit.fixtures.FileFixtures
import app.tacit.golden.GoldenFile
import org.junit.Assert.assertEquals
import org.junit.Test

class FileGoldenTest {

    private val queries: List<String> = listOf(
        "report", "invoice", "holiday", "photo", "budget", "resume", "notes", "screenshot", "contract", "meeting",
        "project", "summary", "backup", "passport", "cairo", "nile", "gouda", "salah", "wedding", "beach",
        "report final", "holiday photo", "budget 2024", "invoice 2023", "meeting notes", "project plan", "tax statement",
        "bank account", "flight ticket", "hotel booking", "wedding photo", "summer holiday", "final report",
        "photo holiday", "plan project", "notes meeting",
        "ext:pdf", "ext:docx", "ext:apk", "ext:.jpg", "ext:zip report", "*.jpg", "*.png holiday", "*.mp4", "*.pdf invoice",
        "ext:md notes", "ext:csv", "ext:xlsx budget",
        "invoice pdf", "report docx", "photo jpg", "notes txt", "budget xlsx", "holiday mp4", "song mp3", "contract pdf",
        "resume pdf", "scan png",
        "in:download", "in:documents report", "in:dcim photo", "in:pictures", "in:music song", "in:whatsapp",
        "in:telegram pdf", "report in:download", "invoice in:documents", "in:books epub", "in:android", "photo in:dcim",
        "تقرير", "فاتورة", "عقد", "صورة", "ملاحظات", "ميزانية", "اجتماع", "مشروع", "تقرير مشروع", "فاتوره", "ايصال",
        "رحله", "ext:pdf تقرير", "in:download صورة",
        "download", "documents", "pictures", "music", "archive", "winter", "family", "vacation", "mountain",
        "r", "re", "rep", "2024", "zzzqqq", "qwertyuiop", "report zzzqqq"
    )

    @Test
    fun theQueryListHasOneHundredEntries() {
        assertEquals(100, queries.size)
        assertEquals(100, queries.distinct().size)
    }

    @Test
    fun fileSearchMatchesTheGoldenFile() {
        val table = FileFixtures.table(FileFixtures.NOW)
        val lines = queries.map { query ->
            val hits = FileMatcher.search(table, query, 10, now = FileFixtures.NOW) { false }
            GoldenFile.label(query) + "\t" + hits.joinToString("|") { it.path + "@" + it.score }
        }
        GoldenFile.check("files.tsv", lines.joinToString("\n") + "\n")
    }
}
