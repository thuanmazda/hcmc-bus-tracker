# Phase 11 — Round 3: Test Failure & OOM Root Cause Analysis

**Date:** 2026-11-12  
**Scope:** `:feature-map:testDebugUnitTest` failures identified in `MapViewModelTest`  
**Status:** FIXED & VERIFIED (`159/159` unit tests PASS across `25` suites; `assembleDebug` PASS)

---

## 1. Failure Overview (Before Fix)

When running `.\gradlew.bat :feature-map:testDebugUnitTest --rerun`, two newly added Lượt 3 unit tests failed in [MapViewModelTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/MapViewModelTest.kt):

1. `trackVehicle with invalid path coordinates avoids auto-center and reports warning`  
   - **Exception:** `java.lang.AssertionError: No item was found` at `app.cash.turbine.ChannelKt.expectMostRecentItem(channel.kt:59)` triggered at `MapViewModelTest.kt:652`.
2. `camera state machine transitions between focus, follow, user gesture, and idle`  
   - **Exception:** `java.lang.OutOfMemoryError: Java heap space` after `96.872s` in the `-Xmx512m` Gradle Test Executor JVM.

---

## 2. Root Cause Analysis

### 2.1 Root Cause 1 — Assertion Error (`No item was found` at `MapViewModelTest.kt:652`)

1. **Test Dispatcher Synchronization (`StandardTestDispatcher` + `stateIn`):**  
   `MapViewModel.uiState` uses `.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = MapUiState())`. In `MapViewModelTest`, `Dispatchers.Main` is bound to `StandardTestDispatcher`.  
   After calling `viewModel.clearCameraWarning()` (line 651), the test immediately called `expectMostRecentItem()` without `testDispatcher.scheduler.runCurrent()`. Because `StandardTestDispatcher` queues coroutines rather than executing them eagerly, the `combine` / `stateIn` upstream pipeline had not yet emitted the updated `MapUiState` into Turbine's channel, causing `expectMostRecentItem()` to throw `AssertionError: No item was found` at `channel.kt:59`.
2. **Incomplete Coordinate & Path Guards in `MapViewModel`:**  
   - In `MapViewModel.trackVehicle` ([MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt#L990-L1107)), `_selectedVehiclePlate.value = plate` was assigned eagerly *before* verifying route geometry/stop coordinates, leaving stale selection state when tracking failed on invalid coordinates.
   - Path validation only checked `path.points.size >= 2`, without checking whether `stop` coordinates or individual polyline vertices contained `(0.0, 0.0)`, `NaN`, `Infinity`, or out-of-range coordinates (`lat !in -90.0..90.0` / `lng !in -180.0..180.0`).
   - In `MapViewModel.updateUserLocation` ([MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt#L915-L928)), receiving `(0.0, 0.0)`, `NaN`, or out-of-range coordinates set `_isUserLocationUnavailable.value = true` and returned without clearing the previously cached `_userLocation`, `_userBearing`, and `_userAccuracyMeters`.

### 2.2 Root Cause 2 — `OutOfMemoryError: Java heap space` (`96.872s`)

1. **Missed `runCurrent()` Triggered Early Exit Before Polling Cancellation:**  
   In `camera state machine transitions between focus, follow, user gesture, and idle`, calling `viewModel.selectRouteVehicle("50E61663")` (line 561) followed immediately by `expectMostRecentItem()` without `testDispatcher.scheduler.runCurrent()` caused Turbine to throw `AssertionError: No item was found` inside `viewModel.uiState.test { ... }`.
2. **Unbounded Virtual-Time Polling Loop During `runTest` Cleanup:**  
   Because the assertion failed *before* `viewModel.onMapPaused()` (line 613) executed, `routePollingJob` in `viewModelScope` remained active with `while (isActive) { ... delay(nextDelayMs) }` ([MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt#L584-L660)).  
   When exiting `runTest { ... }` (note `@After tearDown()` runs *after* `runTest` returns), `kotlinx-coroutines-test`'s `TestScope` drained pending tasks on the shared `TestCoroutineScheduler` via `advanceUntilIdle()`. That caused `while (isActive)` + `delay(10_000L)` to spin millions of virtual-time polling cycles, allocating `TrackedVehicleState`, `SimpleDateFormat`, `ArrayList`, and MockK invocation records until the `512 MB` test heap exhausted with `OutOfMemoryError: Java heap space`.

---

## 3. Fixes Applied

| File | Approx. Lines | Change Description |
| :--- | :--- | :--- |
| [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt) | L140–L368 | Added `cameraFollowRequestId: Long` to `MapUiState` and `CameraControlParams` so `FOLLOW_VEHICLE` only triggers camera updates when valid coordinates move `>= 12.0m` (`FOLLOW_SIGNIFICANT_MOVE_METERS`). |
| [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt) | L470–L560 | Reset `lastFollowTargetLatLng = null` and `_cameraMode = IDLE` consistently on `loadRoute`, `switchVariant`, `clearRoute`, and `clearTrackedVehicle`. |
| [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt) | L645–L670 | Synchronized `FOLLOW_VEHICLE` updates using `isValidCoordinate` and distance delta `>= 12.0m`; blocked updates during `USER_CONTROLLED` and emitted a warning instead of moving camera to invalid coordinates. |
| [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt) | L799–L856 | Added `isValidWgs84Coordinate` (`isFinite()`, `lat in -90.0..90.0`, `lng in -180.0..180.0`, `!= (0,0)`) alongside HCMC bounds `isValidCoordinate` (`lat in 8.0..13.0`, `lng in 104.0..109.0`). |
| [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt) | L915–L928 | Updated `updateUserLocation` to reject invalid WGS84 coordinates and explicitly clear `_userLocation`, `_userBearing`, and `_userAccuracyMeters` while marking `isUserLocationUnavailable = true`. |
| [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt) | L990–L1107 | Hardened `trackVehicle` to validate `stop` coordinates, `path.points.size >= 2`, all path vertices, and `interpolated` output before setting `_selectedVehiclePlate`, `_trackedVehicle`, or `FOCUS_VEHICLE`. |
| [MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt) | L448–L492 | Switched `FOLLOW_VEHICLE` `LaunchedEffect` to key on `state.cameraFollowRequestId` (`lastHandledFollowRequestId`) in addition to the `12.0m` distance check and `isValidCoordinate` guard. |
| [MapViewModelTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/MapViewModelTest.kt) | L508–L798 | Added `testDispatcher.scheduler.runCurrent()` before `expectMostRecentItem()`; wrapped polling tests in `try { ... } finally { viewModel.onMapPaused(); cancelAndIgnoreRemainingEvents() }`; added coverage for `FOLLOW` suppression when stationary vs moved `>= 12m`, out-of-range lat/lng, `NaN`, `Infinity`, `(0,0)`, empty path, and invalid path vertices. |

*(No changes were made to `PositionCompensationEngine`, `VehicleInterpolationEngine`, EBMS APIs, polling intervals, Room schema, offline transit DB, `applicationId`, or signing configuration.)*

---

## 4. Verification Results (Narrow → Broad)

| Step | Scope | Result | Notes |
| :--- | :--- | :--- | :--- |
| 1 | `trackVehicle with invalid path coordinates...` & `trackVehicle rejects out of range coordinates...` | **PASS** (1m 08s full build+test) | Both invalid-coordinate suites passed cleanly (`exit code 0`). |
| 2 | `camera state machine transitions...` & `follow mode suppresses...` | **PASS** (45s) | Zero OOM; verified `IDLE ↔ FOCUS ↔ FOLLOW ↔ USER_CONTROLLED` and `>= 12m` follow threshold. |
| 3 | Full `MapViewModelTest` (`21` tests) | **PASS** (45s Gradle; `9.437s` test suite) | `21/21` passed, `0` failed, `0` skipped. |
| 4 | Full `:feature-map:testDebugUnitTest --rerun` (`44` tests / `5` suites) | **PASS** (47s) | `44/44` passed, `0` failed, `0` skipped. |
| 5 | Project-wide `.\gradlew.bat testDebugUnitTest --rerun` (`159` tests / `25` suites) | **PASS** (2m 37s) | `159/159` passed, `0` failed, `0` skipped. |
| 6 | `.\gradlew.bat assembleDebug` | **PASS** (1m 13s) | Built [app-debug.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/debug/app-debug.apk) (`64,411,127` bytes). |
