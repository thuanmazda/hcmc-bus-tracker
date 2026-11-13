# BÁO CÁO KHẢO SÁT VÀ PHÁT HIỆN CÁC NGUỒN DỮ LIỆU XE BUÝT CÔNG KHAI TẠI TP.HCM
## (PHASE 15 – PUBLIC API DISCOVERY)

---

**Dự án:** HCMC Bus Tracker  
**Phiên bản mục tiêu:** `1.1.0`  
**Giai đoạn:** Nghiên cứu / Phát hiện API / Đo kiểm dữ liệu (Research-only / API Discovery)  
**Tình trạng:** NGHIÊN CỨU THUẦN TÚY – **TUYỆT ĐỐI KHÔNG SỬA CODE PRODUCTION**  
**Ngày thực hiện:** Ngày 13 tháng 11 năm 2026  
**Chủ thể khảo sát:** Nhóm Nghiên cứu Kỹ thuật & Khảo sát Dữ liệu Công cộng (HCMC Bus Tracker Core Team)  

---

## 1. TỔNG QUAN VÀ PHƯƠNG PHÁP KHẢO SÁT

Hệ thống **HCMC Bus Tracker** hiện đang vận hành ổn định trên nền tảng nguồn dữ liệu EBMS (`https://apicms.ebms.vn/`).
Nhằm mục đích nâng cao độ phủ phương tiện, độ mới dữ liệu và độ chính xác của vị trí xe trong phiên bản `1.1.0`, Phase 15 thực hiện quy trình khảo sát có hệ thống, đối soát đa nguồn nhằm phát hiện:
1. Các cổng dữ liệu mở chính thức của cơ quan nhà nước.
2. Các nguồn dữ liệu chuẩn hóa quốc tế (GTFS, GTFS-Realtime).
3. Các nền tảng tra cứu giao thông công cộng đang hoạt động tại TP.HCM (MultiGo, BusMap).
4. Các hệ thống điều hành viễn thám phương tiện từ các đơn vị vận tải (SaigonBus, FUTA Bus Lines, các Hợp tác xã vận tải xe buýt).

### Quy tắc đạo đức và pháp lý:
- Chỉ khảo sát và đánh giá các nguồn có tài liệu công bố công khai, giấy phép dữ liệu mở (Open Data License) hoặc cơ chế đối tác chính thức.
- **Tuyệt đối không:** Bóc tách (scrape) API nội bộ, vượt qua xác thực người dùng, giả mạo ứng dụng di động hoặc khai thác lỗ hổng hệ thống.

---

## 2. MA TRẬN TỔNG HỢP CÁC NGUỒN DỮ LIỆU KHẢO SÁT TẠI TP.HCM

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                   HỆ THỐNG DỮ LIỆU GIAO THÔNG CÔNG CỘNG TẠI TP.HCM                     │
├───────────────────────────────┬────────────────────────────────────────────────────────┤
│ Cấp 1: Cơ quan Quản lý        │ Sở Xây dựng / Sở GTVT TP.HCM & Trung tâm QLGTCC        │
│ Cổng máy chủ trung tâm:       │ EBMS (apicms.ebms.vn) - Tích hợp bởi FPT IS           │
├───────────────────────────────┼────────────────────────────────────────────────────────┤
│ Cấp 2: Ứng dụng Tiêu dùng     │ MultiGo (Go Labs / FPT IS) & BusMap (Phenikaa MaaS)    │
│ Giao tiếp dữ liệu:            │ Kết nối API trung tâm nội bộ / Không mở Public API    │
├───────────────────────────────┼────────────────────────────────────────────────────────┤
│ Cấp 3: Doanh nghiệp Vận tải   │ SaigonBus, FUTA, HTX 19/5, HTX Quyết Thắng, v.v.       │
│ Hệ thống viễn thám:           │ BA GPS (Bình Anh), Vietmap GSHT - Đẩy dữ liệu về EBMS  │
└───────────────────────────────┴────────────────────────────────────────────────────────┘
```

### Bảng tổng hợp chi tiết 7 ứng viên dữ liệu (Candidate Matrix):

| STT | Ứng viên nguồn dữ liệu | Đơn vị chủ quản / Nhà cung cấp | Loại hình cung cấp | Tình trạng tài liệu công khai | Khả năng truy cập hợp pháp |
| :---: | :--- | :--- | :--- | :--- | :---: |
| **1** | **EBMS API (`apicms.ebms.vn`)** | Trung tâm Quản lý Giao thông công cộng TP.HCM / FPT IS | Cổng dữ liệu vận tải công cộng mở | Có tài liệu tích hợp từ các dự án cộng đồng; Endpoint REST mở không khóa API | **HỢP PHÁP / ĐANG SỬ DỤNG** *(Nguồn nền tảng)* |
| **2** | **Cổng Dữ liệu mở TP.HCM (`data.hochiminhcity.gov.vn`)** | UBND TP. Hồ Chí Minh / Sở Thông tin & Truyền thông | Cổng Open Data của Chính quyền đô thị | Công bố danh mục dữ liệu hạ tầng (camera, bãi đỗ xe); **Chưa có Vehicle Positions Realtime** | **HỢP PHÁP / THIẾU STREAM REALTIME** |
| **3** | **GTFS / GTFS-Realtime TP.HCM** | Chuẩn dữ liệu mở quốc tế (Mobility Database, Transitland) | Chuẩn nạp dữ liệu vận tải công cộng mở | **Không có feed chính thức** được công bố cho TP.HCM trên các kho dữ liệu quốc tế | **CHƯA KHẢ DỤNG** *(Chưa được công bố rộng rãi)* |
| **4** | **MultiGo Backend (`golabs.vn` / `gobus.vn`)** | Công ty Cổ phần Công nghệ Go Labs & FPT IS | Nền tảng ứng dụng giao thông tích hợp chính thức | Ứng dụng đóng gói người dùng cuối; **Không có cổng Developer Open API công khai** | ⚠️ **CẦN THỎA THUẬN ĐỐI TÁC (B2B)** |
| **5** | **BusMap API (`phenikaamaas.com`)** | Công ty Cổ phần Công nghệ Phenikaa MaaS | Dịch vụ công nghệ giao thông thông minh B2B | Cung cấp API tùy chỉnh có trả phí cho đối tác doanh nghiệp; **Không có Open API miễn phí** | ⚠️ **CẦN KÝ HỢP ĐỒNG THƯƠNG MẠI** |
| **6** | **BA GPS FUTA (`futagps.binhanh.vn`)** | Công ty TNHH Phát triển Công nghệ Điện tử Bình Anh | Nền tảng giám sát hành trình nội bộ FUTA | Cổng WebForms nội bộ sắp ngừng hoạt động; **Chưa cấp quyền tích hợp và phân phối công khai** | ❌ **NO-GO FOR SCRAPING** *(Phase 13)* |
| **7** | **Hệ thống GSHT SaigonBus / Vietmap** | Công ty Cổ phần Xe khách Sài Gòn & Vietmap | Nền tảng quản trị đội xe nội bộ doanh nghiệp | Không cung cấp API công khai; dữ liệu vận hành được đẩy trực tiếp về EBMS | ❌ **KHÔNG CÓ KÊNH CÔNG KHAI** |

---

## 3. PHÂN TÍCH ĐÁNH GIÁ TỪNG NGUỒN ỨNG VIÊN

### 3.1. Ứng viên 1: EBMS API (`https://apicms.ebms.vn/`)
* **Chủ thể quản lý:** Trung tâm Quản lý Giao thông công cộng TP.HCM (thuộc Sở GTVT / Sở Xây dựng TP.HCM).
* **Đặc điểm:**
  - Là cổng backend hợp nhất dữ liệu từ tất cả các đơn vị vận tải xe buýt trên toàn địa bàn TP.HCM.
  - Cung cấp: Toàn bộ danh mục tuyến (`getroutesforcombo`), lộ trình biến thể (`getpathsbyvar`), danh sách trạm dừng (`getstopsbyvar`, `getstopsinbounds`), và dự báo thời gian thực (`predictbystopid`, `predictnextstops`).
* **Ưu điểm vượt trội:**
  - Bao phủ toàn diện 100% mạng lưới xe buýt trợ giá và không trợ giá của thành phố.
  - Không phân biệt nhà xe (SaigonBus, FUTA, HTX 19/5 đều có mặt).
  - Hoạt động ổn định, mở công khai, không đòi hỏi trả phí hay ký hợp đồng thuê bao viễn thông.
* **Hạn chế kỹ thuật:**
  - Không trả về tọa độ `(lat, lng)` trực tiếp; chỉ trả về khoảng cách $d$ (mét) dọc tuyến, phụ thuộc vào thuật toán nội suy polyline.

### 3.2. Ứng viên 2: Cổng Dữ liệu Mở TP.HCM (`data.hochiminhcity.gov.vn`)
* **Chủ thể quản lý:** UBND TP.HCM.
* **Đặc điểm khảo sát:**
  - Cổng dữ liệu mở cung cấp các tập dữ liệu tĩnh: Danh mục điểm trông giữ xe, vị trí camera giám sát giao thông đô thị, bản đồ phân vùng hành chính (thông qua HCMGIS `hcmgis.vn`).
  - **Kết quả điều tra:** Cổng **hoàn toàn KHÔNG cung cấp luồng dữ liệu thời gian thực (Real-time Stream)** về tọa độ xe buýt hoặc API vị trí phương tiện đang chạy. Dữ liệu chỉ dừng ở mức báo cáo thống kê định kỳ và lớp bản đồ tĩnh.

### 3.3. Ứng viên 3: Nguồn dữ liệu mở chuẩn quốc tế GTFS / GTFS-Realtime
* **Tổ chức kiểm định:** Mobility Database (`mobilitydatabase.org`) và Transitland (`transit.land`).
* **Kết quả điều tra:**
  - Tra cứu toàn bộ cơ sở dữ liệu quốc tế về giao thông công cộng: **Thành phố Hồ Chí Minh chưa có feed GTFS tĩnh hoặc GTFS-Realtime công khai chính thức** được đăng ký trên các cổng dữ liệu mở toàn cầu.
  - Một số nghiên cứu khoa học của Trường Đại học Việt - Đức (VGU) và các viện nghiên cứu có sử dụng dữ liệu mẫu GTFS nội bộ phục vụ đề tài nghiên cứu đô thị, nhưng đây không phải là nguồn cấp dữ liệu trực tuyến đang hoạt động.

### 3.4. Ứng viên 4: Nền tảng MultiGo (`golabs.vn` / `gobus.vn`)
* **Đơn vị phát triển:** Công ty Cổ phần Công nghệ Go Labs phối hợp cùng Trung tâm Quản lý Giao thông công cộng TP.HCM và FPT IS.
* **Đặc điểm kiến trúc:**
  - MultiGo là ứng dụng giao thông công cộng chính thức phục vụ hành khách (tích hợp xe buýt, metro số 1, buýt sông, vé điện tử MultiPass).
  - Về bản chất kỹ thuật, tầng dữ liệu xe buýt của MultiGo **cũng lấy nguồn từ hệ thống trung tâm EBMS của Trung tâm QLGTCC**.
  - **Tình trạng API:** Go Labs không cung cấp cổng Self-service Developer Portal hay Open API công khai cho bên thứ ba. Các endpoint di động được ký mã riêng và phục vụ duy nhất cho client MultiGo. Muốn khai thác dữ liệu chuẩn hóa từ MultiGo bắt buộc phải thông qua thỏa thuận đối tác phát triển (B2B Partnership).

### 3.5. Ứng viên 5: Nền tảng BusMap (`phenikaamaas.com`)
* **Đơn vị phát triển:** Công ty Cổ phần Công nghệ Phenikaa MaaS.
* **Đặc điểm kiến trúc:**
  - BusMap sở hữu hệ thống bản đồ số và thuật toán tìm đường chuyên sâu, phục vụ dịch vụ B2B cho các khách hàng lớn (VinBus, Grab).
  - **Tình trạng API:** Phenikaa MaaS cung cấp giải pháp API thương mại tùy chỉnh (Custom Enterprise API). Không có cổng API miễn phí hay tài liệu mở cho cộng đồng. Việc tích hợp đòi hỏi đàm phán hợp đồng thương mại và trả phí bản quyền dịch vụ theo lưu lượng (API calls).

### 3.6. Ứng viên 6 & 7: Hệ thống Viễn thám Doanh nghiệp (BA GPS, Vietmap)
* **SaigonBus & FUTA Bus Lines:** Các doanh nghiệp vận tải sử dụng hộp đen giám sát hành trình của Bình Anh (BA GPS) hoặc Vietmap.
* **Đặc điểm:** Các hệ thống này là công cụ quản trị nội bộ của doanh nghiệp (quản lý lái xe, chấm công, mức tiêu hao nhiên liệu, kiểm tra vi phạm theo Nghị định 10 và QCVN 31). Toàn bộ dữ liệu vị trí bắt buộc phải chuyển tiếp (forward) về máy chủ của Cục Đường bộ Việt Nam và Trung tâm QLGTCC TP.HCM (EBMS). Doanh nghiệp không mở API ra công chúng.

---

## 4. KẾT LUẬN VÀ PHÂN HẠNG ỨNG VIÊN

| Xếp hạng | Nguồn dữ liệu | Tình trạng xác minh | Phân loại đề xuất |
| :---: | :--- | :--- | :---: |
| **Hạng 1** | **EBMS API (`apicms.ebms.vn`)** | Nguồn chính thống, bao phủ toàn diện 100% mạng lưới xe buýt TP.HCM, truy cập mở công khai, không thu phí. | 🟢 **GO (Nền tảng cốt lõi)** |
| **Hạng 2** | **MultiGo / Go Labs B2B** | Cùng hệ sinh thái điều hành giao thông thành phố; có tiềm năng kỹ thuật cao nếu đạt được thỏa thuận hợp tác dữ liệu chính thức. | 🟡 **CONDITIONAL GO (Cần MOU)** |
| **Hạng 3** | **Phenikaa MaaS (BusMap API)** | Năng lực công nghệ cao; nhưng là dịch vụ thương mại có phí, không có Open Data. | 🟡 **CONDITIONAL GO (Cần Ngân sách)** |
| **Hạng 4** | **BA GPS / Vietmap / Đơn vị vận tải** | Thiếu kênh API công khai; rủi ro pháp lý cao nếu tự ý bóc tách tài khoản giám sát nội bộ. | 🔴 **NO-GO (Bị nghiêm cấm)** |
| **Hạng 5** | **GTFS-RT / Cổng Open Data TP.HCM** | Chưa có luồng phát dữ liệu phương tiện thời gian thực. | ⚪ **CHƯA KHẢ DỤNG** |

**Nhận định bao quát:**  
Tại thời điểm hiện tại, **EBMS (`apicms.ebms.vn`) vẫn là nguồn dữ liệu mở duy nhất có tính pháp lý đầy đủ, bao phủ 100% mạng lưới giao thông công cộng TP.HCM và khả dụng cho mục đích tra cứu công cộng**. Mọi ứng dụng tra cứu xe buýt trên thị trường TP.HCM đều hội tụ về cơ sở dữ liệu điều hành tập trung này.
