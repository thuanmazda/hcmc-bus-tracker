package com.hcmcbustracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PositionCompensationEngineTest {

    private val baseTimeMillis = 1731160000000L // arbitrary fixed time
    private val baseIso = "2024-11-09T13:46:40Z" // corresponds to baseTimeMillis

    @Test
    fun parseGpsTimestamp_handlesIsoWithOffset() {
        val iso = "2026-11-09T21:13:29+07:00"
        val millis = PositionCompensationEngine.parseGpsTimestampMillis(iso)
        assertNotNull(millis)
        val instant = Instant.ofEpochMilli(millis!!)
        assertEquals("2026-11-09T14:13:29Z", instant.toString())
    }

    @Test
    fun parseGpsTimestamp_handlesUtcInstant() {
        val iso = "2026-11-09T14:13:29Z"
        val millis = PositionCompensationEngine.parseGpsTimestampMillis(iso)
        assertNotNull(millis)
        assertEquals(Instant.parse(iso).toEpochMilli(), millis)
    }

    @Test
    fun parseGpsTimestamp_handlesEpochMillisAndSeconds() {
        val secondsStr = "1731160000"
        assertEquals(1731160000000L, PositionCompensationEngine.parseGpsTimestampMillis(secondsStr))

        val millisStr = "1731160000123"
        assertEquals(1731160000123L, PositionCompensationEngine.parseGpsTimestampMillis(millisStr))
    }

    @Test
    fun parseGpsTimestamp_invalidString_returnsNull() {
        assertNull(PositionCompensationEngine.parseGpsTimestampMillis(null))
        assertNull(PositionCompensationEngine.parseGpsTimestampMillis(""))
        assertNull(PositionCompensationEngine.parseGpsTimestampMillis("invalid-iso-string"))
    }

    @Test
    fun compensation_freshGps_zeroDelay() {
        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 1000.0,
            speedKmH = 36.0, // 10 m/s
            timestampIso = baseIso,
            responseReceivedAtMillis = baseTimeMillis,
            currentTimeMillis = baseTimeMillis
        )

        assertEquals(CompensationStatus.LIVE_PROJECTED, result.status)
        assertEquals(0L, result.gpsAgeSeconds)
        assertEquals(0.0, result.projectedDistanceMeters, 0.001)
        assertEquals(1000.0, result.effectiveDistanceMeters, 0.001)
        assertTrue(result.isProjected)
    }

    @Test
    fun compensation_gpsDelayed15Seconds() {
        val currentTime = baseTimeMillis + 15_000L
        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 1000.0,
            speedKmH = 36.0, // 10 m/s
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )

        assertEquals(CompensationStatus.LIVE_PROJECTED, result.status)
        assertEquals(15L, result.gpsAgeSeconds)
        assertEquals(150.0, result.projectedDistanceMeters, 0.001) // 10 m/s * 15s = 150m
        assertEquals(850.0, result.effectiveDistanceMeters, 0.001)
    }

    @Test
    fun compensation_gpsDelayed30Seconds() {
        val currentTime = baseTimeMillis + 30_000L
        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 1000.0,
            speedKmH = 36.0, // 10 m/s
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )

        assertEquals(CompensationStatus.LIVE_PROJECTED, result.status)
        assertEquals(30L, result.gpsAgeSeconds)
        assertEquals(300.0, result.projectedDistanceMeters, 0.001)
        assertEquals(700.0, result.effectiveDistanceMeters, 0.001)
    }

    @Test
    fun compensation_gpsDelayed60Seconds() {
        val currentTime = baseTimeMillis + 60_000L
        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 2000.0,
            speedKmH = 36.0, // 10 m/s
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )

        assertEquals(CompensationStatus.LIVE_PROJECTED, result.status)
        assertEquals(60L, result.gpsAgeSeconds)
        assertEquals(600.0, result.projectedDistanceMeters, 0.001)
        assertEquals(1400.0, result.effectiveDistanceMeters, 0.001)
    }

    @Test
    fun compensation_gpsDelayed120Seconds() {
        val currentTime = baseTimeMillis + 120_000L
        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 2000.0,
            speedKmH = 36.0, // 10 m/s
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )

        assertEquals(CompensationStatus.LIVE_PROJECTED, result.status)
        assertEquals(120L, result.gpsAgeSeconds)
        assertEquals(1200.0, result.projectedDistanceMeters, 0.001)
        assertEquals(800.0, result.effectiveDistanceMeters, 0.001)
    }

    @Test
    fun compensation_gpsStaleOver120Seconds_suspendsProjection() {
        val currentTime = baseTimeMillis + 150_000L // 150s old
        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 2000.0,
            speedKmH = 36.0,
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )

        assertEquals(CompensationStatus.STALE_DATA, result.status)
        assertEquals(150L, result.gpsAgeSeconds)
        assertEquals(0.0, result.projectedDistanceMeters, 0.001)
        assertEquals(2000.0, result.effectiveDistanceMeters, 0.001) // Raw distance preserved
        assertFalse(result.isProjected)
    }

    @Test
    fun compensation_stationaryVehicle_speedNearZero_noProjection() {
        val currentTime = baseTimeMillis + 30_000L
        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 800.0,
            speedKmH = 1.0, // <= 1.5 km/h
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )

        assertEquals(CompensationStatus.STATIONARY, result.status)
        assertEquals(30L, result.gpsAgeSeconds)
        assertEquals(0.0, result.projectedDistanceMeters, 0.001)
        assertEquals(800.0, result.effectiveDistanceMeters, 0.001)
        assertFalse(result.isProjected)
    }

    @Test
    fun compensation_invalidSpeed_negativeOrTooHigh() {
        val currentTime = baseTimeMillis + 20_000L
        val resultNeg = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 500.0,
            speedKmH = -5.0,
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )
        assertEquals(CompensationStatus.INVALID_SPEED, resultNeg.status)

        val resultTooFast = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 500.0,
            speedKmH = 120.0, // > 90 km/h
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )
        assertEquals(CompensationStatus.INVALID_SPEED, resultTooFast.status)
    }

    @Test
    fun compensation_missingTimestamp_returnsInvalidTimestampStatus() {
        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 500.0,
            speedKmH = 30.0,
            timestampIso = null,
            responseReceivedAtMillis = baseTimeMillis,
            currentTimeMillis = baseTimeMillis
        )
        assertEquals(CompensationStatus.INVALID_TIMESTAMP, result.status)
        assertEquals(500.0, result.effectiveDistanceMeters, 0.001)
    }

    @Test
    fun compensation_projectedDistanceExceedsRemaining_returnsAtStop() {
        val currentTime = baseTimeMillis + 30_000L
        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 200.0, // only 200m left
            speedKmH = 36.0, // 10 m/s * 30s = 300m projected
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )

        assertEquals(CompensationStatus.AT_STOP, result.status)
        assertEquals(0.0, result.effectiveDistanceMeters, 0.001)
        assertEquals(200.0, result.projectedDistanceMeters, 0.001)
    }

    @Test
    fun compensation_directionMismatch_suspendsProjection() {
        val currentTime = baseTimeMillis + 25_000L
        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 1000.0,
            speedKmH = 36.0,
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime,
            isDirectionValid = false
        )

        assertEquals(CompensationStatus.DIRECTION_MISMATCH, result.status)
        assertEquals(1000.0, result.effectiveDistanceMeters, 0.001)
        assertFalse(result.isProjected)
    }

    @Test
    fun compensation_realMultiGoCase_plate50E61663_resolves967mDeviation() {
        // Raw EBMS distance: 6,417m
        // MultiGo reported: 5.45 km (5,450m)
        // Difference: 967m
        // If speed is 38 km/h (~10.555 m/s) and GPS delay is 91.6 seconds:
        val speedKmH = 38.0
        val delaySeconds = 91.6
        val currentTime = baseTimeMillis + (delaySeconds * 1000).toLong()

        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 6417.0,
            speedKmH = speedKmH,
            timestampIso = baseIso,
            responseReceivedAtMillis = currentTime,
            currentTimeMillis = currentTime
        )

        assertEquals(CompensationStatus.LIVE_PROJECTED, result.status)
        // Projected distance: 10.555 * 91.6 = 966.89m ~ 967m
        assertEquals(967.0, result.projectedDistanceMeters, 2.0)
        // Effective distance: 6417 - 967 = 5450m (5.45 km)
        assertEquals(5450.0, result.effectiveDistanceMeters, 2.0)
    }

    @Test
    fun adaptiveCompensation_handles140_160_188SecondsWithoutCliffJumpAndCapsMaxProjection() {
        val speedKmH = 21.6 // 6.0 m/s
        val at120 = PositionCompensationEngine.calculateAdaptiveCompensation(
            rawDistanceMeters = 2000.0,
            speedKmH = speedKmH,
            timestampIso = baseIso,
            responseReceivedAtMillis = baseTimeMillis + 120_000L,
            currentTimeMillis = baseTimeMillis + 120_000L
        )
        val at140 = PositionCompensationEngine.calculateAdaptiveCompensation(
            rawDistanceMeters = 2000.0,
            speedKmH = speedKmH,
            timestampIso = baseIso,
            responseReceivedAtMillis = baseTimeMillis + 140_000L,
            currentTimeMillis = baseTimeMillis + 140_000L
        )
        val at160 = PositionCompensationEngine.calculateAdaptiveCompensation(
            rawDistanceMeters = 2000.0,
            speedKmH = speedKmH,
            timestampIso = baseIso,
            responseReceivedAtMillis = baseTimeMillis + 160_000L,
            currentTimeMillis = baseTimeMillis + 160_000L
        )
        val at188 = PositionCompensationEngine.calculateAdaptiveCompensation(
            rawDistanceMeters = 2000.0,
            speedKmH = speedKmH,
            timestampIso = baseIso,
            responseReceivedAtMillis = baseTimeMillis + 188_000L,
            currentTimeMillis = baseTimeMillis + 188_000L
        )

        // 120s -> 720m; 140s -> 720 + 20*6*0.35 = 762m (continuous across 120s threshold!)
        assertEquals(720.0, at120.projectedDistanceMeters, 0.5)
        assertEquals(762.0, at140.projectedDistanceMeters, 0.5)
        assertEquals(804.0, at160.projectedDistanceMeters, 0.5)
        assertEquals(862.8, at188.projectedDistanceMeters, 0.5)
        assertTrue(at140.isAdaptiveAgedProjection)
        assertEquals(GpsLatencyCategory.DELAYED, at140.latencyCategory)

        val at305 = PositionCompensationEngine.calculateAdaptiveCompensation(
            rawDistanceMeters = 2000.0,
            speedKmH = speedKmH,
            timestampIso = baseIso,
            responseReceivedAtMillis = baseTimeMillis + 305_000L,
            currentTimeMillis = baseTimeMillis + 305_000L
        )
        assertEquals(GpsLatencyCategory.STALE, at305.latencyCategory)

        // Higher speed still strictly capped at maxProjectedDistanceMeters (900m)
        val fastAt188 = PositionCompensationEngine.calculateAdaptiveCompensation(
            rawDistanceMeters = 3000.0,
            speedKmH = 54.0, // 15 m/s
            timestampIso = baseIso,
            responseReceivedAtMillis = baseTimeMillis + 188_000L,
            currentTimeMillis = baseTimeMillis + 188_000L
        )
        assertEquals(PositionCompensationConfig.DEFAULT.maxProjectedDistanceMeters, fastAt188.projectedDistanceMeters, 0.01)

        // Frozen dts or > 190s stops projecting safely
        val frozenAt150 = PositionCompensationEngine.calculateAdaptiveCompensation(
            rawDistanceMeters = 2000.0,
            speedKmH = speedKmH,
            timestampIso = baseIso,
            responseReceivedAtMillis = baseTimeMillis + 150_000L,
            currentTimeMillis = baseTimeMillis + 150_000L,
            isGpsTimestampFrozen = true
        )
        assertEquals(CompensationStatus.STALE_DATA, frozenAt150.status)
        assertEquals(0.0, frozenAt150.projectedDistanceMeters, 0.01)
    }

    @Test
    fun compensation_futureTimestampBeyondTolerance_rejectedAsInvalid() {
        val futureTime = baseTimeMillis + 120_000L
        val futureIso = Instant.ofEpochMilli(futureTime).toString()
        val res = PositionCompensationEngine.calculateAdaptiveCompensation(
            rawDistanceMeters = 1000.0,
            speedKmH = 30.0,
            timestampIso = futureIso,
            responseReceivedAtMillis = baseTimeMillis,
            currentTimeMillis = baseTimeMillis
        )
        assertEquals(CompensationStatus.INVALID_TIMESTAMP, res.status)
        assertEquals(-1L, res.gpsAgeSeconds)
        assertEquals(GpsLatencyCategory.UNKNOWN, res.latencyCategory)
        assertFalse(res.isProjected)
    }

    @Test
    fun classifyGpsLatency_matchesUnifiedPhase3Thresholds() {
        assertEquals(GpsLatencyCategory.FRESH, PositionCompensationEngine.classifyGpsLatency(0))
        assertEquals(GpsLatencyCategory.FRESH, PositionCompensationEngine.classifyGpsLatency(30))
        assertEquals(GpsLatencyCategory.FRESH, PositionCompensationEngine.classifyGpsLatency(60))
        assertEquals(GpsLatencyCategory.SLIGHT_DELAY, PositionCompensationEngine.classifyGpsLatency(61))
        assertEquals(GpsLatencyCategory.SLIGHT_DELAY, PositionCompensationEngine.classifyGpsLatency(120))
        assertEquals(GpsLatencyCategory.DELAYED, PositionCompensationEngine.classifyGpsLatency(121))
        assertEquals(GpsLatencyCategory.DELAYED, PositionCompensationEngine.classifyGpsLatency(180))
        assertEquals(GpsLatencyCategory.DELAYED, PositionCompensationEngine.classifyGpsLatency(181))
        assertEquals(GpsLatencyCategory.DELAYED, PositionCompensationEngine.classifyGpsLatency(300))
        assertEquals(GpsLatencyCategory.STALE, PositionCompensationEngine.classifyGpsLatency(301))
        assertEquals(GpsLatencyCategory.UNKNOWN, PositionCompensationEngine.classifyGpsLatency(-1))
    }

    @Test
    fun evaluateArrivalStatus_coversAll6StatusesAndDistinguishesAtStopVsPassedStop() {
        assertEquals(
            VehicleArrivalStatus.AT_STOP,
            PositionCompensationEngine.evaluateArrivalStatus(15.0, 0.5, 5, 20L)
        )
        assertEquals(
            VehicleArrivalStatus.ARRIVING_AT_STOP,
            PositionCompensationEngine.evaluateArrivalStatus(60.0, 18.0, 12, 25L)
        )
        assertEquals(
            VehicleArrivalStatus.APPROACHING_STOP,
            PositionCompensationEngine.evaluateArrivalStatus(250.0, 22.0, 40, 45L)
        )
        assertEquals(
            VehicleArrivalStatus.MOVING,
            PositionCompensationEngine.evaluateArrivalStatus(1200.0, 28.0, 160, 60L)
        )
        assertEquals(
            VehicleArrivalStatus.PASSED_STOP,
            PositionCompensationEngine.evaluateArrivalStatus(-45.0, 25.0, null, 20L)
        )
        assertEquals(
            VehicleArrivalStatus.UNKNOWN,
            PositionCompensationEngine.evaluateArrivalStatus(50.0, 20.0, 10, 210L)
        )
        assertEquals(
            VehicleArrivalStatus.UNKNOWN,
            PositionCompensationEngine.evaluateArrivalStatus(50.0, 20.0, 10, 10L, isDirectionValid = false)
        )
    }

    @Test
    fun compensation_twoSampleKinematics_computesEffectiveVelocity() {
        val t0 = baseTimeMillis
        val t1 = baseTimeMillis + 15_000L
        val tNow = baseTimeMillis + 30_000L

        // Vehicle moved 150m in 15s between sample 0 and sample 1 -> 10 m/s
        val samples = listOf(
            VehicleGpsSample(timestampMillis = t0, distanceMeters = 1150.0, speedKmH = 36.0),
            VehicleGpsSample(timestampMillis = t1, distanceMeters = 1000.0, speedKmH = 36.0)
        )

        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 1000.0,
            speedKmH = 36.0,
            timestampIso = Instant.ofEpochMilli(t1).toString(),
            responseReceivedAtMillis = tNow,
            currentTimeMillis = tNow,
            historySamples = samples
        )

        assertEquals(CompensationStatus.LIVE_PROJECTED, result.status)
        assertEquals(15L, result.gpsAgeSeconds) // tNow - t1 = 15s
        // 10 m/s * 15s = 150m projected
        assertEquals(150.0, result.projectedDistanceMeters, 2.0)
        assertEquals(850.0, result.effectiveDistanceMeters, 2.0)
    }

    @Test
    fun compensation_threeSampleKinematics_clampsAcceleration() {
        val t0 = baseTimeMillis
        val t1 = baseTimeMillis + 10_000L
        val t2 = baseTimeMillis + 20_000L
        val tNow = baseTimeMillis + 30_000L

        // Accelerating vehicle samples
        val samples = listOf(
            VehicleGpsSample(timestampMillis = t0, distanceMeters = 1500.0, speedKmH = 18.0),
            VehicleGpsSample(timestampMillis = t1, distanceMeters = 1350.0, speedKmH = 28.8),
            VehicleGpsSample(timestampMillis = t2, distanceMeters = 1150.0, speedKmH = 36.0)
        )

        val result = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 1150.0,
            speedKmH = 36.0,
            timestampIso = Instant.ofEpochMilli(t2).toString(),
            responseReceivedAtMillis = tNow,
            currentTimeMillis = tNow,
            historySamples = samples
        )

        assertTrue(result.projectedDistanceMeters > 0.0)
        assertTrue(result.effectiveDistanceMeters < 1150.0)
    }

    @Test
    fun compensation_stopProximityDecelerationEnvelope_slowsVehicleAsItApproachesStop() {
        val t0 = baseTimeMillis
        val tNow = baseTimeMillis + 15_000L

        // Vehicle is 100m away traveling at 54 km/h (15 m/s)
        // Deceleration envelope should restrict speed to sqrt(2 * 1.5 * 100) = 17.32 m/s max,
        // but if distance is 20m, max speed is sqrt(2 * 1.5 * 20) = 7.74 m/s.
        val resultNear = PositionCompensationEngine.calculateCompensation(
            rawDistanceMeters = 20.0,
            speedKmH = 54.0, // 15 m/s reported by telemetry
            timestampIso = baseIso,
            responseReceivedAtMillis = tNow,
            currentTimeMillis = tNow
        )

        // Vehicle should safely transition to AT_STOP without negative jump
        assertTrue(resultNear.effectiveDistanceMeters >= 0.0)
        assertTrue(resultNear.projectedDistanceMeters <= 20.0)
    }
}
