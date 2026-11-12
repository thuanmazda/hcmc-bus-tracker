# PHASE 11 — PHÂN TÍCH CỰ LY ĐẾN TRẠM, TRẠNG THÁI & ETA (LƯỢT 1 AUDIT)

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Trạng thái:** Lượt 1 — Audit nguyên nhân sai lệch cự ly & trạng thái khi bấm biển số xe (Chưa sửa code)  

---

## 1. Tại sao Cự ly đến Trạm & Trạng thái bị Sai khi Nhấn Biển số Xe?

Qua đọc trực tiếp [`MapViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt), [`BusRepositoryImpl.kt`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/repository/BusRepositoryImpl.kt) và [`MapScreen.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt):

### Nguyên nhân 1: Dùng "Checkpoint lấy mẫu" thay cho "Trạm kế tiếp" hoặc "Trạm đang chọn"
- `fetchRouteVariantVehicles` chỉ gọi 5 trạm checkpoint `[N/5, 2N/5, 3N/5, 4N/5, N-1]`.
- Khi nhấn vào một xe trên bản đồ (`MapViewModel.selectRouteVehicle`, dòng `600-632`), thẻ thông tin hiển thị trực tiếp `telemetry.checkpointStopName`, `position.effectiveDistanceMeters` và `telemetry.etaSeconds` — tức là **cự ly và ETA tới trạm checkpoint** (có thể cách 3–8 km với hàng chục trạm nằm giữa!) thay vì **trạm kế tiếp thực sự ngay phía trước xe**.
- Khi người dùng mở xe từ `StopPredictionScreen` của một trạm cụ thể (ví dụ trạm #12), `trackVehicle` ban đầu tính theo trạm #12, nhưng vài giây sau `startRouteWideVehiclePolling` (dòng `516-522`) **ghi đè** `_trackedVehicle` bằng `checkpointStopId` (ví dụ trạm #24), làm cự ly và tên trạm đích đột ngột nhảy sang trạm khác.

### Nguyên nhân 2: Gộp chung "Đã tới trạm" và "Đã đi qua trạm"
- `PositionCompensationEngine` dòng `219`: mọi trường hợp `effective <= 0.0` (dù còn `0m` tại trạm hay xe đã vượt qua trạm đang chọn tới `1.5 km`) đều bị gắn chung nhãn `AT_STOP` (`"🏁 Đã đến trạm"`).
- Cần phân biệt rõ dựa trên `signedRouteDistance = selectedStopCumDist - vehicleCumDist`:
  - `signedRouteDistance < -30m`: Xe **đã đi qua trạm đang chọn** (đồng thời vẫn tính chính xác khoảng cách tới trạm kế tiếp thực sự phía trước!).
  - `abs(signedRouteDistance) <= 40m` & `speedKmH <= 3.0`: **"Đang dừng tại trạm"**.
  - `effectiveDistanceToNextStop <= 80m`: **"Đang đến trạm"**.
  - `effectiveDistanceToNextStop <= 350m` hoặc `etaSeconds in 1..60`: **"Sắp đến trạm"**.

### Nguyên nhân 3: ETA (`t`) chưa được đồng bộ lại theo Trạm kế tiếp
- `toTrackedVehicleState()` (dòng `622`) lấy nguyên `telemetry.etaSeconds` của EBMS tính tới **trạm checkpoint xa**, khiến người dùng nhìn thấy `"Hướng về Trạm X • Cách 350m • ETA 18 phút"` hoặc `"Cách 6.2 km"` gây mâu thuẫn cảm quan.
- Khi đã xác định đúng `nextStop` (trạm kế tiếp thực sự dọc polyline) và có khoảng cách dọc tuyến `distToNextStop`:
  - Nếu `nextStop.id == telemetry.checkpointStopId` (và `gpsAge` nhỏ): ưu tiên dùng hoặc hiệu chỉnh từ `etaSeconds` EBMS theo phần cự ly còn lại.
  - Nếu khác checkpoint: ước lượng ETA đến trạm kế tiếp chỉ khi có vận tốc hợp lệ (`> 3 km/h`) hoặc tốc độ trung bình tuyến hợp lý, và **ẩn/đánh dấu `--`** nếu xe đang dừng (`speed ~ 0`) hoặc `INVALID_SPEED` / `Dữ liệu quá cũ` thay vì hiển thị ETA ảo của trạm khác!

### Nguyên nhân 4: Hiển thị nhãn sai nguồn gốc vị trí
- [`VehicleMetricBox`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt#L1684) hiện ghi sublabel `"Trực tiếp"` mỗi khi `projectedDistanceMeters == 0`, kể cả khi tọa độ thực chất là **nội suy từ cự ly EBMS có tuổi GPS 150 giây**. Phải thay thế bằng nhãn chính xác (`"Nội suy theo tuyến (tại GPS)"`, `"Dự đoán bù trễ (+Xm)"`, hoặc `"GPS đã cũ — giữ vị trí gốc"`).

---

## 2. Kết quả Triển khai & Ngưỡng Cấu hình Tập trung (Lượt 2)

1. **Tách bạch hoàn toàn 4 ID trạm từ Repository $\rightarrow$ ViewModel $\rightarrow$ UI State:**
   - `selectedStopId`: Trạm do người dùng chọn từ `StopPredictionScreen` (`MapViewModel.focusedSelectedStopId`), tuyệt đối không bị ghi đè bởi `checkpointStopId` khi polling chạy.
   - `nextStopId` / `nextStopName`: Trạm kế tiếp thực sự dọc chiều polyline phía trước `vehicleCumulativeDistance`, kèm `distanceToNextStopMeters` và `etaToNextStopSeconds`.
   - `checkpointStopId`: Trạm checkpoint dùng để thu thập response từ EBMS.
   - `predictionTargetStopId`: ID trạm thực sự mà giá trị `ebms_d` đang tham chiếu.
2. **Phân loại đủ 6 Trạng thái Xe Tập trung tại [`ArrivalStatusThresholds`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/PositionCompensationEngine.kt#L78-L90) & [`PositionCompensationEngine.evaluateArrivalStatus`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/PositionCompensationEngine.kt#L166-L207):**
   - `PASSED_STOP`: Khi `distanceToSelectedStopMeters < -25.0m` (xe đã vượt qua trạm đang chọn theo chiều tuyến), tách biệt hoàn toàn khỏi `AT_STOP`!
   - `AT_STOP`: `|dist| <= 40m`, `speed <= 3.0 km/h`, và `gpsAge <= 120s`.
   - `ARRIVING_AT_STOP`: `dist <= 85m` và `gpsAge <= 120s`.
   - `APPROACHING_STOP`: `dist <= 350m` hoặc (`eta in 1..60s` với GPS `<= 120s`), và `gpsAge <= 190s`.
   - `MOVING`: Xe đang chạy hợp lệ ngoài vùng tiếp cận gần.
   - `UNKNOWN`: Thiếu hướng tuyến, sai hình học, vận tốc bất thường, hoặc dữ liệu GPS quá cũ (`> 190s`) — ngăn tuyệt đối cảnh báo "Đang đến trạm" giả tạo từ dữ liệu cũ.
3. **Tính lại ETA tin cậy (`computeReliableEtaSeconds`):**
   - Nếu `displayedStopId == predictionTargetStopId` và GPS $\le 120\text{s}$: giữ và điều chỉnh ETA EBMS tương ứng phần cự ly bù trừ.
   - Nếu hiển thị trạm kế tiếp khác checkpoint và tốc độ $\ge 3.0\text{ km/h}$ với GPS $\le 190\text{s}$: ước lượng $d_{\text{next}} / v$ (giới hạn `7200s`). Nếu xe dừng hoặc GPS quá cũ (`> 190s`), trả `null` thay vì gắn ETA sai trạm.
