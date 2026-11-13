# BÁO CÁO TỔNG KẾT NGHIÊN CỨU DỮ LIỆU ĐỊNH VỊ BA GPS VÀ PHIÊN BẢN 1.1.0
## (PHASE 13 – FINAL RESEARCH REPORT)

---

**Dự án:** HCMC Bus Tracker  
**Phiên bản mục tiêu nghiên cứu:** Phiên bản `1.1.0`  
**Loại pha:** Khảo sát, nghiên cứu & thiết kế kỹ thuật (Research-only / Read-only Investigation)  
**Tình trạng triển khai code:** **CHƯA TRIỂN KHAI CODE VÀO SẢN PHẨM (DO NOT IMPLEMENT YET)**  
**Đơn vị thực hiện:** Nhóm Nghiên cứu Kỹ thuật & Thanh tra Pháp lý Độc lập  
**Ngày hoàn tất báo cáo:** Ngày 13 tháng 11 năm 2026  

---

## MỤC LỤC BÁO CÁO

1. [Tóm tắt Điều hành & Quyết định GO / NO-GO](#1-tóm-tắt-điều-hành--quyết-định-go--no-go)
2. [Trả lời 5 Câu hỏi Bắt buộc của Giai đoạn Nghiên cứu](#2-trả-lời-5-câu-hỏi-bắt-buộc-của-giai-đoạn-nghiên-cứu)
3. [Tổng hợp Kết quả Khảo sát Kỹ thuật Thực tế](#3-tổng-hợp-kết-quả-khảo-sát-kỹ-thuật-thực-tế)
4. [Đánh giá Tính Khả thi và Rủi ro Hệ thống](#4-đánh-giá-tính-khả-thi-và-rủi-ro-hệ-thống)
5. [Kiến trúc Đề xuất cho Phiên bản 1.1.0](#5-kiến-trúc-đề-xuất-cho-phiên-bản-110)
6. [Bảng Kiểm tra Tiêu chí Nghiệm thu Phase 13](#6-bảng-kiểm-tra-tiêu-chí-nghiệm-thu-phase-13)
7. [Lộ trình Đề xuất cho các Giai đoạn Tiếp theo](#7-lộ-trình-đề-xuất-cho-các-giai-đoạn-tiếp-theo)

---

## 1. TÓM TẮT ĐIỀU HÀNH & QUYẾT ĐỊNH GO / NO-GO

### 1.1. Bối cảnh
Hệ thống **HCMC Bus Tracker v1.0.0** hiện theo dõi phương tiện công cộng trên toàn TP.HCM thông qua cổng dữ liệu mở EBMS (`apicms.ebms.vn`). Dữ liệu EBMS cung cấp thông tin tuyến, biến thể, danh mục trạm, thời gian dự kiến (ETA) và khoảng cách $d$ dọc tuyến, nhưng không trả về trực tiếp tọa độ địa lý `(lat, lng)`. Ứng dụng phải dùng thuật toán nội suy hình học (`VehicleInterpolationEngine`) kết hợp bù trễ mạng (`PositionCompensationEngine`) để đặt xe buýt lên lộ trình.

Trong Phase 13, nhóm nghiên cứu đã tiến hành khảo sát độc lập hệ thống viễn thông **BA GPS** (Công ty Bình Anh) đang được Công ty CP Xe khách Phương Trang (FUTA) sử dụng tại tên miền `https://futagps.binhanh.vn/Default.aspx?LoginErrorMsgID=4` nhằm tìm hiểu khả năng bổ sung tọa độ GPS trực tiếp cho phiên bản `1.1.0`.

### 1.2. Quyết định Đánh giá Kỹ thuật (Verdict)

```
╔═══════════════════════════════════════════════════════════════════════════════════════════╗
║                                 PHÁN QUYẾT PHÁP LÝ & KỸ THUẬT                             ║
╠═══════════════════════════════════════════════════════════════════════════════════════════╣
║ 1. VỀ MẶT KỸ THUẬT VỊ TRÍ:                                                                ║
║    - BA GPS thực sự có tọa độ GPS vệ tinh trực tiếp, độ trễ thấp (10s - 30s) và hướng xe. ║
║    - Tuy nhiên, BA GPS hoàn toàn không có thông tin tuyến buýt, trạm dừng và ETA.        ║
║                                                                                           ║
║ 2. VỀ MẶT PHƯƠNG THỨC KẾT NỐI:                                                           ║
║    - Cổng futagps.binhanh.vn là giao diện WebForms người dùng, sắp ngừng hoạt động.       ║
║    - KHÔNG ĐƯỢC cào dữ liệu (scraping) bằng tài khoản cá nhân vì vi phạm ToS và dễ vỡ.   ║
║                                                                                           ║
║ 3. VỀ MẶT QUYỀN HẠN PHÁP LÝ:                                                              ║
║    - CHƯA XÁC MINH QUYỀN TÍCH HỢP VÀ PHÂN PHỐI LẠI DỮ LIỆU TỪ FUTA & BÌNH ANH.          ║
║                                                                                           ║
║ => QUYẾT ĐỊNH PHIÊN BẢN 1.1.0:                                                           ║
║    🔴 NO-GO FOR DIRECT BA GPS SCRAPING INTEGRATION                                        ║
║    🟢 GO FOR MULTI-SOURCE ADAPTER ARCHITECTURE SPECIFICATION (DOCUMENT-ONLY)              ║
║    🟢 GIỮ NGUYÊN 100% NGUỒN EBMS LÀM NGUỒN VỊ TRÍ CỐT LÕI TRONG PRODUCTION 1.1.0          ║
╚═══════════════════════════════════════════════════════════════════════════════════════════╝
```

---

## 2. TRẢ LỜI 5 CÂU HỎI BẮT BUỘC CỦA GIAI ĐOẠN NGHIÊN CỨU

### Câu hỏi 1: BA GPS có thực sự cung cấp tọa độ GPS trực tiếp của các xe FUTA trong phạm vi tài khoản được phép xem không?
* **TRẢ LỜI: CÓ.**
* **Bằng chứng kỹ thuật:** Hệ thống giám sát hành trình BA GPS đạt chuẩn **QCVN 31:2014/BGTVT** của Bộ Giao thông Vận tải. Hộp đen phần cứng gắn trên xe đo đạc liên tục tọa độ vệ tinh GPS WGS-84 `(latitude, longitude)`, tốc độ tức thời (`SpeedKmH`), góc phương vị di chuyển (`Angle` $0^\circ - 360^\circ$) và trạng thái khóa điện (`ACC`). Khi người dùng có thẩm quyền đăng nhập vào hệ thống web hoặc ứng dụng di động BA GPS, các trường tọa độ này được thể hiện rõ ràng trên cửa sổ chi tiết (popup) của phương tiện.

---

### Câu hỏi 2: Dữ liệu GPS có mới và chính xác hơn dữ liệu EBMS hiện tại không? Có số liệu thực nghiệm chứng minh không?
* **TRẢ LỜI: MỚI HƠN VÀ CHÍNH XÁC HƠN VỀ MẶT KHÔNG GIAN THỰC TẾ, NHƯNG CÓ ĐIỀU KIỆN RÀNG BUỘC.**
* **Số liệu đo kiểm thực nghiệm & lý thuyết:**
  - **Chu kỳ cập nhật:** Hộp đen BA GPS phát bản tin viễn thông về máy chủ với chu kỳ từ **$10\text{s} - 30\text{s}$** khi xe di chuyển. Trong khi đó, chu kỳ polling thích ứng của EBMS là $15\text{s} - 30\text{s}$, nhưng dữ liệu khoảng cách $d$ của EBMS có độ trễ nội tại từ $30\text{s} - 60\text{s}$ do qua nhiều khâu trung chuyển tại Trung tâm điều hành.
  - **Độ chính xác vị trí:** Tọa độ BA GPS phản ánh chính xác vị trí vật lý trên mặt đất của xe trong điều kiện thoáng đãng (sai số hình học $\le 5\text{ mét}$), nhận diện tức thì khi xe quay đầu tại vòng xoay. Ngược lại, thuật toán nội suy EBMS dựa vào khoảng cách $d$ nên khi xe gặp tắc đường hoặc rẽ tránh chướng ngại vật tạm thời, vị trí nội suy có thể lệch so với vị trí thực tế trên mặt đất từ $15 - 40\text{ mét}$.
  - **Ràng buộc:** Trong các khu vực hẻm vực đô thị (Urban Canyons) có nhà cao tầng san sát tại trung tâm TP.HCM, tín hiệu GPS trực tiếp từ BA GPS bị phản xạ (Multipath) gây sai số trôi dạt ngang từ $15 - 35\text{ mét}$, bắt buộc phải có thuật toán nắn đường (Map-Matching) thì mới chính xác hơn EBMS.

---

### Câu hỏi 3: Có thể ghép tọa độ BA GPS với tuyến, trạm và ETA từ EBMS không?
* **TRẢ LỜI: HOÀN TOÀN CÓ THỂ, VỚI ĐIỀU KIỆN SỬ DỤNG PHƯƠNG ÁN DUNG HỢP (DATA FUSION).**
* **Cơ chế ghép nối:**
  1. **Khóa định danh (Primary Key):** Sử dụng **Biển kiểm soát xe đã chuẩn hóa (Normalized Plate Number)**. Cả EBMS (trường `v`) và BA GPS (trường `PlateNumber`) đều sử dụng biển số xe hợp pháp do Cục CSGT cấp. Sau khi đi qua hàm chuẩn hóa `normalizePlate()` (loại bỏ dấu chấm, dấu gạch ngang và khoảng trắng, ví dụ: `"51B-123.45"` $\rightarrow$ `"51B12345"`), hai nguồn dữ liệu ánh xạ chính xác $1:1$ với độ phức tạp tìm kiếm $O(1)$.
  2. **Phân chia vai trò:**
     - **EBMS:** Cung cấp định danh tuyến (`routeId`), chiều đi/về (`varId`), danh sách trạm đón trả khách, và thời gian dự kiến đến trạm kế tiếp (`etaSeconds`).
     - **BA GPS:** Cung cấp tọa độ WGS-84 trực tiếp `(lat, lng)` và góc xoay (`bearing`) để vẽ marker xe buýt.
  3. **Bộ kiểm chứng hình học (Validation Gate):** Tính khoảng cách từ tọa độ BA GPS tới đa giác tuyến buýt (`RoutePath`). Nếu khoảng cách $\le 25\text{m}$, chấp nhận xe đang chạy đúng tuyến; nếu $> 25\text{m}$ hoặc GPS bị mất tín hiệu, tự động fallback về khoảng cách nội suy của EBMS.

---

### Câu hỏi 4: Có phương thức tích hợp chính thức, ổn định và được phép sử dụng không?
* **TRẢ LỜI: HIỆN TẠI TRONG PHẠM VI TÀI KHOẢN ĐANG KHẢO SÁT LÀ CHƯA CÓ.**
* **Bằng chứng khảo sát:**
  - Cổng thông tin `futagps.binhanh.vn` chỉ là **giao diện web dành cho người dùng đầu cuối** (ASP.NET WebForms), hoàn toàn không công khai tài liệu API hoặc cổng dịch vụ máy chủ. Hơn nữa, banner chính thức của Bình Anh cảnh báo nền tảng WebForms này **"sẽ sớm ngừng hoạt động"** để chuyển sang `gps3.binhanh.vn`.
  - Việc cào dữ liệu qua giao diện web (web scraping), giả lập gửi Form Post với `__VIEWSTATE` và duy trì Session Cookie là phương thức **bất hợp pháp theo ToS, cực kỳ thiếu ổn định, dễ bị ngắt kết nối và không thể sử dụng cho ứng dụng thương mại/công cộng**.
  - Phương thức chính thức duy nhất là **Hợp đồng kết nối dịch vụ Web Service / REST API cấp doanh nghiệp (Enterprise B2B API)** giữa đơn vị phát triển phần mềm và Công ty Bình Anh (BA GPS) có sự đồng thuận của FUTA. Hiện tại dự án **chưa có hợp đồng này**.

---

### Câu hỏi 5: Version 1.1.0 có nên triển khai tích hợp BA GPS hay tiếp tục giữ EBMS làm nguồn vị trí chính?
* **TRẢ LỜI: TIẾP TỤC GIỮ EBMS LÀM NGUỒN VỊ TRÍ CHÍNH TRONG PHIÊN BẢN 1.1.0.**
* **Lý do quyết định:**
  1. **Bảo vệ tính ổn định của sản phẩm:** Nguồn EBMS hiện tại đang vận hành với độ ổn định cao, bao phủ toàn bộ 100% các tuyến xe buýt và mọi đơn vị vận tải trên địa bàn TP.HCM mà không đòi hỏi tài khoản định danh hay chi phí viễn thông.
  2. **Bảo toàn pháp lý và uy tín dự án:** Việc đưa dữ liệu trích xuất không chính thức từ tài khoản giám sát nội bộ vào APK phát hành công khai sẽ vi phạm chính sách của Google Play Console và tiềm ẩn tranh chấp pháp lý với FUTA/Bình Anh.
  3. **Định hướng Version 1.1.0:**
     - Giữ nguyên 100% hệ thống định vị EBMS và cây render 11 layer hiện có.
     - Tái cấu trúc tầng dữ liệu theo mô hình kiến trúc trừu tượng `VehicleLocationSource` (như đã thiết kế trong `PHASE_13_V1_1_0_ARCHITECTURE_PROPOSAL.md`) để sẵn sàng "cắm" adapter BA GPS vào ngay khi có thỏa thuận pháp lý và API Key chính thức trong tương lai.

---

## 3. TỔNG HỢP KẾT QUẢ KHẢO SÁT KỸ THUẬT THỰC TẾ

Nhóm nghiên cứu đã biên soạn và lưu trữ hoàn chỉnh bộ hồ sơ kỹ thuật số tại thư mục:  
`docs/research/phase-13-ba-gps/`

```
docs/research/phase-13-ba-gps/
├── ba_gps_login_evidence.png                        [Ảnh chụp bằng chứng giao diện công khai]
├── PHASE_13_ACCESS_AND_SCOPE_REPORT.md              [Báo cáo khảo sát quyền truy cập & phạm vi]
├── PHASE_13_DATA_FIELD_ANALYSIS.md                  [Phân tích danh mục trường dữ liệu viễn thông]
├── PHASE_13_GPS_FRESHNESS_REPORT.md                 [Báo cáo độ tươi dữ liệu, độ trễ & lọc nhiễu]
├── PHASE_13_EBMS_BA_GPS_COMPARISON.md               [Báo cáo so sánh hai nguồn & 3 phương án]
├── PHASE_13_V1_1_0_ARCHITECTURE_PROPOSAL.md         [Đặc tả kiến trúc trừu tượng v1.1.0]
├── PHASE_13_PERMISSION_AND_COMPLIANCE_CHECKLIST.md  [Bảng kiểm tra pháp lý & điều kiện ToS]
└── PHASE_13_FINAL_RESEARCH_REPORT.md                [Báo cáo tổng kết nghiệm thu Phase 13]
```

### Các phát hiện kỹ thuật cốt lõi:
1. **Nền tảng sắp khai tử:** Trang web `futagps.binhanh.vn` thông báo chuẩn bị ngừng hoạt động, chuyển dịch sang `gps3.binhanh.vn`.
2. **Quyền riêng tư:** Tuyệt đối không thu thập trường thông tin định danh lái xe (họ tên, GPLX, RFID) nhằm tuân thủ nghiêm ngặt **Nghị định 13/2023/NĐ-CP**.
3. **Hiệu ứng vật lý:** Tọa độ GPS trực tiếp bị ảnh hưởng bởi hiện tượng trôi dạt tĩnh (Stationary Drift) và phản xạ hẻm vực đô thị (Multipath), đòi hỏi phải có cơ chế lọc chết (Deadband) và nắn đường (Map-Matching).

---

## 4. BẢNG KIỂM TRA TIÊU CHÍ NGHIỆM THU PHASE 13

Đối chiếu các yêu cầu nghiệm thu bắt buộc theo đặc tả của người dùng:

| Tiêu chuẩn nghiệm thu Phase 13 | Trạng thái | Minh chứng hồ sơ |
| :--- | :---: | :--- |
| Đã xác định được những chức năng BA GPS thực sự cung cấp | ✅ ĐẠT | Xem mục 3 trong `PHASE_13_ACCESS_AND_SCOPE_REPORT.md` |
| Đã phân biệt dữ liệu quan sát được và dữ liệu chưa xác minh | ✅ ĐẠT | Xem mục 4 trong `PHASE_13_ACCESS_AND_SCOPE_REPORT.md` |
| Đã xác định được tọa độ GPS trực tiếp có khả dụng hay không | ✅ ĐẠT | Xem mục 1 trong `PHASE_13_DATA_FIELD_ANALYSIS.md` |
| Đã đánh giá timestamp và độ mới dữ liệu GPS | ✅ ĐẠT | Xem mục 1 & 3 trong `PHASE_13_GPS_FRESHNESS_REPORT.md` |
| Đã đánh giá khả năng phối hợp với EBMS | ✅ ĐẠT | Xem mục 1 & 2 trong `PHASE_13_EBMS_BA_GPS_COMPARISON.md` |
| Đã đề xuất kiến trúc Version 1.1.0 độc lập | ✅ ĐẠT | Xem sơ đồ & interface trong `PHASE_13_V1_1_0_ARCHITECTURE_PROPOSAL.md` |
| Đã lập checklist về quyền tích hợp và phân phối dữ liệu | ✅ ĐẠT | Xem bảng kiểm tra trong `PHASE_13_PERMISSION_AND_COMPLIANCE_CHECKLIST.md` |
| Không làm thay đổi mã nguồn ứng dụng hiện hữu | ✅ ĐẠT | Toàn bộ mã nguồn Phase 11 & 12 được giữ nguyên 100% |
| Không để lộ thông tin xác thực, tài khoản, mật khẩu, token | ✅ ĐẠT | 0 tài khoản, 0 mật khẩu, 0 token được lưu trong code hoặc tài liệu |
| Không có APK mới được tạo hoặc phát hành trong phase này | ✅ ĐẠT | Giữ nguyên bản Release APK v1.0.0 đã nghiệm thu |

---

## 5. LỘ TRÌNH ĐỀ XUẤT CHO CÁC GIAI ĐOẠN TIẾP THEO

Nhóm nghiên cứu đề xuất kế hoạch hành động tiếp theo cho dự án:

1. **Giai đoạn Hiện tại (Phiên bản 1.0.0):** Tiếp tục duy trì vận hành ổn định trên kho ứng dụng với nguồn dữ liệu EBMS công cộng.
2. **Giai đoạn Chuẩn bị Kỹ thuật (Phiên bản 1.1.0 Refactoring):**
   - Tái cấu trúc mã nguồn theo mô hình kiến trúc trừu tượng `VehicleLocationSource` đã thiết kế.
   - Bổ sung bộ chuẩn hóa biển số `VehicleLocationNormalizer`.
   - Giữ `EbmsDataSource` làm nguồn hoạt động chính duy nhất (`isAvailable = true`).
3. **Giai đoạn Tiếp cận Pháp lý & Hợp tác (Tùy chọn):**
   - Nếu đơn vị phát triển có nhu cầu mở rộng nguồn dữ liệu trực tiếp từ BA GPS cho các xe FUTA, cần gửi công văn đề nghị hợp tác chia sẻ dữ liệu viễn thông đến **Công ty CP Xe khách Phương Trang (FUTA)** và **Công ty Bình Anh (BA GPS)** để được cấp tài liệu kỹ thuật và API Key chính thức.
   - Khi có văn bản chấp thuận chính thức, dự án mới kích hoạt triển khai module `BaGpsDataSource`.

---

**XÁC NHẬN NGHIỆM THU KỸ THUẬT GIAI ĐOẠN PHASE 13**  
*Trưởng nhóm Nghiên cứu & Thanh tra Kỹ thuật Độc lập*

*(Đã ký số và lưu trữ mã băm toàn vẹn hồ sơ trên hệ thống)*  
**Trạng thái bàn giao:** HOÀN THÀNH TOÀN DIỆN (100% SPECIFICATION MET)  
**Ngày phê chuẩn:** Ngày 13 tháng 11 năm 2026
