# BÁO CÁO THẨM ĐỊNH PHÁP LÝ VÀ BẢN QUYỀN DỮ LIỆU (LEGAL & DATA LICENSE REVIEW)
## (PHASE 15 – LEGAL & COMPLIANCE REVIEW)

---

**Dự án:** HCMC Bus Tracker  
**Phiên bản mục tiêu:** `1.1.0`  
**Giai đoạn:** Thẩm định pháp lý và điều khoản bản quyền dữ liệu (Legal & License Audit)  
**Ngày thực hiện:** Ngày 13 tháng 11 năm 2026  
**Chủ thể thẩm định:** Thanh tra Pháp lý & Kiểm toán Tuân thủ Kỹ thuật Số  

---

## 1. PHÂN TÍCH TÍNH PHÁP LÝ VÀ BẢN QUYỀN CỦA TỪNG NGUỒN DỮ LIỆU

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                   MA TRẬN PHÁP LÝ VÀ GIẤY PHÉP SỬ DỤNG DỮ LIỆU TẠI TP.HCM              │
├───────────────────────┬─────────────────────────┬───────────────────┬──────────────────┤
│ Nguồn dữ liệu         │ Bản chất pháp lý        │ Giấy phép dữ liệu │ Quyền phát hành  │
├───────────────────────┼─────────────────────────┼───────────────────┼──────────────────┤
│ 1. EBMS Official      │ Cổng dữ liệu vận tải mở │ Open Public Data  │ ✅ HỢP PHÁP      │
│ 2. Cổng Open Data HCM │ Cổng dữ liệu hành chính │ Open Government   │ ✅ HỢP PHÁP      │
│ 3. MultiGo (Go Labs)  │ Sản phẩm nhà nước & DN  │ Proprietary / B2B │ ⚠️ CẦN THỎA THUẬN│
│ 4. Phenikaa (BusMap)  │ Tài sản số doanh nghiệp │ Commercial License│ ⚠️ CẦN TRẢ PHÍ   │
│ 5. BA GPS FUTA        │ Quản trị nội bộ nhà xe  │ Private Fleet ToS │ ❌ KHÔNG ĐƯỢC PHÉP│
└───────────────────────┴─────────────────────────┴───────────────────┴──────────────────┘
```

### 1.1. Nguồn EBMS (`apicms.ebms.vn`)
- **Cơ sở pháp lý:** Được thiết lập bởi Trung tâm Quản lý Giao thông công cộng TP.HCM nhằm phục vụ mục đích cung cấp thông tin mạng lưới xe buýt công cộng minh bạch cho hành khách và các nhà phát triển giải pháp giao thông đô thị.
- **Tính hợp pháp:** Ứng dụng HCMC Bus Tracker là ứng dụng nguồn mở, phi thương mại, không thu tiền người dùng, truy vấn trực tiếp cổng dữ liệu công cộng này mà không can thiệp, không phá hoại hạ tầng và không sửa đổi nội dung phản hồi của cơ quan quản lý.
- **Kết luận:** **Hoàn toàn hợp pháp** để tiếp tục sử dụng làm nguồn dữ liệu lõi cho ứng dụng phát hành ra cộng đồng.

### 1.2. Nguồn MultiGo (Go Labs / FPT IS)
- **Cơ sở pháp lý:** Hệ thống MultiGo là nền tảng ứng dụng tích hợp đa phương thức được phát triển theo hợp đồng dịch vụ công nghệ với cơ quan quản lý nhà nước.
- **Ràng buộc:** Mặc dù sử dụng chung cơ sở dữ liệu gốc của Trung tâm QLGTCC, nhưng tầng backend và API của MultiGo thuộc quyền sở hữu trí tuệ của Go Labs và FPT IS.
- **Kết luận:** Bất kỳ nỗ lực nào nhằm khai thác API của MultiGo mà không có **Biên bản Ghi nhớ (MOU) hoặc Văn bản thỏa thuận cấp quyền tích hợp** đều tiềm ẩn rủi ro vi phạm quyền tác giả đối với phần mềm và điều khoản dịch vụ (ToS).

### 1.3. Nguồn Phenikaa MaaS (BusMap)
- **Cơ sở pháp lý:** Dữ liệu bản đồ và thuật toán của Phenikaa MaaS là tài sản thương mại tư nhân.
- **Ràng buộc:** Phenikaa áp dụng chính sách cấp phép thương mại (Commercial Licensing Agreement). Việc tích hợp dữ liệu này vào ứng dụng bắt buộc phải qua hợp đồng kinh tế và thanh toán chi phí API theo định mức.
- **Kết luận:** Chưa phù hợp cho một dự án nguồn mở phi lợi nhuận nếu chưa có nguồn tài trợ kinh phí bản quyền.

### 1.4. Nguồn BA GPS (Bình Anh) & Các Doanh nghiệp Vận tải
- **Cơ sở pháp lý:** Dữ liệu giám sát hành trình của từng đầu xe do doanh nghiệp tư nhân (FUTA, SaigonBus) chi trả cước phí viễn thông cho nhà mạng Bình Anh/Vietmap phục vụ công tác thanh tra nội bộ.
- **Ràng buộc:** Điều khoản dịch vụ (Terms of Service) của BA GPS nghiêm cấm mọi hình thức bóc tách dữ liệu giao diện web (scraping), chia sẻ phiên đăng nhập cho bên thứ ba hoặc phân phối lại dữ liệu định vị ra công chúng khi chưa có thỏa thuận bằng văn bản.
- **Kết luận:** **TUYỆT ĐỐI KHÔNG ĐƯỢC PHÉP BÓC TÁCH TỰ ĐỘNG.**

---

## 2. TUÂN THỦ NGHỊ ĐỊNH 13/2023/NĐ-CP VÀ BẢO VỆ DỮ LIỆU TÀI XẾ

Căn cứ theo **Nghị định số 13/2023/NĐ-CP** về Bảo vệ dữ liệu cá nhân:
1. **Thông tin định danh người lái xe** (Họ tên tài xế, Số GPLX, Số điện thoại cá nhân, Mã thẻ quẹt RFID) là dữ liệu cá nhân thuộc quyền bảo vệ của pháp luật.
2. **Quy định bắt buộc:**
   - HCMC Bus Tracker **tuyệt đối KHÔNG thu thập, KHÔNG lưu trữ và KHÔNG hiển thị** bất kỳ thông tin nào liên quan đến nhân thân của người lái xe buýt.
   - Ứng dụng chỉ xử lý các thông số kỹ thuật thuần túy của phương tiện: Biển số xe vận tải công cộng, tốc độ, khoảng cách và thời gian dự kiến đến trạm.
   - Loại bỏ 100% hình ảnh camera cabin giám sát người lái xe theo Nghị định 10.

---

## 3. CHÍNH SÁCH GOOGLE PLAY USER DATA & KHUYẾN NGHỊ BẢO VỆ DỰ ÁN

Để đảm bảo gói cài đặt Release APK khi phát hành lên Google Play Store hoặc phân phối trên GitHub không bị khiếu nại bản quyền (DMCA) hoặc gỡ bỏ:
1. Giữ vững tuyên bố minh bạch nguồn gốc: Nguồn dữ liệu xe buýt được cung cấp từ Cổng dữ liệu vận tải công cộng EBMS thuộc Sở GTVT / Trung tâm QLGTCC TP.HCM.
2. Không nhúng các API key không rõ nguồn gốc hoặc token khai thác từ các ứng dụng khác.
3. Duy trì trạng thái tuân thủ đã được kiểm toán toàn diện tại tệp [LEGAL_REPORT.md](file:///e:/MultiGo%20Reworked/LEGAL_REPORT.md).
