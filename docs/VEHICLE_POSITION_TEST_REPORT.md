# HCMC BUS TRACKER – BÁO CÁO KIỂM THỬ VỊ TRÍ XE DỰ BÁO TRÊN BẢN ĐỒ (PHASE 8)
## ESTIMATED VEHICLE POSITION ON MAP VERIFICATION REPORT

- **Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)
- **Giai đoạn:** Phase 8 – Map, GPS, Bus Stops & Prediction Experience Improvement
- **Module kiểm thử:** `feature-prediction`, `feature-map`
- **Môi trường:** Android SDK 34 / Robolectric & Gradle Unit Tests
- **Thời gian thực hiện:** 2026-11-11
- **Kết quả tổng quan:** **100% PASS (6/6 tiêu chí nghiệm thu)**

---

## 1. MỤC TIÊU VÀ PHẠM VI KIỂM THỬ

1. **Thuật toán Nội suy vị trí xe (Reverse Polyline Linear Referencing):**
   - Sử dụng `VehicleInterpolationEngine` để tính toán tọa độ `(lat, lng)` và hướng xoay `bearing` của xe buýt dựa trên cự ly $d$ mét từ trạm dừng đi ngược về phía đầu tuyến trên tập tọa độ thực tế của polyline (`points`).
2. **Luồng người dùng từ Màn hình Dự báo sang Bản đồ:**
   - Tại mỗi thẻ thông tin xe trong `StopPredictionScreen` (`VehicleRow`), cung cấp nút hành động "Xem vị trí xe trên bản đồ".
   - Truyền đầy đủ tham số `routeId`, `varId`, `stopId`, `plate`, `distance`, `speed` qua Jetpack Navigation Compose.
3. **Hiển thị trên Bản đồ số:**
   - Vẽ marker xe buýt (`VEHICLE_SOURCE_ID`, `VEHICLE_LAYER_ID`) nổi bật bằng vòng tròn màu đỏ (`#D32F2F`) với viền trắng $3.0\text{px}$.
   - Tự động di chuyển camera đến vị trí xe (zoom 15.5).
   - Thẻ thông tin nổi ở góc dưới màn hình hiển thị: Biển số xe, vận tốc (km/h), cự ly đến trạm (m), và nhãn minh bạch:
     `⚠️ Vị trí ước lượng từ dữ liệu cự ly EBMS`.
   - Nút đóng theo dõi xóa marker xe và trả bản đồ về trạng thái ban đầu.

---

## 2. KẾT QUẢ KIỂM THỬ CHI TIẾT

| Mã test case | Nội dung kiểm thử | Dữ liệu đầu vào / Điều kiện | Kết quả thực tế | Trạng thái |
| :--- | :--- | :--- | :--- | :---: |
| **TC-VEH-01** | Nội suy tọa độ khi xe cách trạm trong phân đoạn polyline | Polyline 3 điểm, cự ly xe cách trạm $300\text{m}$ | `VehicleInterpolationEngine.interpolatePosition` trả về tọa độ chính xác nằm trên phân đoạn, bearing $45.0^\circ$ | **PASS** |
| **TC-VEH-02** | Nội suy tọa độ khi cự ly vượt qua điểm đầu tuyến (fallback) | Cự ly xe $50,000\text{m}$ (lớn hơn toàn tuyến) | Fallback an toàn đặt xe tại điểm xuất phát đầu tuyến, không ném ngoại lệ | **PASS** |
| **TC-VEH-03** | Tạo GeoJSON Point Feature cho xe buýt | Tọa độ `(10.765, 106.685)`, Biển số `51B-12345`, $32.5\text{ km/h}$ | Tạo Feature với đầy đủ metadata: `plate`, `bearing`, `speed` | **PASS** |
| **TC-VEH-04** | Nhấp "Xem vị trí xe trên bản đồ" tại `StopPredictionScreen` | Bấm nút trên thẻ xe `51B-062.45` | Gọi `onViewVehicleOnMap`, điều hướng chính xác đến `interactive_map?routeId=...` | **PASS** |
| **TC-VEH-05** | Quản lý trạng thái `trackedVehicle` trong `MapViewModel` | Gọi `trackVehicle(1, 101, 142, "51B-12345", 300.0, 35.0)` | `uiState.trackedVehicle` cập nhật đầy đủ; gọi `clearTrackedVehicle()` trả về `null` | **PASS** |
| **TC-VEH-06** | Hiển thị thẻ thông tin xe ước lượng và nhãn cảnh báo | Mở bản đồ có `trackedVehicle` | Hiển thị thẻ nổi phía dưới với đầy đủ thông số và nhãn minh bạch cự ly | **PASS** |

---

## 3. ĐỐI CHIẾU NGUYÊN TẮC BẢO MẬT & DỮ LIỆU

1. **Tuyệt đối không dùng Mock / Fake Coordinates:**
   - Tọa độ xe được tính trực tiếp từ polyline chính thống của EBMS lưu trong Room (`route_paths.pointsJson`).
   - Cự ly và vận tốc lấy trực tiếp từ response `PredictionDto.d` và `PredictionDto.s`.
2. **Minh bạch UX:**
   - Nhãn `⚠️ Vị trí ước lượng từ dữ liệu cự ly EBMS` đảm bảo người dùng hiểu rõ đây là vị trí mô phỏng theo mét của máy chủ trung tâm, không nhầm lẫn với GPS vệ tinh trực tiếp.

---

## 4. KẾT LUẬN

Tính năng hiển thị vị trí xe dự báo trên bản đồ đã hoàn thành 100%, tích hợp liền mạch giữa hai module `feature-prediction` và `feature-map`.
