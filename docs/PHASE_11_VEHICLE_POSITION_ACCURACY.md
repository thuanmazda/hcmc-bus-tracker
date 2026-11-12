# PHASE 11 — PHÂN TÍCH ĐỘ CHÍNH XÁC VỊ TRÍ XE & BÙ TRỄ (LƯỢT 1 AUDIT)

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Trạng thái:** Lượt 1 — Kiểm tra thuật toán Phase 5 & Phase 9 + Xác minh hình học thực tế (Chưa sửa code)  

---

## 1. Xác minh Ý nghĩa Trường Dữ liệu EBMS (`d`, `t`, `s`, `dts`, `sts`)

Kiểm thử đối chiếu đồng thời trên Tuyến `01` Biến thể `1` (`stops=30`, `polyPts=219`, `polyLen=8,542.5 m`) lúc `14:11 (+07:00)`:

| Xe (`v`) | Trạm A (`id=127`, `cum=4,675.7m`) | Trạm B (`id=139`, `cum=8,219.0m`) | Trạm C (`id=8`, `cum=8,542.5m`) | Chênh lệch EBMS `d` | Chiều dài Polyline thực | Sai lệch |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| `50E61577` | `d = 2,084.5m` | `d = 5,625.1m` | `d = 5,948.3m` | `A -> C = 3,863.8m` | `3,866.8m` *(chim bay `3,346.1m`)* | **`2.9m` (0.075%)** |
| `50E76203` | `d = 4,483.5m` | `d = 8,024.2m` | `d = 8,347.4m` | `A -> C = 3,863.9m` | `3,866.8m` | **`2.9m` (0.075%)** |
| `50E61852` | — | `d = 406.4m` | `d = 729.6m` | `B -> C = 323.2m` | `323.4m` *(chim bay `275.8m`)* | **`0.2m` (0.06%)** |

- **Kết luận 100% có căn cứ:**
  - `d` của EBMS là **khoảng cách dọc theo polyline chiều tuyến (`varId`)** tính từ vị trí xe tại thời điểm `dts` đến đúng `stopId` đó (không phải đường chim bay).
  - API `/prediction/predictbystopid/{stopId}` chỉ trả 6 khóa: `v`, `s` (km/h), `d` (m), `t` (giây), `dts` (ISO+07:00), `sts` (`Int`); **không có** tọa độ GPS thô và **không có** `heading`.

---

## 2. Nguyên nhân Vị trí Xe Sai lệch khi Kiểm tra Thuật toán Phase 5 & Phase 9

1. **Tắt hoàn toàn bù trừ khi `gpsAge > 120s` & Nhảy lùi tại ngưỡng 120s ([`PositionCompensationEngine.kt:74, 199`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/PositionCompensationEngine.kt#L199)):**
   - Dữ liệu EBMS thực tế hiện nay thường có tuổi `138–188s`. Ngưỡng cắt cứng `120s` khiến `projectedDistanceMeters` tụt về `0.0 m` (nếu `age = 119s @ 25 km/h` xe được bù `+826m`, chỉ 2 giây sau tại `121s` xe bị kéo giật lùi `-826m`!).
   - Tuy nhiên, nếu nới ngưỡng quá cao mà vẫn phóng tuyến tính thô `speed * gpsAge` lên tới `200s+` thì xe đi nhanh ở ngoại thành (`50 km/h`) có thể bị đẩy quá lố hàng km nếu xe vừa dừng đèn đỏ.
2. **Bù trừ vượt mốc dừng/vượt chiều khi chưa xét trạm kế tiếp & nhánh tuyến:**
   - Trong [`VehicleInterpolationEngine.kt:95`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/engine/VehicleInterpolationEngine.kt#L95), khi chiếu về checkpoint xa (`n-1`, cách 8km), `effectiveDistance = d - projectedDistance` vẫn dương nên tiếp tục cộng trừ từ điểm checkpoint xa; trong khi nếu người dùng mở từ một trạm gần bị xe đi qua (`effective <= 0`), engine kẹp `cumulativeDist = targetStopCumDist` ("dính chết" ở trạm đó thay vì biết xe đã đi qua trạm và đang tiến tới trạm tiếp theo!).
   - Hàm [`findStopCumulativeDistance`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/engine/VehicleInterpolationEngine.kt#L142-L179) chỉ dùng 1 trạm liền trước (`prevStop`) làm `minCumDist`, nhưng không bắt buộc `cumDist(stop[i]) >= cumDist(stop[i-1])` trên toàn chuỗi; với tuyến vòng lặp hoặc hai chiều trùng hành lang (ví dụ Bến Thành - Chợ Lớn), trạm gần nút giao có thể snap sang đoạn polyline quay lại!
3. **Tính toán góc quay (`bearing`) thiếu độ ổn định:**
   - [`interpolatePointAtDistance`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/engine/VehicleInterpolationEngine.kt#L267) lấy trực tiếp `computeBearing(p1, p2)` của **1 phân đoạn polyline đơn lẻ** mà `v` đang đứng. Nếu polyline có hai đỉnh rất sát nhau hoặc góc ngoặt nhỏ/ngược nhiễu, hướng mũi tên giật loạn hoặc trỏ sai góc giao lộ. Đồng thời chưa ưu tiên vector di chuyển tin cậy giữa hai lần lấy mẫu liên tiếp và chưa làm mượt qua biên `0°/360°`.
4. **Mất đồng nhất khi `onViewVehicleOnMap` mở từ `StopPredictionScreen` rồi bị polling ghi đè:**
   - [`MapViewModel.trackVehicle`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt#L678-L770) nội suy theo trạm người dùng chọn (`stopId`), nhưng ngay sau đó `startRouteWideVehiclePolling` dòng `521` ghi đè `_trackedVehicle` bằng `checkpointStopId` (một trạm ngẫu nhiên trong 5 checkpoints), làm vị trí/cự ly đổi đột ngột trước mắt người dùng.

---

## 3. Thuật toán Đã Triển khai (Lượt 2)

1. **Bù trừ có giới hạn & liên tục qua mốc 120s ([`PositionCompensationEngine.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/PositionCompensationEngine.kt)):**
   - Bảo toàn 100% hành vi tương thích ngược của `calculateCompensation(..., allowAdaptiveExtendedProjection = false)` (toàn bộ test Phase 5/9 giữ nguyên kết quả).
   - Triển khai `calculateAdaptiveCompensation` (`allowAdaptiveExtendedProjection = true`) sử dụng công thức liên tục bậc 0 ($C^0$) tại ngưỡng 120s:
     - $0 \le \text{gpsAge} \le 120\text{s}$: $\text{proj} = v \cdot \text{gpsAge}$ (giới hạn trần `maxProjectedDistanceMeters = 900.0 m`).
     - $120\text{s} < \text{gpsAge} \le 190\text{s}$ (khi `dts` không đóng băng `consecutiveUnchangedPolls < 2`): $\text{proj} = \min(900\text{m},\; v \cdot 120 + v \cdot (\text{gpsAge} - 120) \cdot 0.35)$ — liên tục hoàn toàn qua mốc `120s`, triệt tiêu hiện tượng nhảy lùi hàng trăm mét.
     - $\text{gpsAge} > 190\text{s}$ hoặc `dts` đóng băng $\ge 3$ chu kỳ (`isGpsTimestampFrozen`) hoặc tốc độ $\le 1.5\text{ km/h}$: dừng projection; nếu xe đang có tọa độ hợp lệ trước đó, `VehicleInterpolationEngine` giữ nguyên vị trí hợp lệ cuối (`HELD_AT_LAST_VALID_GPS`) thay vì kéo tụt lùi về tọa độ gốc cũ.
   - Gắn `unclampedProjectedDistanceMeters` riêng để khi xe đi qua trạm chọn (`rawDistance - unclampedProjected < 0`), tọa độ xe vẫn tiến mượt qua trạm dọc polyline thay vì bị dính chết tại trạm cũ.
2. **Snap Trạm Đơn Điệu & Kiểm tra Sai Chiều ([`VehicleInterpolationEngine.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/engine/VehicleInterpolationEngine.kt)):**
   - `snapStopsMonotonically(...)` chiếu tuần tự toàn bộ danh sách `routeStops` với ràng buộc `cumDist[i] >= cumDist[i-1]`, chống snap nhầm vào nhánh trùng/đoạn vòng quay lại.
   - Kiểm tra `crossTrackErrorMeters <= 1,500m` và `isDirectionValid`: nếu checkpoint nằm ngoài hành lang polyline hoặc sai chiều tuyến $\rightarrow$ trả về `null`, tuyệt đối không ghép marker sang polyline chiều ngược lại.
3. **Thuật toán Bearing đa nguồn & làm mượt qua `0°/360°`:**
   - `computeLookaheadTangent` lấy tiếp tuyến qua cửa sổ `[-12m .. +20m]` dọc polyline, loại nhiễu vi phân đoạn `1–2m`.
   - `resolveSmoothedBearing` ưu tiên vector di chuyển thực tế giữa 2 mẫu liên tiếp (khi đi $\ge 8\text{m}$ đồng thuận trong $\pm 75^\circ$ với hướng tuyến), kết hợp `smoothBearingDegrees` qua phép chuẩn hóa góc ngắn nhất `[-180°, +180°]` (không giật khi qua `359° <-> 0°`).
   - Xe đứng yên (`speed <= 1.5 km/h`) giữ nguyên góc hướng tin cậy trước đó hoặc tiếp tuyến tuyến; polyline thoái hóa trả `hasValidHeading = false`.
