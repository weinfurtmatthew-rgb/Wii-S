package app.wiis.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Every visual and audio decision in the launcher, as data.
 *
 * The same structure is edited in the Design Workbench and stored as `theme.json`. The built-in
 * proposal lives in `assets/theme/default.json`; the defaults below mirror it field for field so a
 * theme file that omits a key still renders (a unit test keeps the two identical).
 *
 * Nothing here is final until approved in the workbench: see docs/DESIGN.md.
 */
@Immutable
@Serializable
data class ThemeSpec(
    val schema: Int = 3,
    val name: String = "Draft 3",
    val layout: LayoutTokens = LayoutTokens(),
    val colors: ColorTokens = ColorTokens(),
    val glass: GlassTokens = GlassTokens(),
    val systemColors: Map<String, String> = DEFAULT_SYSTEM_COLORS,
    val shape: ShapeTokens = ShapeTokens(),
    val spacing: SpacingTokens = SpacingTokens(),
    val type: TypeTokens = TypeTokens(),
    val shelf: ShelfTokens = ShelfTokens(),
    val tile: TileTokens = TileTokens(),
    val hero: HeroTokens = HeroTokens(),
    val systemTile: SystemTileTokens = SystemTileTokens(),
    val motion: MotionTokens = MotionTokens(),
    val header: HeaderTokens = HeaderTokens(),
    val nav: NavTokens = NavTokens(),
    val hints: HintTokens = HintTokens(),
    val companion: CompanionTokens = CompanionTokens(),
    val sounds: SoundTokens = SoundTokens(),
    val haptics: HapticTokens = HapticTokens(),
) {
    fun systemColor(systemId: String): Color =
        systemColors[systemId].toColorOr(colors.accent.toColorOr(Color(0xFFFF6B4A)))

    companion object {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true; prettyPrint = true }
        fun parse(text: String): ThemeSpec = json.decodeFromString(serializer(), text)
        fun encode(spec: ThemeSpec): String = json.encodeToString(serializer(), spec)
    }
}

@Immutable @Serializable
data class LayoutTokens(
    /**
     * "hero" (Jump back in card + systems grid + recent row), "shelves" (one row per system) or
     * "grid" (one vertical grid with headers).
     */
    val games: String = "hero",
    val showRecent: Boolean = true,
    val recentCount: Int = 12,
    /** "registry" (fixed console order), "alphabetical", "mostGames". */
    val systemOrder: String = "alphabetical",
    /** Where a row scrolls the focused tile: "edge" (minimal), "start", "center". */
    val scrollAnchor: String = "center",
    /** Hero layout: width of the Jump back in card, in dp. */
    val heroWidth: Int = 360,
    /** Hero layout: systems grid beside the card. */
    val systemColumns: Int = 3,
    val systemRows: Int = 2,
    val systemTileHeight: Int = 96,
    /** Hero layout: size of the square game tiles in the recent row. */
    val recentTileSize: Int = 96,
    /** Size of game tiles inside a system's game grid. */
    val gridTileSize: Int = 132,
    /** A line under the recent row naming the focused game or system. */
    val focusCaption: Boolean = true,
)

@Immutable @Serializable
data class ColorTokens(
    /**
     * "art" (the focused game's art, blurred and dimmed), "solid", "gradient" (backgroundTop →
     * background) or "glow" (gradient plus two soft light pools). "art" falls back to the gradient
     * when a game has no art yet.
     */
    val backgroundStyle: String = "art",
    /** Blur radius for the art background, in dp. */
    val artBlur: Int = 36,
    /** How much the art background is darkened: 0 = untouched, 1 = black. */
    val artDim: Float = 0.5f,
    val background: String = "#15141E",
    val backgroundTop: String = "#1D1A2C",
    val glowA: String = "#1A0A52",
    val glowB: String = "#3FA7F5",
    val glowStrength: Float = 0.16f,
    val surface: String = "#221F33",
    val tile: String = "#2A2740",
    val tileFocused: String = "#353052",
    val text: String = "#FFFFFF",
    val textMuted: String = "#D9D4E6",
    val accent: String = "#C2C2C2",
    val focusRing: String = "#FFD24A",
    val focusGlow: String = "#FFD24A",
    val focusGlowStrength: Float = 0f,
    val tabActiveFill: String = "#999999",
    val tabActiveText: String = "#FFFFFF",
    val tabIdleText: String = "#3B3549",
    val hintBadge: String = "#1E1B27",
    val hintBadgeText: String = "#FFFFFF",
    val messageBar: String = "#2E2A44",
)

/** Frosted-glass panels and tiles that sit over the art background. */
@Immutable @Serializable
data class GlassTokens(
    val fill: String = "#FFFFFF",
    /** Fill opacity: 1 = solid, lower lets the art show through. */
    val opacity: Float = 0.82f,
    /** Blur of whatever is behind the glass, in dp. */
    val blur: Int = 24,
    val border: String = "#FFFFFF",
    val borderOpacity: Float = 0.9f,
    val borderWidth: Float = 1.5f,
    /** Text on glass. */
    val text: String = "#1E1B27",
    val textMuted: String = "#5E5870",
    val radius: Int = 22,
)

val DEFAULT_SYSTEM_COLORS: Map<String, String> = linkedMapOf(
    "recent" to "#008CB4",
    "nes" to "#E4483C",
    "snes" to "#8C6CF0",
    "n64" to "#2FB86B",
    "gb" to "#9CB83A",
    "gbc" to "#C653D9",
    "gba" to "#5B6CFF",
    "nds" to "#3FA7F5",
    "n3ds" to "#EC4A63",
    "gc" to "#7C5CFF",
    "wii" to "#56C6F2",
    "wiiu" to "#22A6C8",
    "switch" to "#FF4B57",
    "genesis" to "#2F6FE4",
    "psx" to "#B4B8C6",
    "ps2" to "#3D5BD9",
    "psp" to "#7C8796",
    "xbox360" to "#7BC043",
    "steam" to "#5B8DEF",
)

@Immutable @Serializable
data class ShapeTokens(
    val tileWidth: Int = 196,
    val tileHeight: Int = 196,
    val tileRadius: Int = 22,
    val tileBorderWidth: Int = 0,
    val tileBorderColor: String = "#3A3552",
    val focusRingWidth: Int = 0,
    val focusRingGap: Int = 3,
    /** "none", "soft" (blurred drop shadow) or "hard" (solid offset block, retro). */
    val shadow: String = "soft",
    val shadowColor: String = "#000000",
    val shadowOffset: Int = 6,
    /** Coloured band naming the system: "none", "top", "bottom", "left". */
    val systemStrip: String = "none",
    val systemStripSize: Int = 6,
    /** "pill" (filled), "underline" or "text". */
    val tabStyle: String = "pill",
    val tabRadius: Int = 999,
    val cardRadius: Int = 24,
    val dialogRadius: Int = 28,
    val badgeRadius: Int = 999,
)

@Immutable @Serializable
data class SpacingTokens(
    val screenPadding: Int = 28,
    val headerGap: Int = 16,
    val shelfGap: Int = 22,
    val tileGap: Int = 18,
    val shelfTitleGap: Int = 10,
)

@Immutable @Serializable
data class TypeTokens(
    /** Google Fonts family names; "System" uses the device font. */
    val displayFont: String = "Fredoka",
    val bodyFont: String = "Nunito",
    val wordmarkSize: Int = 30,
    val wordmarkWeight: Int = 700,
    val tabSize: Int = 17,
    val tabWeight: Int = 700,
    val shelfTitleSize: Int = 14,
    val shelfTitleWeight: Int = 800,
    /** "upper", "title" (as written) or "lower". */
    val shelfTitleCase: String = "upper",
    val shelfTitleTracking: Float = 1.4f,
    val tileTitleSize: Int = 16,
    val tileTitleWeight: Int = 700,
    val labelSize: Int = 12,
    val labelWeight: Int = 700,
    val clockSize: Int = 18,
    val clockWeight: Int = 600,
)

@Immutable @Serializable
data class ShelfTokens(
    /** Shelf title colour: "system", "text" or "muted". */
    val titleColor: String = "system",
    /** Marker before the shelf title: "dot", "bar" or "none". */
    val marker: String = "bar",
    val showCount: Boolean = true,
)

@Immutable @Serializable
data class TileTokens(
    /** "full" (art fills the tile), "card" (art above a title strip) or "title" (no art). */
    val artStyle: String = "full",
    /** "contain" (whole cover visible) or "cover" (fill and crop). */
    val artFit: String = "cover",
    /** Title text on art tiles: "always", "focused" or "never". */
    val titleVisibility: String = "never",
    /** System name badge: "always", "recent" (only in Recently played) or "never". */
    val systemBadge: String = "never",
    val showFileType: Boolean = false,
    /** Tile without art: "system" (system-colour panel) or "plain" (tile colour). */
    val placeholder: String = "system",
    /** Small console mark in the top-right corner: "line" (line drawing), "badge" (short name) or "none". */
    val consoleIcon: String = "line",
    val consoleIconSize: Int = 22,
    /** Always-on ring in the console's colour, in dp (0 = none). */
    val systemRing: Int = 3,
)

/** The "Jump back in" card for the last-played game. */
@Immutable @Serializable
data class HeroTokens(
    val prompt: String = "Jump back in",
    val showPlaytime: Boolean = true,
    val showSystem: Boolean = true,
)

/** System tiles in the grid beside the hero card. */
@Immutable @Serializable
data class SystemTileTokens(
    /** "line" (large console line drawing + name) or "name" (name only). */
    val style: String = "line",
    val showCount: Boolean = true,
    /** How strongly the console colour washes over the glass, 0..1. */
    val tint: Float = 0.5f,
)

@Immutable @Serializable
data class MotionTokens(
    val focusScale: Float = 1.08f,
    val pressScale: Float = 0.94f,
    /** Spring stiffness (Compose units: 50 very soft … 1500 snappy). */
    val stiffness: Float = 400f,
    /** Spring damping ratio: below 1 overshoots (bouncy), 1 settles without overshoot. */
    val damping: Float = 0.55f,
    val focusLift: Int = 4,
    val idleBob: Boolean = true,
    val idleBobAmplitude: Int = 2,
    val idleBobPeriodMs: Int = 1800,
    /** Between tabs: "none", "fade" or "slide". */
    val tabTransition: String = "slide",
    val tabTransitionMs: Int = 220,
    val reduceMotion: Boolean = false,
)

@Immutable @Serializable
data class HeaderTokens(
    val showWordmark: Boolean = true,
    val wordmark: String = "Wii-S",
    val clock24h: Boolean = false,
    val showDate: Boolean = false,
    val tabGames: String = "Home",
    val tabApps: String = "Apps",
    val tabSystem: String = "System",
    /** Clock, Wi-Fi and battery together in a pill at the top right. */
    val statusPill: Boolean = true,
    /** Small icons in the tabs. */
    val tabIcons: Boolean = true,
)

/** Top-screen navigation: a glass dock along the bottom (with app shortcuts) or tabs in the header. */
@Immutable @Serializable
data class NavTokens(
    /** "dock" (along the bottom of the top screen) or "tabs" (in the header). */
    val style: String = "dock",
    /** App shortcuts after the Home / Apps / System buttons in the dock. */
    val apps: Int = 5,
    /** Labels on the navigation buttons: "active", "all" or "none" (icons only). */
    val labels: String = "active",
    /** With the dock: the caption naming the focused item sits in the "header" or "aboveDock". */
    val captionPlace: String = "header",
)

@Immutable @Serializable
data class HintTokens(
    val show: Boolean = true,
    /** "start", "center" or "end". */
    val align: String = "end",
    /** "circle", "rounded" or "square". */
    val badgeShape: String = "circle",
    val colorA: String = "#1E1B27",
    val colorB: String = "#1E1B27",
    val colorX: String = "#1E1B27",
    val colorY: String = "#1E1B27",
    val colorOther: String = "#1E1B27",
)

@Immutable @Serializable
data class CompanionTokens(
    /**
     * Bottom screen: "artDock" (focused art full screen + app dock), "art" (large cover + details),
     * "info" (details card) or "minimal" (title only).
     */
    val layout: String = "art",
    /** "inherit" (same background as the top screen) or "color". */
    val background: String = "inherit",
    val backgroundColor: String = "#15141E",
    val showPlaytime: Boolean = true,
    val showLastPlayed: Boolean = true,
    val showEmulator: Boolean = true,
    val showClock: Boolean = true,
    val showHints: Boolean = false,
    /** Dock of Android apps on the bottom screen. */
    val dock: Boolean = true,
    val dockApps: Int = 5,
    /** App-drawer button at the start of the dock. */
    val showDrawer: Boolean = true,
)

@Immutable @Serializable
data class SoundTokens(
    val enabled: Boolean = true,
    val volume: Float = 0.7f,
    val events: Map<String, SynthSpec> = DEFAULT_SOUNDS,
)

/**
 * A short synthesised sound. Rendered identically by the workbench (Web Audio) and the launcher
 * ([Synth]): oscillator stepping through [notes], linear attack, hold, exponential decay,
 * optional pitch sweep, noise mix and one-pole low-pass filter.
 */
@Immutable @Serializable
data class SynthSpec(
    /** "sine", "triangle", "square" or "saw". */
    val wave: String = "triangle",
    /** Frequencies in Hz, played one after another. */
    val notes: List<Float> = listOf(880f),
    val noteMs: Int = 30,
    val attackMs: Int = 2,
    val decayMs: Int = 60,
    /** Pitch glide over the whole sound, in semitones (negative falls). */
    val sweep: Float = 0f,
    /** Low-pass cutoff in Hz. */
    val lowpass: Float = 8000f,
    /** 0 = pure tone, 1 = pure noise. */
    val noise: Float = 0f,
    val volume: Float = 0.5f,
)

val SOUND_EVENTS = listOf("move", "select", "back", "tab", "launch", "error", "bump")

val DEFAULT_SOUNDS: Map<String, SynthSpec> = linkedMapOf(
    "move" to SynthSpec("sine", listOf(880f, 988f), 18, 2, 45, 0f, 7000f, 0f, 0.32f),
    "select" to SynthSpec("triangle", listOf(659f, 988f), 45, 2, 90, 0f, 8000f, 0f, 0.5f),
    "back" to SynthSpec("triangle", listOf(740f, 494f), 45, 2, 80, 0f, 6000f, 0f, 0.45f),
    "tab" to SynthSpec("square", listOf(523f, 784f), 30, 1, 60, 0f, 3200f, 0f, 0.28f),
    "launch" to SynthSpec("triangle", listOf(523f, 659f, 784f, 1047f), 70, 3, 220, 0f, 9000f, 0f, 0.55f),
    "error" to SynthSpec("square", listOf(233f, 196f), 90, 2, 120, 0f, 2200f, 0f, 0.35f),
    "bump" to SynthSpec("sine", listOf(150f), 20, 1, 50, -6f, 2000f, 0.15f, 0.5f),
)

@Immutable @Serializable
data class HapticTokens(
    val enabled: Boolean = true,
    /** Per event: "off", "light", "medium" or "heavy". */
    val events: Map<String, String> = linkedMapOf(
        "move" to "light",
        "select" to "medium",
        "back" to "light",
        "tab" to "light",
        "launch" to "heavy",
        "error" to "heavy",
        "bump" to "light",
    ),
)

/** "#RRGGBB" or "#AARRGGBB"; anything unparsable falls back. */
fun String?.toColorOr(fallback: Color): Color {
    val hex = this?.trim()?.removePrefix("#") ?: return fallback
    val value = hex.toLongOrNull(16) ?: return fallback
    return when (hex.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> fallback
    }
}
