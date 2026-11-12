package com.hcmcbustracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cross-reference table mapping which bus stops belong to a specific route and variant,
 * ordered sequentially from first stop to last stop.
 */
@Entity(
    tableName = "route_stop_cross_reference",
    indices = [
        Index(value = ["routeId", "varId"]),
        Index(value = ["stopId"]),
        Index(value = ["routeId", "varId", "stopId"], unique = true)
    ]
)
data class RouteStopCrossReference(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Int,
    val varId: Int,
    val stopId: Int,
    val orderIndex: Int
)

/**
 * Entity caching geographic polyline paths for route variants.
 */
@Entity(tableName = "route_paths")
data class RoutePathEntity(
    @PrimaryKey val compositeKey: String, // "${routeId}_${varId}"
    val routeId: Int,
    val varId: Int,
    val latsJson: String,
    val lngsJson: String,
    val pointCount: Int,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
)

/**
 * Entity storing user's bookmarked / favorite bus stops.
 */
@Entity(tableName = "favorite_stops")
data class FavoriteStopEntity(
    @PrimaryKey val stopId: Int,
    val addedAtTimestamp: Long = System.currentTimeMillis(),
    val customNote: String? = null
)

/**
 * Entity storing user's bookmarked / favorite bus routes.
 */
@Entity(tableName = "favorite_routes")
data class FavoriteRouteEntity(
    @PrimaryKey val routeId: Int,
    val addedAtTimestamp: Long = System.currentTimeMillis()
)

/**
 * Entity tracking synchronization history and data freshness.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val syncType: String, // e.g. "ROUTES", "STOPS"
    val lastSyncTimestamp: Long,
    val itemCount: Int,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * Authoritative metadata for the validated offline static transit dataset.
 */
@Entity(tableName = "static_dataset_metadata")
data class StaticDatasetMetadataEntity(
    @PrimaryKey val id: String = ACTIVE_ID,
    val datasetVersion: String,
    val datasetCreatedAt: Long,
    val datasetUpdatedAt: Long,
    val source: String,
    val totalRoutes: Int,
    val totalVariants: Int,
    val totalStops: Int,
    val totalPolylines: Int,
    val checksum: String,
    val lastSyncStatus: String,
    val lastErrorMessage: String? = null
) {
    companion object {
        const val ACTIVE_ID = "ACTIVE_DATASET"
    }
}
