# DANH SÁCH CÂU HỎI MỞ & THÔNG TIN CẦN THỐNG NHẤT (OPEN QUESTIONS)
**Dự án:** HCMC Bus Tracker  
**Giai đoạn:** Kết thúc Phase 0 – Chuẩn bị cho Phase 1  

Dưới đây là các vấn đề kỹ thuật và quyết định thiết kế cần người dùng (Product Owner / Lead Developer) xem xét và phản hồi trước khi bắt đầu Phase 1:

---

## 1. VỀ DỮ LIỆU ĐỊNH VỊ PHƯƠNG TIỆN (VEHICLE GPS LOCATION)

* **Thực trạng từ kết quả phân tích:**
  - Nguồn dữ liệu `apicms.ebms.vn` cung cấp chính xác: Biển số xe (`v`), Vận tốc tức thời (`s` - km/h), Cự ly cách trạm (`d` - mét), Thời gian dự kiến đến trạm (`t` - giây) và Dấu thời gian GPS (`dts`).
  - Nguồn dữ liệu **KHÔNG** trả về cặp tọa độ trực tiếp `(Lat, Lng)` của chiếc xe buýt.
* **Giải pháp đề xuất của Architect:**
  - Sử dụng thuật toán **Reverse Polyline Linear Referencing (Tham chiếu cự ly ngược trên đa tuyến)** để nội suy ra tọa độ xe và góc xoay trên bản đồ.
* **Câu hỏi dành cho bạn:**
  1. *Bạn có đồng ý áp dụng giải pháp thuật toán nội suy này để hiển thị icon xe trên bản đồ không?*
  2. *Bạn có thông tin hoặc quyền truy cập nào vào nguồn API nội bộ khác (nếu có) của Sở GTVT TP.HCM cung cấp trực tiếp tọa độ Lat/Lng thô của xe không?*

---

## 2. VỀ LỰA CHỌN CÔNG NGHỆ BẢN ĐỒ (MAP SDK SELECTION)

* **Phương án A: MapLibre Native Android SDK (Khuyến nghị)**
  - *Ưu điểm:* Mã nguồn mở, hoàn toàn miễn phí, hỗ trợ nạp trực tiếp Tile Server OSM của FPT STIS (`https://map.stis.vn/bright/{z}/{x}/{y}.png`) hoặc OpenStreetMap, không phát sinh chi phí hàng tháng, không bị giới hạn hạn mức (Quota).
* **Phương án B: Google Maps SDK for Android**
  - *Ưu điểm:* Bản đồ Google quen thuộc, chất lượng hình ảnh và địa danh rất tốt tại Việt Nam.
  - *Nhược điểm:* Yêu cầu tài khoản Google Cloud, khai báo thẻ thanh toán quốc tế, phát sinh chi phí nếu vượt ngưỡng miễn phí hàng tháng ($200 credit).
* **Câu hỏi:**
  *Bạn muốn ưu tiên sử dụng MapLibre (miễn phí, độc lập) hay Google Maps SDK cho ứng dụng?*

---

## 3. VỀ BỘ GIAO DIỆN ANDROID (UI FRAMEWORK)

* **Phương án A: Jetpack Compose (Khuyến nghị)**
  - Công nghệ UI hiện đại tiêu chuẩn của Google, kiến trúc khai báo (Declarative UI), dễ dàng tạo animation mượt mà cho các thông số thời gian thực và icon xe di chuyển.
* **Phương án B: Android XML Views + ViewBinding**
  - Công nghệ truyền thống, quen thuộc với các dự án Android cũ.
* **Câu hỏi:**
  *Bạn muốn dự án được xây dựng hoàn toàn bằng Jetpack Compose hay XML Views?*

---

## 4. VỀ CHIẾN LƯỢC HẠ TẦNG (DIRECT vs PROXY BACKEND)

* **Đánh giá của Architect:**
  - Ở **Phase 1**, ứng dụng nên gọi trực tiếp đến `apicms.ebms.vn` để hoàn thành MVP nhanh nhất mà không cần duy trì chi phí server.
  - Ở giai đoạn sau, nếu ứng dụng phát hành rộng rãi có hàng chục ngàn người dùng, ta mới cần dựng backend trung gian để cache và giảm tải.
* **Câu hỏi:**
  *Bạn có đồng ý với phương án Client-to-API trực tiếp cho Phase 1 không?*

---

## 5. THÔNG TIN BỔ SUNG NẾU BẠN CÓ (OPTIONAL HAR CAPTURE)

* Nếu bạn đang có hoặc có thể capture file Network HAR từ ứng dụng di động MultiGo (hoặc BusMap) khi đang theo dõi xe, bạn có thể chia sẻ (đã loại bỏ thông tin cá nhân) để đội ngũ kiến trúc đối chiếu xem MultiGo có sử dụng thêm endpoint phụ nào khác ngoài cụm `ebms.vn` hay không.
