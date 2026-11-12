package com.hcmcbustracker.core.network.mapper

import com.hcmcbustracker.core.model.ArrivalVehicle
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.LatLngPoint
import com.hcmcbustracker.core.model.Prediction
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.RouteCombo
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.core.model.RouteVariant
import com.hcmcbustracker.core.model.Timetable
import com.hcmcbustracker.core.model.Trip
import com.hcmcbustracker.core.network.dto.ArrivalVehicleDto
import com.hcmcbustracker.core.network.dto.BusStopDto
import com.hcmcbustracker.core.network.dto.PredictionDto
import com.hcmcbustracker.core.network.dto.RouteComboDto
import com.hcmcbustracker.core.network.dto.RouteDetailDto
import com.hcmcbustracker.core.network.dto.RoutePathDto
import com.hcmcbustracker.core.network.dto.RouteVarDto
import com.hcmcbustracker.core.network.dto.TimetableDto
import com.hcmcbustracker.core.network.dto.TripDto

fun RouteComboDto.toDomain(): RouteCombo {
    val id = value.toIntOrNull() ?: 0
    // lable format: "[01] Ga Ba Son - Bến xe buýt Chợ Lớn"
    var routeNo = ""
    var name = lable
    val regex = Regex("\\[(.*?)\\]\\s*(.*)")
    val match = regex.find(lable)
    if (match != null) {
        routeNo = match.groupValues[1]
        name = match.groupValues[2]
    }
    return RouteCombo(
        id = id,
        routeNo = routeNo,
        name = name
    )
}

fun RouteDetailDto.toDomain(): Route {
    return Route(
        id = routeId,
        routeNo = routeNo.trim(),
        name = routeName.trim(),
        color = color?.trim(),
        type = type?.trim(),
        distanceMeters = distance,
        headway = headway?.trim(),
        operationTime = operationTime?.trim(),
        orgs = orgs?.replace("<br/>", " ")?.replace("<br>", " ")?.trim(),
        tickets = tickets?.replace("<br/>", "\n")?.replace("&nbsp", " ")?.trim(),
        outBoundDesc = outBoundDescription?.replace("¿", "")?.trim(),
        inBoundDesc = inBoundDescription?.replace("¿", "")?.trim(),
        totalTrips = totalTrip?.replace("\\[TPD\\]", "chuyến/ngày")?.trim()
    )
}

fun RouteVarDto.toDomain(): RouteVariant {
    return RouteVariant(
        id = routeVarId,
        routeId = routeId,
        name = routeVarName.trim(),
        shortName = routeVarShortName.trim(),
        routeNo = routeNo.trim(),
        startStop = startStop?.trim().orEmpty(),
        endStop = endStop?.trim().orEmpty(),
        distanceMeters = distance ?: 0.0,
        isOutbound = outbound ?: true,
        runningTimeMinutes = runningTime ?: 0
    )
}

fun BusStopDto.toDomain(): BusStop {
    val parsedRoutes = routes?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    return BusStop(
        id = stopId,
        code = code?.trim().orEmpty(),
        name = name?.trim().orEmpty(),
        stopType = stopType?.trim().orEmpty(),
        zone = zone?.trim().orEmpty(),
        ward = ward?.trim(),
        addressNo = addressNo?.trim().orEmpty(),
        street = street?.trim().orEmpty(),
        supportDisability = supportDisability?.contains("Có", ignoreCase = true) == true,
        status = status?.trim().orEmpty(),
        lat = lat ?: 0.0,
        lng = lng ?: 0.0,
        searchKeywords = search?.trim().orEmpty(),
        routes = parsedRoutes
    )
}

fun RoutePathDto.toDomain(routeId: Int, varId: Int): RoutePath {
    val points = mutableListOf<LatLngPoint>()
    val count = minOf(lat.size, lng.size)
    for (i in 0 until count) {
        points.add(LatLngPoint(lat = lat[i], lng = lng[i]))
    }
    return RoutePath(
        routeId = routeId,
        varId = varId,
        points = points
    )
}

fun PredictionDto.toDomain(
    receivedTimeMillis: Long = System.currentTimeMillis(),
    requestStartedTimeMillis: Long = receivedTimeMillis,
    predictionReceivedTimeMillis: Long = System.currentTimeMillis()
): Prediction {
    return Prediction(
        routeId = routeId,
        routeNo = routeNo?.trim().orEmpty(),
        routeName = routeName?.trim().orEmpty(),
        stopId = stopId ?: 0,
        stopName = stopName?.trim().orEmpty(),
        varId = varId ?: 0,
        destinationName = destinationName?.trim().orEmpty(),
        vehicles = arrivals?.map {
            it.toDomain(
                receivedTimeMillis = receivedTimeMillis,
                requestStartedTimeMillis = requestStartedTimeMillis,
                predictionReceivedTimeMillis = predictionReceivedTimeMillis
            )
        } ?: emptyList()
    )
}

fun ArrivalVehicleDto.toDomain(
    receivedTimeMillis: Long = System.currentTimeMillis(),
    requestStartedTimeMillis: Long = receivedTimeMillis,
    predictionReceivedTimeMillis: Long = System.currentTimeMillis()
): ArrivalVehicle {
    return ArrivalVehicle(
        plateNumber = plateNumber?.trim().orEmpty(),
        etaSeconds = etaSeconds ?: 0,
        distanceMeters = distanceMeters ?: 0.0,
        speedKmH = speedKmH ?: 0.0,
        timestampIso = timestampIso?.trim().orEmpty(),
        status = status ?: 0,
        responseReceivedTimeMillis = receivedTimeMillis,
        requestStartedAtMillis = requestStartedTimeMillis,
        predictionReceivedAtMillis = predictionReceivedTimeMillis
    )
}

fun TimetableDto.toDomain(): Timetable {
    return Timetable(
        id = timetableId,
        routeId = routeId,
        varId = routeVarId ?: 0,
        varShortName = routeVarShortName?.trim().orEmpty(),
        runningTime = runningTime?.trim().orEmpty(),
        headway = headway?.trim().orEmpty(),
        totalTrip = totalTrip ?: 0,
        operationTime = operationTime?.trim().orEmpty(),
        applyDates = applyDates?.trim().orEmpty()
    )
}

fun TripDto.toDomain(): Trip {
    return Trip(
        id = tripId,
        routeId = routeId,
        timetableId = timetableId,
        startTime = startTime.trim(),
        endTime = endTime.trim()
    )
}
