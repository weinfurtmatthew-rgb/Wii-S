package app.wiis.launcher.spike

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wiis.designsystem.WiisTile
import app.wiis.launcher.BuildConfigInfo
import app.wiis.launcher.graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Milestone 0 hardware spike. Produces one plain-text report covering displays, which app owns the
 * bottom screen, and whether each emulator definition resolves on this device.
 */
@Composable
fun SpikeScreen(onPickFolder: () -> Unit) {
    val context = LocalContext.current
    val companion by context.graph.shared.companion.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf("Collecting…") }
    var savedTo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh, companion) {
        // Give a just-launched companion a moment to register before reporting.
        if (refresh > 0) delay(800)
        report = withContext(Dispatchers.Default) { buildReport(context) }
        savedTo = withContext(Dispatchers.IO) { saveReport(context, report) }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpikeButton("Refresh") { refresh++ }
            SpikeButton("Re-place bottom screen") {
                context.graph.companionPlacer.ensurePlaced(force = true)
                refresh++
            }
            SpikeButton("Share report") { share(context, report) }
            SpikeButton("ROM folder") { onPickFolder() }
        }
        savedTo?.let { Text("Saved to $it", style = MaterialTheme.typography.labelMedium) }
        Text(
            report,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        )
    }
}

@Composable
private fun SpikeButton(label: String, onClick: () -> Unit) {
    WiisTile(onClick = onClick) { Text(label, Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
}

private fun buildReport(context: Context): String = buildString {
    val g = context.graph
    val probe = g.displayProbe
    appendLine("Wii-S spike report — ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
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
    appendLine(
        if (c != null) "Companion: RUNNING on display ${c.displayId}, mode ${c.mode}"
        else "Companion: NOT visible",
    )
    appendLine()
    append(g.perf.report(probe.displays().firstOrNull { it.id == 0 }?.refreshHz))
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
            appendLine("  Tip: ambiguous types (zip, 7z, iso, chd…) need a system folder, e.g. nds/, switch/, psp/.")
        }
    }
}

private fun saveReport(context: Context, report: String): String? = runCatching {
    val dir = context.getExternalFilesDir(null) ?: return null
    File(dir, "spike-report.txt").apply { writeText(report) }.absolutePath
}.getOrNull()

private fun share(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
    context.startActivity(Intent.createChooser(send, "Share spike report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
