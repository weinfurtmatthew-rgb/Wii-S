package app.wiis.emulator.ra

import java.io.InputStream
import java.security.MessageDigest
import java.text.Normalizer

/**
 * RetroAchievements identifies a game by a fingerprint of its ROM, computed by its rcheevos
 * library (rc_hash). This reproduces it for the cartridge systems whose rules only need the file:
 * NES (skip the 16-byte iNES/FDS header), SNES (skip a 512-byte copier header), N64 (convert to
 * big-endian first), Game Boy, Game Boy Color, Game Boy Advance and Genesis (whole file), and
 * Nintendo DS (header, ARM9 and ARM7 code, icon). Disc systems are matched by title instead.
 */
object RaHash {
    val SUPPORTED = setOf("nes", "snes", "n64", "gb", "gbc", "gba", "genesis", "nds")

    /** Reads up to [length] bytes at [offset]; fewer at the end of the file. */
    fun interface RandomAccess {
        fun read(offset: Long, length: Int): ByteArray
    }

    /** Fingerprint of a whole ROM held in memory (also used for ROMs inside zip files). */
    fun ofBuffer(systemId: String, data: ByteArray): String? = when (systemId) {
        "nes" -> md5(data, if (hasNesHeader(data)) 16 else 0)
        "snes" -> md5(data, if (data.size % 0x2000 == 512) 512 else 0)
        "n64" -> n64Order(data[0])?.let { order -> md5(toBigEndian(data.copyOf(), order)) }
        "gb", "gbc", "gba", "genesis" -> md5(data)
        "nds" -> nds { offset, length -> data.copyOfRange(offset.toInt().coerceIn(0, data.size), (offset + length).toInt().coerceIn(0, data.size)) }
        else -> null
    }

    /**
     * Fingerprint of a ROM file without loading it whole: [stream] opens it from the start,
     * [random] reads at offsets (needed for the DS; null if the file can only be streamed).
     */
    fun ofFile(systemId: String, size: Long, stream: () -> InputStream, random: RandomAccess?): String? = when (systemId) {
        "nds" -> random?.let { nds(it) }
        "nes" -> stream().use { input ->
            val head = input.readUpTo(16)
            streamMd5(input, if (hasNesHeader(head)) ByteArray(0) else head)
        }
        "snes" -> stream().use { input ->
            if (size % 0x2000 == 512L) input.skipFully(512)
            streamMd5(input, ByteArray(0))
        }
        "n64" -> stream().use { input ->
            val first = input.readUpTo(4)
            val order = first.firstOrNull()?.let(::n64Order) ?: return@use null
            val md = MessageDigest.getInstance("MD5")
            val pending = java.io.ByteArrayOutputStream().apply { write(first) }
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                pending.write(buf, 0, n)
                if (pending.size() >= 1 shl 16) {
                    val all = pending.toByteArray()
                    val usable = all.size - all.size % 4
                    md.update(toBigEndian(all.copyOf(usable), order))
                    pending.reset()
                    pending.write(all, usable, all.size - usable)
                }
            }
            md.update(toBigEndian(pending.toByteArray(), order))
            hex(md.digest())
        }
        "gb", "gbc", "gba", "genesis" -> stream().use { streamMd5(it, ByteArray(0)) }
        else -> null
    }

    /** rcheevos rc_hash_nintendo_ds: 0x160-byte header, ARM9 code, ARM7 code, 0xA00 bytes of icon and titles. */
    fun nds(read: RandomAccess): String? {
        var base = 0L
        var header = read.read(0, 512)
        if (header.size < 512) return null
        // A SuperCard prefix moves the real ROM 512 bytes in.
        if (header.u8(0) == 0x2E && header.u8(1) == 0 && header.u8(2) == 0 && header.u8(3) == 0xEA &&
            header.u8(0xB0) == 0x44 && header.u8(0xB1) == 0x46 && header.u8(0xB2) == 0x96 && header.u8(0xB3) == 0
        ) {
            base = 512
            header = read.read(base, 512)
            if (header.size < 512) return null
        }
        val arm9Addr = header.le32(0x20)
        val arm9Size = header.le32(0x2C)
        val arm7Addr = header.le32(0x30)
        val arm7Size = header.le32(0x3C)
        val iconAddr = header.le32(0x68)
        if (arm9Size + arm7Size > 16L * 1024 * 1024) return null
        val md = MessageDigest.getInstance("MD5")
        md.update(header, 0, 0x160)
        md.update(read.read(base + arm9Addr, arm9Size.toInt()).padTo(arm9Size.toInt()))
        md.update(read.read(base + arm7Addr, arm7Size.toInt()).padTo(arm7Size.toInt()))
        md.update(read.read(base + iconAddr, 0xA00).padTo(0xA00))
        return hex(md.digest())
    }

    private fun hasNesHeader(b: ByteArray) = b.size >= 4 && b[3].toInt() == 0x1A &&
        ((b[0] == 'N'.code.toByte() && b[1] == 'E'.code.toByte() && b[2] == 'S'.code.toByte()) ||
            (b[0] == 'F'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == 'S'.code.toByte()))

    /** 0 = big-endian (.z64), 1 = byte-swapped (.v64), 2 = little-endian (.n64); null if not an N64 ROM. */
    private fun n64Order(first: Byte): Int? = when (first.toInt() and 0xFF) {
        0x80 -> 0
        0x37 -> 1
        0x40 -> 2
        else -> null
    }

    private fun toBigEndian(b: ByteArray, order: Int): ByteArray {
        when (order) {
            1 -> for (i in 0 until b.size - 1 step 2) { val t = b[i]; b[i] = b[i + 1]; b[i + 1] = t }
            2 -> for (i in 0 until b.size - 3 step 4) {
                val a = b[i]; val c = b[i + 1]
                b[i] = b[i + 3]; b[i + 1] = b[i + 2]; b[i + 2] = c; b[i + 3] = a
            }
        }
        return b
    }

    private fun md5(data: ByteArray, skip: Int = 0): String {
        val md = MessageDigest.getInstance("MD5")
        md.update(data, skip, (data.size - skip).coerceAtLeast(0))
        return hex(md.digest())
    }

    private fun streamMd5(input: InputStream, prefix: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        md.update(prefix)
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return hex(md.digest())
    }

    private fun InputStream.readUpTo(n: Int): ByteArray {
        val out = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = read(out, got, n - got)
            if (r < 0) break
            got += r
        }
        return out.copyOf(got)
    }

    private fun InputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val s = skip(left)
            if (s <= 0) { if (read() < 0) return else left-- } else left -= s
        }
    }

    private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
    private fun ByteArray.le32(i: Int): Long = (u8(i).toLong() or (u8(i + 1).toLong() shl 8) or (u8(i + 2).toLong() shl 16) or (u8(i + 3).toLong() shl 24))
    private fun ByteArray.padTo(n: Int): ByteArray = if (size >= n) this else copyOf(n)

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

/** Title matching for systems without a fingerprint, and as a fallback when a fingerprint is unknown. */
object RaTitles {
    private val articles = Regex("^(.*?), (the|a|an)(\\b.*)$")

    /** "Legend of Zelda, The - Spirit Tracks (USA) (En,Fr)" and "Legend of Zelda, The: Spirit Tracks" both become "the legend of zelda spirit tracks". */
    fun normalize(raw: String): String {
        var t = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        t = t.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace("&", " and ")
        t = t.replace(Regex("\\s+"), " ").trim()
        articles.matchEntire(t)?.let { m -> t = "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}" }
        return t.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    /**
     * The game whose title matches [title] or [fileName], ignoring region tags, punctuation and
     * a trailing ", The". RetroAchievements' hacks and homebrew (titles starting with "~") match
     * only an identical name.
     */
    fun match(title: String, fileName: String, entries: List<RaGameListEntry>): RaGameListEntry? {
        val wanted = setOf(normalize(title), normalize(fileName.substringBeforeLast('.'))).filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return null
        val candidates = entries.filter { it.numAchievements > 0 }
        candidates.firstOrNull { !it.title.startsWith("~") && normalize(it.title) in wanted }?.let { return it }
        return candidates.firstOrNull { normalize(it.title.removePrefix("~").substringAfter("~").trim()) in wanted && wanted.any { w -> w.contains("hack") || w.contains("homebrew") } }
    }

    /** The entry whose fingerprints include [hash]. */
    fun byHash(hash: String, entries: List<RaGameListEntry>): RaGameListEntry? = entries.firstOrNull { hash.lowercase() in it.hashes }
}
