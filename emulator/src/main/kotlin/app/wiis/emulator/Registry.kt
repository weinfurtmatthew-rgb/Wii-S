package app.wiis.emulator

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Root of `assets/emulators.json`. Data, not code, so emulator breakage can be fixed without touching Kotlin. */
@Serializable
data class EmulatorRegistry(
    val version: Int,
    val emulators: List<EmulatorDef>,
    val systems: List<SystemDef>,
) {
    private val emulatorsById by lazy { emulators.associateBy { it.id } }
    private val systemsById by lazy { systems.associateBy { it.id } }

    fun emulator(id: String): EmulatorDef? = emulatorsById[id]
    fun system(id: String): SystemDef? = systemsById[id]

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): EmulatorRegistry = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class EmulatorDef(
    val id: String,
    val name: String,
    /** Package/activity pairs in preference order; the first installed one wins. */
    val targets: List<Target>,
    val launch: LaunchSpec,
    val control: ControlSpec? = null,
    /** Emulator renders to both displays itself (DS/3DS); the companion should step aside. */
    val usesBothScreens: Boolean = false,
    val verified: Boolean = false,
)

@Serializable
data class Target(val `package`: String, val activity: String)

@Serializable
data class LaunchSpec(
    val action: String? = null,
    val category: String? = null,
    /** Template for Intent data, e.g. "{rom.uri}". */
    val data: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val boolExtras: Map<String, Boolean> = emptyMap(),
    /** Templates that must render to an integer. */
    val intExtras: Map<String, String> = emptyMap(),
    /** Symbolic flags: CLEAR_TASK, CLEAR_TOP. NEW_TASK is always added. */
    val flags: List<String> = emptyList(),
)

@Serializable
data class ControlSpec(val type: String, val port: Int)

@Serializable
data class SystemDef(
    val id: String,
    val name: String,
    val extensions: List<String>,
    /** Folder names (case-insensitive) that identify this system anywhere in a ROM's path. */
    val folders: List<String>,
    /** Emulator choices in preference order; the first is the default. */
    val emulators: List<EmulatorChoice>,
)

@Serializable
data class EmulatorChoice(val emulator: String, val vars: Map<String, String> = emptyMap())
