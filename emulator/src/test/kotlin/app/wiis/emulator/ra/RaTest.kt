package app.wiis.emulator.ra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class RaHashTest {
    private fun md5(vararg parts: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        parts.forEach { md.update(it) }
        return RaHash.hex(md.digest())
    }
    private fun data(n: Int, seed: Int) = ByteArray(n) { ((it * 31 + seed * 7) xor (it shr 3)).toByte() }
    private fun both(system: String, file: ByteArray): String? {
        val buffer = RaHash.ofBuffer(system, file)
        val streamed = RaHash.ofFile(system, file.size.toLong(), { file.inputStream() }) { off, len ->
            file.copyOfRange(off.toInt().coerceIn(0, file.size), (off + len).toInt().coerceIn(0, file.size))
        }
        assertEquals("$system: streaming and in-memory fingerprints differ", buffer, streamed)
        return buffer
    }

    @Test fun `NES skips the iNES header, and only then`() {
        val payload = data(40_000, 1)
        val header = byteArrayOf('N'.code.toByte(), 'E'.code.toByte(), 'S'.code.toByte(), 0x1A) + ByteArray(12)
        assertEquals(md5(payload), both("nes", header + payload))
        assertEquals(md5(payload), both("nes", payload))
    }

    @Test fun `SNES skips a 512-byte copier header`() {
        val rom = data(0x2000 * 4, 2)
        assertEquals(md5(rom), both("snes", ByteArray(512) + rom))
        assertEquals(md5(rom), both("snes", rom))
    }

    @Test fun `N64 hashes the big-endian image whatever the byte order`() {
        val z64 = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40) + data(70_002, 3).copyOf(70_000)
        val v64 = z64.copyOf().also { b -> for (i in b.indices step 2) { val t = b[i]; b[i] = b[i + 1]; b[i + 1] = t } }
        val n64 = z64.copyOf().also { b -> for (i in b.indices step 4) { b.reverse(i, i + 4) } }
        val expected = md5(z64)
        assertEquals(expected, both("n64", z64))
        assertEquals(expected, both("n64", v64))
        assertEquals(expected, both("n64", n64))
        assertNull(RaHash.ofBuffer("n64", data(64, 4).also { it[0] = 0x12 }))
    }

    @Test fun `Game Boy family and Genesis hash the whole file`() {
        val rom = data(100_000, 5)
        for (system in listOf("gb", "gbc", "gba", "genesis")) assertEquals(system, md5(rom), both(system, rom))
    }

    @Test fun `DS hashes header, ARM9, ARM7 and icon`() {
        val rom = data(0x20000, 6)
        fun put32(at: Int, v: Int) { for (i in 0 until 4) rom[at + i] = (v shr (8 * i)).toByte() }
        put32(0x20, 0x4000); put32(0x2C, 0x1234); put32(0x30, 0x8000); put32(0x3C, 0x0800); put32(0x68, 0x1F000)
        rom[0] = 'W'.code.toByte()
        val expected = md5(
            rom.copyOfRange(0, 0x160),
            rom.copyOfRange(0x4000, 0x4000 + 0x1234),
            rom.copyOfRange(0x8000, 0x8000 + 0x0800),
            rom.copyOfRange(0x1F000, 0x1F000 + 0xA00), // the icon and titles block is 0xA00 bytes
        )
        assertEquals(expected, both("nds", rom))
    }

    @Test fun `DS icon past the end of the file is padded with zeros`() {
        val rom = data(0x10000, 7)
        fun put32(at: Int, v: Int) { for (i in 0 until 4) rom[at + i] = (v shr (8 * i)).toByte() }
        put32(0x20, 0x4000); put32(0x2C, 0x100); put32(0x30, 0x5000); put32(0x3C, 0x100); put32(0x68, 0xFC00)
        val icon = rom.copyOfRange(0xFC00, 0x10000) + ByteArray(0xA00 - 0x400)
        assertEquals(md5(rom.copyOfRange(0, 0x160), rom.copyOfRange(0x4000, 0x4100), rom.copyOfRange(0x5000, 0x5100), icon), both("nds", rom))
    }

    @Test fun `disc systems have no fingerprint here`() {
        assertNull(RaHash.ofBuffer("psx", data(1000, 8)))
        assertTrue("psx" !in RaHash.SUPPORTED)
    }
}

class RaTitlesTest {
    @Test fun `region tags, punctuation and a trailing The are ignored`() {
        val ra = RaTitles.normalize("Legend of Zelda, The: Spirit Tracks")
        assertEquals("the legend of zelda spirit tracks", ra)
        assertEquals(ra, RaTitles.normalize("Legend of Zelda, The - Spirit Tracks (USA) (En,Fr,Es)"))
        assertEquals("pokemon platinum version", RaTitles.normalize("Pokémon - Platinum Version (USA) [b]"))
        assertEquals("mario and luigi bowser s inside story", RaTitles.normalize("Mario & Luigi - Bowser's Inside Story (Europe)"))
    }

    private val list = listOf(
        RaGameListEntry(1, "Pokemon Platinum Version", 18, 80, listOf("aaa")),
        RaGameListEntry(2, "~Hack~ Pokemon Platinum Version", 18, 20, emptyList()),
        RaGameListEntry(3, "Legend of Zelda, The: Spirit Tracks", 18, 50, listOf("bbb")),
        RaGameListEntry(4, "Empty Set", 18, 0, emptyList()),
    )

    @Test fun `matches by title, skipping hacks and sets without achievements`() {
        assertEquals(1, RaTitles.match("Pokemon - Platinum Version", "Pokemon - Platinum Version (USA).nds", list)?.id)
        assertEquals(3, RaTitles.match("Zelda", "Legend of Zelda, The - Spirit Tracks (USA).nds", list)?.id)
        assertNull(RaTitles.match("Empty Set", "Empty Set.nds", list))
        assertNull(RaTitles.match("Something Else", "Something Else.nds", list))
    }

    @Test fun `fingerprint wins and is case-insensitive`() {
        assertEquals(3, RaTitles.byHash("BBB", list)?.id)
        assertNull(RaTitles.byHash("ccc", list))
    }
}

class RaParseTest {
    @Test fun `game progress with achievements as a map, numbers as strings`() {
        val p = RaParse.gameProgress(
            """{"ID":"1234","Title":"Star Courier","ConsoleName":"Nintendo DS","ImageIcon":"/Images/0001.png",
               "Achievements":{"10":{"ID":10,"Title":"First Steps","Description":"Finish chapter 1","Points":"5","BadgeName":"00010","DisplayOrder":2,"DateEarned":"2026-10-02 18:00:00"},
                               "11":{"ID":11,"Title":"Long Haul","Description":"Fly far","Points":25,"BadgeName":"00011","DisplayOrder":1},
                               "12":{"ID":12,"Title":"Night Flight","Description":"After dark","Points":10,"BadgeName":"00012","DisplayOrder":3,"DateEarnedHardcore":"2026-10-05 21:00:00"}}}""",
        )
        assertEquals(1234, p.id)
        assertEquals(3, p.achievements.size)
        assertEquals(2, p.earned)
        assertEquals(40, p.points)
        assertEquals(15, p.earnedPoints)
        assertEquals(listOf(12, 10, 11), p.ordered.map { it.id })
        assertEquals("https://media.retroachievements.org/Badge/00011_lock.png", p.achievements.first { it.id == 11 }.lockedBadgeUrl)
        assertEquals("https://media.retroachievements.org/Images/0001.png", p.iconUrl)
    }

    @Test fun `an empty achievement list may arrive as an array`() {
        val p = RaParse.gameProgress("""{"ID":5,"Title":"No Set","Achievements":[]}""")
        assertTrue(p.achievements.isEmpty())
        assertEquals(0, p.earned)
    }

    @Test fun `game list with fingerprints`() {
        val list = RaParse.gameList("""[{"ID":1,"Title":"A","ConsoleID":18,"NumAchievements":30,"Hashes":["ABC","def"]},{"Title":"broken"}]""")
        assertEquals(1, list.size)
        assertEquals(listOf("abc", "def"), list[0].hashes)
    }

    @Test fun `completion progress across games`() {
        val c = RaParse.completion("""{"Count":2,"Total":2,"Results":[
            {"GameID":1,"Title":"A","ConsoleID":18,"ConsoleName":"Nintendo DS","MaxPossible":40,"NumAwarded":12,"NumAwardedHardcore":14,"MostRecentAwardedDate":"2026-11-01T10:00:00+00:00"},
            {"GameID":2,"Title":"B","ConsoleID":5,"ConsoleName":"Game Boy Advance","MaxPossible":20,"NumAwarded":"3","NumAwardedHardcore":0}]}""")
        assertEquals(2, c.size)
        assertEquals(14, c[0].unlocked)
        assertEquals(3, c[1].unlocked)
    }

    @Test fun `api urls carry the key and parameters encoded`() {
        val url = RaRepository.url("API_GetGameInfoAndUserProgress.php", RaCredentials("me", "k&y"), "g" to 12, "u" to "me two")
        assertEquals("https://retroachievements.org/API/API_GetGameInfoAndUserProgress.php?y=k%26y&g=12&u=me+two", url)
    }
}
