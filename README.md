# Wii-S

A dual-screen game launcher for the **AYN Thor**: Android 13, built with Kotlin and Jetpack Compose. Its look mixes retro warmth with modern polish.

- **Plan:** [`docs/BLUEPRINT.md`](docs/BLUEPRINT.md) covers the architecture, roadmap and decision log.
- **Current iteration:** 2, the design system. Every visual and sound detail is a theme token you approve in the Design Workbench ([`design/workbench.html`](design/workbench.html), process in [`docs/DESIGN.md`](docs/DESIGN.md)).
- **Box art sources:** [`docs/SCRAPING.md`](docs/SCRAPING.md). **Hardware spike:** [`docs/SPIKE.md`](docs/SPIKE.md).

## Download

Every push builds an APK in GitHub Actions (**Actions → Android → latest run → `wii-s-apk`**).
Pushing a `v*` tag publishes a GitHub Release with the APK attached.

## Build

JDK 17+ and an Android SDK with platform 36.

```sh
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest        # registry, detection, intents, theme, synth parity, art naming
```

## Modules

| Module | Purpose |
|---|---|
| `:app` | Top-screen `HomeActivity`, bottom-screen `CompanionActivity`, spike screen, app graph |
| `:core:designsystem` | Theme schema (`ThemeSpec`, `assets/theme/default.json`), sound synthesiser, themed `WiisTile`, backgrounds |
| `:core:system` | Installed apps through `LauncherApps`, display probe |
| `:emulator` | Emulator registry (`assets/emulators.json`), ROM scanner, launcher, diagnostics, box art (`art/`) |
| `design/` | Design Workbench page (source of the published artifact) |
| `:benchmark` | Startup and scroll Macrobenchmarks |
| `build-logic` | Gradle convention plugins |

Wii-S is an independent project. It is not affiliated with or endorsed by Nintendo or AYN.
