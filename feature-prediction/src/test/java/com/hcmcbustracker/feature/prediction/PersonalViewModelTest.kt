package com.hcmcbustracker.feature.prediction

import app.cash.turbine.test
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.AppThemeMode
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.DatasetSyncStatus
import com.hcmcbustracker.core.model.DatasetUpdateOutcome
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.StaticDatasetMetadata
import com.hcmcbustracker.core.model.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BusRepository>(relaxed = true)
    private lateinit var viewModel: PersonalViewModel

    private val sampleFavorites = listOf(
        BusStop(
            id = 142,
            code = "Q1 001",
            name = "Bến xe buýt Sài Gòn",
            stopType = "Bến bãi",
            zone = "Quận 1",
            addressNo = "Lê Lai",
            street = "Lê Lai",
            supportDisability = true,
            status = "Active",
            lat = 10.7684,
            lng = 106.6942,
            searchKeywords = "ben xe buyt sai gon le lai quan 1",
            routes = listOf("01", "150")
        )
    )

    private val sampleFavoriteRoutes = listOf(
        Route(
            id = 1,
            routeNo = "01",
            name = "Bến Thành - Chợ Lớn"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getFavoriteStops() } returns flowOf(sampleFavorites)
        every { repository.getFavoriteRoutes() } returns flowOf(sampleFavoriteRoutes)
        every { repository.getSearchHistory() } returns flowOf(emptyList())
        every { repository.getUserPreferences() } returns flowOf(UserPreferences())
        coEvery { repository.getLastSyncTimestamp("ROUTES") } returns 1700000000L

        viewModel = PersonalViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads favorite stops routes and last sync time`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.favoriteStops.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(1, state.favoriteStops.size)
            assertEquals(1, state.favoriteRoutes.size)
            assertEquals("01", state.favoriteRoutes[0].routeNo)
            assertEquals(1700000000L, state.lastSyncTimestamp)
            assertEquals(AppThemeMode.SYSTEM, state.userPreferences.themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tab selection changes active tab`() = runTest {
        viewModel.selectTab(PersonalTab.ROUTES)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.selectedTab != PersonalTab.ROUTES) {
                state = awaitItem()
            }
            assertEquals(PersonalTab.ROUTES, state.selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeFavoriteStop calls repository toggleFavoriteStop`() = runTest {
        viewModel.removeFavoriteStop(142)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.toggleFavoriteStop(142) }
    }

    @Test
    fun `removeFavoriteRoute calls repository toggleFavoriteRoute`() = runTest {
        viewModel.removeFavoriteRoute(1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.toggleFavoriteRoute(1) }
    }

    @Test
    fun `settings update actions call repository`() = runTest {
        viewModel.setThemeMode(AppThemeMode.DARK)
        viewModel.setPollingIntervalSeconds(30)
        viewModel.setNotificationsEnabled(false)
        viewModel.clearCache()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setThemeMode(AppThemeMode.DARK) }
        coVerify { repository.setPollingIntervalSeconds(30) }
        coVerify { repository.setNotificationsEnabled(false) }
        coVerify { repository.clearCache() }
    }

    @Test
    fun `syncAllData triggers syncRoutes and syncStops`() = runTest {
        coEvery { repository.syncRoutes() } returns NetworkResult.Success(136)
        coEvery { repository.syncStops() } returns NetworkResult.Success(6414)

        viewModel.syncAllData()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.syncRoutes() }
        coVerify { repository.syncStops() }
    }

    @Test
    fun `updateFullStaticDataset Updated reports counts cleanly`() = runTest {
        val sampleMeta = StaticDatasetMetadata(
            datasetVersion = "ebms-20261112",
            datasetCreatedAt = 1000L,
            datasetUpdatedAt = 2000L,
            source = "EBMS Live",
            totalRoutes = 181,
            totalVariants = 338,
            totalStops = 6412,
            totalPolylines = 338,
            checksum = "sha256:abc",
            lastSyncStatus = DatasetSyncStatus.VALID
        )
        coEvery { repository.updateFullStaticDatasetFromEbms() } returns DatasetUpdateOutcome.Updated(sampleMeta)

        viewModel.uiState.test {
            awaitItem()
            viewModel.updateFullStaticDataset()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(
                "Đã cập nhật hoàn tất: 181 tuyến, 338 chiều, 6412 trạm, 338 lộ trình.",
                state.syncSuccessMessage
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateFullStaticDataset AlreadyUpToDate reports no-op message`() = runTest {
        val sampleMeta = StaticDatasetMetadata(
            datasetVersion = "ebms-20261112",
            datasetCreatedAt = 1000L,
            datasetUpdatedAt = 2000L,
            source = "EBMS Seed",
            totalRoutes = 181,
            totalVariants = 338,
            totalStops = 6412,
            totalPolylines = 338,
            checksum = "sha256:same",
            lastSyncStatus = DatasetSyncStatus.VALID
        )
        coEvery { repository.updateFullStaticDatasetFromEbms() } returns DatasetUpdateOutcome.AlreadyUpToDate(sampleMeta)

        viewModel.uiState.test {
            awaitItem()
            viewModel.updateFullStaticDataset()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(
                "Dữ liệu đã mới nhất (không cần ghi lại database).",
                state.syncSuccessMessage
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateFullStaticDataset Failed exposes preserved error message`() = runTest {
        coEvery { repository.updateFullStaticDatasetFromEbms() } returns DatasetUpdateOutcome.Failed(
            message = "Dữ liệu tải về không vượt qua kiểm tra toàn vẹn",
            preservedMetadata = null
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.updateFullStaticDataset()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals("Dữ liệu tải về không vượt qua kiểm tra toàn vẹn", state.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search history actions call repository`() = runTest {
        viewModel.removeSearchHistoryItem("01")
        viewModel.clearSearchHistory()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.removeSearchHistoryItem("01") }
        coVerify { repository.clearSearchHistory() }
    }

    @Test
    fun `toggleStopNotification calls repository`() = runTest {
        viewModel.toggleStopNotification(142, enabled = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.toggleStopNotification(142, false) }
    }
}
