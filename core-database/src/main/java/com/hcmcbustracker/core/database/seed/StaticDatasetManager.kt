package com.hcmcbustracker.core.database.seed

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.room.withTransaction
import com.hcmcbustracker.core.database.BusTrackerDatabase
import com.hcmcbustracker.core.database.entity.BusStopEntity
import com.hcmcbustracker.core.database.entity.RouteEntity
import com.hcmcbustracker.core.database.entity.RoutePathEntity
import com.hcmcbustracker.core.database.entity.RouteStopCrossReference
import com.hcmcbustracker.core.database.entity.RouteVariantEntity
import com.hcmcbustracker.core.database.entity.StaticDatasetMetadataEntity
import com.hcmcbustracker.core.database.entity.SyncMetadataEntity
import com.hcmcbustracker.core.database.mapper.toEntity
import com.hcmcbustracker.core.model.DatasetSyncProgress
import com.hcmcbustracker.core.model.DatasetSyncStatus
import com.hcmcbustracker.core.model.DatasetUpdateOutcome
import com.hcmcbustracker.core.model.StaticDatasetMetadata
import com.hcmcbustracker.core.model.VietnameseNormalizer
import com.hcmcbustracker.core.network.api.EbmsApiService
import com.hcmcbustracker.core.network.mapper.toDomain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream

/**
 * Manages verified offline seed installation and atomic staging-validated updates
 * for HCMC bus static transit data while strictly preserving user favorites.
 */
object StaticDatasetManager {

    private const val SEED_ASSET_NAME = "ebms_static_dataset.json.gz"
    private const val SEED_ASSET_UNPACKED_NAME = "ebms_static_dataset.json"
    private val seedMutex = Mutex()
    private val syncMutex = Mutex()

    fun StaticDatasetMetadataEntity.toDomain(): StaticDatasetMetadata {
        val parsedStatus = try {
            DatasetSyncStatus.valueOf(lastSyncStatus)
        } catch (_: Exception) {
            DatasetSyncStatus.FAILED
        }
        return StaticDatasetMetadata(
            datasetVersion = datasetVersion,
            datasetCreatedAt = datasetCreatedAt,
            datasetUpdatedAt = datasetUpdatedAt,
            source = source,
            totalRoutes = totalRoutes,
            totalVariants = totalVariants,
            totalStops = totalStops,
            totalPolylines = totalPolylines,
            checksum = checksum,
            lastSyncStatus = parsedStatus,
            lastErrorMessage = lastErrorMessage
        )
    }

    /**
     * Checks if static dataset (routes, variants, stop coordinates, polylines) is already VALID in Room.
     * If missing or incomplete (fresh install or upgrade from legacy Phase 1-9 schema), loads and validates
     * the packaged EBMS seed asset cleanly inside an atomic transaction without touching user favorites.
     */
    suspend fun ensureInitialStaticDatasetLoaded(
        database: BusTrackerDatabase,
        context: Context,
        json: Json,
        progressFlow: MutableStateFlow<DatasetSyncProgress?>
    ): Boolean = withContext(Dispatchers.IO) {
        seedMutex.withLock {
            val metaDao = database.staticDatasetMetadataDao()
            val existingMeta = metaDao.getMetadataOnce()
            val routeCount = database.routeDao().getRouteCount()
            val variantCount = database.routeVariantDao().getVariantCount()
            val pathCount = database.routePathDao().getPathCount()

            if (existingMeta != null &&
                existingMeta.lastSyncStatus == DatasetSyncStatus.VALID.name &&
                routeCount > 0 &&
                variantCount > 0 &&
                pathCount > 0
            ) {
                return@withLock true
            }

            return@withLock try {
                openSeedAssetStream(context).use { rawStream ->
                    seedFromGzipStream(database, rawStream, json, progressFlow)
                }
            } catch (_: IOException) {
                // Safe fallback in test harnesses without packaged Android assets
                false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            } finally {
                progressFlow.value = null
            }
        }
    }

    /**
     * Opens the packaged EBMS seed asset, supporting both `.json.gz` and AAPT2-unpacked `.json`.
     */
    private fun openSeedAssetStream(context: Context): InputStream {
        return try {
            context.assets.open(SEED_ASSET_NAME)
        } catch (_: IOException) {
            context.assets.open(SEED_ASSET_UNPACKED_NAME)
        }
    }

    /**
     * Parses (auto-detecting GZIP vs plain JSON), validates, and atomically commits
     * a [StaticSeedDataset] stream into Room.
     */
    internal suspend fun seedFromGzipStream(
        database: BusTrackerDatabase,
        rawStream: InputStream,
        json: Json,
        progressFlow: MutableStateFlow<DatasetSyncProgress?>
    ): Boolean {
        return try {
            progressFlow.value = DatasetSyncProgress(
                phase = "Đang chuẩn bị dữ liệu tuyến xe offline...",
                completedSteps = 1,
                totalSteps = 3,
                isCancellable = false
            )

            val buffered = BufferedInputStream(rawStream, 16 * 1024)
            buffered.mark(2)
            val b0 = buffered.read()
            val b1 = buffered.read()
            buffered.reset()
            val isGzip = (b0 == 0x1F && b1 == 0x8B)
            val jsonStream: InputStream = if (isGzip) GZIPInputStream(buffered) else buffered
            val jsonText = jsonStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            progressFlow.value = DatasetSyncProgress(
                phase = "Đang kiểm tra tính toàn vẹn dữ liệu...",
                completedSteps = 2,
                totalSteps = 3,
                isCancellable = false
            )

            val seed = json.decodeFromString(StaticSeedDataset.serializer(), jsonText)
            val staged = mapSeedToValidatedStaging(seed, json) ?: return false

            progressFlow.value = DatasetSyncProgress(
                phase = "Đang nạp cơ sở dữ liệu ngoại tuyến...",
                completedSteps = 3,
                totalSteps = 3,
                isCancellable = false
            )

            commitValidatedDatasetAtomically(database, staged)
            true
        } finally {
            progressFlow.value = null
        }
    }

    /**
     * Stages a full download from EBMS API, validates all records, detects checksum changes,
     * and atomically swaps static tables in Room only after 100% validation succeeds.
     * Any failure or cancellation leaves the active database and user favorites completely intact.
     */
    suspend fun updateFullStaticDatasetFromEbms(
        database: BusTrackerDatabase,
        apiService: EbmsApiService,
        context: Context,
        json: Json,
        progressFlow: MutableStateFlow<DatasetSyncProgress?>
    ): DatasetUpdateOutcome = withContext(Dispatchers.IO) {
        val currentMetaEntity = database.staticDatasetMetadataDao().getMetadataOnce()
        val currentMeta = currentMetaEntity?.toDomain()

        if (!isNetworkConnected(context)) {
            return@withContext DatasetUpdateOutcome.Failed(
                message = "Không có kết nối Internet. Vui lòng bật mạng để cập nhật dữ liệu mới.",
                preservedMetadata = currentMeta
            )
        }

        if (syncMutex.isLocked) {
            return@withContext DatasetUpdateOutcome.Failed(
                message = "Quá trình cập nhật đang chạy. Vui lòng đợi hoàn tất.",
                preservedMetadata = currentMeta
            )
        }

        syncMutex.withLock {
            try {
                // Phase 1: Route Catalog
                progressFlow.value = DatasetSyncProgress(
                    phase = "Đang tải danh mục tuyến xe từ EBMS...",
                    completedSteps = 0,
                    totalSteps = 100,
                    isCancellable = true
                )

                val combos = apiService.getRoutesForCombo()
                if (combos.isEmpty()) {
                    return@withLock DatasetUpdateOutcome.Failed(
                        message = "Máy chủ EBMS trả về danh sách tuyến rỗng.",
                        preservedMetadata = currentMeta
                    )
                }

                ensureActive()

                val totalRoutesCount = combos.size
                val routeCounter = AtomicInteger(0)
                val semaphore = Semaphore(6)

                val routeEntities = mutableListOf<RouteEntity>()
                val variantEntities = mutableListOf<RouteVariantEntity>()
                val variantPairs = mutableListOf<Pair<Int, Int>>()

                // Phase 2: Route Details & Variants (concurrency bounded)
                coroutineScope {
                    val jobs = combos.map { combo ->
                        async {
                            semaphore.withPermit {
                                ensureActive()
                                val domainCombo = combo.toDomain()
                                val rid = domainCombo.id
                                if (rid <= 0) return@withPermit null

                                val detail = try {
                                    apiService.getRouteById(rid).toDomain()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    com.hcmcbustracker.core.model.Route(
                                        id = rid,
                                        routeNo = domainCombo.routeNo,
                                        name = domainCombo.name
                                    )
                                }

                                val vars = apiService.getVarsByRoute(rid).map { it.toDomain() }
                                val done = routeCounter.incrementAndGet()
                                val pct = 10 + (done * 35 / totalRoutesCount)
                                progressFlow.value = DatasetSyncProgress(
                                    phase = "Đang tải thông tin & chiều tuyến ($done/$totalRoutesCount)...",
                                    completedSteps = pct,
                                    totalSteps = 100,
                                    isCancellable = true
                                )
                                Pair(detail.toEntity(), vars)
                            }
                        }
                    }

                    jobs.awaitAll().filterNotNull().forEach { (rEntity, vars) ->
                        routeEntities.add(rEntity)
                        vars.forEach { v ->
                            variantEntities.add(v.toEntity())
                            variantPairs.add(rEntity.id to v.id)
                        }
                    }
                }

                if (variantPairs.isEmpty()) {
                    return@withLock DatasetUpdateOutcome.Failed(
                        message = "Không thể tải danh sách chiều tuyến từ EBMS.",
                        preservedMetadata = currentMeta
                    )
                }

                ensureActive()

                // Phase 3: Stops by Variant & Polylines (concurrency bounded)
                val stopsMap = LinkedHashMap<Int, BusStopEntity>()
                val crossRefs = mutableListOf<RouteStopCrossReference>()
                val pathEntities = mutableListOf<RoutePathEntity>()
                val varCounter = AtomicInteger(0)
                val totalVarsCount = variantPairs.size

                coroutineScope {
                    val varJobs = variantPairs.map { (rid, vid) ->
                        async {
                            semaphore.withPermit {
                                ensureActive()
                                val stops = apiService.getStopsByVar(rid, vid).map { it.toDomain() }
                                val path = apiService.getPathsByVar(rid, vid).toDomain(rid, vid)

                                val done = varCounter.incrementAndGet()
                                val pct = 45 + (done * 45 / totalVarsCount)
                                progressFlow.value = DatasetSyncProgress(
                                    phase = "Đang tải trạm dừng & lộ trình bản đồ ($done/$totalVarsCount)...",
                                    completedSteps = pct,
                                    totalSteps = 100,
                                    isCancellable = true
                                )
                                Triple(Pair(rid, vid), stops, path)
                            }
                        }
                    }

                    varJobs.awaitAll().forEach { (pair, stops, path) ->
                        val (rid, vid) = pair
                        val seenInVar = HashSet<Int>()
                        stops.forEachIndexed { idx, s ->
                            val existing = stopsMap[s.id]
                            if (existing == null || (existing.lat == 0.0 && s.lat != 0.0)) {
                                stopsMap[s.id] = s.toEntity()
                            }
                            if (seenInVar.add(s.id)) {
                                crossRefs.add(
                                    RouteStopCrossReference(
                                        routeId = rid,
                                        varId = vid,
                                        stopId = s.id,
                                        orderIndex = idx
                                    )
                                )
                            }
                        }
                        if (path.points.size >= 2) {
                            pathEntities.add(path.toEntity(json))
                        }
                    }
                }

                ensureActive()

                // Phase 4: Merge additional searchable stops from autocomplete
                progressFlow.value = DatasetSyncProgress(
                    phase = "Đang hoàn tất danh mục tìm kiếm & kiểm tra dữ liệu...",
                    completedSteps = 92,
                    totalSteps = 100,
                    isCancellable = true
                )

                try {
                    val auto = apiService.getStopsForAutocomplete()
                    val count = minOf(auto.v.size, auto.g.size)
                    for (i in 0 until count) {
                        val sid = auto.g[i]
                        if (stopsMap.containsKey(sid)) continue
                        val rawVal = auto.v[i]
                        val rawLbl = if (i < auto.l.size) auto.l[i] else ""
                        var code = ""
                        var name = rawVal
                        var address = ""
                        Regex("\\[(.*?)\\]\\s*(.*)").find(rawVal)?.let { m ->
                            code = m.groupValues[1].trim()
                            name = m.groupValues[2].trim()
                        }
                        Regex("(.*?)\\s*\\((.*)\\)").find(name)?.let { m ->
                            name = m.groupValues[1].trim()
                            address = m.groupValues[2].trim()
                        }
                        if (name.isBlank()) continue
                        val searchStr = "$code $name $address $rawLbl ${VietnameseNormalizer.removeAccents(name)} ${VietnameseNormalizer.removeAccents(address)}"
                        stopsMap[sid] = BusStopEntity(
                            id = sid,
                            code = code,
                            name = name,
                            stopType = "Trạm dừng",
                            zone = address,
                            ward = null,
                            addressNo = "",
                            street = address,
                            supportDisability = false,
                            status = "Đang khai thác",
                            lat = 0.0,
                            lng = 0.0,
                            searchKeywords = rawLbl,
                            routesRaw = "",
                            normalizedSearch = VietnameseNormalizer.removeAccents(searchStr)
                        )
                    }
                } catch (_: Exception) {
                    // Autocomplete supplement is optional if variant stops already fetched cleanly
                }

                ensureActive()

                // Phase 5: Validate staged dataset
                val now = System.currentTimeMillis()
                val versionTag = "ebms-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
                val validation = StaticDatasetValidator.validateAndPackage(
                    datasetVersion = versionTag,
                    datasetCreatedAt = now,
                    source = EbmsApiService.BASE_URL,
                    routes = routeEntities,
                    variants = variantEntities,
                    stops = stopsMap.values.toList(),
                    crossRefs = crossRefs,
                    paths = pathEntities
                )

                when (validation) {
                    is DatasetValidationResult.Invalid -> {
                        return@withLock DatasetUpdateOutcome.Failed(
                            message = "Kiểm tra dữ liệu thất bại: ${validation.reason}. Dữ liệu cũ vẫn được giữ nguyên.",
                            preservedMetadata = currentMeta
                        )
                    }
                    is DatasetValidationResult.Valid -> {
                        val staged = validation.dataset
                        // Check if checksum is identical to active valid database
                        if (currentMeta != null &&
                            currentMeta.lastSyncStatus == DatasetSyncStatus.VALID &&
                            currentMeta.checksum == staged.checksum
                        ) {
                            val refreshedMeta = currentMetaEntity.copy(datasetUpdatedAt = now)
                            database.staticDatasetMetadataDao().upsertMetadata(refreshedMeta)
                            return@withLock DatasetUpdateOutcome.AlreadyUpToDate(refreshedMeta.toDomain())
                        }

                        // Phase 6: Atomic DB replacement (NonCancellable once commit begins)
                        progressFlow.value = DatasetSyncProgress(
                            phase = "Đang lưu dữ liệu an toàn vào thiết bị...",
                            completedSteps = 98,
                            totalSteps = 100,
                            isCancellable = false
                        )

                        val savedMeta = withContext(NonCancellable) {
                            commitValidatedDatasetAtomically(database, staged)
                        }
                        return@withLock DatasetUpdateOutcome.Updated(savedMeta.toDomain())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock DatasetUpdateOutcome.Failed(
                    message = "Cập nhật thất bại (${e.localizedMessage ?: "Lỗi mạng"}). Dữ liệu offline hiện tại vẫn được bảo toàn.",
                    preservedMetadata = currentMeta
                )
            } finally {
                progressFlow.value = null
            }
        }
    }

    private fun mapSeedToValidatedStaging(
        seed: StaticSeedDataset,
        json: Json
    ): ValidatedStagingDataset? {
        val routeEntities = seed.routes.map { r ->
            val searchContent = "${r.routeNo} ${r.name} ${VietnameseNormalizer.removeAccents(r.name)}"
            RouteEntity(
                id = r.id,
                routeNo = r.routeNo,
                name = r.name,
                color = r.color,
                type = r.type,
                distanceMeters = r.distanceMeters,
                headway = r.headway,
                operationTime = r.operationTime,
                orgs = r.orgs,
                tickets = r.tickets,
                outBoundDesc = r.outBoundDesc,
                inBoundDesc = r.inBoundDesc,
                totalTrips = r.totalTrips,
                normalizedSearch = VietnameseNormalizer.removeAccents(searchContent)
            )
        }

        val variantEntities = seed.variants.map { v ->
            RouteVariantEntity(
                id = v.id,
                routeId = v.routeId,
                name = v.name,
                shortName = v.shortName,
                routeNo = v.routeNo,
                startStop = v.startStop,
                endStop = v.endStop,
                distanceMeters = v.distanceMeters,
                isOutbound = v.isOutbound,
                runningTimeMinutes = v.runningTimeMinutes
            )
        }

        val stopEntities = seed.stops.map { s ->
            val searchContent = "${s.code} ${s.name} ${s.street} ${s.zone} ${s.searchKeywords} ${VietnameseNormalizer.removeAccents(s.name)} ${VietnameseNormalizer.removeAccents(s.street)}"
            BusStopEntity(
                id = s.id,
                code = s.code,
                name = s.name,
                stopType = s.stopType,
                zone = s.zone,
                ward = s.ward,
                addressNo = s.addressNo,
                street = s.street,
                supportDisability = s.supportDisability,
                status = s.status,
                lat = s.lat,
                lng = s.lng,
                searchKeywords = s.searchKeywords,
                routesRaw = s.routes.joinToString(", "),
                normalizedSearch = VietnameseNormalizer.removeAccents(searchContent)
            )
        }

        val crossRefs = seed.crossRefs.map { x ->
            RouteStopCrossReference(
                routeId = x.routeId,
                varId = x.varId,
                stopId = x.stopId,
                orderIndex = x.orderIndex
            )
        }

        val pathEntities = seed.paths.map { p ->
            RoutePathEntity(
                compositeKey = "${p.routeId}_${p.varId}",
                routeId = p.routeId,
                varId = p.varId,
                latsJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<Double>()), p.lats),
                lngsJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<Double>()), p.lngs),
                pointCount = minOf(p.lats.size, p.lngs.size),
                cachedAtTimestamp = seed.datasetCreatedAt
            )
        }

        val result = StaticDatasetValidator.validateAndPackage(
            datasetVersion = seed.datasetVersion,
            datasetCreatedAt = seed.datasetCreatedAt,
            source = seed.source,
            routes = routeEntities,
            variants = variantEntities,
            stops = stopEntities,
            crossRefs = crossRefs,
            paths = pathEntities
        )

        return (result as? DatasetValidationResult.Valid)?.dataset
    }

    internal suspend fun commitValidatedDatasetAtomically(
        database: BusTrackerDatabase,
        staged: ValidatedStagingDataset
    ): StaticDatasetMetadataEntity {
        val now = System.currentTimeMillis()
        val metadata = StaticDatasetMetadataEntity(
            id = StaticDatasetMetadataEntity.ACTIVE_ID,
            datasetVersion = staged.datasetVersion,
            datasetCreatedAt = staged.datasetCreatedAt,
            datasetUpdatedAt = now,
            source = staged.source,
            totalRoutes = staged.routes.size,
            totalVariants = staged.variants.size,
            totalStops = staged.stops.size,
            totalPolylines = staged.paths.size,
            checksum = staged.checksum,
            lastSyncStatus = DatasetSyncStatus.VALID.name,
            lastErrorMessage = null
        )

        database.withTransaction {
            // 1. Replace ONLY static transit tables (NEVER touch favorite_stops or favorite_routes)
            database.routePathDao().clearAllPaths()
            database.busStopDao().clearAllRouteStopRefs()
            database.routeVariantDao().clearAllVariants()
            database.busStopDao().clearAllStops()
            database.routeDao().clearAllRoutes()

            // 2. Chunked bulk inserts for SQLite bind-parameter compatibility across Android 8+
            staged.routes.chunked(500).forEach { database.routeDao().insertRoutes(it) }
            staged.variants.chunked(500).forEach { database.routeVariantDao().insertVariants(it) }
            staged.stops.chunked(500).forEach { database.busStopDao().insertStops(it) }
            staged.crossRefs.chunked(1000).forEach { database.busStopDao().insertRouteStopRefs(it) }
            staged.paths.chunked(100).forEach { database.routePathDao().insertPaths(it) }

            // 3. Record metadata in the same transaction
            database.staticDatasetMetadataDao().upsertMetadata(metadata)
            database.syncMetadataDao().recordSync(
                SyncMetadataEntity(
                    syncType = "ROUTES",
                    lastSyncTimestamp = now,
                    itemCount = staged.routes.size,
                    isSuccess = true
                )
            )
            database.syncMetadataDao().recordSync(
                SyncMetadataEntity(
                    syncType = "STOPS",
                    lastSyncTimestamp = now,
                    itemCount = staged.stops.size,
                    isSuccess = true
                )
            )
        }
        return metadata
    }

    private fun isNetworkConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (_: SecurityException) {
            true
        } catch (_: LinkageError) {
            true
        } catch (_: Exception) {
            true
        }
    }
}
