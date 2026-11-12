# CHANGELOG - HCMC Bus Tracker

Toàn bộ các mốc phát triển, thay đổi kiến trúc và tính năng của ứng dụng theo từng giai đoạn (Phase).

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
