package com.hcmcbustracker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Timetable(
    val id: Int,
    val routeId: Int,
    val varId: Int,
    val varShortName: String,
    val runningTime: String,
    val headway: String,
    val totalTrip: Int,
    val operationTime: String,
    val applyDates: String
)

@Serializable
data class Trip(
    val id: Int,
    val routeId: Int,
    val timetableId: Int,
    val startTime: String,
    val endTime: String
)
