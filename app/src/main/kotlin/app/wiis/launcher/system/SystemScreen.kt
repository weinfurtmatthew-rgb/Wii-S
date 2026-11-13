@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.wiis.launcher.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.SOUND_EVENTS
import app.wiis.designsystem.WiisStyle
import app.wiis.launcher.AppGraph
import app.wiis.launcher.BuildConfigInfo
import app.wiis.launcher.graph
import app.wiis.launcher.ui.ActionTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Page(val label: String) { Theme("Theme"), Sounds("Sounds"), Art("Box art"), Report("Report") }

private val EVENT_LABELS = mapOf(
    "move" to "Move",
    "select" to "Select",
    "back" to "Back",
    "tab" to "Switch tab",
    "launch" to "Start game",
    "error" to "Error",
    "bump" to "End of row",
)

/** Settings and diagnostics. Everything here is operable with the controller. */
@Composable
fun SystemScreen(onPickRomFolder: () -> Unit) {
    val style = LocalWiis.current
    var page by rememberSaveable { mutableStateOf(Page.Theme) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 8.dp, top = 6.dp)) {
            Page.entries.forEachIndexed { i, p ->
                ActionTile(
                    label = p.label,
                    onClick = { page = p },
                    emphasized = p == page,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                Page.Theme -> ThemePage(style)
                Page.Sounds -> SoundsPage(style)
                Page.Art -> ArtPage(style)
                Page.Report -> ReportPage(style, onPickRomFolder)
            }
        }
    }
}

@Composable
private fun Section(title: String, style: WiisStyle, body: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(style.surface, style.cardShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = style.tab.copy(color = style.text))
        body()
    }
}

@Composable
private fun Note(text: String, style: WiisStyle) = Text(text, style = style.label)

@Composable
private fun ThemePage(style: WiisStyle) {
    val context = LocalContext.current
    val graph = context.graph
    val spec by graph.themes.spec.collectAsStateWithLifecycle()
    val fontStatus by graph.fonts.status.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        message = graph.themes.import(uri).fold(
            onSuccess = { "Applied \"${it.name}\"." },
            onFailure = { "That file isn't a Wii-S theme: ${it.message}" },
        )
    }
    Section("Theme", style) {
        Text("Active: ${spec.name}" + if (graph.themes.isImported) " (imported)" else " (built-in)", style = style.body)
        Note("Fonts: ${fontStatus.ifEmpty { "loading…" }}", style)
        Note("Make or change a theme in the Design Workbench, export theme.json, then import it here.", style)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Import theme file…", onClick = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, emphasized = true)
            if (graph.themes.isImported) ActionTile("Use built-in theme", onClick = { graph.themes.reset(); message = "Back to the built-in theme." })
        }
        message?.let { Text(it, style = style.body) }
    }
}

@Composable
private fun SoundsPage(style: WiisStyle) {
    val graph = LocalContext.current.graph
    val spec by graph.themes.spec.collectAsStateWithLifecycle()
    val muted by graph.sounds.muted.collectAsStateWithLifecycle()
    val custom by graph.sounds.custom.collectAsStateWithLifecycle()
    var pendingEvent by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val event = pendingEvent
        pendingEvent = null
        if (uri == null || event == null) return@rememberLauncherForActivityResult
        message = graph.sounds.importCustom(event, uri).fold(
            onSuccess = { "\"$it\" now plays for ${EVENT_LABELS[event]}." },
            onFailure = { "Couldn't use that file: ${it.message}" },
        )
    }
    Section("Sounds", style) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionTile(if (muted) "Sounds: Off" else "Sounds: On", onClick = { graph.sounds.setMuted(!muted) }, emphasized = !muted)
            if (!spec.sounds.enabled) Note("This theme turns sounds off.", style)
        }
        Note("Each sound comes from the theme unless you choose your own file (OGG, WAV, MP3, under 5 MB).", style)
        message?.let { Text(it, style = style.body) }
    }
    SOUND_EVENTS.forEach { event ->
        Section(EVENT_LABELS[event] ?: event, style) {
            Note(custom[event]?.let { "Your file: $it" } ?: "Theme sound", style)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Play", onClick = { graph.sounds.play(event, force = true) })
                ActionTile("Use my file…", onClick = { pendingEvent = event; pick.launch(arrayOf("audio/*")) })
                if (custom.containsKey(event)) ActionTile("Use theme sound", onClick = { graph.sounds.clearCustom(event) })
            }
        }
    }
}

@Composable
private fun ArtPage(style: WiisStyle) {
    val graph = LocalContext.current.graph
    val status by graph.art.status.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    val enabled = remember(refresh) { graph.artEnabled }
    val libretro = remember(refresh) { graph.artLibretro }
    val folder = remember(refresh) { graph.artFolder }
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        graph.artFolder = uri
        refresh++
        graph.art.prefetch(graph.currentGames(), force = true)
    }
    Section("Box art", style) {
        Text(artSummary(status), style = style.body)
        status.lastError?.let { Note("Last problem: $it", style) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(if (enabled) "Box art: On" else "Box art: Off", onClick = { graph.artEnabled = !enabled; refresh++ }, emphasized = enabled)
            ActionTile("Fetch missing art now", onClick = { graph.art.prefetch(graph.currentGames(), force = true) })
        }
    }
    Section("Sources (tried in this order)", style) {
        Text("1. Your art folder: " + (folder?.lastPathSegment?.substringAfter(':') ?: "none"), style = style.body)
        Note("Images named exactly like your ROM files (for example ES-DE's downloaded_media).", style)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(if (folder == null) "Choose art folder…" else "Change folder…", onClick = { pickFolder.launch(null) })
            if (folder != null) ActionTile("Remove folder", onClick = { graph.artFolder = null; refresh++ })
        }
        Text("2. libretro thumbnails: " + if (libretro) "on" else "off", style = style.body)
        Note("Free, no account. Matches No-Intro / Redump file names. Doesn't cover Switch or Steam.", style)
        ActionTile(if (libretro) "Turn off libretro" else "Turn on libretro", onClick = { graph.artLibretro = !libretro; refresh++ })
        Note("More sources (ScreenScraper, SteamGridDB, IGDB…) need your account keys. They come next, once you pick which ones.", style)
    }
}

private fun artSummary(s: app.wiis.emulator.art.ArtStatus): String = when {
    s.running -> "Checking ${s.checked} of ${s.total}…"
    s.total == 0 -> "No games yet."
    else -> buildString {
        append("Art for ${s.found} of ${s.total} games")
        if (s.byProvider.isNotEmpty()) append(" (" + s.byProvider.entries.joinToString { "${providerLabel(it.key)} ${it.value}" } + ")")
        if (s.missingBySystem.isNotEmpty()) append(". Missing: " + s.missingBySystem.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" })
    }
}

private fun providerLabel(id: String) = when (id) {
    "libretro" -> "libretro"
    "folder" -> "your folder"
    else -> id
}

@Composable
private fun ReportPage(style: WiisStyle, onPickRomFolder: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val companion by graph.shared.companion.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf("Collecting…") }
    var savedTo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh, companion) {
        if (refresh > 0) delay(800)
        report = withContext(Dispatchers.Default) { buildReport(context, graph) }
        savedTo = withContext(Dispatchers.IO) { saveReport(context, report) }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile("Refresh", onClick = { refresh++ })
        ActionTile("Share report", onClick = { share(context, report) })
        ActionTile("Re-place bottom screen", onClick = { graph.companionPlacer.ensurePlaced(force = true); refresh++ })
        ActionTile("ROM folder…", onClick = onPickRomFolder)
    }
    savedTo?.let { Note("Saved to $it", style) }
    Text(
        report,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = style.text,
        modifier = Modifier
            .fillMaxWidth()
            .background(style.surface, style.cardShape)
            .padding(14.dp),
    )
}

private fun buildReport(context: Context, g: AppGraph): String = buildString {
    val probe = g.displayProbe
    appendLine("Wii-S report — ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
    appendLine("Launcher ${BuildConfigInfo.versionName(context)}")
    appendLine(probe.deviceSummary())
    appendLine()
    appendLine("== Displays ==")
    probe.displays().forEach { d ->
        appendLine("#${d.id} \"${d.name}\" ${d.widthPx}x${d.heightPx} @${"%.0f".format(d.refreshHz)}Hz ${d.densityDpi}dpi ${d.state}")
        appendLine("    flags: ${d.flags.joinToString(" ")}")
        appendLine("    modes: ${d.supportedModes.joinToString(" ")}")
    }
    appendLine()
    appendLine("== Home resolution ==")
    append(probe.homeResolution())
    val c = g.shared.companion.value
    appendLine(if (c != null) "Companion: RUNNING on display ${c.displayId}, mode ${c.mode}" else "Companion: NOT visible")
    appendLine()
    append(g.perf.report(probe.displays().firstOrNull { it.id == 0 }?.refreshHz))
    appendLine()
    appendLine("== Theme ==")
    val spec = g.themes.spec.value
    appendLine("${spec.name} (${if (g.themes.isImported) "imported" else "built-in"}), schema ${spec.schema}")
    appendLine("Fonts: ${g.fonts.status.value}")
    appendLine("Sounds: ${if (g.sounds.muted.value) "muted on this device" else if (spec.sounds.enabled) "on" else "off in theme"}; your files: ${g.sounds.custom.value.keys.joinToString().ifEmpty { "none" }}")
    appendLine()
    appendLine("== Box art ==")
    appendLine(artSummary(g.art.status.value))
    g.art.status.value.lastError?.let { appendLine("Last problem: $it") }
    appendLine("Sources: " + listOfNotNull(g.artFolder?.let { "your folder" }, "libretro".takeIf { g.artLibretro }).joinToString().ifEmpty { "none" } + if (g.artEnabled) "" else " (box art off)")
    appendLine()
    append(g.diagnostics.report(g.diagnostics.run()))
    appendLine()
    appendLine("== Library ==")
    val lib = g.libraryStore.load()
    if (lib == null) appendLine("No ROM folder scanned yet") else {
        appendLine("${lib.games.size} games from ${lib.rootUri}")
        lib.games.groupingBy { it.systemId }.eachCount().forEach { (s, n) -> appendLine("  $s: $n") }
        if (lib.unrecognized.isNotEmpty()) {
            appendLine("Skipped (not recognised): " + lib.unrecognized.entries.sortedByDescending { it.value }.joinToString { "${it.key}: ${it.value}" })
            lib.unrecognizedExamples.forEach { appendLine("    $it") }
        }
    }
}

private fun saveReport(context: Context, report: String): String? = runCatching {
    val dir = context.getExternalFilesDir(null) ?: return null
    File(dir, "wii-s-report.txt").apply { writeText(report) }.absolutePath
}.getOrNull()

private fun share(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
    context.startActivity(Intent.createChooser(send, "Share Wii-S report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
