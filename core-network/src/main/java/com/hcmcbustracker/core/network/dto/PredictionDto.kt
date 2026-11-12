package com.hcmcbustracker.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PredictionDto(
    @SerialName("r") val routeId: Int,
    @SerialName("rN") val routeName: String? = null,
    @SerialName("rNo") val routeNo: String? = null,
    @SerialName("s") val stopId: Int? = null,
    @SerialName("sN") val stopName: String? = null,
    @SerialName("v") val varId: Int? = null,
    @SerialName("vN") val destinationName: String? = null,
    @SerialName("arrs") val arrivals: List<ArrivalVehicleDto>? = null
)

@Serializable
data class ArrivalVehicleDto(
    @SerialName("v") val plateNumber: String? = null,
    @SerialName("t") val etaSeconds: Int? = null,
    @SerialName("d") val distanceMeters: Double? = null,
    @SerialName("s") val speedKmH: Double? = null,
    @SerialName("dts") val timestampIso: String? = null,
    @SerialName("sts") val status: Int? = null
)
