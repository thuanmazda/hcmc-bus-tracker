# PHASE 11 — BÁO CÁO TRIỂN KHAI LƯỢT 2 (GPS & VEHICLE POSITION ACCURACY)

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Phạm vi Lượt 2:** Tầng dữ liệu GPS, Polling, Thuật toán bù trừ/nội suy, Cự ly đến trạm, Trạng thái đến trạm & Bearing  
**Build Debug:** `.\gradlew.bat assembleDebug` $\rightarrow$ **Thành công** (`app/build/outputs/apk/debug/app-debug.apk`, `64,411,127` bytes)  
**Unit & Integration Tests:** `.\gradlew.bat testDebugUnitTest --rerun` $\rightarrow$ **151/151 PASS** trên 25 test suites (`0 failures`, `0 errors`, `0 skipped`)

*(Tuân thủ phạm vi Lượt 2: giữ nguyên 100% giao diện `MapScreen.kt`, marker, màu sắc, camera auto-center — các phần đó dành cho Lượt 3).*

---

## 1. Danh sách Mã nguồn Đã Chỉnh sửa trong Lượt 2

### Mã nguồn chính
1. [`PositionCompensationEngine.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/PositionCompensationEngine.kt)
2. [`Prediction.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/Prediction.kt)
3. [`RouteVehicleTelemetry.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/RouteVehicleTelemetry.kt)
4. [`BusRepositoryImpl.kt`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/repository/BusRepositoryImpl.kt)
5. [`PredictionViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/PredictionViewModel.kt)
6. [`VehicleInterpolationEngine.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/engine/VehicleInterpolationEngine.kt)
7. [`MapViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt)

### Unit Tests mở rộng
1. [`PositionCompensationEngineTest.kt`](file:///e:/MultiGo%20Reworked/core-model/src/test/java/com/hcmcbustracker/core/model/PositionCompensationEngineTest.kt) (20 tests)
2. [`RouteVehicleAggregatorTest.kt`](file:///e:/MultiGo%20Reworked/core-model/src/test/java/com/hcmcbustracker/core/model/RouteVehicleAggregatorTest.kt) (7 tests)
3. [`VehicleInterpolationEngineTest.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/VehicleInterpolationEngineTest.kt) (11 tests)
4. [`MapViewModelTest.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/test/java/com/hcmcbustracker/feature/map/MapViewModelTest.kt) (16 tests)

---

## 2. So sánh Trước và Sau theo Từng Nhóm Nguyên nhân

### 2.1. Quản lý 5 Mốc Thời gian, Tuổi GPS & Polling
- **Trước:** `TrackedVehicleState` không lưu tách biệt `requestStartedAtMillis`, `responseReceivedAtMillis`, `predictionUpdatedAtMillis`, `renderedAtMillis`; `Prediction.getGpsAgeSeconds()` tự parse tách biệt; `PredictionViewModel.refresh()` có thể chạy song song với polling loop; khi `dts` giữ nguyên giữa 2 chu kỳ, `MapViewModel` trả về `prevPos` cũ làm `gpsAgeSeconds` bị đóng băng theo số cũ.
- **Sau:**
  - Lưu đủ 5 mốc thời gian tới `TrackedVehicleState`, cung cấp tường minh `gpsAgeSeconds`, `networkLatencyMillis`, `dataProcessingDelayMillis`.
  - Bổ sung `queryMutex` + `refreshJob` trong `PredictionViewModel` và `activePollingRouteVar` guard trong `MapViewModel` chặn hoàn toàn polling/request trùng lặp.
  - Từ chối timestamp tương lai bất thường (`> +30s`) ở cả `PositionCompensationEngine`, `RouteVehicleAggregator` và `PredictionViewModel` (không bao giờ cho ghi đè bản ghi hợp lệ).
  - Thống nhất ngưỡng phân loại 5 mức trong [`PositionCompensationConfig`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/PositionCompensationEngine.kt#L92-L115): `FRESH (0–30s)`, `SLIGHT_DELAY (31–60s)`, `DELAYED (61–120s)`, `STALE (>120s)`, `UNKNOWN`.

### 2.2. Vùng Bù trừ Độ trễ 120–188 giây
- **Trước:** Cắt cứng tại `120s`: tại `119s` bù tới `+800m`, sang `121s` đột ngột gán `projected = 0m`, kéo giật lùi marker hàng trăm mét trong khi dữ liệu thực tế của EBMS thường có tuổi `140–188s`.
- **Sau:** Giữ nguyên tương thích ngược `calculateCompensation(..., allowAdaptiveExtendedProjection = false)` cho test Phase 5/9, đồng thời triển khai `calculateAdaptiveCompensation` cho tracking tuyến thực tế:
  - $0..120\text{s}$: bù trừ vật lý $v \cdot t$ (trần `900.0 m`).
  - $120..190\text{s}$ (chỉ khi `dts` chưa đóng băng $\ge 3$ chu kỳ): bù trừ phần đuôi suy giảm `0.35 * v * (t - 120s)`, liên tục $C^0$ tại biên `120s` (không giật lùi), gắn cờ `isAdaptiveAgedProjection = true` và `GpsLatencyCategory.STALE`.
  - $> 190\text{s}$ hoặc `dts` đóng băng / vận tốc $= 0$: ngưng projection và giữ nguyên vị trí hợp lệ cuối (`HELD_AT_LAST_VALID_GPS`), minh bạch phân loại nguồn `PositionSource`.

### 2.3. Tách 4 loại ID Trạm, Vượt trạm (`PASSED_STOP`), 6 Trạng thái & Bearing
- **Trước:** `startRouteWideVehiclePolling` ghi đè trạm người dùng chọn (`selectedStopId`) bằng `checkpointStopId`; vượt trạm (`effective <= 0`) bị gộp thành `AT_STOP` và kẹp cứng ở trạm cũ; snap trạm chỉ kiểm tra 1 trạm liền trước; ETA lấy trực tiếp từ checkpoint xa; bearing lấy từ 1 phân đoạn vi mô.
- **Sau:**
  - Tách bạch 4 biến trên `TrackedVehicleState` & `RouteVehicleTelemetry`: `selectedStopId`, `nextStopId`, `checkpointStopId`, `predictionTargetStopId`.
  - Thêm `unclampedProjectedDistanceMeters` trên `CompensationResult` để `VehicleInterpolationEngine` di chuyển xe vượt mượt qua trạm đã chọn dọc polyline hướng về `nextStopId`, tính `distanceToSelectedStopMeters` có dấu và phân loại đúng **`PASSED_STOP`** khi `< -25m`.
  - Tập trung toàn bộ 6 trạng thái (`MOVING`, `APPROACHING_STOP`, `ARRIVING_AT_STOP`, `AT_STOP`, `PASSED_STOP`, `UNKNOWN`) vào `PositionCompensationEngine.evaluateArrivalStatus` & `ArrivalStatusThresholds`.
  - Snap đơn điệu toàn chuỗi trạm (`snapStopsMonotonically`), kiểm tra lệch hành lang (`> 1,500m`), và tính bearing đa nguồn (`resolveSmoothedBearing` + `computeLookaheadTangent [-12m..+20m]` + `smoothBearingDegrees` qua biên `0°/360°`).

---

## 3. Giới hạn & Hạng mục Chờ Lượt 3–4
1. **Giao diện MapScreen chưa cập nhật (theo kế hoạch Lượt 3):** Marker mũi tên xoay theo `bearing`, chấm xanh dương vị trí điện thoại, camera auto-center/auto-follow và tối ưu nội dung `VehicleTrackingInfoCard` (để hiển thị đủ 5 mốc thời gian, `nextStop`/`selectedStop`, 6 trạng thái, nguồn tọa độ) sẽ được triển khai ở **Lượt 3**.
2. **Bảo toàn dữ liệu & cấu hình:** `BusTrackerDatabase` (`version = 3`), asset offline Phase 10, `applicationId` (`com.hcmcbustracker.app`) và signing configuration đều giữ nguyên 100%, không xóa/migration phá hủy.
3. **Chưa đối chiếu thực địa trên thiết bị thật sau Lượt 2/3:** Việc đối chiếu sai số thực tế nhiều xe giữa EBMS – Ứng dụng – MultiGo sẽ được thực hiện đầy đủ trong **Lượt 4**.
