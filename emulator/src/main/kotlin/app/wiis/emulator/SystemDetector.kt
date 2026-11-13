package app.wiis.emulator

/**
 * Decides which system a ROM belongs to.
 *
 * Rule 1: the nearest ancestor folder whose name matches a system's folder alias wins
 *         (a "psp/" folder makes an .iso a PSP game).
 * Rule 2: otherwise the extension decides, but only when exactly one system claims it.
 *         Ambiguous extensions (.iso, .chd, .rvz, …) outside a recognised folder are skipped
 *         rather than guessed.
 */
class SystemDetector(registry: EmulatorRegistry) {

    private val byFolder: Map<String, SystemDef> = buildMap {
        for (system in registry.systems) for (alias in system.folders) putIfAbsent(alias.lowercase(), system)
    }

    private val byExtension: Map<String, List<SystemDef>> =
        registry.systems
            .flatMap { s -> s.extensions.map { it.lowercase() to s } }
            .groupBy({ it.first }, { it.second })

    val knownExtensions: Set<String> = byExtension.keys

    /** @param folders path segments from the scan root down to the file's parent. */
    fun detect(folders: List<String>, fileName: String): SystemDef? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val candidates = byExtension[ext] ?: return null
        for (folder in folders.asReversed()) {
            val system = byFolder[folder.lowercase()] ?: continue
            return system.takeIf { it in candidates }
        }
        return candidates.singleOrNull()
    }
}
