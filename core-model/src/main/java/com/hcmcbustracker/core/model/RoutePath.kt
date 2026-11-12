package com.hcmcbustracker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LatLngPoint(
    val lat: Double,
    val lng: Double
)

/**
 * Domain model representing the sequence of geographic coordinates forming the route polyline.
 */
@Serializable
data class RoutePath(
    val routeId: Int,
    val varId: Int,
    val points: List<LatLngPoint>
) {
    val size: Int get() = points.size
    val isEmpty: Boolean get() = points.isEmpty()
}
