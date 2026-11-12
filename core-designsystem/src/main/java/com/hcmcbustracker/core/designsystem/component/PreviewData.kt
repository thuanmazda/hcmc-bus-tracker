package com.hcmcbustracker.core.designsystem.component

import com.hcmcbustracker.core.model.ArrivalVehicle
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.Prediction
import com.hcmcbustracker.core.model.Route

/**
 * Design Preview Data strictly designated for Jetpack Compose @Preview tooling.
 *
 * NOTE: This mock dataset is isolated exclusively for visual previewing and design inspection.
 * It is NEVER used in production repositories, ViewModels, or runtime pipelines.
 */
object DesignPreviewData {

    val sampleRoute = Route(
        id = 1,
        routeNo = "01",
        name = "Bến Thành - Bến xe Chợ Lớn",
        color = "#1E88E5",
        type = "Phổ thông có trợ giá",
        distanceMeters = 8500.0,
        headway = "10-15 phút",
        operationTime = "05:00 - 20:30",
        outBoundDesc = "Bến xe buýt Sài Gòn - Lê Lai - Phạm Hồng Thái - Chợ Lớn",
        inBoundDesc = "Bến xe Chợ Lớn - Hồng Bàng - Nguyễn Trãi - Bến xe buýt Sài Gòn"
    )

    val sampleRoute150 = Route(
        id = 150,
        routeNo = "150",
        name = "Bến xe Chợ Lớn - Ngã 3 Tân Vạn",
        color = "#00897B",
        type = "Phổ thông có trợ giá",
        distanceMeters = 32000.0,
        headway = "7-12 phút",
        operationTime = "04:30 - 21:00",
        outBoundDesc = "Bến xe Chợ Lớn - Hùng Vương - Xa Lộ Hà Nội - Tân Vạn",
        inBoundDesc = "Ngã 3 Tân Vạn - Xa Lộ Hà Nội - Hùng Vương - Bến xe Chợ Lớn"
    )

    val sampleStop = BusStop(
        id = 142,
        code = "Q1 001",
        name = "Bến xe buýt Sài Gòn",
        stopType = "Bến bãi",
        zone = "Quận 1",
        ward = "Phường Phạm Ngũ Lão",
        addressNo = "Công viên 23/9",
        street = "Lê Lai",
        supportDisability = true,
        status = "Active",
        lat = 10.7684,
        lng = 106.6942,
        searchKeywords = "ben xe buyt sai gon cong vien 23 9 le lai quan 1",
        routes = listOf("01", "03", "04", "18", "19", "20", "36", "53", "56", "109")
    )

    val sampleStopChoLon = BusStop(
        id = 205,
        code = "Q5 018",
        name = "Bến xe Chợ Lớn",
        stopType = "Bến bãi",
        zone = "Quận 5",
        ward = "Phường 14",
        addressNo = "Hồng Bàng",
        street = "Hồng Bàng",
        supportDisability = true,
        status = "Active",
        lat = 10.7533,
        lng = 106.6544,
        searchKeywords = "ben xe cho lon hong bang quan 5",
        routes = listOf("01", "06", "56", "68", "150")
    )

    val sampleApproachingVehicle = ArrivalVehicle(
        plateNumber = "51B-123.45",
        etaSeconds = 45,
        distanceMeters = 240.0,
        speedKmH = 22.5,
        timestampIso = "2026-11-10T08:30:00Z",
        status = 1
    )

    val sampleIncomingVehicle = ArrivalVehicle(
        plateNumber = "51B-678.90",
        etaSeconds = 480,
        distanceMeters = 2100.0,
        speedKmH = 28.0,
        timestampIso = "2026-11-10T08:29:40Z",
        status = 1
    )

    val samplePrediction = Prediction(
        routeId = 1,
        routeNo = "01",
        routeName = "Bến Thành - Bến xe Chợ Lớn",
        stopId = 142,
        stopName = "Bến xe buýt Sài Gòn",
        varId = 101,
        destinationName = "Bến xe Chợ Lớn",
        vehicles = listOf(sampleApproachingVehicle, sampleIncomingVehicle)
    )
}
