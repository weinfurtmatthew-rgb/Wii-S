package app.wiis.launcher.home

import app.wiis.emulator.Game
import app.wiis.emulator.ra.RaRecent
import app.wiis.system.AppEntry

/** The page below Home as the screen draws it. */
data class BoardUi(
    /** The saved tiles laid out for this grid (see Board.normalize). */
    val tiles: List<BoardTile> = emptyList(),
    val cols: Int = 6,
    val rows: Int = 3,
    /** The games that game tiles name, by URI. */
    val games: Map<String, Game> = emptyMap(),
    /** The random game tile's pick (a new one each time you come back, or Y). */
    val random: Game? = null,
    /** Play time per day, Monday to Sunday of this week, and which day is today. */
    val week: List<Long> = emptyList(),
    val today: Int = 0,
    val achievements: RecentUnlocks = RecentUnlocks.Loading,
    /** Arranging: null when not. */
    val edit: BoardEdit? = null,
)

/**
 * Arranging the page. [sticky]: the edit mode you enter by holding A, with "+" in the empty cells,
 * which B leaves; otherwise just moving one tile (Select → Move), which A or B ends. [carrying]:
 * the tile the D-pad moves, if any.
 */
data class BoardEdit(val sticky: Boolean, val carrying: Int? = null)

sealed interface RecentUnlocks {
    data object Loading : RecentUnlocks
    data object NotSetUp : RecentUnlocks
    data class Failed(val message: String) : RecentUnlocks
    data class Ready(val items: List<RaRecent>) : RecentUnlocks
}

/** The apps the first page starts with: well-known ones first, then A to Z, none from the dock or emulators. */
object BoardApps {
    private val preferred = listOf(
        "com.android.vending", "com.google.android.youtube", "com.spotify.music", "com.discord",
        "com.valvesoftware.steamlink", "com.moonlight_stream.moonlight", "com.android.chrome", "org.mozilla.firefox",
    )

    fun pick(installed: List<AppEntry>, dock: List<AppEntry>, emulators: Set<String>, own: String): List<AppEntry> {
        val inDock = dock.map { it.key }.toSet()
        return installed
            .filter { it.key !in inDock && it.component.packageName !in emulators && it.component.packageName != own }
            .sortedWith(compareBy<AppEntry> { preferred.indexOf(it.component.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { it.label.lowercase() })
    }
}
