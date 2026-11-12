package com.hcmcbustracker.feature.search

import app.cash.turbine.test
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BusRepository>(relaxed = true)
    private lateinit var viewModel: SearchViewModel

    private val dummyRoutes = listOf(
        Route(
            id = 1,
            routeNo = "01",
            name = "Bến Thành - Chợ Lớn",
            outBoundDesc = "Bến Thành đi Chợ Lớn",
            inBoundDesc = "Chợ Lớn đi Bến Thành",
            color = "#1E88E5"
        )
    )

    private val dummyStops = listOf(
        BusStop(
            id = 79,
            code = "Q1 031",
            name = "Bến Bạch Đằng",
            stopType = "Bến bãi",
            zone = "Quận 1",
            addressNo = "21",
            street = "Tôn Đức Thắng",
            supportDisability = true,
            status = "Active",
            lat = 10.77,
            lng = 106.70,
            searchKeywords = "ben bach dang ton duc thang quan 1",
            routes = listOf("01")
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAllRoutes() } returns flowOf(dummyRoutes)
        every { repository.searchRoutes("01") } returns flowOf(dummyRoutes)
        every { repository.searchStops("Bạch Đằng") } returns flowOf(dummyStops)
        every { repository.getFavoriteRoutes() } returns flowOf(dummyRoutes)
        every { repository.getFavoriteStops() } returns flowOf(dummyStops)
        coEvery { repository.getLastSyncTimestamp("ROUTES") } returns 1700000000L

        viewModel = SearchViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial tab should be ROUTES with favorites synchronized`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            var state = awaitItem()
            while (state.routes.isEmpty() || state.favoriteRouteIds.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(SearchTab.ROUTES, state.selectedTab)
            assertEquals(1, state.routes.size)
            assertTrue(state.favoriteRouteIds.contains(1))
            assertTrue(state.favoriteStopIds.contains(79))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching tab updates selectedTab in uiState`() = runTest {
        viewModel.onTabSelected(SearchTab.STOPS)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.selectedTab != SearchTab.STOPS) {
                state = awaitItem()
            }
            assertEquals(SearchTab.STOPS, state.selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query changed debounces 300ms and filters routes`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("01")
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            var state = awaitItem()
            while (state.query != "01") {
                state = awaitItem()
            }
            assertEquals("01", state.query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavoriteRoute and toggleFavoriteStop call repository`() = runTest {
        viewModel.toggleFavoriteRoute(1)
        viewModel.toggleFavoriteStop(79)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.toggleFavoriteRoute(1) }
        coVerify { repository.toggleFavoriteStop(79) }
    }

    @Test
    fun `triggerSync calls repository syncRoutes and syncStops`() = runTest {
        coEvery { repository.syncRoutes() } returns NetworkResult.Success(100)
        coEvery { repository.syncStops() } returns NetworkResult.Success(500)

        viewModel.triggerSync()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.syncRoutes() }
        coVerify { repository.syncStops() }
    }
}
