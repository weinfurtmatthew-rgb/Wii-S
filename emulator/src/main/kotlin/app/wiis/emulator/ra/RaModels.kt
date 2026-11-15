package app.wiis.emulator.ra

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * RetroAchievements Web API data (https://api-docs.retroachievements.org). Parsed by hand from
 * JSON trees: the API sends numbers as numbers or strings depending on the endpoint, and empty
 * maps as `[]`, which typed decoding would reject.
 */
data class RaGameListEntry(val id: Int, val title: String, val consoleId: Int, val numAchievements: Int, val hashes: List<String>)

data class RaAchievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badgeName: String,
    val displayOrder: Int,
    val dateEarned: String?,
    val dateEarnedHardcore: String?,
    /** How many players have unlocked it (softcore and hardcore). */
    val numAwarded: Int = 0,
) {
    val earned: Boolean get() = dateEarned != null || dateEarnedHardcore != null
    /** When it was unlocked: the hardcore date if there is one, as RetroAchievements gives it. */
    val earnedAt: String? get() = dateEarnedHardcore ?: dateEarned

    /** Share of the set's players who have it, in percent; null when RetroAchievements didn't say. */
    fun rarityPercent(players: Int): Float? = if (players > 0) 100f * numAwarded / players else null
    val badgeUrl: String get() = "$MEDIA/Badge/$badgeName.png"
    val lockedBadgeUrl: String get() = "$MEDIA/Badge/${badgeName}_lock.png"
}

data class RaGameProgress(
    val id: Int,
    val title: String,
    val consoleName: String,
    val imageIcon: String?,
    val achievements: List<RaAchievement>,
    /** Players who have played the set: with [RaAchievement.numAwarded], how rare each one is. */
    val numDistinctPlayers: Int = 0,
) {
    val earned: Int get() = achievements.count { it.earned }
    val points: Int get() = achievements.sumOf { it.points }
    val earnedPoints: Int get() = achievements.filter { it.earned }.sumOf { it.points }
    val iconUrl: String? get() = imageIcon?.let { MEDIA + it }

    /** Earned first (most recent first), then the rest in the set's own order. */
    val ordered: List<RaAchievement>
        get() = achievements.sortedWith(
            compareByDescending<RaAchievement> { it.earned }
                .thenByDescending { it.dateEarnedHardcore ?: it.dateEarned ?: "" }
                .thenBy { it.displayOrder }
                .thenBy { it.id },
        )
}

data class RaCompletionEntry(
    val gameId: Int,
    val title: String,
    val imageIcon: String?,
    val consoleId: Int,
    val consoleName: String,
    val maxPossible: Int,
    val numAwarded: Int,
    val numAwardedHardcore: Int,
    val mostRecentAwardedDate: String?,
    /** "mastered", "completed", "beaten-hardcore" or "beaten-softcore"; null when none yet. */
    val highestAwardKind: String? = null,
) {
    val iconUrl: String? get() = imageIcon?.let { MEDIA + it }
    val unlocked: Int get() = maxOf(numAwarded, numAwardedHardcore)
}

data class RaProfile(val user: String, val totalPoints: Int)

const val MEDIA = "https://media.retroachievements.org"

object RaParse {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun element(text: String): JsonElement = json.parseToJsonElement(text)

    fun gameList(text: String): List<RaGameListEntry> = element(text).jsonArray.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        RaGameListEntry(
            id = o.int("ID") ?: return@mapNotNull null,
            title = o.str("Title") ?: "",
            consoleId = o.int("ConsoleID") ?: 0,
            numAchievements = o.int("NumAchievements") ?: 0,
            hashes = (o["Hashes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }.orEmpty(),
        )
    }

    fun gameProgress(text: String): RaGameProgress {
        val o = element(text).jsonObject
        val list = when (val a = o["Achievements"]) {
            is JsonObject -> a.values.toList()
            is JsonArray -> a.toList()
            else -> emptyList()
        }
        return RaGameProgress(
            id = o.int("ID") ?: 0,
            title = o.str("Title") ?: "",
            consoleName = o.str("ConsoleName") ?: "",
            imageIcon = o.str("ImageIcon"),
            achievements = list.mapNotNull { e ->
                val a = e as? JsonObject ?: return@mapNotNull null
                RaAchievement(
                    id = a.int("ID") ?: return@mapNotNull null,
                    title = a.str("Title") ?: "",
                    description = a.str("Description") ?: "",
                    points = a.int("Points") ?: 0,
                    badgeName = a.str("BadgeName") ?: "",
                    displayOrder = a.int("DisplayOrder") ?: 0,
                    dateEarned = a.str("DateEarned"),
                    dateEarnedHardcore = a.str("DateEarnedHardcore"),
                    numAwarded = a.int("NumAwarded") ?: 0,
                )
            },
            numDistinctPlayers = o.int("NumDistinctPlayers")
                ?: maxOf(o.int("NumDistinctPlayersCasual") ?: 0, o.int("NumDistinctPlayersHardcore") ?: 0),
        )
    }

    fun completion(text: String): List<RaCompletionEntry> {
        val root = element(text)
        val results = (root as? JsonObject)?.get("Results") as? JsonArray ?: (root as? JsonArray) ?: return emptyList()
        return results.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            RaCompletionEntry(
                gameId = o.int("GameID") ?: return@mapNotNull null,
                title = o.str("Title") ?: "",
                imageIcon = o.str("ImageIcon"),
                consoleId = o.int("ConsoleID") ?: 0,
                consoleName = o.str("ConsoleName") ?: "",
                maxPossible = o.int("MaxPossible") ?: 0,
                numAwarded = o.int("NumAwarded") ?: 0,
                numAwardedHardcore = o.int("NumAwardedHardcore") ?: 0,
                mostRecentAwardedDate = o.str("MostRecentAwardedDate"),
                highestAwardKind = o.str("HighestAwardKind")?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun profile(text: String): RaProfile {
        val o = element(text).jsonObject
        return RaProfile(user = o.str("User") ?: "", totalPoints = o.int("TotalPoints") ?: 0)
    }

    private fun JsonObject.str(key: String): String? = when (val v = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.contentOrNull?.takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun JsonObject.int(key: String): Int? = when (val v = this[key]) {
        is JsonPrimitive -> v.intOrNull ?: v.contentOrNull?.trim()?.toDoubleOrNull()?.toInt() ?: v.booleanOrNull?.let { if (it) 1 else 0 }
        else -> null
    }
}
