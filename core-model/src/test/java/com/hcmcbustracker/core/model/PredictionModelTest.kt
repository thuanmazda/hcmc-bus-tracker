package com.hcmcbustracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionModelTest {

    @Test
    fun arrivalVehicle_etaCalculations_andFormatting() {
        val v1 = ArrivalVehicle(
            plateNumber = "50E20651",
            etaSeconds = 250,
            distanceMeters = 1168.21,
            speedKmH = 24.0,
            timestampIso = "2026-11-09T21:13:29+07:00",
            status = 0
        )
        // 250s = 4 minutes 10 seconds
        assertEquals(5, v1.etaMinutes)
        assertEquals("4 phút 10 giây", v1.formattedEta)
        assertFalse(v1.isApproaching)
        assertEquals("1.2 km", v1.formattedDistance)
        assertEquals("24 km/h", v1.formattedSpeed)
        assertEquals("50E-206.51", v1.formattedPlate)
        assertEquals("21:13:29", v1.gpsTimeFormatted)

        val vApproaching = ArrivalVehicle(
            plateNumber = "51B26725",
            etaSeconds = 45,
            distanceMeters = 210.0,
            speedKmH = 15.0,
            timestampIso = "2026-11-09T21:19:11+07:00",
            status = 0
        )
        // < 60s
        assertEquals(1, vApproaching.etaMinutes)
        assertEquals("Sắp đến (< 1 phút)", vApproaching.formattedEta)
        assertTrue(vApproaching.isApproaching)
        assertEquals("210m", vApproaching.formattedDistance)
        assertEquals("21:19:11", vApproaching.gpsTimeFormatted)

        val vZero = ArrivalVehicle(
            plateNumber = "51B12345",
            etaSeconds = 0,
            distanceMeters = 0.0,
            speedKmH = 0.0,
            timestampIso = "",
            status = 0
        )
        assertEquals("--", vZero.formattedEta)
    }

    @Test
    fun arrivalVehicle_gpsFreshness_evaluatesCorrectly() {
        val now = System.currentTimeMillis()

        // GPS packet sent 30 seconds ago
        val recentIso = java.time.Instant.ofEpochMilli(now - 30_000L).toString()
        val vehicleRecent = ArrivalVehicle(
            plateNumber = "50E20651",
            etaSeconds = 300,
            distanceMeters = 1500.0,
            speedKmH = 25.0,
            timestampIso = recentIso,
            status = 0
        )
        assertFalse(vehicleRecent.isGpsStale(now))
        assertEquals(30L, vehicleRecent.getGpsAgeSeconds(now))

        // GPS packet sent 250 seconds ago (> 180s threshold)
        val oldIso = java.time.Instant.ofEpochMilli(now - 250_000L).toString()
        val vehicleStale = ArrivalVehicle(
            plateNumber = "50E20658",
            etaSeconds = 600,
            distanceMeters = 3500.0,
            speedKmH = 18.0,
            timestampIso = oldIso,
            status = 0
        )
        assertTrue(vehicleStale.isGpsStale(now))
        assertEquals(250L, vehicleStale.getGpsAgeSeconds(now))

        // GPS packet with invalid / blank timestamp is safely marked as stale (C.6)
        val vehicleInvalid = ArrivalVehicle(
            plateNumber = "50E99999",
            etaSeconds = 120,
            distanceMeters = 800.0,
            speedKmH = 20.0,
            timestampIso = "INVALID_TIMESTAMP",
            status = 0
        )
        assertTrue(vehicleInvalid.isGpsStale(now))
        assertEquals(-1L, vehicleInvalid.getGpsAgeSeconds(now))
    }
}
