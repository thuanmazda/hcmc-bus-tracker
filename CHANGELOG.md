# CHANGELOG - HCMC Bus Tracker

Toàn bộ các mốc phát triển, thay đổi kiến trúc và tính năng của ứng dụng theo từng giai đoạn (Phase).

---

## [1.1.0] - 2026-11-22
### Phát hành chính thức v1.1.0 (Phase 7 – Phase 12 & Kiểm thử thiết bị thật)

#### Tính năng nổi bật & Nâng cấp (Added / Improved):
- **Gia cố & Tối ưu hóa Bản đồ & Vòng đời Polling (Lifecycle Hardening):**
  - Tích hợp `DisposableEffect(lifecycleOwner)` trong `MapScreen`, kiểm soát chặt chẽ `onMapResumed` và `onMapPaused`, dừng hoàn toàn luồng mạng/GPS polling khi chuyển tab hoặc đưa ứng dụng vào nền (background), triệt tiêu lãng phí tài nguyên và rò rỉ bộ nhớ.
  - Tối ưu hóa MapLibre vector map rendering mượt mà ở tần số quét 90Hz trên màn hình AMOLED.
- **Đồng bộ hóa & Chuẩn hóa Ngưỡng GPS Freshness:**
  - Đồng bộ thống nhất 4 trạng thái độ tươi GPS trên toàn bộ ứng dụng: `FRESH` ($\le 60$s), `SLIGHT_DELAY` ($60-120$s), `DELAYED` ($120-300$s), `STALE` ($> 300$s).
  - Tích hợp cờ cảnh báo `Tín hiệu GPS cũ` minh bạch trên các thẻ xe và luồng dự báo ETA.
- **Hệ thống Tìm kiếm Phương tiện Trực tiếp (`ActiveFleetRegistry`):**
  - Tra cứu biển số xe theo thời gian thực kết hợp chuẩn hóa biển số xe (`LicensePlateNormalizer`), cho phép tra cứu chính xác cả định dạng đầy đủ (vd: `50E-620.14`) hoặc 5 số cuối.
  - Lưu trữ lịch sử tìm kiếm tối đa 15 mục gần nhất, hỗ trợ xóa từng mục hoặc toàn bộ.
- **Màn hình Tuyến xe buýt & Lộ trình chi tiết:**
  - Nâng cấp danh mục 181 tuyến xe buýt TP.HCM với bộ lọc phân loại (Tất cả, Có trợ giá, Không trợ giá, Xe điện).
  - Chi tiết lộ trình hai chiều đi/về với danh sách trạm dừng tuần tự và liên kết trực tiếp sang bản đồ Tab 2.
- **Nâng cấp Giao diện Đa nền tảng (Material 3 Dynamic Theming):**
  - Hỗ trợ đầy đủ 3 chế độ: Theo hệ thống, Giao diện sáng (Light Theme) và Giao diện tối (Dark Theme) tối ưu màn hình AMOLED.

#### Sửa lỗi & Hoàn thiện Giao diện (Bug Fixes & UI Polishing):
- **UI-001 (ArrivalCard Speed Wrap):** Khắc phục lỗi ngắt dòng từng ký tự của giá trị tốc độ; đảm bảo tốc độ và đơn vị hiển thị liền khối trên 1 dòng duy nhất (`24 km/h`, `20 km/h`, `0 km/h`).
- **UI-002 (Route Title Ellipsis):** Bổ sung `TextOverflow.Ellipsis` cho tiêu đề và lộ trình tại `RouteCard`, `RouteDetailScreen`, `ArrivalCard` và `SearchScreen`, chấm dứt hiện tượng chữ bị cắt cụt đột ngột.
- **UI-003 (Stop Address Formatting):** Chuẩn hóa chuỗi địa chỉ trạm dừng, loại bỏ các dấu phẩy phân cách thừa hoặc nội dung hiển thị dở dang.
- **UI-004 (VehicleTrackingSheet Navigation Bar Inset):** Khắc phục lỗi thanh điều hướng hệ thống (Navigation Bar) che khuất nội dung Bottom Sheet bằng cách áp dụng padding an toàn `navigationBarsPadding()`.
- **UI-005 (Search Filter Chips Scrolling):** Chuyển đổi thanh bộ lọc tìm kiếm sang `LazyRow` cho phép cuộn ngang mượt mà khi có nhiều danh mục lọc.
- **UI-006 (VehicleSearchResultCard Collision):** Khắc phục triệt để hiện tượng dính chữ giữa tên điểm dừng và nút điều hướng "Xem trên bản đồ".
- **OBS-01 (StopPrediction TopAppBar Ellipsis):** Bổ sung `maxLines = 1` và `TextOverflow.Ellipsis` cho subtitle địa chỉ trạm dừng tại TopAppBar màn hình Dự báo, không che lấp nút Yêu thích và Làm mới.

#### Bảo mật & Gia cố Hệ thống (Security Hardening):
- **SEC-01 (App Sandbox Backup Protection):** Cấu hình `android:allowBackup="false"` trong `AndroidManifest.xml`, ngăn ngừa trích xuất dữ liệu trái phép qua ADB backup hoặc auto-backup không mã hóa.
- **SEC-02 (Enforce Strict HTTPS):** Khai báo tường minh `android:usesCleartextTraffic="false"`, từ chối và chặn ở cấp độ socket mọi kết nối mạng HTTP dạng cleartext không bảo mật.
- **Release Signing Enforcement:** Tích hợp kiểm tra bắt buộc thông tin ký số trong `app/build.gradle.kts`, từ chối tạo APK release nếu thiếu keystore hợp lệ.
- **Bảo vệ Secrets:** Toàn bộ keystore, passwords và `keystore.properties` được giữ bí mật cục bộ, loại trừ tuyệt đối khỏi Git.

#### Kiểm thử & Đóng gói (Verified & Packaged):
- Toàn bộ **42 test suites / 259 unit tests PASS (100%)** (`0` fail, `0` error, `0` skipped) trên 9 module: `:app`, `:core-model`, `:core-network`, `:core-database`, `:core-designsystem`, `:feature-map`, `:feature-prediction`, `:feature-route`, `:feature-search`.
- Nghiệm thu thực tế thành công 12/12 kịch bản vận hành trên thiết bị **Xiaomi Mi 10S** (`M2102J2SC`, Android 13 / HyperOS 1.0, 90Hz AMOLED, `440 dpi`).
- Đóng gói và ký Release APK chính thức: `release-candidate/HCMC-Bus-Tracker-v1.1.0-release.apk` (Dung lượng: `57,662,004 bytes`, SHA-256: `23E9BC1F2027E9E1B3F6D43DA93467A49000F09657BE850CEB5FD9FC59117AFC`).

---

## [1.0.0] - 2026-11-12
### Phát hành chính thức v1.0.0 (Phase 0 – Phase 12 & Kiểm thử thiết bị thật)

#### Tính năng nổi bật (Added / Improved):
- **Phase 12 – Redesign hiển thị phương tiện & trải nghiệm bản đồ (MultiGo Style):**
  - Thiết kế lại marker xe buýt dạng **Top-Down Bus Coach** (`VehicleMarkerBitmapFactory`) chuẩn kích thước `dp` trên mọi mật độ màn hình (`160–640 dpi`), phân biệt trạng thái bằng màu/viền/badge (`ACTIVE`, `DELAYED`, `STALE_OR_HELD`, `Selected` với halo hổ phách `58.8 dp`) và ẩn mũi hướng khi `hasValidHeading = false`.
  - Bổ sung `MapLayerManager` chuẩn hóa thứ tự Z 11 tầng (Route Casing/Line &rarr; Stop Clusters/Markers &rarr; Accuracy Circle &rarr; Normal/Selected Coaches &rarr; License Plate Labels &rarr; User Blue Dot).
  - Nhãn biển số 3 mức zoom (ẩn `< 13.0`, rút gọn `13.0..14.8`, đầy đủ `>= 14.8` hoặc xe đang chọn) với expression `step(zoom, ...)` tương thích MapLibre `11.5.2` `gl-native`.
  - Bổ sung `VehicleRenderDiffer`, `BearingSmoother` (xoay cung ngắn nhất qua `0°/360°`) và `VehicleOverlapResolver` + Material 3 `VehicleOverlapPickerSheet` chạm chọn cụm xe gần nhau (`44 dp`) không làm lệch GPS thật.
- **Phase 11 – Bù trễ GPS, Camera State Machine & Polling đa luồng:**
  - `PositionCompensationEngine` & `VehicleInterpolationEngine` bù trễ có kiểm soát và giữ vị trí cuối khi dữ liệu cũ.
  - `GlobalPollingCoordinator`, `AdaptiveConcurrencyController`, `SharedStopCoalescer` & `VehicleDataMerger` tối ưu tốc độ polling nhiều tuyến và gộp checkpoint dùng chung.
  - Camera State Machine (`IDLE`, `FOCUS_VEHICLE`, `FOLLOW_VEHICLE`, `USER_CONTROLLED`) và `VehicleTrackingInfoCard`.
- **Phase 0 – Phase 10 – Nền tảng cốt lõi & Nghiệp vụ TP.HCM:**
  - Dữ liệu hơn 130 tuyến & hơn 6.000 điểm dừng với Room Database v2 (`MIGRATION_1_2`), đồng bộ ngoại tuyến và bộ nhớ đệm.
  - Tìm kiếm tiếng Việt có/không dấu (`VietnameseNormalizer`), dự báo ETA theo trạm, thông báo đến trạm (`POST_NOTIFICATIONS`), giao diện Light/Dark/System.

#### Kiểm thử & Đóng gói (Verified):
- Toàn bộ **`36` suites / `191` unit tests PASS** (`0` fail, `0` error, `0` skipped) qua `.\gradlew.bat testDebugUnitTest --rerun`.
- Nghiệm thu thực tế trên **Xiaomi M2102J2SC** (Android 13, `1080×2340`, `440 dpi`).
- Đóng gói và ký Release APK (`APK Signature Scheme v2` hợp lệ): `HCMC-Bus-Tracker-v1.0.0-release.apk`.

#### Bảo mật (Security):
- Không đưa keystore, private key hoặc mật khẩu ký số (`keystore.properties`, `release-keystore/`, `*.jks`) vào Git.
