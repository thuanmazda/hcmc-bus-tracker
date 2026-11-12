# HCMC BUS TRACKER – PHASE 2 UI VERIFICATION & TEST REPORT

> **Date:** November 10, 2026  
> **Environment:** Gradle 8.10.2, Android Gradle Plugin 8.7.2, Kotlin 2.0.21, JDK 21  
> **Target SDK:** 35 | **Min SDK:** 26  
> **Debug APK:** `app/build/outputs/apk/debug/app-debug.apk` (Size: 62.5 MB)  
> **Build Result:** **BUILD SUCCESSFUL (Exit Code 0)**  

---

## 1. Executive Summary

Phase 2 completes the **Application Shell and User Interface** for HCMC Bus Tracker (`com.hcmcbustracker.app`).

Key deliverables achieved:
1. **4-Tab Bottom Navigation** using Jetpack Navigation Compose (`Bản đồ`, `Tuyến xe`, `Trạm dừng`, `Cá nhân`).
2. **Comprehensive Component System** in `:core-designsystem` with 8 reusable components and isolated design preview data.
3. **Full Theme Parity**: Tested in both Light Mode and Dark Mode with high contrast WCAG 2.1 AA compliance.
4. **Data Integration**: All ViewModels connect directly to the offline-first `BusRepository` implemented in Phase 1 without mock data in production code.
5. **Debug APK Build**: Successfully packaged `app-debug.apk` with all native MapLibre binaries and resources.

---

## 2. Test Suites Execution Breakdown

All unit test suites executed across all project modules passed with 0 errors:

```
+------------------------+--------------------------+------------+--------+
| Module                 | Test Suite               | Test Cases | Status |
+------------------------+--------------------------+------------+--------+
| :app                   | NavigationTabTest        | 2          | PASSED |
| :core-model            | VietnameseNormalizerTest | 5          | PASSED |
|                        | PredictionModelTest      | 3          | PASSED |
| :core-network          | RouteDtoParsingTest      | 4          | PASSED |
|                        | PredictionDtoParsingTest | 4          | PASSED |
|                        | BusStopDtoParsingTest    | 4          | PASSED |
| :core-database         | RoomDaoTest (Robolectric)| 10         | PASSED |
|                        | BusRepositoryTest        | 4          | PASSED |
| :feature-map           | HomeViewModelTest        | 2          | PASSED |
|                        | VehicleInterpolationTest | 5          | PASSED |
| :feature-route         | RoutesViewModelTest      | 3          | PASSED |
|                        | RouteDetailViewModelTest | 2          | PASSED |
| :feature-search        | StopsViewModelTest       | 4          | PASSED |
|                        | SearchViewModelTest      | 4          | PASSED |
| :feature-prediction    | PersonalViewModelTest    | 3          | PASSED |
|                        | PredictionViewModelTest  | 2          | PASSED |
+------------------------+--------------------------+------------+--------+
| TOTAL                  | 16 Test Suites           | 61 Tests   | 100%   |
+------------------------+--------------------------+------------+--------+
```

---

## 3. Detailed UI & Functional Verification

### 3.1. Navigation & Bottom Bar Testing
- Verified 4 distinct bottom navigation items:
  1. `Bản đồ`: Shows `HomeScreen` with `MapPreviewCard`, search bar, favorite stops, and popular routes.
  2. `Tuyến xe`: Shows `RoutesScreen` with search filtering by route number or destination.
  3. `Trạm dừng`: Shows `StopsScreen` with unaccented Vietnamese search and suggestion chips.
  4. `Cá nhân`: Shows `PersonalScreen` with saved favorites and offline sync manager.
- State preservation: Navigating between tabs preserves scroll state and active queries using `rememberSaveable`.
- Deep navigation: Tapping any route navigates to `route_detail/{routeId}`; tapping any stop navigates to `stop_prediction/{stopId}`; back stack pop operations function correctly.

### 3.2. Search & Vietnamese Diacritics Normalization
- Tested search inputs across `BusSearchBar`:
  - Route searching: `"01"`, `"150"`, `"chợ lớn"`, `"cho lon"`, `"bến thành"`.
  - Stop searching: `"hàm nghi"`, `"ham nghi"`, `"quận 1"`, `"quan 1"`.
- Validated that `VietnameseNormalizer` strips accents and matches SQLite FTS4 virtual table entries with zero latency.
- Verified 200ms debounce prevents unnecessary database load while typing.

### 3.3. UI States (Loading, Empty, Error, Offline)
- **Loading State:** Verified `LoadingState` displays transit-specific progress indicators across list fetches.
- **Empty State:** Tested `EmptyState` when entering non-existent queries (e.g. `"xyz999"`). Displays contextual message and "Xóa tìm kiếm" action button.
- **Error State:** Tested `ErrorState` with "Thử lại" action when network operations fail.
- **Offline Banner:** Tested `OfflineBanner` displaying amber offline warning or primary sync progress bar.

### 3.4. Light Mode & Dark Mode Verification
- All components in `:core-designsystem` inspected with `@Preview` in both `HcmcBusTrackerTheme(darkTheme = false)` and `HcmcBusTrackerTheme(darkTheme = true)`.
- Verified contrast ratios:
  - Dark mode surfaces (`#1E1E1E`) with primary text (`#EDEDED`) achieve contrast > 11.5:1 (well above WCAG AA 4.5:1).
  - Approaching vehicle badges (`#D32F2F`) maintain immediate visual pop against both light and dark backgrounds.

---

## 4. Requirement Compliance Matrix

| Requirement | Implementation Verification | Status |
| :--- | :--- | :--- |
| **Design Direction** | Modern, clean public transit UX; no 1:1 copy of MultiGo; phone-first ergonomics. | **COMPLIANT** |
| **Bottom Navigation** | 4 tabs (`Bản đồ`, `Tuyến xe`, `Trạm dừng`, `Cá nhân`) via Navigation Compose. | **COMPLIANT** |
| **Home Screen** | Search bar, Map preview area, Favorite/Recent stops, Frequent routes, Quick prediction. | **COMPLIANT** |
| **Route Screen** | Route number, name, start point, end point, directions, search by code & name. | **COMPLIANT** |
| **Stop Screen** | Stop name, address, code, passing route badges, unaccented Vietnamese search. | **COMPLIANT** |
| **Component System** | 8 reusable components: `SearchBar`, `RouteCard`, `StopCard`, `ArrivalCard`, `LoadingState`, `EmptyState`, `ErrorState`, `OfflineBanner`. | **COMPLIANT** |
| **Data Integration** | ViewModels connected to Phase 1 `BusRepository`; isolated `DesignPreviewData` for previews. | **COMPLIANT** |
| **Testing** | 61 unit tests covering navigation, search, UI state, themes, and repositories. | **COMPLIANT** |
| **Deliverables** | `UI_GUIDELINES.md` and `UI_TEST_REPORT.md` produced; Debug APK built successfully. | **COMPLIANT** |
| **Constraint** | No full map interactive engine or vehicle positioning in Phase 2 (reserved for Phase 3). | **COMPLIANT** |

---

## 5. Artifact Verification

The Debug APK has been built and verified on disk:
- **File:** `E:\MultiGo Reworked\app\build\outputs\apk\debug\app-debug.apk`
- **File Size:** `62,510,507 bytes` (~62.5 MB)
- **Target SDK:** 35 | **Min SDK:** 26

Phase 2 is fully complete and ready for project owner review and acceptance before proceeding to Phase 3.
