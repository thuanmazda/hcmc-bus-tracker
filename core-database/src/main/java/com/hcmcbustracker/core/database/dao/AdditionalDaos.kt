package com.hcmcbustracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hcmcbustracker.core.database.entity.BusStopEntity
import com.hcmcbustracker.core.database.entity.FavoriteRouteEntity
import com.hcmcbustracker.core.database.entity.FavoriteStopEntity
import com.hcmcbustracker.core.database.entity.RouteEntity
import com.hcmcbustracker.core.database.entity.RoutePathEntity
import com.hcmcbustracker.core.database.entity.StaticDatasetMetadataEntity
import com.hcmcbustracker.core.database.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutePathDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPath(path: RoutePathEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaths(paths: List<RoutePathEntity>)

    @Query("SELECT * FROM route_paths WHERE compositeKey = :compositeKey LIMIT 1")
    fun getPath(compositeKey: String): Flow<RoutePathEntity?>

    @Query("SELECT * FROM route_paths WHERE compositeKey = :compositeKey LIMIT 1")
    suspend fun getPathOnce(compositeKey: String): RoutePathEntity?

    @Query("SELECT COUNT(*) FROM route_paths")
    suspend fun getPathCount(): Int

    @Query("DELETE FROM route_paths")
    suspend fun clearAllPaths()
}

@Dao
interface FavoriteStopDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteStopEntity)

    @Query("DELETE FROM favorite_stops WHERE stopId = :stopId")
    suspend fun removeFavorite(stopId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_stops WHERE stopId = :stopId)")
    fun isFavorite(stopId: Int): Flow<Boolean>

    @Query("""
        SELECT s.* FROM bus_stops s
        INNER JOIN favorite_stops f ON s.id = f.stopId
        ORDER BY f.addedAtTimestamp DESC
    """)
    fun getFavoriteStops(): Flow<List<BusStopEntity>>
}

@Dao
interface FavoriteRouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteRouteEntity)

    @Query("DELETE FROM favorite_routes WHERE routeId = :routeId")
    suspend fun removeFavorite(routeId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_routes WHERE routeId = :routeId)")
    fun isFavorite(routeId: Int): Flow<Boolean>

    @Query("""
        SELECT r.* FROM routes r
        INNER JOIN favorite_routes f ON r.id = f.routeId
        ORDER BY f.addedAtTimestamp DESC
    """)
    fun getFavoriteRoutes(): Flow<List<RouteEntity>>
}

@Dao
interface SyncMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordSync(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE syncType = :syncType LIMIT 1")
    suspend fun getSyncMetadata(syncType: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE syncType = :syncType LIMIT 1")
    fun observeSyncMetadata(syncType: String): Flow<SyncMetadataEntity?>
}

@Dao
interface StaticDatasetMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: StaticDatasetMetadataEntity)

    @Query("SELECT * FROM static_dataset_metadata WHERE id = :id LIMIT 1")
    suspend fun getMetadataOnce(id: String = StaticDatasetMetadataEntity.ACTIVE_ID): StaticDatasetMetadataEntity?

    @Query("SELECT * FROM static_dataset_metadata WHERE id = :id LIMIT 1")
    fun observeMetadata(id: String = StaticDatasetMetadataEntity.ACTIVE_ID): Flow<StaticDatasetMetadataEntity?>
}
