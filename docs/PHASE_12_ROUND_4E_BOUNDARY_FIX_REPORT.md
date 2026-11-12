# PHASE 12 – ROUND 4E: NORMAL COACH BOUNDARY FIX REPORT

**Project:** HCMC Bus Tracker (`MultiGo Reworked`)  
**Phase / Round:** Phase 12 – Round 4E (Normal Coach Boundary Fix)  
**Date:** 2026-11-12  

---

## 1. Root Cause Recap & Scope Compliance

In Round 4D, coordinate analysis showed that on the `44.0 × 44.0 dp` Normal Coach bitmap (`cx = 22.0 dp`, `cy = 22.0 dp`, `body = 17.5 × 28.0 dp`), the prior `casingDp = 2.4 dp` and `noseTipOffsetDp = 7.0 dp` pushed the outer white nose casing vertex to `y = 22.0 - 14.0 - 7.0 - (2.4 × 0.9) = -1.16 dp`, clipping `1.16 dp` past the top edge (`y = 0`) of the bitmap.

Strictly adhering to Round 4E constraints:
- **Only modified:** [VehicleMarkerBitmapFactory.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/render/VehicleMarkerBitmapFactory.kt) and bitmap geometry tests in [VehicleMarkerBitmapFactoryTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/render/VehicleMarkerBitmapFactoryTest.kt).
- **Preserved untouched:** All Phase 11 components (`PositionCompensationEngine.kt`, `VehicleInterpolationEngine.kt`, `GlobalPollingCoordinator.kt`, `AdaptiveConcurrencyController.kt`, `SharedStopCoalescer.kt`, `VehicleDataMerger.kt`, `MapViewModel.kt`), MapLibre `iconSize` curve, bearing/rotation logic, `MapLayerManager.kt`, `MapScreen.kt`, Selected Coach (`22.5 × 36.0 dp`), and Selected Halo (`58.8 dp`).

---

## 2. Geometry Adjustments Applied

Introduced `CoachCanvasGeometry` and `VehicleMarkerBitmapFactory.computeCoachGeometry(spec)` as a single mathematical source of truth driving `renderCoachBitmap(spec)`:

| Component / Primitive (Normal Coach, `44.0 × 44.0 dp`) | Before Round 4E (`dp`) | After Round 4E (`dp`) | Canvas Bounds Check (`0..44 dp`) |
| :--- | :--- | :--- | :--- |
| Canvas / Center | `44.0 dp` (`cx=22.0, cy=22.0`) | `44.0 dp` (`cx=22.0, cy=22.0`) | Unchanged |
| Inner Chassis (`bodyWidthDp × bodyHeightDp`) | `17.5 × 28.0 dp` | `17.5 × 28.0 dp` | `x: 13.25..30.75`, `y: 8.00..36.00` |
| Outer Casing (`casingDp`) | `2.4 dp` | `2.2 dp` | `x: 11.05..32.95`, `y: 5.80..38.20` (`±0.7` dashed -> `10.35..33.65`, `5.10..38.90`) |
| Heading Nose Inner Tip (`noseTipOffsetDp`) | `7.0 dp` (`y = +1.00`) | `4.6 dp` (`y = +3.40`, `noseBaseHalfDp = 5.2`) | Inside canvas |
| Heading Nose Outer White Casing Vertex | `y = -1.16 dp` **(Clipped!)** | **`y = +1.42 dp`** (`+1.42 dp` clear margin) | **0 clipping** |
| Corner Status Badge (right-front) | Center `(31.75, 9.5)`, outer `r=6.4` | Center `(31.75, 9.5)`, outer `r=6.4` | `x: 25.35..38.15`, `y: 3.10..15.90` |
| Bottom Drop Shadow | `bottom = 39.18 dp` | `bottom = 39.04 dp` | `4.96 dp` clear margin |
| **Total Opaque Height (Heading Nose → Body Bottom)** | `39.56 dp` (`38.40 dp` clipped) | **`36.78 dp`** (cased body+nose) | Strictly within **`36–40 dp`** |

### Selected Coach Geometry (Verified Unchanged & Clip-Free)
- Canvas: `60.0 × 60.0 dp` (`cx = 30.0`, `cy = 30.0`).
- Amber Halo (`haloRadiusDp = 28.5`, `haloStrokeDp = 1.8`): spans `[0.60, 59.40] dp` (`58.8 dp` outer diameter, inside `56–62 dp`).
- Selected Chassis + Casing + Nose: `22.5 × 36.0 dp` chassis, `2.8 dp` casing, `8.5 dp` nose tip -> spans `y = 0.98..50.80 dp` (`49.82 dp` opaque coach height, inside `48–54 dp`).
