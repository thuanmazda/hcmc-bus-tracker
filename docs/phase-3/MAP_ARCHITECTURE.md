# HCMC Bus Tracker – Phase 3: Map Architecture & MapLibre Integration
*Tài liệu Thiết kế Kiến trúc Bản đồ và Tích hợp MapLibre Native*

**Phiên bản:** 1.0.0  
**Giai đoạn:** Phase 3 – OpenStreetMap & MapLibre Integration  
**Trạng thái:** Hoàn thành & Chờ phê duyệt  
**Ngày cập nhật:** 2026-11-10  

---

## 1. TỔNG QUAN KIẾN TRÚC BẢN ĐỒ (MAP ENGINE OVERVIEW)

Trong giai đoạn Phase 3, dự án **HCMC Bus Tracker** tích hợp công cụ dựng bản đồ hiệu năng cao dựa trên nền tảng nguồn mở:
- **Map Engine:** MapLibre Native Android SDK (`org.maplibre.gl:android-sdk:11.5.1`).
- **Nguồn dữ liệu bản đồ:** OpenStreetMap (OSM) tương thích chuẩn quốc tế và hạ tầng FPT STIS TP.HCM.
- **Cam kết kỹ thuật cốt lõi:**
  - **TUYỆT ĐỐI KHÔNG** sử dụng Google Maps SDK, không phát sinh chi phí billing hay phụ thuộc bản quyền Google Maps API Key.
  - Sử dụng tầng đồ họa OpenGL ES tăng tốc phần cứng thông qua MapLibre Native C++ Core.
  - Tuân thủ nguyên tắc Clean Architecture và kiến trúc đa module Android hiện đại.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│    MapScreen.kt ◄───► TopControlsOverlay ◄───► StopDetailCardOverlay   │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ StateFlow<MapUiState>
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    MapViewModel (feature-map)                          │
│  - DisplayStateFlow (TileProvider, Viewport Stops, Zoom Threshold)     │
│  - RouteStateFlow (Active Route, Variants, Direction, RoutePath)      │
│  - StatusFlow (GPS Location, Permissions, Loading, Errors)             │
│  - Camera State Persistence (savedCameraPosition)                      │
└───────┬──────────────────────────┬─────────────────────────────┬───────┘
        │                          │                             │
        ▼                          ▼                             ▼
┌──────────────────┐    ┌────────────────────┐       ┌───────────────────┐
│TileProviderManager│   │ BusRepository       │       │FusedLocationClient│
│Dynamic Style v8  │    │ (core-database)    │       │(Play Services Loc)│
└──────────────────┘    └──────────┬─────────┘       └───────────────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    ▼                             ▼
         ┌─────────────────────┐       ┌──────────────────────┐
         │ Room Local Database │       │ EbmsApiService       │
         │ (BusStopDao, etc.)  │       │ (apicms.ebms.vn)     │
         └─────────────────────┘       └──────────────────────┘
```

---

## 2. QUẢN LÝ TILE PROVIDER & DYNAMIC MAP STYLING

Ứng dụng không hardcode tile server mà trừu tượng hóa hoàn toàn qua interface và enum `TileProvider`:

| Tile Provider | Base Raster URL | Đánh giá & Điều kiện sử dụng |
| :--- | :--- | :--- |
| **FPT STIS (Mặc định)** | `https://map.stis.vn/bright/{z}/{x}/{y}.png` | Nguồn tile bản đồ chính thức được vận hành cho hệ thống Bus TP.HCM. Độ trễ thấp tại Việt Nam. Tuân thủ User-Agent. |
| **OSM Standard** | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | Bản đồ OpenStreetMap toàn cầu. Có cấu hình User-Agent định danh ứng dụng và tuân thủ chặt chẽ Tile Usage Policy (không bulk download). |
| **Carto Voyager** | `https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png` | Tông màu thanh lịch, độ tương phản cao, làm nổi bật lộ trình xe buýt và các trạm trung chuyển. |

### Cơ chế sinh Style JSON v8 động
MapLibre Native Android hỗ trợ nạp cấu hình Style trực tiếp từ chuỗi JSON chuẩn Mapbox/MapLibre Style Specification v8. Hàm `TileProvider.toStyleJson()` tạo động schema chuẩn:
```json
{
  "version": 8,
  "name": "HCMC-Bus-Tracker-Dynamic-Raster",
  "sources": {
    "raster-tiles": {
      "type": "raster",
      "tiles": ["<TILE_URL>"],
      "tileSize": 256,
      "attribution": "<ATTRIBUTION_HTML>",
      "maxzoom": 19
    }
  },
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#f8f9fa" }
    },
    {
      "id": "raster-layer",
      "type": "raster",
      "source": "raster-tiles",
      "minzoom": 0,
      "maxzoom": 19
    }
  ]
}
```
Nhờ cơ chế này, người dùng có thể chuyển đổi mượt mà giữa các nhà cung cấp bản đồ tại runtime mà không cần khởi động lại `MapView` hoặc reload toàn bộ Activity.

---

## 3. LỘ TRÌNH VÀ POLYLINE CHÍNH THỨC (ROUTE POLYLINE ENGINE)

### 3.1. Nguồn tọa độ chính xác
Toàn bộ tọa độ polyline được nạp chính thức từ API EBMS (`/businfo/getroutepath`) thông qua `BusRepository.ensureRoutePath(routeId, varId)`.
- **Tuyệt đối không suy đoán tọa độ** hoặc tự vẽ đường nối không có trong dữ liệu CMS.
- Kiểm tra hợp lệ tọa độ trong phạm vi kinh độ `[105.0, 108.0]` và vĩ độ `[9.0, 12.0]` (khu vực TP.HCM và các tỉnh lân cận).

### 3.2. Dựng Layer trên MapLibre
Lớp polyline được thêm vào MapLibre thông qua `GeoJsonSource` và `LineLayer`:
```kotlin
val lineLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
    setProperties(
        PropertyFactory.lineColor(AndroidColor.parseColor(hexColor)),
        PropertyFactory.lineWidth(5.5f),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineOpacity(0.9f)
    )
}
```

### 3.3. Phân biệt màu sắc trực quan theo hướng tuyến
- **Chiều đi (Outbound / Lượt đi):** Sử dụng sắc thái Teal hiện đại (`#00897B`).
- **Chiều về (Inbound / Lượt về):** Sử dụng sắc thái Coral/Deep Orange tương phản (`#E64A19`).
- Nút bấm `DirectionToggle` trực quan trên màn hình cho phép người dùng đổi chiều đi / về tức thì kèm cập nhật danh sách trạm và camera bounding box tự động (`CameraUpdateFactory.newLatLngBounds(bounds, 80)`).

---

## 4. QUẢN LÝ TRẠM DỪNG & VIEWPORT QUERY (PERFORMANCE OPTIMIZATION)

Để tránh giật lag (frame drop) và tràn bộ nhớ RAM khi hiển thị hàng nghìn trạm dừng trên bản đồ TP.HCM:
1. **Ngưỡng Zoom (Zoom Threshold):**
   - Giới hạn ngưỡng zoom tối thiểu: **`zoom >= 13.5`**.
   - Khi zoom xa hơn 13.5: Giao diện hiển thị banner nhắc nhở *"Phóng to bản đồ (zoom >= 13.5) để xem các trạm xe buýt"* và không thực hiện truy vấn database để bảo vệ tài nguyên CPU/GPU.
2. **Truy vấn Bounding Box cục bộ:**
   - Khi camera dừng lại (`onCameraIdle`), ứng dụng tính toán tọa độ hộp bao `[minLat, maxLat, minLng, maxLng]` của viewport hiện tại và truy vấn nhanh qua Room DAO SQLite index (`BusStopDao.getStopsInBounds`).
3. **Bộ nhớ tạm và trạng thái Camera (Camera State Persistence):**
   - Khi chuyển tab giữa Bản đồ, Tuyến xe, Trạm dừng hoặc Cá nhân, `savedCameraPosition` được bảo lưu nguyên vẹn trong `MapViewModel`, loại bỏ tình trạng bản đồ bị reset về vị trí mặc định khi người dùng quay lại.

---

## 5. ĐỊNH VỊ VỊ TRÍ NGƯỜI DÙNG (GPS LOCATION INTEGRATION)

- Sử dụng `FusedLocationProviderClient` từ Google Play Services Location để lấy vị trí chính xác với mức tiêu thụ pin thấp nhất.
- Kiểm tra quyền truy cập vị trí runtime Android 6.0+ (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) thông qua `ActivityResultContracts.RequestMultiplePermissions()`.
- Nút bấm tiện ích `GPS FAB` hỗ trợ người dùng nhảy nhanh camera về vị trí hiện tại với hiệu ứng mượt mà (`CameraUpdateFactory.newLatLngZoom(latLng, 15.0)`).

---

## 6. PHẠM VI VÀ RANH GIỚI KỸ THUẬT GIAI ĐOẠN 3

- **Đã hoàn thành:**
  - MapLibre Native SDK + Compose AndroidView wrapper.
  - Bộ 3 Tile Provider độc lập (FPT STIS, OSM Standard, Carto Voyager).
  - Polyline chính thức từ EBMS với phân màu chiều đi/về.
  - Tải trạm theo bounding box và ngưỡng zoom tối ưu.
  - Thẻ thông tin trạm (Stop Detail Popup) liên kết trực tiếp sang màn hình dự báo thời gian thực.
  - Điều hướng từ danh sách tuyến (`RouteDetailScreen`) mở bản đồ và focus lộ trình tương ứng.
- **Tuân thủ giới hạn:**
  - **Chưa hiển thị vị trí xe suy diễn:** Giữ đúng chỉ thị của tài liệu dự án, vị trí phương tiện thời gian thực và thuật toán nội suy lộ trình xe sẽ được thực thi tập trung trong **Phase 4 – Live Vehicle Tracking & Prediction**.
