package com.hcmcbustracker.feature.map.engine

import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.CompensationStatus
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.LatLngPoint
import com.hcmcbustracker.core.model.PositionCompensationConfig
import com.hcmcbustracker.core.model.PositionCompensationEngine
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Enhanced vehicle position model containing full physical telemetry, latency audit trail,
 * separated stop progress (next stop vs selected stop), and smoothed directional heading.
 */
data class InterpolatedVehiclePosition(
    val lat: Double,
    val lng: Double,
    val bearing: Float,
    val plateNumber: String,
    val speedKmH: Double,
    val rawDistanceMeters: Double = 0.0,
    val effectiveDistanceMeters: Double = 0.0,
    val projectedDistanceMeters: Double = 0.0,
    val gpsAgeSeconds: Long = 0L,
    val gpsTimestampMillis: Long? = null,
    val compensationStatus: CompensationStatus = CompensationStatus.LIVE_PROJECTED,
    val targetStopId: Int = 0,
    val cumulativeDistanceMeters: Double = 0.0,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val nextStopId: Int? = null,
    val nextStopName: String? = null,
    val distanceToNextStopMeters: Double? = null,
    val etaToNextStopSeconds: Int? = null,
    val selectedStopId: Int? = null,
    val selectedStopName: String? = null,
    val distanceToSelectedStopMeters: Double? = null,
    val arrivalStatus: VehicleArrivalStatus = VehicleArrivalStatus.UNKNOWN,
    val positionSource: PositionSource = PositionSource.INTERPOLATED_ON_ROUTE,
    val latencyCategory: GpsLatencyCategory = GpsLatencyCategory.UNKNOWN,
    val hasValidHeading: Boolean = true,
    val isAgedAdaptiveProjection: Boolean = false
)

data class StopPolylineProjection(
    val stop: BusStop,
    val cumulativeDist: Double,
    val crossTrackErrorMeters: Double
)

/**
 * Intelligent Vehicle Tracking Engine.
 *
 * Implements:
 * 1. Physical Dead-Reckoning Latency Compensation using [PositionCompensationEngine].
 * 2. Sequential Monotonic Stop Snapping along route polyline (prevents snapping to loops/return legs).
 * 3. Orthogonal Segment Linear Referencing with off-corridor & branch validation.
 * 4. Multi-source smoothed directional bearing (motion vector + polyline lookahead tangent, 0/360 wrap safe).
 * 5. Separated Next-Stop and Selected-Stop along-route distance & arrival status evaluation.
 */
object VehicleInterpolationEngine {

    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val MAX_CORRIDOR_SNAP_ERROR_METERS = 1500.0
    private const val LOOKAHEAD_BEHIND_METERS = 12.0
    private const val LOOKAHEAD_AHEAD_METERS = 20.0
    private const val MIN_MOTION_BEARING_TRAVEL_METERS = 8.0
    private const val PASSED_CURRENT_STOP_SNAP_TOLERANCE_METERS = 15.0

    /**
     * Interpolates the geographic location, bearing, compensated distance, next stop progress,
     * and arrival state of a bus approaching [targetStopLocation] on [polylinePoints].
     */
    fun interpolatePosition(
        polylinePoints: List<LatLngPoint>,
        targetStopLocation: LatLngPoint,
        targetStopId: Int,
        distanceToStopMeters: Double,
        plateNumber: String,
        speedKmH: Double,
        timestampIso: String? = null,
        responseReceivedAtMillis: Long = System.currentTimeMillis(),
        currentTimeMillis: Long = System.currentTimeMillis(),
        routeStops: List<BusStop> = emptyList(),
        previousPosition: InterpolatedVehiclePosition? = null,
        selectedStopId: Int? = null,
        rawEtaSeconds: Int? = null,
        isDirectionValid: Boolean = true,
        allowAdaptiveExtendedProjection: Boolean = false,
        isGpsTimestampFrozen: Boolean = false,
        config: PositionCompensationConfig = PositionCompensationConfig.DEFAULT
    ): InterpolatedVehiclePosition? {
        if (polylinePoints.size < 2 || !isDirectionValid || distanceToStopMeters.isNaN() || distanceToStopMeters < 0.0) {
            return null
        }

        // 1. Build cumulative distance array along polyline vertices
        val cumDist = buildCumulativeDistances(polylinePoints)
        val totalPolylineLength = cumDist.last()
        if (totalPolylineLength < 5.0) return null

        // 2. Monotonically snap all ordered stops on this variant polyline
        val stopProjections = if (routeStops.isNotEmpty()) {
            snapStopsMonotonically(polylinePoints, cumDist, routeStops)
        } else {
            emptyList()
        }

        // 3. Determine prediction target stop cumulative distance & validate corridor snap
        val matchedTarget = stopProjections.firstOrNull { it.stop.id == targetStopId }
        val targetStopCumulativeDist: Double
        val targetSnapError: Double
        if (matchedTarget != null) {
            targetStopCumulativeDist = matchedTarget.cumulativeDist
            targetSnapError = matchedTarget.crossTrackErrorMeters
        } else {
            val fallbackMatch = projectPointOrthogonalDetailed(
                polylinePoints = polylinePoints,
                cumDist = cumDist,
                point = targetStopLocation,
                searchStartDist = 0.0
            )
            targetStopCumulativeDist = fallbackMatch.cumulativeDist
            targetSnapError = fallbackMatch.crossTrackErrorMeters
        }

        if (targetSnapError > MAX_CORRIDOR_SNAP_ERROR_METERS) {
            return null
        }

        // 4. Apply Dead-Reckoning Latency Compensation (strict Phase 5/9 or bounded adaptive EBMS mode)
        val compensation = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = distanceToStopMeters,
            speedKmH = speedKmH,
            timestampIso = timestampIso,
            responseReceivedAtMillis = responseReceivedAtMillis,
            currentTimeMillis = currentTimeMillis,
            isDirectionValid = isDirectionValid,
            config = config,
            allowAdaptiveExtendedProjection = allowAdaptiveExtendedProjection,
            isGpsTimestampFrozen = isGpsTimestampFrozen
        )

        val effectiveDistance = compensation.effectiveDistanceMeters

        // 5. Calculate vehicle cumulative distance along polyline:
        //    When a selected stop was passed via projection (rawDistance - projected < 0), advance past the stop smoothly
        //    along the variant corridor up to totalPolylineLength instead of pinning at targetStopCumulativeDist.
        val vehicleTargetCumulativeDist = if (compensation.isProjected) {
            (targetStopCumulativeDist - distanceToStopMeters + compensation.unclampedProjectedDistanceMeters)
                .coerceIn(0.0, totalPolylineLength)
        } else {
            (targetStopCumulativeDist - effectiveDistance).coerceIn(0.0, targetStopCumulativeDist)
        }

        // 6. Linear interpolation along polyline segment at vehicleTargetCumulativeDist
        val rawEstimatedPosition = interpolatePointAtDistance(
            polylinePoints = polylinePoints,
            cumDist = cumDist,
            targetDistanceMeters = vehicleTargetCumulativeDist
        )

        // 7. Velocity-bounded Smoothing (when updated within rapid 0.1..5s window for same checkpoint/route)
        val smoothedPosition = if (previousPosition != null &&
            previousPosition.plateNumber == plateNumber &&
            previousPosition.targetStopId == targetStopId
        ) {
            applyVelocityBoundedSmoothing(
                previous = previousPosition,
                current = rawEstimatedPosition,
                speedKmH = speedKmH,
                currentTimeMillis = currentTimeMillis,
                maxAllowedCumulativeDist = totalPolylineLength
            )
        } else {
            rawEstimatedPosition
        }

        // 8. Prevent abrupt backward jump when GPS crosses adaptive/frozen cutoff while stationary or aged
        val holdLastValid = compensation.status == CompensationStatus.STALE_DATA &&
            previousPosition != null &&
            previousPosition.plateNumber == plateNumber &&
            smoothedPosition.cumulativeDistanceMeters < previousPosition.cumulativeDistanceMeters

        val finalLat = if (holdLastValid) previousPosition!!.lat else smoothedPosition.lat
        val finalLng = if (holdLastValid) previousPosition!!.lng else smoothedPosition.lng
        val finalCumDist = if (holdLastValid) previousPosition!!.cumulativeDistanceMeters else smoothedPosition.cumulativeDistanceMeters

        // 9. Resolve multi-source smoothed bearing (0..360)
        val (lookaheadBearing, hasValidLookahead) = computeLookaheadTangent(polylinePoints, cumDist, finalCumDist)
        val (finalBearing, hasValidHeading) = resolveSmoothedBearing(
            currentLat = finalLat,
            currentLng = finalLng,
            currentCumDist = finalCumDist,
            speedKmH = speedKmH,
            lookaheadBearing = lookaheadBearing,
            hasValidLookahead = hasValidLookahead,
            isGpsFrozen = isGpsTimestampFrozen || holdLastValid,
            previousPosition = previousPosition,
            minMovingSpeed = config.minMovingSpeedKmH
        )

        // 10. Resolve Next Stop ahead along variant & Signed Selected Stop distance
        val nextProj = stopProjections.firstOrNull {
            it.cumulativeDist >= finalCumDist - PASSED_CURRENT_STOP_SNAP_TOLERANCE_METERS
        } ?: stopProjections.lastOrNull()
        val distanceToNextStopMeters = nextProj?.let { maxOf(0.0, it.cumulativeDist - finalCumDist) }

        val selectedProj = selectedStopId?.let { sid ->
            stopProjections.firstOrNull { it.stop.id == sid }
        }
        val distanceToSelectedStopMeters = selectedProj?.let { it.cumulativeDist - finalCumDist }

        // Evaluate arrival status relative to user-selected stop if present, else next stop along polyline
        val statusTargetDist = distanceToSelectedStopMeters ?: distanceToNextStopMeters ?: effectiveDistance
        val etaForDisplayStop = computeReliableEtaSeconds(
            displayedStopId = (selectedProj?.stop?.id ?: nextProj?.stop?.id ?: targetStopId),
            predictionTargetStopId = targetStopId,
            distanceToDisplayedStopMeters = maxOf(0.0, statusTargetDist),
            rawEtaSeconds = rawEtaSeconds,
            projectedDistanceMeters = compensation.projectedDistanceMeters,
            speedKmH = speedKmH,
            gpsAgeSeconds = compensation.gpsAgeSeconds,
            config = config
        )

        val arrivalStatus = PositionCompensationEngine.evaluateArrivalStatus(
            distanceToTargetStopMeters = if (selectedProj != null || nextProj != null) statusTargetDist else effectiveDistance,
            speedKmH = speedKmH,
            etaSeconds = etaForDisplayStop,
            gpsAgeSeconds = compensation.gpsAgeSeconds,
            isDirectionValid = isDirectionValid,
            hasValidGeometry = true,
            config = config
        )

        val positionSource = when {
            holdLastValid -> PositionSource.HELD_AT_LAST_VALID_GPS
            compensation.isProjected && compensation.projectedDistanceMeters > 0.0 -> PositionSource.LATENCY_COMPENSATED
            else -> PositionSource.INTERPOLATED_ON_ROUTE
        }

        return InterpolatedVehiclePosition(
            lat = finalLat,
            lng = finalLng,
            bearing = finalBearing,
            plateNumber = plateNumber,
            speedKmH = speedKmH,
            rawDistanceMeters = distanceToStopMeters,
            effectiveDistanceMeters = effectiveDistance,
            projectedDistanceMeters = compensation.projectedDistanceMeters,
            gpsAgeSeconds = compensation.gpsAgeSeconds,
            gpsTimestampMillis = compensation.gpsTimestampMillis,
            compensationStatus = compensation.status,
            targetStopId = targetStopId,
            cumulativeDistanceMeters = finalCumDist,
            updatedAtMillis = currentTimeMillis,
            nextStopId = nextProj?.stop?.id,
            nextStopName = nextProj?.stop?.name,
            distanceToNextStopMeters = distanceToNextStopMeters,
            etaToNextStopSeconds = computeReliableEtaSeconds(
                displayedStopId = nextProj?.stop?.id ?: targetStopId,
                predictionTargetStopId = targetStopId,
                distanceToDisplayedStopMeters = distanceToNextStopMeters,
                rawEtaSeconds = rawEtaSeconds,
                projectedDistanceMeters = compensation.projectedDistanceMeters,
                speedKmH = speedKmH,
                gpsAgeSeconds = compensation.gpsAgeSeconds,
                config = config
            ),
            selectedStopId = selectedProj?.stop?.id ?: selectedStopId,
            selectedStopName = selectedProj?.stop?.name,
            distanceToSelectedStopMeters = distanceToSelectedStopMeters,
            arrivalStatus = arrivalStatus,
            positionSource = positionSource,
            latencyCategory = compensation.latencyCategory,
            hasValidHeading = hasValidHeading,
            isAgedAdaptiveProjection = compensation.isAdaptiveAgedProjection
        )
    }

    fun buildCumulativeDistances(polylinePoints: List<LatLngPoint>): DoubleArray {
        val cumDist = DoubleArray(polylinePoints.size)
        cumDist[0] = 0.0
        for (i in 1 until polylinePoints.size) {
            cumDist[i] = cumDist[i - 1] + computeDistanceMeters(polylinePoints[i - 1], polylinePoints[i])
        }
        return cumDist
    }

    /**
     * Snaps an ordered list of variant stops monotonically onto the route polyline
     * so loops or overlapping opposite lanes never jump backward out of route sequence.
     */
    fun snapStopsMonotonically(
        polylinePoints: List<LatLngPoint>,
        cumDist: DoubleArray,
        routeStops: List<BusStop>
    ): List<StopPolylineProjection> {
        val result = ArrayList<StopPolylineProjection>(routeStops.size)
        var currentMinDist = 0.0
        for (stop in routeStops) {
            if (stop.lat == 0.0 && stop.lng == 0.0) {
                result.add(StopPolylineProjection(stop, currentMinDist, Double.MAX_VALUE))
                continue
            }
            val match = projectPointOrthogonalDetailed(
                polylinePoints = polylinePoints,
                cumDist = cumDist,
                point = LatLngPoint(stop.lat, stop.lng),
                searchStartDist = currentMinDist
            )
            currentMinDist = maxOf(currentMinDist, match.cumulativeDist)
            result.add(StopPolylineProjection(stop, currentMinDist, match.crossTrackErrorMeters))
        }
        return result
    }

    private fun computeReliableEtaSeconds(
        displayedStopId: Int,
        predictionTargetStopId: Int,
        distanceToDisplayedStopMeters: Double?,
        rawEtaSeconds: Int?,
        projectedDistanceMeters: Double,
        speedKmH: Double,
        gpsAgeSeconds: Long,
        config: PositionCompensationConfig
    ): Int? {
        if (distanceToDisplayedStopMeters == null || distanceToDisplayedStopMeters < 0.0) return null
        if (gpsAgeSeconds < 0L || gpsAgeSeconds > config.maxAdaptiveProjectionAgeSeconds) return null

        val speedMps = speedKmH / 3.6
        if (displayedStopId == predictionTargetStopId &&
            rawEtaSeconds != null &&
            rawEtaSeconds > 0 &&
            gpsAgeSeconds <= config.delayedThresholdSeconds
        ) {
            val savedSec = if (speedMps >= 1.0 && projectedDistanceMeters > 0.0) {
                (projectedDistanceMeters / speedMps).roundToInt()
            } else {
                0
            }
            return maxOf(0, rawEtaSeconds - savedSec)
        }

        if (distanceToDisplayedStopMeters <= 15.0) return 0
        if (speedKmH < 3.0) return null
        return (distanceToDisplayedStopMeters / speedMps).roundToInt().coerceAtMost(7200)
    }

    private data class OrthogonalProjectionMatch(
        val cumulativeDist: Double,
        val crossTrackErrorMeters: Double
    )

    /**
     * Finds the orthogonal projection of [point] onto polyline segments starting at [searchStartDist].
     * Returns the cumulative distance along the polyline of the projected point.
     */
    fun projectPointOrthogonal(
        polylinePoints: List<LatLngPoint>,
        cumDist: DoubleArray,
        point: LatLngPoint,
        searchStartDist: Double = 0.0
    ): Double = projectPointOrthogonalDetailed(polylinePoints, cumDist, point, searchStartDist).cumulativeDist

    private fun projectPointOrthogonalDetailed(
        polylinePoints: List<LatLngPoint>,
        cumDist: DoubleArray,
        point: LatLngPoint,
        searchStartDist: Double = 0.0
    ): OrthogonalProjectionMatch {
        var bestCumulativeDist = searchStartDist
        var minCrossTrackDist = Double.MAX_VALUE

        // Find starting segment index matching searchStartDist
        var startIndex = 0
        while (startIndex < cumDist.size - 2 && cumDist[startIndex + 1] < searchStartDist) {
            startIndex++
        }

        val cosRef = cos(Math.toRadians(point.lat))

        for (i in startIndex until polylinePoints.size - 1) {
            val p1 = polylinePoints[i]
            val p2 = polylinePoints[i + 1]

            // Convert to local Cartesian coordinates (meters)
            val dx = (p2.lng - p1.lng) * 111320.0 * cosRef
            val dy = (p2.lat - p1.lat) * 111320.0
            val segLenSq = dx * dx + dy * dy

            val minT = if (cumDist[i] < searchStartDist && cumDist[i + 1] > cumDist[i]) {
                ((searchStartDist - cumDist[i]) / (cumDist[i + 1] - cumDist[i])).coerceIn(0.0, 1.0)
            } else {
                0.0
            }

            val t = if (segLenSq > 0.0001) {
                val px = (point.lng - p1.lng) * 111320.0 * cosRef
                val py = (point.lat - p1.lat) * 111320.0
                ((px * dx + py * dy) / segLenSq).coerceIn(minT, 1.0)
            } else {
                minT
            }

            val projLat = p1.lat + t * (p2.lat - p1.lat)
            val projLng = p1.lng + t * (p2.lng - p1.lng)
            val distToProj = computeDistanceMeters(point, LatLngPoint(projLat, projLng))

            if (distToProj < minCrossTrackDist) {
                minCrossTrackDist = distToProj
                val segDist = cumDist[i + 1] - cumDist[i]
                bestCumulativeDist = cumDist[i] + t * segDist
            }
        }

        return OrthogonalProjectionMatch(bestCumulativeDist, minCrossTrackDist)
    }

    /**
     * Interpolates geographic point and lookahead bearing at [targetDistanceMeters] along the polyline.
     */
    private fun interpolatePointAtDistance(
        polylinePoints: List<LatLngPoint>,
        cumDist: DoubleArray,
        targetDistanceMeters: Double
    ): IntermediatePosition {
        val totalLen = cumDist.last()
        val clamped = targetDistanceMeters.coerceIn(0.0, totalLen)
        val pt = samplePointAtDistance(polylinePoints, cumDist, clamped)
        val (bearing, _) = computeLookaheadTangent(polylinePoints, cumDist, clamped)
        return IntermediatePosition(pt.lat, pt.lng, bearing, clamped)
    }

    private fun samplePointAtDistance(
        polylinePoints: List<LatLngPoint>,
        cumDist: DoubleArray,
        targetDistanceMeters: Double
    ): LatLngPoint {
        if (targetDistanceMeters <= 0.0) return polylinePoints.first()
        val totalLen = cumDist.last()
        if (targetDistanceMeters >= totalLen) return polylinePoints.last()

        var idx = 0
        while (idx < cumDist.size - 2 && cumDist[idx + 1] < targetDistanceMeters) {
            idx++
        }
        val p1 = polylinePoints[idx]
        val p2 = polylinePoints[idx + 1]
        val segLen = cumDist[idx + 1] - cumDist[idx]
        val ratio = if (segLen > 0.0001) (targetDistanceMeters - cumDist[idx]) / segLen else 0.0
        return LatLngPoint(
            lat = p1.lat + ratio * (p2.lat - p1.lat),
            lng = p1.lng + ratio * (p2.lng - p1.lng)
        )
    }

    /**
     * Computes stable forward polyline tangent over a [-12m, +20m] span to avoid micro-segment jitter.
     */
    fun computeLookaheadTangent(
        polylinePoints: List<LatLngPoint>,
        cumDist: DoubleArray,
        distanceMeters: Double
    ): Pair<Float, Boolean> {
        if (polylinePoints.size < 2 || cumDist.last() < 1.0) return 0f to false
        val totalLen = cumDist.last()
        val behindDist = (distanceMeters - LOOKAHEAD_BEHIND_METERS).coerceAtLeast(0.0)
        val aheadDist = (distanceMeters + LOOKAHEAD_AHEAD_METERS).coerceAtMost(totalLen)
        val pBehind = samplePointAtDistance(polylinePoints, cumDist, behindDist)
        val pAhead = samplePointAtDistance(polylinePoints, cumDist, aheadDist)

        if (computeDistanceMeters(pBehind, pAhead) >= 2.0) {
            return computeBearing(pBehind, pAhead) to true
        }
        val firstDistinct = polylinePoints.first()
        val lastDistinct = polylinePoints.last()
        return if (computeDistanceMeters(firstDistinct, lastDistinct) >= 1.0) {
            computeBearing(firstDistinct, lastDistinct) to true
        } else {
            0f to false
        }
    }

    /**
     * Smoothly interpolates azimuth across the 0/360 degree boundary along shortest angular arc.
     */
    fun smoothBearingDegrees(previousDeg: Float, targetDeg: Float, alpha: Float = 0.45f): Float {
        val prevNorm = ((previousDeg % 360f) + 360f) % 360f
        val targetNorm = ((targetDeg % 360f) + 360f) % 360f
        val delta = ((targetNorm - prevNorm + 540f) % 360f) - 180f
        return ((prevNorm + alpha * delta) % 360f + 360f) % 360f
    }

    /**
     * Resolves vehicle bearing prioritizing valid consecutive motion vectors,
     * falling back cleanly to polyline lookahead tangent, preserving heading when stopped.
     */
    fun resolveSmoothedBearing(
        currentLat: Double,
        currentLng: Double,
        currentCumDist: Double,
        speedKmH: Double,
        lookaheadBearing: Float,
        hasValidLookahead: Boolean,
        isGpsFrozen: Boolean,
        previousPosition: InterpolatedVehiclePosition?,
        minMovingSpeed: Double = PositionCompensationEngine.MIN_MOVING_SPEED_KMH
    ): Pair<Float, Boolean> {
        // Stationary vehicles hold last reliable heading (or route tangent) without random spinning
        if (speedKmH <= minMovingSpeed || isGpsFrozen) {
            return when {
                previousPosition != null && previousPosition.hasValidHeading -> previousPosition.bearing to true
                hasValidLookahead -> lookaheadBearing to true
                else -> 0f to false
            }
        }

        var chosenTarget = lookaheadBearing
        var validTarget = hasValidLookahead

        if (previousPosition != null) {
            val prevPt = LatLngPoint(previousPosition.lat, previousPosition.lng)
            val currPt = LatLngPoint(currentLat, currentLng)
            val movedMeters = computeDistanceMeters(prevPt, currPt)
            val deltaCum = currentCumDist - previousPosition.cumulativeDistanceMeters

            if (movedMeters >= MIN_MOTION_BEARING_TRAVEL_METERS && deltaCum in -5.0..1500.0) {
                val motionBearing = computeBearing(prevPt, currPt)
                val angleDiff = if (hasValidLookahead) {
                    abs(((motionBearing - lookaheadBearing + 540f) % 360f) - 180f)
                } else {
                    0f
                }
                if (!hasValidLookahead || angleDiff <= 75f) {
                    chosenTarget = if (hasValidLookahead) {
                        smoothBearingDegrees(lookaheadBearing, motionBearing, alpha = 0.6f)
                    } else {
                        motionBearing
                    }
                    validTarget = true
                }
            }
        }

        if (!validTarget) {
            return if (previousPosition != null && previousPosition.hasValidHeading) {
                previousPosition.bearing to true
            } else {
                0f to false
            }
        }

        val finalHeading = if (previousPosition != null && previousPosition.hasValidHeading) {
            smoothBearingDegrees(previousPosition.bearing, chosenTarget, alpha = 0.55f)
        } else {
            chosenTarget
        }
        return finalHeading to true
    }

    /**
     * Applies velocity-bounded exponential smoothing between previous estimated position and new target.
     */
    private fun applyVelocityBoundedSmoothing(
        previous: InterpolatedVehiclePosition,
        current: IntermediatePosition,
        speedKmH: Double,
        currentTimeMillis: Long,
        maxAllowedCumulativeDist: Double
    ): IntermediatePosition {
        val dtSeconds = ((currentTimeMillis - previous.updatedAtMillis) / 1000.0).coerceAtLeast(0.0)
        val distanceJump = computeDistanceMeters(
            LatLngPoint(previous.lat, previous.lng),
            LatLngPoint(current.lat, current.lng)
        )

        // Physical maximum distance vehicle could travel in dtSeconds (speed + 40% margin, min 25m)
        val speedMps = maxOf(10.0, speedKmH * (1000.0 / 3600.0))
        val maxPhysicalTravel = maxOf(25.0, speedMps * dtSeconds * 1.4)

        // If jump is physically plausible and within 5s, smooth position
        return if (dtSeconds in 0.1..5.0 && distanceJump <= maxPhysicalTravel) {
            val alpha = 0.65 // Blend weight towards new measurement
            val smoothedLat = previous.lat + alpha * (current.lat - previous.lat)
            val smoothedLng = previous.lng + alpha * (current.lng - previous.lng)
            val smoothedCumDist = (previous.cumulativeDistanceMeters + alpha * (current.cumulativeDistanceMeters - previous.cumulativeDistanceMeters))
                .coerceIn(0.0, maxAllowedCumulativeDist)

            IntermediatePosition(smoothedLat, smoothedLng, current.bearing, smoothedCumDist)
        } else {
            // Fresh update or step jump: snap directly to verified position
            current
        }
    }

    private data class IntermediatePosition(
        val lat: Double,
        val lng: Double,
        val bearing: Float,
        val cumulativeDistanceMeters: Double
    )

    /**
     * Calculates Haversine distance in meters between two lat/lng coordinates.
     */
    fun computeDistanceMeters(p1: LatLngPoint, p2: LatLngPoint): Double {
        val lat1Rad = Math.toRadians(p1.lat)
        val lat2Rad = Math.toRadians(p2.lat)
        val deltaLat = Math.toRadians(p2.lat - p1.lat)
        val deltaLng = Math.toRadians(p2.lng - p1.lng)

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLng / 2) * sin(deltaLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates the forward azimuth / bearing in degrees (0..360) from p1 to p2.
     */
    fun computeBearing(p1: LatLngPoint, p2: LatLngPoint): Float {
        val lat1 = Math.toRadians(p1.lat)
        val lat2 = Math.toRadians(p2.lat)
        val deltaLng = Math.toRadians(p2.lng - p1.lng)

        val y = sin(deltaLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng)

        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)
        return ((bearingDeg + 360.0) % 360.0).toFloat()
    }
}
