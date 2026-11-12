# HCMC BUS TRACKER – PHASE 10 IMPLEMENTATION PLAN
# OFFLINE TRANSIT DATABASE, SMART LIVE GPS REFRESH & ROUTE-WIDE VEHICLE TRACKING

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Phiên bản kế hoạch:** 1.0  
**Ngày lập:** 2026-11-12  

---

## 1. ĐÁNH GIÁ KIẾN TRÚC HIỆN TẠI VÀ XÁC ĐỊNH NGUYÊN NHÂN GỐC RỄ

Qua kiểm tra trực tiếp mã nguồn (`BusTrackerDatabase.kt`, `BusRepositoryImpl.kt`, `EbmsApiService.kt`, `RoutesViewModel.kt`, `HomeViewModel.kt`, `RouteDetailViewModel.kt`, `PredictionViewModel.kt`, `MapViewModel.kt`, `PersonalScreen.kt`) và đo đạc thực tế trên `https://apicms.ebms.vn`:

### 1.1. Vấn đề 1: Dữ liệu nền offline chưa đầy đủ khi mở ứng dụng
- **Hiện trạng mã nguồn:**
  - `HomeViewModel` / `RoutesViewModel` khi khởi tạo chỉ gọi `syncRoutes()` (`/businfo/getroutesforcombo` — chỉ có ID, mã số, tên tuyến) và `syncStops()` (`/businfo/getstopsforautocomplete`).
  - Trong `BusRepositoryImpl.kt:182-183`, `syncStops()` khởi tạo toàn bộ 6.414 trạm với **`lat = 0.0, lng = 0.0`** vì endpoint autocomplete không chứa tọa độ.
  - `route_variants`, `route_stop_cross_reference` và `route_paths` (polyline) hoàn toàn **rỗng** khi cài mới; chúng chỉ được tải lẻ tẻ theo cơ chế lazy-loading (`ensureRoutePath`, `syncStopsForRouteVar`) khi người dùng bấm vào từng tuyến khi đang có mạng.
  - Bảng `sync_metadata` hiện tại chỉ lưu `syncType`, `lastSyncTimestamp`, `itemCount`, `isSuccess` — **không** lưu `datasetVersion`, `checksum`, tổng số variants, tổng số polylines hay trạng thái toàn vẹn (`VALID`).
- **Hệ quả:** Mở ứng dụng khi offline (hoặc vừa mở lần đầu chưa kịp vào chi tiết tuyến) dẫn tới không có polyline, không có danh sách trạm theo chiều, và bản đồ không có tọa độ trạm thực.

### 1.2. Vấn đề 2: Cơ chế làm mới GPS & Hiểu rõ độ trễ nguồn EBMS
- **Kết quả đo thực nghiệm trực tiếp (10 lần polling liên tiếp trên trạm `4223` Hàm Nghi và các trạm Tuyến `01`):**
  - EBMS API phản hồi nhanh (~`220–320 ms`), header `CF-Cache-Status: DYNAMIC`, `Cache-Control: no-cache` (CDN Cloudflare không cache API động).
  - Độ lệch đồng hồ Server HTTP `Date` so với client chỉ là `-1.64s` (không phải lỗi timezone).
  - Tuy nhiên, trường `dts` từ EBMS trên mọi tuyến (`01`, `44`, `65`, `86`, `102`) luôn có tuổi cố hữu **`112–120 giây`** so với thời gian thực của server, và mỗi xe cập nhật `dts` theo nhịp **~30 giây/lần** (ví dụ `12:19:07` $\rightarrow$ `12:19:37`).
  - Việc polling 1–2 giây không làm GPS mới hơn mà chỉ gây lãng phí băng thông/tải server. Ngoài ra, hệ thống hiện tại chưa kiểm tra đơn điệu thời gian (monotonic `dts` check) để chặn response cũ đến muộn ghi đè bản ghi mới, và chưa xử lý HTTP `429 Retry-After`.
- **Nguyên tắc Phase 10:** Giữ nguyên công thức nội suy `PositionCompensationEngine` và `VehicleInterpolationEngine` đã kiểm chứng ở Phase 9; chỉ tối ưu tầng thu thập dữ liệu, quản lý mốc thời gian, polling có kiểm soát và hiển thị trạng thái trung thực.

### 1.3. Vấn đề 3: Theo dõi vị trí xe trên chi tiết tuyến mới chỉ hỗ trợ 1 xe đơn lẻ
- **Hiện trạng mã nguồn:**
  - `RouteDetailScreen` chỉ liệt kê danh sách trạm và nút mở bản đồ (`interactive_map?routeId={routeId}`).
  - `MapViewModel.trackVehicle(...)` hiện chỉ nhận thông tin 1 chiếc xe được truyền qua tham số điều hướng từ màn hình Dự báo trạm (`StopPredictionScreen`).
  - Trong [`API_CATALOG.md`](file:///e:/MultiGo%20Reworked/docs/phase-0/API_CATALOG.md), EBMS **không có endpoint `livefleet` hay `prediction by route`**, nhưng kết quả kiểm thử thực tế cho thấy `/prediction/predictbystopid/{stopId}` trả về nhiều xe đang tiến về trạm ở khoảng cách tới **31.5 km** (ví dụ Tuyến 102 tại trạm 4223 trả về 6 xe dọc tuyến).
- **Giải pháp xác minh:** Lấy mẫu theo các trạm kiểm tra (checkpoints) phân bố chiến lược theo chiều tuyến trên endpoint đã xác minh `/prediction/predictbystopid/{stopId}` với concurrency giới hạn, sau đó khử trùng lặp thông minh theo biển số (`v`), biến thể (`varId`), `dts` mới nhất và khoảng cách dương nhỏ nhất đến trạm phía trước xe.

---

## 2. ĐÁNH GIÁ LỰA CHỌN KIẾN TRÚC DATABASE (PHƯƠNG ÁN A vs B)

| Tiêu chí | Phương án A: 1 Room DB (`BusTrackerDatabase`) phân tách lớp logic | Phương án B: Tách 2 Room DB (`StaticTransitDB` + `LiveDB`) |
| :--- | :--- | :--- |
| **Quản lý dữ liệu tĩnh vs động** | Static tables + `static_dataset_metadata` trong Room; Live Predictions trong In-Memory Repository Cache (TTL ngắn ~15-60s). | Phải tạo thêm database SQLite thứ 2 cho dữ liệu sống chỉ tồn tại vài chục giây, tăng I/O ghi flash vô ích. |
| **Khóa ngoại & Join Favorite** | `favorite_stops` & `favorite_routes` nằm cùng DB nên dễ join (`INNER JOIN` trên Room Flow), phản hồi tức thì khi bấm yêu thích. | Không thể SQL `JOIN` trực tiếp giữa Favorite và Route/Stop; phải ghép thủ công ở tầng Kotlin. |
| **Migration & Rủi ro** | Chỉ cần nâng `version = 2 -> 3` với `MIGRATION_2_3` bổ sung bảng metadata, giữ nguyên 100% bảng và dữ liệu yêu thích của người dùng. | Rủi ro mất/trùng lặp dữ liệu khi tách file DB cũ sang 2 file mới. |

**Quyết định:** Chọn **Phương án A** — Duy trì một `BusTrackerDatabase` (`version = 3`) duy nhất, phân tách rạch ròi 3 vùng dữ liệu:
1. **Static Transit Data:** `routes`, `route_variants`, `bus_stops`, `bus_stops_fts`, `route_stop_cross_reference`, `route_paths`.
2. **Dataset Integrity Metadata:** `static_dataset_metadata` (`StaticDatasetMetadataEntity`).
3. **User Data (Bất khả xâm phạm khi sync):** `favorite_stops`, `favorite_routes` (+ SharedPreferences `UserPreferences`).
*(Dữ liệu Prediction GPS thời gian thực lưu thuần in-memory với timestamp rõ ràng, không làm phình SQLite).*

---

## 3. CHIẾN LƯỢC MIGRATION & DỮ LIỆU OFFLINE BAN ĐẦU

### 3.1. Database Migration Strategy (`Version 2 -> Version 3`)
- **KHÔNG** xóa dữ liệu cũ, **KHÔNG** dùng destructive migration trên runtime khi nâng cấp bản phát hành hợp lệ.
- Viết `MIGRATION_2_3`:
  ```sql
  CREATE TABLE IF NOT EXISTS `static_dataset_metadata` (
      `id` TEXT NOT NULL PRIMARY KEY,
      `datasetVersion` TEXT NOT NULL,
      `datasetCreatedAt` INTEGER NOT NULL,
      `datasetUpdatedAt` INTEGER NOT NULL,
      `source` TEXT NOT NULL,
      `totalRoutes` INTEGER NOT NULL,
      `totalVariants` INTEGER NOT NULL,
      `totalStops` INTEGER NOT NULL,
      `totalPolylines` INTEGER NOT NULL,
      `checksum` TEXT NOT NULL,
      `lastSyncStatus` TEXT NOT NULL,
      `lastErrorMessage` TEXT
  )
  ```
- Bổ sung chỉ mục tối ưu tìm kiếm tọa độ viewport trong `BusStopEntity` (`Index(value = ["lat", "lng"])`) nếu cần hoặc tối ưu query trực tiếp.

### 3.2. Nguồn dữ liệu cài đặt ban đầu & Đóng gói Asset EBMS hợp lệ
- Sử dụng công cụ xuất dữ liệu chuẩn từ chính server EBMS (`https://apicms.ebms.vn`) trong giai đoạn phát triển để tạo bản snapshot **100% dữ liệu thật đã kiểm định**:
  - Toàn bộ tuyến (`getroutesforcombo` + `getroutebyid`), toàn bộ `RouteVariant` (`getvarsbyroute`), toàn bộ `BusStop` với tọa độ `Lat/Lng` chuẩn xác (`getstopsbyvar` + bổ sung `getstopsforautocomplete`), toàn bộ liên kết thứ tự trạm và toàn bộ polyline (`getpathsbyvar`).
  - Đóng gói nén GZIP tại `core-database/src/main/assets/ebms_static_dataset.json.gz` (kích thước dự kiến < 1 MB, không chứa thông tin nhạy cảm).
- **Cơ chế nạp khởi động an toàn (`ensureInitialStaticDatasetLoaded()`):**
  - Khi mở app: đọc `static_dataset_metadata`. Nếu chưa có dataset `VALID` (cài mới hoặc nâng cấp từ bản cũ thiếu polyline/tọa độ), tự động giải nén asset trên `Dispatchers.IO`, chạy qua `StaticDatasetValidator`, và nạp bằng `database.withTransaction` **một lần duy nhất**.
  - Không bao giờ chạm vào `favorite_stops` hay `favorite_routes`.
  - Thời gian đọc metadata + sẵn sàng bản đồ: `< 50 ms` sau lần nạp đầu, chạy hoàn toàn **100% offline** (không cần chờ mạng).

### 3.3. Quy trình Cập nhật An toàn (Staging -> Validate -> Atomic Transaction)
Khi người dùng chủ động bấm **"Cập nhật dữ liệu"** trong Settings (hoặc đồng bộ lại):
1. Kiểm tra mạng (`NetworkMonitor`). Nếu offline $\rightarrow$ báo lỗi thân thiện, **giữ nguyên 100% DB hiện tại**.
2. Tải toàn bộ payload từ EBMS vào cấu trúc staging trong RAM (`StagingTransitDataset`) với bounded concurrency (`Semaphore(6)`), báo tiến độ theo từng bước (Tuyến $\rightarrow$ Biến thể & Trạm $\rightarrow$ Polyline). Cho phép người dùng hủy (`Job.cancel()`) trong khi đang tải trước bước Transaction.
3. **Validate (`StaticDatasetValidator`):**
   - Kiểm tra `routes.isNotEmpty()`, `variants.isNotEmpty()`, `stops.count { lat != 0.0 && lng != 0.0 } > 0`, `paths.isNotEmpty()`.
   - Kiểm tra tọa độ thuộc phạm vi TP.HCM/lân cận (`9.0..12.0`, `105.0..108.0`), kiểm tra tính toàn vẹn quan hệ (`routeStopRefs` trỏ tới `stopId` và `varId` hợp lệ).
   - Tính toán `SHA-256 checksum` cho bộ dữ liệu đã chuẩn hóa.
4. **Kiểm tra thay đổi:** Nếu `checksum == currentMetadata.checksum` và `currentMetadata.lastSyncStatus == "VALID"` $\rightarrow$ thông báo **"Dữ liệu đã mới nhất"**, chỉ cập nhật `datasetUpdatedAt` nhẹ nhàng mà không ghi đè toàn bộ bảng.
5. **Transaction thay thế nguyên tử:** Trong `database.withTransaction`:
   - Xóa các bảng dữ liệu tĩnh (`route_paths`, `route_stop_cross_reference`, `route_variants`, `bus_stops`, `routes`). *(Tuyệt đối không xóa `favorite_stops`, `favorite_routes`)*.
   - Insert toàn bộ bộ dữ liệu staging đã kiểm định.
   - Ghi nhận `StaticDatasetMetadataEntity` với `lastSyncStatus = "VALID"`.
6. **Rollback:** Nếu bất kỳ ngoại lệ nào xảy ra trước hoặc trong Transaction, Room tự động rollback toàn bộ, giữ nguyên phiên bản dataset cũ đang hoạt động và cập nhật thông báo lỗi.

---

## 4. CẢI TIẾN LIVE GPS REFRESH & CƠ CHẾ QUẢN LÝ PREDICTION

### 4.1. Bảo toàn tuyệt đối thuật toán Phase 5 & Phase 9
- Không sửa công thức trong [`PositionCompensationEngine.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/PositionCompensationEngine.kt) và thuật toán Linear Referencing trong [`VehicleInterpolationEngine.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/engine/VehicleInterpolationEngine.kt).
- Đối chiếu chạy lại bộ regression test trước và sau Phase 10 phải cho kết quả y hệt trên cùng bộ input.

### 4.2. Cải tiến Polling & Chống ghi đè ngược (Monotonic Prediction Manager)
- Tạo **`RoutePredictionTracker`** / **`PredictionFreshnessManager`**:
  - Lưu riêng 4 mốc thời gian cho từng request/bản ghi: `requestStartedAt`, `responseReceivedAt`, `gpsTimestampMillis`, `predictionReceivedAt`.
  - Hỗ trợ kiểm tra **HTTP `429` + `Retry-After`** header và exponential backoff khi gặp lỗi mạng (`15s -> 30s -> 60s -> 120s`).
  - Chu kỳ polling mặc định giữ ở **15 giây** (khớp với website gốc `buyttphcm.com.vn` và phù hợp nhịp ~30s thay đổi `dts` của server EBMS), cho phép người dùng chỉnh `[10s, 15s, 30s, 60s]` trong Settings nhưng chặn cứng mức `< 10s`.
  - **Chống race condition & response trễ:** Khi response đến, chỉ chấp nhận cập nhật xe `plateNumber` nếu `newGpsTimestamp >= currentGpsTimestamp` (trường hợp cùng `dts`, ưu tiên bản ghi vừa nhận hoặc trạm gần hơn).
  - **Khi `dts` không đổi qua nhiều lần polling:** Đánh dấu trạng thái dữ liệu chưa đổi (`isGpsTimestampUnchanged = true`), **không** tạo animation giả di chuyển marker nếu `speed == 0` hoặc `dts` đóng băng vượt ngưỡng 120s; giữ nguyên vị trí hợp lệ cuối cùng và hiển thị đúng thời điểm GPS thực tế.
  - **Lifecycle-aware:** Tự động tạm dừng polling khi ứng dụng xuống Background (`ON_PAUSE` / `ON_STOP`) và khôi phục khi quay lại Foreground (`ON_RESUME`), đồng thời hủy ngay các HTTP call đang chờ khi đổi chiều tuyến hoặc thoát màn hình.

---

## 5. HIỂN THỊ TẤT CẢ XE ĐANG HOẠT ĐỘNG TRÊN CHI TIẾT TUYẾN (ROUTE-WIDE VEHICLE TRACKING)

### 5.1. Chiến lược thu thập dữ liệu xe toàn tuyến trên Endpoint EBMS đã xác minh
- Vì EBMS chỉ cung cấp `/prediction/predictbystopid/{stopId}` (không có `livefleet` theo tuyến), áp dụng thuật toán **Strategic Checkpoint Sampling**:
  - Với danh sách $N$ trạm đã sắp xếp theo chiều tuyến (`routeStops` từ 0 đến $N-1$):
    - Nếu $N \le 4$: truy vấn tối đa các trạm cuối/giữa (tối đa 3–4 trạm).
    - Nếu $N > 4$: chọn tối đa **5 trạm kiểm tra (checkpoints)** phân bố đều từ 25% lộ trình đến bến cuối (ví dụ chỉ số `[N*0.25, N*0.50, N*0.75, N-2, N-1]`). Kết hợp thực nghiệm cho thấy 1 trạm cuối tuyến có thể quét xe cách xa hàng chục km, việc chọn 4–5 trạm rải đều đảm bảo bao phủ 100% xe ở các phân đoạn đầu, giữa và cuối tuyến (kể cả tuyến dài >30 km hay tuyến vòng).
  - Gọi song song có kiểm soát qua `Semaphore(permits = 4)` với timeout `8,000 ms` mỗi request và hủy ngay lập tức qua `coroutineScope` nếu người dùng đổi tuyến/đổi chiều hoặc đóng bản đồ.

### 5.2. Thuật toán Tổng hợp & Khử trùng lặp Xe (`RouteVehicleAggregator`)
1. Lọc đúng các khối `Prediction` khớp `prediction.routeId == activeRouteId && prediction.varId == activeVarId`.
2. Đọc trường trạng thái `sts` (`ArrivalVehicle.status`) và `dts` (`timestampIso`):
   - Phân loại rõ: GPS hợp lệ (`LIVE_PROJECTED` / `STATIONARY` / `AT_STOP`), GPS cũ (`STALE_DATA` > 120s), Thiếu dữ liệu (`INVALID_TIMESTAMP` / `INVALID_SPEED`).
3. Khử trùng lặp theo khóa `(routeId, varId, normalizedPlate)`:
   - Nếu cùng biển số xuất hiện ở nhiều checkpoint:
     1) Chọn bản ghi có **`gpsTimestampMillis` lớn hơn (mới hơn)**.
     2) Nếu `gpsTimestampMillis` bằng nhau: chọn checkpoint gần xe nhất có **`distanceMeters` dương hợp lệ nhỏ nhất** (giảm thiểu tối đa sai số tích lũy polyline).
4. Chạy `VehicleInterpolationEngine.interpolatePosition(...)` cho TẤT CẢ xe hợp lệ sau khử trùng lặp trên `Dispatchers.Default`, kết hợp làm mượt với danh sách vị trí trước đó `Map<String, InterpolatedVehiclePosition>`.
5. Không tạo marker nếu `interpolatePosition` trả về `null` hoặc polyline không hợp lệ.

### 5.3. Thiết kế Marker & Tương tác Bản đồ Toàn tuyến
- Hỗ trợ hiển thị **đồng thời toàn bộ marker xe buýt** trên lớp MapLibre (`ROUTE_VEHICLES_SOURCE_ID` / `SymbolLayer` + `CircleLayer` phân biệt rõ xe đang được chọn và các xe khác):
  - Xe thường trên tuyến: Icon vòng tròn xanh ngọc (`#00897B`) + viền trắng + nhãn biển số gọn (`textField = plateNumber`).
  - Xe đang được chọn (`selectedVehiclePlate`): Phóng lớn nổi bật màu cam/đỏ đô (`#E65100` hoặc `#D32F2F`), viền trắng dày, có thể bấm chọn trực tiếp trên bản đồ (`queryRenderedFeatures` ưu tiên kiểm tra lớp xe trước lớp trạm!).
  - Nút hành động nhanh trên `RouteDetailScreen` & TopBar bản đồ: **"Xem toàn tuyến & Xe trực tuyến (N xe)"**.
  - Khi bấm chuyển chiều (Lượt đi $\leftrightarrow$ Lượt về): hủy job polling của chiều cũ, xóa sạch marker chiều cũ, vẽ polyline & trạm mới lập tức từ SQLite offline, sau đó kích hoạt thu thập xe cho chiều mới.

---

## 6. THIẾT KẾ SETTINGS ("DỮ LIỆU ỨNG DỤNG / DỮ LIỆU TUYẾN XE")

Trong `PersonalScreen.kt` (Tab Cài đặt) & `PersonalViewModel.kt`:
- Bổ sung card **"Dữ liệu tuyến xe (Offline Transit Data)"** hiển thị trực tiếp từ `Flow<StaticDatasetMetadata?>`:
  - Phiên bản dataset (`datasetVersion`), nguồn (`EBMS / TP.HCM`), trạng thái (`Hợp lệ - Sẵn sàng offline` / `Đang cập nhật` / `Lỗi`).
  - Thời gian cập nhật gần nhất (`dd/MM/yyyy HH:mm:ss`).
  - Các chỉ số thực tế: **Tổng số tuyến**, **Tổng số chiều tuyến (variants)**, **Tổng số trạm dừng**, **Tổng số polyline lộ trình**.
- Nút **"Cập nhật dữ liệu"** (có thanh tiến trình `LinearProgressIndicator` + thông báo từng giai đoạn + nút Hủy khi đang tải trước Transaction).
- **Không** có nút "Cập nhật GPS thủ công" trong khối này — dữ liệu GPS được quản lý tự động theo lifecycle bởi Polling Engine.

---

## 7. TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA)

1. **Offline-First Startup:** Gỡ app cài mới (hoặc nâng cấp từ Phase 9), bật chế độ Máy bay ngay lần mở đầu tiên $\rightarrow$ Danh sách tuyến, biến thể (Lượt đi/Lượt về), danh sách trạm dừng (có tọa độ Lat/Lng thực) và Polyline hiển thị đầy đủ 100% không cần mạng.
2. **Safe Dataset Update & Rollback:** Cập nhật khi mất mạng hoặc payload lỗi $\rightarrow$ Giữ nguyên 100% dataset cũ và dữ liệu Yêu thích (`favorite_stops`, `favorite_routes`); nếu dataset không đổi (`checksum` khớp) $\rightarrow$ Báo "Dữ liệu đã mới nhất", không ghi đè vô ích.
3. **GPS Freshness & No Regression:** Toàn bộ test Phase 9 (`PositionCompensationEngineTest`, `VehicleInterpolationEngineTest`) tiếp tục PASS với kết quả giữ nguyên 100%; có unit test kiểm chứng monotonic `dts`, response trễ bị loại bỏ, `dts` không đổi không tạo animation giả, và xử lý `429 Retry-After`.
4. **Route-Wide Tracking:** Mở bản đồ một tuyến hoạt động $\rightarrow$ hiển thị nhiều xe cùng lúc trên polyline, không trùng lặp biển số, bấm chọn từng xe xem được biển số, vận tốc, cự ly đã bù trừ, ETA, trạm đích, tuổi GPS; đổi chiều tuyến xóa sạch xe chiều cũ và nạp xe chiều mới.
5. **Kiểm thử & Build:** Pass toàn bộ Unit Tests + Integration Tests (Robolectric/Room DB tests), Build Debug APK và Signed Release APK thành công với keystore hiện tại (`com.hcmcbustracker.app`).
