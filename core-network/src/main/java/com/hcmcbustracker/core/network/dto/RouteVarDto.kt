package com.hcmcbustracker.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RouteVarDto(
    @SerialName("RouteId") val routeId: Int,
    @SerialName("RouteVarId") val routeVarId: Int,
    @SerialName("RouteVarName") val routeVarName: String,
    @SerialName("RouteVarShortName") val routeVarShortName: String,
    @SerialName("RouteNo") val routeNo: String,
    @SerialName("StartStop") val startStop: String? = null,
    @SerialName("EndStop") val endStop: String? = null,
    @SerialName("Distance") val distance: Double? = null,
    @SerialName("Outbound") val outbound: Boolean? = null,
    @SerialName("RunningTime") val runningTime: Int? = null
)
