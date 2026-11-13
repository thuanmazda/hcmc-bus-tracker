# DANH MỤC ĐẶC TẢ CÁC ĐIỂM CUỐI DỮ LIỆU (API ENDPOINT CATALOG)
## (PHASE 15 – API ENDPOINT CATALOG)

---

**Dự án:** HCMC Bus Tracker  
**Phiên bản mục tiêu:** `1.1.0`  
**Giai đoạn:** Khảo sát danh mục API / Đặc tả Schema (Research-only / API Catalog)  
**Ngày thực hiện:** Ngày 13 tháng 11 năm 2026  
**Chủ thể khảo sát:** Nhóm Nghiên cứu Kỹ thuật & Tích hợp Dữ liệu (HCMC Bus Tracker Core Team)  

---

## 1. TỔNG QUAN DANH MỤC API

Tài liệu này tổng hợp danh mục đặc tả kỹ thuật chi tiết của các endpoint từ nguồn dữ liệu công khai chính thức đang vận hành (**EBMS**) và các giao diện đối tác tiềm năng (**B2B APIs**), phục vụ việc chuẩn bị kiến trúc đa nguồn cho phiên bản `1.1.0`.

---

## 2. NGUỒN CỐT LÕI: HỆ THỐNG ĐIỀU HÀNH EBMS (`https://apicms.ebms.vn/`)

Hệ thống cung cấp dịch vụ web RESTful định dạng JSON, mở công khai, không yêu cầu xác thực API key cá nhân, cho phép truy vấn trực tiếp bằng phương thức HTTP `GET`.

### 2.1. Nhóm Endpoint Dự báo và Theo dõi Phương tiện (Vehicle & Prediction Group)

#### Endpoint A: Dự báo phương tiện theo Mã Trạm dừng (`predictbystopid`)
* **URL:** `GET https://apicms.ebms.vn/prediction/predictbystopid/{stopId}`
* **Mục đích:** Lấy danh sách các xe buýt đang di chuyển hướng về trạm dừng cụ thể (theo `stopId`).
* **Tham số:**
  - `stopId` (`Int`, Path Variable): Mã số định danh của trạm dừng trên hệ thống cơ sở dữ liệu thành phố.
* **Headers:**
  - `Accept: application/json`
  - `Cache-Control: no-cache`
* **Xác thực:** Không (Public / No Auth).
* **Định dạng phản hồi (Response Schema - JSON Array):**
```json
[
  {
    "routeId": 150,
    "varId": 1,
    "routeNo": "150",
    "stopId": 1234,
    "v": "51B-12345",
    "d": 450.0,
    "t": 120,
    "s": 28.5,
    "dts": "2026-11-13T14:30:15+07:00",
    "sts": 0
  }
]
```

#### Endpoint B: Dự báo phương tiện theo Mã số Trạm trên biển báo (`predictbystopcode`)
* **URL:** `GET https://apicms.ebms.vn/prediction/predictbystopcode/{stopCode}`
* **Mục đích:** Tra cứu danh sách xe buýt đang tới theo mã số trạm in trên biển báo vật lý ngoài thực địa.
* **Tham số:**
  - `stopCode` (`String`, Path Variable): Mã trạm (Ví dụ: `"BX01"`, `"Q1_002"`).
* **Xác thực:** Không (Public / No Auth).
* **Định dạng phản hồi:** Cùng cấu trúc dữ liệu với Endpoint A.

#### Endpoint C: Dự báo các trạm kế tiếp của tuyến (`predictnextstops`)
* **URL:** `GET https://apicms.ebms.vn/prediction/{routeId}/{varId}/{stopId}/predictnextstops/{maxNextStops}`
* **Mục đích:** Lấy danh sách dự báo thời gian xe đến các trạm kế tiếp dọc theo chiều tuyến.
* **Tham số:**
  - `routeId` (`Int`), `varId` (`Int`), `stopId` (`Int`), `maxNextStops` (`Int`, mặc định: 3).
* **Định dạng phản hồi:** Danh sách các bản ghi dự báo tương ứng với các trạm lân cận.

---

### 2.2. Nhóm Endpoint Dữ liệu Tĩnh Mạng lưới Tuyến và Trạm (Static Transit Data)

| Endpoint Path | Phương thức | Tham số | Nội dung trả về |
| :--- | :---: | :--- | :--- |
| `/businfo/getroutesforcombo` | `GET` | Không | Danh mục toàn bộ các tuyến xe buýt (`RouteComboDto`). |
| `/businfo/getroutebyid/{routeId}` | `GET` | `routeId` (`Int`) | Thông tin chi tiết: cự ly, thời gian hoạt động, giá vé. |
| `/businfo/getvarsbyroute/{routeId}` | `GET` | `routeId` (`Int`) | Danh sách biến thể (Chiều đi `varId = 1`, Chiều về `varId = 2`). |
| `/businfo/getpathsbyvar/{routeId}/{varId}` | `GET` | `routeId`, `varId` | Chuỗi tọa độ polyline lộ trình (`lat`, `lng`). |
| `/businfo/getstopsbyvar/{routeId}/{varId}` | `GET` | `routeId`, `varId` | Danh sách các trạm dừng tuần tự dọc theo chiều đi/về. |
| `/businfo/getstopsinbounds/{minLon}/{minLat}/{maxLon}/{maxLat}` | `GET` | 4 góc khung nhìn | Toàn bộ trạm dừng nằm trong khung nhìn bản đồ. |

---

## 3. BẢNG PHÂN TÍCH CHI TIẾT CÁC TRƯỜNG DỮ LIỆU ĐỊNH VỊ (DATA FIELD SPECIFICATION)

Khảo sát và xác minh tính hiện diện của 16 trường dữ liệu cốt lõi trên nguồn EBMS:

| Tên trường chuẩn hóa | Tên trường trên EBMS | Kiểu dữ liệu | Đơn vị đo | Cho phép Null | Trạng thái thực tế | Ghi chú kỹ thuật |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **Vehicle ID** | Tự sinh từ `v` | `String` | - | Không | **CÓ** | Sử dụng biển số chuẩn hóa làm Vehicle ID duy nhất. |
| **Plate Number** | `v` | `String` | - | Có (hiếm) | **CÓ** | Ví dụ: `"51B-12345"`, `"50F00123"`. |
| **Route ID** | `routeId` | `Integer` | - | Không | **CÓ** | Khóa định danh tuyến (1..300). |
| **Trip ID** | `tripId` / `tid` | `Integer` | - | Có | **THIẾU** | Không xuất hiện thường xuyên trong dự báo tức thời. |
| **Latitude** | Không có | `Double` | Độ | Có | ❌ **KHÔNG** | **EBMS không trả về tọa độ WGS-84 trực tiếp.** |
| **Longitude** | Không có | `Double` | Độ | Có | ❌ **KHÔNG** | Phải nội suy từ khoảng cách $d$ dọc theo polyline. |
| **Speed** | `s` | `Double` | km/h | Không | **CÓ** | Tốc độ tức thời của xe tại thời điểm đo. |
| **Bearing** | Không có | `Float` | Độ ($0-360^\circ$) | Có | ❌ **KHÔNG** | Phải tính từ tiếp tuyến đa giác polyline. |
| **GPS Timestamp** | `dts` | `String` (ISO 8601)| UTC+7 | Có | **CÓ** | Thời điểm hộp đen xe ghi nhận tín hiệu vệ tinh. |
| **Server Timestamp** | Response Header `Date` | `HTTP-Date` | GMT | Không | **CÓ** | Thời điểm máy chủ web EBMS phản hồi request. |
| **Status** | `sts` | `Integer` | Enum | Không | **CÓ** | `0`: Đang chạy bình thường; `1`: Chậm giờ; khác: Lệch tuyến. |
| **Operator** | Tra cứu từ `routeId` | `String` | - | Không | **GIÁN TIẾP** | Tra từ danh mục tuyến (SaigonBus, FUTA, HTX 19/5...). |
| **Next Stop** | `stopId` | `Integer` | - | Không | **CÓ** | Trạm dừng tiếp theo mà xe đang di chuyển tới. |
| **Distance to Stop** | `d` | `Double` | Mét (m) | Không | **CÓ** | Khoảng cách hình học còn lại tới trạm kiểm soát. |
| **ETA** | `t` | `Integer` | Giây (s) | Không | **CÓ** | Thời gian dự kiến xe sẽ tiếp cận trạm dừng. |
| **Data Source** | Hằng số hệ thống | `String` | - | Không | **CÓ** | Gán nhãn cứng `EBMS_OFFICIAL` để bảo đảm nguồn gốc. |

---

## 4. ĐÁNH GIÁ CHỈ SỐ VẬN HÀNH MẠNG (NETWORK RUNTIME PROFILE)

| Chỉ số kỹ thuật | Giá trị quan trắc thực tế | Đánh giá vận hành |
| :--- | :---: | :--- |
| **Giao thức mạng** | HTTP / HTTPS (Cổng 80 / 443) | Hỗ trợ kết nối chuẩn Android OkHttp 4. |
| **HTTP Status phổ biến** | `200 OK`, `404 Not Found`, `500 Server Error` | Xử lý an toàn qua khối `SafeApiCall` bọc `Result<T>`. |
| **Thời gian phản hồi (Latency P50)** | **$180\text{ ms} - 350\text{ ms}$** | Tốc độ phản hồi tốt trong mạng nội địa Việt Nam. |
| **Thời gian phản hồi (Latency P95)** | **$650\text{ ms} - 1.200\text{ ms}$** | Chấp nhận được; kích hoạt timeout an toàn ở mức 15 giây. |
| **Kích thước gói tin (Payload Size)** | $1.2\text{ KB} - 18\text{ KB}$ (cho dự báo trạm) | Dung lượng nhẹ, tiết kiệm băng thông 3G/4G của người dùng. |
| **Tần suất Polling an toàn** | **$10\text{s} - 15\text{s}$ / lần truy vấn** | Đã được chuẩn hóa qua `GlobalPollingCoordinator`. |
| **Hạn mức giới hạn (Rate Limit)** | Không công bố giới hạn cứng; tự điều tiết | Không nên gửi request đồng thời quá $15\text{ req/s}$ từ 1 IP. |
| **Chính sách Cache** | Static data: 24h; Realtime: `no-cache` | Áp dụng đúng chuẩn giao vận HTTP Cache. |

---

## 5. ĐẶC TẢ GIAO DIỆN CỦA CÁC ĐỐI TÁC TIỀM NĂNG (B2B APIS)

### 5.1. MultiGo B2B Integration Interface (Go Labs)
* **Kênh kết nối dự kiến:** REST API / WebSocket.
* **Yêu cầu xác thực:** `Authorization: Bearer <Partner_Token>`.
* **Khả năng cung cấp:** Danh sách xe buýt đang hoạt động kèm tọa độ `(lat, lng)` từ hệ thống điều hành tập trung của Trung tâm QLGTCC.
* **Tình trạng:** Cần ký Biên bản Ghi nhớ (MOU) hợp tác chia sẻ dữ liệu với đơn vị chủ quản.

### 5.2. Phenikaa MaaS (BusMap B2B API)
* **Kênh kết nối dự kiến:** `https://api.phenikaamaas.com/v1/...`
* **Yêu cầu xác thực:** `X-API-KEY: <Enterprise_Key>`.
* **Khả năng cung cấp:** Tọa độ xe, lộ trình nắn đường chuyên sâu, trạm dừng trên nền tảng bản đồ số độc quyền của Phenikaa.
* **Tình trạng:** Dịch vụ có tính phí thương mại theo dung lượng truy vấn.
