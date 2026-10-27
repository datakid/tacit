package app.tacit.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColdStartSectionsBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun coldStartSections() = rule.measureRepeated(
        packageName = Targets.TACIT,
        metrics = listOf(
            StartupTimingMetric(),
            TraceSectionMetric("tacit.cache.read", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("tacit.buildViews", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("tacit.firstQuery", TraceSectionMetric.Mode.Sum)
        ),
        compilationMode = compilationMode(),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
        waitForSearchField()
    }
}
