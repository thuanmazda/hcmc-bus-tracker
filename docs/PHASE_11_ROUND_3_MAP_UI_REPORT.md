# Phase 11 — Round 3: Map UI, Directional Markers & Info Card Report

**Date:** 2026-11-12  
**Modules Updated:** `:feature-map` (`MapScreen.kt`, `MapViewModel.kt`, `RoutePathGeoJsonConverter.kt`)  
**Lượt 2 Core Logic Preserved:** `PositionCompensationEngine`, `VehicleInterpolationEngine`, `BusRepositoryImpl`, EBMS APIs, Room schema, and offline transit DB were NOT modified.

---

## 1. Split MapLibre Vehicle Marker Layers (Font/Glyph-Independent)

Because the offline OSM tile configurations in `TileProvider` do not specify a `"glyphs"` server URL, rendering license plates via `SymbolLayer.textField` fails without glyph PBF fonts. To preserve sharp directional markers and always-upright license plate labels without external fonts, vehicle rendering in [MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L1660-L1748) and [RoutePathGeoJsonConverter.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/util/RoutePathGeoJsonConverter.kt#L99-L214) was split into two independent MapLibre `SymbolLayer`s backed by separate bitmaps on `VEHICLE_SOURCE_ID`:

1. **`VEHICLE_ARROW_LAYER_ID` (Directional Arrow Bitmap — `createBusArrowBitmap`)**
   - Uses `PropertyFactory.iconImage(Expression.get("arrow_icon_id"))` (`76px` selected / `64px` unselected canvas with white casing stroke and amber halo on selection; colors `#D32F2F` selected, `#00796B` live, `#607D8B` stale).
   - Rotates strictly by `bearing` via `ICON_ROTATION_ALIGNMENT_MAP` **only when `has_heading == true`** (`Expression.switchCase(Expression.get("has_heading"), Expression.get("bearing"), Expression.literal(0f))`).
   - When `hasValidHeading == false` (stationary / unreliable / uncomputable heading), `RoutePathGeoJsonConverter` sets `has_heading = false`, forces `bearing = 0.0`, and `createBusArrowBitmap` draws a **non-directional symmetric diamond** rather than a fake-pointing arrow.
   - Zoom-scaled icon size (`11 -> 0.72f`, `14 -> 0.92f`, `17 -> 1.15f`) with `iconAllowOverlap = true` for reliable mobile tap targets.
2. **`VEHICLE_LABEL_LAYER_ID` (Upright License Plate Pill — `createBusLabelBitmap`)**
   - Separate pill bitmap (`#1E293B` default, `#B71C1C` selected, `#455A64` stale) anchored cleanly below the arrow (`ICON_ANCHOR_TOP`, `iconOffset = [0f, 1.75f]`).
   - Uses `Property.ICON_ROTATION_ALIGNMENT_VIEWPORT` with no rotation expression so the plate pill **always stays upright** even when the vehicle arrow points south/west or the map rotates.
   - Layer filter (`is_selected == true || zoom >= 13.2f`) hides unselected labels when zoomed out to prevent cluttering stops.
3. **Style Reload Safety & Invalid Coordinate Filtering**
   - `LaunchedEffect(..., currentStyleInstance)` re-applies route polylines, stops, user GPS blue dot, and vehicle markers whenever MapLibre loads or reloads a `Style` instance.
   - `RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection` validates `(lat, lng)` (`!NaN`, not `(0,0)`, within `8..13` / `104..109`) before emitting GeoJSON features, preventing stale/invalid coordinates from rendering.

---

## 2. Phone Location — Google Maps–Style Blue Dot

Replaced the legacy red phone location dot in [MapScreen.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L1412-L1535) with:

- **`USER_BLUE_DOT_ICON_ID` (`createGoogleMapsBlueDotBitmap`):** `56x56` ARGB_8888 bitmap with subtle translucent blue halo (`argb(48, 26, 115, 232)`), crisp `19f` white border ring, and `#1A73E8` (`14f`) Google Maps blue core.
- **`USER_LOCATION_ACCURACY_LAYER_ID`:** Translucent `#4285F4` circle (`0.16f` opacity when `has_accuracy == true`, `0.0f` when unavailable), dynamically sized by `accuracy` (`0..300m`) and zoom.
- **No Fake / Stale Fallback Coordinates:**
  - `fetchDeviceLocation` checks `LocationManager` provider availability (`GPS_PROVIDER` / `NETWORK_PROVIDER`), rejects cached `lastLocation` older than `180_000 ms`, falls back to `PRIORITY_HIGH_ACCURACY` active fix, and invokes `viewModel.markUserLocationUnavailable()` if unavailable.
  - `MapViewModel.updateUserLocation` rejects `NaN`, `Infinity`, `(0.0, 0.0)`, and out-of-range WGS84 coordinates, clears `_userLocation`, and sets `isUserLocationUnavailable = true`.
  - When location permission is revoked (`setLocationPermissionGranted(false)`), `userLocation` is cleared immediately without substituting any default point or vehicle position.

---

## 3. Redesigned `VehicleTrackingInfoCard`

Updated [VehicleTrackingInfoCard](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L1810-L2097) to display compact, honest tracking metadata:

- **Header:** Formatted license plate (`plateNumber`), route number & direction (`routeNo`, `Tuyến đi / chiều`), and 6-tier `VehicleArrivalStatusChip` (`MOVING`, `APPROACHING_STOP`, `ARRIVING_AT_STOP`, `AT_STOP`, `PASSED_STOP`, `UNKNOWN` → `"Không đủ dữ liệu"` without fabrication).
- **Stop Progression:** Explicitly displays `"Đã đi qua trạm <selectedStopName> (<dist>)"` in `#E65100` when `arrivalStatus == PASSED_STOP`, alongside `"Trạm kế tiếp: <nextStopName>"`.
- **Honest Position Source Labels (Zero Fake `"Trực tiếp"` for Interpolated Points):**
  - `PositionSource.LATENCY_COMPENSATED` → `"Dự đoán bù +<m>m"` / `"Dự đoán theo tuyến"`
  - `PositionSource.INTERPOLATED_ON_ROUTE` → `"Ước tính theo tuyến"`
  - `PositionSource.HELD_AT_LAST_VALID_GPS` → `"Giữ vị trí cũ (GPS trễ)"`
  - `PositionSource.UNKNOWN` → `"Thiếu dữ liệu vị trí"` (displays `--` for distance and ETA instead of fake values).
- **Latency & Timestamps:** Displays GPS record timestamp (`formattedGpsTime`), GPS age (`gpsAgeSeconds`), `LatencyTierBadge` (5 levels: `FRESH`, `SLIGHT_DELAY`, `DELAYED`, `STALE`, `UNKNOWN`), stationary GPS hint (`"• GPS giữ nguyên"` when unchanged), app render time (`"Cập nhật app: HH:mm:ss"`), and `"Theo dõi xe"` / `"Đang theo dõi"` toggle chip.

---

## 4. Verification Caveat

- All `:feature-map` Compose UI & GeoJSON/ViewModel bindings compiled cleanly in `assembleDebug` (`app-debug.apk`) and passed JVM/Robolectric unit tests (`44/44` in `:feature-map`).
- Live visual verification on an Android emulator or physical device could not be run in this session because `adb.exe devices` reported zero connected devices/emulators; physical screenshots and live MapLibre style inspection remain pending hardware/emulator attachment.
