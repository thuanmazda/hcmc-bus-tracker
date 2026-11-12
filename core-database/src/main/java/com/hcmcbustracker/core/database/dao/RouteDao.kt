package com.hcmcbustracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hcmcbustracker.core.database.entity.RouteEntity
import com.hcmcbustracker.core.database.entity.RouteVariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<RouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity)

    @Update
    suspend fun updateRoute(route: RouteEntity)

    @Query("SELECT * FROM routes ORDER BY CAST(routeNo AS INTEGER) ASC, routeNo ASC")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :routeId LIMIT 1")
    fun getRouteById(routeId: Int): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE id = :routeId LIMIT 1")
    suspend fun getRouteByIdOnce(routeId: Int): RouteEntity?

    @Query("SELECT * FROM routes WHERE routeNo = :routeNo LIMIT 1")
    fun getRouteByNo(routeNo: String): Flow<RouteEntity?>

    @Query("""
        SELECT * FROM routes 
        WHERE normalizedSearch LIKE '%' || :query || '%' 
           OR routeNo LIKE :query || '%'
        ORDER BY CAST(routeNo AS INTEGER) ASC, routeNo ASC
    """)
    fun searchRoutes(query: String): Flow<List<RouteEntity>>

    @Query("SELECT COUNT(*) FROM routes")
    suspend fun getRouteCount(): Int

    @Query("DELETE FROM routes")
    suspend fun clearAllRoutes()
}

@Dao
interface RouteVariantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<RouteVariantEntity>)

    @Query("SELECT * FROM route_variants WHERE routeId = :routeId ORDER BY id ASC")
    fun getVariantsForRoute(routeId: Int): Flow<List<RouteVariantEntity>>

    @Query("SELECT * FROM route_variants WHERE routeId = :routeId ORDER BY id ASC")
    suspend fun getVariantsForRouteOnce(routeId: Int): List<RouteVariantEntity>

    @Query("SELECT * FROM route_variants WHERE id = :varId LIMIT 1")
    fun getVariantById(varId: Int): Flow<RouteVariantEntity?>

    @Query("SELECT COUNT(*) FROM route_variants")
    suspend fun getVariantCount(): Int

    @Query("DELETE FROM route_variants")
    suspend fun clearAllVariants()
}
