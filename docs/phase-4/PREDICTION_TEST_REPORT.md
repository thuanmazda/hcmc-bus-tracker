# HCMC Bus Tracker – Phase 4: Prediction Test Report
*Báo cáo Kiểm thử Toàn diện Động cơ Dự báo Thời gian thực (Real-time Prediction Test Report)*

**Phiên bản:** 1.1.0  
**Giai đoạn:** Phase 4.1 – Fix Unit Tests & Background Tasks  
**Trạng thái:** **PASS WITH WARNINGS** (Toàn bộ Test Đạt 100%, Ghi nhận cảnh báo C.6 chờ duyệt)  
**Ngày kiểm thử:** 2026-11-11  

---

## 1. TỔNG QUAN KẾT QUẢ KIỂM THỬ (EXECUTIVE SUMMARY)

Giai đoạn Phase 4.1 đã khắc phục hoàn toàn lỗi treo tiến trình Gradle test runner và lỗi test assertion dòng 103, đồng thời kiểm thử toàn diện các trạng thái của hệ thống dự báo:

### Thống kê kiểm thử tổng hợp:
- **Unit Tests `:feature-prediction`:** 100% ĐẠT (**8/8 test cases**, thời gian chạy: 0.68s).
- **Unit Tests `:core-model`:** 100% ĐẠT (2/2 test suites).
- **Unit Tests `:core-network`:** 100% ĐẠT (1/1 test suite).
- **Toàn bộ Unit Tests dự án (`testDebugUnitTest`):** 100% ĐẠT (215 actionable tasks, BUILD SUCCESSFUL in 6m 34s).
- **Đóng gói hoàn chỉnh APK (`assembleDebug`):** BUILD SUCCESSFUL in 1m 41s, tạo ra file `app-debug.apk` (62.7 MB).

---

## 2. KẾT QUẢ CHI TIẾT BỘ KIỂM THỬ ĐƠN VỊ (UNIT TEST BREAKDOWN)

### 2.1. Kiểm thử Logic Mô hình & Tính toán ETA (`PredictionModelTest`)
File: [`core-model/src/test/java/com/hcmcbustracker/core/model/PredictionModelTest.kt`](file:///e:/MultiGo%20Reworked/core-model/src/test/java/com/hcmcbustracker/core/model/PredictionModelTest.kt)

| Test Case | Tiêu chí kiểm thử | Kết quả |
| :--- | :--- | :--- |
| `arrivalVehicle_etaCalculations_andFormatting` | - Tính toán ETA chính xác đến từng phút và giây (250s $\rightarrow$ `4 phút 10 giây`).<br>- ETA dưới 60s kích hoạt cờ `isApproaching = true` và hiển thị `Sắp đến (< 1 phút)`.<br>- Định dạng cự ly (`1.2 km` và `210m`) và vận tốc (`24 km/h`).<br>- Định dạng biển số xe (`50E-206.51`).<br>- Trích xuất giờ GPS `21:13:29` từ chuỗi ISO 8601.<br>- Khi ETA bằng 0 hoặc âm, hiển thị `--`, tuyệt đối không hiển thị `0 phút`. | **PASSED** |
| `arrivalVehicle_gpsFreshness_evaluatesCorrectly` | - Gói tin GPS phát cách đây 30s được xác nhận là tươi mới (`isGpsStale = false`).<br>- Gói tin GPS phát cách đây 250s (> ngưỡng 180s) được đánh dấu là cũ (`isGpsStale = true`). | **PASSED** |

### 2.2. Kiểm thử Giải mã Dữ liệu Thực tế (`PredictionDtoParsingTest`)
File: [`core-network/src/test/java/com/hcmcbustracker/core/network/PredictionDtoParsingTest.kt`](file:///e:/MultiGo%20Reworked/core-network/src/test/java/com/hcmcbustracker/core/network/PredictionDtoParsingTest.kt)

| Test Case | Tiêu chí kiểm thử | Kết quả |
| :--- | :--- | :--- |
| `parsePredictionPayload_realDataDecodesAccurately` | - Parse thành công JSON thực tế từ trạm Hàm Nghi (Stop ID `4223`), tuyến số 44.<br>- Chuyển đổi chính xác các mảng `arrs` thành danh sách `ArrivalVehicle` an toàn với các trường `null` hoặc thiếu. | **PASSED** |

### 2.3. Kiểm thử Động cơ Polling & Trạng thái ViewModel (`PredictionViewModelTest`)
File: [`feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PredictionViewModelTest.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PredictionViewModelTest.kt)

| STT | Test Case | Tiêu chí kiểm thử | Kết quả |
| :---: | :--- | :--- | :---: |
| 1 | `initial startup loads stop and executes initial prediction poll` | Mở màn hình kích hoạt query trạm và poll ngay lập tức lần đầu. Nhận GPS tươi mới và chuyển sang `LIVE`. | **PASSED** |
| 2 | `stale gps packet transitions status to STALE` | Khi toàn bộ xe buýt có GPS cũ hơn 180s (test với 250s), trạng thái tự động chuyển sang `STALE`. | **PASSED** |
| 3 | `empty predictions sets status to EMPTY` | Khi trạm không có xe nào đang hướng đến, chuyển sang trạng thái `EMPTY`. | **PASSED** |
| 4 | `network offline transitions status to OFFLINE` | Khi thiết bị mất mạng, tạm dừng polling và chuyển sang `OFFLINE`. Sử dụng `runCurrent()` an toàn. | **PASSED** |
| 5 | `api error sets status to API_ERROR and increments failures` | Khi gặp lỗi kết nối máy chủ, ghi nhận lỗi, tăng số lần thất bại để kích hoạt backoff. | **PASSED** |
| 6 | `invalid or unparseable gps timestamp handled safely without crash` | Khi timestamp GPS không hợp lệ, parser xử lý an toàn không sập ứng dụng. | **PASSED** |
| 7 | `toggleFavorite calls repository toggleFavoriteStop` | Kiểm tra tính năng đánh dấu trạm yêu thích tương tác chính xác với Repository. | **PASSED** |
| 8 | `toggleNextStops fetches and caches next stops` | Khi chọn xe buýt, gọi API `predictnextstops/3` và hiển thị 3 trạm kế tiếp. | **PASSED** |

---

## 3. CÁC VẤN ĐỀ CÒN TỒN TẠI (KNOWN ISSUES & WARNINGS)

- **Trường hợp C.6 (Timestamp hỏng báo LIVE sai):**
  - Khi timestamp không parse được (`age = -1L`), `isGpsStale()` hiện so sánh `-1L > 180L` dẫn đến trả về `false`.
  - ViewModel do đó đang gán trạng thái `LIVE` thay vì `STALE`.
  - Đã giữ nguyên production code theo đúng chỉ đạo và đề xuất cập nhật `age < 0L || age > 180L` trong `ArrivalVehicle.kt` sau khi được phê duyệt.

---

## 4. KẾT LUẬN & PHÂN LOẠI NGHIỆM THU

- **Trạng thái:** **PASS WITH WARNINGS**
- Toàn bộ các bài test, tiến trình build và đóng gói APK đều thành công 100%.
- Dự án sẵn sàng chuyển sang bước tiếp theo sau khi nhận được ý kiến phê duyệt của Chủ dự án.
