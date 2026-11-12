# PHASE 11 — PHÂN TÍCH ĐỘ TRỄ GPS & LUỒNG THỜI GIAN (LƯỢT 1 AUDIT)

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Trạng thái:** Lượt 1 — Kiểm tra nguyên nhân & Phương án kỹ thuật (Chưa sửa code)  
**Thời điểm kiểm tra thực nghiệm:** `2026-11-12 14:11:07 – 14:12:44 (+07:00)`  

---

## 1. Luồng dữ liệu thời gian thực hiện tại (End-to-End Audit)

`EbmsApiService.getPredictionByStopId` (`https://apicms.ebms.vn`)  
$\rightarrow$ `SafeApiCall.execute` ([`SafeApiCall.kt`](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/util/SafeApiCall.kt))  
$\rightarrow$ `NetworkMappers.toDomain` ([`NetworkMappers.kt`](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/mapper/NetworkMappers.kt))  
$\rightarrow$ `BusRepositoryImpl` ([`BusRepositoryImpl.kt`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/repository/BusRepositoryImpl.kt))  
$\rightarrow$ `RouteVehicleAggregator` / `PositionCompensationEngine` / `VehicleInterpolationEngine`  
$\rightarrow$ `PredictionViewModel` & `MapViewModel` $\rightarrow$ `MapScreen` / `StopPredictionScreen`

---

## 2. Kết quả Đo thực nghiệm 7 Chu kỳ liên tiếp (Trạm `4223` Hàm Nghi, mỗi 10s)

| Chu kỳ | Giờ Client nhận | HTTP Time | Skew (`client - Date`) | Header Cache | Tuyến / Biển số | `dts` từ EBMS | `ageSrv = Date - dts` | `dts` đổi? |
| :---: | :---: | :---: | :---: | :--- | :--- | :---: | :---: | :---: |
| #1 | `14:11:07` | `234 ms` | `-2.02s` | `DYNAMIC / no-cache` | `44` / `50E20650` | `14:08:50+07:00` | **140.0s** | Initial |
| #1 | `14:11:07` | `234 ms` | `-2.02s` | `DYNAMIC / no-cache` | `01` / `50E76203` | `14:08:24+07:00` | **166.0s** | Initial |
| #2 | `14:11:18` | `246 ms` | `-1.77s` | `DYNAMIC / no-cache` | `44` / `50E20650` | `14:08:50+07:00` | **150.0s** | Không |
| #2 | `14:11:18` | `246 ms` | `-1.77s` | `DYNAMIC / no-cache` | `01` / `50E76203` | `14:08:54+07:00` | **146.0s** | **Có (+30s)** |
| #3 | `14:11:28` | `240 ms` | `-1.53s` | `DYNAMIC / no-cache` | `44` / `50E20650` | `14:08:50+07:00` | **160.0s** | Không |
| #3 | `14:11:28` | `240 ms` | `-1.53s` | `DYNAMIC / no-cache` | `01` / `50E76203` | `14:08:54+07:00` | **156.0s** | Không |
| #4 | `14:11:38` | `245 ms` | `-2.29s` | `DYNAMIC / no-cache` | `44` / `50E20650` | `14:09:10+07:00` | **151.0s** | **Có (+20s)** |
| #4 | `14:11:38` | `245 ms` | `-2.29s` | `DYNAMIC / no-cache` | `01` / `50E76203` | `14:09:04+07:00` | **157.0s** | **Có (+10s)** |
| #5 | `14:11:49` | `686 ms` | `-1.60s` | `DYNAMIC / no-cache` | `01` / `50E76203` | `14:09:04+07:00` | **167.0s** | Không |
| #6 | `14:11:59` | `248 ms` | `-1.35s` | `DYNAMIC / no-cache` | `01` / `50E76203` | `14:09:04+07:00` | **177.0s** | Không |
| #7 | `14:12:09` | `277 ms` | `-2.07s` | `DYNAMIC / no-cache` | `01` / `50E76203` | `14:09:04+07:00` | **188.0s** | Không |

*(Kiểm tra thêm tại Trạm 8 & Trạm 127 lúc `14:12:41` cho thấy `50E61852` có `dts = 14:10:02+07:00` và `50E61577` có `dts = 14:09:55+07:00`, độ tuổi ~159–166s).*

---

## 3. Phân loại Nguyên nhân Gốc rễ (Theo Section 2.2)

- **Nguyên nhân chính thuộc máy chủ/hệ thống EBMS (Loại A):**
  1. Dữ liệu `dts` do thiết bị giám sát hành trình/máy chủ EBMS công bố có **độ trễ cố hữu 138–188 giây** ngay tại thời điểm server phát hành snapshot mới (`14:11:20` phát `dts = 14:08:54`, tuổi gốc đã `146.0s`).
  2. Chu kỳ cập nhật `dts` trên server là **10–30 giây/nhịp**; giữa các nhịp, API trả lại y nguyên `d`, `t`, `s`, `dts`.
  3. CDN Cloudflare (`CF-Cache-Status: DYNAMIC`, `Cache-Control: no-cache`) **không** cache; đồng hồ server HTTP `Date` khớp giờ UTC+7 client trong `1.35–2.29s` (**không** có lỗi timezone/đơn vị, loại trừ Loại B và Loại D).

- **Các thiếu sót thuộc ứng dụng hiện tại:**
  1. **Thiếu mốc `renderedAt` & đồng bộ chỉ số độ trễ:** Hiện có `requestStartedAtMillis`, `responseReceivedTimeMillis`, `predictionReceivedAtMillis` trên `ArrivalVehicle` / `RouteVehicleTelemetry`, nhưng `TrackedVehicleState` chưa lưu `requestStartedAtMillis`, `responseReceivedAtMillis`, `predictionUpdatedAtMillis`, `renderedAtMillis`, dẫn tới chưa tính/hiển thị được `networkLatency`, `dataProcessingDelay`, và thời điểm ứng dụng cập nhật.
  2. **Lỗi "cắt cứng 120s" trong `PositionCompensationEngine` ([`PositionCompensationEngine.kt:74, 199`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/PositionCompensationEngine.kt#L74)):**
     - Hiện đặt `MAX_PROJECTION_AGE_SECONDS = 120L` và cắt thẳng `projectedDistanceMeters = 0.0` ngay khi `gpsAge > 120s`.
     - Vì dữ liệu thực tế của EBMS lúc `14:11` luôn có tuổi **`138–188s`**, engine hiện tại **tắt toàn bộ cơ chế bù trừ độ trễ (`STALE_DATA`, 0m bù trừ)** cho gần 100% xe sống ngoài thực tế, khiến vị trí xe bị trễ 140–188s so với thực tế; hoặc nếu xe ở sát biên `119s -> 121s`, marker **nhảy giật lùi hàng trăm mét**!
  3. **Ngưỡng phân loại độ mới hiển thị chưa khớp bảng Phase 11:**
     - Chưa phân loại tường minh 4 ngưỡng UI Phase 11 (`0–30s: Mới`, `31–60s: Có độ trễ nhẹ`, `61–120s: Có độ trễ`, `>120s: Dữ liệu cũ`, thiếu/lỗi: `Không xác định`), đồng thời cần chú thích rõ bản chất khi dữ liệu gốc EBMS trễ >120s.
  4. **Chiếm dụng đồng thời polling:** Cần rà soát đảm bảo không có request chồng chéo trên cùng xe/tuyến và hiển thị trung thực `gpsTimestamp` so với `predictionUpdatedAt` (tuyệt đối không thay thế `dts` bằng `responseReceivedAt`).

---

## 4. Triển khai Thực tế (Lượt 2)

1. **Chuẩn hóa đủ 5 mốc thời gian đầu-cuối & chỉ số trễ:**
   - Mở rộng [`RouteVehicleTelemetry`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/RouteVehicleTelemetry.kt#L27-L68), [`InterpolatedVehiclePosition`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/engine/VehicleInterpolationEngine.kt#L22-L50) và [`TrackedVehicleState`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt#L56-L109) với đầy đủ 5 mốc: `gpsTimestampMillis`, `requestStartedAtMillis`, `responseReceivedAtMillis`, `predictionUpdatedAtMillis`, `renderedAtMillis`.
   - Tính tường minh `gpsAge = now - gpsTimestamp` (không bao giờ thay `dts` bằng `responseReceivedAt`), `networkLatency = responseReceivedAt - requestStartedAt`, và `dataProcessingDelay = renderedAt - responseReceivedAt`.
   - Thống nhất chuẩn phân tích timestamp trong [`ArrivalVehicle.getGpsAgeSeconds`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/Prediction.kt#L140-L151) gọi thẳng `PositionCompensationEngine.parseGpsTimestampMillis`, hỗ trợ ISO offset (`+07:00`), UTC (`Z`), LocalDateTime VN, epoch giây/milliseconds và từ chối timestamp tương lai vượt ngưỡng dung sai `+30s`.
2. **Bảo vệ chống trùng lặp & ghi đè ngược Polling:**
   - Trong [`PredictionViewModel`](file:///e:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/PredictionViewModel.kt), bổ sung `refreshJob` + `queryMutex` loại bỏ request chồng chéo giữa kéo làm mới thủ công và polling job; bảo toàn mốc `dts` khi gặp dữ liệu `null` hoặc timestamp tương lai.
   - Trong [`MapViewModel`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt), bổ sung `activePollingRouteVar` + `routeJob?.cancel()` đảm bảo chỉ 1 vòng lặp polling hoạt động cho `(routeId, varId)`, cập nhật đúng `gpsAgeSeconds` tăng dần khi `dts` giữ nguyên mà không tạo chuyển động giả.
3. **Thang phân loại độ mới 5 cấp (`GpsLatencyCategory`) tập trung trong [`PositionCompensationConfig`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/PositionCompensationEngine.kt#L92-L115):**
   - `0–30s`: `FRESH` (*Mới*)
   - `31–60s`: `SLIGHT_DELAY` (*Có độ trễ nhẹ*)
   - `61–120s`: `DELAYED` (*Có độ trễ*)
   - `> 120s`: `STALE` (*Dữ liệu cũ*)
   - Lỗi / tương lai / thiếu: `UNKNOWN` (*Không xác định*)
