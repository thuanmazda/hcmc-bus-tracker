# Phase 11 — Round 3: Unit Test & Build Verification Report

**Date:** 2026-11-12  
**Commands Executed (Post-Final-Edit):**
1. `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest --rerun`
2. `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug`
3. `"$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices`

---

## 1. Summary Results

| Check | Command | Outcome | Counts / Artifact Details |
| :--- | :--- | :--- | :--- |
| Full Project Unit Tests | `.\gradlew.bat testDebugUnitTest --rerun` | **PASS** (2m 37s) | **159 / 159 passed** across **25 suites** (`0` failed, `0` errors, `0` skipped) |
| Map Module Unit Tests | Included in `testDebugUnitTest --rerun` (& isolated `:feature-map:testDebugUnitTest --rerun`, 47s) | **PASS** | **44 / 44 passed** across **5 suites** (`0` failed, `0` errors, `0` skipped) |
| Debug APK Build | `.\gradlew.bat assembleDebug` | **PASS** (1m 13s) | [app-debug.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/debug/app-debug.apk) — **64,411,127 bytes** |
| Emulator / Device Check | `adb.exe devices` | **NONE ATTACHED** | `List of devices attached` was empty — live emulator UI screenshots/QA pending Lượt 4 / device attachment |

---

## 2. Exact Per-Suite Unit Test Breakdown (`testDebugUnitTest --rerun`)

| Test Suite | Tests | Failures | Errors | Skipped | Time (s) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `com.hcmcbustracker.app.NavigationTabTest` | 2 | 0 | 0 | 0 | 0.007 |
| `com.hcmcbustracker.core.database.BusRepositoryTest` | 6 | 0 | 0 | 0 | 14.684 |
| `com.hcmcbustracker.core.database.RoomDaoTest` | 5 | 0 | 0 | 0 | 0.633 |
| `com.hcmcbustracker.core.database.StaticDatasetManagerTest` | 8 | 0 | 0 | 0 | 17.012 |
| `com.hcmcbustracker.core.model.PositionCompensationEngineTest` | 20 | 0 | 0 | 0 | 0.069 |
| `com.hcmcbustracker.core.model.PredictionModelTest` | 2 | 0 | 0 | 0 | 0.033 |
| `com.hcmcbustracker.core.model.RouteNumberComparatorTest` | 6 | 0 | 0 | 0 | 0.055 |
| `com.hcmcbustracker.core.model.RouteVehicleAggregatorTest` | 7 | 0 | 0 | 0 | 0.038 |
| `com.hcmcbustracker.core.model.VietnameseNormalizerTest` | 4 | 0 | 0 | 0 | 0.027 |
| `com.hcmcbustracker.core.network.BusStopDtoParsingTest` | 2 | 0 | 0 | 0 | 0.134 |
| `com.hcmcbustracker.core.network.PredictionDtoParsingTest` | 1 | 0 | 0 | 0 | 0.033 |
| `com.hcmcbustracker.core.network.RouteDtoParsingTest` | 2 | 0 | 0 | 0 | 0.016 |
| `com.hcmcbustracker.core.network.SafeApiCallTest` | 6 | 0 | 0 | 0 | 0.501 |
| `com.hcmcbustracker.feature.map.HomeViewModelTest` | 2 | 0 | 0 | 0 | 6.993 |
| `com.hcmcbustracker.feature.map.MapViewModelTest` | 21 | 0 | 0 | 0 | 9.437 |
| `com.hcmcbustracker.feature.map.RoutePathGeoJsonConverterTest` | 7 | 0 | 0 | 0 | 0.291 |
| `com.hcmcbustracker.feature.map.TileProviderTest` | 3 | 0 | 0 | 0 | 0.026 |
| `com.hcmcbustracker.feature.map.VehicleInterpolationEngineTest` | 11 | 0 | 0 | 0 | 0.020 |
| `com.hcmcbustracker.feature.prediction.notification.BusArrivalNotificationManagerTest` | 5 | 0 | 0 | 0 | 3.680 |
| `com.hcmcbustracker.feature.prediction.PersonalViewModelTest` | 9 | 0 | 0 | 0 | 4.004 |
| `com.hcmcbustracker.feature.prediction.PredictionViewModelTest` | 12 | 0 | 0 | 0 | 0.966 |
| `com.hcmcbustracker.feature.route.RouteDetailViewModelTest` | 3 | 0 | 0 | 0 | 3.583 |
| `com.hcmcbustracker.feature.route.RoutesViewModelTest` | 4 | 0 | 0 | 0 | 0.199 |
| `com.hcmcbustracker.feature.search.SearchViewModelTest` | 5 | 0 | 0 | 0 | 3.600 |
| `com.hcmcbustracker.feature.search.StopsViewModelTest` | 4 | 0 | 0 | 0 | 0.194 |
| **TOTAL (25 Suites)** | **159** | **0** | **0** | **0** | **66.235** |

---

## 3. Lượt 3 Coverage Matrix (`MapViewModelTest` & `RoutePathGeoJsonConverterTest`)

| Scenario | Verified By | Status |
| :--- | :--- | :---: |
| Split `arrow_icon_id` & `label_icon_id`, upright label metadata | [RoutePathGeoJsonConverterTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/RoutePathGeoJsonConverterTest.kt) | PASS (JVM) |
| Directional bearing when `hasValidHeading = true` vs `0.0` diamond when `false` | `RoutePathGeoJsonConverterTest.kt` | PASS (JVM) |
| Invalid `(0,0)` / out-of-range / `NaN` / `Infinity` vehicle or path points rejected | [MapViewModelTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/MapViewModelTest.kt) | PASS (Robolectric) |
| `IDLE → FOCUS_VEHICLE → FOLLOW_VEHICLE → USER_CONTROLLED → FOLLOW_VEHICLE → IDLE` | `MapViewModelTest.kt` | PASS (Robolectric) |
| Re-tapping same plate increments `cameraFocusRequestId` | `MapViewModelTest.kt` | PASS (Robolectric) |
| Stationary vehicle `< 12m` suppresses redundant `cameraFollowRequestId` emissions; `>= 12m` updates | `MapViewModelTest.kt` | PASS (Robolectric) |
| Phone GPS `updateUserLocation` guards (`(0,0)`, `[-90,90]`, `[-180,180]`, `NaN`, `Infinity`, permission denied) | `MapViewModelTest.kt` | PASS (Robolectric) |
| MapLibre rendering, `REASON_API_GESTURE` listener, bitmap appearance on screen | Not run (no attached `adb` device/emulator) | **Pending hardware/emulator** |
