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
import androidx.compose.ui.test.isFocused
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

    @Test fun homeRecent() = shoot("top-3-home-recent", home(focus = HomeViewModel.recentKey(game("gc", "Coral Drift"))))

    @Test fun homeNav() = shoot("top-4-home-nav", home(focus = HomeViewModel.navKey(Section.Apps)))

    @Test fun systemView() {
        val games = HomeModel.gamesFor("nds", library)
        shoot("top-5-system-nds", home(focus = HomeViewModel.gridKey(games.first())).copy(view = HomeView.InSystem("nds"), viewGames = games))
    }

    @Test fun apps() = shoot("top-6-apps", home(focus = null).copy(section = Section.Apps))

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

    private fun home(focus: String?): HomeUiState {
        val played = recentOrder.mapIndexed { i, (sys, n) -> lib.first { it.first == sys }.second[n].first to (1_000L - i) }.toMap()
        val (hero, recent) = HomeModel.heroAndRecent(library, { played[it.title] ?: 0L }, spec.layout.recentCount, spec.layout.showRecent)
        val systems = HomeModel.systems(library, names, emptyMap(), spec.layout.systemOrder)
        return HomeUiState(
            section = Section.Games,
            library = LibraryStatus.Ready,
            apps = listOf("Settings", "Files", "Browser", "Music", "Camera", "Store", "Clock", "Gallery").map(::app),
            hero = hero,
            heroPlayedMillis = (3 * 60 + 12) * 60_000L,
            recent = recent,
            systems = systems,
            allPreview = HomeModel.allTilePreview(hero, recent, systems),
            heroFocus = focus?.let { HeroFocus(it, 1L) },
            dock = listOf("Browser", "Video", "Store", "Music", "Files").map(::app),
        )
    }

    // ---- Rendering --------------------------------------------------------------------------

    private fun shoot(name: String, state: HomeUiState) {
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
                WiisTheme(spec = spec, fonts = fonts(), feedback = UiFeedback.None) {
                    CompositionLocalProvider(LocalImageSource provides images) {
                        HeroHomeScreen(state, DeviceStatus(batteryPercent = 76, online = true, wifi = true), NoActions) {}
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
        override fun onSelect() = Unit
        override fun openSystem(id: String) = Unit
        override fun closeSystem() = false
        override fun focusGame(game: Game) = Unit
        override fun focusSystem(entry: SystemEntry) = Unit
        override fun focusApp(app: AppEntry) = Unit
        override fun launchGame(game: Game) = Unit
        override fun openPicker(game: Game) = Unit
        override fun launchApp(app: AppEntry) = Unit
        override fun openDockPicker(slot: Int) = Unit
        override fun focusApplied() = Unit
        override fun quietMoves() = Unit
        override fun pickFolder() = Unit
        override fun rescan() = Unit
    }
}
