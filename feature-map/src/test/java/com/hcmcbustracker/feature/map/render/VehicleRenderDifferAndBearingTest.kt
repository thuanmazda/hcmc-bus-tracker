package com.hcmcbustracker.feature.map.render

import com.hcmcbustracker.core.model.GpsFreshnessState
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.RouteVehicleTelemetry
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel
import com.hcmcbustracker.feature.map.engine.InterpolatedVehiclePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleRenderDifferAndBearingTest {

    @Test
    fun `bearing smoother wraps cleanly across 0 and 360 degree boundary`() {
        assertEquals(-1.0f, BearingSmoother.shortestSignedDeltaDegrees(0f, 359f), 0.001f)
        assertEquals(2.0f, BearingSmoother.shortestSignedDeltaDegrees(359f, 1f), 0.001f)
        assertEquals(359.5f, BearingSmoother.interpolateBearingDegrees(0f, 359f, 0.5f), 0.001f)
        assertEquals(0.0f, BearingSmoother.interpolateBearingDegrees(359f, 1f, 0.5f), 0.001f)

        val delta90To270 = BearingSmoother.shortestSignedDeltaDegrees(90f, 270f)
        assertEquals(180f, kotlin.math.abs(delta90To270), 0.001f)

        assertEquals(0f, BearingSmoother.normalizeBearing(Float.NaN), 0.001f)
        assertEquals(315f, BearingSmoother.normalizeBearing(-45f), 0.001f)
        assertEquals(5f, BearingSmoother.normalizeBearing(725f), 0.001f)
    }

    @Test
    fun `shouldAnimateRotation refuses stationary or missing heading vehicles`() {
        assertFalse(
            BearingSmoother.shouldAnimateRotation(
                previousHasHeading = false,
                nextHasHeading = true,
                isStationary = false,
                fromDeg = 0f,
                toDeg = 90f
            )
        )
        assertFalse(
            BearingSmoother.shouldAnimateRotation(
                previousHasHeading = true,
                nextHasHeading = false,
                isStationary = false,
                fromDeg = 0f,
                toDeg = 90f
            )
        )
        assertFalse(
            BearingSmoother.shouldAnimateRotation(
                previousHasHeading = true,
                nextHasHeading = true,
                isStationary = true,
                fromDeg = 10f,
                toDeg = 80f
            )
        )
        assertFalse(
            BearingSmoother.shouldAnimateRotation(
                previousHasHeading = true,
                nextHasHeading = true,
                isStationary = false,
                fromDeg = 90f,
                toDeg = 91f
            )
        )
        assertTrue(
            BearingSmoother.shouldAnimateRotation(
                previousHasHeading = true,
                nextHasHeading = true,
                isStationary = false,
                fromDeg = 359f,
                toDeg = 2f
            )
        )
    }

    @Test
    fun `differ detects freshness status provenance bearing selection changes even with fixed coordinates`() {
        val busA = makeVehicle("50E61577", "50E-615.77", 10.77, 106.70, bearing = 90f)
        val busB = makeVehicle("50E76203", "50E-762.03", 10.76, 106.69, bearing = 180f)
        val invalidZero = makeVehicle("50E00000", "50E-000.00", 0.0, 0.0, bearing = 0f)

        val prev = VehicleRenderDiffer.buildSnapshots(
            vehicles = listOf(busA, busB, invalidZero),
            selectedPlate = null
        )
        assertEquals(2, prev.size) // (0.0, 0.0) excluded

        val identicalDiff = VehicleRenderDiffer.diff(prev, prev)
        assertFalse(identicalDiff.hasAnyChange)

        val updatedBusA = makeVehicle(
            plate = "50E61577",
            formatted = "50E-615.77",
            lat = 10.77,
            lng = 106.70,
            bearing = 95f,
            freshness = GpsFreshnessState.STALE,
            latencyCategory = GpsLatencyCategory.STALE,
            arrivalStatus = VehicleArrivalStatus.AT_STOP,
            positionSource = PositionSource.HELD_AT_LAST_VALID_GPS
        )
        val busC = makeVehicle("50E99999", "50E-999.99", 10.75, 106.68, bearing = 45f)

        val next = VehicleRenderDiffer.buildSnapshots(
            vehicles = listOf(updatedBusA, busC),
            selectedPlate = "50E61577"
        )
        val diff = VehicleRenderDiffer.diff(prev, next)

        assertTrue(diff.hasAnyChange)
        assertTrue(diff.addedPlates.contains("50E99999"))
        assertTrue(diff.removedPlates.contains("50E76203"))
        assertTrue(diff.coordinateChangedPlates.isEmpty())
        assertTrue(diff.bearingChangedPlates.contains("50E61577"))
        assertTrue(diff.freshnessChangedPlates.contains("50E61577"))
        assertTrue(diff.statusChangedPlates.contains("50E61577"))
        assertTrue(diff.provenanceChangedPlates.contains("50E61577"))
        assertTrue(diff.selectionChangedPlates.contains("50E61577"))
    }

    private fun makeVehicle(
        plate: String,
        formatted: String,
        lat: Double,
        lng: Double,
        bearing: Float,
        freshness: GpsFreshnessState = GpsFreshnessState.FRESH,
        latencyCategory: GpsLatencyCategory = GpsLatencyCategory.FRESH,
        arrivalStatus: VehicleArrivalStatus = VehicleArrivalStatus.MOVING,
        positionSource: PositionSource = PositionSource.INTERPOLATED_ON_ROUTE
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
                rawDistanceMeters = 250.0,
                speedKmH = 25.0,
                etaSeconds = 35,
                gpsTimestampIso = "2026-11-12T12:00:00+07:00",
                gpsTimestampMillis = now - 5_000L,
                requestStartedAtMillis = now,
                responseReceivedAtMillis = now,
                predictionReceivedAtMillis = now,
                statusCode = 0,
                freshnessState = freshness,
                latencyCategory = latencyCategory
            ),
            position = InterpolatedVehiclePosition(
                lat = lat,
                lng = lng,
                bearing = bearing,
                plateNumber = plate,
                speedKmH = 25.0,
                hasValidHeading = true,
                arrivalStatus = arrivalStatus,
                positionSource = positionSource,
                latencyCategory = latencyCategory
            )
        )
    }
}
