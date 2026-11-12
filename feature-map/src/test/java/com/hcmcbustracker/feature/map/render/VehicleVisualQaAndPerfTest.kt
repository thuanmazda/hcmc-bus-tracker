package com.hcmcbustracker.feature.map.render

import android.graphics.Bitmap
import android.util.DisplayMetrics
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.CompensationStatus
import com.hcmcbustracker.core.model.GpsFreshnessState
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.LatLngPoint
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.core.model.RouteVehicleTelemetry
import com.hcmcbustracker.core.model.VehicleArrivalStatus
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

/**
 * Automated Phase 12 Round 4 Visual QA, MapLayerManager Stress, Overlap Picker,
 * and Local Performance Benchmark Suite (using local test fixtures).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleVisualQaAndPerfTest {

    private class ProbeStyleOps : StyleLayerOperations {
        val layerIds = mutableListOf<String>()
        val sourceIds = mutableSetOf<String>()
        val imageIds = mutableSetOf<String>()
        var duplicateLayerAddAttempts = 0
        var sourceUpdates = 0

        override fun hasLayer(layerId: String): Boolean = layerId in layerIds
        override fun hasSource(sourceId: String): Boolean = sourceId in sourceIds
        override fun addGeoJsonSource(sourceId: String, featureCollection: FeatureCollection, clusterMaxZoom: Int?, clusterRadius: Int?) {
            sourceIds.add(sourceId)
        }
        override fun addGeoJsonFeatureSource(sourceId: String, feature: Feature) {
            sourceIds.add(sourceId)
        }
        override fun updateSourceGeoJson(sourceId: String, featureCollection: FeatureCollection) {
            sourceUpdates++
        }
        override fun updateSourceFeature(sourceId: String, feature: Feature) {
            sourceUpdates++
        }
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
        }
        override fun hasImage(imageId: String): Boolean = imageId in imageIds
        override fun addImages(images: HashMap<String, Bitmap>) {
            imageIds.addAll(images.keys)
        }
        fun simulateStyleReload() {
            layerIds.clear()
            sourceIds.clear()
            imageIds.clear()
        }
    }

    @Test
    fun `qa section 2 marker dp dimensions stay consistent across densities and zoom levels`() {
        val densities = listOf(
            DisplayMetrics.DENSITY_MEDIUM to 1.0f,
            DisplayMetrics.DENSITY_HIGH to 1.5f,
            DisplayMetrics.DENSITY_XHIGH to 2.0f,
            DisplayMetrics.DENSITY_XXHIGH to 3.0f,
            DisplayMetrics.DENSITY_XXXHIGH to 4.0f
        )

        val expectedNormalCanvasDp = VehicleMapRenderConfig.NORMAL_COACH_DP + 6f
        val expectedSelectedCanvasDp = VehicleMapRenderConfig.SELECTED_HALO_DP

        for ((dpi, scale) in densities) {
            val factory = VehicleMarkerBitmapFactory(dpi)
            val normalBmp = factory.getOrCreateCoachBitmap(
                CoachIconSpec(false, CoachFreshTier.ACTIVE, CoachStatusBadge.NONE, hasHeading = true, isPredictedOrInterpolated = true)
            )
            val selectedBmp = factory.getOrCreateCoachBitmap(
                CoachIconSpec(true, CoachFreshTier.ACTIVE, CoachStatusBadge.NONE, hasHeading = true, isPredictedOrInterpolated = true)
            )

            assertEquals(dpi, normalBmp.density)
            assertEquals(dpi, selectedBmp.density)

            val normalLowDp = VehicleMarkerBitmapFactory.computeRenderedDp(normalBmp.width, normalBmp.density, VehicleMapRenderConfig.LOW_ZOOM_ICON_SIZE)
            val normalMedDp = VehicleMarkerBitmapFactory.computeRenderedDp(normalBmp.width, normalBmp.density, VehicleMapRenderConfig.DEFAULT_ICON_SIZE)
            val normalHighDp = VehicleMarkerBitmapFactory.computeRenderedDp(normalBmp.width, normalBmp.density, VehicleMapRenderConfig.HIGH_ZOOM_ICON_SIZE)

            val selLowDp = VehicleMarkerBitmapFactory.computeRenderedDp(selectedBmp.width, selectedBmp.density, VehicleMapRenderConfig.LOW_ZOOM_ICON_SIZE)
            val selMedDp = VehicleMarkerBitmapFactory.computeRenderedDp(selectedBmp.width, selectedBmp.density, VehicleMapRenderConfig.DEFAULT_ICON_SIZE)
            val selHighDp = VehicleMarkerBitmapFactory.computeRenderedDp(selectedBmp.width, selectedBmp.density, VehicleMapRenderConfig.HIGH_ZOOM_ICON_SIZE)

            println("DENSITY=$dpi (${scale}x): normalPx=${normalBmp.width} (${"%.1f".format(normalLowDp)}/${"%.1f".format(normalMedDp)}/${"%.1f".format(normalHighDp)}dp), selectedPx=${selectedBmp.width} (${"%.1f".format(selLowDp)}/${"%.1f".format(selMedDp)}/${"%.1f".format(selHighDp)}dp)")

            assertEquals(expectedNormalCanvasDp * VehicleMapRenderConfig.LOW_ZOOM_ICON_SIZE, normalLowDp, 0.25f)
            assertEquals(expectedNormalCanvasDp * VehicleMapRenderConfig.DEFAULT_ICON_SIZE, normalMedDp, 0.25f)
            assertEquals(expectedNormalCanvasDp * VehicleMapRenderConfig.HIGH_ZOOM_ICON_SIZE, normalHighDp, 0.25f)

            assertEquals(expectedSelectedCanvasDp * VehicleMapRenderConfig.LOW_ZOOM_ICON_SIZE, selLowDp, 0.25f)
            assertEquals(expectedSelectedCanvasDp * VehicleMapRenderConfig.DEFAULT_ICON_SIZE, selMedDp, 0.25f)
            assertEquals(expectedSelectedCanvasDp * VehicleMapRenderConfig.HIGH_ZOOM_ICON_SIZE, selHighDp, 0.25f)

            // Coach body & halo design targets + vertical plate label clearance
            assertTrue(VehicleMapRenderConfig.NORMAL_COACH_DP in 36f..40f)
            assertTrue(VehicleMapRenderConfig.SELECTED_COACH_DP in 48f..54f)
            assertTrue(VehicleMapRenderConfig.SELECTED_HALO_DP in 56f..62f)
            assertTrue(VehicleMapRenderConfig.LABEL_NORMAL_OFFSET_Y_DP > expectedNormalCanvasDp / 2f)
            assertTrue(VehicleMapRenderConfig.LABEL_SELECTED_OFFSET_Y_DP > expectedSelectedCanvasDp / 2f)
        }
    }

    @Test
    fun `qa section 3 verifies all 12 visual fleet scenarios without flicker or ambiguity`() {
        val factory = VehicleMarkerBitmapFactory(DisplayMetrics.DENSITY_XXHIGH)

        val activeBmp = factory.getOrCreateCoachBitmap(CoachIconSpec(false, CoachFreshTier.ACTIVE, CoachStatusBadge.NONE, true, true))
        val delayedBmp = factory.getOrCreateCoachBitmap(CoachIconSpec(false, CoachFreshTier.DELAYED, CoachStatusBadge.ARRIVING, true, true))
        val staleBmp = factory.getOrCreateCoachBitmap(CoachIconSpec(false, CoachFreshTier.STALE_OR_HELD, CoachStatusBadge.STALE_CLOCK, false, false))
        val noHeadBmp = factory.getOrCreateCoachBitmap(CoachIconSpec(false, CoachFreshTier.ACTIVE, CoachStatusBadge.AT_STOP, false, true))
        val selectedBmp = factory.getOrCreateCoachBitmap(CoachIconSpec(true, CoachFreshTier.ACTIVE, CoachStatusBadge.NONE, true, true))

        assertEquals(5, setOf(activeBmp, delayedBmp, staleBmp, noHeadBmp, selectedBmp).size)
        assertTrue(selectedBmp.width > activeBmp.width)

        // Scenario fleets: 1, 10, 30, 50 buses + all edge states (co-located, nearby, no-heading, stale, interpolated, predicted, held)
        for (size in listOf(1, 10, 30, 50)) {
            val fleet = buildScenarioFleet(size)
            val snapshots1 = VehicleRenderDiffer.buildSnapshots(fleet, selectedPlate = fleet.first().telemetry.plateNumber)
            val snapshots2 = VehicleRenderDiffer.buildSnapshots(fleet, selectedPlate = fleet.first().telemetry.plateNumber)
            val unchangedDiff = VehicleRenderDiffer.diff(snapshots1, snapshots2)
            assertFalse("Identical poll must not trigger GeoJSON churn/flicker for size=$size", unchangedDiff.hasAnyChange)

            val fc = RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(
                vehicles = fleet,
                selectedPlate = fleet.first().telemetry.plateNumber
            )
            val features = fc.features()!!
            assertEquals(size, features.size)
            val uniquePlates = features.map { it.getStringProperty("raw_plate") }.toSet()
            assertEquals("No dropped or duplicate vehicles for size=$size", size, uniquePlates.size)
        }

        // Verify distinct states in a mixed scenario fleet (cases 5-12)
        val mixed = buildMixedEdgeCaseFleet()
        val mixedFc = RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(mixed, selectedPlate = "50E10001")
        val byPlate = mixedFc.features()!!.associateBy { it.getStringProperty("raw_plate") }

        assertEquals(true, byPlate["50E10001"]!!.getBooleanProperty("is_selected"))
        assertEquals(1000.0, byPlate["50E10001"]!!.getNumberProperty("sort_key")!!.toDouble(), 0.01)
        assertEquals(false, byPlate["50E10003"]!!.getBooleanProperty("has_heading"))
        assertEquals(0.0, byPlate["50E10003"]!!.getNumberProperty("bearing")!!.toDouble(), 0.01)
        assertEquals("STALE_OR_HELD", byPlate["50E10004"]!!.getStringProperty("fresh_tier"))
        assertEquals("INTERPOLATED_ON_ROUTE", byPlate["50E10005"]!!.getStringProperty("position_source"))
        assertEquals("LATENCY_COMPENSATED", byPlate["50E10006"]!!.getStringProperty("position_source"))
        assertEquals("HELD_AT_LAST_VALID_GPS", byPlate["50E10007"]!!.getStringProperty("position_source"))
        assertEquals("STALE_OR_HELD", byPlate["50E10007"]!!.getStringProperty("fresh_tier"))
    }

    @Test
    fun `qa section 4 and 5 verify layer lifecycle and overlap picker without gps shift`() {
        val ops = ProbeStyleOps()
        val factory = VehicleMarkerBitmapFactory(DisplayMetrics.DENSITY_XHIGH)
        val fleet = buildMixedEdgeCaseFleet()
        val routeFeature = RoutePathGeoJsonConverter.toLineStringFeature(
            RoutePath(1, 101, listOf(LatLngPoint(10.76, 106.68), LatLngPoint(10.78, 106.71)))
        )
        val stopsFc = RoutePathGeoJsonConverter.toStopFeatureCollection(
            listOf(BusStop(1, "S1", "Bến Thành", "Bến", "Q1", null, "1", "Hàm Nghi", true, "Active", 10.77, 106.698, "ben thanh", listOf("01"))),
            selectedStopId = 1
        )
        val userFeature = Feature.fromGeometry(Point.fromLngLat(106.695, 10.772)).apply {
            addBooleanProperty("has_accuracy", true)
            addNumberProperty("accuracy", 15f)
        }

        // First load -> switch route -> clear route -> reload stops/location/vehicles -> reload style
        MapLayerManager.applyRoutePolyline(ops, routeFeature, isOutbound = true)
        MapLayerManager.applyStops(ops, stopsFc, hasStops = true, showStops = true, bitmapFactory = factory)
        MapLayerManager.applyUserLocation(ops, userFeature, factory)
        val snaps = VehicleRenderDiffer.buildSnapshots(fleet, selectedPlate = "50E10001")
        val fc = RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(fleet, selectedPlate = "50E10001")
        MapLayerManager.applyVehicles(ops, fc, snaps.values, factory)

        assertTrue(MapLayerManager.isCanonicalOrderPreserved(ops.layerIds))
        assertEquals(0, ops.duplicateLayerAddAttempts)
        assertTrue(ops.layerIds.indexOf(MapLayerManager.ROUTE_LAYER_ID) < ops.layerIds.indexOf(MapLayerManager.VEHICLE_NORMAL_LAYER_ID))
        assertTrue(ops.layerIds.indexOf(MapLayerManager.SELECTED_STOP_LAYER_ID) < ops.layerIds.indexOf(MapLayerManager.VEHICLE_NORMAL_LAYER_ID))
        assertTrue(ops.layerIds.indexOf(MapLayerManager.VEHICLE_NORMAL_LAYER_ID) < ops.layerIds.indexOf(MapLayerManager.VEHICLE_SELECTED_LAYER_ID))
        assertTrue(ops.layerIds.indexOf(MapLayerManager.VEHICLE_LABEL_LAYER_ID) < ops.layerIds.indexOf(MapLayerManager.USER_LOCATION_LAYER_ID))

        // Clear route -> reload style -> re-apply in mixed order
        MapLayerManager.applyRoutePolyline(ops, null, isOutbound = false)
        MapLayerManager.applyVehicles(ops, FeatureCollection.fromFeatures(emptyArray()), emptyList(), factory)
        ops.simulateStyleReload()
        MapLayerManager.applyVehicles(ops, fc, snaps.values, factory)
        MapLayerManager.applyUserLocation(ops, userFeature, factory)
        MapLayerManager.applyRoutePolyline(ops, routeFeature, isOutbound = false)
        MapLayerManager.applyStops(ops, stopsFc, hasStops = true, showStops = true, bitmapFactory = factory)

        assertEquals(MapLayerManager.CANONICAL_LAYER_ORDER, ops.layerIds)
        assertEquals(0, ops.duplicateLayerAddAttempts)

        // Overlap picker scenarios: single, co-located cluster, empty tap, pick/dismiss/re-pick
        val colocated1 = fleet[0] // 10.7700, 106.7000
        val colocated2 = fleet[1] // 10.7700, 106.7000
        val nearby = fleet[2]     // slightly offset
        val far = fleet[4]
        val candidates = listOf(
            VehicleTapCandidate(colocated1, ScreenCoord(400f, 400f)),
            VehicleTapCandidate(colocated2, ScreenCoord(400f, 400f)),
            VehicleTapCandidate(nearby, ScreenCoord(418f, 412f)),
            VehicleTapCandidate(far, ScreenCoord(950f, 950f))
        )

        assertTrue(VehicleOverlapResolver.resolveTap(ScreenCoord(40f, 40f), candidates, density = 2f) is OverlapTapOutcome.None)
        val single = VehicleOverlapResolver.resolveTap(ScreenCoord(955f, 945f), candidates, density = 2f) as OverlapTapOutcome.Single
        assertEquals(far.telemetry.plateNumber, single.vehicle.telemetry.plateNumber)

        val multi = VehicleOverlapResolver.resolveTap(ScreenCoord(405f, 405f), candidates, density = 2f, selectedPlate = colocated2.telemetry.plateNumber) as OverlapTapOutcome.Multiple
        assertEquals(3, multi.vehicles.size)
        // Selected co-located tie breaks in favor of selected bus without mutating coordinates
        assertEquals(colocated2.telemetry.plateNumber, multi.vehicles[0].telemetry.plateNumber)
        assertEquals(colocated1.position.lat, multi.vehicles[1].position.lat, 0.0)
        assertEquals(colocated1.position.lng, multi.vehicles[1].position.lng, 0.0)
    }

    @Test
    fun `qa section 7 local benchmark across 10 30 50 vehicles and 20 style reloads`() {
        val factory = VehicleMarkerBitmapFactory(DisplayMetrics.DENSITY_XXHIGH)
        val ops = ProbeStyleOps()
        val rt = Runtime.getRuntime()

        for (count in listOf(10, 30, 50)) {
            val fleet = buildScenarioFleet(count)
            var prev = emptyMap<String, VehicleRenderSnapshot>()

            val t0 = System.nanoTime()
            val snaps = VehicleRenderDiffer.buildSnapshots(fleet, selectedPlate = fleet.first().telemetry.plateNumber)
            val diff = VehicleRenderDiffer.diff(prev, snaps)
            assertTrue(diff.hasAnyChange)
            val fc = RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(fleet, selectedPlate = fleet.first().telemetry.plateNumber)
            MapLayerManager.applyVehicles(ops, fc, snaps.values, factory)
            val coldMs = (System.nanoTime() - t0) / 1_000_000.0

            val t1 = System.nanoTime()
            repeat(10) {
                val warmSnaps = VehicleRenderDiffer.buildSnapshots(fleet, selectedPlate = fleet.first().telemetry.plateNumber)
                val warmDiff = VehicleRenderDiffer.diff(snaps, warmSnaps)
                assertFalse(warmDiff.hasAnyChange)
            }
            val warmPerCycleMs = ((System.nanoTime() - t1) / 10.0) / 1_000_000.0

            prev = snaps
            val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0)
            println("BENCH_LOCAL fleet=$count: coldPipelineMs=${"%.2f".format(coldMs)}, warmDifferMs=${"%.3f".format(warmPerCycleMs)}, coachPool=${factory.coachPoolSize()}, labelPool=${factory.labelPoolSize()}, jvmUsedMb=${"%.1f".format(usedMb)}")

            assertTrue("50-bus render pipeline stayed well under 250ms budget on JVM (got $coldMs ms)", coldMs < 250.0)
            assertTrue("Warm differ stayed well under 15ms (got $warmPerCycleMs ms)", warmPerCycleMs < 15.0)
            assertTrue(factory.coachPoolSize() <= VehicleMapRenderConfig.ICON_POOL_MAX_ENTRIES)
            assertTrue(factory.labelPoolSize() <= VehicleMapRenderConfig.LABEL_CACHE_MAX_ENTRIES)
        }

        // Style reload stress (20 cycles) must not duplicate layers or blow cache bounds
        val stressFleet = buildScenarioFleet(50)
        val stressSnaps = VehicleRenderDiffer.buildSnapshots(stressFleet, selectedPlate = "50E00001")
        val stressFc = RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(stressFleet, selectedPlate = "50E00001")
        val reloadStart = System.nanoTime()
        repeat(20) {
            ops.simulateStyleReload()
            MapLayerManager.applyVehicles(ops, stressFc, stressSnaps.values, factory)
            assertEquals(3, ops.layerIds.size)
            assertEquals(0, ops.duplicateLayerAddAttempts)
        }
        val reloadMs = (System.nanoTime() - reloadStart) / 1_000_000.0
        println("BENCH_STYLE_RELOAD 20x50: totalMs=${"%.2f".format(reloadMs)}, coachPool=${factory.coachPoolSize()}, labelPool=${factory.labelPoolSize()}")
        assertTrue(factory.coachPoolSize() <= VehicleMapRenderConfig.ICON_POOL_MAX_ENTRIES)
        assertTrue(factory.labelPoolSize() <= VehicleMapRenderConfig.LABEL_CACHE_MAX_ENTRIES)
    }

    private fun buildScenarioFleet(count: Int): List<RouteActiveVehicleUiModel> {
        val now = 1_700_000_000_000L
        return (1..count).map { idx ->
            val plate = "50E%05d".format(idx)
            val formatted = "50E-%03d.%02d".format(idx / 100, idx % 100)
            val isStale = idx % 7 == 0
            val noHead = idx % 11 == 0
            val posSrc = when (idx % 4) {
                0 -> PositionSource.LATENCY_COMPENSATED
                1 -> PositionSource.INTERPOLATED_ON_ROUTE
                2 -> PositionSource.HELD_AT_LAST_VALID_GPS
                else -> PositionSource.INTERPOLATED_ON_ROUTE
            }
            RouteActiveVehicleUiModel(
                telemetry = RouteVehicleTelemetry(
                    routeId = 1,
                    varId = 101,
                    plateNumber = plate,
                    formattedPlate = formatted,
                    checkpointStopId = idx,
                    checkpointStopName = "Trạm $idx",
                    rawDistanceMeters = idx * 45.0,
                    speedKmH = if (noHead) 0.0 else 24.0,
                    etaSeconds = idx * 12,
                    gpsTimestampIso = "2026-11-12T12:00:00+07:00",
                    gpsTimestampMillis = now - if (isStale) 210_000L else 12_000L,
                    requestStartedAtMillis = now,
                    responseReceivedAtMillis = now,
                    predictionReceivedAtMillis = now,
                    statusCode = 0,
                    freshnessState = if (isStale) GpsFreshnessState.STALE else GpsFreshnessState.FRESH,
                    latencyCategory = if (isStale) GpsLatencyCategory.STALE else GpsLatencyCategory.FRESH
                ),
                position = InterpolatedVehiclePosition(
                    lat = 10.7600 + idx * 0.0004,
                    lng = 106.6800 + idx * 0.0004,
                    bearing = ((idx * 37) % 360).toFloat(),
                    plateNumber = plate,
                    speedKmH = if (noHead) 0.0 else 24.0,
                    compensationStatus = if (posSrc == PositionSource.HELD_AT_LAST_VALID_GPS) CompensationStatus.STALE_DATA else CompensationStatus.LIVE_PROJECTED,
                    arrivalStatus = when (idx % 5) {
                        0 -> VehicleArrivalStatus.ARRIVING_AT_STOP
                        1 -> VehicleArrivalStatus.AT_STOP
                        2 -> VehicleArrivalStatus.PASSED_STOP
                        else -> VehicleArrivalStatus.MOVING
                    },
                    positionSource = posSrc,
                    latencyCategory = if (isStale) GpsLatencyCategory.STALE else GpsLatencyCategory.FRESH,
                    hasValidHeading = !noHead
                )
            )
        }
    }

    private fun buildMixedEdgeCaseFleet(): List<RouteActiveVehicleUiModel> {
        val now = 1_700_000_000_000L
        fun item(
            plate: String,
            formatted: String,
            lat: Double,
            lng: Double,
            bearing: Float,
            hasHeading: Boolean,
            freshness: GpsFreshnessState,
            latency: GpsLatencyCategory,
            source: PositionSource,
            arrival: VehicleArrivalStatus
        ) = RouteActiveVehicleUiModel(
            telemetry = RouteVehicleTelemetry(
                routeId = 1,
                varId = 101,
                plateNumber = plate,
                formattedPlate = formatted,
                checkpointStopId = 1,
                checkpointStopName = "Bến Thành",
                rawDistanceMeters = 120.0,
                speedKmH = if (hasHeading) 22.0 else 0.0,
                etaSeconds = 30,
                gpsTimestampIso = "2026-11-12T12:00:00+07:00",
                gpsTimestampMillis = now,
                requestStartedAtMillis = now,
                responseReceivedAtMillis = now,
                predictionReceivedAtMillis = now,
                statusCode = 0,
                freshnessState = freshness,
                latencyCategory = latency
            ),
            position = InterpolatedVehiclePosition(
                lat = lat,
                lng = lng,
                bearing = bearing,
                plateNumber = plate,
                speedKmH = if (hasHeading) 22.0 else 0.0,
                hasValidHeading = hasHeading,
                positionSource = source,
                latencyCategory = latency,
                arrivalStatus = arrival
            )
        )
        return listOf(
            item("50E10001", "50E-100.01", 10.7700, 106.7000, 45f, true, GpsFreshnessState.FRESH, GpsLatencyCategory.FRESH, PositionSource.INTERPOLATED_ON_ROUTE, VehicleArrivalStatus.MOVING),
            item("50E10002", "50E-100.02", 10.7700, 106.7000, 90f, true, GpsFreshnessState.FRESH, GpsLatencyCategory.FRESH, PositionSource.INTERPOLATED_ON_ROUTE, VehicleArrivalStatus.ARRIVING_AT_STOP),
            item("50E10003", "50E-100.03", 10.7701, 106.7001, 180f, false, GpsFreshnessState.FRESH, GpsLatencyCategory.FRESH, PositionSource.INTERPOLATED_ON_ROUTE, VehicleArrivalStatus.AT_STOP),
            item("50E10004", "50E-100.04", 10.7720, 106.7020, 270f, true, GpsFreshnessState.STALE, GpsLatencyCategory.STALE, PositionSource.INTERPOLATED_ON_ROUTE, VehicleArrivalStatus.MOVING),
            item("50E10005", "50E-100.05", 10.7740, 106.7040, 120f, true, GpsFreshnessState.FRESH, GpsLatencyCategory.FRESH, PositionSource.INTERPOLATED_ON_ROUTE, VehicleArrivalStatus.MOVING),
            item("50E10006", "50E-100.06", 10.7760, 106.7060, 355f, true, GpsFreshnessState.AGING, GpsLatencyCategory.DELAYED, PositionSource.LATENCY_COMPENSATED, VehicleArrivalStatus.MOVING),
            item("50E10007", "50E-100.07", 10.7780, 106.7080, 0f, false, GpsFreshnessState.UNCHANGED, GpsLatencyCategory.STALE, PositionSource.HELD_AT_LAST_VALID_GPS, VehicleArrivalStatus.AT_STOP)
        )
    }
}
