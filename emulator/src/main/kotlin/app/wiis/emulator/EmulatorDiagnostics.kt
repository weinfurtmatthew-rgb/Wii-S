package app.wiis.emulator

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Checks every registry entry against what is actually installed. When a known package is
 * installed but its activity does not resolve, it lists the package's exported activities so the
 * registry can be corrected from a single report.
 */
class EmulatorDiagnostics(private val context: Context, private val registry: EmulatorRegistry) {

    data class Row(
        val emulator: EmulatorDef,
        val installedPackage: String?,
        val versionName: String?,
        val activityResolves: Boolean,
        val exportedActivities: List<String>,
    )

    fun run(): List<Row> {
        val pm = context.packageManager
        return registry.emulators.map { def ->
            val installed = def.targets.firstOrNull { isInstalled(pm, it.`package`) }
            if (installed == null) {
                Row(def, null, null, false, emptyList())
            } else {
                val info = pm.getPackageInfo(installed.`package`, PackageManager.GET_ACTIVITIES)
                val resolves = runCatching {
                    pm.getActivityInfo(ComponentName(installed.`package`, installed.activity), 0)
                }.isSuccess
                Row(
                    emulator = def,
                    installedPackage = installed.`package`,
                    versionName = info.versionName,
                    activityResolves = resolves,
                    exportedActivities = if (resolves) emptyList()
                    else info.activities.orEmpty().filter { it.exported }.map { it.name },
                )
            }
        }
    }

    private fun isInstalled(pm: PackageManager, pkg: String) =
        runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess

    fun report(rows: List<Row>): String = buildString {
        appendLine("== Emulators ==")
        for (r in rows) {
            val status = when {
                r.installedPackage == null -> "not installed"
                r.activityResolves -> "OK  ${r.installedPackage} v${r.versionName}"
                else -> "ACTIVITY MISSING  ${r.installedPackage} v${r.versionName}"
            }
            appendLine("${r.emulator.name.padEnd(12)} $status")
            r.exportedActivities.forEach { appendLine("    exported: $it") }
        }
    }
}
