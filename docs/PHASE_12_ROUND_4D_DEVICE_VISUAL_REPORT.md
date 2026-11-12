# PHASE 12 – ROUND 4D: DEVICE VISUAL ACCEPTANCE REPORT

**Project:** HCMC Bus Tracker (`MultiGo Reworked`)  
**Phase / Round:** Phase 12 – Round 4D (Device Visual Acceptance — Executed on Real Hardware)  
**Date:** 2026-11-12  
**Acceptance Result:** **PASS (Real Device Visual & Interaction Verification Complete)**

---

## A. Target Device & Environment Verified

- **Device:** Xiaomi **M2102J2SC** (Serial: `5e4b5f4d`, Product: `thyme`)
- **OS / API:** Android `13` (SDK `33`)
- **Display Metrics:** Physical `1080 × 2340` px, Density **`440 dpi`** (`scaleFactor = 2.75x`, intermediate between `xhdpi 320 dpi` and `xxhdpi 480 dpi`)
- **GPU Renderer:** `Adreno (TM) 650` (confirmed via `Mbgl` logcat)
- **Installed Build:** `app/build/outputs/apk/debug/app-debug.apk` (`com.hcmcbustracker.app.debug`, installed after fixing Round 4E & MapLibre `icon-image` expression)
- **Protection Compliance:** Zero changes to EBMS API, `PositionCompensationEngine`, `VehicleInterpolationEngine`, `GlobalPollingCoordinator`, `AdaptiveConcurrencyController`, `SharedStopCoalescer`, `VehicleDataMerger`, `MapViewModel`, or Room database.

---

## B. Runtime Hardware Bug Caught & Fixed During Device QA

During live device verification on `M2102J2SC`:
1. **Observed Symptom:** Initial live capture showed crisp Top-Down Coaches, stop clusters, Blue Dot, and Selected Halo, **but license plate pill badges did not render** on either normal or selected buses.
2. **Verified Root Cause via `adb logcat`:**
   `E Mbgl : {acker.app.debug}[JNI]: Error setting property: icon-image "zoom" expression may only be used as input to a top-level "step" or "interpolate" expression.`
   Nesting `Expression.step(Expression.zoom(), ...)` inside `Expression.switchCase(...)` inside `VEHICLE_LABEL_LAYER_ID` violated MapLibre GL Native's camera-expression rule.
3. **Fix Applied in [MapLayerManager.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/render/MapLayerManager.kt):** Extracted `buildLabelIconImageExpression()` placing `Expression.step(Expression.zoom(), ...)` at the expression root wrapping `switchCase(is_selected, full, short)` for `zoom < 14.8f` and `full_label_icon_id` for `zoom >= 14.8f`, plus unit test regression check in [MapLayerManagerTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/render/MapLayerManagerTest.kt).
4. **Re-Verification on `M2102J2SC`:** Rebuilt, reinstalled, relaunched Route `08`, and verified **0 `Error setting property` logs**; both SHORT (`784.27`, `782.93`, `786.14`, `783.49`) and FULL (`50E-784.27`, `50E-786.14`) plate pills rendered cleanly below every bus with `0` overlap over the coach body.

---

## C. Marker Sizing & Visual Acceptance Matrix on `440 dpi` (`2.75x`) Hardware

| Level / Aspect | Normal Coach (`440 dpi`) | Selected Coach + Halo (`440 dpi`) | Visual Inspection Result |
| :--- | :--- | :--- | :--- |
| **1. Bitmap Canvas** | `44.0 dp` → **`121 × 121 px`** (`density = 440`) | `60.0 dp` → **`165 × 165 px`** (`density = 440`) | Exact scaling verified |
| **2. Opaque Visual Box (Round 4E)** | Cased coach + heading nose: `~36.78 dp` height (`0` edge clip) | Inner coach `49.82 dp`, Amber Halo **`58.8 dp`** | Clean perimeter margins |
| **3. MapLibre Final Render** | `34.9 / 36.8 / 38.6 dp` (`low / mid / high` zoom coach height) | Halo `55.9 / 58.8 / 61.7 dp` (`low / mid / high` zoom) | Proportions match MultiGo-style reference cleanly |
| **4. Live Visual Clarity on FPT STIS** | Unmistakably larger & structurally bus-shaped vs faint grey road one-way arrows (`04_zoom_high_follow.png`, `07_single_bus_high_zoom.png`) | Crimson coach + `#F59E0B` halo stands out immediately above route & stop layers | **PASS** |

---

## D. Live Interaction Verification Results on `M2102J2SC`

- **Z-Order Hierarchy (`MapLayerManager`):** Confirmed visually on screen that Route Casing/Line < Stop Circles & Clusters < Normal Buses < Selected Bus < Horizontal License Plate Labels < User Location Accuracy Ring & Blue Dot.
- **`VehicleOverlapPickerSheet`:** Tapped 3 stacked buses on Route `08` → Material 3 Bottom Sheet opened listing `"Chọn xe đang gần nhau (3 xe)"` (`50E-784.27`, `50E-782.93`, `50E-784.13`) with untouched coordinates, speed, ETA distance, status, and GPS latency badge; selecting `50E-784.27` closed the sheet cleanly and focused camera on the exact bus.
- **Camera Follow & Gestures:** Tapped `"Theo dõi xe"` → transitioned to `"Đang theo dõi"`, dragged map to verify user-gesture pause, tapped follow again → smoothly re-centered on `50E-784.27` (`zoom ~17`) without camera jitter.
- **Direction Switch (`Lượt đi` ⇄ `Lượt về`):** Tapped swap direction on Route `08` → cleanly cleared previous selection, switched polyline to coral/inbound (`23 xe`), and updated buses cleanly with 0 ghost markers or crashes.
