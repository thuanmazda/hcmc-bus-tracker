# SO SÁNH ĐỐI CHIẾU DỮ LIỆU EBMS VÀ BA GPS (EBMS VS. BA GPS COMPARISON)
## TÀI LIỆU DỰ ÁN: HCMC BUS TRACKER – PHIÊN BẢN 1.1.0 (PHASE 13)

---

**Mã tài liệu:** `PHASE_13_EBMS_BA_GPS_COMPARISON.md`  
**Ngày thực hiện:** Ngày 13 tháng 11 năm 2026  
**Chủ thể phân tích:** Nhóm Nghiên cứu Kỹ thuật & Tích hợp Dữ liệu (HCMC Bus Tracker Core Team)  
**Mục tiêu:** Đánh giá tính tương thích, ưu nhược điểm và tiềm năng dung hợp (Data Fusion) giữa hai nguồn dữ liệu  

---

## 1. BẢNG SO SÁNH MA TRẬN NĂNG LỰC DỮ LIỆU TOÀN DIỆN

| Tiêu chí kỹ thuật & nghiệp vụ | Cổng Dữ liệu Mở EBMS (`apicms.ebms.vn`) | Hệ thống Quản trị BA GPS (`futagps.binhanh.vn`) | Đánh giá so sánh |
| :--- | :--- | :--- | :--- |
| **Bản chất hệ thống** | Cổng điều hành và dự báo vận tải công cộng đô thị của Sở GTVT TP.HCM. | Nền tảng giám sát hành trình và quản lý đội xe doanh nghiệp (Fleet Management). | Hai hệ thống phục vụ mục đích khác nhau nhưng bổ trợ cho nhau. |
| **Phạm vi phương tiện** | **Toàn bộ mạng lưới xe buýt TP.HCM** (~130 tuyến, >2.000 xe buýt thuộc mọi hợp tác xã). | **Chỉ giới hạn trong đội xe của FUTA Bus Lines** (hoặc danh sách xe tài khoản được cấp quyền). | EBMS có độ phủ toàn diện; BA GPS chỉ là tập con (subset). |
| **Tọa độ GPS trực tiếp `(Lat, Lng)`** | **Không có trực tiếp.** Phải nội suy từ khoảng cách `d` dọc theo polyline tuyến đường. | **Có trực tiếp từ chip vệ tinh** lắp trên từng đầu xe. | **BA GPS vượt trội về độ chính xác vị trí thực tế.** |
| **Ngữ cảnh Tuyến đường & Chiều đi** | **Rất chi tiết:** Biết chính xác `routeId`, `varId` (chiều đi/chiều về), số hiệu tuyến `routeNo`. | **Không có.** Hệ thống không biết xe đang chạy tuyến buýt công cộng số mấy hay chiều nào. | **EBMS là bắt buộc để xác định lộ trình.** |
| **Danh mục Trạm dừng & Lộ trình** | **Đầy đủ:** Danh sách trạm theo thứ tự, tọa độ trạm, hình học polyline tuyến. | **Hoàn toàn không có.** Không có khái niệm trạm dừng đón trả khách công cộng. | **EBMS là bắt buộc để quản lý trạm xe.** |
| **Dự báo thời gian đến trạm (ETA)** | **Có sẵn:** Phản hồi mốc thời gian ước lượng `t` (giây) cho từng trạm phía trước. | **Không có.** BA GPS không có mô hình tính toán ETA theo biểu đồ giờ chạy của Sở. | **EBMS là nguồn độc quyền về ETA.** |
| **Phương thức truy cập hiện tại** | Cổng REST API mở công khai không cần tài khoản bí mật (`HTTP GET`). | Giao diện WebForms yêu cầu đăng nhập mật khẩu/session; API B2B cần hợp đồng doanh nghiệp. | **EBMS ổn định và dễ truy cập hơn; BA GPS bị rào cản truy cập.** |
| **Chi phí & Pháp lý tích hợp** | Miễn phí, dữ liệu mở phục vụ người dân thành phố. | Dịch vụ thương mại có phí viễn thông; cần cấp quyền chia sẻ dữ liệu từ FUTA và Bình Anh. | Cần văn bản thỏa thuận pháp lý cho BA GPS. |

---

## 2. PHÂN TÍCH BA PHƯƠNG ÁN TÍCH HỢP CHO VERSION 1.1.0

Để trả lời bài toán thiết kế kiến trúc cho phiên bản 1.1.0, nhóm nghiên cứu xây dựng phân tích chi tiết 3 phương án kỹ thuật:

```
  +---------------------------------------------------------------------------------------+
  |                                BA PHƯƠNG ÁN KỸ THUẬT                                   |
  | [Phương án 1] BA GPS là nguồn tọa độ chính  ---> Bỏ nội suy EBMS                     |
  | [Phương án 2] Kết hợp đa nguồn (Fusion)     ---> Ghép tọa độ BA GPS + Ngữ cảnh EBMS  |
  | [Phương án 3] Dự phòng bền vững (Fallback) ---> Ưu tiên EBMS, nạp BA GPS có chọn lọc |
  +---------------------------------------------------------------------------------------+
```

---

### PHƯƠNG ÁN 1: BA GPS LÀ NGUỒN TỌA ĐỘ CHÍNH
* **Cơ chế hoạt động:**
  Ứng dụng truy vấn tọa độ GPS trực tiếp từ BA GPS cho tất cả các xe FUTA. Vị trí marker được vẽ thẳng từ tọa độ WGS-84 nhận được. Hệ thống EBMS chỉ đóng vai trò lấy thông tin tuyến, trạm và danh sách biển số.
* **Ưu điểm:**
  - Vị trí xe phản ánh chính xác vị trí vật lý tức thời trên mặt đất, không bị sai lệch nếu xe đi đường vòng tránh kẹt xe.
  - Nhận biết ngay lập tức góc quay đầu xe thông qua trường hướng di chuyển (`Bearing`).
* **Nhược điểm nghiêm trọng:**
  - Chỉ áp dụng được cho xe FUTA (chiếm một tỷ lệ trong mạng lưới xe buýt TP.HCM). Các tuyến xe buýt do HTX 19/5, SaigonBus, May 19th vận hành sẽ không có dữ liệu BA GPS.
  - Nếu xe chạy lệch tuyến hoặc đi bảo dưỡng trong gara bến xe, marker vẫn hiện lên bản đồ làm người dùng hiểu lầm xe đang đón khách.
  - Gây hiện tượng "xe chạy trên nóc nhà" do hiệu ứng hẻm vực đô thị (Multipath) nếu không nắn đường.
* **Điều kiện sử dụng:** Phải có API chính thức từ Bình Anh và xe bắt buộc phải có gắn hộp đen BA GPS hợp chuẩn.

---

### PHƯƠNG ÁN 2: DUNG HỢP DỮ LIỆU ĐA NGUỒN (POSITION DATA FUSION)
* **Cơ chế hoạt động:**
  Hệ thống chạy song song cả 2 luồng dữ liệu và ghép nối thông qua khóa định danh duy nhất là **Biển kiểm soát xe (Normalized Plate Number)**:
  1. EBMS cung cấp danh sách xe đang vận hành trên tuyến $R$, chiều $V$, cùng khoảng cách $d$ và thời gian ETA $t$.
  2. BA GPS cung cấp tọa độ thực địa $(Lat_{\text{raw}}, Lng_{\text{raw}})$ và góc hướng di chuyển $\theta$.
  3. **Bộ lọc kiểm chứng (Validation Gate):**
     - Tính khoảng cách hình học ngắn nhất từ $(Lat_{\text{raw}}, Lng_{\text{raw}})$ tới đa giác lộ trình `RoutePath` của tuyến.
     - **Nếu khoảng cách $\le 25\text{ mét}$:** Chấp nhận xe đang chạy đúng tuyến. Chiếu vuông góc tọa độ vào tim đường (Map-Matching) và xoay marker theo $\theta$.
     - **Nếu khoảng cách $> 25\text{ mét}$:** Đánh dấu xe đang đi sai lộ trình hoặc tín hiệu GPS bị trôi dạt nghiêm trọng; tự động chuyển sang vị trí nội suy từ khoảng cách $d$ của EBMS.
* **Ưu điểm:**
  - Kết hợp độ chính xác vật lý của GPS với độ tin cậy ngữ cảnh của EBMS.
  - Loại bỏ hoàn toàn lỗi xe chạy lạc đường do nhảy tọa độ vệ tinh.
  - Vẫn hiển thị trạm dừng và thời gian đến trạm chính xác cho hành khách.
* **Nhược điểm:**
  - Thuật toán ghép nối phức tạp hơn; tăng nhẹ tài nguyên tính toán trên thiết bị di động.
  - Cần đồng bộ chu kỳ polling giữa 2 máy chủ khác nhau.

---

### PHƯƠNG ÁN 3: DỰ PHÒNG BỀN VỮNG (STRICT FALLBACK ARCHITECTURE)
* **Cơ chế hoạt động:**
  Hệ thống tiếp tục duy trì **EBMS làm nguồn dữ liệu vị trí cốt lõi và mặc định 100%**. BA GPS chỉ được xem là một kênh tăng cường thứ cấp (Optional Enhancement Plugin):
  - Khi xe là xe FUTA **VÀ** có dữ liệu BA GPS hợp lệ ($\text{age} \le 30\text{s}$) **VÀ** đã xác thực quyền truy cập: Dùng tọa độ GPS đã nắn đường.
  - Trong mọi trường hợp khác (mất mạng BA GPS, xe thuộc hợp tác xã khác, token hết hạn, dữ liệu GPS cũ $> 120\text{s}$, hoặc người dùng tắt tính năng nâng cao): **Hệ thống tự động sử dụng 100% thuật toán nội suy EBMS của Phase 11 & Phase 12.**
* **Ưu điểm:**
  - **Đảm bảo tính sẵn sàng 100%:** Ứng dụng không bao giờ bị gián đoạn hay "mất dấu xe" nếu phía Bình Anh bảo trì máy chủ hoặc ngắt kết nối.
  - Bảo vệ toàn vẹn kiến trúc Phase 11 và Phase 12 đã kiểm thử nghiệm thu thành công.
  - Không gây phụ thuộc chết (hard-dependency) vào một nhà cung cấp thiết bị tư nhân.
* **Nhược điểm:**
  - Trải nghiệm giữa các xe có thể có sự khác biệt nhẹ về độ mượt nếu người dùng theo dõi cả xe FUTA lẫn xe HTX khác.

---

## 3. ĐÁNH GIÁ KHẢ NĂNG GHÉP NỐI BIỂN SỐ XE (PLATE MATCHING AUDIT)

Một thách thức kỹ thuật lớn trong việc ghép nối dữ liệu giữa EBMS và BA GPS là định dạng biển số xe:

```
[EBMS Format]         "51B12345" hoặc "51B-12345" (Chuỗi viết liền hoặc có 1 gạch)
[BA GPS Format]       "51B-123.45" hoặc "51B123.45" (Có dấu chấm phân cách biển 5 số)
[Chuẩn hóa thống nhất] "51B12345" (Loại bỏ toàn bộ ký tự không phải chữ và số, viết hoa)
```

### Hàm chuẩn hóa đề xuất (Kotlin Specification):
```kotlin
fun normalizePlateNumber(rawPlate: String): String {
    return rawPlate.uppercase()
        .replace(Regex("[^A-Z0-9]"), "")
}
```

* **Kết quả đối chiếu:** Sau khi chuẩn hóa qua hàm trên:
  - `"51B-123.45"` $\rightarrow$ `"51B12345"`
  - `"51B-12345"`  $\rightarrow$ `"51B12345"`
  - Cả hai nguồn dữ liệu đều quy về một khóa chuỗi đồng nhất, cho phép thực hiện phép băm `Map<String, Telemetry>` với độ phức tạp $O(1)$.

---

## 4. KẾT LUẬN VÀ LỰA CHỌN PHƯƠNG ÁN

1. **Tuyệt đối không chọn Phương án 1:** Phương án 1 phá vỡ tính phổ quát của ứng dụng công cộng và phụ thuộc rủi ro vào một nhà xe duy nhất.
2. **Khuyến nghị kiến trúc cho tương lai:** **Phương án 3 (Dự phòng bền vững kết hợp Phương án 2 khi đủ điều kiện)** là giải pháp duy nhất khả thi về mặt kỹ thuật, an toàn về mặt vận hành và hợp pháp về mặt quyền hạn.
3. **Quy tắc chuyển tiếp:** Nếu chưa có dữ liệu thực nghiệm diện rộng và chưa có hợp đồng API chính thức, dự án **bắt buộc giữ nguyên 100% cơ chế EBMS hiện tại**.
