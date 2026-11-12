package com.hcmcbustracker.feature.map.render

import com.hcmcbustracker.core.model.GpsFreshnessState
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel
import com.hcmcbustracker.feature.map.TrackedVehicleState
import kotlin.math.abs

/**
 * Circular angular interpolation and normalization helper (0..360 deg).
 * Handles 359° -> 1° and 1° -> 359° transitions cleanly across the wrap boundary
 * without generating artificial heading for stationary or unoriented vehicles.
 */
object BearingSmoother {

    fun normalizeBearing(deg: Float): Float {
        if (!deg.isFinite()) return 0f
        val mod = deg % 360f
        return if (mod < 0f) mod + 360f else mod
    }

    /**
     * Signed shortest angular delta in [-180, +180) from [fromDeg] to [toDeg].
     */
    fun shortestSignedDeltaDegrees(fromDeg: Float, toDeg: Float): Float {
        val f = normalizeBearing(fromDeg)
        val t = normalizeBearing(toDeg)
        return ((t - f + 540f) % 360f) - 180f
    }

    fun interpolateBearingDegrees(fromDeg: Float, toDeg: Float, fraction: Float): Float {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        val delta = shortestSignedDeltaDegrees(fromDeg, toDeg)
        return normalizeBearing(fromDeg + delta * clampedFraction)
    }

    /**
     * Determines whether display rotation should smoothly interpolate rather than snap/hold.
     */
    fun shouldAnimateRotation(
        previousHasHeading: Boolean,
        nextHasHeading: Boolean,
        isStationary: Boolean,
        fromDeg: Float,
        toDeg: Float
    ): Boolean {
        if (!previousHasHeading || !nextHasHeading || isStationary) return false
        return abs(shortestSignedDeltaDegrees(fromDeg, toDeg)) >= 2.0f
    }
}

/**
 * Immutable snapshot of one rendered vehicle used by [VehicleRenderDiffer] to avoid
 * redundant GeoJSON updates while ensuring telemetry status/freshness changes never get dropped.
 */
data class VehicleRenderSnapshot(
    val normalizedPlate: String,
    val formattedPlate: String,
    val lat: Double,
    val lng: Double,
    val bearing: Float,
    val hasValidHeading: Boolean,
    val freshnessState: GpsFreshnessState,
    val latencyCategory: GpsLatencyCategory,
    val arrivalStatus: VehicleArrivalStatus,
    val positionSource: PositionSource,
    val isSelected: Boolean,
    val coachIconId: String,
    val shortLabelIconId: String,
    val fullLabelIconId: String
)

data class VehicleDiffResult(
    val addedPlates: Set<String>,
    val removedPlates: Set<String>,
    val coordinateChangedPlates: Set<String>,
    val bearingChangedPlates: Set<String>,
    val freshnessChangedPlates: Set<String>,
    val statusChangedPlates: Set<String>,
    val provenanceChangedPlates: Set<String>,
    val selectionChangedPlates: Set<String>,
    val labelChangedPlates: Set<String>,
    val snapshotsByPlate: Map<String, VehicleRenderSnapshot>
) {
    val hasAnyChange: Boolean
        get() = addedPlates.isNotEmpty() ||
            removedPlates.isNotEmpty() ||
            coordinateChangedPlates.isNotEmpty() ||
            bearingChangedPlates.isNotEmpty() ||
            freshnessChangedPlates.isNotEmpty() ||
            statusChangedPlates.isNotEmpty() ||
            provenanceChangedPlates.isNotEmpty() ||
            selectionChangedPlates.isNotEmpty() ||
            labelChangedPlates.isNotEmpty()
}

/**
 * Incremental differ comparing previous rendered vehicle snapshots against incoming route fleet state.
 */
object VehicleRenderDiffer {

    private const val COORD_EPSILON = 1e-6 // ~0.11 m
    private const val BEARING_EPSILON_DEG = 0.5f

    fun buildSnapshots(
        vehicles: List<RouteActiveVehicleUiModel>,
        selectedPlate: String?,
        fallbackTracked: TrackedVehicleState? = null,
        bearingOverrides: Map<String, Float> = emptyMap()
    ): Map<String, VehicleRenderSnapshot> {
        val map = LinkedHashMap<String, VehicleRenderSnapshot>(vehicles.size + 1)

        for (item in vehicles) {
            val t = item.telemetry
            val p = item.position
            if (!isValidCoord(p.lat, p.lng)) continue
            val normKey = normalizeKey(t.plateNumber)
            val isSel = selectedPlate != null && (
                t.plateNumber.equals(selectedPlate, ignoreCase = true) ||
                    t.formattedPlate.equals(selectedPlate, ignoreCase = true)
                )
            val freshTier = VehicleMarkerBitmapFactory.mapFreshTier(t.freshnessState, p.latencyCategory, p.positionSource)
            val statusBadge = VehicleMarkerBitmapFactory.mapStatusBadge(p.arrivalStatus, freshTier, p.hasValidHeading)
            val isPred = p.positionSource != PositionSource.HELD_AT_LAST_VALID_GPS
            val coachSpec = CoachIconSpec(isSel, freshTier, statusBadge, p.hasValidHeading, isPred)
            val isStale = freshTier == CoachFreshTier.STALE_OR_HELD
            val shortTxt = VehicleMarkerBitmapFactory.formatShortPlate(t.formattedPlate)
            val shortSpec = LabelIconSpec(shortTxt, LabelZoomTier.SHORT, isSel, isStale)
            val fullSpec = LabelIconSpec(t.formattedPlate, LabelZoomTier.FULL, isSel, isStale)
            val displayBearing = if (p.hasValidHeading) {
                BearingSmoother.normalizeBearing(bearingOverrides[normKey] ?: p.bearing)
            } else {
                0f
            }

            map[normKey] = VehicleRenderSnapshot(
                normalizedPlate = normKey,
                formattedPlate = t.formattedPlate,
                lat = p.lat,
                lng = p.lng,
                bearing = displayBearing,
                hasValidHeading = p.hasValidHeading,
                freshnessState = t.freshnessState,
                latencyCategory = p.latencyCategory,
                arrivalStatus = p.arrivalStatus,
                positionSource = p.positionSource,
                isSelected = isSel,
                coachIconId = coachSpec.toIconId(),
                shortLabelIconId = shortSpec.toIconId(),
                fullLabelIconId = fullSpec.toIconId()
            )
        }

        if (fallbackTracked != null && isValidCoord(fallbackTracked.lat, fallbackTracked.lng)) {
            val normFb = normalizeKey(fallbackTracked.plateNumber)
            if (!map.containsKey(normFb)) {
                val freshTier = VehicleMarkerBitmapFactory.mapFreshTier(
                    if (fallbackTracked.latencyCategory == GpsLatencyCategory.STALE) GpsFreshnessState.STALE else GpsFreshnessState.FRESH,
                    fallbackTracked.latencyCategory,
                    fallbackTracked.positionSource
                )
                val statusBadge = VehicleMarkerBitmapFactory.mapStatusBadge(
                    fallbackTracked.arrivalStatus,
                    freshTier,
                    fallbackTracked.hasValidHeading
                )
                val isPred = fallbackTracked.positionSource != PositionSource.HELD_AT_LAST_VALID_GPS
                val coachSpec = CoachIconSpec(true, freshTier, statusBadge, fallbackTracked.hasValidHeading, isPred)
                val isStale = freshTier == CoachFreshTier.STALE_OR_HELD
                val shortTxt = VehicleMarkerBitmapFactory.formatShortPlate(fallbackTracked.plateNumber)
                val shortSpec = LabelIconSpec(shortTxt, LabelZoomTier.SHORT, true, isStale)
                val fullSpec = LabelIconSpec(fallbackTracked.plateNumber, LabelZoomTier.FULL, true, isStale)
                val displayBearing = if (fallbackTracked.hasValidHeading) {
                    BearingSmoother.normalizeBearing(bearingOverrides[normFb] ?: fallbackTracked.bearing)
                } else {
                    0f
                }

                map[normFb] = VehicleRenderSnapshot(
                    normalizedPlate = normFb,
                    formattedPlate = fallbackTracked.plateNumber,
                    lat = fallbackTracked.lat,
                    lng = fallbackTracked.lng,
                    bearing = displayBearing,
                    hasValidHeading = fallbackTracked.hasValidHeading,
                    freshnessState = if (isStale) GpsFreshnessState.STALE else GpsFreshnessState.FRESH,
                    latencyCategory = fallbackTracked.latencyCategory,
                    arrivalStatus = fallbackTracked.arrivalStatus,
                    positionSource = fallbackTracked.positionSource,
                    isSelected = true,
                    coachIconId = coachSpec.toIconId(),
                    shortLabelIconId = shortSpec.toIconId(),
                    fullLabelIconId = fullSpec.toIconId()
                )
            }
        }

        return map
    }

    fun diff(
        previous: Map<String, VehicleRenderSnapshot>,
        current: Map<String, VehicleRenderSnapshot>
    ): VehicleDiffResult {
        val added = HashSet<String>()
        val removed = HashSet<String>()
        val coordChanged = HashSet<String>()
        val bearingChanged = HashSet<String>()
        val freshnessChanged = HashSet<String>()
        val statusChanged = HashSet<String>()
        val provenanceChanged = HashSet<String>()
        val selectionChanged = HashSet<String>()
        val labelChanged = HashSet<String>()

        for (prevKey in previous.keys) {
            if (!current.containsKey(prevKey)) {
                removed.add(prevKey)
            }
        }

        for ((key, cur) in current) {
            val prev = previous[key]
            if (prev == null) {
                added.add(key)
                continue
            }
            if (abs(cur.lat - prev.lat) > COORD_EPSILON || abs(cur.lng - prev.lng) > COORD_EPSILON) {
                coordChanged.add(key)
            }
            if (cur.hasValidHeading != prev.hasValidHeading ||
                abs(BearingSmoother.shortestSignedDeltaDegrees(prev.bearing, cur.bearing)) >= BEARING_EPSILON_DEG
            ) {
                bearingChanged.add(key)
            }
            if (cur.freshnessState != prev.freshnessState || cur.latencyCategory != prev.latencyCategory) {
                freshnessChanged.add(key)
            }
            if (cur.arrivalStatus != prev.arrivalStatus) {
                statusChanged.add(key)
            }
            if (cur.positionSource != prev.positionSource) {
                provenanceChanged.add(key)
            }
            if (cur.isSelected != prev.isSelected) {
                selectionChanged.add(key)
            }
            if (cur.fullLabelIconId != prev.fullLabelIconId || cur.shortLabelIconId != prev.shortLabelIconId) {
                labelChanged.add(key)
            }
        }

        return VehicleDiffResult(
            addedPlates = added,
            removedPlates = removed,
            coordinateChangedPlates = coordChanged,
            bearingChangedPlates = bearingChanged,
            freshnessChangedPlates = freshnessChanged,
            statusChangedPlates = statusChanged,
            provenanceChangedPlates = provenanceChanged,
            selectionChangedPlates = selectionChanged,
            labelChangedPlates = labelChanged,
            snapshotsByPlate = current
        )
    }

    private fun isValidCoord(lat: Double, lng: Double): Boolean {
        return lat.isFinite() && lng.isFinite() && !(lat == 0.0 && lng == 0.0) &&
            lat in 8.0..13.0 && lng in 104.0..109.0
    }

    private fun normalizeKey(raw: String): String {
        return raw.trim().uppercase().replace("-", "").replace(".", "").replace(" ", "")
    }
}
