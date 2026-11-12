# PHASE 11 — BÁO CÁO UNIT TEST LƯỢT 2

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Lệnh thực thi:** `.\gradlew.bat testDebugUnitTest --rerun`  
**Tổng kết:** **151/151 tests PASS** trên **25 test suites** (`0 failures`, `0 errors`, `0 skipped`).

---

## 1. Bảng Kết quả Toàn bộ 25 Test Suites (Post-Edit)

| Module / Test Suite | Tests | Passed | Failed | Skipped | Trọng tâm Kiểm chứng Lượt 2 |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `:core-model` — `PositionCompensationEngineTest` | 20 | 20 | 0 | 0 | Tuổi GPS 30/60/120/140/160/188s, liên tục qua 120s không nhảy lùi, trần 900m, sai đơn vị giây/ms, timestamp tương lai, tốc độ 0, 5 mức độ mới, đủ 6 trạng thái đến trạm (`AT_STOP` vs `PASSED_STOP`) |
| `:core-model` — `RouteVehicleAggregatorTest` | 7 | 7 | 0 | 0 | Khử trùng lặp đa checkpoint, từ chối response trễ/tương lai, đếm `dts` giữ nguyên qua nhiều polling, 5 mốc timestamp & latency |
| `:core-model` — `PredictionModelTest` | 2 | 2 | 0 | 0 | Chuẩn hóa parser `ArrivalVehicle.getGpsAgeSeconds` |
| `:core-model` — `RouteNumberComparatorTest` | 6 | 6 | 0 | 0 | Regression Phase 8/9/10 |
| `:core-model` — `VietnameseNormalizerTest` | 4 | 4 | 0 | 0 | Regression Phase 8/9/10 |
| `:core-network` — `SafeApiCallTest` + DTO Parsing (3 suites) | 11 | 11 | 0 | 0 | HTTP 408 timeout, 429 `Retry-After`, parse EBMS DTO |
| `:core-database` — `BusRepositoryTest` + `RoomDaoTest` + `StaticDatasetManagerTest` | 19 | 19 | 0 | 0 | Bảo toàn 100% offline database Phase 10, `MIGRATION_2_3`, favorites |
| `:feature-map` — `VehicleInterpolationEngineTest` | 11 | 11 | 0 | 0 | `ebms_d` khớp polyline cong (`nextStop` giữa 2 trạm), `PASSED_STOP` vượt trạm, tuyến 2 chiều/nhánh/lặp, ngoài hành lang (`>1500m`), polyline thoái hóa, bearing 0°/90°/180°/270°, wrap `359° -> 0°`, đứng yên giữ hướng |
| `:feature-map` — `MapViewModelTest` | 16 | 16 | 0 | 0 | Tách `selectedStopId` vs `checkpointStopId` vs `predictionTargetStopId` vs `nextStopId`, chống trùng lặp polling job, route-wide tracking |
| `:feature-map` — `HomeViewModelTest` + `GeoJson` + `TileProvider` | 11 | 11 | 0 | 0 | Regression |
| `:feature-prediction` — `PredictionViewModelTest` + `Personal` + `Notification` | 26 | 26 | 0 | 0 | Chống polling trùng lặp (`queryMutex`), không ghi đè ngược khi gặp `dts` null/tương lai, `HTTP 429 Retry-After` |
| `:feature-route` & `:feature-search` & `:app` (5 suites) | 18 | 18 | 0 | 0 | Regression toàn luồng điều hướng & tìm kiếm offline |
| **TỔNG CỘNG** | **151** | **151** | **0** | **0** | **100% PASS (sau chỉnh sửa cuối của Lượt 2)** |

---

## 2. Đối chiếu Yêu cầu Kiểm thử Mục 7 (Lượt 2)

- **GPS:** Đã kiểm thử các mốc `0s`, `15s`, `30s`, `60s`, `120s`, `140s`, `160s`, `188s`, `> 190s`, `dts` không đổi qua nhiều polling, `dts` đóng băng (`isGpsTimestampFrozen = true`), response cũ đến sau response mới, timestamp 10/13 chữ số, timestamp tương lai (`+120s` $\rightarrow$ `INVALID_TIMESTAMP`), tốc độ `0.0` (`STATIONARY`) và tốc độ âm/`> 90 km/h` (`INVALID_SPEED`).
- **Vị trí & Khoảng cách:** Đã kiểm thử độ dài polyline cong vs chim bay, xe giữa hai trạm, xe vừa đi qua trạm (`PASSED_STOP`), xe gần trạm (`ARRIVING_AT_STOP` / `AT_STOP`), tuyến hai chiều & tuyến có nhánh & đoạn vòng lặp, trạm mục tiêu ngoài hành lang (`> 1500m`), polyline thiếu/thoái hóa (`< 2` điểm).
- **Trạng thái:** Kiểm thử đầy đủ 6 trạng thái (`MOVING`, `APPROACHING_STOP`, `ARRIVING_AT_STOP`, `AT_STOP`, `PASSED_STOP`, `UNKNOWN`), xác nhận `effectiveDistance <= 0` khi xe vượt qua trạm chọn được phân loại đúng thành `PASSED_STOP`.
- **Bearing:** Kiểm thử 4 hướng chính (`0°`, `90°`, `180°`, `270°`), làm mượt qua `359° -> 0°`, tốc độ `0` giữ hướng cũ/tuyến, thiếu đỉnh hình học trả `hasValidHeading = false`.

## 3. Giới hạn & Các Trường hợp Chưa Kiểm chứng Thực địa (Sẽ đối chiếu ở Lượt 4)
1. **Dữ liệu đối chiếu thực địa ngoài đường:** Các số đo 151 unit tests chạy trên JVM/Robolectric với kịch bản tổng hợp dựa trên mẫu đo thực tế của EBMS; chưa đối chiếu trực tiếp nhiều chuyến xe thực tế cùng lúc với ứng dụng MultiGo trên thiết bị thật sau Lượt 3 UI.
2. **Xe có vòng lặp phức tạp đi qua cùng một trạm 2 lần ở hai lượt cùng biến thể:** Hiện hệ thống snap đơn điệu tiến dần theo `orderIndex` (tránh nhảy ngược), nhưng nếu EBMS xuất hiện `varId` nhánh đặc biệt chưa đồng bộ đúng `orderIndex`, hiệu chỉnh có thể rơi vào giới hạn dữ liệu gốc của EBMS.
