# ĐỀ XUẤT KIẾN TRÚC KỸ THUẬT & YÊU CẦU PHASE 1 (PHASE 1 REQUIREMENTS)
**Dự án:** HCMC Bus Tracker  
**Vai trò:** Senior Android Software Architect  
**Trạng thái:** DỰ THẢO ĐỀ XUẤT (Chờ phê duyệt từ User)  

---

## 1. MỤC TIÊU CỦA GIAI ĐOẠN 1 (PHASE 1 OBJECTIVES)

Giai đoạn 1 sẽ tập trung vào việc xây dựng nền tảng cốt lõi (Core Foundation) của ứng dụng Android:
1. Thiết lập cấu trúc dự án chuẩn Android Clean Architecture.
2. Xây dựng Network Layer giao tiếp trực tiếp với `apicms.ebms.vn`.
3. Xây dựng Local Database (Room) để lưu trữ và lập chỉ mục Offline cho toàn bộ tuyến và 6.414 trạm dừng.
4. Triển khai Map Engine (bản đồ và đa tuyến lộ trình).
5. Xây dựng Realtime Prediction Engine (Polling 15s tự động quản lý theo vòng đời màn hình).
6. Triển khai thuật toán nội suy vị trí xe buýt trên bản đồ.

---

## 2. ĐỀ XUẤT STACK CÔNG NGHỆ (TECH STACK)

### 2.1. Ngôn ngữ & Nền tảng
* **Ngôn ngữ chính:** **Kotlin 2.0+**
* **Target SDK:** Android 15 (API level 35)
* **Min SDK:** Android 7.0 (API level 24) – đảm bảo tương thích hơn 96% thiết bị Android tại Việt Nam.

### 2.2. Kiến trúc Ứng dụng
* **Mô hình kiến trúc:** **Clean Architecture + MVI / MVVM** tuân thủ hướng dẫn chính thức từ Google Android Architecture Guide.
* **Cấu trúc phân tầng:**
  - `Presentation Layer`: UI (Jetpack Compose / XML View), ViewModel, UI State (Kotlin `StateFlow`).
  - `Domain Layer`: UseCases, Domain Models, Repository Interfaces (thuần túy Kotlin, độc lập framework).
  - `Data Layer`: Repository Implementations, Remote Data Source (Retrofit), Local Data Source (Room).

### 2.3. Các thư viện cốt lõi (Core Libraries)
* **Dependency Injection (DI):** **Dagger Hilt** (quản lý vòng đời và tiêm phụ thuộc chuẩn Android).
* **Bất đồng bộ & Xử lý luồng:** **Kotlin Coroutines + Flow** (StateFlow, SharedFlow).
* **Mạng (Networking):**
  - **Retrofit v2.11+** + **OkHttp v4.12+**
  - **Kotlinx.Serialization** (hoặc Moshi) để parse JSON tốc độ cao và an toàn kiểu dữ liệu (Type-safe).
* **Cơ sở dữ liệu cục bộ (Local Storage):**
  - **Room Database v2.6+** (SQLite ORM) hỗ trợ FTS4/FTS5 để tìm kiếm không dấu siêu tốc cho 6.414 trạm dừng.
* **Bản đồ (Map SDK):**
  - **MapLibre Native Android SDK** (hoặc **Google Maps SDK for Android**).
  - Khuyến nghị: MapLibre Native kết hợp Vector Tiles hoặc Raster Tiles từ STIS/OSM giúp hoàn toàn miễn phí, không phụ thuộc Google Maps Billing quota.
* **Định vị vị trí người dùng:**
  - **Google Play Services Location** (`FusedLocationProviderClient`) để lấy vị trí hiện tại của người dùng.
* **Tác vụ ngầm định kỳ:**
  - **Android Jetpack WorkManager** (dùng đồng bộ danh mục tuyến/trạm mới 1 lần mỗi tuần trong chế độ cắm sạc/wifi).

---

## 3. THIẾT KẾ CÁC MODULE ỨNG DỤNG (MODULARIZATION)

Dự án được phân rã theo kiến trúc đa module (Multi-module) để dễ bảo trì, tăng tốc độ build và mở rộng:

```
MultiGo-Reworked/
 ├── app/                             # Application Entry Point & Navigation
 ├── core/
 │    ├── core-model/                 # Data Models & Entities chung
 │    ├── core-network/               # Retrofit, OkHttp, DTOs, API Interfaces
 │    ├── core-database/              # Room DB, DAOs, Migrations, Pre-populated data
 │    ├── core-location/              # GPS Tracking & Location Providers
 │    └── core-designsystem/          # UI Components, Theme, Typography, Icons
 └── features/
      ├── feature-home/               # Màn hình chính, Tab navigation
      ├── feature-search/             # Tìm kiếm tuyến buýt & trạm dừng
      ├── feature-route/              # Chi tiết tuyến, danh sách trạm, biểu đồ giờ
      ├── feature-prediction/         # Bảng dự báo xe đến trạm & polling engine
      └── feature-map/                # Bản đồ hiển thị trạm, đa tuyến và vị trí xe
```

---

## 4. CHI TIẾT CÁC THÀNH PHẦN KỸ THUẬT QUAN TRỌNG

### 4.1. Network Client (Retrofit Interface)
```kotlin
interface EbmsApiService {
    @GET("businfo/getroutesforcombo")
    suspend fun getRoutesForCombo(): List<RouteComboDto>

    @GET("businfo/getroutebyid/{routeId}")
    suspend fun getRouteById(@Path("routeId") routeId: Int): RouteDetailDto

    @GET("businfo/getvarsbyroute/{routeId}")
    suspend fun getVarsByRoute(@Path("routeId") routeId: Int): List<RouteVarDto>

    @GET("businfo/getstopsbyvar/{routeId}/{varId}")
    suspend fun getStopsByVar(
        @Path("routeId") routeId: Int,
        @Path("varId") varId: Int
    ): List<BusStopDto>

    @GET("businfo/getpathsbyvar/{routeId}/{varId}")
    suspend fun getPathsByVar(
        @Path("routeId") routeId: Int,
        @Path("varId") varId: Int
    ): RoutePathDto

    @GET("businfo/getstopsforautocomplete")
    suspend fun getStopsForAutocomplete(): StopAutocompleteDto

    @GET("businfo/getstopsinbounds/{minLon}/{minLat}/{maxLon}/{maxLat}")
    suspend fun getStopsInBounds(
        @Path("minLon") minLon: Double,
        @Path("minLat") minLat: Double,
        @Path("maxLon") maxLon: Double,
        @Path("maxLat") maxLat: Double
    ): List<BusStopDto>

    @GET("prediction/predictbystopid/{stopId}")
    suspend fun getPredictionByStopId(@Path("stopId") stopId: Int): List<PredictionDto>

    @GET("prediction/{routeId}/{varId}/{stopId}/predictnextstops/{maxNextStops}")
    suspend fun getPredictNextStops(
        @Path("routeId") routeId: Int,
        @Path("varId") varId: Int,
        @Path("stopId") stopId: Int,
        @Path("maxNextStops") maxNextStops: Int = 3
    ): List<PredictionDto>
}
```

### 4.2. Quản lý Realtime Polling Engine (StateFlow Pattern)
```kotlin
class PredictionRepositoryImpl @Inject constructor(
    private val api: EbmsApiService
) : PredictionRepository {

    override fun observePrediction(stopId: Int, intervalMs: Long): Flow<Result<List<Prediction>>> = flow {
        while (currentCoroutineContext().isActive) {
            try {
                val dtos = api.getPredictionByStopId(stopId)
                emit(Result.success(dtos.map { it.toDomain() }))
            } catch (e: Exception) {
                emit(Result.failure(e))
            }
            delay(intervalMs) // 15.000 ms
        }
    }.flowOn(Dispatchers.IO)
}
```

### 4.3. Thuật toán nội suy xe buýt trên bản đồ (Interpolation Helper)
* Xây dựng `VehicleInterpolationEngine`:
  - Input: `RoutePath` (mảng tọa độ), `BusStop` (tọa độ trạm), `distance` ($d$ mét).
  - Output: `LatLng(lat, lng)`, `bearing` (độ xoay của xe).
  - Diễn hoạt chuyển động mượt mà bằng phép nội suy tuyến tính (Linear Interpolation - LERP) giữa hai lần cập nhật.

---

## 5. LỘ TRÌNH TRIỂN KHAI DỰ KIẾN (PHASE 1 MILESTONES)

* **Milestone 1: Project Setup & Core Modules**
  - Cấu trúc Git, cấu hình Gradle Kotlin DSL, CI lint check.
  - Xây dựng module `core-network` và các DTOs.
  - Viết Unit Test kiểm thử gọi thực tế đến API EBMS.
* **Milestone 2: Local Database & Data Sync Engine**
  - Xây dựng Room Database: Tables cho Route, RouteVar, BusStop, PolylinePath.
  - Xây dựng UseCase đồng bộ lần đầu (Initial Sync) 6.414 trạm dừng.
  - Tối ưu FTS (Full-Text Search) cho tìm kiếm không dấu nhanh dưới 10ms.
* **Milestone 3: UI & Map Integration**
  - Màn hình tìm kiếm tuyến & trạm dừng.
  - Tích hợp Map SDK hiển thị trạm xung quanh và vẽ polyline lộ trình xe.
* **Milestone 4: Realtime Prediction & Bus Tracking**
  - Tích hợp Polling Engine 15 giây.
  - Hiển thị bảng dự báo ETA, vận tốc, khoảng cách, biển số xe.
  - Hiển thị Marker xe buýt di chuyển trên bản đồ.
* **Milestone 5: Verification & Review**
  - Kiểm thử thực tế trên đường phố TP.HCM (so sánh dữ liệu app với biển báo thực tế ngoài trạm).
