package app.tacit.data

import app.tacit.core.AliasRules

object Csv {

    fun encodeRow(fields: List<String>): String = fields.joinToString(",") { field ->
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    fun encode(rows: List<List<String>>): String = rows.joinToString("\n") { encodeRow(it) }

    fun decode(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(field.toString())
                        field.clear()
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(field.toString())
                        field.clear()
                        rows.add(row)
                        row = ArrayList()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}

object AliasText {
    fun split(cell: String): List<String> =
        AliasRules.clean(cell.split(',', '،', ';'))

    fun join(values: Collection<String>): String = values.joinToString(", ")
}
