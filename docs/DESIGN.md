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

### Draft 4 (2026-11-14): your choices from the look review

| Detail | Value | Status |
|---|---|---|
| Direction | Look B (light, Cocoon + iiSU), with a light **and** a dark mode | **Your answer** |
| Corner tab on game cards (iiSU) | Yes, top left, console drawn in white | **Your answer** |
| XMB system icons | Yes; no count or name on the tile, the nameplate names it | **Your answer** |
| Nameplate at the top, hints beside the dock | Yes | **Your answer** |
| Focus motion | Grow and bounce, as in Draft 3 (stiffness 400, damping 0.55, lift 4, idle bob) | **Your answer** |
| Focus ring | Refined instead of removed: 3 dp mint, 1 dp gap, no glow | Proposed (you said remove or refine) |
| Ring can't reach the title pill | Big cards grow by at most 8 dp a side (new setting `motion.focusGrowMax`); gap below the header 24 dp (was 16); compact title pill | Proposed |
| Typeface | Poppins | **Your answer** |
| Accent on light | Ink #23212D | **Your answer** |
| Dark palette | Background #15141C → #1D1C26, surfaces #23222D, text #ECEAF3 / #A9A5B9, light active tab and hint badges, mint ring kept | Proposed |
| Light or dark | Follows Android's dark theme (setting `appearance`); System → Theme → Appearance on the Thor overrides | Proposed |
| Bottom screen pages | Details, Stats, RetroAchievements, swiped by touch, labels at the top (`companion.pageStyle`, or dots) | **Your request**; page designs proposed |
| Stats page | Total time, sessions, average, longest, first played, this week Monday to Sunday with today in the console colour; for a console: total, games played, top three, this week | Proposed |
| Achievements page | Unlocked count, points and a progress bar, then each achievement (earned first, locked dimmed with RetroAchievements' grey badge); for a console: every game with progress, most recent first | Proposed |
| Draft 4 | All of the above, now the proposal in `default.json` (`design/themes/draft-4-2026-11-14.json`) | **Draft, open to change** |

### Bottom-screen Stats and Achievements from your two mocks (2026-11-15)

Your answers: tabs on top with Stats on one screen, gold/silver/bronze by rarity, and all four extras. The two styles are drawn in the workbench to compare before either goes into the launcher: **option 1** keeps Draft 4's calm dark, **option 3** is the neon dark ("Try a look" → "Draft 4 · neon dark (option 3)", with Dark mode ticked). Light mode is the same in both.

| Detail | Value | Status |
|---|---|---|
| Layout | Page tabs with icons across the top (Details, Stats, Achievements); Stats fits one screen; the achievement list scrolls | **Your answer** |
| Trophies | By rarity on RetroAchievements: gold if fewer than 1 in 10 players have it, silver under 1 in 3, bronze the rest | **Your answer**; the thresholds are proposed |
| Extras | Trend line under each number, progress ring, play time on Achievements, chart range switch | **Your answer** |
| Style | Option 1, calm: Draft 4's dark, no glow | **Your answer**; built into the launcher in 0.5.0 |
| Stats for a game | Total time, sessions, average session, average per week; beside the chart, recent sessions | Proposed |
| Stats for a console | Total time, games played, average per week, plays per week; beside the chart, Top games with covers and bars in each game's console colour | Proposed |
| Big numbers | Hours with one decimal ("3.2 h"), minutes under an hour ("48 min"), as in the mock | Proposed |
| Colour per number | Blue #2F86F6, green #1FA968, purple #8A5CF6, orange #F28C28 (the chart uses the blue) | Proposed |
| Trophy colours | Gold #E9AE25, silver #A3AEBE, bronze #C9773D | Proposed |
| Chart | Bars with dashed guides; the current week (or day, or month) in full colour, the rest at half; opens on the last 12 weeks; a menu switches to this week or the last 12 months | Proposed |
| Achievements for a game | Cover, title, RetroAchievements tag, trophy counts; All / Locked / Unlocked; each row: badge, name, two lines of description, then "Unlocked" with date and time, or "Locked" with how many players have it; ring in the console colour; play time box above it | Proposed |
| Achievements for a console | Console tile, mastered and beaten counts; All / In progress / Finished; each game with a progress bar, its award and last unlock; ring over every game played | Proposed |
| Left out from the mocks | The side rail, A/B hints and selection outline (touch-only screen), the repeated percentage bar, the info footer | Proposed |
| Neon dark (option 3) | Navy #090D24 → #121A45, cards #121937 with glowing blue edges, electric-blue active items #2F7BFF with glow, cyan focus ring #4CC3FF with glow, a blue-to-magenta line around both screens | To compare |

New settings the launcher draws from 0.5.0: `companion.sparklines`, `companion.chartRange`, `companion.achievementsRing`, `companion.achievementsPlaytime`, `companion.statColors`, `companion.trophyGold/Silver/Bronze`. The glow settings (`colors.activeGlow`, `edgeGlow`, `frameGlow`) and the neon look stay in the workbench only, for reference: you chose calm, so the launcher doesn't draw glows.

### Bottom-screen cover and dock motion (2026-11-15)

| Detail | Value | Status |
|---|---|---|
| Bottom-screen cover | No panel around it: the cover at its own shape with the console-colour outline and corner tab, and the tiles' resting shadow. Without art, a card in the console's colour with the title | **Your answer** (outline + corner tab) |
| Dock pill | Glides and stretches between Home, Apps and System while the labels fold and unfold (`nav.pillMotion`) | **Your answer** |
| Dock motion | Smooth, no bounce, 250 ms (`nav.motionMs`) | **Your answer**; the duration is proposed |
| L1/R1 badges | Dip like keys when pressed: 84% size, 1.5 dp down, 220 ms (`nav.badgePress`) | **Your answer**; the values are proposed |
| Focus highlight | The light highlight glides along the dock; around app icons it shows as a halo 4 dp out (`nav.focusGlide`) | **Your answer**; the halo is proposed |
| Dock rise | Rises 24 dp into place when Home opens or you come back (`nav.riseIn`) | **Your answer**; the distance is proposed |
| Dock slide | When the hints beside it change width, the dock slides to its new place instead of jumping | Replaced in 0.5.4: the dock no longer moves |
| Dock items | No longer grow on focus: the gliding highlight shows it | Proposed |

### Whole rows, a fixed dock and pinned games (2026-11-17, from your photos)

| Detail | Value | Status |
|---|---|---|
| Overflow | Only rows that fit completely are drawn, nothing cut and no sliver (or shadow) of the next row; moving past the last row scrolls one whole row in. Systems stay 3 × 2, the third row slides in when you move down. Also the Apps grid, a system's games and the pinned row | **Your answer** (whole rows only) |
| System pages | Whole cards only: a card that doesn't fit waits below until focus moves into it, then the page scrolls so it sits at the top. A card taller than the page (the Report text, the RetroAchievements sign-in) can't fit whole and stays cut | Proposed, how your answer applies to the settings cards |
| Focus near hidden rows | From outside a grid, only the rows it shows take focus, so Up from the pinned row or the dock never lands on a row out of sight | Proposed |
| A system's games | Covers shrink from the theme's 132 dp just enough for two whole rows: about 121 dp on the Thor (`layout.gridTileSize` stays the largest size) | **Your report** (one row was an issue); the size rule is proposed |
| Dock place | Its left edge lines up with the content above and never moves; the hints follow it, 18 dp after the dock and 14 dp apart | **Your answer** (dock fixed left); the gaps are proposed |
| Hints that don't fit | Left out whole from the last one back (in a system's games, "B Back" goes first; the B badge stays in the header) | Proposed |
| Bottom row | Games you pin, newest first; no limit (it scrolls a whole tile at a time). Jump back in still shows the last game played | **Your answer** (only pins); the order is proposed |
| Pinning | X on any game (Jump back in, a system's games, the row itself) pins or unpins it; the hints read "X Pin" or "X Unpin". The select sound when pinning, the back sound when unpinning; no message | **Your answer** (X); the sounds are proposed |
| Unpinning from the row | Focus moves to the game that takes its place (or the one before it), or to Jump back in when the row empties | Proposed |
| Empty row | One glass card at the row's height: an X badge, "Pin your games here", "Press X on any game to add it to this row." | **Your answer** (a hint card); the wording and look are proposed |
| Pinned row width | As many whole tiles as fit between the left padding and the screen edge less the focus room (7 on the Thor), always from the left: the theme's centre scroll anchor no longer applies to this row | Proposed |
| Jump back in hint | "A Play" (was "A Jump back in"; the card's own chip still says Jump back in) so "X Pin" fits beside the dock | Proposed |

### Navigation audit (2026-11-22, from your report)

| Detail | Value | Status |
|---|---|---|
| Holding a direction | One step per repeat. The focused tile's lift and bob no longer move where focus search thinks it is, so holding Down goes card, pinned row, dock and stops (0.5.4 bounced left and right along the row) | Fix |
| Up and Down in a grid | The next row: the same column, or the row's last tile when it is shorter, so a row out of sight slides in from any column. In a system's games the column stays the same (0.5.4 could jump to the end of the row above) | Proposed |
| Up and Down elsewhere | The nearest element directly above or below (the most overlap, then the leftmost). From the right end of the pinned row, Up goes to the tile above it (0.5.4 went to the far-left column) | Proposed |
| Down from Jump back in | The first pinned game (was the one under the card's centre) | Proposed |
| Up from a System page | Back to the tab it was opened from (the open page's tab the first time), not the nearest tab | Proposed |
| Cards waiting below | From outside a System page (Up from the dock) only a card that shows takes focus | Proposed |
| Left and Right | Unchanged: Android's search, no wrap at the ends | Unchanged |

### Collections, the Select menu and the settings popup (2026-11-23)

Your answers: a page below Home reached with Down, a grid of sized tiles, holding apps, HTML widgets (your own files), collections, built-in and Android widgets, single games and shortcuts; both edit modes to try. Those come in 0.6.1 and 0.6.2. 0.6.0 brings:

| Detail | Value | Status |
|---|---|---|
| Third section | Collections replaces System in the dock and L1/R1 (stacked-cards icon, `header.tabCollections`) | **Your answer** |
| Finding series | About 60 well-known series by name anywhere in the title (Pokémon, Mario, Zelda, Sonic, Grand Theft Auto…), and titles that start alike (two shared words in two games, or one uncommon word in three). One game on two consoles is not a series. Named without "collection" | **Your answer** (suggested from titles); the rules and list are proposed |
| Suggestions | After your collections, with a "Suggested" badge. Select → Keep, Rename…, Hide; adding or removing a game keeps it; a hidden series is never offered again | Proposed |
| Order | Yours A to Z, then suggestions, largest first | Proposed |
| Collection card | Its covers by count (one fills it, two side by side, three as one tall and two stacked, four in a square, 2 dp apart), name and count below; four across, two whole rows | **Your answer** (cover mosaic); the layouts are proposed |
| Inside a collection | Its games A to Z in the two-row grid; B goes back to its card | Proposed |
| Select | A menu for whatever is highlighted, "Wii-S settings" last; Select no longer switches Games and Apps | **Your answer** (like iiSU) |
| Menu look | Glass panel 380 dp wide over the screen dimmed 40% (55% in dark), the item's name and console or count on top, each row showing the button that does it directly (X, Y, A, Start), a line above Wii-S settings. B or Select closes; focus goes back where it was | Proposed |
| On a game | Pin / Unpin (X), Add to collection…, Remove from the collection (inside one), Emulator… (Y) | **Your answer**; Add to collection lists New collection…, yours, then suggestions |
| On a collection | Open (A), Keep (suggestions), Rename…, Hide (series) or Delete (yours) | Proposed |
| Naming | A popup with the Android keyboard; Save or the keyboard's Done | Proposed |
| Settings popup | Start, or the menu's last row. 785 × 412 dp: categories on the left (the page follows focus), the page on the right, B closes, focus stays inside | **Your answers** (centred popup, Start); the details are proposed |
| Bottom screen | A highlighted collection: its covers, name, games, played and consoles; Stats for its games together; Achievements ask you to highlight a game | Proposed |
| Older layouts | The shelves and grid layouts keep the settings as their third page | Proposed |

Older theme files (schema 3 and before) have one palette; the launcher and the workbench use it in both modes, so an imported Draft 3 looks the same in light and dark.

