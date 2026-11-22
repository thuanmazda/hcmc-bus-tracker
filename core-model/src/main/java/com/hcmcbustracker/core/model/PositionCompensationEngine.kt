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

data class VehicleGpsSample(
    val distanceMeters: Double,
    val speedKmH: Double,
    val timestampMillis: Long,
    val responseReceivedMillis: Long = System.currentTimeMillis()
)

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
    val freshThresholdSeconds: Long = 60L,
    val slightDelayThresholdSeconds: Long = 120L,
    val delayedThresholdSeconds: Long = 300L,
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
        val isReliableFreshForArrival = gpsAgeSeconds <= config.slightDelayThresholdSeconds

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
        isGpsTimestampFrozen: Boolean = false,
        historySamples: List<VehicleGpsSample> = emptyList()
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
        val validHistory = historySamples.filter { it.timestampMillis in 1..currentTimeMillis }
            .sortedBy { it.timestampMillis }
        val hasRecentMovement = validHistory.size >= 2 && run {
            val prev = validHistory[validHistory.size - 2]
            val curr = validHistory.last()
            val dt = (curr.timestampMillis - prev.timestampMillis) / 1000.0
            val dd = prev.distanceMeters - curr.distanceMeters
            dt in 1.0..90.0 && dd > 5.0
        }

        if (speedKmH <= config.minMovingSpeedKmH && !hasRecentMovement) {
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

        // 7. Controlled Kinematic Projection (Phase 13 Method C):
        //    Integrates 1-sample, 2-sample or 3-sample kinematic estimation with stop proximity deceleration
        val instantSpeedMps = speedKmH * (1000.0 / 3600.0)
        val (kinematicSpeedMps, kinematicAcceleration) = computeKinematicVelocityAndAcceleration(
            instantSpeedMps = instantSpeedMps,
            validHistory = validHistory
        )

        // Stop proximity deceleration envelope: when within 100m of stop, bound speed by stopping curve
        val proximitySpeedMps = if (rawDistanceMeters in 1.0..100.0) {
            val maxComfortSpeed = kotlin.math.sqrt(2.0 * 1.0 * rawDistanceMeters)
            minOf(kinematicSpeedMps, maxComfortSpeed)
        } else {
            kinematicSpeedMps
        }

        val rawProjected = if (ageSecondsDouble <= config.maxFullProjectionAgeSeconds) {
            val base = proximitySpeedMps * ageSecondsDouble
            val accelComp = 0.5 * kinematicAcceleration * (ageSecondsDouble * ageSecondsDouble)
            (base + accelComp).coerceAtLeast(0.0)
        } else {
            val baseProj = proximitySpeedMps * config.maxFullProjectionAgeSeconds
            val extraSec = ageSecondsDouble - config.maxFullProjectionAgeSeconds
            baseProj + (proximitySpeedMps * extraSec * config.agedTailDecayFactor)
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
        config: PositionCompensationConfig = PositionCompensationConfig.DEFAULT,
        historySamples: List<VehicleGpsSample> = emptyList()
    ): CompensationResult = calculateCompensation(
        rawDistanceMeters = rawDistanceMeters,
        speedKmH = speedKmH,
        timestampIso = timestampIso,
        responseReceivedAtMillis = responseReceivedAtMillis,
        currentTimeMillis = currentTimeMillis,
        isDirectionValid = isDirectionValid,
        config = config,
        allowAdaptiveExtendedProjection = true,
        isGpsTimestampFrozen = isGpsTimestampFrozen,
        historySamples = historySamples
    )

    /**
     * Evaluates multi-sample kinematic velocity and acceleration:
     * - 2 samples: Blends instantaneous speed (60%) and observed segment displacement velocity (40%).
     * - 3+ samples: Adds acceleration trend with outlier rejection and physical clamping [-1.5, +1.0] m/s^2.
     */
    internal fun computeKinematicVelocityAndAcceleration(
        instantSpeedMps: Double,
        validHistory: List<VehicleGpsSample>
    ): Pair<Double, Double> {
        if (validHistory.size < 2) {
            return instantSpeedMps to 0.0
        }

        if (validHistory.size == 2) {
            val s0 = validHistory[0]
            val s1 = validHistory[1]
            val dt = (s1.timestampMillis - s0.timestampMillis) / 1000.0
            val dd = s0.distanceMeters - s1.distanceMeters
            if (dt in 1.0..90.0 && dd >= 0.0) {
                val vObs = (dd / dt).coerceIn(0.0, 25.0)
                val vBlend = 0.6 * instantSpeedMps + 0.4 * vObs
                return vBlend to 0.0
            }
            return instantSpeedMps to 0.0
        }

        // 3+ samples: s0, s1, s2
        val s0 = validHistory[validHistory.size - 3]
        val s1 = validHistory[validHistory.size - 2]
        val s2 = validHistory.last()

        val dt1 = (s1.timestampMillis - s0.timestampMillis) / 1000.0
        val dt2 = (s2.timestampMillis - s1.timestampMillis) / 1000.0

        if (dt1 < 1.0 || dt2 < 1.0 || dt1 > 90.0 || dt2 > 90.0) {
            return instantSpeedMps to 0.0
        }

        val dd1 = s0.distanceMeters - s1.distanceMeters
        val dd2 = s1.distanceMeters - s2.distanceMeters

        if (dd1 < 0.0 || dd2 < 0.0) {
            // Out of order distance, fallback to instant
            return instantSpeedMps to 0.0
        }

        val v1 = (dd1 / dt1).coerceIn(0.0, 25.0)
        val v2 = (dd2 / dt2).coerceIn(0.0, 25.0)

        // Outlier detection: sudden unphysical velocity surge (> 15 m/s jump)
        if (kotlin.math.abs(v2 - v1) > 15.0) {
            val vBlend = 0.6 * instantSpeedMps + 0.4 * v2
            return vBlend to 0.0
        }

        val dtMid = 0.5 * (dt1 + dt2)
        val aObs = ((v2 - v1) / dtMid).coerceIn(-1.5, 1.0)
        val vBlend = 0.5 * instantSpeedMps + 0.5 * v2

        return vBlend to aObs
    }
}
