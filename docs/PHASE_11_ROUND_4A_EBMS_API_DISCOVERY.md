# Phase 11 — Round 4A: EBMS API & Public Real-Time GPS Source Discovery

**Date:** 2026-11-12  
**Mode:** Research Only (Zero code/config modifications)  
**Repository / Workspace Baseline:** `e:\MultiGo Reworked` (Note: `git status/branch/log` returned `fatal: not a git repository`; baseline corresponds to verified Round 4 Release state — `159/159` unit tests PASS, `app-release.apk` `57,432,464` bytes).

---

## 1. Scope & Methodology

Audited all public endpoints on `https://apicms.ebms.vn` and `https://buyttphcm.com.vn` via:
1. Codebase & documentation audit: [EbmsApiService.kt](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/api/EbmsApiService.kt), [PredictionDto.kt](file:///e:/MultiGo%20Reworked/core-network/src/main/java/com/hcmcbustracker/core/network/dto/PredictionDto.kt), [API_CATALOG.md](file:///e:/MultiGo%20Reworked/docs/phase-0/API_CATALOG.md), [WEBSITE_ANALYSIS.md](file:///e:/MultiGo%20Reworked/docs/phase-0/WEBSITE_ANALYSIS.md).
2. Read-only inspection of live public HTML & JS bundles on `https://buyttphcm.com.vn/Prediction`, `/Route`, `/RouteDetail?rId=1`, `/RouteFinding` (build cache tag `?cdv=167`, inspected `2026-11-12 15:36–15:37 ICT`).
3. Single-shot read-only HTTP checks (`timeout = 10s`, no retries, no auth bypass, no rate-limit evasion).

---

## 2. Complete Endpoint Matrix (Including Live Portal Bundle Verification)

| Endpoint (`https://apicms.ebms.vn...`) | Method | Auth | Parameters | Response | Vehicle ID | GPS Coords `(Lat, Lng)` | Timestamp | Speed | Heading | Update Frequency | Rate Limit | Data Provenance |
| :--- | :---: | :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- | :--- | :--- |
| `/businfo/getroutesforcombo` | GET | None | None | Array of `{value, lable, key}` (~17.6 KB) | No | No | No | No | No | Static (schedule/network updates) | Cloudflare WAF (uncalibrated threshold) | Static transit catalog |
| `/businfo/getallroute` | GET | None | None | Array of full route objects (legacy encoding) | No | No | No | No | No | Static | Same CDN | Static transit catalog |
| `/businfo/getroutebyid/{routeId}` | GET | None | Path: `routeId` | JSON object (`RouteId, RouteNo, Distance, OperationTime, ...`) | No | No | No | No | No | Static | Same CDN | Static transit metadata |
| `/businfo/getvarsbyroute/{routeId}` | GET | None | Path: `routeId` | Array of directional variants (`RouteVarId, Outbound, Distance, RunningTime`) | No | No | No | No | No | Static | Same CDN | Static transit metadata |
| `/businfo/getpathsbyvar/{routeId}/{varId}` | GET | None | Path: `routeId, varId` | `{lat: Double[], lng: Double[]}` | No | **Route polyline** (not vehicle GPS) | No | No | No | Static road geometry | Same CDN | Static GIS geometry |
| `/businfo/getstopsbyvar/{routeId}/{varId}` | GET | None | Path: `routeId, varId` | Array of ordered stops with `StopId, Code, Name, Lat, Lng, Routes` | No | **Stop coords** (not vehicle GPS) | No | No | No | Static | Same CDN | Static GIS stop database |
| `/businfo/getstopsbyroutevar/{routeId}/{varId}` | GET | None | Path: `routeId, varId` | Simplified stop list (`key, value`) for dropdowns | No | No | No | No | No | Static | Same CDN | Static transit UI catalog |
| `/businfo/getstopsforautocomplete` | GET | None | None | `{l[], v[], g[]}` (~726 KB, 6,414 stops) | No | No | No | No | No | Static | Same CDN | Static search index |
| `/businfo/getstoplocations` *(spotted in JS bundle)* | GET | None | None | `{l: null, v: null, lt[6412], ln[6412], g[6412]}` (`164,318 B`, HTTP `200`, `967 ms`) | No | **Stop coords** (`lt/ln` per `StopId`, NOT buses) | No | No | No | Static (`Cache-Control: no-cache`) | Same CDN | Static stop location lookup |
| `/businfo/getstopsinbounds/{minLon}/{minLat}/{maxLon}/{maxLat}` | GET | None | 4 bounding-box floats | Array of stop objects in viewport | No | **Stop coords** only | No | No | No | Static | Same CDN | Static spatial stop query |
| `/businfo/getroutesthroughstop/{stopId}` | GET | None | Path: `stopId` | Array of `{RouteId, RouteNo, RouteName}` | No | No | No | No | No | Static | Same CDN | Static stop-route mapping |
| `/businfo/gettimetablebyroute/{routeId}` | GET | None | Path: `routeId` | Array of active schedule metadata | No | No | Operation window only | No | No | Schedule-based | Same CDN | Static timetable |
| `/businfo/gettripsbytimetable/{routeId}/{timetableId}` | GET | None | Path: `routeId, timetableId` | Array of `{TripId, StartTime, EndTime}` | No | No | Planned schedule | No | No | Schedule-based | Same CDN | Planned trips |
| `/pathfinding/getpathbystop/{from}/{to}/{routeNumber}` | GET | None | Stop IDs/coords, `routeNumber` | Computed transit itinerary steps | No | Stop/path geometry only | Planned ETA | No | No | On-demand routing | Same CDN | Derived transit routing |
| **`/prediction/predictbystopid/{stopId}`** | GET | None | Path: `stopId` (int) | Array per route-var `{r, rN, rNo, s, sN, v, vN, arrs: [{v, d, t, s, sts, dts}]}` | **Yes (`v`: plate string)** | **NO `(lat, lng)`** (`d` is meters along route!) | **Yes (`dts`: ISO-8601 `+07:00`)** | **Yes (`s`: km/h)** | **NO** | Server ticks `10–30s` per vehicle (`CF: DYNAMIC`) | Cloudflare + HTTP `429` risk on burst | **Hybrid:** raw telemetry (`v, s, dts`) + EBMS backend derived (`d, t`) |
| **`/prediction/predictbystopcode/{stopCode}`** | GET | None | Path: `stopCode` (e.g. `Q1%20122`) | Identical schema to `predictbystopid` | **Yes (`v`)** | **NO `(lat, lng)`** | **Yes (`dts`)** | **Yes (`s`)** | **NO** | Same (`261.1 ms` measured) | Same | Same |
| **`/prediction/{routeId}/{varId}/{stopId}/predictnextstops/{maxNextStops}`** | GET | None | `routeId, varId, stopId, maxNextStops` | Array of next N stops along variant, each containing `arrs` | **Yes (`v`)** | **NO `(lat, lng)`** | **Yes (`dts`)** | **Yes (`s`)** | **NO** | Same (`258.9 ms` measured, identical `dts` as `predictbystopid`) | Same | Derived downstream projections from same backend tick |
| Dedicated Vehicle GPS / Fleet API (`/vehicle/*`, `/gps/*`, WS/SSE) | — | — | — | **NOT PRESENT** (`UNAVAILABLE`) | — | — | — | — | — | — | — | — |

*(Also verified secondary helper host `GEO_API_URL = http://api.thongtingiaothong.vn/v1/geocoding/` referenced in `L.MapBase.js` — address geocoding only, zero bus tracking capability.)*

---

## 3. Verification Against Live Portal JS Bundles (`buyttphcm.com.vn`, `?cdv=167`)

Scanned all relevant public scripts on `/Prediction`, `/Route`, `/RouteDetail`, and `/RouteFinding`:
- `L.Config.js`, `L.MapBase.js`, `L.RouteMap.js`, `prediction.js`, `L.MarkerStopArround.js`, `routevar.js`, `timetable.js`, `iBus_RouteDetail/module.js`, `iBus_PathFinding/addressSearch.js`, `iBus_PathFinding/module.js`.
- **Negative findings (verified honestly):**
  1. **No raw `(lat, lng)` vehicle feed:** All 3 prediction endpoints return **only** `v` (plate), `d` (along-route distance in meters to queried stop `s`), `t` (predicted ETA in seconds), `s` (speed in km/h), `sts` (status integer), and `dts` (GPS timestamp).
  2. **Critical distinction:** `d` is a **1D derived scalar along route geometry** computed by the EBMS backend after matching a bus's blackbox GPS to its route variant — **not** raw 2D `(lat, lng)` GPS coordinates.
  3. **No WebSocket / SSE / SignalR / MQTT:** Zero `ws://`, `wss://`, `EventSource`, or `SignalR` references exist anywhere in public portal scripts; the official web portal uses `jQuery.ajax` REST polling with `PREDICT_INTERVAL = 15000` (`15s`).
  4. **Same `dts` across endpoints:** Querying `/prediction/1/1/4223/predictnextstops/3` at `15:36:19` returned the exact same `dts` (`2026-11-12T15:32:59+07:00` for `50E61852`) as `/prediction/predictbystopid/4223`, confirming both endpoints read from the same EBMS backend state.

---

## 4. Key Conclusion for Hướng A

Under all publicly accessible, authorized EBMS endpoints (`apicms.ebms.vn` and `buyttphcm.com.vn`), **no fresher or direct-coordinate GPS endpoint exists**. `/prediction/predictbystopid/{stopId}` (and its stop-code / next-stops variants) remains the sole public telemetry source.
