package app.wiis.launcher.screenshots

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Process
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wiis.designsystem.ThemeSpec
import app.wiis.designsystem.UiFeedback
import app.wiis.designsystem.WiisFonts
import app.wiis.designsystem.WiisTheme
import app.wiis.designsystem.mix
import app.wiis.emulator.Game
import app.wiis.launcher.home.CollectionEntry
import app.wiis.launcher.home.HeroActions
import app.wiis.launcher.home.HeroFocus
import app.wiis.launcher.home.HeroHomeScreen
import app.wiis.launcher.home.HomeModel
import app.wiis.launcher.home.HomeUiState
import app.wiis.launcher.home.HomeView
import app.wiis.launcher.home.HomeViewModel
import app.wiis.launcher.home.LibraryStatus
import app.wiis.launcher.home.Section
import app.wiis.launcher.home.SystemEntry
import app.wiis.launcher.ui.ImageSource
import app.wiis.launcher.ui.LocalImageSource
import app.wiis.system.AppEntry
import app.wiis.system.DeviceStatus
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders the Draft 3 top screen at the Thor's size (833 × 468 dp) with the workbench's sample
 * library and stand-in covers, so the result can be laid next to the workbench mockup.
 *
 *   ./gradlew :app:testDebugUnitTest -Pscreenshots -PwiisFonts=<folder with Fredoka-700.ttf, Nunito-400.ttf…>
 *
 * Images land in app/build/screenshots/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w833dp-h468dp-land-xhdpi", application = Application::class)
class HeroScreenshots {
    @get:Rule val compose = createComposeRule()

    private val spec = ThemeSpec()
    private val outDir = File(System.getProperty("wiis.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    @Test fun homeHero() = shoot("top-1-home-hero", home(focus = HomeViewModel.KEY_HERO))

    @Test fun homeSystem() = shoot("top-2-home-system", home(focus = HomeViewModel.sysKey("nds")))

    @Test fun homePinned() = shoot("top-3-home-pinned", home(focus = HomeViewModel.pinKey(game("gc", "Coral Drift"))))

    @Test fun homeNav() = shoot("top-4-home-nav", home(focus = HomeViewModel.navKey(Section.Apps)))

    @Test fun systemView() {
        val games = HomeModel.gamesFor("nds", library)
        shoot("top-5-system-nds", home(focus = HomeViewModel.gridKey(games.first())).copy(view = HomeView.InSystem("nds"), viewGames = games))
    }

    @Test fun apps() = shoot("top-6-apps", home(focus = null).copy(section = Section.Apps))

    @Test fun homeHeroDark() = shoot("top-7-home-hero-dark", home(focus = HomeViewModel.KEY_HERO), dark = true)

    @Test fun homeSystemDark() = shoot("top-8-home-system-dark", home(focus = HomeViewModel.sysKey("nds")), dark = true)

    /** Nothing pinned yet: the bottom row is one card that says how to pin. */
    @Test fun homeNoPins() = shoot("top-9-home-no-pins", home(focus = HomeViewModel.KEY_HERO, pinned = emptyList()))

    /** The systems grid moved down to its third row: rows 2 and 3, nothing of row 1 above them. */
    @Test fun homeThirdRow() = shoot("top-10-home-third-row", home(focus = HomeViewModel.sysKey("ps2")))

    /** The last pinned game: the row scrolls whole tiles, the first ones leave completely. */
    @Test fun homeLastPin() = shoot("top-11-home-last-pin", home(focus = HomeViewModel.pinKey(game("ps2", "Kite Festival"))))

    /** Many apps: only rows that fit completely, nothing cut off above the dock. */
    @Test fun appsMany() = shoot(
        "top-12-apps-many",
        home(focus = null).copy(section = Section.Apps, apps = (1..22).map { app(appColors.keys.elementAt(it % appColors.size) + " $it") }),
    )

    // ---- Collections and the Select menu (0.6.0) -------------------------------------------------

    private val sampleCollections: List<CollectionEntry> by lazy {
        fun pick(vararg titles: String) = titles.map { t -> library.first { it.title == t } }
        listOf(
            CollectionEntry("c:1", "Favourites", pick("Star Courier", "Coral Drift", "Night Market", "Pixel Quest"), suggested = false),
            CollectionEntry("s:k:tiny", "Tiny", pick("Tiny Kingdom", "Tiny Tactics"), suggested = false),
            CollectionEntry("s:t:paper", "Paper", pick("Paper Pilots", "Paper Planets"), suggested = true),
            CollectionEntry("s:t:sky", "Sky", pick("Sky Garden", "Sky Mail"), suggested = true),
            CollectionEntry("s:t:lantern", "Island and Lantern", pick("Lumen Island", "Lantern Keep", "Hover Cup"), suggested = true),
        )
    }

    private fun collectionsHome(focus: String?) = home(focus = focus).copy(section = Section.Collections, collections = sampleCollections)

    @Test fun collections() = shoot("top-16-collections", collectionsHome(HomeViewModel.colKey("c:1")))

    @Test fun collectionsSuggested() = shoot("top-17-collections-suggested", collectionsHome(HomeViewModel.colKey("s:t:paper")))

    @Test fun collectionsEmpty() = shoot("top-18-collections-empty", home(focus = null).copy(section = Section.Collections))

    @Test fun collectionView() {
        val c = sampleCollections.first()
        shoot("top-19-collection-open", collectionsHome(HomeViewModel.gridKey(c.games.first())).copy(view = HomeView.InCollection(c.id), viewGames = c.games))
    }

    /** Select on a game, on a collection, then Add to collection from the game's menu. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun selectMenus() {
        val live = Live(home(focus = HomeViewModel.KEY_HERO).copy(collections = sampleCollections))
        render({ live.state }, live)
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.ButtonSelect) }
        settle()
        compose.onRoot().captureRoboImage(File(outDir, "top-20-menu-game.png").path)
        // Down to Add to collection, A.
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.ButtonA) }
        settle()
        compose.onRoot().captureRoboImage(File(outDir, "top-21-add-to-collection.png").path)
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.ButtonB) }
        settle()
        check(where(live) == "Star Courier @ 28,68") { "focus after closing: ${where(live)}" }
        live.selectSection(Section.Collections)
        live.state = live.state.copy(heroFocus = HeroFocus(HomeViewModel.colKey("s:t:paper"), 77L))
        settle()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.ButtonSelect) }
        settle()
        compose.onRoot().captureRoboImage(File(outDir, "top-22-menu-collection.png").path)
    }

    /** Start: the settings popup over Home (its pages come from the app; the test shows the frame). */
    @Test fun settingsPopup() = shoot("top-23-settings-frame", home(focus = HomeViewModel.KEY_HERO).copy(settings = "theme"))

    /** X on the Jump back in card, then X on that game in the row: pinned, then unpinned. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun pinWithX() {
        var state by mutableStateOf(home(focus = HomeViewModel.KEY_HERO, pinned = emptyList()))
        val actions = object : HeroActions by NoActions {
            override fun togglePin(game: Game, fromRow: Boolean) {
                val pinned = if (state.pinned.any { it.uri == game.uri }) state.pinned.filter { it.uri != game.uri } else listOf(game) + state.pinned
                state = state.copy(pinned = pinned, heroFocus = if (fromRow) HeroFocus(HomeViewModel.KEY_HERO, 9L) else state.heroFocus)
            }
            override fun focusApplied() { state = state.copy(heroFocus = null) }
        }
        render({ state }, actions)
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.ButtonX) }
        settle()
        compose.onRoot().captureRoboImage(File(outDir, "top-13-pinned-with-x.png").path)
        check(state.pinned.map { it.title } == listOf("Star Courier")) { "pinned: ${state.pinned.map { it.title }}" }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        compose.onRoot().captureRoboImage(File(outDir, "top-14-pin-focused.png").path)
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.ButtonX) }
        settle()
        compose.onRoot().captureRoboImage(File(outDir, "top-15-unpinned.png").path)
        check(state.pinned.isEmpty()) { "still pinned: ${state.pinned.map { it.title }}" }
    }

    // ---- Navigation audit: the controller paths, as fast as a held button repeats ---------------

    /** The home screen driven like the view model drives it, with a trail of what took focus. */
    private inner class Live(initial: HomeUiState) : HeroActions by NoActions {
        var state by mutableStateOf(initial)
        var last = ""
        private var token = 100L
        override fun selectSection(section: Section) {
            state = state.copy(section = section, view = HomeView.Home, viewGames = emptyList(), heroFocus = if (section == Section.Games) HeroFocus(HomeViewModel.KEY_HERO, token++) else null)
        }
        override fun cycleSection(delta: Int) { val o = Section.entries; selectSection(o[(o.indexOf(state.section) + delta).mod(o.size)]) }
        override fun openSystem(id: String) {
            val list = HomeModel.gamesFor(id, library)
            state = state.copy(view = HomeView.InSystem(id), viewGames = list, heroFocus = list.firstOrNull()?.let { HeroFocus(HomeViewModel.gridKey(it), token++) })
        }
        override fun closeView(): Boolean {
            val v = state.view as? HomeView.InSystem ?: return false
            state = state.copy(view = HomeView.Home, viewGames = emptyList(), heroFocus = HeroFocus(HomeViewModel.sysKey(v.systemId), token++))
            return true
        }
        override fun openSettings(page: String?) { state = state.copy(settings = page ?: "theme") }
        override fun closeSettings() { state = state.copy(settings = null) }
        override fun focusGame(game: Game) { last = game.title }
        override fun focusSystem(entry: SystemEntry) { last = entry.name }
        override fun focusApp(app: AppEntry) { last = app.label }
        override fun focusApplied() { state = state.copy(heroFocus = null) }
    }

    /** "title @ x,y" of the focused element, in dp. */
    private fun where(live: Live): String {
        val nodes = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
        if (nodes.isEmpty()) return "nothing"
        val b = nodes.first().boundsInRoot
        val d = compose.density.density
        val y = (b.top / d).roundToInt()
        val label = if (y > 390) "dock" else live.last
        return "$label @ ${(b.left / d).roundToInt()},$y"
    }

    /** [times] presses of [key], [gapMs] apart (a held button repeats about every 50 ms). */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    private fun walk(live: Live, key: Key, times: Int, gapMs: Long = 50): List<String> = List(times) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(gapMs)
        compose.waitForIdle()
        where(live)
    }

    /** Hold Down from Jump back in: the first pinned game, then the dock, and it stays there (0.5.4 bounced along the row). */
    @Test fun navHoldDown() {
        val live = Live(home(focus = HomeViewModel.KEY_HERO))
        render({ live.state }, live)
        val trail = walk(live, Key.DirectionDown, 16)
        println("NAV hold down from the card: " + trail.joinToString(" | "))
        check(trail.first().startsWith("Tiny Kingdom")) { "card down: ${trail.first()}" }
        check(trail.drop(1).all { it.startsWith("dock") }) { "held down: $trail" }
        compose.onRoot().captureRoboImage(File(outDir, "nav-1-hold-down.png").path)
    }

    /** Up from the right end of the pinned row goes to what is above it, not to the far-left column. */
    @Test fun navUpFromRowEnd() {
        val live = Live(home(focus = HomeViewModel.sysKey("ps2")))
        render({ live.state }, live)
        println("NAV start: " + where(live))
        val down = walk(live, Key.DirectionDown, 1)
        val right = walk(live, Key.DirectionRight, 9)
        val up = walk(live, Key.DirectionUp, 4)
        println("NAV row end: down ${down.joinToString()} | right ${right.joinToString(" | ")} | up ${up.joinToString(" | ")}")
        check(up.first().startsWith("Nintendo DS")) { "up from the row's end: ${up.first()}" }
    }

    /** Hold Up from the dock: the pinned row, then the card or a system above it, then it stays. */
    @Test fun navHoldUpFromDock() {
        val live = Live(home(focus = HomeViewModel.navKey(Section.Collections)))
        render({ live.state }, live)
        val trail = walk(live, Key.DirectionUp, 10)
        println("NAV hold up from the dock: " + trail.joinToString(" | "))
        check(!trail.last().startsWith("dock") && trail.takeLast(4).toSet().size == 1) { "held up: $trail" }
    }

    /** Inside All games: two rows; holding Down and Up keeps the column, then the dock. */
    @Test fun navAllGames() {
        val live = Live(home(focus = HomeViewModel.KEY_HERO))
        render({ live.state }, live)
        live.openSystem(HomeModel.ALL)
        settle()
        walk(live, Key.DirectionRight, 2)
        val down = walk(live, Key.DirectionDown, 8)
        val up = walk(live, Key.DirectionUp, 8)
        println("NAV all games: down ${down.joinToString(" | ")} || up ${up.joinToString(" | ")}")
        val column = { t: String -> t.substringAfter("@ ").substringBefore(",") }
        val inGrid = (down + up).filter { !it.startsWith("dock") }
        check(inGrid.map(column).toSet().size == 1) { "column changed: $inGrid" }
        compose.onRoot().captureRoboImage(File(outDir, "nav-2-all-games.png").path)
    }

    /** Everything else on Home and Apps, printed for review (NAV lines in the test log). */
    @Test fun navAudit() {
        val live = Live(home(focus = HomeViewModel.KEY_HERO))
        render({ live.state }, live)
        fun log(name: String, key: Key, times: Int, gap: Long = 50) = println("NAV $name: " + walk(live, key, times, gap).joinToString(" | "))
        log("card right", Key.DirectionRight, 5)
        log("grid down", Key.DirectionDown, 4)
        log("up from below", Key.DirectionUp, 5)
        log("left to the card", Key.DirectionLeft, 4)
        log("down to the row", Key.DirectionDown, 1)
        log("row right", Key.DirectionRight, 10)
        log("row left", Key.DirectionLeft, 10)
        log("to the dock", Key.DirectionDown, 2)
        log("dock right", Key.DirectionRight, 10)
        log("dock up", Key.DirectionUp, 4)
        log("back down", Key.DirectionDown, 4)
        log("dock left", Key.DirectionLeft, 10)
        log("dock up from the left", Key.DirectionUp, 4)
        live.selectSection(Section.Apps)
        settle()
        println("NAV opened apps: " + where(live))
        log("apps down", Key.DirectionDown, 4)
        log("apps up", Key.DirectionUp, 4)
        log("apps right", Key.DirectionRight, 6)
        log("apps down again", Key.DirectionDown, 3)
        log("apps up from the dock", Key.DirectionUp, 3)
    }

    // ---- Sample data (design/workbench.html: LIB, RECENT, APPS, DOCK) ------------------------

    private val lib = listOf(
        "n64" to listOf("Tiny Kingdom" to 1.42f, "Rally Rush 64" to 1.42f, "Moon Base" to 1.42f, "Paper Pilots" to null),
        "gba" to listOf("Pixel Quest" to 1f, "Sky Garden" to 1f, "Rail Rider" to null),
        "nds" to listOf("Star Courier" to 1.1f, "Puzzle Parade" to 1.1f, "Garden Days" to 1.1f, "Pocket Atlas" to null, "Sky Mail" to 1.1f, "Tiny Tactics" to 1.1f),
        "n3ds" to listOf("Lumen Island" to 1.1f, "Paper Planets" to 1.1f),
        "gc" to listOf("Coral Drift" to 0.71f, "Lantern Keep" to 0.71f, "Hover Cup" to 0.71f),
        "ps2" to listOf("Night Market" to 0.71f, "Iron Tide" to 0.71f, "Echo Run" to null, "Kite Festival" to 0.71f),
    )
    private val recentOrder = listOf("nds" to 0, "n64" to 0, "gc" to 0, "ps2" to 0, "gba" to 0, "n3ds" to 0, "nds" to 2, "gc" to 1, "ps2" to 3)
    private val names = mapOf(
        "n64" to "Nintendo 64", "gba" to "Game Boy Advance", "nds" to "Nintendo DS",
        "n3ds" to "Nintendo 3DS", "gc" to "GameCube", "ps2" to "PlayStation 2",
    )
    private val ratios = mutableMapOf<String, Float?>()
    private val library: List<Game> = lib.flatMap { (sys, games) ->
        games.map { (title, ratio) -> game(sys, title).also { ratios[it.uri] = ratio } }
    }

    private fun game(sys: String, title: String) = Game("sample:$sys/$title", "sample", title, title, "$title.rom", sys, 1)

    private val appColors = mapOf(
        "Browser" to 0xFF3FB4E8, "Video" to 0xFFE5484D, "Store" to 0xFF3DBA74, "Music" to 0xFF9A6BFF, "Files" to 0xFFF2A33A,
        "Settings" to 0xFF6F7DFF, "Camera" to 0xFF5BBF7A, "Clock" to 0xFFF07E4B, "Gallery" to 0xFF45C2B1,
    )

    private fun app(label: String) = AppEntry(label, ComponentName("sample.${label.lowercase()}", "sample.Main"), Process.myUserHandle())

    /** [pinned]: the bottom row; by default the workbench's recent games after the first. */
    private fun home(focus: String?, pinned: List<Game>? = null): HomeUiState {
        val played = recentOrder.mapIndexed { i, (sys, n) -> lib.first { it.first == sys }.second[n].first to (1_000L - i) }.toMap()
        val (hero, recent) = HomeModel.heroAndRecent(library, { played[it.title] ?: 0L }, spec.layout.recentCount, spec.layout.showRecent)
        val systems = HomeModel.systems(library, names, emptyMap(), spec.layout.systemOrder)
        return HomeUiState(
            section = Section.Games,
            library = LibraryStatus.Ready,
            apps = listOf("Settings", "Files", "Browser", "Music", "Camera", "Store", "Clock", "Gallery").map(::app),
            hero = hero,
            heroPlayedMillis = (3 * 60 + 12) * 60_000L,
            pinned = pinned ?: recent,
            systems = systems,
            allPreview = HomeModel.allTilePreview(hero, recent, systems),
            systemSamples = HomeModel.systemSamples(library, { played[it.title] ?: 0L }, { ratios[it.uri] != null }, hero),
            heroFocus = focus?.let { HeroFocus(it, 1L) },
            dock = listOf("Browser", "Video", "Store", "Music", "Files").map(::app),
        )
    }

    // ---- Rendering --------------------------------------------------------------------------

    /** R1 on Home: frames of the pill gliding to Apps while the labels fold and unfold. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun dockGlide() {
        var state by mutableStateOf(home(focus = HomeViewModel.KEY_HERO))
        val actions = object : HeroActions by NoActions {
            override fun cycleSection(delta: Int) {
                val order = Section.entries
                state = state.copy(section = order[(order.indexOf(state.section) + delta + order.size) % order.size], heroFocus = null)
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard); ready = true }
            if (ready) {
                WiisTheme(spec = spec, fonts = fonts(), feedback = UiFeedback.None) {
                    CompositionLocalProvider(LocalImageSource provides images) {
                        HeroHomeScreen(state, DeviceStatus(batteryPercent = 76, online = true, wifi = true), actions) { _, _ -> }
                    }
                }
            }
        }
        repeat(30) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle(); Thread.sleep(15) }
        compose.onRoot().captureRoboImage(File(outDir, "dock-0.png").path)
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.ButtonR1) }
        var t = 0L
        for (at in listOf(16L, 50L, 100L, 150L, 200L, 300L, 500L)) {
            compose.mainClock.advanceTimeBy(at - t); t = at
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(File(outDir, "dock-$at.png").path)
        }
    }

    private fun render(state: () -> HomeUiState, actions: HeroActions) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard); ready = true }
            if (ready) {
                WiisTheme(spec = spec, fonts = fonts(), feedback = UiFeedback.None) {
                    CompositionLocalProvider(LocalImageSource provides images) {
                        HeroHomeScreen(state(), DeviceStatus(batteryPercent = 76, online = true, wifi = true), actions) { _, _ -> }
                    }
                }
            }
        }
        settle()
    }

    private fun settle() = repeat(30) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle(); Thread.sleep(15) }

    private fun shoot(name: String, state: HomeUiState, dark: Boolean = false) {
        val theme = spec.resolve(dark)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            // Controller mode first, as on the Thor once a button has been pressed: in touch mode
            // Android doesn't let tiles take focus.
            val inputMode = LocalInputModeManager.current
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                inputMode.requestInputMode(InputMode.Keyboard)
                ready = true
            }
            if (ready) {
                WiisTheme(spec = theme, fonts = fonts(), feedback = UiFeedback.None) {
                    CompositionLocalProvider(LocalImageSource provides images) {
                        HeroHomeScreen(state, DeviceStatus(batteryPercent = 76, online = true, wifi = true), NoActions) { _, _ -> }
                    }
                }
            }
        }
        // Let focus land, springs settle and the background blur (off the main thread) finish.
        repeat(40) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            Thread.sleep(15)
        }
        val focused = compose.onAllNodes(isFocused()).fetchSemanticsNodes().size
        check(state.heroFocus == null || focused == 1) { "$name: expected one focused element, found $focused" }
        compose.onRoot().captureRoboImage(File(outDir, "$name.png").path)
    }

    private fun fonts(): WiisFonts {
        val dir = System.getProperty("wiis.fonts")?.let(::File)?.takeIf { it.isDirectory } ?: return WiisFonts()
        fun family(name: String): FontFamily {
            val fonts = dir.listFiles().orEmpty()
                .mapNotNull { f -> Regex("$name-(\\d+)\\.ttf").matchEntire(f.name)?.let { Font(f, FontWeight(it.groupValues[1].toInt())) } }
            return if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
        }
        return WiisFonts(display = family(spec.type.displayFont.replace(" ", "")), body = family(spec.type.bodyFont.replace(" ", "")))
    }

    private val images = object : ImageSource {
        override val artUpdates: Flow<String> = emptyFlow()
        override suspend fun art(game: Game, sizePx: Int): Bitmap? = ratios[game.uri]?.let { cover(game, it, sizePx) }
        override suspend fun icon(app: AppEntry): Bitmap = appIcon(app)
    }

    /** The workbench's stand-in cover (coverHtml): gradient, a sun-like disc, a band with the system, the title. */
    private fun cover(game: Game, ratio: Float, longest: Int): Bitmap {
        val w = if (ratio >= 1f) longest else (longest * ratio).roundToInt()
        val h = if (ratio >= 1f) (longest / ratio).roundToInt() else longest
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val color = spec.systemColor(game.systemId)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dx = 0.342f * w // 160deg
        paint.shader = LinearGradient(w / 2f - dx / 2, 0f, w / 2f + dx / 2, h.toFloat(), mix(color, Color.White, 0.25f).toArgb(), mix(color, Color.Black, 0.35f).toArgb(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        val seed = fnv(game.title)
        val x = 20 + (seed % 60)
        val y = 20 + ((seed shr 8) % 50)
        val r = 25 + ((seed shr 16) % 30)
        val top = h * 0.13f
        paint.color = mix(color, Color(0xFFFFD24A), 0.5f).toArgb()
        c.drawCircle(w * x / 100f, top + (h - top) * y / 100f, hypot(w.toFloat(), h - top) / 1.4142f * r / 100f, paint)
        paint.color = 0x59000000
        c.drawRect(0f, 0f, w.toFloat(), top, paint)
        paint.color = android.graphics.Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = top * 0.55f
        c.drawText(game.systemId.uppercase(), w * 0.06f, top * 0.72f, paint)
        paint.textSize = min(max(w / 9f, 22f), w / 6f)
        paint.setShadowLayer(0f, 0f, paint.textSize * 0.12f, 0x59000000)
        val words = game.title.split(" ")
        var line = ""
        val lines = mutableListOf<String>()
        for (word in words) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(next) > w * 0.86f && line.isNotEmpty()) { lines += line; line = word } else line = next
        }
        lines += line
        lines.reversed().forEachIndexed { i, text -> c.drawText(text, w * 0.07f, h * 0.92f - i * paint.textSize * 1.05f, paint) }
        return bmp
    }

    private fun appIcon(app: AppEntry): Bitmap {
        val size = 144
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = (appColors[app.label] ?: 0xFF777777).toInt()
        c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.3f, size * 0.3f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = size * 0.45f
        paint.textAlign = Paint.Align.CENTER
        c.drawText(app.label.take(1), size / 2f, size * 0.66f, paint)
        return bmp
    }

    private fun fnv(s: String): Int {
        var h = 2166136261L
        for (ch in s) {
            h = h xor ch.code.toLong()
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return (h and 0x7FFFFFFF).toInt()
    }

    private object NoActions : HeroActions {
        override fun selectSection(section: Section) = Unit
        override fun cycleSection(delta: Int) = Unit
        override fun onStart() = Unit
        override fun openSystem(id: String) = Unit
        override fun closeView() = false
        override fun openCollection(id: String) = Unit
        override fun focusCollection(entry: CollectionEntry) = Unit
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
