# HCMC Bus Tracker – Phase 3: Tile Provider Specification & Usage Policy
*Đặc tả Nhà cung cấp Gạch Bản đồ (Tile Provider) và Chính sách Sử dụng*

**Phiên bản:** 1.0.0  
**Giai đoạn:** Phase 3 – OpenStreetMap & MapLibre Integration  
**Trạng thái:** Hoàn thành & Chờ phê duyệt  
**Ngày cập nhật:** 2026-11-10  

---

## 1. MỤC TIÊU VÀ NGUYÊN TẮC CỐT LÕI

Hệ thống bản đồ của **HCMC Bus Tracker** được thiết kế với tính độc lập cao, không phụ thuộc vào một máy chủ tile duy nhất. Kiến trúc cho phép:
- Thay đổi linh hoạt giữa các nhà cung cấp raster tile mà không ảnh hưởng đến logic ứng dụng.
- Tuân thủ tuyệt đối **Chính sách sử dụng Tile (Tile Usage Policy)** của từng nhà cung cấp.
- Đảm bảo hiển thị đầy đủ thông tin bản quyền (Attribution) theo quy định quốc tế.
- **TUYỆT ĐỐI KHÔNG** cào dữ liệu hàng loạt (bulk downloading/scraping) hay làm quá tải hạ tầng máy chủ công cộng.

---

## 2. DANH MỤC CÁC NHÀ CUNG CẤP TILE (EVALUATION MATRIX)

| Tiêu chí | FPT STIS (Mặc định) | OpenStreetMap Standard | Carto Voyager |
| :--- | :--- | :--- | :--- |
| **Mã định danh** | `TileProvider.FPT_STIS` | `TileProvider.OSM_STANDARD` | `TileProvider.CARTO_VOYAGER` |
| **URL Raster Template** | `https://map.stis.vn/bright/{z}/{x}/{y}.png` | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | `https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png` |
| **Kích thước Tile** | 256 x 256 px | 256 x 256 px | 256 x 256 px |
| **Max Zoom** | 19 | 19 | 19 |
| **Hạ tầng máy chủ** | CDN / Server đặt tại Việt Nam (FPT STIS TP.HCM) | Máy chủ OSM Foundation phân tán toàn cầu | Fastly CDN toàn cầu của CARTO |
| **Tốc độ phản hồi tại TP.HCM** | **Rất nhanh** (~25 - 60ms) | Trung bình - Khá (~120 - 250ms) | Rất nhanh (~45 - 90ms) |
| **Attribution bắt buộc** | `© FPT STIS, OpenStreetMap contributors` | `© OpenStreetMap contributors` | `© CARTO, © OpenStreetMap contributors` |
| **Mục đích sử dụng chính** | Vận hành sản phẩm chính thức với độ trễ thấp | Dự phòng độc lập khi STIS bảo trì | Tùy chọn giao diện độ tương phản cao, phong cách hiện đại |

---

## 3. CHÍNH SÁCH SỬ DỤNG VÀ TUÂN THỦ (USAGE POLICY & COMPLIANCE)

### 3.1. Chính sách OpenStreetMap Tile Usage Policy
1. **User-Agent Hợp lệ:**
   - OSM nghiêm cấm các ứng dụng gửi request với User-Agent mặc định hoặc rỗng.
   - Ứng dụng HCMC Bus Tracker cấu hình User-Agent thống nhất:
     ```
     HCMC-Bus-Tracker/1.0 (Android; com.hcmcbustracker.app; contact: support@hcmcbustracker.local)
     ```
2. **Nghiêm cấm tải hàng loạt (No Bulk Scraping):**
   - Chỉ tải tile khi người dùng di chuyển camera đến viewport tương ứng.
   - Không tích hợp tính năng tải trước bản đồ ngoại tuyến toàn bộ TP.HCM (offline bulk pre-caching) từ máy chủ công cộng của OSM.
3. **Bộ nhớ đệm (Caching):**
   - Sử dụng cơ chế cache tự nhiên của MapLibre Native dựa trên SQLite (`mbgl-cache.db`) và HTTP Cache-Control headers để hạn chế tối đa việc tải lại các tile đã xem.

### 3.2. Điều kiện sử dụng FPT STIS
- Máy chủ tile của FPT STIS phục vụ cho hệ thống giám sát và thông tin xe buýt TP.HCM.
- Ứng dụng tôn trọng tài nguyên băng thông bằng cách áp dụng ngưỡng zoom (`zoom >= 13.5` khi tải trạm) và giới hạn tỷ lệ yêu cầu.
- Giữ nguyên nhãn bản quyền FPT STIS trên giao diện bản đồ.

---

## 4. THIẾT KẾ DYNAMIC STYLE SPECIFICATION V8

MapLibre Native Android sử dụng chuẩn đặc tả Style Specification v8. Hàm chuyển đổi `toStyleJson()` trong `TileProvider.kt` đóng gói cấu hình như sau:

```kotlin
enum class TileProvider(
    val title: String,
    val description: String,
    val tileUrlTemplate: String,
    val attributionHtml: String,
    val maxZoom: Int = 19
) {
    FPT_STIS(
        title = "FPT STIS (Mặc định)",
        description = "Bản đồ giao thông TP.HCM chính thức, độ trễ thấp",
        tileUrlTemplate = "https://map.stis.vn/bright/{z}/{x}/{y}.png",
        attributionHtml = "© FPT STIS, © OpenStreetMap contributors"
    ),
    OSM_STANDARD(
        title = "OpenStreetMap Chuẩn",
        description = "Bản đồ cộng đồng OSM toàn cầu",
        tileUrlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        attributionHtml = "© OpenStreetMap contributors"
    ),
    CARTO_VOYAGER(
        title = "Carto Voyager",
        description = "Bản đồ thanh lịch, độ tương phản cao",
        tileUrlTemplate = "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
        attributionHtml = "© CARTO, © OpenStreetMap contributors"
    );

    fun toStyleJson(): String {
        return """
        {
          "version": 8,
          "name": "Dynamic-Raster-Style",
          "sources": {
            "raster-tiles": {
              "type": "raster",
              "tiles": ["$tileUrlTemplate"],
              "tileSize": 256,
              "attribution": "$attributionHtml",
              "maxzoom": $maxZoom
            }
          },
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": { "background-color": "#f8f9fa" }
            },
            {
              "id": "raster-layer",
              "type": "raster",
              "source": "raster-tiles",
              "minzoom": 0,
              "maxzoom": $maxZoom
            }
          ]
        }
        """.trimIndent()
    }
}
```

---

## 5. TỐI ƯU HIỆU NĂNG & TIẾT KIỆM TÀI NGUYÊN BĂNG THÔNG

1. **SQLite Native Tile Cache:**
   - MapLibre tự động duy trì cơ sở dữ liệu tile đệm trên thiết bị Android (`/data/data/com.hcmcbustracker.app/cache/mbgl-cache.db`).
   - Các vùng bản đồ người dùng thường xuyên xem (như khu vực trung tâm Quận 1, Bến xe Miền Đông, Bến xe Chợ Lớn) sẽ được load tức thì từ bộ nhớ flash mà không phát sinh thêm HTTP request.
2. **Chuyển đổi Provider tức thời (Zero Latency Switching):**
   - Bộ quản lý `TileProviderManager` phát tín hiệu `StateFlow<TileProvider>`. Khi người dùng lựa chọn provider mới từ menu bản đồ, MapLibre nạp lại Style JSON mới trong tích tắc mà không làm giật khung hình hay hủy bỏ phiên làm việc của người dùng.
3. **Xử lý ngắt kết nối mạng:**
   - Khi mất mạng, MapLibre tiếp tục hiển thị các tile đã được lưu đệm. Nếu tile chưa được nạp, nền màu trung tính `#f8f9fa` được hiển thị để tránh hiện tượng màn hình đen hoặc crash ứng dụng.
