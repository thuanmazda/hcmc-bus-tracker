# STATIC TRANSIT DATABASE MIGRATION & SEED REPORT (PHASE 10)

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Schema Database:** Room `BusTrackerDatabase` (`version = 2 -> version = 3`)  
**Asset Seed chính thức:** `core-database/src/main/assets/ebms_static_dataset.json.gz`  

---

## 1. Chi tiết Migration `Version 2 -> Version 3`

### 1.1. Loại bỏ Destructive Migration
Trong [`DatabaseModule.kt`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/di/DatabaseModule.kt#L72-L82), cấu hình `.fallbackToDestructiveMigration()` trước đây đã được **gỡ bỏ hoàn toàn** và thay bằng đường dẫn nâng cấp bảo toàn dữ liệu tường minh:
```kotlin
Room.databaseBuilder(context, BusTrackerDatabase::class.java, BusTrackerDatabase.DATABASE_NAME)
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
    .build()
```

### 1.2. Định nghĩa `MIGRATION_2_3`
[`MIGRATION_2_3`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/di/DatabaseModule.kt#L38-L94) thực hiện hai tác vụ nguyên tử mà không làm thay đổi hay xóa bất kỳ hàng nào trong `favorite_stops`, `favorite_routes`, `sync_metadata`:
1. Tạo bảng `static_dataset_metadata`:
```sql
CREATE TABLE IF NOT EXISTS `static_dataset_metadata` (
    `id` TEXT NOT NULL,
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
    `lastErrorMessage` TEXT,
    PRIMARY KEY(`id`)
)
```
2. Tái tạo bảng `route_variants` với khóa chính tổ hợp `PRIMARY KEY(routeId, id)` và giữ nguyên các chỉ mục (`index_route_variants_routeId`, `index_route_variants_routeNo`), đồng thời bảo toàn các bản ghi biến thể cũ thông qua `INSERT OR IGNORE INTO route_variants_new ... SELECT ... FROM route_variants`.

---

## 2. Nguồn gốc & Kiểm định Dữ liệu Seed EBMS (`ebms_static_dataset.json.gz`)

- **Nguồn trích xuất duy nhất:** Server chính thức EBMS (`https://apicms.ebms.vn`) trong giai đoạn phát triển qua các endpoint:
  - `/businfo/getroutesforcombo`
  - `/businfo/getroutebyid/{routeId}`
  - `/businfo/getvarsbyroute/{routeId}`
  - `/businfo/getstopsbyvar/{routeId}/{varId}` (cung cấp tọa độ `Lat/Lng` thực và thứ tự trạm)
  - `/businfo/getpathsbyvar/{routeId}/{varId}` (cung cấp danh sách tọa độ polyline `lat`/`lng`)
  - `/businfo/getstopsforautocomplete` (bổ sung thông tin tuyến đi qua / từ khóa tìm kiếm)
- **Phiên bản đóng gói:** `ebms-20261112-0525` (`1,040,076` bytes $\approx$ `1,015.7 KB` GZIP).
- **Quy mô dữ liệu thực tế trong asset:**

| Thành phần | Số lượng đã xác minh | Ghi chú chất lượng |
| :--- | :---: | :--- |
| **Routes (`routes`)** | **181** | 0 tuyến thiếu tên hoặc ID không hợp lệ |
| **Variants (`route_variants`)** | **338** | 0 biến thể mồ côi (`routeId` không tồn tại) |
| **Bus Stops (`bus_stops`)** | **6,412** | **5,882** trạm có tọa độ thực tại TP.HCM + **38** trạm Tuyến 173 Côn Đảo (`lat 8.68..8.73`); 492 trạm chỉ xuất hiện trong danh mục autocomplete |
| **Cross-References (`route_stop_cross_reference`)** | **14,089** | Bảo toàn nguyên thứ tự `orderIndex` theo từng `(routeId, varId)` |
| **Route Polylines (`route_paths`)** | **338** | 0 polyline lỗi hoặc dưới 2 điểm (`pointCount >= 2`) |

---

## 3. Rủi ro phát hiện và Khắc phục trong quá trình Migration

1. **Tọa độ Tuyến 173 (Côn Đảo):**
   - *Phát hiện:* Kiểm thử thực tế trên dữ liệu thật của EBMS phát hiện 38 trạm thuộc Tuyến 173 nằm tại Côn Đảo (`lat = 8.68..8.73, lng = 106.60..106.62`), vượt ra ngoài khung dự kiến ban đầu `(9.0..12.0, 105.0..108.0)`.
   - *Khắc phục:* Mở rộng giới hạn vùng kiểm duyệt hợp lệ trong [`StaticDatasetValidator.kt`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/seed/StaticDatasetValidator.kt#L37-L40) và [`RoutePathGeoJsonConverter.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/util/RoutePathGeoJsonConverter.kt#L25) thành `lat in 8.0..13.0, lng in 104.0..109.0`, đảm bảo 100% tuyến toàn mạng lưới (kể cả Côn Đảo) vượt qua validation và vẽ polyline đầy đủ.
2. **Sửa lỗi khóa chính trùng lặp `RouteVariantEntity` (Phát hiện qua benchmark seed thật):**
   - *Phát hiện:* Ở schema v2 trước đây, [`RouteVariantEntity`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/entity/RouteEntity.kt#L31-L49) đặt `@PrimaryKey val id: Int` đơn lẻ. Khi kiểm thử nạp thực tế toàn bộ `ebms_static_dataset.json.gz` vào Room, số `route_variants` bị ghi đè chỉ còn **84/338** do mã `varId` (`1`, `2`, ...) của EBMS chỉ là duy nhất theo từng tuyến chứ không duy nhất toàn mạng lưới.
   - *Khắc phục:* Đổi khóa chính của `RouteVariantEntity` thành khóa tổ hợp `primaryKeys = ["routeId", "id"]` và cập nhật `MIGRATION_2_3` tái tạo bảng `route_variants` an toàn. Kết quả đo lại trong `StaticDatasetManagerTest` xác nhận toàn bộ **338/338 biến thể** (cùng 181 tuyến, 6.412 trạm, 338 polyline) được giải nén, SHA-256 validate và nạp nguyên vẹn vào Room trong **`5,508 ms`**.
3. **Bảo toàn dữ liệu người dùng khi nâng cấp hoặc đồng bộ:**
   - Khi nâng cấp từ Phase 9 (DB v2) lên Phase 10 (DB v3), `MIGRATION_2_3` tạo bảng metadata mới; ở lần mở app đầu tiên, `StaticDatasetMetadata` chưa tồn tại nên `ensureInitialStaticDatasetLoaded()` tự động nạp lại 5 bảng tĩnh (cấp tọa độ thực thay cho `0.0, 0.0` trước đây và nạp sẵn toàn bộ 338 polyline) mà **không xóa** `favorite_stops` hay `favorite_routes`.
4. **Tự động nhận diện đóng gói AAPT2 (`.json.gz` vs `.json` trong APK):**
   - *Phát hiện:* Khi kiểm tra bên trong file `app-release.apk`, công cụ đóng gói `aapt2` của Android Gradle Plugin tự động giải nén và bỏ hậu tố `.gz`, biến tên asset trong APK thành `assets/ebms_static_dataset.json` (`6,051,502` bytes plain JSON nén ZIP xuống `1,055,556` bytes).
   - *Khắc phục:* [`StaticDatasetManager.openSeedAssetStream`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/seed/StaticDatasetManager.kt#L121-L130) thử mở `ebms_static_dataset.json.gz` và tự fallback sang `ebms_static_dataset.json`; đồng thời `seedFromGzipStream` kiểm tra 2 magic bytes (`0x1F 0x8B`) để đọc chính xác cả luồng GZIP lẫn luồng plain JSON đã được `aapt2` giải nén.
5. **Loại bỏ API Call dư thừa tại Màn hình Chi tiết tuyến:**
   - [`RouteDetailViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-route/src/main/java/com/hcmcbustracker/feature/route/RouteDetailViewModel.kt) chuyển sang đọc local-first trực tiếp từ Room (`getRouteById`, `getVariantsForRoute`, `getStopsForRouteVar`), chỉ gọi fallback mạng khi variant/stop hoàn toàn trống ở DB (trường hợp hiếm khi chưa seed xong).
