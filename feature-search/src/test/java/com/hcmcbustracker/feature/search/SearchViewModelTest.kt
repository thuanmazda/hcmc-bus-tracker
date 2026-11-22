package com.hcmcbustracker.feature.search

import app.cash.turbine.test
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.ActiveFleetRegistry
import com.hcmcbustracker.core.model.ActiveVehicleSummary
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.RouteVehicleTelemetry
import com.hcmcbustracker.core.model.SearchHistoryItem
import com.hcmcbustracker.core.model.SearchHistoryType
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private val sampleVehicle = ActiveVehicleSummary(
        plateNumber = "50E-616.63",
        formattedPlate = "50E-616.63",
        normalizedPlate = "50E61663",
        routeId = 1,
        routeNo = "01",
        routeName = "Bến Thành - Chợ Lớn",
        varId = 101,
        directionName = "Lượt đi",
        checkpointStopId = 79,
        checkpointStopName = "Bến Bạch Đằng",
        distanceMeters = 350.0,
        speedKmH = 22.0,
        etaSeconds = 75,
        gpsTimestampIso = "2026-11-15T09:45:12.000Z",
        gpsTimestampMillis = 1700000000000L,
        latencyCategory = GpsLatencyCategory.FRESH
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ActiveFleetRegistry.clear()

        every { repository.getAllRoutes() } returns flowOf(dummyRoutes)
        every { repository.searchRoutes("01") } returns flowOf(dummyRoutes)
        every { repository.searchRoutes("Bến Thành") } returns flowOf(dummyRoutes)
        every { repository.searchStops("Q1 031") } returns flowOf(dummyStops)
        every { repository.searchStops("Bạch Đằng") } returns flowOf(dummyStops)
        every { repository.getFavoriteRoutes() } returns flowOf(dummyRoutes)
        every { repository.getFavoriteStops() } returns flowOf(dummyStops)
        every { repository.getSearchHistory() } returns flowOf(emptyList())
        every { repository.searchActiveVehiclesByPlate("50E-616.63") } answers {
            ActiveFleetRegistry.searchByPlate("50E-616.63")
        }
        every { repository.searchActiveVehiclesByPlate("50e61663") } answers {
            ActiveFleetRegistry.searchByPlate("50e61663")
        }
        every { repository.searchActiveVehiclesByPlate("50E 616.63") } answers {
            ActiveFleetRegistry.searchByPlate("50E 616.63")
        }
        every { repository.searchActiveVehiclesByPlate("99Z-999.99") } answers {
            ActiveFleetRegistry.searchByPlate("99Z-999.99")
        }
        coEvery { repository.getLastSyncTimestamp("ROUTES") } returns 1700000000L

        viewModel = SearchViewModel(repository)
    }

    @After
    fun tearDown() {
        ActiveFleetRegistry.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial tab should be ALL with favorites synchronized`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            var state = awaitItem()
            while (state.routes.isEmpty() || state.favoriteRouteIds.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(SearchTab.ALL, state.selectedTab)
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
    fun `search routes by routeNo 01`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("01")
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            var state = awaitItem()
            while (state.query != "01" || state.routes.isEmpty()) {
                state = awaitItem()
            }
            assertEquals("01", state.query)
            assertEquals("01", state.routes[0].routeNo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search routes by route name Ben Thanh`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("Bến Thành")
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            var state = awaitItem()
            while (state.query != "Bến Thành" || state.routes.isEmpty()) {
                state = awaitItem()
            }
            assertEquals("Bến Thành", state.query)
            assertEquals("01", state.routes[0].routeNo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search stops by stop code Q1 031`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("Q1 031")
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            var state = awaitItem()
            while (state.query != "Q1 031" || state.stops.isEmpty()) {
                state = awaitItem()
            }
            assertEquals("Q1 031", state.query)
            assertEquals("Q1 031", state.stops[0].code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search stops by stop name Bach Dang`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("Bạch Đằng")
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            var state = awaitItem()
            while (state.query != "Bạch Đằng" || state.stops.isEmpty()) {
                state = awaitItem()
            }
            assertEquals("Bạch Đằng", state.query)
            assertEquals("Bến Bạch Đằng", state.stops[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ActiveFleetRegistry starts empty after launch and finds bus after recording`() = runTest {
        // 1. Initially registry is empty
        val initialResult = ActiveFleetRegistry.searchByPlate("50E-616.63")
        assertTrue(initialResult.isEmpty())

        // 2. Telemetry packet arrives from route/stop observation
        val telemetry = RouteVehicleTelemetry(
            routeId = 1,
            varId = 101,
            plateNumber = "50E-616.63",
            formattedPlate = "50E-616.63",
            checkpointStopId = 79,
            checkpointStopName = "Bến Bạch Đằng",
            rawDistanceMeters = 350.0,
            speedKmH = 22.0,
            etaSeconds = 75,
            gpsTimestampIso = "2026-11-15T09:45:12.000Z",
            gpsTimestampMillis = 1700000000000L,
            requestStartedAtMillis = 1000L,
            responseReceivedAtMillis = 1200L,
            predictionReceivedAtMillis = 1250L,
            statusCode = 1,
            latencyCategory = GpsLatencyCategory.FRESH
        )
        ActiveFleetRegistry.recordVehicle(telemetry, routeNo = "01", routeName = "Bến Thành - Chợ Lớn")

        // 3. Search with hyphen
        val withHyphen = ActiveFleetRegistry.searchByPlate("50E-616.63")
        assertEquals(1, withHyphen.size)
        assertEquals("50E-616.63", withHyphen[0].formattedPlate)

        // 4. Search without hyphen (normalized)
        val withoutHyphen = ActiveFleetRegistry.searchByPlate("50e61663")
        assertEquals(1, withoutHyphen.size)
        assertEquals("50E-616.63", withoutHyphen[0].formattedPlate)

        // 5. Search with spaces and different casing
        val withSpaces = ActiveFleetRegistry.searchByPlate(" 50E 616.63 ")
        assertEquals(1, withSpaces.size)

        // 6. Search non-existent plate returns empty list without error
        val notFound = ActiveFleetRegistry.searchByPlate("99Z-999.99")
        assertTrue(notFound.isEmpty())
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

    @Test
    fun `onHistoryItemClicked updates query and switches to corresponding tab`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onHistoryItemClicked(SearchHistoryItem("01", SearchHistoryType.ROUTE))
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            var state = awaitItem()
            while (state.query != "01") {
                testDispatcher.scheduler.advanceTimeBy(350)
                testDispatcher.scheduler.advanceUntilIdle()
                state = awaitItem()
            }
            assertEquals("01", state.query)
            assertEquals(SearchTab.ROUTES, state.selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `history actions call repository methods`() = runTest {
        viewModel.recordSearchSelection("150", SearchHistoryType.ROUTE)
        viewModel.removeHistoryItem("150")
        viewModel.clearAllHistory()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.addSearchHistory("150", SearchHistoryType.ROUTE) }
        coVerify { repository.removeSearchHistoryItem("150") }
        coVerify { repository.clearSearchHistory() }
    }
}
