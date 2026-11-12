package com.hcmcbustracker.feature.map

import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.CompensationStatus
import com.hcmcbustracker.core.model.LatLngPoint
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import com.hcmcbustracker.feature.map.engine.InterpolatedVehiclePosition
import com.hcmcbustracker.feature.map.engine.VehicleInterpolationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleInterpolationEngineTest {

    @Test
    fun computeDistanceMeters_calculatesAccurateDistance() {
        // Ben Thanh (10.7719, 106.6983) to Ham Nghi hub (10.7710, 106.6995)
        val p1 = LatLngPoint(10.7719, 106.6983)
        val p2 = LatLngPoint(10.7710, 106.6995)

        val dist = VehicleInterpolationEngine.computeDistanceMeters(p1, p2)
        // Expected approx 160m
        assertTrue(dist in 140.0..180.0)
    }

    @Test
    fun computeBearing_pointsEastwardProperly() {
        val p1 = LatLngPoint(10.0, 106.0)
        val p2 = LatLngPoint(10.0, 107.0) // directly east

        val bearing = VehicleInterpolationEngine.computeBearing(p1, p2)
        // Heading east is ~90 degrees
        assertEquals(90.0f, bearing, 2.0f)
    }

    @Test
    fun interpolatePosition_accuratelyPlacesBusBehindStop() {
        // Line from (10.0, 106.0) -> (10.0, 106.01) -> (10.0, 106.02)
        val points = listOf(
            LatLngPoint(10.0, 106.0),
            LatLngPoint(10.0, 106.01),
            LatLngPoint(10.0, 106.02)
        )
        val stopLocation = LatLngPoint(10.0, 106.02)
        val distanceBehind = 500.0 // 500 meters behind stop

        val result = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = points,
            targetStopLocation = stopLocation,
            targetStopId = 101,
            distanceToStopMeters = distanceBehind,
            plateNumber = "50E-12345",
            speedKmH = 0.0 // stationary, no dead reckoning
        )

        assertNotNull(result)
        assertEquals("50E-12345", result?.plateNumber)
        assertEquals(0.0, result?.speedKmH ?: 0.0, 0.01)
        // Position should be west of the stop (lng < 106.02)
        assertTrue(result!!.lng < 106.02)
        assertTrue(result.lng >= 106.0)
    }

    @Test
    fun interpolatePosition_appliesLatencyCompensation_projectsVehicleForward() {
        val points = listOf(
            LatLngPoint(10.0, 106.0),
            LatLngPoint(10.0, 106.01),
            LatLngPoint(10.0, 106.02)
        )
        val stopLocation = LatLngPoint(10.0, 106.02)
        val rawDistance = 1000.0
        val speedKmH = 36.0 // 10 m/s
        val baseTime = 1731160000000L
        val timestampIso = "2024-11-09T13:46:40Z"
        val currentTime = baseTime + 30_000L // 30 seconds delay -> 300m projected

        val result = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = points,
            targetStopLocation = stopLocation,
            targetStopId = 101,
            distanceToStopMeters = rawDistance,
            plateNumber = "50E-616.63",
            speedKmH = speedKmH,
            timestampIso = timestampIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )

        assertNotNull(result)
        assertEquals(CompensationStatus.LIVE_PROJECTED, result!!.compensationStatus)
        assertEquals(1000.0, result.rawDistanceMeters, 0.01)
        assertEquals(300.0, result.projectedDistanceMeters, 0.01)
        assertEquals(700.0, result.effectiveDistanceMeters, 0.01)
    }

    @Test
    fun interpolatePosition_sequentialMonotonicSnapping_handlesRouteLoops() {
        // A route that loops:
        // (10.0, 106.0) -> (10.0, 106.05) -> (10.02, 106.05) -> (10.02, 106.0) -> (10.0, 106.0) -> (10.0, 106.10)
        val points = listOf(
            LatLngPoint(10.0, 106.0),
            LatLngPoint(10.0, 106.05),
            LatLngPoint(10.02, 106.05),
            LatLngPoint(10.02, 106.0),
            LatLngPoint(10.0, 106.0),
            LatLngPoint(10.0, 106.10)
        )

        val stop1 = BusStop(id = 1, code = "S1", name = "Trạm 1", stopType = "", zone = "", ward = null, addressNo = "", street = "", supportDisability = false, status = "", lat = 10.0, lng = 106.01, searchKeywords = "", routes = emptyList())
        val stop2InLoop = BusStop(id = 2, code = "S2", name = "Trạm 2 Trong Vòng", stopType = "", zone = "", ward = null, addressNo = "", street = "", supportDisability = false, status = "", lat = 10.02, lng = 106.03, searchKeywords = "", routes = emptyList())
        val stop3AfterLoop = BusStop(id = 3, code = "S3", name = "Trạm 3 Sau Vòng", stopType = "", zone = "", ward = null, addressNo = "", street = "", supportDisability = false, status = "", lat = 10.0, lng = 106.08, searchKeywords = "", routes = emptyList())

        val routeStops = listOf(stop1, stop2InLoop, stop3AfterLoop)

        // Interpolate for stop 3: it should snap to the segment after the loop (lng 106.0 -> 106.10), not before the loop
        val result = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = points,
            targetStopLocation = LatLngPoint(stop3AfterLoop.lat, stop3AfterLoop.lng),
            targetStopId = stop3AfterLoop.id,
            distanceToStopMeters = 500.0,
            plateNumber = "50E-99999",
            speedKmH = 0.0,
            routeStops = routeStops
        )

        assertNotNull(result)
        // Position must have cumulative distance past the loop
        assertTrue(result!!.cumulativeDistanceMeters > 5000.0)
    }

    @Test
    fun interpolatePosition_smoothsPositionWhenMovingRegularly() {
        val points = listOf(
            LatLngPoint(10.0, 106.0),
            LatLngPoint(10.0, 106.05),
            LatLngPoint(10.0, 106.10)
        )
        val stopLocation = LatLngPoint(10.0, 106.10)
        val baseTime = 1731160000000L

        // First position
        val p1 = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = points,
            targetStopLocation = stopLocation,
            targetStopId = 100,
            distanceToStopMeters = 1000.0,
            plateNumber = "50E-55555",
            speedKmH = 36.0,
            currentTimeMillis = baseTime
        )
        assertNotNull(p1)

        // Second position 2 seconds later, vehicle moved 20m closer
        val p2 = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = points,
            targetStopLocation = stopLocation,
            targetStopId = 100,
            distanceToStopMeters = 980.0,
            plateNumber = "50E-55555",
            speedKmH = 36.0,
            currentTimeMillis = baseTime + 2000L,
            previousPosition = p1
        )
        assertNotNull(p2)
        assertTrue(p2!!.lng >= p1!!.lng)
    }

    @Test
    fun interpolatePosition_ebmsDistanceMatchesCurvedPolylineAndResolvesNextStopBetweenStops() {
        // L-shaped route: North 1000m then East 1000m (total ~2000m)
        val p0 = LatLngPoint(10.000, 106.000)
        val p1 = LatLngPoint(10.009, 106.000) // ~1000.7m north
        val p2 = LatLngPoint(10.009, 106.009) // ~985.8m east
        val polyline = listOf(p0, p1, p2)

        val stopA = BusStop(1, "A", "Trạm A", "", "", null, "", "", false, "", p0.lat, p0.lng, "", emptyList())
        val stopB = BusStop(2, "B", "Trạm B", "", "", null, "", "", false, "", p1.lat, p1.lng, "", emptyList())
        val stopC = BusStop(3, "C", "Trạm C", "", "", null, "", "", false, "", p2.lat, p2.lng, "", emptyList())
        val stops = listOf(stopA, stopB, stopC)

        // Vehicle is 1400m behind Terminal C along polyline => placed along first vertical segment (lat < 10.009)
        // approaching Stop B first (nextStopId = 2) rather than jumping straight to Terminal C
        val result = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = polyline,
            targetStopLocation = LatLngPoint(stopC.lat, stopC.lng),
            targetStopId = stopC.id,
            distanceToStopMeters = 1400.0,
            plateNumber = "50E-11111",
            speedKmH = 18.0,
            routeStops = stops
        )
        assertNotNull(result)
        assertEquals(stopB.id, result!!.nextStopId)
        assertTrue(result.lat in 10.003..10.008)
        assertTrue(result.distanceToNextStopMeters!! in 350.0..500.0)
        assertEquals(PositionSource.INTERPOLATED_ON_ROUTE, result.positionSource)
    }

    @Test
    fun interpolatePosition_detectsPassedSelectedStopWhenCompensatedForward() {
        val p0 = LatLngPoint(10.000, 106.000)
        val p1 = LatLngPoint(10.000, 106.005) // ~548m east (Selected Stop S1)
        val p2 = LatLngPoint(10.000, 106.015) // ~1644m east (Next Stop S2)
        val polyline = listOf(p0, p1, p2)

        val s1 = BusStop(1, "S1", "Trạm 1", "", "", null, "", "", false, "", p1.lat, p1.lng, "", emptyList())
        val s2 = BusStop(2, "S2", "Trạm 2", "", "", null, "", "", false, "", p2.lat, p2.lng, "", emptyList())

        val baseTime = 1731160000000L
        // At dts, bus was 100m before S1; 40s later at 36 km/h (10 m/s) => projected 400m => 300m PAST S1!
        val result = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = polyline,
            targetStopLocation = LatLngPoint(s1.lat, s1.lng),
            targetStopId = s1.id,
            distanceToStopMeters = 100.0,
            plateNumber = "50E-22222",
            speedKmH = 36.0,
            timestampIso = "2024-11-09T13:46:40Z",
            responseReceivedAtMillis = baseTime + 40_000L,
            currentTimeMillis = baseTime + 40_000L,
            routeStops = listOf(s1, s2),
            selectedStopId = s1.id,
            allowAdaptiveExtendedProjection = true
        )
        assertNotNull(result)
        assertEquals(VehicleArrivalStatus.PASSED_STOP, result!!.arrivalStatus)
        assertTrue((result.distanceToSelectedStopMeters ?: 0.0) < -250.0)
        assertEquals(s2.id, result.nextStopId)
    }

    @Test
    fun interpolatePosition_guardsInvalidDirectionOffCorridorAndDegeneratePolyline() {
        val polyline = listOf(LatLngPoint(10.76, 106.68), LatLngPoint(10.77, 106.69))
        // Off-corridor target stop (10km away)
        val farStop = LatLngPoint(10.90, 106.90)
        val offRes = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = polyline,
            targetStopLocation = farStop,
            targetStopId = 999,
            distanceToStopMeters = 200.0,
            plateNumber = "50E-33333",
            speedKmH = 20.0
        )
        assertEquals(null, offRes)

        // Direction mismatch
        val wrongDirRes = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = polyline,
            targetStopLocation = LatLngPoint(10.77, 106.69),
            targetStopId = 1,
            distanceToStopMeters = 200.0,
            plateNumber = "50E-33333",
            speedKmH = 20.0,
            isDirectionValid = false
        )
        assertEquals(null, wrongDirRes)

        // Degenerate single point polyline
        val emptyPoly = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = listOf(LatLngPoint(10.76, 106.68)),
            targetStopLocation = LatLngPoint(10.76, 106.68),
            targetStopId = 1,
            distanceToStopMeters = 100.0,
            plateNumber = "50E-33333",
            speedKmH = 0.0
        )
        assertEquals(null, emptyPoly)
    }

    @Test
    fun bearing_coversCardinalsSmoothWrap359To0AndStationaryFallback() {
        val origin = LatLngPoint(10.77, 106.70)
        val north = VehicleInterpolationEngine.computeBearing(origin, LatLngPoint(10.78, 106.70))
        val east = VehicleInterpolationEngine.computeBearing(origin, LatLngPoint(10.77, 106.71))
        val south = VehicleInterpolationEngine.computeBearing(origin, LatLngPoint(10.76, 106.70))
        val west = VehicleInterpolationEngine.computeBearing(origin, LatLngPoint(10.77, 106.69))

        assertEquals(0.0f, north, 1.5f)
        assertEquals(90.0f, east, 1.5f)
        assertEquals(180.0f, south, 1.5f)
        assertEquals(270.0f, west, 1.5f)

        // Crossing 359 -> 1 must interpolate through 0, never wrapping around 180!
        val wrapped = VehicleInterpolationEngine.smoothBearingDegrees(previousDeg = 359f, targetDeg = 1f, alpha = 0.5f)
        assertTrue(wrapped in 359.5f..360f || wrapped in 0f..0.5f)

        // Stationary vehicle (speed == 0) preserves previous valid heading rather than spinning
        val prev = InterpolatedVehiclePosition(
            lat = 10.77, lng = 106.70, bearing = 135.0f,
            plateNumber = "50E-44444", speedKmH = 0.0, hasValidHeading = true
        )
        val (stationaryHeading, hasHeading) = VehicleInterpolationEngine.resolveSmoothedBearing(
            currentLat = 10.77, currentLng = 106.70, currentCumDist = 100.0,
            speedKmH = 0.0, lookaheadBearing = 270.0f, hasValidLookahead = true,
            isGpsFrozen = false, previousPosition = prev
        )
        assertTrue(hasHeading)
        assertEquals(135.0f, stationaryHeading, 0.01f)

        // Insufficient degenerate geometry yields hasValidHeading = false cleanly
        val degenerate = listOf(origin, origin)
        val (_, degenerateValid) = VehicleInterpolationEngine.computeLookaheadTangent(
            degenerate, VehicleInterpolationEngine.buildCumulativeDistances(degenerate), 0.0
        )
        assertEquals(false, degenerateValid)
    }

    @Test
    fun interpolatePosition_twoWayAndBranchedCorridors_stayOnCorrectVariantWithoutOppositeSnap() {
        // Outbound East: 106.68 -> 106.70
        val outboundPoly = listOf(LatLngPoint(10.77, 106.68), LatLngPoint(10.77, 106.70))
        // Inbound West: 106.70 -> 106.68
        val inboundPoly = listOf(LatLngPoint(10.77, 106.70), LatLngPoint(10.77, 106.68))

        val outStopEnd = BusStop(10, "O1", "Bến Đông", "", "", null, "", "", false, "", 10.77, 106.70, "", listOf("01"))
        val inStopEnd = BusStop(20, "I1", "Bến Tây", "", "", null, "", "", false, "", 10.77, 106.68, "", listOf("01"))

        val outPos = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = outboundPoly,
            targetStopLocation = LatLngPoint(outStopEnd.lat, outStopEnd.lng),
            targetStopId = outStopEnd.id,
            distanceToStopMeters = 400.0,
            plateNumber = "50E-10101",
            speedKmH = 20.0,
            routeStops = listOf(outStopEnd)
        )
        val inPos = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = inboundPoly,
            targetStopLocation = LatLngPoint(inStopEnd.lat, inStopEnd.lng),
            targetStopId = inStopEnd.id,
            distanceToStopMeters = 400.0,
            plateNumber = "50E-20202",
            speedKmH = 20.0,
            routeStops = listOf(inStopEnd)
        )

        assertNotNull(outPos)
        assertNotNull(inPos)
        assertEquals(90.0f, outPos!!.bearing, 2.0f)   // Eastbound
        assertEquals(270.0f, inPos!!.bearing, 2.0f)   // Westbound
        assertTrue(outPos.lng > 106.69)               // Approaching 106.70 from West
        assertTrue(inPos.lng < 106.69)                // Approaching 106.68 from East

        // Branch variant going North-East avoids main stem South branch stop
        val branchPoly = listOf(LatLngPoint(10.77, 106.68), LatLngPoint(10.785, 106.695))
        val branchTarget = BusStop(30, "BR", "Nhánh Bắc", "", "", null, "", "", false, "", 10.785, 106.695, "", listOf("01B"))
        val branchRes = VehicleInterpolationEngine.interpolatePosition(
            polylinePoints = branchPoly,
            targetStopLocation = LatLngPoint(branchTarget.lat, branchTarget.lng),
            targetStopId = branchTarget.id,
            distanceToStopMeters = 300.0,
            plateNumber = "50E-30303",
            speedKmH = 18.0,
            routeStops = listOf(branchTarget)
        )
        assertNotNull(branchRes)
        assertTrue(branchRes!!.lat > 10.775)
    }
}
