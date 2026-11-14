package app.wiis.launcher.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import app.wiis.designsystem.wiisBackground
import app.wiis.designsystem.LocalWiis
import app.wiis.emulator.Game
import app.wiis.emulator.ra.RaAchievement
import app.wiis.emulator.ra.RaCompletionEntry
import app.wiis.emulator.ra.RaGameProgress
import app.wiis.emulator.ra.RaResult
import app.wiis.launcher.companion.AchievementsPage
import app.wiis.launcher.companion.CompanionPage
import app.wiis.launcher.companion.CompanionPager
import app.wiis.launcher.companion.GameStatsPage
import app.wiis.launcher.companion.SystemDetailsPage
import app.wiis.launcher.companion.SystemStatsPage
import app.wiis.launcher.stats.GameStats
import app.wiis.launcher.stats.SystemStats
import app.wiis.launcher.stats.Week
import app.wiis.launcher.ui.ImageSource
import app.wiis.launcher.ui.LocalImageSource
import app.wiis.system.AppEntry
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The bottom screen's pages (538 × 468 dp) with sample stats and achievements. Run with -Pscreenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w538dp-h468dp-land-xhdpi", application = Application::class)
class CompanionScreenshots {
    @get:Rule val compose = createComposeRule()
    private val spec = ThemeSpec()
    private val outDir = File(System.getProperty("wiis.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
    private val min = 60_000L
    private val pages = listOf(CompanionPage.Details, CompanionPage.Stats, CompanionPage.Achievements)

    private val gameStats = GameStats(192 * min, 9, 21 * min, 51 * min, 1_759_400_000_000, 1_763_100_000_000, Week(listOf(0, 35 * min, 0, 60 * min, 48 * min, 0, 0), 4))
    private val names = listOf("First Steps", "Night Flight", "Full Satchel", "No Detours", "Early Bird", "Collector", "Rain or Shine", "Long Haul", "Perfect Week", "Postmaster")
    private val progress = RaGameProgress(1, "Star Courier", "Nintendo DS", null, names.mapIndexed { i, n ->
        RaAchievement(i, n, "Sample description for $n", listOf(5, 10, 10, 25, 5, 10, 25, 50, 10, 25)[i], "b$i", i, if (i % 3 != 2) "2026-10-0${1 + i % 8} 12:00:00" else null, null)
    })
    private fun game(t: String, s: String) = Game("sample:$s/$t", "sample", t, t, "$t.rom", s, 1)

    @Test fun gameStats() = shoot("bottom-1-game-stats", 1) { GameStatsPage(gameStats, LocalWiis.current.systemColor("nds")) }
    @Test fun gameStatsDark() = shoot("bottom-2-game-stats-dark", 1, dark = true) { GameStatsPage(gameStats, LocalWiis.current.systemColor("nds")) }
    @Test fun gameAchievements() = shoot("bottom-3-game-achievements", 2) { AchievementsPage(RaResult.OfGame(progress), LocalWiis.current.systemColor("nds"), {}, {}) }
    @Test fun gameAchievementsDark() = shoot("bottom-4-game-achievements-dark", 2, dark = true) { AchievementsPage(RaResult.OfGame(progress), LocalWiis.current.systemColor("nds"), {}, {}) }
    @Test fun notSetUp() = shoot("bottom-5-not-set-up", 2) { AchievementsPage(RaResult.NotSetUp, LocalWiis.current.systemColor("nds"), {}, {}) }
    @Test fun systemDetails() = shoot("bottom-6-system-details", 0) { SystemDetailsPage("nds", "Nintendo DS", 6, 4, "melonDS") }
    @Test fun systemStats() = shoot("bottom-7-system-stats", 1) {
        SystemStatsPage(SystemStats(366 * min, 4, 6, listOf(game("Star Courier", "nds") to 192 * min, game("Puzzle Parade", "nds") to 95 * min, game("Garden Days", "nds") to 41 * min), gameStats.week), "Nintendo DS", LocalWiis.current.systemColor("nds"))
    }
    @Test fun systemAchievements() = shoot("bottom-8-system-achievements", 2) {
        AchievementsPage(
            RaResult.OfSystem("Nintendo DS", listOf("Star Courier" to 6, "Puzzle Parade" to 4, "Sky Mail" to 7, "Tiny Tactics" to 2).mapIndexed { i, (t, n) -> RaCompletionEntry(i, t, null, 18, "Nintendo DS", 12, n, 0, null) }),
            LocalWiis.current.systemColor("nds"), {}, {},
        )
    }

    private fun shoot(name: String, page: Int, dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WiisTheme(spec = spec.resolve(dark), fonts = fonts(), feedback = UiFeedback.None) {
                CompositionLocalProvider(LocalImageSource provides images) {
                    val style = LocalWiis.current
                    Box(Modifier.fillMaxSize().wiisBackground(style)) {
                        val state = rememberPagerState(initialPage = page) { pages.size }
                        CompanionPager(pages, state, showClock = true) { p -> if (pages.indexOf(p) == page) content() }
                    }
                }
            }
        }
        repeat(20) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle(); Thread.sleep(10) }
        compose.onRoot().captureRoboImage(File(outDir, "$name.png").path)
    }

    private fun fonts(): WiisFonts {
        val dir = System.getProperty("wiis.fonts")?.let(::File)?.takeIf { it.isDirectory } ?: return WiisFonts()
        val fonts = dir.listFiles().orEmpty().mapNotNull { f -> Regex("Poppins-(\\d+)\\.ttf").matchEntire(f.name)?.let { Font(f, FontWeight(it.groupValues[1].toInt())) } }
        return if (fonts.isEmpty()) WiisFonts() else WiisFonts(FontFamily(fonts), FontFamily(fonts))
    }

    private val images = object : ImageSource {
        override val artUpdates: Flow<String> = emptyFlow()
        override suspend fun art(game: Game, sizePx: Int): Bitmap? = null
        override suspend fun icon(app: AppEntry): Bitmap? = null
        override suspend fun remote(url: String, sizePx: Int): Bitmap {
            val locked = url.contains("_lock")
            return Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { b ->
                val c = Canvas(b)
                c.drawColor(if (locked) 0xFF8C8A99.toInt() else spec.systemColor("nds").toArgb())
                c.drawCircle(32f, 32f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
            }
        }
    }
}
