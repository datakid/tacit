package app.tacit.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypingBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun typingThirtyKeystrokes() = rule.measureRepeated(
        packageName = Targets.TACIT,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric("tacit.query", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("tacit.query", TraceSectionMetric.Mode.Max)
        ),
        compilationMode = compilationMode(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForSearchField()
        }
    ) {
        typeScript()
    }
}
