package app.wiis.launcher.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import android.view.View
import app.wiis.designsystem.HapticTokens
import app.wiis.designsystem.SOUND_EVENTS
import app.wiis.designsystem.SoundTokens
import app.wiis.designsystem.Synth
import app.wiis.designsystem.UiEvent
import app.wiis.designsystem.UiFeedback
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Interface sounds. Each event plays either your own audio file (imported on the device) or the
 * theme's synthesised sound, rendered to a WAV once and played through SoundPool for low latency.
 */
class SoundEngine(private val context: Context, private val scope: CoroutineScope) {
    private val customDir = File(context.filesDir, "sounds").apply { mkdirs() }
    private val renderDir = File(context.cacheDir, "synth").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("sounds", Context.MODE_PRIVATE)
    private val lock = Mutex()

    @Volatile private var pool: SoundPool? = null
    @Volatile private var ids: Map<String, Int> = emptyMap()
    @Volatile private var tokens = SoundTokens()
    @Volatile private var suppressMoveUntil = 0L

    private val _muted = MutableStateFlow(prefs.getBoolean(KEY_MUTED, false))
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _custom = MutableStateFlow(customFiles())
    /** event -> your file's name, for events that use your own sound. */
    val custom: StateFlow<Map<String, String>> = _custom.asStateFlow()

    fun apply(sounds: SoundTokens) {
        tokens = sounds
        reload()
    }

    fun setMuted(value: Boolean) {
        prefs.edit { putBoolean(KEY_MUTED, value) }
        _muted.value = value
    }

    /** Quiet the focus "move" sound briefly, e.g. while a screen restores focus on its own. */
    fun quietMoves(ms: Long = 400) {
        suppressMoveUntil = SystemClock.uptimeMillis() + ms
    }

    fun movesQuiet(): Boolean = SystemClock.uptimeMillis() < suppressMoveUntil

    fun play(event: String, force: Boolean = false) {
        if (!force && (!tokens.enabled || _muted.value)) return
        if (!force && event == "move" && SystemClock.uptimeMillis() < suppressMoveUntil) return
        val id = ids[event] ?: return
        val perEvent = if (_custom.value.containsKey(event)) tokens.events[event]?.volume ?: 1f else 1f
        val volume = (tokens.volume * perEvent).coerceIn(0f, 1f)
        pool?.play(id, volume, volume, 1, 0, 1f)
    }

    /** Copies an audio file you picked into app storage and uses it for [event]. */
    fun importCustom(event: String, uri: Uri): Result<String> = runCatching {
        require(event in SOUND_EVENTS)
        val name = displayName(uri) ?: "sound"
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it in AUDIO_EXTENSIONS } ?: "ogg"
        customDir.listFiles()?.filter { it.nameWithoutExtension == event }?.forEach { it.delete() }
        val target = File(customDir, "$event.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        } ?: error("Couldn't open that file.")
        require(target.length() in 1..MAX_CUSTOM_BYTES) { target.delete(); "Pick a short sound (under 5 MB)." }
        prefs.edit { putString("name_$event", name) }
        _custom.value = customFiles()
        reload()
        name
    }

    fun clearCustom(event: String) {
        customDir.listFiles()?.filter { it.nameWithoutExtension == event }?.forEach { it.delete() }
        prefs.edit { remove("name_$event") }
        _custom.value = customFiles()
        reload()
    }

    private fun customFiles(): Map<String, String> =
        customDir.listFiles().orEmpty()
            .filter { it.nameWithoutExtension in SOUND_EVENTS }
            .associate { it.nameWithoutExtension to (prefs.getString("name_${it.nameWithoutExtension}", null) ?: it.name) }

    private fun reload() {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                val newPool = SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .build()
                val newIds = HashMap<String, Int>()
                for (event in SOUND_EVENTS) {
                    val file = customDir.listFiles()?.firstOrNull { it.nameWithoutExtension == event }
                        ?: tokens.events[event]?.let { spec ->
                            File(renderDir, "$event-${spec.hashCode().toUInt()}.wav").apply {
                                if (!exists()) writeBytes(Synth.wav(Synth.render(spec)))
                            }
                        }
                        ?: continue
                    newIds[event] = newPool.load(file.path, 1)
                }
                val old = pool
                pool = newPool
                ids = newIds
                old?.release()
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private companion object {
        const val KEY_MUTED = "muted"
        const val MAX_CUSTOM_BYTES = 5L * 1024 * 1024
        val AUDIO_EXTENSIONS = setOf("ogg", "wav", "mp3", "m4a", "aac", "flac", "opus")
    }
}

/** Sound plus the theme's haptic for each interface event. */
class AppFeedback(
    private val engine: SoundEngine,
    private val view: View,
    private val haptics: HapticTokens,
) : UiFeedback {
    override fun emit(event: UiEvent) {
        if (event == UiEvent.Move && engine.movesQuiet()) return
        engine.play(event.key)
        if (!haptics.enabled) return
        val constant = when (haptics.events[event.key]) {
            "light" -> HapticFeedbackConstants.CLOCK_TICK
            "medium" -> HapticFeedbackConstants.KEYBOARD_TAP
            "heavy" -> HapticFeedbackConstants.LONG_PRESS
            else -> return
        }
        view.performHapticFeedback(constant)
    }
}
