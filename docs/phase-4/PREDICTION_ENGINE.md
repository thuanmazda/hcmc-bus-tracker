# HCMC Bus Tracker – Phase 4: Real-time Prediction Engine
*Tài liệu Thiết kế Kiến trúc Động cơ Polling Thời gian thực & Xử lý Dự báo*

**Phiên bản:** 1.0.0  
**Giai đoạn:** Phase 4 – Real-time Bus Arrival Prediction  
**Trạng thái:** Hoàn thành & Chờ phê duyệt  
**Ngày cập nhật:** 2026-11-10  

---

## 1. TỔNG QUAN KIẾN TRÚC PREDICTION ENGINE

Động cơ dự báo thời gian thực (Real-time Prediction Engine) của **HCMC Bus Tracker** chịu trách nhiệm điều phối luồng dữ liệu xe buýt sắp đến trạm trực tiếp từ hệ thống điều hành giao thông EBMS TP.HCM (`apicms.ebms.vn`).

```
┌────────────────────────────────────────────────────────────────────────┐
│                        StopPredictionScreen.kt                         │
│   (Giao diện Compose: Status Banner, Card Xe, Expand Next Stops)      │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
      Lifecycle Observer           │ StateFlow<PredictionUiState>
   (ON_RESUME / ON_PAUSE)          │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        PredictionViewModel                             │
│  - Realtime Polling Loop (Chu kỳ 15s)                                  │
│  - Exponential Backoff Controller (15s -> 30s -> 60s -> 120s)          │
│  - Time-ago Ticker (Tăng mỗi 1s, tự chuyển STALE sau 45s)             │
│  - Network Connectivity Monitor (Phát hiện Offline, tự refresh)        │
└──────────────┬──────────────────────────────────────────┬──────────────┘
               │                                          │
               ▼                                          ▼
┌───────────────────────────────┐        ┌───────────────────────────────┐
│ BusRepositoryImpl             │        │ NetworkMonitor                │
│ - fetchRealtimePrediction()   │        │ - ConnectivityManager         │
│ - fetchPredictNextStops()     │        │ - Flow<Boolean> isOnline      │
│ - withTimeoutOrNull(10s)      │        └───────────────────────────────┘
└──────────────┬────────────────┘
               │
               ▼
┌───────────────────────────────┐
│ EbmsApiService                │
│ - GET /prediction/...         │
└───────────────────────────────┘
```

---

## 2. CHÍNH SÁCH POLLING THỜI GIAN THỰC (REALTIME POLLING POLICY)

### 2.1. Chu kỳ Polling Mặc định (15 giây)
- Chu kỳ thăm dò mặc định được cố định là **15 giây** (`15_000L`), đã được thẩm định và xác minh trong Phase 0 nhằm đồng bộ với tần suất phát tín hiệu GPS của hộp đen xe buýt và giảm tải băng thông máy chủ.
- **Poll ngay lập tức:** Khi người dùng mở màn hình dự báo của một trạm, request đầu tiên được kích hoạt ngay lập tức mà không phải chờ hết chu kỳ 15 giây.

### 2.2. Kiểm soát Luồng & Quản lý Vòng đời (Lifecycle-Aware Polling)
1. **Không tạo Job trùng lặp:** Hàm `startRealtimePolling()` luôn gọi `stopRealtimePolling()` trước khi khởi tạo coroutine mới, đảm bảo duy nhất 1 luồng polling hoạt động cho trạm đang xem.
2. **Hủy khi rời màn hình hoặc chuyển sang Background:**
   - Sử dụng `LifecycleEventObserver` kết hợp `LocalLifecycleOwner.current`.
   - Khi Activity/Composable chuyển sang `ON_PAUSE` hoặc `ON_STOP` (người dùng khóa màn hình hoặc chuyển sang ứng dụng khác), `viewModel.stopRealtimePolling()` được gọi ngay lập tức.
   - Khi người dùng quay lại ứng dụng (`ON_RESUME`), polling tự động tái kích hoạt.
   - Khi thoát màn hình (`onDispose`), observer được gỡ bỏ và coroutine scope bị hủy triệt để, ngăn chặn rò rỉ bộ nhớ (zero zombie network requests).
3. **Chống nhiễm dữ liệu cũ (No Stale Race Condition):**
   - Mỗi ViewModel gắn chặt với một `stopId` bất biến thông qua `SavedStateHandle`.
   - Khi chuyển sang trạm mới, ViewModel mới độc lập được khởi tạo và hủy ViewModel cũ, loại bỏ nguy cơ kết quả chậm của trạm trước ghi đè lên trạm sau.

### 2.3. Cơ chế Exponential Backoff khi gặp lỗi liên tiếp
Khi máy chủ EBMS gặp sự cố quá tải, chập chờn hoặc ngắt kết nối:
$$\text{Next Delay} = \min(15000 \times 2^{(\text{consecutiveFailures} - 1)}, 120000)\text{ ms}$$

- **Lỗi lần 1:** Thử lại sau 15 giây.
- **Lỗi lần 2:** Thử lại sau 30 giây.
- **Lỗi lần 3:** Thử lại sau 60 giây.
- **Lỗi lần 4+:** Giới hạn trần tối đa sau 120 giây (2 phút).
- **Khi thành công:** `consecutiveFailures` lập tức reset về `0` và chu kỳ trở lại chuẩn `15s`.

### 2.4. Xử lý Mất Mạng Ngoại Tuyến (Offline Protection)
- Sử dụng `NetworkMonitor` tích hợp `ConnectivityManager.NetworkCallback`.
- Trước mỗi chu kỳ gửi request, kiểm tra `networkMonitor.isCurrentlyOnline`. Nếu không có mạng:
  - Chuyển trạng thái sang `PredictionFeedStatus.OFFLINE`.
  - Tạm dừng gửi request, không spam HTTP call vô ích làm hao pin thiết bị.
  - Lắng nghe luồng `networkMonitor.isOnline`: ngay khi thiết bị kết nối lại Wi-Fi/4G/5G, động cơ tự động gửi request làm mới tức thì.

### 2.5. Giới hạn Thời gian chờ (Request Timeout)
- Mọi request dự báo được bọc bởi `kotlinx.coroutines.withTimeoutOrNull(10_000L)` tại tầng Repository.
- Nếu sau 10 giây máy chủ không trả response, coroutine tự hủy và trả về `NetworkResult.Error("Hết thời gian kết nối máy chủ (Timeout 10s)")`.

---

## 3. ĐẶC TẢ ĐỘ TƯƠI MỚI DỮ LIỆU (DATA FRESHNESS SPECIFICATION)

Theo nguyên tắc kỹ thuật số 5 của Phase 4, hệ thống phân biệt rạch ròi 3 khái niệm thời gian:
1. **Thời gian GPS thiết bị (`dts`):** Thời điểm thực tế mà hộp đen GPS trên xe buýt phát tín hiệu vệ tinh (chuỗi ISO 8601 từ EBMS, VD: `2026-11-09T21:13:29+07:00`).
2. **Thời điểm ứng dụng nhận response (`responseReceivedTimeMillis`):** Mốc thời gian hệ thống Android nhận và giải mã gói tin JSON từ máy chủ EBMS (VD: `21:13:35`).
3. **Thời gian trôi qua từ lần cập nhật cuối (`secondsSinceLastUpdate`):** Đồng hồ đếm giây tăng dần từ `0s` sau mỗi response thành công (VD: `Cập nhật 4 giây trước`).

### Tiêu chí cảnh báo Dữ liệu cũ (Stale Thresholds):
- **Cảnh báo tín hiệu GPS xe quá cũ:** Nếu $\text{Age}_{\text{GPS}} = \text{CurrentTime} - \text{Time}_{\text{dts}} > 180\text{ giây}$ (3 phút), xe được đánh dấu `isGpsStale = true` và hiển thị nhãn cảnh báo `⚠️ Tín hiệu GPS cũ`.
- **Cảnh báo mất kết nối nguồn cấp:** Nếu `secondsSinceLastUpdate >= 45s`, trạng thái chung của trạm tự động chuyển từ `LIVE` sang `STALE` để cảnh báo hành khách không tin cậy vào ETA hiện tại.

---

## 4. MÁY TRẠNG THÁI NGUỒN CẤP (PREDICTION FEED STATE MACHINE)

```
        ┌──────────────┐
        │   LOADING    │ ◄── Khởi tạo trạm / Đang refresh lần đầu
        └──────┬───────┘
               │ Thành công (có xe)
               ▼
        ┌──────────────┐   secondsAgo >= 45s    ┌──────────────┐
        │     LIVE     ├───────────────────────►│    STALE     │
        └──────┬───────┘                        └──────┬───────┘
               │ Không có xe chạy                      │
               ▼                                       │
        ┌──────────────┐                               │
        │    EMPTY     │                               │
        └──────────────┘                               │
               ▲                                       │
               │ Mất mạng       Lỗi Server / Timeout   │
        ┌──────┴───────┐        ┌──────────────────────┴───────┐
        │   OFFLINE    │        │          API_ERROR           │
        └──────────────┘        └──────────────────────────────┘
```

| Trạng thái | Điều kiện kích hoạt | Biểu hiện giao diện |
| :--- | :--- | :--- |
| **`LOADING`** | Đang nạp lần đầu, danh sách xe rỗng | Shimmer / Vòng xoay nạp dữ liệu |
| **`LIVE`** | Nhận response thành công, có xe, dữ liệu tươi mới | Huy hiệu xanh lá `LIVE • Cập nhật Xs trước` |
| **`STALE`** | Không nhận được cập nhật mới sau >= 45s hoặc toàn bộ xe có GPS cũ > 3 phút | Huy hiệu màu hổ phách `DỮ LIỆU CŨ • Cập nhật Xs trước` |
| **`OFFLINE`** | Thiết bị mất kết nối Wi-Fi / Dữ liệu di động | Huy hiệu đỏ `NGOẠI TUYẾN • Không có mạng` |
| **`EMPTY`** | Request thành công nhưng không có xe nào đang hướng về trạm | Thẻ `EmptyState`: "Chưa có xe đang đến" |
| **`API_ERROR`** | HTTP 5xx, lỗi mạng, quá thời gian 10s | Thẻ `ErrorState` kèm nút "Thử lại" |

---

## 5. DỰ BÁO TRẠM TIẾP THEO (NEXT STOPS PREDICTION)

- Khi người dùng bấm vào một xe trên thẻ tuyến, hệ thống gọi API chính thức:
  `GET /prediction/{routeId}/{varId}/{stopId}/predictnextstops/3`
- Động cơ nạp tối đa 3 trạm kế tiếp dọc hành trình của phương tiện đó, bao gồm:
  - Tên trạm kế tiếp (`stopName`).
  - Dự kiến thời gian xe sẽ đến từng trạm (`etaMinutes`, `formattedEta`).
  - Khoảng cách từ xe đến trạm đó (`formattedDistance`).
- Dữ liệu được lưu đệm tạm thời trong `expandedNextStops: Map<String, List<Prediction>>` và cho phép đóng/mở mượt mà thông qua hiệu ứng hoạt họa Compose `AnimatedVisibility`.
