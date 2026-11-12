# Phase 11 — Round 4A: Current Polling Audit & Concurrent Polling Architecture Analysis

**Date:** 2026-11-12  
**Mode:** Research Only (Zero code changes)

---

## 1. Current Polling & Concurrency Audit

Inspected [BusRepositoryImpl.kt](file:///e:/MultiGo%20Reworked/core-database/src/main/java/com/hcmcbustracker/core/database/repository/BusRepositoryImpl.kt), [PredictionViewModel.kt](file:///e:/MultiGo%20Reworked/feature-prediction/src/main/java/com/hcmcbustracker/feature/prediction/PredictionViewModel.kt), [MapViewModel.kt](file:///e:/MultiGo%20Reworked/feature-map/src/main/java/com/hcmcbustracker/feature/map/MapViewModel.kt), [RouteVehicleTelemetry.kt](file:///e:/MultiGo%20Reworked/core-model/src/main/java/com/hcmcbustracker/core/model/RouteVehicleTelemetry.kt), [NetworkModule.kt](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/di/NetworkModule.kt), and [SafeApiCall.kt](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/util/SafeApiCall.kt):

### Answers to Section 4.1 Audit Questions

1. **By route or by vehicle?**  
   - **By stop (Station Screen) or by route-variant checkpoints (Map Screen):** `PredictionViewModel` polls 1 stop (`predictbystopid/{stopId}`); `MapViewModel` polls 1 active `(routeId, varId)` via `BusRepositoryImpl.fetchRouteVariantVehicles`, which selects `3..5` evenly spaced checkpoint stops along the route corridor (`selectCheckpointStops`) and merges/deduplicates all vehicles found via `RouteVehicleAggregator`. Never polls per individual vehicle.
2. **Is anything serialized?**  
   - Yes, intentionally within a single screen: `PredictionViewModel` serializes `refresh()` and polling via `queryMutex.withLock` + `refreshJob?.isActive == true` guard so manual pull-to-refresh and auto-polling cannot overlap.
   - Across checkpoints of 1 route variant, `BusRepositoryImpl` runs **bounded concurrency (`Semaphore(4)`)** via `coroutineScope { checkpoints.map { async { semaphore.withPermit { ... } } }.awaitAll() }`.
3. **Can multiple routes be fetched concurrently today?**  
   - Partially at the transport layer (`OkHttpClient` uses default dispatcher `maxRequests = 64`, `maxRequestsPerHost = 5`!), **not** at the ViewModel/Repository scheduler level: `MapViewModel` tracks a single `(activeRoute, activeVariant)` (`routePollingJob`). Moreover, OkHttp's default **`maxRequestsPerHost = 5`** silently queues any `> 5` concurrent calls to `apicms.ebms.vn` anyway unless configured explicitly.
4. **How long does 1 polling cycle take today?**  
   - Single-stop screen (`PredictionViewModel`): `~230–770 ms`.  
   - Single-route map (`MapViewModel`, `5` checkpoints, `Semaphore(4)`): `~2` HTTP waves (`4` + `1`) $\approx$ **`500–1,000 ms`** under normal EBMS response times (`~250 ms/req`), up to `8,000 ms` per-checkpoint timeout on stall.
5. **Are there duplicate requests?**  
   - Within the same ViewModel: guarded cleanly by `queryMutex` + `refreshJob` (`PredictionViewModel`) and `activePollingRouteVar` + `routeJob?.cancel()` (`MapViewModel`).
   - **Cross-screen / Multi-route gap (future risk):** If multi-route / home-nearby tracking is added later, different routes sharing major hubs (e.g., `4223` Hàm Nghi serves `01, 44, 65, 86, 102`, `1401` serves `13, 31, 38, 56, 69, 152`) would hit the **same `stopId`** redundantly unless request coalescing is added.
6. **Cancellation on navigation / lifecycle?**  
   - Verified: `ON_PAUSE` cancels `pollingJob`/`timerJob` (`PredictionViewModel.stopRealtimePolling`) and `routePollingJob` (`MapViewModel.onMapPaused`); `switchVariant` and `clearRoute` cancel `routeJob` and `routePollingJob` immediately; `SafeApiCall` rethrows `CancellationException`.
7. **Error backoff?**  
   - Verified: Exponential backoff (`base -> 2x -> 4x -> 8x`, clamped `[10s..120s]`) plus `HTTP 429 Retry-After` parsing clamped `[5s..300s]` (default `30s`). Notice missing jitter today, meaning simultaneous errors could align retries.
8. **Rate-limit risk?**  
   - Low for single-route (`4–5` requests every `15s` $\approx 0.33\text{ req/s}$). **High** if scaled naively to `10–20` simultaneous routes without global semaphore + stop-coalescing (`100` burst requests per cycle risks triggering Cloudflare WAF / `HTTP 429`).

---

## 2. Comparison of Three Polling Architectures

| Criteria | Method 1: Sequential (`C = 1`) | Method 2: Bounded Concurrent (Fixed `C`) | Method 3: Adaptive Concurrent (AIMD `C = 1..4`) |
| :--- | :--- | :--- | :--- |
| **Cycle Completion Time** (for `M` requests, avg `L`) | $M \cdot L$ (linearly slow: ~13.4s for 10 routes, ~26.7s for 20 routes) | $\lceil M / C \rceil \cdot L$ (~3.3s for 10 routes @ `C=4`, ~6.8s for 20 routes @ `C=4`) | Near Bounded (`~3.6–7.0s` healthy), gracefully widens on congestion |
| **Concurrent In-Flight Requests** | Always `1` | Fixed cap `C` (e.g. `2..4` global permits) | Dynamic `C in [1, maxCap]` tuned by AIMD feedback |
| **Client Resource Usage (RAM / Sockets)** | Minimal (`1` socket, ~98–278 KB) | Low & strictly bounded (`C` sockets, ~212–392 KB) | Low & bounded (`<= maxCap` sockets, ~212–392 KB) |
| **Rate-Limit (`HTTP 429`) Risk** | Very Low | Low if `C <= 4` + host rate limiter; spikes if `C >= 8` | **Lowest among concurrent modes** — halves `C` immediately on `429`/`5xx`/slow RTT |
| **Scalability (5 → 20 Routes)** | Poor — cycle time exceeds `15s` poll period at ~6 routes | Good up to ~10–15 routes if combined with stop-coalescing | **Best** — protects EBMS stability when users watch many routes or hubs |
| **Implementation Complexity** | Trivial | Low (already partially present via `Semaphore(4)` per route, needs global scope) | Moderate (requires global metrics tracker, AIMD controller, jittered backoff) |

---

## 3. Safe Concurrency & Rate-Limit Control Design

If multi-route concurrent polling is needed in a future phase, the scheduler must retain **every existing guard** (`queryMutex`, `activePollingRouteVar`, `SafeApiCall` timeouts, monotonic `dts` merge) and add 10 layered safety controls:

1. **Global Host Semaphore (`Semaphore(permits <= 4)` shared across all callers):** Never allow N routes × 4 checkpoints = `4N` parallel requests; enforce a single process-wide cap toward `apicms.ebms.vn`.
2. **In-Flight Request Deduplication / Coalescing:** Map `(stopId)` -> active `Deferred<NetworkResult<List<Prediction>>>`. If Route 01, Route 44, and Route 102 all need checkpoint `4223` within a `2s` freshness window, send **1 HTTP GET**, not 3! *(Because `predictbystopid/{stopId}` already returns ALL routes at that stop in 1 payload!)*
3. **Token-Bucket Rate Limiter per Endpoint:** Soft cap e.g. max `3–4 req/s` sustained, burst `<= 4`.
4. **Explicit Strict Timeouts:** Connect `10s`, per-request `withTimeoutOrNull(8_000 ms)` (already in place via `SafeApiCall`).
5. **AIMD Concurrency Adaptation:** Start/target `C = 3..4`; halve `C` (`max(1, C/2)`) upon `HTTP 429`, `HTTP 5xx`, timeout `408`, or p95 RTT `> 1,500 ms`; recover `+1` only after consecutive healthy responses.
6. **Exponential Backoff with Full Jitter:** Add `+-15–25%` random jitter on top of `[10s..120s]` backoff so multi-route retries never synchronize.
7. **Deterministic Cancellation:** Structured `CoroutineScope` bound to foreground lifecycle (`ON_RESUME`/`ON_PAUSE`); `CancellationException` never caught/swallowed.
8. **Bounded Retry:** `0` automatic immediate retries inside a failed cycle for polling (wait for next jittered cycle or honor `Retry-After`).
9. **GPS Age & Unchanged-Tick Awareness:** Avoid polling a stop faster than `15s` when all active vehicles at that stop recently ticked or sit in Tier-3 unchanged state (`isGpsTimestampUnchanged`).
10. **Strict Non-Overlap Rule:** Never dispatch a poll for `stopId` while an earlier request for the same `stopId` is still in flight.
