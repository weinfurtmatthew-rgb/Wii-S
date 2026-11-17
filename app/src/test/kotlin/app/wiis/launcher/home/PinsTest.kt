package app.wiis.launcher.home

import app.wiis.emulator.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class PinsTest {
    private fun game(title: String) = Game("sample:nds/$title", "sample", title, title, "$title.nds", "nds", 1)

    @Test fun `a new pin goes first and X again removes it`() {
        val once = Pins.toggle(listOf("a", "b"), "c")
        assertEquals(listOf("c", "a", "b"), once)
        assertEquals(listOf("a", "b"), Pins.toggle(once, "c"))
        assertEquals(listOf("b"), Pins.toggle(listOf("a", "b"), "a"))
    }

    @Test fun `pins of games that are gone are kept but not shown`() {
        val games = listOf(game("Star Courier"), game("Garden Days"))
        val pins = listOf("sample:nds/Garden Days", "sample:nds/Missing", "sample:nds/Star Courier")
        assertEquals(listOf("Garden Days", "Star Courier"), Pins.resolve(pins, games).map { it.title })
    }

    @Test fun `stored as one URI per line`() {
        val pins = listOf("content://a/1", "content://b/2")
        assertEquals(pins, Pins.decode(Pins.encode(pins)))
        assertEquals(emptyList<String>(), Pins.decode(null))
        assertEquals(emptyList<String>(), Pins.decode(""))
        assertEquals(listOf("x", "y"), Pins.decode("x\n\ny\nx"))
    }
}
