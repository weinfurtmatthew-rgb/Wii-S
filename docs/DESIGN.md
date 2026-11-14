# Design decisions

**Rule (owner's instruction, 2026-11-13):** the owner has complete control over the design. Every detail, however small, is theirs to decide. Values I put in are proposals until they're approved.

## How it works

- **Theme as data.** Every visual and audio detail is a token in one theme file (`theme.json`, schema 1). The launcher draws only from it; nothing visual is hard-coded in screens. The proposal lives in `core/designsystem/src/main/assets/theme/default.json`.
- **Design Workbench** (`design/workbench.html`, published privately at https://claude.ai/artifact/VPcG1tqcsKakm9WAkhXM5s). It shows a live mockup of both Thor screens at true proportions (top 833×468 dp, bottom 538×468 dp) with all 143 details editable, and each detail has an **Approve** button.
  - Changing an approved value un-approves it.
  - Notes to Claude can be left per group.
  - Choices save to the workbench's store, where I read them.
  - Motion uses the same spring maths as Compose.
  - Sounds come from the same synthesiser as the launcher. A unit test checks the two produce the same samples.
- **On-device preview.** Export `theme.json` from the workbench, then on the Thor go to System → Theme → Import. Any Google Fonts family downloads once.
- **Baking.** When details are approved, I copy those exact values into `default.json`, record them below, and ship a build. Values that aren't approved stay as proposals and stay listed as open.
- **Design Outline** (`design/outline.html`, published privately at https://claude.ai/artifact/LRMxPnLTb2qKpCQAkFTU19). A numbered, read-only summary of the current draft: both screens, palette with contrast ratios, type scale, tile anatomy, spacing, motion curve, sounds. Tweaks are made there with the Tweak buttons or by item number in chat. Rebuild with `design/tools/`.
- **Custom sounds** are a device setting (System → Sounds → Use my file…), not part of the theme. They override the theme's sound for that event on that device.

## What's not a token yet

These are fixed for now. Each can become a token if you want control over it:

- The size and padding of System-screen panels.
- The bottom screen's cover panel width (220 dp).
- The scrim gradient behind titles on art.
- The glow blur radius (22 dp).
- Dialog title size.

## Decision register

| Date | Detail | Value | Status |
|---|---|---|---|
| 2026-11-13 | Overall mood | Hybrid: modern dark layout, warm, chunky, colourful tiles, playful sounds | **Approved** (direction only) |
| 2026-11-13 | All 143 workbench details | Proposal 1 (`default.json`) | Proposed, awaiting review |
| 2026-11-13 | Draft 1 (yours, `design/themes/draft-1-2026-11-13.json`) | Focused tile centred in its row (`layout.scrollAnchor: center`); bar before row titles (`shelf.marker: bar`); sine wave for the move sound (`sounds.events.move.wave: sine`) | **Draft, open to change.** Not approved, not built into the launcher |
| 2026-11-14 | Base look (from your inspiration pictures) | Frosted light panels and tiles over the focused game's art, blurred and darkened | **Your answer** (direction) |
| 2026-11-14 | Top screen | "Jump back in" card for the last game + systems grid beside it + recent games row | **Your answer** (direction) |
| 2026-11-14 | Bottom screen | The focused game's art with an Android app dock and app-drawer button | **Your answer** (direction) |
| 2026-11-14 | Tiles | Full box art in a thin ring of the console's colour, small console mark top right | **Your answer** (direction) |
| 2026-11-14 | Focus | Grow and bounce only; no focus ring, no glow | **Your answer** (direction) |
| 2026-11-14 | Console marks | Original line drawings of console shapes (no makers' logos) | **Your answer** (direction) |
| 2026-11-14 | Draft 2 (`design/themes/draft-2-2026-11-14.json`, now the proposal in `default.json`) | Implements the answers above, keeps Draft 1's three changes; every other value is my proposal | **Draft, open to change.** Not approved |
| 2026-11-14 | Your edits to Draft 2 (`design/themes/draft-2-your-edits-2026-11-14.json`) | Systems A to Z; grey accent `#C2C2C2`; grey active tab `#999999`; Recently played `#008CB4`; glow colour `#1A0A52` (shows on the bottom screen in this layout); bottom screen "Large cover + details" | **Draft, open to change.** Not approved. Note: white tab text on `#999999` is 2.9:1 |
| 2026-11-14 | Active tab fill `#999999` with white text | Kept as is, despite 2.9:1 contrast | **Your answer** |
| 2026-11-14 | Navigation | The dock becomes the top screen's navigation, along the bottom: Home, Apps, System (L1/R1) + app shortcuts; tabs leave the header | **Your answer** (direction) |
| 2026-11-14 | Caption placement | In the header between wordmark and status pill (alternative: just above the dock) | Proposed |
| 2026-11-14 | Draft 3 (`design/themes/draft-3-2026-11-14.json`, now the proposal in `default.json`) | Draft 2 + your six edits + the navigation dock | **Draft, open to change.** Not approved |
| 2026-11-14 | Draft 3 built into the launcher (0.3.0-i3) | Every value from `default.json` (= Draft 3). Screenshot tests compare the device rendering with the workbench | **Built as a draft.** Still not approved |

### Proposed, not in the workbench (0.3.0-i3)

Building Draft 3 on the device needed a few details the workbench doesn't show. Each is my proposal and yours to change:

| Detail | What 0.3.0 does | Status |
|---|---|---|
| Jump back in before anything has been played | Shows the first game A to Z, with the same "Jump back in" prompt; the recent row stays hidden | Proposed |
| Order inside a system | Games A to Z (All games: every game A to Z) | Proposed |
| All games tile squares | Colours of the systems you played last, then your biggest systems not shown yet | Proposed |
| Dock apps | Default: your browser, then video, Play Store, music, files, chat apps if installed, then other apps A to Z (emulators left out). **Y on a dock app** opens a picker to change that spot; the caption shows "Y Change" | Proposed |
| Bottom screen when a system is highlighted | "Large cover + details" shows the idle message, as the workbench does. Alternative: show the system's console drawing and game count | Proposed; your call |
| Background change between games | Instant, as in the workbench. A short fade is possible | Proposed |
| Game counts | "1 game" / "6 games" | Proposed |
| System screen | Keeps its dark panels from 0.2.0; the workbench sketches it frosted | Proposed |
| Apps grid | A focused tile at the left edge isn't cut off (the workbench mock clips it) | Proposed |
| Emulator and dock-app pickers | Dark dialogs as in 0.2.0 (not drawn in the workbench) | Proposed |

### Draft 4 directions (2026-11-14, from your iiSU and Cocoon references)

Three whole looks to compare with Draft 3, in the workbench under **Try a look** and as files in `design/themes/look-*-2026-11-14.json`. Review page: https://claude.ai/artifact/RCwi4oEs8crTPdWceYL6xT

| Look | What changes | Status |
|---|---|---|
| A · Draft 3, calmer | Dark art kept (veil 62%), Lexend, 14 dp minimum text, sentence-case caption, XMB system icons, corner tabs, no count badges | Proposed |
| B · Light (Cocoon + iiSU) | Light dot-grid background with a soft colour glow, solid white surfaces, ink text, Poppins, nameplate + hints beside the dock, mint focus ring and glow, firm spring (no bounce, no bob), XMB icons, corner tabs | Proposed (my pick) |
| C · Light over your art | B over the focused game's art washed light, Outfit | Proposed |

New setting values (workbench only until you choose; the launcher draws them once a look is picked): `tile.consoleIcon: "tab"`, `systemTile.style: "xmb"`, `nav.captionPlace: "nameplate"`, `colors.backgroundStyle: "dots"` and `"artLight"`. Don't import a look file on the Thor before then: 0.3.0 would draw a light look's text on its dark fallback background.

