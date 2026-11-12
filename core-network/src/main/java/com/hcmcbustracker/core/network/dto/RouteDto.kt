package com.hcmcbustracker.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RouteComboDto(
    @SerialName("value") val value: String,
    @SerialName("lable") val lable: String,
    @SerialName("key") val key: Int = 0
)

@Serializable
data class RouteDetailDto(
    @SerialName("RouteId") val routeId: Int,
    @SerialName("RouteNo") val routeNo: String,
    @SerialName("RouteName") val routeName: String,
    @SerialName("Color") val color: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Distance") val distance: Double? = null,
    @SerialName("Orgs") val orgs: String? = null,
    @SerialName("TimeOfTrip") val timeOfTrip: String? = null,
    @SerialName("Headway") val headway: String? = null,
    @SerialName("OperationTime") val operationTime: String? = null,
    @SerialName("NumOfSeats") val numOfSeats: String? = null,
    @SerialName("OutBoundName") val outBoundName: String? = null,
    @SerialName("InBoundName") val inBoundName: String? = null,
    @SerialName("OutBoundDescription") val outBoundDescription: String? = null,
    @SerialName("InBoundDescription") val inBoundDescription: String? = null,
    @SerialName("TotalTrip") val totalTrip: String? = null,
    @SerialName("Tickets") val tickets: String? = null
)
