package app.wiis.launcher.home

import android.content.Intent
import android.net.Uri
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

enum class Section { Games, Apps, Spike }

sealed interface LibraryStatus {
    data object NoFolder : LibraryStatus
    data class Scanning(val found: Int) : LibraryStatus
    data class Ready(val games: List<Game>) : LibraryStatus
}

data class EmulatorOption(val choice: EmulatorChoice, val name: String, val installed: Boolean)
data class EmulatorPicker(val game: Game, val options: List<EmulatorOption>)

data class HomeUiState(
    val section: Section = Section.Games,
    val apps: List<AppEntry> = emptyList(),
    val library: LibraryStatus = LibraryStatus.NoFolder,
    val systemNames: Map<String, String> = emptyMap(),
    val picker: EmulatorPicker? = null,
    val message: String? = null,
)

/** Unidirectional: the screen sends intents (functions), state flows back as one immutable value. */
class HomeViewModel(private val graph: AppGraph) : ViewModel() {

    private val local = MutableStateFlow(
        HomeUiState(systemNames = graph.registry.systems.associate { it.id to it.name }),
    )

    val state: StateFlow<HomeUiState> =
        combine(local, graph.apps.apps) { s, apps -> s.copy(apps = apps) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { graph.libraryStore.load() }
            if (cached != null) local.update { it.copy(library = LibraryStatus.Ready(cached.games)) }
            else if (rootUri() != null) rescan()
        }
    }

    fun selectSection(section: Section) = local.update { it.copy(section = section) }

    fun cycleSection(delta: Int) = local.update {
        val all = Section.entries
        it.copy(section = all[(all.indexOf(it.section) + delta).mod(all.size)])
    }

    fun focusGame(game: Game) = graph.shared.setFocus(Focus.OnGame(game))
    fun focusApp(app: AppEntry) = graph.shared.setFocus(Focus.OnApp(app))

    fun launchApp(app: AppEntry) = graph.apps.launch(app)

    fun onFolderPicked(uri: Uri?) {
        uri ?: return
        graph.context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        graph.prefs.edit { putString(KEY_ROOT, uri.toString()) }
        rescan()
    }

    fun rescan() {
        val root = rootUri() ?: return
        local.update { it.copy(library = LibraryStatus.Scanning(0)) }
        viewModelScope.launch {
            val games = runCatching {
                withContext(Dispatchers.IO) {
                    RomScanner(graph.context.contentResolver, graph.detector).scan(root) { found ->
                        local.update { it.copy(library = LibraryStatus.Scanning(found)) }
                    }
                }
            }.getOrElse { e ->
                showMessage("Scan failed: ${e.message}. Re-pick the ROM folder.")
                emptyList()
            }
            withContext(Dispatchers.IO) {
                graph.libraryStore.save(LibrarySnapshot(root.toString(), System.currentTimeMillis(), games))
            }
            local.update { it.copy(library = LibraryStatus.Ready(games)) }
        }
    }

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

    /** Called when the top screen comes back to the foreground: closes any running session. */
    fun onReturnedHome() {
        val ended = graph.shared.endSession() ?: return
        graph.playtime.record(ended.game.uri, ended.startedAtMillis, System.currentTimeMillis())
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
    }
}
