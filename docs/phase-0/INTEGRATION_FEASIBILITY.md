# ĐÁNH GIÁ KHẢ NĂNG TÍCH HỢP HỆ THỐNG (INTEGRATION FEASIBILITY)
**Dự án:** HCMC Bus Tracker  
**Trạng thái phân tích:** VERIFIED  

Tài liệu này đánh giá tính khả thi kỹ thuật khi tích hợp trực tiếp nguồn dữ liệu `https://apicms.ebms.vn` vào ứng dụng di động Android độc lập.

---

## 1. TRẢ LỜI CHI TIẾT CÁC CÂU HỎI ĐÁNH GIÁ KỸ THUẬT

### 1.1. Có thể truy cập API trực tiếp từ Android không?
* **Khả năng:** **HOÀN TOÀN CÓ THỂ (100% FEASIBLE).**
* **Chi tiết kỹ thuật:**
  - API `https://apicms.ebms.vn` sử dụng giao thức HTTPS tiêu chuẩn với chứng chỉ số SSL hợp lệ do Cloudflare cấp.
  - Trên web browser có header `Access-Control-Allow-Origin: https://buyttphcm.com.vn` (CORS). Tuy nhiên, trên ứng dụng Android Native sử dụng `OkHttp` / `Retrofit`, cơ chế chính sách Same-Origin của trình duyệt **hoàn toàn không được áp dụng**.
  - Đã thực nghiệm gửi HTTP GET trực tiếp từ các HTTP Client độc lập không gắn kèm Origin Header, máy chủ trả về `200 OK` với dữ liệu JSON đầy đủ.
  - Không có yêu cầu Captcha, API Key, hay Token xác thực nào đối với các endpoint đọc dữ liệu.

---

### 1.2. Có cần xây dựng backend trung gian không?
* **Đánh giá kiến trúc:**
  - **Giai đoạn phát triển ban đầu (Phase 1 / MVP):** **KHÔNG CẦN THIẾT.**
    Ứng dụng Android có thể giao tiếp trực tiếp (Client-to-API) với `apicms.ebms.vn`. Điều này giúp tiết kiệm tối đa chi phí vận hành máy chủ, loại bỏ điểm nghẽn hạ tầng trung gian và đẩy nhanh tiến độ hoàn thiện ứng dụng.
  - **Giai đoạn mở rộng quy mô lớn (Production với >10.000 DAU):** **NÊN CÂN NHẮC PROXY BACKEND.**
    Một backend trung gian nhẹ (ví dụ Cloudflare Workers / Go Fiber / Node.js) sẽ mang lại các lợi ích:
    1. Tránh trường hợp máy chủ EBMS thay đổi cấu trúc endpoint gây gián đoạn app (Backend đóng vai trò Adapter).
    2. Cung cấp bộ đệm (In-memory Cache / Redis) cho các truy vấn tĩnh (danh sách tuyến, trạm) để giảm tải cho máy chủ thành phố.
    3. Hỗ trợ push notification thông minh khi người dùng đặt chuông báo xe đến.

---

### 1.3. Có thể lưu cache danh sách tuyến và trạm không?
* **Khả năng:** **CỰC KỲ KHẢ THI VÀ LÀ THIẾT KẾ BẮT BUỘC (HIGHLY RECOMMENDED).**
* **Lý do & Dữ liệu thực nghiệm:**
  - Toàn bộ mạng lưới TP.HCM có **~140 tuyến buýt** và **6.414 trạm dừng**.
  - Dữ liệu này mang tính chất tĩnh, rất ít thay đổi (chỉ điều chỉnh khi có phân luồng giao thông hoặc quy hoạch tuyến mới theo tuần/tháng).
  - Dung lượng tải về của toàn bộ danh mục trạm chỉ khoảng **726 KB**.
* **Giải pháp trên Android:**
  - Sử dụng **Room Database (SQLite)** trên Android để lưu trữ cục bộ toàn bộ danh sách tuyến, các hướng đi (variants), đa tuyến tọa độ (paths) và danh sách 6.414 trạm dừng.
  - Khi mở app: App hoạt động ở chế độ Offline-first (tìm kiếm trạm, tra cứu lộ trình tức thì mà không cần mạng).
  - Chỉ khi người dùng chọn một trạm cụ thể để xem xe sắp đến, app mới kích hoạt kết nối mạng để gọi Prediction API.
  - Tần suất cập nhật cache: Kiểm tra phiên bản dữ liệu 1 lần mỗi tuần hoặc cho phép người dùng bấm "Cập nhật dữ liệu mới" thủ công.

---

### 1.4. Dữ liệu vị trí xe cập nhật với tần suất nào?
* **Tần suất máy chủ EBMS tính toán:** Cập nhật liên tục theo các gói tin GPS gửi về từ xe buýt (khoảng 10s - 30s/gói tin).
* **Tần suất Polling khuyến nghị trên Android:** **15 giây/lần**.
  - Đây là tần suất được FPT và Trung tâm QLGTCC thiết kế chuẩn cho web portal.
  - Khoảng thời gian 15 giây là tối ưu: Đủ nhanh để người dùng quan sát xe tiến lại gần trạm, vừa đủ an toàn để không tạo ra tải đột biến (DDoS) lên máy chủ thành phố.

---

### 1.5. Có thể theo dõi một xe trên bản đồ không?
* **Khả năng:** **CÓ THỂ TRIỂN KHAI QUA THUẬT TOÁN NỘI SUY (FEASIBLE VIA INTERPOLATION).**
* **Bản chất kỹ thuật:**
  - API không cung cấp trực tiếp `(lat, lng)` của xe.
  - Tuy nhiên, API cung cấp: cự ly mét dọc tuyến `d`, tốc độ tức thời `s`, biển số xe `v`, mã hướng tuyến `v`, và chúng ta đã có sẵn polyline tọa độ chi tiết của hướng tuyến đó.
  - Bằng cách áp dụng giải thuật **Reverse Polyline Linear Referencing**, ứng dụng Android có thể xác định tọa độ tương ứng trên đa tuyến lộ trình và vẽ Marker xe buýt di chuyển mượt mà trên bản đồ.

---

### 1.6. Có thể dự báo thời gian xe đến một trạm cụ thể không?
* **Khả năng:** **XÁC MINH 100% (FULLY VERIFIED).**
* **Endpoint:** `GET /prediction/predictbystopid/{stopId}` hoặc `/prediction/predictbystopcode/{stopCode}`.
* Trả về chính xác:
  - Thời gian dự kiến đến trạm tính bằng giây (`t`).
  - Khoảng cách đến trạm tính bằng mét (`d`).
  - Biển số xe (`v`).
  - Tốc độ di chuyển hiện tại (`s`).
  - Hỗ trợ xem cả xe sắp đến gần nhất và các xe kế tiếp phía sau.

---

### 1.7. Có thể tìm xe theo tuyến không?
* **Khả năng:** **HOÀN TOÀN CÓ THỂ (FEASIBLE).**
* **Quy trình thực hiện:**
  - Người dùng chọn Tuyến (VD: Tuyến 01) -> chọn Hướng (Lượt đi: Ga Ba Son -> Bến xe buýt Chợ Lớn).
  - Ứng dụng lấy danh sách các trạm trên hướng tuyến đó (`/businfo/getstopsbyvar/{routeId}/{varId}`).
  - Bằng cách query dự báo tại các trạm then chốt dọc tuyến, ứng dụng có thể tổng hợp toàn bộ các xe đang vận hành trên tuyến đó và hiển thị toàn bộ đoàn xe trên bản đồ lộ trình của tuyến.

---

### 1.8. Có thể lấy toàn bộ dữ liệu hay chỉ dữ liệu theo từng truy vấn?
* **Dữ liệu tĩnh (Tuyến, Trạm, Lộ trình, Lịch biểu):**
  - **Lấy được toàn bộ (Bulk Download):**
    - `getroutesforcombo`: Lấy toàn bộ tuyến trong 1 request.
    - `getstopsforautocomplete`: Lấy toàn bộ 6.414 trạm trong 1 request.
    - `getpathsbyvar` & `getstopsbyvar`: Lấy theo từng hướng tuyến (có thể tải sẵn trong lần đầu cài app hoặc tải theo nhu cầu khi xem tuyến).
* **Dữ liệu động (Dự báo xe đến, vị trí xe):**
  - **Chỉ lấy theo từng truy vấn (Per-query On-demand):** Phải truyền mã trạm `stopId` hoặc `stopCode`. Đây là thiết kế hợp lý vì không hệ thống nào cho phép người dùng kéo vị trí toàn bộ hàng ngàn xe buýt thành phố cùng lúc về một máy khách.

---

### 1.9. Có giới hạn sử dụng hoặc điều kiện cấp phép nào cần lưu ý?
1. **Bảo vệ tầng mạng:** Máy chủ `apicms.ebms.vn` nằm sau Cloudflare. Nếu có hành vi gửi request quá nhanh (VD: vòng lặp polling < 2 giây hoặc quét ồ ạt hàng ngàn request một lúc), Cloudflare có thể kích hoạt Rate Limiting hoặc chặn IP bằng Cloudflare Challenge.
2. **Nguyên tắc thiết kế app (Good Citizen Policy):**
   - Không được phép spam request.
   - Luôn tuân thủ chu kỳ polling $\ge 15$ giây.
   - Tự động ngắt polling khi app ở chế độ nền (Background) hoặc tắt màn hình.
   - Không crawl tự động làm nghẽn server.
3. **Pháp lý và Điều khoản dịch vụ:**
   - Dữ liệu thuộc sở hữu của Trung tâm Quản lý Giao thông Công cộng TP.HCM.
   - Ứng dụng phát triển cần ghi rõ nguồn dữ liệu công khai từ Cổng thông tin Xe buýt TP.HCM (buyttphcm.com.vn / EBMS) và phục vụ mục đích tiện ích cộng đồng cho người dân tham gia giao thông công cộng.
