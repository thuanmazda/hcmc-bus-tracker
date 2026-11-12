package com.hcmcbustracker.feature.map

import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.LatLngPoint
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.feature.map.util.RoutePathGeoJsonConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.geojson.LineString
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutePathGeoJsonConverterTest {

    private val sampleRoutePath = RoutePath(
        routeId = 1,
        varId = 101,
        points = listOf(
            LatLngPoint(10.7684, 106.6942),
            LatLngPoint(10.7650, 106.6850),
            LatLngPoint(10.7600, 106.6750)
        )
    )

    @Test
    fun `toLineStringFeature converts coordinates accurately`() {
        val feature = RoutePathGeoJsonConverter.toLineStringFeature(sampleRoutePath)
        assertNotNull(feature)
        val geometry = feature?.geometry() as? LineString
        assertNotNull(geometry)
        assertEquals(3, geometry?.coordinates()?.size)
        assertEquals(106.6942, geometry?.coordinates()?.get(0)?.longitude() ?: 0.0, 0.0001)
        assertEquals(10.7684, geometry?.coordinates()?.get(0)?.latitude() ?: 0.0, 0.0001)
    }

    @Test
    fun `toLineStringFeature returns null for insufficient points`() {
        val invalidPath = RoutePath(
            routeId = 1,
            varId = 101,
            points = listOf(LatLngPoint(10.7684, 106.6942))
        )
        val feature = RoutePathGeoJsonConverter.toLineStringFeature(invalidPath)
        assertNull(feature)
    }

    @Test
    fun `calculateBounds computes valid bounding box`() {
        val bounds = RoutePathGeoJsonConverter.calculateBounds(sampleRoutePath)
        assertNotNull(bounds)
        assertEquals(10.7684, bounds?.latitudeNorth ?: 0.0, 0.0001)
        assertEquals(10.7600, bounds?.latitudeSouth ?: 0.0, 0.0001)
    }

    @Test
    fun `toStopFeatureCollection assigns correct start, end, and order attributes`() {
        val stops = listOf(
            BusStop(id = 1, code = "S1", name = "Bến Thành", stopType = "Bến", zone = "Q1", addressNo = "1", street = "Hàm Nghi", supportDisability = true, status = "Active", lat = 10.7684, lng = 106.6942, searchKeywords = "ben thanh", routes = listOf("01")),
            BusStop(id = 2, code = "S2", name = "Chợ Lớn", stopType = "Bến", zone = "Q5", addressNo = "2", street = "Hồng Bàng", supportDisability = true, status = "Active", lat = 10.7533, lng = 106.6544, searchKeywords = "cho lon", routes = listOf("01"))
        )

        val featureCollection = RoutePathGeoJsonConverter.toStopFeatureCollection(stops, selectedStopId = 2)
        val features = featureCollection.features()
        assertNotNull(features)
        assertEquals(2, features?.size)

        val first = features?.get(0)
        assertEquals(true, first?.getBooleanProperty("is_start"))
        assertEquals(false, first?.getBooleanProperty("is_end"))
        assertEquals(1, first?.getNumberProperty("order")?.toInt())

        val second = features?.get(1)
        assertEquals(false, second?.getBooleanProperty("is_start"))
        assertEquals(true, second?.getBooleanProperty("is_end"))
        assertEquals(true, second?.getBooleanProperty("is_selected"))
    }

    @Test
    fun `toUserLocationFeature creates valid point feature`() {
        val feature = RoutePathGeoJsonConverter.toUserLocationFeature(10.772, 106.698)
        assertNotNull(feature)
        val point = feature.geometry() as? org.maplibre.geojson.Point
        assertNotNull(point)
        assertEquals(106.698, point?.longitude() ?: 0.0, 0.0001)
        assertEquals(10.772, point?.latitude() ?: 0.0, 0.0001)
    }

    @Test
    fun `toVehicleFeature includes plate, bearing, and speed properties`() {
        val feature = RoutePathGeoJsonConverter.toVehicleFeature(
            lat = 10.765,
            lng = 106.685,
            plate = "51B-12345",
            bearing = 45.0f,
            speedKmH = 32.5
        )
        assertNotNull(feature)
        assertEquals("51B-12345", feature.getStringProperty("plate"))
        assertEquals(45.0f, feature.getNumberProperty("bearing")?.toFloat() ?: 0f, 0.01f)
        assertEquals(32.5, feature.getNumberProperty("speed")?.toDouble() ?: 0.0, 0.01)
    }

    @Test
    fun `toRouteVehiclesFeatureCollection splits arrow and label icons and skips invalid coordinates`() {
        val nowMs = System.currentTimeMillis()
        val telemetryHeading = com.hcmcbustracker.core.model.RouteVehicleTelemetry(
            routeId = 1,
            varId = 101,
            plateNumber = "50E61577",
            formattedPlate = "50E-615.77",
            checkpointStopId = 1,
            checkpointStopName = "Bến Thành",
            rawDistanceMeters = 300.0,
            speedKmH = 22.0,
            etaSeconds = 40,
            gpsTimestampIso = "2026-11-12T12:00:00+07:00",
            gpsTimestampMillis = nowMs - 20_000L,
            requestStartedAtMillis = nowMs,
            responseReceivedAtMillis = nowMs,
            predictionReceivedAtMillis = nowMs,
            statusCode = 0
        )
        val validPosWithHeading = com.hcmcbustracker.feature.map.engine.InterpolatedVehiclePosition(
            lat = 10.77,
            lng = 106.70,
            bearing = 123.0f,
            plateNumber = "50E61577",
            speedKmH = 22.0,
            hasValidHeading = true
        )
        val validPosNoHeading = validPosWithHeading.copy(
            lat = 10.76,
            lng = 106.69,
            bearing = 99.0f,
            plateNumber = "50E76203",
            hasValidHeading = false
        )
        val invalidZeroCoordPos = validPosWithHeading.copy(lat = 0.0, lng = 0.0, plateNumber = "50E00000")

        val fc = RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(
            vehicles = listOf(
                com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel(telemetryHeading, validPosWithHeading),
                com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel(
                    telemetryHeading.copy(plateNumber = "50E76203", formattedPlate = "50E-762.03"),
                    validPosNoHeading
                ),
                com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel(
                    telemetryHeading.copy(plateNumber = "50E00000", formattedPlate = "50E-000.00"),
                    invalidZeroCoordPos
                )
            ),
            selectedPlate = "50E61577"
        )

        val features = fc.features()!!
        assertEquals(2, features.size) // (0.0, 0.0) vehicle filtered out cleanly!

        val fHeading = features[0]
        assertEquals(true, fHeading.getBooleanProperty("has_heading"))
        assertEquals(123.0, fHeading.getNumberProperty("bearing")?.toDouble() ?: 0.0, 0.01)
        assertEquals("hcmc-bus-arrow-true-false", fHeading.getStringProperty("arrow_icon_id"))
        assertEquals("hcmc-bus-label-50E-615.77-true-false", fHeading.getStringProperty("label_icon_id"))

        val fNoHeading = features[1]
        assertEquals(false, fNoHeading.getBooleanProperty("has_heading"))
        assertEquals(0.0, fNoHeading.getNumberProperty("bearing")?.toDouble() ?: -1.0, 0.01)
        assertEquals("hcmc-bus-nohead-false-false", fNoHeading.getStringProperty("arrow_icon_id"))
        assertEquals("hcmc-bus-label-50E-762.03-false-false", fNoHeading.getStringProperty("label_icon_id"))
    }

    @Test
    fun `toRouteVehiclesFeatureCollection emits phase 12 coach label and sort key properties`() {
        val nowMs = System.currentTimeMillis()
        val telemetry = com.hcmcbustracker.core.model.RouteVehicleTelemetry(
            routeId = 1,
            varId = 101,
            plateNumber = "50E61577",
            formattedPlate = "50E-615.77",
            checkpointStopId = 1,
            checkpointStopName = "Bến Thành",
            rawDistanceMeters = 120.0,
            speedKmH = 18.0,
            etaSeconds = 20,
            gpsTimestampIso = "2026-11-12T12:00:00+07:00",
            gpsTimestampMillis = nowMs - 4_000L,
            requestStartedAtMillis = nowMs,
            responseReceivedAtMillis = nowMs,
            predictionReceivedAtMillis = nowMs,
            statusCode = 0
        )
        val selectedPos = com.hcmcbustracker.feature.map.engine.InterpolatedVehiclePosition(
            lat = 10.77,
            lng = 106.70,
            bearing = 123.0f,
            plateNumber = "50E61577",
            speedKmH = 18.0,
            hasValidHeading = true
        )
        val normalPos = selectedPos.copy(lat = 10.765, lng = 106.695, plateNumber = "50E76203", bearing = 40f)

        val fc = RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(
            vehicles = listOf(
                com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel(telemetry, selectedPos),
                com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel(
                    telemetry.copy(plateNumber = "50E76203", formattedPlate = "50E-762.03"),
                    normalPos
                )
            ),
            selectedPlate = "50E61577",
            bearingOverrides = mapOf("50E61577" to 358.5f)
        )

        val features = fc.features()!!
        assertEquals(2, features.size)

        val selectedFeature = features[0]
        assertEquals(358.5, selectedFeature.getNumberProperty("bearing")?.toDouble() ?: -1.0, 0.01)
        assertEquals("615.77", selectedFeature.getStringProperty("short_plate"))
        assertEquals(1000.0, selectedFeature.getNumberProperty("sort_key")?.toDouble() ?: -1.0, 0.01)
        assertNotNull(selectedFeature.getStringProperty("coach_icon_id"))
        assertEquals("hcmc-lbl-SHORT-615.77-true-false", selectedFeature.getStringProperty("short_label_icon_id"))
        assertEquals("hcmc-lbl-FULL-50E-615.77-true-false", selectedFeature.getStringProperty("full_label_icon_id"))

        val normalFeature = features[1]
        assertEquals(40.0, normalFeature.getNumberProperty("bearing")?.toDouble() ?: -1.0, 0.01)
        assertEquals(100.0, normalFeature.getNumberProperty("sort_key")?.toDouble() ?: -1.0, 0.01)
    }
}
