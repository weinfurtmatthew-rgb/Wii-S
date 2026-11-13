package app.wiis.launcher.home

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wiis.emulator.EmulatorChoice
import app.wiis.emulator.Game
import app.wiis.emulator.LaunchResult
import app.wiis.emulator.LibrarySnapshot
import app.wiis.emulator.RomScanner
import app.wiis.launcher.AppGraph
import app.wiis.launcher.Focus
import app.wiis.launcher.Session
import app.wiis.system.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Section(val label: String) { Games("Games"), Apps("Apps"), Spike("System") }

sealed interface LibraryStatus {
    data object NoFolder : LibraryStatus
    data class Scanning(val found: Int) : LibraryStatus
    data object Ready : LibraryStatus
}

/** One horizontal row on the Games screen. */
data class Shelf(val id: String, val title: String, val games: List<Game>)

/**
 * Which tile should take focus. [token] changes every time focus must be (re)applied, so the UI
 * can react even when the target is the same game as last time.
 */
data class FocusTarget(val shelfId: String, val gameUri: String, val token: Long)

data class EmulatorOption(val choice: EmulatorChoice, val name: String, val installed: Boolean)
data class EmulatorPicker(val game: Game, val options: List<EmulatorOption>)

data class HomeUiState(
    val section: Section = Section.Games,
    val apps: List<AppEntry> = emptyList(),
    val library: LibraryStatus = LibraryStatus.NoFolder,
    val shelves: List<Shelf> = emptyList(),
    val focusTarget: FocusTarget? = null,
    val picker: EmulatorPicker? = null,
    val message: String? = null,
)

/** Unidirectional: the screen calls intents (functions); state flows back as one immutable value. */
class HomeViewModel(private val graph: AppGraph) : ViewModel() {

    private val systemNames = graph.registry.systems.associate { it.id to it.name }
    private val systemOrder = graph.registry.systems.mapIndexed { i, s -> s.id to i }.toMap()

    private val local = MutableStateFlow(HomeUiState())
    private var games: List<Game> = emptyList()
    private var scanning = false
    private var lastScanAt = 0L

    val state: StateFlow<HomeUiState> =
        combine(local, graph.apps.apps) { s, apps -> s.copy(apps = apps) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { graph.libraryStore.load() }
            if (cached != null) {
                setGames(cached.games, focusFirst = true)
                local.update { it.copy(library = LibraryStatus.Ready) }
            }
            // Always refresh in the background: picks up games copied since the last scan.
            if (rootUri() != null) rescan(silent = cached != null)
        }
    }

    // ---- Navigation -------------------------------------------------------------------------

    fun selectSection(section: Section) = local.update { it.copy(section = section) }

    fun cycleSection(delta: Int) = local.update {
        val all = Section.entries
        it.copy(section = all[(all.indexOf(it.section) + delta).mod(all.size)])
    }

    /** Start: system screen. Select: flip between Games and Apps. */
    fun onStart() = selectSection(Section.Spike)
    fun onSelect() = local.update {
        it.copy(section = if (it.section == Section.Apps) Section.Games else Section.Apps)
    }

    fun focusGame(game: Game) = graph.shared.setFocus(Focus.OnGame(game))
    fun focusApp(app: AppEntry) = graph.shared.setFocus(Focus.OnApp(app))
    fun focusConsumed() = local.update { it.copy(focusTarget = null) }

    fun launchApp(app: AppEntry) = graph.apps.launch(app)

    // ---- Library ----------------------------------------------------------------------------

    fun onFolderPicked(uri: Uri?) {
        uri ?: return
        graph.context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        graph.prefs.edit { putString(KEY_ROOT, uri.toString()) }
        rescan(silent = false)
    }

    fun rescan() = rescan(silent = false)

    private fun rescan(silent: Boolean) {
        val root = rootUri() ?: return
        if (scanning) return
        scanning = true
        if (!silent) local.update { it.copy(library = LibraryStatus.Scanning(0)) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    RomScanner(graph.context.contentResolver, graph.detector).scan(root) { found ->
                        if (!silent) local.update { it.copy(library = LibraryStatus.Scanning(found)) }
                    }
                }
            }
            scanning = false
            lastScanAt = SystemClock.elapsedRealtime()
            result.onSuccess { r ->
                withContext(Dispatchers.IO) {
                    graph.libraryStore.save(
                        LibrarySnapshot(root.toString(), System.currentTimeMillis(), r.games, r.unrecognized, r.unrecognizedExamples),
                    )
                }
                val added = r.games.size - games.size
                setGames(r.games, focusFirst = !silent)
                local.update { it.copy(library = LibraryStatus.Ready) }
                if (silent && added > 0) showMessage("Found $added new game${if (added == 1) "" else "s"}")
            }.onFailure { e ->
                local.update { it.copy(library = if (games.isEmpty()) LibraryStatus.NoFolder else LibraryStatus.Ready) }
                showMessage("Scan failed: ${e.message}. Re-pick the ROM folder from the System tab.")
            }
        }
    }

    private fun setGames(list: List<Game>, focusFirst: Boolean) {
        games = list
        val shelves = buildShelves()
        local.update { s ->
            val first = shelves.firstOrNull()?.let { sh -> sh.games.firstOrNull()?.let { FocusTarget(sh.id, it.uri, SystemClock.uptimeMillis()) } }
            s.copy(shelves = shelves, focusTarget = if (focusFirst) first else s.focusTarget)
        }
    }

    private fun buildShelves(): List<Shelf> {
        val recent = games
            .map { it to graph.playtime.lastPlayed(it.uri) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(RECENT_COUNT)
            .map { it.first }
        val bySystem = games.groupBy { it.systemId }
            .toSortedMap(compareBy { systemOrder[it] ?: Int.MAX_VALUE })
            .map { (id, list) -> Shelf(id, systemNames[id] ?: id, list) }
        return listOfNotNull(recent.takeIf { it.isNotEmpty() }?.let { Shelf(RECENT_ID, "Recently played", it) }) + bySystem
    }

    // ---- Launching --------------------------------------------------------------------------

    fun launchGame(game: Game, choice: EmulatorChoice? = null) {
        local.update { it.copy(picker = null) }
        when (val r = graph.launcher.launch(game, choice)) {
            is LaunchResult.Launched -> graph.shared.startSession(
                Session(game, r.emulator.name, r.emulator.usesBothScreens, System.currentTimeMillis()),
            )
            is LaunchResult.NotInstalled -> showMessage("${r.emulator.name} isn't installed. Press Y to pick another emulator.")
            is LaunchResult.NoEmulatorForSystem -> showMessage("No emulator configured for ${r.systemId}.")
            is LaunchResult.NeedsFilePath -> showMessage("${r.emulator.name} needs a ROM folder on internal storage or an SD card.")
            is LaunchResult.Failed -> showMessage("Couldn't start ${r.emulator?.name ?: "emulator"}: ${r.reason}")
        }
    }

    fun openPicker(game: Game) {
        val options = graph.launcher.choicesFor(game.systemId).mapNotNull { c ->
            val def = graph.registry.emulator(c.emulator) ?: return@mapNotNull null
            val label = c.vars["core"]?.let { "${def.name} · $it" } ?: def.name
            EmulatorOption(c, label, graph.launcher.installedTarget(def) != null)
        }
        local.update { it.copy(picker = EmulatorPicker(game, options)) }
    }

    fun dismissPicker() = local.update { it.copy(picker = null) }

    /**
     * Top screen is back in front. Closes a running session, records playtime, rebuilds the
     * "Recently played" shelf and puts focus back on the game that was just played (now first in
     * that shelf). Also refreshes the library in the background if the last scan is stale.
     */
    fun onReturnedHome() {
        val ended = graph.shared.endSession()
        if (ended != null) {
            graph.playtime.record(ended.game.uri, ended.startedAtMillis, System.currentTimeMillis())
            local.update {
                it.copy(
                    section = Section.Games,
                    shelves = buildShelves(),
                    focusTarget = FocusTarget(RECENT_ID, ended.game.uri, SystemClock.uptimeMillis()),
                )
            }
        }
        if (lastScanAt != 0L && SystemClock.elapsedRealtime() - lastScanAt > STALE_SCAN_MS) rescan(silent = true)
    }

    private fun showMessage(text: String) {
        local.update { it.copy(message = text) }
        viewModelScope.launch {
            delay(5_000)
            local.update { if (it.message == text) it.copy(message = null) else it }
        }
    }

    private fun rootUri(): Uri? = graph.prefs.getString(KEY_ROOT, null)?.let(Uri::parse)

    private companion object {
        const val KEY_ROOT = "rom_root"
        const val RECENT_ID = "recent"
        const val RECENT_COUNT = 12
        const val STALE_SCAN_MS = 10 * 60_000L
    }
}
