package com.hcmcbustracker.core.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Status categorization of the dead-reckoning latency compensation result.
 */
enum class CompensationStatus {
    /**
     * Fresh, valid GPS data projected forward along the vehicle trajectory.
     */
    LIVE_PROJECTED,

    /**
     * Vehicle speed is near zero (<= 1.5 km/h). Vehicle is stationary; no projection applied.
     */
    STATIONARY,

    /**
     * Projected distance equals or exceeds remaining distance to stop.
     * Vehicle has reached or is at the destination stop.
     */
    AT_STOP,

    /**
     * GPS packet age exceeds safe linear projection limit (> 120 seconds).
     * Linear extrapolation is suspended to prevent runaway position estimates.
     */
    STALE_DATA,

    /**
     * Vehicle speed is negative or unrealistically high for city transit (> 90 km/h).
     */
    INVALID_SPEED,

    /**
     * GPS timestamp is missing, malformed, or cannot be determined.
     */
    INVALID_TIMESTAMP,

    /**
     * Direction or route sequence mismatch. Projection suspended.
     */
    DIRECTION_MISMATCH
}

/**
 * User-visible GPS freshness tier per Phase 11 thresholds.
 */
enum class GpsLatencyCategory(val labelVi: String) {
    FRESH("Mới"),
    SLIGHT_DELAY("Có độ trễ nhẹ"),
    DELAYED("Có độ trễ"),
    STALE("Dữ liệu cũ"),
    UNKNOWN("Không xác định")
}

/**
 * Origin / confidence classification of displayed vehicle coordinates.
 */
enum class PositionSource(val labelVi: String) {
    INTERPOLATED_ON_ROUTE("Nội suy theo tuyến (tại GPS)"),
    LATENCY_COMPENSATED("Dự đoán bù trễ theo tuyến"),
    HELD_AT_LAST_VALID_GPS("Giữ vị trí hợp lệ cuối (GPS cũ)"),
    UNKNOWN("Chưa đủ dữ liệu vị trí")
}

/**
 * Operational arrival status relative to target / selected stop along the route corridor.
 */
enum class VehicleArrivalStatus(val labelVi: String) {
    MOVING("Đang di chuyển"),
    APPROACHING_STOP("Sắp đến trạm"),
    ARRIVING_AT_STOP("Đang đến trạm"),
    AT_STOP("Đang dừng tại trạm"),
    PASSED_STOP("Đã đi qua trạm"),
    UNKNOWN("Không đủ dữ liệu xác định")
}

/**
 * Centralized thresholds for GPS latency classification, bounded dead-reckoning projection,
 * and stop arrival classification. Single source of truth across engines, ViewModels, and UI.
 */
data class ArrivalStatusThresholds(
    val atStopDistanceMeters: Double = 40.0,
    val atStopMaxSpeedKmH: Double = 3.0,
    val arrivingDistanceMeters: Double = 85.0,
    val arrivingMaxEtaSeconds: Int = 20,
    val approachingDistanceMeters: Double = 350.0,
    val approachingMaxEtaSeconds: Int = 60,
    val passedStopThresholdMeters: Double = -25.0
)

data class PositionCompensationConfig(
    val minMovingSpeedKmH: Double = 1.5,
    val maxValidSpeedKmH: Double = 90.0,
    val freshThresholdSeconds: Long = 30L,
    val slightDelayThresholdSeconds: Long = 60L,
    val delayedThresholdSeconds: Long = 120L,
    val maxFullProjectionAgeSeconds: Long = 120L,
    val maxAdaptiveProjectionAgeSeconds: Long = 190L,
    val agedTailDecayFactor: Double = 0.35,
    val maxProjectedDistanceMeters: Double = 900.0,
    val staleCutoffSeconds: Long = 240L,
    val futureTimestampToleranceMs: Long = 30_000L,
    val arrivalThresholds: ArrivalStatusThresholds = ArrivalStatusThresholds()
) {
    companion object {
        val DEFAULT = PositionCompensationConfig()
    }
}

/**
 * Domain data class encapsulating the full audit trail of latency compensation.
 */
data class CompensationResult(
    val rawDistanceMeters: Double,
    val effectiveDistanceMeters: Double,
    val projectedDistanceMeters: Double,
    val gpsAgeSeconds: Long,
    val gpsTimestampMillis: Long?,
    val responseReceivedAtMillis: Long,
    val calculatedAtMillis: Long,
    val status: CompensationStatus,
    val isProjected: Boolean,
    val latencyCategory: GpsLatencyCategory = GpsLatencyCategory.UNKNOWN,
    val isAdaptiveAgedProjection: Boolean = false,
    val unclampedProjectedDistanceMeters: Double = projectedDistanceMeters
)

/**
 * Pure domain engine responsible for GPS latency compensation using bounded dead reckoning
 * and centralized arrival/freshness evaluation.
 * Distinguishes GPS packet timestamp, response received time, and render calculation time.
 */
object PositionCompensationEngine {

    const val MIN_MOVING_SPEED_KMH = 1.5
    const val MAX_VALID_SPEED_KMH = 90.0
    const val MAX_PROJECTION_AGE_SECONDS = 120L
    const val STALE_THRESHOLD_SECONDS = 180L

    private val VN_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")

    fun classifyGpsLatency(
        gpsAgeSeconds: Long,
        config: PositionCompensationConfig = PositionCompensationConfig.DEFAULT
    ): GpsLatencyCategory {
        if (gpsAgeSeconds < 0L) return GpsLatencyCategory.UNKNOWN
        return when {
            gpsAgeSeconds <= config.freshThresholdSeconds -> GpsLatencyCategory.FRESH
            gpsAgeSeconds <= config.slightDelayThresholdSeconds -> GpsLatencyCategory.SLIGHT_DELAY
            gpsAgeSeconds <= config.delayedThresholdSeconds -> GpsLatencyCategory.DELAYED
            else -> GpsLatencyCategory.STALE
        }
    }

    /**
     * Evaluates deterministic vehicle arrival state from along-route signed distance, speed,
     * reliable ETA, GPS age, direction validity, and geometry availability.
     */
    fun evaluateArrivalStatus(
        distanceToTargetStopMeters: Double?,
        speedKmH: Double,
        etaSeconds: Int?,
        gpsAgeSeconds: Long,
        isDirectionValid: Boolean = true,
        hasValidGeometry: Boolean = true,
        config: PositionCompensationConfig = PositionCompensationConfig.DEFAULT
    ): VehicleArrivalStatus {
        val t = config.arrivalThresholds
        if (!isDirectionValid ||
            !hasValidGeometry ||
            distanceToTargetStopMeters == null ||
            distanceToTargetStopMeters.isNaN() ||
            gpsAgeSeconds < 0L ||
            speedKmH < 0.0 ||
            speedKmH > config.maxValidSpeedKmH
        ) {
            return VehicleArrivalStatus.UNKNOWN
        }

        // Definitively passed selected/target stop along the route corridor
        if (distanceToTargetStopMeters < t.passedStopThresholdMeters) {
            return VehicleArrivalStatus.PASSED_STOP
        }

        // Prevent high-confidence arrival badges when GPS is severely outdated (> 190s)
        if (gpsAgeSeconds > config.maxAdaptiveProjectionAgeSeconds) {
            return VehicleArrivalStatus.UNKNOWN
        }

        val absDist = kotlin.math.abs(distanceToTargetStopMeters)
        val isReliableFreshForArrival = gpsAgeSeconds <= config.delayedThresholdSeconds

        if (isReliableFreshForArrival && absDist <= t.atStopDistanceMeters && speedKmH <= t.atStopMaxSpeedKmH) {
            return VehicleArrivalStatus.AT_STOP
        }
        if (isReliableFreshForArrival && absDist <= t.arrivingDistanceMeters) {
            return VehicleArrivalStatus.ARRIVING_AT_STOP
        }
        if (distanceToTargetStopMeters <= t.approachingDistanceMeters ||
            (isReliableFreshForArrival && etaSeconds != null && etaSeconds in 1..t.approachingMaxEtaSeconds)
        ) {
            return VehicleArrivalStatus.APPROACHING_STOP
        }
        return VehicleArrivalStatus.MOVING
    }

    /**
     * Parses diverse ISO-8601 or epoch timestamp formats produced by EBMS telematics.
     * Guaranteed safe: returns null if the string is empty or cannot be parsed.
     */
    fun parseGpsTimestampMillis(timestampIso: String?): Long? {
        if (timestampIso.isNullOrBlank()) return null
        val trimmed = timestampIso.trim()

        // 1. Try ISO-8601 with offset (e.g. 2026-11-09T21:13:29+07:00)
        try {
            return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {}

        // 2. Try ISO-8601 UTC Instant (e.g. 2026-11-09T14:13:29Z)
        try {
            return Instant.parse(trimmed).toEpochMilli()
        } catch (_: DateTimeParseException) {}

        // 3. Try ISO-8601 local datetime without offset (assumed VN time +07:00)
        try {
            val localDt = LocalDateTime.parse(trimmed)
            return localDt.atZone(VN_ZONE_ID).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {}

        // 4. Try pure epoch numeric millis or seconds
        if (trimmed.all { it.isDigit() }) {
            val num = trimmed.toLongOrNull()
            if (num != null) {
                // If seconds (e.g. 10 digits around 1.7e9), convert to millis
                return if (num < 100_000_000_000L) num * 1000L else num
            }
        }

        return null
    }

    /**
     * Calculates the latency-compensated distance to stop using physical dead reckoning.
     *
     * @param rawDistanceMeters Distance d reported by EBMS API at the GPS timestamp.
     * @param speedKmH Instantaneous speed s in km/h reported by EBMS.
     * @param timestampIso ISO GPS timestamp dts from EBMS telematics packet.
     * @param responseReceivedAtMillis Client timestamp when HTTP response was received.
     * @param currentTimeMillis Evaluation timestamp (default: System.currentTimeMillis()).
     * @param isDirectionValid True if vehicle is verified traveling towards the destination stop.
     */
    fun calculateCompensation(
        rawDistanceMeters: Double,
        speedKmH: Double,
        timestampIso: String?,
        responseReceivedAtMillis: Long,
        currentTimeMillis: Long = System.currentTimeMillis(),
        isDirectionValid: Boolean = true,
        config: PositionCompensationConfig = PositionCompensationConfig.DEFAULT,
        allowAdaptiveExtendedProjection: Boolean = false,
        isGpsTimestampFrozen: Boolean = false
    ): CompensationResult {
        // 1. Check Direction validity
        if (!isDirectionValid) {
            return CompensationResult(
                rawDistanceMeters = rawDistanceMeters,
                effectiveDistanceMeters = rawDistanceMeters,
                projectedDistanceMeters = 0.0,
                gpsAgeSeconds = 0L,
                gpsTimestampMillis = null,
                responseReceivedAtMillis = responseReceivedAtMillis,
                calculatedAtMillis = currentTimeMillis,
                status = CompensationStatus.DIRECTION_MISMATCH,
                isProjected = false,
                latencyCategory = GpsLatencyCategory.UNKNOWN
            )
        }

        // 2. Parse GPS packet timestamp & guard anomalous future timestamps (> 30s ahead)
        val gpsTimestampMillis = parseGpsTimestampMillis(timestampIso)
        if (gpsTimestampMillis == null || gpsTimestampMillis > currentTimeMillis + config.futureTimestampToleranceMs) {
            return CompensationResult(
                rawDistanceMeters = rawDistanceMeters,
                effectiveDistanceMeters = rawDistanceMeters,
                projectedDistanceMeters = 0.0,
                gpsAgeSeconds = -1L,
                gpsTimestampMillis = gpsTimestampMillis,
                responseReceivedAtMillis = responseReceivedAtMillis,
                calculatedAtMillis = currentTimeMillis,
                status = CompensationStatus.INVALID_TIMESTAMP,
                isProjected = false,
                latencyCategory = GpsLatencyCategory.UNKNOWN
            )
        }

        // 3. Compute GPS Age (seconds). Minor clock skew (<= 30s) clamps cleanly to 0s.
        val ageSecondsDouble = maxOf(0.0, (currentTimeMillis - gpsTimestampMillis) / 1000.0)
        val gpsAgeSeconds = ageSecondsDouble.toLong()
        val latencyCategory = classifyGpsLatency(gpsAgeSeconds, config)

        // 4. Validate Speed
        if (speedKmH < 0.0 || speedKmH > config.maxValidSpeedKmH || rawDistanceMeters < 0.0 || rawDistanceMeters.isNaN()) {
            return CompensationResult(
                rawDistanceMeters = rawDistanceMeters,
                effectiveDistanceMeters = maxOf(0.0, rawDistanceMeters),
                projectedDistanceMeters = 0.0,
                gpsAgeSeconds = gpsAgeSeconds,
                gpsTimestampMillis = gpsTimestampMillis,
                responseReceivedAtMillis = responseReceivedAtMillis,
                calculatedAtMillis = currentTimeMillis,
                status = CompensationStatus.INVALID_SPEED,
                isProjected = false,
                latencyCategory = latencyCategory
            )
        }

        // 5. Stationary vehicle check (speed <= 1.5 km/h)
        if (speedKmH <= config.minMovingSpeedKmH) {
            return CompensationResult(
                rawDistanceMeters = rawDistanceMeters,
                effectiveDistanceMeters = rawDistanceMeters,
                projectedDistanceMeters = 0.0,
                gpsAgeSeconds = gpsAgeSeconds,
                gpsTimestampMillis = gpsTimestampMillis,
                responseReceivedAtMillis = responseReceivedAtMillis,
                calculatedAtMillis = currentTimeMillis,
                status = CompensationStatus.STATIONARY,
                isProjected = false,
                latencyCategory = latencyCategory
            )
        }

        // 6. Stale / Frozen check:
        //    - Legacy strict mode cuts off at > maxFullProjectionAgeSeconds (120s).
        //    - Adaptive EBMS mode allows smooth bounded tail projection in (120s..190s] ONLY when dts is not frozen.
        val maxAllowedAge = if (allowAdaptiveExtendedProjection && !isGpsTimestampFrozen) {
            config.maxAdaptiveProjectionAgeSeconds
        } else {
            config.maxFullProjectionAgeSeconds
        }

        if (ageSecondsDouble > maxAllowedAge) {
            return CompensationResult(
                rawDistanceMeters = rawDistanceMeters,
                effectiveDistanceMeters = rawDistanceMeters,
                projectedDistanceMeters = 0.0,
                gpsAgeSeconds = gpsAgeSeconds,
                gpsTimestampMillis = gpsTimestampMillis,
                responseReceivedAtMillis = responseReceivedAtMillis,
                calculatedAtMillis = currentTimeMillis,
                status = CompensationStatus.STALE_DATA,
                isProjected = false,
                latencyCategory = latencyCategory
            )
        }

        // 7. Continuous C0 bounded dead-reckoning projection:
        //    - [0..120s]: full speedMps * age
        //    - (120..190s] (adaptive): continuous tapered tail velocity * decay factor, bounded by maxProjectedDistanceMeters
        val speedMps = speedKmH * (1000.0 / 3600.0)
        val rawProjected = if (ageSecondsDouble <= config.maxFullProjectionAgeSeconds) {
            speedMps * ageSecondsDouble
        } else {
            val baseProj = speedMps * config.maxFullProjectionAgeSeconds
            val extraSec = ageSecondsDouble - config.maxFullProjectionAgeSeconds
            baseProj + (speedMps * extraSec * config.agedTailDecayFactor)
        }
        val maxProjCap = if (allowAdaptiveExtendedProjection) config.maxProjectedDistanceMeters else Double.MAX_VALUE
        val projectedDistance = minOf(rawProjected, maxProjCap)
        val effective = rawDistanceMeters - projectedDistance
        val isAgedTail = ageSecondsDouble > config.maxFullProjectionAgeSeconds

        // 8. Vehicle arrived at or passed checkpoint/target stop
        if (effective <= 0.0) {
            return CompensationResult(
                rawDistanceMeters = rawDistanceMeters,
                effectiveDistanceMeters = 0.0,
                projectedDistanceMeters = minOf(rawDistanceMeters, projectedDistance),
                gpsAgeSeconds = gpsAgeSeconds,
                gpsTimestampMillis = gpsTimestampMillis,
                responseReceivedAtMillis = responseReceivedAtMillis,
                calculatedAtMillis = currentTimeMillis,
                status = CompensationStatus.AT_STOP,
                isProjected = true,
                latencyCategory = latencyCategory,
                isAdaptiveAgedProjection = isAgedTail,
                unclampedProjectedDistanceMeters = projectedDistance
            )
        }

        // 9. Standard Live or Adaptive Aged Projection
        return CompensationResult(
            rawDistanceMeters = rawDistanceMeters,
            effectiveDistanceMeters = effective,
            projectedDistanceMeters = projectedDistance,
            gpsAgeSeconds = gpsAgeSeconds,
            gpsTimestampMillis = gpsTimestampMillis,
            responseReceivedAtMillis = responseReceivedAtMillis,
            calculatedAtMillis = currentTimeMillis,
            status = CompensationStatus.LIVE_PROJECTED,
            isProjected = true,
            latencyCategory = latencyCategory,
            isAdaptiveAgedProjection = isAgedTail
        )
    }

    /**
     * Convenience entry point for EBMS live route-wide tracking where real server GPS updates lag 120-188s.
     */
    fun calculateAdaptiveCompensation(
        rawDistanceMeters: Double,
        speedKmH: Double,
        timestampIso: String?,
        responseReceivedAtMillis: Long,
        currentTimeMillis: Long = System.currentTimeMillis(),
        isDirectionValid: Boolean = true,
        isGpsTimestampFrozen: Boolean = false,
        config: PositionCompensationConfig = PositionCompensationConfig.DEFAULT
    ): CompensationResult = calculateCompensation(
        rawDistanceMeters = rawDistanceMeters,
        speedKmH = speedKmH,
        timestampIso = timestampIso,
        responseReceivedAtMillis = responseReceivedAtMillis,
        currentTimeMillis = currentTimeMillis,
        isDirectionValid = isDirectionValid,
        config = config,
        allowAdaptiveExtendedProjection = true,
        isGpsTimestampFrozen = isGpsTimestampFrozen
    )
}
