# DANH MỤC API ĐÃ XÁC MINH (API CATALOG)
## Backend Hệ thống Điều hành Xe buýt TP.HCM (EBMS)
**Base URL:** `https://apicms.ebms.vn`  
**Host kiểm tra:** `apicms.ebms.vn` (IP phân giải qua Cloudflare Anycast CDN)  
**Tiêu chuẩn phân loại:**
- `VERIFIED`: Đã kiểm chứng trực tiếp bằng request thực tế và nhận phản hồi 200 OK hợp lệ.
- `PARTIALLY VERIFIED`: Đã xác minh tồn tại nhưng chức năng phụ thuộc hoặc giới hạn định dạng.
- `UNVERIFIED`: Chưa có bằng chứng tồn tại.
- `UNAVAILABLE`: Đã kiểm tra nhưng không tồn tại (HTTP 404/500/Timeout) hoặc không được công khai.

---

## TỔNG HỢP DANH MỤC ENDPOINT

| Nhóm | Endpoint | Method | Phân loại | Mục đích |
|---|---|---|---|---|
| **A. Route** | `/businfo/getroutesforcombo` | GET | `VERIFIED` | Danh mục toàn bộ tuyến buýt (chuẩn Unicode tiếng Việt) |
| **A. Route** | `/businfo/getallroute` | GET | `VERIFIED` | Danh mục tuyến buýt (bản legacy, lỗi font một số ký tự) |
| **A. Route** | `/businfo/getroutebyid/{routeId}` | GET | `VERIFIED` | Thông tin chi tiết một tuyến (giá vé, thời gian, đơn vị...) |
| **A. Route** | `/businfo/getvarsbyroute/{routeId}` | GET | `VERIFIED` | Danh sách hướng tuyến (lượt đi / lượt về) |
| **A. Route** | `/businfo/getpathsbyvar/{routeId}/{varId}` | GET | `VERIFIED` | Tọa độ đa tuyến (polyline GPS) của lộ trình |
| **A. Route** | `/businfo/gettimetablebyroute/{routeId}` | GET | `VERIFIED` | Biểu đồ giờ, tần suất hoạt động theo hướng tuyến |
| **A. Route** | `/businfo/gettripsbytimetable/{routeId}/{timetableId}` | GET | `VERIFIED` | Danh sách các chuyến xe xuất bến trong ngày (giờ đi - đến) |
| **B. Stop** | `/businfo/getstopsforautocomplete` | GET | `VERIFIED` | Toàn bộ 6.414 trạm dừng phục vụ tìm kiếm nhanh (dạng mảng nén) |
| **B. Stop** | `/businfo/getstopsinbounds/{minLon}/{minLat}/{maxLon}/{maxLat}` | GET | `VERIFIED` | Danh sách trạm dừng nằm trong khung nhìn bản đồ |
| **B. Stop** | `/businfo/getstopsbyvar/{routeId}/{varId}` | GET | `VERIFIED` | Danh sách đầy đủ thông tin trạm dừng theo hướng tuyến (thứ tự 1..N) |
| **B. Stop** | `/businfo/getstopsbyroutevar/{routeId}/{varId}` | GET | `VERIFIED` | Danh sách rút gọn trạm dừng cho dropdown |
| **B. Stop** | `/businfo/getroutesthroughstop/{stopId}` | GET | `VERIFIED` | Danh sách các tuyến xe buýt đi ngang qua một trạm |
| **B. Stop** | `/businfo/getstopsforcombo` | GET | `UNAVAILABLE` | Trả về HTTP 404 (đã bị thay thế bởi `getstopsforautocomplete`) |
| **C. Vehicle** | Dedicated Vehicle Fleet API (Vị trí tức thời toàn đội xe) | GET/WS | `UNAVAILABLE` | Không có endpoint công khai danh sách tọa độ (Lat, Lng) của xe |
| **C. Vehicle** | Vehicle Telemetry in Prediction (Biển số, tốc độ, cự ly) | Embedded | `VERIFIED` | Tích hợp bên trong payload của Prediction API |
| **D. Prediction**| `/prediction/predictbystopid/{stopId}` | GET | `VERIFIED` | Dự báo thời gian, khoảng cách, tốc độ xe sắp đến trạm theo Stop ID |
| **D. Prediction**| `/prediction/predictbystopcode/{stopCode}` | GET | `VERIFIED` | Dự báo xe sắp đến trạm theo Mã số trạm (VD: `Q1 122`) |
| **D. Prediction**| `/prediction/{routeId}/{varId}/{stopId}/predictnextstops/{maxNextStops}` | GET | `VERIFIED` | Dự báo thời gian chiếc xe đó sẽ đến N trạm tiếp theo |
| **E. Realtime**| REST Polling (15s interval) | Polling | `VERIFIED` | Cơ chế cập nhật thời gian thực thực tế đang chạy trên web |
| **E. Realtime**| WebSocket / SSE / SignalR / MQTT | Streaming | `UNAVAILABLE` | Không tìm thấy bất kỳ socket hay stream kết nối nào |
| **F. Routing** | `/pathfinding/getpathbystop/{from}/{to}/{routeNumber}` | GET | `VERIFIED` | Tìm lộ trình di chuyển buýt giữa hai trạm hoặc hai tọa độ |

---

## CHI TIẾT CÁC ENDPOINT ĐÃ XÁC MINH

### NHÓM A: ROUTE API (TUYẾN XE BUÝT)

#### A.1. Danh sách tuyến xe buýt (`/businfo/getroutesforcombo`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/businfo/getroutesforcombo`
* **Headers:** Không yêu cầu (`Accept: application/json`)
* **Query/Body:** Không có.
* **HTTP Status:** `200 OK`
* **Content-Type:** `application/json; charset=utf-8`
* **Thời gian phản hồi:** ~400ms – 600ms
* **Cơ chế xác thực:** Không (Open Access)
* **Phân trang:** Không (Trả về toàn bộ ~140 tuyến đang vận hành)
* **Ghi chú quan trọng:** Trả về tiếng Việt chuẩn Unicode UTF-8 (Ưu tiên dùng endpoint này hơn `getallroute`).
* **Response mẫu (Rút gọn):**
```json
[
  {
    "value": "1",
    "lable": "[01] Ga Ba Son - Bến xe buýt Chợ Lớn",
    "key": 0
  },
  {
    "value": "3",
    "lable": "[03] Bến Thành - Thạnh Xuân",
    "key": 0
  },
  {
    "value": "102",
    "lable": "[65] Bến Thành - Cách Mạng Tháng Tám - Bến xe An Sương",
    "key": 0
  }
]
```

#### A.2. Chi tiết thông tin tuyến (`/businfo/getroutebyid/{routeId}`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/businfo/getroutebyid/{routeId}`
* **Tham số Path:** `routeId` (int) - ID của tuyến (VD: `1`).
* **HTTP Status:** `200 OK`
* **Content-Type:** `application/json; charset=utf-8`
* **Thời gian phản hồi:** ~350ms
* **Response mẫu:**
```json
{
  "RouteId": 1,
  "RouteNo": "01",
  "RouteName": "Ga Ba Son - Bến xe buýt Chợ Lớn",
  "Color": "#FFB07C",
  "Type": "Phổ thông - Có trợ giá",
  "Distance": 9620.0,
  "Orgs": "Công ty Cổ phần Xe khách Phương Trang FutaBusLines, ĐT: 1900638494<br/>",
  "TimeOfTrip": "40",
  "Headway": "8 - 20",
  "OperationTime": "05:00 - 22:00",
  "NumOfSeats": "40",
  "OutBoundName": "Bến xe buýt Chợ Lớn¿",
  "InBoundName": "Ga Ba Son¿",
  "OutBoundDescription": "Đường Nguyễn Siêu - Đường Ngô Văn Năm - Đường Tôn Đức Thắng - Đường Hàm Nghi - Đường Trần Hưng Đạo - Đường Nguyễn Tri Phương - Đường Trần Phú - Đường Trần Hưng Đạo - Đường Châu Văn Liêm - Đường Hải Thượng Lãn Ông - Đường Trang Tử - Bến xe buýt Chợ Lớn (Bến A)¿",
  "InBoundDescription": "Bến xe buýt Chợ Lớn (Bến A) - Đường Lê Quang Sung - Đường Nguyễn Hữu Thận - Đường Tháp Mười - Đường Hải Thượng Lãn Ông - Đường Châu Văn Liêm - Đường Nguyễn Trãi - Đường Huỳnh Mẫn Đạt - Đường Trần Hưng Đạo - Đường Hàm Nghi - Đường Hồ Tùng Mậu - Đường nhánh S2 - Đường Tôn Đức Thắng - Quay đầu tại giao lộ Tôn Đức Thắng - Đường Tôn Đức Thắng - Đường Nguyễn Siêu.¿",
  "TotalTrip": "216 [TPD]",
  "Tickets": "<br/>&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp- Vé lượt trợ giá: 5,000 VNĐ<br/>&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp- Vé lượt trợ giá HSSV: 3,000 VNĐ<br/>&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp- Vé tập: 112,500 VNĐ"
}
```

#### A.3. Danh sách hướng tuyến (Lượt đi / Lượt về) (`/businfo/getvarsbyroute/{routeId}`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/businfo/getvarsbyroute/{routeId}`
* **Tham số Path:** `routeId` (int) - ID tuyến.
* **HTTP Status:** `200 OK`
* **Response mẫu:**
```json
[
  {
    "RouteId": 1,
    "RouteVarId": 1,
    "RouteVarName": "Lượt đi: Điểm đầu cuối Nguyễn Siêu - Bến xe buýt Chợ Lớn",
    "RouteVarShortName": "Bến xe buýt Chợ Lớn",
    "RouteNo": "01",
    "StartStop": "Điểm đầu cuối Nguyễn Siêu",
    "EndStop": "Bến xe buýt Chợ Lớn",
    "Distance": 8561.0,
    "Outbound": true,
    "RunningTime": 40
  },
  {
    "RouteId": 1,
    "RouteVarId": 2,
    "RouteVarName": "Lượt về: Bến xe buýt Chợ Lớn - Điểm đầu cuối Nguyễn Siêu",
    "RouteVarShortName": "Ga Ba Son",
    "RouteNo": "01",
    "StartStop": "Bến xe buýt Chợ Lớn",
    "EndStop": "Điểm đầu cuối Nguyễn Siêu",
    "Distance": 10753.0,
    "Outbound": false,
    "RunningTime": 40
  }
]
```

#### A.4. Đa tuyến tọa độ GPS của lộ trình (`/businfo/getpathsbyvar/{routeId}/{varId}`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/businfo/getpathsbyvar/{routeId}/{varId}`
* **Tham số Path:** `routeId` (int), `varId` (int: 1 = lượt đi, 2 = lượt về).
* **HTTP Status:** `200 OK`
* **Response mẫu:**
```json
{
  "lat": [10.77994823, 10.78051281, 10.78026009, 10.78014946, 10.77980709, ...],
  "lng": [106.70639801, 106.70698547, 106.70748901, 106.7077179, 106.70761108, ...]
}
```

#### A.5. Biểu đồ giờ & Giờ hoạt động (`/businfo/gettimetablebyroute/{routeId}`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/businfo/gettimetablebyroute/{routeId}`
* **Response mẫu:**
```json
[
  {
    "RouteId": 1,
    "RouteVarId": 1,
    "TimeTableId": 1,
    "RouteVarShortName": "Bến xe buýt Chợ Lớn",
    "StartDate": "01/03/2026",
    "EndDate": "",
    "StartStop": "Điểm đầu cuối Nguyễn Siêu",
    "EndStop": "Bến xe buýt Chợ Lớn",
    "IsCurrent": true,
    "RunningTime": "40",
    "Headway": "8 - 20",
    "TotalTrip": 108,
    "OperationTime": "05:00 - 22:00",
    "ApplyDates": "T2, T3, T4, T5, T6, T7, CN"
  }
]
```

#### A.6. Chi tiết từng chuyến xuất bến trong ngày (`/businfo/gettripsbytimetable/{routeId}/{timetableId}`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/businfo/gettripsbytimetable/{routeId}/{timetableId}`
* **Response mẫu:**
```json
[
  { "RouteId": 1, "TripId": 2, "TimeTableId": 1, "StartTime": "05:00", "EndTime": "05:40" },
  { "RouteId": 1, "TripId": 3, "TimeTableId": 1, "StartTime": "05:15", "EndTime": "05:55" },
  { "RouteId": 1, "TripId": 4, "TimeTableId": 1, "StartTime": "05:30", "EndTime": "06:10" }
]
```

---

### NHÓM B: BUS STOP API (TRẠM DỪNG XE BUÝT)

#### B.1. Dữ liệu tìm kiếm nhanh toàn bộ trạm (`/businfo/getstopsforautocomplete`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/businfo/getstopsforautocomplete`
* **HTTP Status:** `200 OK`
* **Dung lượng:** ~726 KB (JSON nén 3 mảng song song để tối ưu truyền tải mạng)
* **Tổng số lượng trạm:** **6.414 trạm dừng**
* **Response mẫu:**
```json
{
  "l": ["DST D DST", "NbHH 385 CMTT", "BBD 21 TDT", ...],
  "v": ["[BX 01-1] Điểm đầu cuối Nguyễn Siêu (Đối diện The Nexus Nguyễn Siêu, Quận 1)", ...],
  "g": [9742, 1192, 7677, ...]
}
```
*Ghi chú:*
- `l[i]`: Chuỗi viết tắt/ký hiệu tìm kiếm không dấu.
- `v[i]`: Tên trạm dừng kèm số nhà, tên đường, quận.
- `g[i]`: Khóa định danh `StopId` của trạm.

#### B.2. Danh sách trạm theo khung tọa độ Bounding Box (`/businfo/getstopsinbounds/...`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/businfo/getstopsinbounds/{minLon}/{minLat}/{maxLon}/{maxLat}`
* **Tham số:** 4 góc tọa độ WGS84 dạng số thực phân cách bằng dấu gạch chéo `/`.
* **Ví dụ:** `GET https://apicms.ebms.vn/businfo/getstopsinbounds/106.69/10.76/106.71/10.78`
* **Response mẫu:**
```json
[
  {
    "StopId": 23,
    "Code": "Q1 134",
    "Name": "Hồ Hảo Hớn",
    "StopType": "Trụ dừng",
    "Zone": "Quận 1",
    "Ward": null,
    "AddressNo": "301F",
    "Street": "Trần Hưng Đạo",
    "SupportDisability": "",
    "Status": "Đang khai thác",
    "Lng": 106.690636,
    "Lat": 10.762395,
    "Search": "HHH 301F THD",
    "Routes": "01, 139, 152, DL03"
  }
]
```

#### B.3. Chi tiết trạm dừng trên một hướng tuyến (`/businfo/getstopsbyvar/{routeId}/{varId}`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/businfo/getstopsbyvar/{routeId}/{varId}`
* **Mô tả:** Trả về danh sách trạm theo đúng thứ tự đi qua của tuyến buýt từ bến đầu (A) đến bến cuối (B), đầy đủ tọa độ `Lat`, `Lng`, các tuyến giao cắt `Routes`.

#### B.4. Danh sách tuyến đi qua một trạm (`/businfo/getroutesthroughstop/{stopId}`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/businfo/getroutesthroughstop/{stopId}`
* **Ví dụ:** `GET https://apicms.ebms.vn/businfo/getroutesthroughstop/4223` (Trạm Hàm Nghi)
* **Response mẫu:**
```json
[
  { "RouteId": 1, "RouteNo": "01", "RouteName": "Ga Ba Son - Bến xe buýt Chợ Lớn" },
  { "RouteId": 13, "RouteNo": "102", "RouteName": "Bến xe buýt Sài Gòn - Nguyễn Văn Linh - Bến xe Miền Tây" },
  { "RouteId": 65, "RouteNo": "44", "RouteName": "Cảng Quận 4 - Bình Quới" }
]
```

---

### NHÓM C: VEHICLE API (THÔNG TIN PHƯƠNG TIỆN)

* **Trạng thái tổng thể:** `PARTIALLY VERIFIED`
1. **Endpoint tọa độ trực tiếp (`getvehicles` / `livefleet`):** `UNAVAILABLE` trên web công khai. Không có endpoint trả về `[ { vehicleId, lat, lng } ]` trên toàn hệ thống.
2. **Dữ liệu viễn thông xe nhúng trong Prediction:** `VERIFIED`. Thông tin xe đang vận hành thực tế được nhúng trong đối tượng `arrs` của Prediction API:
   - Biển kiểm soát (`v`): Chuỗi định danh phương tiện thực tế.
   - Vận tốc tức thời (`s`): Đơn vị km/h từ thiết bị định vị GPS hộp đen.
   - Khoảng cách đến trạm (`d`): Mét.
   - Thời gian truyền dữ liệu GPS (`dts`): Timestamp chuẩn ISO 8601 từ GPS thiết bị.

---

### NHÓM D: PREDICTION API (DỰ BÁO XE ĐẾN TRẠM)

#### D.1. Dự báo theo Stop ID (`/prediction/predictbystopid/{stopId}`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/prediction/predictbystopid/{stopId}`
* **Tham số Path:** `stopId` (int) - Khóa định danh của trạm (VD: `4223`).
* **Thời gian phản hồi:** ~300ms - 500ms
* **Response mẫu:**
```json
[
  {
    "arrs": [
      {
        "d": 1168.2146370208116,
        "dts": "2026-11-09T21:13:29+07:00",
        "s": 24.0,
        "sts": 0,
        "t": 250,
        "v": "50E20651"
      },
      {
        "d": 8561.88450933371,
        "dts": "2026-11-09T21:13:23+07:00",
        "s": 23.0,
        "sts": 0,
        "t": 1791,
        "v": "50E20658"
      }
    ],
    "r": 65,
    "rN": "Cảng Quận 4 - Bình Quới",
    "rNo": "44",
    "s": 4223,
    "sN": "Trạm Trung chuyển trên đường Hàm Nghi",
    "v": 130,
    "vN": "Cảng Quận 4"
  }
]
```

#### D.2. Dự báo theo Mã trạm (`/prediction/predictbystopcode/{stopCode}`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/prediction/predictbystopcode/{stopCode}`
* **Ví dụ:** `GET https://apicms.ebms.vn/prediction/predictbystopcode/Q1%20122`
* **Đặc tính:** Hoạt động tương đương với `predictbystopid` nhưng chấp nhận chuỗi mã bảng trạm hiển thị ngoài đường (thuận tiện cho việc nhập nhanh bằng mã quét hoặc gõ số trạm).

#### D.3. Dự báo các trạm kế tiếp (`/prediction/{routeId}/{varId}/{stopId}/predictnextstops/{maxNextStops}`)
* **Trạng thái:** `VERIFIED`
* **URL:** `GET https://apicms.ebms.vn/prediction/{routeId}/{varId}/{stopId}/predictnextstops/{maxNextStops}`
* **Ví dụ:** `GET https://apicms.ebms.vn/prediction/65/130/4223/predictnextstops/3`
* **Mục đích:** Khi người dùng theo dõi xe `50E20651`, API này tính toán luôn thời gian xe này sẽ lần lượt đến 3 trạm tiếp theo dọc lộ trình.
* **Response mẫu:**
```json
[
  {
    "arrs": [
      { "d": 2079.52, "dts": "2026-11-09T21:13:29+07:00", "s": 24.0, "sts": 0, "t": 422, "v": "50E20651" },
      { "d": 9473.19, "dts": "2026-11-09T21:13:23+07:00", "s": 23.0, "sts": 0, "t": 1963, "v": "50E20658" }
    ],
    "r": 65, "rN": "Cảng Quận 4 - Bình Quới", "rNo": "44", "s": 853, "sN": "Calmette", "v": 130, "vN": "Cảng Quận 4"
  },
  {
    "arrs": [
      { "d": 2860.18, "dts": "2026-11-09T21:13:29+07:00", "s": 24.0, "sts": 0, "t": 573, "v": "50E20651" }
    ],
    "r": 65, "rN": "Cảng Quận 4 - Bình Quới", "rNo": "44", "s": 1195, "sN": "Chùa Anh Linh", "v": 130, "vN": "Cảng Quận 4"
  }
]
```

---

### NHÓM E: REALTIME API & TẦN SUẤT CẬP NHẬT

* **Cơ chế:** **REST Polling** (`VERIFIED`).
* **Chu kỳ Polling:** 15.000 ms (15 giây).
* **Đánh giá Stream (WebSocket / SSE):** `UNAVAILABLE`. Không có socket gateway nào được phát hiện trong hệ thống frontend hay server header.
* **Độ trễ dữ liệu GPS (`dts`):** Thiết bị GPS trên xe gửi dữ liệu về máy chủ EBMS với tần suất dao động từ 10 đến 30 giây/lần.
