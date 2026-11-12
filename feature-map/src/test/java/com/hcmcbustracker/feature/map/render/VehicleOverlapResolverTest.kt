package com.hcmcbustracker.feature.map.render

import com.hcmcbustracker.core.model.RouteVehicleTelemetry
import com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel
import com.hcmcbustracker.feature.map.engine.InterpolatedVehiclePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleOverlapResolverTest {

    @Test
    fun `resolveTap distinguishes empty single and overlapping co-located buses without altering gps`() {
        val v1 = makeCandidate("50E61577", "50E-615.77", 10.7700, 106.7000, x = 500f, y = 500f)
        val v2 = makeCandidate("50E76203", "50E-762.03", 10.7700, 106.7000, x = 510f, y = 505f)
        val v3 = makeCandidate("50E88888", "50E-888.88", 10.7702, 106.7002, x = 520f, y = 512f)
        val farBus = makeCandidate("50E99999", "50E-999.99", 10.7900, 106.7200, x = 900f, y = 900f)

        val noneOutcome = VehicleOverlapResolver.resolveTap(
            tapPoint = ScreenCoord(100f, 100f),
            candidates = listOf(v1, v2, v3, farBus),
            density = 2.0f
        )
        assertTrue(noneOutcome is OverlapTapOutcome.None)

        val singleOutcome = VehicleOverlapResolver.resolveTap(
            tapPoint = ScreenCoord(905f, 905f),
            candidates = listOf(v1, v2, v3, farBus),
            density = 2.0f
        )
        assertTrue(singleOutcome is OverlapTapOutcome.Single)
        val singleVehicle = (singleOutcome as OverlapTapOutcome.Single).vehicle
        assertEquals("50E99999", singleVehicle.telemetry.plateNumber)
        assertEquals(10.7900, singleVehicle.position.lat, 0.000001)
        assertEquals(106.7200, singleVehicle.position.lng, 0.000001)

        val multiOutcome = VehicleOverlapResolver.resolveTap(
            tapPoint = ScreenCoord(502f, 502f),
            candidates = listOf(v1, v2, v3, farBus),
            density = 2.0f,
            selectedPlate = "50E76203"
        )
        assertTrue(multiOutcome is OverlapTapOutcome.Multiple)
        val multiList = (multiOutcome as OverlapTapOutcome.Multiple).vehicles
        assertEquals(3, multiList.size)
        // Nearest distance wins first (v1 at ~2.8px vs v2 at ~8.5px), and true GPS coords stay untouched
        assertEquals("50E61577", multiList[0].telemetry.plateNumber)
        assertEquals(10.7700, multiList[0].position.lat, 0.000001)
        assertEquals(10.7700, multiList[1].position.lat, 0.000001)
    }

    @Test
    fun `resolveTap scales 44dp hit radius accurately across screen densities`() {
        val bus = makeCandidate("50E61577", "50E-615.77", 10.7700, 106.7000, x = 100f, y = 100f)

        // Offset = 80 px: > 44dp at 1.0f density, <= 132px (44dp * 3.0) at xxhdpi 3.0f density
        val tap = ScreenCoord(180f, 100f)

        val mdpi = VehicleOverlapResolver.resolveTap(tap, listOf(bus), density = 1.0f)
        val xxhdpi = VehicleOverlapResolver.resolveTap(tap, listOf(bus), density = 3.0f)

        assertTrue(mdpi is OverlapTapOutcome.None)
        assertTrue(xxhdpi is OverlapTapOutcome.Single)
    }

    private fun makeCandidate(
        plate: String,
        formatted: String,
        lat: Double,
        lng: Double,
        x: Float,
        y: Float
    ): VehicleTapCandidate {
        val now = 1_700_000_000_000L
        return VehicleTapCandidate(
            vehicle = RouteActiveVehicleUiModel(
                telemetry = RouteVehicleTelemetry(
                    routeId = 1,
                    varId = 101,
                    plateNumber = plate,
                    formattedPlate = formatted,
                    checkpointStopId = 1,
                    checkpointStopName = "Bến Thành",
                    rawDistanceMeters = 100.0,
                    speedKmH = 10.0,
                    etaSeconds = 20,
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
                    bearing = 0f,
                    plateNumber = plate,
                    speedKmH = 10.0
                )
            ),
            screenCoord = ScreenCoord(x, y)
        )
    }
}
