# Phase 11 — Round 3: Map Camera Interaction & Auto-Follow Report

**Date:** 2026-11-12  
**Primary Files:** [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt), [MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt)

---

## 1. Camera State Machine Architecture

Implemented explicit `VehicleCameraMode` enum in [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt#L112-L146):

- **`IDLE`**: Default state; no active vehicle camera tracking (`clearTrackedVehicle`, `clearRoute`, `loadRoute`, `switchVariant`, or invalid target coordinates).
- **`FOCUS_VEHICLE`**: Single smooth center-and-zoom (`550 ms`, zoom `16.0` unless user is already within `15.2..17.2`) triggered via monotonic `cameraFocusRequestId: Long`.
- **`FOLLOW_VEHICLE`**: Automatically tracks live vehicle displacement when enabled via `"Theo dõi xe"`, emitting monotonic `cameraFollowRequestId: Long` updates **only when** coordinates are valid and moved `>= 12.0 m` (`FOLLOW_SIGNIFICANT_MOVE_METERS`).
- **`USER_CONTROLLED`**: Entered immediately upon user pan/pinch gesture (`REASON_API_GESTURE`) or tapping `"Đang theo dõi"` to pause; stops all automatic camera movements while keeping live telemetry updates active on `VehicleTrackingInfoCard`.

---

## 2. Fixing the Auto-Center Race & Same-Plate Re-Tap Bug

Previously, `LaunchedEffect(state.selectedVehiclePlate)` fired only when the plate string changed, silently failing when:
1. `maplibreMapInstance` or `state.trackedVehicle` was still `null` on initial composition, or
2. The user re-tapped the **same** already-selected vehicle plate (no string change).

### Solution Implemented
- [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt#L755-L830) maintains `cameraFocusRequestId: Long` (`_cameraFocusRequestId`) incremented on every `selectRouteVehicle`, `enableVehicleAutoFollow`, and valid `trackVehicle` completion.
- [MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L448-L492) keys `LaunchedEffect(state.cameraFocusRequestId, state.trackedVehicle, maplibreMapInstance)` and tracks `lastHandledFocusRequestId`:
  - If `maplibreMapInstance == null` or `trackedVehicle == null` when clicked, the `LaunchedEffect` simply returns without advancing `lastHandledFocusRequestId`.
  - As soon as `maplibreMapInstance` or `trackedVehicle` becomes ready, the effect re-runs and executes the camera animation (`CameraUpdateFactory.newLatLngZoom(..., 550)`).
  - Re-tapping the same license plate increments `cameraFocusRequestId`, forcing a clean re-center.

---

## 3. Auto-Follow Throttling, Gesture Override & Invalid-Coordinate Safety

- **Anti-Jitter & Anti-Loop Protection:** Both [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt#L653-L672) and [MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L473-L492) require displacement `>= 12.0 m` (`FOLLOW_SIGNIFICANT_MOVE_METERS`) before issuing a `500 ms` `CameraUpdateFactory.newLatLng` animation, leaving camera bearing and user zoom untouched during polling.
- **Gesture Priority:** MapLibre's `addOnCameraMoveStartedListener` detects `OnCameraMoveStartedListener.REASON_API_GESTURE` ([MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L281-L285)) and calls `viewModel.onUserMapGesture()`, transitioning `FOCUS_VEHICLE` / `FOLLOW_VEHICLE` -> `USER_CONTROLLED`. No polling update can reclaim the camera until the user taps `"Theo dõi xe"` again.
- **Invalid Coordinate Guard:** All camera entry points (`trackVehicle`, `selectRouteVehicle`, `enableVehicleAutoFollow`, polling follow sync, `MapScreen` `LaunchedEffect`s) validate coordinates against `isFinite()`, `!= (0.0, 0.0)`, and HCMC bounds `lat in 8.0..13.0, lng in 104.0..109.0`. Invalid inputs emit `cameraWarningMessage` and leave `cameraMode == IDLE` without moving the camera.

---

## 4. Verification Caveat

State transitions, race/re-tap `cameraFocusRequestId` behavior, `>= 12 m` follow throttling, gesture pause, variant switch reset, and invalid-coordinate guards were verified in Robolectric/JVM unit tests (`MapViewModelTest`). Live MapLibre gesture/animation smoothness on Android hardware/emulator was **not** executed due to `0` attached `adb` devices.
