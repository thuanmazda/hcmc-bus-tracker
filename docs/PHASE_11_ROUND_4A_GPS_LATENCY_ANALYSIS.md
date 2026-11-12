# Phase 11 — Round 4A: EBMS GPS Latency & Root Cause Analysis

**Date:** 2026-11-12  
**Mode:** Research Only (Safe spaced HTTP measurements, zero code changes)

---

## 1. Empirical Measurements (Measured 2026-11-12 ICT)

### 1.1 Multi-Stop Simultaneous Snapshot (`15:38:42+07:00` / `08:38:42 GMT`)

Executed 1 single GET per stop across 3 major HCMC stops (`4223` Hàm Nghi, `1401` Q1 122 Bến Thành, `321` Chợ Lớn), yielding **56 vehicle-stop telemetry records** across **16 bus routes**:

| Route Group | Observed Routes | Sample Plates | Server Date (`UTC+7`) | Observed `dts` Range (`UTC+7`) | `age_srv = Date - dts` | Observed Spread Within Group |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **Tier 1 (Fresher Fleet)** | `69`, `60-1`, `DL02`, `33`, `38` | `51B29028`, `50H41185`, `29F01684`, `50E53423`, `50E65467` | `15:38:42` | `15:37:52` – `15:38:08` | **`34.0s – 50.0s`** | Tight cluster (`~34–50s`), independent of distance (`103m` to `23.2km`) |
| **Tier 2 (Moderate Lag)** | `19`, `05`, `13`, `56` | `51B32275`, `50H26180`, `51B30783`, `51B26764` | `15:38:42` | `15:37:15` – `15:37:54` | **`48.0s – 87.0s`** | Typically `62–87s` (`13` & `56`: `74–87s`; `19` & `05`: `48–70s`) |
| **Tier 3 (Aged Fleet)** | `01`, `31`, `44`, `65`, `86`, `102`, `152` | `50E61563`, `50H74596`, `50E20534`, `50E85118`, `50H85419` | `15:38:42` | `15:35:36` – `15:35:49` | **`173.0s – 186.0s`** | Consistent `+130s` gap relative to Tier 1 **inside the same HTTP response** |

*(Also earlier at `15:36:19+07:00`: Route `31` was `198.0s` old while Route `38` in the same JSON array of Stop `1401` was `67.0s` old. Overall across the 56 observations at `15:38:42`: `min = 34.0s`, `median = 76.0s`, `max = 186.0s`.)*

### 1.2 Spaced Polling Tick Cadence (Stop `1401`, `11s` Interval)

| Timestamp (`Date`, `UTC+7`) | HTTP Latency | Route `56` (`51B26616`) | Route `38` (`50E65372`) | Route `152` (`50E77264`) |
| :---: | :---: | :---: | :---: | :---: |
| `15:38:42` | `229.2 ms` | `dts=15:37:26` (`age=76s`, `d=529.1m`) | `dts=15:37:52` (`age=50s`) | `dts=15:35:43` (`age=179s`, `d=3189.1m`) |
| `15:38:54` (`+12s`) | `769.0 ms` | `dts=15:37:46` (**`+20s`**, `age=68s`, `d=462.6m`) | `dts=15:37:52` (unchanged, `age=62s`) | `dts=15:36:03` (**`+20s`**, `age=171s`, `d=3189.5m`) |
| `15:39:05` (`+11s`) | `245.2 ms` | `dts=15:37:46` (unchanged, `age=79s`) | `dts=15:37:52` (unchanged, `age=73s`) | `dts=15:36:13` (**`+10s`**, `age=172s`, `d=3179.8m`) |

*(Headers on every request: `CF-Cache-Status: DYNAMIC`, `Cache-Control: no-cache`, zero `Age` header; HTTP roundtrip `229–769 ms`; clock skew `client - Date` within `-1.35s .. -2.29s`.)*

---

## 2. Proven Facts vs Hypotheses

### 2.1 Proven Facts (Empirically Verified)
1. **Network & CDN are NOT the bottleneck:** Every `/prediction/` call bypasses Cloudflare edge cache (`CF-Cache-Status: DYNAMIC`, `Cache-Control: no-cache`) and completes in **`229–769 ms`** (`< 0.8s`). Client polling speed contributes `< 1s` HTTP latency.
2. **Not a timezone / unit / clock skew error:** `dts` carries explicit `+07:00` offset matching HTTP `Date` to `~2s`.
3. **Not a single uniform EBMS backend stall:** Inside the **exact same HTTP response** on Stop `1401` (`15:38:42`), Route `69` buses carry `age_srv = 34–35s` while Route `152` and `31` carry `age_srv = 173–181s` — a **140-second systematic divergence by route/fleet** that persists simultaneously across stops (`4223`, `1401`, `321`).
4. **Even newly published ticks have a minimum floor (`>= 34s`):** When `50E77264` ticked forward by `+10s` (`15:36:03 -> 15:36:13`) during our observation at `15:39:05`, its freshly emitted `dts` was already `172.0s` old (`+10s` tick still lagged by ~`170s`), while Tier 1 fleets (`69`) floor at ~`34–40s`.

### 2.2 Technical Hypotheses (Upstream Pipeline Causes Beyond Client Visibility)
Because `apicms.ebms.vn` exposes only the output JSON (`arrs`) without internal ingestion timestamps, the exact split inside the `34s` floor and the extra `~40s` (Tier 2) or `~135s` (Tier 3) offset cannot be directly inspected from client telemetry, and is hypothesized to arise from:
- **Universal ~34–40s baseline floor:** Combined GSHT blackbox reporting cycle (`10–30s`), QCVN telematics gateway relay, and EBMS backend map-matching / ETA calculation pipeline (`d, t`).
- **Route/fleet-specific `+40s` to `+140s` delay (Tier 2 & Tier 3):** Different bus enterprises/cooperatives use distinct GPS/GSHT vendors and intermediate forwarding servers or queued batch synchronization into the central EBMS database (notably routes `01, 31, 44, 65, 86, 102, 152` clustering near `171–205s` while `33, 38, 60-1, 69, DL02` cluster near `34–50s`).

---

## 3. Diagnostic Implication

- Any client-side polling optimization (faster polling or concurrency) can only eliminate **redundant HTTP waiting time** (e.g., bringing `t_refresh` closer to `0.3s` per cycle) and detect a new `dts` tick as soon as EBMS publishes it.
- Neither client concurrency nor cache tuning can reduce the **server-published `age_srv` floor (`34s` on Tier 1, `171–205s` on Tier 3)** without upstream changes at EBMS or direct operator GPS feeds.
