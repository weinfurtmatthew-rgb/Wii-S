package app.wiis.emulator

/**
 * Decides which system a ROM belongs to.
 *
 * Rule 1: the nearest ancestor folder whose name matches a system's folder alias wins
 *         ("psp/" makes an .iso a PSP game). Folder names are normalised, so "Nintendo DS",
 *         "nintendo_ds" and "NintendoDS" all match the alias "nintendods".
 * Rule 2: otherwise the extension decides, but only when exactly one system claims it.
 *         Ambiguous extensions (.iso, .chd, .zip, .7z, …) outside a recognised folder are
 *         reported as unrecognised rather than guessed.
 */
class SystemDetector(registry: EmulatorRegistry) {

    private val byFolder: Map<String, SystemDef> = buildMap {
        for (system in registry.systems) {
            putIfAbsent(normalize(system.id), system)
            for (alias in system.folders) putIfAbsent(normalize(alias), system)
        }
    }

    private val byExtension: Map<String, List<SystemDef>> =
        registry.systems
            .flatMap { s -> s.extensions.map { it.lowercase() to s } }
            .groupBy({ it.first }, { it.second })

    val knownExtensions: Set<String> = byExtension.keys

    /** @param folders path segments from the scan root down to the file's parent. */
    fun detect(folders: List<String>, fileName: String): SystemDef? {
        val ext = extensionOf(fileName)
        val candidates = byExtension[ext] ?: return null
        for (folder in folders.asReversed()) {
            val system = byFolder[normalize(folder)] ?: continue
            return system.takeIf { it in candidates }
        }
        return candidates.singleOrNull()
    }

    companion object {
        fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

        /** Lower-case, letters and digits only: "Nintendo - DS (EU)" -> "nintendodseu". */
        fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
    }
}
