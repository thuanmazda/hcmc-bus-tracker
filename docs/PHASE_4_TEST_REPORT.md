# HCMC BUS TRACKER – PHASE 4: TEST REPORT
*Báo cáo Kết quả Kiểm thử Toàn diện Sau Khắc phục Unit Test Phase 4.1*

- **Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)
- **Phiên bản:** 1.0.0
- **Giai đoạn:** Phase 4.1 – Fix Unit Tests & Background Tasks
- **Ngày kiểm thử:** 2026-11-11
- **Môi trường:** OpenJDK 21.0.10 (JetBrains JBR), Gradle Wrapper 8.10.2, Android Gradle Plugin 8.8.0
- **Trạng thái:** **PASS WITH WARNINGS**

---

## 1. TỔNG QUAN KẾT QUẢ THỰC THI (EXECUTIVE SUMMARY)

Tất cả các kiểm thử đơn vị và quá trình đóng gói ứng dụng đã được thực thi tuần tự theo đúng quy trình D và kết thúc thành công:

| Hạng mục kiểm thử | Lệnh thực thi | Kết quả thực tế | Thời gian | Tỷ lệ Đạt |
| :--- | :--- | :--- | :--- | :--- |
| **Prediction Unit Tests** | `.\gradlew.bat :feature-prediction:testDebugUnitTest` | `BUILD SUCCESSFUL` | 1m 47s (test suite: 0.68s) | **100% (8/8 tests)** |
| **All Modules Unit Tests** | `.\gradlew.bat testDebugUnitTest` | `BUILD SUCCESSFUL` | 6m 34s (215 tasks) | **100% PASS** |
| **Debug APK Assemble** | `.\gradlew.bat assembleDebug` | `BUILD SUCCESSFUL` | 1m 41s (290 tasks) | **APK Tạo thành công (62.7MB)** |

---

## 2. CHI TIẾT BỘ TEST CASE `:feature-prediction:testDebugUnitTest`

File kiểm thử: [`feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PredictionViewModelTest.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PredictionViewModelTest.kt)

| STT | Test Case | Mục đích kiểm thử | Kết quả | Thời gian |
| :---: | :--- | :--- | :---: | :---: |
| 1 | `initial startup loads stop and executes initial prediction poll` | Mở màn hình nạp dữ liệu trạm, gửi query ngay lần đầu, nhận gói tin GPS mới và chuyển sang trạng thái `PredictionFeedStatus.LIVE`. | **PASSED** | 0.105s |
| 2 | `stale gps packet transitions status to STALE` | Khi xe buýt có GPS cũ hơn 180 giây (thử nghiệm với 250s), hệ thống tự động nhận diện và chuyển trạng thái sang `PredictionFeedStatus.STALE`. | **PASSED** | 0.044s |
| 3 | `empty predictions sets status to EMPTY` | Khi máy chủ trả về danh sách xe rỗng (không có xe đang chạy), hệ thống chuyển sang `PredictionFeedStatus.EMPTY`. | **PASSED** | 0.054s |
| 4 | `network offline transitions status to OFFLINE` | Khi thiết bị mất mạng, hệ thống phát hiện qua `NetworkMonitor` và chuyển trạng thái sang `PredictionFeedStatus.OFFLINE`. Đã sửa bẫy vô hạn scheduler. | **PASSED** | 0.061s |
| 5 | `api error sets status to API_ERROR and increments failures` | Khi API gặp lỗi kết nối/timeout, hệ thống ghi nhận lỗi, tăng số lần lỗi liên tiếp phục vụ cơ chế exponential backoff. | **PASSED** | 0.048s |
| 6 | `invalid or unparseable gps timestamp handled safely without crash` | Khi gói tin GPS có timestamp hỏng hoặc không parse được, parser xử lý an toàn không gây sập ứng dụng. | **PASSED** | 0.256s |
| 7 | `toggleFavorite calls repository toggleFavoriteStop` | Kiểm tra tính năng thêm/xóa trạm yêu thích tương tác chính xác với Repository. | **PASSED** | 0.030s |
| 8 | `toggleNextStops fetches and caches next stops` | Khi chọn xe buýt, gọi API chính thức `predictnextstops/3` và hiển thị 3 trạm kế tiếp. | **PASSED** | 0.077s |

---

## 3. FILE ĐÃ SỬA & NGUYÊN NHÂN ĐIỀU CHỈNH

Chỉ duy nhất một file thuộc phạm vi chỉ đạo được chỉnh sửa:
- **File:** `feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PredictionViewModelTest.kt`
- **Lý do sửa:**
  1. **Khắc phục lỗi timestamp cũ:** Thay thế hardcode timestamp `2026-11-09` bằng timestamp động `freshTimestampIso` từ `OffsetDateTime.now()` để đảm bảo tuổi gói tin GPS $< 180$s khi kiểm thử trạng thái `LIVE`.
  2. **Khắc phục bẫy vòng lặp thời gian ảo vô hạn:** Trong bài test `network offline transitions status to OFFLINE`, chuyển từ `advanceUntilIdle()` sang `testDispatcher.scheduler.runCurrent()`, đồng thời bổ sung `viewModel.stopRealtimePolling()` để hủy coroutine sau khi kiểm thử.
  3. **Bổ sung các test trạng thái GPS:** Thêm test độc lập cho trạng thái `STALE` và test xử lý an toàn timestamp không hợp lệ.

---

## 4. CÁC VẤN ĐỀ CÒN TỒN TẠI (KNOWN ISSUES & WARNINGS)

### Vấn đề Trường hợp C.6 (Báo LIVE sai khi timestamp GPS không parse được)
- **Hiện trạng:**
  - Trong `ArrivalVehicle.kt`, `getGpsAgeSeconds()` có try-catch an toàn nên không gây crash khi gặp timestamp hỏng (trả về `-1L`).
  - Tuy nhiên, `isGpsStale()` hiện chỉ kiểm tra `age > 180L`. Với `age = -1L`, biểu thức `-1L > 180L` trả về `false`, khiến ViewModel hiểu nhầm xe vẫn có GPS tươi mới và báo `PredictionFeedStatus.LIVE`.
- **Tuân thủ quy tắc:**
  Theo chỉ đạo: *"Nếu hành vi hiện tại chưa hỗ trợ một trường hợp, báo cáo trước khi sửa logic nghiệp vụ"*, tôi giữ nguyên mã nguồn production và báo cáo hiện trạng.
- **Giải pháp đề xuất:**
  Cập nhật dòng 160 trong [`core-model/.../Prediction.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/Prediction.kt):
  ```kotlin
  fun isGpsStale(currentMillis: Long = System.currentTimeMillis()): Boolean {
      val age = getGpsAgeSeconds(currentMillis)
      return age < 0L || age > 180L
  }
  ```

---

## 5. KẾT LUẬN & ĐÁNH GIÁ NGHIỆM THU

- **Trạng thái:** **PASS WITH WARNINGS**
- **Đánh giá tổng thể:**
  - Lỗi treo Background Tasks và lỗi test failure dòng 103 đã được giải quyết triệt để 100%.
  - Toàn bộ 8/8 test cases của `:feature-prediction` đều PASSED.
  - Toàn bộ Unit Tests dự án (`testDebugUnitTest`) đạt 100% PASSED.
  - Đóng gói ứng dụng `assembleDebug` tạo ra file APK hoàn chỉnh thành công.
  - Đang chờ Chủ dự án phê duyệt áp dụng cập nhật 1 dòng cho `ArrivalVehicle.kt` để giải quyết dứt điểm cảnh báo C.6.
