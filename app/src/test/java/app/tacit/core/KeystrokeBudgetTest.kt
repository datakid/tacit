package app.tacit.core

import app.tacit.fixtures.Budgets
import app.tacit.fixtures.Fixtures
import org.junit.Assert.assertTrue
import org.junit.Test

class KeystrokeBudgetTest {

    private fun engineWithFixtures(): SearchEngine {
        IndexOptions.transliterate = true
        return SearchEngine().also { it.replaceItems(Fixtures.all()) }
    }

    private fun percentile(values: List<Double>, p: Double): Double {
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * p).toInt()
        return sorted[index]
    }

    @Test
    fun fixtureSizes() {
        assertTrue(Fixtures.apps().size == 1000)
        assertTrue(Fixtures.contacts().size == 3000)
    }

    @Test
    fun fixturesStillFindNamedTargets() {
        val engine = engineWithFixtures()
        val expected = listOf("gos" to "Asmaa Gouda", "asmaa" to "أسماء جودة", "zoe" to "Zoë Müller", "yt" to "YouTube")
        val failures = ArrayList<String>()
        for ((query, label) in expected) {
            val results = engine.search(query, 8)
            val ok = if (query == "yt") results.firstOrNull()?.item?.label == label else results.any { it.item.label == label }
            if (!ok) failures.add(query + " -> " + results.joinToString { it.item.label + "@" + it.score })
        }
        assertTrue(failures.joinToString(" | "), failures.isEmpty())
    }

    @Test
    fun keystrokeP95WithinJvmBudget() {
        val engine = engineWithFixtures()
        repeat(20) { for (q in Fixtures.typingScript) engine.search(q, 8) }
        val timings = ArrayList<Double>()
        repeat(10) {
            for (q in Fixtures.typingScript) {
                val start = System.nanoTime()
                engine.search(q, 8)
                timings.add((System.nanoTime() - start) / 1_000_000.0)
            }
        }
        val p50 = percentile(timings, 0.50)
        val p95 = percentile(timings, 0.95)
        println("TACIT_KEYSTROKE_JVM p50=${"%.2f".format(p50)}ms p95=${"%.2f".format(p95)}ms items=4000")
        assertTrue("p95 $p95 ms over JVM budget ${Budgets.KEYSTROKE_JVM_P95_MS} ms (device budget is 8 ms, measured by TypingBenchmark)", p95 < Budgets.KEYSTROKE_JVM_P95_MS)
    }
}
