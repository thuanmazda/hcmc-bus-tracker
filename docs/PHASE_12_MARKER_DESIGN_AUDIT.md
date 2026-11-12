# Phase 12 — Lượt 1: Vehicle Marker & Map Visualization Audit

**Date:** 2026-11-12  
**Mode:** Audit Only (Zero source code, layout, asset, or algorithm modifications)  
**Target Area:** `:feature-map` ([MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt), [RoutePathGeoJsonConverter.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/util/RoutePathGeoJsonConverter.kt), [TileProvider.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/tile/TileProvider.kt), [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt), [VehicleInterpolationEngine.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/engine/VehicleInterpolationEngine.kt))

---

## 1. Current MapLibre Layer, Data Source & Rendering Inventory

| Visual Element | Source ID | Layer ID(s) | Data Provider & Geometry Origin | Current Visual Implementation |
| :--- | :--- | :--- | :--- | :--- |
| **OSM Basemap** | `raster-tiles` / `fpt-stis-tiles` | `raster-tiles-layer` | [TileProvider.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/tile/TileProvider.kt) & [osm_stis_style.json](file:///e:/MultiGo%20Reworked/feature-map/src/main/assets/osm_stis_style.json) (`https://map.stis.vn/bright/{z}/{x}/{y}.png`, OSM Standard, Carto Voyager) | Raster PNG tiles (`256px`), no `"glyphs"` server URL configured. Note: FPT STIS raster tiles bake small street one-way arrows directly into road tiles. |
| **Route Polyline** | `hcmc-route-source` (`ROUTE_SOURCE_ID`) | `hcmc-route-layer` (`ROUTE_LAYER_ID`) | `RoutePathGeoJsonConverter.toLineStringFeature` from Room `RoutePath` | Single `LineLayer`: `lineWidth = 5.5f`, `#00897B` (outbound) / `#E64A19` (inbound), `opacity = 0.9f`. **No outer contrast casing**, no arrows generated in app code along polyline. |
| **Bus Stops** | `hcmc-stops-source` (`STOPS_SOURCE_ID`) | `hcmc-stops-layer`, `hcmc-terminal-stops-layer`, `hcmc-selected-stop-layer` | `RoutePathGeoJsonConverter.toStopFeatureCollection` (`activeRouteStops` or viewport query) | 3 `CircleLayer`s (`5.5f` `#1976D2`, `7.5f` `#2E7D32`, `9.5f` `#FF8F00`), clustering enabled (`withCluster(true)`) without a dedicated cluster count layer. |
| **User Phone Location** | `hcmc-user-location-source` (`USER_LOCATION_SOURCE_ID`) | `hcmc-user-location-accuracy-layer`, `hcmc-user-location-layer` | FusedLocationProvider → `MapViewModel._userLocation` → `toUserLocationFeature` | Translucent `#4285F4` accuracy `CircleLayer` + `56x56 px` `USER_BLUE_DOT_ICON_ID` (`#1A73E8` + white ring). |
| **Bus Vehicles (Icon)** | `hcmc-vehicle-source` (`VEHICLE_SOURCE_ID`) | `hcmc-vehicle-arrow-layer` (`VEHICLE_ARROW_LAYER_ID`) | `RouteVehicleTelemetry` + `VehicleInterpolationEngine.interpolatePosition` → `toRouteVehiclesFeatureCollection` | `createBusArrowBitmap`: bare `64x64 px` (`76x76 px` selected) raw arrow/diamond rotated by `bearing` when `has_heading == true`, zoom-scaled `iconSize` (`0.72f..1.15f`). |
| **Bus License Plate Labels** | `hcmc-vehicle-source` (`VEHICLE_SOURCE_ID`) | `hcmc-vehicle-label-layer` (`VEHICLE_LABEL_LAYER_ID`) | Same GeoJSON feature (`label_icon_id`, `plate`, `is_selected`, `is_stale`) | `createBusLabelBitmap`: horizontal pill (`textSize = 19f/22f px`), filtered at `zoom >= 13.2f || is_selected`, `iconAllowOverlap(false)`. |

---

## 2. Root-Cause Findings (Why Map Markers Look Small, Cluttered, and Hard to Distinguish)

### Finding 1 — Marker & Label Sizes Use Raw Unscaled Pixels (`px`), Not Screen Density (`dp`)
- In [`createBusArrowBitmap`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L1538-L1605), canvas size is hardcoded to `64 px` (normal) / `76 px` (selected), with the inner triangle spanning `0.14..0.80` (~`42 px`).
- Combined with `iconSize` interpolated by zoom (`0.72f` at `z=11`, `0.92f` at `z=14`), on typical modern Android phones (`xxhdpi = 3.0x` to `xxxhdpi = 3.5–4.0x` density), the visible icon shrinks to **`~14–21 dp`**, far below mobile readability & touch targets (`36–40 dp` normal, `48–54 dp` selected).
- Similarly, [`createBusLabelBitmap`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L1610-L1653) hardcodes `textSize = 19f / 22f px` and `pillHeight = 26f / 30f px` (`~7–9 dp` tall on `xxhdpi`), making plates tiny unless zoomed close.
- Zoom-proportional shrinking at low zooms violates the Phase 12 rule that marker physical size (`dp`) must stay consistent and legible across zoom levels.

### Finding 2 — Ambiguous "Arrows" & Confusion With Map Road Arrows
- **Source of dense small arrows along streets:** Code inspection confirms `MapScreen.kt` and `RoutePathGeoJsonConverter.kt` do **not** generate any route-direction arrow symbols along polylines (`renderRoutePolylineOnStyle` adds only a plain `LineLayer`). Small repeated directional arrows visible along corridors come directly from the **FPT STIS OSM raster basemap (`bright` street tiles)** showing one-way street arrows, visually colliding with our **bare triangular `createBusArrowBitmap`** vehicle marker!
- Because the current vehicle bitmap draws only a sharp chevron/triangle (`arrowPath`) without a recognizable bus body/badge container, moving vehicles blend visually into street arrows or generic navigation cursors instead of reading immediately as buses.

### Finding 3 — Insufficient Visual State Differentiation (Beyond Color Alone)
- `buildBusArrowIconId(hasHeading, isSelected, isStale)` ([RoutePathGeoJsonConverter.kt:202-205](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/util/RoutePathGeoJsonConverter.kt#L202-L205)) collapses all vehicle telemetry states into only `isSelected` vs `isStale` vs `hasHeading`.
- Users cannot visually distinguish on the map (without tapping every bus) between:
  - Active moving bus (`MOVING`) vs approaching/at stop (`APPROACHING_STOP` / `ARRIVING_AT_STOP` / `AT_STOP`) vs passed (`PASSED_STOP`),
  - Compensated/predicted (`LATENCY_COMPENSATED`), route-interpolated (`INTERPOLATED_ON_ROUTE`), or held at last valid GPS (`HELD_AT_LAST_VALID_GPS`),
  - Nor does stationary/no-heading state show a clean distinct non-rotated bus badge.

### Finding 4 — Overlapping Vehicles & Label Collision / Multi-Bus Selection
- When two or more buses cluster near a terminal or junction:
  - `VEHICLE_ARROW_LAYER_ID` uses `iconAllowOverlap(true)` with **no `symbolSortKey`**, meaning an unselected bus drawn later in the GeoJSON list can render **on top of** and obscure the currently selected vehicle.
  - Clicking a crowded cluster in `addOnMapClickListener` ([MapScreen.kt:300-324](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L300-L324)) unconditionally picks `vehicleFeatures.first()` or `minByOrNull`, giving the user **no picker or way to choose** between co-located vehicles without shifting coordinates (shifting real coordinates is forbidden).
  - Labels in `VEHICLE_LABEL_LAYER_ID` use `iconAllowOverlap(false)` at zoom `>= 13.2f`, hiding colliding plates cleanly, **but** do not differentiate medium-zoom compact IDs vs high-zoom full plates, and do not guarantee selected vehicle labels outrank unselected ones via `symbolSortKey`.

### Finding 5 — Unpinned Layer Z-Order & Bitmap/Lifecycle Efficiency
- `renderRoutePolylineOnStyle`, `renderStopsOnStyle`, `renderUserLocationOnStyle`, and `renderRouteVehiclesOnStyle` call plain `style.addLayer(...)` inside independent Compose `LaunchedEffect` blocks ([MapScreen.kt:407-508](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L407-L508)). When a user switches routes/variants or toggles stops after vehicles/location are already present, newly added layers append to the top of the MapLibre layer stack — allowing route lines or stop circles to render **above** vehicle arrows or user Blue Dot.
- `STOPS_SOURCE_ID` enables `.withCluster(true)` without a cluster layer expression filter (`["has", "point_count"]`), so clustered stop points inherit normal stop circles inconsistently.
- `DisposableEffect(mapView)` ([MapScreen.kt:366-374](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L366-L374)) does not forward `onLowMemory` (or `ComponentCallbacks2`), and style bitmap image caching in `renderRouteVehiclesOnStyle` recreates bitmaps for every `(plate, isSelected, isStale)` tuple across style reloads without pruning unused plate label images.
- Note on rotation smoothing across `0°/360°`: `VehicleInterpolationEngine.smoothBearingDegrees` already computes circular EMA smoothing in the domain engine, **but** MapLibre `SymbolLayer.iconRotate` updates instantaneously each poll without lightweight angular interpolation in the UI layer when coordinates update cleanly.

---

## 3. Preserved Logic & Telemetry Verification (Untouched)

Verified during Audit that all Phase 11 core algorithms remain clean and must **not** be altered in Phase 12:
- GPS telemetry, 5-timestamp audit, `PositionCompensationEngine`, `PositionCompensationConfig`, `VehicleInterpolationEngine`, `GlobalPollingCoordinator`, `AdaptiveConcurrencyController`, `SharedStopCoalescer`, `VehicleDataMerger`, camera state machine (`VehicleCameraMode`, `cameraFocusRequestId`, `cameraFollowRequestId`), and offline Room DB.

---

**End of Lượt 1 (Audit). Ready for user confirmation to proceed to Lượt 2 (Design Proposal).**
