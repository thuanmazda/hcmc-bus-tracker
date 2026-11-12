# KẾ HOẠCH TRIỂN KHAI CHI TIẾT GIAI ĐOẠN 1 (PHASE 1 IMPLEMENTATION PLAN)
**Dự án:** HCMC Bus Tracker  
**Package:** `com.hcmcbustracker.app`  
**Kiến trúc:** Android Clean Architecture + MVVM + Jetpack Compose  
**Nguồn dữ liệu:** Duy nhất từ `https://apicms.ebms.vn` (Cổng `buyttphcm.com.vn/Prediction`)  
**Ngày lập:** 09/11/2026  

---

## 1. MỤC TIÊU VÀ PHẠM VI GIAI ĐOẠN 1 (SCOPE & OBJECTIVES)

Giai đoạn 1 sẽ chuyển dịch toàn bộ kết quả phân tích dữ liệu ở Phase 0 thành một ứng dụng Android hoàn chỉnh có thể biên dịch, chạy và kiểm thử thực tế trên thiết bị/máy ảo Android:

* **Không làm dữ liệu giả (No Mocking):** Sử dụng 100% dữ liệu thực từ EBMS API.
* **Không phụ thuộc bên thứ ba:** Không dùng Google Maps trả phí, không dùng API buýt bên ngoài.
* **Bảo toàn trải nghiệm MultiGo:** Hiển thị trạm xung quanh, tra cứu tuyến, xem đa tuyến lộ trình, xem thời gian dự báo đến trạm (ETA), vận tốc, khoảng cách, biển số xe và mô phỏng vị trí xe buýt trên bản đồ.

---

## 2. PHÂN KỲ TRIỂN KHAI CHI TIẾT (MILESTONES)

```
[Phase 1 Roadmap]
 ├── Milestone 1: Khởi tạo Project & Cấu hình Gradle Version Catalog
 ├── Milestone 2: Xây dựng Core Network Layer (Retrofit + Kotlinx.Serialization)
 ├── Milestone 3: Xây dựng Core Database Layer (Room DB + FTS4 Search)
 ├── Milestone 4: Xây dựng Map Engine (MapLibre Native + STIS/OSM Tiles)
 ├── Milestone 5: Xây dựng Realtime Prediction & Vehicle Interpolation Engine
 └── Milestone 6: Xây dựng Giao diện Người dùng (Jetpack Compose + Material 3)
```

---

### MILESTONE 1: KHỞI TẠO PROJECT & VERSION CATALOG
* **Nhiệm vụ:**
  - Khởi tạo cấu trúc dự án Android Native tiêu chuẩn với Application ID: `com.hcmcbustracker.app`.
  - Thiết lập file `gradle/libs.versions.toml` theo [DEPENDENCY_COMPATIBILITY_MATRIX.md](file:///E:/MultiGo%20Reworked/docs/phase-1/DEPENDENCY_COMPATIBILITY_MATRIX.md).
  - Cấu hình `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`, Java 21 toolchain.
  - Cấu hình Hilt Dependency Injection tại Application level (`HcmcBusTrackerApp`).
* **Tiêu chí hoàn thành (DoD):**
  - Chạy `./gradlew build` thành công, không có cảnh báo xung đột thư viện.

---

### MILESTONE 2: NETWORK LAYER (RETROFIT + KOTLINX.SERIALIZATION)
* **Nhiệm vụ:**
  - Tạo các DTOs (Data Transfer Objects) tương ứng 100% với schema đã xác minh trong [DATA_SCHEMA.md](file:///E:/MultiGo%20Reworked/docs/phase-1/DATA_SCHEMA.md):
    - `RouteComboDto`, `RouteDetailDto`, `RouteVarDto`, `RoutePathDto`.
    - `StopAutocompleteDto`, `BusStopDto`, `RoutesThroughStopDto`.
    - `PredictionDto`, `ArrivalVehicleDto`.
    - `TimetableDto`, `TripDto`.
  - Khởi tạo `EbmsApiService` với đầy đủ các REST endpoints:
    - `/businfo/getroutesforcombo`
    - `/businfo/getroutebyid/{id}`
    - `/businfo/getvarsbyroute/{id}`
    - `/businfo/getpathsbyvar/{rId}/{vId}`
    - `/businfo/getstopsforautocomplete`
    - `/businfo/getstopsinbounds/...`
    - `/businfo/getstopsbyvar/{rId}/{vId}`
    - `/businfo/gettimetablebyroute/{id}`
    - `/businfo/gettripsbytimetable/{rId}/{tId}`
    - `/prediction/predictbystopid/{stopId}`
    - `/prediction/{rId}/{vId}/{sId}/predictnextstops/{n}`
  - Cấu hình OkHttp với Timeout (15s), Logging Interceptor và User-Agent header tiêu chuẩn.
* **Tiêu chí hoàn thành (DoD):**
  - Unit test gọi thử API thực tế trả về kết quả 200 OK và parse thành công các DTOs.

---

### MILESTONE 3: DATABASE LAYER (ROOM OFFLINE-FIRST CACHE)
* **Nhiệm vụ:**
  - Tạo các Room Entities lưu trữ danh mục tĩnh:
    - `RouteEntity` (ID, Số hiệu, Tên tuyến, Màu sắc, Loại trợ giá...).
    - `RouteVarEntity` (ID, Tuyến ID, Hướng đi/về, Cự ly, Điểm đầu/cuối).
    - `BusStopEntity` (ID, Mã trạm, Tên trạm, Loại trạm, Tọa độ Lat/Lng, Tuyến đi qua).
    - `RoutePathEntity` (Tuyến ID, Hướng ID, JSON chuỗi tọa độ Lat/Lng).
  - Tích hợp SQLite FTS (Full-Text Search) để tìm kiếm tức thì theo:
    - Số hiệu tuyến (VD: "01", "102").
    - Tên trạm, tên đường có dấu hoặc không dấu (VD: "Hàm Nghi", "ham nghi").
  - Xây dựng `SyncRepository` để tải 6.414 trạm dừng trong lần đầu mở app và lưu vào Room.
* **Tiêu chí hoàn thành (DoD):**
  - Thực hiện truy vấn tìm kiếm trạm trong 6.414 trạm dừng với thời gian phản hồi dưới 15ms.

---

### MILESTONE 4: MAP ENGINE (MAPLIBRE NATIVE + OSM/STIS)
* **Nhiệm vụ:**
  - Tích hợp MapLibre Native SDK vào Jetpack Compose thông qua `AndroidView`.
  - Cấu hình Style JSON nạp Raster Tile từ FPT STIS (`https://map.stis.vn/bright/{z}/{x}/{y}.png`) với fallback sang OpenStreetMap chuẩn.
  - Hiển thị bản quyền hợp lệ: `© OpenStreetMap contributors | Dữ liệu: buyttphcm.com.vn`.
  - Triển khai chức năng vẽ Polyline lộ trình 2 chiều:
    - Lượt đi: Đường vẽ màu xanh dương `#1976D2`.
    - Lượt về: Đường vẽ màu cam đỏ `#E64A19`.
  - Hiển thị Marker các trạm dừng trên bản đồ với Cluster hoặc Marker icon số thứ tự trạm.
  - Tích hợp định vị GPS người dùng (`FusedLocationProviderClient`) để căn giữa bản đồ vào vị trí người dùng.
* **Tiêu chí hoàn thành (DoD):**
  - Bản đồ hiển thị mượt mà 60 FPS, vẽ chuẩn xác lộ trình tuyến buýt thực tế.

---

### MILESTONE 5: REALTIME PREDICTION & NỘI SUY VỊ TRÍ XE BUÝT
* **Nhiệm vụ:**
  - Xây dựng `PredictionPollingEngine`:
    - Sử dụng Kotlin Coroutine Flow với `delay(15000)`.
    - Quản lý theo vòng đời màn hình (tự động kích hoạt khi mở bảng dự báo trạm, tự động hủy khi thoát hoặc ẩn app).
  - Xây dựng thuật toán `VehicleInterpolationEngine` (Linear Referencing):
    - Đọc cự ly `d` từ xe đến trạm đang xét.
    - Truy ngược đa tuyến Polyline lộ trình để xác định tọa độ `(lat, lng)` tương ứng.
    - Tính góc phương vị `bearing` của đoạn đường để xoay icon đầu xe buýt đúng chiều di chuyển.
  - Sử dụng Animation chuyển động (`ValueAnimator` / LERP) để icon xe buýt lướt mượt mà dọc tuyến đường giữa các chu kỳ cập nhật.
* **Tiêu chí hoàn thành (DoD):**
  - Nhận dữ liệu dự báo mỗi 15s; icon xe buýt xuất hiện và di chuyển đúng vị trí trên lộ trình bản đồ.

---

### MILESTONE 6: GIAO DIỆN NGƯỜI DÙNG (JETPACK COMPOSE + MATERIAL 3)
* **Nhiệm vụ:**
  - Thiết kế hệ thống Theme (Màu sắc giao thông, Typography, Dark/Light Mode).
  - Xây dựng các màn hình:
    1. **Màn hình Bản đồ & Trạm xung quanh (HomeScreen):** Bản đồ chiếm toàn màn hình, thanh tìm kiếm nổi ở trên, Bottom Sheet hiển thị danh sách trạm gần nhất.
    2. **Màn hình Tra cứu Tuyến (RouteListScreen):** Danh sách toàn bộ tuyến xe buýt với thanh tìm kiếm nhanh, lọc theo tuyến trợ giá / không trợ giá.
    3. **Màn hình Chi tiết Tuyến (RouteDetailScreen):** Tab Lộ trình (Bản đồ + Polyline), Tab Danh sách trạm (A..B), Tab Biểu đồ giờ & Chuyến xuất bến.
    4. **Màn hình Dự báo Xe đến Trạm (StopDetailBottomSheet / Screen):** Hiển thị danh sách xe sắp đến kèm biển số, ETA (phút), cự ly (m/km), vận tốc (km/h) và đồng hồ đếm giây cập nhật. Bấm vào xe xem dự báo trạm kế tiếp.
* **Tiêu chí hoàn thành (DoD):**
  - Giao diện trực quan, mượt mà, phản hồi cảm ứng nhạy bén tương tự trải nghiệm MultiGo.

---

## 3. NGUYÊN TẮC THI CÔNG & PHÊ DUYỆT

Theo đúng chỉ đạo của Chủ dự án:
> **"Chủ dự án sẽ duyệt từng giai đoạn trước khi chuyển sang giai đoạn tiếp theo."**

Chúng ta sẽ không tự ý nhảy bước:
1. Đệ trình toàn bộ báo cáo đánh giá Map & Ma trận dependency cho Chủ dự án xem xét.
2. Khi được phê duyệt, sẽ tiến hành thi công **Milestone 1 & Milestone 2** (Khởi tạo Project + Network Layer).
3. Sau khi hoàn thành Milestone 1 & 2 với các bài test kết nối API thành công, đệ trình kết quả để nghiệm thu trước khi bước sang Milestone 3 (Database).
