# STATIC TRANSIT DATABASE DESIGN (PHASE 10)

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Phạm vi:** Offline Static Transit Database, Pre-packaged Verified EBMS Seed, Safe Update Staging & Integrity Governance  

---

## 1. Mục tiêu kiến trúc

Trong các Phase 1–9, ứng dụng chỉ đồng bộ danh mục rút gọn (`/businfo/getroutesforcombo` và `/businfo/getstopsforautocomplete` với tọa độ `0.0, 0.0`) khi khởi tạo, còn chi tiết tuyến, `RouteVariant`, danh sách trạm theo chiều và `RoutePath` polyline được tải theo kiểu lazy-loading khi người dùng bấm mở từng tuyến. Điều này gây ra 2 vấn đề lớn:
1. Mở app lần đầu khi mất mạng hoặc chưa từng bấm vào tuyến khiến bản đồ không có polyline, không có trạm thực tế và danh sách chiều trống.
2. Không có bảng quản lý phiên bản/checksum (`datasetVersion`, `checksum`, `lastSyncStatus`) để phát hiện dữ liệu đã lỗi hoặc tránh ghi đè dữ liệu khi tải thất bại giữa chừng.

**Phase 10 giải quyết triệt để** bằng mô hình **Offline-First Verified Transit Database**:
- Dữ liệu tĩnh toàn thành phố (tuyến, biến thể, điểm dừng có tọa độ GPS thực, liên kết thứ tự trạm theo chiều và polyline) được đóng gói sẵn từ chính server chính thống `https://apicms.ebms.vn` và tự động nạp một lần vào Room khi khởi động ứng dụng mà **không cần mạng**.
- Dữ liệu GPS dự báo thời gian thực vẫn tách bạch thuần túy tại tầng bộ nhớ (in-memory telemetry cache), **không** ghi làm phình SQLite.

---

## 2. Đánh giá phương án lưu trữ (1 Room DB vs 2 Room DB)

| Tiêu chí | Phương án A: Một `BusTrackerDatabase` (`v3`) phân tách tầng logic (Đã chọn) | Phương án B: Tách riêng `StaticTransitDB` & `User/LiveDB` |
| :--- | :--- | :--- |
| **Truy vấn Yêu thích (`favorite_stops`, `favorite_routes`)** | Thực hiện `INNER JOIN` trực tiếp bằng SQLite Room Flow (`FavoriteStopDao`, `FavoriteRouteDao`), phản hồi UI tức thì. | Không thể SQL `JOIN` qua 2 file SQLite; phải map/filter thủ công trong RAM. |
| **An toàn khi cập nhật Static Data** | Transaction xóa/nạp lại **chỉ 5 bảng tĩnh** (`route_paths`, `route_stop_cross_reference`, `route_variants`, `bus_stops`, `routes`); bảng yêu thích nằm ngoài danh sách xóa. | Cô lập file vật lý nhưng làm phức tạp DI Hilt, backup và migration đa file. |
| **Migration từ Phase 9** | Nâng `version = 2 -> 3` chỉ bằng 1 lệnh `CREATE TABLE IF NOT EXISTS static_dataset_metadata` (`MIGRATION_2_3`). | Phải copy dữ liệu yêu thích sang DB mới, tiềm ẩn rủi ro mất dữ liệu người dùng. |

---

## 3. Phân tầng dữ liệu trong `BusTrackerDatabase` (`version = 3`)

1. **Nhóm bảng dữ liệu giao thông tĩnh (Static Transit Tables — Quản lý nguyên tử bởi `StaticDatasetManager`):**
   - `routes` (`RouteEntity`)
   - `route_variants` (`RouteVariantEntity`)
   - `bus_stops` (`BusStopEntity`) + `bus_stops_fts` (`BusStopFtsEntity` FTS4)
   - `route_stop_cross_reference` (`RouteStopCrossReference`, index `["routeId", "varId"]`, `["stopId"]`, unique `["routeId", "varId", "stopId"]`)
   - `route_paths` (`RoutePathEntity`, `compositeKey = "${routeId}_${varId}"`)
2. **Nhóm bảng kiểm soát toàn vẹn (Dataset Integrity Metadata):**
   - `static_dataset_metadata` (`StaticDatasetMetadataEntity`, `id = "ACTIVE_DATASET"`):
     - `datasetVersion`, `datasetCreatedAt`, `datasetUpdatedAt`, `source`
     - `totalRoutes`, `totalVariants`, `totalStops`, `totalPolylines`
     - `checksum` (SHA-256 chuẩn hóa), `lastSyncStatus` (`UNINITIALIZED`, `SEEDING`, `SYNCING`, `VALID`, `FAILED`), `lastErrorMessage`
3. **Nhóm dữ liệu cá nhân (User Data — Bất khả xâm phạm):**
   - `favorite_stops` (`FavoriteStopEntity`)
   - `favorite_routes` (`FavoriteRouteEntity`)
   - `sync_metadata` (`SyncMetadataEntity`) & SharedPreferences (`UserPreferences`)

---

## 4. Quy trình nạp Seed khi khởi động (`ensureInitialStaticDatasetLoaded`)

- Gọi bất đồng bộ trên `Dispatchers.IO` ngay trong [`HcmcBusTrackerApp.onCreate()`](file:///e:/MultiGo%20Reworked/app/src/main/java/com/hcmcbustracker/app/HcmcBusTrackerApp.kt#L20-L26) và bảo vệ idempotent qua `seedMutex` + gọi bổ sung ở `HomeViewModel`, `RoutesViewModel`, `StopsViewModel`, `MapViewModel` nếu người dùng mở màn hình trước khi app scope xong.
- Kiểm tra fast-path: nếu `static_dataset_metadata` tồn tại với `lastSyncStatus == "VALID"` và `routeCount > 0 && variantCount > 0 && pathCount > 0` $\rightarrow$ trả về `true` ngay lập tức, **0 lần đọc asset**, **0 gọi mạng**.
- Nếu thiếu dữ liệu (cài mới hoặc vừa nâng cấp từ v2 chưa có đầy đủ variants/polylines):
  1. Đọc file nén asset [`ebms_static_dataset.json.gz`](file:///e:/MultiGo%20Reworked/core-database/src/main/assets/ebms_static_dataset.json.gz) bằng `GZIPInputStream`.
  2. Giải mã thành [`StaticSeedDataset`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/seed/StaticSeedDataset.kt).
  3. Kiểm duyệt qua [`StaticDatasetValidator`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/seed/StaticDatasetValidator.kt).
  4. Thực thi `database.withTransaction` thay thế 5 bảng tĩnh theo từng lô `chunked(500)` để tuân thủ giới hạn SQLite bind parameters (`<= 999`) trên Android 8+ (API 26+), đồng thời **giữ nguyên 100%** `favorite_stops` và `favorite_routes`.

---

## 5. Bộ kiểm duyệt & Staging-Swap an toàn (`StaticDatasetValidator` & `StaticDatasetManager`)

### 5.1. Quy tắc kiểm duyệt (`StaticDatasetValidator.validateAndPackage`)
Mọi bộ dữ liệu trước khi commit vào Room (từ seed asset hay tải từ EBMS Live API) đều phải vượt qua toàn bộ kiểm tra:
- **Tính đầy đủ:** `routes`, `variants`, `stops`, `paths`, `cleanRefs` đều phải khác rỗng.
- **Tính hợp lệ khóa & tên:** `route.id > 0 && route.name.isNotBlank()`; `variant.id > 0` gắn đúng `routeId` tồn tại; `stop.id > 0 && stop.name.isNotBlank()`.
- **Phạm vi địa lý TP.HCM & Côn Đảo:** Các trạm có tọa độ khác `(0.0, 0.0)` phải thỏa `lat in 8.0..13.0` và `lng in 104.0..109.0` (bao phủ cả khu vực TP.HCM và 38 trạm Tuyến 173 tại Côn Đảo ở `lat ~8.68..8.73`). Ít nhất `geoValidStopCount > 0`.
- **Tính hợp lệ Polyline:** Mỗi `RoutePathEntity` phải thuộc `(routeId, varId)` hợp lệ và `pointCount >= 2`.
- **Canonical Checksum:** `computeCanonicalSha256(...)` sắp xếp tiền định toàn bộ `routes`, `variants`, `stops` (làm tròn `%.5f`), `crossRefs`, `paths` và băm SHA-256.

### 5.2. Cơ chế Cập nhật thủ công từ Settings (`updateFullStaticDatasetFromEbms`)
1. Kiểm tra kết nối qua `ConnectivityManager` (`isNetworkConnected`); nếu offline $\rightarrow$ trả `DatasetUpdateOutcome.Failed` và giữ nguyên DB cũ.
2. Tải tuần tự có kiểm soát concurrency (`Semaphore(6)`) trong RAM theo 4 giai đoạn: Danh mục tuyến $\rightarrow$ Chi tiết tuyến & biến thể $\rightarrow$ Điểm dừng có tọa độ & thứ tự $\rightarrow$ Polyline, báo tiến độ qua `MutableStateFlow<DatasetSyncProgress?>` và cho phép hủy (`Job.cancel()`).
3. Chạy `StaticDatasetValidator`. Nếu `Invalid` $\rightarrow$ **không mở transaction ghi đè**, giữ nguyên dataset hiện hành.
4. So sánh `staged.checksum == currentMeta.checksum`:
   - Nếu trùng khớp và dataset hiện tại đang `VALID` $\rightarrow$ chỉ cập nhật `datasetUpdatedAt`, trả `DatasetUpdateOutcome.AlreadyUpToDate` (UI hiển thị `"Dữ liệu đã mới nhất (không cần ghi lại database)."`).
5. Chỉ khi có thay đổi và validation hợp lệ $\rightarrow$ chạy `commitValidatedDatasetAtomically` trong `database.withTransaction`. Nếu có bất kỳ ngoại lệ/hủy nào, SQLite transaction rollback hoàn toàn.

---

## 6. Giao diện Quản lý Dữ liệu trong Settings (`PersonalScreen.kt`)

Trong tab **Cài đặt** dưới nhóm **"Dữ liệu ứng dụng"**, card **"Dữ liệu tuyến xe (Offline)"** hiển thị:
- Badge trạng thái: `"Hợp lệ"` / `"Đang cập nhật"` / `"Lỗi"` / `"Chưa khởi tạo"`.
- Bảng thông số thời gian thực từ `StaticDatasetMetadata`: Phiên bản, Cập nhật gần nhất, Nguồn dữ liệu, Tổng số tuyến, Tổng số chiều tuyến, Tổng số điểm dừng, Polyline hợp lệ.
- Thanh tiến trình `LinearProgressIndicator` hiển thị từng bước khi cập nhật, kèm nút **Hủy** (ẩn nút Hủy khi đã vào pha commit không thể hủy).
- **Không** chứa nút "Cập nhật GPS thủ công" trong khối này — việc làm mới GPS thời gian thực do Polling Engine quản lý tự động theo lifecycle màn hình.
