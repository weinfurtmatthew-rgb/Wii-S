package app.wiis.launcher.home

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wiis.designsystem.LayoutTokens
import app.wiis.designsystem.UiEvent
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Section { Games, Apps, System }

sealed interface LibraryStatus {
    data object NoFolder : LibraryStatus
    data class Scanning(val found: Int) : LibraryStatus
    data object Ready : LibraryStatus
}

/** One row (shelves layout) or one header group (grid layout) on the Games screen. */
data class Shelf(val id: String, val title: String, val games: List<Game>)

/**
 * Which tile should take focus. [token] changes every time focus must be (re)applied, so the UI
 * reacts even when the target is the same game as last time.
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
    private var layout: LayoutTokens = graph.themes.spec.value.layout
    private var scanning = false
    private var lastScanAt = 0L
    private var lastFocused: Pair<String, String>? = null

    /** Sounds the screen should play for outcomes only the ViewModel knows (launch worked or failed). */
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

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
        viewModelScope.launch {
            graph.themes.spec.map { it.layout }.distinctUntilChanged().collect { tokens ->
                layout = tokens
                if (games.isNotEmpty()) local.update { it.copy(shelves = buildShelves()) }
            }
        }
    }

    // ---- Navigation -------------------------------------------------------------------------

    fun selectSection(section: Section) = local.update { s ->
        if (s.section == section) return@update s
        val target = if (section == Section.Games) restoreTarget(s.shelves) else null
        s.copy(section = section, focusTarget = target ?: s.focusTarget)
    }

    fun cycleSection(delta: Int) {
        val all = Section.entries
        selectSection(all[(all.indexOf(local.value.section) + delta).mod(all.size)])
    }

    /** Start: System screen. Select: flip between Games and Apps. */
    fun onStart() = selectSection(Section.System)
    fun onSelect() = selectSection(if (local.value.section == Section.Apps) Section.Games else Section.Apps)

    fun focusGame(shelfId: String, game: Game) {
        lastFocused = shelfId to game.uri
        graph.shared.setFocus(Focus.OnGame(game))
    }

    fun focusApp(app: AppEntry) = graph.shared.setFocus(Focus.OnApp(app))
    fun focusConsumed() = local.update { it.copy(focusTarget = null) }

    fun launchApp(app: AppEntry) = graph.apps.launch(app)

    private fun restoreTarget(shelves: List<Shelf>): FocusTarget? {
        val token = SystemClock.uptimeMillis()
        lastFocused?.let { (shelf, uri) ->
            if (shelves.any { it.id == shelf && it.games.any { g -> g.uri == uri } }) return FocusTarget(shelf, uri, token)
        }
        val first = shelves.firstOrNull() ?: return null
        val game = first.games.firstOrNull() ?: return null
        return FocusTarget(first.id, game.uri, token)
    }

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
                showMessage("Scan failed: ${e.message}. Pick the ROM folder again from the System screen.", error = true)
            }
        }
    }

    private fun setGames(list: List<Game>, focusFirst: Boolean) {
        games = list
        val shelves = buildShelves()
        local.update { s -> s.copy(shelves = shelves, focusTarget = if (focusFirst) restoreTarget(shelves) else s.focusTarget) }
        graph.art.refreshSummary(list)
        graph.art.prefetch(list)
    }

    private fun buildShelves(): List<Shelf> {
        val recent = if (!layout.showRecent) emptyList() else games
            .map { it to graph.playtime.lastPlayed(it.uri) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(layout.recentCount.coerceIn(1, 50))
            .map { it.first }
        val groups = games.groupBy { it.systemId }
        val ordered = when (layout.systemOrder) {
            "alphabetical" -> groups.entries.sortedBy { (systemNames[it.key] ?: it.key).lowercase() }
            "mostGames" -> groups.entries.sortedByDescending { it.value.size }
            else -> groups.entries.sortedBy { systemOrder[it.key] ?: Int.MAX_VALUE }
        }
        val bySystem = ordered.map { (id, list) -> Shelf(id, systemNames[id] ?: id, list) }
        return listOfNotNull(recent.takeIf { it.isNotEmpty() }?.let { Shelf(RECENT_ID, "Recently played", it) }) + bySystem
    }

    // ---- Launching --------------------------------------------------------------------------

    fun launchGame(game: Game, choice: EmulatorChoice? = null) {
        local.update { it.copy(picker = null) }
        when (val r = graph.launcher.launch(game, choice)) {
            is LaunchResult.Launched -> {
                _events.tryEmit(UiEvent.Launch)
                graph.shared.startSession(Session(game, r.emulator.name, r.emulator.usesBothScreens, System.currentTimeMillis()))
            }
            is LaunchResult.NotInstalled -> showMessage("${r.emulator.name} isn't installed. Press Y to pick another emulator.", error = true)
            is LaunchResult.NoEmulatorForSystem -> showMessage("No emulator is set up for ${r.systemId}.", error = true)
            is LaunchResult.NeedsFilePath -> showMessage("${r.emulator.name} needs the ROM on internal storage or an SD card.", error = true)
            is LaunchResult.Failed -> showMessage("Couldn't start ${r.emulator?.name ?: "the emulator"}: ${r.reason}", error = true)
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
     * "Recently played" shelf and puts focus back on the game that was just played. Also refreshes
     * the library in the background if the last scan is stale.
     */
    fun onReturnedHome() {
        val ended = graph.shared.endSession()
        if (ended != null) {
            graph.playtime.record(ended.game.uri, ended.startedAtMillis, System.currentTimeMillis())
            val shelves = buildShelves()
            val shelfId = if (shelves.any { it.id == RECENT_ID }) RECENT_ID else ended.game.systemId
            lastFocused = shelfId to ended.game.uri
            local.update {
                it.copy(
                    section = Section.Games,
                    shelves = shelves,
                    focusTarget = FocusTarget(shelfId, ended.game.uri, SystemClock.uptimeMillis()),
                )
            }
        }
        if (lastScanAt != 0L && SystemClock.elapsedRealtime() - lastScanAt > STALE_SCAN_MS) rescan(silent = true)
    }

    private fun showMessage(text: String, error: Boolean = false) {
        if (error) _events.tryEmit(UiEvent.Error)
        local.update { it.copy(message = text) }
        viewModelScope.launch {
            delay(5_000)
            local.update { if (it.message == text) it.copy(message = null) else it }
        }
    }

    private fun rootUri(): Uri? = graph.prefs.getString(KEY_ROOT, null)?.let(Uri::parse)

    companion object {
        const val KEY_ROOT = "rom_root"
        const val RECENT_ID = "recent"
        private const val STALE_SCAN_MS = 10 * 60_000L
    }
}
