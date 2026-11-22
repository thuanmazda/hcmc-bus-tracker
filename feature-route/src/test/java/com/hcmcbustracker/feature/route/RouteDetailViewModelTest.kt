package com.hcmcbustracker.feature.route

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.RouteVariant
import com.hcmcbustracker.core.model.Timetable
import com.hcmcbustracker.core.model.Trip
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BusRepository>(relaxed = true)
    private lateinit var viewModel: RouteDetailViewModel

    private val dummyRoute = Route(
        id = 1,
        routeNo = "01",
        name = "Bến Thành - Chợ Lớn",
        outBoundDesc = "Bến Thành đi Chợ Lớn",
        inBoundDesc = "Chợ Lớn đi Bến Thành",
        color = "#1E88E5",
        operationTime = "05:00 - 22:00",
        headway = "8 - 15"
    )

    private val dummyVariants = listOf(
        RouteVariant(
            id = 101,
            routeId = 1,
            name = "Lượt đi: Bến Thành - Chợ Lớn",
            shortName = "Bến Thành - Chợ Lớn",
            routeNo = "01",
            startStop = "Bến Thành",
            endStop = "Chợ Lớn",
            distanceMeters = 8500.0,
            isOutbound = true,
            runningTimeMinutes = 35
        ),
        RouteVariant(
            id = 102,
            routeId = 1,
            name = "Lượt về: Chợ Lớn - Bến Thành",
            shortName = "Chợ Lớn - Bến Thành",
            routeNo = "01",
            startStop = "Chợ Lớn",
            endStop = "Bến Thành",
            distanceMeters = 8700.0,
            isOutbound = false,
            runningTimeMinutes = 35
        )
    )

    private val dummyTimetables = listOf(
        Timetable(
            id = 501,
            routeId = 1,
            varId = 101,
            varShortName = "Lượt đi",
            runningTime = "35 phút",
            headway = "8 - 15 phút",
            totalTrip = 120,
            operationTime = "05:00 - 22:00",
            applyDates = "Thứ 2 - Chủ nhật"
        )
    )

    private val dummyTrips = listOf(
        Trip(id = 1, routeId = 1, timetableId = 501, startTime = "05:00:00", endTime = "05:35:00"),
        Trip(id = 2, routeId = 1, timetableId = 501, startTime = "05:15:00", endTime = "05:50:00"),
        Trip(id = 3, routeId = 1, timetableId = 501, startTime = "06:00:00", endTime = "06:35:00")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getRouteById(1) } returns flowOf(dummyRoute)
        every { repository.getVariantsForRoute(1) } returns flowOf(dummyVariants)
        every { repository.getStopsForRouteVar(1, any()) } returns flowOf(emptyList())
        every { repository.getRoutePath(1, any()) } returns flowOf(null)
        every { repository.isRouteFavorite(1) } returns flowOf(false)
        coEvery { repository.getTimetablesForRoute(1) } returns NetworkResult.Success(dummyTimetables)
        coEvery { repository.getTripsForTimetable(1, 501) } returns NetworkResult.Success(dummyTrips)

        val savedStateHandle = SavedStateHandle(mapOf("routeId" to 1))
        viewModel = RouteDetailViewModel(repository, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads route and variants correctly`() = runTest {
        testDispatcher.scheduler.runCurrent()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.route == null) {
                state = awaitItem()
            }
            assertEquals("01", state.route?.routeNo)
            assertEquals(2, state.variants.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectVariant updates selectedVariant and reloads timetable`() = runTest {
        testDispatcher.scheduler.runCurrent()

        viewModel.selectVariant(102)
        testDispatcher.scheduler.runCurrent()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.selectedVariant?.id != 102) {
                state = awaitItem()
            }
            assertEquals(102, state.selectedVariant?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite calls repository toggleFavoriteRoute`() = runTest {
        viewModel.toggleFavorite()
        testDispatcher.scheduler.runCurrent()

        coVerify { repository.toggleFavoriteRoute(1) }
    }

    @Test
    fun `loads timetables and individual trip departure times correctly`() = runTest {
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            var state = awaitItem()
            while (state.timetables.isEmpty()) {
                testDispatcher.scheduler.advanceUntilIdle()
                state = awaitItem()
            }
            assertEquals(1, state.timetables.size)
            assertEquals(3, state.trips.size)
            assertEquals("05:00:00", state.trips[0].startTime)
            assertNotNull(state.hourlyTripCounts)
            assertEquals(2, state.hourlyTripCounts?.get(5)) // 2 trips at 05:xx
            assertEquals(1, state.hourlyTripCounts?.get(6)) // 1 trip at 06:xx
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `formatDepartureTime extracts hour and minute accurately`() {
        assertEquals("05:00", formatDepartureTime("05:00:00"))
        assertEquals("05:15", formatDepartureTime("5:15:00"))
        assertEquals("14:30", formatDepartureTime("14:30"))
        assertEquals("invalid", formatDepartureTime("invalid"))
    }
}
