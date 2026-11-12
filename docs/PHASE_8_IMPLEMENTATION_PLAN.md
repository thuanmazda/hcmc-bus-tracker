# HCMC BUS TRACKER – KẾ HOẠCH TRIỂN KHAI PHASE 8
## PHASE 8 IMPLEMENTATION PLAN
### NÂNG CẤP TRẢI NGHIỆM BẢN ĐỒ, GPS, ĐIỂM DỪNG, DỰ BÁO VỊ TRÍ XE & CHUẨN HÓA HỆ THỐNG

- **Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)
- **Giai đoạn:** Phase 8 – Map, GPS, Bus Stops & Prediction Experience Improvement
- **Vai trò:** Senior Android Software Architect & Lead Independent QA Engineer
- **Thời gian lập:** 2026-11-11
- **Trạng thái:** DỰ THẢO KẾ HOẠCH CHI TIẾT (Trình phê duyệt)

---

## 1. MỤC TIÊU VÀ PHẠM VI PHASE 8

Nâng cấp trải nghiệm người dùng trên nền tảng Clean Architecture hiện có mà không viết lại ứng dụng:
1. **Tích hợp GPS điện thoại:** Lớp hiển thị vị trí người dùng (User Location Marker), kiểm tra quyền động, xử lý khi tắt GPS, nút định vị đưa camera về vị trí hiện tại (không auto-pan gây khó chịu).
2. **Hiển thị điểm dừng trên bản đồ:** Bật/tắt lớp trạm dừng, clustering hoặc quản lý mật độ theo viewport, popup thông tin trạm kèm các tuyến phục vụ, điều hướng đến màn hình dự báo.
3. **Tìm trạm gần vị trí hiện tại:** Tính khoảng cách Haversine từ GPS thiết bị đến các trạm trong cơ sở dữ liệu Room, sắp xếp gần nhất, lọc theo bán kính.
4. **Hiển thị danh sách trạm trên lộ trình chi tiết:** Khắc phục lỗi danh sách trạm bị rỗng trong `RouteDetailScreen`, tự động tải trạm theo hướng tuyến (`syncStopsForRouteVar`), hiển thị theo thứ tự hành trình (Bến đầu $\rightarrow$ Trạm trung gian $\rightarrow$ Bến cuối), bấm trạm để xem trên bản đồ.
5. **Hiển thị vị trí xe ước lượng từ dự báo:** Bấm vào xe trong `StopPredictionScreen` để chuyển sang bản đồ, di chuyển camera đến vị trí xe ước lượng (Linear Referencing), hiển thị marker xe buýt riêng biệt kèm biển số, vận tốc, cự ly và nhãn minh bạch "Vị trí ước lượng".
6. **Khắc phục lỗi dữ liệu rỗng ban đầu:** Bổ sung cơ chế tự động kích hoạt `initialSyncIfNeeded()` trong các ViewModel chính (`RoutesViewModel`, `HomeViewModel`, `StopsViewModel`), sửa phân nhánh UI `RoutesScreen` để hiển thị trạng thái đang tải hoặc tải dữ liệu thay vì chuỗi rỗng `\"\"`.
7. **Phân tích sai lệch khoảng cách dự báo:** Khảo sát 5 ca thực tế, đối chiếu dữ liệu EBMS `d`, `s`, `t`, `dts`, phân tích nguyên nhân sai lệch cự ly (khoảng cách dọc tuyến vs đường chim bay, độ trễ GPS) và lập báo cáo `docs/DISTANCE_ACCURACY_ANALYSIS.md`.
8. **Chuẩn hóa sắp xếp mã tuyến (Natural Alphanumeric Sorting):** Xây dựng `RouteNumberComparator` dùng chung cho toàn dự án: Nhóm chữ cái trước (A01, B02, D4, DL01, MRT1, SWB1) $\rightarrow$ Nhóm số tự nhiên (01, 02, 10, 150) $\rightarrow$ Nhóm hỗn hợp (62-1, 62-2, 156D).
9. **Thiết kế Icon ứng dụng:** Bộ icon Android Adaptive Icon vector sắc nét tông màu xanh ngọc (Teal `#00897B`), hỗ trợ Monochrome Themed Icons (Android 13+).

---

## 2. PHÂN TÍCH HIỆN TRẠNG & NGUYÊN NHÂN CÁC HẠN CHẾ

| Hạng mục | Hiện trạng mã nguồn | Nguyên nhân kỹ thuật |
| :--- | :--- | :--- |
| **GPS trên bản đồ** | `MapScreen.kt` gọi `fusedLocationClient.lastLocation` khi bấm FAB, nhưng không có Layer nào trong MapLibre vẽ marker vị trí người dùng. | Thiếu `GeoJsonSource` và `CircleLayer` riêng cho User Location; thiếu lắng nghe `LocationCallback` liên tục theo vòng đời. |
| **Điểm dừng trên bản đồ** | Đã có `STOPS_LAYER_ID` nhưng chưa có nút bật/tắt (toggle) và chưa bật clustering trên `GeoJsonSource`. | `GeoJsonSource` chưa cấu hình `cluster = true, clusterRadius = 50`. Giao diện chưa có nút bật/tắt hiển thị trạm. |
| **Trạm gần vị trí hiện tại** | Chưa có chức năng tính trạm gần GPS. | Chưa có UseCase/Repository method tính khoảng cách địa lý và sắp xếp top trạm gần nhất. |
| **Danh sách trạm chi tiết tuyến rỗng** | `RouteDetailViewModel.kt` chỉ gọi `syncRouteDetailAndVariants()`, không tự động gọi `syncStopsForRouteVar()` cho variant mặc định. | Logic ViewModel thiếu trigger tải trạm cho variant được chọn ban đầu; UI không hiển thị trạng thái loading cho trạm. |
| **Vị trí xe từ dự báo** | `VehicleInterpolationEngine` đã có từ Phase 5 nhưng không được kết nối với màn hình bản đồ và `StopPredictionScreen`. | `VehicleRow` không có sự kiện click; Navigation chưa truyền tham số xe cần xem sang `MapScreen`; `MapScreen` thiếu Layer vẽ xe buýt. |
| **Màn hình Tuyến xe rỗng ban đầu** | `RoutesViewModel` và `MainActivity` không có `init { initialSyncIfNeeded() }`. `RoutesScreen` in ra `"Không có tuyến buýt nào khớp với \"\""`. | Thiếu trigger tự động tải mạng lưới khi Room rỗng; logic `RoutesScreen` nhầm lẫn giữa "Database rỗng" và "Tìm kiếm không khớp". |
| **Sắp xếp mã tuyến** | SQLite dùng `ORDER BY CAST(routeNo AS INTEGER) ASC, routeNo ASC` khiến các tuyến chữ (D4, DL01, MRT1) bị đẩy xuống cuối hoặc sắp xếp lộn xộn. | Thiếu bộ so sánh tự nhiên phân cấp chữ $\rightarrow$ số $\rightarrow$ hỗn hợp. |
| **Icon ứng dụng** | `AndroidManifest.xml` dùng `@android:drawable/sym_def_app_icon`. | Chưa có tài nguyên mipmap adaptive vector trong `app/src/main/res`. |

---

## 3. KẾ HOẠCH TRIỂN KHAI CHI TIẾT THEO TỪNG NHÓM

### Nhóm 1: Chuẩn hóa Sắp xếp Mã Tuyến (`RouteNumberComparator`)
- **Tệp tạo mới:** [`core-model/src/main/java/com/hcmcbustracker/core/model/RouteNumberComparator.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/RouteNumberComparator.kt)
- **Thuật toán:**
  - Tách chuỗi thành các token (chữ và số).
  - Ưu tiên: Prefix chữ cái (Category 1) < Prefix số (Category 2).
  - So sánh token số theo giá trị nguyên tự nhiên (`toIntOrNull()`), giữ nguyên định dạng số 0 ở đầu.
- **Áp dụng thống nhất:**
  - Trong `BusRepositoryImpl.kt` (sort kết quả trước khi phát Flow).
  - Trong `RoutesViewModel.kt`, `SearchViewModel.kt`, `RouteDetailViewModel.kt`.
- **Unit Test:** [`core-model/src/test/java/com/hcmcbustracker/core/model/RouteNumberComparatorTest.kt`](file:///e:/MultiGo%20Reworked/core-model/src/test/java/com/hcmcbustracker/core/model/RouteNumberComparatorTest.kt)

### Nhóm 2: Khắc phục Đồng bộ Ban đầu & Hiển thị Danh sách Tuyến
- **Tệp chỉnh sửa:**
  - [`feature-route/src/main/java/com/hcmcbustracker/feature/route/RoutesViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-route/src/main/java/com/hcmcbustracker/feature/route/RoutesViewModel.kt): Bổ sung `init { initialSyncIfNeeded() }`, duy trì `isLoading = true` khi `routes.isEmpty() && isSyncing`.
  - [`feature-route/src/main/java/com/hcmcbustracker/feature/route/RoutesScreen.kt`](file:///e:/MultiGo%20Reworked/feature-route/src/main/java/com/hcmcbustracker/feature/route/RoutesScreen.kt): Sửa phân nhánh EmptyState:
    - `state.isLoading || (state.routes.isEmpty() && state.isSyncing)` $\rightarrow$ `LoadingState`.
    - `state.query.isNotBlank() && state.routes.isEmpty()` $\rightarrow$ `EmptyState` tìm kiếm.
    - `state.query.isBlank() && state.routes.isEmpty()` $\rightarrow$ `EmptyState` kèm nút "Tải dữ liệu ngay".
  - [`feature-map/src/main/java/com/hcmcbustracker/feature/map/HomeViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/HomeViewModel.kt): Bổ sung `init { syncDataIfNeeded() }`.
  - [`feature-search/src/main/java/com/hcmcbustracker/feature/search/StopsViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-search/src/main/java/com/hcmcbustracker/feature/search/StopsViewModel.kt): Bổ sung `init { syncStopsIfNeeded() }`.

### Nhóm 3: Hiển thị Danh sách Trạm Chi tiết Tuyến (`RouteDetailScreen`)
- **Tệp chỉnh sửa:**
  - [`feature-route/src/main/java/com/hcmcbustracker/feature/route/RouteDetailViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-route/src/main/java/com/hcmcbustracker/feature/route/RouteDetailViewModel.kt):
    - Tự động gọi `loadVariantStopsAndPath(currentVar.id)` ngay khi variant đầu tiên được xác định.
    - Quản lý trạng thái `_isLoading` chuẩn xác.
  - [`feature-route/src/main/java/com/hcmcbustracker/feature/route/RouteDetailScreen.kt`](file:///e:/MultiGo%20Reworked/feature-route/src/main/java/com/hcmcbustracker/feature/route/RouteDetailScreen.kt):
    - Hiển thị danh sách trạm với số thứ tự rõ ràng (`#1` Bến đầu, `#2..#N-1` Trạm trung gian, `#N` Bến cuối).
    - Thêm nút / tương tác chạm để mở trực tiếp vị trí trạm trên bản đồ hoặc mở dự báo trạm.
    - Hiển thị thông báo trạng thái rõ ràng nếu API trả về danh sách rỗng.

### Nhóm 4: GPS Điện thoại, Lớp Trạm Dừng & Tìm Trạm Gần
- **Tệp chỉnh sửa & tạo mới:**
  - [`feature-map/src/main/java/com/hcmcbustracker/feature/map/location/LocationManager.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/location/LocationManager.kt): Quản lý vị trí thiết bị qua `FusedLocationProviderClient`, callback an toàn theo vòng đời.
  - [`feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt):
    - Thêm `userLocation: LatLng?`, `userHeading: Float?`, `isLocationActive: Boolean`.
    - Thêm `showStopsLayer: Boolean`, `findNearbyStops(radiusMeters: Double)`.
  - [`feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt):
    - Thêm Layer vẽ vị trí người dùng (`USER_LOCATION_LAYER_ID`: chấm xanh lam pulsing có viền trắng và vòng tròn độ chính xác).
    - Thêm nút Recenter Camera về vị trí hiện tại của người dùng (chỉ pan khi bấm nút, không auto-pan khi người dùng đang di chuyển bản đồ).
    - Thêm nút Bật/Tắt hiển thị lớp điểm dừng (`showStopsLayer`).
    - Bật MapLibre GeoJson Clustering cho các trạm dừng khi zoom xa.
    - Thêm BottomSheet / Panel hiển thị danh sách "Trạm dừng gần bạn" sắp xếp theo khoảng cách.

### Nhóm 5: Hiển thị Vị trí Xe Dự báo trên Bản đồ
- **Tệp chỉnh sửa:**
  - [`feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/StopPredictionScreen.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/StopPredictionScreen.kt):
    - Thêm nút "Xem vị trí xe trên bản đồ" tại mỗi thẻ `VehicleRow`.
    - Điều hướng sang `interactive_map?routeId={routeId}&varId={varId}&stopId={stopId}&vehiclePlate={plate}&distance={d}&speed={s}`.
  - [`feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt):
    - Thêm hàm `trackVehicle(...)`: Tính tọa độ ước lượng qua `VehicleInterpolationEngine.interpolatePosition(...)`.
    - Lưu trạng thái `trackedVehicle: TrackedVehicleState?`.
  - [`feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt):
    - Thêm Layer vẽ xe buýt (`VEHICLE_LAYER_ID`: Marker xe buýt màu đỏ/cam nổi bật, hiển thị biển số, hướng di chuyển bearing).
    - Camera tự động căn chỉnh hiển thị cả xe buýt và trạm dừng đến.
    - Thẻ thông tin xe ước lượng nổi trên bản đồ (Biển số, tốc độ km/h, cự ly m, ETA phút/giây, nhãn "⚠️ Vị trí ước lượng").

### Nhóm 6: Phân tích Sai lệch Khoảng cách Dự báo
- Khảo sát trực tiếp dữ liệu từ 5 xe/trạm thực tế trên mạng EBMS.
- So sánh `d` gốc với khoảng cách đường thẳng và độ trễ polling.
- Xuất tài liệu: [`docs/DISTANCE_ACCURACY_ANALYSIS.md`](file:///e:/MultiGo%20Reworked/docs/DISTANCE_ACCURACY_ANALYSIS.md).

### Nhóm 7: Thiết kế Icon Ứng Dụng (Adaptive Icon Vector)
- **Tệp tạo mới trong `app/src/main/res/`:**
  - `drawable/ic_launcher_background.xml`: Nền gradient xanh ngọc (Teal `#00897B` đến `#004D40`).
  - `drawable/ic_launcher_foreground.xml`: Biểu tượng vector xe buýt cách điệu kết hợp định vị GPS và đường tuyến hiện đại.
  - `drawable/ic_launcher_monochrome.xml`: Biểu tượng phẳng cho Android 13+ Themed Icons.
  - `mipmap-anydpi-v26/ic_launcher.xml` và `ic_launcher_round.xml`.
  - Cập nhật [`app/src/main/AndroidManifest.xml`](file:///e:/MultiGo%20Reworked/app/src/main/AndroidManifest.xml) trỏ đến `@mipmap/ic_launcher`.

---

## 4. TIÊU CHÍ NGHIỆM THU PHASE 8 (ACCEPTANCE CRITERIA)

| Hạng mục | Tiêu chí nghiệm thu bắt buộc | Trạng thái dự kiến |
| :--- | :--- | :---: |
| **1. GPS điện thoại** | Xin quyền vị trí hợp lệ; hiển thị marker xanh lam pulsing; nút định vị đưa camera về máy; không auto-pan khi người dùng đang zoom/kéo bản đồ; tắt GPS hiển thị thông báo thân thiện. | `PASS` |
| **2. Điểm dừng bản đồ** | Bật/tắt lớp trạm dừng; clustering khi zoom xa; click trạm mở popup thông tin; bấm xem dự báo chuyển tiếp chính xác; không lệch tọa độ Lat/Lng. | `PASS` |
| **3. Trạm gần bạn** | Danh sách trạm gần nhất sắp xếp theo cự ly thực tế (m); bấm trạm focus bản đồ; hoạt động khi có GPS và có fallback khi không có GPS. | `PASS` |
| **4. Chi tiết tuyến & trạm** | Tuyến 01, 150, 03... hiển thị đầy đủ danh sách trạm theo đúng thứ tự (Bến đầu $\rightarrow$ Trung gian $\rightarrow$ Bến cuối) cho cả 2 chiều Lượt đi & Lượt về. | `PASS` |
| **5. Vị trí xe dự báo** | Bấm xe tại màn hình dự báo chuyển sang bản đồ; vẽ marker xe ước lượng trên polyline; camera hiển thị rõ xe và trạm; thẻ thông tin đầy đủ kèm disclaimer "Vị trí ước lượng". | `PASS` |
| **6. Đồng bộ ban đầu** | Cài mới mở app: tự động tải dữ liệu, không còn hiển thị "Tất cả tuyến (0)" hay "Không có tuyến buýt nào khớp với \"\"". | `PASS` |
| **7. Sắp xếp mã tuyến** | Tuyến chữ (D4, DL01, MRT1...) hiển thị trước $\rightarrow$ Tuyến số (01, 02, 10, 150...) theo giá trị tự nhiên $\rightarrow$ Tuyến hỗn hợp (62-1, 156D). 100% test case pass. | `PASS` |
| **8. Báo cáo khoảng cách** | Lập `docs/DISTANCE_ACCURACY_ANALYSIS.md` với 5 ca thực tế, minh bạch nguồn dữ liệu mét của EBMS. | `PASS` |
| **9. Icon ứng dụng** | Adaptive icon hiển thị sắc nét trên launcher Android, tông màu xanh ngọc, không dùng asset MultiGo. | `PASS` |
| **10. Đóng gói & Release** | `testDebugUnitTest` 100% pass; `assembleDebug` và `assembleRelease` tạo APK có chữ ký số hợp lệ (`apksigner verify` PASS). | `PASS` |

---

*Kế hoạch đã sẵn sàng để tiến hành thực thi tuần tự từng nhóm theo đúng quy chuẩn.*
