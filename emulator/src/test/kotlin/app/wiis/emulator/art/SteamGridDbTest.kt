package app.wiis.emulator.art

import app.wiis.emulator.Game
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGridDbTest {
    private fun game(title: String, file: String = "$title.nsp") = Game("content://x/$file", "x", file, title, file, "switch", 1)

    @Test fun `search terms leave out tags and put a trailing The first`() {
        assertEquals("Pixel Quest", SteamGridDb.searchTerm("Pixel Quest (USA) (Rev 1) [!]"))
        assertEquals("The Legend of Zelda", SteamGridDb.searchTerm("Legend of Zelda, The"))
        assertEquals("Sky Mail", SteamGridDb.searchTerm("Sky_Mail"))
    }

    @Test fun `a match is the same game, or nothing`() {
        val games = listOf(
            SteamGridDb.SgdbGame(1, "Star Courier: Deluxe", false),
            SteamGridDb.SgdbGame(2, "Star Courier", false),
            SteamGridDb.SgdbGame(3, "Star Courier", true),
        )
        assertEquals(3, SteamGridDb.bestMatch("Star Courier", games)?.id)
        // A subtitle on one side still matches the first result.
        assertEquals(1, SteamGridDb.bestMatch("Star Courier", games.take(1))?.id)
        // Another game entirely: no art rather than the wrong art.
        assertNull(SteamGridDb.bestMatch("Garden Days", games))
        assertEquals(9, SteamGridDb.bestMatch("Pokémon & Friends", listOf(SteamGridDb.SgdbGame(9, "Pokemon and Friends", false)))?.id ?: SteamGridDb.bestMatch("Pokemon & Friends", listOf(SteamGridDb.SgdbGame(9, "Pokemon and Friends", false)))?.id)
    }

    @Test fun `finds the game, takes its first cover, and sends the key only to the API`() {
        val asked = mutableListOf<Pair<String, String?>>()
        val sgdb = SteamGridDb(
            key = { "k123" },
            get = { url, key ->
                asked += url to key
                when {
                    "/search/autocomplete/" in url -> """{"success":true,"data":[{"id":42,"name":"Tiny Tactics","types":["steam"],"verified":true,"release_date":1600000000}]}""".toByteArray()
                    "/grids/game/42?" in url -> """{"success":true,"data":[{"id":7,"score":3,"style":"alternate","url":"https://cdn2.steamgriddb.com/grid/abc.png","thumb":"https://cdn2.steamgriddb.com/thumb/abc.png","width":600,"height":900},{"id":8,"score":1,"url":"https://cdn2.steamgriddb.com/grid/def.png","width":600,"height":900}]}""".toByteArray()
                    url == "https://cdn2.steamgriddb.com/grid/abc.png" -> byteArrayOf(1, 2, 3)
                    else -> null
                }
            },
            shrink = { it },
        )
        val bytes = runBlocking { sgdb.find(game("Tiny Tactics (Europe)"), null) }
        assertEquals(listOf<Byte>(1, 2, 3), bytes?.toList())
        assertEquals("https://www.steamgriddb.com/api/v2/search/autocomplete/Tiny%20Tactics", asked[0].first)
        assertTrue(asked[1].first.startsWith("https://www.steamgriddb.com/api/v2/grids/game/42?dimensions=600x900,342x482,660x930&types=static&nsfw=false&humor=false"))
        assertEquals(listOf("k123", "k123", null), asked.map { it.second })
    }

    @Test fun `without a key nothing is asked`() {
        var calls = 0
        val sgdb = SteamGridDb(key = { null }, get = { _, _ -> calls++; null }, shrink = { it })
        assertNull(runBlocking { sgdb.find(game("Anything"), null) })
        assertEquals(0, calls)
    }

    @Test fun `SteamGridDB's own error message comes through`() {
        val e = runCatching { SteamGridDb.parseGames("""{"success":false,"errors":["Game not found."]}""".toByteArray()) }.exceptionOrNull()
        assertEquals("Game not found.", e?.message)
        assertTrue(SteamGridDb.parseImages("""{"success":true,"data":[]}""".toByteArray()).isEmpty())
    }

    @Test fun `the key never goes anywhere but the API`() {
        val e = runCatching { steamGridGet("https://example.com/grab", "secret") }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }
}
