package com.hcmcbustracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hcmcbustracker.core.database.entity.BusStopEntity
import com.hcmcbustracker.core.database.entity.RouteStopCrossReference
import kotlinx.coroutines.flow.Flow

@Dao
interface BusStopDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<BusStopEntity>)

    @Query("SELECT * FROM bus_stops WHERE id = :stopId LIMIT 1")
    fun getStopById(stopId: Int): Flow<BusStopEntity?>

    @Query("SELECT * FROM bus_stops WHERE id = :stopId LIMIT 1")
    suspend fun getStopByIdOnce(stopId: Int): BusStopEntity?

    @Query("SELECT * FROM bus_stops WHERE code = :code LIMIT 1")
    suspend fun getStopByCodeOnce(code: String): BusStopEntity?

    @Query("""
        SELECT * FROM bus_stops 
        WHERE lat BETWEEN :minLat AND :maxLat 
          AND lng BETWEEN :minLng AND :maxLng
        LIMIT 300
    """)
    fun getStopsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Flow<List<BusStopEntity>>

    @Query("""
        SELECT * FROM bus_stops 
        WHERE normalizedSearch LIKE '%' || :query || '%' 
           OR code LIKE '%' || :query || '%'
        LIMIT 100
    """)
    fun searchStops(query: String): Flow<List<BusStopEntity>>

    @Query("""
        SELECT bus_stops.* FROM bus_stops
        JOIN bus_stops_fts ON bus_stops.id = bus_stops_fts.rowid
        WHERE bus_stops_fts MATCH :query || '*'
        LIMIT 100
    """)
    fun searchStopsFts(query: String): Flow<List<BusStopEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteStopRefs(refs: List<RouteStopCrossReference>)

    @Query("DELETE FROM route_stop_cross_reference WHERE routeId = :routeId AND varId = :varId")
    suspend fun clearRouteStopRefs(routeId: Int, varId: Int)

    @Transaction
    suspend fun replaceRouteStopRefs(routeId: Int, varId: Int, refs: List<RouteStopCrossReference>) {
        clearRouteStopRefs(routeId, varId)
        insertRouteStopRefs(refs)
    }

    @Query("""
        SELECT s.* FROM bus_stops s
        INNER JOIN route_stop_cross_reference r ON s.id = r.stopId
        WHERE r.routeId = :routeId AND r.varId = :varId
        ORDER BY r.orderIndex ASC
    """)
    fun getStopsForRouteVar(routeId: Int, varId: Int): Flow<List<BusStopEntity>>

    @Query("SELECT COUNT(*) FROM bus_stops")
    suspend fun getTotalStopCount(): Int

    @Query("DELETE FROM bus_stops")
    suspend fun clearAllStops()

    @Query("DELETE FROM route_stop_cross_reference")
    suspend fun clearAllRouteStopRefs()
}
