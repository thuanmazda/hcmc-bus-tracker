package com.hcmcbustracker.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a direction (outbound or inbound) of a bus route.
 */
@Serializable
data class RouteVariant(
    val id: Int,
    val routeId: Int,
    val name: String,
    val shortName: String,
    val routeNo: String,
    val startStop: String,
    val endStop: String,
    val distanceMeters: Double,
    val isOutbound: Boolean,
    val runningTimeMinutes: Int
)
