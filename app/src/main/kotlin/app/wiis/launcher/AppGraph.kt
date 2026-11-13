package app.wiis.launcher

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import app.wiis.emulator.EmulatorDiagnostics
import app.wiis.emulator.EmulatorLauncher
import app.wiis.emulator.EmulatorRegistry
import app.wiis.emulator.Game
import app.wiis.emulator.LibraryStore
import app.wiis.emulator.SystemDetector
import app.wiis.system.AppEntry
import app.wiis.system.AppRepository
import app.wiis.system.DisplayProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class WiisApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.apps.start()
        graph.companionPlacer.start()
    }
}

val Context.graph: AppGraph get() = (applicationContext as WiisApplication).graph

/**
 * Manual dependency graph. Both display activities run in this one process and read the same
 * instances, which is what keeps the two screens in sync without IPC. A DI framework would add
 * build time and APK size for a dozen singletons; revisit if the graph grows past that.
 */
class AppGraph(val context: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val registry: EmulatorRegistry by lazy {
        EmulatorRegistry.parse(context.assets.open("emulators.json").bufferedReader().use { it.readText() })
    }
    val detector by lazy { SystemDetector(registry) }
    val launcher by lazy { EmulatorLauncher(context, registry) }
    val diagnostics by lazy { EmulatorDiagnostics(context, registry) }
    val libraryStore by lazy { LibraryStore(File(context.filesDir, "library.json")) }
    val apps = AppRepository(context, scope)
    val displayProbe by lazy { DisplayProbe(context) }
    val prefs by lazy { context.getSharedPreferences("wiis", Context.MODE_PRIVATE) }

    val shared = SharedState()
    val playtime by lazy { PlaytimeStore(prefs) }
    val perf = PerfMonitor()
    val companionPlacer by lazy { app.wiis.launcher.companion.CompanionPlacer(context, shared) }
}

/** What the top screen is pointing at, as seen by the bottom screen. */
sealed interface Focus {
    data object None : Focus
    data class OnGame(val game: Game) : Focus
    data class OnApp(val app: AppEntry) : Focus
}

data class Session(val game: Game, val emulatorName: String, val usesBothScreens: Boolean, val startedAtMillis: Long)

data class CompanionStatus(val displayId: Int, val mode: String, val sinceMillis: Long)

class SharedState {
    private val _focus = MutableStateFlow<Focus>(Focus.None)
    val focus: StateFlow<Focus> = _focus.asStateFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _companion = MutableStateFlow<CompanionStatus?>(null)
    val companion: StateFlow<CompanionStatus?> = _companion.asStateFlow()

    fun setFocus(f: Focus) { _focus.value = f }
    fun startSession(s: Session) { _session.value = s }
    fun endSession(): Session? = _session.value.also { _session.value = null }
    fun companionAttached(s: CompanionStatus?) { _companion.value = s }
}

class PlaytimeStore(private val prefs: android.content.SharedPreferences) {
    fun totalMillis(gameUri: String): Long = prefs.getLong("pt:$gameUri", 0L)
    fun lastPlayed(gameUri: String): Long = prefs.getLong("lp:$gameUri", 0L)

    fun record(gameUri: String, startedAt: Long, endedAt: Long) {
        val duration = (endedAt - startedAt).coerceAtLeast(0)
        prefs.edit {
            putLong("pt:$gameUri", totalMillis(gameUri) + duration)
            putLong("lp:$gameUri", endedAt)
        }
    }
}

fun formatPlaytime(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "under a minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

object BuildConfigInfo {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
}
