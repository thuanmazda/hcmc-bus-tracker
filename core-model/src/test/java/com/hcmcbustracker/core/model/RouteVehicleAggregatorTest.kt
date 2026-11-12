package com.hcmcbustracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RouteVehicleAggregatorTest {

    private val baseMillis = 1762920000000L // arbitrary epoch
    private val isoT0 = Instant.ofEpochMilli(baseMillis).toString()
    private val isoTMinus30 = Instant.ofEpochMilli(baseMillis - 30_000L).toString()
    private val isoTPlus30 = Instant.ofEpochMilli(baseMillis + 30_000L).toString()

    @Test
    fun aggregateAndMerge_emptyPredictions_returnsEmpty() {
        val result = RouteVehicleAggregator.aggregateAndMerge(
            routeId = 1,
            varId = 1,
            predictions = emptyList(),
            requestStartedAtMillis = baseMillis,
            currentMillis = baseMillis
        )
        assertTrue(result.vehicles.isEmpty())
        assertEquals(0, result.totalRawRecords)
        assertEquals(0, result.duplicatesRemoved)
    }

    @Test
    fun aggregateAndMerge_deduplicatesSamePlateAcrossCheckpoints_prefersClosestStopWhenSameTimestamp() {
        val vStop18 = ArrivalVehicle("50E61577", 159, 718.3, 15.0, isoT0, 0, baseMillis)
        val vStop24 = ArrivalVehicle("50E61577", 538, 2500.5, 15.0, isoT0, 0, baseMillis)
        val vStop29 = ArrivalVehicle("50E61577", 856, 3978.4, 15.0, isoT0, 0, baseMillis)
        val otherBus = ArrivalVehicle("50E76203", 348, 1655.2, 0.0, isoT0, 0, baseMillis)

        val predictions = listOf(
            Prediction(1, "01", "Route 01", 130, "Stop 18", 1, "Cho Lon", listOf(vStop18, otherBus)),
            Prediction(1, "01", "Route 01", 133, "Stop 24", 1, "Cho Lon", listOf(vStop24)),
            Prediction(1, "01", "Route 01", 8, "Stop 29", 1, "Cho Lon", listOf(vStop29))
        )

        val result = RouteVehicleAggregator.aggregateAndMerge(
            routeId = 1,
            varId = 1,
            predictions = predictions,
            requestStartedAtMillis = baseMillis,
            currentMillis = baseMillis + 10_000L
        )

        assertEquals(2, result.vehicles.size)
        assertEquals(4, result.totalRawRecords)
        assertEquals(2, result.duplicatesRemoved)

        val bus50E61577 = result.vehicles.first { it.plateNumber == "50E61577" }
        assertEquals(130, bus50E61577.checkpointStopId) // Closest checkpoint (718.3m) selected!
        assertEquals(718.3, bus50E61577.rawDistanceMeters, 0.1)
    }

    @Test
    fun aggregateAndMerge_deduplicatesSamePlatePreferringNewerGpsTimestamp() {
        val olderAtCloseStop = ArrivalVehicle("50E61577", 100, 500.0, 20.0, isoTMinus30, 0, baseMillis)
        val newerAtFarStop = ArrivalVehicle("50E61577", 400, 1800.0, 25.0, isoT0, 0, baseMillis)

        val predictions = listOf(
            Prediction(1, "01", "Route 01", 130, "Stop 18", 1, "Cho Lon", listOf(olderAtCloseStop)),
            Prediction(1, "01", "Route 01", 133, "Stop 24", 1, "Cho Lon", listOf(newerAtFarStop))
        )

        val result = RouteVehicleAggregator.aggregateAndMerge(
            routeId = 1,
            varId = 1,
            predictions = predictions,
            requestStartedAtMillis = baseMillis,
            currentMillis = baseMillis + 5_000L
        )

        assertEquals(1, result.vehicles.size)
        assertEquals(133, result.vehicles[0].checkpointStopId)
        assertEquals(1800.0, result.vehicles[0].rawDistanceMeters, 0.1)
    }

    @Test
    fun aggregateAndMerge_filtersOutOtherRouteVariants() {
        val inboundBus = ArrivalVehicle("50E11111", 200, 900.0, 22.0, isoT0, 0, baseMillis)
        val outboundBus = ArrivalVehicle("50E22222", 250, 1100.0, 28.0, isoT0, 0, baseMillis)

        val predictions = listOf(
            Prediction(1, "01", "Route 01", 130, "Stop Outbound", 1, "Cho Lon", listOf(outboundBus)),
            Prediction(1, "01", "Route 01", 130, "Stop Inbound", 2, "Ga Ba Son", listOf(inboundBus))
        )

        val outboundOnly = RouteVehicleAggregator.aggregateAndMerge(
            routeId = 1,
            varId = 1,
            predictions = predictions,
            requestStartedAtMillis = baseMillis,
            currentMillis = baseMillis
        )

        assertEquals(1, outboundOnly.vehicles.size)
        assertEquals("50E22222", outboundOnly.vehicles[0].plateNumber)
    }

    @Test
    fun mergeMonotonically_rejectsOutOfOrderOlderResponse() {
        val current = RouteVehicleTelemetry(
            routeId = 1,
            varId = 1,
            plateNumber = "50E61577",
            formattedPlate = "50E-615.77",
            checkpointStopId = 130,
            checkpointStopName = "Stop 18",
            rawDistanceMeters = 500.0,
            speedKmH = 30.0,
            etaSeconds = 80,
            gpsTimestampIso = isoT0,
            gpsTimestampMillis = baseMillis,
            requestStartedAtMillis = baseMillis,
            responseReceivedAtMillis = baseMillis,
            predictionReceivedAtMillis = baseMillis,
            statusCode = 0
        )

        val delayedOlder = current.copy(
            rawDistanceMeters = 850.0,
            gpsTimestampIso = isoTMinus30,
            gpsTimestampMillis = baseMillis - 30_000L
        )

        val merged = RouteVehicleAggregator.mergeMonotonically(current, delayedOlder, baseMillis + 5000L)
        assertEquals(500.0, merged.rawDistanceMeters, 0.1)
        assertEquals(baseMillis, merged.gpsTimestampMillis)
    }

    @Test
    fun mergeMonotonically_detectsUnchangedGpsTimestampAcrossConsecutivePolls() {
        val firstPoll = RouteVehicleTelemetry(
            routeId = 1,
            varId = 1,
            plateNumber = "50E61577",
            formattedPlate = "50E-615.77",
            checkpointStopId = 130,
            checkpointStopName = "Stop 18",
            rawDistanceMeters = 500.0,
            speedKmH = 20.0,
            etaSeconds = 80,
            gpsTimestampIso = isoT0,
            gpsTimestampMillis = baseMillis,
            requestStartedAtMillis = baseMillis,
            responseReceivedAtMillis = baseMillis,
            predictionReceivedAtMillis = baseMillis,
            statusCode = 0
        )

        // Second poll 15s later has exact same dts
        val secondPollSameDts = firstPoll.copy(
            requestStartedAtMillis = baseMillis + 15_000L,
            responseReceivedAtMillis = baseMillis + 15_200L,
            predictionReceivedAtMillis = baseMillis + 15_250L
        )

        val merged1 = RouteVehicleAggregator.mergeMonotonically(firstPoll, secondPollSameDts, baseMillis + 15_250L)
        assertTrue(merged1.isGpsTimestampUnchanged)
        assertEquals(1, merged1.consecutiveUnchangedPolls)
        assertEquals(GpsFreshnessState.UNCHANGED, merged1.freshnessState)

        // Third poll arrives with newer dts (+30s) -> resets unchanged count
        val newerPoll = firstPoll.copy(
            gpsTimestampIso = isoTPlus30,
            gpsTimestampMillis = baseMillis + 30_000L,
            rawDistanceMeters = 300.0
        )
        val merged2 = RouteVehicleAggregator.mergeMonotonically(merged1, newerPoll, baseMillis + 35_000L)
        assertFalse(merged2.isGpsTimestampUnchanged)
        assertEquals(0, merged2.consecutiveUnchangedPolls)
    }

    @Test
    fun aggregateAndMerge_recordsFiveTimestampsLatenciesAndRejectsFutureDts() {
        val futureIso = Instant.ofEpochMilli(baseMillis + 180_000L).toString()
        val validBus = ArrivalVehicle(
            plateNumber = "50E61577",
            etaSeconds = 120,
            distanceMeters = 600.0,
            speedKmH = 22.0,
            timestampIso = isoT0,
            status = 0,
            responseReceivedTimeMillis = baseMillis + 20_250L,
            requestStartedAtMillis = baseMillis + 20_000L,
            predictionReceivedAtMillis = baseMillis + 20_300L
        )
        val futureInvalidBus = ArrivalVehicle(
            plateNumber = "50E99999",
            etaSeconds = 90,
            distanceMeters = 400.0,
            speedKmH = 18.0,
            timestampIso = futureIso,
            status = 0,
            responseReceivedTimeMillis = baseMillis + 20_250L,
            requestStartedAtMillis = baseMillis + 20_000L,
            predictionReceivedAtMillis = baseMillis + 20_300L
        )

        val res = RouteVehicleAggregator.aggregateAndMerge(
            routeId = 1,
            varId = 1,
            predictions = listOf(Prediction(1, "01", "R1", 130, "Checkpoint", 1, "Cho Lon", listOf(validBus, futureInvalidBus))),
            requestStartedAtMillis = baseMillis + 20_000L,
            predictionReceivedAtMillis = baseMillis + 20_300L,
            currentMillis = baseMillis + 20_350L
        )

        val vValid = res.vehicles.first { it.plateNumber == "50E61577" }
        assertEquals(130, vValid.predictionTargetStopId)
        assertEquals(250L, vValid.networkLatencyMillis)
        assertEquals(100L, vValid.dataProcessingDelayMillis)
        assertEquals(GpsLatencyCategory.FRESH, vValid.latencyCategory)

        val vFuture = res.vehicles.first { it.plateNumber == "50E99999" }
        assertEquals(GpsFreshnessState.UNKNOWN, vFuture.freshnessState)
        assertEquals(GpsLatencyCategory.UNKNOWN, vFuture.latencyCategory)
        assertEquals(-1L, vFuture.computeGpsAgeSeconds(baseMillis + 20_350L))
    }
}
