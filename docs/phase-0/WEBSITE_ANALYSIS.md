# BÁO CÁO PHÂN TÍCH WEBSITE & CÔNG NGHỆ NGUỒN DỮ LIỆU
## Nguồn phân tích: https://buyttphcm.com.vn/Prediction
**Dự án:** HCMC Bus Tracker  
**Trạng thái phân tích:** VERIFIED (Kiểm chứng trực tiếp trên mã nguồn và mạng truyền thông)  
**Thời điểm kiểm tra:** 09/11/2026  

---

## 1. TỔNG QUAN HỆ THỐNG MỤC TIÊU

Trang tra cứu dự báo xe buýt thuộc **Trung tâm Quản lý Giao thông Công cộng TP.HCM** (thuộc Sở GTVT TP.HCM), được xây dựng và bảo trì công nghệ bởi **Công ty Hệ thống Thông tin FPT (FPT Information System)**.

Hệ thống bao gồm hai thành phần web chính:
1. **Frontend Portal:** `https://buyttphcm.com.vn` (CMS cung cấp giao diện người dùng).
2. **Backend REST API:** `https://apicms.ebms.vn` (Hệ thống điều hành và thông tin xe buýt điện tử - *Electronic Bus Management System - EBMS*).

---

## 2. KIẾN TRÚC VÀ CÔNG NGHỆ FRONTEND

### 2.1. Nền tảng Máy chủ & CMS
* **CMS Platform:** **DotNetNuke (DNN) 7.x** (nền tảng CMS ASP.NET WebForms). Thể hiện qua các thẻ meta `MetaKeywords: DotNetNuke,DNN`, cấu trúc thư mục `/DesktopModules/`, `/Portals/0/`, cookie `dnn_IsMobile`, `.ASPXANONYMOUS`.
* **Server-side Environment:** **Microsoft ASP.NET 4.0.30319** chạy trên Microsoft IIS. Thể hiện qua header `X-AspNet-Version: 4.0.30319`, `X-Powered-By: ASP.NET`, và các resource handler `/Telerik.Web.UI.WebResource.axd`.
* **CDN & Edge Layer:** **Cloudflare** (HTTP/1.1 và HTTP/3, Dynamic Caching, Cloudflare Turnstile/Challenge Platform).
* **Module giao diện chính:** `/DesktopModules/iBus_Prediction/`.

### 2.2. Thư viện JavaScript & CSS
* **Core JS Framework:** **jQuery v1.9.1** (`/Resources/libraries/jQuery/01_09_01/jquery.js`).
* **UI Components:** **jQuery UI v1.11.03** (`/Resources/libraries/jQuery-UI/01_11_03/jquery-ui.js`):
  - `$("#tabs").tabs()`: Điều hướng tab Bản đồ, Theo điểm dừng, Theo tuyến.
  - `$("#txtStopCode").autocomplete()`: Tự động hoàn thành trạm dừng.
  - `$("#txtRouId").autocomplete()`: Tự động hoàn thành mã/tên tuyến xe.
  - `$("#accordion").accordion()`: Danh sách các hướng tuyến (lượt đi/về).
* **CSS Framework:** **Twitter Bootstrap 3.x** (tùy biến dưới tên `NewsTwo/bootstrap/bootstrap.ex.css` và `bootstrap.min.js`).
* **Icon Set:** **FontAwesome 4.5.0** (`font-awesome-4.5.0/css/font-awesome.min.css`).

### 2.3. Thư viện Bản đồ (Mapping Engine)
* **Bản đồ nền (Map Library):** **Leaflet.js** (`/Resources/Shared/scripts/cms/Leaflet/leaflet.js`).
* **Lớp Bản đồ tùy biến (FPT CMS Extension):**
  - `L.MapBase.js`: Lớp quản lý bản đồ cơ bản, quản lý zoom, tâm bản đồ, sự kiện di chuyển và tải trạm trong khung nhìn (`getstopsinbounds`).
  - `L.RouteMap.js`: Mở rộng hiển thị danh sách trạm (`showStops`), vẽ lộ trình đường đi (`showPath`), định vị trạm (`findStop`).
  - `L.MarkerStop.js`, `L.MarkerStopArround.js`: Quản lý Marker trạm xe buýt và popup kích hoạt dự báo.
  - `L.PolylineBase.js`: Vẽ đa tuyến lộ trình xe.
* **Map Tiles Server:**
  - Cấu hình trong `L.Config.js`: `https://map.stis.vn/bright/{z}/{x}/{y}.png`
  - Nhà cung cấp: FPT STIS (OpenStreetMap định dạng raster tile phóng độ sáng `bright`).
  - Điểm trung tâm mặc định: `LatLng(10.77079, 106.700152)` (Quận 1, TP.HCM), zoom cấp 16, ngưỡng zoom hiển thị trạm: 15.
* **Geocoding & Tìm kiếm địa chỉ:**
  - Tích hợp **Google Maps JavaScript API v3 (Places Library)** với API Key nhúng trực tiếp: `AIzaSyD1J1N5WAfHGtMZZyV7yMcC-PaM5JH0nso`.

---

## 3. CẤU TRÚC HTML & DOM CỦA TRANG DỰ BÁO

Giao diện trang `Prediction` được chia thành hai khối chức năng chính:

```
[div#tabs]
 ├── ul (Thanh chọn phương thức tìm kiếm)
 │    ├── #tabs-1: Tab "Bản đồ" (#map_canvas - kích thước 400px chiều cao)
 │    ├── #tabs-2: Tab "Theo điểm dừng" (Ô tìm kiếm #txtStopCode)
 │    └── #tabs-3: Tab "Theo tuyến" (#txtRouId -> #txtVarId -> #txtStopId)
 ├── div#predictToStop (Hiển thị tiêu đề trạm đang chọn)
 └── div#tabDetails (Khối kết quả dự báo)
      ├── #tabDetail-1: "Thông tin dự báo"
      │    ├── div#predictLoading (Spinner tải)
      │    ├── div#predictTimeAgo (Đếm giây làm mới: "Cập nhật cách đây X giây")
      │    └── table.predict-table (tbody#divResult: Danh sách xe sắp đến)
      └── #tabDetail-2: "Các tuyến đi qua"
           └── table.predict-table (tbody#divRoute: Tuyến số, Tên tuyến)
```

---

## 4. QUY TRÌNH TƯƠNG TÁC NGƯỜI DÙNG & TRIGGER REQUEST

Website cung cấp 3 luồng thao tác người dùng để xem dự báo xe đến trạm:

### Luồng 1: Tìm kiếm theo Điểm dừng (Tab 2)
1. Khi trang tải xong (`$(window).ready`), website tự động gọi:
   `GET https://apicms.ebms.vn/businfo/getstopsforautocomplete`
   Dữ liệu 6.414 trạm dừng toàn thành phố được tải về một lần và lưu vào biến toàn cục JavaScript `DATA_STOPS`.
2. Người dùng gõ từ khóa vào `#txtStopCode` (tên đường, mã trạm, địa chỉ).
3. jQuery UI Autocomplete thực hiện tìm kiếm cục bộ trên mảng `DATA_STOPS` (không gửi thêm request lên server).
4. Người dùng bấm nút **Dự báo** (`btnShow_Click()`):
   - Kích hoạt hàm `ShowPredict()`.
   - Gửi request: `GET https://apicms.ebms.vn/prediction/predictbystopid/{stopId}`.
   - Gửi request phụ: `GET https://apicms.ebms.vn/businfo/getroutesthroughstop/{stopId}` để liệt kê các tuyến khác qua trạm.
5. Thiết lập Timer tự động polling lại sau mỗi **15 giây** (`PREDICT_INTERVAL = 15000`).

### Luồng 2: Tìm kiếm theo Tuyến (Tab 3)
1. Khi trang tải, website gọi `GET https://apicms.ebms.vn/businfo/getroutesforcombo` để nạp danh mục tuyến vào `DATA_ROUTES`.
2. Người dùng gõ tìm số hiệu tuyến (VD: Tuyến 01) vào `#txtRouId`.
3. Khi chọn tuyến, trigger sự kiện gọi:
   `GET https://apicms.ebms.vn/businfo/getvarsbyroute/{routeId}` -> điền vào dropdown Hướng đi (`#txtVarId`).
4. Khi chọn Hướng đi (`txtVarId_OnChange`), trigger:
   `GET https://apicms.ebms.vn/businfo/getstopsbyroutevar/{routeId}/{varId}` -> điền vào dropdown Trạm dừng (`#txtStopId`).
5. Người dùng chọn trạm và bấm nút **Dự báo** -> kích hoạt `ShowPredict()` như Luồng 1.

### Luồng 3: Tìm kiếm trực quan trên Bản đồ (Tab 1)
1. Người dùng di chuyển hoặc phóng to bản đồ (`zoom >= 15`).
2. Bản đồ bắt sự kiện `moveend` và kích hoạt hàm `showStopsArroundMe`:
   `GET https://apicms.ebms.vn/businfo/getstopsinbounds/{minLon}/{minLat}/{maxLon}/{maxLat}`
3. Website hiển thị các icon trạm dừng (`L.markerStopArround`).
4. Người dùng click vào một icon trạm trên bản đồ -> popup mở ra có nút **Dự báo** gắn sự kiện `onclick="btnPredict_Click(stopId, name, address)"`.
5. Khi click nút Dự báo trong popup -> tự động kích hoạt `ShowPredict()` cho trạm đó.

### Luồng 4: Xem dự báo chi tiết cho các trạm kế tiếp (Sub-row Click)
1. Khi bảng kết quả hiển thị các xe sắp đến tại trạm đang chọn, người dùng click vào một dòng kết quả của một tuyến bất kỳ.
2. Trigger sự kiện `predictRowClick()` gửi request:
   `GET https://apicms.ebms.vn/prediction/{routeId}/{varId}/{stopId}/predictnextstops/3`
3. Bảng mở rộng (accordion child row) chèn ngay bên dưới, hiển thị 3 trạm tiếp theo cùng thời gian dự kiến mà chiếc xe đó sẽ đến các trạm kế tiếp.

---

## 5. CƠ CHẾ HIỂN THỊ DỰ BÁO XE ĐẾN TRẠM

Dữ liệu trả về từ API dự báo được format theo các quy tắc frontend sau:
* **Thời gian xe đến (`t` - đơn vị giây):**
  - Đổi sang phút: `displayTime = Math.ceil(time / 60)`.
  - Nếu `displayTime < 1`: hiển thị `<1 phút` kèm class CSS `approaching` (màu nổi bật xe đang cập trạm).
  - Ngược lại: hiển thị `X phút`.
* **Khoảng cách đến trạm (`d` - đơn vị mét):**
  - Nếu `< 1000m`: hiển thị `Xm` (ví dụ `520m`).
  - Nếu `>= 1000m`: hiển thị `(d / 1000).toFixed(1) km` (ví dụ `1.2km`, `10.5km`).
* **Vận tốc tức thời (`s` - đơn vị km/h):**
  - Làm tròn: `speed.toFixed() km/h` (ví dụ `24 km/h`).
* **Biển số xe (`v`):**
  - Hiển thị chuỗi biển kiểm soát (ví dụ `50E20651`, `51B26725`).
* **Đồng hồ đếm thời gian cập nhật:**
  - JavaScript duy trì bộ đếm `_predictTimeAgo` tăng mỗi 1 giây: `"Cập nhật cách đây {0} giây"`.
  - Mỗi khi chu kỳ 15 giây hoàn thành và có dữ liệu mới, bộ đếm được reset về 0 với chữ `"Mới cập nhật!"`.

---

## 6. ĐÁNH GIÁ CHUNG VỀ PHẦN MỀM NGUỒN

1. **Khả năng tiếp cận:** API backend `https://apicms.ebms.vn` hoạt động độc lập với frontend DNN. Mặc dù frontend là kiến trúc cũ (DNN 7 + jQuery), hệ thống REST API trả về dữ liệu JSON chuẩn, sạch và cực kỳ trực quan.
2. **Tính bảo mật:** API hiện tại **hoàn toàn mở**, không đòi hỏi token xác thực hay API key cho các truy vấn tra cứu và dự báo.
3. **Hiệu năng:** Tốc độ phản hồi của API rất tốt (300ms - 800ms) nhờ có Cloudflare CDN đặt trước và ASP.NET runtime xử lý query trực tiếp.
