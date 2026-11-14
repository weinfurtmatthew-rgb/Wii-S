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
)

data class SystemStats(
    val totalMillis: Long,
    val gamesPlayed: Int,
    val gamesTotal: Int,
    /** The three most played games with their time, most first. */
    val top: List<Pair<Game, Long>>,
    val week: Week,
)

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
        )
    }

    /** A system's games ("all": every game). [storedTotal] gives each game's total time. */
    fun system(systemId: String, games: List<Game>, log: List<Played>, storedTotal: (String) -> Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): SystemStats {
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
        )
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
