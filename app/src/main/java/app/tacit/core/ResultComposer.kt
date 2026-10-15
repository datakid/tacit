package app.tacit.core

import app.tacit.calc.Calculator

class ComposeInput(
    val raw: String,
    val limit: Int,
    val defaultEngine: WebEngine,
    val quicklinkRows: (String) -> List<SearchItem> = { emptyList() },
    val actionRows: (String) -> List<SearchItem> = { emptyList() },
    val clipboardItems: () -> List<SearchItem>
)

class ComposeOutput(val rows: List<SearchItem>, val parsed: ParsedQuery, val wantsFiles: Boolean)

class ResultComposer(
    private val engine: SearchEngine,
    private val grammar: PrefixGrammar,
    private val calculator: Calculator
) {

    fun compose(input: ComposeInput): ComposeOutput {
        val parsed = grammar.parse(input.raw)
        val text = parsed.text
        val rows = ArrayList<SearchItem>(input.limit + 4)
        when (parsed.scope) {
            Scope.CALCULATOR -> calculatorRow(text)?.let { rows.add(it) }
            Scope.WEB -> if (text.isNotEmpty() && parsed.engine != null) rows.add(webRow(parsed.engine, text))
            Scope.CLIPBOARD -> rows.addAll(filterLocal(input.clipboardItems(), text, input.limit))
            Scope.SETTINGS -> rows.addAll(engine.search(text, input.limit, setOf(ItemKind.SETTING)).map { it.item })
            Scope.CONTACTS -> rows.addAll(engine.search(text, input.limit, setOf(ItemKind.CONTACT)).map { it.item })
            Scope.SNIPPETS -> rows.addAll(engine.search(text, input.limit, setOf(ItemKind.SNIPPET)).map { it.item })
            Scope.FILES -> Unit
            Scope.ALL -> composeAll(text, input, rows)
        }
        val wantsFiles = (parsed.scope == Scope.ALL && Normalizer.normalize(text).trim().length >= 2) ||
            (parsed.scope == Scope.FILES && text.isNotEmpty())
        return ComposeOutput(rows, parsed, wantsFiles)
    }

    private fun composeAll(text: String, input: ComposeInput, rows: MutableList<SearchItem>) {
        if (text.isEmpty()) {
            rows.addAll(engine.search("", input.limit).map { it.item })
            return
        }
        val recognized = InputRecognizer.recognize(text)
        val calc = if (recognized == null && calculator.looksLikeMath(text)) calculatorRow(text) else null
        val matches = engine.search(text, input.limit)
        val exactAlias = matches.firstOrNull()?.let { it.score >= Tier.ALIAS_EXACT * Tier.SCALE } == true
        val actions = input.actionRows(text)
        val strongMatch = matches.firstOrNull()?.let { it.score >= Tier.WORD_PREFIX * Tier.SCALE } == true
        val actionsLast = actions.isNotEmpty() && !exactAlias && text.none { it.isWhitespace() } && strongMatch
        rows.addAll(input.quicklinkRows(text))
        if (actions.isNotEmpty() && !exactAlias && !actionsLast) rows.addAll(actions)
        if (calc != null && !exactAlias) rows.add(calc)
        rows.addAll(recognizedRows(recognized))
        for (match in matches) if (rows.size < input.limit) rows.add(match.item)
        if (actions.isNotEmpty() && exactAlias) rows.addAll(1.coerceAtMost(rows.size), actions)
        if (actionsLast) rows.addAll(actions)
        if (calc != null && exactAlias) rows.add(1.coerceAtMost(rows.size), calc)
        rows.add(webRow(input.defaultEngine, text))
    }

    private fun recognizedRows(recognized: Recognized?): List<SearchItem> = when (recognized) {
        is Recognized.Phone -> listOf(
            SearchItem("num:call", ItemKind.ACTION, "Call ${recognized.number}", "Phone", payload = "dial|${recognized.number}"),
            SearchItem("num:sms", ItemKind.ACTION, "Message ${recognized.number}", "SMS", payload = "sms|${recognized.number}"),
            SearchItem("num:wa", ItemKind.ACTION, "WhatsApp ${recognized.number}", "WhatsApp", payload = "wa|${recognized.number}")
        )
        is Recognized.Url -> listOf(SearchItem("url", ItemKind.ACTION, "Open ${recognized.url}", "Browser", payload = "url|${recognized.url}"))
        is Recognized.Email -> listOf(SearchItem("mail", ItemKind.ACTION, "Email ${recognized.address}", "Mail", payload = "mail|${recognized.address}"))
        null -> emptyList()
    }

    private fun calculatorRow(text: String): SearchItem? {
        val result = calculator.evaluate(text) ?: return null
        return SearchItem("calc", ItemKind.CALCULATOR, result.display, result.expression, payload = result.copyValue)
    }

    private fun webRow(engine: WebEngine, text: String): SearchItem =
        SearchItem("web:${engine.prefix}", ItemKind.WEB, text, engine.name, payload = engine.urlFor(text))

    private fun filterLocal(items: List<SearchItem>, text: String, limit: Int): List<SearchItem> {
        if (text.isEmpty()) return items.take(limit * 4)
        val needle = Normalizer.normalize(text)
        return items.filter { Normalizer.normalize(it.payload).contains(needle) }.take(limit * 4)
    }
}
