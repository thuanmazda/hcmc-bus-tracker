# Phase 11 — Round 4C: Release Build Report (Post-Round 4B)

**Date:** 2026-11-12  
**Mode:** Release Packaging & Validation Only (Zero new features or code modifications)  
**Deliverable APK:** [app-release.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/release/app-release.apk)

---

## 1. Source State & Round 4B Freshness Check

- **Workspace & VCS Check:** `e:\MultiGo Reworked` (`git status --short` returned `fatal: not a git repository`). No code was altered in Round 4C; source state is identical to verified Round 4B.
- **Included Round 2 / 3 / 4B Features Confirmed in Build:**
  - **Round 2:** 5-timestamp end-to-end GPS tracking, bounded adaptive delay compensation (`0–120s` physical, `120–190s` attenuated tail, `> 190s` / frozen hold), 6 arrival statuses, smoothed bearing.
  - **Round 3:** Split `VEHICLE_ARROW_LAYER_ID` + upright `VEHICLE_LABEL_LAYER_ID`, Google Maps–style Blue Dot phone location, `cameraFocusRequestId` & `cameraFollowRequestId` + `VehicleCameraMode` state machine, compact `VehicleTrackingInfoCard`.
  - **Round 4B:** `AdaptivePollingConfig`, `EndpointCapabilityRegistry`, `GpsSourceAdapter`, `VehicleDataMerger`, `AdaptiveConcurrencyController` (AIMD `C = 1..4`), `SharedStopCoalescer`, `GlobalPollingCoordinator`, jittered backoff, and multi-route coalescing in `BusRepositoryImpl`.
- **No Stale Release Artifact Reuse:**
  - Previous pre-4B APK (`15:22:47`, `57,432,464` B) was superseded cleanly: `assembleRelease` executed `107` tasks across `:core-model`, `:core-network`, `:core-database`, `:feature-map`, `:feature-prediction`, `:app:lintVitalRelease`, and `:app:packageRelease`, outputting a fresh APK at `16:08:56` (`57,465,232` B, `classes3.dex` expanded cleanly from `3,245,692` B to `3,453,536` B).

---

## 2. Release Configuration, R8/ProGuard & Signing Audit

| Setting | Verified Configuration | Status |
| :--- | :--- | :---: |
| **`applicationId`** | `com.hcmcbustracker.app` (unchanged) | Preserved |
| **`versionCode` / `versionName`** | `1` / `1.0.0` | Verified |
| **SDK Targets** | `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35` | Verified |
| **Release Signing** | `keystore.properties` → existing `release-keystore/hcmc-bus-tracker-release.jks` (`2,323` B, gitignored, no secrets in code/reports) | Verified |
| **R8 / ProGuard / Shrinking** | `isMinifyEnabled = false` in [app/build.gradle.kts](file:///e:/MultiGo%20Reworked/app/build.gradle.kts); keep rules preserved in [proguard-rules.pro](file:///e:/MultiGo%20Reworked/app/proguard-rules.pro) | Safe (no stripping) |
| **Network Security** | All EBMS APIs (`https://apicms.ebms.vn/`) & OSM tiles use HTTPS | Verified |
| **Room & Offline Assets** | `BusTrackerDatabase` (`version = 3`), `assets/ebms_static_dataset.json` (`6,051,502` B), `assets/osm_stis_style.json` (`535` B) | Packaged |

### Round 4B Concurrency & Leak Review for Release Build
- Reviewed [AdaptiveConcurrencyController.kt](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/polling/AdaptiveConcurrencyController.kt), [GlobalPollingCoordinator.kt](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/polling/GlobalPollingCoordinator.kt), [SharedStopCoalescer.kt](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/polling/SharedStopCoalescer.kt), and [VehicleDataMerger.kt](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/VehicleDataMerger.kt).
- Hilt `@Singleton` injection cleanly binds `GlobalPollingCoordinator.@Inject constructor()` and `BusRepositoryImpl`; no reflection or stripped DTO fields affect concurrency classes because `isMinifyEnabled = false` and `@Serializable` rules remain intact.

---

## 3. Verification Pipeline Results

*(Zero source modifications occurred across steps 1–7.)*

| Step | Command | Result | Summary |
| :---: | :--- | :---: | :--- |
| 1 | `.\gradlew.bat :core-model:testDebugUnitTest --rerun` | **PASS** | `35s` — includes `VehicleDataMergerTest` (4/4) & compensation tests (20/20) |
| 2 | `.\gradlew.bat :core-network:testDebugUnitTest --rerun` | **PASS** | `56s` — includes AIMD, Coalescer, Coordinator & Benchmark suites (11/11) |
| 3 | `.\gradlew.bat :core-database:testDebugUnitTest --rerun` | **PASS** | `2m 1s` — includes `MultiRoutePollingTest` (2/2) & Room/Static DB tests |
| 4 | `.\gradlew.bat :feature-map:testDebugUnitTest --rerun` | **PASS** | `1m 13s` — all `44/44` map tests PASS |
| 5 | `.\gradlew.bat testDebugUnitTest --rerun` | **PASS** | `3m 4s` — **176 / 176 PASS** across **31 suites** (`0` fail, `0` skipped) |
| 6 | `.\gradlew.bat assembleDebug` | **PASS** | `49s` — [app-debug.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/debug/app-debug.apk) (`64,411,127` B) |
| 7 | `.\gradlew.bat assembleRelease` | **PASS** | `2m 52s` — `383` tasks (`107` executed, `lintVitalRelease` + `packageRelease` PASS) → [app-release.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/release/app-release.apk) (`57,465,232` B) |
