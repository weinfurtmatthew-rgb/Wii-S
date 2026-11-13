package app.wiis.emulator

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

sealed interface LaunchResult {
    data class Launched(val emulator: EmulatorDef, val packageName: String) : LaunchResult
    data class NotInstalled(val emulator: EmulatorDef) : LaunchResult
    data class NoEmulatorForSystem(val systemId: String) : LaunchResult
    data class NeedsFilePath(val emulator: EmulatorDef) : LaunchResult
    data class Failed(val emulator: EmulatorDef?, val reason: String) : LaunchResult
}

class EmulatorLauncher(private val context: Context, private val registry: EmulatorRegistry) {

    private val pm: PackageManager get() = context.packageManager

    /** First target whose package is installed and whose activity resolves. */
    fun installedTarget(def: EmulatorDef): Target? = def.targets.firstOrNull { t ->
        runCatching { pm.getActivityInfo(ComponentName(t.`package`, t.activity), 0) }.isSuccess
    }

    fun choicesFor(systemId: String): List<EmulatorChoice> = registry.system(systemId)?.emulators.orEmpty()

    fun launch(game: Game, choice: EmulatorChoice? = null, displayId: Int = 0): LaunchResult {
        val pick = choice
            ?: choicesFor(game.systemId).firstOrNull { c -> registry.emulator(c.emulator)?.let(::installedTarget) != null }
            ?: choicesFor(game.systemId).firstOrNull()
            ?: return LaunchResult.NoEmulatorForSystem(game.systemId)
        val def = registry.emulator(pick.emulator) ?: return LaunchResult.Failed(null, "Unknown emulator '${pick.emulator}'")
        val target = installedTarget(def) ?: return LaunchResult.NotInstalled(def)

        val rom = RomVars(
            uri = game.uri,
            path = SafPaths.documentIdToPath(game.authority, game.documentId),
            name = game.title,
            contents = { readFirstLine(Uri.parse(game.uri)) },
        )
        val spec = when (val r = IntentSpecBuilder.build(def, target, pick, rom)) {
            is BuildResult.Ok -> r.spec
            is BuildResult.MissingValue ->
                return if (r.variable == "rom.path") LaunchResult.NeedsFilePath(def)
                else LaunchResult.Failed(def, "Missing value for {${r.variable}}")
            is BuildResult.BadTemplate -> return LaunchResult.Failed(def, r.message)
        }

        val intent = spec.toIntent()
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        return try {
            context.startActivity(intent, options.toBundle())
            LaunchResult.Launched(def, target.`package`)
        } catch (e: ActivityNotFoundException) {
            LaunchResult.Failed(def, "Activity not found: ${spec.packageName}/${spec.activity}")
        } catch (e: SecurityException) {
            LaunchResult.Failed(def, "Emulator refused the launch: ${e.message}")
        }
    }

    private fun readFirstLine(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLine() }
    }.getOrNull()
}

fun IntentSpec.toIntent(): Intent = Intent().apply {
    component = ComponentName(packageName, activity)
    this@toIntent.action?.let { action = it }
    this@toIntent.category?.let { addCategory(it) }
    this@toIntent.data?.let { data = Uri.parse(it) }
    this@toIntent.extras.forEach { (k, v) -> putExtra(k, v) }
    this@toIntent.boolExtras.forEach { (k, v) -> putExtra(k, v) }
    this@toIntent.intExtras.forEach { (k, v) -> putExtra(k, v) }
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (clearTask) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (clearTop) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (grantUris.isNotEmpty()) {
        val clip = ClipData.newRawUri("rom", Uri.parse(grantUris.first()))
        grantUris.drop(1).forEach { clip.addItem(ClipData.Item(Uri.parse(it))) }
        clipData = clip
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
}
