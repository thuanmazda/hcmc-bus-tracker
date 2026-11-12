package com.hcmcbustracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routes",
    indices = [
        Index(value = ["routeNo"]),
        Index(value = ["normalizedSearch"])
    ]
)
data class RouteEntity(
    @PrimaryKey val id: Int,
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
    val totalTrips: String? = null,
    val normalizedSearch: String
)

@Entity(
    tableName = "route_variants",
    primaryKeys = ["routeId", "id"],
    indices = [
        Index(value = ["routeId"]),
        Index(value = ["routeNo"])
    ]
)
data class RouteVariantEntity(
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
