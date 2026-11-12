# BÁO CÁO KỸ THUẬT: CƠ CHẾ BÙ TRỪ ĐỘ TRỄ GPS & DỰ PHÓNG VỊ TRÍ XE BUÝT

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Giai đoạn:** Phase 9 – Intelligent Vehicle Tracking & Position Compensation  
**Ngày cập nhật:** 2026-11-12  
**Thành phần cốt lõi:** [`PositionCompensationEngine.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/PositionCompensationEngine.kt)

---

## 1. NGUYÊN NHÂN GÂY LỆCH VỊ TRÍ GIỮA CÁC ỨNG DỤNG

Dữ liệu thời gian thực từ máy chủ EBMS (`https://apicms.ebms.vn`) cung cấp thông tin dự báo cho một trạm dựa trên thông số gần nhất thu được từ hộp đen GPS gắn trên xe buýt:
- `d`: Cự ly dọc tuyến tính từ xe đến trạm (mét).
- `s`: Vận tốc tức thời của xe (km/h).
- `dts`: Chuỗi thời gian ISO-8601 ghi nhận gói tin GPS trên xe.
- `t`: Thời gian dự kiến đến trạm (giây).
- `sts`: Trạng thái hành trình.

**Bản chất độ trễ (Latency Chain):**
1. **Chu kỳ gửi gói tin của thiết bị giám sát hành trình:** Hộp đen trên xe buýt gửi tín hiệu GPS về máy chủ trung tâm theo chu kỳ 15s – 60s.
2. **Độ trễ xử lý máy chủ (Backend Pipeline):** Thời gian đồng bộ từ server hộp đen $\rightarrow$ server xử lý STIS/EBMS $\rightarrow$ API cache thường mất từ 10s – 45s.
3. **Chu kỳ làm mới ứng dụng (Client Polling):** Ứng dụng gửi request mỗi 15 giây.

Do đó, tại thời điểm người dùng nhìn vào màn hình điện thoại, cự ly thô $d_{\text{EBMS}}$ phản ánh vị trí của xe tại thời điểm quá khứ cách hiện tại từ **30 giây đến hơn 120 giây**. Nếu vẽ trực tiếp theo $d_{\text{EBMS}}$, marker xe buýt bị tụt lại phía sau thực tế hàng trăm mét đến hơn 1 km.

---

## 2. PHÂN ĐỊNH CÁC MỐC THỜI GIAN ĐỘC LẬP

Để đảm bảo không bao giờ lấy thời điểm mạng để ngụy tạo thành thời điểm GPS, hệ thống lưu trữ và phân tách 4 mốc thời gian rõ ràng:

| Thuộc tính | Ý nghĩa kỹ thuật | Nguồn dữ liệu |
| :--- | :--- | :--- |
| **`gpsTimestampMillis`** | Thời điểm thực tế gói tin GPS được tạo ra trên xe buýt. | Trích xuất từ chuỗi ISO `dts` (`OffsetDateTime` / `Instant` / VN timezone `+07:00`). |
| **`responseReceivedAtMillis`** | Thời điểm ứng dụng nhận được HTTP Response từ EBMS API. | `System.currentTimeMillis()` tại Network Mapper. |
| **`predictionUpdatedAtMillis`** | Thời điểm ViewModel phân tích và đưa trạng thái mới vào `StateFlow`. | `System.currentTimeMillis()` khi cập nhật trạng thái dự báo. |
| **`renderUpdatedAtMillis`** | Thời điểm khung hình bản đồ nội suy và vẽ marker lên MapLibre. | `System.currentTimeMillis()` trong engine nội suy. |

### Xử lý đa định dạng & Sai lệch đồng hồ:
Bộ phân tích `parseGpsTimestampMillis()` hỗ trợ an toàn cho mọi biến thể:
- ISO-8601 có offset (chuẩn chính thức EBMS): `"2026-11-09T21:13:29+07:00"`.
- ISO-8601 UTC: `"2026-11-09T14:13:29Z"`.
- LocalDateTime không kèm múi giờ: Tự động gắn múi giờ `Asia/Ho_Chi_Minh` (`+07:00`).
- Epoch Unix seconds (10 chữ số) hoặc milliseconds (13 chữ số).
- Bảo vệ chống trôi đồng hồ (Clock Skew Guard): Nếu đồng hồ máy người dùng chạy chậm hơn server một chút, `ageSeconds` được giữ ở mức tối thiểu `0.0`.

---

## 3. CÔNG THỨC BÙ TRỪ ĐỘ TRỄ (DEAD-RECKONING PROJECTION)

Khi có đủ các điều kiện hợp lệ, quãng đường xe tiếp tục di chuyển dọc theo lộ trình trong khoảng thời gian trễ được tính bằng công thức vật lý động học:

1. **Tuổi gói tin GPS (tính đến mili-giây):**
   $$\Delta t_{\text{age}} = \max\left(0.0, \; \frac{T_{\text{current}} - T_{\text{gps}}}{1000.0}\right) \quad (\text{giây})$$

2. **Quãng đường dự phóng (Projected Distance):**
   $$v_{\text{m/s}} = s_{\text{km/h}} \times \frac{1000.0}{3600.0}$$
   $$d_{\text{projected}} = v_{\text{m/s}} \times \Delta t_{\text{age}} \quad (\text{mét})$$

3. **Cự ly hiệu dụng đến trạm (Effective Distance to Stop):**
   $$d_{\text{effective}} = \max\left(0.0, \; d_{\text{EBMS}} - d_{\text{projected}}\right) \quad (\text{mét})$$

*Nguyên tắc bảo toàn dữ liệu:* Trường $d_{\text{EBMS}}$ gốc luôn được giữ nguyên trong `rawDistanceMeters` và tuyệt đối không bị ghi đè.

---

## 4. NGƯỠNG GIỚI HẠN VÀ CÁC TRƯỜNG HỢP NGƯNG DỰ PHÓNG

Hệ thống không áp dụng phép trừ $d - d_{\text{projected}}$ một cách mù quáng hoặc vô hạn. Bộ kiểm tra quy chuẩn gồm 6 chốt an toàn bắt buộc:

| Mã trạng thái (`CompensationStatus`) | Điều kiện kích hoạt | Hành vi hệ thống |
| :--- | :--- | :--- |
| **`LIVE_PROJECTED`** | Tốc độ $1.5 < s \le 90.0\text{ km/h}$, tuổi GPS $0 \le \Delta t \le 120\text{s}$, cùng chiều tuyến. | Áp dụng bù trừ tuyến tính, hiển thị nhãn `🟢 Đang cập nhật`. |
| **`STATIONARY`** | Tốc độ $s \le 1.5\text{ km/h}$ (xe đang kẹt xe, chờ đèn đỏ, hoặc dừng bến). | $d_{\text{projected}} = 0$, giữ nguyên vị trí GPS cuối, hiển thị `🅿️ Đang dừng`. |
| **`AT_STOP`** | $d_{\text{projected}} \ge d_{\text{EBMS}}$ (tức $d_{\text{effective}} \le 0$). | Ghim xe tại trạm đích ($d_{\text{effective}} = 0\text{m}$), không cho vượt qua trạm, hiển thị `🏁 Đã đến trạm`. |
| **`STALE_DATA`** | Tuổi gói tin GPS $> 120\text{ giây}$. | **Ngưng dự phóng** để tránh xe bị đẩy đi quá xa sai thực tế giao thông, hiển thị `⏳ Dữ liệu cũ`. |
| **`INVALID_SPEED`** | $s < 0$ hoặc $s > 90.0\text{ km/h}$ (nhiễu cảm biến vận tốc). | Không dự phóng, sử dụng cự ly gốc EBMS. |
| **`INVALID_TIMESTAMP`** | Chuỗi `dts` rỗng hoặc sai định dạng hoàn toàn. | Không thể tính tuổi GPS, giữ nguyên cự ly gốc với nhãn `📍 Vị trí ước lượng`. |
| **`DIRECTION_MISMATCH`** | Chiều biến thể không khớp hoặc xe đang quay đầu/rời bến. | Ngưng dự phóng ngay lập tức (`isProjected = false`), hiển thị `🔄 Khác hướng`. |

---

## 5. KẾT QUẢ KIỂM THỬ UNIT TEST (VERIFICATION)

Toàn bộ các ca biên (edge cases) đã được kiểm chứng tự động tại [`PositionCompensationEngineTest.kt`](file:///e:/MultiGo%20Reworked/core-model/src/test/java/com/hcmcbustracker/core/model/PositionCompensationEngineTest.kt) (16/16 test cases đạt **PASS 100%**):

- Parse chuẩn các chuỗi ISO-8601 `+07:00`, UTC `Z`, Unix Epoch giây/mili-giây, chuỗi lỗi trả về `null`.
- Kiểm thử giả lập độ trễ đa cấp: `0s` $\rightarrow$ `15s` $\rightarrow$ `30s` $\rightarrow$ `60s` $\rightarrow$ `120s` khớp chính xác từng mét.
- Ngưỡng an toàn `> 120s` (ví dụ `150s`): Xác nhận dự phóng dừng tại 0m và giữ nguyên cự ly gốc (`STALE_DATA`).
- Xe đứng yên ($s = 1.0\text{ km/h}$): Xác nhận không dự phóng (`STATIONARY`).
- Xe chuẩn bị cập bến ($d_{\text{projected}} > d_{\text{EBMS}}$): Chặn ở mức `0.0m` (`AT_STOP`), không âm.
- Kiểm chứng trực tiếp ca đối chiếu thực tế xe **`50E-616.63`**: với $d_{\text{EBMS}} = 6,417\text{ m}$, vận tốc $38\text{ km/h}$ và độ trễ ~91.6s, cự ly dự phóng đạt **966.9 m**, đưa cự ly hiệu dụng về **5,450.1 m (5.45 km)**.
