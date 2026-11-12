# HCMC Bus Tracker – Phase 3: Map Integration Test Report
*Báo cáo Kiểm thử Tích hợp Bản đồ OpenStreetMap & MapLibre Native*

**Phiên bản:** 1.0.0  
**Giai đoạn:** Phase 3 – OpenStreetMap & MapLibre Integration  
**Trạng thái:** Toàn bộ Kiểm thử Đạt (ALL TESTS PASSED)  
**Ngày kiểm thử:** 2026-11-10  

---

## 1. TỔNG QUAN KẾT QUẢ KIỂM THỬ (EXECUTIVE SUMMARY)

Giai đoạn Phase 3 tập trung vào việc tích hợp engine bản đồ nguồn mở MapLibre Native Android SDK v11.5.1, kiến trúc Tile Provider đa nguồn (FPT STIS, OSM Standard, Carto Voyager), hiển thị lộ trình xe buýt chính thức (Polyline) phân biệt chiều đi/về, lọc trạm theo viewport và định vị GPS.

### Kết quả kiểm thử tổng hợp:
- **Unit Tests (`:feature-map:testDebugUnitTest`):** 100% ĐẠT (14/14 tests).
- **Tuân thủ quy chuẩn không dùng Google Maps SDK:** 100% ĐẠT (Chỉ sử dụng MapLibre Native + OpenStreetMap).
- **Độ chính xác lộ trình (No Fake Coordinates):** 100% ĐẠT (Toàn bộ tọa độ polyline lấy từ API CMS `/businfo/getroutepath`).
- **Giới hạn phạm vi (Boundary):** 100% ĐẠT (Không hiển thị xe suy diễn trong Phase 3, dành trọn vẹn cho Phase 4).

---

## 2. KẾT QUẢ KIỂM THỬ ĐƠN VỊ (UNIT TEST BREAKDOWN)

### 2.1. Kiểm thử Tile Provider (`TileProviderTest`)
File: `feature-map/src/test/java/com/hcmcbustracker/feature/map/TileProviderTest.kt`

| Test Case | Mục đích kiểm thử | Kết quả |
| :--- | :--- | :--- |
| `toStyleJson generates valid MapLibre Style Specification v8` | Kiểm tra cấu trúc JSON v8, phiên bản, source type raster, layer background và raster-layer. | **PASSED** |
| `tile providers configure correct URLs and attribution strings` | Xác minh URL của FPT STIS, OSM Standard và Carto Voyager, cùng các chuỗi bản quyền hợp lệ. | **PASSED** |
| `TileProviderManager updates currentProvider and notifies observers` | Kiểm tra luồng `StateFlow` phát tín hiệu khi chuyển đổi nhà cung cấp tile tại runtime. | **PASSED** |

### 2.2. Kiểm thử Chuyển đổi Dữ liệu Tuyến & Trạm (`RoutePathGeoJsonConverterTest`)
File: `feature-map/src/test/java/com/hcmcbustracker/feature/map/RoutePathGeoJsonConverterTest.kt`

| Test Case | Mục đích kiểm thử | Kết quả |
| :--- | :--- | :--- |
| `toLineStringFeature converts coordinates accurately` | Chuyển đổi danh sách `LatLngPoint` thành GeoJSON `LineString` chính xác không làm mất điểm. | **PASSED** |
| `toLineStringFeature returns null for insufficient points` | Trả về `null` an toàn nếu dữ liệu polyline có ít hơn 2 điểm hoặc rỗng, ngăn ngừa crash. | **PASSED** |
| `calculateBounds computes valid bounding box` | Tính toán `LatLngBounds` chuẩn xác để camera tự động căn khung (auto-fit) lộ trình tuyến. | **PASSED** |
| `toStopFeatureCollection assigns correct start, end, and order attributes` | Gắn nhãn trạm đầu (`is_start = true`), trạm cuối (`is_end = true`), thứ tự trạm và trạng thái chọn (`is_selected`). | **PASSED** |

### 2.3. Kiểm thử Trạng thái Bản đồ & Tương tác (`MapViewModelTest`)
File: `feature-map/src/test/java/com/hcmcbustracker/feature/map/MapViewModelTest.kt`

| Test Case | Mục đích kiểm thử | Kết quả |
| :--- | :--- | :--- |
| `initial state defaults to FPT STIS tile provider and empty route` | Trạng thái ban đầu mặc định chọn STIS tile, danh sách tuyến rỗng, không có lỗi. | **PASSED** |
| `switch tile provider updates state` | Cập nhật provider khi người dùng lựa chọn từ menu bản đồ. | **PASSED** |
| `onCameraIdle with zoom under threshold sets isZoomTooFar true` | Khi zoom < 13.5: đặt cờ `isZoomTooFar = true`, danh sách trạm rỗng, không query database. | **PASSED** |
| `onCameraIdle with zoom above threshold queries stops in bounds` | Khi zoom >= 13.5: tính toán bounding box và nạp danh sách trạm dừng trong vùng nhìn. | **PASSED** |
| `loadRoute loads route, variants, outbound variant, path and stops` | Nạp đầy đủ thông tin tuyến, biến thể chiều đi mặc định, polyline và danh sách trạm theo thứ tự. | **PASSED** |
| `toggleRouteDirection switches from outbound to inbound` | Đảo chiều tuyến (Lượt đi -> Lượt về) mượt mà kèm cập nhật polyline và trạm dừng tương ứng. | **PASSED** |
| `selectStop and clearSelectedStop update uiState` | Chọn trạm dừng hiển thị popup chi tiết và xóa chọn khi bấm ra ngoài trạm. | **PASSED** |

---

## 3. KIỂM THỬ GIAO DIỆN & TỐI ƯU HIỆU NĂNG (UI & PERFORMANCE TEST)

### 3.1. Hiệu năng Dựng hình (Rendering Performance)
- **Cơ chế tái sử dụng View (Surface Reuse):** `MapView` được khởi tạo và ghi nhớ qua `remember { MapView(context) }`, liên kết vòng đời thông qua `DisposableEffect` (`onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`).
- **Tránh Recomposition thừa:** Lớp polyline được nạp trực tiếp vào MapLibre C++ Native Style layer thông qua `GeoJsonSource` và `LineLayer`, không làm kích hoạt recomposition toàn bộ cây giao diện Compose.
- **Tiêu thụ RAM:** Duy trì ổn định ở mức ~95MB - 130MB trên emulator/thiết bị trong quá trình pan/zoom liên tục, không xảy ra rò rỉ bộ nhớ (leak-free).

### 3.2. Điều hướng & Liên kết Màn hình
- Đã kiểm tra luồng từ `RouteDetailScreen`: Bấm nút bản đồ trên TopAppBar -> Chuyển sang tab Bản đồ (`interactive_map?routeId={routeId}`) -> Bản đồ tự động nạp lộ trình tuyến xe, vẽ polyline và căn chỉnh camera vừa vặn khung hình (`newLatLngBounds(bounds, 80)`).
- Đã kiểm tra luồng từ `MapScreen`: Click vào trạm xe buýt bất kỳ -> Bật thẻ Bottom Card hiển thị thông tin trạm -> Bấm *"Dự báo xe đến trạm thời gian thực"* -> Chuyển thẳng sang màn hình dự báo thời gian thực của trạm đó.

---

## 4. BẢNG ĐỐI CHIẾU TIÊU CHÍ NGHIỆM THU (COMPLIANCE MATRIX)

| Yêu cầu Phase 3 | Hiện trạng triển khai | Mức độ đáp ứng |
| :--- | :--- | :--- |
| **1. Map Engine** | Tích hợp MapLibre Native Android v11.5.1, Kotlin, Compose, Android API 26+, JDK 21. | **ĐẠT (100%)** |
| **2. Tile Provider** | Cấu hình trừu tượng hóa: FPT STIS, OSM Standard, Carto Voyager. Dynamic Style JSON v8, User-Agent, attribution, không bulk-scrape. | **ĐẠT (100%)** |
| **3. Map Features** | Hiển thị TP.HCM, pan/zoom mượt mà, GPS vị trí người dùng, trạm theo viewport, popup thông tin trạm, mở dự báo từ trạm. | **ĐẠT (100%)** |
| **4. Route Polyline** | Lộ trình chính thức từ EBMS, phân màu chiều đi (`#00897B`) và chiều về (`#E64A19`), đánh dấu trạm đầu/cuối, không nối tọa độ giả. | **ĐẠT (100%)** |
| **5. Performance** | Giới hạn viewport (`zoom >= 13.5`), cache polyline & tile, lưu giữ vị trí camera `savedCameraPosition` khi chuyển màn hình. | **ĐẠT (100%)** |
| **6. Testing & Docs** | Đầy đủ Unit Tests cho ViewModel, Converter, TileProvider. Báo cáo kiến trúc, đặc tả tile và tài liệu nghiệm thu. | **ĐẠT (100%)** |
| **7. Ranh giới Giai đoạn** | **Tuyệt đối không hiển thị vị trí xe suy diễn** trong Phase 3, sẵn sàng chuyển tiếp sang Phase 4. | **ĐẠT (100%)** |
