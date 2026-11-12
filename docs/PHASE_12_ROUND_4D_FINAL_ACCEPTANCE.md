# PHASE 12 – ROUND 4D: FINAL ACCEPTANCE

**Project:** HCMC Bus Tracker (`MultiGo Reworked`)  
**Phase / Round:** Phase 12 – Round 4D (Device Visual Acceptance)  
**Date:** 2026-11-12  

---

## Final Acceptance Decision

- **Device Visual & Interaction Acceptance:** **PASS** on **Xiaomi M2102J2SC** (`Android 13`, `1080×2340`, `440 dpi`, `Adreno 650`).
- **Automated Regression Verification (`.\gradlew.bat testDebugUnitTest --rerun`):** **PASS** across all **`36` suites / `191` unit tests** (`0` failures, `0` errors, `0` skipped) after fixing the MapLibre `icon-image` top-level `step(zoom)` expression discovered during device testing.

---

## Summary of Hardware-Verified Behavior

1. **Bitmap Sizing & Zero Clipping:** Normal (`36.78 dp` visual height inside `44 dp` canvas) and Selected (`49.82 dp` coach + `58.8 dp` halo inside `60 dp` canvas) render razor-sharp at `2.75x` (`440 dpi`), completely distinguishable from FPT STIS raster one-way street arrows.
2. **Zoom-Adaptive Labels:** Resolved MapLibre `gl-native` expression rejection (`"zoom" expression may only be used as input to a top-level "step" or "interpolate" expression`) in `MapLayerManager.kt`. Verified short labels (`zoom >= 13.0`), full labels (`zoom >= 14.8` or selected), viewport alignment, collision culling, and clean vertical separation (`iconOffset = 23f / 31f` dp).
3. **Overlap Picker & Camera Stability:** Confirmed real-touch opening of `VehicleOverlapPickerSheet` for co-located buses, accurate focus without shifting GPS coordinates, follow toggle/pause/resume, and clean reset when toggling outbound/inbound directions.
4. **Release Packaging Readiness:** Approved for Phase 12 Release APK build & signature validation.
