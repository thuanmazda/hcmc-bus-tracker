# HCMC BUS TRACKER – PHASE 4 DIAGNOSTIC REPORT
*Báo cáo Chẩn đoán Độc lập & Kết quả Khắc phục: Tình trạng Treo Background Tasks & Lỗi Unit Test PredictionViewModel*

- **Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)
- **Phạm vi:** `:feature-prediction:testDebugUnitTest`, Toàn bộ Unit Tests dự án & Đóng gói `assembleDebug`
- **Vai trò:** Senior Software Architect & Lead Diagnostic Engineer
- **Ngày thực hiện:** 2026-11-11
- **Trạng thái:** **PASS WITH WARNINGS** (Khắc phục triệt để lỗi test và treo tiến trình, toàn bộ test passed 100%, APK đóng gói thành công; ghi nhận cảnh báo trường hợp biên GPS timestamp hỏng chờ duyệt cập nhật logic nghiệp vụ).

---

## 1. TỔNG QUAN HIỆN TRẠNG TIẾN TRÌNH & BACKGROUND TASKS

### 1.1. Background Tasks trong Antigravity
- Kiểm tra qua công cụ `manage_task(Action = "list")`:
  - **Kết quả:** `No background tasks are currently running.`
  - Toàn bộ các background tasks trước đó đã kết thúc sạch sẽ sau khi được sửa bẫy vô hạn scheduler. Không còn task nào bị kẹt.

### 1.2. Tiến trình cấp Hệ điều hành (OS Processes)
- Toàn bộ Gradle Daemons và Worker Daemons hoạt động bình thường, không còn tình trạng chạy 100% CPU vô hạn.

---

## 2. NGUYÊN NHÂN GỐC RỄ ĐÃ ĐƯỢC XÁC ĐỊNH & KHẮC PHỤC

### 2.1. Nguyên nhân Treo Tiến trình (Infinite Virtual Clock Loop)
- **Vấn đề:** Trong [`PredictionViewModelTest.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PredictionViewModelTest.kt), bài test `network offline transitions status to OFFLINE` gọi `viewModel.startRealtimePolling()`, kích hoạt 2 coroutine lặp vô hạn `while(isActive) { delay(...) }`. Lệnh `testDispatcher.scheduler.advanceUntilIdle()` tua thời gian ảo vô tận vì hàng đợi delay liên tục được bổ sung task mới.
- **Khắc phục:** Thay `advanceUntilIdle()` bằng `testDispatcher.scheduler.runCurrent()`, chỉ thực thi các coroutine ở thời điểm hiện tại và dừng ngay khi gặp `delay()`. Đồng thời gọi `viewModel.stopRealtimePolling()` ngay trong test và trong `@After tearDown()`.
- **Kết quả:** Tiến trình test kết thúc tức thì trong **0.68s**, giải phóng 100% tải CPU.

### 2.2. Nguyên nhân Lỗi Assertion tại dòng 103 (`expected:<LIVE> but was:<STALE>`)
- **Vấn đề:** Biến `dummyVehicle` bị hardcode timestamp cũ `"2026-11-09T21:13:29+07:00"`. Khi test chạy vào ngày 11/11/2026, gói tin GPS đã cũ gần 48 giờ (> ngưỡng 180s). Phương thức `ArrivalVehicle.isGpsStale()` trả về `true` và ViewModel gán trạng thái `STALE` hoàn toàn chuẩn xác theo nghiệp vụ Phase 4, nhưng bài test lại assert `LIVE`.
- **Khắc phục:** Thay thế timestamp cố định đã cũ bằng `freshTimestampIso` động sử dụng `OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)` để mô phỏng chính xác xe vừa phát GPS tươi mới (< 180s).
- **Kết quả:** Bài test khởi động ban đầu đạt trạng thái `LIVE` chuẩn xác 100%.

---

## 3. FILE ĐÃ SỬA ĐỔI & CHI TIẾT ĐIỀU CHỈNH

Chỉ duy nhất một file thuộc phạm vi cho phép được điều chỉnh:
- **File:** [`feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PredictionViewModelTest.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PredictionViewModelTest.kt)

### Chi tiết các thay đổi:
1. **Timestamp động tươi mới:**
   ```kotlin
   private val freshTimestampIso: String
       get() = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
   ```
2. **Khắc phục bẫy vòng lặp thời gian ảo vô hạn:**
   Sử dụng `testDispatcher.scheduler.runCurrent()` thay vì `advanceUntilIdle()` trong các test kích hoạt polling.
3. **Bổ sung đầy đủ 8 test cases độc lập:**
   - `initial startup loads stop and executes initial prediction poll`: Kiểm thử GPS mới $\rightarrow$ `LIVE`.
   - `stale gps packet transitions status to STALE`: Kiểm thử GPS cũ > 180s (250s) $\rightarrow$ `STALE`.
   - `empty predictions sets status to EMPTY`: Kiểm thử không có xe $\rightarrow$ `EMPTY`.
   - `network offline transitions status to OFFLINE`: Kiểm thử mất mạng $\rightarrow$ `OFFLINE`.
   - `api error sets status to API_ERROR and increments failures`: Kiểm thử lỗi máy chủ $\rightarrow$ `API_ERROR`.
   - `invalid or unparseable gps timestamp handled safely without crash`: Kiểm thử parser GPS xử lý an toàn không sập ứng dụng.
   - `toggleFavorite calls repository toggleFavoriteStop`: Kiểm thử đánh dấu trạm yêu thích.
   - `toggleNextStops fetches and caches next stops`: Kiểm thử mở rộng 3 trạm kế tiếp.

---

## 4. KẾT QUẢ THỰC THI KIỂM THỬ THỰC TẾ (TEST EXECUTION RESULTS)

Đã thực hiện đúng quy trình tuần tự theo chỉ đạo (chỉ chạy 1 lệnh tại một thời điểm):

### 4.1. Chạy riêng `:feature-prediction:testDebugUnitTest`
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :feature-prediction:testDebugUnitTest
```
- **Kết quả:** `BUILD SUCCESSFUL in 1m 47s`
- **Số lượng test:** **8/8 PASSED** (0 failures, 0 errors, 0 skipped, thời gian chạy test suite: 0.68s).

### 4.2. Chạy toàn bộ kiểm thử đơn vị dự án `testDebugUnitTest`
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest
```
- **Kết quả:** `BUILD SUCCESSFUL in 6m 34s` (215 actionable tasks: 44 executed, 171 up-to-date).
- **Trạng thái:** 100% test cases trên toàn bộ các module (`:core-database`, `:core-model`, `:core-network`, `:feature-map`, `:feature-prediction`, `:feature-route`, `:feature-search`, `:app`) đều vượt qua thành công.

### 4.3. Đóng gói hoàn chỉnh APK `assembleDebug`
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug
```
- **Kết quả:** `BUILD SUCCESSFUL in 1m 41s` (290 actionable tasks: 28 executed, 262 up-to-date).
- **File đầu ra:** `app/build/outputs/apk/debug/app-debug.apk` (Dung lượng: 62.7 MB).

---

## 5. CÁC VẤN ĐỀ CÒN TỒN TẠI & BÁO CÁO LOGIC NGHIỆP VỤ (KNOWN LIMITATIONS & BUSINESS LOGIC REPORT)

Theo đúng quy định tại Mục C của chỉ đạo:
> *"Nếu hành vi hiện tại chưa hỗ trợ một trường hợp, báo cáo trước khi sửa logic nghiệp vụ."*

### Hiện trạng phát hiện tại Trường hợp C.6:
- **Biểu hiện:** Khi gói tin GPS có timestamp không hợp lệ (chuỗi rỗng hoặc format lỗi `INVALID_CORRUPT_TIMESTAMP`), phương thức `ArrivalVehicle.getGpsAgeSeconds()` xử lý an toàn qua khối `try-catch` và trả về `-1L` (không gây crash ứng dụng).
- **Vấn đề logic:** Phương thức `ArrivalVehicle.isGpsStale()` hiện được định nghĩa là:
  ```kotlin
  fun isGpsStale(currentMillis: Long = System.currentTimeMillis()): Boolean {
      val age = getGpsAgeSeconds(currentMillis)
      return age > 180L
  }
  ```
  Vì `age = -1L`, biểu thức `-1L > 180L` trả về `false` (coi là xe không bị stale).
  Do đó, `PredictionViewModel` đánh giá tất cả xe không bị stale và gán trạng thái `_status.value = PredictionFeedStatus.LIVE` (báo LIVE sai khi GPS thực chất bị lỗi/hỏng).
- **Hành động tuân thủ:** Tôi đã **KHÔNG tự ý sửa mã nguồn production** trong `core-model/src/main/java/com/hcmcbustracker/core/model/Prediction.kt` nhằm bảo đảm tuyệt đối tính toàn vẹn và tuân thủ quy tắc bảo toàn module ngoài phạm vi.
- **Đề xuất giải pháp sửa tối thiểu (1 dòng):**
  Trong file [`ArrivalVehicle.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/Prediction.kt), sửa:
  ```kotlin
  fun isGpsStale(currentMillis: Long = System.currentTimeMillis()): Boolean {
      val age = getGpsAgeSeconds(currentMillis)
      return age < 0L || age > 180L
  }
  ```
  Khi đó, các gói tin có timestamp không parse được (`age = -1L`) sẽ được coi là stale (`true`), và ViewModel sẽ chuyển sang `PredictionFeedStatus.STALE` thay vì báo `LIVE` sai.

---

## 6. KẾT LUẬN & PHÂN LOẠI NGHIỆM THU

- **Trạng thái:** **PASS WITH WARNINGS**
- **Lý do phân loại:**
  1. Toàn bộ lỗi treo tiến trình Gradle runner do bẫy scheduler vô hạn đã được giải quyết triệt để.
  2. Toàn bộ Unit Tests `:feature-prediction` và toàn bộ các module trong project đạt tỷ lệ pass 100%.
  3. Quá trình biên dịch và đóng gói APK thành công hoàn toàn.
  4. Cảnh báo duy nhất: Trường hợp biên GPS timestamp hỏng đang báo LIVE sai đã được phát hiện, khoanh vùng chính xác và báo cáo giải pháp; chờ chủ dự án phê duyệt áp dụng sửa đổi 1 dòng vào `core-model`.
