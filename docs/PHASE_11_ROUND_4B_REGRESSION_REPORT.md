# Phase 11 — Round 4B: Full-Project Regression & Build Verification Report

**Date:** 2026-11-12  
**Post-Final-Edit Verification Commands:**
1. `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-model:testDebugUnitTest --rerun` → **PASS** (`35s`)
2. `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-network:testDebugUnitTest --rerun` → **PASS** (`56s`)
3. `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-database:testDebugUnitTest --rerun` → **PASS** (`2m 1s`)
4. `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :feature-map:testDebugUnitTest --rerun` → **PASS** (`1m 13s`)
5. `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest --rerun` → **PASS** (`3m 4s`)
6. `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug` → **PASS** (`49s`)

---

## 1. Exact Per-Suite Unit Test Results (`176 / 176 PASS` across `31` Suites)

| Suite Name | Tests | Failures | Errors | Skipped | Time (s) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `com.hcmcbustracker.app.NavigationTabTest` | 2 | 0 | 0 | 0 | 0.008 |
| `com.hcmcbustracker.core.database.BusRepositoryTest` | 6 | 0 | 0 | 0 | 10.525 |
| `com.hcmcbustracker.core.database.MultiRoutePollingTest` | 2 | 0 | 0 | 0 | 0.360 |
| `com.hcmcbustracker.core.database.RoomDaoTest` | 5 | 0 | 0 | 0 | 0.420 |
| `com.hcmcbustracker.core.database.StaticDatasetManagerTest` | 8 | 0 | 0 | 0 | 67.199 |
| `com.hcmcbustracker.core.model.PositionCompensationEngineTest` | 20 | 0 | 0 | 0 | 0.057 |
| `com.hcmcbustracker.core.model.PredictionModelTest` | 2 | 0 | 0 | 0 | 0.029 |
| `com.hcmcbustracker.core.model.RouteNumberComparatorTest` | 6 | 0 | 0 | 0 | 0.040 |
| `com.hcmcbustracker.core.model.RouteVehicleAggregatorTest` | 7 | 0 | 0 | 0 | 0.024 |
| `com.hcmcbustracker.core.model.VehicleDataMergerTest` | 4 | 0 | 0 | 0 | 0.009 |
| `com.hcmcbustracker.core.model.VietnameseNormalizerTest` | 4 | 0 | 0 | 0 | 0.019 |
| `com.hcmcbustracker.core.network.BusStopDtoParsingTest` | 2 | 0 | 0 | 0 | 0.119 |
| `com.hcmcbustracker.core.network.polling.AdaptiveConcurrencyControllerTest` | 3 | 0 | 0 | 0 | 0.011 |
| `com.hcmcbustracker.core.network.polling.GlobalPollingCoordinatorTest` | 3 | 0 | 0 | 0 | 0.178 |
| `com.hcmcbustracker.core.network.polling.PollingBenchmarkTest` | 1 | 0 | 0 | 0 | 0.732 |
| `com.hcmcbustracker.core.network.polling.SharedStopCoalescerTest` | 4 | 0 | 0 | 0 | 0.132 |
| `com.hcmcbustracker.core.network.PredictionDtoParsingTest` | 1 | 0 | 0 | 0 | 0.024 |
| `com.hcmcbustracker.core.network.RouteDtoParsingTest` | 2 | 0 | 0 | 0 | 0.015 |
| `com.hcmcbustracker.core.network.SafeApiCallTest` | 6 | 0 | 0 | 0 | 0.230 |
| `com.hcmcbustracker.feature.map.HomeViewModelTest` | 2 | 0 | 0 | 0 | 2.152 |
| `com.hcmcbustracker.feature.map.MapViewModelTest` | 21 | 0 | 0 | 0 | 6.441 |
| `com.hcmcbustracker.feature.map.RoutePathGeoJsonConverterTest` | 7 | 0 | 0 | 0 | 0.238 |
| `com.hcmcbustracker.feature.map.TileProviderTest` | 3 | 0 | 0 | 0 | 0.021 |
| `com.hcmcbustracker.feature.map.VehicleInterpolationEngineTest` | 11 | 0 | 0 | 0 | 0.014 |
| `com.hcmcbustracker.feature.prediction.notification.BusArrivalNotificationManagerTest` | 5 | 0 | 0 | 0 | 2.623 |
| `com.hcmcbustracker.feature.prediction.PersonalViewModelTest` | 9 | 0 | 0 | 0 | 4.019 |
| `com.hcmcbustracker.feature.prediction.PredictionViewModelTest` | 12 | 0 | 0 | 0 | 0.435 |
| `com.hcmcbustracker.feature.route.RouteDetailViewModelTest` | 3 | 0 | 0 | 0 | 2.440 |
| `com.hcmcbustracker.feature.route.RoutesViewModelTest` | 4 | 0 | 0 | 0 | 0.105 |
| `com.hcmcbustracker.feature.search.SearchViewModelTest` | 5 | 0 | 0 | 0 | 5.049 |
| `com.hcmcbustracker.feature.search.StopsViewModelTest` | 4 | 0 | 0 | 0 | 0.083 |
| **TOTAL** | **176** | **0** | **0** | **0** | **103.854** |

---

## 2. Build & Preservation Verification

- **Debug APK Built:** [app-debug.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/debug/app-debug.apk) — **`64,411,127` bytes** (`16:03:00 ICT`), exit code `0`.
- **Unmodified Protected Components Verified Clean:**
  - `PositionCompensationEngine`, `PositionCompensationConfig`, `ArrivalStatusThresholds`, `VehicleInterpolationEngine` (all 31 engine tests PASS unchanged).
  - `MapScreen.kt`, camera state machine (`21/21` `MapViewModelTest` PASS), GPS Blue Dot, GeoJSON converters (`7/7` PASS).
  - `BusTrackerDatabase` (`version = 3`), offline transit dataset (`StaticDatasetManagerTest` `8/8`, `RoomDaoTest` `5/5`, `BusRepositoryTest` `6/6` PASS).
  - `applicationId` (`com.hcmcbustracker.app`) and release signing configuration untouched.
- **Live Device Check:** `adb.exe devices` showed `List of devices attached` empty (`0` emulators/phones connected), so live on-device diagnostics execution remains unverified on hardware and is not claimed as device-tested.
