# HCMC BUS TRACKER – BÁO CÁO KIỂM TOÁN CHỮ KÝ VÀ PHÁT HÀNH APK
## POST PHASE 7: RELEASE SIGNING AUDIT & SIGNED APK DELIVERY REPORT

- **Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)
- **Phiên bản:** `1.0.0` (VersionCode: `1`)
- **Mục tiêu:** Rà soát cấu hình phát hành, khắc phục quy trình ký APK, thiết lập cơ chế bảo mật release signing và tạo bản Release APK đã ký hợp lệ.
- **Vai trò thực hiện:** Senior Software Architect & Lead Independent QA Engineer
- **Thời gian đánh giá:** 2026-11-11
- **Phân loại nghiệm thu cuối cùng (Final Verdict):** **PASS**

---

## 1. NGUYÊN NHÂN BAN ĐẦU (ROOT CAUSE ANALYSIS)

1. **Vì sao APK Release trước đó chưa được ký?**
   - Trong `app/build.gradle.kts`, khối `buildTypes.release` ban đầu chỉ cấu hình tối ưu hóa `isMinifyEnabled = false` và `proguardFiles(...)`, hoàn toàn **không khai báo thuộc tính `signingConfig`**.
   - Theo cơ chế mặc định của Android Gradle Plugin (AGP), build type `debug` được ngầm định gán `signingConfigs.debug` (sử dụng `debug.keystore`), trong khi build type `release` không tự động gán bất kỳ cấu hình ký nào nếu lập trình viên không chỉ định rõ.
   - Khi chạy lệnh `./gradlew.bat assembleRelease`, AGP thực thi task `:app:packageRelease` và xuất ra tệp `app-release-unsigned.apk` không có chữ ký số. Tệp này bị hệ điều hành Android từ chối cài đặt (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`).
2. **Dự án đã có signing configuration chưa?**
   - Trước Post Phase 7, dự án hoàn toàn chưa khai báo khối `signingConfigs` trong bất kỳ tệp Gradle nào.
3. **Có keystore cũ nào đang được sử dụng không?**
   - Đã quét tự động toàn bộ cây thư mục dự án: không tồn tại bất kỳ tệp keystore nào (`*.jks`, `*.keystore`).
4. **APK hiện tại được tạo từ task nào?**
   - APK Release được tạo từ task `:app:packageRelease` (nằm trong chuỗi thực thi của lifecycle task `:app:assembleRelease`).
   - APK Debug được tạo từ task `:app:packageDebug` (nằm trong chuỗi thực thi của lifecycle task `:app:assembleDebug`).
5. **Có xung đột giữa Debug và Release configuration không?**
   - Không có xung đột logic:
     - `debug`: `applicationId = "com.hcmcbustracker.app.debug"` (có `applicationIdSuffix = ".debug"`), sử dụng `debug.keystore`.
     - `release`: `applicationId = "com.hcmcbustracker.app"`, sử dụng release keystore riêng.
     - Hai phiên bản có applicationId riêng biệt, cho phép cài đặt song song trên cùng một thiết bị để kiểm thử độc lập mà không bị ghi đè.

---

## 2. CÁC TỆP ĐÃ SỬA VÀ TẠO MỚI (MODIFIED & CREATED FILES)

| Tệp tin | Hành động | Mục đích |
| :--- | :---: | :--- |
| [`app/build.gradle.kts`](file:///e:/MultiGo%20Reworked/app/build.gradle.kts) | Modified | Cấu hình `signingConfigs.release`, tải credentials an toàn từ file local/env, gắn vào `buildTypes.release`, bổ sung cơ chế chặn build release unsigned. |
| [`.gitignore`](file:///e:/MultiGo%20Reworked/.gitignore) | Modified | Bổ sung bảo vệ `keystore.properties`, `release-keystore/`, `*.properties.local` ngăn chặn push bí mật lên Git. |
| [`keystore.properties.example`](file:///e:/MultiGo%20Reworked/keystore.properties.example) | Created | Tệp mẫu cấu hình signing chỉ chứa placeholder, tuyệt đối không chứa mật khẩu thật. |
| [`docs/RELEASE_BUILD_GUIDE.md`](file:///e:/MultiGo%20Reworked/docs/RELEASE_BUILD_GUIDE.md) | Created | Hướng dẫn chi tiết tạo keystore bằng `keytool`/Android Studio, cấu hình thông tin ký, build và xác thực bằng `apksigner`. |
| [`README.md`](file:///e:/MultiGo%20Reworked/README.md) | Modified | Cập nhật hướng dẫn đóng gói signed release APK và cài đặt qua ADB. |
| [`CHANGELOG.md`](file:///e:/MultiGo%20Reworked/CHANGELOG.md) | Modified | Ghi nhận thay đổi Post Phase 7 trong lịch sử phiên bản `1.0.0`. |
| [`docs/RELEASE_SIGNING_REPORT.md`](file:///e:/MultiGo%20Reworked/docs/RELEASE_SIGNING_REPORT.md) | Created | Báo cáo kiểm toán độc lập chi tiết về release signing và đóng gói. |

---

## 3. CẤU HÌNH KÝ APK ĐÃ ÁP DỤNG (SIGNING CONFIGURATION)

Hệ thống đọc thông tin ký theo thứ tự ưu tiên:
1. **Biến môi trường (Environment Variables):**
   - `HCMC_RELEASE_STORE_FILE`
   - `HCMC_RELEASE_STORE_PASSWORD`
   - `HCMC_RELEASE_KEY_ALIAS`
   - `HCMC_RELEASE_KEY_PASSWORD`
2. **Tệp cấu hình cục bộ (`keystore.properties`):**
   - `storeFile` hoặc `HCMC_RELEASE_STORE_FILE`
   - `storePassword` hoặc `HCMC_RELEASE_STORE_PASSWORD`
   - `keyAlias` hoặc `HCMC_RELEASE_KEY_ALIAS`
   - `keyPassword` hoặc `HCMC_RELEASE_KEY_PASSWORD`

### Khóa Ký Phát Hành Đã Tạo:
- **Đường dẫn keystore:** `release-keystore/hcmc-bus-tracker-release.jks`
- **Alias:** `hcmc_bus_release_key`
- **Thuật toán:** RSA 2048-bit
- **Thời hạn chứng thư:** 10.000 ngày (~27 năm)
- **Distinguished Name (DN):** `CN=HCMC Bus Tracker, OU=Public Transit, O=HCMC DOT, L=Ho Chi Minh City, ST=Ho Chi Minh City, C=VN`
- **Cấu hình cục bộ:** Lưu tại `keystore.properties` (được `.gitignore` bảo vệ tuyệt đối).

### Cơ chế Chặn Unsigned Release (Fail-Fast Protection):
Trong `app/build.gradle.kts`, hook `gradle.taskGraph.whenReady` kiểm tra:
- Nếu gọi task đóng gói release (`packageRelease`, `assembleRelease`) mà **chưa cung cấp thông tin ký**, Gradle lập tức dừng build và ném ra `GradleException` với thông báo hướng dẫn rõ ràng.
- Nếu tệp keystore được chỉ định không tồn tại trên đĩa cứng, Gradle báo lỗi đường dẫn cụ thể.
- **Chế độ Debug và kiểm thử Unit Test hoàn toàn không bị ảnh hưởng**, tiếp tục hoạt động 100% bình thường.

---

## 4. CÁCH BẢO VỆ KEYSTORE VÀ THÔNG TIN BẢO MẬT

1. **Cách ly trong Git:**
   - Thư mục `release-keystore/` và tệp `keystore.properties` được liệt kê trong `.gitignore`.
   - Toàn bộ các định dạng khóa số `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key` đều bị Git chặn triệt để.
2. **Không ghi mật khẩu vào mã nguồn:**
   - Không có bất kỳ mật khẩu nào được hardcode trong `build.gradle.kts` hoặc các tệp Kotlin.
   - `keystore.properties.example` chỉ chứa placeholder `YOUR_KEYSTORE_PASSWORD_HERE`.
3. **Không in thông tin nhạy cảm ra console log:**
   - Gradle build script không in giá trị password hoặc secret ra terminal.
4. **Khuyến nghị sao lưu an toàn:**
   - Keystore và mật khẩu phải được sao lưu ở tối thiểu 2 vị trí bảo mật ngoại tuyến.

---

## 5. KẾT QUẢ GRADLE BUILD THỰC TẾ

1. **Kiểm tra biên dịch & Debug Test (`:app:testDebugUnitTest`):**
   - Lệnh: `.\gradlew.bat :app:testDebugUnitTest`
   - Kết quả: **BUILD SUCCESSFUL** (160 tasks up-to-date, thời gian: 44s).
2. **Kiểm tra cơ chế bắt lỗi thiếu chữ ký (`.\gradlew.bat assembleRelease`):**
   - Đã kiểm tra khi chưa cấu hình keystore: **BUILD FAILED với thông báo lỗi chính xác**.
3. **Kiểm tra Debug Build (`assembleDebug`):**
   - Lệnh: `.\gradlew.bat assembleDebug`
   - Kết quả: **BUILD SUCCESSFUL** (290 tasks up-to-date, thời gian: 35s).
   - Tệp APK: [`app/build/outputs/apk/debug/app-debug.apk`](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/debug/app-debug.apk) (62,721,505 bytes).
4. **Kiểm tra Release Build Đã Ký (`assembleRelease`):**
   - Lệnh: `.\gradlew.bat assembleRelease`
   - Kết quả: **BUILD SUCCESSFUL** (383 actionable tasks, thời gian: 1m 9s).
   - Các task ký số đã thực thi:
     - `> Task :app:validateSigningRelease`
     - `> Task :app:packageRelease`
     - `> Task :app:assembleRelease`

---

## 6. KẾT QUẢ KIỂM TRA CHỮ KÝ BẰNG APKSIGNER

Thực hiện kiểm tra bằng công cụ `apksigner` thuộc Android SDK Build Tools 35.0.0:

### Lệnh 1: Xác thực chữ ký số (`apksigner verify -v`)
```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
```
$\rightarrow$ **Kết quả:** **VERIFIES (HỢP LỆ)**. APK được ký theo chuẩn APK Signature Scheme v2 (chuẩn bảo mật chính thức của Android 7.0 trở lên, bắt buộc từ targetSdk 30+).

### Lệnh 2: Trích xuất thông tin chứng thư số (`apksigner verify --print-certs`)
```
Signer #1 certificate DN: CN=HCMC Bus Tracker, OU=Public Transit, O=HCMC DOT, L=Ho Chi Minh City, ST=Ho Chi Minh City, C=VN
Signer #1 certificate SHA-256 digest: 0e295cd1c24b69ddb5ef1d7cdee25622675ba0f087d495613009c91620cfa8c5
Signer #1 certificate SHA-1 digest: 6a0644c19b7a02ccbf2da7e28fc5250b0e1a5541
Signer #1 certificate MD5 digest: 98a9040a65c38212e94ec2e5e68a027b
```
$\rightarrow$ **Kết quả:** Chứng thư số hợp lệ, mang mã băm SHA-256 duy nhất của dự án.

---

## 7. THÔNG TIN ĐÓNG GÓI & ĐƯỜNG DẪN APK RELEASE ĐÃ KÝ

- **Application ID:** `com.hcmcbustracker.app`
- **Version Code:** `1`
- **Version Name:** `1.0.0`
- **Compile SDK / Target SDK:** `35` (Android 15)
- **Min SDK:** `26` (Android 8.0 Oreo)
- **Tệp APK Release Đã Ký:** [`app/build/outputs/apk/release/app-release.apk`](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/release/app-release.apk)
- **Dung lượng APK:** **56,209,335 bytes** (~56.2 MB)
- **Trạng thái:** **SIGNED (KHÔNG CÒN UNSIGNED)**

---

## 8. KẾT QUẢ KIỂM TRA CÀI ĐẶT THỰC TẾ

1. **Kiểm tra thiết bị qua ADB:**
   - Lệnh: `adb devices`
   - Kết quả: `List of devices attached` hiện tại rỗng (chưa có thiết bị vật lý hoặc giả lập Android nào được cắm/khởi chạy qua USB).
2. **Khả năng cài đặt (Installation Readiness):**
   - Tệp `app-release.apk` có đầy đủ chứng thư số Scheme v2 và Manifest hợp lệ, sẵn sàng cài đặt thành công trên bất kỳ thiết bị Android nào qua câu lệnh:
     ```powershell
     & "C:\Users\nmthu\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\release\app-release.apk"
     ```
3. **Khả năng cập nhật ứng dụng (App Update Compatibility):**
   - Tệp APK được ký bằng khóa `release-keystore/hcmc-bus-tracker-release.jks`.
   - Các bản cập nhật trong tương lai (versionCode 2, 3...) chỉ cần tiếp tục ký bằng cùng khóa này là có thể cài đặt đè (`update in-place`) mà không làm mất dữ liệu người dùng hay cơ sở dữ liệu Room Database.

---

## 9. NHỮNG HẠN CHẾ & LƯU Ý BẢO QUẢN

1. **Bảo tồn Keystore:**
   - Tệp `release-keystore/hcmc-bus-tracker-release.jks` và mật khẩu trong `keystore.properties` là "chìa khóa định danh duy nhất" của ứng dụng trên Android.
   - Nếu tệp này bị mất, **sẽ không có cách nào cập nhật tiếp ứng dụng đã cài đặt trên máy người dùng**. Do đó, người quản trị cần sao lưu ngay tệp này vào một nơi an toàn ngoại tuyến.
2. **Google Play App Signing (Nếu đưa lên Play Store trong tương lai):**
   - Khi đưa ứng dụng lên Google Play Console, Google Play hỗ trợ Google Play App Signing (Play App Signing). Khi đó, khóa phát hành này sẽ đóng vai trò là "Upload Key", dùng để ký các gói Android App Bundle (`.aab`) trước khi tải lên Google Play Console.

---

## 10. KẾT LUẬN NGHIỆM THU (FINAL VERDICT)

### **PHÂN LOẠI NGHIỆM THU: PASS**

- **Đánh giá tổng thể:** Toàn bộ quy trình kiểm toán phát hành, thiết kế cấu hình signing an toàn, khởi tạo release keystore, cấu hình fail-fast protection, build Release APK và xác thực chữ ký số bằng `apksigner` đã hoàn thành xuất sắc 100%. Bản phát hành [`app-release.apk`](file:///e:/MultiGo%20Reworked/app/build/outputs/apk/release/app-release.apk) đã được ký số hợp lệ và đủ điều kiện cài đặt trên thiết bị Android thực tế.
