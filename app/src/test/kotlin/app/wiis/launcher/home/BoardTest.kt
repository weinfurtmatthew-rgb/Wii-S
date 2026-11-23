package app.wiis.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {
    private val cols = 6
    private val rows = 3

    private fun board() = Board.defaults(apps = listOf("a1", "a2", "a3", "a4"), collections = listOf("c:1", "s:k:sonic"))
        .let { it.copy(tiles = Board.normalize(it.tiles, cols, rows)) }

    private fun where(tiles: List<BoardTile>) = Board.layout(tiles, cols, rows).associate { p -> "${p.tile.kind}:${p.tile.ref}" to Triple(p.col, p.row, "${p.w}x${p.h}") }
    private fun where(file: BoardFile) = where(file.tiles)

    private fun id(file: BoardFile, ref: String) = file.tiles.first { "${it.kind}:${it.ref}" == ref }.id

    @Test fun `the first page fills the six by three grid exactly`() {
        val b = board()
        assertTrue(Board.fits(b.tiles, cols, rows))
        assertEquals(18, Board.layout(b.tiles, cols, rows).sumOf { it.w * it.h })
        assertTrue(Board.freeCells(b.tiles, cols, rows).isEmpty())
        val at = where(b)
        assertEquals(Triple(0, 0, "2x1"), at["clock:"])
        assertEquals(Triple(2, 0, "2x2"), at["lastPlayed:"])
        assertEquals(Triple(4, 0, "2x1"), at["week:"])
        assertEquals(Triple(0, 1, "1x1"), at["random:"])
        assertEquals(Triple(1, 1, "1x1"), at["shortcut:wifi"])
        assertEquals(Triple(4, 1, "2x1"), at["achievements:"])
        assertEquals(Triple(0, 2, "2x1"), at["collection:c:1"])
        assertEquals(Triple(4, 2, "1x1"), at["app:a1"])
    }

    @Test fun `without collections the page fills with apps`() {
        val b = Board.defaults(apps = listOf("a1", "a2", "a3", "a4", "a5", "a6", "a7"), collections = emptyList())
        assertEquals(18, Board.layout(b.tiles, cols, rows).sumOf { it.w * it.h })
    }

    @Test fun `the saved order gives every tile back its cell, holes too`() {
        val b = Board.remove(board(), id(board(), "lastPlayed:"), cols, rows)
        val again = Board.normalize(b.tiles, cols, rows)
        assertEquals(b.tiles, again)
        assertEquals(where(board()) - "lastPlayed:", where(again))
        assertEquals(listOf(2 to 0, 3 to 0, 2 to 1, 3 to 1), Board.freeCells(again, cols, rows))
    }

    @Test fun `a carried tile steps one cell at a time and the rest make room`() {
        val b = board()
        val random = id(b, "random:")
        // Right: Wi-Fi takes its old cell.
        val right = Board.step(b.tiles, random, 1, 0, cols, rows)!!
        assertEquals(Triple(1, 1, "1x1"), where(right)["random:"])
        assertEquals(Triple(0, 1, "1x1"), where(right)["shortcut:wifi"])
        // Up from the second row: into the clock's row.
        val up = Board.step(b.tiles, random, 0, -1, cols, rows)!!
        assertEquals(Triple(0, 0, "1x1"), where(up)["random:"])
        // Left at the left edge: nowhere to go.
        assertNull(Board.step(b.tiles, random, -1, 0, cols, rows))
        // Every step keeps every tile on the page.
        assertTrue(Board.fits(right, cols, rows) && Board.fits(up, cols, rows))
        assertEquals(10, Board.layout(up, cols, rows).size)
    }

    @Test fun `a small tile steps into a big one's cell and the big one makes room`() {
        val b = board()
        val wifi = id(b, "shortcut:wifi")
        val next = Board.step(b.tiles, wifi, 1, 0, cols, rows)!!
        assertEquals(Triple(2, 1, "1x1"), where(next)["shortcut:wifi"])
        assertTrue(where(next)["lastPlayed:"] != where(b)["lastPlayed:"])
        assertTrue(Board.fits(next, cols, rows))
        assertEquals(10, Board.layout(next, cols, rows).size)
    }

    @Test fun `a wide tile onto two small ones swaps with them`() {
        val b = board()
        // Recent achievements (4,1)-(5,1) down onto the two apps (4,2), (5,2): they go up into its cells.
        val down = Board.step(b.tiles, id(b, "achievements:"), 0, 1, cols, rows)!!
        val at = where(down)
        assertEquals(Triple(4, 2, "2x1"), at["achievements:"])
        assertEquals(setOf(4 to 1, 5 to 1), setOf(at["app:a1"]!!.let { it.first to it.second }, at["app:a2"]!!.let { it.first to it.second }))
        assertEquals(where(b) - "achievements:" - "app:a1" - "app:a2", at - "achievements:" - "app:a1" - "app:a2")
    }

    @Test fun `into empty cells a tile just goes there`() {
        val b = Board.remove(board(), id(board(), "app:a1"), cols, rows)
        val moved = Board.step(b.tiles, id(b, "app:a2"), -1, 0, cols, rows)!!
        assertEquals(Triple(4, 2, "1x1"), where(moved)["app:a2"])
        assertEquals(listOf(5 to 2), Board.freeCells(moved, cols, rows))
        assertEquals(where(b) - "app:a2", where(moved) - "app:a2")
    }

    @Test fun `resizing grows into empty cells, or the rest flow along`() {
        val b = board()
        val clock = id(b, "clock:")
        // The page is full: a large clock doesn't fit, so it stays.
        assertEquals(b.tiles, Board.resize(b.tiles, clock, cols, rows))
        val roomy = Board.remove(b, id(b, "lastPlayed:"), cols, rows)
        // Into the hole beside it: the clock (0,0) can't grow down over Random and Wi-Fi, so they flow.
        val flowed = Board.resize(roomy.tiles, clock, cols, rows)
        assertEquals(Triple(0, 0, "2x2"), where(flowed)["clock:"])
        assertTrue(Board.fits(flowed, cols, rows))
        // Random (0,1) can't widen into Wi-Fi either; Wi-Fi (1,1) widens into the hole on its right.
        val wide = Board.resize(roomy.tiles, id(roomy, "shortcut:wifi"), cols, rows)
        assertEquals(Triple(1, 1, "2x1"), where(wide)["shortcut:wifi"])
        assertEquals(where(roomy) - "shortcut:wifi", where(wide) - "shortcut:wifi")
        // And back: shrinking leaves the cell empty, nothing else moves.
        val back = Board.resize(wide, id(roomy, "shortcut:wifi"), cols, rows)
        assertEquals(where(roomy), where(back))
    }

    @Test fun `adding needs room`() {
        val b = board()
        assertNull(Board.add(b, Board.APP, "x", cols, rows))
        val roomy = Board.remove(b, id(b, "random:"), cols, rows)
        // Before the clock: everything flows one cell along into the hole.
        val added = Board.add(roomy, Board.APP, "x", cols, rows, before = id(roomy, "clock:"))!!
        assertEquals(Triple(0, 0, "1x1"), where(added)["app:x"])
        assertEquals(b.nextId + 1, added.nextId)
        assertTrue(Board.fits(added.tiles, cols, rows))
        // A collection (2×1) in a one-cell hole goes in at 1×1.
        val inGap = Board.add(roomy, Board.COLLECTION, "c:9", cols, rows)!!
        assertEquals(Triple(0, 1, "1x1"), where(inGap)["collection:c:9"])
    }

    @Test fun `adding into an empty cell puts the tile there`() {
        val b = board()
        val roomy = Board.remove(Board.remove(b, id(b, "app:a1"), cols, rows), id(b, "app:a2"), cols, rows)
        val added = Board.add(roomy, Board.SHORTCUT, "sound", cols, rows, at = 5 to 2)!!
        assertEquals(Triple(5, 2, "1x1"), where(added)["shortcut:sound"])
        assertEquals(listOf(4 to 2), Board.freeCells(added.tiles, cols, rows))
        // A wide tile in two empty cells side by side.
        val wide = Board.add(roomy, Board.WEEK, "", cols, rows, at = 4 to 2)!!
        assertEquals(Triple(4, 2, "2x1"), where(wide)["week:"])
    }

    @Test fun `tiles that don't fit a smaller grid come back when it grows`() {
        val b = board()
        val small = Board.normalize(b.tiles, 6, 2)
        assertTrue(Board.layout(small, 6, 2).size < 10)
        assertEquals(10, small.count { !Board.isGap(it) })
        assertEquals(10, Board.layout(Board.normalize(small, 6, 3), 6, 3).size)
    }
}
