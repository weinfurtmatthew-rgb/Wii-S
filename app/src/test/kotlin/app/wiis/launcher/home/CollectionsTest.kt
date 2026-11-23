package app.wiis.launcher.home

import app.wiis.emulator.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsTest {
    private fun game(sys: String, title: String) = Game("sample:$sys/$title", "sample", title, title, "$title.rom", sys, 1)

    private val library = listOf(
        game("genesis", "Sonic the Hedgehog (USA)"),
        game("genesis", "Sonic the Hedgehog 2"),
        game("gba", "Sonic Advance"),
        game("gba", "Pokémon Emerald"),
        game("nds", "Pokemon Platinum"),
        game("ps2", "Grand Theft Auto: Vice City"),
        game("ps2", "Grand Theft Auto III"),
        game("n64", "Super Mario 64"),
        game("nds", "Mario Kart DS"),
        game("nds", "New Super Mario Bros."),
        game("snes", "Mega Man X"),
        game("snes", "Mega Man X2"),
        game("ps2", "Star Courier"),
        game("gba", "Star Courier"),
        game("n64", "Star Fox 64"),
        game("gc", "Star Wars Rogue Squadron"),
        game("ps2", "Kite Festival"),
        game("ps2", "Kite Festival 2"),
        game("gba", "Tiny Kingdom"),
        game("nds", "Tiny Tactics"),
        game("psx", "Coral Drift"),
        game("ps2", "Coral Drift Rally"),
        game("gc", "Coral Drift Reef"),
    )

    private fun found() = SeriesFinder.find(library).associate { it.name to it.games.map { g -> g.title }.toSet() }

    @Test fun `well-known series are found by name anywhere in the title`() {
        val s = found()
        assertEquals(setOf("Sonic the Hedgehog (USA)", "Sonic the Hedgehog 2", "Sonic Advance"), s["Sonic"])
        assertEquals(setOf("Pokémon Emerald", "Pokemon Platinum"), s["Pokémon"])
        assertEquals(setOf("Grand Theft Auto: Vice City", "Grand Theft Auto III"), s["Grand Theft Auto"])
        assertEquals(setOf("Super Mario 64", "Mario Kart DS", "New Super Mario Bros."), s["Mario"])
        assertEquals(2, s["Mega Man"]?.size)
    }

    @Test fun `titles that start alike make a series, common words and one game on two consoles don't`() {
        val s = found()
        assertEquals(setOf("Kite Festival", "Kite Festival 2"), s["Kite Festival"])
        assertEquals(setOf("Coral Drift", "Coral Drift Rally", "Coral Drift Reef"), s["Coral Drift"])
        assertFalse("Star" in s)
        assertFalse("Star Courier" in s)
        assertFalse("Tiny" in s)
        assertFalse("Super Mario" in s) // already the Mario series
        assertFalse("Sonic the Hedgehog" in s)
    }

    @Test fun `the largest series come first`() {
        val names = SeriesFinder.find(library).map { it.name }
        assertEquals("Coral Drift", names.first { it == "Coral Drift" || it == "Kite Festival" })
        assertTrue(names.indexOf("Sonic") < names.indexOf("Pokémon"))
    }

    @Test fun `kept, renamed, edited, hidden and your own`() {
        val series = SeriesFinder.find(library)
        var file = CollectionsFile()
        val sonic = SeriesFinder.key("Sonic")
        file = CollectionsModel.rename(file, "s:$sonic", "Sonic collection")
        file = CollectionsModel.remove(file, "s:$sonic", "sample:gba/Sonic Advance")
        file = CollectionsModel.add(file, "s:$sonic", "sample:nds/Tiny Tactics")
        file = CollectionsModel.hide(file, "s:${SeriesFinder.key("Pokémon")}")
        val (withOwn, id) = CollectionsModel.create(file, "Favourites", "sample:gba/Tiny Kingdom")
        val list = CollectionsModel.resolve(withOwn, series, library)
        val mine = list.filter { !it.suggested }
        assertEquals(listOf("Favourites", "Sonic collection"), mine.map { it.name })
        assertEquals(listOf("Sonic the Hedgehog (USA)", "Sonic the Hedgehog 2", "Tiny Tactics"), mine[1].games.map { it.title })
        assertEquals(id, mine[0].id)
        assertTrue(list.none { it.name == "Pokémon" })
        assertTrue(list.filter { it.suggested }.any { it.name == "Grand Theft Auto" })
        assertTrue(list.indexOfFirst { it.suggested } > list.indexOfLast { !it.suggested })
    }

    @Test fun `words drop accents, tags and punctuation`() {
        assertEquals(listOf("pokemon", "emerald"), SeriesFinder.words("Pokémon: Emerald (USA) [!]"))
    }
}
