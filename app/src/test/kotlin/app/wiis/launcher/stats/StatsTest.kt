package app.wiis.launcher.stats

import app.wiis.emulator.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class StatsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private val minute = 60_000L

    // Saturday 14 Nov 2026; the week runs Monday 9 to Sunday 15.
    private val now = at(2026, 11, 14, 20)

    private fun game(title: String, system: String) = Game("u:$title", "a", title, title, "$title.rom", system, 1)

    @Test fun `week buckets Monday to Sunday and splits a session over midnight`() {
        val log = listOf(
            Played("a", "nds", at(2026, 11, 9, 10), at(2026, 11, 9, 10, 30)), // Monday 30 min
            Played("a", "nds", at(2026, 11, 13, 23, 30), at(2026, 11, 14, 0, 45)), // Fri 30 + Sat 45
            Played("a", "nds", at(2026, 11, 8, 12), at(2026, 11, 8, 13)), // last Sunday: not this week
        )
        val week = Stats.week(log, now, zone)
        assertEquals(listOf(30L, 0, 0, 0, 30, 45, 0).map { it * minute }, week.days)
        assertEquals(5, week.todayIndex)
        assertEquals(105 * minute, week.total)
    }

    @Test fun `game stats use logged sessions and keep older stored time`() {
        val log = listOf(
            Played("a", "nds", at(2026, 11, 10, 18), at(2026, 11, 10, 18, 40)),
            Played("a", "nds", at(2026, 11, 12, 20), at(2026, 11, 12, 21, 20)),
            Played("b", "gba", at(2026, 11, 12, 9), at(2026, 11, 12, 9, 5)),
        )
        val s = Stats.game("a", log, storedTotal = 300 * minute, storedLast = 0, now = now, zone = zone)
        assertEquals(300 * minute, s.totalMillis)
        assertEquals(2, s.sessions)
        assertEquals(60 * minute, s.averageMillis)
        assertEquals(80 * minute, s.longestMillis)
        assertEquals(at(2026, 11, 10, 18), s.firstPlayed)
        assertEquals(at(2026, 11, 12, 21, 20), s.lastPlayed)
    }

    @Test fun `a game never played has no dates`() {
        val s = Stats.game("x", emptyList(), 0, 0, now, zone)
        assertEquals(0, s.sessions)
        assertNull(s.firstPlayed)
        assertNull(s.lastPlayed)
    }

    @Test fun `system stats count played games and list the top three`() {
        val games = listOf(game("A", "nds"), game("B", "nds"), game("C", "nds"), game("D", "nds"), game("E", "gba"))
        val stored = mapOf("u:A" to 10 * minute, "u:B" to 50 * minute, "u:C" to 30 * minute, "u:E" to 99 * minute)
        val s = Stats.system("nds", games, emptyList(), { stored[it] ?: 0 }, now, zone)
        assertEquals(90 * minute, s.totalMillis)
        assertEquals(3, s.gamesPlayed)
        assertEquals(4, s.gamesTotal)
        assertEquals(listOf("B", "C", "A"), s.top.map { it.first.title })
        val all = Stats.system("all", games, emptyList(), { stored[it] ?: 0 }, now, zone)
        assertEquals(5, all.gamesTotal)
        assertEquals("E", all.top.first().first.title)
    }

    @Test fun `history spreads sessions over weeks and months`() {
        val log = listOf(
            Played("a", "nds", at(2026, 11, 13, 23, 30), at(2026, 11, 14, 0, 45)), // this week: 75 min
            Played("b", "nds", at(2026, 11, 3, 20), at(2026, 11, 3, 21)), // last week: 60 min
            Played("a", "nds", at(2026, 8, 25, 20), at(2026, 8, 25, 20, 30)), // the first of the 12 weeks: 30 min, August
            Played("a", "nds", at(2026, 6, 1, 20), at(2026, 6, 1, 21)), // before the 12 weeks, June
        )
        val h = Stats.history(log, now, zone)
        assertEquals(12, h.weeks.size)
        assertEquals(75 * minute, h.weeks[11])
        assertEquals(60 * minute, h.weeks[10])
        assertEquals(30 * minute, h.weeks[0])
        assertEquals(listOf(1, 1, 1), listOf(h.weekSessions[11], h.weekSessions[10], h.weekSessions[0]))
        assertEquals(1, h.weekGames[10])
        // Months: June .. November, the current month last.
        assertEquals(60 * minute, h.months[11 - 5])
        assertEquals(30 * minute, h.months[11 - 3])
        assertEquals(135 * minute, h.months[11])
        // The first session is in the first of the 12 weeks, so averages count all 12.
        assertEquals(12, h.activeWeeks)
        assertEquals(165 * minute / 12, h.perWeekMillis)
    }

    @Test fun `averages start from the first logged week`() {
        val h = Stats.history(listOf(Played("a", "nds", at(2026, 11, 10, 18), at(2026, 11, 10, 19))), now, zone)
        assertEquals(1, h.activeWeeks)
        assertEquals(60 * minute, h.perWeekMillis)
        assertEquals(1, History.EMPTY.activeWeeks)
    }

    @Test fun `game stats keep the latest sessions, newest first`() {
        val log = (1..6).map { Played("a", "nds", at(2026, 11, 8 + it, 18), at(2026, 11, 8 + it, 18) + 10 * it * minute) }
        val s = Stats.game("a", log, 0, 0, now, zone)
        assertEquals(listOf(14, 13, 12, 11), s.recent.map { Instant.ofEpochMilli(it.start).atZone(zone).dayOfMonth })
    }

    @Test fun `session log survives a reload`() {
        val file = File.createTempFile("sessions", ".tsv").apply { delete() }
        val log = SessionLog(file)
        log.add(Played("content://x/1", "nds", 1, 2))
        log.add(Played("content://x/2", "gba", 3, 9))
        assertEquals(2, SessionLog(file).all().size)
        assertEquals(6, SessionLog(file).all()[1].millis)
        file.delete()
    }
}
