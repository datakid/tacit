package app.tacit.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

object Targets {
    const val TACIT = "app.tacit"
    const val TACIT_ACTIVITY = "app.tacit.ui.SearchActivity"
    const val KISS = "fr.neamar.kiss"
    const val SEARCH_FIELD_ID = "search_input"
    const val TIMEOUT_MS = 5_000L

    val typingScript = listOf(
        "g", "go", "gos", "", "y", "yt", "", "w", "wf", "wfi", "", "b", "bl", "blu", "blut", "bluto",
        "blutoo", "", "2", "2+", "2+2", "", "c", "ca", "cal", "calc", "", "a", "ag", ""
    )
}

fun compilationMode(): CompilationMode =
    if (InstrumentationRegistry.getArguments().getString("tacit.compilation") == "none") CompilationMode.None() else CompilationMode.Partial()

fun MacrobenchmarkScope.waitForSearchField() {
    device.wait(Until.hasObject(By.res(Targets.TACIT, Targets.SEARCH_FIELD_ID)), Targets.TIMEOUT_MS)
}

fun MacrobenchmarkScope.typeScript() {
    val field = device.findObject(By.res(Targets.TACIT, Targets.SEARCH_FIELD_ID)) ?: return
    for (step in Targets.typingScript) {
        field.text = step
        device.waitForIdle(50)
    }
}
