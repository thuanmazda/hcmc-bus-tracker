package com.hcmcbustracker.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a bus route in HCMC.
 */
@Serializable
data class Route(
    val id: Int,
    val routeNo: String,
    val name: String,
    val color: String? = null,
    val type: String? = null,
    val distanceMeters: Double? = null,
    val headway: String? = null,
    val operationTime: String? = null,
    val orgs: String? = null,
    val tickets: String? = null,
    val outBoundDesc: String? = null,
    val inBoundDesc: String? = null,
    val totalTrips: String? = null
)

/**
 * Simplified route item used for quick dropdown/combo selection and search.
 */
@Serializable
data class RouteCombo(
    val id: Int,
    val routeNo: String,
    val name: String
)
