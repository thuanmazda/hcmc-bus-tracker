package com.hcmcbustracker.core.database

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.hcmcbustracker.core.database.di.MIGRATION_2_3
import com.hcmcbustracker.core.database.entity.BusStopEntity
import com.hcmcbustracker.core.database.entity.FavoriteRouteEntity
import com.hcmcbustracker.core.database.entity.FavoriteStopEntity
import com.hcmcbustracker.core.database.entity.RouteEntity
import com.hcmcbustracker.core.database.entity.RoutePathEntity
import com.hcmcbustracker.core.database.entity.RouteStopCrossReference
import com.hcmcbustracker.core.database.entity.RouteVariantEntity
import com.hcmcbustracker.core.database.repository.BusRepositoryImpl
import com.hcmcbustracker.core.database.seed.DatasetValidationResult
import com.hcmcbustracker.core.database.seed.StaticDatasetManager
import com.hcmcbustracker.core.database.seed.StaticDatasetValidator
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.DatasetSyncStatus
import com.hcmcbustracker.core.model.DatasetUpdateOutcome
import com.hcmcbustracker.core.network.api.EbmsApiService
import com.hcmcbustracker.core.network.dto.ArrivalVehicleDto
import com.hcmcbustracker.core.network.dto.BusStopDto
import com.hcmcbustracker.core.network.dto.PredictionDto
import com.hcmcbustracker.core.network.dto.RouteComboDto
import com.hcmcbustracker.core.network.dto.RouteDetailDto
import com.hcmcbustracker.core.network.dto.RoutePathDto
import com.hcmcbustracker.core.network.dto.RouteVarDto
import com.hcmcbustracker.core.network.dto.StopAutocompleteDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class StaticDatasetManagerTest {

    private lateinit var context: Context
    private lateinit var database: BusTrackerDatabase
    private lateinit var apiService: EbmsApiService
    private lateinit var repository: BusRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetwork?.let { network ->
            val caps = ShadowNetworkCapabilities.newInstance()
            shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(cm).setNetworkCapabilities(network, caps)
        }
        database = Room.inMemoryDatabaseBuilder(context, BusTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        apiService = mockk()
        repository = BusRepositoryImpl(database, apiService, json, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun validator_rejectsEmptyOrCorruptedData() {
        val invalidRoutes = StaticDatasetValidator.validateAndPackage(
            datasetVersion = "v1",
            datasetCreatedAt = 1000L,
            source = "EBMS",
            routes = emptyList(),
            variants = emptyList(),
            stops = emptyList(),
            crossRefs = emptyList(),
            paths = emptyList()
        )
        assertTrue(invalidRoutes is DatasetValidationResult.Invalid)
    }

    @Test
    fun validator_acceptsConDaoCoordinatesWithinExpandedRegionBounds() {
        val route = RouteEntity(173, "173", "Đặc khu Côn Đảo", normalizedSearch = "173 dac khu con dao")
        val variant = RouteVariantEntity(1731, 173, "Lượt đi", "Côn Đảo", "173", "A", "B", 5000.0, true, 20)
        val conDaoStop = BusStopEntity(9501, "CD01", "Chợ Côn Đảo", "Bến xe", "Đặc khu Côn Đảo", null, "", "Phạm Văn Đồng", false, "Đang khai thác", 8.686167, 106.608715, "", "173", "cd01 cho con dao")
        val ref = RouteStopCrossReference(routeId = 173, varId = 1731, stopId = 9501, orderIndex = 0)
        val path = RoutePathEntity("173_1731", 173, 1731, "[8.6861,8.6900]", "[106.6087,106.6120]", 2)

        val res = StaticDatasetValidator.validateAndPackage(
            datasetVersion = "v1",
            datasetCreatedAt = 1000L,
            source = "EBMS",
            routes = listOf(route),
            variants = listOf(variant),
            stops = listOf(conDaoStop),
            crossRefs = listOf(ref),
            paths = listOf(path)
        )
        assertTrue(res is DatasetValidationResult.Valid)
    }

    @Test
    fun commitValidatedDatasetAtomically_preservesUserFavoritesAndRecordsValidMetadata() = runTest {
        // 1. Pre-populate user favorites
        val oldStop = BusStopEntity(4223, "Q1", "Hàm Nghi", "", "", null, "", "", false, "", 10.77, 106.69, "", "", "ham nghi")
        val oldRoute = RouteEntity(1, "01", "Bến Thành - Chợ Lớn", normalizedSearch = "01 ben thanh")
        database.busStopDao().insertStops(listOf(oldStop))
        database.routeDao().insertRoutes(listOf(oldRoute))
        database.favoriteStopDao().addFavorite(FavoriteStopEntity(4223))
        database.favoriteRouteDao().addFavorite(FavoriteRouteEntity(1))

        // 2. Prepare valid staged dataset
        val variant = RouteVariantEntity(101, 1, "Lượt đi", "Chợ Lớn", "01", "Bến Thành", "Chợ Lớn", 8500.0, true, 35)
        val crossRef = RouteStopCrossReference(routeId = 1, varId = 101, stopId = 4223, orderIndex = 0)
        val path = RoutePathEntity("1_101", 1, 101, "[10.77,10.75]", "[106.69,106.65]", 2)

        val valid = (StaticDatasetValidator.validateAndPackage(
            datasetVersion = "ebms-test-v1",
            datasetCreatedAt = 2000L,
            source = "https://apicms.ebms.vn",
            routes = listOf(oldRoute),
            variants = listOf(variant),
            stops = listOf(oldStop),
            crossRefs = listOf(crossRef),
            paths = listOf(path)
        ) as DatasetValidationResult.Valid).dataset

        StaticDatasetManager.commitValidatedDatasetAtomically(database, valid)

        // 3. Verify metadata & static tables
        val meta = repository.getStaticDatasetMetadata()
        assertNotNull(meta)
        assertEquals(DatasetSyncStatus.VALID, meta?.lastSyncStatus)
        assertEquals("ebms-test-v1", meta?.datasetVersion)
        assertEquals(1, meta?.totalRoutes)
        assertEquals(1, meta?.totalPolylines)

        // 4. Verify favorites are strictly preserved!
        val favStops = repository.getFavoriteStops().first()
        val favRoutes = repository.getFavoriteRoutes().first()
        assertEquals(1, favStops.size)
        assertEquals(4223, favStops[0].id)
        assertEquals(1, favRoutes.size)
        assertEquals(1, favRoutes[0].id)
    }

    @Test
    fun updateFullStaticDatasetFromEbms_whenNetworkFailsMidway_preservesExistingDatabase() = runTest {
        // Seed initial valid dataset
        val route = RouteEntity(1, "01", "Bến Thành - Chợ Lớn", normalizedSearch = "01 ben thanh")
        val variant = RouteVariantEntity(1, 1, "Đi", "Chợ Lớn", "01", "A", "B", 8500.0, true, 35)
        val stop = BusStopEntity(142, "BX01", "Bến xe Sài Gòn", "", "", null, "", "", false, "", 10.768, 106.694, "", "01", "sai gon")
        val ref = RouteStopCrossReference(routeId = 1, varId = 1, stopId = 142, orderIndex = 0)
        val path = RoutePathEntity("1_1", 1, 1, "[10.768,10.753]", "[106.694,106.654]", 2)

        val valid = (StaticDatasetValidator.validateAndPackage(
            "initial-v1", 1000L, "EBMS", listOf(route), listOf(variant), listOf(stop), listOf(ref), listOf(path)
        ) as DatasetValidationResult.Valid).dataset
        StaticDatasetManager.commitValidatedDatasetAtomically(database, valid)

        // Mock EBMS failing midway while downloading variant paths
        coEvery { apiService.getRoutesForCombo() } returns listOf(RouteComboDto("1", "[01] Ga Ba Son - Cho Lon"))
        coEvery { apiService.getRouteById(1) } returns RouteDetailDto(1, "01", "Ga Ba Son - Cho Lon")
        coEvery { apiService.getVarsByRoute(1) } returns listOf(RouteVarDto(1, 1, "Đi", "CL", "01", "A", "B", 8500.0, true, 35))
        coEvery { apiService.getStopsByVar(1, 1) } throws IOException("Connection dropped mid-sync")

        val outcome = repository.updateFullStaticDatasetFromEbms()
        assertTrue(outcome is DatasetUpdateOutcome.Failed)

        // Verify original valid dataset is STILL intact and VALID!
        val meta = repository.getStaticDatasetMetadata()
        assertEquals("initial-v1", meta?.datasetVersion)
        assertEquals(DatasetSyncStatus.VALID, meta?.lastSyncStatus)
        assertEquals(1, database.routeDao().getRouteCount())
        assertEquals(1, database.routePathDao().getPathCount())
    }

    @Test
    fun updateFullStaticDatasetFromEbms_whenUnchangedChecksum_returnsAlreadyUpToDate() = runTest {
        coEvery { apiService.getRoutesForCombo() } returns listOf(RouteComboDto("1", "[01] Bến Thành - Chợ Lớn"))
        coEvery { apiService.getRouteById(1) } returns RouteDetailDto(1, "01", "Bến Thành - Chợ Lớn")
        coEvery { apiService.getVarsByRoute(1) } returns listOf(RouteVarDto(1, 1, "Đi", "Chợ Lớn", "01", "A", "B", 8500.0, true, 35))
        coEvery { apiService.getStopsByVar(1, 1) } returns listOf(BusStopDto(142, "S1", "Trạm 1", lat = 10.77, lng = 106.69))
        coEvery { apiService.getPathsByVar(1, 1) } returns RoutePathDto(listOf(10.77, 10.75), listOf(106.69, 106.65))
        coEvery { apiService.getStopsForAutocomplete() } returns StopAutocompleteDto()

        val firstOutcome = repository.updateFullStaticDatasetFromEbms()
        assertTrue("Expected Updated but got $firstOutcome", firstOutcome is DatasetUpdateOutcome.Updated)

        val secondOutcome = repository.updateFullStaticDatasetFromEbms()
        assertTrue("Expected AlreadyUpToDate but got $secondOutcome", secondOutcome is DatasetUpdateOutcome.AlreadyUpToDate)
    }

    @Test
    fun migration_2_to_3_preservesExistingUserFavoritesAndAddsStaticMetadataTable() {
        val dbName = "test_migration_2_3.db"
        context.deleteDatabase(dbName)

        // Manually build a v2 SQLite database
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `routes` (`id` INTEGER NOT NULL PRIMARY KEY, `routeNo` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT, `type` TEXT, `distanceMeters` REAL, `headway` TEXT, `operationTime` TEXT, `orgs` TEXT, `tickets` TEXT, `outBoundDesc` TEXT, `inBoundDesc` TEXT, `totalTrips` TEXT, `normalizedSearch` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `route_variants` (`id` INTEGER NOT NULL PRIMARY KEY, `routeId` INTEGER NOT NULL, `name` TEXT NOT NULL, `shortName` TEXT NOT NULL, `routeNo` TEXT NOT NULL, `startStop` TEXT NOT NULL, `endStop` TEXT NOT NULL, `distanceMeters` REAL NOT NULL, `isOutbound` INTEGER NOT NULL, `runningTimeMinutes` INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_variants_routeId` ON `route_variants` (`routeId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_variants_routeNo` ON `route_variants` (`routeNo`)")
                        db.execSQL("INSERT INTO `route_variants` VALUES (1, 1, 'Out', 'Out', '01', 'A', 'B', 1000.0, 1, 30)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_routes` (`routeId` INTEGER NOT NULL PRIMARY KEY, `addedAtTimestamp` INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO `favorite_routes` VALUES (1, 123456)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val v2Db = helper.writableDatabase
        MIGRATION_2_3.migrate(v2Db)

        // Check static_dataset_metadata table created cleanly
        val metaCursor = v2Db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='static_dataset_metadata'")
        assertTrue(metaCursor.moveToFirst())
        metaCursor.close()

        // Verify route_variants now accepts same varId=1 for a different routeId=2 (composite PK routeId, id)
        v2Db.execSQL("INSERT INTO `route_variants` VALUES (1, 2, 'Out', 'Out', '02', 'C', 'D', 2000.0, 1, 40)")
        val rvCursor = v2Db.query("SELECT COUNT(*) FROM `route_variants`")
        assertTrue(rvCursor.moveToFirst())
        assertEquals(2, rvCursor.getInt(0))
        rvCursor.close()

        // Check favorite_routes data untouched
        val favCursor = v2Db.query("SELECT routeId FROM favorite_routes")
        assertTrue(favCursor.moveToFirst())
        assertEquals(1, favCursor.getInt(0))
        favCursor.close()

        v2Db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun fetchRouteVariantVehicles_handlesCheckpointsDeduplicationAndHttp429RetryAfter() = runTest {
        val stops = (1..10).map { idx ->
            BusStop(idx, "C$idx", "Stop $idx", "", "", null, "", "", false, "", 10.7 + idx * 0.001, 106.6, "", listOf("01"))
        }

        // Mock 429 on first check
        val response429 = Response.error<List<PredictionDto>>(
            429,
            "{}".toResponseBody("application/json".toMediaType())
        )
        val rawResp = okhttp3.Response.Builder()
            .code(429)
            .message("Too Many Requests")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://apicms.ebms.vn/prediction/predictbystopid/1").build())
            .header("Retry-After", "45")
            .build()
        val exception429 = HttpException(Response.error<List<PredictionDto>>(response429.errorBody()!!, rawResp))

        coEvery { apiService.getPredictionByStopId(any()) } throws exception429

        val errRes = repository.fetchRouteVariantVehicles(1, 1, stops)
        assertTrue(errRes is com.hcmcbustracker.core.model.NetworkResult.Error)
        val err = errRes as com.hcmcbustracker.core.model.NetworkResult.Error
        assertEquals(429, err.code)
        assertEquals(45L, err.retryAfterSeconds)

        // Mock valid deduplicated checkpoint responses
        coEvery { apiService.getPredictionByStopId(any()) } answers {
            val sid = firstArg<Int>()
            listOf(
                PredictionDto(
                    routeId = 1,
                    routeName = "Route 01",
                    routeNo = "01",
                    stopId = sid,
                    stopName = "Stop $sid",
                    varId = 1,
                    destinationName = "Chợ Lớn",
                    arrivals = listOf(
                        ArrivalVehicleDto("50E61577", 120 + sid * 10, 500.0 + sid * 100.0, 20.0, "2026-11-12T12:19:04+07:00", 0)
                    )
                )
            )
        }

        val successRes = repository.fetchRouteVariantVehicles(1, 1, stops)
        assertTrue(successRes.isSuccess)
        val dedup = successRes.getOrNull()!!
        assertEquals(1, dedup.vehicles.size)
        assertEquals("50E61577", dedup.vehicles[0].plateNumber)
        assertTrue(dedup.duplicatesRemoved >= 1)
    }

    @Test
    fun packagedEbmsSeedAsset_parsesValidatesAndSeedsFullTopologyWithinBenchmark() = runTest {
        val assetFile = listOf(
            java.io.File("src/main/assets/ebms_static_dataset.json.gz"),
            java.io.File("core-database/src/main/assets/ebms_static_dataset.json.gz")
        ).firstOrNull { it.exists() }
        assertNotNull("Packaged seed asset must exist for offline distribution", assetFile)

        val progress = MutableStateFlow<com.hcmcbustracker.core.model.DatasetSyncProgress?>(null)
        val startMs = System.currentTimeMillis()
        val seeded = assetFile!!.inputStream().use { stream ->
            StaticDatasetManager.seedFromGzipStream(database, stream, json, progress)
        }
        val elapsedMs = System.currentTimeMillis() - startMs
        println("BENCHMARK_REAL_SEED_MS=$elapsedMs assetBytes=${assetFile.length()}")

        assertTrue("Packaged EBMS seed dataset must pass validator and commit cleanly", seeded)
        val meta = database.staticDatasetMetadataDao().getMetadataOnce()
        assertNotNull(meta)
        assertEquals(DatasetSyncStatus.VALID.name, meta?.lastSyncStatus)
        assertEquals(181, database.routeDao().getRouteCount())
        assertEquals(338, database.routeVariantDao().getVariantCount())
        assertEquals(6412, database.busStopDao().getTotalStopCount())
        assertEquals(338, database.routePathDao().getPathCount())
        assertTrue("Room seed time (${elapsedMs}ms) should complete reasonably quickly", elapsedMs < 30_000L)

        // Also verify AAPT2-unpacked plain JSON stream path works seamlessly
        val plainJsonBytes = assetFile.inputStream().use { raw ->
            java.util.zip.GZIPInputStream(raw).readBytes()
        }
        val plainSeeded = plainJsonBytes.inputStream().use { stream ->
            StaticDatasetManager.seedFromGzipStream(database, stream, json, progress)
        }
        assertTrue("AAPT2-unpacked plain JSON asset stream must also seed cleanly", plainSeeded)
        assertEquals(338, database.routeVariantDao().getVariantCount())
    }
}
