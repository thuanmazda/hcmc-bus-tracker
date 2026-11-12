package com.hcmcbustracker.core.database.repository

import android.content.Context
import androidx.room.withTransaction
import com.hcmcbustracker.core.database.BusTrackerDatabase
import com.hcmcbustracker.core.database.entity.BusStopEntity
import com.hcmcbustracker.core.database.entity.FavoriteRouteEntity
import com.hcmcbustracker.core.database.entity.FavoriteStopEntity
import com.hcmcbustracker.core.database.entity.RouteEntity
import com.hcmcbustracker.core.database.entity.RouteStopCrossReference
import com.hcmcbustracker.core.database.entity.SyncMetadataEntity
import com.hcmcbustracker.core.database.mapper.toDomain
import com.hcmcbustracker.core.database.mapper.toEntity
import com.hcmcbustracker.core.database.seed.StaticDatasetManager
import com.hcmcbustracker.core.database.seed.StaticDatasetManager.toDomain
import com.hcmcbustracker.core.model.AppThemeMode
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.DatasetSyncProgress
import com.hcmcbustracker.core.model.DatasetUpdateOutcome
import com.hcmcbustracker.core.model.EndpointCapabilityRegistry
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.PollingDiagnosticsSnapshot
import com.hcmcbustracker.core.model.Prediction
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.RouteNumberComparator
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.core.model.RouteVariant
import com.hcmcbustracker.core.model.RouteVariantPollingTarget
import com.hcmcbustracker.core.model.RouteVehicleAggregator
import com.hcmcbustracker.core.model.RouteVehicleTelemetry
import com.hcmcbustracker.core.model.StaticDatasetMetadata
import com.hcmcbustracker.core.model.UserPreferences
import com.hcmcbustracker.core.model.VietnameseNormalizer
import com.hcmcbustracker.core.network.api.EbmsApiService
import com.hcmcbustracker.core.network.mapper.toDomain
import com.hcmcbustracker.core.network.polling.GlobalPollingCoordinator
import com.hcmcbustracker.core.network.polling.PollingResourceKey
import com.hcmcbustracker.core.network.util.SafeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusRepositoryImpl @Inject constructor(
    private val database: BusTrackerDatabase,
    private val apiService: EbmsApiService,
    private val json: Json,
    @ApplicationContext private val context: Context,
    private val pollingCoordinator: GlobalPollingCoordinator
) : BusRepository {

    constructor(
        database: BusTrackerDatabase,
        apiService: EbmsApiService,
        json: Json,
        context: Context
    ) : this(database, apiService, json, context, GlobalPollingCoordinator())

    private val routeDao = database.routeDao()
    private val routeVariantDao = database.routeVariantDao()
    private val busStopDao = database.busStopDao()
    private val routePathDao = database.routePathDao()
    private val favoriteStopDao = database.favoriteStopDao()
    private val favoriteRouteDao = database.favoriteRouteDao()
    private val syncMetadataDao = database.syncMetadataDao()
    private val staticDatasetMetadataDao = database.staticDatasetMetadataDao()

    private val prefs = context.getSharedPreferences("hcmc_bus_tracker_prefs", Context.MODE_PRIVATE)
    private val _userPreferencesFlow = MutableStateFlow(loadInitialPreferences())
    private val _datasetSyncProgress = MutableStateFlow<DatasetSyncProgress?>(null)

    private fun loadInitialPreferences(): UserPreferences {
        val themeOrdinal = prefs.getInt("pref_theme_mode", AppThemeMode.SYSTEM.ordinal)
        val themeMode = AppThemeMode.entries.getOrElse(themeOrdinal) { AppThemeMode.SYSTEM }
        val pollingSec = prefs.getInt("pref_polling_interval", 15)
        val notifEnabled = prefs.getBoolean("pref_notifications_enabled", true)
        return UserPreferences(
            themeMode = themeMode,
            pollingIntervalSeconds = pollingSec,
            notificationsEnabled = notifEnabled
        )
    }

    override fun getAllRoutes(): Flow<List<Route>> =
        routeDao.getAllRoutes().map { entities ->
            entities.map { it.toDomain() }.sortedWith(RouteNumberComparator.ROUTE_COMPARATOR)
        }

    override fun getRouteById(routeId: Int): Flow<Route?> =
        routeDao.getRouteById(routeId).map { it?.toDomain() }

    override fun getVariantsForRoute(routeId: Int): Flow<List<RouteVariant>> =
        routeVariantDao.getVariantsForRoute(routeId).map { entities -> entities.map { it.toDomain() } }

    override fun getRoutePath(routeId: Int, varId: Int): Flow<RoutePath?> =
        routePathDao.getPath("${routeId}_${varId}").map { it?.toDomain(json) }

    override fun searchRoutes(query: String): Flow<List<Route>> {
        val cleanQuery = VietnameseNormalizer.removeAccents(query)
        return routeDao.searchRoutes(cleanQuery).map { entities ->
            entities.map { it.toDomain() }.sortedWith(RouteNumberComparator.ROUTE_COMPARATOR)
        }
    }

    override fun searchStops(query: String): Flow<List<BusStop>> {
        val cleanQuery = VietnameseNormalizer.removeAccents(query)
        return busStopDao.searchStops(cleanQuery).map { entities -> entities.map { it.toDomain() } }
    }

    override fun getStopsInBounds(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): Flow<List<BusStop>> =
        busStopDao.getStopsInBounds(minLat, maxLat, minLng, maxLng).map { entities -> entities.map { it.toDomain() } }

    override fun getStopById(stopId: Int): Flow<BusStop?> =
        busStopDao.getStopById(stopId).map { it?.toDomain() }

    override fun getStopsForRouteVar(routeId: Int, varId: Int): Flow<List<BusStop>> =
        busStopDao.getStopsForRouteVar(routeId, varId).map { entities -> entities.map { it.toDomain() } }

    // --- PHASE 10: OFFLINE STATIC TRANSIT DATASET LIFECYCLE ---

    override fun observeStaticDatasetMetadata(): Flow<StaticDatasetMetadata?> =
        staticDatasetMetadataDao.observeMetadata().map { it?.toDomain() }

    override fun observeDatasetSyncProgress(): Flow<DatasetSyncProgress?> =
        _datasetSyncProgress.asStateFlow()

    override suspend fun getStaticDatasetMetadata(): StaticDatasetMetadata? =
        staticDatasetMetadataDao.getMetadataOnce()?.toDomain()

    override suspend fun ensureInitialStaticDatasetLoaded(): Boolean =
        StaticDatasetManager.ensureInitialStaticDatasetLoaded(
            database = database,
            context = context,
            json = json,
            progressFlow = _datasetSyncProgress
        )

    override suspend fun updateFullStaticDatasetFromEbms(): DatasetUpdateOutcome =
        StaticDatasetManager.updateFullStaticDatasetFromEbms(
            database = database,
            apiService = apiService,
            context = context,
            json = json,
            progressFlow = _datasetSyncProgress
        )

    // --- DATA SYNC OPERATIONS ---

    override suspend fun syncRoutes(): NetworkResult<Int> {
        return executeWithRetry {
            val comboDtos = apiService.getRoutesForCombo()
            val entities = comboDtos.map { dto ->
                val domain = dto.toDomain()
                val searchStr = "${domain.routeNo} ${domain.name} ${VietnameseNormalizer.removeAccents(domain.name)}"
                RouteEntity(
                    id = domain.id,
                    routeNo = domain.routeNo,
                    name = domain.name,
                    normalizedSearch = VietnameseNormalizer.removeAccents(searchStr)
                )
            }

            database.withTransaction {
                routeDao.insertRoutes(entities)
                syncMetadataDao.recordSync(
                    SyncMetadataEntity(
                        syncType = "ROUTES",
                        lastSyncTimestamp = System.currentTimeMillis(),
                        itemCount = entities.size,
                        isSuccess = true
                    )
                )
            }
            entities.size
        }
    }

    override suspend fun syncStops(): NetworkResult<Int> {
        return executeWithRetry {
            val autocompleteDto = apiService.getStopsForAutocomplete()
            val labels = autocompleteDto.l
            val values = autocompleteDto.v
            val ids = autocompleteDto.g

            val count = minOf(values.size, ids.size)
            val entities = ArrayList<BusStopEntity>(count)

            for (i in 0 until count) {
                val stopId = ids[i]
                val rawValue = values[i] // e.g. "[Q1 031] Bến Bạch Đằng (21 Tôn Đức Thắng, Quận 1)"
                val rawLabel = if (i < labels.size) labels[i] else ""

                var code = ""
                var name = rawValue
                var address = ""

                // Extract code from [CODE]
                val codeMatch = Regex("\\[(.*?)\\]\\s*(.*)").find(rawValue)
                if (codeMatch != null) {
                    code = codeMatch.groupValues[1]
                    name = codeMatch.groupValues[2]
                }

                // Extract address in parenthesis e.g. (21 Tôn Đức Thắng, Quận 1)
                val parenMatch = Regex("(.*?)\\s*\\((.*)\\)").find(name)
                if (parenMatch != null) {
                    name = parenMatch.groupValues[1].trim()
                    address = parenMatch.groupValues[2].trim()
                }

                val searchStr = "$code $name $address $rawLabel ${VietnameseNormalizer.removeAccents(name)} ${VietnameseNormalizer.removeAccents(address)}"

                entities.add(
                    BusStopEntity(
                        id = stopId,
                        code = code,
                        name = name,
                        stopType = "Trạm dừng",
                        zone = address,
                        ward = null,
                        addressNo = "",
                        street = address,
                        supportDisability = false,
                        status = "Đang khai thác",
                        lat = 0.0, // Detailed Lat/Lng updated when route stops are fetched
                        lng = 0.0,
                        searchKeywords = rawLabel,
                        routesRaw = "",
                        normalizedSearch = VietnameseNormalizer.removeAccents(searchStr)
                    )
                )
            }

            database.withTransaction {
                busStopDao.insertStops(entities)
                syncMetadataDao.recordSync(
                    SyncMetadataEntity(
                        syncType = "STOPS",
                        lastSyncTimestamp = System.currentTimeMillis(),
                        itemCount = entities.size,
                        isSuccess = true
                    )
                )
            }
            entities.size
        }
    }

    override suspend fun syncRouteDetailAndVariants(routeId: Int): NetworkResult<Route> {
        return executeWithRetry {
            val detailDto = apiService.getRouteById(routeId)
            val variantDtos = apiService.getVarsByRoute(routeId)

            val domainRoute = detailDto.toDomain()
            val domainVariants = variantDtos.map { it.toDomain() }

            database.withTransaction {
                routeDao.insertRoute(domainRoute.toEntity())
                routeVariantDao.insertVariants(domainVariants.map { it.toEntity() })
            }
            domainRoute
        }
    }

    override suspend fun syncStopsForRouteVar(routeId: Int, varId: Int): NetworkResult<List<BusStop>> {
        return executeWithRetry {
            val stopDtos = apiService.getStopsByVar(routeId, varId)
            val domainStops = stopDtos.map { it.toDomain() }

            val stopEntities = domainStops.map { it.toEntity() }
            val crossRefs = domainStops.mapIndexed { index, stop ->
                RouteStopCrossReference(
                    routeId = routeId,
                    varId = varId,
                    stopId = stop.id,
                    orderIndex = index
                )
            }

            database.withTransaction {
                busStopDao.insertStops(stopEntities)
                busStopDao.replaceRouteStopRefs(routeId, varId, crossRefs)
            }
            domainStops
        }
    }

    override suspend fun ensureRoutePath(routeId: Int, varId: Int): NetworkResult<RoutePath> {
        val compositeKey = "${routeId}_${varId}"
        val cached = routePathDao.getPathOnce(compositeKey)
        if (cached != null) {
            return NetworkResult.Success(cached.toDomain(json))
        }

        return executeWithRetry {
            val dto = apiService.getPathsByVar(routeId, varId)
            val domainPath = dto.toDomain(routeId, varId)
            routePathDao.insertPath(domainPath.toEntity(json))
            domainPath
        }
    }

    override suspend fun getLastSyncTimestamp(syncType: String): Long? {
        return syncMetadataDao.getSyncMetadata(syncType)?.lastSyncTimestamp
    }

    override suspend fun getRouteCount(): Int {
        return routeDao.getRouteCount()
    }

    override suspend fun getStopCount(): Int {
        return busStopDao.getTotalStopCount()
    }

    // --- REALTIME PREDICTION OPERATIONS ---

    override fun observePollingDiagnostics(): Flow<PollingDiagnosticsSnapshot> =
        pollingCoordinator.diagnostics

    override suspend fun fetchRealtimePrediction(stopId: Int): NetworkResult<List<Prediction>> =
        executePredictionListCall(
            key = PollingResourceKey(
                endpointId = EndpointCapabilityRegistry.ENDPOINT_PREDICT_BY_STOP_ID,
                scopeParams = mapOf("stopId" to stopId.toString())
            ),
            bypassCache = true
        ) { apiService.getPredictionByStopId(stopId) }

    override suspend fun fetchRealtimePredictionByCode(stopCode: String): NetworkResult<List<Prediction>> =
        executePredictionListCall(
            key = PollingResourceKey(
                endpointId = EndpointCapabilityRegistry.ENDPOINT_PREDICT_BY_STOP_CODE,
                scopeParams = mapOf("stopCode" to stopCode.trim())
            ),
            bypassCache = true
        ) { apiService.getPredictionByStopCode(stopCode) }

    override suspend fun fetchPredictNextStops(
        routeId: Int,
        varId: Int,
        stopId: Int,
        maxNextStops: Int
    ): NetworkResult<List<Prediction>> =
        executePredictionListCall(
            key = PollingResourceKey(
                endpointId = EndpointCapabilityRegistry.ENDPOINT_PREDICT_NEXT_STOPS,
                scopeParams = mapOf(
                    "routeId" to routeId.toString(),
                    "varId" to varId.toString(),
                    "stopId" to stopId.toString(),
                    "max" to maxNextStops.toString()
                )
            ),
            bypassCache = true
        ) { apiService.getPredictNextStops(routeId, varId, stopId, maxNextStops) }

    private suspend fun executePredictionListCall(
        key: PollingResourceKey,
        bypassCache: Boolean,
        timeoutMs: Long = 10_000L,
        block: suspend () -> List<com.hcmcbustracker.core.network.dto.PredictionDto>
    ): NetworkResult<List<Prediction>> {
        return pollingCoordinator.executeCoalesced(
            key = key,
            bypassCache = bypassCache
        ) {
            val requestStartedAt = System.currentTimeMillis()
            val netRes = SafeApiCall.execute(timeoutMs = timeoutMs, block = block)
            val responseTime = System.currentTimeMillis()
            when (netRes) {
                is NetworkResult.Success -> {
                    val receivedAt = System.currentTimeMillis()
                    NetworkResult.Success(
                        netRes.data.map {
                            it.toDomain(
                                receivedTimeMillis = responseTime,
                                requestStartedTimeMillis = requestStartedAt,
                                predictionReceivedTimeMillis = receivedAt
                            )
                        }
                    )
                }
                is NetworkResult.Error -> netRes
                is NetworkResult.Loading -> netRes
            }
        }
    }

    override suspend fun fetchRouteVariantVehicles(
        routeId: Int,
        varId: Int,
        routeStops: List<BusStop>,
        previousVehicles: Map<String, RouteVehicleTelemetry>
    ): NetworkResult<RouteVehicleAggregator.DeduplicationResult> {
        val resultMap = fetchMultiRouteVehicles(
            targets = listOf(RouteVariantPollingTarget(routeId = routeId, varId = varId, routeStops = routeStops)),
            priorityRouteId = routeId,
            previousByRouteVar = mapOf((routeId to varId) to previousVehicles)
        )
        return resultMap[routeId to varId] ?: NetworkResult.Success(
            RouteVehicleAggregator.DeduplicationResult(
                vehicles = emptyList(),
                totalRawRecords = 0,
                duplicatesRemoved = 0
            )
        )
    }

    override suspend fun fetchMultiRouteVehicles(
        targets: List<RouteVariantPollingTarget>,
        priorityRouteId: Int?,
        previousByRouteVar: Map<Pair<Int, Int>, Map<String, RouteVehicleTelemetry>>
    ): Map<Pair<Int, Int>, NetworkResult<RouteVehicleAggregator.DeduplicationResult>> {
        if (targets.isEmpty()) return emptyMap()
        val cycleStartedAt = System.currentTimeMillis()

        // Never sacrifice active/priority route freshness behind background routes
        val orderedTargets = targets.sortedBy { if (it.routeId == priorityRouteId) 0 else 1 }
        val checkpointsByTarget = LinkedHashMap<Pair<Int, Int>, List<BusStop>>(orderedTargets.size)
        for (target in orderedTargets) {
            val effectiveStops = target.routeStops.ifEmpty {
                busStopDao.getStopsForRouteVar(target.routeId, target.varId)
                    .firstOrNull()?.map { it.toDomain() } ?: emptyList()
            }
            checkpointsByTarget[target.routeId to target.varId] = selectCheckpointStops(effectiveStops)
        }

        // Collect unique checkpoint stops across targets while marking priority checkpoints bypassCache=true
        val priorityStopIds = HashSet<Int>()
        val allUniqueStops = LinkedHashMap<Int, BusStop>()
        for (target in orderedTargets) {
            val cps = checkpointsByTarget[target.routeId to target.varId].orEmpty()
            val isPriority = target.routeId == priorityRouteId
            for (stop in cps) {
                if (isPriority) priorityStopIds.add(stop.id)
                allUniqueStops.putIfAbsent(stop.id, stop)
            }
        }

        if (allUniqueStops.isEmpty()) {
            return orderedTargets.associate { (rId, vId, _) ->
                (rId to vId) to NetworkResult.Success(
                    RouteVehicleAggregator.DeduplicationResult(emptyList(), 0, 0)
                )
            }
        }

        // Order stop queries so priority route stops acquire permits first
        val sortedStopList = allUniqueStops.values.sortedBy { if (it.id in priorityStopIds) 0 else 1 }
        val requestStartedAt = System.currentTimeMillis()

        val stopResponses: Map<Int, NetworkResult<List<Prediction>>> = coroutineScope {
            sortedStopList.map { stop ->
                async {
                    val key = PollingResourceKey(
                        endpointId = EndpointCapabilityRegistry.ENDPOINT_PREDICT_BY_STOP_ID,
                        scopeParams = mapOf("stopId" to stop.id.toString())
                    )
                    val res = executePredictionListCall(
                        key = key,
                        bypassCache = stop.id in priorityStopIds,
                        timeoutMs = 8_000L
                    ) { apiService.getPredictionByStopId(stop.id) }
                    stop.id to res
                }
            }.awaitAll().toMap()
        }

        val processingStart = System.currentTimeMillis()
        val outcomes = LinkedHashMap<Pair<Int, Int>, NetworkResult<RouteVehicleAggregator.DeduplicationResult>>(orderedTargets.size)
        val allAges = mutableListOf<Long>()
        var newDtsCount = 0
        var totalMergedVehicles = 0

        for (target in orderedTargets) {
            val key = target.routeId to target.varId
            val checkpoints = checkpointsByTarget[key].orEmpty()
            if (checkpoints.isEmpty()) {
                outcomes[key] = NetworkResult.Success(
                    RouteVehicleAggregator.DeduplicationResult(emptyList(), 0, 0)
                )
                continue
            }

            var successCount = 0
            var firstError: NetworkResult.Error? = null
            val targetPredictions = mutableListOf<Prediction>()

            for (cp in checkpoints) {
                when (val stopRes = stopResponses[cp.id]) {
                    is NetworkResult.Success -> {
                        successCount++
                        targetPredictions.addAll(stopRes.data)
                    }
                    is NetworkResult.Error -> {
                        if (firstError == null || stopRes.code == 429) {
                            firstError = stopRes
                        }
                    }
                    else -> {}
                }
            }

            if (successCount == 0 && firstError != null) {
                outcomes[key] = firstError
            } else {
                val nowMs = System.currentTimeMillis()
                val merged = RouteVehicleAggregator.aggregateAndMerge(
                    routeId = target.routeId,
                    varId = target.varId,
                    predictions = targetPredictions,
                    requestStartedAtMillis = requestStartedAt,
                    predictionReceivedAtMillis = nowMs,
                    previousVehicles = previousByRouteVar[key].orEmpty(),
                    currentMillis = nowMs
                )
                outcomes[key] = NetworkResult.Success(merged)
                for (v in merged.vehicles) {
                    totalMergedVehicles++
                    if (!v.isGpsTimestampUnchanged) newDtsCount++
                    val age = v.computeGpsAgeSeconds(nowMs)
                    if (age >= 0L) allAges.add(age)
                }
            }
        }

        val finishedAt = System.currentTimeMillis()
        pollingCoordinator.recordCycleMetrics(
            cycleDurationMs = finishedAt - cycleStartedAt,
            processingDelayMs = finishedAt - processingStart,
            gpsAgesSeconds = allAges,
            newDtsCount = newDtsCount,
            totalComparedCount = totalMergedVehicles
        )

        return outcomes
    }

    /**
     * Selects up to 5 evenly distributed checkpoints covering 20%..100% along the variant corridor.
     */
    internal fun selectCheckpointStops(stops: List<BusStop>): List<BusStop> {
        if (stops.isEmpty()) return emptyList()
        val n = stops.size
        if (n <= 4) {
            return stops.takeLast(minOf(3, n)).distinctBy { it.id }
        }
        val indices = listOf(
            n / 5,
            (2 * n) / 5,
            (3 * n) / 5,
            (4 * n) / 5,
            n - 1
        ).distinct()
        return indices.mapNotNull { idx -> stops.getOrNull(idx) }.distinctBy { it.id }
    }

    // --- USER FAVORITES ---

    override fun isStopFavorite(stopId: Int): Flow<Boolean> =
        favoriteStopDao.isFavorite(stopId)

    override suspend fun toggleFavoriteStop(stopId: Int) {
        val isFav = favoriteStopDao.isFavorite(stopId).firstOrNull() ?: false
        if (isFav) {
            favoriteStopDao.removeFavorite(stopId)
        } else {
            favoriteStopDao.addFavorite(FavoriteStopEntity(stopId = stopId))
        }
    }

    override fun getFavoriteStops(): Flow<List<BusStop>> =
        favoriteStopDao.getFavoriteStops().map { entities -> entities.map { it.toDomain() } }

    override fun isRouteFavorite(routeId: Int): Flow<Boolean> =
        favoriteRouteDao.isFavorite(routeId)

    override suspend fun toggleFavoriteRoute(routeId: Int) {
        val isFav = favoriteRouteDao.isFavorite(routeId).firstOrNull() ?: false
        if (isFav) {
            favoriteRouteDao.removeFavorite(routeId)
        } else {
            favoriteRouteDao.addFavorite(FavoriteRouteEntity(routeId = routeId))
        }
    }

    override fun getFavoriteRoutes(): Flow<List<Route>> =
        favoriteRouteDao.getFavoriteRoutes().map { entities -> entities.map { it.toDomain() } }

    // --- APP SETTINGS & USER PREFERENCES ---

    override fun getUserPreferences(): Flow<UserPreferences> = _userPreferencesFlow.asStateFlow()

    override suspend fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putInt("pref_theme_mode", mode.ordinal).apply()
        _userPreferencesFlow.value = _userPreferencesFlow.value.copy(themeMode = mode)
    }

    override suspend fun setPollingIntervalSeconds(seconds: Int) {
        val safeSeconds = seconds.coerceIn(10, 120)
        prefs.edit().putInt("pref_polling_interval", safeSeconds).apply()
        _userPreferencesFlow.value = _userPreferencesFlow.value.copy(pollingIntervalSeconds = safeSeconds)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pref_notifications_enabled", enabled).apply()
        _userPreferencesFlow.value = _userPreferencesFlow.value.copy(notificationsEnabled = enabled)
    }

    override suspend fun clearCache() {
        routePathDao.clearAllPaths()
    }

    /**
     * Executes network actions with limited bounded retries and exponential backoff (1s, 2s).
     */
    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        block: suspend () -> T
    ): NetworkResult<T> {
        var currentAttempt = 0
        var lastException: Exception? = null

        while (currentAttempt < maxAttempts) {
            try {
                val result = block()
                return NetworkResult.Success(result)
            } catch (e: IOException) {
                lastException = e
                currentAttempt++
                if (currentAttempt < maxAttempts) {
                    delay(1000L * currentAttempt)
                }
            } catch (e: Exception) {
                return NetworkResult.Error(message = e.localizedMessage ?: "Lỗi dữ liệu", throwable = e)
            }
        }

        return NetworkResult.Error(
            message = lastException?.localizedMessage ?: "Kết nối mạng thất bại sau $maxAttempts lần thử",
            throwable = lastException
        )
    }
}
