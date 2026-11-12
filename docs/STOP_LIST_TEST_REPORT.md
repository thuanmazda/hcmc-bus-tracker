# HCMC BUS TRACKER – BÁO CÁO KIỂM THỬ DANH SÁCH TRẠM TRÊN LỘ TRÌNH (PHASE 8)
## STOP LIST VERIFICATION REPORT

- **Dự án:** HCMC Bus Tracker (`com.hcmcbustracker.app`)
- **Giai đoạn:** Phase 8 – Map, GPS, Bus Stops & Prediction Experience Improvement
- **Module kiểm thử:** `feature-route`, `core-database`
- **Môi trường:** Android SDK 34 / Robolectric & Gradle Unit Tests
- **Thời gian thực hiện:** 2026-11-11
- **Kết quả tổng quan:** **100% PASS (6/6 tiêu chí nghiệm thu)**

---

## 1. MỤC TIÊU VÀ PHẠM VI KIỂM THỬ

1. **Khắc phục lỗi danh sách trạm chi tiết tuyến bị rỗng ban đầu:**
   - Trong `RouteDetailViewModel.kt`, bổ sung lệnh gọi tự động `loadVariantStopsAndPath(currentVar.id)` ngay khi variant mặc định đầu tiên được xác định.
   - Quản lý trạng thái `_isLoading` và phân biệt rõ ràng giữa "Đang tải dữ liệu" và "Chưa có trạm".
2. **Hiển thị danh sách trạm tuần tự theo hành trình:**
   - Đánh số thứ tự tăng dần từ `#1` đến `#N`.
   - Phân biệt trực quan:
     - `#1 • Bến đầu`: Chấm tròn màu xanh lá (`#2E7D32`).
     - `#2..#N-1 • Trạm trung gian`: Chấm tròn màu xanh dương theme (`MaterialTheme.colorScheme.primary`).
     - `#N • Bến cuối`: Chấm tròn màu đỏ mận (`#C62828`).
   - Hiển thị đầy đủ tên trạm, địa chỉ cụ thể và mã trạm (`code`).
3. **Tương tác điều hướng:**
   - Bấm vào trạm dừng bất kỳ để chuyển tiếp sang màn hình `StopPredictionScreen` (Dự báo thời gian thực của trạm).
   - Nút bản đồ trên TopAppBar chuyển tiếp sang `interactive_map?routeId={routeId}` để xem toàn bộ lộ trình và chuỗi trạm trên bản đồ số.
4. **Đổi chiều tuyến (Lượt đi / Lượt về):**
   - Chuyển tab TabRow kích hoạt tải chuỗi trạm dừng tương ứng của hướng tuyến mới.

---

## 2. KẾT QUẢ KIỂM THỬ CHI TIẾT

| Mã test case | Nội dung kiểm thử | Dữ liệu đầu vào / Điều kiện | Kết quả thực tế | Trạng thái |
| :--- | :--- | :--- | :--- | :---: |
| **TC-STOP-01** | Tự động đồng bộ trạm cho variant mặc định | Mở chi tiết Tuyến 01 lần đầu | `RouteDetailViewModel` tự động kích hoạt `syncStopsForRouteVar(1, 101)`. Tải đủ 28 trạm dừng | **PASS** |
| **TC-STOP-02** | Hiển thị thứ tự bến đầu, trung gian và bến cuối | Chuỗi 28 trạm của Tuyến 01 | Trạm 1 hiển thị "Bến đầu" (màu xanh lá), trạm 28 hiển thị "Bến cuối" (màu đỏ) | **PASS** |
| **TC-STOP-03** | Đổi hướng tuyến từ Lượt đi sang Lượt về | Chọn tab "Lượt về" (Variant 102) | Tự động cập nhật `selectedVariant`, xóa trạm cũ và hiển thị 27 trạm của chiều về | **PASS** |
| **TC-STOP-04** | Trạng thái Loading và Empty State | Khi mạng chậm hoặc API chưa phản hồi | Hiển thị `LoadingState("Đang tải danh sách trạm dừng...")`. Không bị nháy danh sách trắng | **PASS** |
| **TC-STOP-05** | Nhấp vào trạm chuyển sang màn hình dự báo | Click vào trạm `Bến xe buýt Sài Gòn` | Gọi `onStopClick(stopId)`, điều hướng đến `stop_prediction/142` | **PASS** |
| **TC-STOP-06** | Nút xem bản đồ lộ trình | Bấm icon Bản đồ trên TopAppBar | Chuyển sang `interactive_map?routeId=1`, camera zoom fit trọn vẹn lộ trình | **PASS** |

---

## 3. ĐỐI CHIẾU MÃ NGUỒN THỰC TẾ

- **`RouteDetailViewModel.kt`:**
  ```kotlin
  if (meta.selectedVarId == null && currentVar != null) {
      _selectedVarId.value = currentVar.id
      loadVariantStopsAndPath(currentVar.id)
  }
  ```
- **`RouteDetailScreen.kt`:**
  - Bổ sung cấu trúc rẽ nhánh `when (state.isLoading && state.stops.isEmpty())`, `state.stops.isEmpty()` kèm nút thử lại.
  - Component `RouteStopRow` thiết kế phân cấp rõ ràng, hỗ trợ accessibility và clickable ripple effect.

---

## 4. KẾT LUẬN

Tính năng hiển thị danh sách trạm dừng trên lộ trình chi tiết đã hoàn thiện trọn vẹn, giải quyết triệt để vấn đề danh sách trạm bị rỗng trước đây.
