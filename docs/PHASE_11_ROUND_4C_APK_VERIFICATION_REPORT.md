# Phase 11 — Round 4C: Release APK Verification Report (Post-Round 4B)

**Date:** 2026-11-12  
**Verified Artifact:** `E:\MultiGo Reworked\app\build\outputs\apk\release\app-release.apk` ([app-release.apk](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/release/app-release.apk))

---

## 1. File Integrity & Manifest Metadata (`aapt2.exe dump badging`)

| Field | Verified Value |
| :--- | :--- |
| **Existence & Timestamp** | Exists — `2026-11-12 16:08:56 ICT` (fresh post-Round 4B build) |
| **File Size** | **57,465,232 bytes** (~54.80 MiB) |
| **Package SHA-256** | `52218D578CFAA023D8337DF913319AB64FBC45EE1B06D826058321856F912065` |
| **Package Name (`applicationId`)** | `com.hcmcbustracker.app` |
| **`versionCode` / `versionName`** | `'1'` / `'1.0.0'` |
| **`compileSdkVersion` / `targetSdkVersion`** | `'35'` / `'35'` (`minSdk = 26`) |
| **Application Label** | `'HCMC Bus Tracker'` |
| **Launchable Activity** | `'com.hcmcbustracker.app.MainActivity'` |
| **Permissions** | `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS`, `ACCESS_WIFI_STATE`, `com.hcmcbustracker.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |

*(Cleanly parsed by `C:\Users\nmthu\AppData\Local\Android\Sdk\build-tools\35.0.0\aapt2.exe`, confirming Android Package Manager compatibility.)*

---

## 2. Cryptographic Signature Verification (`apksigner.bat verify --verbose --print-certs`)

- **Overall Result:** **`Verifies`** (exit code `0`)
- **APK Signature Scheme v2:** **`true`** (`v1 = false` expected for `minSdk = 26`; `v3 / v3.1 / v4 = false`)
- **Number of Signers:** `1` (matches existing Round 4 release keystore untouched)
- **Signer #1 Certificate DN:** `CN=HCMC Bus Tracker, OU=Public Transit, O=HCMC DOT, L=Ho Chi Minh City, ST=Ho Chi Minh City, C=VN`
- **Key Algorithm / Size:** `RSA`, `2048` bits
- **Certificate SHA-256 Digest:** `0e295cd1c24b69ddb5ef1d7cdee25622675ba0f087d495613009c91620cfa8c5`
- **Public Key SHA-256 Digest:** `40cca3193573a041293ca4eb86ae094ddd83f5879dca74aac6be2dcc856d331c`

---

## 3. Packaged Round 4B & Runtime Assets Inspection

| Archive Entry | Size (Bytes) | Verification Note |
| :--- | :---: | :--- |
| `classes.dex` | `32,447,520` | Primary DEX bytecode |
| `classes2.dex` | `10,505,712` | Secondary DEX bytecode |
| `classes3.dex` | `3,453,536` | Grown `+207,844 B` vs pre-4B (`3,245,692 B`), confirming Round 4B classes & Hilt bindings compiled into Release |
| `assets/dexopt/baseline.prof` | `8,271` | Baseline profile updated for Release ART dexopt |
| `assets/ebms_static_dataset.json` | `6,051,502` | Offline route/stop/polyline dataset bundled |
| `assets/osm_stis_style.json` | `535` | MapLibre OSM style JSON bundled |
| `lib/arm64-v8a/libmaplibre.so` | `11,378,984` | MapLibre native binary present (plus `armeabi-v7a`, `x86`, `x86_64`) |
| `AndroidManifest.xml` | `7,296` | Binary manifest intact |

---

## 4. Physical Device / Emulator Check & Limitations

- **Emulator / Physical Device (`adb.exe devices`):** Returned empty `List of devices attached` (`0` connected devices/emulators).
- **Unverified Runtime Scope:** On-device APK installation (`adb install`), cold startup, offline Room seeding on real flash storage, live EBMS concurrent polling over cellular/Wi-Fi, live MapLibre camera follow, and interactive UI QA have **not** been executed in this environment. Build/signature/unit checks verify packaging integrity, not live device runtime execution.
