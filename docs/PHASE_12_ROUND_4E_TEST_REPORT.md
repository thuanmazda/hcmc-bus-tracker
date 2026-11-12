# PHASE 12 – ROUND 4E: BOUNDARY FIX TEST & BUILD REPORT

**Project:** HCMC Bus Tracker (`MultiGo Reworked`)  
**Phase / Round:** Phase 12 – Round 4E (Normal Coach Boundary Fix)  
**Date:** 2026-11-12  

---

## 1. Execution Summary

| Scope | Command | Suites | Tests | Failures | Errors | Skipped | Status |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Affected Module (`:feature-map`) | `.\gradlew.bat :feature-map:testDebugUnitTest --rerun` | 10 | 60 | 0 | 0 | 0 | **PASS** |
| All Modules | `.\gradlew.bat testDebugUnitTest --rerun` | 36 | 190 | 0 | 0 | 0 | **PASS** |
| Debug APK Build | `.\gradlew.bat assembleDebug --console=plain` | — | — | — | — | — | **BUILD SUCCESSFUL** |

- **Debug APK Artifact:** `app/build/outputs/apk/debug/app-debug.apk` (`64,800,396` bytes, built cleanly after final edit; **Release APK was NOT built**).

---

## 2. Native Skia Pixel Raster Verification (`160..640 dpi`)

Added `round 4e normal and selected coaches never clip across 160 to 640 dpi in native skia raster` to [VehicleMarkerBitmapFactoryTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/render/VehicleMarkerBitmapFactoryTest.kt) annotated with Robolectric `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (executing real Android Skia rendering against the local `nativeruntime 4.14.1` artifact).

Verified 6 coach variants (`active`, `delayed+arriving`, `stale+dashed+clock`, `noHeading+atStop`, `selected+active`, `selected+delayed`) across all 5 screen densities (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`):

- **Normal Coach Perimeter Check:** `alpha == 0` along all 4 outer bitmap edges (`y = 0`, `y = h - 1`, `x = 0`, `x = w - 1`) at every density (`160..640 dpi`).
- **Selected Coach Perimeter Check:** `alpha < 128` along all 4 outer edges (`0` clipped opaque pixels), keeping halo untouched at `~58.8 dp`.
- **Measured Real Opaque Bounding Boxes (`alpha >= 128`):**

| Density | Scale | Normal Canvas (`px`) | Normal Opaque Box (`px`) | Measured Normal Height (`dp`) | Selected Canvas (`px`) | Selected Opaque Halo Box (`px`) | Measured Halo Diameter (`dp`) |
| :---: | :---: | :---: | :--- | :---: | :---: | :--- | :---: |
| `160 dpi` (`mdpi`) | `1.0x` | `44 × 44` | `(11, 2)..(32, 37)` | **`36.00 dp`** | `60 × 60` | `(1, 1)..(58, 58)` | **`58.00 dp`** |
| `240 dpi` (`hdpi`) | `1.5x` | `66 × 66` | `(17, 3)..(48, 56)` | **`36.00 dp`** | `90 × 90` | `(1, 1)..(88, 88)` | **`58.67 dp`** |
| `320 dpi` (`xhdpi`) | `2.0x` | `88 × 88` | `(22, 3)..(65, 76)` | **`37.00 dp`** | `120 × 120` | `(1, 1)..(118, 118)` | **`59.00 dp`** |
| `480 dpi` (`xxhdpi`) | `3.0x` | `132 × 132` | `(33, 5)..(98, 114)` | **`36.67 dp`** | `180 × 180` | `(2, 2)..(177, 177)` | **`58.67 dp`** |
| `640 dpi` (`xxxhdpi`) | `4.0x` | `176 × 176` | `(44, 6)..(131, 152)` | **`36.75 dp`** | `240 × 240` | `(2, 3)..(237, 236)` | **`58.50 dp`** |

---

## 3. Full Module Regression Breakdown (`190/190 PASS`)

| Module | Suites | Tests | Failures | Errors | Skipped |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `:app` | 1 | 2 | 0 | 0 | 0 |
| `:core-database` | 4 | 21 | 0 | 0 | 0 |
| `:core-model` | 6 | 43 | 0 | 0 | 0 |
| `:core-network` | 8 | 22 | 0 | 0 | 0 |
| `:feature-map` | 10 | 60 | 0 | 0 | 0 |
| `:feature-prediction` | 3 | 26 | 0 | 0 | 0 |
| `:feature-route` | 2 | 7 | 0 | 0 | 0 |
| `:feature-search` | 2 | 9 | 0 | 0 | 0 |
| **Total** | **36** | **190** | **0** | **0** | **0** |
