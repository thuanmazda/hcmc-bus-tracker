package com.hcmcbustracker.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TimetableDto(
    @SerialName("RouteId") val routeId: Int,
    @SerialName("RouteVarId") val routeVarId: Int? = null,
    @SerialName("TimeTableId") val timetableId: Int,
    @SerialName("RouteVarShortName") val routeVarShortName: String? = null,
    @SerialName("StartDate") val startDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("StartStop") val startStop: String? = null,
    @SerialName("EndStop") val endStop: String? = null,
    @SerialName("IsCurrent") val isCurrent: Boolean? = null,
    @SerialName("RunningTime") val runningTime: String? = null,
    @SerialName("Headway") val headway: String? = null,
    @SerialName("TotalTrip") val totalTrip: Int? = null,
    @SerialName("OperationTime") val operationTime: String? = null,
    @SerialName("ApplyDates") val applyDates: String? = null
)

@Serializable
data class TripDto(
    @SerialName("RouteId") val routeId: Int,
    @SerialName("TripId") val tripId: Int,
    @SerialName("TimeTableId") val timetableId: Int,
    @SerialName("StartTime") val startTime: String,
    @SerialName("EndTime") val endTime: String
)
