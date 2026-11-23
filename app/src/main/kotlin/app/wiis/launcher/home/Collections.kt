package app.wiis.launcher.home

import app.wiis.emulator.Game
import kotlinx.serialization.Serializable
import java.text.Normalizer

/**
 * A group of games across consoles: a series Wii-S found in your titles ("Sonic", "Pokémon",
 * "Grand Theft Auto") or one you made. [id] is "s:<series key>" or "c:<number>". [suggested]: a
 * series Wii-S offers that you haven't kept yet (Select → Keep, Rename or Hide).
 */
data class CollectionEntry(val id: String, val name: String, val games: List<Game>, val suggested: Boolean)

/** What you did with collections, saved as collections.json. */
@Serializable
data class CollectionsFile(
    /** Series you kept, in the order you kept them, with your changes. */
    val kept: List<KeptSeries> = emptyList(),
    /** Series you hid: never offered again. */
    val hidden: List<String> = emptyList(),
    /** Collections you made from a game's menu. */
    val own: List<OwnCollection> = emptyList(),
    val nextId: Int = 1,
)

@Serializable
data class KeptSeries(val key: String, val name: String? = null, val added: List<String> = emptyList(), val removed: List<String> = emptyList())

@Serializable
data class OwnCollection(val id: String, val name: String, val games: List<String>)

/** Finds series in game titles: well-known ones by name, others by titles that start the same. */
object SeriesFinder {
    data class Series(val key: String, val name: String, val games: List<Game>)

    /** Well-known series: the name shown, then the words that mark a title as part of it. */
    private val KNOWN: List<Pair<String, List<String>>> = listOf(
        "Pokémon" to listOf("pokemon"),
        "Mario" to listOf("mario"),
        "Zelda" to listOf("zelda"),
        "Sonic" to listOf("sonic"),
        "Grand Theft Auto" to listOf("grand theft auto", "gta"),
        "Final Fantasy" to listOf("final fantasy"),
        "Kirby" to listOf("kirby"),
        "Metroid" to listOf("metroid"),
        "Mega Man" to listOf("mega man", "megaman", "rockman"),
        "Castlevania" to listOf("castlevania"),
        "Resident Evil" to listOf("resident evil", "biohazard"),
        "Street Fighter" to listOf("street fighter"),
        "Tekken" to listOf("tekken"),
        "Mortal Kombat" to listOf("mortal kombat"),
        "Crash Bandicoot" to listOf("crash bandicoot", "crash team racing"),
        "Spyro" to listOf("spyro"),
        "Donkey Kong" to listOf("donkey kong"),
        "Fire Emblem" to listOf("fire emblem"),
        "Kingdom Hearts" to listOf("kingdom hearts"),
        "Dragon Quest" to listOf("dragon quest", "dragon warrior"),
        "Metal Gear" to listOf("metal gear"),
        "Persona" to listOf("persona"),
        "Tony Hawk" to listOf("tony hawk"),
        "Need for Speed" to listOf("need for speed"),
        "Call of Duty" to listOf("call of duty"),
        "Ratchet & Clank" to listOf("ratchet"),
        "Jak and Daxter" to listOf("jak and daxter", "jak ii", "jak 3", "jak x"),
        "Sly Cooper" to listOf("sly cooper", "sly 2", "sly 3"),
        "God of War" to listOf("god of war"),
        "Silent Hill" to listOf("silent hill"),
        "Pikmin" to listOf("pikmin"),
        "Animal Crossing" to listOf("animal crossing"),
        "Star Fox" to listOf("star fox", "starfox"),
        "F-Zero" to listOf("f zero"),
        "Yoshi" to listOf("yoshi"),
        "Wario" to listOf("wario"),
        "Advance Wars" to listOf("advance wars"),
        "Golden Sun" to listOf("golden sun"),
        "Harvest Moon" to listOf("harvest moon", "story of seasons"),
        "Monster Hunter" to listOf("monster hunter"),
        "Tales of" to listOf("tales of"),
        "Dragon Ball" to listOf("dragon ball", "dragonball"),
        "Bomberman" to listOf("bomberman"),
        "Contra" to listOf("contra"),
        "Ninja Gaiden" to listOf("ninja gaiden"),
        "Gran Turismo" to listOf("gran turismo"),
        "Smash Bros." to listOf("smash bros"),
        "Professor Layton" to listOf("professor layton", "layton"),
        "Ace Attorney" to listOf("ace attorney", "phoenix wright"),
        "Xenoblade" to listOf("xenoblade"),
        "Splatoon" to listOf("splatoon"),
        "Metal Slug" to listOf("metal slug"),
        "Halo" to listOf("halo"),
        "Gears of War" to listOf("gears of war"),
        "Forza" to listOf("forza"),
        "Guitar Hero" to listOf("guitar hero"),
        "Burnout" to listOf("burnout"),
        "Prince of Persia" to listOf("prince of persia"),
        "Kingdom Rush" to listOf("kingdom rush"),
    )

    /** Words too common to name a series on their own. */
    private val GENERIC = setOf(
        "the", "a", "an", "super", "new", "my", "dr", "mr", "pro", "world", "star", "dragon", "final", "metal", "street",
        "grand", "legend", "legends", "tales", "little", "big", "dark", "power", "puzzle", "pocket", "mega", "sports",
        "ultimate", "classic", "adventure", "adventures", "battle", "war", "wars", "space", "crazy", "extreme", "real",
        "total", "true", "king", "kings", "prince", "princess", "magic", "monster", "monsters", "ninja", "robot",
        "spider", "black", "red", "blue", "green", "golden", "silver", "crystal", "great", "hot", "cool", "lost", "last",
        "first", "fantasy", "tiny", "paper", "sky", "night", "moon", "sun", "kid", "kids", "game", "games",
    )

    fun find(games: List<Game>): List<Series> {
        if (games.isEmpty()) return emptyList()
        val words = games.associateWith { words(it.title) }
        val found = ArrayList<Series>()

        // Known series: their words anywhere in the title, two games or more.
        for ((name, marks) in KNOWN) {
            val markWords = marks.map { it.split(" ") }
            val mine = games.filter { g -> markWords.any { m -> contains(words.getValue(g), m) } }
            if (mine.size >= 2) found += Series(key(name), name, mine)
        }

        // Titles that start the same: two words or more shared by two games, or one distinctive
        // word shared by three. The shortest such start names the series.
        val starts = HashMap<List<String>, MutableList<Game>>()
        for (g in games) {
            val w = words.getValue(g).let { if (it.firstOrNull() == "the") it.drop(1) else it }
            for (n in 1..minOf(3, w.size)) starts.getOrPut(w.take(n)) { ArrayList() } += g
        }
        // Counted by title, so one game on two consoles isn't a series.
        val qualifying = starts.filter { (start, list) ->
            val titles = list.map { words.getValue(it) }.distinct().size
            if (start.size >= 2) titles >= 2 else titles >= 3 && start[0] !in GENERIC && start[0].length >= 3 && !start[0].all(Char::isDigit)
        }
        val covered = found.flatMap { it.games }.toSet()
        for ((start, list) in qualifying.entries.sortedBy { it.key.size }) {
            val mine = list.distinct()
            // Already a series with these games or more (a known one, or a shorter start).
            if (covered.containsAll(mine) || found.any { s -> s.key.startsWith("t:") && s.games.containsAll(mine) }) continue
            // Named by the longest start the same games share: "Coral Drift", not "Coral".
            var longest = start
            for (n in start.size + 1..3) {
                longest = starts.entries.firstOrNull { (k, v) -> k.size == n && k.take(longest.size) == longest && v.distinct().toSet() == mine.toSet() }?.key ?: break
            }
            found += Series("t:" + longest.joinToString(" "), displayName(mine.first().title, longest.size), mine)
        }
        return found.sortedWith(compareByDescending<Series> { it.games.size }.thenBy { it.name.lowercase() })
    }

    /** Lowercase words without accents, brackets or punctuation: "Pokémon: Emerald (USA)" → [pokemon, emerald]. */
    fun words(title: String): List<String> {
        val noTags = title.replace(TAGS, " ")
        val plain = Normalizer.normalize(noTags, Normalizer.Form.NFD).replace(MARKS, "")
        return plain.lowercase().split(NOT_WORD).filter { it.isNotEmpty() }
    }

    // Written for Android's ICU as well as the JVM (RegexPortabilityTest): brackets escaped.
    private val TAGS = Regex("\\([^)]*\\)|\\[[^\\]]*\\]")
    private val MARKS = Regex("\\p{Mn}+")
    private val NOT_WORD = Regex("[^a-z0-9]+")
    private val SPACES = Regex("\\s+")

    fun key(name: String): String = "k:" + words(name).joinToString(" ")

    private fun contains(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        for (i in 0..haystack.size - needle.size) if (haystack.subList(i, i + needle.size) == needle) return true
        return false
    }

    /** The first [count] words of [title] as written there (after a leading "The"). */
    private fun displayName(title: String, count: Int): String {
        val clean = title.replace(TAGS, " ").trim()
        val parts = clean.split(SPACES).map { it.trim(':', '-', ',', '.', '!', '?') }.filter { it.isNotEmpty() }
        val start = if (parts.firstOrNull()?.equals("the", ignoreCase = true) == true) parts.drop(1) else parts
        return start.take(count).joinToString(" ").ifEmpty { clean }
    }
}

/** Your collections, then the series Wii-S offers (the largest first). */
object CollectionsModel {
    fun resolve(file: CollectionsFile, series: List<SeriesFinder.Series>, games: List<Game>): List<CollectionEntry> {
        val byUri = games.associateBy { it.uri }
        val byKey = series.associateBy { it.key }
        val keptKeys = file.kept.map { it.key }.toSet()
        val mine = file.kept.mapNotNull { k ->
            val found = byKey[k.key]
            val base = found?.games.orEmpty().map { it.uri }
            val uris = (base + k.added).distinct().filter { it !in k.removed }
            val list = uris.mapNotNull(byUri::get)
            if (list.isEmpty() && found == null) null else CollectionEntry("s:${k.key}", k.name ?: found?.name ?: k.key.substringAfter(':'), sortGames(list), suggested = false)
        } + file.own.map { o -> CollectionEntry(o.id, o.name, sortGames(o.games.mapNotNull(byUri::get)), suggested = false) }
        val offered = series.filter { it.key !in keptKeys && it.key !in file.hidden }
            .map { CollectionEntry("s:${it.key}", it.name, sortGames(it.games), suggested = true) }
        return mine.sortedBy { it.name.lowercase() } + offered
    }

    fun keep(file: CollectionsFile, key: String): CollectionsFile =
        if (file.kept.any { it.key == key }) file else file.copy(kept = file.kept + KeptSeries(key), hidden = file.hidden - key)

    fun hide(file: CollectionsFile, id: String): CollectionsFile = when {
        id.startsWith("s:") -> { val key = id.removePrefix("s:"); file.copy(kept = file.kept.filter { it.key != key }, hidden = (file.hidden + key).distinct()) }
        else -> file.copy(own = file.own.filter { it.id != id })
    }

    fun rename(file: CollectionsFile, id: String, name: String): CollectionsFile = when {
        id.startsWith("s:") -> editSeries(keep(file, id.removePrefix("s:")), id.removePrefix("s:")) { it.copy(name = name) }
        else -> file.copy(own = file.own.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun add(file: CollectionsFile, id: String, uri: String): CollectionsFile = when {
        id.startsWith("s:") -> editSeries(keep(file, id.removePrefix("s:")), id.removePrefix("s:")) { it.copy(added = (it.added + uri).distinct(), removed = it.removed - uri) }
        else -> file.copy(own = file.own.map { if (it.id == id) it.copy(games = (it.games + uri).distinct()) else it })
    }

    fun remove(file: CollectionsFile, id: String, uri: String): CollectionsFile = when {
        id.startsWith("s:") -> editSeries(keep(file, id.removePrefix("s:")), id.removePrefix("s:")) { it.copy(added = it.added - uri, removed = (it.removed + uri).distinct()) }
        else -> file.copy(own = file.own.map { if (it.id == id) it.copy(games = it.games - uri) else it })
    }

    /** A new collection of yours, holding [uri]; returns the file and the new id. */
    fun create(file: CollectionsFile, name: String, uri: String): Pair<CollectionsFile, String> {
        val id = "c:${file.nextId}"
        return file.copy(own = file.own + OwnCollection(id, name, listOf(uri)), nextId = file.nextId + 1) to id
    }

    private fun editSeries(file: CollectionsFile, key: String, change: (KeptSeries) -> KeptSeries) =
        file.copy(kept = file.kept.map { if (it.key == key) change(it) else it })

    private fun sortGames(list: List<Game>) = list.sortedWith(compareBy({ it.title.lowercase() }, { it.uri }))
}
