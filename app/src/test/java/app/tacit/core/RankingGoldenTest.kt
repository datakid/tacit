package app.tacit.core

import app.tacit.calc.Calculator
import app.tacit.fixtures.Fixtures
import app.tacit.golden.GoldenFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale
import java.util.Random

class RankingGoldenTest {

    private val fixedNow = LocalDateTime.of(2026, 10, 1, 9, 0)
    private var savedLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ROOT)
        IndexOptions.transliterate = true
    }

    @After
    fun tearDown() {
        Locale.setDefault(savedLocale)
    }

    private val searchQueries: List<String> = (
        listOf(
            "y", "yt", "you", "gos", "ag", "asm gou", "asmaa", "wfi", "wi fi", "blutooth", "bluetoth", "set", "sett",
            "cal", "cam", "zoe", "zoë", "mul", "أسماء", "اسما", "جودة"
        ) +
            Fixtures.typingScript +
            listOf(
                "gouda asmaa", "galal ahmed", "salah mohamed", "osman gamal", "business whatsapp", "store play",
                "music youtube", "alvarez jose", "lefevre francois", "جودة أسماء", "صلاح محمد",
                "q", "whatsappbusi", "asmaagoudaxx",
                "yotube", "youtub", "gogle", "calculater", "calandar", "camra", "whatsap", "telegam", "spotfy", "chrom",
                "settngs", "fdroid", "f-droid", "k9", "k-9", "aegis", "bitwardn", "termux", "ter", "sig", "signa",
                "mohamed salah", "mo sal", "ms", "اح", "نور", "youssef", "يوسف", "ابراهيم", "فاطمة", "fatma", "fatema",
                "abdel", "abdel rahman", "nour el din", "jose", "josé", "alvarez", "francois", "lefevre", "muller",
                "mueller", "zoe muller", "note", "notes", "simple note", "quick scan", "pro vault", "vault", "pod", "scan",
                "zq9", "zq", "pocket calc", "pocket"
            )
        ).distinct()

    private val composerQueries: List<String> = listOf(
        "", "gos", "zq9", "calc", "2+2", "=15% of 340", "15% of 340", "255 in hex", "10 km in miles", "=sqrt(144)",
        "timer 10 tea", "alarm 7:30", "upper hello", "b64 hello", "flip", "uuid", "pass 12", "roll 2d6", "rand 1 10",
        "+20 100 123 4567", "01001234567", "github.com", "https://example.org/path", "a@b.com",
        "yt cats", "g kotlin", "ddg ", "cb meeting", "cb ", "> wifi", "@ asm", "; sig", "/ report", "/ ", "gh octocat"
    )

    private val clipboard = listOf(
        SearchItem("clip:1", ItemKind.CLIPBOARD, "Meeting link", "2 min ago", payload = "https://meet.example.org/abc"),
        SearchItem("clip:2", ItemKind.CLIPBOARD, "Address", "1 h ago", payload = "12 Nile Street, Cairo"),
        SearchItem("clip:3", ItemKind.CLIPBOARD, "Note", "yesterday", payload = "meeting at five")
    )

    private fun engine(): SearchEngine {
        val items = Fixtures.all()
        items.first { it.key == "app:fixture/Calculator" }.withAliases(listOf("zq9", "pocket calc"))
        return SearchEngine().also { it.replaceItems(items) }
    }

    private fun quicklinkRows(text: String): List<SearchItem> =
        if (text.startsWith("gh ")) listOf(SearchItem("ql:gh", ItemKind.ACTION, "GitHub", "github.com", payload = "ql|gh")) else emptyList()

    private fun actionRows(text: String): List<SearchItem> {
        val result = QuickActions.evaluate(text, { fixedNow }, { Random(1) }) ?: return emptyList()
        val item = when (result) {
            is QuickResult.Timer -> SearchItem("act:timer", ItemKind.ACTION, "timer " + result.seconds, result.label)
            is QuickResult.Alarm -> SearchItem("act:alarm", ItemKind.ACTION, "alarm " + result.hour + ":" + result.minute, result.label)
            is QuickResult.Event -> SearchItem("act:event", ItemKind.ACTION, "event " + result.title, result.date.toString())
            is QuickResult.Value -> SearchItem("act:" + result.kind.name.lowercase(), ItemKind.ACTION, result.text, result.spec)
            is QuickResult.Coin -> SearchItem("act:coin", ItemKind.ACTION, if (result.heads) "heads" else "tails")
            is QuickResult.ClipboardTransform -> SearchItem("act:clipxform:" + result.transform.key, ItemKind.ACTION, "clipboard " + result.transform.key)
            is QuickResult.Export -> SearchItem("act:export", ItemKind.ACTION, "export")
        }
        return listOf(item)
    }

    private fun searchLine(engine: SearchEngine, query: String): String =
        GoldenFile.label(query) + "\t" + engine.search(query, 8).joinToString("|") { it.item.key + "@" + it.score }

    private fun composerLine(composer: ResultComposer, raw: String): String {
        val output = composer.compose(
            ComposeInput(
                raw = raw,
                limit = 8,
                defaultEngine = PrefixGrammar.defaultEngines.first(),
                quicklinkRows = { quicklinkRows(it) },
                actionRows = { actionRows(it) },
                clipboardItems = { clipboard }
            )
        )
        val head = "scope=" + output.parsed.scope.name + "|files=" + output.wantsFiles
        val rows = output.rows.joinToString("|") { it.kind.name + " " + it.key + " [" + it.label + "]" }
        return "compose:" + GoldenFile.label(raw) + "\t" + head + (if (rows.isEmpty()) "" else "|" + rows)
    }

    @Test
    fun rankingAndCompositionMatchTheGoldenFile() {
        val engine = engine()
        val composer = ResultComposer(engine, PrefixGrammar(), Calculator())
        val lines = ArrayList<String>()
        for (query in searchQueries) lines.add(searchLine(engine, query))
        for (raw in composerQueries) lines.add(composerLine(composer, raw))
        GoldenFile.check("ranking.tsv", lines.joinToString("\n") + "\n")
    }
}
