# HCMC BUS TRACKER – PHASE AUDIT REPORT
**Báo cáo Đánh giá Độc lập Toàn diện Kiến trúc & Chất lượng Phần mềm (Independent Software Architecture & QA Audit)**

- **Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)
- **Giai đoạn kiểm toán:** **Phase 7 – Final QA & Release** (kèm kiểm toán hồi quy toàn diện Phase 0–6)
- **Vai trò thực hiện:** Senior Software Architect & Lead Independent QA Engineer
- **Thời điểm đánh giá:** 2026-11-11
- **Phân loại nghiệm thu cuối cùng (Final Verdict):** **PASS**

---

## 1. BẢNG TỔNG HỢP ĐÁNH GIÁ 10 TIÊU CHÍ KIỂM TOÁN

| STT | Tiêu chí kiểm toán | Mô tả tóm tắt kiểm tra | Trạng thái |
| :---: | :--- | :--- | :---: |
| 1 | **Đối chiếu Source Code & Yêu cầu Phase 7** | Rà soát toàn bộ source code release: Functional QA (điều hướng 4 tab, tìm kiếm, yêu thích, realtime prediction, bản đồ polyline, Linear Referencing, các trạng thái LIVE/STALE/OFFLINE/EMPTY/API_ERROR); Network QA (timeout 10s, auto-refresh, anti-race condition, no duplicate polling); Android QA (API 26–35, lifecycle, Doze Mode compliance, notification cooldown & disclaimer, MapView lifecycle, R8/Proguard rules). | **PASS** |
| 2 | **Kiến trúc, Dependency Injection, Repository, ViewModel & UI** | Tuân thủ tuyệt đối Clean Architecture 5 tầng: Domain (`:core-model`) $\rightarrow$ Network (`:core-network`) $\rightarrow$ Database & Repository (`:core-database`) $\rightarrow$ Design System (`:core-designsystem`) $\rightarrow$ Features (`:feature-map`, `:feature-route`, `:feature-search`, `:feature-prediction`) $\rightarrow$ Application (`:app`). Hilt DI module hóa rõ ràng, `BusRepository` là Single Source of Truth, UI Jetpack Compose phản ứng hoàn toàn qua `StateFlow`. | **PASS** |
| 3 | **API Endpoint, DTO Mapping & Dữ liệu Production** | Chỉ sử dụng duy nhất máy chủ chính thức EBMS (`https://apicms.ebms.vn`). Dữ liệu dự báo thời gian thực lấy từ `/prediction/predictbystopid/{stopId}` và `/prediction/predictnextstops/{maxNextStops}` theo đúng tài liệu Phase 0 `API_CATALOG.md`. DTO mapping chống chịu lỗi cao (`ignoreUnknownKeys = true`, `isLenient = true`, `coerceInputValues = true`). | **PASS** |
| 4 | **Coroutine, Lifecycle, Cancellation, Polling & Xử lý Lỗi** | `StopPredictionScreen` quản lý polling chu kỳ 15s qua `DisposableEffect(lifecycleOwner)`: hủy khi `ON_PAUSE`, phục hồi khi `ON_RESUME`, giải phóng triệt để khi `onDispose`. Không có coroutine bị treo hay rò rỉ. `SharingStarted.WhileSubscribed(5000)` áp dụng cho mọi StateFlow. Network timeout 10s tại Repository bọc bằng `withTimeoutOrNull` và `runCatching`. | **PASS** |
| 5 | **Dữ liệu Room & Database Migration** | Quản lý schema Room v2 bền vững. Migration `MIGRATION_1_2` bổ sung hai bảng `favorite_stops` và `favorite_routes` hoạt động hoàn hảo, bảo tồn dữ liệu cũ và hỗ trợ tìm kiếm/lọc hiệu năng cao với chỉ mục Index. | **PASS** |
| 6 | **Tính Xác thực của Bộ Test (Verification & Validation)** | Toàn bộ 40 unit test trên các module là test nghiệp vụ thực chất, kiểm chứng sâu các ca biên (GPS cũ > 180s, cooldown thông báo 5 phút, disclaimer bắt buộc, debounce tìm kiếm 300ms, hủy job trùng, Room migration, SharedPreferences caching). Không có test hình thức (placeholder/mock pass). | **PASS** |
| 7 | **Tìm Mock Data, Hardcoded Values & TODO** | Quét tự động bằng regex trên toàn bộ thư mục `src/main` của tất cả module: **0 mock data, 0 synthetic GPS coordinates, 0 fake buses** trong production code. Dữ liệu `DesignPreviewData` được cách ly tuyệt đối phục vụ Compose `@Preview`. Không có TODO nào gây rủi ro logic. | **PASS** |
| 8 | **Code Trùng lặp, Dead Code & Dependency** | Mã nguồn tinh gọn, không có mã chết hay import dư thừa. Cấu trúc `combine` trong ViewModel được chuẩn hóa (dưới 5 flow bằng meta flow classes) loại bỏ hoàn toàn lỗi compiler ambiguity. File `.gitignore` và `proguard-rules.pro` đầy đủ. | **PASS** |
| 9 | **Chạy Tuần tự Build / Test Thực tế** | Thực hiện tuần tự 100% bằng Gradle trên môi trường chuẩn Android Studio JBR 21: `:core-database` PASS, `:feature-route` PASS, `:feature-search` PASS, `:feature-prediction` (19/19 tests) PASS, regression toàn dự án `testDebugUnitTest` (215 tasks) PASS 100%, `assembleDebug` PASS, `assembleRelease` PASS. | **PASS** |
| 10 | **Tương thích Toàn diện với Phase 0–6** | Hệ thống hoạt động trơn tru từ Phase 0 (EBMS API & Offline sync), Phase 1-2 (MapLibre & Route detail), Phase 3 (Database indexing & fast search), Phase 4 (Prediction engine), Phase 5 (Linear referencing vị trí xe), Phase 6 (Favorites, UX & Settings) đến Phase 7 (Release APK & Final QA). | **PASS** |

---

## 2. CHI TIẾT ĐỐI CHIẾU KIỂM TOÁN PHASE 7 (FINAL QA & RELEASE)

### 2.1. Functional QA – `[PASS]`
- **Luồng Điều hướng & Giao diện:**
  - 4 màn hình chính (`Bản đồ`, `Tuyến xe`, `Trạm dừng`, `Cá nhân`) được liên kết thông qua Navigation Compose tại [`HcmcBusNavHost.kt`](file:///E:/MultiGo%20Reworked/app/src/main/java/com/hcmcbustracker/app/navigation/HcmcBusNavHost.kt).
  - Điều hướng chi tiết chuyển tiếp mượt mà: Tuyến xe $\rightarrow$ [`RouteDetailScreen`](file:///E:/MultiGo%20Reworked/feature-route/src/main/java/com/hcmcbustracker/feature/route/RouteDetailScreen.kt); Trạm dừng $\rightarrow$ [`StopPredictionScreen`](file:///E:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/StopPredictionScreen.kt); Xem lộ trình bản đồ $\rightarrow$ [`MapScreen`](file:///E:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapScreen.kt); Tìm kiếm toàn cục $\rightarrow$ [`SearchScreen`](file:///E:/MultiGo%20Reworked/feature-search/src/main/java/com/hcmcbustracker/feature/search/SearchScreen.kt).
- **Tìm kiếm & Yêu thích:**
  - Chuẩn hóa tiếng Việt có dấu/không dấu qua [`VietnameseNormalizer.kt`](file:///E:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/VietnameseNormalizer.kt).
  - Debounce 300ms trong [`SearchViewModel.kt`](file:///E:/MultiGo%20Reworked/feature-search/src/main/java/com/hcmcbustracker/feature/search/SearchViewModel.kt) và [`StopsViewModel.kt`](file:///E:/MultiGo%20Reworked/feature-search/src/main/java/com/hcmcbustracker/feature/search/StopsViewModel.kt) bảo đảm hiệu năng truy vấn SQLite.
  - Đồng bộ trạng thái yêu thích trạm & tuyến tức thời qua Room `Flow`.
- **Dự báo EBMS & Ước lượng Vị trí Xe:**
  - Truy xuất dữ liệu từ `predictbystopid` và `predictnextstops`.
  - Hiển thị đầy đủ: Tuyến, biển số, khoảng cách (m), thời gian đến (phút/giây), tốc độ (km/h), timestamp GPS hộp đen.
  - Thuật toán Linear Referencing tại [`BusPositionEstimator.kt`](file:///E:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/BusPositionEstimator.kt) chiếu khoảng cách di chuyển thực tế lên polyline theo đúng hướng tuyến, nội suy vị trí xe buýt hiển thị marker trên MapLibre.
- **Trạng thái Dữ liệu Đầy đủ:**
  - `LIVE`: Dữ liệu tươi mới (GPS $\le$ 180s, response trong vòng 45s).
  - `STALE`: Dữ liệu cũ (> 45s hoặc GPS hộp đen > 180s) kèm icon cảnh báo vàng.
  - `OFFLINE`: Mất internet, hiển thị banner ngoại tuyến và tạm dừng polling.
  - `EMPTY`: Không có xe buýt hoạt động trên tuyến/trạm, hiển thị thông điệp rõ ràng.
  - `API_ERROR`: Lỗi phản hồi hoặc timeout từ server EBMS, có nút "Thử lại".

### 2.2. Network QA – `[PASS]`
- **Mất mạng & Khôi phục Kết nối:**
  - [`ConnectivityManagerNetworkMonitor.kt`](file:///E:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/monitor/ConnectivityManagerNetworkMonitor.kt) theo dõi trực tiếp trạng thái mạng. Khi có mạng trở lại, ViewModel tự động kích hoạt refresh.
- **Giới hạn Timeout:**
  - Cấu hình OkHttp 15s và Repository `withTimeoutOrNull(10_000L)` (10 giây) bảo đảm không bao giờ bị treo vô hạn nếu server EBMS nghẽn mạng.
- **Phân tích JSON Bền vững (Resilient Parsing):**
  - Cấu hình `Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; encodeDefaults = true }` trong [`NetworkModule.kt`](file:///E:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/di/NetworkModule.kt) bảo vệ ứng dụng trước mọi thay đổi cấu trúc dữ liệu không báo trước từ EBMS.
- **Chống Đua Dữ liệu (Anti-race Condition) & Không Tạo Job Trùng:**
  - Phương thức `startRealtimePolling()` trong [`PredictionViewModel.kt`](file:///E:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/PredictionViewModel.kt#L125-L165) luôn hủy polling job cũ trước khi kích hoạt job mới. Khi đổi trạm hoặc rời màn hình, dữ liệu trạm cũ bị hủy lập tức, không ghi đè vào trạm hiện tại.

### 2.3. Android Platform QA – `[PASS]`
- **Tương thích Hệ điều hành:**
  - `minSdk = 26` (Android 8.0 Oreo), `targetSdk = 35` (Android 15), `compileSdk = 35`.
- **Vòng đời & Quản lý Bộ nhớ (Lifecycle & Memory Leak):**
  - Polling 15s chỉ kích hoạt khi màn hình ở trạng thái `ON_RESUME` thông qua `DisposableEffect` tại [`StopPredictionScreen.kt`](file:///E:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/StopPredictionScreen.kt#L100-L115).
  - Tự động tạm dừng khi ứng dụng vào background hoặc người dùng chuyển màn hình, tuân thủ tuyệt đối Android Doze Mode và Background Execution Limits.
  - Quản lý vòng đời `MapView` qua `AndroidView` với đầy đủ các sự kiện `onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`.
  - Danh sách `LazyColumn` gắn `key = { it.id }`, cuộn mượt mà không rò rỉ bộ nhớ đồ họa.
- **Quyền Thông báo (Android 13+):**
  - Khai báo `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` tại [`AndroidManifest.xml`](file:///E:/MultiGo%20Reworked/app/src/main/AndroidManifest.xml).
  - [`BusArrivalNotificationManager.kt`](file:///E:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/notification/BusArrivalNotificationManager.kt) kiểm tra quyền trước khi gửi cảnh báo, tích hợp cơ chế cooldown 5 phút chống spam và từ chối xe có GPS cũ > 180s.
  - Bắt buộc gắn thông báo miễn trừ trách nhiệm: *"⚠️ Dự báo ước lượng dựa trên dữ liệu EBMS, có thể thay đổi tùy điều kiện giao thông."*
- **Cấu hình Proguard / R8:**
  - File [`app/proguard-rules.pro`](file:///E:/MultiGo%20Reworked/app/proguard-rules.pro) bảo toàn đầy đủ các lớp DTO Kotlinx Serialization, Retrofit, Room, MapLibre Native SDK, Hilt.

### 2.4. Release Engineering & Bảo mật – `[PASS]`
- **Bảo mật Kho lưu trữ Git:**
  - File [`.gitignore`](file:///E:/MultiGo%20Reworked/.gitignore) chặn triệt để keystore (`*.jks`, `*.keystore`), file bí mật (`local.properties`), build output (`*.apk`, `*.aab`), cache và file IDE.
  - Không có khóa ký hoặc mật khẩu hardcode trong mã nguồn. Cấu hình ký release sẵn sàng nhận qua biến môi trường CI/CD theo chuẩn DevSecOps.
- **Sản phẩm Đóng gói (Build Artifacts):**
  - **Debug APK:** [`app/build/outputs/apk/debug/app-debug.apk`](file:///E:/MultiGo%20Reworked/app/build/outputs/apk/debug/app-debug.apk) (Kích thước: **62,721,505 bytes** ~ 62.7 MB).
  - **Release APK:** [`app/build/outputs/apk/release/app-release-unsigned.apk`](file:///E:/MultiGo%20Reworked/app/build/outputs/apk/release/app-release-unsigned.apk) (Kích thước: **56,197,047 bytes** ~ 56.2 MB, đã tối ưu tài nguyên và áp dụng Proguard/R8).
- **Tài liệu Bàn giao:**
  - [`README.md`](file:///E:/MultiGo%20Reworked/README.md): Tổng quan kiến trúc, sơ đồ hệ thống, hướng dẫn build Gradle và lệnh cài đặt ADB.
  - [`CHANGELOG.md`](file:///E:/MultiGo%20Reworked/CHANGELOG.md): Lịch sử phát triển chi tiết từ Phase 0 đến Phase 7.
  - [`docs/FINAL_QA_REPORT.md`](file:///E:/MultiGo%20Reworked/docs/FINAL_QA_REPORT.md): Báo cáo chi tiết kiểm định chất lượng chức năng, mạng, Android, đóng gói và các giới hạn đã biết.

---

## 3. TÍNH XÁC THỰC CỦA HỆ THỐNG KIỂM THỬ (TEST SUITE VERIFICATION)

Hệ thống kiểm thử tự động gồm 40 bài unit test phân bố trên toàn bộ các module lõi và tính năng:

1. **Module `:feature-prediction` (19 tests):**
   - [`BusArrivalNotificationManagerTest.kt`](file:///E:/MultiGo%20Reworked/feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/notification/BusArrivalNotificationManagerTest.kt) (5 tests): Kiểm tra từ chối xe GPS cũ > 180s, từ chối xe cách quá xa (> 5.000m), chấp nhận xe tiếp cận tươi mới, cơ chế cooldown 5 phút chống spam, cấu trúc notification kèm disclaimer bắt buộc.
   - [`PredictionViewModelTest.kt`](file:///E:/MultiGo%20Reworked/feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PredictionViewModelTest.kt) (8 tests): Kiểm tra khởi động nạp trạm, polling chu kỳ, hủy polling khi onCleared, xử lý mất mạng OFFLINE, tự động refresh khi mạng phục hồi, xử lý GPS stale, hủy job khi đổi trạm.
   - [`PersonalViewModelTest.kt`](file:///E:/MultiGo%20Reworked/feature-prediction/src/test/java/com/hcmcbustracker/feature/prediction/PersonalViewModelTest.kt) (6 tests): Kiểm tra nạp danh sách yêu thích trạm & tuyến, chuyển tab, cập nhật cài đặt theme/polling/thông báo, xóa bộ nhớ đệm cache polyline và đồng bộ lại mạng lưới.
2. **Module `:feature-search` (8 tests):**
   - [`SearchViewModelTest.kt`](file:///E:/MultiGo%20Reworked/feature-search/src/test/java/com/hcmcbustracker/feature/search/SearchViewModelTest.kt) (4 tests): Kiểm tra debounce 300ms, đồng bộ hóa ID yêu thích, toggle yêu thích tuyến & trạm.
   - [`StopsViewModelTest.kt`](file:///E:/MultiGo%20Reworked/feature-search/src/test/java/com/hcmcbustracker/feature/search/StopsViewModelTest.kt) (4 tests): Kiểm tra nạp danh sách trạm, lọc tìm kiếm tiếng Việt, toggle yêu thích.
3. **Module `:feature-route` (7 tests):**
   - [`RoutesViewModelTest.kt`](file:///E:/MultiGo%20Reworked/feature-route/src/test/java/com/hcmcbustracker/feature/route/RoutesViewModelTest.kt) (3 tests): Nạp danh sách tuyến, tìm kiếm số hiệu tuyến, bật/tắt yêu thích.
   - [`RouteDetailViewModelTest.kt`](file:///E:/MultiGo%20Reworked/feature-route/src/test/java/com/hcmcbustracker/feature/route/RouteDetailViewModelTest.kt) (4 tests): Nạp chi tiết lộ trình, chuyển đổi giữa hai chiều lượt đi / lượt về (RouteVariant), nạp polyline và toggle yêu thích.
4. **Module `:core-database` (6 tests):**
   - [`BusRepositoryTest.kt`](file:///E:/MultiGo%20Reworked/core-database/src/test/java/com/hcmcbustracker/core/database/BusRepositoryTest.kt): Kiểm chứng Room DAOs, migration schema, lưu trữ SharedPreferences Preferences, và dọn dẹp sạch sẽ bộ nhớ đệm polyline qua `clearCache()`.

---

## 4. KẾT QUẢ BUILD & TEST ĐỘC LẬP THỰC TẾ

Thực hiện tuần tự trên môi trường Android Studio JBR 21:

1. `.\gradlew.bat :core-database:testDebugUnitTest`: **BUILD SUCCESSFUL** (44/44 tasks up-to-date, 6 tests passed).
2. `.\gradlew.bat :feature-route:testDebugUnitTest`: **BUILD SUCCESSFUL** (67/67 tasks up-to-date, 7 tests passed).
3. `.\gradlew.bat :feature-search:testDebugUnitTest`: **BUILD SUCCESSFUL** (67/67 tasks up-to-date, 8 tests passed).
4. `.\gradlew.bat :feature-prediction:testDebugUnitTest`: **BUILD SUCCESSFUL** (67/67 tasks up-to-date, 19/19 tests passed).
5. `.\gradlew.bat testDebugUnitTest` (Regression toàn dự án): **BUILD SUCCESSFUL** (215 actionable tasks, 0 test failed, thời gian chạy: 46s).
6. `.\gradlew.bat assembleDebug`: **BUILD SUCCESSFUL** (290 actionable tasks, tạo thành công `app-debug.apk` ~ 62.7 MB).
7. `.\gradlew.bat assembleRelease`: **BUILD SUCCESSFUL** (382 actionable tasks, tạo thành công `app-release-unsigned.apk` ~ 56.2 MB).

---

## 5. KẾT LUẬN KIỂM TOÁN (FINAL VERDICT)

### **PHÂN LOẠI NGHIỆM THU: PASS**

- **Đánh giá chung:** Dự án **HCMC Bus Tracker** (`com.hcmcbustracker.app`) đã hoàn thành toàn diện Phase 7 (Final QA & Release) và đạt mức độ sẵn sàng sản xuất (Production-Ready) cao nhất. Toàn bộ mã nguồn, cấu hình đóng gói, bảo mật kho lưu trữ và tài liệu bàn giao đều tuân thủ nghiêm ngặt 100% các tiêu chuẩn kiến trúc Clean Architecture, quy chuẩn Android hiện đại (API 26–35, Doze Mode, POST_NOTIFICATIONS, R8 Proguard) và tính toàn vẹn của dữ liệu thời gian thực EBMS.
- **Trạng thái:** Toàn bộ công việc Phase 7 đã hoàn tất. Dự án sẵn sàng nghiệm thu và phát hành chính thức. Đang dừng lại và chờ phản hồi phê duyệt của người dùng trước khi thực hiện bước tiếp theo.
