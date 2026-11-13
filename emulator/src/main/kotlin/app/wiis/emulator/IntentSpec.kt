package app.wiis.emulator

/**
 * Platform-free description of a launch Intent. Built by [IntentSpecBuilder] (pure, unit-tested)
 * and turned into a real Intent by [EmulatorLauncher].
 */
data class IntentSpec(
    val packageName: String,
    val activity: String,
    val action: String?,
    val category: String?,
    val data: String?,
    val extras: Map<String, String>,
    val boolExtras: Map<String, Boolean>,
    val intExtras: Map<String, Int>,
    val clearTask: Boolean,
    val clearTop: Boolean,
    /** content:// URIs the emulator must be granted read/write access to. */
    val grantUris: List<String>,
)

/** Values a ROM can contribute to a template. Null means "not available for this ROM". */
data class RomVars(
    val uri: String,
    val path: String?,
    val name: String,
    /** First line of the file; only read for templates that need it (GameNative app ids). */
    val contents: () -> String? = { null },
)

sealed interface BuildResult {
    data class Ok(val spec: IntentSpec) : BuildResult
    data class MissingValue(val variable: String) : BuildResult
    data class BadTemplate(val message: String) : BuildResult
}

object IntentSpecBuilder {

    const val EXTERNAL_DATA = "/storage/emulated/0"
    const val INTERNAL_DATA = "/data/data"

    private val placeholder = Regex("""\{([A-Za-z0-9_.]+)\}""")

    fun build(def: EmulatorDef, target: Target, choice: EmulatorChoice, rom: RomVars): BuildResult {
        val launch = def.launch
        val usedUris = mutableSetOf<String>()

        fun resolve(name: String): String? = when (name) {
            "rom.uri" -> rom.uri.also { usedUris += it }
            "rom.path" -> rom.path
            "rom.name" -> rom.name
            "rom.contents" -> rom.contents()?.trim()
            "pkg" -> target.`package`
            "externalData" -> EXTERNAL_DATA
            "internalData" -> INTERNAL_DATA
            else -> choice.vars[name]
        }

        var missing: String? = null
        fun render(template: String): String = placeholder.replace(template) { m ->
            val key = m.groupValues[1]
            resolve(key) ?: run { if (missing == null) missing = key; "" }
        }

        val data = launch.data?.let(::render)
        val extras = launch.extras.mapValues { render(it.value) }
        val intRendered = launch.intExtras.mapValues { render(it.value) }
        missing?.let { return BuildResult.MissingValue(it) }

        val intExtras = intRendered.mapValues { (key, value) ->
            value.toIntOrNull() ?: return BuildResult.BadTemplate("'$key' must be an integer, got '$value'")
        }

        val unknownFlags = launch.flags - setOf("CLEAR_TASK", "CLEAR_TOP")
        if (unknownFlags.isNotEmpty()) return BuildResult.BadTemplate("Unknown flags: $unknownFlags")

        return BuildResult.Ok(
            IntentSpec(
                packageName = target.`package`,
                activity = target.activity,
                action = launch.action,
                category = launch.category,
                data = data,
                extras = extras,
                boolExtras = launch.boolExtras,
                intExtras = intExtras,
                clearTask = "CLEAR_TASK" in launch.flags,
                clearTop = "CLEAR_TOP" in launch.flags,
                grantUris = usedUris.toList(),
            ),
        )
    }
}

/**
 * Converts a Storage Access Framework document ID from the system's external-storage provider
 * into a real file path, for emulators (RetroArch) that only accept paths. The launcher itself
 * never opens the path, so it needs no All Files Access; the emulator uses its own storage access.
 */
object SafPaths {
    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    fun documentIdToPath(authority: String, documentId: String): String? {
        if (authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val volume = documentId.substringBefore(':', "")
        if (volume.isEmpty()) return null
        val relative = documentId.substringAfter(':')
        val root = when {
            volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0"
            volume.equals("home", ignoreCase = true) -> "/storage/emulated/0/Documents"
            else -> "/storage/$volume"
        }
        return if (relative.isEmpty()) root else "$root/$relative"
    }
}
