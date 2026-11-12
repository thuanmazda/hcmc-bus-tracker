# HCMC BUS TRACKER – PHASE 9 IMPLEMENTATION PLAN
# INTELLIGENT VEHICLE TRACKING, POSITION COMPENSATION & MAP UX OPTIMIZATION

**Project:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Version:** 1.0.0 (Build Phase 9)  
**Date:** 2026-11-11  
**Author:** Antigravity Agentic Assistant

---

## 1. MỤC TIÊU VÀ PHẠM VI PHASE 9

1. **Hiệu chỉnh độ trễ GPS (GPS Latency Compensation):**
   - Xây dựng cơ chế bù trừ thời gian thực dựa trên timestamp GPS (`dts`) và vận tốc (`s`), phân định rõ ràng giữa thời gian GPS gói tin, thời gian ứng dụng nhận response và thời điểm hiển thị.
   - Tính toán khoảng cách hiệu dụng (`effectiveDistanceToStop`) một cách có cơ sở, không sử dụng hằng số giả định.
2. **Đối chiếu và làm rõ sai số khoảng cách với MultiGo:**
   - Phân tích nguyên nhân kỹ thuật khiến khoảng cách trên HCMC Bus Tracker (hiện tại hiển thị khoảng cách gốc EBMS) chênh lệch so với MultiGo (hiển thị khoảng cách đã dự phóng bù trừ độ trễ).
   - Thiết lập bảng so sánh dữ liệu với tối thiểu 10 ca kiểm thử mẫu.
3. **Nâng cấp thuật toán nội suy vị trí xe (Intelligent Vehicle Tracking):**
   - Khắc phục lỗi lùi vị trí máy móc: chiếu điểm dừng tuần tự theo lộ trình (Sequential Monotonic Snapping) để tránh chọn nhầm đoạn polyline khi tuyến có vòng lặp, chạy song song hoặc nút giao nhiều tầng.
   - Tích hợp cơ chế làm mượt vị trí (Smoothing) có kiểm soát gia tốc/vận tốc, ngăn marker nhảy loạn hoặc bay qua trạm đích.
4. **Thiết kế lại Marker GPS điện thoại:**
   - Chuyển đổi từ chấm đỏ/xanh sang **Mũi tên định hướng màu đỏ** xoay theo góc phương vị (bearing) của thiết bị, kèm vòng tròn hiển thị bán kính độ chính xác GPS.
   - Xử lý trạng thái trung tính khi chưa có dữ liệu bearing. Phân biệt rõ rệt với marker xe buýt.
5. **Thiết kế lại giao diện bản đồ hiện đại:**
   - Tối ưu bố cục: Bản đồ tràn viền, thanh thông tin tuyến trên đỉnh, thanh công cụ FABs bên phải, thẻ thông tin xe bo góc gọn gàng ở cạnh dưới.
   - Loại bỏ các dòng cảnh báo kỹ thuật dài dòng, thay thế bằng các nhãn ngắn gọn (`Vị trí ước lượng`, `Đang cập nhật`, `Dữ liệu cũ`).
6. **Tối ưu hiệu năng hiển thị bản đồ và điểm dừng:**
   - Giảm tải CPU/Main Thread bằng cách xử lý GeoJSON trên `Dispatchers.Default`.
   - Cơ chế bộ đệm không gian (Spatial Containment Cache) và Debounce sự kiện camera (300ms) để loại bỏ việc truy vấn Room lặp đi lặp lại khi người dùng vuốt nhẹ bản đồ.
   - Gom cụm thông minh (Clustering) ở mức zoom thấp và chỉ hiển thị chi tiết khi zoom gần.

---

## 2. KẾT QUẢ RÀ SOÁT SOURCE CODE HIỆN TẠI (AUDIT FINDINGS)

Dưới đây là kết quả kiểm tra vị trí tiếp nhận và xử lý các trường dữ liệu EBMS trong mã nguồn hiện có:

| Trường EBMS | Tên trường DTO | Vị trí tiếp nhận DTO | Ánh xạ Domain Model | Vị trí xử lý hiện tại | Đánh giá hiện trạng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `v` (biển số) | `plateNumber` | `PredictionDto.kt:20` | `ArrivalVehicle.plateNumber` | `MapViewModel.kt:349`, `MapScreen.kt:597` | Đã nhận và format dạng `50E-206.51`. |
| `d` (cự ly mét) | `distanceMeters` | `PredictionDto.kt:22` | `ArrivalVehicle.distanceMeters` | `MapViewModel.kt:339`, `VehicleInterpolationEngine.kt:51` | Hiện tại dùng trực tiếp cự ly thô `d` để lùi trên polyline, **chưa bù trừ độ trễ GPS**. |
| `s` (vận tốc km/h)| `speedKmH` | `PredictionDto.kt:23` | `ArrivalVehicle.speedKmH` | `MapViewModel.kt:341`, `VehicleInterpolationEngine.kt:71` | Đã nhận nhưng chỉ dùng hiển thị trên UI, **chưa dùng để dự phóng quãng đường**. |
| `t` (ETA giây) | `etaSeconds` | `PredictionDto.kt:21` | `ArrivalVehicle.etaSeconds` | `StopPredictionScreen.kt:344`, `ArrivalVehicle.formattedEta` | Đã hiển thị dạng "4 phút 15 giây", nhưng chưa truyền sang màn hình bản đồ. |
| `dts` (GPS ISO) | `timestampIso` | `PredictionDto.kt:24` | `ArrivalVehicle.timestampIso` | `ArrivalVehicle.getGpsAgeSeconds()` | Đã parse ISO kiểm tra `isGpsStale(180s)`, **nhưng chưa truyền sang `MapViewModel.trackVehicle`**. |
| `sts` (trạng thái) | `status` | `PredictionDto.kt:25` | `ArrivalVehicle.status` | `ArrivalVehicle.status` | Đã nhận từ network nhưng chưa khai thác trên giao diện bản đồ. |

### Các khiếm khuyết kỹ thuật được phát hiện:
1. **Mất thông tin khi chuyển màn hình:**
   - Tại `MainActivity.kt:212`, tham số truyền vào route `interactive_map?routeId=...&plate=...` chỉ bao gồm `routeId, varId, stopId, plate, distance, speed`.
   - **Thiếu `timestampIso` (`dts`) và `etaSeconds` (`t`)**. Do đó, `MapViewModel` hoàn toàn không có dữ liệu thời gian GPS để tính toán độ trễ!
2. **Thuật toán tìm trạm trên polyline ở Phase 8 còn đơn giản:**
   - Trong `VehicleInterpolationEngine.kt:40`, hàm chỉ lặp tìm điểm đỉnh polyline gần `stopLocation` nhất theo khoảng cách chim bay. Nếu tuyến đường có 2 chiều đi gần nhau trên cùng đại lộ hoặc có nút xoay vòng (roundabout), thuật toán có thể chọn nhầm đỉnh của đoạn đường ngược chiều hoặc đoạn đã đi qua trước đó.
3. **Marker GPS người dùng:**
   - Được vẽ bằng `CircleLayer` màu xanh/đỏ (`USER_LOCATION_LAYER_ID`) cố định, không nhận diện góc hướng di chuyển (bearing) và chưa có mũi tên chỉ hướng.
4. **Hiệu năng hiển thị trạm:**
   - `MapScreen.kt` đăng ký `addOnCameraIdleListener` và gọi `viewModel.onCameraIdle(bounds, zoom)` trực tiếp mà không có debounce.
   - Hàm `toStopFeatureCollection()` chuyển đổi hàng trăm trạm sang GeoJSON thực thi trên Main Thread mỗi khi `stopsInViewport` cập nhật, gây giật lag khi lia bản đồ.

---

## 3. THIẾT KẾ CƠ CHẾ BÙ TRỪ ĐỘ TRỄ GPS (POSITION COMPENSATION ENGINE)

### 3.1. Phân biệt các mốc thời gian độc lập
Để đảm bảo tính khoa học và không giả định sai dữ liệu:
- $T_{\text{gps}}$ (`gpsTimestampMillis`): Thời điểm thiết bị định vị trên xe buýt ghi nhận tọa độ, được trích xuất từ chuỗi ISO `dts` (ví dụ: `2026-11-09T21:13:29+07:00`).
- $T_{\text{recv}}$ (`responseReceivedAtMillis`): Thời điểm ứng dụng nhận phản hồi HTTP từ EBMS API (`System.currentTimeMillis()`).
- $T_{\text{pred}}$ (`predictionUpdatedAtMillis`): Thời điểm dữ liệu dự báo được nạp vào StateFlow.
- $T_{\text{render}}$ (`renderUpdatedAtMillis`): Thời điểm khung hình bản đồ được cập nhật.

### 3.2. Công thức bù trừ cự ly (Dead Reckoning Projection)
Tuổi của gói tin GPS tại thời điểm hiển thị:
$$\Delta t_{\text{age}} = \frac{T_{\text{render}} - T_{\text{gps}}}{1000} \quad (\text{giây})$$

Quãng đường xe đã di chuyển thêm trong khoảng thời gian trễ:
$$d_{\text{projected}} = \left( s \times \frac{1000}{3600} \right) \times \Delta t_{\text{age}} \quad (\text{mét})$$

Cự ly hiệu dụng từ xe đến trạm đích:
$$d_{\text{effective}} = \max(0.0, \, d_{\text{EBMS}} - d_{\text{projected}})$$

### 3.3. Các điều kiện biên & Ngưỡng an toàn bắt buộc:
1. **Xe đứng yên ($s \le 1.0\text{ km/h}$):** $d_{\text{projected}} = 0$, giữ nguyên cự ly gốc $d_{\text{EBMS}}$.
2. **Vận tốc không hợp lệ ($s < 0$ hoặc $s > 90.0\text{ km/h}$):** Vận tốc bất thường trong đô thị, không áp dụng dự phóng, sử dụng $d_{\text{EBMS}}$.
3. **Giới hạn thời gian dự phóng tối đa ($\Delta t_{\text{max}} = 120\text{ giây}$):** Nếu $\Delta t_{\text{age}} > 120\text{s}$, không tiếp tục dự phóng tuyến tính vì điều kiện giao thông thay đổi; chuyển sang trạng thái "Dữ liệu cũ" (`STALE`).
4. **Xe đã cập bến ($d_{\text{effective}} \le 0$):** Nếu $d_{\text{projected}} \ge d_{\text{EBMS}}$, xe được coi là đã tới trạm đích; ghim vị trí xe tại trạm đích kèm nhãn "Đang cập bến", không lùi ngược qua trạm đích.
5. **Timestamp không hợp lệ / Thiếu $dts$:** Nếu không parse được $dts$, sử dụng độ trễ từ $T_{\text{recv}}$ với mức trần an toàn 15 giây, hoặc giữ nguyên cự ly gốc kèm cảnh báo dữ liệu.

---

## 4. PHÂN TÍCH ĐỐI CHIẾU KHOẢNG CÁCH VỚI MULTIGO

### 4.1. Bản chất sự khác biệt giữa HCMC Bus Tracker và MultiGo
- **EBMS API:** Chỉ trả về cự ly tĩnh $d$ tại thời điểm hộp đen xe buýt gửi bản tin GPS gần nhất ($dts$). Chu kỳ gửi của thiết bị giám sát hành trình thường là 30s – 60s. Thêm độ trễ truyền dữ liệu về máy chủ Sở GTVT/STIS, độ trễ thường rơi vào khoảng **60s – 120s**.
- **MultiGo:** Áp dụng thuật toán dự phóng (dead reckoning) bù trừ độ trễ giữa thời điểm GPS và thời điểm người dùng xem màn hình.
- **Minh chứng trường hợp xe 50E-616.63:**
  - $d_{\text{EBMS}} = 6,417\text{ m}$.
  - MultiGo hiển thị: $5.45\text{ km} = 5,450\text{ m}$.
  - Sai lệch quan sát: $6,417 - 5,450 = 967\text{ m}$.
  - Nếu xe chạy trên đại lộ với vận tốc trung bình $38\text{ km/h} \approx 10.56\text{ m/s}$:
    $$\Delta t = \frac{967}{10.56} \approx 91.5\text{ giây}$$
  - **Kết luận:** Khoảng cách 967m hoàn toàn tương ứng với độ trễ GPS ~90 giây. Khi HCMC Bus Tracker áp dụng công thức $d_{\text{effective}} = d_{\text{EBMS}} - (s \times \Delta t_{\text{age}})$, kết quả sẽ khớp với MultiGo một cách tự nhiên dựa trên cơ sở vật lý mà không cần bất kỳ hệ số offset nhân tạo nào!

---

## 5. CẢI TIẾN THUẬT TOÁN NỘI SUY VỊ TRÍ (INTELLIGENT VEHICLE TRACKING)

1. **Sequential Monotonic Stop Snapping:**
   - Chiếu danh sách trạm dừng của biến thể tuyến lên polyline theo thứ tự tăng dần của chỉ số cự ly tích lũy ($D_{\text{cum}}$).
   - Xác định chính xác cự ly tích lũy của trạm đích $S_{\text{target}}$: $D_{\text{target}}$.
   - Điểm ước lượng của xe sẽ nằm tại cự ly tích lũy:
     $$D_{\text{vehicle}} = D_{\text{target}} - d_{\text{effective}}$$
   - Đảm bảo xe luôn nằm trên đoạn đường đi tới trạm, loại bỏ hoàn toàn hiện tượng nhảy sang nhánh đường đối diện hoặc vòng xoay sai thứ tự.
2. **Làm mượt vị trí (Position Smoothing):**
   - Khi nhận bản tin mới, nếu khoảng cách giữa vị trí cũ và vị trí mới phù hợp với vận tốc xe ($\Delta d \le s \times \Delta t + \epsilon$), thực hiện phép nội suy tuyến tính (LERP) mượt mà.
   - Nếu phát hiện bước nhảy bất thường (ví dụ xe đổi lộ trình hoặc GPS nhảy cóc), cập nhật tức thời vị trí mới đã kiểm thực mà không bay xuyên màn hình.

---

## 6. THIẾT KẾ MARKER VÀ GIAO DIỆN BẢN ĐỒ MỚI

1. **Marker GPS điện thoại:**
   - Biểu tượng **Mũi tên đỏ điều hướng (Red Navigation Arrow)**:
     - Tạo vector icon mũi tên đỏ viền trắng, có đổ bóng nhẹ.
     - Xoay linh hoạt theo giá trị `bearing` lấy từ Android `LocationServices` (`location.bearing`).
     - Khi `bearing == null` hoặc thiết bị đứng yên: hiển thị mũi tên ở hướng mặc định hoặc chấm tròn đỏ viền trắng (neutral state).
     - Lớp vòng tròn mờ bán kính độ chính xác (Accuracy circle) màu đỏ nhạt `#FFCDD2` bao quanh.
2. **Marker Xe buýt:**
   - Biểu tượng xe buýt tròn màu đỏ đô/xanh ngọc, có biểu tượng chiếc xe buýt rõ rệt, kích thước nổi bật khi được chọn.
3. **Thẻ thông tin xe tối ưu:**
   - Bố cục bo tròn (16dp), nền surface phẳng, đổ bóng mềm (elevation 6dp).
   - Dòng 1: Biển số xe (Bold), Vận tốc (km/h), Cự ly đến trạm (m / km), ETA (phút).
   - Dòng 2: Tên trạm sắp đến, thời điểm cập nhật GPS.
   - Thay thế khung cảnh báo màu cam cồng kềnh bằng **Chip/Badge tinh gọn**:
     - `[📍 Vị trí ước lượng]` (Màu cam đất trang nhã).
     - `[🟢 Đang cập nhật]` (Màu xanh lá).
     - `[⏳ Dữ liệu cũ]` (Màu xám/vàng nếu trễ > 60s).

---

## 7. TỐI ƯU HÓA HIỆU NĂNG BẢN ĐỒ VÀ ĐIỂM DỪNG

1. **Bộ đệm không gian (Spatial Containment Cache):**
   - Lưu trữ Bounding Box của lần truy vấn gần nhất được mở rộng thêm 25% lề (margin).
   - Khi người dùng lia bản đồ, nếu toàn bộ viewport mới vẫn nằm trọn trong Bounding Box đã tải, **không gọi truy vấn Room**.
2. **Debounce Camera Events (300ms):**
   - Sử dụng Flow debounce trên `cameraIdle` để tránh trigger truy vấn liên tục khi người dùng đang lướt tay trên màn hình.
3. **Chuyển đổi GeoJSON trên nền (`Dispatchers.Default`):**
   - Không thực hiện serialize GeoJSON trên Main Thread.
   - Cung cấp dữ liệu đã parse hoàn chỉnh cho `GeoJsonSource.setGeoJson()`.
4. **Duy trì Clustering:**
   - Cụm trạm dừng với bán kính 40dp và `clusterMaxZoom = 13`. Chỉ bung trạm chi tiết từ zoom 14 trở lên.

---

## 8. LỘ TRÌNH TRIỂN KHAI VÀ DANH MỤC FILE THAY ĐỔI

- [x] **Bước 0:** Lập tài liệu kế hoạch `docs/PHASE_9_IMPLEMENTATION_PLAN.md`.
- [ ] **Bước 1:** Cập nhật `core-model` & `core-network`:
  - Mở rộng model `ArrivalVehicle` để lưu trữ đầy đủ `gpsTimestampMillis`, các hàm tính `effectiveDistance(currentTimeMillis)`.
- [ ] **Bước 2:** Xây dựng `PositionCompensationEngine`:
  - Triển khai lớp chuyên biệt tính toán bù trừ độ trễ, kiểm tra điều kiện an toàn, vận tốc và trần thời gian.
  - Viết unit tests chuyên biệt cho mọi ca biên (đứng yên, chạy nhanh, dữ liệu cũ, thời gian âm).
- [ ] **Bước 3:** Nâng cấp `VehicleInterpolationEngine`:
  - Bổ sung Sequential Monotonic Snapping dọc theo danh sách trạm và polyline.
  - Hỗ trợ làm mượt (smoothing) và nội suy hướng di chuyển chính xác.
- [ ] **Bước 4:** Cập nhật `MapViewModel` & `MainActivity`:
  - Truyền đầy đủ `timestampIso` và `etaSeconds` qua Navigation route.
  - Tích hợp bộ đệm không gian (Spatial Containment Cache) và Debounce truy vấn trạm.
  - Lắng nghe Location Updates (vị trí + bearing) từ FusedLocationProviderClient.
- [ ] **Bước 5:** Nâng cấp `MapScreen`:
  - Vẽ mũi tên đỏ xoay theo bearing cho GPS điện thoại.
  - Thiết kế lại thẻ thông tin xe với chip trạng thái tinh gọn, bỏ cảnh báo cồng kềnh.
  - Đưa GeoJSON serialization sang background coroutine.
- [ ] **Bước 6:** Soạn thảo các tài liệu báo cáo:
  - `docs/POSITION_DELAY_COMPENSATION.md`
  - `docs/DISTANCE_COMPARISON_REPORT.md`
  - `docs/MAP_PERFORMANCE_REPORT.md`
  - `docs/REAL_DEVICE_TEST_REPORT.md`
  - `docs/PHASE_9_IMPLEMENTATION_REPORT.md`
- [ ] **Bước 7:** Chạy toàn bộ Unit Tests, build Debug APK, build Signed Release APK và verify bằng `apksigner`.
