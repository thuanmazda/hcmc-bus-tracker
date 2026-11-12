# PHASE 10 TEST & VERIFICATION REPORT

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Giai đoạn:** Phase 10 – Offline Transit Database, Smart Live GPS Refresh & Route-Wide Vehicle Tracking  
**Ngày kiểm định:** 2026-11-12  

---

## 1. Tóm tắt Kết quả Build & Signing

| Hạng mục | Kết quả | Chi tiết xác minh thực tế |
| :--- | :---: | :--- |
| **Full Unit & Integration Suite** | **PASS** | `.\gradlew.bat testDebugUnitTest --rerun` $\rightarrow$ **140/140 tests passed** trên **25 test suites** (`0 skipped`, `0 failures`, `0 errors`). |
| **Debug APK Build** | **PASS** | `.\gradlew.bat assembleDebug` $\rightarrow$ `app/build/outputs/apk/debug/app-debug.apk` (`64,411,127` bytes). |
| **Signed Release APK Build** | **PASS** | `.\gradlew.bat assembleRelease` (gồm `lintVitalRelease`) $\rightarrow$ `app/build/outputs/apk/release/app-release.apk` (`57,399,696` bytes). |
| **Release Signature Verify** | **PASS** | `apksigner verify --verbose --print-certs` $\rightarrow$ **Verifies** (`APK Signature Scheme v2: true`, 1 signer `CN=HCMC Bus Tracker, O=HCMC DOT`, RSA 2048-bit, SHA-256 `0e295cd1c24b69ddb5ef1d7cdee25622675ba0f087d495613009c91620cfa8c5`). |
| **Asset Packaging Inspection** | **PASS** | Kiểm tra ZIP bên trong `app-release.apk`: `assets/ebms_static_dataset.json` (`6,051,502` bytes plain JSON / `1,055,556` bytes ZIP-compressed) hiện diện đầy đủ; đã sửa và test hỗ trợ cả `.json.gz` lẫn file `.json` do `aapt2` giải nén. |

---

## 2. Chi tiết 25 Test Suites (`testDebugUnitTest --rerun`)

| Module / Test Suite | Số Tests | Passed | Failed | Skipped | Phạm vi kiểm chứng chính |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `:app` — `NavigationTabTest` | 2 | 2 | 0 | 0 | Điều hướng tab chính ứng dụng |
| `:core-model` — `PositionCompensationEngineTest` | 16 | 16 | 0 | 0 | **Phase 9 Regression:** Toàn bộ công thức bù trễ GPS giữ nguyên 100% |
| `:core-model` — `PredictionModelTest` | 2 | 2 | 0 | 0 | Format ETA & biển số |
| `:core-model` — `RouteNumberComparatorTest` | 6 | 6 | 0 | 0 | Chuẩn hóa & sắp xếp mã tuyến |
| `:core-model` — `RouteVehicleAggregatorTest` | 6 | 6 | 0 | 0 | Khử trùng lặp đa checkpoint, monotonic `dts`, cờ `UNCHANGED`, status validity |
| `:core-model` — `VietnameseNormalizerTest` | 4 | 4 | 0 | 0 | Bỏ dấu tiếng Việt & chuẩn hóa tìm kiếm |
| `:core-network` — `BusStopDtoParsingTest` | 2 | 2 | 0 | 0 | Parse JSON `BusStopDto` EBMS |
| `:core-network` — `PredictionDtoParsingTest` | 1 | 1 | 0 | 0 | Parse JSON `PredictionDto` EBMS |
| `:core-network` — `RouteDtoParsingTest` | 2 | 2 | 0 | 0 | Parse JSON `RouteComboDto` & `RouteDetailDto` EBMS |
| `:core-network` — `SafeApiCallTest` | 6 | 6 | 0 | 0 | Timeout HTTP `408`, `HTTP 429` đọc `Retry-After` (và mặc định `30s`), `IOException`, giữ nguyên `CancellationException` |
| `:core-database` — `RoomDaoTest` | 5 | 5 | 0 | 0 | Room DAO queries & FTS |
| `:core-database` — `BusRepositoryTest` | 6 | 6 | 0 | 0 | Repository sync & clamping polling interval `[10..120]` |
| `:core-database` — `StaticDatasetManagerTest` | 8 | 8 | 0 | 0 | Seed từ file thật `ebms_static_dataset.json.gz` & luồng AAPT2 plain JSON (`181` tuyến, `338` chiều, `6.412` trạm, `338` polyline trong `5,412 ms`), `MIGRATION_2_3`, bảo toàn Favorites, rollback khi đứt mạng, checksum no-op, đa checkpoint + HTTP 429 |
| `:feature-map` — `HomeViewModelTest` | 2 | 2 | 0 | 0 | Offline-first startup trên Home |
| `:feature-map` — `MapViewModelTest` | 15 | 15 | 0 | 0 | Load route offline-first, route-wide fleet polling, `selectRouteVehicle`, clear fleet khi đổi chiều |
| `:feature-map` — `RoutePathGeoJsonConverterTest` | 6 | 6 | 0 | 0 | GeoJSON polyline, bounds (kể cả Côn Đảo `8.0..13.0`), stop features |
| `:feature-map` — `TileProviderTest` | 3 | 3 | 0 | 0 | MapLibre tile style JSON |
| `:feature-map` — `VehicleInterpolationEngineTest` | 6 | 6 | 0 | 0 | **Phase 9 Regression:** Linear referencing & snap giữ nguyên 100% |
| `:feature-prediction` — `BusArrivalNotificationManagerTest` | 5 | 5 | 0 | 0 | Quản lý thông báo xe sắp đến |
| `:feature-prediction` — `PersonalViewModelTest` | 9 | 9 | 0 | 0 | Settings, `updateFullStaticDataset()` (`Updated`, `AlreadyUpToDate`, `Failed`), bảo toàn sync cũ |
| `:feature-prediction` — `PredictionViewModelTest` | 12 | 12 | 0 | 0 | LIVE/STALE/EMPTY/OFFLINE/API_ERROR, monotonic `dts`, `isGpsTimestampUnchanged`, `HTTP 429 Retry-After` backoff, configurable polling interval |
| `:feature-route` — `RouteDetailViewModelTest` | 3 | 3 | 0 | 0 | Đọc chi tiết tuyến/trạm offline-first, chuyển tab chiều tuyến |
| `:feature-route` — `RoutesViewModelTest` | 4 | 4 | 0 | 0 | Tìm kiếm & sắp xếp danh sách tuyến |
| `:feature-search` — `SearchViewModelTest` | 5 | 5 | 0 | 0 | Tìm kiếm tuyến & trạm |
| `:feature-search` — `StopsViewModelTest` | 4 | 4 | 0 | 0 | Tải & tìm kiếm điểm dừng offline-first |
| **TỔNG CỘNG** | **140** | **140** | **0** | **0** | **100% Pass sau lần chỉnh sửa mã nguồn cuối cùng** |

---

## 3. Đánh giá Từng Tiêu chí Nghiệm thu Phase 10

| STT | Tiêu chí Nghiệm thu | Trạng thái | Bằng chứng & Ghi chú |
| :---: | :--- | :---: | :--- |
| 1 | **Offline-First Static Dataset & Startup Seeding** | **PASS** | [`ebms_static_dataset.json.gz`](file:///e:/MultiGo%20Reworked/core-database/src/main/assets/ebms_static_dataset.json.gz) đóng gói đủ 181 tuyến, 338 biến thể, 6.412 trạm, 338 polyline. `StaticDatasetManagerTest` xác nhận seed toàn bộ trong **`5,412 ms`** cả luồng GZIP lẫn luồng plain JSON từ `aapt2`. |
| 2 | **Room Schema Migration v2 $\rightarrow$ v3 & Bảo toàn Favorites** | **PASS** | Đã bỏ `.fallbackToDestructiveMigration()`, bổ sung [`MIGRATION_2_3`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/di/DatabaseModule.kt#L38-L94) (tạo `static_dataset_metadata` và nâng cấp `route_variants` lên khóa chính tổ hợp `(routeId, id)`). Test xác nhận `favorite_stops` & `favorite_routes` được bảo toàn 100%. |
| 3 | **Safe Staging Update, Validation, Checksum No-Op & Rollback** | **PASS** | `StaticDatasetValidator` kiểm duyệt cấu trúc, tọa độ `lat 8.0..13.0 / lng 104.0..109.0`, polyline, SHA-256. Test xác nhận lỗi mạng giữa chừng giữ nguyên DB cũ; cùng checksum trả `AlreadyUpToDate` không ghi đè. |
| 4 | **Cài đặt "Dữ liệu ứng dụng / Dữ liệu tuyến xe"** | **PASS** | [`PersonalScreen.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/PersonalScreen.kt) & [`PersonalViewModelTest.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PersonalViewModelTest.kt) kiểm chứng hiển thị đủ metadata, tiến trình, nút hủy, kết quả cập nhật; không đặt nút GPS thủ công tại Settings. |
| 5 | **Điều tra Thực nghiệm EBMS GPS Freshness** | **PASS** | Đo trực tiếp trên `https://apicms.ebms.vn` xác nhận không cache CDN (`CF-Cache-Status: DYNAMIC`), độ lệch đồng hồ server `-1.64s`, tuổi GPS gốc `112–145s`, chu kỳ server cập nhật `dts` ~30s. Tài liệu hóa tại [`GPS_FRESHNESS_ANALYSIS.md`](file:///e:/MultiGo%20Reworked/docs/GPS_FRESHNESS_ANALYSIS.md). |
| 6 | **Bảo toàn Thuật toán Phase 5 & Phase 9** | **PASS** | `PositionCompensationEngineTest` (16 tests) và `VehicleInterpolationEngineTest` (6 tests) PASS toàn bộ không thay đổi công thức. |
| 7 | **Monotonic `dts`, Unchanged `dts`, `HTTP 429 Retry-After` & Lifecycle Polling** | **PASS** | Kiểm chứng qua `SafeApiCallTest`, `PredictionViewModelTest`, `RouteVehicleAggregatorTest`; bổ sung `Cache-Control: no-cache` cho `/prediction/`, kẹp tần suất `[10s..120s]`, tạm dừng/khôi phục theo `ON_PAUSE`/`ON_RESUME`. |
| 8 | **Route-Wide Vehicle Tracking & Deduplication** | **PASS** | Truy vấn tối đa 5 checkpoints qua `Semaphore(4)` trên `/prediction/predictbystopid/{stopId}`, khử trùng lặp tại `RouteVehicleAggregator`, marker kèm biển số offline-safe Canvas `createBusWithPlateBitmap`, xóa xe chiều cũ khi đổi chiều. |
| 9 | **Tài liệu Thiết kế, Migration, GPS & Tracking** | **PASS** | Đã hoàn thiện đủ 6 tài liệu Phase 10 trong thư mục `docs/`. |
| 10 | **Build Debug & Signed Release APK** | **PASS** | `app-debug.apk` và `app-release.apk` build thành công sau sửa lỗi cuối, `apksigner verify` xác nhận hợp lệ với keystore hiện tại (`com.hcmcbustracker.app`). |
| 11 | **Kiểm thử UI & Runtime trên Thiết bị Android Thực tế** | **PASS WITH WARNINGS** | Toàn bộ logic DB, Migration, Seed Asset (cả luồng AAPT2 `.json`), API parsing, ViewModels và nội suy đa xe đã pass qua **140 JVM/Robolectric tests**; tuy nhiên chưa chạy thử tương tác bản đồ MapLibre và cảm quan UI trực tiếp trên thiết bị Android/Emulator vật lý trong phiên làm việc này (cần người dùng/QA cài `app-release.apk` nghiệm thu trực quan trên máy thật). |

---

## 4. Các Phát hiện Quan trọng Đã Khắc phục Trong Quá trình Kiểm thử

1. **Khóa chính `RouteVariantEntity` trùng lặp trên dữ liệu EBMS thật:** Phát hiện khi test seed toàn mạng lưới khiến chỉ còn 84/338 variants $\rightarrow$ đã sửa sang khóa chính tổ hợp `(routeId, id)` kèm nâng cấp SQLite trong `MIGRATION_2_3`, phục hồi trọn vẹn **338/338 biến thể**.
2. **`aapt2` tự giải nén `.json.gz` khi đóng gói APK:** Kiểm tra bên trong `app-release.apk` phát hiện file trở thành `assets/ebms_static_dataset.json` plain JSON $\rightarrow$ đã cập nhật `StaticDatasetManager.openSeedAssetStream` + kiểm tra magic byte `0x1F 0x8B` để đọc trơn tru cả trong unit test lẫn APK thực tế.
3. **Tọa độ Tuyến 173 tại Côn Đảo (`lat ~8.68..8.73`):** Mở rộng giới hạn kiểm duyệt địa lý lên `lat 8.0..13.0, lng 104.0..109.0` để không loại nhầm các tuyến ngoại đảo thuộc TP.HCM mở rộng.
