package com.hcmcbustracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDataMergerTest {

    private val nowMs = 1_794_470_400_000L

    private fun sampleTelemetry(
        plate: String = "50E-616.63",
        gpsMs: Long? = nowMs - 40_000L,
        responseMs: Long = nowMs - 200L,
        dist: Double = 900.0,
        routeId: Int = 1,
        varId: Int = 101
    ): RouteVehicleTelemetry {
        return RouteVehicleTelemetry(
            routeId = routeId,
            varId = varId,
            plateNumber = plate,
            formattedPlate = plate,
            checkpointStopId = 4223,
            checkpointStopName = "Hàm Nghi",
            rawDistanceMeters = dist,
            speedKmH = 22.0,
            etaSeconds = 150,
            gpsTimestampIso = "2026-11-12T15:00:00+07:00",
            gpsTimestampMillis = gpsMs,
            requestStartedAtMillis = responseMs - 150L,
            responseReceivedAtMillis = responseMs,
            predictionReceivedAtMillis = responseMs + 10L,
            statusCode = 0
        )
    }

    @Test
    fun `newer record replaces older record and older record never overwrites newer`() {
        val older = SourcedVehicleTelemetry(
            telemetry = sampleTelemetry(gpsMs = nowMs - 90_000L, responseMs = nowMs - 5_000L),
            sourceType = GpsSourceType.EBMS_PREDICTION_REST,
            capabilityRank = 1
        )
        val newer = SourcedVehicleTelemetry(
            telemetry = sampleTelemetry(gpsMs = nowMs - 30_000L, responseMs = nowMs - 3_000L, dist = 650.0),
            sourceType = GpsSourceType.EBMS_PREDICTION_REST,
            capabilityRank = 1
        )

        val mergedForward = VehicleDataMerger.merge(older, newer, nowMs)
        assertEquals(nowMs - 30_000L, mergedForward?.telemetry?.gpsTimestampMillis)
        assertEquals(650.0, mergedForward?.telemetry?.rawDistanceMeters ?: 0.0, 0.01)

        // Late-arriving network packet with higher responseMs MUST NOT overwrite newer gpsMs
        val delayedOlderWithLaterResponse = SourcedVehicleTelemetry(
            telemetry = sampleTelemetry(gpsMs = nowMs - 70_000L, responseMs = nowMs - 10L, dist = 800.0),
            sourceType = GpsSourceType.EBMS_PREDICTION_REST,
            capabilityRank = 1
        )
        val mergedRollbackAttempt = VehicleDataMerger.merge(mergedForward, delayedOlderWithLaterResponse, nowMs)
        assertEquals(nowMs - 30_000L, mergedRollbackAttempt?.telemetry?.gpsTimestampMillis)
        assertEquals(650.0, mergedRollbackAttempt?.telemetry?.rawDistanceMeters ?: 0.0, 0.01)
    }

    @Test
    fun `different sources distinguish provenance and follow gps timestamp over response time`() {
        val ebmsDerived = SourcedVehicleTelemetry(
            telemetry = sampleTelemetry(gpsMs = nowMs - 60_000L, responseMs = nowMs - 10L),
            sourceType = GpsSourceType.EBMS_PREDICTION_REST,
            capabilityRank = 1,
            isDerivedPrediction = true
        )
        val partnerNewerGps = SourcedVehicleTelemetry(
            telemetry = sampleTelemetry(gpsMs = nowMs - 15_000L, responseMs = nowMs - 1_000L),
            sourceType = GpsSourceType.PARTNER_DIRECT_GPS,
            capabilityRank = 5,
            isDerivedPrediction = false
        )

        val merged = VehicleDataMerger.merge(ebmsDerived, partnerNewerGps, nowMs)
        assertNotNull(merged)
        assertEquals(GpsSourceType.PARTNER_DIRECT_GPS, merged?.sourceType)
        assertEquals(false, merged?.isDerivedPrediction)
        assertEquals(nowMs - 15_000L, merged?.telemetry?.gpsTimestampMillis)

        // Older partner packet must NOT overwrite newer EBMS packet
        val olderPartner = SourcedVehicleTelemetry(
            telemetry = sampleTelemetry(gpsMs = nowMs - 120_000L, responseMs = nowMs),
            sourceType = GpsSourceType.PARTNER_DIRECT_GPS,
            capabilityRank = 5,
            isDerivedPrediction = false
        )
        val freshEbms = ebmsDerived.copy(
            telemetry = sampleTelemetry(gpsMs = nowMs - 35_000L, responseMs = nowMs - 2_000L)
        )
        val preserved = VehicleDataMerger.merge(freshEbms, olderPartner, nowMs)
        assertEquals(GpsSourceType.EBMS_PREDICTION_REST, preserved?.sourceType)
        assertEquals(nowMs - 35_000L, preserved?.telemetry?.gpsTimestampMillis)
    }

    @Test
    fun `invalid or future timestamps never overwrite valid records and invalid plates are ignored`() {
        val valid = SourcedVehicleTelemetry(
            telemetry = sampleTelemetry(gpsMs = nowMs - 45_000L),
            sourceType = GpsSourceType.EBMS_PREDICTION_REST
        )
        val futureRecord = SourcedVehicleTelemetry(
            telemetry = sampleTelemetry(gpsMs = nowMs + 120_000L),
            sourceType = GpsSourceType.EBMS_PREDICTION_REST
        )
        val mergedFuture = VehicleDataMerger.merge(valid, futureRecord, nowMs)
        assertEquals(nowMs - 45_000L, mergedFuture?.telemetry?.gpsTimestampMillis)

        // Invalid plate identifiers
        assertNull(VehicleDataMerger.normalizePlateIdentifier(""))
        assertNull(VehicleDataMerger.normalizePlateIdentifier("  "))
        assertNull(VehicleDataMerger.normalizePlateIdentifier("??"))
        assertEquals("50E61663", VehicleDataMerger.normalizePlateIdentifier(" 50e-616.63 "))

        val invalidPlateRecord = SourcedVehicleTelemetry(
            telemetry = sampleTelemetry(plate = "!!!", gpsMs = nowMs - 5_000L),
            sourceType = GpsSourceType.EBMS_PREDICTION_REST
        )
        val unchanged = VehicleDataMerger.merge(valid, invalidPlateRecord, nowMs)
        assertEquals("50E-616.63", unchanged?.telemetry?.plateNumber)
        assertEquals(nowMs - 45_000L, unchanged?.telemetry?.gpsTimestampMillis)
    }

    @Test
    fun `endpoint capability registry enforces prediction capabilities accurately`() {
        val cap = EndpointCapabilityRegistry.resolve(EndpointCapabilityRegistry.ENDPOINT_PREDICT_BY_STOP_ID)
        assertEquals(GpsSourceType.EBMS_PREDICTION_REST, cap.sourceType)
        assertTrue(!cap.supportsDirectLatLng)
        assertTrue(!cap.supportsDirectHeading)
        assertTrue(cap.minSafePollIntervalMs >= 10_000L)
    }
}
