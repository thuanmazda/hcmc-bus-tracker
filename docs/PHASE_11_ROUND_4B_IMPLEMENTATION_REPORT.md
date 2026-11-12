# Phase 11 — Round 4B: Adaptive Concurrent Polling Implementation Report

**Date:** 2026-11-12  
**Scope:** Implementation of Global Polling Coordinator, AIMD Adaptive Concurrency Controller, Shared-Stop Coalescing, Provenance-Safe `VehicleDataMerger`, and Diagnostics  
**Preserved Core Logic:** `PositionCompensationEngine`, `VehicleInterpolationEngine`, `PositionCompensationConfig`, `ArrivalStatusThresholds`, `MapScreen` & camera state machine, Room schema (`version = 3`), offline transit dataset, `applicationId`, and signing config were **NOT modified**.

---

## 1. Before vs After Architecture

| Aspect | Before Round 4B | After Round 4B |
| :--- | :--- | :--- |
| **Concurrency Control** | Local fixed `Semaphore(4)` scoped per individual `fetchRouteVariantVehicles` call; no cross-screen or multi-route coordination. | Singleton [GlobalPollingCoordinator](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/polling/GlobalPollingCoordinator.kt) + [AdaptiveConcurrencyController](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/polling/AdaptiveConcurrencyController.kt) enforcing a process-wide dynamic AIMD cap (`initial = 1`, `min = 1`, `max = 4`). |
| **Shared-Stop Handling** | Calling multiple routes sharing hubs (e.g., Stop `4223` or `1401`) or watching the same stop across screens issued separate HTTP GETs. | [SharedStopCoalescer](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/polling/SharedStopCoalescer.kt) deduplicates in-flight calls by `PollingResourceKey(endpointId, scopeParams)` and supports bounded `5,000 ms` freshness-checked reuse for secondary routes (`bypassCache = false`). |
| **Rate-Limit & Backoff** | Fixed deterministic exponential backoff (`base * 2^k`) without jitter; `429` affected only the failing ViewModel loop. | AIMD halves `C` immediately on `HTTP 429`, records global cooldown honoring `Retry-After` (`[5s..300s]` + jitter), pauses permit acquisition during cooldown, and adds jitter in `PredictionViewModel` and `MapViewModel` poll loops. |
| **Data Merging & Sources** | `RouteVehicleAggregator` handled single-source monotonic `dts`; no explicit `GpsSourceAdapter` or `EndpointCapabilityRegistry`. | [AdaptivePollingConfig.kt](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/AdaptivePollingConfig.kt) + [VehicleDataMerger.kt](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/VehicleDataMerger.kt) add capability registry, provenance separation, plate validation, and monotonic cross-source merging. |
| **Diagnostics** | Only local `lastUpdatedTime` / basic logs. | Reactive `StateFlow<PollingDiagnosticsSnapshot>` tracking `currentConcurrency`, `maxConcurrency`, `requestsInFlight`, `requestQueueLength`, RTT, `429`/`5xx`/timeout/retry counts, dedup/cache hits, cycle duration, processing delay, GPS age percentiles, and `% new dts` (zero tokens/secrets). |

---

## 2. Modified & Added Source Files

### Created Production Files
1. [`AdaptivePollingConfig.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/AdaptivePollingConfig.kt) — Centralized `AdaptivePollingConfig`, `GpsSourceType`, `EndpointCapability`, `EndpointCapabilityRegistry`, `GpsSourceAdapter`, `PollingDiagnosticsSnapshot`, and `RouteVariantPollingTarget`.
2. [`VehicleDataMerger.kt`](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/VehicleDataMerger.kt) — Canonical plate normalization, provenance separation, future/invalid timestamp rejection, and strict GPS-timestamp monotonic merging.
3. [`AdaptiveConcurrencyController.kt`](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/polling/AdaptiveConcurrencyController.kt) — Deterministic AIMD controller (`C = 1..4`, 6-success ramp window, immediate halving on `429`, `503`/timeout step-down, exponential backoff + jitter).
4. [`SharedStopCoalescer.kt`](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/polling/SharedStopCoalescer.kt) — In-flight `Deferred` deduplication with waiter reference counting + cooperative full-waiter cancellation and freshness-guarded `5s` TTL cache (`newer dts` never overwritten).
5. [`GlobalPollingCoordinator.kt`](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/polling/GlobalPollingCoordinator.kt) — Hilt `@Singleton` dynamic permit gate (`1..4`), cooldown enforcement, `EbmsPredictionSourceAdapter`, cycle/GPS metrics collector, and non-secret `StateFlow<PollingDiagnosticsSnapshot>`.

### Updated Production Files
1. [`BusRepository.kt`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/repository/BusRepository.kt) — Added `observePollingDiagnostics()` and `fetchMultiRouteVehicles(...)` (backward-compatible default bodies).
2. [`BusRepositoryImpl.kt`](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/repository/BusRepositoryImpl.kt) — Injects `@Singleton GlobalPollingCoordinator` (preserving 4-arg convenience constructor for tests), routes stop/next-stop/variant/multi-route calls through coordinator + coalescer, prioritizes active `priorityRouteId` (`bypassCache = true`) ahead of secondary routes, and records cycle/GPS diagnostics.
3. [`PredictionViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/PredictionViewModel.kt) — Preserves `queryMutex`, `refreshJob`, and monotonic merge while adding backoff jitter (sub-second jitter on `Retry-After`, `±15%` jitter on exponential backoff).
4. [`MapViewModel.kt`](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt) — Preserves `activePollingRouteVar`, `routePollingJob`, `routeJob`, GPS/camera logic; exposes `pollingDiagnostics` `StateFlow` and adds jittered error backoff.

### Created Unit & Integration Test Files
1. [`VehicleDataMergerTest.kt`](file:///e:/MultiGo%20Reworked/core-model/src/test/java/com/hcmcbustracker/core/model/VehicleDataMergerTest.kt) (4 tests)
2. [`AdaptiveConcurrencyControllerTest.kt`](file:///e:/MultiGo%20Reworked/core-network/src/test/java/com/hcmcbustracker/core/network/polling/AdaptiveConcurrencyControllerTest.kt) (3 tests)
3. [`SharedStopCoalescerTest.kt`](file:///e:/MultiGo%20Reworked/core-network/src/test/java/com/hcmcbustracker/core/network/polling/SharedStopCoalescerTest.kt) (4 tests)
4. [`GlobalPollingCoordinatorTest.kt`](file:///e:/MultiGo%20Reworked/core-network/src/test/java/com/hcmcbustracker/core/network/polling/GlobalPollingCoordinatorTest.kt) (3 tests)
5. [`PollingBenchmarkTest.kt`](file:///e:/MultiGo%20Reworked/core-network/src/test/java/com/hcmcbustracker/core/network/polling/PollingBenchmarkTest.kt) (1 synthetic benchmark suite)
6. [`MultiRoutePollingTest.kt`](file:///e:/MultiGo%20Reworked/core-database/src/test/java/com/hcmcbustracker/core/database/MultiRoutePollingTest.kt) (2 Robolectric Room + Repo integration tests)

---

## 3. Known Remaining Gaps

- No attached Android emulator/physical device was present (`adb.exe devices` empty), so live on-device diagnostics UI logging over cellular/Wi-Fi remains pending hardware verification.
- `C = 4` represents a strict application ceiling only; actual server rate-limit thresholds on EBMS Cloudflare WAF remain undocumented externally.
