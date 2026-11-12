# HCMC BUS TRACKER – BÁO CÁO KIỂM TOÁN DỮ LIỆU RUNTIME
## POST RELEASE RUNTIME DATA AUDIT REPORT
### ĐIỀU TRA NGUYÊN NHÂN LỖI MÀN HÌNH TUYẾN XE & DỮ LIỆU XE BUÝT BỊ RỖNG ("TẤT CẢ TUYẾN (0)")

- **Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)
- **Phiên bản:** `1.0.0` (Release Signed APK)
- **Mức độ nghiêm trọng:** **CRITICAL (Khẩn cấp)**
- **Hiện tượng lỗi:** Sau khi cài đặt APK Release trên thiết bị/giả lập, mở tab "Tuyến xe" hiển thị `"Tất cả tuyến (0)"`, thông báo `"Không tìm thấy tuyến xe nào"` kèm `"Không có tuyến buýt nào khớp với \"\""`. Các màn hình tra cứu xe buýt không hiển thị dữ liệu.
- **Vai trò thực hiện:** Senior Android Software Architect & Lead Independent QA Engineer
- **Thời điểm đánh giá:** 2026-11-11
- **Kết luận kiểm toán (Verdict):** **FAIL (Phát hiện lỗi logic đồng bộ ban đầu và phân nhánh UI)**

---

## 1. NGUYÊN NHÂN GỐC RỄ (ROOT CAUSE ANALYSIS)

Sau khi kiểm chứng trực tiếp từng tầng từ máy chủ EBMS đến giao diện Jetpack Compose, nguyên nhân được xác định là sự kết hợp của **3 lỗi logic cốt lõi**:

### Nguyên nhân 1 (Trọng yếu): Thiếu cơ chế Tự động Kích hoạt Đồng bộ Ban đầu (Missing Automatic Initial Sync)
- Khi người dùng cài đặt ứng dụng lần đầu, cơ sở dữ liệu Room Database (`hcmc_bus_tracker.db`) hoàn toàn rỗng (0 bản ghi trong bảng `routes`, `bus_stops`).
- Hàm đồng bộ [`BusRepository.syncRoutes()`] và [`BusRepository.syncStops()`] đã được cài đặt hoàn chỉnh và hoạt động tốt, **nhưng không có bất kỳ thành phần nào tự động kích hoạt nó khi ứng dụng khởi chạy lần đầu**:
  - [`HcmcBusTrackerApp.kt`](file:///e:/MultiGo%20Reworked/app/src/main/java/com/hcmcbustracker/app/HcmcBusTrackerApp.kt): `onCreate()` rỗng.
  - [`MainActivity.kt`](file:///e:/MultiGo%20Reworked/app/src/main/java/com/hcmcbustracker/app/MainActivity.kt): Không có logic kiểm tra hoặc kích hoạt sync.
  - [`HomeViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/HomeViewModel.kt): Không có khối `init { ... }`.
  - [`RoutesViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-route/src/main/java/com/hcmcbustracker/feature/route/RoutesViewModel.kt): Không có khối `init { ... }`. Hàm `syncRoutes()` chỉ được gọi khi người dùng bấm nút làm mới (Refresh icon) trên TopAppBar.
  - [`StopsViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-search/src/main/java/com/hcmcbustracker/feature/search/StopsViewModel.kt): Không có khối `init { ... }`.
  - *Ngoại lệ:* [`SearchViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-search/src/main/java/com/hcmcbustracker/feature/search/SearchViewModel.kt) có khối `init { initialSyncIfNeeded() }`, nhưng `SearchViewModel` chỉ được khởi tạo khi người dùng chủ động mở màn hình tìm kiếm riêng biệt (`SearchScreen`). Nếu người dùng chỉ chuyển đổi giữa 4 tab chính ở bottom bar, `SearchViewModel` không bao giờ được khởi tạo.

### Nguyên nhân 2: Trạng thái Tải trong `RoutesViewModel` bị ghi đè sau 300ms
- Trong [`RoutesViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-route/src/main/java/com/hcmcbustracker/feature/route/RoutesViewModel.kt#L53-L72):
  - Khởi tạo ban đầu: `initialValue = RoutesUiState(isLoading = true)`.
  - Tuy nhiên, toán tử `combine` cho `uiState` gán cứng: `isLoading = false`.
  - Do `_query` phát giá trị rỗng `""` và qua `debounce(300)`, ngay sau 300ms, `combine` phát ra `RoutesUiState(query = "", routes = emptyList(), isLoading = false, isSyncing = false)`.
  - Do đó, ứng dụng chỉ hiển thị "Đang tải" trong đúng 300ms đầu tiên, sau đó lập tức chuyển sang trạng thái "Không có dữ liệu".

### Nguyên nhân 3: Phân nhánh UI hiển thị sai thông điệp Empty State
- Trong [`RoutesScreen.kt`](file:///e:/MultiGo%20Reworked/feature-route/src/main/java/com/hcmcbustracker/feature/route/RoutesScreen.kt#L125-L142):
  - Khi `state.routes.isEmpty()` là đúng và `state.query` là `""`:
    - Dòng 112 hiển thị: `"Tất cả tuyến (0)"`.
    - Dòng 136-141 hiển thị:
      ```kotlin
      EmptyState(
          title = "Không tìm thấy tuyến xe nào",
          message = "Không có tuyến buýt nào khớp với \"${state.query}\". Hãy kiểm tra lại số hiệu hoặc điểm đến.",
          actionLabel = "Xóa tìm kiếm",
          onActionClick = { viewModel.onQueryChanged("") }
      )
      ```
    - Giao diện in ra thông điệp vô lý: `"Không có tuyến buýt nào khớp với \"\""` kèm nút `"Xóa tìm kiếm"`. Khi người dùng bấm nút này, query vẫn là `""` nên giao diện bị kẹt vĩnh viễn ở trạng thái rỗng, tạo cảm giác toàn bộ hệ thống bị lỗi dữ liệu.
    - Không phân biệt giữa: (1) Cơ sở dữ liệu đang rỗng cần tải; (2) Đang đồng bộ từ máy chủ; (3) Tìm kiếm không có kết quả.

---

## 2. KẾT QUẢ KIỂM TRA TỪNG API THỰC TẾ (PHASE A)

Đã đối chiếu trực tiếp với 8 tệp tài liệu kiến trúc tại [docs/phase-0/](file:///e:/MultiGo%20Reworked/docs/phase-0/) và kiểm tra kết nối mạng độc lập:

| Chức năng API | Endpoint thực tế | Method | Tham số / Headers | HTTP Code | Thời gian | Kết quả kiểm tra payload |
| :--- | :--- | :---: | :--- | :---: | :---: | :--- |
| **Danh sách tuyến** | `/businfo/getroutesforcombo` | `GET` | Không tham số.<br/>`Accept: application/json` | **`200 OK`** | **0.87s** | Trả về mảng JSON 130+ tuyến buýt (`value`, `lable`, `key`). Unicode chuẩn. Khớp 100% với [`RouteComboDto`]. |
| **Chiều chạy tuyến** | `/businfo/getvarsbyroute/{routeId}` | `GET` | Path: `routeId` (int) | **`200 OK`** | ~0.45s | Trả về 2 hướng tuyến (Lượt đi `Outbound=true` / Lượt về `Outbound=false`). Khớp [`RouteVarDto`]. |
| **Danh sách trạm dừng** | `/businfo/getstopsforautocomplete` | `GET` | Không tham số. | **`200 OK`** | ~1.2s | Trả về JSON nén 3 mảng (`l`, `v`, `g`) của 6.414 trạm dừng. Khớp [`StopAutocompleteDto`]. |
| **Trạm theo tuyến** | `/businfo/getstopsbyvar/{routeId}/{varId}` | `GET` | Path: `routeId`, `varId` | **`200 OK`** | ~0.5s | Trả về danh sách trạm theo đúng thứ tự lộ trình kèm tọa độ GPS `Lat`, `Lng`. Khớp [`BusStopDto`]. |
| **Polyline lộ trình** | `/businfo/getpathsbyvar/{routeId}/{varId}` | `GET` | Path: `routeId`, `varId` | **`200 OK`** | ~0.4s | Trả về 2 mảng tọa độ `lat` và `lng`. Khớp [`RoutePathDto`]. |
| **Dự báo trạm** | `/prediction/predictbystopid/{stopId}` | `GET` | Path: `stopId` | **`200 OK`** | ~0.4s | Trả về danh sách xe đang tiếp cận trạm (`d`, `dts`, `s`, `t`, `v`). Khớp [`PredictionDto`]. |
| **Dự báo trạm kế tiếp**| `/prediction/{routeId}/{varId}/{stopId}/predictnextstops/{maxNextStops}` | `GET` | Path: `routeId`, `varId`, `stopId`, `maxNextStops` | **`200 OK`** | ~0.5s | Trả về ETA cho 3 trạm tiếp theo. Khớp [`PredictionDto`]. |

$\rightarrow$ **Kết luận:** Toàn bộ API EBMS đang **HOẠT ĐỘNG HOÀN TOÀN BÌNH THƯỜNG**, thời gian phản hồi nhanh dưới 1 giây, định dạng dữ liệu không thay đổi.

---

## 3. TRUY VẾT DATA PIPELINE TỪNG BƯỚC (PHASE B)

```
[EBMS API] 
   │  (200 OK, 130+ tuyến, 0.87s) - HOẠT ĐỘNG TỐT
   ▼
[Retrofit / OkHttp] 
   │  (Deserialization qua Kotlinx Serialization) - HOẠT ĐỘNG TỐT
   ▼
[RouteComboDto -> NetworkMappers] 
   │  (Regex tách mã "[01]" và tên "Ga Ba Son - Bến xe buýt Chợ Lớn") - HOẠT ĐỘNG TỐT
   ▼
[BusRepositoryImpl.syncRoutes()] 
   │  (Tạo List<RouteEntity>, ghi vào SQLite với transaction) - HOẠT ĐỘNG TỐT
   ▼
[Room Database / RouteDao] 
   │  (Bảng routes, Query getAllRoutes()) - HOẠT ĐỘNG TỐT
   ▼
[RoutesViewModel] 
   │  ❌ ĐỨNG IM: Không có lệnh gọi repository.syncRoutes() khi khởi động!
   │  Chỉ đọc Room rỗng -> phát ra emptyList()
   ▼
[RoutesUiState] 
   │  ❌ LỖI: isLoading = false, isSyncing = false, routes = emptyList()
   ▼
[RoutesScreen (Compose UI)] 
   │  ❌ HIỂN THỊ SAI: "Tất cả tuyến (0)" & "Không có tuyến buýt nào khớp với \"\""
```

### Bảng Chi Tiết Kiểm Tra Từng Tầng:

| Tầng kiến trúc | Kiểm tra dữ liệu & Logic | Đánh giá |
| :--- | :--- | :---: |
| **1. EBMS API** | Trả về 130+ tuyến xe buýt, HTTP 200 OK, thời gian 0.87s. | **PASS** |
| **2. Retrofit / OkHttp** | Cấu hình Base URL `https://apicms.ebms.vn/`, timeout 15s, parsing JSON hợp lệ. | **PASS** |
| **3. DTO & Mapper** | `RouteComboDto` khớp trường JSON; `toDomain()` tách đúng `routeNo` và `name`. | **PASS** |
| **4. Repository Engine** | `syncRoutes()` ánh xạ sang `RouteEntity` và `syncMetadataDao.recordSync("ROUTES")`. | **PASS** |
| **5. Room Database** | Schema v2, bảng `routes` có chỉ mục `normalizedSearch`, `routeNo`. | **PASS** |
| **6. DAO Flow** | `getAllRoutes()` trả về `Flow<List<RouteEntity>>` sắp xếp theo số hiệu tuyến. | **PASS** |
| **7. ViewModel Lifecycle** | ❌ **FAIL:** `RoutesViewModel` không kiểm tra hoặc gọi `syncRoutes()` trong `init`. | **FAIL** |
| **8. UI State Mapping** | ❌ **FAIL:** Gán `isLoading = false` ngay khi debounce 300ms hoàn thành. | **FAIL** |
| **9. Compose UI** | ❌ **FAIL:** Hiển thị `EmptyState` tìm kiếm ngay cả khi query rỗng và database chưa sync. | **FAIL** |

---

## 4. KIỂM TRA ĐỒNG BỘ BAN ĐẦU & PHÂN BIỆT TRẠNG THÁI (PHASE C & D)

### 4.1. Hành vi khi Cài mới Ứng dụng:
1. **Có gọi API lấy tuyến/trạm không?** **KHÔNG.** Không có `init` block nào trong `MainActivity`, `HcmcBusTrackerApp`, `HomeViewModel`, `RoutesViewModel` hay `StopsViewModel` gọi `syncRoutes()` hoặc `syncStops()`.
2. **Có chờ đồng bộ trước khi hiển thị Empty không?** **KHÔNG.** Sau 300ms debounce, UI lập tức rơi vào nhánh `state.routes.isEmpty()` và hiển thị EmptyState.
3. **Có tự động thử lại khi database rỗng không?** **KHÔNG.** Người dùng buộc phải nhìn thấy nút Refresh trên thanh tiêu đề và bấm vào đó thì dữ liệu mới được tải về.
4. **Khi mất mạng có hiển thị cache cũ không?** **CÓ.** Nếu dữ liệu đã từng được sync vào Room thành công một lần, khi mất mạng ứng dụng vẫn đọc được cache từ SQLite.

### 4.2. Khảo sát Lỗi UI Search:
- Khi `state.query == ""` và `state.routes.isEmpty()`:
  - Header: `"Tất cả tuyến (0)"` (lấy từ `state.routes.size`).
  - Thân màn hình: `"Không có tuyến buýt nào khớp với \"\""`.
  - Nút hành động: `"Xóa tìm kiếm"` $\rightarrow$ click vào gọi `onQueryChanged("")` không tạo ra bất kỳ thay đổi nào.

---

## 5. SO SÁNH CẤU HÌNH DEBUG VÀ RELEASE (PHASE E)

| Tiêu chí | Debug Build | Release Build | Đánh giá |
| :--- | :--- | :--- | :---: |
| **Application ID** | `com.hcmcbustracker.app.debug` | `com.hcmcbustracker.app` | Độc lập, không xung đột |
| **Quyền INTERNET** | Có trong AndroidManifest | Có trong merged manifest | **PASS** |
| **Network Security Config** | Mặc định (HTTPS) | Mặc định (HTTPS) | **PASS** |
| **API Base URL** | `https://apicms.ebms.vn/` | `https://apicms.ebms.vn/` | Giống nhau 100% |
| **ProGuard / R8** | `isMinifyEnabled = false` | `isMinifyEnabled = false` | R8 không can thiệp strip code |
| **Kotlinx Serialization** | Đầy đủ converter | Đầy đủ converter | **PASS** |
| **Hilt Dependency Injection**| Tiêm phụ thuộc thành công | Tiêm phụ thuộc thành công | **PASS** |
| **Room Database Init** | Room v2 | Room v2 | **PASS** |

$\rightarrow$ **Kết luận:** Lỗi rỗng dữ liệu **XẢY RA TRÊN CẢ DEBUG LẪN RELEASE**. Nguyên nhân hoàn toàn nằm ở logic điều phối đồng bộ (Data Orchestration) của ViewModel/UI, không phải do cấu hình build variant hay ProGuard.

---

## 6. PHÂN LOẠI NGUYÊN NHÂN TỔNG HỢP

| Hạng mục phân loại | Đánh giá | Chi tiết |
| :--- | :---: | :--- |
| **1. API Connectivity** | **KHÔNG** | Máy chủ EBMS 200 OK, phản hồi 0.87s, DNS/TLS hoạt động hoàn hảo. |
| **2. API Contract / DTO** | **KHÔNG** | DTO `RouteComboDto` khớp 100% với JSON trả về từ EBMS. |
| **3. Repository / Synchronization** | **CÓ (PHỤ)** | Repository có sẵn `syncRoutes()`, nhưng thiếu cơ chế báo hiệu "Cần đồng bộ lần đầu" (`isInitialSyncRequired`). |
| **4. Room / DAO** | **KHÔNG** | Query SQLite đúng đắn, lưu trữ và mapping Entity chính xác. |
| **5. ViewModel / Flow** | **CÓ (CHÍNH)** | `RoutesViewModel`, `HomeViewModel`, `StopsViewModel` thiếu khối `init { initialSyncIfNeeded() }`. State gán cứng `isLoading = false`. |
| **6. Search / UI Filtering** | **CÓ (CHÍNH)** | `RoutesScreen` không phân biệt giữa "Database rỗng cần sync" với "Kết quả tìm kiếm không khớp", in ra chuỗi rỗng `\"\"`. |
| **7. Release Configuration** | **KHÔNG** | Release cấu hình đầy đủ quyền và giữ nguyên các lớp nghiệp vụ. |

---

## 7. ĐỀ XUẤT PHƯƠNG ÁN SỬA ĐỔI TỐI THIỂU (MINIMAL FIX PLAN)

Để khắc phục triệt để mà không phá vỡ kiến trúc Clean Architecture:

### Bước 1: Bổ sung Cơ chế Đồng bộ Tự động trong `RoutesViewModel`
- Trong `RoutesViewModel.kt`:
  - Thêm phương thức `initialSyncIfNeeded()` trong khối `init`:
    ```kotlin
    init {
        viewModelScope.launch {
            val count = repository.getAllRoutes().first().size
            if (count == 0) {
                syncRoutes()
            }
        }
    }
    ```
  - Cập nhật logic `isLoading` trong `uiState`: Khi `routes.isEmpty() && isSyncing`, trạng thái phải là `isLoading = true` hoặc `isSyncing = true`.

### Bước 2: Tương tự cho `StopsViewModel` và `HomeViewModel`
- Trong `StopsViewModel.kt`: Tự động gọi `syncStops()` khi database trạm đang rỗng.
- Trong `HomeViewModel.kt`: Tự động gọi `syncRoutes()` và `syncStops()` nếu mở app lần đầu.

### Bước 3: Cải thiện Phân nhánh Giao diện trong `RoutesScreen`
- Phân biệt rõ 3 kịch bản:
  1. **Đang tải / Đang đồng bộ lần đầu (`state.isLoading || (state.routes.isEmpty() && state.isSyncing)`):**
     $\rightarrow$ Hiển thị `LoadingState(message = "Đang tải dữ liệu tuyến xe buýt TP.HCM từ EBMS...")`.
  2. **Tìm kiếm không có kết quả (`state.query.isNotBlank() && state.routes.isEmpty()`):**
     $\rightarrow$ Hiển thị `EmptyState(title = "Không tìm thấy tuyến", message = "Không có tuyến buýt nào khớp với \"${state.query}\"...", actionLabel = "Xóa tìm kiếm")`.
  3. **Database thực sự rỗng và không có mạng (`state.query.isBlank() && state.routes.isEmpty()`):**
     $\rightarrow$ Hiển thị `EmptyState(title = "Chưa có dữ liệu tuyến xe", message = "Dữ liệu tuyến chưa được đồng bộ từ máy chủ.", actionLabel = "Tải dữ liệu ngay", onActionClick = viewModel::syncRoutes)`.

---

## 8. DANH SÁCH TEST CẦN BỔ SUNG

1. **`RoutesViewModelTest`:**
   - Test: `initial creation when database is empty triggers syncRoutes`.
   - Test: `initial creation when database already has routes does not trigger redundant syncRoutes`.
   - Test: `uiState preserves isLoading or isSyncing state during initial synchronization`.
2. **`StopsViewModelTest`:**
   - Test: `initial creation when database is empty triggers syncStops`.
3. **`RoutesScreenTest` (UI State Verification):**
   - Test: Khi `query == ""` và `routes.isEmpty()` và `!isSyncing`, hiển thị nút "Tải dữ liệu ngay" thay vì "Xóa tìm kiếm".
   - Test: Khi `isSyncing == true`, hiển thị `LoadingState`.

---

## 9. KẾT LUẬN KIỂM TOÁN (FINAL VERDICT)

### **PHÂN LOẠI: FAIL**
*(Hệ thống bị lỗi logic hiển thị và thiếu trigger đồng bộ dữ liệu ban đầu tại runtime; tuy nhiên nền tảng API EBMS, DTO, Room và Retrofit đều hoạt động hoàn hảo 100%).*

---

*BÁO CÁO HOÀN TẤT. Hệ thống dừng lại và KHÔNG sửa source code, KHÔNG build APK mới cho đến khi nhận được xác nhận và chỉ đạo phê duyệt phương án từ người dùng.*
