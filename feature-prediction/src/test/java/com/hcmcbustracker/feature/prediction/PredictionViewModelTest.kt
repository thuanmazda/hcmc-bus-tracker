package com.hcmcbustracker.feature.prediction

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.ArrivalVehicle
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.Prediction
import com.hcmcbustracker.core.model.PredictionFeedStatus
import com.hcmcbustracker.core.model.UserPreferences
import com.hcmcbustracker.core.network.monitor.NetworkMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class PredictionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BusRepository>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true)
    private val isOnlineFlow = MutableStateFlow(true)
    private lateinit var viewModel: PredictionViewModel

    private val dummyStop = BusStop(
        id = 142,
        code = "BX01",
        name = "Bến xe Chợ Lớn",
        stopType = "Bến xe",
        zone = "Quận 5",
        ward = "Phường 14",
        addressNo = "Hồng Bàng",
        street = "Hồng Bàng",
        supportDisability = true,
        status = "Active",
        lat = 10.7533,
        lng = 106.6544,
        searchKeywords = "ben xe cho lon",
        routes = listOf("01", "56")
    )

    // Dynamic fresh timestamp to represent fresh GPS signal (< 180s old)
    private val freshTimestampIso: String
        get() = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private val dummyVehicle: ArrivalVehicle
        get() = ArrivalVehicle(
            plateNumber = "50E20651",
            etaSeconds = 250,
            distanceMeters = 1168.21,
            speedKmH = 24.0,
            timestampIso = freshTimestampIso,
            status = 0
        )

    private val dummyPredictions: List<Prediction>
        get() = listOf(
            Prediction(
                routeId = 1,
                routeNo = "01",
                routeName = "Bến Thành - Chợ Lớn",
                stopId = 142,
                stopName = "Bến xe Chợ Lớn",
                varId = 101,
                destinationName = "Chợ Lớn",
                vehicles = listOf(dummyVehicle)
            )
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getStopById(142) } returns flowOf(dummyStop)
        every { repository.isStopFavorite(142) } returns flowOf(false)
        every { networkMonitor.isOnline } returns isOnlineFlow
        every { networkMonitor.isCurrentlyOnline } returns true
        coEvery { repository.fetchRealtimePrediction(142) } answers { NetworkResult.Success(dummyPredictions) }

        val savedStateHandle = SavedStateHandle(mapOf("stopId" to 142))
        viewModel = PredictionViewModel(repository, networkMonitor, savedStateHandle)
    }

    @After
    fun tearDown() {
        viewModel.stopRealtimePolling()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial startup loads stop and executes initial prediction poll`() = runTest {
        viewModel.refresh()
        testDispatcher.scheduler.runCurrent()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.stop == null || state.predictions.isEmpty()) {
                state = awaitItem()
            }
            assertEquals("Bến xe Chợ Lớn", state.stop?.name)
            assertEquals(1, state.predictions.size)
            assertEquals(PredictionFeedStatus.LIVE, state.status)
            assertEquals("50E-206.51", state.predictions[0].vehicles[0].formattedPlate)
            assertEquals("4 phút 10 giây", state.predictions[0].vehicles[0].formattedEta)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stale gps packet transitions status to STALE`() = runTest {
        // Vehicle GPS timestamp is 250s old (> 180s stale threshold)
        val staleIso = OffsetDateTime.now().minusSeconds(250).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val staleVehicle = dummyVehicle.copy(timestampIso = staleIso)
        val stalePredictions = listOf(
            dummyPredictions[0].copy(vehicles = listOf(staleVehicle))
        )
        coEvery { repository.fetchRealtimePrediction(142) } returns NetworkResult.Success(stalePredictions)

        viewModel.refresh()
        testDispatcher.scheduler.runCurrent()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.status != PredictionFeedStatus.STALE) {
                state = awaitItem()
            }
            assertEquals(PredictionFeedStatus.STALE, state.status)
            assertTrue(state.isStale)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty predictions sets status to EMPTY`() = runTest {
        coEvery { repository.fetchRealtimePrediction(142) } returns NetworkResult.Success(emptyList())

        viewModel.refresh()
        testDispatcher.scheduler.runCurrent()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.status != PredictionFeedStatus.EMPTY) {
                state = awaitItem()
            }
            assertEquals(PredictionFeedStatus.EMPTY, state.status)
            assertTrue(state.predictions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network offline transitions status to OFFLINE`() = runTest {
        every { networkMonitor.isCurrentlyOnline } returns false

        viewModel.startRealtimePolling()
        testDispatcher.scheduler.runCurrent()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.status != PredictionFeedStatus.OFFLINE) {
                state = awaitItem()
            }
            assertEquals(PredictionFeedStatus.OFFLINE, state.status)
            assertTrue(state.isOffline)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.stopRealtimePolling()
    }

    @Test
    fun `api error sets status to API_ERROR and increments failures`() = runTest {
        coEvery { repository.fetchRealtimePrediction(142) } returns NetworkResult.Error("Timeout 10s")

        viewModel.refresh()
        testDispatcher.scheduler.runCurrent()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.status != PredictionFeedStatus.API_ERROR) {
                state = awaitItem()
            }
            assertEquals(PredictionFeedStatus.API_ERROR, state.status)
            assertEquals(1, state.consecutiveFailures)
            assertEquals("Timeout 10s", state.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invalid or unparseable gps timestamp handled safely without crash`() = runTest {
        val invalidVehicle = dummyVehicle.copy(timestampIso = "INVALID_CORRUPT_TIMESTAMP")
        val invalidPredictions = listOf(
            dummyPredictions[0].copy(vehicles = listOf(invalidVehicle))
        )
        coEvery { repository.fetchRealtimePrediction(142) } returns NetworkResult.Success(invalidPredictions)

        viewModel.refresh()
        testDispatcher.scheduler.runCurrent()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.predictions.isEmpty()) {
                state = awaitItem()
            }
            assertNotNull(state.predictions)
            assertEquals(1, state.predictions.size)
            assertEquals("INVALID_CORRUPT_TIMESTAMP", state.predictions[0].vehicles[0].timestampIso)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite calls repository toggleFavoriteStop`() = runTest {
        viewModel.toggleFavorite()
        testDispatcher.scheduler.runCurrent()

        coVerify { repository.toggleFavoriteStop(142) }
    }

    @Test
    fun `toggleNextStops fetches and caches next stops`() = runTest {
        val nextStopsSample = listOf(
            Prediction(
                routeId = 1,
                routeNo = "01",
                routeName = "Bến Thành - Chợ Lớn",
                stopId = 853,
                stopName = "Calmette",
                varId = 101,
                destinationName = "Chợ Lớn",
                vehicles = listOf(dummyVehicle)
            )
        )
        coEvery { repository.fetchPredictNextStops(1, 101, 142, 3) } returns NetworkResult.Success(nextStopsSample)

        viewModel.toggleNextStops(dummyPredictions[0])
        testDispatcher.scheduler.runCurrent()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.expandedNextStops.isEmpty()) {
                state = awaitItem()
            }
            val key = "1_101"
            assertTrue(state.expandedNextStops.containsKey(key))
            assertEquals("Calmette", state.expandedNextStops[key]?.get(0)?.stopName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `monotonic dts protection ignores delayed older gps timestamp`() = runTest {
        val newerIso = OffsetDateTime.now().minusSeconds(10).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val olderIso = OffsetDateTime.now().minusSeconds(60).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val firstPred = listOf(
            dummyPredictions[0].copy(
                vehicles = listOf(dummyVehicle.copy(timestampIso = newerIso, distanceMeters = 450.0))
            )
        )
        val staleDelayedPred = listOf(
            dummyPredictions[0].copy(
                vehicles = listOf(dummyVehicle.copy(timestampIso = olderIso, distanceMeters = 950.0))
            )
        )

        var callCount = 0
        coEvery { repository.fetchRealtimePrediction(142) } answers {
            callCount++
            if (callCount == 1) NetworkResult.Success(firstPred) else NetworkResult.Success(staleDelayedPred)
        }

        viewModel.refresh()
        testDispatcher.scheduler.runCurrent()
        viewModel.refresh()
        testDispatcher.scheduler.runCurrent()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.predictions.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(450.0, state.predictions[0].vehicles[0].distanceMeters, 0.01)
            assertFalse(state.isGpsTimestampUnchanged)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unchanged dts across consecutive polls sets isGpsTimestampUnchanged flag`() = runTest {
        val fixedIso = OffsetDateTime.now().minusSeconds(20).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val fresherIso = OffsetDateTime.now().minusSeconds(5).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val fixedPred = listOf(
            dummyPredictions[0].copy(vehicles = listOf(dummyVehicle.copy(timestampIso = fixedIso)))
        )
        val fresherPred = listOf(
            dummyPredictions[0].copy(vehicles = listOf(dummyVehicle.copy(timestampIso = fresherIso)))
        )

        var callCount = 0
        coEvery { repository.fetchRealtimePrediction(142) } answers {
            callCount++
            if (callCount <= 2) NetworkResult.Success(fixedPred) else NetworkResult.Success(fresherPred)
        }

        viewModel.uiState.test {
            viewModel.refresh()
            testDispatcher.scheduler.runCurrent()
            viewModel.refresh()
            testDispatcher.scheduler.runCurrent()

            var state = expectMostRecentItem()
            if (!state.isGpsTimestampUnchanged) {
                state = awaitItem()
            }
            assertTrue(state.isGpsTimestampUnchanged)

            viewModel.refresh()
            testDispatcher.scheduler.runCurrent()

            val updatedState = awaitItem()
            assertFalse(updatedState.isGpsTimestampUnchanged)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `http 429 error respects retryAfterSeconds backoff during polling`() = runTest {
        coEvery { repository.fetchRealtimePrediction(142) } returns NetworkResult.Error(
            message = "HTTP 429",
            code = 429,
            retryAfterSeconds = 25L
        )

        viewModel.startRealtimePolling()
        testDispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { repository.fetchRealtimePrediction(142) }

        // Default poll interval (15s) has elapsed, but 25s Retry-After must still hold off next call
        testDispatcher.scheduler.advanceTimeBy(16_000L)
        testDispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { repository.fetchRealtimePrediction(142) }

        // After >= 25s, next poll fires
        testDispatcher.scheduler.advanceTimeBy(10_000L)
        testDispatcher.scheduler.runCurrent()
        coVerify(exactly = 2) { repository.fetchRealtimePrediction(142) }

        viewModel.stopRealtimePolling()
    }

    @Test
    fun `configurable polling interval updates poll cadence from user preferences`() = runTest {
        every { repository.getUserPreferences() } returns flowOf(UserPreferences(pollingIntervalSeconds = 20))
        val customVm = PredictionViewModel(
            repository = repository,
            networkMonitor = networkMonitor,
            savedStateHandle = SavedStateHandle(mapOf("stopId" to 142))
        )
        testDispatcher.scheduler.runCurrent()

        customVm.startRealtimePolling()
        testDispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { repository.fetchRealtimePrediction(142) }

        testDispatcher.scheduler.advanceTimeBy(16_000L)
        testDispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { repository.fetchRealtimePrediction(142) }

        testDispatcher.scheduler.advanceTimeBy(5_000L)
        testDispatcher.scheduler.runCurrent()
        coVerify(exactly = 2) { repository.fetchRealtimePrediction(142) }

        customVm.stopRealtimePolling()
    }
}
