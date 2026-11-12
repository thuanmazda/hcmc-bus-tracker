# Phase 11 — Round 4A: Final Recommendation & Round 4B Roadmap

**Date:** 2026-11-12  
**Mode:** Research Only — Awaiting user approval before any Round 4B implementation

---

## 1. Executive Decision Summary

1. **No fresher public GPS endpoint exists on EBMS (`apicms.ebms.vn` or `buyttphcm.com.vn`).**  
   Empirical audit of all public portal JS bundles (`?cdv=167`) and API endpoints confirms zero live `(lat, lng)` vehicle feeds, zero WebSockets, and zero SSE streams. The newly inspected `/businfo/getstoplocations` (`6,412` stop coords) is static stop geometry only.
2. **The `34–186s` `dts` age is an upstream fleet/GSHT-to-EBMS ingestion characteristic, NOT HTTP or client cache latency.**  
   Multi-stop probing at `15:38:42+07:00` (`56` vehicle-stop observations across `16` routes) proved HTTP RTT is only `229–769 ms` (`CF-Cache-Status: DYNAMIC`) while **`age_srv = Date - dts` splits cleanly by route fleet inside the exact same HTTP response**:
   - Tier 1 routes (`33, 38, 60-1, 69, DL02`): **`34–50s`**
   - Tier 2 routes (`05, 13, 19, 56`): **`48–87s`**
   - Tier 3 routes (`01, 31, 44, 65, 86, 102, 152`): **`173–186s`**
3. **Honest Expectation on GPS Age Reduction:**  
   With public EBMS data alone, client engineering **cannot** lower Tier-3 routes' reported `dts` age below `~170s` or break below the `~34s` Tier-1 floor. However, improving client acquisition concurrency & coalescing **can** eliminate unnecessary polling latency, prevent `HTTP 429` throttling, keep multi-route sweeps under `~1.7–3.6s`, and guarantee immediate pickup of every `10–30s` EBMS tick.

---

## 2. Recommended Strategy: Hybrid Adaptive Bounded Acquisition + Existing Compensation

**Adopt Method 3 (Adaptive Bounded Concurrent Polling with Global Semaphore `C = 1..4` + Shared-Stop Coalescing + Jittered Backoff)** paired untouched with our existing Round 2 & Round 3 compensation/UI stack (`PositionCompensationEngine`, `VehicleInterpolationEngine`, split directional markers, honest provenance labels):

- **Do NOT** decrease polling interval below `10–15s` per stop or raise concurrency above `4` toward `apicms.ebms.vn` (local simulation shows `C=10` causes congestion & simulated `429` spikes with minimal cycle gain).
- **Do** deduplicate overlapping `stopId` requests across routes/tabs (`SharedStopCoalescer`), configure `OkHttpClient` `Dispatcher.maxRequestsPerHost` cleanly with our global semaphore, add `±20%` retry jitter, and prepare clean `GpsSourceAdapter` + `EndpointCapabilityRegistry` abstractions so an authorized partner feed (GTFS-RT / HCMC DOT stream) can drop in seamlessly later if credentials are ever obtained.

---

## 3. Small-Step Implementation Plan for Round 4B (If Approved)

1. **Round 4B Step 1 — Domain Registry & Freshness Telemetry (`core-model`):** Add `EndpointCapabilityRegistry`, `GpsSourceAdapter` contract, and `GpsFreshnessMonitor` metrics with unit tests (preserving `PositionCompensationEngine` 100%).
2. **Round 4B Step 2 — Global RateLimitManager, SharedStopCoalescer & Jittered Backoff (`core-network` + `core-database`):** Centralize global host `Semaphore(1..4)` + AIMD `429`/RTT controller, `5s` in-flight/recent `stopId` coalescing in `BusRepositoryImpl`, keeping `queryMutex`, `activePollingRouteVar`, and monotonic `dts` rules intact.
3. **Round 4B Step 3 — Verification & Benchmarks:** Unit tests for coalescing (e.g., 3 shared-hub routes triggering only 1 HTTP call for stop `4223`), AIMD `429` downgrade/recovery, and regression verification (`testDebugUnitTest`, `assembleDebug`, `assembleRelease`).

**Round 4A Research Complete — Waiting for user confirmation before starting Round 4B.**
