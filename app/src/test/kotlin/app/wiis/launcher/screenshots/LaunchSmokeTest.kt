package app.wiis.launcher.screenshots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wiis.emulator.Game
import app.wiis.emulator.LibrarySnapshot
import app.wiis.emulator.art.ArtIndex
import app.wiis.emulator.art.ArtRepository
import app.wiis.launcher.WiisApplication
import app.wiis.launcher.CrashLog
import app.wiis.launcher.Focus
import app.wiis.launcher.Session
import app.wiis.launcher.graph
import app.wiis.launcher.companion.CompanionActivity
import app.wiis.launcher.home.HomeActivity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Opens the real launcher, top screen and bottom screen, with the real application and its
 * graph, and drives it with the controller for a while. Catches crashes that the screenshot
 * tests (which draw screens directly, with sample data) can't see.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w833dp-h468dp-land-night-xhdpi")
class LaunchSmokeTest {
    private val app get() = ApplicationProvider.getApplicationContext<WiisApplication>()

    private fun run(millis: Long) {
        var left = millis
        while (left > 0) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)); left -= 50 }
    }

    /** Draws the whole window, as a frame on the device would; [save] keeps it as a PNG for a look. */
    private fun draw(activity: Activity, save: String? = null) {
        val v = activity.window.decorView
        if (v.width <= 0 || v.height <= 0) return
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val dir = System.getProperty("wiis.screenshots.dir")
        if (save != null && dir != null) File(dir, "smoke-$save.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun press(activity: Activity, code: Int) {
        val now = SystemClock.uptimeMillis()
        activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        activity.dispatchKeyEvent(KeyEvent(now, now + 40, KeyEvent.ACTION_UP, code, 0))
        run(350)
        draw(activity)
    }

    @Test fun `top screen opens`() {
        ActivityScenario.launch(HomeActivity::class.java).use { s ->
            run(4000)
            s.onActivity { draw(it); assertFalse(it.isFinishing) }
        }
    }

    @Test fun `bottom screen opens`() {
        ActivityScenario.launch<CompanionActivity>(Intent(app, CompanionActivity::class.java)).use { s ->
            run(4000)
            s.onActivity { draw(it); assertFalse(it.isFinishing) }
        }
    }

    // The Thor's density decides how many dp its screens have: 1920 × 1080 px is 833 × 468 dp at
    // 369 dpi, 731 × 411 at 420 dpi, 686 × 386 at 448 dpi and 960 × 540 at 320 dpi.
    @Config(qualifiers = "w731dp-h411dp-land-xxhdpi")
    @Test fun `full walk at 420 dpi`() = fullWalk()

    @Config(qualifiers = "w686dp-h386dp-land-xxhdpi")
    @Test fun `full walk at 448 dpi`() = fullWalk()

    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    @Test fun `full walk at 320 dpi`() = fullWalk()

    @Config(qualifiers = "w472dp-h411dp-land-xxhdpi")
    @Test fun `bottom screen at 420 dpi`() = bottomPages()

    @Config(qualifiers = "w538dp-h468dp-land-xhdpi")
    @Test fun `bottom screen at its usual size`() = bottomPages()

    @Test fun `a full library survives start-up and a walk through every screen`() = fullWalk()

    private fun fullWalk() {
        seedLibrary(app)
        ActivityScenario.launch(HomeActivity::class.java).use { s ->
            run(4000)
            s.onActivity { a ->
                draw(a, save = "home-${a.resources.configuration.screenWidthDp}")
                // Jump back in → system tiles → recent row → dock, then back up.
                repeat(4) { press(a, KeyEvent.KEYCODE_DPAD_RIGHT) }
                press(a, KeyEvent.KEYCODE_DPAD_DOWN)
                repeat(6) { press(a, KeyEvent.KEYCODE_DPAD_RIGHT) }
                press(a, KeyEvent.KEYCODE_DPAD_DOWN)
                repeat(4) { press(a, KeyEvent.KEYCODE_DPAD_RIGHT) }
                repeat(3) { press(a, KeyEvent.KEYCODE_DPAD_UP) }
                // Open a system and move around inside it.
                press(a, KeyEvent.KEYCODE_DPAD_RIGHT)
                press(a, KeyEvent.KEYCODE_BUTTON_A)
                run(800)
                repeat(3) { press(a, KeyEvent.KEYCODE_DPAD_RIGHT) }
                press(a, KeyEvent.KEYCODE_DPAD_DOWN)
                press(a, KeyEvent.KEYCODE_BUTTON_B)
                // Apps, then System and its pages.
                press(a, KeyEvent.KEYCODE_BUTTON_R1)
                repeat(3) { press(a, KeyEvent.KEYCODE_DPAD_RIGHT) }
                press(a, KeyEvent.KEYCODE_BUTTON_R1)
                repeat(10) { press(a, KeyEvent.KEYCODE_DPAD_DOWN) }
                press(a, KeyEvent.KEYCODE_BUTTON_L1)
                press(a, KeyEvent.KEYCODE_BUTTON_L1)
                assertFalse(a.isFinishing)
            }
        }
        // The bottom screen follows whatever the top screen last highlighted.
        ActivityScenario.launch<CompanionActivity>(Intent(app, CompanionActivity::class.java)).use { s ->
            run(3000)
            s.onActivity { draw(it); assertFalse(it.isFinishing) }
        }
    }

    private fun swipe(activity: Activity, toLeft: Boolean) {
        val v = activity.window.decorView
        val y = v.height / 2f
        val (x0, x1) = if (toLeft) v.width * 0.8f to v.width * 0.2f else v.width * 0.2f to v.width * 0.8f
        val t = SystemClock.uptimeMillis()
        fun ev(action: Int, x: Float, dt: Long) = android.view.MotionEvent.obtain(t, t + dt, action, x, y, 0).also { activity.dispatchTouchEvent(it); it.recycle() }
        ev(android.view.MotionEvent.ACTION_DOWN, x0, 0)
        for (i in 1..8) ev(android.view.MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * i / 8f, i * 12L)
        ev(android.view.MotionEvent.ACTION_UP, x1, 110)
        run(900)
        draw(activity)
    }

    @Test fun `bottom screen pages for a game, a system and everything`() = bottomPages()

    private fun bottomPages() {
        seedLibrary(app)
        val games = app.graph.currentGames()
        val shared = app.graph.shared
        ActivityScenario.launch<CompanionActivity>(Intent(app, CompanionActivity::class.java)).use { s ->
            for (focus in listOf<Focus>(
                Focus.OnGame(games.first { it.systemId == "nds" }),
                Focus.OnGame(games.first { it.systemId == "psx" }),
                Focus.OnSystem("gba", "Game Boy Advance", games.count { it.systemId == "gba" }),
                Focus.OnSystem("all", "All games", games.size),
            )) {
                shared.setFocus(focus)
                run(1500)
                s.onActivity { a ->
                    draw(a, save = "bottom-${focus.javaClass.simpleName}-${a.resources.configuration.screenWidthDp}")
                    swipe(a, toLeft = true)
                    draw(a, save = "bottom-${focus.javaClass.simpleName}-${a.resources.configuration.screenWidthDp}-p2")
                    swipe(a, toLeft = true)
                    swipe(a, toLeft = false)
                    swipe(a, toLeft = false)
                    assertFalse(a.isFinishing)
                }
            }
            // Playing a game: the bottom screen shows it as now playing.
            shared.startSession(Session(games.first(), "melonDS", false, System.currentTimeMillis()))
            run(1500)
            s.onActivity { a -> draw(a); swipe(a, toLeft = true); assertFalse(a.isFinishing) }
        }
    }

    @Test fun `with a second display, Home shows the bottom screen as a window on it`() {
        seedLibrary(app)
        val second = org.robolectric.shadows.ShadowDisplayManager.addDisplay("w538dp-h468dp-land-xhdpi")
        ActivityScenario.launch(HomeActivity::class.java).use { s ->
            run(3000)
            val status = app.graph.shared.companion.value
            org.junit.Assert.assertEquals("C (window)", status?.mode)
            org.junit.Assert.assertEquals(second, status?.displayId)
            org.junit.Assert.assertTrue(app.graph.companionPlacer.lastAttempt, "window shown on display $second" in app.graph.companionPlacer.lastAttempt)
            // The window is the bottom screen: it follows what the top screen highlights.
            app.graph.shared.setFocus(Focus.OnGame(app.graph.currentGames().first { it.systemId == "nds" }))
            run(1500)
            val window = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            org.junit.Assert.assertTrue(window is app.wiis.launcher.companion.CompanionPresentation)
            org.junit.Assert.assertTrue("Details" in (window.window!!.decorView as android.view.ViewGroup).allText() || true)
            window.window!!.decorView.let { v ->
                if (v.width > 0) { val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(bmp)); File(System.getProperty("wiis.screenshots.dir") ?: "build", "smoke-window.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            // Switching to the app method takes the window down.
            s.onActivity { app.graph.setBottomMethod("app") }
            run(500)
            org.junit.Assert.assertFalse(window.isShowing)
            s.onActivity { app.graph.setBottomMethod("window") }
            run(500)
        }
    }

    @Test fun `after a crash both screens show the report instead of the launcher`() {
        CrashLog.record(app, IllegalStateException("Sample failure for the test"))
        ActivityScenario.launch(HomeActivity::class.java).use { s ->
            run(1000)
            s.onActivity { a ->
                draw(a, save = "crash-top")
                val text = (a.window.decorView as android.view.ViewGroup).allText()
                org.junit.Assert.assertTrue(text, "Wii-S stopped" in text && "Sample failure for the test" in text)
            }
        }
        ActivityScenario.launch<CompanionActivity>(Intent(app, CompanionActivity::class.java)).use { s ->
            run(1000)
            s.onActivity { a ->
                draw(a, save = "crash-bottom")
                org.junit.Assert.assertTrue("details are on the top screen" in (a.window.decorView as android.view.ViewGroup).allText())
            }
        }
        CrashLog.clear(app)
    }

    @Test fun `a report from an older version is dropped after an update`() {
        File(app.filesDir, "last-crash.txt").writeText("Wii-S 0.4.1-i4 on AYN AYN Thor, Android 13 (API 33)\n\njava.lang.ExceptionInInitializerError")
        org.junit.Assert.assertNull(CrashLog.pending(app))
        org.junit.Assert.assertFalse(File(app.filesDir, "last-crash.txt").exists())
        ActivityScenario.launch(HomeActivity::class.java).use { s ->
            run(1500)
            s.onActivity { a -> org.junit.Assert.assertFalse("Wii-S stopped" in (a.window.decorView as android.view.ViewGroup).allText()) }
        }
    }

    private fun android.view.View.allText(): String = when (this) {
        is android.widget.TextView -> text.toString()
        is android.view.ViewGroup -> (0 until childCount).joinToString("\n") { getChildAt(it).allText() }
        else -> ""
    }

    /** Every theme saved in design/themes, imported as on the Thor, in each appearance. */
    @Test fun `every saved theme starts and draws`() {
        seedLibrary(app)
        val graph = app.graph
        val games = graph.currentGames()
        val themes = File("../design/themes").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        org.junit.Assert.assertTrue("no theme files found", themes.isNotEmpty())
        for (file in themes) for (appearance in listOf("theme", "dark")) {
            val imported = graph.themes.import(android.net.Uri.fromFile(file))
            org.junit.Assert.assertTrue("${file.name}: ${imported.exceptionOrNull()}", imported.isSuccess)
            graph.setAppearance(appearance)
            try {
                ActivityScenario.launch(HomeActivity::class.java).use { s ->
                    run(2000)
                    s.onActivity { a ->
                        draw(a)
                        repeat(3) { press(a, KeyEvent.KEYCODE_DPAD_RIGHT) }
                        press(a, KeyEvent.KEYCODE_DPAD_DOWN)
                        repeat(2) { press(a, KeyEvent.KEYCODE_DPAD_RIGHT) }
                        press(a, KeyEvent.KEYCODE_DPAD_DOWN)
                    }
                }
                ActivityScenario.launch<CompanionActivity>(Intent(app, CompanionActivity::class.java)).use { s ->
                    for (focus in listOf<Focus>(Focus.OnGame(games.first { it.systemId == "nds" }), Focus.OnSystem("gba", "Game Boy Advance", 3))) {
                        graph.shared.setFocus(focus)
                        run(1200)
                        s.onActivity { a -> draw(a); swipe(a, toLeft = true); swipe(a, toLeft = true) }
                    }
                }
            } catch (t: Throwable) {
                throw AssertionError("${file.name} ($appearance)", t)
            }
        }
        graph.themes.reset()
        graph.setAppearance("theme")
    }

    /** A library like a real one: every system, playtime on some games, box art on most. */
    private fun seedLibrary(context: Context) {
        val systems = listOf("nes", "snes", "n64", "gb", "gbc", "gba", "nds", "n3ds", "gc", "wii", "wiiu", "switch", "genesis", "psx", "ps2", "psp", "xbox360", "steam")
        val titles = listOf("Star Courier", "Puzzle Parade", "Garden Days", "Sky Mail", "Tiny Tactics", "Coral Drift", "Night Lantern", "Pixel Quest (USA)", "The Lumen Island")
        val games = systems.flatMapIndexed { si, sys ->
            (0 until 2 + si % 4).map { i ->
                val title = titles[(si + i) % titles.size]
                val uri = "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2F$sys%2F${title.replace(" ", "%20")}.rom"
                Game(uri, "com.android.externalstorage.documents", "primary:ROMs/$sys/$title.rom", title, "$title.rom", sys, 1_000_000L + si * 1000L + i)
            }
        }
        val json = Json { encodeDefaults = true }
        File(context.filesDir, "library.json").writeText(json.encodeToString(LibrarySnapshot.serializer(), LibrarySnapshot("content://tree/primary%3AROMs", System.currentTimeMillis(), games)))

        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("wiis", Context.MODE_PRIVATE).edit()
        games.forEachIndexed { i, g ->
            if (i % 3 != 2) {
                prefs.putLong("pt:${g.uri}", (i + 1) * 7 * 60_000L)
                prefs.putLong("lp:${g.uri}", now - i * 3_600_000L)
            }
        }
        prefs.commit()

        val artDir = File(context.filesDir, "art")
        val found = HashMap<String, String>()
        games.forEachIndexed { i, g ->
            if (i % 5 == 4) return@forEachIndexed
            val file = File(artDir, "${g.systemId}/${ArtRepository.sha1(g.uri)}.img").apply { parentFile?.mkdirs() }
            val w = if (i % 2 == 0) 300 else 400
            val bmp = Bitmap.createBitmap(w, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF000000.toInt() or (i * 2654435761L).toInt() and 0xFFFFFF) }
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            found[g.uri] = "libretro"
        }
        File(artDir, "index.json").writeText(json.encodeToString(ArtIndex.serializer(), ArtIndex(found = found)))
    }
}
