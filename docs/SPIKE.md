# Milestone 0 — Hardware spike on the AYN Thor Max

Takes about 15 minutes. What you send back decides how the bottom screen works (mode A, B or C, from BLUEPRINT §4.4) and fixes any emulator definitions that don't match your installed versions.

## 1. Install

Use the APK from GitHub (Actions → latest run → `wii-s-apk` artifact, or a Release once one is tagged), or the file shared in chat.

Either sideload it on the device, or:

```sh
adb install -r Wii-S-*.apk
```

Builds are signed with the same development key, so each new build installs over the last one.

## 2. Make it the launcher

Press Home. Android asks which launcher to use: pick **Wii-S**, then **Always**.
(Later: Settings → Apps → Default apps → Home app.)

**Look at the bottom screen now.** Does it show the Wii-S companion ("Highlight a game on the top screen…")? Write that down. It's the key result.

If AYN's dual-screen settings have an option for what the bottom screen shows, note what it is set to.

## 3. Run the spike

1. Top screen → **Spike** tab (R1 cycles tabs).
2. If the companion did *not* appear on its own, press **Test mode B: place bottom screen**. Note whether it appears.
3. Press **ROM folder**, choose the folder that holds your system folders (e.g. `ROMs/` containing `snes/`, `psp/`, `ps2/` …), and allow access.
4. Go to **Games**. Test at least:
   - one **RetroArch** game (SNES/GBA/N64),
   - one **standalone** emulator game (PPSSPP, DuckStation, NetherSX2, Dolphin…),
   - **Y** on a game opens the emulator picker.
5. After each game, press Home and confirm the bottom screen shows playtime for it.
6. Spike tab → **Share report** (or pull it, below) and send me the text.

```sh
adb pull /sdcard/Android/data/app.wiis.launcher/files/spike-report.txt
```

## 4. Extra useful output (if you have adb)

```sh
adb shell dumpsys display | grep -E "mDisplayId|DisplayDeviceInfo" | head -20
adb shell dumpsys activity activities | grep -E "Display #|ResumedActivity|mFocusedApp" | head -30
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.SECONDARY_HOME
```

## 5. Benchmarks (optional for M0)

With USB debugging on and the device connected:

```sh
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Results land in `benchmark/build/outputs/connected_android_test_additional_output/`.

## What to send back

- Did the companion appear automatically (mode A)? If not, did mode B work?
- The spike report text.
- Which emulator launches worked or failed, with any error message shown at the bottom of the top screen.
- Anything that felt slow or wrong with the controller.
