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
