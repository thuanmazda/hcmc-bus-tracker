# PHASE 11 — PHÂN TÍCH MARKER BẢN ĐỒ, CAMERA AUTO-CENTER & UX THẺ XE (LƯỢT 1 AUDIT)

**Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)  
**Trạng thái:** Lượt 1 — Audit MapLibre Layers, Marker Bitmap, Camera Lifecycle & Info Card (Chưa sửa code)  

---

## 1. Hiện trạng Mã nguồn & Nguyên nhân Lỗi UI/UX

### 1.1. Biểu tượng Xe buýt (`MapScreen.kt:1438-1583`)
- `createBusWithPlateBitmap` hiện vẽ **hình tròn** xanh ngọc/đỏ chứa icon mặt trước xe buýt và biển số bên dưới.
- `SymbolLayer(VEHICLE_LAYER_ID)` (dòng `1574-1582`) **không đặt** `PropertyFactory.iconRotate(Expression.get("bearing"))` hay `ICON_ROTATION_ALIGNMENT_MAP` — dù GeoJSON feature đã truyền thuộc tính `bearing`. Kết quả: xe **không thể hiện hướng di chuyển**.
- *Lưu ý thiết kế quan trọng (Offline MapLibre):* `TileProvider.toStyleJson()` không cấu hình `"glyphs"` font server nên không dùng `textField` thuần được. Nếu đặt mũi tên xoay và chữ biển số trong cùng 1 bitmap rồi xoay toàn bitmap bằng `iconRotate`, chữ biển số sẽ bị xoay lộn ngược khi xe đi hướng Nam (`180°`).
  - **Giải pháp đúng:** Tách thành **2 `SymbolLayer`** cùng `GeoJsonSource(VEHICLE_SOURCE_ID)`:
    1. **`VEHICLE_ARROW_LAYER_ID`:** Icon mũi tên GPS định hướng sắc nét (phân biệt màu xe thường vs xe đang chọn vs xe GPS cũ, có hào quang & viền trắng), gắn `PropertyFactory.iconRotate(Expression.get("bearing"))` + `PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP)` (chỉ áp dụng rotation khi `has_heading == true`, hoặc icon mũi tên theo hướng tuyến).
    2. **`VEHICLE_LABEL_LAYER_ID`:** Viên nhộng biển số luôn nằm ngang dễ đọc bên cạnh/dưới mũi tên (`iconOffset`, `iconRotationAlignment = VIEWPORT`, không xoay).

### 1.2. Biểu tượng Vị trí Điện thoại (`MapScreen.kt:1294-1433`)
- Hiện tại `createRedArrowBitmap()` và `createRedNeutralPuckBitmap()` vẽ **mũi tên/chấm màu ĐỎ (`#E53935`)** — ngược với yêu cầu thiết kế chuẩn Google Maps (`Blue Dot`).
- **Giải pháp Lượt 3:** Thay toàn bộ thành **Chấm xanh dương Google Maps** (`#1A73E8`, viền trắng đậm, vòng sai số GPS `#4285F4` mờ theo `accuracy`), khác biệt hoàn toàn về hình dáng lẫn màu sắc với mũi tên xe buýt.

### 1.3. Lỗi Không Tự Động Căn Giữa Khi Nhấn Biển Số (`MapScreen.kt:417-424` & `MapViewModel.kt:690`)
1. **Race Condition 3 bước trong `LaunchedEffect(state.selectedVehiclePlate)`:**
   - Trong `MapViewModel.trackVehicle(...)`, `_selectedVehiclePlate.value = plate` được set ngay đầu hàm (dòng `690`), trước khi `ensureInitialStaticDatasetLoaded`, `loadVariantDetails` và `interpolatePosition` hoàn tất (`_trackedVehicle.value` lúc này vẫn là `null`, và `maplibreMapInstance` có thể chưa sẵn sàng).
   - `LaunchedEffect(state.selectedVehiclePlate)` chạy ngay lúc đó, thấy `vehicle == null`, không làm gì, và **không chạy lại** khi `trackedVehicle` có tọa độ hoặc khi `maplibreMapInstance` khởi tạo xong!
2. **Không re-center khi chọn lại cùng một xe hoặc chuyển qua lại giữa các xe:**
   - Nếu người dùng đã kéo bản đồ ra chỗ khác rồi nhấn lại biển số/danh sách xe, `selectedVehiclePlate` không đổi nên camera đứng im.
3. **Thiếu cơ chế Auto-Follow & Tạm dừng khi kéo bản đồ:**
   - Chưa có `addOnCameraMoveStartedListener` kiểm tra `reason == REASON_API_GESTURE` để tự động tạm dừng `isAutoFollowVehicle` khi người dùng chủ động vuốt/kéo bản đồ.
   - Chưa có nút **"Theo dõi xe"** để bật lại auto-center + auto-follow mượt (`400–650 ms`, zoom `16.0` cho vùng đô thị), cũng chưa có bảo vệ tọa độ `(0.0, 0.0)` / null.

### 1.4. Thẻ Thông tin Chi tiết Xe (`VehicleTrackingInfoCard`, `MapScreen.kt:1605-1704`)
- Hiện vẫn còn nhãn `"Trực tiếp"` sai bản chất cho tọa độ nội suy (dòng `1684`), thiếu hiển thị tuyến & chiều tuyến rõ ràng ngay trên thẻ, thiếu thời điểm ứng dụng cập nhật (`updatedAt`), và chưa tách rạch ròi nguồn vị trí (`GPS gốc` vs `Nội suy theo tuyến` vs `Dự đoán bù trễ`).

---

## 2. Phương án Kỹ thuật cho Lượt 3

1. Bổ sung vào `MapViewModel`:
   - `cameraFocusEvent` (`vehicleCameraFocusTick: Long`, tăng mỗi khi bấm chọn xe hoặc nhấn nút "Theo dõi xe" / từ màn Dự báo).
   - `isAutoFollowVehicle: Boolean` (`true` khi bấm chọn xe / mở theo dõi; tự động chuyển `false` khi nhận `onUserMapGesture()` từ `REASON_API_GESTURE`; bật lại khi gọi `resumeVehicleAutoFollow()`).
   - Bảo vệ không căn camera nếu tọa độ `lat == 0.0 && lng == 0.0` hoặc nằm ngoài vùng hợp lệ (`8.0..13.0`, `104.0..109.0`), hiển thị thông báo rõ trên UI.
2. Cập nhật `MapScreen.kt`:
   - Vẽ marker xe buýt bằng mũi tên định hướng GPS (`VEHICLE_ARROW_LAYER_ID`, có trạng thái hướng không xác định rõ ràng nếu thiếu heading) + lớp biển số đứng ngang (`VEHICLE_LABEL_LAYER_ID`), click trúng nhạy ở cả 2 lớp.
   - Vẽ vị trí điện thoại bằng chấm xanh dương Google Maps (`USER_LOCATION_LAYER_ID`).
   - `LaunchedEffect(state.cameraFocusRequestId, state.trackedVehicle?.lat, state.trackedVehicle?.lng, state.isAutoFollowVehicle, maplibreMapInstance)` thực hiện `animateCamera(..., durationMs = 550)` với zoom phù hợp (`16.0`) khi focus mới hoặc auto-follow đang bật, không giật khi người dùng đang kéo bản đồ.
   - Tối ưu lại `VehicleTrackingInfoCard` gọn, hiển thị đủ: Biển số, Tuyến & Chiều, Trạng thái đến trạm, Trạm kế tiếp (+ khoảng cách tới trạm đang chọn nếu có), Cự ly dọc tuyến, ETA tin cậy, Thời điểm GPS (`dts`), Độ trễ GPS, Thời điểm ứng dụng cập nhật, Nguồn tọa độ, và nút **Theo dõi xe** khi Auto-Follow bị tạm dừng.
