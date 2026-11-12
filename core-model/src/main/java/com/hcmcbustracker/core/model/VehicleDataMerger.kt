package com.hcmcbustracker.core.model

/**
 * Normalized telemetry record wrapped with explicit data-source provenance so cross-source
 * comparisons never confuse derived arrival distances with raw vehicle GPS fixes.
 */
data class SourcedVehicleTelemetry(
    val telemetry: RouteVehicleTelemetry,
    val sourceType: GpsSourceType = GpsSourceType.EBMS_PREDICTION_REST,
    val capabilityRank: Int = 1,
    val isDerivedPrediction: Boolean = true
)

/**
 * Normalized key uniquely identifying one tracked vehicle operating on a specific route direction.
 */
data class VehicleObservationKey(
    val routeId: Int,
    val varId: Int,
    val normalizedPlate: String
)

/**
 * Pure domain merger for vehicle telemetry records.
 *
 * Guarantees:
 * - Normalizes vehicle plate identifiers and rejects blank/malformed IDs.
 * - Preserves source provenance ([GpsSourceType], [isDerivedPrediction]).
 * - Strictly separates GPS timestamp ([RouteVehicleTelemetry.gpsTimestampMillis]) from
 *   response receipt time ([RouteVehicleTelemetry.responseReceivedAtMillis]).
 * - Never overwrites newer valid GPS timestamps with older, null, or anomalous future timestamps.
 */
object VehicleDataMerger {

    private val VALID_PLATE_REGEX = Regex("^[A-Z0-9]{4,12}$")

    /**
     * Extracts uppercase alphanumeric plate identifier; returns null when missing or malformed.
     */
    fun normalizePlateIdentifier(rawPlate: String?): String? {
        if (rawPlate.isNullOrBlank()) return null
        val normalized = rawPlate.trim()
            .uppercase()
            .replace("-", "")
            .replace(".", "")
            .replace(" ", "")
        if (!VALID_PLATE_REGEX.matches(normalized)) return null
        return normalized
    }

    /**
     * Merges an [incoming] vehicle record into an [existing] record for the same route/direction/vehicle.
     */
    fun merge(
        existing: SourcedVehicleTelemetry?,
        incoming: SourcedVehicleTelemetry,
        currentMillis: Long = System.currentTimeMillis()
    ): SourcedVehicleTelemetry? {
        val incPlate = normalizePlateIdentifier(incoming.telemetry.plateNumber) ?: return existing
        val toleranceMs = PositionCompensationConfig.DEFAULT.futureTimestampToleranceMs

        val sanitizedIncomingTs = incoming.telemetry.gpsTimestampMillis?.takeIf { ts ->
            ts > 0L && ts <= currentMillis + toleranceMs
        }
        val cleanIncomingTelemetry = if (sanitizedIncomingTs == incoming.telemetry.gpsTimestampMillis) {
            incoming.telemetry
        } else {
            incoming.telemetry.copy(
                gpsTimestampMillis = null,
                freshnessState = GpsFreshnessState.UNKNOWN,
                latencyCategory = GpsLatencyCategory.UNKNOWN,
                validity = VehiclePredictionValidity.INSUFFICIENT_DATA
            )
        }
        val cleanIncoming = incoming.copy(telemetry = cleanIncomingTelemetry)

        if (existing == null) return cleanIncoming

        val existingPlate = normalizePlateIdentifier(existing.telemetry.plateNumber) ?: return cleanIncoming
        if (existingPlate != incPlate ||
            existing.telemetry.routeId != cleanIncoming.telemetry.routeId ||
            existing.telemetry.varId != cleanIncoming.telemetry.varId
        ) {
            // Different vehicles or route/directions must never be merged together
            return existing
        }

        val oldTs = existing.telemetry.gpsTimestampMillis?.takeIf { ts ->
            ts > 0L && ts <= currentMillis + toleranceMs
        }
        val newTs = cleanIncoming.telemetry.gpsTimestampMillis

        // Never overwrite valid GPS record with missing/unreliable/future timestamp
        if (oldTs != null && newTs == null) {
            return existing
        }
        if (oldTs == null && newTs != null) {
            return cleanIncoming
        }

        // Same source: delegate directly to strict monotonic timestamp merge
        if (existing.sourceType == cleanIncoming.sourceType) {
            val mergedTelemetry = RouteVehicleAggregator.mergeMonotonically(
                existing = existing.telemetry,
                incoming = cleanIncoming.telemetry,
                currentMillis = currentMillis
            )
            return cleanIncoming.copy(telemetry = mergedTelemetry)
        }

        // Different sources: compare strictly via GPS timestamps (never via HTTP response time)
        if (oldTs != null && newTs != null) {
            return when {
                newTs > oldTs -> cleanIncoming
                newTs < oldTs -> existing
                cleanIncoming.capabilityRank > existing.capabilityRank -> cleanIncoming
                else -> existing
            }
        }

        // Neither has a reliable GPS timestamp: cannot assert freshness dominance, mark UNKNOWN provenance
        return existing.copy(
            sourceType = GpsSourceType.UNKNOWN,
            telemetry = existing.telemetry.copy(
                freshnessState = GpsFreshnessState.UNKNOWN,
                latencyCategory = GpsLatencyCategory.UNKNOWN
            )
        )
    }

    /**
     * Merges a batch of route-wide records against previous state maps while preserving monotonic ordering.
     */
    fun mergeBatch(
        previous: Map<String, SourcedVehicleTelemetry>,
        incoming: List<SourcedVehicleTelemetry>,
        currentMillis: Long = System.currentTimeMillis()
    ): Map<String, SourcedVehicleTelemetry> {
        val result = LinkedHashMap<String, SourcedVehicleTelemetry>(previous.size + incoming.size)
        for ((k, v) in previous) {
            val norm = normalizePlateIdentifier(k) ?: continue
            result[norm] = v
        }
        for (item in incoming) {
            val norm = normalizePlateIdentifier(item.telemetry.plateNumber) ?: continue
            val merged = merge(result[norm], item, currentMillis)
            if (merged != null) {
                result[norm] = merged
            }
        }
        return result
    }
}
