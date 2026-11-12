# Phase 12 — Round 2: Vehicle Marker & Map UX Design Specification

**Date:** 2026-11-12  
**Mode:** Technical & UX Design Specification Only (Zero code/Gradle/DB edits, zero APK build)  
**Inputs:** [PHASE_12_MARKER_DESIGN_AUDIT.md](file:///e:/MultiGo%20Reworked/docs/PHASE_12_MARKER_DESIGN_AUDIT.md), [MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt), [RoutePathGeoJsonConverter.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/util/RoutePathGeoJsonConverter.kt), MapLibre Android SDK `11.5.2`  
**Visual Mockup:** [PHASE_12_ROUND_2_MARKER_MOCKUPS.svg](file:///e:/MultiGo%20Reworked/docs/PHASE_12_ROUND_2_MARKER_MOCKUPS.svg)

---

## 1. Root Problems Resolved by This Design

Based on Lượt 1 Audit and bytecode verification of MapLibre `11.5.2` (`Style.toImage`):

1. **Pixel vs DP Shrinkage Bug:** In `MapScreen.kt`, `createBusArrowBitmap` and `createBusLabelBitmap` allocate fixed `64 px`/`76 px` bitmaps without density scaling. Because MapLibre Android's `Style.toImage` computes `pixelRatio = bitmap.getDensity() / 160.0f` (`3.0f` on `xxhdpi`), `64 px * iconSize(0.72f..0.92f)` renders at **`15–20 dp`** (~`11–14 dp` visible arrow). This design locks physical `dp` sizing using `bitmapPx = round(targetDp * density)` paired with explicit `bitmap.density = densityDpi` and flat `iconSize`.
2. **Confusion With Raster Street Arrows:** Eliminates bare chevron icons; replaces them with a recognizable **top-down bus coach icon** (body, panoramic windshield, headlights, high-contrast white casing, subtle directional nose only when heading is valid). Zero arrows are placed along route polylines.
3. **Lack of Visual State Cues:** Maps all `13` domain states into simple, non-cluttered structural cues (marker tone, border ring, corner mini-badge, halo, detail card provenance) without multi-color rainbow noise.
4. **Overlapping Vehicles & Selection:** Replaces `first()`/`minByOrNull` silent picking with `VEHICLE_SELECTED_LAYER_ID` Z-priority + screen-space proximity detection opening a **Bottom Sheet Vehicle Picker**, preserving exact real GPS coordinates.
5. **Unpinned Layer Order:** Introduces a centralized `MapLayerManager` using `addLayerBelow` / `addLayerAbove` anchors so route switches, stop toggles, and style reloads never place lines or stops above vehicles or the Blue Dot.

---

## 2. Detailed Vehicle Marker Design & MapLibre `dp → px` Formula

### 2.1 Top-Down Bus Coach Anatomy

Rendered cleanly onto `ARGB_8888` `Canvas` from density-aware dimensions (`dp -> px`):
- **Normal Coach (`38 dp` canvas, ~`30 x 40 dp` coach + nose footprint):**
  - `2.5 dp` outer white `#FFFFFF` casing + soft drop halo (`argb(55, 15, 23, 42)`).
  - Rounded coach roof/chassis (`rx = 7 dp`) with deep teal `#00796B` (fresh/live) or slate `#64748B` (stale/held).
  - High-contrast panoramic front windshield (`#B2DFDB`) + two warm micro headlight indicators (`#FEF08A`) + subtle roof AC unit rectangle so orientation is unmistakably a **bus**, never a map street arrow.
  - Compact front directional nose cone protruding cleanly ahead of windshield when `hasValidHeading = true`.
- **Selected Coach (`50 dp` marker + `60 dp` halo):**
  - `60 dp` translucent amber ring (`#F59E0B` @ `24%` fill + `2 dp` border) beneath coach.
  - High-contrast `#FFFFFF` `3.0 dp` casing + crimson `#D32F2F` coach + `#FECACA` windshield.
  - Always renders on dedicated `VEHICLE_SELECTED_LAYER_ID` above all unselected buses.

### 2.2 Verified MapLibre `11.5.2` `dp → px` Scaling Rules

Bytecode inspection (`javap -c org.maplibre.android.maps.Style`) of `Style.toImage(ImageWrapper)` proves:
$$\text{pixelRatio} = \frac{\text{bitmap.getDensity()}}{160.0}$$
$$\text{Rendered Size (dp)} = \frac{\text{bitmapWidthPx}}{\text{pixelRatio}} \times \text{SymbolLayer.iconSize}$$

Therefore, **simply multiplying bitmap pixels by `density` without controlling `bitmap.density` and `iconSize` would double-scale if `bitmap.density` defaults to `DENSITY_MEDIUM` (`160`), or shrink to `1/density` if unscaled pixels are left at device density (`480`).**

**Mandatory Implementation Formula:**
1. Clamp `safeDensityDpi = context.resources.displayMetrics.densityDpi.coerceAtLeast(160)`.
2. Compute `scale = safeDensityDpi / 160f`.
3. Create bitmap with `widthPx = (targetDp * scale).roundToInt()`, `heightPx = (targetDp * scale).roundToInt()`.
4. Explicitly assign **`bitmap.density = safeDensityDpi`** BEFORE `style.addImage(...)`.
5. Keep MapLibre `iconSize` near `1.0f` (e.g., `0.95f` at `z<=11` → `1.00f` at `z>=13` → `1.04f` at `z>=17`) so rendered size stays within **36–40 dp** (normal) and **48–54 dp** (selected) identically across `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, and `xxxhdpi` devices.

---

## 3. Unified Vehicle State Representation Matrix (All 13 States)

To avoid rainbow clutter, markers use **2 primary fills (`#00796B` Active Teal, `#64748B` Aged Slate, `#D32F2F` Selected Crimson)** combined with **structural outline, directional nose, and 1 corner mini-badge**:

| State Category & Value | Marker Fill & Border | Heading Nose | Corner Mini-Badge (`11 dp`) | Halo / Detail Card Representation |
| :--- | :--- | :---: | :--- | :--- |
| **`MOVING`** | `#00796B` + solid `2.5dp` `#FFFFFF` | Yes (rotated by `bearing`) | None | Normal shadow; card badge `"Đang di chuyển"` |
| **`APPROACHING_STOP`** | `#00796B` + `2.5dp` `#FFFFFF` | Yes | Subtle cyan/blue chevron dot | Highlighted in detail card `"Sắp đến trạm"` |
| **`ARRIVING_AT_STOP`** | `#00796B` + `2.5dp` `#FBBF24` amber rim | Yes | Amber arrival dot (`#F59E0B`) | Soft subtle pulse-free amber rim; card `"Đang vào trạm"` |
| **`AT_STOP`** | `#00796B` + `2.5dp` `#FFFFFF` | Held if valid, else none | Amber pause `"II"` badge (`#F59E0B`) | Anchored cleanly at stop; card `"Đang tại trạm"` |
| **`PASSED_STOP`** | `#00796B` + solid `2.5dp` `#FFFFFF` | Yes | Small orange past-arrow | Card explicitly reads `"Đã đi qua trạm <X>"` |
| **`UNKNOWN`** | `#64748B` muted slate + `#FFFFFF` border | None (upright coach) | Gray `?` mini-badge | Card shows `"Không đủ dữ liệu"`, `--` ETA |
| **`FRESH` (`0–30s`)** | `#00796B` vivid fill | Per `hasValidHeading` | None | Card green badge `"Mới"` |
| **`SLIGHT_DELAY` (`31–60s`)** | `#00796B` vivid fill | Per `hasValidHeading` | None | Card teal badge `"Có độ trễ nhẹ"` |
| **`DELAYED` (`61–120s`)** | `#00796B` standard + subtle `#FDE68A` rim | Per `hasValidHeading` | Small amber clock dot | Card amber badge `"Có độ trễ"` |
| **`STALE` (`>120s`)** | `#64748B` desaturated slate + dashed `#CBD5E1` border | Per `hasValidHeading` | Gray clock badge (`#475569`) | Card red/gray badge `"Dữ liệu cũ"` — **never** `"Trực tiếp"` |
| **`LATENCY_COMPENSATED`** | Standard active/delayed fill | Per `hasValidHeading` | Dotted forward arc / card badge only | Detail card explicitly labels `"Dự đoán bù +Xm"` |
| **`INTERPOLATED_ON_ROUTE`** | Standard fill | Per `hasValidHeading` | Card badge only | Detail card labels `"Ước tính theo tuyến"` — **never** `"Trực tiếp"` |
| **`HELD_AT_LAST_VALID_GPS`** | `#64748B` desaturated fill + double ring | Suppressed (`false` if stagnant/frozen) | Pause/anchor lock badge | Marker stays fixed at last valid point; card `"Giữ vị trí cũ (GPS trễ)"` |

---

## 4. Movement Orientation & Smooth Rotation Design

1. **Valid Heading (`hasValidHeading == true`):** `VEHICLE_NORMAL_LAYER_ID` & `VEHICLE_SELECTED_LAYER_ID` rotate via `PropertyFactory.iconRotate(Expression.get("bearing"))` with `ICON_ROTATION_ALIGNMENT_MAP`.
2. **Invalid / Stationary Heading (`hasValidHeading == false`):** `RoutePathGeoJsonConverter` emits `has_heading = false`, forces `bearing = 0.0`, and selects a **non-directional top-down coach bitmap** (rounded symmetric nose without pointer cone + pause/neutral badge) so stationary buses never point falsely.
3. **Shortest-Arc `0°/360°` UI Display Smoothing:** When polling delivers updated coordinates for moving buses (`isGpsTimestampUnchanged == false` and `hasValidHeading == true`), UI marker state animates bearing over `350 ms` using signed circular delta $\Delta\theta = ((\theta_{\text{new}} - \theta_{\text{old}} + 540^\circ) \bmod 360^\circ) - 180^\circ$ (updating GeoJSON without touching domain coordinates). Stationary/unchanged ticks hold both position and bearing rock-steady.
4. **Distinction From Street Arrows:** Top-down bus silhouette + `2.5–3dp` white casing + headlights makes vehicles visually orthogonal to grey/blue OSM raster one-way arrows. Zero polyline arrows are rendered.

---

## 5. Adaptive Zoom Sizing & 3-Tier License Plate Label System

### 5.1 Marker & Stop Size Table (`dp`, constant density-compensated scale)

| Element | Low Zoom (`< 12.8`) | Medium Zoom (`12.8 – 14.8`) | High Zoom (`>= 14.8`) | Interaction Priority |
| :--- | :---: | :---: | :---: | :--- |
| **Normal Bus Marker** | `36 dp` (`iconSize = 0.95f`) | `38 dp` (`iconSize = 1.00f`) | `40 dp` (`iconSize = 1.05f`) | Secondary (`symbolSortKey = 10..100`) |
| **Selected Bus Marker** | `48 dp` (`56 dp` halo) | `50 dp` (`60 dp` halo) | `52 dp` (`62 dp` halo) | Highest (`VEHICLE_SELECTED_LAYER_ID`) |
| **Regular Stop** | Clustered (`< 13.0`) | `4.5 dp` radius (`9 dp` diam) | `5.5 dp` radius (`11 dp` diam) | Below all bus markers |
| **Selected Stop** | `8 dp` radius | `8.5 dp` radius (`17 dp` diam) | `9.5 dp` radius (`19 dp` diam) | Highlighted amber `#FF8F00` |
| **User Blue Dot** | `20 dp` (`+ 2.5 dp` border) | `22 dp` | `22 dp` | Translucent accuracy ring underneath buses |

*(Exact values will be tuned visually on real device DPI once hardware is attached.)*

### 5.2 3-Tier License Plate Labels

Split from arrows on `VEHICLE_LABEL_LAYER_ID` with `ICON_ROTATION_ALIGNMENT_VIEWPORT` (always horizontal, anchored `ICON_ANCHOR_TOP`, `offset = [0f, 1.35f]` cleanly beneath coach):
- **Zoom `< 13.0f`:** Only the **selected** vehicle renders its full plate pill (`filter: zoom >= 13.0 || is_selected == true`). Unselected vehicles show bus coaches only.
- **Medium Zoom (`13.0f .. 14.8f`):** Renders a **compact short ID** (`short_plate` = last 5–6 digits e.g. `"616.63"` via `short_label_icon_id`, `10 sp` bold, height `18 dp`, `iconAllowOverlap = false`) when space permits; selected vehicle still displays **full plate** (`"50E-616.63"`, `12 sp`, `#B71C1C` bg + `#FBBF24` border).
- **High Zoom (`>= 14.8f`):** Renders **full plate pills** (`full_label_icon_id`, `11 sp` bold, height `21 dp`, rounded `6 dp`) with MapLibre collision detection (`iconAllowOverlap = false`, `iconIgnorePlacement = false`) and `symbolSortKey`: Selected = `1000.0`, Arriving/Near = `200.0`, Moving = `100.0`, Stale = `20.0`.

---

## 6. Overlapping Vehicles & Mobile Vehicle Picker Design

### Evaluation of Options
| Approach | GPS Integrity | MapLibre Mobile UX | Verdict |
| :--- | :---: | :--- | :--- |
| **Vehicle Clustering (`withCluster(true)`)** | Risky if hides close buses | Hides individual headings & makes active fleet feel missing at stations | **Rejected for Vehicles** (Used **only** for Stops at zoom `< 13.0`) |
| **Spiderfy / Coord Jitter** | **Violates rule** (alters visual coords) | Confuses street alignment | **Rejected** |
| **MapLibre Floating Popup** | Safe | Cluttered on small screens, obscures adjacent stops/labels | **Secondary** |
| **Exact Layer Priority + Tap-Cluster Bottom Sheet Picker** | **100% Preserved** | Idiomatic Material 3 mobile UX (used by BusMap/MultiGo); handles any overlap count cleanly | **SELECTED** |

### Selected Mechanism (`VehicleOverlapPicker`)
1. Vehicles render at **exact true coordinates** (`cluster = false` on `VEHICLE_SOURCE_ID`). `VEHICLE_NORMAL_LAYER_ID` renders normal vehicles sorted by telemetry health; `VEHICLE_SELECTED_LAYER_ID` guarantees the chosen vehicle is never buried underneath overlapping coaches.
2. Small overlap count badge? When user taps the map (`addOnMapClickListener`):
   - Project tap point to screen pixels (`pointF`) and check all active vehicles whose screen projections (`map.projection.toScreenLocation`) lie within **`44 dp` touch radius** (capped by `density`).
   - If **`0` matches:** fall through to stop click checks.
   - If **`1` match:** select that vehicle immediately (`viewModel.selectRouteVehicle(plate)`).
   - If **`>= 2` matches** (e.g., buses queued at terminal / hub intersection): open a sleek Material 3 **`ModalBottomSheet` / anchored Vehicle Picker Card** listing only those nearby buses (Route badge, Formatted Plate, `VehicleArrivalStatus` chip, distance to next stop, GPS freshness tier), letting the user pick the exact bus with 1 tap!

---

## 7. Fixed 11-Layer Z-Order & Centralized `MapLayerManager`

### Layer Stack (Bottom → Top)

1. `raster-tiles-layer` (OSM Basemap)
2. `hcmc-route-casing-layer` (`LineLayer`: `8.5f`, high-contrast `#FFFFFF` / `#004D40` / `#BF360C` border)
3. `hcmc-route-layer` (`LineLayer`: `5.5f`, `#00897B` outbound / `#E64A19` inbound)
4. `hcmc-stop-cluster-layer` + `hcmc-stop-cluster-count-layer` (Circle + numeric bitmap badge for clustered stops `zoom < 13.0`, fixing the missing cluster layer from Lượt 1!)
5. `hcmc-stops-layer`, `hcmc-terminal-stops-layer`, `hcmc-selected-stop-layer` (`CircleLayer`s smaller than bus icons)
6. `hcmc-user-location-accuracy-layer` *(placed right beneath vehicles so its faint `0.14f` blue ring never washes out bus colors)*
7. `hcmc-vehicle-normal-layer` (`SymbolLayer`: `is_selected == false`)
8. `hcmc-vehicle-selected-layer` (`SymbolLayer`: `is_selected == true`)
9. `hcmc-vehicle-label-layer` (`SymbolLayer`: adaptive plate pills; selected `symbolSortKey = 1000`)
10. `hcmc-user-location-layer` (`SymbolLayer`: Blue Dot `22 dp` so user's own position stays crisp on top)
11. Compose Interaction Overlays (`VehicleTrackingInfoCard`, Vehicle Overlap Picker sheet, Top Controls, FABs)

### Centralized `MapLayerManager` Architecture
Instead of ad-hoc `style.addLayer(...)` calls racing across 4 separate `LaunchedEffect`s, **`MapLayerManager`** owns style setup & updates:
- **`ensureLayerOrder(style)`:** Ensures all sources and layers are created/re-anchored using `style.addLayerBelow(layer, upperAnchorId)` / `style.addLayerAbove(layer, lowerAnchorId)` following the exact canonical layer ID sequence `LAYER_ORDER`.
- Invoked identically upon: (a) initial `map.setStyle`, (b) `TileProvider` switch / style reload, (c) `activeRoute` / `switchVariant`, and (d) dynamic stop/vehicle/location updates — guaranteeing routes or stops can **never** overlap above vehicles regardless of Compose effect timing.

---

## 8. Incremental Update Architecture (MapLibre Native Approach)

- **Chosen Rendering Pattern:** **`GeoJsonSource` + `SymbolLayer` + Density-Aware Bitmap Pool (`VehicleBitmapCache`)** (staying on existing `maplibre-android:11.5.2`, avoiding heavy Maps Annotation plugin dependency or full annotation tear-down/re-add per poll).
- **Incremental Optimization Strategy:**
  1. **Bounded Vehicle Icon Palette Cache:** Instead of creating bitmaps per bus every poll, cache at most ~`12–16` reusable bus coach bitmaps keyed cleanly by `(isSelected, isFreshStaleDelayedHeld, hasHeading, statusTier, densityDpi)`.
  2. **LRU Plate Label Cache (`VehicleLabelBitmapCache`):** Key plate labels by `(plateText, zoomTier /* SHORT | FULL */, isSelected, isStale, densityDpi)`; only call `style.addImage(...)` for unseen keys and evict stale ones cleanly when switching routes.
  3. **Render Signature Diffing (`VehicleRenderDiffer`):** Track per-vehicle `(lat, lng, bearing, iconKey, shortLabelKey, fullLabelKey, status)`; skip `GeoJsonSource.setGeoJson(...)` when telemetry & selection states have not changed (e.g., unchanged poll cycle), and update bearing independently without re-rasterizing any label bitmap!
  4. **Lifecycle Cleanup:** Forward `mapView.onLowMemory()` cleanly via `ComponentCallbacks2` in `MapScreen`.

---

## 9. Planned Files for Lượt 3 Implementation & Verification Matrix

### Planned File Changes (Lượt 3)
1. **New:** `feature-map/src/main/java/com/hcmcbustracker/feature/map/render/VehicleMarkerBitmapFactory.kt` (Density-accurate `dp -> px` bus coach icon + stop cluster badge + label pill rasterizers with `bitmap.density` & cache)
2. **New:** `feature-map/src/main/java/com/hcmcbustracker/feature/map/render/MapLayerManager.kt` (Canonical 11-layer Z-order manager, route casing + line, stop clusters + stops, normal/selected/label vehicle layers, Blue Dot layers)
3. **New:** `feature-map/src/main/java/com/hcmcbustracker/feature/map/render/VehicleOverlapPicker.kt` (Screen-space overlap cluster resolver `<= 44dp` & Compose picker sheet)
4. **Update:** [RoutePathGeoJsonConverter.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/util/RoutePathGeoJsonConverter.kt) (Emit `short_plate`, `full_label_icon_id`, `short_label_icon_id`, `bus_icon_id`, `sort_key`, `status_badge`)
5. **Update:** [MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt) (Wire `MapLayerManager`, smooth shortest-arc UI bearing animation, screen-space overlap picker sheet, `onLowMemory` forwarding)
6. **Tests (Lượt 3 & 5):** `VehicleMarkerBitmapFactoryTest`, `MapLayerManagerTest`, expanded `RoutePathGeoJsonConverterTest` & `MapViewModelTest` covering `1, 10, 30, 50` vehicles, co-located vehicles, `hasValidHeading = false`, stale/held/interpolated states, zoom thresholds, route switch, style reload, and zero duplicate marker regression.

### Key Risks & Mitigations
- **Risk 1 (Double-scaling on `xxhdpi`/`xxxhdpi`):** Strictly set `bitmap.density = densityDpi` matching `widthPx = targetDp * (densityDpi / 160f)` + `iconSize ~ 1.0f`, and write unit test asserting `bitmapWidth / (bitmap.density / 160f) == targetDp`.
- **Risk 2 (Bitmap memory pressure with 50+ vehicles across routes):** Reuse fixed `~12–16` bus coach bitmaps across all vehicles (`plate` decoupled from `bus_icon_id`!) and bounded LRU cache for plate labels; recycle/remove unused images on route clear.
- **Risk 3 (Clustered stops vs clickable tap):** Render explicit cluster count circles at zoom `< 13.0` and zoom in smoothly when tapping a stop cluster.

---

**End of Lượt 2 (Design Specification). Zero source code was modified. Awaiting user confirmation to begin Lượt 3 (Implementation).**
