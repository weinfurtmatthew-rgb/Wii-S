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
import app.wiis.launcher.stats.Played
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

enum class Section { Games, Apps, Collections }

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

/** Hero layout: a section's first page, one system's games (A on its tile) or one collection's (A on its card). */
sealed interface HomeView {
    data object Home : HomeView
    data class InSystem(val systemId: String) : HomeView
    data class InCollection(val id: String) : HomeView
}

/**
 * Hero layout: the element that should take focus. Keys: "hero", "sys:<id>", "pin:<uri>",
 * "grid:<uri>", "col:<id>", "nav:<section>", "dock:<slot>". [token] changes on every request.
 */
data class HeroFocus(val key: String, val token: Long)

data class DockPicker(val slot: Int, val options: List<AppEntry>)

data class HomeUiState(
    val section: Section = Section.Games,
    val apps: List<AppEntry> = emptyList(),
    val library: LibraryStatus = LibraryStatus.NoFolder,
    val shelves: List<Shelf> = emptyList(),
    val focusTarget: FocusTarget? = null,
    val picker: EmulatorPicker? = null,
    val message: String? = null,
    // Hero layout
    val hero: Game? = null,
    val heroPlayedMillis: Long = 0,
    /** The bottom row: games pinned with X, newest first (only those in the library). */
    val pinned: List<Game> = emptyList(),
    val systems: List<SystemEntry> = emptyList(),
    val allPreview: List<String> = emptyList(),
    /** Per system id ("all" included): the game whose art fills its XMB icon. */
    val systemSamples: Map<String, Game> = emptyMap(),
    val view: HomeView = HomeView.Home,
    val viewGames: List<Game> = emptyList(),
    val heroFocus: HeroFocus? = null,
    val dock: List<AppEntry> = emptyList(),
    val dockPicker: DockPicker? = null,
    /** Collections: yours, then the series Wii-S offers. */
    val collections: List<CollectionEntry> = emptyList(),
    /** The settings popup is open on this page ("theme", "sounds", "art", "ra", "report"); null: closed. */
    val settings: String? = null,
)

/** Unidirectional: the screen calls intents (functions); state flows back as one immutable value. */
class HomeViewModel(private val graph: AppGraph) : ViewModel() {

    private val systemNames = graph.registry.systems.associate { it.id to it.name }
    private val systemOrder = graph.registry.systems.mapIndexed { i, s -> s.id to i }.toMap()

    private val local = MutableStateFlow(HomeUiState())
    private var games: List<Game> = emptyList()
    /** The series found in the library's titles (worked out when the library changes). Before init: collected there. */
    private var series: List<SeriesFinder.Series> = emptyList()
    private var layout: LayoutTokens = graph.themes.spec.value.layout
    private var scanning = false
    private var lastScanAt = 0L
    private var lastFocused: Pair<String, String>? = null

    /** Sounds the screen should play for outcomes only the ViewModel knows (launch worked or failed). */
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val state: StateFlow<HomeUiState> =
        combine(local, graph.apps.apps, graph.dock) { s, apps, dock -> s.copy(apps = apps, dock = dock) }
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
                if (games.isNotEmpty()) local.update { buildHome(it.copy(shelves = buildShelves())) }
            }
        }
        viewModelScope.launch { graph.shared.openApps.collect { selectSection(Section.Apps) } }
        viewModelScope.launch { graph.pins.collect { pins -> local.update { it.copy(pinned = Pins.resolve(pins, games)) } } }
        viewModelScope.launch { graph.shared.openSystemPage.collect { page -> openSettings(page) } }
        viewModelScope.launch { graph.collections.collect { file -> local.update { it.copy(collections = CollectionsModel.resolve(file, series, games)).withCollectionView() } } }
    }

    // ---- Navigation -------------------------------------------------------------------------

    fun selectSection(section: Section) = local.update { s ->
        if (s.section == section) return@update s
        val target = if (section == Section.Games) restoreTarget(s.shelves) else null
        // Hero layout, as in the workbench: a section always opens on its first item (Home: the card).
        val heroTarget = when (section) {
            Section.Games -> homeFocus(s)
            Section.Collections -> s.collections.firstOrNull()?.let { HeroFocus(colKey(it.id), token()) }
            Section.Apps -> null
        }
        s.copy(section = section, focusTarget = target ?: s.focusTarget, view = HomeView.Home, viewGames = emptyList(), heroFocus = heroTarget)
    }

    fun cycleSection(delta: Int) {
        val all = Section.entries
        selectSection(all[(all.indexOf(local.value.section) + delta).mod(all.size)])
    }

    /** Start: the settings popup, from anywhere (again to close it). Select opens the highlighted item's menu (the screen does that). */
    fun onStart() = if (local.value.settings == null) openSettings(null) else closeSettings()

    /** The settings popup, on [page] or the last one. */
    fun openSettings(page: String?) = local.update { it.copy(settings = page ?: it.settings ?: "theme") }
    fun closeSettings() = local.update { it.copy(settings = null) }
    fun settingsPage(page: String) = local.update { if (it.settings == null) it else it.copy(settings = page) }

    fun focusGame(shelfId: String, game: Game) {
        lastFocused = shelfId to game.uri
        graph.shared.setFocus(Focus.OnGame(game))
    }

    fun focusApp(app: AppEntry) = graph.shared.setFocus(Focus.OnApp(app))
    fun focusCollection(entry: CollectionEntry) = graph.shared.setFocus(Focus.OnCollection(entry.id, entry.name, entry.games))
    fun focusSystem(entry: SystemEntry) = graph.shared.setFocus(Focus.OnSystem(entry.id, entry.name, entry.count))
    fun focusConsumed() = local.update { it.copy(focusTarget = null) }

    // ---- Hero layout --------------------------------------------------------------------------

    /** A on a system tile: its games, A to Z, with focus on the first. B comes back to the tile. */
    fun openSystem(id: String) {
        val list = HomeModel.gamesFor(id, games)
        local.update { s ->
            s.copy(view = HomeView.InSystem(id), viewGames = list, heroFocus = list.firstOrNull()?.let { HeroFocus(gridKey(it), token()) })
        }
    }

    /** B inside a system or a collection: back to its tile or card. False if not inside one. */
    fun closeView(): Boolean {
        val back = when (val view = local.value.view) {
            is HomeView.InSystem -> sysKey(view.systemId)
            is HomeView.InCollection -> colKey(view.id)
            HomeView.Home -> return false
        }
        local.update { it.copy(view = HomeView.Home, viewGames = emptyList(), heroFocus = HeroFocus(back, token())) }
        return true
    }

    // ---- Collections ----------------------------------------------------------------------------

    /** A on a collection's card: its games, A to Z, focus on the first. */
    fun openCollection(id: String) {
        val list = local.value.collections.firstOrNull { it.id == id }?.games.orEmpty()
        local.update { s ->
            s.copy(view = HomeView.InCollection(id), viewGames = list, heroFocus = list.firstOrNull()?.let { HeroFocus(gridKey(it), token()) })
        }
    }

    /** Saves a change and shows it at once (the collector would only catch up on the next dispatch). */
    private fun editCollections(change: (CollectionsFile) -> CollectionsFile) {
        graph.editCollections(change)
        local.update { it.copy(collections = CollectionsModel.resolve(graph.collections.value, series, games)).withCollectionView() }
    }

    fun keepCollection(id: String) = editCollections { CollectionsModel.keep(it, id.removePrefix("s:")) }
    fun renameCollection(id: String, name: String) { if (name.isNotBlank()) editCollections { CollectionsModel.rename(it, id, name.trim()) } }

    /** Hide (a series) or delete (one of yours); focus moves to the next card. */
    fun hideCollection(id: String) {
        val i = local.value.collections.indexOfFirst { it.id == id }
        editCollections { CollectionsModel.hide(it, id) }
        val now = local.value.collections
        val next = now.getOrNull(i) ?: now.lastOrNull()
        local.update { it.copy(heroFocus = next?.let { n -> HeroFocus(colKey(n.id), token()) }) }
    }

    fun addToCollection(id: String, game: Game) = editCollections { CollectionsModel.add(it, id, game.uri) }

    /** Remove [game] from collection [id]; inside that collection, focus moves to the game that takes its place. */
    fun removeFromCollection(id: String, game: Game) {
        val before = local.value.viewGames
        editCollections { CollectionsModel.remove(it, id, game.uri) }
        val view = local.value.view
        if (view is HomeView.InCollection && view.id == id) {
            val i = before.indexOfFirst { it.uri == game.uri }
            val after = local.value.viewGames
            val next = after.getOrNull(i) ?: after.lastOrNull()
            local.update {
                if (next != null) it.copy(heroFocus = HeroFocus(gridKey(next), token()))
                else it.copy(view = HomeView.Home, viewGames = emptyList(), heroFocus = HeroFocus(colKey(id), token()))
            }
        }
    }

    fun createCollection(name: String, game: Game) {
        if (name.isBlank()) return
        editCollections { CollectionsModel.create(it, name.trim(), game.uri).first }
    }

    /** Inside a collection: its games follow the collection as it changes. */
    private fun HomeUiState.withCollectionView(): HomeUiState {
        val view = view as? HomeView.InCollection ?: return this
        val entry = collections.firstOrNull { it.id == view.id } ?: return copy(view = HomeView.Home, viewGames = emptyList())
        return copy(viewGames = entry.games)
    }

    fun heroFocusConsumed() = local.update { it.copy(heroFocus = null) }

    /**
     * X on a game: pins it to the bottom row, or unpins it. Unpinned from the row itself, focus
     * moves to the game that takes its place (or the one before it), or to the Jump back in card
     * once the row is empty.
     */
    fun togglePin(game: Game, fromRow: Boolean) {
        val before = local.value.pinned
        val pinned = graph.togglePin(game.uri)
        val after = Pins.resolve(graph.pins.value, games)
        val focus = if (fromRow && !pinned) {
            val i = before.indexOfFirst { it.uri == game.uri }.coerceAtLeast(0)
            val next = after.getOrNull(i) ?: after.lastOrNull()
            HeroFocus(if (next != null) pinKey(next) else KEY_HERO, token())
        } else {
            null
        }
        local.update { it.copy(pinned = after, heroFocus = focus ?: it.heroFocus) }
    }

    /** Y on a dock app: choose another app for that spot. */
    fun openDockPicker(slot: Int) = local.update { it.copy(dockPicker = DockPicker(slot, graph.apps.apps.value)) }

    fun chooseDockApp(app: AppEntry) {
        val picker = local.value.dockPicker ?: return
        graph.setDockApp(picker.slot, app)
        local.update { it.copy(dockPicker = null, heroFocus = HeroFocus(dockKey(picker.slot), token())) }
    }

    fun dismissDockPicker() {
        val picker = local.value.dockPicker ?: return
        local.update { it.copy(dockPicker = null, heroFocus = HeroFocus(dockKey(picker.slot), token())) }
    }

    private fun homeFocus(s: HomeUiState): HeroFocus? = when {
        s.hero != null -> HeroFocus(KEY_HERO, token())
        s.pinned.isNotEmpty() -> HeroFocus(pinKey(s.pinned.first()), token())
        s.systems.isNotEmpty() -> HeroFocus(sysKey(s.systems.first().id), token())
        else -> null
    }

    private fun buildHome(s: HomeUiState): HomeUiState {
        val (hero, recent) = HomeModel.heroAndRecent(games, { graph.playtime.lastPlayed(it.uri) }, layout.recentCount, layout.showRecent)
        val systems = HomeModel.systems(games, systemNames, systemOrder, layout.systemOrder)
        graph.shared.setHero(hero)
        val view = s.view
        val stillThere = view !is HomeView.InSystem || systems.any { it.id == view.systemId }
        series = SeriesFinder.find(games)
        return s.copy(
            collections = CollectionsModel.resolve(graph.collections.value, series, games),
            hero = hero,
            heroPlayedMillis = hero?.let { graph.playtime.totalMillis(it.uri) } ?: 0L,
            pinned = Pins.resolve(graph.pins.value, games),
            systems = systems,
            allPreview = HomeModel.allTilePreview(hero, recent, systems),
            systemSamples = HomeModel.systemSamples(games, { graph.playtime.lastPlayed(it.uri) }, graph.art::hasArt, hero),
            view = if (stillThere) view else HomeView.Home,
            viewGames = if (stillThere && view is HomeView.InSystem) HomeModel.gamesFor(view.systemId, games) else s.viewGames,
        ).withCollectionView()
    }

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
        local.update { s ->
            val home = buildHome(s.copy(shelves = shelves, focusTarget = if (focusFirst) restoreTarget(shelves) else s.focusTarget))
            if (focusFirst && home.section == Section.Games && home.view == HomeView.Home) home.copy(heroFocus = homeFocus(home)) else home
        }
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
            val now = System.currentTimeMillis()
            graph.playtime.record(ended.game.uri, ended.startedAtMillis, now)
            runCatching { graph.sessions.add(Played(ended.game.uri, ended.game.systemId, ended.startedAtMillis, now)) }
            val shelves = buildShelves()
            val shelfId = if (shelves.any { it.id == RECENT_ID }) RECENT_ID else ended.game.systemId
            lastFocused = shelfId to ended.game.uri
            local.update {
                val home = buildHome(
                    it.copy(
                        section = Section.Games,
                        shelves = shelves,
                        focusTarget = FocusTarget(shelfId, ended.game.uri, SystemClock.uptimeMillis()),
                    ),
                )
                // Hero layout: the game just played is now the Jump back in card; inside a system, stay on its tile.
                val inSystem = home.view is HomeView.InSystem && home.viewGames.any { g -> g.uri == ended.game.uri }
                home.copy(heroFocus = HeroFocus(if (inSystem) gridKey(ended.game) else KEY_HERO, token()))
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

    private fun token() = SystemClock.uptimeMillis()

    companion object {
        const val KEY_ROOT = "rom_root"
        const val RECENT_ID = "recent"
        private const val STALE_SCAN_MS = 10 * 60_000L

        const val KEY_HERO = "hero"
        fun sysKey(id: String) = "sys:$id"
        fun pinKey(game: Game) = "pin:${game.uri}"
        fun gridKey(game: Game) = "grid:${game.uri}"
        fun navKey(section: Section) = "nav:${section.name}"
        fun dockKey(slot: Int) = "dock:$slot"
        fun colKey(id: String) = "col:$id"
    }
}
