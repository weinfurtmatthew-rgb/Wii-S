# Wii-S

A dual-screen game launcher for the **AYN Thor**: Android 13, built with Kotlin and Jetpack Compose. Its look mixes retro warmth with modern polish.

- **Plan:** [`docs/BLUEPRINT.md`](docs/BLUEPRINT.md) covers the architecture, roadmap and decision log.
- **Current milestone:** M0, the scaffold and hardware spike. On-device test steps are in [`docs/SPIKE.md`](docs/SPIKE.md).

## Download

Every push builds an APK in GitHub Actions (**Actions → Android → latest run → `wii-s-apk`**).
Pushing a `v*` tag publishes a GitHub Release with the APK attached.

## Build

JDK 17+ and an Android SDK with platform 36.

```sh
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest        # registry, system detection and intent-building tests
```

## Modules

| Module | Purpose |
|---|---|
| `:app` | Top-screen `HomeActivity`, bottom-screen `CompanionActivity`, spike screen, app graph |
| `:core:designsystem` | Tokens, theme, focusable `WiisTile` with gamepad handling |
| `:core:system` | Installed apps through `LauncherApps`, display probe |
| `:emulator` | Emulator registry (`assets/emulators.json`), ROM scanner, launcher, diagnostics |
| `:benchmark` | Startup and scroll Macrobenchmarks |
| `build-logic` | Gradle convention plugins |

Wii-S is an independent project. It is not affiliated with or endorsed by Nintendo or AYN.
