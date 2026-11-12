# BIÊN BẢN KIỂM THỬ VÀ QUY TRÌNH NGHIỆM THU TRÊN THIẾT BỊ ANDROID THỰC TẾ

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Giai đoạn:** Phase 9 – Intelligent Vehicle Tracking & Map UX  
**Ngày cập nhật:** 2026-11-12  

> [!IMPORTANT]
> **Nguyên tắc nghiệm thu trung thực:** Báo cáo này phân biệt rõ các kịch bản đã được kiểm chứng tự động bằng bộ Unit Test (JVM/Robolectric trên dữ liệu EBMS thật) và **bảng kịch bản nghiệm thu thực địa trên thiết bị Android thật** (vì giả lập Android Emulator không phản ánh chính xác cảm biến la bàn/tốc độ di chuyển thực tế và độ trễ mạng di động 4G/5G).

---

## 1. KẾT QUẢ KIỂM THỬ TỰ ĐỘNG (AUTOMATED TEST VERIFICATION)

Toàn bộ các lớp thuật toán và quản lý trạng thái đã được kiểm chứng tự động trước khi đóng gói APK:

| Hạng mục kiểm thử | Module & File Test | Số test cases | Kết quả |
| :--- | :--- | :---: | :---: |
| **Chuyển đổi Timestamp & Timezone (`dts`)** | `:core-model` / `PositionCompensationEngineTest.kt` | 4 | **PASS 100%** |
| **Tính tuổi GPS & Bù trừ độ trễ (0s, 15s, 30s, 60s, 120s)** | `:core-model` / `PositionCompensationEngineTest.kt` | 5 | **PASS 100%** |
| **Điều kiện biên (Ngưng >120s, Đứng yên, Sai tốc độ, Đích âm, Khác chiều)** | `:core-model` / `PositionCompensationEngineTest.kt` | 6 | **PASS 100%** |
| **Đối chiếu ca thực tế biển số `50E-616.63` (6,417m $\rightarrow$ 5.45km)** | `:core-model` / `PositionCompensationEngineTest.kt` | 1 | **PASS 100%** |
| **Sequential Monotonic Stop Snapping (Xử lý vòng lặp Polyline)** | `:feature-map` / `VehicleInterpolationEngineTest.kt` | 1 | **PASS 100%** |
| **Nội suy Polyline & Làm mượt giới hạn vận tốc (Velocity Smoothing)** | `:feature-map` / `VehicleInterpolationEngineTest.kt` | 3 | **PASS 100%** |
| **Quản lý `TrackedVehicleState`, `userBearing`, `accuracy` trong ViewModel** | `:feature-map` / `MapViewModelTest.kt` | 11 | **PASS 100%** |
| **Parse gói tin thật từ API EBMS (`PredictionDto`) & Network** | `:core-network` / `PredictionDtoParsingTest.kt` | 12 | **PASS 100%** |
| **Toàn bộ Test Suite dự án (`testDebugUnitTest`)** | Tất cả 8 modules | **215 tasks** | **BUILD SUCCESSFUL** |

---

## 2. QUY TRÌNH KIỂM THỬ THỰC TẾ TRÊN THIẾT BỊ ANDROID THẬT (FIELD TEST CHECKLIST)

Sau khi cài đặt tệp Signed Release APK (`app-release.apk`) lên điện thoại Android thực tế (Android 8.0+ / API 26+), thực hiện nghiệm thu đầy đủ 9 hạng mục ngoài hiện trường:

### Checklist 1: GPS Điện thoại & Mũi tên định hướng màu đỏ
- [ ] **Tắt/Bật GPS điện thoại:** Bấm nút "Vị trí của tôi" $\rightarrow$ Hộp thoại xin quyền `ACCESS_FINE_LOCATION` xuất hiện. Khi cấp quyền, mũi tên/chấm đỏ xuất hiện đúng tọa độ thực tế kèm vòng tròn độ chính xác `#FFCDD2`. Khi từ chối hoặc tắt GPS hệ thống, không crash app.
- [ ] **Đứng yên tại chỗ:** Khi không có vận tốc di chuyển (`speed <= 0.3 m/s` hoặc `hasBearing == false`), icon GPS hiển thị trạng thái trung tính (Puck tròn đỏ chấm trắng giữa), **không** tự giả định hướng Bắc hay xoay lung tung.
- [ ] **Di chuyển ngoài thực tế (đi bộ/ngồi trên xe):** Khi tốc độ $> 0.3\text{ m/s}$ và có góc phương vị (`bearing`), icon chuyển thành **Mũi tên đỏ** và xoay mượt theo đúng hướng di chuyển trên bản đồ (`ICON_ROTATION_ALIGNMENT_MAP`).

### Checklist 2: Hiệu năng bản đồ & Điểm dừng
- [ ] **Mở bản đồ lần đầu:** Bản đồ nền OSM/FPT STIS tải nhanh; trong vòng ~100ms các trạm trong khung nhìn xuất hiện mà không khựng khung hình.
- [ ] **Zoom In / Zoom Out:**
  - Tại zoom $< 13.5$: Hiện gợi ý phóng to bản đồ, dừng truy vấn thừa.
  - Tại zoom $13.5 \rightarrow 14.0$: Các điểm dừng gom cụm (cluster) sạch sẽ.
  - Tại zoom $> 14.0$: Bung chi tiết các trạm (xanh dương cho trạm thường, xanh lục cho bến đầu/cuối).
- [ ] **Vuốt bản đồ liên tục:** Nhờ Spatial Containment Cache (mở rộng 25%) và Debounce 250ms, vuốt trong phạm vi 500m xung quanh không bị giật hay chớp tắt marker.

### Checklist 3: Tracking Xe Buýt & Bù trừ độ trễ
- [ ] **Chọn trạm $\rightarrow$ Dự báo:** Xem chi tiết trạm (ví dụ Bến Thành / Hàm Nghi), danh sách xe kèm cự ly và ETA hiển thị đầy đủ từ EBMS.
- [ ] **Chọn "Xem vị trí xe trên bản đồ":**
  - Truyền đủ `dts` và `eta` sang màn hình bản đồ.
  - Bản đồ vẽ lộ trình, zoom tới khu vực xe, ghim marker xe buýt **màu xanh ngọc (`#00897B`)** phân biệt rõ rệt với **mũi tên đỏ** của người dùng.
  - Thẻ thông tin cạnh dưới bo góc đẹp, hiển thị đủ: Biển số, tên trạm đích, chip trạng thái ngắn gọn (`🟢 Đang cập nhật`), và 3 ô thông số: **Cự ly (kèm mức bù trễ)**, **ETA (kèm tuổi GPS)**, **Vận tốc**.
  - Không còn khung cảnh báo cồng kềnh choán màn hình.
- [ ] **Chuyển chiều tuyến (`Lượt đi` $\leftrightarrow$ `Lượt về`):** Bấm nút đảo chiều ở thanh thông tin trên cùng $\rightarrow$ Polyline đổi màu (Xanh ngọc $\leftrightarrow$ Cam đất) và cập nhật thứ tự trạm tương ứng.

### Checklist 4: Mạng & Vòng đời (Lifecycle & Network Recovery)
- [ ] **Mất mạng & Khôi phục mạng:** Bật chế độ máy bay khi đang mở màn hình Dự báo $\rightarrow$ Trạng thái chuyển sang `OFFLINE`. Tắt chế độ máy bay $\rightarrow$ `NetworkMonitor` phát hiện có mạng trở lại và tự động làm mới dữ liệu ngay lập tức.
- [ ] **Xoay màn hình & Thoát/Vào lại App:** Vị trí camera được bảo toàn (`savedCameraPosition`), Location updates được dọn dẹp sạch sẽ khi `onDispose` để không hao pin ngầm.
