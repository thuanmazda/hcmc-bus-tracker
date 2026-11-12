package com.hcmcbustracker.feature.search

import app.cash.turbine.test
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.NetworkResult
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
class StopsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BusRepository>(relaxed = true)
    private lateinit var viewModel: StopsViewModel

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
        every { repository.getStopsInBounds(any(), any(), any(), any()) } returns flowOf(sampleStops)
        every { repository.searchStops("ben xe") } returns flowOf(sampleStops)
        every { repository.getFavoriteStops() } returns flowOf(sampleStops)
        coEvery { repository.getStopCount() } returns 1

        viewModel = StopsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads initial stops and favorite IDs`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.stops.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(1, state.stops.size)
            assertTrue(state.favoriteStopIds.contains(142))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onQueryChanged triggers unaccented stop search`() = runTest {
        viewModel.onQueryChanged("ben xe")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.query != "ben xe") {
                state = awaitItem()
            }
            assertEquals("ben xe", state.query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite calls repository toggleFavoriteStop`() = runTest {
        viewModel.toggleFavorite(142)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.toggleFavoriteStop(142) }
    }

    @Test
    fun `syncStops calls repository syncStops`() = runTest {
        coEvery { repository.syncStops() } returns NetworkResult.Success(6000)

        viewModel.syncStops()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.syncStops() }
    }
}
