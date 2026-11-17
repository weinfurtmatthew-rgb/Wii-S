package app.wiis.launcher.home

import app.wiis.emulator.Game

/**
 * Games pinned to the Home screen's bottom row with X. Kept as their URIs, newest first, so a new
 * pin shows at the start of the row. A pin whose game is missing (card out, folder changed) stays
 * saved and comes back with the game.
 */
object Pins {
    /** Pins [uri] at the front, or unpins it if it was pinned. */
    fun toggle(pins: List<String>, uri: String): List<String> = if (uri in pins) pins - uri else listOf(uri) + pins

    /** The pinned games that are in the library, in pin order. */
    fun resolve(pins: List<String>, games: List<Game>): List<Game> {
        if (pins.isEmpty()) return emptyList()
        val byUri = games.associateBy { it.uri }
        return pins.mapNotNull(byUri::get)
    }

    fun encode(pins: List<String>): String = pins.joinToString("\n")

    fun decode(text: String?): List<String> = text?.split("\n")?.filter { it.isNotBlank() }?.distinct().orEmpty()
}
