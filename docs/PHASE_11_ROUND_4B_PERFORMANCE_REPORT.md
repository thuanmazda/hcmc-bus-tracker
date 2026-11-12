# Phase 11 — Round 4B: Synthetic Polling Performance Comparison Report

**Date:** 2026-11-12  
**Test Class:** [`PollingBenchmarkTest.kt`](file:///e:/MultiGo%20Reworked/core-network/src/test/java/com/hcmcbustracker/core/network/polling/PollingBenchmarkTest.kt)

> [!IMPORTANT]
> **Synthetic Benchmark Notice:** All metrics below were measured inside Kotlin Coroutines `runTest` virtual time (`TestCoroutineScheduler`, simulated `250 ms` per HTTP checkpoint call, `5` checkpoints per route including `2` shared corridor/hub stops `4223` & `1401`) driving real `GlobalPollingCoordinator` + `SharedStopCoalescer` code. **These are NOT live stress-test benchmarks against production EBMS servers.**

---

## 1. Before vs After Benchmark Results (`5`, `10`, and `20` Routes across `C = 1, 2, 3, 4`)

| Routes | Mode & Cap (`C`) | Shared-Stop Coalescing | Cycle Duration (`ms`) | Logical Checkpoints | Actual Network Reqs | Requests Saved (Dedup + Cache) | Peak In-Flight | Speedup vs Baseline (`C=1`, No Coalesce) |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **5** | Baseline (`C=1`) | No | `6,250` | 25 | 25 | 0 | 1 | `1.00x` |
| **5** | Coalesced (`C=1`) | Yes | `4,250` | 25 | 17 | 8 (`32%`) | 1 | `1.47x` |
| **5** | Coalesced (`C=2`) | Yes | `2,250` | 25 | 17 | 8 (`32%`) | 2 | `2.78x` |
| **5** | Coalesced (`C=3`) | Yes | `1,500` | 25 | 17 | 8 (`32%`) | 3 | `4.17x` |
| **5** | Coalesced (`C=4`) | Yes | **`1,250`** | 25 | 17 | 8 (`32%`) | 4 | **`5.00x`** |
| **10** | Baseline (`C=1`) | No | `12,500` | 50 | 50 | 0 | 1 | `1.00x` |
| **10** | Coalesced (`C=1`) | Yes | `8,000` | 50 | 32 | 18 (`36%`) | 1 | `1.56x` |
| **10** | Coalesced (`C=2`) | Yes | `4,000` | 50 | 32 | 18 (`36%`) | 2 | `3.13x` |
| **10** | Coalesced (`C=3`) | Yes | `2,750` | 50 | 32 | 18 (`36%`) | 3 | `4.55x` |
| **10** | Coalesced (`C=4`) | Yes | **`2,000`** | 50 | 32 | 18 (`36%`) | 4 | **`6.25x`** |
| **20** | Baseline (`C=1`) | No | **`25,000`** *(exceeds 15s)* | 100 | 100 | 0 | 1 | `1.00x` |
| **20** | Coalesced (`C=1`) | Yes | `15,250` | 100 | 61 | 39 (`39%`) | 1 | `1.64x` |
| **20** | Coalesced (`C=2`) | Yes | `7,750` | 100 | 61 | 39 (`39%`) | 2 | `3.23x` |
| **20** | Coalesced (`C=3`) | Yes | `5,250` | 100 | 61 | 39 (`39%`) | 3 | `4.76x` |
| **20** | Coalesced (`C=4`) | Yes | **`4,000`** | 100 | 61 | 39 (`39%`) | 4 | **`6.25x`** |

*(Simulated `errorCount = 0`, `timeoutCount = 0` under nominal RTT; `HTTP 429` & error scenarios are verified separately in `AdaptiveConcurrencyControllerTest` and `GlobalPollingCoordinatorTest`; max live permit objects in memory never exceed `4` in-flight entries regardless of `5/10/20` route count.)*

---

## 2. Observations & Practical Interpretation

1. **HTTP Requests Eliminated:** Even at `C = 1`, deduplicating just `2` shared transit corridor hubs per route eliminates **`32–39%`** of redundant HTTP GET calls (`100 -> 61` requests for 20 routes).
2. **Staying Within the `15s` Poll Window:** Without coalescing or concurrency, `20` routes take `25.0s` (breaking the `15s` poll budget); with `SharedStopCoalescer` + `GlobalPollingCoordinator (C = 4)`, all `20` routes finish in **`4.0s`** (`6.25x` faster) while staying strictly within `<= 4` concurrent requests to `apicms.ebms.vn`.
3. **Truthfulness on GPS Age:** Cycle compression eliminates client-side HTTP queuing lag only; underlying `dts` timestamps emitted by EBMS remain governed by upstream vehicle/gateway latency tiers (`34–50s` Tier 1, `171–205s` Tier 3 as measured in Round 4A).
