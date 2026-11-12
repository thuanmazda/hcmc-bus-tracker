# Phase 11 — Round 4B: Concurrency, Coalescing & Merger Test Report

**Date:** 2026-11-12  
**Verified Commands:**
- `.\gradlew.bat :core-model:testDebugUnitTest --rerun` → **PASS**
- `.\gradlew.bat :core-network:testDebugUnitTest --rerun` → **PASS**
- `.\gradlew.bat :core-database:testDebugUnitTest --rerun` → **PASS**
- `.\gradlew.bat testDebugUnitTest --rerun` → **176 / 176 PASS across 31 suites** (`0` failures, `0` errors, `0` skipped)

---

## 1. Round 4B Unit & Integration Coverage Matrix

| Category | Test Suite | Tests | Pass | Verified Behaviors |
| :--- | :--- | :---: | :---: | :--- |
| **AIMD Concurrency** | `AdaptiveConcurrencyControllerTest` | 3 | 3 | Starts at `C = 1`; single success does NOT raise `C`; climbs `1 → 2 → 3 → 4` across sustained healthy windows; capped strictly at `4`; `HTTP 429` halves `C` immediately, enforces `Retry-After` + jitter, blocks ramp-up during cooldown; `HTTP 503` + timeouts drop `C` cleanly; exponential backoff bounded with jitter. |
| **Shared-Stop Coalescing** | `SharedStopCoalescerTest` | 4 | 4 | Concurrent identical `PollingResourceKey` requests join 1 HTTP call (`wasDeduplicated = true`); different stops / route variants (`varId=1` vs `2`) never coalesce; expires cleanly past `5s` TTL; `bypassCache = true` returning stale `dts` NEVER overwrites newer cached `dts`; full waiter cancellation cancels underlying coroutine (`inFlightCount() == 0`). |
| **Global Coordinator** | `GlobalPollingCoordinatorTest` | 3 | 3 | Dynamic permit gate enforces `peakInFlight <= maxConcurrency (4)` across 12 simultaneous stops; `HTTP 429` (`Retry-After = 10s`) drops `C` and holds next caller past cooldown; 3 simultaneous route callers hitting hub `4223` emit `1` HTTP execution and increment `deduplicatedRequestCount = 2`. |
| **Data Merger & Registry** | `VehicleDataMergerTest` | 4 | 4 | Newer `gpsTimestampMillis` supersedes older; delayed network packet (`higher responseMs`, `older gpsMs`) NEVER overwrites newer `gpsMs`; `PARTNER_DIRECT_GPS` vs `EBMS_PREDICTION_REST` preserves `isDerivedPrediction` provenance; future timestamps rejected; invalid/blank plates ignored safely. |
| **Multi-Route Integration** | `MultiRoutePollingTest` | 2 | 2 | `BusRepositoryImpl.fetchMultiRouteVehicles` across Routes `01`, `44`, `102` sharing Hub `4223` executes `1` HTTP GET for `4223`, maintains route isolation (`50E61852`, `50E20534`, `50E85118`), prioritizes focused `priorityRouteId`, rejects `95s`-old delayed rollback vs `25s` record, and drops `C` to `1` on `429`. |
| **Synthetic Benchmark** | `PollingBenchmarkTest` | 1 | 1 | Compares `5`, `10`, and `20` routes (`C = 1..4` + coalescing vs sequential without coalescing) using virtual scheduler delays (zero live server load). |

---

## 2. Lifecycle & Leak Safeguards Verified

- `SharedStopCoalescer` tracks atomic waiter counts per `InFlightEntry`: cancelling a single caller leaves peer waiters unaffected, while cancelling the final waiter cancels `entry.job` and removes the entry from `inFlight`.
- `GlobalPollingCoordinator.acquireDynamicPermit` handles `CancellationException` cleanly: removes uncompleted waiter from FIFO `waitQueue`, or returns already handed-off permit back to pool so permits never leak under cancellation.
- No test uses infinite loops or relaxed assertions; all `31` suites passed cleanly with `0` skipped tests.
