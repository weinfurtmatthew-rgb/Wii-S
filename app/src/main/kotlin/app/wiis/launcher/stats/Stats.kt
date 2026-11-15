package app.wiis.launcher.stats

import app.wiis.emulator.Game
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One play session. */
data class Played(val uri: String, val systemId: String, val start: Long, val end: Long) {
    val millis: Long get() = (end - start).coerceAtLeast(0)
}

/**
 * Every session from 0.4.0 on, one line each ("uri, system, start, end" separated by tabs), so
 * the bottom screen can show sessions and this week. Playtime from before 0.4.0 is still in the
 * totals (see [Stats.game]); it just has no session detail.
 */
class SessionLog(private val file: File) {
    private var cache: List<Played>? = null

    @Synchronized
    fun add(p: Played) {
        file.appendText("${p.uri}\t${p.systemId}\t${p.start}\t${p.end}\n")
        cache = all() + p
    }

    @Synchronized
    fun all(): List<Played> = cache ?: load().also { cache = it }

    private fun load(): List<Played> = if (!file.exists()) emptyList() else file.readLines().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size != 4) return@mapNotNull null
        Played(p[0], p[1], p[2].toLongOrNull() ?: return@mapNotNull null, p[3].toLongOrNull() ?: return@mapNotNull null)
    }
}

data class Week(
    /** Play time per day, Monday to Sunday of the current week. */
    val days: List<Long>,
    val todayIndex: Int,
) {
    val total: Long get() = days.sum()
}

data class GameStats(
    val totalMillis: Long,
    val sessions: Int,
    val averageMillis: Long,
    val longestMillis: Long,
    val firstPlayed: Long?,
    val lastPlayed: Long?,
    val week: Week,
    val history: History = History.EMPTY,
    /** The latest sessions, newest first. */
    val recent: List<Played> = emptyList(),
)

data class SystemStats(
    val totalMillis: Long,
    val gamesPlayed: Int,
    val gamesTotal: Int,
    /** The three most played games with their time, most first. */
    val top: List<Pair<Game, Long>>,
    val week: Week,
    val history: History = History.EMPTY,
    /** Sessions logged, and the last time any of these games was played. */
    val sessions: Int = 0,
    val lastPlayed: Long? = null,
)

/** Logged play over the last 12 weeks and 12 months, oldest first (the current one last). */
data class History(
    val weeks: List<Long>,
    val weekSessions: List<Int>,
    /** Different games played in each week. */
    val weekGames: List<Int>,
    val months: List<Long>,
) {
    /** Weeks from the first logged session to this one (at least 1): the fair divisor for averages. */
    val activeWeeks: Int get() = (weeks.size - weeks.indices.firstOrNull { weeks[it] > 0 || weekSessions[it] > 0 }.let { it ?: (weeks.size - 1) }).coerceAtLeast(1)
    val perWeekMillis: Long get() = weeks.sum() / activeWeeks
    val sessionsPerWeek: Float get() = weekSessions.sum().toFloat() / activeWeeks

    companion object {
        val EMPTY = History(List(12) { 0L }, List(12) { 0 }, List(12) { 0 }, List(12) { 0L })
    }
}

object Stats {
    /**
     * [storedTotal] and [storedLast] come from the playtime store, which also counts play from
     * before sessions were logged; the larger of the two totals wins.
     */
    fun game(uri: String, log: List<Played>, storedTotal: Long, storedLast: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): GameStats {
        val mine = log.filter { it.uri == uri }
        val total = maxOf(storedTotal, mine.sumOf { it.millis })
        return GameStats(
            totalMillis = total,
            sessions = mine.size,
            averageMillis = if (mine.isEmpty()) 0 else mine.sumOf { it.millis } / mine.size,
            longestMillis = mine.maxOfOrNull { it.millis } ?: 0,
            firstPlayed = mine.minOfOrNull { it.start },
            lastPlayed = maxOf(storedLast, mine.maxOfOrNull { it.end } ?: 0).takeIf { it > 0 },
            week = week(mine, now, zone),
            history = history(mine, now, zone),
            recent = mine.sortedByDescending { it.start }.take(4),
        )
    }

    /** A system's games ("all": every game). [storedTotal] gives each game's total time. */
    fun system(
        systemId: String,
        games: List<Game>,
        log: List<Played>,
        storedTotal: (String) -> Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        storedLast: (String) -> Long = { 0L },
    ): SystemStats {
        val mine = if (systemId == "all") games else games.filter { it.systemId == systemId }
        val uris = mine.map { it.uri }.toSet()
        val sessions = log.filter { it.uri in uris }
        val byGame = mine.map { g -> g to maxOf(storedTotal(g.uri), sessions.filter { it.uri == g.uri }.sumOf { it.millis }) }
        val played = byGame.filter { it.second > 0 }
        return SystemStats(
            totalMillis = played.sumOf { it.second },
            gamesPlayed = played.size,
            gamesTotal = mine.size,
            top = played.sortedByDescending { it.second }.take(3),
            week = week(sessions, now, zone),
            history = history(sessions, now, zone),
            sessions = sessions.size,
            lastPlayed = (mine.maxOfOrNull { storedLast(it.uri) } ?: 0L).coerceAtLeast(sessions.maxOfOrNull { it.end } ?: 0L).takeIf { it > 0 },
        )
    }

    /** The last 12 weeks (Monday to Sunday) and 12 months of [sessions]; a session over midnight counts on both days. */
    fun history(sessions: List<Played>, now: Long, zone: ZoneId = ZoneId.systemDefault(), count: Int = 12): History {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(count - 1L)
        val firstMonth = java.time.YearMonth.from(today).minusMonths(count - 1L)
        val weeks = LongArray(count)
        val months = LongArray(count)
        val weekSessions = IntArray(count)
        val weekGames = Array(count) { HashSet<String>() }
        fun weekOf(day: LocalDate) = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, day).let { if (it < 0) -1 else (it / 7).toInt() }
        for (s in sessions) {
            val startDay = Instant.ofEpochMilli(s.start).atZone(zone).toLocalDate()
            weekOf(startDay).takeIf { it in 0 until count }?.let { weekSessions[it]++ }
            forEachDay(s, zone) { day, ms ->
                weekOf(day).takeIf { it in 0 until count }?.let { weeks[it] += ms; weekGames[it] += s.uri }
                java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, java.time.YearMonth.from(day)).toInt().takeIf { it in 0 until count }?.let { months[it] += ms }
            }
        }
        return History(weeks.toList(), weekSessions.toList(), weekGames.map { it.size }, months.toList())
    }

    /** [s] split at local midnights: each day it touches with the time spent in it. */
    private inline fun forEachDay(s: Played, zone: ZoneId, each: (LocalDate, Long) -> Unit) {
        var day = Instant.ofEpochMilli(s.start).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(s.end).atZone(zone).toLocalDate()
        while (!day.isAfter(last)) {
            val from = maxOf(s.start, startOf(day, zone))
            val to = minOf(s.end, startOf(day.plusDays(1), zone))
            if (to > from) each(day, to - from)
            day = day.plusDays(1)
        }
    }

    /** Monday-to-Sunday play time for the week containing [now]; a session over midnight counts on both days. */
    fun week(sessions: List<Played>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Week {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = LongArray(7)
        for (s in sessions) {
            var day = Instant.ofEpochMilli(s.start).atZone(zone).toLocalDate()
            val last = Instant.ofEpochMilli(s.end).atZone(zone).toLocalDate()
            while (!day.isAfter(last)) {
                val from = maxOf(s.start, startOf(day, zone))
                val to = minOf(s.end, startOf(day.plusDays(1), zone))
                val index = java.time.temporal.ChronoUnit.DAYS.between(monday, day).toInt()
                if (index in 0..6 && to > from) days[index] += to - from
                day = day.plusDays(1)
            }
        }
        return Week(days.toList(), java.time.temporal.ChronoUnit.DAYS.between(monday, today).toInt())
    }

    private fun startOf(day: LocalDate, zone: ZoneId) = day.atStartOfDay(zone).toInstant().toEpochMilli()
}
