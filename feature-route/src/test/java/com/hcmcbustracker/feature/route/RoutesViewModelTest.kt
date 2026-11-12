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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BusRepository>(relaxed = true)
    private lateinit var viewModel: RoutesViewModel

    private val sampleRoutes = listOf(
        Route(
            id = 1,
            routeNo = "01",
            name = "Bến Thành - Bến xe Chợ Lớn",
            outBoundDesc = "Bến xe buýt Sài Gòn - Chợ Lớn",
            inBoundDesc = "Chợ Lớn - Bến xe buýt Sài Gòn"
        ),
        Route(
            id = 150,
            routeNo = "150",
            name = "Bến xe Chợ Lớn - Ngã 3 Tân Vạn",
            outBoundDesc = "Chợ Lớn - Tân Vạn",
            inBoundDesc = "Tân Vạn - Chợ Lớn"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAllRoutes() } returns flowOf(sampleRoutes)
        every { repository.getFavoriteRoutes() } returns flowOf(emptyList())
        every { repository.searchRoutes("150") } returns flowOf(listOf(sampleRoutes[1]))
        coEvery { repository.getRouteCount() } returns 2

        viewModel = RoutesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads all routes on initial state`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()
            var state = awaitItem()
            if (state.routes.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(2, state.routes.size)
            assertEquals("01", state.routes[0].routeNo)
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
