package com.hcmcbustracker.feature.map

import app.cash.turbine.test
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.ArrivalVehicle
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.Prediction
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
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BusRepository>(relaxed = true)
    private lateinit var viewModel: HomeViewModel

    private val sampleRoutes = listOf(
        Route(id = 1, routeNo = "01", name = "Bến Thành - Chợ Lớn"),
        Route(id = 150, routeNo = "150", name = "Chợ Lớn - Tân Vạn")
    )

    private val sampleStops = listOf(
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAllRoutes() } returns flowOf(sampleRoutes)
        every { repository.getFavoriteStops() } returns flowOf(sampleStops)
        every { repository.getFavoriteRoutes() } returns flowOf(sampleRoutes.take(1))
        coEvery { repository.getRouteCount() } returns 2

        viewModel = HomeViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads popular routes and favorites into home state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.popularRoutes.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(2, state.popularRoutes.size)
            assertEquals(1, state.favoriteStops.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query changed updates state`() = runTest {
        viewModel.onQueryChanged("01")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.query != "01") {
                state = awaitItem()
            }
            assertEquals("01", state.query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshFavoriteEta loads predictions for top favorite stop`() = runTest {
        val samplePrediction = listOf(
            Prediction(
                routeId = 1,
                routeNo = "01",
                routeName = "Bến Thành - Chợ Lớn",
                stopId = 142,
                stopName = "Bến xe buýt Sài Gòn",
                varId = 1,
                destinationName = "Bến xe Chợ Lớn",
                vehicles = listOf(
                    ArrivalVehicle(
                        plateNumber = "51B-123.45",
                        etaSeconds = 120,
                        distanceMeters = 500.0,
                        speedKmH = 20.0,
                        timestampIso = "2026-11-15T09:00:00Z",
                        status = 1
                    )
                )
            )
        )
        coEvery { repository.fetchRealtimePrediction(142) } returns NetworkResult.Success(samplePrediction)

        viewModel.refreshFavoriteEta()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.favoriteEta?.predictions.isNullOrEmpty()) {
                state = awaitItem()
            }
            assertEquals(142, state.favoriteEta?.stop?.id)
            assertEquals("01", state.favoriteEta?.predictions?.first()?.routeNo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavoriteStop calls repository toggleFavoriteStop`() = runTest {
        viewModel.toggleFavoriteStop(142)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.toggleFavoriteStop(142) }
    }

    @Test
    fun `toggleFavoriteRoute calls repository toggleFavoriteRoute`() = runTest {
        viewModel.toggleFavoriteRoute(1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.toggleFavoriteRoute(1) }
    }
}
