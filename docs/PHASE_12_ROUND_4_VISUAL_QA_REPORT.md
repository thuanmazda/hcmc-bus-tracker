# PHASE 12 – ROUND 4: VISUAL QA REPORT

**Project:** HCMC Bus Tracker (`MultiGo Reworked`)  
**Phase / Round:** Phase 12 – Round 4 (Visual QA & Map Interaction Validation)  
**Date:** 2026-11-12  
**Visual QA Overall Status:** **PARTIAL PASS (Automated Code/Geometry/Unit QA PASSED — Live On-Device Visual/Screenshot QA PENDING due to 0 attached devices/emulators)**

---

## 1. Real Marker Sizing & MapLibre `11.5.2` Property Audit (Section 2)

Checked [VehicleMarkerBitmapFactory.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/render/VehicleMarkerBitmapFactory.kt) and [MapLayerManager.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/render/MapLayerManager.kt) against MapLibre Android `11.5.2` bytecode & automated tests (`VehicleVisualQaAndPerfTest`):

| Metric / Property | Configuration / Behavior | Verified Across `160..640 dpi` | Target / Status |
| :--- | :--- | :--- | :--- |
| Normal coach body | `NORMAL_COACH_DP = 38f` (body `17.5 x 28.0 dp` + casing + heading nose; canvas `44 dp`) | `44 px` @ `mdpi` → `176 px` @ `xxxhdpi` (`bitmap.density = safeDensityDpi`) | Target `36–40 dp` body / **Verified geometry** |
| Selected coach body | `SELECTED_COACH_DP = 50f` (body `22.5 x 36.0 dp` + casing + heading nose) | Canvas `60 dp` (`60 px` @ `mdpi` → `240 px` @ `xxxhdpi`) | Target `48–54 dp` body / **Verified geometry** |
| Selected halo ring | `SELECTED_HALO_DP = 60f`, radius `0.475f * 60 + 1.8 dp` stroke (`58.8 dp` diameter) | `58.8 dp` (adjusted up from `55.8 dp` in Round 4 fix) | Target `56–62 dp` / **Verified geometry** |
| `iconSize` (zoom curve) | `LOW_ZOOM = 0.95f` (`<=11`), `DEFAULT = 1.0f` (`13`), `HIGH_ZOOM = 1.05f` (`>=17`) | Normal rendered span `41.8 / 44.0 / 46.2 dp`; Selected canvas `57.0 / 60.0 / 63.0 dp` | **Verified** |
| `iconOffset` (labels) | Fixed in Round 4: `[0f, 23f]` normal, `[0f, 31f]` selected (`ICON_ANCHOR_TOP`) | Strictly clears normal (`22 dp` half) and selected (`30 dp` half) coaches | **Bug Fixed & Verified** |
| `iconPadding` | Added in Round 4: `2f` on coach layers, `4f` on `VEHICLE_LABEL_LAYER_ID` | Prevents adjacent plate pills crowding tightly at stops | **Fixed & Verified** |
| `iconRotationAlignment` | Coaches: `MAP` (rotates by bearing); Labels: `VIEWPORT` (stays horizontal) | Readable plate text even on N/S/E/W rotating buses | **Verified** |
| `iconAllowOverlap` / `IgnorePlacement` | Coaches: `true` / `true` (never vanish in clusters); Labels: `false` / `false` (collision culled) | Buses remain visible; overlapping labels declutter gracefully | **Verified** |
| `symbolSortKey` | Selected `1000.0`, Arriving/At-Stop `200.0`, Active `100.0`, Stale `20.0` | Prioritizes tracked and arriving buses above stale icons/labels | **Verified** |

---

## 2. Visual Defects Caught & Fixed During Round 4 Code Audit

1. **`iconOffset` Unit Bug in `MapLayerManager.kt` (`1.45f / 1.85f` → `23f / 31f`):**
   - `PropertyFactory.iconOffset` in MapLibre Native Android operates in logical pixels scaled by `iconSize` (NOT fractional multiples of icon height). The initial `1.45f` / `1.85f` values anchored the top of the license plate pill only `1.45–1.85 dp` below the coach center, overlapping the lower body and AC pod.
   - Updated to `VehicleMapRenderConfig.LABEL_NORMAL_OFFSET_Y_DP = 23f` and `LABEL_SELECTED_OFFSET_Y_DP = 31f` alongside explicit `iconPadding(4f)` on `VEHICLE_LABEL_LAYER_ID` (`2f` on coach layers).
2. **Selected Halo Diameter Slightly Below Target (`55.8 dp` → `58.8 dp`):**
   - Increased halo radius multiplier in `VehicleMarkerBitmapFactory` from `0.45f` to `0.475f` so outer stroke diameter measures `58.8 dp`, squarely inside the `56–62 dp` target while remaining cleanly bounded inside the `60 dp` bitmap.
3. **Orphan `VehicleOverlapPickerSheet` Across Route/Variant Switches (`MapScreen.kt`):**
   - Added `LaunchedEffect(state.activeRoute?.id, state.activeVariant?.id) { overlapCandidates = emptyList() }` so changing route or direction immediately dismisses any open overlap sheet from the prior route.

---

## 3. Fleet Scenario Matrix Validation (Section 3 – Automated Fixtures)

Verified via [VehicleVisualQaAndPerfTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/render/VehicleVisualQaAndPerfTest.kt) using local benchmark/QA fixtures (zero real/fake EBMS mutation):

| # | Scenario | Automated Result | Notes / On-Device Status |
| :---: | :--- | :--- | :--- |
| 1–4 | Fleet sizes `1`, `10`, `30`, `50` buses | **PASS** (100% unique features, `diff.hasAnyChange == false` on steady poll) | On-device visual frame check pending |
| 5–6 | Two nearby buses & co-located same-coord buses | **PASS** (`VehicleOverlapResolver` triggers Bottom Sheet cleanly; lat/lng unaltered) | On-device touch check pending |
| 7 | Selected bus adjacent to normal bus | **PASS** (`50 dp` + `#F59E0B` halo, `sort_key = 1000.0` vs `100.0`) | On-device screenshot pending |
| 8 | Bus without `hasValidHeading` | **PASS** (`has_heading = false`, `bearing = 0f`, nose cone suppressed) | Verified icon spec distinctness |
| 9–12 | Stale GPS / `INTERPOLATED_ON_ROUTE` / `LATENCY_COMPENSATED` / `HELD_AT_LAST_VALID_GPS` | **PASS** (`CoachFreshTier`, `status_badge`, `position_source` cleanly mapped & diffed even with fixed coords) | Real raster tile background contrast pending device GPU |

*Visual Screenshot Gap:* Real OpenGL ES MapLibre screenshot comparison against FPT STIS raster tiles (one-way road arrows contrast, legibility under bright outdoor basemaps) could not be captured because no Android emulator or physical device is connected (`adb devices` = 0).

---

## 4. `MapLayerManager`, `VehicleOverlapPicker` & Camera Compatibility (Sections 4–6)

- **Layer Lifecycle (`MapLayerManagerTest` & `VehicleVisualQaAndPerfTest`):** Verified load → reverse-order insertion → direction flip → route clear → stop update → location update → `20x` simulated style reloads preserve `CANONICAL_LAYER_ORDER` (`11` layers max, `0` duplicate layer attempts). Blue Dot remains above all vehicle layers; selected vehicle sits above normal vehicles; routes/stops stay below vehicles.
- **Overlap Picker (`VehicleOverlapResolverTest` & `VehicleVisualQaAndPerfTest`):** Verified `None`, `Single`, `Multiple`, dismiss & re-pick, `44 dp` density scaling, priority tie-breaking for currently selected plate, and strict zero GPS mutation.
- **Camera Compatibility (`MapViewModel.kt` + `MapScreen.kt` + `MapViewModelTest`):** Audited `loadRoute`, `switchVariant`, `clearRoute`, `selectRouteVehicle`, `onUserMapGesture`, `enableVehicleAutoFollow`, and `toggleVehicleAutoFollow`. `MapViewModelTest` (`21/21` PASS) verifies `FOCUS_VEHICLE`, `FOLLOW_VEHICLE`, `USER_CONTROLLED`, `IDLE`, invalid-coordinate warning without camera hijack, and clean state reset on route/direction changes. `VehicleInterpolationEngine`, polling, and camera state machine were **not modified**.
