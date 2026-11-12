# PHASE 12 – ROUND 4: DEVICE TEST REPORT

**Project:** HCMC Bus Tracker (`MultiGo Reworked`)  
**Phase / Round:** Phase 12 – Round 4 (Visual QA & Map Interaction Validation)  
**Date:** 2026-11-12  

---

## 1. Environment Discovery

Checked connected devices and local Android Virtual Devices (AVDs):

- **`adb.exe devices -l`:** Empty (`List of devices attached` — **0 devices connected**).
- **`emulator.exe -list-avds`:** Empty (**0 local AVDs configured**).

Per Round 4 Section 8 protocol, live Visual QA **cannot** be marked PASS without an actual device or emulator run, and all unexecuted physical tests are listed honestly as **PENDING**.

---

## 2. Debug APK Artifact Verification

Rebuilt the Debug APK cleanly after all Round 4 code/test fixes:

- **Command:** `.\gradlew.bat assembleDebug --console=plain` -> **BUILD SUCCESSFUL** (`45s`, exit code `0`).
- **Output Path:** `app/build/outputs/apk/debug/app-debug.apk`
- **File Size:** `64,800,396` bytes (`LastWriteTime`: `11/12/2026 4:55:01 PM`).
- **Signing Status:** Standard debug keystore only; **Release signing was NOT run** per Round 4 boundaries.

---

## 3. On-Device Verification Status Checklist

| Check Category | Required On-Device Action | Current Status |
| :--- | :--- | :--- |
| APK Installation | `adb install -r app-debug.apk` & cold launch | **PENDING** (0 devices attached) |
| Live Marker Pixel Sizing | Visual measurement of normal (`36–40 dp`), selected (`48–54 dp`), halo (`56–62 dp`), and label offset beneath coach on MapLibre GL `11.5.2` | **PENDING** (geometry/formula verified on JVM/Robolectric `160..640 dpi`) |
| Tile Background Separation | Capture screenshots over FPT STIS & alternate raster tiles to confirm coach vs one-way road arrows clarity at low/mid/high zoom | **PENDING** |
| Touch & Bottom Sheet UX | Tap single vehicle, empty space, overlapping cluster, switch buses inside `VehicleOverlapPickerSheet`, dismiss sheet | **PENDING** (logic & UI composable compiled; unit tested in `VehicleOverlapResolverTest`) |
| Live Camera + Polling Smoothness | Verify `50ms x 6` shortest-arc rotation, 12m follow guard, gesture pause (`USER_CONTROLLED`), resume follow, and route switches with live GL frames | **PENDING** |
| Live Profiler / LeakCanary Check | Inspect Android Studio GPU Profiler, Memory heap, and native MapLibre texture churn over extended style reloads | **PENDING** |
