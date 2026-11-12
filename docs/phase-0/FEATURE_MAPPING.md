# ĐỐI CHIẾU TÍNH NĂNG: NGUỒN DỮ LIỆU vs ỨNG DỤNG MỤC TIÊU (FEATURE MAPPING)
**Dự án:** HCMC Bus Tracker  
**Tham chiếu trải nghiệm:** MultiGo (BusMap) & Cổng thông tin Buyttphcm.com.vn  
**Trạng thái:** VERIFIED & MAPPED  

---

## 1. MA TRẬN ĐỐI CHIẾU TÍNH NĂNG

Bảng đối chiếu giữa các tính năng công khai của **MultiGo**, khả năng đáp ứng của nguồn dữ liệu **buyttphcm.com.vn (EBMS API)**, và giải pháp kỹ thuật cho ứng dụng **Android**:

| STT | Tính năng mục tiêu | Trải nghiệm MultiGo | Khả năng từ API EBMS | Trạng thái kỹ thuật | Giải pháp kỹ thuật trên Android |
|---|---|---|---|---|---|
| **1** | **Dự báo thời gian đến trạm (ETA)** | Hiển thị số phút xe sắp đến trạm đang chờ | Cung cấp trường `t` (giây) tại `/prediction/predictbystopid` | `VERIFIED` | Hiển thị nhãn `ceil(t / 60)` phút, đổi màu nổi bật khi `< 1 phút`. |
| **2** | **Hiển thị biển số xe buýt** | Hiển thị biển kiểm soát (VD: `50E-206.51`) | Cung cấp trường `v` trong đối tượng `arrs` | `VERIFIED` | Format chuỗi biển số xe (VD: `50E20651` -> `50E-206.51`). |
| **3** | **Khoảng cách xe đến trạm** | Hiển thị khoảng cách theo mét hoặc kilomet | Cung cấp trường `d` (mét) trong đối tượng `arrs` | `VERIFIED` | Format `< 1000m` -> `"Xm"`, `>= 1000m` -> `"X.X km"`. |
| **4** | **Vận tốc tức thời của xe** | Hiển thị tốc độ hiện tại của xe (km/h) | Cung cấp trường `s` (km/h) từ GPS thiết bị | `VERIFIED` | Hiển thị nhãn `"X km/h"` kèm icon vận tốc. |
| **5** | **Biểu diễn xe di chuyển trên bản đồ** | Icon xe buýt di chuyển mượt mà trên bản đồ | API không trả trực tiếp `(lat, lng)` của xe | `PARTIALLY VERIFIED` | Sử dụng thuật toán chiếu ngược cự ly `d` trên Polyline của tuyến để tính tọa độ và góc xoay. |
| **6** | **Tra cứu danh mục tuyến buýt** | Tìm theo số hiệu tuyến (01, 102...) hoặc tên | Endpoint `/businfo/getroutesforcombo` | `VERIFIED` | Tìm kiếm tức thì không dấu từ Room Database cục bộ. |
| **7** | **Tra cứu thông tin chi tiết tuyến** | Giá vé, đơn vị quản lý, giờ hoạt động, tần suất | Endpoint `/businfo/getroutebyid/{id}` | `VERIFIED` | Màn hình chi tiết tuyến với đầy đủ thông số hoạt động. |
| **8** | **Xem lộ trình hai chiều trên bản đồ** | Vẽ đường đa tuyến lượt đi (xanh) và về (đỏ) | Endpoint `/businfo/getpathsbyvar/{rId}/{vId}` | `VERIFIED` | Vẽ Polyline chính xác qua MapLibre / Google Maps SDK. |
| **9** | **Danh sách trạm dừng theo tuyến** | Danh sách trạm dừng theo thứ tự từ A đến B | Endpoint `/businfo/getstopsbyvar/{rId}/{vId}` | `VERIFIED` | Hiển thị danh sách cuộn dọc có đánh số thứ tự trạm. |
| **10**| **Tra cứu trạm dừng xung quanh** | Tự động định vị GPS và hiển thị trạm gần | Endpoint `/businfo/getstopsinbounds/...` | `VERIFIED` | Lấy tọa độ GPS người dùng, tính bounding box và truy vấn trạm lân cận. |
| **11**| **Lịch trình xuất bến trong ngày** | Xem giờ chạy chi tiết của từng chuyến | Endpoint `/businfo/gettripsbytimetable` | `VERIFIED` | Bảng thời gian xe xuất bến từ 05:00 đến 22:00. |
| **12**| **Dự báo trạm tiếp theo của xe** | Bấm vào xe để xem thời gian đến các trạm sau | Endpoint `/prediction/.../predictnextstops` | `VERIFIED` | Mở rộng danh sách (Accordion/Sublist) hiển thị các trạm tiếp theo. |
| **13**| **Tìm đường đi bằng xe buýt** | Nhập điểm đi và đến, gợi ý tuyến cần đi | Endpoint `/pathfinding/getpathbystop/...` | `VERIFIED` | Hỗ trợ tìm chuyến xe kết nối giữa 2 trạm dừng. |
| **14**| **Thông báo khi xe sắp đến (Alert)** | Rung/chuông khi xe còn cách trạm < 500m | Tính toán cục bộ từ polling cự ly `d` và `t` | `FEASIBLE` | Android Foreground Service / Notification khi đang chờ trạm. |
| **15**| **Vé điện tử / Thẻ MultiPass** | Mua vé, nạp tiền, thanh toán QR/NFC | Không có trên cổng công cộng EBMS | `UNSUPPORTED` | **Ngoại phạm vi dự án** (tính năng thương mại riêng của đơn vị vận hành thẻ). |

---

## 2. PHÂN TÍCH SỰ KHÁC BIỆT GIỮA CÁC NGUỒN

### 2.1. Website Buyttphcm.com.vn
* **Ưu điểm:**
  - Dữ liệu gốc chính thống trực tiếp từ Trung tâm QLGTCC TP.HCM.
  - Phản ánh chính xác các điều chỉnh luồng tuyến xe buýt theo thời gian thực.
  - Cung cấp đầy đủ biểu đồ giờ xuất bến và thông tin chi tiết từng tuyến.
* **Hạn chế của bản web:**
  - Giao diện cũ, trải nghiệm tìm kiếm chưa tối ưu cho màn hình cảm ứng di động.
  - Chưa tận dụng dữ liệu cự ly `d` để vẽ trực quan icon xe di chuyển trên bản đồ.

### 2.2. Trải nghiệm mong muốn trên ứng dụng Android
* Khắc phục toàn bộ hạn chế của bản web:
  - Tải trước dữ liệu tĩnh (Offline-first) để tìm kiếm tuyến/trạm tức thì, mượt mà 60/120 FPS.
  - Tích hợp bản đồ hiện đại (MapLibre Native / Google Maps) hiển thị trạm xung quanh theo vị trí người dùng.
  - Vẽ lộ trình polyline sắc nét.
  - Mô phỏng vị trí xe buýt đang di chuyển trên lộ trình đến trạm bằng thuật toán nội suy cự ly.
  - Tự động làm mới dữ liệu dự báo mỗi 15 giây với trải nghiệm liền mạch không giật lag.
