# HCMC BUS TRACKER – BÁO CÁO TRIỂN KHAI PHASE 8
## PHASE 8 IMPLEMENTATION REPORT
### NÂNG CẤP TRẢI NGHIỆM BẢN ĐỒ, GPS, ĐIỂM DỪNG, DỰ BÁO VỊ TRÍ XE & CHUẨN HÓA HỆ THỐNG

- **Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)
- **Giai đoạn:** Phase 8 – Map, GPS, Bus Stops & Prediction Experience Improvement
- **Vai trò:** Senior Android Software Architect & Lead Independent QA Engineer
- **Thời gian lập:** 2026-11-11
- **Trạng thái:** HOÀN THÀNH TOÀN DIỆN (10/10 Tiêu chí Nghiệm thu Đạt Yêu cầu)

---

## 1. TỔNG QUAN KẾT QUẢ TRIỂN KHAI THEO TỪNG NHÓM

### Nhóm 1: Chuẩn hóa Sắp xếp Mã Tuyến (`RouteNumberComparator`)
- **Thực thi:** Tạo mới [`core-model/src/main/java/com/hcmcbustracker/core/model/RouteNumberComparator.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/RouteNumberComparator.kt) tuân thủ quy tắc tự nhiên (Natural Alphanumeric Order):
  - Phân nhóm 1: Tuyến chữ cái trước (A01, D4, DL01, MRT1, SWB1)
  - Phân nhóm 2: Tuyến số tự nhiên (01, 02, 10, 150)
  - Phân nhóm 3: Tuyến hỗn hợp / phân đoạn (62-1, 62-2, 62-10, 156D, 156V)
  - Bảo toàn định dạng gốc của EBMS và số 0 ở đầu.
- **Tích hợp:** Áp dụng trực tiếp vào `BusRepositoryImpl.kt` (`getAllRoutes()`, `searchRoutes()`).
- **Kiểm thử:** [`core-model/src/test/java/com/hcmcbustracker/core/model/RouteNumberComparatorTest.kt`](file:///e:/MultiGo%20Reworked/core-model/src/test/java/com/hcmcbustracker/core/model/RouteNumberComparatorTest.kt) – 100% test cases pass.

### Nhóm 2: Khắc phục Lỗi Màn hình Rỗng Ban đầu & Tự động Đồng bộ
- **Nguyên nhân cũ:** Cài đặt mới ứng dụng có cơ sở dữ liệu Room rỗng, các ViewModel không có trigger tự động tải; `RoutesScreen` in ra `"Không có tuyến buýt nào khớp với \"\""`.
- **Thực thi:**
  - Bổ sung `init { initialSyncIfNeeded() }` trong `RoutesViewModel`, `HomeViewModel`, `StopsViewModel`.
  - Kiểm tra `repository.getRouteCount()` và `repository.getStopCount()`, nếu DB rỗng tự động gọi API EBMS đồng bộ.
  - Phân nhánh UI rõ ràng trong `RoutesScreen.kt` và `StopsScreen.kt`:
    - Đang tải mạng lưới: Hiển thị `LoadingState`.
    - Tìm kiếm không khớp: Hiển thị `EmptyState` tìm kiếm.
    - Database trống: Hiển thị `EmptyState` kèm nút bấm "Tải dữ liệu ngay".

### Nhóm 3: Hiển thị Danh sách Trạm trên Lộ trình Chi tiết (`RouteDetailScreen`)
- **Nguyên nhân cũ:** `RouteDetailViewModel` chỉ gọi `syncRouteDetailAndVariants()`, không gọi `syncStopsForRouteVar()` cho variant mặc định.
- **Thực thi:**
  - Tự động gọi `loadVariantStopsAndPath(currentVar.id)` ngay khi variant đầu tiên được xác định.
  - Hiển thị danh sách trạm tuần tự theo hành trình: `#1 • Bến đầu` (xanh lá), `#2..#N-1 • Trạm trung gian` (xanh theme), `#N • Bến cuối` (đỏ).
  - Tích hợp điều hướng: Bấm trạm mở dự báo thời gian thực (`StopPredictionScreen`), bấm icon bản đồ mở toàn bộ lộ trình trên bản đồ số.
- **Báo cáo chi tiết:** [`docs/STOP_LIST_TEST_REPORT.md`](file:///e:/MultiGo%20Reworked/docs/STOP_LIST_TEST_REPORT.md).

### Nhóm 4: Tích hợp GPS Điện thoại, Lớp Trạm Dừng & Tìm Trạm Gần
- **Thực thi:**
  - Bổ sung `USER_LOCATION_SOURCE_ID`, `USER_LOCATION_LAYER_ID`, `USER_LOCATION_ACCURACY_LAYER_ID` vào MapLibre Style: Chấm xanh pulsing viền trắng và vòng tròn độ chính xác.
  - Nút FAB MyLocation đưa camera về vị trí người dùng mượt mà, không auto-pan khi người dùng đang zoom/kéo bản đồ.
  - Nút FAB Toggle Stops Layer: Bật/tắt hiển thị trạm dừng theo ý muốn.
  - Bật GeoJSON Clustering trên `STOPS_SOURCE_ID` (`clusterRadius = 45`, `clusterMaxZoom = 14`).
  - Chức năng tìm trạm gần: Tính cự ly Haversine từ GPS tới các trạm trong Room, sắp xếp tăng dần và hiển thị danh sách trạm gần nhất.
- **Báo cáo chi tiết:** [`docs/MAP_GPS_TEST_REPORT.md`](file:///e:/MultiGo%20Reworked/docs/MAP_GPS_TEST_REPORT.md).

### Nhóm 5: Hiển thị Vị trí Xe Dự báo trên Bản đồ
- **Thực thi:**
  - Tại mỗi thẻ xe trong `StopPredictionScreen` (`VehicleRow`), bổ sung nút hành động "Xem vị trí xe trên bản đồ".
  - Chuyển tiếp tham số qua Compose Navigation sang `interactive_map?routeId=...&varId=...&stopId=...&plate=...&distance=...&speed=...`.
  - `MapViewModel.trackVehicle()` áp dụng thuật toán `VehicleInterpolationEngine` (Reverse Polyline Linear Referencing) để xác định tọa độ và hướng xoay (bearing).
  - Vẽ marker xe màu đỏ (`#D32F2F`) nổi bật trên polyline tuyến, camera tự động focus vào xe.
  - Hiển thị thẻ thông tin nổi kèm nhãn minh bạch: `⚠️ Vị trí ước lượng từ dữ liệu cự ly EBMS`.
- **Báo cáo chi tiết:** [`docs/VEHICLE_POSITION_TEST_REPORT.md`](file:///e:/MultiGo%20Reworked/docs/VEHICLE_POSITION_TEST_REPORT.md).

### Nhóm 6: Phân tích Sai lệch Khoảng cách Dự báo
- **Thực thi:** Khảo sát trực tiếp 5 ca vận hành thực tế (Tuyến 01, 150, D4, 03, 19).
- Đối chiếu độ lệch giữa cự ly dọc tuyến $D_{\text{polyline}}$ và đường chim bay $D_{\text{straight}}$ (độ lệch từ $1.4\%$ đến $27.6\%$).
- Phân tích nguyên nhân độ trễ GPS và chu kỳ polling 15s.
- **Báo cáo chi tiết:** [`docs/DISTANCE_ACCURACY_ANALYSIS.md`](file:///e:/MultiGo%20Reworked/docs/DISTANCE_ACCURACY_ANALYSIS.md).

### Nhóm 7: Thiết kế Icon Ứng Dụng (Adaptive Icon Vector)
- **Thực thi:** Tạo mới toàn bộ tài nguyên icon trong `app/src/main/res/`:
  - `drawable/ic_launcher_background.xml`: Nền gradient xanh ngọc (Teal `#00897B` đến `#004D40`).
  - `drawable/ic_launcher_foreground.xml`: Biểu tượng vector xe buýt hiện đại kết hợp ghim định vị GPS và đường lộ trình.
  - `drawable/ic_launcher_monochrome.xml`: Silhouette đơn sắc cho Android 13+ Themed Icons.
  - `mipmap-anydpi-v26/ic_launcher.xml` và `ic_launcher_round.xml`.
  - Cập nhật `AndroidManifest.xml` sử dụng `@mipmap/ic_launcher` và `@mipmap/ic_launcher_round`.

---

## 2. BẢNG NGHIỆM THU TIÊU CHÍ PHASE 8 (ACCEPTANCE CRITERIA)

| STT | Tiêu chí nghiệm thu | Yêu cầu kỹ thuật | Trạng thái |
| :---: | :--- | :--- | :---: |
| 1 | **GPS điện thoại** | Chấm xanh pulsing viền trắng; xin quyền động hợp lệ; nút định vị đưa camera về máy; không auto-pan khi người dùng thao tác. | **PASS** |
| 2 | **Lớp điểm dừng bản đồ** | Bật/tắt lớp trạm dừng; clustering khi zoom xa; click trạm mở popup thông tin; bấm xem dự báo chuyển tiếp chính xác. | **PASS** |
| 3 | **Trạm dừng gần bạn** | Danh sách trạm gần nhất sắp xếp theo cự ly thực tế (m); bấm trạm focus bản đồ; hoạt động khi có GPS. | **PASS** |
| 4 | **Chi tiết tuyến & trạm** | Hiển thị đầy đủ danh sách trạm theo thứ tự (Bến đầu $\rightarrow$ Trung gian $\rightarrow$ Bến cuối) cho cả 2 chiều Lượt đi & Lượt về. | **PASS** |
| 5 | **Vị trí xe dự báo** | Bấm xe tại màn hình dự báo chuyển sang bản đồ; vẽ marker xe ước lượng trên polyline; camera hiển thị rõ xe; nhãn minh bạch "Vị trí ước lượng". | **PASS** |
| 6 | **Đồng bộ ban đầu** | Cài mới mở app: tự động tải dữ liệu khi Room rỗng, không còn hiển thị "Tất cả tuyến (0)" hay "Không có tuyến buýt nào khớp với \"\"". | **PASS** |
| 7 | **Sắp xếp mã tuyến** | Tuyến chữ (D4, DL01, MRT1...) hiển thị trước $\rightarrow$ Tuyến số (01, 02, 10, 150...) theo giá trị tự nhiên $\rightarrow$ Tuyến hỗn hợp (62-1, 156D). 100% test case pass. | **PASS** |
| 8 | **Báo cáo khoảng cách** | Lập `docs/DISTANCE_ACCURACY_ANALYSIS.md` với 5 ca thực tế, minh bạch nguồn dữ liệu mét của EBMS. | **PASS** |
| 9 | **Icon ứng dụng** | Adaptive icon hiển thị sắc nét trên launcher Android, tông màu xanh ngọc, hỗ trợ Monochrome Themed Icons. | **PASS** |
| 10 | **Đóng gói & Ký số APK** | `testDebugUnitTest` 100% pass; `assembleRelease` tạo APK có chữ ký số hợp lệ (`apksigner verify` Scheme v2 PASS). | **PASS** |

---

## 3. DANH SÁCH TỆP MÃ NGUỒN TẠO MỚI & CHỈNH SỬA

### Tệp tạo mới:
1. `core-model/src/main/java/com/hcmcbustracker/core/model/RouteNumberComparator.kt`
2. `core-model/src/test/java/com/hcmcbustracker/core/model/RouteNumberComparatorTest.kt`
3. `app/src/main/res/drawable/ic_launcher_background.xml`
4. `app/src/main/res/drawable/ic_launcher_foreground.xml`
5. `app/src/main/res/drawable/ic_launcher_monochrome.xml`
6. `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
7. `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
8. `docs/PHASE_8_IMPLEMENTATION_PLAN.md`
9. `docs/PHASE_8_IMPLEMENTATION_REPORT.md`
10. `docs/MAP_GPS_TEST_REPORT.md`
11. `docs/STOP_LIST_TEST_REPORT.md`
12. `docs/VEHICLE_POSITION_TEST_REPORT.md`
13. `docs/DISTANCE_ACCURACY_ANALYSIS.md`

### Tệp chỉnh sửa:
1. `core-database/src/main/java/com/hcmcbustracker/core/database/repository/BusRepository.kt`
2. `core-database/src/main/java/com/hcmcbustracker/core/database/repository/BusRepositoryImpl.kt`
3. `feature-route/src/main/java/com/hcmcbustracker/feature/route/RoutesViewModel.kt`
4. `feature-route/src/main/java/com/hcmcbustracker/feature/route/RoutesScreen.kt`
5. `feature-route/src/main/java/com/hcmcbustracker/feature/route/RouteDetailViewModel.kt`
6. `feature-route/src/main/java/com/hcmcbustracker/feature/route/RouteDetailScreen.kt`
7. `feature-route/src/test/java/com/hcmcbustracker/feature/route/RoutesViewModelTest.kt`
8. `feature-search/src/main/java/com/hcmcbustracker/feature/search/StopsViewModel.kt`
9. `feature-search/src/main/java/com/hcmcbustracker/feature/search/StopsScreen.kt`
10. `feature-search/src/test/java/com/hcmcbustracker/feature/search/StopsViewModelTest.kt`
11. `feature-map/src/main/java/com/hcmcbustracker/feature/map/HomeViewModel.kt`
12. `feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt`
13. `feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt`
14. `feature-map/src/main/java/com/hcmcbustracker/feature/map/util/RoutePathGeoJsonConverter.kt`
15. `feature-map/src/test/java/com/hcmcbustracker/feature/map/HomeViewModelTest.kt`
16. `feature-map/src/test/java/com/hcmcbustracker/feature/map/MapViewModelTest.kt`
17. `feature-map/src/test/java/com/hcmcbustracker/feature/map/RoutePathGeoJsonConverterTest.kt`
18. `feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/StopPredictionScreen.kt`
19. `app/src/main/java/com/hcmcbustracker/app/MainActivity.kt`
20. `app/src/main/AndroidManifest.xml`
