# HCMC BUS TRACKER – PHASE 1 API IMPLEMENTATION & NETWORK LAYER

> **Base URL:** `https://apicms.ebms.vn`  
> **Source Platform:** HCMC Management Centre of Public Transport (EBMS)  
> **Status:** Verified & Fully Implemented  

---

## 1. Network Architecture Overview

The network layer is encapsulated entirely within `:core-network`. It provides type-safe, resilient HTTP communications against EBMS backend endpoints using **Retrofit 2.11.0**, **OkHttp 4.12.0**, and **Kotlinx Serialization 1.7.3**.

```mermaid
flowchart LR
    Caller[BusRepository] --> SafeApiCall["safeApiCall() Coroutine Handler"]
    SafeApiCall --> EbmsApiService["EbmsApiService (Retrofit)"]
    EbmsApiService --> OkHttp["OkHttpClient (Timeouts & Logging)"]
    OkHttp --> Network["https://apicms.ebms.vn"]
    Network --> Response[JSON Payload]
    Response --> Kotlinx["Kotlinx.Serialization (Lenient Parser)"]
    Kotlinx --> DTO[Data Transfer Objects]
    DTO --> Mapper[NetworkMapper Functions]
    Mapper --> DomainModel[Core Domain Models]
```

---

## 2. Verified Endpoints & Interface Contract

All endpoints defined in `com.hcmcbustracker.core.network.api.EbmsApiService` strictly match the findings from Phase 0 reverse engineering:

### 2.1. Get All Bus Routes
- **Endpoint:** `GET /businfo/getallroute`
- **Purpose:** Fetches the master catalog of all active bus routes in Ho Chi Minh City.
- **Parameters:** None.
- **Method Signature:**
  ```kotlin
  @GET("businfo/getallroute")
  suspend fun getAllRoutes(): Response<List<RouteDto>>
  ```
- **Response Structure (Sample):**
  ```json
  [
    {
      "RouteId": 1,
      "RouteNo": "01",
      "RouteName": "Bến Thành - Bến xe Chợ Lớn",
      "Color": "#1E88E5",
      "Type": "Phổ thông có trợ giá",
      "Distance": 8500.0,
      "TimeOfTrip": "35 phút",
      "Headway": "10-15 phút",
      "OperationTime": "05:00 - 20:30",
      "OutBoundDescription": "Bến xe buýt Sài Gòn - Chợ Lớn",
      "InBoundDescription": "Chợ Lớn - Bến xe buýt Sài Gòn"
    }
  ]
  ```

### 2.2. Get Route Variants (Directions)
- **Endpoint:** `GET /businfo/getroutesbyvar`
- **Purpose:** Retrieves the directional variants (e.g., Lượt đi - Outbound, Lượt về - Inbound) for a specific route.
- **Parameters:**
  - `routeId` (Int, query): ID of the bus route.
- **Method Signature:**
  ```kotlin
  @GET("businfo/getroutesbyvar")
  suspend fun getRouteVariants(
      @Query("routeId") routeId: Int
  ): Response<List<RouteVariantDto>>
  ```
- **Response Structure (Sample):**
  ```json
  [
    {
      "RouteId": 1,
      "RouteVarId": 101,
      "RouteVarName": "Lượt đi: Bến xe buýt Sài Gòn - Chợ Lớn",
      "RouteVarShortName": "Bến Thành - Chợ Lớn",
      "RouteNo": "01",
      "StartStop": "Bến xe buýt Sài Gòn",
      "EndStop": "Bến xe Chợ Lớn",
      "Distance": 8500.0,
      "OutBound": true,
      "RunningTime": 35
    }
  ]
  ```

### 2.3. Get Stops by Route & Variant
- **Endpoint:** `GET /businfo/getstopsbyroute`
- **Purpose:** Fetches the ordered sequence of physical bus stops along a particular route variant.
- **Parameters:**
  - `routeId` (Int, query): ID of the bus route.
  - `varId` (Int, query): ID of the route variant.
- **Method Signature:**
  ```kotlin
  @GET("businfo/getstopsbyroute")
  suspend fun getStopsByRoute(
      @Query("routeId") routeId: Int,
      @Query("varId") varId: Int
  ): Response<List<BusStopDto>>
  ```

### 2.4. Get Master Bus Stops
- **Endpoint:** `GET /businfo/getstopinfo`
- **Purpose:** Fetches stop metadata across the transit network.
- **Parameters:**
  - `routeId` (Int, default: 0): Setting to 0 retrieves the full network catalog.
  - `varId` (Int, default: 0): Variant filter.
- **Method Signature:**
  ```kotlin
  @GET("businfo/getstopinfo")
  suspend fun getAllStops(
      @Query("routeId") routeId: Int = 0,
      @Query("varId") varId: Int = 0
  ): Response<List<BusStopDto>>
  ```

### 2.5. Real-time Arrival Predictions
- **Endpoint:** `GET /businfo/getpredict`
- **Purpose:** Retrieves vehicles currently en route to a target stop with real-time ETA, distance, speed, and GPS timestamp.
- **Parameters:**
  - `stopId` (Int, optional): Numeric stop identifier.
  - `code` (String, optional): Alphanumeric stop code.
- **Method Signature:**
  ```kotlin
  @GET("businfo/getpredict")
  suspend fun getRealtimePrediction(
      @Query("stopId") stopId: Int? = null,
      @Query("code") stopCode: String? = null
  ): Response<List<PredictionDto>>
  ```
- **Response Structure (Sample):**
  ```json
  [
    {
      "RouteId": 1,
      "RouteNo": "01",
      "RouteName": "Bến Thành - Bến xe Chợ Lớn",
      "StopId": 142,
      "StopName": "Bến xe Chợ Lớn",
      "RouteVarId": 101,
      "Destination": "Bến xe Chợ Lớn",
      "Vehicles": [
        {
          "VehiclePlate": "51B-123.45",
          "Time": 420,
          "Distance": 1850.0,
          "Speed": 24.5,
          "ModifiedDate": "2026-11-10T08:15:30Z",
          "Status": 1
        }
      ]
    }
  ]
  ```

### 2.6. Downstream Stop Arrival Predictions
- **Endpoint:** `GET /businfo/getpredictnextstops`
- **Method Signature:**
  ```kotlin
  @GET("businfo/getpredictnextstops")
  suspend fun getPredictNextStops(
      @Query("routeId") routeId: Int,
      @Query("varId") varId: Int,
      @Query("stopId") stopId: Int
  ): Response<List<PredictionDto>>
  ```

### 2.7. Route Path Polyline Geometries
- **Endpoint:** `GET /businfo/getroutepath`
- **Method Signature:**
  ```kotlin
  @GET("businfo/getroutepath")
  suspend fun getRoutePath(
      @Query("routeId") routeId: Int,
      @Query("varId") varId: Int
  ): Response<RoutePathDto>
  ```

---

## 3. Serialization Configuration & Null Safety

External API responses from legacy transit platforms frequently suffer from inconsistent data types (e.g. empty strings instead of numbers, null fields, or undocumented key additions).

To guarantee crash-proof parsing, the `Json` configuration in `NetworkModule` is configured with:

```kotlin
@Provides
@Singleton
fun provideJson(): Json = Json {
    ignoreUnknownKeys = true      // Discards unanticipated JSON keys
    coerceInputValues = true      // Coerces nulls into default values when specified
    isLenient = true              // Tolerates minor JSON syntax quirks
    encodeDefaults = true         // Guarantees proper serialization of defaults
}
```

### Safe DTO Mapping
Every DTO is strictly mapped to its corresponding Domain model through extension functions in `NetworkMappers.kt`:
- Null strings default to `""` or null where acceptable.
- Distances in meters and speeds in km/h are normalized to standard doubles.
- Comma-delimited route listings in stops (e.g. `"01, 56, 150"`) are split into clean lists of strings.

---

## 4. HTTP Client Specifications & Security

OkHttp configuration in `NetworkModule`:
- **Connect Timeout:** 15 seconds
- **Read Timeout:** 30 seconds
- **Write Timeout:** 15 seconds
- **Retry on Connection Failure:** Enabled

### Headers & Traffic Optimization
```kotlin
val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "HcmcBusTracker/1.0 (Android; CleanArchitecture)")
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }
    .apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }
    }
    .build()
```
*Note: In Release mode, HTTP Body logging is disabled to eliminate memory churn and ensure privacy.*

---

## 5. Unified Error Handling: `NetworkResult<T>`

All network operations are executed through `safeApiCall`:

```kotlin
sealed interface NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>
    data class Error(val code: Int? = null, val message: String, val cause: Throwable? = null) : NetworkResult<Nothing>
    data object Loading : NetworkResult<Nothing>
}
```

`safeApiCall` catches `SocketTimeoutException`, `UnknownHostException`, and `SerializationException`, converting raw exceptions into human-readable error messages in Vietnamese for display in the UI.
