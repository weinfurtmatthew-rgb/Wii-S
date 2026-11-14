package app.wiis.launcher.home

import app.wiis.emulator.Game

/** One tile in the systems grid. [id] "all" is the All games tile. */
data class SystemEntry(val id: String, val name: String, val count: Int)

/** The hero layout's content, worked out from the library and play history (no Android types). */
object HomeModel {
    const val ALL = "all"
    const val ALL_NAME = "All games"

    /** All games first, then one entry per system that has games, in the theme's order. */
    fun systems(games: List<Game>, names: Map<String, String>, registryOrder: Map<String, Int>, order: String): List<SystemEntry> {
        if (games.isEmpty()) return emptyList()
        val entries = games.groupingBy { it.systemId }.eachCount().map { (id, n) -> SystemEntry(id, names[id] ?: id, n) }
        val sorted = when (order) {
            "alphabetical" -> entries.sortedBy { it.name.lowercase() }
            "mostGames" -> entries.sortedWith(compareByDescending<SystemEntry> { it.count }.thenBy { it.name.lowercase() })
            else -> entries.sortedBy { registryOrder[it.id] ?: Int.MAX_VALUE }
        }
        return listOf(SystemEntry(ALL, ALL_NAME, games.size)) + sorted
    }

    /**
     * The Jump back in game is the one played last; the recent row holds the ones before it.
     * Before anything has been played, the card shows the first game A to Z and the row is empty.
     */
    fun heroAndRecent(games: List<Game>, lastPlayed: (Game) -> Long, recentCount: Int, showRecent: Boolean): Pair<Game?, List<Game>> {
        val played = games
            .map { it to lastPlayed(it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
        val hero = played.firstOrNull() ?: games.minWithOrNull(byTitle)
        val recent = if (showRecent) played.drop(1).take(recentCount.coerceIn(1, 50)) else emptyList()
        return hero to recent
    }

    /** A system's games (or every game for [ALL]), A to Z. */
    fun gamesFor(systemId: String, games: List<Game>): List<Game> =
        (if (systemId == ALL) games else games.filter { it.systemId == systemId }).sortedWith(byTitle)

    /** Systems whose colours fill the All games tile's four squares: recent play first, then the biggest systems not shown yet. */
    fun allTilePreview(hero: Game?, recent: List<Game>, systems: List<SystemEntry>): List<String> {
        val fromPlay = (listOfNotNull(hero) + recent).map { it.systemId }
        val bySize = systems.filter { it.id != ALL && it.id !in fromPlay }.sortedByDescending { it.count }.map { it.id }
        val base = (fromPlay + bySize).take(4)
        return if (base.isEmpty()) emptyList() else List(4) { base[it % base.size] }
    }

    private val byTitle = compareBy<Game>({ it.title.lowercase() }, { it.uri })
}
