package com.hcmcbustracker.feature.map

import com.hcmcbustracker.core.model.CompensationStatus
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTrackingSheetTest {

    private fun createTrackedVehicleState(
        routeId: Int = 1,
        routeNo: String = "01",
        varId: Int = 101,
        stopId: Int = 142,
        plateNumber: String = "50E-123.45",
        speedKmH: Double = 25.0,
        rawDistanceMeters: Double = 500.0,
        effectiveDistanceMeters: Double = 500.0,
        projectedDistanceMeters: Double = 500.0,
        lat: Double = 10.7684,
        lng: Double = 106.6942,
        bearing: Float = 90f,
        timestampIso: String? = "2026-11-15T09:45:12.000Z",
        requestStartedAtMillis: Long = 1000L,
        responseReceivedAtMillis: Long = 1350L,
        renderedAtMillis: Long = 1420L,
        latencyCategory: GpsLatencyCategory = GpsLatencyCategory.FRESH,
        arrivalStatus: VehicleArrivalStatus = VehicleArrivalStatus.MOVING
    ) = TrackedVehicleState(
        routeId = routeId,
        routeNo = routeNo,
        varId = varId,
        stopId = stopId,
        plateNumber = plateNumber,
        speedKmH = speedKmH,
        rawDistanceMeters = rawDistanceMeters,
        effectiveDistanceMeters = effectiveDistanceMeters,
        projectedDistanceMeters = projectedDistanceMeters,
        lat = lat,
        lng = lng,
        bearing = bearing,
        timestampIso = timestampIso,
        requestStartedAtMillis = requestStartedAtMillis,
        responseReceivedAtMillis = responseReceivedAtMillis,
        renderedAtMillis = renderedAtMillis,
        latencyCategory = latencyCategory,
        arrivalStatus = arrivalStatus,
        compensationStatus = CompensationStatus.LIVE_PROJECTED
    )

    @Test
    fun `formatMetricDistance formats under 1000m as integer meters`() {
        assertEquals("0m", formatMetricDistance(0.0))
        assertEquals("45m", formatMetricDistance(45.2))
        assertEquals("350m", formatMetricDistance(350.0))
        assertEquals("999m", formatMetricDistance(999.4))
    }

    @Test
    fun `formatMetricDistance formats 1000m and above as kilometers with 2 decimal places`() {
        assertEquals("1.00 km", formatMetricDistance(1000.0))
        assertEquals("1.50 km", formatMetricDistance(1500.0))
        assertEquals("2.45 km", formatMetricDistance(2450.0))
        assertEquals("10.82 km", formatMetricDistance(10823.0))
    }

    @Test
    fun `formatMetricEta handles null and negative values`() {
        assertEquals("--", formatMetricEta(null))
        assertEquals("--", formatMetricEta(-1))
        assertEquals("--", formatMetricEta(-100))
    }

    @Test
    fun `formatMetricEta formats under 60 seconds as less than 1 minute`() {
        assertEquals("< 1 phút", formatMetricEta(0))
        assertEquals("< 1 phút", formatMetricEta(25))
        assertEquals("< 1 phút", formatMetricEta(59))
    }

    @Test
    fun `formatMetricEta formats exact minutes and minutes plus seconds`() {
        assertEquals("1 phút", formatMetricEta(60))
        assertEquals("1 p 30 s", formatMetricEta(90))
        assertEquals("5 phút", formatMetricEta(300))
        assertEquals("5 p 45 s", formatMetricEta(345))
        assertEquals("12 p 10 s", formatMetricEta(730))
    }

    @Test
    fun `TrackedVehicleState formattedGpsTime extracts time cleanly from ISO string`() {
        val stateWithT = createTrackedVehicleState(timestampIso = "2026-11-15T09:45:12.000Z")
        assertEquals("09:45:12", stateWithT.formattedGpsTime)

        val stateWithoutT = createTrackedVehicleState(timestampIso = "09:45:12")
        assertEquals("09:45:12", stateWithoutT.formattedGpsTime)

        val stateNullIso = createTrackedVehicleState(timestampIso = null)
        assertEquals("--", stateNullIso.formattedGpsTime)
    }

    @Test
    fun `TrackedVehicleState telemetry delay metrics compute correctly`() {
        val reqStart = 1000L
        val respRecv = 1350L
        val renderAt = 1420L

        val state = createTrackedVehicleState(
            requestStartedAtMillis = reqStart,
            responseReceivedAtMillis = respRecv,
            renderedAtMillis = renderAt,
            effectiveDistanceMeters = 750.0
        )

        assertEquals(750.0, state.distanceMeters, 0.001)
        assertEquals(350L, state.networkLatencyMillis)
        assertEquals(70L, state.dataProcessingDelayMillis)
    }

    @Test
    fun `TrackedVehicleState clamp negative latency to zero`() {
        val state = createTrackedVehicleState(
            requestStartedAtMillis = 2000L,
            responseReceivedAtMillis = 1500L, // Clock drift / anomaly
            renderedAtMillis = 1000L
        )

        assertEquals(0L, state.networkLatencyMillis)
        assertEquals(0L, state.dataProcessingDelayMillis)
    }

    @Test
    fun `GpsLatencyCategory mappings are truthful and defined`() {
        assertEquals("Mới", GpsLatencyCategory.FRESH.labelVi)
        assertEquals("Có độ trễ nhẹ", GpsLatencyCategory.SLIGHT_DELAY.labelVi)
        assertEquals("Có độ trễ", GpsLatencyCategory.DELAYED.labelVi)
        assertEquals("Dữ liệu cũ", GpsLatencyCategory.STALE.labelVi)
        assertEquals("Không xác định", GpsLatencyCategory.UNKNOWN.labelVi)
    }

    @Test
    fun `VehicleArrivalStatus labels in Vietnamese reflect movement progression truthfully`() {
        assertEquals("Đang di chuyển", VehicleArrivalStatus.MOVING.labelVi)
        assertEquals("Sắp đến trạm", VehicleArrivalStatus.APPROACHING_STOP.labelVi)
        assertEquals("Đang đến trạm", VehicleArrivalStatus.ARRIVING_AT_STOP.labelVi)
        assertEquals("Đang dừng tại trạm", VehicleArrivalStatus.AT_STOP.labelVi)
        assertEquals("Đã đi qua trạm", VehicleArrivalStatus.PASSED_STOP.labelVi)
        assertEquals("Không đủ dữ liệu xác định", VehicleArrivalStatus.UNKNOWN.labelVi)
    }

    @Test
    fun `PositionSource descriptions are truthful without hiding interpolation`() {
        val sources = PositionSource.entries
        assertTrue(sources.contains(PositionSource.INTERPOLATED_ON_ROUTE))
        assertTrue(sources.contains(PositionSource.LATENCY_COMPENSATED))
        assertTrue(sources.contains(PositionSource.HELD_AT_LAST_VALID_GPS))
        assertTrue(sources.contains(PositionSource.UNKNOWN))
    }
}
