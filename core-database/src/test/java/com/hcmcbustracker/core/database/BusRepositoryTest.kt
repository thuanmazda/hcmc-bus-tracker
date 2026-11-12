package com.hcmcbustracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hcmcbustracker.core.database.entity.RouteEntity
import com.hcmcbustracker.core.database.entity.RoutePathEntity
import com.hcmcbustracker.core.database.repository.BusRepositoryImpl
import com.hcmcbustracker.core.model.AppThemeMode
import com.hcmcbustracker.core.network.api.EbmsApiService
import com.hcmcbustracker.core.network.dto.RouteComboDto
import com.hcmcbustracker.core.network.dto.StopAutocompleteDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BusRepositoryTest {

    private lateinit var database: BusTrackerDatabase
    private lateinit var apiService: EbmsApiService
    private lateinit var repository: BusRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun syncRoutes_success_savesToDatabase() = runTest {
        coEvery { apiService.getRoutesForCombo() } returns listOf(
            RouteComboDto(value = "1", lable = "[01] Ga Ba Son - Bến xe buýt Chợ Lớn"),
            RouteComboDto(value = "3", lable = "[03] Bến Thành - Thạnh Xuân")
        )

        val result = repository.syncRoutes()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        val routes = repository.getAllRoutes().first()
        assertEquals(2, routes.size)
        assertEquals("01", routes[0].routeNo)
    }

    @Test
    fun syncRoutes_failure_preservesExistingData() = runTest {
        // Pre-insert an existing route
        database.routeDao().insertRoutes(
            listOf(
                RouteEntity(
                    id = 99,
                    routeNo = "99",
                    name = "Tuyến hiện hữu",
                    normalizedSearch = "99 tuyen hien huu"
                )
            )
        )

        // API fails
        coEvery { apiService.getRoutesForCombo() } throws IOException("Network unreachable")

        val result = repository.syncRoutes()
        assertTrue(result.isError)

        // Existing data must NOT be wiped
        val routes = repository.getAllRoutes().first()
        assertEquals(1, routes.size)
        assertEquals("99", routes[0].routeNo)
    }

    @Test
    fun syncStops_success_parsesAndSavesAllStops() = runTest {
        coEvery { apiService.getStopsForAutocomplete() } returns StopAutocompleteDto(
            l = listOf("BBD 21 TDT", "NbHH 385 CMTT"),
            v = listOf(
                "[Q1 031] Bến Bạch Đằng (21 Tôn Đức Thắng, Quận 1)",
                "[Q3 045] Ngã ba Hòa Hưng (385 CMTT, Quận 3)"
            ),
            g = listOf(79, 385)
        )

        val result = repository.syncStops()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        val stopCount = database.busStopDao().getTotalStopCount()
        assertEquals(2, stopCount)

        val stop79 = database.busStopDao().getStopByIdOnce(79)
        assertEquals("Q1 031", stop79?.code)
        assertEquals("Bến Bạch Đằng", stop79?.name)
    }

    @Test
    fun userPreferences_and_themeMode_persistAndEmit() = runTest {
        val initialPrefs = repository.getUserPreferences().first()
        assertEquals(AppThemeMode.SYSTEM, initialPrefs.themeMode)
        assertEquals(15, initialPrefs.pollingIntervalSeconds)
        assertTrue(initialPrefs.notificationsEnabled)

        repository.setThemeMode(AppThemeMode.DARK)
        repository.setPollingIntervalSeconds(30)
        repository.setNotificationsEnabled(false)

        val updatedPrefs = repository.getUserPreferences().first()
        assertEquals(AppThemeMode.DARK, updatedPrefs.themeMode)
        assertEquals(30, updatedPrefs.pollingIntervalSeconds)
        assertEquals(false, updatedPrefs.notificationsEnabled)
    }

    @Test
    fun favoriteRoutes_toggleAndRetrieve() = runTest {
        val route1 = RouteEntity(
            id = 1,
            routeNo = "01",
            name = "Bến Thành - Chợ Lớn",
            normalizedSearch = "01 ben thanh cho lon"
        )
        database.routeDao().insertRoutes(listOf(route1))

        var isFav = repository.isRouteFavorite(1).first()
        assertEquals(false, isFav)

        repository.toggleFavoriteRoute(1)
        isFav = repository.isRouteFavorite(1).first()
        assertEquals(true, isFav)

        val favRoutes = repository.getFavoriteRoutes().first()
        assertEquals(1, favRoutes.size)
        assertEquals("01", favRoutes[0].routeNo)

        repository.toggleFavoriteRoute(1)
        isFav = repository.isRouteFavorite(1).first()
        assertEquals(false, isFav)
    }

    @Test
    fun clearCache_removesAllRoutePaths() = runTest {
        val path = RoutePathEntity(
            compositeKey = "1_1",
            routeId = 1,
            varId = 1,
            latsJson = "[10.77]",
            lngsJson = "[106.69]",
            pointCount = 1
        )
        database.routePathDao().insertPath(path)
        val beforeClear = database.routePathDao().getPathOnce("1_1")
        assertTrue(beforeClear != null)

        repository.clearCache()

        val afterClear = database.routePathDao().getPathOnce("1_1")
        assertEquals(null, afterClear)
    }
}
