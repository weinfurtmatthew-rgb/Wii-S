package app.wiis.launcher.home

import app.wiis.emulator.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModelTest {
    private fun game(title: String, system: String) = Game("uri:$system/$title", "auth", "doc", title, "$title.rom", system, 1)

    private val names = mapOf("nds" to "Nintendo DS", "gba" to "Game Boy Advance", "n64" to "Nintendo 64", "psx" to "PlayStation")
    private val registryOrder = mapOf("n64" to 0, "gba" to 1, "nds" to 2, "psx" to 3)
    private val games = listOf(
        game("Mario Kart DS", "nds"), game("Pokemon Platinum", "nds"), game("Advance Wars", "nds"),
        game("Golden Sun", "gba"),
        game("Ocarina of Time", "n64"), game("Banjo", "n64"),
    )

    @Test fun `systems put All games first, then the theme's order`() {
        val alpha = HomeModel.systems(games, names, registryOrder, "alphabetical")
        assertEquals(listOf("all", "gba", "n64", "nds"), alpha.map { it.id })
        assertEquals(SystemEntry("all", "All games", 6), alpha.first())
        assertEquals(3, alpha.first { it.id == "nds" }.count)

        assertEquals(listOf("all", "nds", "n64", "gba"), HomeModel.systems(games, names, registryOrder, "mostGames").map { it.id })
        assertEquals(listOf("all", "n64", "gba", "nds"), HomeModel.systems(games, names, registryOrder, "registry").map { it.id })
    }

    @Test fun `no games means no systems, not an empty All games tile`() {
        assertTrue(HomeModel.systems(emptyList(), names, registryOrder, "alphabetical").isEmpty())
    }

    @Test fun `hero is the last played game and the row holds the ones before it`() {
        val played = mapOf("Golden Sun" to 300L, "Banjo" to 100L, "Mario Kart DS" to 200L)
        val (hero, recent) = HomeModel.heroAndRecent(games, { played[it.title] ?: 0L }, recentCount = 12, showRecent = true)
        assertEquals("Golden Sun", hero?.title)
        assertEquals(listOf("Mario Kart DS", "Banjo"), recent.map { it.title })
    }

    @Test fun `recent row respects the count and can be switched off`() {
        val played = games.withIndex().associate { (i, g) -> g.title to (i + 1L) }
        val (_, recent) = HomeModel.heroAndRecent(games, { played.getValue(it.title) }, recentCount = 2, showRecent = true)
        assertEquals(2, recent.size)
        val (hero, none) = HomeModel.heroAndRecent(games, { played.getValue(it.title) }, recentCount = 2, showRecent = false)
        assertEquals("Banjo", hero?.title)
        assertTrue(none.isEmpty())
    }

    @Test fun `before anything is played the card shows the first game A to Z`() {
        val (hero, recent) = HomeModel.heroAndRecent(games, { 0L }, recentCount = 12, showRecent = true)
        assertEquals("Advance Wars", hero?.title)
        assertTrue(recent.isEmpty())
        assertNull(HomeModel.heroAndRecent(emptyList(), { 0L }, 12, true).first)
    }

    @Test fun `a system's games are A to Z, and All games holds every game`() {
        assertEquals(listOf("Advance Wars", "Mario Kart DS", "Pokemon Platinum"), HomeModel.gamesFor("nds", games).map { it.title })
        assertEquals(6, HomeModel.gamesFor("all", games).size)
        assertEquals("Advance Wars", HomeModel.gamesFor("all", games).first().title)
    }

    @Test fun `All games tile takes recent systems first and always has four squares`() {
        val systems = HomeModel.systems(games, names, registryOrder, "alphabetical")
        assertEquals(listOf("gba", "nds", "nds", "n64"), HomeModel.allTilePreview(games[3], listOf(games[0], games[1]), systems))
        assertEquals(listOf("nds", "n64", "gba", "nds"), HomeModel.allTilePreview(null, emptyList(), systems))
        assertEquals(listOf("gba", "gba", "gba", "gba"), HomeModel.allTilePreview(games[3], emptyList(), listOf(SystemEntry("all", "All games", 1))))
        assertTrue(HomeModel.allTilePreview(null, emptyList(), emptyList()).isEmpty())
    }
}

class DockAppsTest {
    private fun app(pkg: String, label: String) = AppRef("$pkg/.Main", pkg, label)

    private val chrome = app("com.android.chrome", "Chrome")
    private val firefox = app("org.mozilla.firefox", "Firefox")
    private val youtube = app("com.google.android.youtube", "YouTube")
    private val store = app("com.android.vending", "Play Store")
    private val settings = app("com.android.settings", "Settings")
    private val calculator = app("com.android.calculator2", "Calculator")
    private val retroarch = app("com.retroarch", "RetroArch")
    private val installed = listOf(calculator, chrome, firefox, retroarch, settings, store, youtube)

    @Test fun `defaults follow the preferred kinds, then other apps A to Z without emulators`() {
        val dock = DockApps.select(installed, saved = emptyList(), count = 5, exclude = setOf("com.retroarch"))
        assertEquals(listOf(chrome, youtube, store, calculator, firefox), dock)
    }

    @Test fun `the system default browser wins the browser spot`() {
        val dock = DockApps.select(installed, emptyList(), count = 2, defaultBrowser = "org.mozilla.firefox")
        assertEquals(listOf(firefox, youtube), dock)
    }

    @Test fun `saved choices keep their slots and defaults fill around them`() {
        val dock = DockApps.select(installed, saved = listOf(null, settings.key), count = 3, exclude = setOf("com.retroarch"))
        assertEquals(listOf(chrome, settings, youtube), dock)
    }

    @Test fun `an uninstalled saved app falls back to a default`() {
        val dock = DockApps.select(installed, saved = listOf("gone.app/.Main"), count = 1)
        assertEquals(listOf(chrome), dock)
    }

    @Test fun `fewer apps than slots gives a shorter dock`() {
        assertEquals(listOf(chrome), DockApps.select(listOf(chrome), emptyList(), count = 5))
        assertTrue(DockApps.select(installed, emptyList(), count = 0).isEmpty())
    }

    @Test fun `choosing an app already in the dock swaps the two`() {
        val current = listOf(chrome, youtube, store)
        assertEquals(listOf(store.key, youtube.key, chrome.key), DockApps.choose(current, slot = 0, pick = store, count = 3))
        assertEquals(listOf(chrome.key, settings.key, store.key), DockApps.choose(current, slot = 1, pick = settings, count = 3))
    }

    @Test fun `saved slots survive encoding, including empty ones`() {
        val keys = listOf(null, chrome.key, null, store.key)
        assertEquals(keys, DockApps.decode(DockApps.encode(keys)))
        assertTrue(DockApps.decode(null).isEmpty())
        assertTrue(DockApps.decode("").isEmpty())
    }
}
