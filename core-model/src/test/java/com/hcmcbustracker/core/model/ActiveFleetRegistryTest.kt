package com.hcmcbustracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActiveFleetRegistryTest {

    @Before
    fun setUp() {
        ActiveFleetRegistry.clear()
    }

    private fun createTelemetry(
        routeId: Int = 1,
        varId: Int = 1,
        plate: String = "50E-784.27",
        distanceMeters: Double = 1500.0,
        speedKmH: Double = 32.0,
        etaSeconds: Int = 240,
        gpsMillis: Long = System.currentTimeMillis()
    ): RouteVehicleTelemetry {
        return RouteVehicleTelemetry(
            routeId = routeId,
            varId = varId,
            plateNumber = plate,
            formattedPlate = LicensePlateNormalizer.formatPlate(plate),
            checkpointStopId = 101,
            checkpointStopName = "Bến Thành",
            rawDistanceMeters = distanceMeters,
            speedKmH = speedKmH,
            etaSeconds = etaSeconds,
            gpsTimestampIso = "2026-11-14T12:00:00Z",
            gpsTimestampMillis = gpsMillis,
            requestStartedAtMillis = gpsMillis - 200,
            responseReceivedAtMillis = gpsMillis,
            predictionReceivedAtMillis = gpsMillis,
            statusCode = 1
        )
    }

    @Test
    fun recordVehicle_storesVehicleAndAllowsQueryByPlate() {
        val telemetry = createTelemetry(plate = "50E-784.27")
        ActiveFleetRegistry.recordVehicle(telemetry, routeNo = "01", routeName = "Bến Thành - BX Chợ Lớn")

        val found = ActiveFleetRegistry.findVehicleByPlate("50E-784.27")
        assertNotNull(found)
        assertEquals("50E-784.27", found?.formattedPlate)
        assertEquals("01", found?.routeNo)
        assertEquals("Bến Thành - BX Chợ Lớn", found?.routeName)

        // Query by normalized unformatted plate
        val foundNorm = ActiveFleetRegistry.findVehicleByPlate("50e78427")
        assertNotNull(foundNorm)
        assertEquals("50E-784.27", foundNorm?.formattedPlate)
    }

    @Test
    fun searchVehicles_matchesByPlateTailAndRouteNumber() {
        val veh1 = createTelemetry(plate = "50E-784.27")
        val veh2 = createTelemetry(plate = "51B-123.45")
        val veh3 = createTelemetry(plate = "53N-9999")

        ActiveFleetRegistry.recordVehicle(veh1, routeNo = "01", routeName = "Tuyến 01")
        ActiveFleetRegistry.recordVehicle(veh2, routeNo = "150", routeName = "Tuyến 150")
        ActiveFleetRegistry.recordVehicle(veh3, routeNo = "01", routeName = "Tuyến 01")

        // Search by numeric tail "78427"
        val res1 = ActiveFleetRegistry.searchVehicles("78427")
        assertEquals(1, res1.size)
        assertEquals("50E-784.27", res1.first().formattedPlate)

        // Search by route number "01"
        val resRoute = ActiveFleetRegistry.searchVehicles("01")
        assertEquals(2, resRoute.size)

        // Search by plate letters "51b"
        val resPlate = ActiveFleetRegistry.searchVehicles("51b")
        assertEquals(1, resPlate.size)
        assertEquals("51B-123.45", resPlate.first().formattedPlate)
    }

    @Test
    fun recordFleet_batchRegistersAllVehicles() {
        val veh1 = createTelemetry(plate = "50E-111.11")
        val veh2 = createTelemetry(plate = "50E-222.22")

        ActiveFleetRegistry.recordFleet(listOf(veh1, veh2), routeNo = "04", routeName = "Bến Thành - An Sương")

        val snapshot = ActiveFleetRegistry.getAllActiveVehicles()
        assertEquals(2, snapshot.size)
    }

    @Test
    fun evictStaleVehicles_removesVehiclesOlderThanRetentionLimit() {
        val now = System.currentTimeMillis()
        val freshVeh = createTelemetry(plate = "50E-FRESH", gpsMillis = now)
        val staleVeh = createTelemetry(plate = "50E-OLD", gpsMillis = now - 35 * 60 * 1000L) // 35 mins old

        ActiveFleetRegistry.recordVehicle(freshVeh, routeNo = "01")
        ActiveFleetRegistry.recordVehicle(staleVeh, routeNo = "01")

        ActiveFleetRegistry.evictStaleVehicles(now)

        assertNotNull(ActiveFleetRegistry.findVehicleByPlate("50E-FRESH"))
        assertNull(ActiveFleetRegistry.findVehicleByPlate("50E-OLD"))
    }
}
