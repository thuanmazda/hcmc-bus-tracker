# HCMC BUS TRACKER – HƯỚNG DẪN KÝ VÀ PHÁT HÀNH APK
## RELEASE SIGNING & BUILD GUIDE

Tài liệu này hướng dẫn chi tiết quy trình tạo khóa ký phát hành (Release Keystore), cấu hình thông tin bảo mật, thực hiện đóng gói APK Release đã ký, kiểm tra chữ ký số bằng Android SDK Build Tools và cài đặt lên thiết bị Android.

---

## 1. NGUYÊN TẮC BẢO MẬT BẮT BUỘC

1. **Tuyệt đối không commit tệp keystore (`*.jks`, `*.keystore`)** lên GitHub hoặc bất kỳ hệ thống quản lý mã nguồn công khai nào.
2. **Tuyệt đối không commit mật khẩu thật hay private key** vào mã nguồn (`build.gradle.kts`, `gradle.properties`, v.v.).
3. Tệp cấu hình chứa thông tin ký cục bộ `keystore.properties` đã được liệt kê trong `.gitignore` để tránh vô tình push lên Git.
4. **Sao lưu an toàn (Secure Backup):** Lưu trữ tệp keystore và mật khẩu tại ít nhất 2 nơi bảo mật ngoại tuyến (ví dụ: trình quản lý mật khẩu của tổ chức, USB mã hóa). Nếu mất keystore phát hành, ứng dụng sẽ không thể cập nhật trực tiếp trên thiết bị của người dùng.

---

## 2. TẠO KHÓA KÝ PHÁT HÀNH (RELEASE KEYSTORE)

### Cách 1: Sử dụng công cụ `keytool` từ terminal / PowerShell (Khuyến nghị)

Dự án ưu tiên đặt tên tệp là `hcmc-bus-tracker-release.jks` và đặt trong thư mục `release-keystore/` tại gốc dự án (thư mục này đã được Git ignore hoàn toàn).

Chạy lệnh PowerShell sau (sử dụng `keytool` đi kèm JBR của Android Studio):

```powershell
# Tạo thư mục release-keystore nếu chưa có
New-Item -ItemType Directory -Force -Path "release-keystore"

# Sinh khóa ký RSA 2048-bit, thời hạn 10.000 ngày (~27 năm)
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore "release-keystore\hcmc-bus-tracker-release.jks" `
  -alias "hcmc_bus_release_key" `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000 `
  -storetype JKS
```

Khi công cụ hỏi thông tin:
- Nhập mật khẩu keystore (và ghi nhớ lại mật khẩu này).
- Nhập họ tên, đơn vị, thành phố, mã quốc gia (`VN`).
- Xác nhận thông tin bằng cách nhập `yes`.

### Cách 2: Sử dụng giao diện đồ họa Android Studio

1. Mở dự án trong Android Studio.
2. Trên thanh menu, chọn **Build** $\rightarrow$ **Generate Signed App Bundle / APK...**
3. Chọn **APK** $\rightarrow$ nhấn **Next**.
4. Tại mục **Key store path**, nhấn **Create new...**
   - **Key store path:** Trỏ tới thư mục `release-keystore/hcmc-bus-tracker-release.jks`.
   - **Password / Confirm:** Nhập mật khẩu bảo vệ keystore.
   - **Alias:** Nhập `hcmc_bus_release_key`.
   - **Password / Confirm (Key):** Nhập mật khẩu cho key.
   - **Validity (years):** Điền `25` hoặc `30`.
   - **Certificate:** Điền thông tin người quản trị hoặc tổ chức.
   - Nhấn **OK**.

---

## 3. CẤU HÌNH THÔNG TIN KÝ (SIGNING CONFIGURATION)

Hệ thống hỗ trợ 2 phương thức cung cấp thông tin ký:

### Phương thức A: Sử dụng tệp `keystore.properties` (Khuyến nghị cho môi trường Local Dev)

1. Sao chép tệp mẫu `keystore.properties.example` thành `keystore.properties` tại thư mục gốc dự án:
   ```powershell
   Copy-Item "keystore.properties.example" "keystore.properties"
   ```
2. Chỉnh sửa nội dung `keystore.properties` với thông tin thực tế:
   ```properties
   storeFile=release-keystore/hcmc-bus-tracker-release.jks
   storePassword=MAT_KHAU_KEYSTORE_CUA_BAN
   keyAlias=hcmc_bus_release_key
   keyPassword=MAT_KHAU_KEY_CUA_BAN
   ```

### Phương thức B: Sử dụng Biến môi trường (Khuyến nghị cho CI/CD Pipeline)

Thiết lập các biến môi trường trước khi chạy Gradle build:

```powershell
$env:HCMC_RELEASE_STORE_FILE = "release-keystore/hcmc-bus-tracker-release.jks"
$env:HCMC_RELEASE_STORE_PASSWORD = "MAT_KHAU_KEYSTORE_CUA_BAN"
$env:HCMC_RELEASE_KEY_ALIAS = "hcmc_bus_release_key"
$env:HCMC_RELEASE_KEY_PASSWORD = "MAT_KHAU_KEY_CUA_BAN"
```

---

## 4. ĐÓNG GÓI BẢN PHÁT HÀNH (ASSEMBLE RELEASE)

Chạy lệnh đóng gói qua Gradle Wrapper:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

- **Đường dẫn APK đầu ra:** `app/build/outputs/apk/release/app-release.apk`
- **Lưu ý quan trọng:** Nếu chưa cấu hình thông tin ký, Gradle sẽ **lập tức báo lỗi rõ ràng** và dừng tiến trình, ngăn chặn việc vô tình xuất ra file APK unsigned.

---

## 5. KIỂM TRA CHỮ KÝ VÀ CHỨNG THƯ SỐ BẰNG APKSIGNER

Sử dụng công cụ `apksigner` trong Android SDK Build Tools (phiên bản 35.0.0):

```powershell
$apksigner = "C:\Users\nmthu\AppData\Local\Android\Sdk\build-tools\35.0.0\apksigner.bat"

# 1. Xác thực tính hợp lệ của chữ ký APK
& $apksigner verify -v "app\build\outputs\apk\release\app-release.apk"

# 2. In thông tin chi tiết về chứng thư số (DN, SHA-256 fingerprint, scheme v1/v2/v3)
& $apksigner verify --print-certs "app\build\outputs\apk\release\app-release.apk"
```

Kết quả mong đợi:
- `Verifies`: **DOES NOT VERIFY** $\rightarrow$ Thất bại (chưa ký).
- `Verifies`: **VERIFIED** $\rightarrow$ Thành công.
- `Verified using v1 scheme (JAR signing): true`
- `Verified using v2 scheme (APK Signature Scheme v2): true`
- `Signer #1 certificate DN`: Chứa thông tin đã nhập khi tạo keystore.
- `Signer #1 certificate SHA-256 digest`: Mã băm chứng thực dùng để kiểm tra tính toàn vẹn khi cập nhật app.

---

## 6. CÀI ĐẶT VÀ KIỂM THỬ TRÊN THIẾT BỊ THỰC TẾ

1. Kết nối thiết bị Android hoặc khởi chạy giả lập qua cổng USB (bật USB Debugging).
2. Kiểm tra thiết bị đã sẵn sàng:
   ```powershell
   adb devices
   ```
3. Cài đặt APK:
   ```powershell
   adb install -r "app\build\outputs\apk\release\app-release.apk"
   ```
4. Mở ứng dụng và kiểm tra hoạt động:
   - Khởi chạy màn hình chính Bản đồ.
   - Tra cứu lộ trình tuyến xe buýt.
   - Bật theo dõi dự báo thời gian thực EBMS tại trạm dừng.
   - Thao tác lưu/bỏ lưu trạm và tuyến yêu thích.
