package com.hcmcbustracker.feature.map.render

import android.graphics.Bitmap
import android.util.DisplayMetrics
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.LatLngPoint
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.core.model.RouteVehicleTelemetry
import com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel
import com.hcmcbustracker.feature.map.engine.InterpolatedVehiclePosition
import com.hcmcbustracker.feature.map.util.RoutePathGeoJsonConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.style.layers.Layer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapLayerManagerTest {

    private class RecordingStyleOps : StyleLayerOperations {
        val layerIds = mutableListOf<String>()
        val sourceIds = mutableSetOf<String>()
        val clusteredSources = mutableMapOf<String, Pair<Int, Int>>()
        val imageIds = mutableSetOf<String>()
        var duplicateLayerAddAttempts = 0

        override fun hasLayer(layerId: String): Boolean = layerId in layerIds
        override fun hasSource(sourceId: String): Boolean = sourceId in sourceIds

        override fun addGeoJsonSource(
            sourceId: String,
            featureCollection: FeatureCollection,
            clusterMaxZoom: Int?,
            clusterRadius: Int?
        ) {
            sourceIds.add(sourceId)
            if (clusterMaxZoom != null && clusterRadius != null) {
                clusteredSources[sourceId] = clusterMaxZoom to clusterRadius
            }
        }

        override fun addGeoJsonFeatureSource(sourceId: String, feature: Feature) {
            sourceIds.add(sourceId)
        }

        override fun updateSourceGeoJson(sourceId: String, featureCollection: FeatureCollection) = Unit
        override fun updateSourceFeature(sourceId: String, feature: Feature) = Unit

        override fun addAnchoredLayer(layerId: String, anchor: LayerAnchor, createLayer: () -> Layer) {
            if (layerId in layerIds) {
                duplicateLayerAddAttempts++
                return
            }
            when (anchor) {
                is LayerAnchor.Below -> {
                    val idx = layerIds.indexOf(anchor.anchorLayerId)
                    if (idx >= 0) layerIds.add(idx, layerId) else layerIds.add(0, layerId)
                }
                is LayerAnchor.Above -> {
                    val idx = layerIds.indexOf(anchor.anchorLayerId)
                    if (idx >= 0) layerIds.add(idx + 1, layerId) else layerIds.add(layerId)
                }
                LayerAnchor.Top -> layerIds.add(layerId)
            }
        }

        override fun removeLayer(layerId: String) {
            layerIds.remove(layerId)
        }

        override fun removeSource(sourceId: String) {
            sourceIds.remove(sourceId)
            clusteredSources.remove(sourceId)
        }

        override fun hasImage(imageId: String): Boolean = imageId in imageIds
        override fun addImages(images: HashMap<String, Bitmap>) {
            imageIds.addAll(images.keys)
        }

        fun simulateStyleReload() {
            layerIds.clear()
            sourceIds.clear()
            clusteredSources.clear()
            imageIds.clear()
        }
    }

    @Test
    fun `canonical z-order is preserved regardless of insertion order and across updates or style reloads`() {
        val ops = RecordingStyleOps()
        val factory = VehicleMarkerBitmapFactory(DisplayMetrics.DENSITY_XHIGH)

        val routeFeature = RoutePathGeoJsonConverter.toLineStringFeature(
            RoutePath(
                routeId = 1,
                varId = 101,
                points = listOf(LatLngPoint(10.76, 106.68), LatLngPoint(10.77, 106.70))
            )
        )
        val stopsFc = RoutePathGeoJsonConverter.toStopFeatureCollection(
            listOf(
                BusStop(
                    id = 1,
                    code = "S1",
                    name = "Bến Thành",
                    stopType = "Bến",
                    zone = "Q1",
                    addressNo = "1",
                    street = "Hàm Nghi",
                    supportDisability = true,
                    status = "Active",
                    lat = 10.77,
                    lng = 106.698,
                    searchKeywords = "ben thanh",
                    routes = listOf("01")
                )
            ),
            selectedStopId = 1
        )
        val userFeature = Feature.fromGeometry(Point.fromLngLat(106.69, 10.77)).apply {
            addBooleanProperty("has_accuracy", true)
            addNumberProperty("accuracy", 12.0f)
        }
        val vehicles = listOf(makeSampleVehicle("50E61577", "50E-615.77", 10.771, 106.701))
        val snapshots = VehicleRenderDiffer.buildSnapshots(vehicles, selectedPlate = "50E61577")
        val vehiclesFc = RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(vehicles, selectedPlate = "50E61577")

        // Worst-case reverse insertion order: User Dot -> Vehicles -> Stops -> Route Polyline
        MapLayerManager.applyUserLocation(ops, userFeature, factory)
        MapLayerManager.applyVehicles(ops, vehiclesFc, snapshots.values, factory)
        MapLayerManager.applyStops(ops, stopsFc, hasStops = true, showStops = true, bitmapFactory = factory)
        MapLayerManager.applyRoutePolyline(ops, routeFeature, isOutbound = true)

        assertEquals(MapLayerManager.CANONICAL_LAYER_ORDER, ops.layerIds)
        assertTrue(MapLayerManager.isCanonicalOrderPreserved(ops.layerIds))
        assertEquals(0, ops.duplicateLayerAddAttempts)
        assertEquals(13 to 42, ops.clusteredSources[MapLayerManager.STOPS_SOURCE_ID])
        assertTrue(ops.imageIds.contains(MapLayerManager.USER_BLUE_DOT_ICON_ID))
        assertTrue(ops.imageIds.containsAll(snapshots.values.map { it.coachIconId }))
        assertTrue(ops.imageIds.containsAll(snapshots.values.map { it.shortLabelIconId }))
        assertTrue(ops.imageIds.containsAll(snapshots.values.map { it.fullLabelIconId }))

        // Repeat update & direction toggle remains idempotent with zero duplicates
        MapLayerManager.applyRoutePolyline(ops, routeFeature, isOutbound = false)
        MapLayerManager.applyVehicles(ops, vehiclesFc, snapshots.values, factory)
        assertEquals(MapLayerManager.CANONICAL_LAYER_ORDER, ops.layerIds)
        assertTrue(MapLayerManager.isCanonicalOrderPreserved(ops.layerIds))
        assertEquals(0, ops.duplicateLayerAddAttempts)

        // Clear route polyline and stops preserves remaining vehicle & blue dot layers cleanly
        MapLayerManager.applyRoutePolyline(ops, null, isOutbound = false)
        MapLayerManager.applyStops(ops, FeatureCollection.fromFeatures(emptyArray()), hasStops = false, showStops = false, bitmapFactory = factory)
        assertTrue(MapLayerManager.isCanonicalOrderPreserved(ops.layerIds))
        assertFalse(ops.hasLayer(MapLayerManager.ROUTE_LAYER_ID))
        assertFalse(ops.hasLayer(MapLayerManager.STOP_CLUSTER_LAYER_ID))
        assertTrue(ops.hasLayer(MapLayerManager.VEHICLE_NORMAL_LAYER_ID))
        assertTrue(ops.hasLayer(MapLayerManager.USER_LOCATION_LAYER_ID))

        // Style reload clears & rebuilds cleanly in canonical order
        ops.simulateStyleReload()
        MapLayerManager.applyRoutePolyline(ops, routeFeature, isOutbound = true)
        MapLayerManager.applyStops(ops, stopsFc, hasStops = true, showStops = true, bitmapFactory = factory)
        MapLayerManager.applyVehicles(ops, vehiclesFc, snapshots.values, factory)
        MapLayerManager.applyUserLocation(ops, userFeature, factory)

        assertEquals(MapLayerManager.CANONICAL_LAYER_ORDER, ops.layerIds)
        assertTrue(MapLayerManager.isCanonicalOrderPreserved(ops.layerIds))
    }

    @Test
    fun `label iconImage expression keeps zoom at top-level step operator for gl-native validity`() {
        val array = MapLayerManager.buildLabelIconImageExpression().toArray()
        assertEquals("step", array[0])
        val zoomArg = array[1] as Array<*>
        assertEquals("zoom", zoomArg[0])
    }

    private fun makeSampleVehicle(
        plate: String,
        formatted: String,
        lat: Double,
        lng: Double
    ): RouteActiveVehicleUiModel {
        val now = 1_700_000_000_000L
        return RouteActiveVehicleUiModel(
            telemetry = RouteVehicleTelemetry(
                routeId = 1,
                varId = 101,
                plateNumber = plate,
                formattedPlate = formatted,
                checkpointStopId = 1,
                checkpointStopName = "Bến Thành",
                rawDistanceMeters = 100.0,
                speedKmH = 20.0,
                etaSeconds = 30,
                gpsTimestampIso = "2026-11-12T12:00:00+07:00",
                gpsTimestampMillis = now,
                requestStartedAtMillis = now,
                responseReceivedAtMillis = now,
                predictionReceivedAtMillis = now,
                statusCode = 0
            ),
            position = InterpolatedVehiclePosition(
                lat = lat,
                lng = lng,
                bearing = 45f,
                plateNumber = plate,
                speedKmH = 20.0
            )
        )
    }
}
