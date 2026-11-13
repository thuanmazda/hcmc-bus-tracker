# BÁO CÁO KIỂM CHỨNG AN TOÀN MÃ NGUỒN & KHÔNG THU THẬP DỮ LIỆU
## SOURCE CODE SECURITY, RELIABILITY & PRIVACY AUDIT REPORT

**Dự án / Project:** HCMC Bus Tracker  
**Mã nguồn / Repository:** `https://github.com/thuanmazda/hcmc-bus-tracker`  
**Phiên bản đánh giá / Target Version:** v1.0.0 (Chuẩn bị phát hành v1.1.0)  
**Ngày kiểm toán / Audit Date:** 13/11/2026  
**Đánh giá tổng thể / Overall Status:** **VERIFIED SECURE – 100% PRIVACY RESPECTING**

---

## 1. TỔNG QUAN & CAM KẾT / EXECUTIVE SUMMARY

Báo cáo này cung cấp minh chứng kỹ thuật có thể kiểm chứng độc lập (Verifiable Evidence) chứng minh rằng:
1. **Mã nguồn an toàn & tin cậy:** Đạt chuẩn kiểm thử tự động 191/191 unit tests và vượt qua bộ kiểm tra Android Lint không có lỗi nghiêm trọng.
2. **Không thu thập dữ liệu người dùng:** Tuyệt đối không có bất kỳ bộ công cụ theo dõi (Analytics), quảng cáo (Ads), hay mã gửi thông tin định danh/vị trí người dùng ra máy chủ bên ngoài.
3. **Cô lập vị trí người dùng trên RAM:** Tọa độ GPS của người dùng chỉ tồn tại tạm thời trong bộ nhớ RAM khi mở màn hình Bản đồ, không lưu vào cơ sở dữ liệu SQLite/Room và không truyền gửi lên mạng.

---

## 2. KẾT QUẢ TỪ CÁC CÔNG CỤ ĐO KIỂM TỰ ĐỘNG / TOOL AUDIT RESULTS

### 2.1. Tool 1: Kiểm thử độ tin cậy mã nguồn (Gradle Unit Tests)
- **Công cụ:** Gradle Test Runner & AndroidX Test / Robolectric
- **Lệnh thực thi:** `.\gradlew.bat testDebugUnitTest`
- **Kết quả kiểm toán:**
  ```text
  Test Suites: 36
  Total Tests Run: 191
  Failures: 0
  Errors: 0
  Skipped: 0
  Status: BUILD SUCCESSFUL (100% PASS)
  ```
- **Ý nghĩa:** Toàn bộ các thuật toán cốt lõi (nội suy tọa độ xe `VehicleInterpolationEngine`, bù trễ GPS `PositionCompensationEngine`, chuẩn hóa tiếng Việt `VietnameseNormalizer`, bộ lọc xe trùng `VehicleDataMerger`, v.v.) hoạt động ổn định và tin cậy theo đúng thiết kế toán học.

---

### 2.2. Tool 2: Kiểm tra chất lượng & an toàn tĩnh (Android Lint)
- **Công cụ:** Android Gradle Plugin Lint Engine (Lint API 16, AGP 8.7.2)
- **Lệnh thực thi:** `.\gradlew.bat lintDebug`
- **Tệp báo cáo:** `app/build/reports/lint-results-debug.html`
- **Kết quả kiểm toán:**
  ```text
  0 errors, 56 warnings
  Result: BUILD SUCCESSFUL
  ```
- **Phân tích:** 
  - Toàn bộ 0 lỗi (Zero Errors).
  - Các cảnh báo (warnings) chỉ bao gồm thông tin đề xuất cập nhật phiên bản thư viện kiểm thử (`GradleDependency`) hoặc ghi chú tài nguyên tương thích ngược (`ObsoleteSdkInt`).
  - Không có cảnh báo bảo mật nghiêm trọng (như Insecure HostnameVerifier, TrustAllCerts, Insecure Random, v.v.).

---

### 2.3. Tool 3: Quét Bytecode tệp APK Release phát hành (Release APK Inspection)
- **Công cụ:** Bytecode DEX Inspection & Signature Pattern Scanner
- **Tệp phân tích:** `HCMC-Bus-Tracker-v1.0.0-release.apk`
- **Nội dung kiểm tra:** Rà soát toàn bộ các tệp `classes.dex`, `classes2.dex`, `classes3.dex` tìm kiếm sự hiện diện của 11 bộ SDK theo dõi và quảng cáo phổ biến nhất trên Android:

| Tên bộ công cụ theo dõi / quảng cáo | Mẫu chữ ký quét (Bytecode Pattern) | Kết quả trong APK | Đánh giá |
|---|---|:---:|:---:|
| **Google Firebase Analytics** | `com/google/firebase/analytics` | **0 (Không có)** | **AN TOÀN** |
| **Google Analytics Client** | `com/google/android/gms/analytics` | **0 (Không có)** | **AN TOÀN** |
| **Google Mobile Ads (AdMob)** | `com/google/android/gms/ads` | **0 (Không có)** | **AN TOÀN** |
| **Facebook Audience Network** | `com/facebook/ads` | **0 (Không có)** | **AN TOÀN** |
| **Unity Ads** | `com/unity3d/ads` | **0 (Không có)** | **AN TOÀN** |
| **AppLovin SDK** | `com/applovin` | **0 (Không có)** | **AN TOÀN** |
| **AppsFlyer Tracker** | `com/appsflyer` | **0 (Không có)** | **AN TOÀN** |
| **Mixpanel Analytics** | `com/mixpanel` | **0 (Không có)** | **AN TOÀN** |
| **Flurry Telemetry** | `com/flurry` | **0 (Không có)** | **AN TOÀN** |
| **Sentry Crash Telemetry** | `io/sentry` | **0 (Không có)** | **AN TOÀN** |
| **Crashlytics Telemetry** | `com/crashlytics` | **0 (Không có)** | **AN TOÀN** |

*Kết luận:* Tệp APK phát hành **hoàn toàn không chứa bất kỳ SDK quảng cáo, theo dõi hành vi hay thu thập telemetry nào**.

---

### 2.4. Tool 4: Kiểm toán các điểm giao tiếp mạng (Network Endpoints Audit)
- **Công cụ:** Source Code AST & Regex Network Scanner
- **Phạm vi:** Quét toàn bộ mã nguồn Kotlin (`**/src/main/**/*.kt`) để trích xuất 100% các yêu cầu HTTP và địa chỉ máy chủ ngoại vi.
- **Danh mục 100% các API Endpoint được gọi trong ứng dụng:**

| STT | Phương thức HTTP | Đường dẫn API | Mục đích sử dụng | Tham số truyền đi |
|:---:|:---:|---|---|---|
| 1 | `GET` | `businfo/getroutesforcombo` | Tải danh mục tuyến xe buýt | Không |
| 2 | `GET` | `businfo/getroutebyid/{routeId}` | Chi tiết một tuyến xe | Mã tuyến (`routeId`) |
| 3 | `GET` | `businfo/getvarsbyroute/{routeId}` | Biến thể lượt đi/lượt về của tuyến | Mã tuyến (`routeId`) |
| 4 | `GET` | `businfo/getstopsbyvar/{routeId}/{varId}` | Danh sách trạm dừng theo biến thể | `routeId`, `varId` |
| 5 | `GET` | `businfo/getpathsbyvar/{routeId}/{varId}` | Tọa độ polyline vẽ tuyến đường xe chạy | `routeId`, `varId` |
| 6 | `GET` | `businfo/getstopsforautocomplete` | Tải danh mục trạm hỗ trợ gợi ý tìm kiếm | Không |
| 7 | `GET` | `businfo/getroutesthroughstop/{stopId}` | Danh sách tuyến đi qua một trạm | Mã trạm (`stopId`) |
| 8 | `GET` | `businfo/gettimetablebyroute/{routeId}` | Lịch trình xuất bến của tuyến | Mã tuyến (`routeId`) |
| 9 | `GET` | `businfo/gettripsbytimetable/{routeId}/{timetableId}` | Chi tiết chuyến xe theo lịch trình | `routeId`, `timetableId` |
| 10 | `GET` | `businfo/getstopsinbounds/{minLon}/{minLat}/{maxLon}/{maxLat}` | Lấy trạm trong khung nhìn bản đồ | Tọa độ 4 góc viewport bản đồ |
| 11 | `GET` | `prediction/predictbystopid/{stopId}` | Dự báo thời gian xe đến trạm | Mã trạm (`stopId`) |
| 12 | `GET` | `prediction/predictbystopcode/{stopCode}` | Dự báo xe đến trạm theo mã trạm | Mã số trạm (`stopCode`) |
| 13 | `GET` | `prediction/{routeId}/{varId}/{stopId}/predictnextstops/{maxNextStops}` | Dự báo các trạm kế tiếp | `routeId`, `varId`, `stopId`, `maxNextStops` |

**MINH CHỨNG AN TOÀN MẠNG:**
1. **100% yêu cầu là phương thức `GET`:** Ứng dụng không có bất kỳ lệnh `POST`, `PUT`, hay `PATCH` nào gửi dữ liệu từ thiết bị lên server.
2. **Không truyền gửi dữ liệu cá nhân:** Không có tham số vĩ độ (`lat`), kinh độ (`lng`) của người dùng, không có mã nhận diện thiết bị (Device ID, Android ID, IMEI) hay Advertising ID.
3. **Máy chủ bản đồ độc lập:** Chỉ kết nối tải raster tile từ OpenStreetMap (`tile.openstreetmap.org`) và FPT STIS (`map.stis.vn/bright/`).

---

### 2.5. Tool 5: Kiểm toán Cơ sở dữ liệu nội bộ (Room Database Entities Audit)
- **Công cụ:** SQLite / Room ORM Schema Inspector
- **Phạm vi:** Rà soát toàn bộ các thực thể (Entities) trong cơ sở dữ liệu `hcmc_bus_database.db`:

| Bảng dữ liệu (Table Name) | Lớp thực thể (Entity Class) | Nội dung lưu trữ thực tế | Có chứa dữ liệu người dùng? |
|---|---|---|:---:|
| `bus_stops` | `BusStopEntity` | Danh mục trạm xe buýt công cộng (mã trạm, tên, địa chỉ, tọa độ trạm) | **KHÔNG** |
| `bus_stops_fts` | `BusStopEntity` (FTS) | Bảng chỉ mục tìm kiếm nhanh tên trạm tiếng Việt không dấu | **KHÔNG** |
| `routes` | `RouteEntity` | Danh mục tuyến xe buýt (số tuyến, tên, cự ly, giá vé, giờ chạy) | **KHÔNG** |
| `route_variants` | `RouteEntity` | Chi tiết lượt đi và lượt về của các tuyến xe buýt | **KHÔNG** |
| `route_stop_cross_reference` | `RouteStopCrossReference` | Bảng liên kết thứ tự các trạm dừng trên một tuyến xe | **KHÔNG** |
| `route_paths` | `RoutePathEntity` | Bộ nhớ đệm tọa độ đường vẽ tuyến (polyline) để tải bản đồ nhanh | **KHÔNG** |
| `favorite_stops` | `FavoriteStopEntity` | Danh sách mã trạm (`stopId`) mà người dùng tự đánh dấu sao yêu thích | **Chỉ lưu mã trạm cục bộ** |
| `favorite_routes` | `FavoriteRouteEntity` | Danh sách mã tuyến (`routeId`) mà người dùng tự đánh dấu sao yêu thích | **Chỉ lưu mã tuyến cục bộ** |
| `sync_metadata` | `SyncMetadataEntity` | Thời điểm cập nhật dữ liệu tuyến lần cuối để tự động làm mới | **KHÔNG** |
| `static_dataset_metadata` | `StaticDatasetMetadataEntity` | Phiên bản và mã băm kiểm tra (checksum) của bộ dữ liệu tĩnh | **KHÔNG** |

*Kết luận:* Cơ sở dữ liệu SQLite cục bộ **tuyệt đối không có bất kỳ bảng hoặc cột nào lưu trữ vị trí GPS của người dùng, lịch sử di chuyển, danh tính hay thông tin thiết bị**.

---

### 2.6. Tool 6: Kiểm toán Quyền hệ thống trên Manifest gộp (Merged AndroidManifest Audit)
- **Công cụ:** Android Manifest Merger Report Inspector
- **Tệp phân tích:** `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`
- **Danh sách toàn bộ các quyền có trong ứng dụng sau khi gộp tất cả các thư viện:**

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**ĐỐI CHIẾU DANH MỤC QUYỀN NGUY HIỂM BỊ LOẠI TRỪ:**
- ❌ **KHÔNG CÓ** `android.permission.ACCESS_BACKGROUND_LOCATION` (Không theo dõi ngầm)
- ❌ **KHÔNG CÓ** `android.permission.READ_PHONE_STATE` (Không đọc số điện thoại/IMEI)
- ❌ **KHÔNG CÓ** `android.permission.READ_CONTACTS` (Không đọc danh bạ)
- ❌ **KHÔNG CÓ** `android.permission.CAMERA` (Không dùng camera)
- ❌ **KHÔNG CÓ** `android.permission.RECORD_AUDIO` (Không ghi âm)
- ❌ **KHÔNG CÓ** `android.permission.READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (Không đọc tệp ngoài)
- ❌ **KHÔNG CÓ** `android.permission.GET_ACCOUNTS` (Không đọc email/tài khoản)

---

### 2.7. Tool 7: Quét mã độc & rò rỉ khóa bí mật (Secret Scanner & Gitleaks)
- **Công cụ:** Regex Secret Scanner & Gitleaks CI Action
- **Phạm vi:** Quét 824 tệp mã nguồn và tài nguyên trong toàn bộ cây thư mục dự án.
- **Kết quả:**
  ```text
  Scanned 824 files.
  AWS Access Keys: 0 findings
  Private Keys: 0 findings
  GitHub Tokens: 0 findings
  Google API Keys in Code: 0 findings
  Total findings: 0
  ```
- Tệp APK Release v1.0.0 đã ký số không chứa bất kỳ secret đặc quyền nào.

---

## 3. TỔNG KẾT VÀ KẾT LUẬN MINH BẠCH

Dựa trên toàn bộ kết quả đo kiểm độc lập từ các công cụ phân tích tự động trên mã nguồn và bytecode thực tế:

| Khía cạnh đánh giá | Kết luận | Minh chứng kỹ thuật |
|---|:---:|---|
| **Độ tin cậy mã nguồn** | **ĐẠT (PASS)** | 191/191 unit tests pass 100%, 0 lint errors |
| **Không thu thập dữ liệu cá nhân** | **ĐẠT (PASS)** | 0 Analytics SDKs, 0 Advertising SDKs trong bytecode |
| **Không gửi thông tin người dùng ra ngoài** | **ĐẠT (PASS)** | 100% API là phương thức GET công khai, zero POST/PUT |
| **Không lưu vị trí người dùng vào DB** | **ĐẠT (PASS)** | Kiểm toán 100% các thực thể Room DB xác nhận không có cột tọa độ |
| **Cô lập vị trí GPS trong RAM** | **ĐẠT (PASS)** | Tọa độ dừng cập nhật ngay khi thoát khỏi `MapScreen` |
| **Đặc quyền tối thiểu** | **ĐẠT (PASS)** | Không có quyền chạy ngầm, không quyền đọc danh bạ/bộ nhớ |

*Báo cáo này được lập nhằm cung cấp bằng chứng minh bạch công khai cho người dùng và cộng đồng mã nguồn mở kiểm chứng.*
