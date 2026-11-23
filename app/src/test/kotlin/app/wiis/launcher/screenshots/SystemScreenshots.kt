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
import app.wiis.launcher.system.SettingsPanel
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

    /** Home with the settings popup open on [page]; moving through the categories changes the page. */
    private var state by mutableStateOf(HomeUiState(section = Section.Games, library = LibraryStatus.Ready))

    private fun open(page: String) {
        state = state.copy(settings = page)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard); ready = true }
            if (ready) {
                WiisRoot {
                    HeroHomeScreen(state, DeviceStatus(batteryPercent = 76, online = true, wifi = true), NoActions) { p, h ->
                        SettingsPanel(p, onPage = { state = state.copy(settings = it) }, onPickRomFolder = {}, height = h)
                    }
                }
            }
        }
        settle()
    }

    private fun settle() = repeat(20) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle(); Thread.sleep(10) }

    @OptIn(ExperimentalTestApi::class)
    private fun go(key: Key, times: Int) = List(times) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(60)
        compose.waitForIdle()
        focusedText()
    }

    private fun shot(name: String) {
        settle()
        compose.onRoot().captureRoboImage(File(outDir, "$name.png").path)
    }

    /** What has focus: its text and place, or "nothing". */
    private fun focusedText(): String {
        val nodes = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
        if (nodes.isEmpty()) return "nothing"
        val n = nodes.first()
        val texts = n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString { it.text }
        val d = compose.density.density
        return (texts ?: "?") + " @ " + (n.boundsInRoot.left / d).toInt() + "," + (n.boundsInRoot.top / d).toInt()
    }

    /** The popup on Theme; Right into the page; Down to Appearance, which shows once focus moves into it. */
    @Test fun themePage() {
        open("theme")
        shot("system-1-theme-0")
        go(Key.DirectionRight, 1)
        shot("system-1-theme-1")
        go(Key.DirectionDown, 1)
        shot("system-1-theme-2")
    }

    /** Down the categories to Sounds (the page follows), then into it: one card per sound, each scrolled in whole. */
    @Test fun soundsPage() {
        open("theme")
        go(Key.DirectionDown, 1)
        shot("system-2-sounds-0")
        go(Key.DirectionRight, 1)
        repeat(3) { i -> go(Key.DirectionDown, 1); shot("system-2-sounds-${i + 1}") }
    }

    /** Box art with its three sources (SteamGridDB third, waiting for a key), and the Widgets page. */
    @Test fun artAndWidgetsPages() {
        open("art")
        shot("system-4-box-art")
        // Down the categories to Widgets: its page follows focus.
        go(Key.DirectionDown, 1)
        check(state.settings == "widgets") { "after Down: ${state.settings}" }
        shot("system-6-widgets")
        // Back up to Box art and into it, down to SteamGridDB.
        go(Key.DirectionUp, 1)
        go(Key.DirectionRight, 1)
        repeat(6) { go(Key.DirectionDown, 1) }
        shot("system-5-box-art-steamgriddb")
    }

    /**
     * Inside the popup: Left from a page comes back to its category, and focus never leaves the
     * popup (Down past the last item stays there instead of reaching the dock behind it).
     */
    @Test fun navigation() {
        open("theme")
        val into = go(Key.DirectionRight, 1) + go(Key.DirectionDown, 6)
        val back = go(Key.DirectionLeft, 1)
        val categories = go(Key.DirectionDown, 6) + go(Key.DirectionUp, 6)
        println("NAV settings: into ${into.joinToString(" | ")} || back ${back.joinToString()} || categories ${categories.joinToString(" | ")}")
        check(into.first().startsWith("Import")) { "into the page: $into" }
        check(into.none { it.startsWith("Home") || it.startsWith("Apps") || it.startsWith("Collections") }) { "left the popup: $into" }
        check(back.first().startsWith("Theme")) { "back to the category: $back" }
        check(categories.none { it.startsWith("Home") || it.startsWith("Apps") || it.startsWith("Collections") }) { "left the popup: $categories" }
        shot("system-3-settings-nav")
    }

    private object NoActions : HeroActions {
        override fun selectSection(section: Section) = Unit
        override fun cycleSection(delta: Int) = Unit
        override fun onStart() = Unit
        override fun openSystem(id: String) = Unit
        override fun closeView() = false
        override fun openCollection(id: String) = Unit
        override fun focusCollection(entry: app.wiis.launcher.home.CollectionEntry) = Unit
        override fun keepCollection(id: String) = Unit
        override fun renameCollection(id: String, name: String) = Unit
        override fun hideCollection(id: String) = Unit
        override fun addToCollection(id: String, game: Game) = Unit
        override fun removeFromCollection(id: String, game: Game) = Unit
        override fun createCollection(name: String, game: Game) = Unit
        override fun openSettings(page: String?) = Unit
        override fun closeSettings() = Unit
        override fun settingsPage(page: String) = Unit
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
