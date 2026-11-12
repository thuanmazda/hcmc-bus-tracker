# CẤU TRÚC DỮ LIỆU & QUAN HỆ THỰC THỂ (DATA SCHEMA)
**Dự án:** HCMC Bus Tracker  
**Trạng thái phân tích:** VERIFIED  
**Mục tiêu:** Định nghĩa mô hình thực thể (Entities/DTOs) chuẩn bị cho việc xây dựng Model trên Android (Kotlin Data Classes / Room Entities).

---

## 1. BIỂU ĐỒ QUAN HỆ DỮ LIỆU (ENTITY RELATIONSHIP)

```
+--------------------+           1..N           +--------------------+
|       Route        | -----------------------> |      RouteVar      |
| (Tuyến xe buýt)    |                          |  (Hướng Lượt đi/về)|
+--------------------+                          +--------------------+
          |                                               |
          | 1..N                                          | 1..N
          v                                               v
+--------------------+           1..N           +--------------------+
|     Timetable      | -----------------------> |    RouteVarStop    |
|   (Biểu đồ giờ)    |                          | (Thứ tự trạm dừng) |
+--------------------+                          +--------------------+
          |                                               |
          | 1..N                                          | N..1
          v                                               v
+--------------------+                          +--------------------+
|       Trip         |                          |      BusStop       |
|  (Chuyến xuất bến) |                          | (Điểm dừng thực tế)|
+--------------------+                          +--------------------+
                                                          |
                                                          | 1..N
                                                          v
                                                +--------------------+
                                                |     Prediction     |
                                                | (Dự báo xe đến)    |
                                                +--------------------+
                                                          |
                                                          | 1..N
                                                          v
                                                +--------------------+
                                                |   ArrivalVehicle   |
                                                | (Xe đang di chuyển)|
                                                +--------------------+
```

---

## 2. CHI TIẾT CÁC SCHEMA DỮ LIỆU

### 2.1. RouteComboItem (Danh mục tuyến rút gọn)
* **Nguồn API:** `/businfo/getroutesforcombo`
* **Mục đích:** Danh mục tìm kiếm nhanh cho autocomplete, hiển thị danh sách toàn bộ tuyến.

| Tên trường | Kiểu dữ liệu | Bắt buộc | Giá trị mẫu | Ý nghĩa & Mô tả |
|---|---|---|---|---|
| `value` | `String` | Có | `"1"` | Mã định danh nội bộ (`RouteId`) của tuyến (chuyển sang `Int` khi parse). |
| `lable` | `String` | Có | `"[01] Ga Ba Son - Bến xe buýt Chợ Lớn"` | Chuỗi hiển thị gồm số hiệu và tên điểm đầu - cuối tuyến. |
| `key` | `Int` | Có | `0` | Luôn trả về 0 (thuộc tính thừa từ ASP.NET Web API). |

---

### 2.2. RouteDetail (Chi tiết tuyến đầy đủ)
* **Nguồn API:** `/businfo/getroutebyid/{routeId}`
* **Mục đích:** Màn hình thông tin chi tiết tuyến, hiển thị giá vé, đơn vị vận hành, lộ trình text.

| Tên trường | Kiểu dữ liệu | Bắt buộc | Giá trị mẫu | Ý nghĩa & Mô tả |
|---|---|---|---|---|
| `RouteId` | `Int` | Có | `1` | Mã định danh tuyến (Primary Key). |
| `RouteNo` | `String` | Có | `"01"`, `"08_TC"`, `"D4"` | Số hiệu tuyến hiển thị trên xe buýt. |
| `RouteName` | `String` | Có | `"Ga Ba Son - Bến xe buýt Chợ Lớn"` | Tên hành trình (Điểm đầu - Điểm cuối). |
| `Color` | `String` | Không | `"#FFB07C"` | Mã màu hex đại diện cho tuyến trên bản đồ. |
| `Type` | `String` | Không | `"Phổ thông - Có trợ giá"` | Phân loại tuyến buýt. |
| `Distance` | `Double` | Không | `9620.0` | Cự ly tuyến (đơn vị: mét). |
| `Orgs` | `String` | Không | `"Công ty CP Xe khách Phương Trang..."` | Tên đơn vị đảm nhận vận chuyển và hotline (có thể kèm thẻ `<br/>`). |
| `TimeOfTrip` | `String` | Không | `"40"` | Thời gian chạy một lượt (đơn vị: phút). |
| `Headway` | `String` | Không | `"8 - 20"` | Giãn cách chuyến (đơn vị: phút). |
| `OperationTime`| `String` | Không | `"05:00 - 22:00"` | Khung giờ hoạt động trong ngày. |
| `NumOfSeats` | `String` | Không | `"40"` | Sức chứa hoặc số chỗ ngồi trên xe. |
| `OutBoundName` | `String` | Không | `"Bến xe buýt Chợ Lớn¿"` | Tên hướng lượt đi (kèm ký tự phân tách `¿`). |
| `InBoundName` | `String` | Không | `"Ga Ba Son¿"` | Tên hướng lượt về. |
| `OutBoundDescription` | `String` | Không | `"Đường Nguyễn Siêu - Ngô Văn Năm..."`| Mô tả chi tiết danh sách đường đi lượt đi. |
| `InBoundDescription` | `String` | Không | `"Bến xe buýt Chợ Lớn - Lê Quang Sung..."`| Mô tả chi tiết danh sách đường đi lượt về. |
| `TotalTrip` | `String` | Không | `"216 [TPD]"` | Tổng số chuyến vận hành trong ngày (`TPD` = Trips Per Day). |
| `Tickets` | `String` | Không | `"<br/>- Vé lượt: 5,000 VNĐ..."` | Đoạn mã HTML liệt kê bảng giá vé các đối tượng. |

---

### 2.3. RouteVariant (Hướng tuyến Lượt đi / Lượt về)
* **Nguồn API:** `/businfo/getvarsbyroute/{routeId}`
* **Mục đích:** Phân biệt hai chiều chạy độc lập của cùng một tuyến buýt.

| Tên trường | Kiểu dữ liệu | Bắt buộc | Giá trị mẫu | Ý nghĩa & Mô tả |
|---|---|---|---|---|
| `RouteId` | `Int` | Có | `1` | ID tuyến xe buýt. |
| `RouteVarId` | `Int` | Có | `1` (Đi) hoặc `2` (Về) | Mã hướng tuyến (1 = Outbound, 2 = Inbound). |
| `RouteVarName` | `String` | Có | `"Lượt đi: Điểm đầu cuối Nguyễn Siêu - Bến xe buýt Chợ Lớn"` | Tên đầy đủ hướng di chuyển. |
| `RouteVarShortName` | `String` | Có | `"Bến xe buýt Chợ Lớn"` | Tên đích đến ngắn gọn hiển thị trên bảng điện tử đầu xe. |
| `RouteNo` | `String` | Có | `"01"` | Số hiệu tuyến. |
| `StartStop` | `String` | Có | `"Điểm đầu cuối Nguyễn Siêu"` | Tên trạm bắt đầu. |
| `EndStop` | `String` | Có | `"Bến xe buýt Chợ Lớn"` | Tên trạm kết thúc. |
| `Distance` | `Double` | Có | `8561.0` | Chiều dài hướng tuyến (mét). |
| `Outbound` | `Boolean` | Có | `true` | `true` nếu là lượt đi, `false` nếu là lượt về. |
| `RunningTime` | `Int` | Có | `40` | Thời gian hành trình dự kiến (phút). |

---

### 2.4. RoutePath (Đa tuyến tọa độ GPS)
* **Nguồn API:** `/businfo/getpathsbyvar/{routeId}/{varId}`
* **Mục đích:** Vẽ đường đi chính xác của tuyến xe trên bản đồ số (MapLibre/Google Maps polyline).

| Tên trường | Kiểu dữ liệu | Bắt buộc | Ý nghĩa & Mô tả |
|---|---|---|---|
| `lat` | `List<Double>` | Có | Mảng các vĩ độ theo hệ WGS84 (VD: `[10.779948, 10.780512, ...]`). |
| `lng` | `List<Double>` | Có | Mảng các kinh độ tương ứng (VD: `[106.706398, 106.706985, ...]`). |

> [!IMPORTANT]
> Hai mảng `lat` và `lng` luôn có số phần tử bằng nhau và index tương ứng tạo thành từng điểm tọa độ `(lat[i], lng[i])` nối tiếp nhau thành đường liên tục.

---

### 2.5. BusStop (Thông tin Trạm dừng xe buýt)
* **Nguồn API:** `/businfo/getstopsbyvar/{routeId}/{varId}` và `/businfo/getstopsinbounds/...`
* **Mục đích:** Lưu trữ thông tin định vị, cơ sở hạ tầng của từng trạm dừng.

| Tên trường | Kiểu dữ liệu | Bắt buộc | Giá trị mẫu | Ý nghĩa & Mô tả |
|---|---|---|---|---|
| `StopId` | `Int` | Có | `4223` | Mã định danh trạm (Primary Key trên server). |
| `Code` | `String` | Có | `"Q1 102"` | Mã biển số trạm hiển thị thực tế tại hiện trường. |
| `Name` | `String` | Có | `"Trạm Trung chuyển trên đường Hàm Nghi"` | Tên trạm dừng xe buýt. |
| `StopType` | `String` | Không | `"Nhà chờ"`, `"Trụ dừng"`, `"Bến xe"`, `"Ô sơn"` | Loại hình hạ tầng của trạm. |
| `Zone` | `String` | Không | `"Quận 1"` | Quận/Huyện đặt trạm. |
| `Ward` | `String?` | Không | `"Phường Bến Thành"` hoặc `null` | Phường/Xã đặt trạm. |
| `AddressNo` | `String` | Không | `"Hàm Nghi 4"`, `"210-212"` | Số nhà địa chỉ. |
| `Street` | `String` | Không | `"Hàm Nghi"`, `"Trần Hưng Đạo"` | Tên đường đặt trạm. |
| `SupportDisability` | `String`| Không | `"Có"` hoặc `""` | Có hỗ trợ người khuyết tật hay không. |
| `Status` | `String` | Không | `"Đang khai thác"`, `"Chưa khai thác"` | Tình trạng vận hành của trạm. |
| `Lat` | `Double` | Có | `10.771043` | Tọa độ Vĩ độ WGS84 (Latitude). |
| `Lng` | `Double` | Có | `106.699514` | Tọa độ Kinh độ WGS84 (Longitude). |
| `Search` | `String` | Không | `"TTctdHN HN4 HN"` | Chuỗi từ khóa viết tắt phục vụ tìm kiếm. |
| `Routes` | `String` | Không | `"01, 102, 44, 65, 86"` | Chuỗi danh sách các số hiệu tuyến đi qua trạm này. |

---

### 2.6. Prediction (Kết quả Dự báo Xe đến Trạm)
* **Nguồn API:** `/prediction/predictbystopid/{stopId}` hoặc `/prediction/predictbystopcode/{stopCode}`
* **Mục đích:** Hiển thị thông tin thời gian thực các tuyến và xe sắp đến trạm đang xét.

Đối tượng trả về là mảng các tuyến buýt sắp ghé trạm:

| Tên trường | Kiểu dữ liệu | Bắt buộc | Giá trị mẫu | Ý nghĩa & Mô tả |
|---|---|---|---|---|
| `r` | `Int` | Có | `65` | `RouteId` của tuyến buýt có xe đang đến. |
| `rNo` | `String` | Có | `"44"` | Số hiệu tuyến (`RouteNo`). |
| `rN` | `String` | Có | `"Cảng Quận 4 - Bình Quới"` | Tên tuyến đầy đủ. |
| `s` | `Int` | Có | `4223` | `StopId` của trạm đang yêu cầu dự báo. |
| `sN` | `String` | Có | `"Trạm Trung chuyển trên đường Hàm Nghi"` | Tên trạm đang yêu cầu dự báo. |
| `v` | `Int` | Có | `130` | `RouteVarId` (hướng tuyến mà xe đang chạy). |
| `vN` | `String` | Có | `"Cảng Quận 4"` | Đích đến của chuyến xe. |
| `arrs` | `List<ArrivalVehicle>` | Có | Mảng các xe | Danh sách các xe đang di chuyển về trạm này (thường gồm xe gần nhất và xe kế tiếp). |

---

### 2.7. ArrivalVehicle (Dữ liệu xe buýt đang di chuyển)
* **Nguồn API:** Nằm trong thuộc tính `arrs` của `Prediction` và `PredictNextStops`.

| Tên trường | Kiểu dữ liệu | Bắt buộc | Giá trị mẫu | Đơn vị & Ý nghĩa chi tiết |
|---|---|---|---|---|
| `v` | `String` | Có | `"50E20651"`, `"51B26725"` | **Biển số xe buýt** (Vehicle License Plate). |
| `t` | `Int` | Có | `250`, `1791` | **Thời gian dự kiến đến trạm** (đơn vị: **Giây**). Client đổi ra phút: `ceil(t / 60)`. |
| `d` | `Double` | Có | `1168.21` | **Khoảng cách dọc tuyến từ xe đến trạm** (đơn vị: **Mét**). |
| `s` | `Double` | Có | `24.0`, `0.0` | **Vận tốc tức thời của xe** (đơn vị: **km/h**) từ GPS hộp đen. |
| `dts` | `String` | Có | `"2026-11-09T21:13:29+07:00"` | **Thời điểm cập nhật GPS của xe** (chuẩn ISO 8601, múi giờ Việt Nam GMT+7). |
| `sts` | `Int` | Có | `0` | Trạng thái hoạt động xe (`0`: bình thường). |

---

### 2.8. Timetable & Trip (Biểu đồ giờ & Chuyến xuất bến)
* **Nguồn API:** `/businfo/gettimetablebyroute/{routeId}` và `/businfo/gettripsbytimetable/{routeId}/{timetableId}`

**Timetable Item:**
| Tên trường | Kiểu dữ liệu | Giá trị mẫu | Ý nghĩa |
|---|---|---|---|
| `TimeTableId` | `Int` | `1` | ID biểu đồ giờ. |
| `RouteId` | `Int` | `1` | ID tuyến. |
| `RouteVarId` | `Int` | `1` | Hướng tuyến áp dụng. |
| `TotalTrip` | `Int` | `108` | Tổng số chuyến của hướng này trong ngày. |
| `RunningTime` | `String` | `"40"` | Thời gian chạy 1 chuyến (phút). |
| `OperationTime`| `String` | `"05:00 - 22:00"` | Giờ đầu bến - cuối bến. |
| `ApplyDates` | `String` | `"T2, T3, T4, T5, T6, T7, CN"` | Các ngày áp dụng trong tuần. |

**Trip Item:**
| Tên trường | Kiểu dữ liệu | Giá trị mẫu | Ý nghĩa |
|---|---|---|---|
| `TripId` | `Int` | `2` | Khóa định danh chuyến xuất bến. |
| `StartTime` | `String` | `"05:00"` | Giờ xe lăn bánh rời bến xuất phát (HH:mm). |
| `EndTime` | `String` | `"05:40"` | Giờ xe dự kiến cập bến cuối cùng (HH:mm). |
