package com.hcmcbustracker.feature.map.util

import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.RoutePath
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

object RoutePathGeoJsonConverter {

    /**
     * Converts an official EBMS RoutePath coordinate sequence into a GeoJSON LineString Feature.
     * Strictly preserves exact point coordinates without creating fake or interpolated vertices.
     */
    fun toLineStringFeature(routePath: RoutePath): Feature? {
        val points = routePath.points
        if (points.size < 2) return null

        val geoPoints = ArrayList<Point>(points.size)
        for (pt in points) {
            // Standard validation for Southern Vietnam / HCMC & Con Dao geographic region
            if (pt.lat in 8.0..13.0 && pt.lng in 104.0..109.0) {
                geoPoints.add(Point.fromLngLat(pt.lng, pt.lat))
            }
        }
        if (geoPoints.size < 2) return null

        val lineString = LineString.fromLngLats(geoPoints)
        return Feature.fromGeometry(lineString)
    }

    /**
     * Calculates the bounding box enclosing the entire route polyline.
     */
    fun calculateBounds(routePath: RoutePath): LatLngBounds? {
        val points = routePath.points
        if (points.isEmpty()) return null

        val builder = LatLngBounds.Builder()
        var validCount = 0
        for (pt in points) {
            if (pt.lat in 8.0..13.0 && pt.lng in 104.0..109.0) {
                builder.include(LatLng(pt.lat, pt.lng))
                validCount++
            }
        }
        return if (validCount > 0) builder.build() else null
    }

    /**
     * Converts stops along a route into a GeoJSON FeatureCollection with terminal indicators.
     */
    fun toStopFeatureCollection(
        stops: List<BusStop>,
        selectedStopId: Int? = null
    ): FeatureCollection {
        val features = stops.mapIndexedNotNull { index, stop ->
            if (stop.lat in 8.0..13.0 && stop.lng in 104.0..109.0) {
                val point = Point.fromLngLat(stop.lng, stop.lat)
                val feature = Feature.fromGeometry(point)
                feature.addNumberProperty("stop_id", stop.id)
                feature.addStringProperty("name", stop.name)
                feature.addStringProperty("code", stop.code)
                feature.addNumberProperty("order", index + 1)
                feature.addBooleanProperty("is_start", index == 0)
                feature.addBooleanProperty("is_end", index == stops.lastIndex)
                feature.addBooleanProperty("is_selected", stop.id == selectedStopId)
                feature
            } else null
        }
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Converts user device GPS coordinate into a GeoJSON Point Feature with bearing and accuracy.
     */
    fun toUserLocationFeature(
        lat: Double,
        lng: Double,
        bearing: Float? = null,
        accuracyMeters: Float? = null
    ): Feature {
        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
        val hasBearing = bearing != null && bearing >= 0f
        val hasAccuracy = accuracyMeters != null && accuracyMeters > 0f
        feature.addBooleanProperty("has_bearing", hasBearing)
        feature.addNumberProperty("bearing", (bearing ?: 0f).toDouble())
        feature.addBooleanProperty("has_accuracy", hasAccuracy)
        feature.addNumberProperty("accuracy", (accuracyMeters ?: 0f).coerceIn(0f, 300f).toDouble())
        return feature
    }

    /**
     * Converts estimated vehicle position into a GeoJSON Point Feature with plate and bearing metadata.
     */
    fun toVehicleFeature(
        lat: Double,
        lng: Double,
        plate: String,
        bearing: Float,
        speedKmH: Double,
        isStationary: Boolean = false,
        statusLabel: String = "",
        isSelected: Boolean = false,
        isStale: Boolean = false,
        hasHeading: Boolean = true
    ): Feature {
        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
        feature.addStringProperty("plate", plate)
        feature.addBooleanProperty("has_heading", hasHeading)
        feature.addNumberProperty("bearing", if (hasHeading) bearing.toDouble() else 0.0)
        feature.addNumberProperty("speed", speedKmH)
        feature.addBooleanProperty("is_stationary", isStationary)
        feature.addStringProperty("status_label", statusLabel)
        feature.addBooleanProperty("is_selected", isSelected)
        feature.addBooleanProperty("is_stale", isStale)
        return feature
    }

    fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return !lat.isNaN() && !lng.isNaN() &&
            !(lat == 0.0 && lng == 0.0) &&
            lat in 8.0..13.0 && lng in 104.0..109.0
    }

    /**
     * Converts all active vehicles along a route variant into a single FeatureCollection for MapLibre rendering.
     */
    fun toRouteVehiclesFeatureCollection(
        vehicles: List<com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel>,
        selectedPlate: String?,
        fallbackTracked: com.hcmcbustracker.feature.map.TrackedVehicleState? = null,
        bearingOverrides: Map<String, Float> = emptyMap()
    ): FeatureCollection {
        val features = ArrayList<Feature>(vehicles.size + 1)
        val seenPlates = HashSet<String>()

        for (item in vehicles) {
            val t = item.telemetry
            val pos = item.position
            if (!isValidCoordinate(pos.lat, pos.lng)) continue
            val normPlate = t.plateNumber.trim().uppercase()
            val canonicalPlateKey = normPlate.replace("-", "").replace(".", "").replace(" ", "")
            seenPlates.add(canonicalPlateKey)
            val isSel = selectedPlate != null && (
                t.plateNumber.equals(selectedPlate, ignoreCase = true) ||
                    t.formattedPlate.equals(selectedPlate, ignoreCase = true)
                )
            val freshTier = com.hcmcbustracker.feature.map.render.VehicleMarkerBitmapFactory.mapFreshTier(
                t.freshnessState,
                pos.latencyCategory,
                pos.positionSource
            )
            val statusBadge = com.hcmcbustracker.feature.map.render.VehicleMarkerBitmapFactory.mapStatusBadge(
                pos.arrivalStatus,
                freshTier,
                pos.hasValidHeading
            )
            val isStale = freshTier == com.hcmcbustracker.feature.map.render.CoachFreshTier.STALE_OR_HELD
            val isPred = pos.positionSource != com.hcmcbustracker.core.model.PositionSource.HELD_AT_LAST_VALID_GPS
            val effectiveBearing = if (pos.hasValidHeading) {
                com.hcmcbustracker.feature.map.render.BearingSmoother.normalizeBearing(
                    bearingOverrides[canonicalPlateKey] ?: pos.bearing
                )
            } else {
                0f
            }

            val f = toVehicleFeature(
                lat = pos.lat,
                lng = pos.lng,
                plate = t.formattedPlate,
                bearing = effectiveBearing,
                speedKmH = t.speedKmH,
                isStationary = t.speedKmH <= 1.5,
                statusLabel = pos.compensationStatus.name,
                isSelected = isSel,
                isStale = isStale,
                hasHeading = pos.hasValidHeading
            )
            val coachSpec = com.hcmcbustracker.feature.map.render.CoachIconSpec(
                isSelected = isSel,
                freshTier = freshTier,
                statusBadge = statusBadge,
                hasHeading = pos.hasValidHeading,
                isPredictedOrInterpolated = isPred
            )
            val shortPlate = com.hcmcbustracker.feature.map.render.VehicleMarkerBitmapFactory.formatShortPlate(t.formattedPlate)
            val shortLabelId = com.hcmcbustracker.feature.map.render.LabelIconSpec(
                text = shortPlate,
                tier = com.hcmcbustracker.feature.map.render.LabelZoomTier.SHORT,
                isSelected = isSel,
                isStale = isStale
            ).toIconId()
            val fullLabelId = com.hcmcbustracker.feature.map.render.LabelIconSpec(
                text = t.formattedPlate,
                tier = com.hcmcbustracker.feature.map.render.LabelZoomTier.FULL,
                isSelected = isSel,
                isStale = isStale
            ).toIconId()
            val sortKey = when {
                isSel -> 1000.0
                pos.arrivalStatus == com.hcmcbustracker.core.model.VehicleArrivalStatus.ARRIVING_AT_STOP ||
                    pos.arrivalStatus == com.hcmcbustracker.core.model.VehicleArrivalStatus.AT_STOP -> 200.0
                !isStale -> 100.0
                else -> 20.0
            }

            f.addStringProperty("raw_plate", t.plateNumber)
            f.addStringProperty("arrow_icon_id", buildBusArrowIconId(pos.hasValidHeading, isSel, isStale))
            val labelId = buildBusLabelIconId(t.formattedPlate, isSel, isStale)
            f.addStringProperty("label_icon_id", labelId)
            f.addStringProperty("icon_id", labelId)
            f.addStringProperty("coach_icon_id", coachSpec.toIconId())
            f.addStringProperty("short_plate", shortPlate)
            f.addStringProperty("short_label_icon_id", shortLabelId)
            f.addStringProperty("full_label_icon_id", fullLabelId)
            f.addNumberProperty("sort_key", sortKey)
            f.addStringProperty("fresh_tier", freshTier.name)
            f.addStringProperty("status_badge", statusBadge.name)
            f.addStringProperty("position_source", pos.positionSource.name)
            features.add(f)
        }

        if (fallbackTracked != null && isValidCoordinate(fallbackTracked.lat, fallbackTracked.lng)) {
            val fbNorm = fallbackTracked.plateNumber.trim().uppercase().replace("-", "").replace(".", "").replace(" ", "")
            val alreadyPresent = seenPlates.contains(fbNorm)
            if (!alreadyPresent) {
                val freshTier = com.hcmcbustracker.feature.map.render.VehicleMarkerBitmapFactory.mapFreshTier(
                    if (fallbackTracked.latencyCategory == com.hcmcbustracker.core.model.GpsLatencyCategory.STALE) {
                        com.hcmcbustracker.core.model.GpsFreshnessState.STALE
                    } else {
                        com.hcmcbustracker.core.model.GpsFreshnessState.FRESH
                    },
                    fallbackTracked.latencyCategory,
                    fallbackTracked.positionSource
                )
                val statusBadge = com.hcmcbustracker.feature.map.render.VehicleMarkerBitmapFactory.mapStatusBadge(
                    fallbackTracked.arrivalStatus,
                    freshTier,
                    fallbackTracked.hasValidHeading
                )
                val fbStale = freshTier == com.hcmcbustracker.feature.map.render.CoachFreshTier.STALE_OR_HELD
                val isPred = fallbackTracked.positionSource != com.hcmcbustracker.core.model.PositionSource.HELD_AT_LAST_VALID_GPS
                val effectiveBearing = if (fallbackTracked.hasValidHeading) {
                    com.hcmcbustracker.feature.map.render.BearingSmoother.normalizeBearing(
                        bearingOverrides[fbNorm] ?: fallbackTracked.bearing
                    )
                } else {
                    0f
                }

                val f = toVehicleFeature(
                    lat = fallbackTracked.lat,
                    lng = fallbackTracked.lng,
                    plate = fallbackTracked.plateNumber,
                    bearing = effectiveBearing,
                    speedKmH = fallbackTracked.speedKmH,
                    isStationary = fallbackTracked.speedKmH <= 1.5,
                    statusLabel = fallbackTracked.compensationStatus.name,
                    isSelected = true,
                    isStale = fbStale,
                    hasHeading = fallbackTracked.hasValidHeading
                )
                val coachSpec = com.hcmcbustracker.feature.map.render.CoachIconSpec(
                    isSelected = true,
                    freshTier = freshTier,
                    statusBadge = statusBadge,
                    hasHeading = fallbackTracked.hasValidHeading,
                    isPredictedOrInterpolated = isPred
                )
                val shortPlate = com.hcmcbustracker.feature.map.render.VehicleMarkerBitmapFactory.formatShortPlate(fallbackTracked.plateNumber)
                val shortLabelId = com.hcmcbustracker.feature.map.render.LabelIconSpec(
                    text = shortPlate,
                    tier = com.hcmcbustracker.feature.map.render.LabelZoomTier.SHORT,
                    isSelected = true,
                    isStale = fbStale
                ).toIconId()
                val fullLabelId = com.hcmcbustracker.feature.map.render.LabelIconSpec(
                    text = fallbackTracked.plateNumber,
                    tier = com.hcmcbustracker.feature.map.render.LabelZoomTier.FULL,
                    isSelected = true,
                    isStale = fbStale
                ).toIconId()

                f.addStringProperty("raw_plate", fallbackTracked.plateNumber)
                f.addStringProperty("arrow_icon_id", buildBusArrowIconId(fallbackTracked.hasValidHeading, isSelected = true, isStale = fbStale))
                val labelId = buildBusLabelIconId(fallbackTracked.plateNumber, isSelected = true, isStale = fbStale)
                f.addStringProperty("label_icon_id", labelId)
                f.addStringProperty("icon_id", labelId)
                f.addStringProperty("coach_icon_id", coachSpec.toIconId())
                f.addStringProperty("short_plate", shortPlate)
                f.addStringProperty("short_label_icon_id", shortLabelId)
                f.addStringProperty("full_label_icon_id", fullLabelId)
                f.addNumberProperty("sort_key", 1000.0)
                f.addStringProperty("fresh_tier", freshTier.name)
                f.addStringProperty("status_badge", statusBadge.name)
                f.addStringProperty("position_source", fallbackTracked.positionSource.name)
                features.add(f)
            }
        }

        return FeatureCollection.fromFeatures(features)
    }

    fun buildBusArrowIconId(hasHeading: Boolean, isSelected: Boolean, isStale: Boolean): String {
        val kind = if (hasHeading) "arrow" else "nohead"
        return "hcmc-bus-$kind-$isSelected-$isStale"
    }

    fun buildBusLabelIconId(plate: String, isSelected: Boolean, isStale: Boolean): String {
        return "hcmc-bus-label-${plate}-$isSelected-$isStale"
    }

    fun buildBusIconId(plate: String, isSelected: Boolean, isStale: Boolean): String {
        return buildBusLabelIconId(plate, isSelected, isStale)
    }
}
