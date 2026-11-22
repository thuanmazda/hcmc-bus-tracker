package com.hcmcbustracker.core.database.repository

import com.hcmcbustracker.core.model.AppThemeMode
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.DatasetSyncProgress
import com.hcmcbustracker.core.model.DatasetUpdateOutcome
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.PollingDiagnosticsSnapshot
import com.hcmcbustracker.core.model.Prediction
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.core.model.RouteVariant
import com.hcmcbustracker.core.model.RouteVariantPollingTarget
import com.hcmcbustracker.core.model.RouteVehicleAggregator
import com.hcmcbustracker.core.model.RouteVehicleTelemetry
import com.hcmcbustracker.core.model.SearchHistoryItem
import com.hcmcbustracker.core.model.SearchHistoryType
import com.hcmcbustracker.core.model.StaticDatasetMetadata
import com.hcmcbustracker.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Single source of truth repository for public bus transit in HCMC.
 */
interface BusRepository {

    // --- Offline-First Local Data Streams ---
    fun getAllRoutes(): Flow<List<Route>>
    fun getRouteById(routeId: Int): Flow<Route?>
    fun getVariantsForRoute(routeId: Int): Flow<List<RouteVariant>>
    fun getRoutePath(routeId: Int, varId: Int): Flow<RoutePath?>
    fun searchRoutes(query: String): Flow<List<Route>>
    fun searchStops(query: String): Flow<List<BusStop>>
    fun getStopsInBounds(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): Flow<List<BusStop>>
    fun getStopById(stopId: Int): Flow<BusStop?>
    fun getStopsForRouteVar(routeId: Int, varId: Int): Flow<List<BusStop>>

    // --- Phase 10: Offline Static Transit Dataset Lifecycle ---
    fun observeStaticDatasetMetadata(): Flow<StaticDatasetMetadata?>
    fun observeDatasetSyncProgress(): Flow<DatasetSyncProgress?>
    suspend fun getStaticDatasetMetadata(): StaticDatasetMetadata?
    suspend fun ensureInitialStaticDatasetLoaded(): Boolean
    suspend fun updateFullStaticDatasetFromEbms(): DatasetUpdateOutcome

    // --- Synchronization Operations (Engine) ---
    suspend fun syncRoutes(): NetworkResult<Int>
    suspend fun syncStops(): NetworkResult<Int>
    suspend fun syncRouteDetailAndVariants(routeId: Int): NetworkResult<Route>
    suspend fun syncStopsForRouteVar(routeId: Int, varId: Int): NetworkResult<List<BusStop>>
    suspend fun ensureRoutePath(routeId: Int, varId: Int): NetworkResult<RoutePath>
    suspend fun getLastSyncTimestamp(syncType: String): Long?
    suspend fun getRouteCount(): Int
    suspend fun getStopCount(): Int

    // --- Realtime Network Operations ---
    suspend fun fetchRealtimePrediction(stopId: Int): NetworkResult<List<Prediction>>
    suspend fun fetchRealtimePredictionByCode(stopCode: String): NetworkResult<List<Prediction>>
    suspend fun fetchPredictNextStops(routeId: Int, varId: Int, stopId: Int, maxNextStops: Int = 3): NetworkResult<List<Prediction>>
    suspend fun fetchRouteVariantVehicles(
        routeId: Int,
        varId: Int,
        routeStops: List<BusStop>,
        previousVehicles: Map<String, RouteVehicleTelemetry> = emptyMap()
    ): NetworkResult<RouteVehicleAggregator.DeduplicationResult>

    fun observePollingDiagnostics(): Flow<PollingDiagnosticsSnapshot> = emptyFlow()

    suspend fun fetchMultiRouteVehicles(
        targets: List<RouteVariantPollingTarget>,
        priorityRouteId: Int? = null,
        previousByRouteVar: Map<Pair<Int, Int>, Map<String, RouteVehicleTelemetry>> = emptyMap()
    ): Map<Pair<Int, Int>, NetworkResult<RouteVehicleAggregator.DeduplicationResult>> = emptyMap()

    // --- Phase 13: Timetable & Operating Hours ---
    suspend fun getTimetablesForRoute(routeId: Int): NetworkResult<List<com.hcmcbustracker.core.model.Timetable>> =
        NetworkResult.Success(emptyList())
    suspend fun getTripsForTimetable(routeId: Int, timetableId: Int): NetworkResult<List<com.hcmcbustracker.core.model.Trip>> =
        NetworkResult.Success(emptyList())

    // --- Phase 13: License Plate Fleet Lookup ---
    fun searchActiveVehiclesByPlate(query: String): List<com.hcmcbustracker.core.model.ActiveVehicleSummary> =
        com.hcmcbustracker.core.model.ActiveFleetRegistry.searchByPlate(query)
    fun getActiveVehicleByPlate(plateNumber: String): com.hcmcbustracker.core.model.ActiveVehicleSummary? =
        com.hcmcbustracker.core.model.ActiveFleetRegistry.getVehicle(plateNumber)

    // --- User Favorites ---
    fun isStopFavorite(stopId: Int): Flow<Boolean>
    suspend fun toggleFavoriteStop(stopId: Int)
    fun getFavoriteStops(): Flow<List<BusStop>>

    fun isRouteFavorite(routeId: Int): Flow<Boolean>
    suspend fun toggleFavoriteRoute(routeId: Int)
    fun getFavoriteRoutes(): Flow<List<Route>>
    suspend fun cleanupOrphanFavorites()

    // --- Search History ---
    fun getSearchHistory(): Flow<List<SearchHistoryItem>>
    suspend fun addSearchHistory(query: String, type: SearchHistoryType = SearchHistoryType.GENERAL)
    suspend fun removeSearchHistoryItem(query: String)
    suspend fun clearSearchHistory()

    // --- App Settings & User Preferences ---
    fun getUserPreferences(): Flow<UserPreferences>
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setPollingIntervalSeconds(seconds: Int)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setArrivalAlertThresholds(timeMinutes: Int, distanceMeters: Double)
    suspend fun toggleStopNotification(stopId: Int, enabled: Boolean)
    suspend fun clearCache()
}
