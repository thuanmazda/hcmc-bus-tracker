# GPS FRESHNESS & LIVE POLLING ANALYSIS (PHASE 10)

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Máy chủ chính thức:** `https://apicms.ebms.vn`  
**Nguyên tắc:** Bảo toàn nguyên vẹn thuật toán Phase 5/9 (`PositionCompensationEngine`, `VehicleInterpolationEngine`); chỉ tối ưu thu thập dữ liệu, kiểm soát cache/rate-limit, và bảo vệ tính đơn điệu của `dts`.

---

## 1. Kết quả Điều tra Thực nghiệm trên EBMS Live API

Nhóm kỹ thuật đã thực hiện đo đạc trực tiếp trên endpoint chính thức `/prediction/predictbystopid/{stopId}` (công cụ `ebms_gps_probe.py` & `check_4223.py`, 10 lần polling liên tiếp trên Trạm `4223` Hàm Nghi và 7 trạm rải đều Tuyến `01` biến thể `1`):

| Hạng mục đo đạc | Kết quả thực nghiệm | Kết luận kỹ thuật |
| :--- | :--- | :--- |
| **Header CDN / Cache** | `CF-Cache-Status: DYNAMIC`, `Cache-Control: no-cache`, không có header `Age` | Cloudflare CDN **không** cache response `/prediction/`; mỗi HTTP GET đi thẳng tới origin EBMS. |
| **Thời gian phản hồi** | `220–320 ms` (trạm thông thường), ~`870 ms` tối đa khi thăm dò | Độ trễ mạng client-server rất nhỏ, không phải nguyên nhân GPS cũ. |
| **Độ lệch đồng hồ Server (`Date`)** | `-1.64s` so với đồng hồ client cục bộ UTC+7 | Trường `dts` đã ở đúng định dạng `+07:00`, **không** bị sai múi giờ. |
| **Tuổi dữ liệu GPS (`server_time - dts`)** | Cố hữu **`112–145 giây`** trên hàng loạt tuyến (`01`, `44`, `65`, `86`, `102`) | Đây là độ trễ đồng bộ từ thiết bị hành trình trên xe về trung tâm BMS/EBMS backend, không phải lỗi tại app Android. |
| **Chu kỳ máy chủ cập nhật `dts`** | Thay đổi theo nhịp ~**`30 giây/lần`** (ví dụ `12:19:07` $\rightarrow$ `12:19:37`) | Polling nhanh hơn `10s` (ví dụ `1–3s`) không tạo ra `dts` mới hơn mà chỉ nhận lại cùng snapshot cũ và tăng rủi ro `HTTP 429`. |

---

## 2. Kiến trúc Thu thập & Quản lý Thời gian (Phase 10 Upgrades)

### 2.1. Phân tách 4 mốc thời gian rõ ràng trên từng Telemetry
Trong [`ArrivalVehicle`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/Prediction.kt#L39-L54) và [`RouteVehicleTelemetry`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/RouteVehicleTelemetry.kt#L27-L47):
1. **`requestStartedAtMillis`**: Thời điểm client bắt đầu gửi HTTP request.
2. **`responseReceivedAtMillis`**: Thời điểm [`NetworkMappers.kt`](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/mapper/NetworkMappers.kt) ánh xạ response về domain.
3. **`predictionReceivedAtMillis`**: Thời điểm dữ liệu sẵn sàng cho tầng UI/interpolation.
4. **`gpsTimestampMillis`**: Thời điểm GPS thô thực tế trên xe phân tích từ chuỗi ISO `dts`.

### 2.2. Header `Cache-Control: no-cache` & Hỗ trợ `HTTP 429 Retry-After`
- Trong [`NetworkModule.kt`](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/di/NetworkModule.kt#L44-L46), mọi request chứa `/prediction/` tự động gắn `Cache-Control: no-cache` để ngăn cache trung gian phía thiết bị/proxy.
- [`SafeApiCall.execute`](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/util/SafeApiCall.kt#L17-L57) bổ sung:
  - Timeout tường minh qua `withTimeoutOrNull` (trả `HTTP 408` khi quá hạn, mặc định `10,000 ms`, route-wide checkpoints `8,000 ms`).
  - Bắt `HttpException` mã **`429`**: đọc header `Retry-After` (kẹp an toàn `[5s..300s]`, mặc định `30s` nếu thiếu header) và truyền vào [`NetworkResult.Error.retryAfterSeconds`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/NetworkResult.kt#L8-L13).
  - Bảo toàn tuyệt đối `CancellationException` để không cản trở hủy coroutine khi người dùng đổi chiều hoặc thoát màn hình.

### 2.3. Chu kỳ Polling Linh hoạt & Lifecycle-Aware
- Tần suất polling do người dùng cấu hình trong Settings (`10s`, `15s` chuẩn, `30s`, `60s`), luôn kẹp an toàn `[10_000 ms .. 120_000 ms]` tại cả [`BusRepositoryImpl`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/repository/BusRepositoryImpl.kt), [`PredictionViewModel`](file:///e:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/PredictionViewModel.kt#L54-L59) và [`MapViewModel`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt).
- Exponential backoff khi gặp lỗi liên tiếp (`base -> 2x -> 4x -> 8x`, trần `120s`), và ưu tiên dùng `retryAfterSeconds` nếu nhận `HTTP 429`.
- Tự động tạm dừng polling khi màn hình vào `ON_PAUSE` và chạy lại khi quay về `ON_RESUME` (cả [`StopPredictionScreen.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/StopPredictionScreen.kt) và [`MapScreen.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt)).

### 2.4. Bảo vệ Đơn điệu `dts` & Phát hiện Dữ liệu Đóng băng (`Unchanged dts`)
- **Chống ghi đè ngược (Monotonic Check):** Tại [`PredictionViewModel.mergePredictionsMonotonically`](file:///e:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/PredictionViewModel.kt#L256-L303) và [`RouteVehicleAggregator.mergeMonotonically`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/RouteVehicleTelemetry.kt#L146-L190), nếu `incoming.gpsTimestampMillis < existing.gpsTimestampMillis` (response cũ đến muộn do lag mạng), hệ thống **từ chối cập nhật và giữ nguyên bản ghi mới hơn**.
- **Khi `dts` giữ nguyên qua nhiều lần poll (`incoming == existing`):**
  - Đánh dấu `isGpsTimestampUnchanged = true` và tăng `consecutiveUnchangedPolls`.
  - Ở `MapViewModel.interpolateRouteFleet`, nếu `isGpsTimestampUnchanged` và cùng checkpoint đích, vị trí marker **giữ nguyên ổn định tại điểm hợp lệ cuối cùng** — **không** tạo chuyển động giả đánh lừa người dùng.
  - Giao diện hiển thị badge trung thực `"⏱ GPS giữ nguyên"` trên thẻ xe/bến (`StopPredictionScreen`, `MapScreen`).

---

## 3. Bảo toàn Thuật toán Phase 5 & Phase 9

- Toàn bộ công thức tính vận tốc/cự ly bù trừ theo tuổi GPS trong `PositionCompensationEngine` và công thức định vị theo polyline `VehicleInterpolationEngine` được **giữ nguyên 100%**.
- Toàn bộ bộ unit test regression của Phase 5 & Phase 9 (`PositionCompensationEngineTest`, `VehicleInterpolationEngineTest`) tiếp tục chạy độc lập và phải giữ nguyên kết quả.
