# BÁO CÁO CHẨN ĐOÁN KẾT NỐI EBMS API
## EBMS ENDPOINT DIAGNOSTIC REPORT

- **Thời điểm thực hiện:** 2026-11-11 20:14:09 (Giờ địa phương)
- **Công cụ thực hiện:** `curl.exe` (Connection timeout: 5s, Max time: 15s, no-retry)
- **Tình trạng tác vụ trước:** Đã hủy thành công Background Task kẹt trước đó (`task-1979`).
- **Endpoint kiểm tra:** `GET https://apicms.ebms.vn/businfo/getroutesforcombo`
- **Tài liệu đối chiếu:** [docs/phase-0/API_CATALOG.md](file:///e:/MultiGo%20Reworked/docs/phase-0/API_CATALOG.md) (Mục A.1: Route API - Danh sách tuyến xe buýt)

---

## 1. THÔNG SỐ REQUEST THỰC TẾ

| Thuộc tính | Chi tiết cấu hình |
| :--- | :--- |
| **URL** | `https://apicms.ebms.vn/businfo/getroutesforcombo` |
| **HTTP Method** | `GET` |
| **Query / Path Parameters** | Không có (Empty) |
| **Headers gửi đi** | `Accept: application/json`<br/>`User-Agent: HCMCBusTracker/1.0 (Android; com.hcmcbustracker.app)` |
| **Connection Timeout** | `5.0 giây` |
| **Read / Max Timeout** | `15.0 giây` |
| **Cơ chế Retry** | Tắt hoàn toàn (Single request) |

---

## 2. KẾT QUẢ PHẢN HỒI HTTP (HTTP RESPONSE)

- **HTTP Status Code:** `200 OK`
- **Content-Type:** `application/json; charset=utf-8`
- **Server:** `cloudflare`
- **Trạng thái kết nối:** `Connection: keep-alive`
- **Cache-Control:** `no-cache`
- **Access-Control-Allow-Origin:** `https://buyttphcm.com.vn`

### Chỉ số Thời gian Phản hồi (Network Timings):
- **Phân giải DNS (time_namelookup):** `0.062719s` (~63 ms)
- **Kết nối TCP (time_connect):** `0.239278s` (~239 ms)
- **Bắt tay SSL/TLS (time_appconnect):** `0.444372s` (~444 ms)
- **Bắt đầu nhận dữ liệu (time_starttransfer):** `0.867718s` (~868 ms)
- **Tổng thời gian hoàn tất (time_total):** **`0.873979s` (~874 ms - dưới 1 giây)**

---

## 3. CẤU TRÚC DỮ LIỆU NHẬN ĐƯỢC (JSON PAYLOAD)

Server trả về một mảng JSON đầy đủ các tuyến xe buýt đang hoạt động tại TP.HCM (dung lượng ~17.6 KB).

### Trích xuất Response mẫu (Đầu mảng):
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
    "value": "4",
    "lable": "[04] Bến Thành - Cộng Hòa - Bến xe An Sương",
    "key": 0
  },
  {
    "value": "5",
    "lable": "[05] Bến xe buýt Chợ Lớn - Bến xe Biên Hòa",
    "key": 0
  },
  {
    "value": "6",
    "lable": "[06] Bến xe buýt Chợ Lớn - Đại học Nông Lâm",
    "key": 0
  },
  {
    "value": "384",
    "lable": "[MRT1] Bến Thành - Suối Tiên",
    "key": 0
  },
  {
    "value": "337",
    "lable": "[SWB1] Bạch Đằng - Linh Đông",
    "key": 0
  }
]
```

### Đối chiếu với DTO trong mã nguồn:
Trong [`core-network/src/main/java/com/hcmcbustracker/core/network/dto/RouteDto.kt`](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/dto/RouteDto.kt):
```kotlin
@Serializable
data class RouteComboDto(
    @SerialName("value") val value: String,
    @SerialName("lable") val lable: String,
    @SerialName("key") val key: Int = 0
)
```
$\rightarrow$ Cấu trúc trường `value`, `lable`, `key` của EBMS API **khớp 100% với DTO hiện có** trong dự án.

---

## 4. KẾT LUẬN CHẨN ĐOÁN BƯỚC ĐẦU

1. **Về phía máy chủ EBMS:**
   - Máy chủ `https://apicms.ebms.vn` đang **HOẠT ĐỘNG HOÀN HÀO**, phản hồi cực nhanh (**0.87 giây**), không có lỗi DNS, TLS, timeout hay 5xx/4xx.
   - Endpoint `/businfo/getroutesforcombo` trả về danh mục tuyến chuẩn xác.
2. **Nguyên nhân màn hình "Tuyến xe" rỗng tại Runtime:**
   - Nguyên nhân **KHÔNG PHẢI** do máy chủ EBMS chết hoặc timeout.
   - Qua rà soát code tại [`RoutesViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-route/src/main/java/com/hcmcbustracker/feature/route/RoutesViewModel.kt) và [`MainActivity.kt`](file:///e:/MultiGo%20Reworked/app/src/main/java/com/hcmcbustracker/app/MainActivity.kt): **Ứng dụng khi khởi động lần đầu KHÔNG CÓ cơ chế tự động kích hoạt `syncRoutes()`**.
   - `RoutesViewModel` chỉ đọc từ Room Database (`repository.getAllRoutes()`). Khi ứng dụng mới cài đặt, cơ sở dữ liệu Room đang rỗng (0 bản ghi). Hàm `syncRoutes()` trong `RoutesViewModel` chỉ được gọi thủ công khi người dùng bấm nút làm mới trên TopAppBar hoặc nút "Thử lại", dẫn đến việc màn hình khởi điểm hiển thị "Tất cả tuyến (0)".
