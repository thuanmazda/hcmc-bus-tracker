# ROUTE-WIDE VEHICLE TRACKING REPORT (PHASE 10)

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Phạm vi:** Thu thập, Khử trùng lặp và Hiển thị đồng thời TẤT CẢ xe hoạt động trên chi tiết tuyến  

---

## 1. Xác minh Khả năng Endpoint EBMS cho Toàn Tuyến

Từ tài liệu [`API_CATALOG.md`](file:///e:/MultiGo%20Reworked/docs/phase-0/API_CATALOG.md) và mã nguồn [`EbmsApiService.kt`](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/api/EbmsApiService.kt):
- EBMS **không** có API dạng `/prediction/getvehiclesbyroute`.
- Endpoint duy nhất trả dữ liệu xe sống là `GET /prediction/predictbystopid/{stopId}`.
- Kiểm thử thực tế (`ebms_gps_probe.py`) chứng minh: một trạm trên tuyến trả về không chỉ xe sát bên mà cả các xe đang tiến đến từ khoảng cách tới **31.5 km** (ví dụ: trạm 4223 Hàm Nghi trả về cùng lúc **6 xe** của Tuyến 102).

---

## 2. Chiến lược Truy vấn Điểm Kiểm tra (`Strategic Checkpoint Sampling`)

Để quét trọn vẹn toàn bộ xe dọc một biến thể tuyến (kể cả tuyến dài 25–40 km, tuyến nhiều nhánh hoặc tuyến vòng) mà không spam gọi 40–60 request cùng lúc cho mọi trạm:

1. **Chọn checkpoint (`BusRepositoryImpl.selectCheckpointStops`):**
   - Với $N \le 4$ trạm: lấy tối đa `3` trạm cuối danh sách (đảm bảo bao quát xe hướng về bến cuối).
   - Với $N > 4$ trạm: chọn tối đa **`5` điểm kiểm tra phân bố đều dọc chiều tuyến** tại các chỉ số `[N/5, 2N/5, 3N/5, 4N/5, N-1]` (trừ trùng lặp).
2. **Kiểm soát Song song & Timeout:**
   - Thực thi qua `coroutineScope` với **`Semaphore(permits = 4)`** — giới hạn tối đa 4 HTTP call đồng thời hướng về EBMS.
   - Mỗi checkpoint call bọc trong [`SafeApiCall.execute(timeoutMs = 8_000L)`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/repository/BusRepositoryImpl.kt#L423-L425).
   - Nếu gặp `HTTP 429`, bảo toàn `retryAfterSeconds` để chu kỳ poll kế tiếp lùi lịch hợp lý; nếu người dùng đổi chiều tuyến hoặc thoát màn hình, toàn bộ coroutine đang chờ bị **hủy tức thì**.

---

## 3. Thuật toán Tổng hợp & Khử trùng lặp Xe (`RouteVehicleAggregator`)

Vì cùng một chiếc xe (ví dụ `50E-615.77`) đi qua đoạn đầu tuyến sẽ đồng thời xuất hiện trong prediction của checkpoint #2, #3, #4 và bến cuối:

[`RouteVehicleAggregator.aggregateAndMerge`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/RouteVehicleTelemetry.kt#L59-L117) xử lý qua 4 bước:
1. **Lọc đúng tuyến & biến thể:** Chỉ nhận block `Prediction` có `pred.routeId == activeRouteId && pred.varId == activeVarId`.
2. **Nhóm theo biển số chuẩn hóa:** Khóa `plateNumber.trim().uppercase()` (bỏ qua record rỗng).
3. **Chọn 1 bản ghi uy tín nhất cho mỗi xe (`pickBestCandidate`):**
   - Ưu tiên 1: Có **`gpsTimestampMillis` lớn hơn (mới hơn)**.
   - Ưu tiên 2 (khi `dts` bằng nhau): Chọn checkpoint có **`distanceMeters` dương hợp lệ nhỏ nhất** — tức là trạm phía trước gần xe nhất $\rightarrow$ giảm thiểu sai số cộng dồn dọc polyline khi nội suy.
4. **Hợp nhất đơn điệu qua thời gian (`mergeMonotonically`):**
   - Nếu `incoming.gpsTimestampMillis < existing.gpsTimestampMillis` $\rightarrow$ loại bỏ response trễ.
   - Nếu `dts` giống hệt lần poll trước $\rightarrow$ gắn cờ `isGpsTimestampUnchanged = true` và `GpsFreshnessState.UNCHANGED` (hoặc `STALE` nếu > 180s).

---

## 4. Nội suy Đa xe & Thiết kế Marker Offline-Safe trên MapLibre

### 4.1. Nội suy song song trên Background Thread
- [`MapViewModel.interpolateRouteFleet`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt#L552-L598) chạy trên `Dispatchers.Default`, gọi trực tiếp `VehicleInterpolationEngine.interpolatePosition(...)` (nguyên bản Phase 5/9) cho **từng xe hợp lệ** trong danh sách sau khử trùng lặp.
- Lưu `previousInterpolatedByPlate` và `previousTelemetryByPlate` theo khóa biển số để mỗi xe giữ mượt độc lập. Nếu `isGpsTimestampUnchanged`, giữ nguyên tọa độ cũ không tạo bước nhảy giả.

### 4.2. Vì sao vẽ Biển số trực tiếp lên Bitmap (`createBusWithPlateBitmap`)
- Kiểm tra [`TileProvider.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/tile/TileProvider.kt) cho thấy JSON Style của MapLibre không cấu hình `"glyphs"` font server, nên dùng `PropertyFactory.textField(...)` thuần sẽ không hiển thị chữ (hoặc lỗi tải font) khi offline.
- Do đó, [`createBusWithPlateBitmap`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L1438-L1531) vẽ trực tiếp **biểu tượng xe buýt + viên nhộng biển số** bằng Android `Canvas`:
  - **Xe thường trên tuyến:** Vòng tròn xanh ngọc (`#00897B`) viền trắng + nhãn biển số nền tối (`#263238`).
  - **Xe GPS cũ (`STALE_DATA`):** Tông xám xanh (`#607D8B`).
  - **Xe đang được chọn (`selectedVehiclePlate`):** Phóng lớn, hào quang vàng (`#FFB300`), đỏ đô nổi bật (`#D32F2F`) + nhãn đỏ (`#B71C1C`).

### 4.3. Tương tác người dùng & Chuyển chiều (Lượt đi $\leftrightarrow$ Lượt về)
- **Từ Chi tiết tuyến (`RouteDetailScreen`):** Bổ sung thanh hành động **"Xem bản đồ & vị trí xe trên toàn tuyến"** gọi `onTrackRouteOnMap(routeId, selectedVariantId)` truyền cả `routeId` và `varId` vào `MapScreen`.
- **Chạm marker xe trên bản đồ:** `map.setOnMapClickListener` truy vấn `VEHICLE_LAYER_ID` trước `STOPS_LAYER_ID`, chọn đúng xe qua `viewModel.selectRouteVehicle(plate)` và mở thẻ thông số chi tiết bên dưới (biển số, vận tốc, cự ly đã bù trừ, ETA, trạm đích kế tiếp, giờ/tuổi GPS).
- **Chuyển chiều tuyến (`toggleRouteDirection` / `switchVariant`):** Hủy ngay `routePollingJob` của chiều cũ, xóa sạch `_routeVehicles`, `previousTelemetryByPlate`, `previousInterpolatedByPlate`, nạp polyline + trạm chiều mới tức thì từ SQLite offline rồi khởi động polling cho chiều mới.
