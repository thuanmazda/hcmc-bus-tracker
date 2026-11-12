# PHASE 12 – ROUND 3: IMPLEMENTATION REPORT

**Project:** HCMC Bus Tracker (`MultiGo Reworked`)  
**Phase / Round:** Phase 12 – Round 3 (Vehicle Marker Implementation)  
**Date:** 2026-11-12  

---

## 1. Executive Summary

Phase 12 Round 3 replaced the legacy raw-pixel vehicle arrow markers and scattered MapLibre style mutation helpers with a unified, density-accurate **Top-Down Bus Coach** rendering system across the map layer.

All Phase 11 core algorithms (`PositionCompensationEngine`, `VehicleInterpolationEngine`, `GlobalPollingCoordinator`, `AdaptiveConcurrencyController`, `SharedStopCoalescer`, `VehicleDataMerger`, EBMS API DTOs/services, Room persistence, and camera state machine `VehicleCameraMode`) remain completely untouched.

---

## 2. Implemented Architecture & Components

### 2.1 Density-Accurate Bitmap Factory (`VehicleMarkerBitmapFactory.kt`)
- **File:** [VehicleMarkerBitmapFactory.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/render/VehicleMarkerBitmapFactory.kt)
- **Root-Cause Fix for MapLibre `11.5.2` Sizing:** Bytecode inspection of `org.maplibre.android.maps.Style.toImage` showed MapLibre computes `pixelRatio = bitmap.density / 160.0f` and renders `SymbolLayer` icons at `(bitmapWidthPx / pixelRatio) * iconSize` dp. Setting BOTH `widthPx = round(targetDp * (safeDensityDpi / 160f))` AND explicit `bitmap.density = safeDensityDpi` (with `safeDensityDpi = densityDpi.coerceAtLeast(DENSITY_MEDIUM)`) keeps rendered physical dp constant across `mdpi (160)` to `xxxhdpi (640)`.
- **Top-Down Coach Anatomy (`VehicleMapRenderConfig`):**
  - Normal coach target body: `38 dp` (`NORMAL_COACH_DP = 38f`, canvas `44 dp` with padding & heading nose), within `36–40 dp`.
  - Selected coach target body: `50 dp` (`SELECTED_COACH_DP = 50f`), within `48–54 dp`, centered inside an amber `60 dp` halo ring (`SELECTED_HALO_DP = 60f`, within `56–62 dp`).
  - Renders panoramic windshield, front headlights & directional nose cone (strictly suppressed when `hasHeading = false`), roof AC pod, drop shadow, white outer casing, and corner status badge (`CoachStatusBadge`: `ARRIVING`, `AT_STOP`, `PASSED`, `STALE_CLOCK`, `UNKNOWN_MARK`, `NONE`).
  - Uses a clean 3-tier color system (`CoachFreshTier`: `ACTIVE` `#00796B`, `DELAYED` with `#FDE68A` rim, `STALE_OR_HELD` `#64748B` with dashed border, plus `Selected` `#D32F2F`) without creating a noisy multi-color rainbow.
- **Label, Cluster & User Location Bitmaps:**
  - Adaptive label pills (`LabelZoomTier.SHORT` formatted via `formatShortPlate` e.g. `615.77`, and `LabelZoomTier.FULL` e.g. `50E-615.77`) with proportional text-width floor (`maxOf(measureText, length * dpToPxF(textDp * 0.54f))`).
  - Stop cluster badges (`getOrCreateStopClusterBitmap`, `STOP_CLUSTER_BADGE_DP = 30f`, clamping counts `> 99` to `99+`) and Google Maps Blue Dot (`USER_BLUE_DOT_DP = 22f`).
- **LRU Caching & Safe Memory Pressure Handling:** Pooled `LruCache` instances (`ICON_POOL_MAX_ENTRIES = 32`, `LABEL_CACHE_MAX_ENTRIES = 64`, `CLUSTER_CACHE_MAX_ENTRIES = 32`). `trimCachesOnLowMemory()` evicts label/cluster entries and trims coach entries to `12` without prematurely calling `.recycle()` on bitmaps that MapLibre GL may still bind.

### 2.2 Circular Bearing Smoother & Incremental Differ (`VehicleRenderDiffer.kt`)
- **File:** [VehicleRenderDiffer.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/render/VehicleRenderDiffer.kt)
- **`BearingSmoother`:** Computes `shortestSignedDeltaDegrees` in `[-180, +180)` and `interpolateBearingDegrees` cleanly across `0° / 360°` boundaries (`359° -> 1°` yields `+2°`; `0° -> 359°` yields `-1°`). `shouldAnimateRotation` disables synthetic rotation when `!previousHasHeading`, `!nextHasHeading`, `isStationary = true`, or delta `< 2.0f`.
- **`VehicleRenderDiffer`:** Compares `VehicleRenderSnapshot` maps (`buildSnapshots` / `diff`) across coordinates (`COORD_EPSILON = 1e-6`), bearing (`BEARING_EPSILON_DEG = 0.5f`), `freshnessState`/`latencyCategory`, `arrivalStatus`, `positionSource`, selection state, plate label changes, additions, and removals — preventing dropped UI updates when status or freshness changes while coordinates stay fixed, and skipping invalid `(0.0, 0.0)` coordinates cleanly.

### 2.3 Centralized Z-Order Layer Manager (`MapLayerManager.kt`)
- **File:** [MapLayerManager.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/render/MapLayerManager.kt)
- **Canonical 11-Layer Z-Order (`CANONICAL_LAYER_ORDER`):**
  1. `hcmc-route-casing-layer`
  2. `hcmc-route-layer`
  3. `hcmc-stop-cluster-layer`
  4. `hcmc-stops-layer`
  5. `hcmc-terminal-stops-layer`
  6. `hcmc-selected-stop-layer`
  7. `hcmc-user-location-accuracy-layer`
  8. `hcmc-vehicle-normal-layer`
  9. `hcmc-vehicle-selected-layer`
  10. `hcmc-vehicle-label-layer`
  11. `hcmc-user-location-layer`
- **`resolveAnchor` & `StyleLayerOperations`:** Every layer insertion scans above and below in `CANONICAL_LAYER_ORDER` using `LayerAnchor.Below` / `LayerAnchor.Above` / `LayerAnchor.Top` so asynchronous Compose `LaunchedEffect` ordering or style reloads cannot place polylines or stops above vehicles or the Blue Dot.
- **3-Tier Label Visibility & Stop Clustering:**
  - `VEHICLE_LABEL_LAYER_ID` hides normal labels below `MEDIUM_ZOOM_LABEL_MIN = 13.0f`, shows `short_label_icon_id` for `13.0 <= zoom < 14.8`, and switches to `full_label_icon_id` at `HIGH_ZOOM_LABEL_MIN = 14.8f`, while keeping the selected vehicle's full plate visible at all zoom levels and prioritizing via `symbolSortKey`.
  - `STOPS_SOURCE_ID` configures MapLibre native clustering (`clusterRadius = 42`, `clusterMaxZoom = 13`) with offline-preloaded `hcmc-stop-cluster-*` badges.

### 2.4 Overlap Tap Resolver & Bottom Sheet Picker (`VehicleOverlapPicker.kt`)
- **File:** [VehicleOverlapPicker.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/render/VehicleOverlapPicker.kt)
- **`VehicleOverlapResolver.resolveTap`:** Performs `44 dp` (`OVERLAP_TAP_RADIUS_DP = 44f`) density-scaled Euclidean screen-space proximity resolution over projected vehicle screen coordinates. Returns `OverlapTapOutcome.None`, `Single`, or `Multiple` without shifting or fabricating real GPS coordinates.
- **`VehicleOverlapPickerSheet`:** Material 3 `ModalBottomSheet` displaying overlapping/co-located buses on the active route with plate number, speed, arrival status badge, latency/provenance metadata, and one-tap selection.

### 2.5 GeoJSON Conversion & `MapScreen` Integration
- **[RoutePathGeoJsonConverter.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/util/RoutePathGeoJsonConverter.kt):** Updated `toRouteVehiclesFeatureCollection` with optional `bearingOverrides` and Phase 12 properties (`coach_icon_id`, `short_plate`, `short_label_icon_id`, `full_label_icon_id`, `sort_key`, `fresh_tier`, `status_badge`, `position_source`) while keeping legacy properties (`arrow_icon_id`, `label_icon_id`) for compatibility.
- **[MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt):** Wired `VehicleMarkerBitmapFactory`, `ComponentCallbacks2` (`onLowMemory` / `onTrimMemory`), smooth `animatedBearings` `LaunchedEffect`, `VehicleRenderDiffer`, `MapLayerManager.apply*` across route polyline, stops, user location, and vehicles, map click handling via `VehicleOverlapResolver`, `VehicleOverlapPickerSheet` UI overlay, and removed the superseded private legacy render helpers (`renderRoutePolylineOnStyle` through `renderRouteVehiclesOnStyle`).

---

## 3. Build Verification

- Executed `.\gradlew.bat assembleDebug --console=plain`: **BUILD SUCCESSFUL** (exit code `0`).
- Verified output artifact: `app/build/outputs/apk/debug/app-debug.apk` (`64,799,773` bytes).
- Checked `adb.exe devices`: **0 Android devices or emulators attached**, so live GPU/MapLibre visual screenshot & physical touch verification is explicitly deferred to device testing (Round 4/5).
