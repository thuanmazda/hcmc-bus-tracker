# PHASE 12 – ROUND 3: UNIT TEST REPORT

**Project:** HCMC Bus Tracker (`MultiGo Reworked`)  
**Phase / Round:** Phase 12 – Round 3 (Vehicle Marker Implementation)  
**Date:** 2026-11-12  

---

## 1. Execution Summary

Both module-scoped tests (`:feature-map:testDebugUnitTest --rerun`) and full-project unit tests (`.\gradlew.bat testDebugUnitTest --rerun`) executed after all final edits and passed with **0 failures, 0 errors, and 0 skipped tests**.

| Scope | Command | Suites | Tests | Failures | Errors | Skipped | Status |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Affected Module (`:feature-map`) | `.\gradlew.bat :feature-map:testDebugUnitTest --rerun` | 9 | 55 | 0 | 0 | 0 | **PASS** |
| All Modules | `.\gradlew.bat testDebugUnitTest --rerun` | 35 | 185 | 0 | 0 | 0 | **PASS** |

---

## 2. Phase 12 Round 3 Unit Test Suites Added / Extended

### 2.1 `VehicleMarkerBitmapFactoryTest` (Robolectric SDK 34 — 4 tests)
- **File:** [VehicleMarkerBitmapFactoryTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/render/VehicleMarkerBitmapFactoryTest.kt)
- **Covered Behaviors:**
  - Exact logical dp consistency across `mdpi (160)`, `hdpi (240)`, `xhdpi (320)`, `xxhdpi (480)`, and `xxxhdpi (640)` using `bitmap.density` + `computeRenderedDp(...)` (`NORMAL_COACH_DP = 38 dp` within `36–40 dp`; `SELECTED_COACH_DP = 50 dp` within `48–54 dp`; `SELECTED_HALO_DP = 60 dp` within `56–62 dp`).
  - Safe sub-mdpi floor clamping (`DENSITY_LOW` -> `DENSITY_MEDIUM`).
  - Distinct icon specs & cached bitmap identities across normal, selected, no-heading (`hasHeading = false`), delayed, and stale/held states; `labelPool` eviction on `trimCachesOnLowMemory()` with `!bmp.isRecycled`.
  - Deterministic `mapFreshTier`, `mapStatusBadge`, `formatShortPlate`, stop cluster count clamping (`99+`), and Blue Dot pooling.

### 2.2 `VehicleRenderDifferAndBearingTest` (JUnit 4 — 3 tests)
- **File:** [VehicleRenderDifferAndBearingTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/render/VehicleRenderDifferAndBearingTest.kt)
- **Covered Behaviors:**
  - `BearingSmoother` circular shortest-arc interpolation across `0° <-> 359°`, `359° -> 1°`, `90° -> 270°`, negative/overflow normalization, and `Float.NaN` guard.
  - `shouldAnimateRotation` suppression for missing heading (`!hasValidHeading`), stationary vehicles, and sub-threshold deltas (`< 2.0f`).
  - `VehicleRenderDiffer.diff` detecting freshness, latency/tier, provenance (`PositionSource`), arrival status, bearing, selection, addition, and removal changes even when latitude/longitude stay fixed; exclusion of `(0.0, 0.0)` invalid coordinates.

### 2.3 `VehicleOverlapResolverTest` (JUnit 4 — 2 tests)
- **File:** [VehicleOverlapResolverTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/render/VehicleOverlapResolverTest.kt)
- **Covered Behaviors:**
  - `OverlapTapOutcome.None`, `Single`, and `Multiple` detection for co-located and nearby buses within `44 dp` touch radius without altering real GPS coordinates.
  - Density-proportional hit radius scaling comparing `mdpi (1.0f)` vs `xxhdpi (3.0f)`.

### 2.4 `MapLayerManagerTest` (Robolectric SDK 34 — 1 test)
- **File:** [MapLayerManagerTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/render/MapLayerManagerTest.kt)
- **Covered Behaviors:**
  - Reverse-order style application (`UserLocation` -> `Vehicles` -> `Stops` -> `RoutePolyline`) resolving strictly to `MapLayerManager.CANONICAL_LAYER_ORDER` via `RecordingStyleOps`.
  - Idempotency across direction flips and repeated updates (`duplicateLayerAddAttempts == 0`), cluster source configuration (`maxZoom = 13, radius = 42`), clean selective removal, and full canonical restoration after simulated style reload.

### 2.5 `RoutePathGeoJsonConverterTest` (Robolectric SDK 34 — extended to 8 tests)
- **File:** [RoutePathGeoJsonConverterTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/RoutePathGeoJsonConverterTest.kt)
- **Covered Behaviors:** Verified all existing GeoJSON tests remain green alongside new assertions for `coach_icon_id`, `short_plate`, `short_label_icon_id`, `full_label_icon_id`, `bearingOverrides`, and `sort_key` (`1000.0` selected vs `100.0` active normal).

---

## 3. Defects Caught & Fixed During Round 3 Test Execution

1. **`GpsLatencyCategory.UNKNOWN` Default Shadowing Active Telemetry:** Initial test run exposed that `VehicleMarkerBitmapFactory.mapFreshTier` classified `GpsFreshnessState.FRESH` vehicles as `STALE_OR_HELD` whenever `InterpolatedVehiclePosition.latencyCategory` carried its default `UNKNOWN` value. Fixed `mapFreshTier` so `STALE_OR_HELD` triggers on `UNKNOWN` only when BOTH `freshnessState == UNKNOWN` and `latencyCategory == UNKNOWN` (or `positionSource == HELD_AT_LAST_VALID_GPS` / `UNKNOWN`, or `STALE` / `UNCHANGED`).
2. **Robolectric Minimal Text Measurement Width:** Under Robolectric SDK 34 without native Android font metrics, `Paint.measureText` returned minimal stub widths below `36 dp`, making short and full pills clamp to equal pixel widths. Added a `maxOf(measureText, length * dpToPxF(textDp * 0.54f))` floor in `renderLabelBitmap` matching real typographic proportions.
3. **MapLibre JNI `ThreadUtils` Isolation:** Separated MapLibre native source/layer instantiation behind `StyleLayerOperations.addGeoJsonSource` / `addAnchoredLayer` so Z-order placement (`resolveAnchor`, `CANONICAL_LAYER_ORDER`) runs deterministically in Robolectric JVM tests without `ThreadUtils isn't correctly initialised`.

---

## 4. Per-Module Verified XML Test Breakdown

| Module | Suites | Tests | Failures | Errors | Skipped |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `:app` | 1 | 2 | 0 | 0 | 0 |
| `:core-database` | 4 | 21 | 0 | 0 | 0 |
| `:core-model` | 6 | 43 | 0 | 0 | 0 |
| `:core-network` | 8 | 22 | 0 | 0 | 0 |
| `:feature-map` | 9 | 55 | 0 | 0 | 0 |
| `:feature-prediction` | 3 | 26 | 0 | 0 | 0 |
| `:feature-route` | 2 | 7 | 0 | 0 | 0 |
| `:feature-search` | 2 | 9 | 0 | 0 | 0 |
| **Total** | **35** | **185** | **0** | **0** | **0** |
