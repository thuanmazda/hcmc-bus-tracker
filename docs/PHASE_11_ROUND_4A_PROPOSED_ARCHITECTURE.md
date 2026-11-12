# Phase 11 — Round 4A: Proposed Modular Acquisition Architecture

**Date:** 2026-11-12  
**Mode:** Research / Design Proposal Only (Zero code changes; preserves Phase 5/9/10/11 Round 2–3 engines)

---

## 1. Architectural Overview & Preservation Guarantees

The target acquisition layer inserts cleanly **in front of** existing domain/UI engines without altering:
- `PositionCompensationEngine` & `PositionCompensationConfig` (preserved unchanged)
- `VehicleInterpolationEngine` (preserved unchanged)
- Room schema `BusTrackerDatabase` (`version = 3`) & offline transit dataset
- MapLibre UI, split arrow/label markers, Blue Dot GPS, & `VehicleCameraMode` state machine
- Application ID (`com.hcmcbustracker.app`) & Clean Architecture / MVVM boundaries

```
  [UI ViewModels: MapViewModel / PredictionViewModel]
                        │
                        ▼
        [BoundedPollingScheduler + SharedStopCoalescer]
          ├── RateLimitManager (Global Token Bucket + AIMD C=1..4 + Jittered 429 Backoff)
          └── In-Flight Dedup (1 HTTP GET per stopId within freshness window)
                        │
                        ▼
             [GpsSourceAdapter + EndpointCapabilityRegistry]
               ├── EbmsPredictionSourceAdapter (Current: ACTIVE, 15s poll, NO_HEADING, NO_LATLNG)
               └── FutureDirectGpsAdapter (Optional future authorized GTFS-RT/Partner feed)
                        │
                        ▼
               [VehicleDataMerger + GpsFreshnessMonitor]
                 (Monotonic dts guard, provenance trust rank, no cross-source overwrite of newer dts)
                        │
                        ▼
    [PositionCompensationEngine] ──► [VehicleInterpolationEngine] ──► [UI State / MapLibre]
```

---

## 2. Component Specifications

### 2.1 `EndpointCapabilityRegistry` (`core-model`)
Describes explicit capabilities and trust metadata per `GpsSourceId`:
- `sourceId`: e.g., `EBMS_PREDICTION_REST`, future `AUTHORIZED_GTFS_RT_PARTNER`
- `supportsDirectLatLng: Boolean` (`false` for EBMS Prediction)
- `supportsDirectBearing: Boolean` (`false` for EBMS Prediction)
- `supportsRouteWideSingleCall: Boolean` (`false` for EBMS — requires stop checkpoints)
- `minSafePollIntervalMs: Long` (`10_000L`..`15_000L` for EBMS)
- `baseTrustRank: Int` (direct authenticated telemetry ranks higher than derived projection **only when** `gpsTimestampMillis` is valid and fresher)

### 2.2 `GpsSourceAdapter` (`core-network` / `core-database`)
- Normalizes any raw backend response into a canonical `RawVehicleObservation` tagged with `(sourceId, gpsTimestampMillis, requestStartedAtMillis, responseReceivedAtMillis, d/t/s or lat/lng/bearing)`.
- Today has one production implementation (`EbmsPredictionSourceAdapter` wrapping `EbmsApiService.getPredictionByStopId`). Any future official partner feed plugs in cleanly behind the same interface without rewriting ViewModels or interpolation math.

### 2.3 `RateLimitManager` & `BoundedPollingScheduler` (`core-network` / `core-database`)
Replaces ad-hoc per-screen or per-route network bursts with coordinated client governance:
1. **Process-Wide Concurrency Cap:** Global `Semaphore(permits = currentConcurrencyCap)` (`currentConcurrencyCap` initialized at `3`, AIMD range `1..4`) ensuring total simultaneous requests to `apicms.ebms.vn` never exceed `4`, even when multiple routes or components refresh concurrently.
2. **In-Flight Stop Coalescing & Short Reuse Window:** Keep a concurrent `inFlightStopCalls: ConcurrentHashMap<Int, Deferred<...>>` and a brief `recentStopCache` (e.g., `5_000 ms` TTL for `predictbystopid/{stopId}` unless explicit user force-refresh). When multiple routes share hub stops like `4223` or `1401`, all callers share **one** HTTP call.
3. **Per-Host Token Bucket + AIMD Feedback:** Track `p95RttMs`, `http429Count`, `http5xxCount`, and `timeoutCount`:
   - On `HTTP 429`: Honor `Retry-After` (`[5s..300s]`), halve `currentConcurrencyCap = max(1, cap / 2)`, apply cooldown across the host scheduler.
   - On consecutive `200 OK < 600ms`: Gradually step `currentConcurrencyCap` back up (`+1`) up to ceiling `4`.
4. **Jittered Exponential Backoff:** Add `±20%` random jitter on `15s * 2^k` error delays so retries spread cleanly over time.
5. **Cancellation & Existing Locks Preserved:** Retain `queryMutex` inside `PredictionViewModel` and `activePollingRouteVar` + `routePollingJob?.cancel()` in `MapViewModel`.

### 2.4 `VehicleDataMerger` & `GpsFreshnessMonitor` (`core-model`)
Evolves `RouteVehicleAggregator.mergeMonotonically`:
1. **Keying:** Canonical uppercase trimmed plate (`50E61663`).
2. **Strict Anti-Rollback Rule:** Never overwrite `existing` with `incoming` if `incoming.gpsTimestampMillis < existing.gpsTimestampMillis` (or if `incoming` has future/null timestamp while `existing` is valid).
3. **Multi-Source Merge Guard:** If two sources ever exist, compare `(gpsTimestampMillis, endpointCapability.baseTrustRank)` — **never** blend/average two snapshots from different sources or timestamps, and never let an older direct-GPS packet overwrite a newer EBMS tick (or vice versa).
4. **GpsFreshnessMonitor:** Tracks per-route/fleet tier baseline (`minAgeSrv`, `unchangedStreak`, `freshRatio`) so UI can surface honest server-delay status (e.g., differentiating Tier-1 `~40s` routes vs Tier-3 `~180s` routes without blaming network connectivity).
