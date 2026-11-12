# HCMC Bus Tracker – Phase 4: Prediction User Interface & Experience
*Tài liệu Đặc tả Giao diện & Trải nghiệm Người dùng Dự báo Thời gian thực*

**Phiên bản:** 1.0.0  
**Giai đoạn:** Phase 4 – Real-time Bus Arrival Prediction  
**Trạng thái:** Hoàn thành & Chờ phê duyệt  
**Ngày cập nhật:** 2026-11-10  

---

## 1. NGUYÊN TẮC THIẾT KẾ GIAO DIỆN DỰ BÁO

Giao diện màn hình dự báo xe buýt đến trạm ([`StopPredictionScreen.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/StopPredictionScreen.kt)) được thiết kế theo các tiêu chuẩn giao thông công cộng hiện đại:
- **Tối ưu tốc độ đọc (Glanceable UI):** Hành khách đang đứng tại trạm chỉ cần nhìn lướt qua trong 1 giây là nắm được: tuyến xe nào đang tới, biển số nào, còn bao nhiêu phút.
- **Minh bạch thông tin (Information Transparency):** Phân định rạch ròi giữa thời gian GPS của phương tiện và thời điểm điện thoại nhận dữ liệu từ máy chủ.
- **Chính xác về ETA:** Không làm tròn sai thời gian; cảnh báo rõ ràng khi xe sắp đến gần trạm (< 60s).
- **Tuyệt đối không hiển thị ETA bằng 0 khi lỗi mạng.**

---

## 2. CẤU TRÚC GIAO DIỆN MÀN HÌNH (SCREEN ANATOMY)

```
┌──────────────────────────────────────────────────────────────┐
│ [←] Bến xe buýt Sài Gòn                          [♡]  [↻]   │ TopAppBar (Tên trạm,
│     Công viên 23/9, Đường Lê Lai, Quận 1                     │ Địa chỉ, Yêu thích)
├──────────────────────────────────────────────────────────────┤
│ ● LIVE • Cập nhật 3s trước         Nhận phản hồi: 09:25:30   │ StatusBanner (Trạng thái,
│                                           (Chu kỳ 15s)   [↻] │ Thời điểm nhận)
├──────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ [ 01 ] Hướng đi: Bến xe buýt Chợ Lớn                     │ │ Route Header
│ │        Ga Ba Son - Bến xe buýt Chợ Lớn                   │ │
│ │ ──────────────────────────────────────────────────────── │ │
│ │ ┌──────────────────────────────────────────────────────┐ │ │
│ │ │ [ 50E-206.51 ] (Xe sắp đến)    [ Sắp đến (< 1 phút) ]│ │ │ VehicleRow (Xe 1)
│ │ │ 📍 Cách 210m       ⚡ 18 km/h                         │ │ │
│ │ │ GPS: 09:25:20 • Nhận: 09:25:30                       │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ │ ┌──────────────────────────────────────────────────────┐ │ │
│ │ │ [ 50E-206.58 ] (Xe kế tiếp)        [ 12 phút 40 giây ]│ │ │ VehicleRow (Xe 2)
│ │ │ 📍 Cách 3.2 km     ⚡ 24 km/h                         │ │ │
│ │ │ GPS: 09:25:15 • Nhận: 09:25:30                       │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ │                                                          │ │
│ │            Xem dự kiến trạm tiếp theo ▼                  │ │ Expand Action
│ │ ┌──────────────────────────────────────────────────────┐ │ │
│ │ │ Dự kiến các trạm tiếp theo của xe này:               │ │ │ NextStops Section
│ │ │ 1. Rạp Kim Châu             Cách 650m      [ 2 phút ]│ │ │
│ │ │ 2. Chợ Dân Sinh             Cách 1.1 km    [ 4 phút ]│ │ │
│ │ │ 3. Trường Ernst Thälmann    Cách 1.8 km    [ 6 phút ]│ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ └──────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. QUY TẮC HIỂN THỊ THỜI GIAN ĐẾN TRẠM (ETA FORMATTING RULES)

1. **Xe đang tới rất gần (`etaSeconds < 60` hoặc `distanceMeters < 300m`):**
   - Hiển thị chip cảnh báo đỏ nổi bật: **`Sắp đến (< 1 phút)`**.
   - Báo hiệu cho hành khách chuẩn bị bước ra điểm đón.
2. **Xe có cự ly bình thường (`etaSeconds >= 60`):**
   - Hiển thị đầy đủ phút và giây thực tế:
     - Nếu `etaSeconds = 250s` $\rightarrow$ **`4 phút 10 giây`**.
     - Nếu `etaSeconds = 300s` $\rightarrow$ **`5 phút`**.
   - Không làm tròn số phút một cách tùy tiện gây hiểu lầm cho người chờ xe.
3. **Khi mất kết nối hoặc dữ liệu không xác định:**
   - Hiển thị: **`--`**.
   - **Tuyệt đối không hiển thị `0 phút` hoặc `0 giây`** khi request bị lỗi hoặc không có phương tiện.

---

## 4. BẢNG MÀU & ĐỒNG BỘ TRẠNG THÁI NGUỒN CẤP (STATUS BANNER SPEC)

| Trạng thái | Mã màu chủ đạo | Nội dung hiển thị | Hành vi giao diện |
| :--- | :--- | :--- | :--- |
| **LIVE** | Xanh lá đậm (`#2E7D32`) | `LIVE • Cập nhật Xs trước` | Hoạt động bình thường, biểu tượng xoay nhẹ khi cập nhật. |
| **STALE** | Hổ phách/Cam (`#F57C00`) | `DỮ LIỆU CŨ • Cập nhật Xs trước` | Nền vàng nhạt cảnh báo. Hiển thị nhãn `⚠️ Tín hiệu GPS cũ` nếu xe phát GPS chậm hơn 3 phút. |
| **OFFLINE** | Đỏ đậm (`#D32F2F`) | `NGOẠI TUYẾN • Không có mạng internet` | Nền đỏ nhạt. Dừng polling định kỳ, hiển thị nút thử lại thủ công. |
| **EMPTY** | Xám trung tính (`#757575`)| `TRẠM TRỐNG • Chưa có xe đến` | Hiển thị thông báo thân thiện: "Hiện không có xe buýt nào đang di chuyển về trạm này". |
| **API_ERROR** | Đỏ gạch (`#C62828`) | `LỖI KẾT NỐI • Máy chủ không phản hồi` | Hiển thị thông báo lỗi từ máy chủ và nút "Thử lại". |

---

## 5. THIẾT KẾ THẺ PHƯƠNG TIỆN (VEHICLE CARD SPEC)

Mỗi phương tiện đang tiến về trạm hiển thị đầy đủ 7 trường thông tin bắt buộc:
1. **Biển số xe chuẩn hóa:** Định dạng biển số xe TP.HCM theo tiêu chuẩn giao thông (VD: `50E-206.51`).
2. **Nhãn thứ tự:** Phân định rõ `(Xe sắp đến)` đối với xe đầu tiên và `(Xe kế tiếp)` đối với các xe sau.
3. **Thời gian dự kiến đến (ETA):** Badge bo góc với màu sắc ngữ cảnh (Đỏ nổi bật khi sắp đến, Tím/Xanh khi còn xa).
4. **Khoảng cách:** Đơn vị mét nếu dưới 1000m (VD: `Cách 450m`), đơn vị kilômét nếu trên 1000m (VD: `Cách 2.4 km`).
5. **Vận tốc tức thời:** Đơn vị km/h kèm icon đồng hồ tốc độ (VD: `⚡ 24 km/h`).
6. **Thời điểm GPS:** Mốc thời gian hộp đen vệ tinh phát tín hiệu (VD: `GPS: 09:24:12`).
7. **Thời điểm ứng dụng nhận:** Mốc thời gian điện thoại nhận phản hồi JSON (VD: `Nhận: 09:24:18`).
