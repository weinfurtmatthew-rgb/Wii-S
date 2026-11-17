package app.wiis.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * "Whole rows only" (0.5.4): a list shows only the rows, tiles or cards that fit completely.
 * Moving focus past the last one scrolls a whole one in; nothing of the ones outside is drawn.
 */

/**
 * Keeps the focused item inside a window of the viewport that starts [startPx] in and is [sizePx]
 * long, scrolling as little as it can. With rows (or tiles) a whole number of which fill the
 * window, every scroll is a whole number of rows.
 */
@OptIn(ExperimentalFoundationApi::class)
class WindowSpec(private val startPx: Float, private val sizePx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val before = offset - startPx
        val after = offset + size - (startPx + sizePx)
        return when {
            before < 0f -> before
            after > 0f -> after
            else -> 0f
        }
    }
}

/**
 * True when lazy-row item [index] overlaps the row's window: [windowPx] long, from where its start
 * content padding ends. Away from a scroll, that is only the tiles that fit completely.
 */
fun LazyListState.itemShown(index: Int, windowPx: Int): Boolean {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    val start = info.viewportStartOffset + info.beforeContentPadding
    return item.offset < start + windowPx - 1 && item.offset + item.size > start + 1
}

/** True when lazy-grid item [index] is in a row that overlaps the grid's window (inside its content padding). */
fun LazyGridState.rowShown(index: Int): Boolean {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    val top = info.viewportStartOffset + info.beforeContentPadding
    val bottom = info.viewportEndOffset - info.afterContentPadding
    return item.offset.y < bottom - 1 && item.offset.y + item.size.height > top + 1
}

/**
 * A scrolling column of cards that shows whole cards only. A card that doesn't fit completely
 * isn't drawn; moving focus into it scrolls until it fits, with a card's top edge at the top. A
 * card taller than the column can't fit: it is drawn cut, and scrolls as little as it can.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WholeCardsColumn(modifier: Modifier = Modifier, spacing: Dp = 12.dp, padding: Dp = 8.dp, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    val cards = remember(scroll) { WholeCards(scroll) }
    // Nested scrollers (a text field's) keep the usual behaviour.
    val inner = LocalBringIntoViewSpec.current
    LaunchedEffect(cards) { snapshotFlow { scroll.isScrollInProgress }.collect { if (!it) cards.rest = scroll.value } }
    BoxWithConstraints(modifier) {
        cards.viewport = constraints.maxHeight
        CompositionLocalProvider(LocalBringIntoViewSpec provides cards) {
            Layout(
                content = { CompositionLocalProvider(LocalBringIntoViewSpec provides inner) { content() } },
                modifier = Modifier.fillMaxWidth().verticalScroll(scroll),
            ) { measurables, constraints ->
                val pad = padding.roundToPx()
                val gap = spacing.roundToPx()
                val width = constraints.maxWidth
                val placeables = measurables.map {
                    it.measure(Constraints(minWidth = 0, maxWidth = (width - 2 * pad).coerceAtLeast(0), minHeight = 0, maxHeight = Constraints.Infinity))
                }
                val tops = IntArray(placeables.size)
                val bottoms = IntArray(placeables.size)
                var y = pad
                placeables.forEachIndexed { i, p ->
                    tops[i] = y
                    bottoms[i] = y + p.height
                    y += p.height + gap
                }
                cards.update(tops, bottoms)
                val natural = (if (placeables.isEmpty()) pad else y - gap) + pad
                // Room to scroll the last card's snap to the top, so every snap can be reached.
                val height = max(natural, cards.lastSnap() + cards.viewport)
                layout(width, height) {
                    placeables.forEachIndexed { i, p -> p.placeWithLayer(pad, tops[i]) { alpha = if (cards.shows(i)) 1f else 0f } }
                }
            }
        }
    }
}

/** Where the cards are, which of them show, and where focus should scroll (see [WholeCardsColumn]). */
@OptIn(ExperimentalFoundationApi::class)
@Stable
private class WholeCards(private val scroll: ScrollState) : BringIntoViewSpec {
    var viewport = 0
    /** The scroll before the one running now; cards whole there stay drawn while they slide away. */
    var rest = 0
    private var target = 0
    private var tops = IntArray(0)
    private var bottoms = IntArray(0)

    fun update(tops: IntArray, bottoms: IntArray) {
        this.tops = tops
        this.bottoms = bottoms
    }

    private fun tall(i: Int) = bottoms[i] - tops[i] > viewport
    private fun whole(i: Int, at: Int) = tops[i] >= at - 1 && bottoms[i] <= at + viewport + 1

    /** The scroll that shows card [i] whole with a card's top at the top: the first card top that does. */
    private fun snapFor(i: Int): Int = tops.firstOrNull { it >= bottoms[i] - viewport } ?: tops[i]

    fun lastSnap(): Int = if (tops.isEmpty() || tall(tops.lastIndex)) 0 else snapFor(tops.lastIndex)

    fun shows(i: Int): Boolean {
        if (i !in tops.indices || tall(i)) return true
        val s = scroll.value
        if (!scroll.isScrollInProgress) return whole(i, s)
        val overlaps = tops[i] < s + viewport && bottoms[i] > s
        return overlaps && (whole(i, rest) || whole(i, target))
    }

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val s = scroll.value
        val at = (offset + s).roundToInt()
        val i = bottoms.indices.firstOrNull { at < bottoms[it] }
        if (i == null || tall(i)) {
            // Not in a card that can fit: as little as possible, as usual.
            val before = offset
            val after = offset + size - containerSize
            val distance = when {
                before < 0f -> before
                after > 0f -> after
                else -> 0f
            }
            target = s + distance.roundToInt()
            return distance
        }
        // Decided from where the scroll last rested, so the answer holds while it moves.
        val want = when {
            whole(i, rest) -> rest
            tops[i] < rest -> tops[i]
            else -> snapFor(i)
        }.coerceIn(0, scroll.maxValue.coerceAtLeast(0))
        target = want
        return (want - s).toFloat()
    }
}
