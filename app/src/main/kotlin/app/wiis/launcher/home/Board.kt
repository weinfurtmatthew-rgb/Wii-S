package app.wiis.launcher.home

import kotlinx.serialization.Serializable

/**
 * The page below Home: a grid of tiles in three sizes. Saved as board.json: the tiles in reading
 * order of their top-left cells, with an empty cell written as a "gap". Laid out by filling each
 * one into the first place it fits, that order gives every tile back its cell; when a tile moves
 * or grows into cells that are taken, the order changes and its neighbours flow along it.
 */
@Serializable
data class BoardFile(val tiles: List<BoardTile> = emptyList(), val nextId: Int = 1, val seeded: Boolean = false)

/**
 * One tile. [kind]: app (ref: app key), game (ref: game URI), collection (ref: collection id),
 * shortcut (ref: wifi, bluetooth, display, sound, settings, or emu:<package>), a built-in widget
 * (clock, lastPlayed, week, achievements, random), or gap (an empty cell). [size]: "1x1", "2x1"
 * or "2x2".
 */
@Serializable
data class BoardTile(val id: Int, val kind: String, val ref: String = "", val size: String = "1x1")

/** A tile in its place: column and row of its top-left cell, its width and height in cells. */
data class PlacedTile(val tile: BoardTile, val col: Int, val row: Int, val w: Int, val h: Int) {
    fun overlaps(c: Int, r: Int, cw: Int, ch: Int) = col < c + cw && c < col + w && row < r + ch && r < row + h
}

object Board {
    const val APP = "app"
    const val GAME = "game"
    const val COLLECTION = "collection"
    const val SHORTCUT = "shortcut"
    const val CLOCK = "clock"
    const val LAST_PLAYED = "lastPlayed"
    const val WEEK = "week"
    const val ACHIEVEMENTS = "achievements"
    const val RANDOM = "random"
    const val GAP = "gap"

    val SHORTCUTS = listOf("wifi", "bluetooth", "display", "sound", "settings")
    val WIDGETS = listOf(CLOCK, LAST_PLAYED, WEEK, ACHIEVEMENTS, RANDOM)

    fun isGap(t: BoardTile) = t.kind == GAP
    private fun gap() = BoardTile(0, GAP)

    /** The sizes a kind of tile comes in, the first being the size it starts at. */
    fun sizesFor(kind: String): List<String> = when (kind) {
        APP, SHORTCUT -> listOf("1x1", "2x1")
        GAME -> listOf("1x1", "2x2", "2x1")
        RANDOM -> listOf("1x1", "2x1", "2x2")
        COLLECTION, CLOCK, LAST_PLAYED, WEEK -> listOf("2x1", "2x2")
        ACHIEVEMENTS -> listOf("2x2", "2x1")
        else -> listOf("1x1")
    }

    fun cells(size: String): Pair<Int, Int> = when (size) {
        "2x1" -> 2 to 1
        "2x2" -> 2 to 2
        else -> 1 to 1
    }

    private fun area(size: String) = cells(size).let { (w, h) -> w * h }

    /**
     * Places [tiles] in a grid of [cols] × [rows], each in the first place it fits, reading left
     * to right, top to bottom. Tiles that find no place come back as the second list.
     */
    fun place(tiles: List<BoardTile>, cols: Int, rows: Int): Pair<List<PlacedTile>, List<BoardTile>> {
        val taken = Array(rows) { BooleanArray(cols) }
        val placed = ArrayList<PlacedTile>()
        val left = ArrayList<BoardTile>()
        for (t in tiles) {
            val (w, h) = cells(t.size)
            val spot = firstFree(taken, cols, rows, w, h)
            if (spot == null) { left += t; continue }
            val (c, r) = spot
            for (y in r until r + h) for (x in c until c + w) taken[y][x] = true
            placed += PlacedTile(t, c, r, w, h)
        }
        return placed to left
    }

    private fun firstFree(taken: Array<BooleanArray>, cols: Int, rows: Int, w: Int, h: Int): Pair<Int, Int>? {
        for (r in 0..rows - h) for (c in 0..cols - w) {
            var free = true
            for (y in r until r + h) { for (x in c until c + w) if (taken[y][x]) { free = false; break }; if (!free) break }
            if (free) return c to r
        }
        return null
    }

    /** The tiles on the page and where they are, empty cells left out. */
    fun layout(tiles: List<BoardTile>, cols: Int, rows: Int): List<PlacedTile> = place(tiles, cols, rows).first.filter { !isGap(it.tile) }

    /** The empty cells, for the "+" spots. */
    fun freeCells(tiles: List<BoardTile>, cols: Int, rows: Int): List<Pair<Int, Int>> {
        val taken = Array(rows) { BooleanArray(cols) }
        for (p in layout(tiles, cols, rows)) for (y in p.row until p.row + p.h) for (x in p.col until p.col + p.w) taken[y][x] = true
        return (0 until rows).flatMap { r -> (0 until cols).filter { c -> !taken[r][c] }.map { c -> c to r } }
    }

    /** True when every tile (gaps aside) has a place. */
    fun fits(tiles: List<BoardTile>, cols: Int, rows: Int): Boolean = place(tiles, cols, rows).second.none { !isGap(it) }

    /**
     * The tiles at [at] (top-left cells), written in reading order with a gap in every empty cell,
     * which lays out to exactly those places again.
     */
    private fun fromPlaces(at: List<Pair<BoardTile, Pair<Int, Int>>>, cols: Int, rows: Int): List<BoardTile> {
        val taken = Array(rows) { BooleanArray(cols) }
        val entries = ArrayList<Pair<Int, BoardTile>>()
        for ((t, cell) in at) {
            val (c, r) = cell
            val (w, h) = cells(t.size)
            for (y in r until (r + h).coerceAtMost(rows)) for (x in c until (c + w).coerceAtMost(cols)) taken[y][x] = true
            entries += (r * cols + c) to t
        }
        for (r in 0 until rows) for (c in 0 until cols) if (!taken[r][c]) entries += (r * cols + c) to gap()
        return entries.sortedBy { it.first }.map { it.second }
    }

    /**
     * The saved form: the tiles where they lay out now, a gap in each empty cell, and the tiles
     * that don't fit (after the grid got smaller) at the end, so they come back if it grows again.
     */
    fun normalize(tiles: List<BoardTile>, cols: Int, rows: Int): List<BoardTile> {
        val (placed, left) = place(tiles, cols, rows)
        return fromPlaces(placed.filter { !isGap(it.tile) }.map { it.tile to (it.col to it.row) }, cols, rows) + left.filter { !isGap(it) }
    }

    /** Runs [op] on the tiles that are on the page and puts back the ones that aren't. */
    private inline fun onPage(tiles: List<BoardTile>, cols: Int, rows: Int, op: (List<PlacedTile>) -> List<BoardTile>?): List<BoardTile>? {
        val (placed, left) = place(tiles, cols, rows)
        val result = op(placed) ?: return null
        return normalize(result, cols, rows) + left.filter { !isGap(it) }
    }

    /**
     * Moves tile [id] so its top-left cell is ([col], [row]). Into empty cells it just goes there.
     * Where tiles are in the way, the arrangement that moves the fewest other tiles wins: the ones
     * in the way move to the nearest empty cells (a swap, on a full page); or the others fill in
     * around it in their order; or it changes place in the order and its neighbours flow along.
     * Null when nothing puts it there.
     */
    fun moveTo(tiles: List<BoardTile>, id: Int, col: Int, row: Int, cols: Int, rows: Int): List<BoardTile>? = onPage(tiles, cols, rows) { placed ->
        val me = placed.firstOrNull { it.tile.id == id && !isGap(it.tile) } ?: return null
        if (col < 0 || row < 0 || col + me.w > cols || row + me.h > rows) return null
        if (me.col == col && me.row == row) return null
        val others = placed.filter { it !== me && !isGap(it.tile) }
        val inTheWay = others.filter { it.overlaps(col, row, me.w, me.h) }
        val target = me.tile to (col to row)
        if (inTheWay.isEmpty()) return@onPage fromPlaces(others.map { it.tile to (it.col to it.row) } + target, cols, rows)
        val page = placed.map { it.tile }.filter { !(it.id == id && !isGap(it)) }
        val tries = listOfNotNull(
            displaced(others, inTheWay, me, col, row, cols, rows),
            around(page, me, col, row, cols, rows),
            around(page.filter { !isGap(it) }, me, col, row, cols, rows),
            reordered(page, me, col, row, cols, rows),
            reordered(page.filter { !isGap(it) }, me, col, row, cols, rows),
        )
        val was = others.associate { it.tile.id to (it.col to it.row) }
        val best = tries.minByOrNull { places -> places.count { (t, cell) -> t.id != id && was[t.id] != cell } } ?: return@onPage null
        fromPlaces(best, cols, rows)
    }

    private fun grid(cols: Int, rows: Int) = Array(rows) { BooleanArray(cols) }
    private fun Array<BooleanArray>.free(c: Int, r: Int, w: Int, h: Int): Boolean {
        if (c < 0 || r < 0 || r + h > size || c + w > this[0].size) return false
        for (y in r until r + h) for (x in c until c + w) if (this[y][x]) return false
        return true
    }
    private fun Array<BooleanArray>.take(c: Int, r: Int, w: Int, h: Int) { for (y in r until r + h) for (x in c until c + w) this[y][x] = true }

    /** The tiles in the way go to the empty cells nearest them; everything else stays. */
    private fun displaced(others: List<PlacedTile>, inTheWay: List<PlacedTile>, me: PlacedTile, col: Int, row: Int, cols: Int, rows: Int): List<Pair<BoardTile, Pair<Int, Int>>>? {
        val taken = grid(cols, rows)
        val result = ArrayList<Pair<BoardTile, Pair<Int, Int>>>()
        for (p in others) if (p !in inTheWay) { taken.take(p.col, p.row, p.w, p.h); result += p.tile to (p.col to p.row) }
        taken.take(col, row, me.w, me.h)
        result += me.tile to (col to row)
        for (p in inTheWay) {
            var best: Pair<Int, Int>? = null
            var bestDist = Int.MAX_VALUE
            for (r in 0..rows - p.h) for (c in 0..cols - p.w) {
                if (!taken.free(c, r, p.w, p.h)) continue
                val d = kotlin.math.abs(c - p.col) + kotlin.math.abs(r - p.row)
                if (d < bestDist) { best = c to r; bestDist = d }
            }
            val (c, r) = best ?: return null
            taken.take(c, r, p.w, p.h)
            result += p.tile to (c to r)
        }
        return result
    }

    /** It is put down first; the others (and the empty cells, while there is room) fill in around it in their order. */
    private fun around(page: List<BoardTile>, me: PlacedTile, col: Int, row: Int, cols: Int, rows: Int): List<Pair<BoardTile, Pair<Int, Int>>>? {
        val taken = grid(cols, rows)
        taken.take(col, row, me.w, me.h)
        val result = arrayListOf(me.tile to (col to row))
        for (t in page) {
            val (w, h) = cells(t.size)
            val spot = firstFree(taken, cols, rows, w, h)
            if (spot == null) { if (isGap(t)) continue else return null }
            taken.take(spot.first, spot.second, w, h)
            if (!isGap(t)) result += t to spot
        }
        return result
    }

    /** It changes place in the order and the tiles after that flow along. */
    private fun reordered(page: List<BoardTile>, me: PlacedTile, col: Int, row: Int, cols: Int, rows: Int): List<Pair<BoardTile, Pair<Int, Int>>>? {
        for (i in 0..page.size) {
            val list = page.toMutableList().apply { add(i, me.tile) }
            val (p2, left) = place(list, cols, rows)
            if (left.any { !isGap(it) }) continue
            val now = p2.first { it.tile === me.tile }
            if (now.col != col || now.row != row) continue
            return p2.filter { !isGap(it.tile) }.map { it.tile to (it.col to it.row) }
        }
        return null
    }

    /**
     * One press of the D-pad while carrying tile [id]: to the next cell that way it can take (a
     * cell it can't take is skipped). Null at the edge.
     */
    fun step(tiles: List<BoardTile>, id: Int, dx: Int, dy: Int, cols: Int, rows: Int): List<BoardTile>? {
        val me = layout(tiles, cols, rows).firstOrNull { it.tile.id == id } ?: return null
        var c = me.col
        var r = me.row
        repeat(maxOf(cols, rows)) {
            c += dx
            r += dy
            if (c < 0 || r < 0 || c >= cols || r >= rows) return null
            moveTo(tiles, id, c, r, cols, rows)?.let { return it }
        }
        return null
    }

    /**
     * The next size tile [id] comes in that fits (around to the first again). It grows into empty
     * cells beside it where it can, otherwise the tiles after it flow along; shrinking leaves the
     * cells it gave up empty. Unchanged when no other size fits.
     */
    fun resize(tiles: List<BoardTile>, id: Int, cols: Int, rows: Int): List<BoardTile> = onPage(tiles, cols, rows) { placed ->
        val me = placed.firstOrNull { it.tile.id == id && !isGap(it.tile) } ?: return tiles
        val sizes = sizesFor(me.tile.kind).let { if (me.tile.size in it) it else listOf(me.tile.size) + it }
        var at = sizes.indexOf(me.tile.size)
        repeat(sizes.size - 1) {
            at = (at + 1) % sizes.size
            resized(placed, me, sizes[at], cols, rows)?.let { return@onPage it }
        }
        null
    } ?: tiles

    private fun resized(placed: List<PlacedTile>, me: PlacedTile, size: String, cols: Int, rows: Int): List<BoardTile>? {
        val grown = me.tile.copy(size = size)
        val (w, h) = cells(size)
        if (me.col + w <= cols && me.row + h <= rows && placed.none { it !== me && !isGap(it.tile) && it.overlaps(me.col, me.row, w, h) }) {
            return fromPlaces(placed.filter { !isGap(it.tile) }.map { p -> (if (p === me) grown else p.tile) to (p.col to p.row) }, cols, rows)
        }
        val page = placed.map { it.tile }
        for (start in listOf(page, page.filter { !isGap(it) })) {
            val list = start.map { if (it.id == me.tile.id && !isGap(it)) grown else it }
            if (fits(list, cols, rows)) return list
        }
        return null
    }

    /** Removes tile [id]; its cells stay empty. */
    fun remove(file: BoardFile, id: Int, cols: Int, rows: Int): BoardFile =
        file.copy(tiles = onPage(file.tiles, cols, rows) { placed -> fromPlaces(placed.filter { !isGap(it.tile) && it.tile.id != id }.map { it.tile to (it.col to it.row) }, cols, rows) } ?: file.tiles)

    /**
     * Adds a tile in the empty cell [at], or before tile [before] (the tiles from there flow
     * along), or else in the first empty place it fits (the tiles flowing along only when there is
     * no such place). It takes the size its kind starts at, or a smaller one where that doesn't
     * fit. Null when the page is full.
     */
    fun add(file: BoardFile, kind: String, ref: String, cols: Int, rows: Int, before: Int? = null, at: Pair<Int, Int>? = null): BoardFile? {
        val first = sizesFor(kind).first()
        val sizes = listOf(first, "2x1", "1x1").distinct().filter { area(it) <= area(first) }
        for (size in sizes) {
            val tile = BoardTile(file.nextId, kind, ref, size)
            val (w, h) = cells(size)
            val tiles = onPage(file.tiles, cols, rows) { placed ->
                val kept = placed.filter { !isGap(it.tile) }
                val taken = grid(cols, rows).also { g -> kept.forEach { g.take(it.col, it.row, it.w, it.h) } }
                val keep = kept.map { it.tile to (it.col to it.row) }
                if (at != null) {
                    return@onPage if (taken.free(at.first, at.second, w, h)) fromPlaces(keep + (tile to at), cols, rows) else null
                }
                if (before == null) firstFree(taken, cols, rows, w, h)?.let { return@onPage fromPlaces(keep + (tile to it), cols, rows) }
                val page = placed.map { it.tile }
                val firstGap = page.indexOfFirst { isGap(it) }.takeIf { it >= 0 } ?: page.size
                for (closed in listOf(false, true)) {
                    val start = if (closed) page.filter { !isGap(it) } else page
                    val i = before?.let { id -> start.indexOfFirst { it.id == id && !isGap(it) } }?.takeIf { it >= 0 }
                        ?: if (closed) page.take(firstGap).count { !isGap(it) } else firstGap
                    val list = start.toMutableList().apply { add(i, tile) }
                    if (fits(list, cols, rows)) return@onPage list
                }
                null
            }
            if (tiles != null) return file.copy(tiles = tiles, nextId = file.nextId + 1)
        }
        return null
    }

    /**
     * The first page, before you change it: the clock, the last game played (large), this week's
     * play, a random game, Wi-Fi, recent achievements, two collections and two apps (more apps
     * where there are fewer collections). Six by three, full.
     */
    fun defaults(apps: List<String>, collections: List<String>): BoardFile {
        val list = ArrayList<BoardTile>()
        var id = 1
        fun add(kind: String, ref: String = "", size: String = sizesFor(kind).first()) { list += BoardTile(id++, kind, ref, size) }
        add(CLOCK)
        add(LAST_PLAYED, size = "2x2")
        add(WEEK)
        add(RANDOM)
        add(SHORTCUT, "wifi")
        add(ACHIEVEMENTS, size = "2x1")
        val cols = collections.take(2)
        cols.forEach { add(COLLECTION, it) }
        apps.take(2 + 2 * (2 - cols.size)).forEach { add(APP, it) }
        return BoardFile(list, nextId = id, seeded = true)
    }
}
