package com.hcmcbustracker.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoutePathDto(
    @SerialName("lat") val lat: List<Double> = emptyList(),
    @SerialName("lng") val lng: List<Double> = emptyList()
)
