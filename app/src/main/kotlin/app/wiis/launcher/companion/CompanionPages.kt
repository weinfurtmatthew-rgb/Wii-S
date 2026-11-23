package app.wiis.launcher.companion

import app.wiis.launcher.ui.CoverMosaic
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wiis.designsystem.ConsoleGlyph
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.WiisStyle
import app.wiis.designsystem.glassBox
import app.wiis.designsystem.mix
import app.wiis.emulator.Game
import app.wiis.emulator.ra.RaResult
import app.wiis.launcher.Session
import app.wiis.launcher.stats.GameStats
import app.wiis.launcher.stats.SystemStats
import app.wiis.launcher.stats.Week
import app.wiis.launcher.ui.ClockText
import app.wiis.launcher.ui.LocalImageSource
import app.wiis.launcher.ui.rememberArt
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import app.wiis.designsystem.toColorOr
import app.wiis.emulator.ra.RaAchievement
import app.wiis.emulator.ra.RaCompletionEntry
import app.wiis.launcher.stats.History
import app.wiis.launcher.stats.Played
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** What the bottom screen is about: a game (perhaps being played) or a system. */
sealed interface CompanionSubject {
    data class OfGame(val game: Game, val session: Session?) : CompanionSubject
    data class OfSystem(val id: String, val name: String, val count: Int) : CompanionSubject
    data class OfCollection(val id: String, val name: String, val games: List<Game>) : CompanionSubject
}

enum class CompanionPage(val label: String, val icon: PageIcon) {
    Details("Details", PageIcon.Details),
    Stats("Stats", PageIcon.Stats),
    Achievements("Achievements", PageIcon.Trophy),
}

/**
 * The bottom screen's pages: details, stats, RetroAchievements. Swiped by touch (the window never
 * takes controller focus) or chosen with the tabs or dots, as the theme's `companion.pageStyle` says.
 */
@Composable
fun CompanionPager(
    pages: List<CompanionPage>,
    pagerState: PagerState,
    showClock: Boolean,
    page: @Composable (CompanionPage) -> Unit,
) {
    val style = LocalWiis.current
    val tabs = style.spec.companion.pageStyle != "dots" && pages.size > 1
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tabs || showClock) {
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tabs) PageTabs(pages, pagerState, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                if (showClock) ClockText(style.clock.copy(color = style.text))
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(), pageSpacing = 24.dp, beyondViewportPageCount = 1) { i ->
            Box(Modifier.fillMaxSize()) { page(pages[i]) }
        }
        if (!tabs && pages.size > 1) PageDots(pages, pagerState)
    }
}

/** Equal tabs with icons across the top, as in the mocks; the current one filled. */
@Composable
private fun PageTabs(pages: List<CompanionPage>, state: PagerState, modifier: Modifier) {
    val style = LocalWiis.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(12.dp)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pages.forEachIndexed { i, p ->
            val on = state.currentPage == i
            val fg = if (on) style.tabActiveText else style.glassTextMuted
            Row(
                Modifier.weight(1f).height(36.dp).clip(shape)
                    .then(if (on) Modifier.background(style.tabActiveFill) else Modifier.glassBox(style, shape))
                    .clickable { scope.launch { state.animateScrollToPage(i) } }
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageIconView(p.icon, 17.dp, fg)
                Text(p.label, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = fg), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun PageDots(pages: List<CompanionPage>, state: PagerState) {
    val style = LocalWiis.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        pages.indices.forEach { i ->
            val on = state.currentPage == i
            Box(
                Modifier
                    .size(width = if (on) 18.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(style.text.copy(alpha = if (on) 0.85f else 0.25f))
                    .clickable { scope.launch { state.animateScrollToPage(i) } },
            )
        }
    }
}

// ---- Details for a system ----------------------------------------------------------------------

@Composable
fun SystemDetailsPage(id: String, name: String, games: Int, played: Int, emulator: String?) {
    val style = LocalWiis.current
    val color = if (id == "all") style.accent else style.systemColor(id)
    val glyph = if (style.glassIsDark) listOf(mix(color, Color.White, 0.45f), mix(color, Color.White, 0.08f)) else listOf(mix(color, Color.White, 0.15f), mix(color, Color.Black, 0.2f))
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(200.dp)
                .glassBox(style, RoundedCornerShape(style.spec.shape.cardRadius.dp))
                .drawBehind { drawRect(Brush.linearGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.05f)))) },
            contentAlignment = Alignment.Center,
        ) {
            ConsoleGlyph(id, 120.dp, color, strokeUnits = 1.9f, brush = Brush.linearGradient(glyph))
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Text(name, style = style.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Fact("Games", "$games", style)
            Fact("Played", "$played", style)
            if (emulator != null) Fact("Emulator", emulator, style)
        }
    }
}

/** A collection on the bottom screen: four of its covers, its name, how many games and how many played. */
@Composable
fun CollectionDetailsPage(name: String, games: List<Game>, played: Int) {
    val style = LocalWiis.current
    val shape = RoundedCornerShape(style.spec.shape.cardRadius.dp)
    val px = with(LocalDensity.current) { 200.dp.roundToPx() }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        CoverMosaic(games, px, Modifier.fillMaxHeight().width(200.dp).clip(shape).glassBox(style, shape))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Text(name, style = style.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Fact("Games", "${games.size}", style)
            Fact("Played", "$played", style)
            Fact("Consoles", "${games.map { it.systemId }.distinct().size}", style)
        }
    }
}

@Composable
fun Fact(label: String, value: String, style: WiisStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style.label, modifier = Modifier.width(110.dp))
        Text(value, style = style.body)
    }
}

// ---- Stats -------------------------------------------------------------------------------------

/** One of the four numbers across the top of the Stats page, with its 12-week line. */
private data class StatNumber(val icon: PageIcon, val value: String, val label: String, val series: List<Float>)

@Composable
fun GameStatsPage(stats: GameStats, @Suppress("UNUSED_PARAMETER") color: Color) {
    if (stats.totalMillis <= 0) {
        EmptyPage("Not played yet", "Time played, sessions and charts appear after the first session.")
        return
    }
    val h = stats.history
    val none = stats.sessions == 0
    StatsLayout(
        numbers = listOf(
            StatNumber(PageIcon.Clock, hours(stats.totalMillis), "Total time", cumulative(h.weeks, stats.totalMillis - h.weeks.sum())),
            StatNumber(PageIcon.Play, if (none) "–" else "${stats.sessions}", "Sessions", h.weekSessions.map(Int::toFloat)),
            StatNumber(PageIcon.History, if (none) "–" else hours(stats.averageMillis), "Avg. session", h.weeks.indices.map { i -> if (h.weekSessions[i] > 0) h.weeks[i].toFloat() / h.weekSessions[i] else 0f }),
            StatNumber(PageIcon.Calendar, if (none) "–" else hours(h.perWeekMillis), "Avg. per week", h.weeks.map(Long::toFloat)),
        ),
        week = stats.week,
        history = h,
        side = { RecentSessions(stats.recent, it) },
    )
}

@Composable
fun SystemStatsPage(stats: SystemStats, name: String, @Suppress("UNUSED_PARAMETER") color: Color) {
    if (stats.totalMillis <= 0) {
        EmptyPage("Nothing played on $name yet", "Time played and your most played games appear here.")
        return
    }
    val h = stats.history
    val none = stats.sessions == 0
    StatsLayout(
        numbers = listOf(
            StatNumber(PageIcon.Clock, hours(stats.totalMillis), "Total time", cumulative(h.weeks, stats.totalMillis - h.weeks.sum())),
            StatNumber(PageIcon.Games, "${stats.gamesPlayed}", "Games played", h.weekGames.map(Int::toFloat)),
            StatNumber(PageIcon.Calendar, if (none) "–" else hours(h.perWeekMillis), "Avg. per week", h.weeks.map(Long::toFloat)),
            StatNumber(PageIcon.Repeat, if (none) "–" else String.format(Locale.getDefault(), "%.1f", h.sessionsPerWeek), "Plays per week", h.weekSessions.map(Int::toFloat)),
        ),
        week = stats.week,
        history = h,
        side = { TopGames(stats.top, it) },
    )
}

@Composable
private fun StatsLayout(numbers: List<StatNumber>, week: Week, history: History, side: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            numbers.forEachIndexed { i, n -> StatCard(n, statColor(i), Modifier.weight(1f)) }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayChart(week, history, Modifier.weight(1.55f).fillMaxHeight())
            side(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun StatCard(n: StatNumber, color: Color, modifier: Modifier) {
    val style = LocalWiis.current
    val shape = cardShape(style)
    Box(modifier.height(104.dp).clip(shape).glassBox(style, shape)) {
        if (style.spec.companion.sparklines) Sparkline(n.series, color, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(30.dp))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            PageIconView(n.icon, 18.dp, color)
            Spacer(Modifier.height(5.dp))
            Text(n.value, style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 25.sp, color = style.glassText), maxLines = 1, softWrap = false)
            Text(n.label, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = style.glassTextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A smooth line through [values] with a fading fill, as under the mocks' numbers. */
@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1e-6f)
        val w = size.width
        val h = size.height
        val pts = values.mapIndexed { i, v -> Offset(i * w / (values.size - 1), h * 0.9f - (v / max) * h * 0.73f) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts[maxOf(0, i - 1)]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts[minOf(pts.size - 1, i + 2)]
                fun y(v: Float) = v.coerceIn(1f, h - 1f)
                cubicTo(p1.x + (p2.x - p0.x) / 6, y(p1.y + (p2.y - p0.y) / 6), p2.x - (p3.x - p1.x) / 6, y(p2.y - (p3.y - p1.y) / 6), p2.x, p2.y)
            }
        }
        val fill = Path().apply { addPath(line); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private val RANGES = listOf("week" to "This week", "12w" to "Last 12 weeks", "year" to "Last 12 months")

/** Play time as bars: this week by day, the last 12 weeks, or the last 12 months, picked from a menu. */
@Composable
private fun PlayChart(week: Week, history: History, modifier: Modifier) {
    val style = LocalWiis.current
    var range by rememberSaveable { mutableStateOf(style.spec.companion.chartRange) }
    var menu by remember { mutableStateOf(false) }
    val color = statColor(0)
    val today = remember { LocalDate.now() }
    val locale = Locale.getDefault()
    val (values, labels, current) = remember(range, week, history) {
        when (range) {
            "week" -> Triple(week.days, DayOfWeek.values().map { it.getDisplayName(java.time.format.TextStyle.NARROW, locale) }, week.todayIndex)
            "year" -> Triple(history.months, (11 downTo 0).map { today.minusMonths(it.toLong()).month.getDisplayName(java.time.format.TextStyle.NARROW, locale) }, history.months.size - 1)
            else -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val starts = (11 downTo 0).map { monday.minusWeeks(it.toLong()) }
                Triple(history.weeks, starts.mapIndexed { i, d -> if (i == 11) "Now" else if (i == 0 || d.month != starts[i - 1].month) d.month.getDisplayName(java.time.format.TextStyle.SHORT, locale) else "" }, 11)
            }
        }
    }
    val shape = cardShape(style)
    Box(modifier.glassBox(style, shape)) {
        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PageIconView(PageIcon.Trend, 18.dp, color)
                Spacer(Modifier.width(8.dp))
                Text("Play time", style = cardTitle(style), maxLines = 1, softWrap = false)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).glassBox(style, RoundedCornerShape(10.dp)).clickable { menu = !menu }.padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(RANGES.first { it.first == range || (it.first == "12w" && range !in listOf("week", "year")) }.second, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = style.glassTextMuted), maxLines = 1, softWrap = false)
                    PageIconView(PageIcon.Chevron, 14.dp, style.glassTextMuted)
                }
            }
            Bars(values.map { it / 60_000f }, labels, current, color, Modifier.weight(1f).fillMaxWidth())
        }
        if (menu) {
            // Tapping anywhere else on the card closes the menu.
            Box(Modifier.matchParentSize().clickable(interactionSource = null, indication = null) { menu = false })
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 42.dp, end = 14.dp).width(150.dp)
                    .clip(RoundedCornerShape(12.dp)).background(style.glassFill.copy(alpha = 1f)).glassBox(style, RoundedCornerShape(12.dp)).padding(4.dp),
            ) {
                RANGES.forEach { (key, label) ->
                    val on = key == range
                    Text(
                        label,
                        style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (on) style.tabActiveText else style.glassText),
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (on) style.tabActiveFill else Color.Transparent)
                            .clickable { range = key; menu = false }.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/** Bars in minutes with three dashed guides; the current period in full colour, the rest at half. */
@Composable
private fun Bars(minutes: List<Float>, labels: List<String>, current: Int, color: Color, modifier: Modifier) {
    val style = LocalWiis.current
    val measurer = rememberTextMeasurer()
    val axis = TextStyle(fontFamily = style.fonts.body, fontSize = 10.5.sp, color = style.glassTextMuted)
    val now = axis.copy(color = style.glassText, fontWeight = FontWeight.SemiBold)
    val top = niceTop(minutes.maxOrNull() ?: 0f)
    Canvas(modifier) {
        val left = 30.dp.toPx()
        val labelH = 16.dp.toPx()
        val plotTop = 8.dp.toPx()
        val plotH = size.height - labelH - plotTop
        val base = plotTop + plotH
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        for (f in listOf(0f, 0.5f, 1f)) {
            val y = base - plotH * f
            drawLine(style.glassText.copy(alpha = if (f == 0f) 0.22f else 0.12f), Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            val t = measurer.measure(axisLabel(top * f), axis)
            drawText(t, topLeft = Offset(left - 6.dp.toPx() - t.size.width, y - t.size.height / 2f))
        }
        val n = minutes.size
        val gap = (if (n > 7) 6 else 12).dp.toPx()
        val slot = (size.width - left - gap * (n - 1)) / n
        val barW = minOf(slot, 28.dp.toPx())
        val light = mix(color, Color.White, 0.32f)
        // Labels that would touch another are skipped; the current period's always shows.
        val placed = ArrayList<ClosedFloatingPointRange<Float>>()
        fun slotX(i: Int) = left + i * (slot + gap) + (slot - barW) / 2 + barW / 2
        fun spanOf(i: Int): ClosedFloatingPointRange<Float>? {
            val label = labels.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: return null
            val w = measurer.measure(label, if (i == current) now else axis).size.width
            return (slotX(i) - w / 2f - 3.dp.toPx())..(slotX(i) + w / 2f + 3.dp.toPx())
        }
        val shown = HashSet<Int>()
        (listOf(current) + minutes.indices.filter { it != current }).forEach { i ->
            val span = spanOf(i) ?: return@forEach
            if (placed.none { it.start < span.endInclusive && span.start < it.endInclusive }) { placed += span; shown += i }
        }
        minutes.forEachIndexed { i, m ->
            val x = left + i * (slot + gap) + (slot - barW) / 2
            val h = if (m <= 0f) 0f else maxOf(3.dp.toPx(), plotH * (m / top))
            if (h > 0f) {
                val alpha = if (i == current) 1f else 0.5f
                val r = minOf(5.dp.toPx(), barW / 2, h / 2)
                val bar = Path().apply {
                    addRoundRect(RoundRect(Rect(x, base - h, x + barW, base), topLeft = CornerRadius(r), topRight = CornerRadius(r), bottomLeft = CornerRadius(minOf(2.dp.toPx(), r)), bottomRight = CornerRadius(minOf(2.dp.toPx(), r))))
                }
                drawPath(bar, Brush.verticalGradient(listOf(light, color), startY = base - h, endY = base), alpha = alpha)
            }
            labels.getOrNull(i)?.takeIf { it.isNotEmpty() && i in shown }?.let { label ->
                val t = measurer.measure(label, if (i == current) now else axis)
                drawText(t, topLeft = Offset(x + barW / 2 - t.size.width / 2f, size.height - t.size.height))
            }
        }
    }
}

/** The chart's top in minutes: twice a round step, so the middle guide is round too. */
private fun niceTop(max: Float): Float {
    for (c in listOf(10, 15, 30, 60, 120, 180, 240, 300, 600, 900, 1200, 1800, 3000, 6000, 12000)) if (2 * c >= max) return 2f * c
    return (kotlin.math.ceil(max / 6000f) * 6000f)
}

private fun axisLabel(minutes: Float): String {
    val m = minutes.toInt()
    return when {
        m < 60 -> "${m}m"
        m % 60 == 0 -> "${m / 60}h"
        else -> String.format(Locale.getDefault(), "%.1fh", m / 60f)
    }
}

@Composable
private fun RecentSessions(recent: List<Played>, modifier: Modifier) {
    val style = LocalWiis.current
    val color = statColor(2)
    Column(modifier.glassBox(style, cardShape(style)).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        CardTitle(PageIcon.History, color, "Recent sessions")
        if (recent.isEmpty()) Text("Sessions are counted from Wii-S 0.4.0 on.", style = style.label.copy(color = style.glassTextMuted))
        val max = recent.maxOfOrNull { it.millis }?.coerceAtLeast(1) ?: 1
        recent.forEach { s ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(dayLabel(s.start), style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = style.glassText), maxLines = 1, modifier = Modifier.alignByBaseline())
                    Spacer(Modifier.width(6.dp))
                    Text(timeLabel(s.start, style), style = TextStyle(fontFamily = style.fonts.body, fontSize = 11.sp, color = style.glassTextMuted), maxLines = 1, modifier = Modifier.weight(1f).alignByBaseline())
                    Text(hours(s.millis), style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = style.glassText), maxLines = 1, modifier = Modifier.alignByBaseline())
                }
                ProgressBar(s.millis.toFloat() / max, color, 4.dp)
            }
        }
    }
}

@Composable
private fun TopGames(top: List<Pair<Game, Long>>, modifier: Modifier) {
    val style = LocalWiis.current
    val max = top.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(modifier.glassBox(style, cardShape(style)).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrophyIcon(18.dp, trophyColor(Tier.Gold))
            Text("Top games", style = cardTitle(style), maxLines = 1, softWrap = false)
        }
        top.forEach { (game, ms) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniArt(game, 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(game.title, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = style.glassText), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { ProgressBar(ms.toFloat() / max, style.systemColor(game.systemId)) }
                        Text(hours(ms), style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = style.glassText), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardTitle(icon: PageIcon, color: Color, text: String) {
    val style = LocalWiis.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PageIconView(icon, 18.dp, color)
        Text(text, style = cardTitle(style), maxLines = 1, softWrap = false)
    }
}

private fun cumulative(weeks: List<Long>, start: Long): List<Float> {
    var acc = start.coerceAtLeast(0)
    return weeks.map { acc += it; acc.toFloat() }
}

/** Big numbers, as in the mocks: hours with one decimal, minutes under an hour. */
fun hours(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) String.format(Locale.getDefault(), "%.1f h", minutes / 60f) else "$minutes min"
}

private fun dayLabel(ms: Long): String {
    val day = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.isAfter(today.minusDays(7)) -> day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        else -> shortDate(ms)
    }
}

private fun timeLabel(ms: Long, style: WiisStyle): String =
    SimpleDateFormat(if (style.spec.header.clock24h) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(ms))

@Composable
private fun statColor(i: Int): Color {
    val c = LocalWiis.current.spec.companion.statColors
    val fallback = listOf(Color(0xFF2F86F6), Color(0xFF1FA968), Color(0xFF8A5CF6), Color(0xFFF28C28))[i % 4]
    return c.getOrNull(i).toColorOr(fallback)
}

// ---- RetroAchievements ------------------------------------------------------------------------

/** Rarity on RetroAchievements: gold if fewer than 1 in 10 players have it, silver under 1 in 3. */
enum class Tier { Gold, Silver, Bronze }

fun tierOf(percent: Float?): Tier? = when {
    percent == null -> null
    percent < 10f -> Tier.Gold
    percent < 33f -> Tier.Silver
    else -> Tier.Bronze
}

@Composable
private fun trophyColor(tier: Tier?): Color {
    val c = LocalWiis.current.spec.companion
    return when (tier) {
        Tier.Gold -> c.trophyGold.toColorOr(Color(0xFFE9AE25))
        Tier.Silver -> c.trophySilver.toColorOr(Color(0xFFA3AEBE))
        Tier.Bronze -> c.trophyBronze.toColorOr(Color(0xFFC9773D))
        null -> LocalWiis.current.glassTextMuted
    }
}

/** A line in the play-time box beside the achievements. [colorIndex] picks one of the Stats colours. */
data class PlayFact(val icon: PageIcon, val colorIndex: Int, val label: String, val value: String)

/** [result] null means loading. [game] or [systemId] give the header its art; [facts] the play-time box. */
@Composable
fun AchievementsPage(
    result: RaResult?,
    color: Color,
    onSetUp: () -> Unit,
    onRetry: () -> Unit,
    game: Game? = null,
    systemId: String? = null,
    facts: List<PlayFact> = emptyList(),
) {
    when (result) {
        null -> EmptyPage("Loading achievements…", "")
        RaResult.NotSetUp -> EmptyPage("Connect RetroAchievements", "Add your RetroAchievements username and Web API key on the top screen.", "Set up", onSetUp)
        RaResult.Unsupported -> EmptyPage("No RetroAchievements here", "RetroAchievements has no sets for this system.")
        is RaResult.NoMatch -> EmptyPage(
            "No achievement set found",
            "RetroAchievements doesn't list this ROM. A No-Intro or Redump copy usually matches." + (result.hash?.let { "\nFingerprint: $it" } ?: ""),
        )
        is RaResult.Failed -> EmptyPage("Couldn't load achievements", result.message, "Try again", onRetry)
        is RaResult.OfGame -> GameAchievements(result, color, game, facts)
        is RaResult.OfSystem -> SystemAchievements(result, color, systemId, facts)
    }
}

@Composable
private fun GameAchievements(result: RaResult.OfGame, color: Color, game: Game?, facts: List<PlayFact>) {
    val style = LocalWiis.current
    val c = style.spec.companion
    val p = result.progress
    if (p.achievements.isEmpty()) {
        EmptyPage("No achievements yet", "${p.title} is on RetroAchievements but has no achievements.")
        return
    }
    val players = p.numDistinctPlayers
    val list = remember(p) {
        p.achievements.sortedWith(
            compareByDescending<RaAchievement> { it.earned }
                .thenByDescending { if (it.earned) it.earnedAt ?: "" else "" }
                .thenByDescending { if (it.earned) 0f else it.rarityPercent(players) ?: 0f }
                .thenBy { it.displayOrder },
        )
    }
    var filter by remember(p.id) { mutableStateOf("all") }
    val shown = list.filter { a -> when (filter) { "locked" -> !a.earned; "unlocked" -> a.earned; else -> true } }
    val earned = p.earned
    val fraction = earned.toFloat() / p.achievements.size
    TwoColumns(
        main = {
            HeaderCard(
                art = { if (game != null) MiniArt(game, 76.dp, 96.dp, 10.dp) else RemoteImage(p.iconUrl, 76.dp, RoundedCornerShape(10.dp)) },
                title = game?.title ?: p.title,
            ) {
                if (players > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Tier.entries.forEach { t ->
                            val all = p.achievements.filter { tierOf(it.rarityPercent(players)) == t }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TrophyIcon(16.dp, trophyColor(t))
                                Text("${all.count { it.earned }}/${all.size}", style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = style.glassText), maxLines = 1)
                            }
                        }
                    }
                }
                if (!c.achievementsRing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { ProgressBar(fraction, color) }
                        Text("${(fraction * 100).roundToInt()}%", style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = style.glassText))
                    }
                }
            }
            FilterChips(listOf(Triple("all", "All", PageIcon.Star), Triple("locked", "Locked", PageIcon.Lock), Triple("unlocked", "Unlocked", PageIcon.Check)), filter) { filter = it }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown, key = { it.id }) { a -> AchievementRow(a, players) }
            }
        },
        side = sideColumn(facts, fraction, "$earned of ${p.achievements.size}", "unlocked", color),
    )
}

@Composable
private fun AchievementRow(a: RaAchievement, players: Int) {
    val style = LocalWiis.current
    val rarity = a.rarityPercent(players)
    val small = TextStyle(fontFamily = style.fonts.body, fontSize = 11.sp, color = style.glassTextMuted)
    Row(
        Modifier.fillMaxWidth().glassBox(style, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.alpha(if (a.earned) 1f else 0.55f)) { RemoteImage(if (a.earned) a.badgeUrl else a.lockedBadgeUrl, 40.dp, RoundedCornerShape(9.dp)) }
        Column(Modifier.weight(1f).alpha(if (a.earned) 1f else 0.62f)) {
            Text(a.title, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = style.glassText), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.description, style = TextStyle(fontFamily = style.fonts.body, fontSize = 11.5.sp, lineHeight = 15.sp, color = style.glassTextMuted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (a.earned) TrophyIcon(14.dp, trophyColor(tierOf(rarity))) else PageIconView(PageIcon.Lock, 13.dp, style.glassTextMuted)
                Text(if (a.earned) "Unlocked" else "Locked", style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = if (a.earned) style.glassText else style.glassTextMuted), maxLines = 1)
            }
            val detail = if (a.earned) a.earnedAt?.let { raDateTime(it, style) } else rarity?.let { if (it < 1f) "Under 1% of players" else "${it.roundToInt()}% of players" }
            if (detail != null) Text(detail, style = small, maxLines = 1)
        }
    }
}

@Composable
private fun SystemAchievements(result: RaResult.OfSystem, color: Color, systemId: String?, facts: List<PlayFact>) {
    val style = LocalWiis.current
    if (result.games.isEmpty()) {
        EmptyPage("Nothing unlocked on ${result.consoleName} yet", "Games you've earned achievements in appear here.")
        return
    }
    val games = remember(result) { result.games.sortedByDescending { it.mostRecentAwardedDate ?: "" } }
    var filter by remember(result.consoleName) { mutableStateOf("all") }
    val shown = games.filter { g -> when (filter) { "progress" -> g.award() == null; "finished" -> g.award() != null; else -> true } }
    val got = games.sumOf { it.unlocked }
    val max = games.sumOf { it.maxPossible }
    TwoColumns(
        main = {
            HeaderCard(
                art = {
                    Box(
                        Modifier.size(76.dp, 96.dp).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(mix(color, Color.White, 0.2f), mix(color, Color.Black, 0.3f)))),
                        contentAlignment = Alignment.Center,
                    ) { ConsoleGlyph(systemId ?: "all", 50.dp, Color.White) }
                },
                title = result.consoleName,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AwardCount(PageIcon.Star, trophyColor(Tier.Gold), "${games.count { it.award() == Award.Mastered || it.award() == Award.Completed }} mastered")
                    AwardCount(PageIcon.Flag, trophyColor(Tier.Silver), "${games.count { it.award() == Award.Beaten }} beaten")
                }
            }
            FilterChips(listOf(Triple("all", "All", PageIcon.Star), Triple("progress", "In progress", PageIcon.Trend), Triple("finished", "Finished", PageIcon.Check)), filter) { filter = it }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown, key = { it.gameId }) { g ->
                    Row(
                        Modifier.fillMaxWidth().glassBox(style, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RemoteImage(g.iconUrl, 40.dp, RoundedCornerShape(9.dp))
                        val f = if (g.maxPossible > 0) g.unlocked.toFloat() / g.maxPossible else 0f
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(g.title, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = style.glassText), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ProgressBar(f, color, 5.dp)
                            Text("${g.unlocked} of ${g.maxPossible} · ${(f * 100).roundToInt()}%", style = TextStyle(fontFamily = style.fonts.body, fontSize = 11.sp, color = style.glassTextMuted), maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            when (g.award()) {
                                Award.Mastered -> AwardCount(PageIcon.Star, trophyColor(Tier.Gold), "Mastered", small = true)
                                Award.Completed -> AwardCount(PageIcon.Star, trophyColor(Tier.Gold), "Completed", small = true)
                                Award.Beaten -> AwardCount(PageIcon.Flag, trophyColor(Tier.Silver), "Beaten", small = true)
                                null -> Unit
                            }
                            g.mostRecentAwardedDate?.let { raDate(it) }?.let { Text(it, style = TextStyle(fontFamily = style.fonts.body, fontSize = 11.sp, color = style.glassTextMuted), maxLines = 1) }
                        }
                    }
                }
            }
        },
        side = sideColumn(facts, if (max > 0) got.toFloat() / max else 0f, "$got of $max", "in ${games.size} games", color),
    )
}

private enum class Award { Mastered, Completed, Beaten }

private fun RaCompletionEntry.award(): Award? = when {
    highestAwardKind == "mastered" -> Award.Mastered
    highestAwardKind == "completed" -> Award.Completed
    highestAwardKind?.startsWith("beaten") == true -> Award.Beaten
    else -> null
}

@Composable
private fun AwardCount(icon: PageIcon, color: Color, text: String, small: Boolean = false) {
    val style = LocalWiis.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PageIconView(icon, if (small) 13.dp else 15.dp, color)
        Text(text, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = if (small) 11.5.sp else 12.5.sp, color = style.glassText), maxLines = 1)
    }
}

/** The list on the left; play time and the progress ring in a 150 dp column on the right. */
@Composable
private fun TwoColumns(main: @Composable ColumnScope.() -> Unit, side: (@Composable ColumnScope.() -> Unit)?) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp), content = main)
        if (side != null) Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp), content = side)
    }
}

@Composable
private fun sideColumn(facts: List<PlayFact>, fraction: Float, big: String, small: String, color: Color): (@Composable ColumnScope.() -> Unit)? {
    val c = LocalWiis.current.spec.companion
    val showFacts = c.achievementsPlaytime && facts.isNotEmpty()
    if (!showFacts && !c.achievementsRing) return null
    return {
        if (showFacts) FactsCard(facts)
        if (c.achievementsRing) RingCard(fraction, big, small, color, Modifier.weight(1f))
    }
}

@Composable
private fun HeaderCard(art: @Composable () -> Unit, title: String, line: @Composable ColumnScope.() -> Unit) {
    val style = LocalWiis.current
    Row(Modifier.fillMaxWidth().glassBox(style, cardShape(style)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        art()
        Column(Modifier.weight(1f).height(96.dp), verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically)) {
            Text(title, style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 19.5.sp, color = style.glassText), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(
                Modifier.clip(CircleShape).background(style.glassText.copy(alpha = 0.07f)).border(1.dp, style.glassText.copy(alpha = 0.1f), CircleShape).padding(start = 6.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TrophyIcon(13.dp, trophyColor(Tier.Gold))
                Text("RetroAchievements", style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = style.glassTextMuted), maxLines = 1)
            }
            line()
        }
    }
}

@Composable
private fun FilterChips(options: List<Triple<String, String, PageIcon>>, current: String, onPick: (String) -> Unit) {
    val style = LocalWiis.current
    val shape = RoundedCornerShape(10.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label, icon) ->
            val on = key == current
            val fg = if (on) style.tabActiveText else style.glassTextMuted
            Row(
                Modifier.height(30.dp).clip(shape).then(if (on) Modifier.background(style.tabActiveFill) else Modifier.glassBox(style, shape))
                    .clickable { onPick(key) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PageIconView(icon, 14.dp, fg)
                Text(label, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = fg), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun FactsCard(facts: List<PlayFact>) {
    val style = LocalWiis.current
    Column(Modifier.fillMaxWidth().glassBox(style, cardShape(style)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        facts.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PageIconView(f.icon, 20.dp, statColor(f.colorIndex), strokeUnits = 2.1f)
                Column {
                    Text(f.label, style = TextStyle(fontFamily = style.fonts.body, fontSize = 11.sp, color = style.glassTextMuted), maxLines = 1)
                    Text(f.value, style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, color = style.glassText), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun RingCard(fraction: Float, big: String, small: String, color: Color, modifier: Modifier) {
    val style = LocalWiis.current
    Column(
        modifier.fillMaxWidth().glassBox(style, cardShape(style)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            val track = style.glassText.copy(alpha = 0.1f)
            val light = mix(color, Color.White, 0.35f)
            Canvas(Modifier.matchParentSize()) {
                val inset = 10.dp.toPx()
                val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
                val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(width = 9.dp.toPx()))
                if (fraction > 0f) drawArc(Brush.linearGradient(listOf(light, color)), -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TrophyIcon(16.dp, trophyColor(Tier.Gold))
                Text("${(fraction * 100).roundToInt()}%", style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 22.sp, color = style.glassText))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = style.glassText), maxLines = 1)
            Text(small, style = TextStyle(fontFamily = style.fonts.body, fontSize = 11.sp, color = style.glassTextMuted), maxLines = 1)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color, height: Dp = 6.dp) {
    val style = LocalWiis.current
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(style.glassText.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(mix(color, Color.White, 0.25f), color))))
    }
}

/** RetroAchievements dates are UTC ("2026-10-25 20:17:00", sometimes ISO); shown in local time. */
private fun raInstant(text: String): Instant? = runCatching {
    if (text.contains('T')) java.time.OffsetDateTime.parse(text).toInstant()
    else java.time.LocalDateTime.parse(text.trim().replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC)
}.getOrNull()

private fun raDate(text: String): String? = raInstant(text)?.let { shortDate(it.toEpochMilli()) }

private fun raDateTime(text: String, style: WiisStyle): String? = raInstant(text)?.let { "${shortDate(it.toEpochMilli())} · ${timeLabel(it.toEpochMilli(), style)}" }

// ---- Shared pieces ------------------------------------------------------------------------------

@Composable
fun EmptyPage(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    val style = LocalWiis.current
    Column(Modifier.fillMaxSize().padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        Text(title, style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = style.text), textAlign = TextAlign.Center)
        if (text.isNotEmpty()) Text(text, style = style.label, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                action,
                style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = style.tabActiveText),
                modifier = Modifier.clip(CircleShape).background(style.tabActiveFill).clickable(onClick = onAction).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val style = LocalWiis.current
    Column(Modifier.fillMaxWidth().glassBox(style, cardShape(style)).padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
}

@Composable
private fun MiniArt(game: Game, width: Dp, height: Dp = width, radius: Dp = 8.dp) {
    val px = with(LocalDensity.current) { maxOf(width, height).roundToPx() }
    val art = rememberArt(game, px * 2)
    Box(Modifier.size(width, height).clip(RoundedCornerShape(radius)).background(LocalWiis.current.systemColor(game.systemId))) {
        if (art != null) Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

/** A badge or game icon from RetroAchievements, with a quiet placeholder while it loads. */
@Composable
private fun RemoteImage(url: String?, size: Dp, shape: RoundedCornerShape) {
    val style = LocalWiis.current
    val images = LocalImageSource.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<android.graphics.Bitmap?>(null, url, images) { value = url?.let { images.remote(it, px) } }
    Box(Modifier.size(size).clip(shape).background(style.glassText.copy(alpha = 0.1f))) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

private fun cardShape(style: WiisStyle) = RoundedCornerShape(maxOf(10, style.spec.glass.radius - 4).dp)
private fun cardTitle(style: WiisStyle) = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = style.glassText)
private fun rowTitle(style: WiisStyle) = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = style.glassText)

/** "3 h 12 min", "21 min", "0 min". */
fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

private fun shortDate(ms: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
