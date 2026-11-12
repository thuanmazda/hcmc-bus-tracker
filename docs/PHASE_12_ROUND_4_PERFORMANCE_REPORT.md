# PHASE 12 – ROUND 4: PERFORMANCE REPORT

**Project:** HCMC Bus Tracker (`MultiGo Reworked`)  
**Phase / Round:** Phase 12 – Round 4 (Visual QA & Map Interaction Validation)  
**Date:** 2026-11-12  

---

## 1. Scope & Disclosure (Local Benchmark Only)

All measurements below were captured on the host Windows JVM running **Robolectric SDK 34** inside [VehicleVisualQaAndPerfTest.kt](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/render/VehicleVisualQaAndPerfTest.kt) using controlled local test fixtures (`buildScenarioFleet(10 / 30 / 50)`).

- These timings represent **local CPU-side rendering pipeline costs** (snapshot building, incremental diffing, GeoJSON feature serialization, bitmap generation/caching, and layer/source coordination).
- They **do NOT** represent live network latency against EBMS API or physical Android Mali/Adreno OpenGL ES rasterization frame times.

---

## 2. Observed Benchmark Results (`10`, `30`, `50` Vehicles & `20x` Style Reloads)

Captured directly from `TEST-com.hcmcbustracker.feature.map.render.VehicleVisualQaAndPerfTest.xml` (Run on `DELL-LATITUDE`, `xxhdpi` `480 dpi` `3.0x` factory):

| Fleet Size | Cold Render Pipeline (`ms`) | Warm Incremental Differ (`ms / cycle`) | Coach Bitmap Pool Entries (Max `32`) | Label Bitmap Pool Entries (Max `64`) | JVM Used Heap Snapshot (`MB`) | Duplicate Markers |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **10 buses** | `14.62 ms` | `0.132 ms` | `5` | `20` | `88.6 MB` | `0` |
| **30 buses** | `18.06 ms` | `0.324 ms` | `9` | `60` | `92.2 MB` | `0` |
| **50 buses** | `21.65 ms` | `0.327 ms` | `9` | `64` | `96.1 MB` | `0` |

### Style Reload Stress Test (`20` consecutive reloads × `50` vehicles)
- **Total Elapsed Time (`20` full style reloads + `50` vehicle uploads):** `549.69 ms` (`~27.48 ms` per full style rebuild).
- **Cache Stability:** `coachPool = 9` (shared icon spec pooling across 50 buses), `labelPool = 64` (strictly capped by LRU eviction limit `64`), **0 duplicate layer additions**, **0 unbounded cache growth**.
- **Low-Memory Recovery:** `ComponentCallbacks2.onTrimMemory` / `onLowMemory()` triggers `bitmapFactory.trimCachesOnLowMemory()`, evicting `labelPool` + `clusterPool` and trimming `coachPool` cleanly without calling `.recycle()` on GPU-uploaded bitmaps.

---

## 3. UI & Frame Responsiveness Safeguards Observed in Code

- **Off-Main-Thread Serialization:** `RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(...)` and `toStopFeatureCollection(...)` execute inside `withContext(Dispatchers.Default)` in [MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt).
- **Churn Suppression:** `VehicleRenderDiffer.diff` skips MapLibre `GeoJsonSource.setGeoJson` and `Style.addImages` whenever steady polls contain unchanged coordinates, headings, freshness, arrival status, provenance, and selection state.
- **Screen-Space Touch Offloading:** `VehicleTapCandidate` projection runs lazily inside `addOnMapClickListener` only when the user taps the map, adding zero per-frame projection work.
- **Camera Jitter Prevention:** `MapScreen.kt` guards follow updates with `lastFollow` `>= 12.0 m` distance threshold and ignores repeated `cameraFocusRequestId` / `cameraFollowRequestId` events.
