package com.hcmcbustracker.feature.route

import app.cash.turbine.test
import com.hcmcbustracker.core.database.repository.BusRepository
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
class RoutesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BusRepository>(relaxed = true)
    private lateinit var viewModel: RoutesViewModel

    private val sampleRoutes = listOf(
        Route(
            id = 4,
            routeNo = "D4",
            name = "Vinhomes Grand Park - Bến xe buýt Sài Gòn",
            type = "Phổ thông - Có trợ giá",
            orgs = "VinBus"
        ),
        Route(
            id = 150,
            routeNo = "150",
            name = "Bến xe Chợ Lớn - Ngã 3 Tân Vạn",
            type = "Phổ thông - Có trợ giá"
        ),
        Route(
            id = 1,
            routeNo = "01",
            name = "Bến Thành - Bến xe Chợ Lớn",
            type = "Phổ thông - Có trợ giá"
        ),
        Route(
            id = 72,
            routeNo = "72-1",
            name = "Sân bay Tân Sơn Nhất - Bến xe Vũng Tàu",
            type = "Phổ thông - Không trợ giá"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAllRoutes() } returns flowOf(sampleRoutes)
        every { repository.getFavoriteRoutes() } returns flowOf(emptyList())
        every { repository.searchRoutes("150") } returns flowOf(listOf(sampleRoutes[1]))
        coEvery { repository.getRouteCount() } returns 4

        viewModel = RoutesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads all routes in natural sorted order where D4 does not precede 01 and 150`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()
            var state = awaitItem()
            if (state.routes.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(4, state.routes.size)
            // Natural sort order: "01", "150", "72-1", "D4"
            assertEquals("01", state.routes[0].routeNo)
            assertEquals("150", state.routes[1].routeNo)
            assertEquals("72-1", state.routes[2].routeNo)
            assertEquals("D4", state.routes[3].routeNo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter by SUBSIDIZED retains only subsidized routes`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // Initial

            viewModel.onFilterSelected(RouteFilterType.SUBSIDIZED)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(RouteFilterType.SUBSIDIZED, state.selectedFilter)
            assertEquals(3, state.routes.size)
            assertTrue(state.routes.all { it.type?.contains("Có trợ giá") == true })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter by NON_SUBSIDIZED retains only non-subsidized routes`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()

            viewModel.onFilterSelected(RouteFilterType.NON_SUBSIDIZED)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(RouteFilterType.NON_SUBSIDIZED, state.selectedFilter)
            assertEquals(1, state.routes.size)
            assertEquals("72-1", state.routes[0].routeNo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter by ELECTRIC retains VinBus and electric bus routes`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()

            viewModel.onFilterSelected(RouteFilterType.ELECTRIC)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(RouteFilterType.ELECTRIC, state.selectedFilter)
            assertEquals(1, state.routes.size)
            assertEquals("D4", state.routes[0].routeNo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onQueryChanged updates query and filters routes`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("150")
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            var state = awaitItem()
            while (state.query != "150") {
                state = awaitItem()
            }
            assertEquals("150", state.query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite calls repository toggleFavoriteRoute`() = runTest {
        viewModel.toggleFavorite(1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.toggleFavoriteRoute(1) }
    }

    @Test
    fun `syncRoutes calls repository syncRoutes`() = runTest {
        coEvery { repository.syncRoutes() } returns NetworkResult.Success(136)

        viewModel.syncRoutes()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.syncRoutes() }
    }
}
