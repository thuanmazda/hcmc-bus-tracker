# BẢNG KIỂM TRA PHÁP LÝ, QUYỀN HẠN VÀ TUÂN THỦ (PERMISSION & COMPLIANCE CHECKLIST)
## TÀI LIỆU DỰ ÁN: HCMC BUS TRACKER – PHIÊN BẢN 1.1.0 (PHASE 13)

---

**Mã tài liệu:** `PHASE_13_PERMISSION_AND_COMPLIANCE_CHECKLIST.md`  
**Ngày thực hiện:** Ngày 13 tháng 11 năm 2026  
**Chủ thể thẩm định:** Thanh tra Pháp lý & Kiểm toán Tuân thủ Kỹ thuật Số  
**Trạng thái pháp lý xác thực:**  
> ### ⚠️ CẢNH BÁO PHÁP LÝ ĐỎ:
> **CHƯA XÁC MINH QUYỀN TÍCH HỢP VÀ PHÂN PHỐI (UNVERIFIED INTEGRATION & REDISTRIBUTION RIGHTS)**  
> Việc một cá nhân hoặc đơn vị sở hữu tài khoản đăng nhập web giám sát đội xe **KHÔNG ĐỒNG NGHĨA** với việc có quyền pháp lý để trích xuất dữ liệu đó và phân phối lại công khai trong ứng dụng di động cho cộng đồng.

---

## 1. NGUYÊN TẮC PHÁP LÝ BẮT BUỘC

Trong hoạt động khai thác dữ liệu số và phát triển phần mềm di động, ranh giới giữa việc "sử dụng dịch vụ" và "khai thác/phân phối lại dữ liệu" được pháp luật phân định rất rạch ròi:

```
[QUYỀN SỬ DỤNG DỊCH VỤ WEB]             !=             [QUYỀN PHÂN PHỐI DỮ LIỆU ĐẾN BÊN THỨ 3]
(Đăng nhập vào xem xe cá nhân)                         (Trích xuất dữ liệu phát lại cho cộng đồng)
- Cấp cho chủ xe/đơn vị vận tải                       - Cần Thỏa thuận B2B / Giấy phép bản quyền
- Phạm vi nội bộ cá nhân / doanh nghiệp                - Ràng buộc trách nhiệm bảo mật và an toàn mạng
```

---

## 2. BẢNG KIỂM TRA CÁC ĐIỀU KIỆN PHÁP LÝ (COMPLIANCE CHECKLIST)

| Hạng mục kiểm tra | Nội dung chi tiết | Hiện trạng tại Phase 13 | Đánh giá & Rủi ro pháp lý |
| :--- | :--- | :---: | :--- |
| **Quyền truy cập giao diện** | Được cấp tài khoản hợp lệ để đăng nhập xem phương tiện trên web `futagps.binhanh.vn`. | **ĐÃ XÁC MINH** *(Có thông tin tài khoản hợp lệ)* | Người dùng chỉ có quyền xem trực quan trên trình duyệt được cấp phép. |
| **Quyền xuất dữ liệu báo cáo** | Quyền xuất tệp Excel/PDF phục vụ công tác thanh tra nội bộ của doanh nghiệp. | **ĐÃ XÁC MINH** *(Chức năng xuất báo cáo của hệ thống)* | Chỉ phục vụ lưu trữ nội bộ; không phải là quyền phát tán dữ liệu trực tuyến. |
| **Quyền kết nối API B2B tự động** | Được nhà cung cấp Bình Anh (BA GPS) cấp API Key / Secret Token chính thức. | ❌ **CHƯA CÓ** | Không có tài liệu API chính thức hoặc token được cấp phép riêng cho dự án. |
| **Điều khoản dịch vụ Bình Anh (ToS)** | Điều khoản của BA GPS có cho phép ứng dụng bên thứ ba cào dữ liệu tự động không? | ❌ **BỊ NGHIÊM CẤM** | Điều khoản dịch vụ chuẩn của các nhà mạng viễn thông nghiêm cấm hành vi scraping tự động làm quá tải hạ tầng. |
| **Thỏa thuận với FUTA Bus Lines** | Văn bản chấp thuận của Công ty CP Xe khách Phương Trang cho phép nhúng dữ liệu xe vào HCMC Bus Tracker. | ❌ **CHƯA CÓ VĂN BẢN** | Rủi ro bị khiếu nại vi phạm bản quyền dữ liệu vận hành nếu tự ý công bố. |
| **Thỏa thuận với Sở GTVT / Trung tâm QLGTCC (EBMS)** | Phối hợp hiển thị dữ liệu xe buýt thuộc các tuyến trợ giá của thành phố. | ⚠️ **CẦN ĐỒNG THUẬN** | Dữ liệu EBMS là dữ liệu công cộng mở; nhưng việc trộn lẫn với dữ liệu tư nhân cần đảm bảo tính minh bạch. |
| **Bảo vệ dữ liệu cá nhân tài xế (Nghị định 13/2023/NĐ-CP)** | Cam kết không thu thập họ tên, giấy phép lái xe, hình ảnh cabin tài xế. | ✅ **TUÂN THỦ HOÀN TOÀN** | Thiết kế kiến trúc đã chủ động lọc bỏ 100% trường dữ liệu cá nhân của lái xe. |
| **Chính sách Google Play (User Data Policy)** | Khai báo trung thực nguồn gốc dữ liệu và đảm bảo quyền phân phối hợp pháp. | ⚠️ **ĐIỀU KIỆN NGUY CƠ** | Google Play có thể từ chối phê duyệt bản cập nhật nếu bị bên thứ ba khiếu nại về việc sử dụng API không được ủy quyền. |

---

## 3. PHÂN TÍCH CHI TIẾT 5 CÂU HỎI PHÁP LÝ QUAN TRỌNG

### 3.1. Tài khoản được cấp quyền xem hay có cả quyền xuất và phân phối lại dữ liệu?
* **Kết luận thanh tra:** Tài khoản được cấp chỉ mang tính chất **Tài khoản Giám sát Người dùng Cuối (End-User Monitoring Account)**. Tài khoản này chỉ có quyền xem trực quan vị trí phương tiện trên giao diện bản đồ web của Bình Anh và xuất các báo cáo thống kê tĩnh nội bộ. Hoàn toàn **không bao hàm quyền thương mại hóa, nhúng vào ứng dụng di động bên thứ ba hoặc phân phối lại cho công chúng**.

### 3.2. Điều khoản sử dụng của BA GPS có cho phép tích hợp vào ứng dụng bên thứ ba không?
* **Kết luận thanh tra:** Theo thông lệ viễn thông và điều khoản dịch vụ của Công ty Bình Anh, hệ thống phần mềm web được thiết kế phục vụ trực tiếp cho người vận hành. Các hành vi can thiệp vào tầng web thông qua bot, script tự động, giả lập session hoặc trích xuất ViewState đều bị coi là hành vi sử dụng trái phép dịch vụ. Việc tích hợp chỉ được xem là hợp lệ khi thông qua **Giao thức Web Service chính thức do Bình Anh ký kết hợp đồng cung cấp dịch vụ dữ liệu**.

### 3.3. Có được phép sử dụng dữ liệu vị trí xe trong ứng dụng công khai không?
* **Kết luận thanh tra:** Dữ liệu vị trí xe buýt chạy tuyến vận tải hành khách công cộng có tính chất phục vụ cộng đồng. Tuy nhiên, luồng dữ liệu trích xuất từ hạ tầng giám sát hành trình của doanh nghiệp tư nhân (FUTA) là tài sản thông tin thuộc quyền quản lý của đơn vị vận tải. Nếu chưa có sự đồng ý của đơn vị sở hữu phương tiện, việc đưa tọa độ này lên ứng dụng công cộng tiềm ẩn rủi ro pháp lý về tranh chấp dữ liệu vận hành.

### 3.4. Có yêu cầu bắt buộc phải ký thỏa thuận với FUTA hoặc Bình Anh không?
* **Kết luận thanh tra:** **BẮT BUỘC PHẢI CÓ THỎA THUẬN BẰNG VĂN BẢN (MOU / API AGREEMENT)**.
  Trước khi phiên bản 1.1.0 phát hành tính năng định vị xe FUTA qua BA GPS ra kho ứng dụng Google Play hoặc GitHub Releases, Ban quản trị dự án bắt buộc phải đạt được một trong hai điều kiện:
  1. Văn bản đồng thuận của FUTA Bus Lines cho phép sử dụng dữ liệu giám sát hành trình của các xe buýt thuộc các tuyến trợ giá TP.HCM.
  2. Kênh API chính thức từ Công ty Bình Anh (BA GPS) có cấp API Key định danh riêng cho dự án HCMC Bus Tracker.

### 3.5. Có giới hạn về lưu trữ, hiển thị, phân phối hoặc thương mại hóa không?
* **Kết luận thanh tra:**
  - Không được phép lưu trữ vĩnh viễn (permanent storage) dữ liệu lịch sử hành trình chi tiết của xe ngoài phạm vi bộ nhớ đệm tạm thời (RAM cache) phục vụ hiển thị tức thời.
  - Không được phép thương mại hóa (bán lại dữ liệu vị trí, chèn quảng cáo trục lợi trên nguồn dữ liệu).
  - Ứng dụng phải giữ vững cam kết phi lợi nhuận phục vụ người dân thành phố.

---

## 4. BẢNG TIÊU CHÍ ĐIỀU KIỆN TIÊN QUYẾT TRƯỚC KHI TÍCH HỢP (GO / NO-GO CRITERIA)

Dự án chỉ được phép chuyển từ giai đoạn Nghiên cứu (Phase 13) sang giai đoạn Triển khai Code khi đáp ứng đầy đủ tất cả các dấu kiểm sau:

- [ ] Đã có văn bản ủy quyền hoặc thỏa thuận chia sẻ dữ liệu với đơn vị vận tải (FUTA).
- [ ] Đã được cung cấp tài liệu kỹ thuật và API Key chính thức từ nhà cung cấp BA GPS (thay vì cào màn hình web).
- [ ] Đã rà soát danh sách biển số xe thực tế để đảm bảo khớp 100% với danh mục tuyến xe buýt công cộng của EBMS.
- [ ] Hệ thống có cơ chế Fallback tự động 100% về EBMS mà không làm crash ứng dụng nếu nguồn BA GPS gặp sự cố.
- [ ] Đã hoàn thành đánh giá tác động quyền riêng tư theo Nghị định 13/2023/NĐ-CP (loại bỏ hoàn toàn dữ liệu tài xế).

**PHÁN QUYẾT PHÁP LÝ TẠI THỜI ĐIỂM HIỆN TẠI:**
Do chưa có văn bản thỏa thuận chính thức và chưa có API Key B2B, dự án được gắn trạng thái:  
🔴 **NO-GO FOR PRODUCTION INTEGRATION – DUY TRÌ TRẠNG THÁI NGHIÊN CỨU THUẦN TÚY.**
