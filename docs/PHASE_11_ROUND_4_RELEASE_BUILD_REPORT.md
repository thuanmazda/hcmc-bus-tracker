# Phase 11 — Round 4: Release Build Report

**Date:** 2026-11-12  
**Project:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Deliverable APK:** [app-release.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/release/app-release.apk)

---

## 1. Pre-Release Audit & Scope Compliance

Reviewed all Round 2 and Round 3 reports ([Round 2 Implementation](file:///e:/MultiGo%20Reworked/docs/PHASE_11_ROUND_2_IMPLEMENTATION_REPORT.md), [Round 2 Unit Tests](file:///e:/MultiGo%20Reworked/docs/PHASE_11_ROUND_2_UNIT_TEST_REPORT.md), [Round 3 Failure Analysis](file:///e:/MultiGo%20Reworked/docs/PHASE_11_ROUND_3_TEST_FAILURE_ANALYSIS.md), [Round 3 Map UI](file:///e:/MultiGo%20Reworked/docs/PHASE_11_ROUND_3_MAP_UI_REPORT.md), [Round 3 Camera Interaction](file:///e:/MultiGo%20Reworked/docs/PHASE_11_ROUND_3_CAMERA_INTERACTION_REPORT.md), [Round 3 Test Report](file:///e:/MultiGo%20Reworked/docs/PHASE_11_ROUND_3_TEST_REPORT.md)) and build configurations:

- **Application ID & Versioning ([app/build.gradle.kts](file:///e:/MultiGo%20Reworked/app/build.gradle.kts)):**
  - `namespace` / `applicationId`: `com.hcmcbustracker.app` (preserved unchanged; `.debug` suffix on `debug` buildType only)
  - `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`
  - `versionCode = 1`, `versionName = "1.0.0"`
  - Kotlin / Java target: `JavaVersion.VERSION_21` (`jvmTarget = "21"`)
- **Modules Audited:** `:app`, `:core-model`, `:core-network`, `:core-database`, `:core-designsystem`, `:feature-map`, `:feature-prediction`, `:feature-route`, `:feature-search`.
- **Network Security & Endpoints:**
  - EBMS API base URL ([EbmsApiService.kt](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/api/EbmsApiService.kt)): `https://apicms.ebms.vn/` (HTTPS).
  - Tile providers ([TileProvider.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/tile/TileProvider.kt)): `https://map.stis.vn/bright/{z}/{x}/{y}.png`, `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, `https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png` (all HTTPS).
- **Room & Offline Dataset:**
  - [BusTrackerDatabase.kt](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/BusTrackerDatabase.kt): `version = 3` (`hcmc_bus_tracker.db`), preserved unchanged.
  - Bundled static transit asset verified inside APK: `assets/ebms_static_dataset.json` (`6,051,502` bytes) and `assets/osm_stis_style.json` (`535` bytes).
- **Preserved Components (Zero Changes in Round 4):**
  - No source code, Room schema, offline transit dataset, EBMS endpoint/polling interval, `PositionCompensationEngine`, `VehicleInterpolationEngine`, `applicationId`, or keystore/signing config files were altered during Round 4. Only the two Round 4 report files in `docs/` were created.

---

## 2. Release Signing & R8/ProGuard Configuration

- **Keystore & Secrets Hygiene:**
  - [app/build.gradle.kts](file:///e:/MultiGo%20Reworked/app/build.gradle.kts#L12-L124) reads signing metadata from `keystore.properties` (present locally at project root) or environment variables and fails `assembleRelease` if signing config or keystore is missing.
  - Existing release keystore `release-keystore/hcmc-bus-tracker-release.jks` (`2,323` bytes) was used untouched.
  - Both `keystore.properties` and `*.jks` / `release-keystore/` are excluded in [.gitignore](file:///e:/MultiGo%20Reworked/.gitignore#L19-L28); no credentials exist in source code or reports.
- **Public Signing Identity (from `apksigner`):**
  - **Certificate DN:** `CN=HCMC Bus Tracker, OU=Public Transit, O=HCMC DOT, L=Ho Chi Minh City, ST=Ho Chi Minh City, C=VN`
  - **Key Algorithm:** RSA `2048`-bit
  - **Certificate SHA-256 Digest:** `0e295cd1c24b69ddb5ef1d7cdee25622675ba0f087d495613009c91620cfa8c5`
- **R8 / ProGuard & Resource Shrinking Status:**
  - `buildTypes.release` currently sets `isMinifyEnabled = false` (no R8 class obfuscation or resource shrinking applied).
  - [proguard-rules.pro](file:///e:/MultiGo%20Reworked/app/proguard-rules.pro) retains explicit keep rules for `@kotlinx.serialization.Serializable`, Retrofit/OkHttp, `RoomDatabase` subclasses, `org.maplibre.android.**`, and `androidx.lifecycle.ViewModel` subclasses in case minification is enabled in a future release.

---

## 3. Unit Tests, Lint & Build Execution Results

*(Zero code changes occurred between unit test runs, `assembleDebug`, and `assembleRelease`.)*

| Step | Command | Result | Exact Summary |
| :--- | :--- | :---: | :--- |
| 1 | `.\gradlew.bat :feature-map:testDebugUnitTest --rerun` | **PASS** | **44 / 44 tests** passed across 5 suites (`0` failures, `0` errors, `0` skipped; `47s`) |
| 2 | `.\gradlew.bat testDebugUnitTest --rerun` | **PASS** | **159 / 159 tests** passed across 25 suites (`0` failures, `0` errors, `0` skipped; `2m 37s`) |
| 3 | `.\gradlew.bat assembleDebug` | **PASS** | [app-debug.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/debug/app-debug.apk) (`64,411,127` bytes; `1m 13s`) |
| 4 | `.\gradlew.bat assembleRelease` | **PASS** | `383` tasks (`97` executed, `286` up-to-date; including `:app:lintVitalRelease` & `:app:packageRelease`; `5m 3s`) |

---

## 4. Modified / Added Files in Round 4

- Added [PHASE_11_ROUND_4_RELEASE_BUILD_REPORT.md](file:///e:/MultiGo%20Reworked/docs/PHASE_11_ROUND_4_RELEASE_BUILD_REPORT.md)
- Added [PHASE_11_ROUND_4_APK_VERIFICATION_REPORT.md](file:///e:/MultiGo%20Reworked/docs/PHASE_11_ROUND_4_APK_VERIFICATION_REPORT.md)
- Built artifact [app-release.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/release/app-release.apk)
