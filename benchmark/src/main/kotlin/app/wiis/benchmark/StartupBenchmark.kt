package app.wiis.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run on the Thor with:  ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * Budgets (docs/BLUEPRINT.md §5): cold start ≤ 500 ms, warm ≤ 150 ms.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun coldStartup() = startup(StartupMode.COLD)
    @Test fun warmStartup() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 10,
        startupMode = mode,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test fun scrollApps() = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.findObject(By.text("Apps"))?.click()
            device.waitForIdle()
        },
    ) {
        device.findObject(By.scrollable(true))?.apply {
            setGestureMargin(device.displayWidth / 5)
            fling(Direction.DOWN)
            fling(Direction.UP)
        }
    }

    private companion object {
        const val TARGET = "app.wiis.launcher"
    }
}
