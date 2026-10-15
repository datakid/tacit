package app.tacit.core

import app.tacit.calc.Calculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Random

class ActionOrderTest {

    private val now = LocalDateTime.of(2026, 10, 15, 10, 0)

    private val provider: (String) -> List<SearchItem> = { text ->
        if (QuickActions.evaluate(text, { now }, { Random(1L) }) == null) emptyList()
        else listOf(SearchItem("act:test", ItemKind.ACTION, "action", payload = "test|"))
    }

    private fun keys(raw: String, vararg items: SearchItem): List<String> {
        val engine = SearchEngine()
        engine.replaceItems(items.toList())
        val composer = ResultComposer(engine, PrefixGrammar(), Calculator())
        val input = ComposeInput(raw, 8, PrefixGrammar.defaultEngines[0], actionRows = provider, clipboardItems = { emptyList() })
        return composer.compose(input).rows.map { it.key }
    }

    private fun app(key: String, label: String) = SearchItem(key, ItemKind.APP, label)

    @Test
    fun aKeywordWithArgumentsComesBeforeAppMatches() {
        val result = keys("timer 10 tea", app("a", "Timer Tools"))
        assertEquals("act:test", result.first())
    }

    @Test
    fun aBareKeywordWaitsForARealNameMatch() {
        val result = keys("flip", app("a", "Flipkart"), app("b", "Photos"))
        assertEquals(listOf("a", "act:test"), result.dropLast(1))
        assertTrue(result.last().startsWith("web:"))
    }

    @Test
    fun aBareKeywordWithoutAMatchIsFirst() {
        val result = keys("uuid", app("a", "Photos"))
        assertEquals("act:test", result.first())
    }

    @Test
    fun anExactAliasStillWins() {
        val item = app("a", "Passwords").withAliases(listOf("pass"))
        val result = keys("pass", item)
        assertEquals("a", result.first())
        assertEquals("act:test", result[1])
    }

    @Test
    fun ordinaryTextAddsNothing() {
        val result = keys("photos", app("a", "Photos"))
        assertTrue(result.none { it == "act:test" })
    }

    @Test
    fun noProviderMeansNoRows() {
        val engine = SearchEngine()
        engine.replaceItems(listOf(app("a", "Flipkart")))
        val composer = ResultComposer(engine, PrefixGrammar(), Calculator())
        val rows = composer.compose(ComposeInput("flip", 8, PrefixGrammar.defaultEngines[0]) { emptyList() }).rows
        assertTrue(rows.none { it.key == "act:test" })
    }
}
