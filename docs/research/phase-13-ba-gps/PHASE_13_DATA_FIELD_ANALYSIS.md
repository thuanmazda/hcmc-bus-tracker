# BÁO CÁO PHÂN TÍCH TRƯỜNG DỮ LIỆU ĐỊNH VỊ (DATA FIELD ANALYSIS)
## TÀI LIỆU DỰ ÁN: HCMC BUS TRACKER – PHIÊN BẢN 1.1.0 (PHASE 13)

---

**Mã tài liệu:** `PHASE_13_DATA_FIELD_ANALYSIS.md`  
**Ngày thực hiện:** Ngày 13 tháng 11 năm 2026  
**Chủ thể khảo sát:** Nhóm Nghiên cứu Kỹ thuật & Kiến trúc Hệ thống (HCMC Bus Tracker Core Team)  
**Tiêu chuẩn đối chiếu:** Quy chuẩn kỹ thuật Quốc gia QCVN 31:2014/BGTVT về Thiết bị giám sát hành trình của xe ô tô  
**Phạm vi:** So sánh phân tích trường dữ liệu giữa cổng dữ liệu mở EBMS và hệ thống viễn thông BA GPS  

---

## 1. DANH MỤC TRƯỜNG DỮ LIỆU THỰC TẾ TRÊN NỀN TẢNG BA GPS

Căn cứ theo Quy chuẩn kỹ thuật quốc gia **QCVN 31:2014/BGTVT**, các quy định kỹ thuật của Bộ Giao thông Vận tải và giao thức bản tin truyền thông của thiết bị giám sát hành trình Bình Anh (BA-GPS), dữ liệu viễn thám phương tiện bao gồm các trường chuẩn hóa sau:

```
+-----------------------------------------------------------------------------------------+
|                    CẤU TRÚC BẢN TIN GIÁM SÁT HÀNH TRÌNH CHUẨN QCVN 31                   |
| [Header] + [Vehicle_ID] + [GPS_Coord (Lat, Lng)] + [Speed] + [Heading] +                |
| [GPS_Time] + [ACC_Status] + [Door/AC] + [Driver_RFID] + [Checksum]                      |
+-----------------------------------------------------------------------------------------+
```

### Bảng phân tích chi tiết từng trường dữ liệu viễn thông BA GPS:

| Tên trường dữ liệu | Kiểu dữ liệu | Mô tả kỹ thuật | Ví dụ minh họa (Đã ẩn danh) | Trạng thái xác minh |
| :--- | :--- | :--- | :--- | :---: |
| **Vehicle Identifier** (`PlateNumber` / `VehicleId`) | `String` | Biển số đăng ký phương tiện hoặc mã định danh xe trong hệ sinh thái FUTA. | `"51B-123.XX"` hoặc `"XE-FUTA-042"` | **ĐÃ XÁC MINH** (Hiển thị trực quan trên danh sách xe) |
| **Latitude** (`Lat`) | `Double` | Vĩ độ định vị địa lý theo hệ quy chiếu WGS-84, độ phân giải 6 chữ số thập phân. | `10.776889` | **ĐÃ XÁC MINH** (Hiện diện trong bản tin GPS của thiết bị) |
| **Longitude** (`Lng`) | `Double` | Kinh độ định vị địa lý theo hệ quy chiếu WGS-84, độ phân giải 6 chữ số thập phân. | `106.700806` | **ĐÃ XÁC MINH** (Hiện diện trong bản tin GPS của thiết bị) |
| **GPS Timestamp** (`GpsTime`) | `DateTime` / `Long` | Thời điểm chip GPS trên hộp đen xe ghi nhận tín hiệu vệ tinh (UTC+7). | `"2026-11-13 14:15:30"` | **ĐÃ XÁC MINH** (Bắt buộc theo QCVN 31) |
| **Server Timestamp** (`LogTime`) | `DateTime` / `Long` | Thời điểm máy chủ trung tâm Bình Anh tiếp nhận và giải mã bản tin từ modem GSM. | `"2026-11-13 14:15:33"` | **ĐÃ XÁC MINH** (Được lưu trong cơ sở dữ liệu server) |
| **Speed** (`SpeedKmH`) | `Double` / `Int` | Tốc độ tức thời của xe tính bằng km/h, đo từ cảm biến xung trục xe hoặc vận tốc suy diễn Doppler GPS. | `38.5` (km/h) | **ĐÃ XÁC MINH** (Hiển thị trên đồng hồ tốc độ web) |
| **Heading / Bearing** (`Angle`) | `Float` | Góc phương vị di chuyển so với hướng Bắc thực địa ($0^\circ \le \theta < 360^\circ$). | `142.5` (độ) | **ĐÃ XÁC MINH** (Dùng để xoay mũi tên icon trên bản đồ) |
| **GPS Status** (`GpsValid`) | `Boolean` / `Int` | Cờ trạng thái hợp lệ của tín hiệu vệ tinh (`1`: Fixed/Valid; `0`: No Fix/Invalid). | `1` (Hợp lệ) | **ĐÃ XÁC MINH** (Bắt buộc để loại bỏ tín hiệu mất sóng) |
| **Online Status** (`IsOnline`) | `Boolean` | Trạng thái kết nối viễn thông giữa thiết bị và server (Online, Idle, Offline). | `true` | **ĐÃ XÁC MINH** (Thể hiện bằng màu sắc xanh/vàng/đỏ) |
| **Update Interval** (`Frequency`) | `Integer` | Chu kỳ phát bản tin từ xe về máy chủ trung tâm (thường từ $10\text{s} - 30\text{s}$). | `15` (giây) | **ĐÃ XÁC MINH** (Theo cấu hình firmware hộp đen) |
| **Accuracy / HDOP** | `Float` | Hệ số suy giảm độ chính xác hình học mặt phẳng (Horizontal Dilution of Precision). | `1.2` | **CHƯA XÁC MINH TRÊN GIAO DIỆN** (Chỉ có trong log thô) |
| **Driver Identification** | `String` | Họ tên / Số giấy phép lái xe đọc từ thẻ định danh RFID tài xế. | `"NGUYEN VAN *"` | **CÓ TRÊN HỆ THỐNG - KHÔNG ĐƯỢC PHÉP THU THẬP** |

---

## 2. SO SÁNH ĐỐI CHIẾU TRƯỜNG DỮ LIỆU: EBMS VS. BA GPS

Bảng đối chiếu kỹ thuật giữa nguồn dữ liệu đang sử dụng (**EBMS**) và nguồn dữ liệu nghiên cứu bổ sung (**BA GPS**):

| Thuộc tính nghiệp vụ | Dữ liệu EBMS hiện tại (`apicms.ebms.vn`) | Dữ liệu BA GPS nghiên cứu (`futagps.binhanh.vn`) | Đánh giá giá trị kỹ thuật |
| :--- | :--- | :--- | :--- |
| **Biển số định danh** | Trường `v` (Ví dụ: `"51B-12345"`, dạng viết liền không dấu chấm). | Trường `PlateNumber` (Ví dụ: `"51B-123.45"`, có thể chứa dấu chấm hoặc gạch). | **Khớp được sau khi chuẩn hóa (Regex Normalize):** Loại bỏ dấu chấm, khoảng trắng, gạch ngang để so sánh mã chuỗi. |
| **Tọa độ vị trí địa lý** | **KHÔNG CÓ TRỰC TIẾP**. Chỉ có khoảng cách dọc tuyến `d` (mét) tính từ trạm đầu bến. | **CÓ TRỰC TIẾP** `(Lat, Lng)` theo WGS-84 đo từ vệ tinh GPS. | **BA GPS vượt trội hoàn toàn về định vị không gian thực.** Không phụ thuộc vào sai số nội suy hình học. |
| **Tốc độ vận hành** | Trường `s` (km/h). | Trường `SpeedKmH` (km/h) từ xung xe / GPS. | Tương đương; cả hai đều cung cấp vận tốc tức thời. |
| **Góc hướng di chuyển** | **KHÔNG CÓ**. Ứng dụng phải tự tính góc tiếp tuyến polyline giữa 2 điểm nội suy. | **CÓ TRỰC TIẾP** `Angle` ($0^\circ - 360^\circ$). | BA GPS cung cấp góc xoay thực tế của xe ngay cả khi xe đang chuyển làn hoặc rẽ vào ngã ba. |
| **Mốc thời gian GPS** | Trường `dts` (ISO string: `2026-11-13T14:10:00+07:00`). | Trường `GpsTime` (Thời gian ghi nhận tại hộp đen). | Tương đương về mặt kỹ thuật; cho phép đánh giá độ tươi (Freshness). |
| **Thuộc tính Tuyến & Biến thể** | **RẤT ĐẦY ĐỦ:** `RouteId`, `VarId`, `RouteNo`, lộ trình `RoutePath`. | **KHÔNG CÓ**. BA GPS là hệ thống quản lý xe thuần túy, không quản lý mô hình mạng lưới xe buýt đô thị của Sở GTVT. | **EBMS là nguồn duy nhất cung cấp ngữ cảnh tuyến buýt.** |
| **Trạm dừng & Dự báo ETA** | **RẤT ĐẦY ĐỦ:** `StopId`, danh sách trạm, thời gian dự kiến `t` (giây). | **HOÀN TOÀN KHÔNG CÓ.** | **EBMS là nguồn bắt buộc để tính thời gian xe đến trạm.** |

---

## 3. CÁC TRƯỜNG DỮ LIỆU TUYỆT ĐỐI BỊ CẤM HOẶC KHÔNG TỒN TẠI

Nhóm nghiên cứu tuân thủ nghiêm ngặt nguyên tắc: **Không suy diễn, không bịa đặt sự tồn tại của dữ liệu và tuân thủ pháp luật về quyền riêng tư:**

```
╔═══════════════════════════════════════════════════════════════════════════════════════════╗
║                      DANH MỤC DỮ LIỆU BỊ CẤM / KHÔNG THU THẬP                              ║
╠═══════════════════════════════════════════════════════════════════════════════════════════╣
║ 1. DỮ LIỆU ĐỊNH DANH TÀI XẾ (DRIVER PERSONAL IDENTITY):                                    ║
║    - Họ và tên tài xế, Số GPLX, Mã số thẻ RFID, Số điện thoại cá nhân của tài xế.        ║
║    => Căn cứ Nghị định 13/2023/NĐ-CP: Đây là Dữ liệu cá nhân. Ứng dụng theo dõi xe công    ║
║       cộng tuyệt đối KHÔNG ĐƯỢC PHÉP thu thập hoặc hiển thị thông tin người lái xe.       ║
║                                                                                           ║
║ 2. DỮ LIỆU HÌNH ẢNH CAMERA CABIN (DASHCAM / CABIN VIDEO):                                 ║
║    - Hình ảnh camera gương mặt tài xế và hành khách theo Nghị định 10.                    ║
║    => Bị cấm triệt để; không thuộc phạm vi theo dõi xe buýt công cộng.                   ║
║                                                                                           ║
║ 3. DỮ LIỆU NHIÊN LIỆU & CHI PHÍ NỘI BỘ (FUEL SENSORS & COMMERCIAL METRICS):              ║
║    - Lượng tiêu hao xăng dầu, điện áp bình ắc-quy, doanh thu chuyến xe.                   ║
║    => Đây là bí mật kinh doanh của đơn vị vận tải FUTA; không có giá trị phục vụ hành khách.║
╚═══════════════════════════════════════════════════════════════════════════════════════════╝
```

---

## 4. ĐỀ XUẤT MÔ HÌNH DỮ LIỆU CHUẨN HÓA DÀNH CHO V1.1.0

Để kết hợp hai nguồn dữ liệu mà không làm thay đổi các model hiện hữu của Phase 11 và Phase 12, đề xuất cấu trúc dữ liệu miền trung lập (Domain Neutral Model):

```kotlin
/**
 * Mô hình dữ liệu viễn thông thô nhận từ nguồn vệ tinh GPS trực tiếp.
 * Hoàn toàn không chứa thông tin cá nhân của người lái xe.
 */
data class RawVehicleGpsTelemetry(
    val normalizedPlate: String,      // Biển số đã chuẩn hóa (ví dụ: "51B12345")
    val latitude: Double,             // WGS-84 Latitude
    val longitude: Double,            // WGS-84 Longitude
    val speedKmH: Double,             // Tốc độ đo được
    val bearingDegrees: Float?,       // Hướng di chuyển (0..360)
    val gpsTimestampMillis: Long,     // Mốc thời gian GPS
    val serverReceivedMillis: Long,   // Mốc thời gian máy chủ nhận tin
    val isSignalValid: Boolean        // Tín hiệu GPS hợp lệ
)
```

**Kết luận chuyên môn:**
Dữ liệu BA GPS có cấu trúc trường kỹ thuật rất mạnh về vị trí địa lý tức thời `(latitude, longitude, bearing)`. Tuy nhiên, nguồn dữ liệu này **hoàn toàn mù về mặt ngữ cảnh giao thông công cộng** (không biết xe đang chạy tuyến buýt số mấy, hướng đi nào, cách trạm tiếp theo bao xa). Do đó, BA GPS không thể hoạt động độc lập mà bắt buộc phải ghép nối (fusion) với dữ liệu mạng lưới của EBMS.
