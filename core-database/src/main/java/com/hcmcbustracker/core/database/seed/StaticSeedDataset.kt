package com.hcmcbustracker.core.database.seed

import kotlinx.serialization.Serializable

@Serializable
data class StaticSeedDataset(
    val datasetVersion: String,
    val datasetCreatedAt: Long,
    val source: String,
    val totalRoutes: Int,
    val totalVariants: Int,
    val totalStops: Int,
    val totalPolylines: Int,
    val routes: List<SeedRoute>,
    val variants: List<SeedVariant>,
    val stops: List<SeedStop>,
    val crossRefs: List<SeedCrossRef>,
    val paths: List<SeedPath>
)

@Serializable
data class SeedRoute(
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

@Serializable
data class SeedVariant(
    val id: Int,
    val routeId: Int,
    val name: String = "",
    val shortName: String = "",
    val routeNo: String = "",
    val startStop: String = "",
    val endStop: String = "",
    val distanceMeters: Double = 0.0,
    val isOutbound: Boolean = true,
    val runningTimeMinutes: Int = 0
)

@Serializable
data class SeedStop(
    val id: Int,
    val code: String = "",
    val name: String = "",
    val stopType: String = "",
    val zone: String = "",
    val ward: String? = null,
    val addressNo: String = "",
    val street: String = "",
    val supportDisability: Boolean = false,
    val status: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val searchKeywords: String = "",
    val routes: List<String> = emptyList()
)

@Serializable
data class SeedCrossRef(
    val routeId: Int,
    val varId: Int,
    val stopId: Int,
    val orderIndex: Int
)

@Serializable
data class SeedPath(
    val routeId: Int,
    val varId: Int,
    val lats: List<Double>,
    val lngs: List<Double>
)
