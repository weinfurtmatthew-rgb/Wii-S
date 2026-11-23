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
    val schema: Int = 4,
    val name: String = "Draft 4",
    /** "system" follows Android's dark theme; "light" or "dark" fixes one. The Thor can override it. */
    val appearance: String = "system",
    val layout: LayoutTokens = LayoutTokens(),
    /** Light palette (used unless dark mode is on). */
    val colors: ColorTokens = ColorTokens(),
    val glass: GlassTokens = GlassTokens(),
    /** Dark palette: replaces the colours, glass colours, hint colours and shadow colour in dark mode. */
    val dark: DarkTokens = DarkTokens(),
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

    /**
     * Themes from before schema 4 have one palette: it becomes the dark palette too, so they look
     * the same in light and dark mode instead of picking up Draft 4's dark colours.
     */
    fun upgraded(): ThemeSpec = if (schema >= 4) this else copy(
        schema = 4,
        appearance = "light",
        dark = DarkTokens(colors = colors, glass = glass, hints = hints, shadowColor = shape.shadowColor),
    )

    /** This theme with its dark palette swapped in when [dark] is true. Layout, type and motion are shared. */
    fun resolve(dark: Boolean): ThemeSpec {
        if (!dark) return this
        val d = this.dark
        return copy(
            colors = d.colors,
            glass = glass.copy(
                fill = d.glass.fill, opacity = d.glass.opacity, border = d.glass.border,
                borderOpacity = d.glass.borderOpacity, text = d.glass.text, textMuted = d.glass.textMuted,
            ),
            hints = hints.copy(colorA = d.hints.colorA, colorB = d.hints.colorB, colorX = d.hints.colorX, colorY = d.hints.colorY, colorOther = d.hints.colorOther),
            shape = shape.copy(shadowColor = d.shadowColor),
        )
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true; prettyPrint = true }
        fun parse(text: String): ThemeSpec = json.decodeFromString(serializer(), text).upgraded()
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
     * "dots" (gradient, a fine dot grid and two soft pools of the focused item's colour), "art" (the
     * focused game's art, blurred and dimmed), "artLight" (the art washed with the background colour),
     * "solid", "gradient" (backgroundTop → background) or "glow" (gradient plus two soft light pools).
     */
    val backgroundStyle: String = "dots",
    /** Blur radius for the art background, in dp. */
    val artBlur: Int = 36,
    /** How much the art background is darkened: 0 = untouched, 1 = black. */
    val artDim: Float = 0.5f,
    val background: String = "#E9E8F0",
    val backgroundTop: String = "#F6F6F9",
    val glowA: String = "#1A0A52",
    val glowB: String = "#3FA7F5",
    /** Strength of the glow pools ("glow") or the colour pools ("dots"). */
    val glowStrength: Float = 0.2f,
    val surface: String = "#FFFFFF",
    val tile: String = "#F1F0F5",
    val tileFocused: String = "#E4E2EC",
    val text: String = "#23212D",
    val textMuted: String = "#5A5769",
    val accent: String = "#23212D",
    val focusRing: String = "#3FD39A",
    val focusGlow: String = "#3FD39A",
    val focusGlowStrength: Float = 0f,
    val tabActiveFill: String = "#23212D",
    val tabActiveText: String = "#FFFFFF",
    val tabIdleText: String = "#4E4B5E",
    val hintBadge: String = "#23212D",
    val hintBadgeText: String = "#FFFFFF",
    val messageBar: String = "#FFFFFF",
)

/** Frosted-glass panels and tiles that sit over the art background. */
@Immutable @Serializable
data class GlassTokens(
    val fill: String = "#FFFFFF",
    /** Fill opacity: 1 = solid, lower lets the art show through. */
    val opacity: Float = 1f,
    /** Blur of whatever is behind the glass, in dp. */
    val blur: Int = 24,
    val border: String = "#DCDAE5",
    val borderOpacity: Float = 1f,
    val borderWidth: Float = 1f,
    /** Text on glass. */
    val text: String = "#23212D",
    val textMuted: String = "#625F72",
    val radius: Int = 18,
)

/** The dark palette. Only colours live here; sizes, type and motion are shared with the light one. */
@Immutable @Serializable
data class DarkTokens(
    val colors: ColorTokens = DARK_COLORS,
    /** Only fill, opacity, border, borderOpacity, text and textMuted are used. */
    val glass: GlassTokens = DARK_GLASS,
    /** Only the button colours are used. */
    val hints: HintTokens = DARK_HINTS,
    val shadowColor: String = "#000000",
)

val DARK_COLORS = ColorTokens(
    background = "#15141C", backgroundTop = "#1D1C26", glowStrength = 0.14f,
    surface = "#22212C", tile = "#2A2935", tileFocused = "#35343F",
    text = "#ECEAF3", textMuted = "#A9A5B9", accent = "#ECEAF3",
    tabActiveFill = "#ECEAF3", tabActiveText = "#15141C", tabIdleText = "#B6B2C6",
    hintBadge = "#ECEAF3", hintBadgeText = "#15141C", messageBar = "#22212C",
)

val DARK_GLASS = GlassTokens(fill = "#23222D", border = "#383645", text = "#ECEAF3", textMuted = "#A9A5B9")

val DARK_HINTS = HintTokens(colorA = "#ECEAF3", colorB = "#ECEAF3", colorX = "#ECEAF3", colorY = "#ECEAF3", colorOther = "#ECEAF3")

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
    val tileRadius: Int = 18,
    val tileBorderWidth: Int = 0,
    val tileBorderColor: String = "#3A3552",
    val focusRingWidth: Int = 3,
    val focusRingGap: Int = 1,
    /** "none", "soft" (blurred drop shadow) or "hard" (solid offset block, retro). */
    val shadow: String = "soft",
    val shadowColor: String = "#4A4766",
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
    val headerGap: Int = 24,
    val shelfGap: Int = 22,
    val tileGap: Int = 18,
    val shelfTitleGap: Int = 10,
)

@Immutable @Serializable
data class TypeTokens(
    /** Google Fonts family names; "System" uses the device font. */
    val displayFont: String = "Poppins",
    val bodyFont: String = "Poppins",
    val wordmarkSize: Int = 26,
    val wordmarkWeight: Int = 700,
    val tabSize: Int = 15,
    val tabWeight: Int = 600,
    val shelfTitleSize: Int = 15,
    val shelfTitleWeight: Int = 600,
    /** "upper", "title" (as written) or "lower". */
    val shelfTitleCase: String = "title",
    val shelfTitleTracking: Float = 0f,
    val tileTitleSize: Int = 17,
    val tileTitleWeight: Int = 600,
    val labelSize: Int = 13,
    val labelWeight: Int = 500,
    val clockSize: Int = 16,
    val clockWeight: Int = 500,
)

@Immutable @Serializable
data class ShelfTokens(
    /** Shelf title colour: "system", "text" or "muted". */
    val titleColor: String = "muted",
    /** Marker before the shelf title: "dot", "bar" or "none". */
    val marker: String = "none",
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
    /**
     * Console mark on game tiles: "tab" (the frame's top-left corner grows into a tab with the console
     * drawn in white), "line" (line drawing on a glass disc, top right), "badge" (short name) or "none".
     */
    val consoleIcon: String = "tab",
    /** Size of the console drawing; the corner tab is 10 dp larger. */
    val consoleIconSize: Int = 18,
    /** Always-on frame in the console's colour, in dp (0 = none). */
    val systemRing: Int = 5,
)

/** The "Jump back in" card for the last-played game. */
@Immutable @Serializable
data class HeroTokens(
    val prompt: String = "Jump back in",
    val showPlaytime: Boolean = true,
    val showSystem: Boolean = false,
)

/** System tiles in the grid beside the hero card. */
@Immutable @Serializable
data class SystemTileTokens(
    /**
     * "xmb" (the system's own art faded behind one bold console drawing, no text), "line" (console
     * line drawing + name) or "name" (name only).
     */
    val style: String = "xmb",
    val showCount: Boolean = false,
    /** How strongly the console colour (and, for "xmb", the faded art) shows, 0..1. */
    val tint: Float = 0.6f,
)

@Immutable @Serializable
data class MotionTokens(
    val focusScale: Float = 1.08f,
    /** Big cards grow by at most this many dp on each side, so a focused card can't reach the header. */
    val focusGrowMax: Int = 8,
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
    /** The third section (System until 0.6.0, when the settings moved into a popup). */
    val tabCollections: String = "Collections",
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
    /**
     * With the dock: the focused item is named by a "nameplate" (a pill at the top, with the button
     * hints beside the dock), a caption in the "header", or a caption "aboveDock".
     */
    val captionPlace: String = "nameplate",
    /** The dark pill between Home, Apps and System: "glide" (slides and stretches) or "jump". */
    val pillMotion: String = "glide",
    /** The light highlight slides from item to item as focus moves along the dock. */
    val focusGlide: Boolean = true,
    /** The L1/R1 badges dip like keys when the shoulder buttons are pressed. */
    val badgePress: Boolean = true,
    /** The dock rises into place when Home opens or comes back. */
    val riseIn: Boolean = true,
    /** How long the dock's motion takes; it eases in and out without bouncing. */
    val motionMs: Int = 250,
)

@Immutable @Serializable
data class HintTokens(
    val show: Boolean = true,
    /** "start", "center" or "end". */
    val align: String = "end",
    /** "circle", "rounded" or "square". */
    val badgeShape: String = "circle",
    val colorA: String = "#23212D",
    val colorB: String = "#23212D",
    val colorX: String = "#23212D",
    val colorY: String = "#23212D",
    val colorOther: String = "#23212D",
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
    /** Pages beside the details, swiped by touch: game or system stats, and RetroAchievements. */
    val showStats: Boolean = true,
    val showAchievements: Boolean = true,
    /** How the pages are shown: "tabs" (labels at the top) or "dots" (dots at the bottom). */
    val pageStyle: String = "tabs",
    /** Stats page: a line of the last 12 weeks under each number. */
    val sparklines: Boolean = true,
    /** The play-time chart's first range: "week", "12w" (12 weeks) or "year" (12 months). */
    val chartRange: String = "12w",
    /** Achievements page: a progress ring beside the list (otherwise a bar under the title). */
    val achievementsRing: Boolean = true,
    /** Achievements page: total time, sessions and last played beside the list. */
    val achievementsPlaytime: Boolean = true,
    /** One colour per number on the Stats page; the first is also the chart's. */
    val statColors: List<String> = listOf("#2F86F6", "#1FA968", "#8A5CF6", "#F28C28"),
    /** RetroAchievements rarity: gold under 1 in 10 players, silver under 1 in 3, bronze the rest. */
    val trophyGold: String = "#E9AE25",
    val trophySilver: String = "#A3AEBE",
    val trophyBronze: String = "#C9773D",
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
