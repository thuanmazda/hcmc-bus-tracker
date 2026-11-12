# PHASE 12 – ROUND 3: REGRESSION REPORT

**Project:** HCMC Bus Tracker (`MultiGo Reworked`)  
**Phase / Round:** Phase 12 – Round 3 (Vehicle Marker Implementation)  
**Date:** 2026-11-12  

---

## 1. Phase 11 Protection Boundary Audit

Because the workspace is not a Git working tree (`git status` returns `not a git repository`), Phase 11 preservation was verified via file modification timestamps (`LastWriteTime`) alongside full multi-module regression execution (`.\gradlew.bat testDebugUnitTest --rerun`).

All Phase 11 core tracking, latency compensation, concurrent polling, interpolation, and camera state machine source files retain their pre-Round-3 Phase 11 timestamps (prior to `16:00` local time) and were not edited:

| Protected Phase 11 File | Module | Observed `LastWriteTime` | Status During Round 3 |
| :--- | :--- | :--- | :--- |
| `PositionCompensationEngine.kt` | `:core-model` | `11/12/2026 2:24:22 PM` | **Unmodified** |
| `VehicleDataMerger.kt` | `:core-model` | `11/12/2026 3:45:40 PM` | **Unmodified** |
| `AdaptiveConcurrencyController.kt` | `:core-network` | `11/12/2026 3:46:59 PM` | **Unmodified** |
| `GlobalPollingCoordinator.kt` | `:core-network` | `11/12/2026 3:47:03 PM` | **Unmodified** |
| `SharedStopCoalescer.kt` | `:core-network` | `11/12/2026 3:47:52 PM` | **Unmodified** |
| `VehicleInterpolationEngine.kt` | `:feature-map` | `11/12/2026 2:24:23 PM` | **Unmodified** |
| `MapViewModel.kt` | `:feature-map` | `11/12/2026 3:52:06 PM` | **Unmodified** |

GPS integrity rules remain preserved: no fake GPS telemetry is generated, and `VehicleOverlapResolver` selects co-located buses in screen space without shifting real lat/lng coordinates.

---

## 2. Phase 11 & Multi-Module Regression Verification

Full re-execution (`.\gradlew.bat testDebugUnitTest --rerun`) confirmed zero regressions across every pre-existing Phase 11 suite:

- **GPS Compensation & Merging (`:core-model`):**
  - `PositionCompensationEngineTest`: `20/20` PASS
  - `VehicleDataMergerTest`: `4/4` PASS
  - `RouteVehicleAggregatorTest`: `7/7` PASS
  - `RouteNumberComparatorTest`: `6/6` PASS
  - `VietnameseNormalizerTest`: `4/4` PASS
  - `PredictionModelTest`: `2/2` PASS
- **Adaptive Concurrent Polling & EBMS API (`:core-network`):**
  - `AdaptiveConcurrencyControllerTest`: `3/3` PASS
  - `GlobalPollingCoordinatorTest`: `3/3` PASS
  - `SharedStopCoalescerTest`: `4/4` PASS
  - `PollingBenchmarkTest`: `1/1` PASS
  - `SafeApiCallTest` (`6/6`), `RouteDtoParsingTest` (`2/2`), `BusStopDtoParsingTest` (`2/2`), `PredictionDtoParsingTest` (`1/1`) PASS
- **Interpolation, Camera State Machine & GeoJSON (`:feature-map`):**
  - `MapViewModelTest`: `21/21` PASS (including `trackVehicle with invalid path coordinates avoids auto-center and reports warning` and camera focus/follow/gesture/idle transitions)
  - `VehicleInterpolationEngineTest`: `11/11` PASS
  - `RoutePathGeoJsonConverterTest`: `8/8` PASS (both legacy compatibility properties `arrow_icon_id`/`label_icon_id` and Phase 12 properties verified)
  - `TileProviderTest` (`3/3`), `HomeViewModelTest` (`2/2`) PASS
- **Database & Other Feature Modules:**
  - `:core-database` (`21/21`), `:feature-prediction` (`26/26`), `:feature-route` (`7/7`), `:feature-search` (`9/9`), `:app` (`2/2`) all PASS.
- **Full Debug APK Assembly:**
  - `.\gradlew.bat assembleDebug` succeeded (`app-debug.apk`, `64,799,773` bytes).

---

## 3. Environment Scope & Next-Step Boundary

- **Not Executed Yet (Out of Round 3 / No Attached Device):** `adb.exe devices` confirmed `0` physical devices or emulators connected. Live on-device MapLibre GL pixel density inspection, animation frame smoothness (`359° -> 1°`), and interactive touch testing of `VehicleOverlapPickerSheet` must be validated on an Android device/emulator in the next round.
- **Boundary Compliance:** Implementation stopped at Round 3 completion as authorized; Release build packaging (`assembleRelease`), R8/ProGuard verification, and visual QA await explicit user approval.
