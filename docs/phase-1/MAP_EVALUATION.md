# BÁO CÁO ĐÁNH GIÁ CÔNG NGHỆ BẢN ĐỒ OPENSTREETMAP TRÊN ANDROID
**Dự án:** HCMC Bus Tracker  
**Mục tiêu:** Đánh giá MapLibre Native Android và các giải pháp OpenStreetMap tương thích  
**Ngày thực hiện:** 09/11/2026  

---

## 1. TỔNG QUAN YÊU CẦU BẢN ĐỒ CỦA DỰ ÁN

Hệ thống HCMC Bus Tracker yêu cầu nền bản đồ với các năng lực kỹ thuật:
1. **Nền bản đồ:** OpenStreetMap (OSM) / Tile Server FPT STIS (`https://map.stis.vn/bright/{z}/{x}/{y}.png`).
2. **Không phụ thuộc vào Google Maps SDK** (loại bỏ yêu cầu tài khoản Billing, hạn mức chi phí phát sinh).
3. **Không sử dụng `osmdroid`** vì dự án này đã chính thức bị Archive trên GitHub (không còn được duy trì bảo mật, lỗi thời về hiệu năng và không tối ưu cho Jetpack Compose).
4. **Khả năng hiển thị:**
   - Vẽ Marker trạm dừng xe buýt (hơn 50+ trạm cùng lúc trên viewport) với icon số thứ tự.
   - Vẽ Polyline lộ trình 2 chiều (Lượt đi / Lượt về) mượt mà, đổi màu linh hoạt.
   - Camera Animation: Bay mượt (`animateCamera`, `flyTo`) khi chọn trạm, pan to vị trí GPS người dùng.
   - Custom Vehicle Icons: Hiển thị icon xe buýt, xoay góc (bearing/rotation) theo hướng di chuyển thực tế của xe dọc lộ trình, hỗ trợ cập nhật vị trí thời gian thực.
5. **Attribution & Tile Policy:** Hiển thị đúng bản quyền © OpenStreetMap contributors / FPT STIS và tuân thủ chính sách tải tile.

---

## 2. MA TRẬN SO SÁNH CÁC GIẢI PHÁP BẢN ĐỒ OSM CHO ANDROID

| Tiêu chí kỹ thuật | MapLibre Native Android SDK | osmdroid (Archived) | Mapbox Android (v10+) | Tangram-ES (Archived) |
|---|---|---|---|---|
| **Tình trạng dự án** | **Active** (MapLibre Foundation / Linux Foundation) | **Archived / Bỏ rơi** | Thương mại (Đóng mã nguồn, thu phí) | Đã ngừng phát triển |
| **Pipeline đồ họa** | **OpenGL ES 2.0/3.0 & Vulkan** (Cực nhanh) | Canvas 2D CPU (Giật lag khi vẽ nhiều marker) | Metal / Vulkan / OpenGL | OpenGL ES |
| **Hỗ trợ Raster Tiles (OSM/STIS)**| **Xuất sắc** (qua JSON Style Raster Source) | Tốt (nhưng CPU render) | Hỗ trợ (nhưng ép dùng Mapbox Token) | Khó cấu hình |
| **Hiệu năng 60/120 FPS** | **Có** (GPU-accelerated hoàn toàn) | Không (Dễ tụt frame khi zoom) | Có | Trung bình |
| **Camera Animation** | **Đầy đủ** (`easeCamera`, `flyTo`, duration, curve) | Cơ bản (Giật khi pan/zoom) | Đầy đủ | Cơ bản |
| **Custom Vehicle Marker** | **SymbolLayer + Dynamic Rotation** hoặc Android View | Marker icon tĩnh | SymbolLayer | Symbol |
| **Vẽ Polyline (Lộ trình buýt)**| **LineLayer / GeoJSON LineString** (Anti-aliased) | Polyline (răng cưa) | LineLayer | Polyline |
| **Tương thích Jetpack Compose** | **Tốt** (Wrap qua `AndroidView` hoặc MapLibre Compose) | Khá phức tạp | Tốt | Kém |
| **Min SDK** | **API 21+** (Hoàn toàn khớp đề xuất API 26) | API 16+ | API 21+ | API 16+ |
| **Chi phí bản quyền** | **100% Miễn phí & Open Source (BSD 2-Clause)** | Miễn phí | Thu phí theo MAU/Tile | Miễn phí |

---

## 3. ĐÁNH GIÁ CHUYÊN SÂU MAPLIBRE NATIVE ANDROID SDK

### 3.1. Cấu hình Tile Source cho FPT STIS & OpenStreetMap
MapLibre Native cho phép tải Tile Server của OpenStreetMap hoặc server STIS của FPT thông qua một Map Style JSON cục bộ đơn giản (lưu trữ trong `assets/`):

```json
{
  "version": 8,
  "name": "OSM-STIS-Raster",
  "sources": {
    "fpt-stis-tiles": {
      "type": "raster",
      "tiles": [
        "https://map.stis.vn/bright/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "attribution": "© <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors, FPT STIS"
    },
    "osm-fallback-tiles": {
      "type": "raster",
      "tiles": [
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "attribution": "© <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors"
    }
  },
  "layers": [
    {
      "id": "stis-layer",
      "type": "raster",
      "source": "fpt-stis-tiles",
      "minzoom": 0,
      "maxzoom": 19
    }
  ]
}
```

### 3.2. Đánh giá tính năng Polyline (Lộ trình tuyến)
* **Kỹ thuật thực hiện:** Sử dụng `GeoJsonSource` kết hợp `LineLayer`.
* **Ưu điểm vượt trội:**
  - Đường vẽ được khử răng cưa (Anti-aliasing) bằng GPU shader.
  - Cho phép tùy chỉnh `lineColor`, `lineWidth`, `lineJoin` (tròn góc cua), `lineCap`.
  - Có thể nạp cùng lúc 2 đa tuyến: Lượt đi (Màu xanh dương `#1976D2`) và Lượt về (Màu cam/đỏ `#E64A19`) và ẩn/hiện tức thì không cần vẽ lại từ đầu.

### 3.3. Đánh giá tính năng Custom Vehicle Marker & Rotation (Vị trí & Góc xe buýt)
* **Kỹ thuật thực hiện:**
  - Đăng ký Bitmap icon xe buýt vào Map Style: `style.addImage("bus-icon", busBitmap)`.
  - Sử dụng `SymbolLayer` với thuộc tính:
    - `iconImage`: `"bus-icon"`
    - `iconRotate`: Gắn với thuộc tính `bearing` tính toán được từ thuật toán nội suy.
    - `iconAllowOverlap`: `true` (không bị ẩn khi zoom).
    - `iconIgnorePlacement`: `true`.
* **Hiệu năng:** GPU xử lý xoay icon và dịch chuyển vị trí cực kỳ mượt mà, không tốn tài nguyên tạo lại View.
* **Animation chuyển động:** Khi nhận chu kỳ polling mới (sau 15s), vị trí mới của xe được diễn hoạt bằng `TypeEvaluator<LatLng>` và `ValueAnimator` trong vòng 1000ms giúp icon lướt êm ái trên polyline.

### 3.4. Đánh giá Attribution & Chính sách Tile (Tile Usage Policy)
1. **Tuân thủ bản quyền:**
   - MapLibre Native có sẵn widget hiển thị Attribution ở góc dưới bản đồ.
   - Thêm dòng chữ rõ ràng: `© OpenStreetMap contributors | Dữ liệu giao thông: buyttphcm.com.vn`.
2. **Tuân thủ chính sách mạng & Cache:**
   - Cấu hình OkHttp Client gắn `User-Agent: HCMCBusTracker/1.0 (Android; com.hcmcbustracker.app)` đúng tiêu chuẩn OpenStreetMap Foundation.
   - Tận dụng Disk Cache tích hợp của MapLibre (dung lượng tối đa 50MB - 100MB) để lưu các tile bản đồ đã tải, giảm thiểu tối đa việc tải lại tile khi di chuyển qua các khu vực quen thuộc.

---

## 4. KẾT LUẬN & ĐỀ XUẤT KIẾN TRÚC

> [!TIP]
> **Khuyến nghị kiến trúc chính thức:**
> Lựa chọn **MapLibre Native Android SDK** (`org.maplibre.gl:android-sdk:11.5.2`) làm nền tảng bản đồ chính cho HCMC Bus Tracker.

* Đáp ứng 100% tiêu chí phê duyệt của Chủ dự án:
  - OpenStreetMap tương thích hoàn toàn.
  - Loại bỏ hoàn toàn `osmdroid` đã bị archive.
  - Đầy đủ tính năng Marker, Polyline, Camera Animation và Custom Vehicle Icon với góc xoay chuẩn.
  - Đảm bảo hiển thị bản quyền hợp lệ.
