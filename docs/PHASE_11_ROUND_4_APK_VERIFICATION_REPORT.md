# Phase 11 — Round 4: APK Verification & Release Readiness Report

**Date:** 2026-11-12  
**Verified APK Path:** `E:\MultiGo Reworked\app\build\outputs\apk\release\app-release.apk` ([app-release.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/release/app-release.apk))

---

## 1. APK File & Manifest Metadata (`aapt2 dump badging`)

| Attribute | Verified Value |
| :--- | :--- |
| **File Exists** | Yes (`2026-11-12 15:22:47 ICT`) |
| **File Size** | **57,432,464 bytes** (~54.77 MiB) |
| **Package SHA-256** | `F98B13FF312C3C45971BA6996B662D95CC7D8409C50809F6FD50FF5F5973559E` |
| **Package Name (`applicationId`)** | `com.hcmcbustracker.app` |
| **Version Code / Name** | `versionCode='1'` / `versionName='1.0.0'` |
| **Compile / Target / Min SDK** | `compileSdkVersion='35'` / `targetSdkVersion='35'` / `minSdk=26` |
| **Application Label** | `HCMC Bus Tracker` |
| **Launchable Activity** | `com.hcmcbustracker.app.MainActivity` |
| **Declared Permissions** | `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS`, `ACCESS_WIFI_STATE`, `com.hcmcbustracker.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |

*(Confirmed readable and validly parsed by Android `aapt2.exe` from `build-tools\35.0.0` for Android Package Manager recognition.)*

---

## 2. Cryptographic Signature Verification (`apksigner verify --verbose --print-certs`)

Executed via `C:\Users\nmthu\AppData\Local\Android\Sdk\build-tools\35.0.0\apksigner.bat`:

- **Overall Result:** **`Verifies`** (exit code `0`)
- **Signature Schemes:**
  - APK Signature Scheme v2: **`true`**
  - v1 JAR signing: `false` (expected and valid for `minSdk = 26`)
  - v3 / v3.1 / v4: `false`
- **Signer #1 Public Certificate:**
  - **DN:** `CN=HCMC Bus Tracker, OU=Public Transit, O=HCMC DOT, L=Ho Chi Minh City, ST=Ho Chi Minh City, C=VN`
  - **Key Algorithm / Size:** `RSA`, `2048` bits
  - **Certificate SHA-256 Digest:** `0e295cd1c24b69ddb5ef1d7cdee25622675ba0f087d495613009c91620cfa8c5`
  - **Certificate SHA-1 Digest:** `6a0644c19b7a02ccbf2da7e28fc5250b0e1a5541`
  - **Public Key SHA-256 Digest:** `40cca3193573a041293ca4eb86ae094ddd83f5879dca74aac6be2dcc856d331c`

---

## 3. Release Packaging Contents & Feature Risk Inspection

ZIP inspection of [app-release.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/release/app-release.apk) confirms all runtime assets, DEX bytecode, native MapLibre binaries, and launcher resources are packaged cleanly:

| Component | Verified Entries Inside `app-release.apk` | Release Risk / Assessment |
| :--- | :--- | :--- |
| **Bytecode & R8 Impact** | `classes.dex` (`32,447,520 B`), `classes2.dex` (`10,634,744 B`), `classes3.dex` (`3,245,692 B`), `baseline.prof` (`7,445 B`) | `isMinifyEnabled = false` prevents R8 stripping/obfuscation of Kotlin Serialization DTOs, Room entities, Hilt bindings, or MapLibre classes. |
| **Offline Transit Database** | `assets/ebms_static_dataset.json` (`6,051,502 B`) + `BusTrackerDatabase` (`version = 3`) | Bundled static dataset intact for offline route/stop/search/favorites loading (`StaticDatasetManagerTest` 8/8 PASS, `BusRepositoryTest` 6/6 PASS, `RoomDaoTest` 5/5 PASS). |
| **MapLibre Native SDK & Styles** | `libmaplibre.so` + `libandroidx.graphics.path.so` across `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`; `assets/osm_stis_style.json` (`535 B`) | Full multi-ABI MapLibre native libraries present; marker/label bitmaps rendered programmatically in `MapScreen.kt` (not shrunk away). |
| **EBMS Network & Resiliency** | `EbmsApiService` (`https://apicms.ebms.vn/`), `SafeApiCall`, HTTP `408`/`429` handling | HTTPS enforced; `SafeApiCallTest` (6/6 PASS) & DTO parsing tests (5/5 PASS) verify offline/error handling logic on JVM. |
| **Tracking, Camera & UI Logic** | Round 2 & Round 3 features (`PositionCompensationEngine`, `VehicleInterpolationEngine`, `MapViewModel`, `MapScreen`) | All 159/159 unit tests pass and `lintVitalRelease` completed cleanly in `assembleRelease`. |

---

## 4. Physical / Emulator Installation & Unverified Runtime Scope

- **Device Check (`adb.exe devices`):** Returned empty `List of devices attached` (`0` connected emulators or physical Android phones in this environment).
- **Explicit Limitation:** Actual on-device APK installation (`adb install`), cold app launch, live MapLibre tile loading (`https://map.stis.vn`), live EBMS GPS telemetry fetching over cellular/Wi-Fi, Blue Dot runtime GPS permission UX, camera follow smoothness, and end-to-end search/favorites interaction **have NOT been executed on a physical device or emulator in Round 4**. Gradle `BUILD SUCCESSFUL` + static APK verification proves build integrity and valid v2 signing, NOT live hardware UI execution.
