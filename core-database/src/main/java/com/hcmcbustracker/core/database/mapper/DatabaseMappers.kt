package com.hcmcbustracker.core.database.mapper

import com.hcmcbustracker.core.database.entity.BusStopEntity
import com.hcmcbustracker.core.database.entity.RouteEntity
import com.hcmcbustracker.core.database.entity.RoutePathEntity
import com.hcmcbustracker.core.database.entity.RouteVariantEntity
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.LatLngPoint
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.core.model.RouteVariant
import com.hcmcbustracker.core.model.VietnameseNormalizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun RouteEntity.toDomain(): Route {
    return Route(
        id = id,
        routeNo = routeNo,
        name = name,
        color = color,
        type = type,
        distanceMeters = distanceMeters,
        headway = headway,
        operationTime = operationTime,
        orgs = orgs,
        tickets = tickets,
        outBoundDesc = outBoundDesc,
        inBoundDesc = inBoundDesc,
        totalTrips = totalTrips
    )
}

fun Route.toEntity(): RouteEntity {
    val searchContent = "$routeNo $name ${VietnameseNormalizer.removeAccents(name)}"
    return RouteEntity(
        id = id,
        routeNo = routeNo,
        name = name,
        color = color,
        type = type,
        distanceMeters = distanceMeters,
        headway = headway,
        operationTime = operationTime,
        orgs = orgs,
        tickets = tickets,
        outBoundDesc = outBoundDesc,
        inBoundDesc = inBoundDesc,
        totalTrips = totalTrips,
        normalizedSearch = VietnameseNormalizer.removeAccents(searchContent)
    )
}

fun RouteVariantEntity.toDomain(): RouteVariant {
    return RouteVariant(
        id = id,
        routeId = routeId,
        name = name,
        shortName = shortName,
        routeNo = routeNo,
        startStop = startStop,
        endStop = endStop,
        distanceMeters = distanceMeters,
        isOutbound = isOutbound,
        runningTimeMinutes = runningTimeMinutes
    )
}

fun RouteVariant.toEntity(): RouteVariantEntity {
    return RouteVariantEntity(
        id = id,
        routeId = routeId,
        name = name,
        shortName = shortName,
        routeNo = routeNo,
        startStop = startStop,
        endStop = endStop,
        distanceMeters = distanceMeters,
        isOutbound = isOutbound,
        runningTimeMinutes = runningTimeMinutes
    )
}

fun BusStopEntity.toDomain(): BusStop {
    val parsedRoutes = routesRaw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    return BusStop(
        id = id,
        code = code,
        name = name,
        stopType = stopType,
        zone = zone,
        ward = ward,
        addressNo = addressNo,
        street = street,
        supportDisability = supportDisability,
        status = status,
        lat = lat,
        lng = lng,
        searchKeywords = searchKeywords,
        routes = parsedRoutes
    )
}

fun BusStop.toEntity(): BusStopEntity {
    val searchContent = "$code $name $street $zone ${VietnameseNormalizer.removeAccents(name)} ${VietnameseNormalizer.removeAccents(street)}"
    return BusStopEntity(
        id = id,
        code = code,
        name = name,
        stopType = stopType,
        zone = zone,
        ward = ward,
        addressNo = addressNo,
        street = street,
        supportDisability = supportDisability,
        status = status,
        lat = lat,
        lng = lng,
        searchKeywords = searchKeywords,
        routesRaw = routes.joinToString(", "),
        normalizedSearch = VietnameseNormalizer.removeAccents(searchContent)
    )
}

fun RoutePathEntity.toDomain(json: Json = Json): RoutePath {
    val lats = try { json.decodeFromString<List<Double>>(latsJson) } catch (e: Exception) { emptyList() }
    val lngs = try { json.decodeFromString<List<Double>>(lngsJson) } catch (e: Exception) { emptyList() }
    val points = mutableListOf<LatLngPoint>()
    val count = minOf(lats.size, lngs.size)
    for (i in 0 until count) {
        points.add(LatLngPoint(lat = lats[i], lng = lngs[i]))
    }
    return RoutePath(
        routeId = routeId,
        varId = varId,
        points = points
    )
}

fun RoutePath.toEntity(json: Json = Json): RoutePathEntity {
    val lats = points.map { it.lat }
    val lngs = points.map { it.lng }
    return RoutePathEntity(
        compositeKey = "${routeId}_${varId}",
        routeId = routeId,
        varId = varId,
        latsJson = json.encodeToString(lats),
        lngsJson = json.encodeToString(lngs),
        pointCount = points.size
    )
}
