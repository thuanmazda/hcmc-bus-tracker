package com.hcmcbustracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hcmcbustracker.core.database.dao.BusStopDao
import com.hcmcbustracker.core.database.dao.FavoriteRouteDao
import com.hcmcbustracker.core.database.dao.FavoriteStopDao
import com.hcmcbustracker.core.database.dao.RouteDao
import com.hcmcbustracker.core.database.dao.RoutePathDao
import com.hcmcbustracker.core.database.dao.RouteVariantDao
import com.hcmcbustracker.core.database.dao.StaticDatasetMetadataDao
import com.hcmcbustracker.core.database.dao.SyncMetadataDao
import com.hcmcbustracker.core.database.entity.BusStopEntity
import com.hcmcbustracker.core.database.entity.BusStopFtsEntity
import com.hcmcbustracker.core.database.entity.FavoriteRouteEntity
import com.hcmcbustracker.core.database.entity.FavoriteStopEntity
import com.hcmcbustracker.core.database.entity.RouteEntity
import com.hcmcbustracker.core.database.entity.RoutePathEntity
import com.hcmcbustracker.core.database.entity.RouteStopCrossReference
import com.hcmcbustracker.core.database.entity.RouteVariantEntity
import com.hcmcbustracker.core.database.entity.StaticDatasetMetadataEntity
import com.hcmcbustracker.core.database.entity.SyncMetadataEntity

@Database(
    entities = [
        RouteEntity::class,
        RouteVariantEntity::class,
        BusStopEntity::class,
        BusStopFtsEntity::class,
        RouteStopCrossReference::class,
        RoutePathEntity::class,
        FavoriteStopEntity::class,
        FavoriteRouteEntity::class,
        SyncMetadataEntity::class,
        StaticDatasetMetadataEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class BusTrackerDatabase : RoomDatabase() {

    abstract fun routeDao(): RouteDao
    abstract fun routeVariantDao(): RouteVariantDao
    abstract fun busStopDao(): BusStopDao
    abstract fun routePathDao(): RoutePathDao
    abstract fun favoriteStopDao(): FavoriteStopDao
    abstract fun favoriteRouteDao(): FavoriteRouteDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun staticDatasetMetadataDao(): StaticDatasetMetadataDao

    companion object {
        const val DATABASE_NAME = "hcmc_bus_tracker.db"
    }
}
