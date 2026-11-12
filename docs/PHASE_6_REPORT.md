# BÁO CÁO NGHIỆM THU PHASE 6: USER EXPERIENCE & PERSONALIZATION
## Dự án: HCMC Bus Tracker (`com.hcmcbustracker.app`)

---

## 1. TỔNG QUAN PHASE 6

Phase 6 tập trung hoàn thiện trải nghiệm người dùng, cá nhân hóa, kiến trúc thông báo chuẩn Android và tối ưu hóa hệ thống tra cứu giao thông công cộng TP.HCM dựa trên nguồn dữ liệu duy nhất từ EBMS API.

### Trạng thái nghiệm thu: **PASS**

---

## 2. KẾT QUẢ TRIỂN KHAI THEO YÊU CẦU

### 2.1. Quản lý Yêu thích (Favorites)
- **Cơ chế lưu trữ:** Sử dụng Room Database v2 với 2 bảng `favorite_stops` và `favorite_routes`, hỗ trợ migration tự động `MIGRATION_1_2`.
- **Đồng bộ đa màn hình:** Trạng thái yêu thích được kết nối thông qua `BusRepository` với các `Flow` phản ứng tức thì:
  - Màn hình Tuyến xe (`RoutesScreen`): Cho phép bật/tắt yêu thích trực tiếp trên `RouteCard`.
  - Màn hình Chi tiết tuyến (`RouteDetailScreen`): Bổ sung nút trái tim tại `TopAppBar` để đánh dấu/hủy yêu thích tuyến.
  - Màn hình Trạm dừng (`StopsScreen` & `StopPredictionScreen`): Cho phép lưu/bỏ lưu trạm yêu thích.
  - Màn hình Tìm kiếm (`SearchScreen`): Đồng bộ trạng thái yêu thích trên cả thẻ trạm và thẻ tuyến.
  - Màn hình Cá nhân (`PersonalScreen`): Hiển thị danh sách trạm và tuyến đã lưu, hỗ trợ xóa nhanh và nhấp chuyển tiếp trực tiếp đến chi tiết.

### 2.2. Tìm kiếm Nâng cao (Search & Debounce)
- **Debounce chuẩn hóa:** Áp dụng `debounce(300ms)` trên cả `SearchViewModel` và `StopsViewModel`, ngăn chặn triệt để tình trạng query database liên tục khi người dùng đang nhập liệu.
- **Xử lý tiếng Việt:** Tích hợp `VietnameseNormalizer.removeAccents()` cho phép tìm kiếm chính xác cả tiếng Việt có dấu, không dấu, số hiệu tuyến (VD: "01", "150"), tên đường hoặc mã trạm (VD: "Q1 001").
- **Trạng thái giao diện:** Xử lý toàn diện các trạng thái: Đang tìm kiếm (`LoadingState`), không có kết quả (`EmptyState`), lỗi kết nối (`ErrorState` có nút Thử lại).

### 2.3. Chi tiết Tuyến và Điểm dừng (Stop & Route Details)
- **Chi tiết trạm:** Hiển thị danh sách tuyến đi qua (`passing routes`), địa chỉ đầy đủ, mã số trạm.
- **Chi tiết tuyến:** Hỗ trợ chuyển đổi lượt đi / lượt về (`RouteVariant`), hiển thị danh sách trạm dừng theo thứ tự và xem lộ trình trên bản đồ (`interactive_map`).
- **Dữ liệu thời gian thực:** Tích hợp dữ liệu dự báo EBMS khi khả dụng; nếu máy chủ không có xe đang hoạt động, hiển thị trạng thái `TRẠM TRỐNG / EMPTY` rõ ràng, không giả định xe ảo.

### 2.4. Kiến trúc Thông báo (Notifications Architecture)
- **Tuân thủ giới hạn nền Android:**
  - Không giả định ứng dụng có thể chạy ngầm polling 15 giây liên tục (tránh vi phạm Android Doze Mode và Background Execution Limits).
  - Làm rõ hạn chế của WorkManager với chu kỳ định kỳ tối thiểu 15 phút (`PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS = 15 min`).
- **Cấu hình Notification Channel:** Kênh `bus_arrival_alerts` ("Thông báo xe buýt đến trạm") với độ ưu tiên `IMPORTANCE_DEFAULT`.
- **Quyền hạn Android 13+ (API 33):** Khai báo quyền `android.permission.POST_NOTIFICATIONS` và kiểm tra runtime permission an toàn trước khi gửi thông báo.
- **Chống spam & Trùng lặp (Anti-duplication):** Lưu vết lịch sử thông báo theo khóa `${stopId}_${plateNumber}_${routeNo}` với thời gian chờ (cooldown) 5 phút giữa các lần cảnh báo của cùng một xe.
- **Loại bỏ GPS cũ (Stale GPS Rejection):** Từ chối gửi thông báo nếu tín hiệu GPS của xe có độ trễ vượt quá ngưỡng 180 giây hoặc timestamp không hợp lệ.
- **Cảnh báo miễn trừ trách nhiệm bắt buộc:** Thông báo ghi rõ disclaimer: *"⚠️ Dự báo ước lượng dựa trên dữ liệu EBMS, có thể thay đổi tùy điều kiện giao thông."* Không cam kết giờ chính xác tuyệt đối.

### 2.5. Cài đặt & Tùy biến (Settings & Personalization)
- **Tùy chọn Chu kỳ Polling:** Hỗ trợ lựa chọn chu kỳ làm mới thời gian thực: 10s, 15s (Mặc định), 30s, 60s.
- **Chế độ Giao diện (Theme):** Lựa chọn giữa Theo hệ thống (`SYSTEM`), Giao diện sáng (`LIGHT`), Giao diện tối (`DARK`). Ứng dụng tự động cập nhật ngay lập tức toàn bộ UI thông qua `userPreferencesFlow`.
- **Quản lý Bộ nhớ đệm:** 
  - Nút *"Đồng bộ lại toàn bộ dữ liệu"* để cập nhật mạng lưới tuyến và trạm mới nhất từ EBMS.
  - Nút *"Xóa bộ nhớ đệm lộ trình (Cache Polyline)"* để giải phóng dung lượng Room và buộc tải mới tọa độ đường đi khi cần.
- **Ghi nhận Bản quyền (Attribution):**
  - Dữ liệu vận hành: Trung tâm Quản lý Giao thông công cộng TP.HCM (EBMS / buyttphcm.com.vn).
  - Dữ liệu bản đồ số: OpenStreetMap contributors & MapLibre Native SDK (© OpenStreetMap contributors).

### 2.6. Trải nghiệm Người dùng & Tối ưu Giao diện
- Thiết kế tương thích Material 3 với bảng màu thương hiệu xanh lá/xanh dương xe buýt Sài Gòn.
- Đầy đủ nhãn trợ năng (`contentDescription`) trên tất cả các icon và nút bấm.
- Sử dụng `LazyColumn` với key định danh duy nhất (`id`), bảo đảm cuộn mượt mà không bị giật/lag.

---

## 3. DANH SÁCH FILE THỰC HIỆN TRONG PHASE 6

| Module | Tệp tin | Mô tả thay đổi |
|---|---|---|
| `:core-model` | `core-model/.../AppSettings.kt` | Định nghĩa `AppThemeMode` và `UserPreferences` |
| `:core-database` | `core-database/.../AdditionalDaos.kt` | Bổ sung hàm `clearAllPaths()` trong `RoutePathDao` |
| `:core-database` | `core-database/.../BusRepository.kt` | Thêm các hàm quản lý `UserPreferences`, theme, chu kỳ và `clearCache()` |
| `:core-database` | `core-database/.../BusRepositoryImpl.kt` | Triển khai SharedPreferences và xử lý xóa cache |
| `:core-database` | `core-database/.../BusRepositoryTest.kt` | Bổ sung unit test cho preferences, favorite routes và clearCache |
| `:feature-route` | `feature-route/.../RouteDetailViewModel.kt` | Tích hợp trạng thái `isFavorite`, hàm `toggleFavorite()` |
| `:feature-route` | `feature-route/.../RouteDetailScreen.kt` | Thêm nút trái tim yêu thích trên TopAppBar |
| `:feature-route` | `feature-route/.../RouteDetailViewModelTest.kt` | Unit test cho `RouteDetailViewModel` và toggle favorite |
| `:feature-route` | `feature-route/.../RoutesViewModelTest.kt` | Sửa timeout ảo và bổ sung test `toggleFavorite` |
| `:feature-search` | `feature-search/.../SearchViewModel.kt` | Cấu hình debounce 300ms, đồng bộ favorite routes và stops |
| `:feature-search` | `feature-search/.../SearchScreen.kt` | Tái cấu trúc dùng `RouteCard` & `StopCard`, xử lý empty/error states |
| `:feature-search` | `feature-search/.../StopsViewModel.kt` | Chuẩn hóa debounce 300ms |
| `:feature-search` | `feature-search/.../SearchViewModelTest.kt` | Unit test kiểm tra debounce và favorites |
| `:feature-search` | `feature-search/.../StopsViewModelTest.kt` | Unit test tìm kiếm trạm dừng |
| `:feature-prediction` | `feature-prediction/.../BusArrivalNotificationManager.kt` | Quản lý thông báo: permission, de-duplication, stale GPS, disclaimer |
| `:feature-prediction` | `feature-prediction/.../BusArrivalNotificationManagerTest.kt` | 5 unit test kiểm chứng logic thông báo |
| `:feature-prediction` | `feature-prediction/.../PersonalViewModel.kt` | Quản lý tab trạm/tuyến yêu thích, theme, polling, xóa cache |
| `:feature-prediction` | `feature-prediction/.../PersonalScreen.kt` | Giao diện Cá nhân hóa đa tab: Trạm, Tuyến, Cài đặt & Bản quyền |
| `:feature-prediction` | `feature-prediction/.../PersonalViewModelTest.kt` | Unit test cho `PersonalViewModel` |
| `:app` | `app/.../MainActivity.kt` | Lắng nghe `userPreferences.themeMode` để điều khiển Dark/Light Theme toàn ứng dụng |
| `:app` | `app/src/main/AndroidManifest.xml` | Khai báo quyền `POST_NOTIFICATIONS` cho Android 13+ |

---

## 4. KẾT QUẢ KIỂM THỬ VÀ ĐÓNG GÓI

### 4.1. Unit Test theo từng Module (Chạy tuần tự)
- `:core-database:testDebugUnitTest`: **BUILD SUCCESSFUL** (Tất cả test pass).
- `:feature-route:testDebugUnitTest`: **BUILD SUCCESSFUL** (Tất cả test pass).
- `:feature-search:testDebugUnitTest`: **BUILD SUCCESSFUL** (Tất cả test pass).
- `:feature-prediction:testDebugUnitTest`: **BUILD SUCCESSFUL** (19 test pass).

### 4.2. Toàn bộ Unit Test dự án (Regression Testing)
- Lệnh: `.\gradlew.bat testDebugUnitTest`
- Kết quả: **BUILD SUCCESSFUL** (215 actionable tasks, 0 test failure).

### 4.3. Đóng gói Debug APK
- Lệnh: `.\gradlew.bat assembleDebug`
- Kết quả: **BUILD SUCCESSFUL** (290 actionable tasks).
- Tệp APK: `app\build\outputs\apk\debug\app-debug.apk` (Dung lượng: ~62.7 MB).

---

## 5. KẾT LUẬN & ĐỀ XUẤT

Phase 6 đã hoàn thành 100% các tiêu chí về Trải nghiệm Người dùng, Yêu thích, Tìm kiếm, Thông báo và Cài đặt cá nhân hóa. Toàn bộ mã nguồn tuân thủ nguyên tắc không đưa dữ liệu giả vào production runtime, chỉ sử dụng dữ liệu thực từ EBMS API.
