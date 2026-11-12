package com.hcmcbustracker.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BusStopDto(
    @SerialName("StopId") val stopId: Int,
    @SerialName("Code") val code: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("StopType") val stopType: String? = null,
    @SerialName("Zone") val zone: String? = null,
    @SerialName("Ward") val ward: String? = null,
    @SerialName("AddressNo") val addressNo: String? = null,
    @SerialName("Street") val street: String? = null,
    @SerialName("SupportDisability") val supportDisability: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("Lng") val lng: Double? = null,
    @SerialName("Lat") val lat: Double? = null,
    @SerialName("Search") val search: String? = null,
    @SerialName("Routes") val routes: String? = null
)

/**
 * Parallel array compressed structure returned by /businfo/getstopsforautocomplete.
 */
@Serializable
data class StopAutocompleteDto(
    @SerialName("l") val l: List<String> = emptyList(),
    @SerialName("v") val v: List<String> = emptyList(),
    @SerialName("g") val g: List<Int> = emptyList()
)

@Serializable
data class RoutesThroughStopDto(
    @SerialName("RouteId") val routeId: Int,
    @SerialName("RouteNo") val routeNo: String,
    @SerialName("RouteName") val routeName: String
)
