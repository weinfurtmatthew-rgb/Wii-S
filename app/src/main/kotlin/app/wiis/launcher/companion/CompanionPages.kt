package app.wiis.launcher.companion

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

/** What the bottom screen is about: a game (perhaps being played) or a system. */
sealed interface CompanionSubject {
    data class OfGame(val game: Game, val session: Session?) : CompanionSubject
    data class OfSystem(val id: String, val name: String, val count: Int) : CompanionSubject
}

enum class CompanionPage(val label: String) { Details("Details"), Stats("Stats"), Achievements("Achievements") }

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
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (tabs || showClock) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tabs) PageTabs(pages, pagerState)
                Spacer(Modifier.weight(1f))
                if (showClock) ClockText(style.clock.copy(color = style.text))
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(), pageSpacing = 24.dp, beyondViewportPageCount = 1) { i ->
            Box(Modifier.fillMaxSize()) { page(pages[i]) }
        }
        if (!tabs && pages.size > 1) PageDots(pages, pagerState)
    }
}

@Composable
private fun PageTabs(pages: List<CompanionPage>, state: PagerState) {
    val style = LocalWiis.current
    val scope = rememberCoroutineScope()
    Row(Modifier.glassBox(style, CircleShape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        pages.forEachIndexed { i, p ->
            val on = state.currentPage == i
            Text(
                p.label,
                style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = style.spec.type.labelSize.sp, color = if (on) style.tabActiveText else style.glassTextMuted),
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) style.tabActiveFill else Color.Transparent)
                    .clickable { scope.launch { state.animateScrollToPage(i) } }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
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

@Composable
fun Fact(label: String, value: String, style: WiisStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style.label, modifier = Modifier.width(110.dp))
        Text(value, style = style.body)
    }
}

// ---- Stats -------------------------------------------------------------------------------------

@Composable
fun GameStatsPage(stats: GameStats, color: Color) {
    val style = LocalWiis.current
    if (stats.totalMillis <= 0) {
        EmptyPage("Not played yet", "Time played, sessions and this week's chart appear after the first session.")
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigNumber(duration(stats.totalMillis), "played in total")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val none = stats.sessions == 0
            FactCard("Sessions", if (none) "–" else "${stats.sessions}", Modifier.weight(1f))
            FactCard("Average", if (none) "–" else duration(stats.averageMillis), Modifier.weight(1f))
            FactCard("Longest", if (none) "–" else duration(stats.longestMillis), Modifier.weight(1f))
            FactCard("First played", stats.firstPlayed?.let(::shortDate) ?: "–", Modifier.weight(1f))
        }
        WeekChart(stats.week, color)
        if (stats.sessions == 0) Text("Sessions are counted from Wii-S 0.4.0 on.", style = style.label)
    }
}

@Composable
fun SystemStatsPage(stats: SystemStats, name: String, color: Color) {
    val style = LocalWiis.current
    if (stats.totalMillis <= 0) {
        EmptyPage("Nothing played on $name yet", "Time played and your most played games appear here.")
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigNumber(duration(stats.totalMillis), "played on $name · ${stats.gamesPlayed} of ${stats.gamesTotal} games played")
        Card {
            Text("Most played", style = cardTitle(style))
            stats.top.forEach { (game, ms) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniArt(game, 34.dp)
                    Text(game.title, style = rowTitle(style), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(duration(ms), style = style.label.copy(color = style.glassTextMuted), maxLines = 1)
                }
            }
        }
        WeekChart(stats.week, color)
    }
}

@Composable
private fun BigNumber(value: String, caption: String) {
    val style = LocalWiis.current
    Column {
        Text(value, style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp, color = style.text), maxLines = 1)
        Text(caption, style = style.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FactCard(label: String, value: String, modifier: Modifier) {
    val style = LocalWiis.current
    Column(modifier.glassBox(style, cardShape(style)).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(label, style = style.label.copy(color = style.glassTextMuted), maxLines = 1, softWrap = false)
        Text(value, style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = style.glassText), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun WeekChart(week: Week, color: Color) {
    val style = LocalWiis.current
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val max = maxOf(30 * 60_000L, week.days.maxOrNull() ?: 0L)
    Card {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This week", style = cardTitle(style), modifier = Modifier.alignByBaseline())
            Text(duration(week.total), style = style.label.copy(color = style.glassTextMuted), modifier = Modifier.alignByBaseline())
        }
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            week.days.forEachIndexed { i, ms ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(
                        Modifier
                            .widthIn(max = 24.dp)
                            .fillMaxWidth()
                            .height((3 + 61 * (ms.toFloat() / max)).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (i == week.todayIndex) color else style.glassText.copy(alpha = 0.16f)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(days[i], style = TextStyle(fontFamily = style.fonts.body, fontSize = 11.sp, color = style.glassTextMuted), maxLines = 1)
                }
            }
        }
    }
}

// ---- RetroAchievements ------------------------------------------------------------------------

/** [result] null means loading. */
@Composable
fun AchievementsPage(result: RaResult?, color: Color, onSetUp: () -> Unit, onRetry: () -> Unit) {
    when (result) {
        null -> EmptyPage("Loading achievements…", "")
        RaResult.NotSetUp -> EmptyPage("Connect RetroAchievements", "Add your RetroAchievements username and Web API key on the top screen.", "Set up", onSetUp)
        RaResult.Unsupported -> EmptyPage("No RetroAchievements here", "RetroAchievements has no sets for this system.")
        is RaResult.NoMatch -> EmptyPage(
            "No achievement set found",
            "RetroAchievements doesn't list this ROM. A No-Intro or Redump copy usually matches." + (result.hash?.let { "\nFingerprint: $it" } ?: ""),
        )
        is RaResult.Failed -> EmptyPage("Couldn't load achievements", result.message, "Try again", onRetry)
        is RaResult.OfGame -> GameAchievements(result, color)
        is RaResult.OfSystem -> SystemAchievements(result, color)
    }
}

@Composable
private fun GameAchievements(result: RaResult.OfGame, color: Color) {
    val style = LocalWiis.current
    val p = result.progress
    if (p.achievements.isEmpty()) {
        EmptyPage("No achievements yet", "${p.title} is on RetroAchievements but has no achievements.")
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProgressHeader("${p.earned} of ${p.achievements.size}", "unlocked · ${p.earnedPoints} of ${p.points} points", p.earned.toFloat() / p.achievements.size, color)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(p.ordered, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().alpha(if (a.earned) 1f else 0.62f).glassBox(style, cardShape(style)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RemoteImage(if (a.earned) a.badgeUrl else a.lockedBadgeUrl, 36.dp, RoundedCornerShape(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = rowTitle(style), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.description, style = TextStyle(fontFamily = style.fonts.body, fontSize = 12.sp, color = style.glassTextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${a.points} pts", style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = style.glassTextMuted), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SystemAchievements(result: RaResult.OfSystem, color: Color) {
    val style = LocalWiis.current
    if (result.games.isEmpty()) {
        EmptyPage("Nothing unlocked on ${result.consoleName} yet", "Games you've earned achievements in appear here.")
        return
    }
    val got = result.games.sumOf { it.unlocked }
    val max = result.games.sumOf { it.maxPossible }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProgressHeader("$got of $max", "unlocked on ${result.consoleName}", if (max > 0) got.toFloat() / max else 0f, color)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(result.games, key = { it.gameId }) { g ->
                Row(
                    Modifier.fillMaxWidth().glassBox(style, cardShape(style)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RemoteImage(g.iconUrl, 34.dp, RoundedCornerShape(8.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.title, style = rowTitle(style), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${g.unlocked} of ${g.maxPossible}", style = TextStyle(fontFamily = style.fonts.body, fontSize = 12.sp, color = style.glassTextMuted), maxLines = 1)
                        }
                        ProgressBar(if (g.maxPossible > 0) g.unlocked.toFloat() / g.maxPossible else 0f, color)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(value: String, caption: String, fraction: Float, color: Color) {
    val style = LocalWiis.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(value, style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = style.text), modifier = Modifier.alignByBaseline())
            Text(caption, style = style.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
        }
        ProgressBar(fraction, color)
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    val style = LocalWiis.current
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(style.glassText.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

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
private fun MiniArt(game: Game, size: Dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val art = rememberArt(game, px * 2)
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(LocalWiis.current.systemColor(game.systemId))) {
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
