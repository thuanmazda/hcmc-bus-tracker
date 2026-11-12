package com.hcmcbustracker.core.model

/**
 * Classification of GPS packet freshness for an incoming transit vehicle.
 */
enum class GpsFreshnessState {
    FRESH,      // <= 125s old & timestamp updated recently
    AGING,      // > 125s and <= 180s
    STALE,      // > 180s
    UNCHANGED,  // dts identical across consecutive polls
    UNKNOWN     // missing or unparseable dts
}

/**
 * Neutral operational categorization of vehicle telemetry derived strictly from EBMS payload.
 * Follows Phase 10 Rule 5.3: never fabricates operational claims beyond payload fields.
 */
enum class VehiclePredictionValidity {
    VALID_FORECAST,      // Valid route/variant, sts == 0, valid positive distance, valid/recent GPS
    STALE_FORECAST,      // Valid coordinates/route but GPS age > 120s
    INSUFFICIENT_DATA    // Missing plate, invalid distance/speed, or abnormal sts
}

/**
 * Deduplicated, timestamp-audited telemetry snapshot of a single bus on a route variant.
 */
data class RouteVehicleTelemetry(
    val routeId: Int,
    val varId: Int,
    val plateNumber: String,
    val formattedPlate: String,
    val checkpointStopId: Int,
    val checkpointStopName: String,
    val rawDistanceMeters: Double,
    val speedKmH: Double,
    val etaSeconds: Int,
    val gpsTimestampIso: String,
    val gpsTimestampMillis: Long?,
    val requestStartedAtMillis: Long,
    val responseReceivedAtMillis: Long,
    val predictionReceivedAtMillis: Long,
    val statusCode: Int,
    val isGpsTimestampUnchanged: Boolean = false,
    val consecutiveUnchangedPolls: Int = 0,
    val validity: VehiclePredictionValidity = VehiclePredictionValidity.VALID_FORECAST,
    val freshnessState: GpsFreshnessState = GpsFreshnessState.FRESH,
    val predictionTargetStopId: Int = checkpointStopId,
    val selectedStopId: Int? = null,
    val renderedAtMillis: Long = predictionReceivedAtMillis,
    val latencyCategory: GpsLatencyCategory = GpsLatencyCategory.UNKNOWN
) {
    val predictionUpdatedAtMillis: Long
        get() = predictionReceivedAtMillis

    val networkLatencyMillis: Long
        get() = maxOf(0L, responseReceivedAtMillis - requestStartedAtMillis)

    val dataProcessingDelayMillis: Long
        get() = maxOf(0L, renderedAtMillis - responseReceivedAtMillis)

    fun computeGpsAgeSeconds(nowMillis: Long = System.currentTimeMillis()): Long {
        val ts = gpsTimestampMillis ?: return -1L
        if (ts > nowMillis + PositionCompensationConfig.DEFAULT.futureTimestampToleranceMs) return -1L
        return maxOf(0L, (nowMillis - ts) / 1000L)
    }
}

/**
 * Pure domain aggregator and deduplicator for route-wide vehicle tracking across checkpoint stops.
 */
object RouteVehicleAggregator {

    /**
     * Aggregates predictions collected from one or more checkpoint stops on [routeId]/[varId],
     * deduplicates multiple appearances of the same vehicle plate, and merges monotonically
     * against [previousVehicles] to ensure out-of-order delayed responses never overwrite fresher data.
     */
    fun aggregateAndMerge(
        routeId: Int,
        varId: Int,
        predictions: List<Prediction>,
        requestStartedAtMillis: Long,
        predictionReceivedAtMillis: Long = System.currentTimeMillis(),
        previousVehicles: Map<String, RouteVehicleTelemetry> = emptyMap(),
        currentMillis: Long = System.currentTimeMillis()
    ): DeduplicationResult {
        val groupedByPlate = LinkedHashMap<String, MutableList<CandidateRecord>>()
        var totalRawCount = 0

        for (pred in predictions) {
            if (pred.routeId != routeId || pred.varId != varId) continue
            for (vehicle in pred.vehicles) {
                totalRawCount++
                val normalizedPlate = vehicle.plateNumber.trim().uppercase()
                if (normalizedPlate.isBlank()) continue

                val gpsMillis = PositionCompensationEngine.parseGpsTimestampMillis(vehicle.timestampIso)
                groupedByPlate.getOrPut(normalizedPlate) { mutableListOf() }.add(
                    CandidateRecord(
                        prediction = pred,
                        vehicle = vehicle,
                        gpsTimestampMillis = gpsMillis
                    )
                )
            }
        }

        val resolved = LinkedHashMap<String, RouteVehicleTelemetry>()
        var duplicatesRemoved = 0

        for ((plateKey, candidates) in groupedByPlate) {
            if (candidates.size > 1) {
                duplicatesRemoved += (candidates.size - 1)
            }
            val bestCandidate = pickBestCandidate(candidates) ?: continue
            val newRecord = toTelemetryRecord(
                routeId = routeId,
                varId = varId,
                candidate = bestCandidate,
                requestStartedAtMillis = requestStartedAtMillis,
                predictionReceivedAtMillis = predictionReceivedAtMillis,
                currentMillis = currentMillis
            )

            // Monotonic comparison against previous poll state
            val existing = previousVehicles[plateKey]
            val merged = mergeMonotonically(existing, newRecord, currentMillis)
            resolved[plateKey] = merged
        }

        return DeduplicationResult(
            vehicles = resolved.values.toList().sortedBy { it.plateNumber },
            totalRawRecords = totalRawCount,
            duplicatesRemoved = duplicatesRemoved
        )
    }

    /**
     * Selects the single authoritative candidate for the same vehicle plate within the same batch:
     * 1. Prefer the candidate with the newest [gpsTimestampMillis].
     * 2. If GPS timestamps are equal, prefer the smallest valid positive [distanceMeters]
     *    (closest downstream checkpoint ahead of the bus, minimizing polyline extrapolation drift).
     */
    private fun pickBestCandidate(candidates: List<CandidateRecord>): CandidateRecord? {
        return candidates.sortedWith(
            Comparator { a, b ->
                val tA = a.gpsTimestampMillis ?: Long.MIN_VALUE
                val tB = b.gpsTimestampMillis ?: Long.MIN_VALUE
                if (tA != tB) {
                    // Descending timestamp (newest first)
                    return@Comparator tB.compareTo(tA)
                }
                // Same timestamp: prefer smallest non-negative distance
                val dA = if (a.vehicle.distanceMeters >= 0.0) a.vehicle.distanceMeters else Double.MAX_VALUE
                val dB = if (b.vehicle.distanceMeters >= 0.0) b.vehicle.distanceMeters else Double.MAX_VALUE
                dA.compareTo(dB)
            }
        ).firstOrNull()
    }

    /**
     * Prevents out-of-order delayed responses from rolling back to older GPS timestamps,
     * and detects when EBMS backend is repeating an unchanged `dts` tick across consecutive polls.
     */
    fun mergeMonotonically(
        existing: RouteVehicleTelemetry?,
        incoming: RouteVehicleTelemetry,
        currentMillis: Long = System.currentTimeMillis()
    ): RouteVehicleTelemetry {
        if (existing == null) return incoming
        if (existing.routeId != incoming.routeId || existing.varId != incoming.varId) {
            return incoming
        }

        val oldT = existing.gpsTimestampMillis
        val newT = incoming.gpsTimestampMillis

        if (oldT != null && newT != null) {
            if (newT < oldT) {
                // Incoming packet is OLDER than existing snapshot -> reject rollback, keep existing!
                return existing
            }
            if (newT == oldT) {
                // Backend returned the exact same GPS snapshot (`dts` did not advance)
                val newCount = existing.consecutiveUnchangedPolls + 1
                val isFuture = newT > currentMillis + PositionCompensationConfig.DEFAULT.futureTimestampToleranceMs
                val ageSec = if (isFuture) -1L else maxOf(0L, (currentMillis - newT) / 1000L)
                val updatedFreshness = when {
                    isFuture -> GpsFreshnessState.UNKNOWN
                    ageSec > PositionCompensationEngine.STALE_THRESHOLD_SECONDS -> GpsFreshnessState.STALE
                    else -> GpsFreshnessState.UNCHANGED
                }
                val updatedCategory = PositionCompensationEngine.classifyGpsLatency(ageSec)
                // Pick closer checkpoint stop if a different poll found a smaller valid distance for the same snapshot
                val preferIncomingDist = incoming.rawDistanceMeters in 0.0..existing.rawDistanceMeters
                val chosen = if (preferIncomingDist) incoming else existing
                return chosen.copy(
                    isGpsTimestampUnchanged = true,
                    consecutiveUnchangedPolls = newCount,
                    freshnessState = updatedFreshness,
                    latencyCategory = updatedCategory,
                    predictionReceivedAtMillis = incoming.predictionReceivedAtMillis,
                    responseReceivedAtMillis = incoming.responseReceivedAtMillis,
                    requestStartedAtMillis = incoming.requestStartedAtMillis,
                    renderedAtMillis = currentMillis
                )
            }
        }

        // Newer GPS packet arrived (or first valid packet replacing invalid null timestamp)! Reset unchanged counter.
        if (existing.gpsTimestampMillis != null && incoming.gpsTimestampMillis == null) {
            return existing
        }
        return incoming.copy(
            isGpsTimestampUnchanged = false,
            consecutiveUnchangedPolls = 0
        )
    }

    private fun toTelemetryRecord(
        routeId: Int,
        varId: Int,
        candidate: CandidateRecord,
        requestStartedAtMillis: Long,
        predictionReceivedAtMillis: Long,
        currentMillis: Long
    ): RouteVehicleTelemetry {
        val v = candidate.vehicle
        val p = candidate.prediction
        val parsedGpsMs = candidate.gpsTimestampMillis
        val isFuture = parsedGpsMs != null &&
            parsedGpsMs > currentMillis + PositionCompensationConfig.DEFAULT.futureTimestampToleranceMs
        val gpsMs = if (isFuture) null else parsedGpsMs
        val ageSec = if (gpsMs != null) maxOf(0L, (currentMillis - gpsMs) / 1000L) else -1L
        val latencyCat = PositionCompensationEngine.classifyGpsLatency(ageSec)

        val freshness = when {
            gpsMs == null -> GpsFreshnessState.UNKNOWN
            ageSec > PositionCompensationEngine.STALE_THRESHOLD_SECONDS -> GpsFreshnessState.STALE
            ageSec > PositionCompensationEngine.MAX_PROJECTION_AGE_SECONDS -> GpsFreshnessState.AGING
            else -> GpsFreshnessState.FRESH
        }

        val isSpeedSane = v.speedKmH >= 0.0 && v.speedKmH <= PositionCompensationEngine.MAX_VALID_SPEED_KMH
        val isDistanceSane = v.distanceMeters >= 0.0
        val validity = when {
            !isDistanceSane || !isSpeedSane || gpsMs == null || v.status < 0 -> VehiclePredictionValidity.INSUFFICIENT_DATA
            freshness == GpsFreshnessState.STALE || freshness == GpsFreshnessState.AGING -> VehiclePredictionValidity.STALE_FORECAST
            else -> VehiclePredictionValidity.VALID_FORECAST
        }

        return RouteVehicleTelemetry(
            routeId = routeId,
            varId = varId,
            plateNumber = v.plateNumber.trim(),
            formattedPlate = v.formattedPlate,
            checkpointStopId = p.stopId,
            checkpointStopName = p.stopName,
            rawDistanceMeters = v.distanceMeters,
            speedKmH = v.speedKmH,
            etaSeconds = v.etaSeconds,
            gpsTimestampIso = v.timestampIso,
            gpsTimestampMillis = gpsMs,
            requestStartedAtMillis = requestStartedAtMillis,
            responseReceivedAtMillis = v.responseReceivedTimeMillis,
            predictionReceivedAtMillis = predictionReceivedAtMillis,
            statusCode = v.status,
            isGpsTimestampUnchanged = false,
            consecutiveUnchangedPolls = 0,
            validity = validity,
            freshnessState = freshness,
            predictionTargetStopId = p.stopId,
            selectedStopId = null,
            renderedAtMillis = currentMillis,
            latencyCategory = latencyCat
        )
    }

    private data class CandidateRecord(
        val prediction: Prediction,
        val vehicle: ArrivalVehicle,
        val gpsTimestampMillis: Long?
    )

    data class DeduplicationResult(
        val vehicles: List<RouteVehicleTelemetry>,
        val totalRawRecords: Int,
        val duplicatesRemoved: Int
    )
}
