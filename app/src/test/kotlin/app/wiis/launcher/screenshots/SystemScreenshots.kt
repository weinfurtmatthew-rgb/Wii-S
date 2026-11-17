package app.wiis.launcher.screenshots

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wiis.emulator.Game
import app.wiis.launcher.home.HeroActions
import app.wiis.launcher.home.HeroHomeScreen
import app.wiis.launcher.home.HomeUiState
import app.wiis.launcher.home.LibraryStatus
import app.wiis.launcher.home.Section
import app.wiis.launcher.home.SystemEntry
import app.wiis.launcher.system.SystemScreen
import app.wiis.launcher.ui.WiisRoot
import app.wiis.system.AppEntry
import app.wiis.system.DeviceStatus
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The System section inside Home, with the real application and its graph, moved through with the
 * controller: whole cards only, a card that doesn't fit waits below until focus moves into it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w833dp-h468dp-land-xhdpi")
class SystemScreenshots {
    @get:Rule val compose = createComposeRule()

    private val outDir = File(System.getProperty("wiis.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    @OptIn(ExperimentalTestApi::class)
    private fun walk(name: String, downs: Int, right: Int = 0) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard); ready = true }
            if (ready) {
                WiisRoot {
                    HeroHomeScreen(HomeUiState(section = Section.System, library = LibraryStatus.Ready), DeviceStatus(batteryPercent = 76, online = true, wifi = true), NoActions) {
                        SystemScreen(onPickRomFolder = {})
                    }
                }
            }
        }
        settle()
        repeat(right) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }; settle() }
        if (right > 0) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.ButtonA) }; settle() }
        compose.onRoot().captureRoboImage(File(outDir, "$name-0.png").path)
        for (i in 1..downs) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            settle()
            compose.onRoot().captureRoboImage(File(outDir, "$name-$i.png").path)
        }
    }

    private fun settle() = repeat(20) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle(); Thread.sleep(10) }

    /** Theme, then Import, then the Appearance card, which only shows once focus moves into it. */
    @Test fun themePage() = walk("system-1-theme", downs = 3)

    /** Sounds (A on its tab): one card per sound, each scrolled in whole. */
    @Test fun soundsPage() = walk("system-2-sounds", downs = 4, right = 1)

    private object NoActions : HeroActions {
        override fun selectSection(section: Section) = Unit
        override fun cycleSection(delta: Int) = Unit
        override fun onStart() = Unit
        override fun onSelect() = Unit
        override fun openSystem(id: String) = Unit
        override fun closeSystem() = false
        override fun focusGame(game: Game) = Unit
        override fun focusSystem(entry: SystemEntry) = Unit
        override fun focusApp(app: AppEntry) = Unit
        override fun launchGame(game: Game) = Unit
        override fun openPicker(game: Game) = Unit
        override fun togglePin(game: Game, fromRow: Boolean) = Unit
        override fun launchApp(app: AppEntry) = Unit
        override fun openDockPicker(slot: Int) = Unit
        override fun focusApplied() = Unit
        override fun quietMoves() = Unit
        override fun pickFolder() = Unit
        override fun rescan() = Unit
    }
}
