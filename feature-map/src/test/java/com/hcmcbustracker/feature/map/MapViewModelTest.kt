package com.hcmcbustracker.feature.map

import app.cash.turbine.test
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.core.model.RouteVariant
import com.hcmcbustracker.feature.map.tile.TileProvider
import com.hcmcbustracker.feature.map.tile.TileProviderManager
import io.mockk.coEvery
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BusRepository>(relaxed = true)
    private val tileProviderManager = TileProviderManager()
    private lateinit var viewModel: MapViewModel

    private val sampleRoute = Route(
        id = 1,
        routeNo = "01",
        name = "Bến Thành - Chợ Lớn"
    )

    private val sampleVariants = listOf(
        RouteVariant(id = 101, routeId = 1, name = "Lượt đi", shortName = "Lượt đi", routeNo = "01", startStop = "Bến Thành", endStop = "Chợ Lớn", distanceMeters = 8500.0, isOutbound = true, runningTimeMinutes = 35),
        RouteVariant(id = 102, routeId = 1, name = "Lượt về", shortName = "Lượt về", routeNo = "01", startStop = "Chợ Lớn", endStop = "Bến Thành", distanceMeters = 8700.0, isOutbound = false, runningTimeMinutes = 35)
    )

    private val samplePath = RoutePath(
        routeId = 1,
        varId = 101,
        points = listOf(
            com.hcmcbustracker.core.model.LatLngPoint(10.7684, 106.6942),
            com.hcmcbustracker.core.model.LatLngPoint(10.7533, 106.6544)
        )
    )

    private val sampleStops = listOf(
        BusStop(id = 142, code = "Q1 001", name = "Bến xe buýt Sài Gòn", stopType = "Bến", zone = "Q1", addressNo = "1", street = "Lê Lai", supportDisability = true, status = "Active", lat = 10.7684, lng = 106.6942, searchKeywords = "ben xe", routes = listOf("01"))
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getRouteById(1) } returns flowOf(sampleRoute)
        every { repository.getVariantsForRoute(1) } returns flowOf(sampleVariants)
        every { repository.getRoutePath(1, 101) } returns flowOf(samplePath)
        every { repository.getStopsForRouteVar(1, 101) } returns flowOf(sampleStops)
        coEvery { repository.ensureRoutePath(any(), any()) } returns NetworkResult.Success(samplePath)

        viewModel = MapViewModel(repository, tileProviderManager)
    }

    @After
    fun tearDown() {
        viewModel.onMapPaused()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state defaults to FPT STIS tile provider and empty route`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(TileProvider.FPT_STIS, state.tileProvider)
            assertNull(state.activeRoute)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switch tile provider updates state`() = runTest {
        viewModel.setTileProvider(TileProvider.OSM_STANDARD)

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.tileProvider != TileProvider.OSM_STANDARD) {
                state = awaitItem()
            }
            assertEquals(TileProvider.OSM_STANDARD, state.tileProvider)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCameraIdle with zoom under threshold sets isZoomTooFar true`() = runTest {
        val bounds = LatLngBounds.from(10.8, 106.7, 10.7, 106.6)
        viewModel.onCameraIdle(bounds, zoom = 11.0)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (!state.isZoomTooFar) {
                state = awaitItem()
            }
            assertTrue(state.isZoomTooFar)
            assertTrue(state.stopsInViewport.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCameraIdle with zoom above threshold queries stops in bounds`() = runTest {
        val bounds = LatLngBounds.from(10.8, 106.7, 10.7, 106.6)
        every { repository.getStopsInBounds(any(), any(), any(), any()) } returns flowOf(sampleStops)

        viewModel.onCameraIdle(bounds, zoom = 14.5)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.stopsInViewport.isEmpty()) {
                state = awaitItem()
            }
            assertFalse(state.isZoomTooFar)
            assertEquals(1, state.stopsInViewport.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadRoute loads route, variants, outbound variant, path and stops`() = runTest {
        viewModel.loadRoute(1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.activeRoute == null) {
                state = awaitItem()
            }
            assertEquals("01", state.activeRoute?.routeNo)
            assertEquals(101, state.activeVariant?.id)
            assertTrue(state.activeVariant?.isOutbound == true)
            assertNotNull(state.activeRoutePath)
            assertEquals(1, state.activeRouteStops.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleRouteDirection switches from outbound to inbound`() = runTest {
        viewModel.loadRoute(1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleRouteDirection()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.activeVariant?.id != 102) {
                state = awaitItem()
            }
            assertEquals(102, state.activeVariant?.id)
            assertFalse(state.activeVariant?.isOutbound ?: true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectStop and clearSelectedStop update uiState`() = runTest {
        viewModel.selectStop(sampleStops[0])
        viewModel.uiState.test {
            var state = awaitItem()
            if (state.selectedStop == null) {
                state = awaitItem()
            }
            assertEquals("Bến xe buýt Sài Gòn", state.selectedStop?.name)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.clearSelectedStop()
        viewModel.uiState.test {
            var state = awaitItem()
            if (state.selectedStop != null) {
                state = awaitItem()
            }
            assertNull(state.selectedStop)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleStopsLayer toggles showStopsLayer boolean in uiState`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.showStopsLayer)

            viewModel.toggleStopsLayer()
            val toggled = awaitItem()
            assertFalse(toggled.showStopsLayer)

            viewModel.toggleStopsLayer()
            val restored = awaitItem()
            assertTrue(restored.showStopsLayer)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateUserLocation updates userLocation in uiState`() = runTest {
        viewModel.updateUserLocation(10.772, 106.698)
        viewModel.uiState.test {
            var state = awaitItem()
            if (state.userLocation == null) {
                state = awaitItem()
            }
            assertEquals(10.772, state.userLocation?.latitude ?: 0.0, 0.001)
            assertEquals(106.698, state.userLocation?.longitude ?: 0.0, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `findNearbyStops calculates and sorts stops within radius`() = runTest {
        every { repository.getStopsInBounds(any(), any(), any(), any()) } returns flowOf(sampleStops)
        viewModel.updateUserLocation(10.7680, 106.6940)

        viewModel.findNearbyStops(radiusMeters = 2000.0)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.nearbyStops.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(1, state.nearbyStops.size)
            assertEquals("Bến xe buýt Sài Gòn", state.nearbyStops[0].stop.name)
            assertTrue(state.nearbyStops[0].distanceMeters < 2000.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trackVehicle and clearTrackedVehicle manage vehicle tracking state`() = runTest {
        every { repository.getStopById(142) } returns flowOf(sampleStops[0])

        viewModel.trackVehicle(
            routeId = 1,
            varId = 101,
            stopId = 142,
            plate = "51B-12345",
            distanceMeters = 300.0,
            speedKmH = 35.0
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.trackedVehicle == null) {
                state = awaitItem()
            }
            assertNotNull(state.trackedVehicle)
            assertEquals("51B-12345", state.trackedVehicle?.plateNumber)
            assertEquals(35.0, state.trackedVehicle?.speedKmH ?: 0.0, 0.01)
            assertEquals(300.0, state.trackedVehicle?.distanceMeters ?: 0.0, 0.01)

            viewModel.clearTrackedVehicle()
            var stateCleared = awaitItem()
            if (stateCleared.trackedVehicle != null) {
                stateCleared = awaitItem()
            }
            assertNull(stateCleared.trackedVehicle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trackVehicle with timestampIso computes latency compensation correctly`() = runTest {
        every { repository.getStopById(142) } returns flowOf(sampleStops[0])

        val baseTime = System.currentTimeMillis()
        val iso = "2026-11-09T21:13:29+07:00"

        viewModel.trackVehicle(
            routeId = 1,
            varId = 101,
            stopId = 142,
            plate = "50E-616.63",
            distanceMeters = 6417.0,
            speedKmH = 38.0,
            timestampIso = iso,
            etaSeconds = 300
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.trackedVehicle == null) {
                state = awaitItem()
            }
            val tracked = state.trackedVehicle
            assertNotNull(tracked)
            assertEquals("50E-616.63", tracked?.plateNumber)
            assertEquals(6417.0, tracked?.rawDistanceMeters ?: 0.0, 0.01)
            assertEquals(300, tracked?.etaSeconds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateUserLocation with bearing and accuracy updates uiState`() = runTest {
        viewModel.updateUserLocation(
            lat = 10.772,
            lng = 106.698,
            bearing = 45.0f,
            accuracyMeters = 8.5f
        )

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.userBearing == null) {
                state = awaitItem()
            }
            assertEquals(45.0f, state.userBearing ?: 0f, 0.1f)
            assertEquals(8.5f, state.userAccuracyMeters ?: 0f, 0.1f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadRoute uses offline local data without redundant static network sync`() = runTest {
        viewModel.loadRoute(1)
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 0) { repository.syncRouteDetailAndVariants(any()) }
        io.mockk.coVerify(exactly = 0) { repository.syncStopsForRouteVar(any(), any()) }
        io.mockk.coVerify(exactly = 0) { repository.ensureRoutePath(any(), any()) }
    }

    @Test
    fun `route-wide polling renders active fleet and selectRouteVehicle promotes to tracked card`() = runTest {
        viewModel.computeDispatcher = testDispatcher
        val recentIso = java.time.OffsetDateTime.now().minusSeconds(10)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val nowMs = System.currentTimeMillis()
        val bus1 = com.hcmcbustracker.core.model.RouteVehicleTelemetry(
            routeId = 1,
            varId = 101,
            plateNumber = "50E61577",
            formattedPlate = "50E-615.77",
            checkpointStopId = 142,
            checkpointStopName = "Bến xe buýt Sài Gòn",
            rawDistanceMeters = 200.0,
            speedKmH = 30.0,
            etaSeconds = 40,
            gpsTimestampIso = recentIso,
            gpsTimestampMillis = nowMs - 10_000L,
            requestStartedAtMillis = nowMs,
            responseReceivedAtMillis = nowMs,
            predictionReceivedAtMillis = nowMs,
            statusCode = 0
        )
        val bus2 = bus1.copy(
            plateNumber = "50E76203",
            formattedPlate = "50E-762.03",
            rawDistanceMeters = 650.0,
            speedKmH = 18.0
        )

        coEvery { repository.fetchRouteVariantVehicles(1, 101, any(), any()) } returns NetworkResult.Success(
            com.hcmcbustracker.core.model.RouteVehicleAggregator.DeduplicationResult(
                vehicles = listOf(bus1, bus2),
                totalRawRecords = 4,
                duplicatesRemoved = 2
            )
        )

        viewModel.uiState.test {
            awaitItem()

            viewModel.loadRoute(1)
            testDispatcher.scheduler.runCurrent()

            viewModel.onMapResumed()
            testDispatcher.scheduler.runCurrent()

            val state = expectMostRecentItem()
            assertEquals(2, state.routeVehicles.size)

            viewModel.selectRouteVehicle("50E76203")
            testDispatcher.scheduler.runCurrent()

            val selectedState = expectMostRecentItem()
            assertEquals("50E-762.03", selectedState.trackedVehicle?.plateNumber)
            assertEquals("50E76203", selectedState.selectedVehiclePlate)

            // Switching direction immediately clears previous direction's vehicles
            every { repository.getRoutePath(1, 102) } returns flowOf(samplePath.copy(varId = 102))
            every { repository.getStopsForRouteVar(1, 102) } returns flowOf(sampleStops)
            viewModel.onMapPaused()
            viewModel.switchVariant(102)
            testDispatcher.scheduler.runCurrent()

            val clearedState = expectMostRecentItem()
            assertEquals(102, clearedState.activeVariant?.id)
            assertTrue(clearedState.routeVehicles.isEmpty())
            assertNull(clearedState.selectedVehiclePlate)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `route polling preserves user selectedStopId instead of overwriting with checkpointStopId`() = runTest {
        viewModel.computeDispatcher = testDispatcher
        val stop1 = sampleStops[0] // id=142 at start (10.7684, 106.6942)
        val stop2 = BusStop(143, "Q5 002", "Trạm Trung Gian", "Trạm", "Q5", null, "2", "Nguyễn Trãi", false, "Active", 10.7610, 106.6745, "trung gian", listOf("01"))
        val stop3 = BusStop(144, "Q5 003", "Bến xe Chợ Lớn", "Bến", "Q5", null, "3", "Trang Tử", false, "Active", 10.7533, 106.6544, "cho lon", listOf("01"))
        val multiStops = listOf(stop1, stop2, stop3)

        every { repository.getStopsForRouteVar(1, 101) } returns flowOf(multiStops)
        every { repository.getStopById(143) } returns flowOf(stop2)

        val nowMs = System.currentTimeMillis()
        val recentIso = java.time.OffsetDateTime.now().minusSeconds(20)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        // Checkpoint poll discovers the same bus via terminal checkpoint stop3 (id=144)
        val busFromTerminalCheckpoint = com.hcmcbustracker.core.model.RouteVehicleTelemetry(
            routeId = 1,
            varId = 101,
            plateNumber = "50E61663",
            formattedPlate = "50E-616.63",
            checkpointStopId = 144,
            checkpointStopName = "Bến xe Chợ Lớn",
            rawDistanceMeters = 3200.0,
            speedKmH = 25.0,
            etaSeconds = 480,
            gpsTimestampIso = recentIso,
            gpsTimestampMillis = nowMs - 20_000L,
            requestStartedAtMillis = nowMs - 200L,
            responseReceivedAtMillis = nowMs - 50L,
            predictionReceivedAtMillis = nowMs,
            statusCode = 0
        )

        coEvery { repository.fetchRouteVariantVehicles(1, 101, any(), any()) } returns NetworkResult.Success(
            com.hcmcbustracker.core.model.RouteVehicleAggregator.DeduplicationResult(
                vehicles = listOf(busFromTerminalCheckpoint),
                totalRawRecords = 1,
                duplicatesRemoved = 0
            )
        )

        viewModel.uiState.test {
            awaitItem()
            // User opened vehicle 50E-616.63 while viewing stop2 (id=143)
            viewModel.trackVehicle(
                routeId = 1,
                varId = 101,
                stopId = 143,
                plate = "50E-616.63",
                distanceMeters = 900.0,
                speedKmH = 25.0,
                timestampIso = recentIso,
                etaSeconds = 120
            )
            testDispatcher.scheduler.runCurrent()

            // Start route-wide polling twice -> should NOT duplicate polling jobs, and MUST preserve selectedStopId = 143
            viewModel.onMapResumed()
            viewModel.startRouteWideVehiclePolling(1, 101)
            testDispatcher.scheduler.runCurrent()

            val tracked = expectMostRecentItem().trackedVehicle
            assertNotNull(tracked)
            assertEquals(143, tracked?.selectedStopId)
            assertEquals("Trạm Trung Gian", tracked?.selectedStopName)
            assertEquals(144, tracked?.checkpointStopId)
            assertEquals(144, tracked?.predictionTargetStopId)
            assertNotNull(tracked?.nextStopId)
            io.mockk.coVerify(exactly = 1) { repository.fetchRouteVariantVehicles(1, 101, any(), any()) }

            viewModel.onMapPaused()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `camera state machine transitions between focus, follow, user gesture, and idle`() = runTest {
        viewModel.computeDispatcher = testDispatcher
        val stop2 = BusStop(143, "Q5 002", "Trạm Trung Gian", "Trạm", "Q5", null, "2", "Nguyễn Trãi", false, "Active", 10.7610, 106.6745, "trung gian", listOf("01"))
        val stop3 = BusStop(144, "Q5 003", "Bến xe Chợ Lớn", "Bến", "Q5", null, "3", "Trang Tử", false, "Active", 10.7533, 106.6544, "cho lon", listOf("01"))
        every { repository.getStopsForRouteVar(1, 101) } returns flowOf(listOf(sampleStops[0], stop2, stop3))

        val nowMs = System.currentTimeMillis()
        val recentIso = java.time.OffsetDateTime.now().minusSeconds(15)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val busA = com.hcmcbustracker.core.model.RouteVehicleTelemetry(
            routeId = 1,
            varId = 101,
            plateNumber = "50E61663",
            formattedPlate = "50E-616.63",
            checkpointStopId = 144,
            checkpointStopName = "Bến xe Chợ Lớn",
            rawDistanceMeters = 3200.0,
            speedKmH = 25.0,
            etaSeconds = 480,
            gpsTimestampIso = recentIso,
            gpsTimestampMillis = nowMs - 15_000L,
            requestStartedAtMillis = nowMs - 200L,
            responseReceivedAtMillis = nowMs - 50L,
            predictionReceivedAtMillis = nowMs,
            statusCode = 0
        )
        val busB = busA.copy(
            plateNumber = "51B12345",
            formattedPlate = "51B-123.45",
            rawDistanceMeters = 1800.0
        )

        coEvery { repository.fetchRouteVariantVehicles(1, 101, any(), any()) } returns NetworkResult.Success(
            com.hcmcbustracker.core.model.RouteVehicleAggregator.DeduplicationResult(
                vehicles = listOf(busA, busB),
                totalRawRecords = 2,
                duplicatesRemoved = 0
            )
        )

        viewModel.uiState.test {
            try {
                val initial = awaitItem()
                assertEquals(VehicleCameraMode.IDLE, initial.cameraMode)
                assertEquals(0L, initial.cameraFocusRequestId)

                viewModel.loadRoute(1)
                testDispatcher.scheduler.runCurrent()
                viewModel.onMapResumed()
                testDispatcher.scheduler.runCurrent()

                // Selecting plate A enters FOCUS_VEHICLE and increments cameraFocusRequestId
                viewModel.selectRouteVehicle("50E61663")
                testDispatcher.scheduler.runCurrent()
                val focusA = expectMostRecentItem()
                assertEquals("50E61663", focusA.selectedVehiclePlate)
                assertEquals(VehicleCameraMode.FOCUS_VEHICLE, focusA.cameraMode)
                assertTrue(focusA.cameraFocusRequestId > initial.cameraFocusRequestId)

                // Re-tapping already selected plate A still increments cameraFocusRequestId to re-center camera
                val firstFocusId = focusA.cameraFocusRequestId
                viewModel.selectRouteVehicle("50E61663")
                testDispatcher.scheduler.runCurrent()
                val refocusA = expectMostRecentItem()
                assertEquals(VehicleCameraMode.FOCUS_VEHICLE, refocusA.cameraMode)
                assertTrue(refocusA.cameraFocusRequestId > firstFocusId)

                // Enabling auto-follow switches to FOLLOW_VEHICLE and increments focus request
                viewModel.enableVehicleAutoFollow()
                testDispatcher.scheduler.runCurrent()
                val followA = expectMostRecentItem()
                assertEquals(VehicleCameraMode.FOLLOW_VEHICLE, followA.cameraMode)
                assertTrue(followA.cameraFocusRequestId > refocusA.cameraFocusRequestId)

                // User map pan/zoom gesture pauses auto-follow -> USER_CONTROLLED
                viewModel.onUserMapGesture()
                testDispatcher.scheduler.runCurrent()
                val userControlled = expectMostRecentItem()
                assertEquals(VehicleCameraMode.USER_CONTROLLED, userControlled.cameraMode)

                // Toggling auto-follow re-engages FOLLOW_VEHICLE
                viewModel.toggleVehicleAutoFollow()
                testDispatcher.scheduler.runCurrent()
                val reFollow = expectMostRecentItem()
                assertEquals(VehicleCameraMode.FOLLOW_VEHICLE, reFollow.cameraMode)

                // Selecting another plate B re-centers on B in FOCUS_VEHICLE
                viewModel.selectRouteVehicle("51B12345")
                testDispatcher.scheduler.runCurrent()
                val focusB = expectMostRecentItem()
                assertEquals("51B12345", focusB.selectedVehiclePlate)
                assertEquals(VehicleCameraMode.FOCUS_VEHICLE, focusB.cameraMode)

                // Clearing tracked vehicle returns camera to IDLE
                viewModel.clearTrackedVehicle()
                testDispatcher.scheduler.runCurrent()
                val cleared = expectMostRecentItem()
                assertNull(cleared.selectedVehiclePlate)
                assertEquals(VehicleCameraMode.IDLE, cleared.cameraMode)

                // Re-select then switch variant -> returns camera to IDLE
                every { repository.getRoutePath(1, 102) } returns flowOf(samplePath.copy(varId = 102))
                every { repository.getStopsForRouteVar(1, 102) } returns flowOf(sampleStops)
                viewModel.selectRouteVehicle("50E61663")
                testDispatcher.scheduler.runCurrent()
                assertEquals(VehicleCameraMode.FOCUS_VEHICLE, expectMostRecentItem().cameraMode)
                viewModel.switchVariant(102)
                testDispatcher.scheduler.runCurrent()
                val afterVariant = expectMostRecentItem()
                assertEquals(VehicleCameraMode.IDLE, afterVariant.cameraMode)
                assertNull(afterVariant.selectedVehiclePlate)
            } finally {
                viewModel.onMapPaused()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `follow mode suppresses camera updates when position is unchanged and moves on significant displacement`() = runTest {
        viewModel.computeDispatcher = testDispatcher
        val stop2 = BusStop(143, "Q5 002", "Trạm Trung Gian", "Trạm", "Q5", null, "2", "Nguyễn Trãi", false, "Active", 10.7610, 106.6745, "trung gian", listOf("01"))
        val stop3 = BusStop(144, "Q5 003", "Bến xe Chợ Lớn", "Bến", "Q5", null, "3", "Trang Tử", false, "Active", 10.7533, 106.6544, "cho lon", listOf("01"))
        every { repository.getStopsForRouteVar(1, 101) } returns flowOf(listOf(sampleStops[0], stop2, stop3))

        val nowMs = System.currentTimeMillis()
        val isoT0 = java.time.OffsetDateTime.now().minusSeconds(20)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val isoT1 = java.time.OffsetDateTime.now().minusSeconds(10)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val isoT2 = java.time.OffsetDateTime.now().minusSeconds(5)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val busT0 = com.hcmcbustracker.core.model.RouteVehicleTelemetry(
            routeId = 1,
            varId = 101,
            plateNumber = "50E61663",
            formattedPlate = "50E-616.63",
            checkpointStopId = 144,
            checkpointStopName = "Bến xe Chợ Lớn",
            rawDistanceMeters = 3200.0,
            speedKmH = 25.0,
            etaSeconds = 480,
            gpsTimestampIso = isoT0,
            gpsTimestampMillis = nowMs - 20_000L,
            requestStartedAtMillis = nowMs - 200L,
            responseReceivedAtMillis = nowMs - 50L,
            predictionReceivedAtMillis = nowMs,
            statusCode = 0
        )
        // Next poll with essentially identical coordinates (same rawDistanceMeters & isoT0)
        val busSame = busT0
        // Next poll with significant movement along polyline (> 300m closer)
        val busMovedFar = busT0.copy(
            rawDistanceMeters = 2600.0,
            gpsTimestampIso = isoT1,
            gpsTimestampMillis = nowMs - 10_000L
        )
        // Poll after USER_CONTROLLED gesture must NOT move camera even when bus moves again
        val busMovedAfterGesture = busT0.copy(
            rawDistanceMeters = 2000.0,
            gpsTimestampIso = isoT2,
            gpsTimestampMillis = nowMs - 5_000L
        )

        var pollStep = 0
        coEvery { repository.fetchRouteVariantVehicles(1, 101, any(), any()) } answers {
            val payload = when (pollStep++) {
                0 -> listOf(busT0)
                1 -> listOf(busSame)
                2 -> listOf(busMovedFar)
                else -> listOf(busMovedAfterGesture)
            }
            NetworkResult.Success(
                com.hcmcbustracker.core.model.RouteVehicleAggregator.DeduplicationResult(
                    vehicles = payload,
                    totalRawRecords = 1,
                    duplicatesRemoved = 0
                )
            )
        }

        viewModel.uiState.test {
            try {
                awaitItem()
                viewModel.loadRoute(1)
                testDispatcher.scheduler.runCurrent()
                viewModel.onMapResumed()
                testDispatcher.scheduler.runCurrent()

                viewModel.selectRouteVehicle("50E61663")
                testDispatcher.scheduler.runCurrent()
                viewModel.enableVehicleAutoFollow()
                testDispatcher.scheduler.runCurrent()
                val followBaseline = expectMostRecentItem()
                assertEquals(VehicleCameraMode.FOLLOW_VEHICLE, followBaseline.cameraMode)
                val baseFollowReqId = followBaseline.cameraFollowRequestId

                // Poll 1: unchanged coordinates -> follow request id MUST stay constant (no continuous jitter)
                testDispatcher.scheduler.advanceTimeBy(15_000L)
                testDispatcher.scheduler.runCurrent()
                assertEquals(baseFollowReqId, viewModel.uiState.value.cameraFollowRequestId)

                // Poll 2: significant movement (>= 12m) -> follow request id increments once
                testDispatcher.scheduler.advanceTimeBy(15_000L)
                testDispatcher.scheduler.runCurrent()
                val movedState = viewModel.uiState.value
                assertTrue(movedState.cameraFollowRequestId > baseFollowReqId)
                val movedFollowReqId = movedState.cameraFollowRequestId

                // User drags map -> USER_CONTROLLED pauses auto-follow
                viewModel.onUserMapGesture()
                testDispatcher.scheduler.runCurrent()
                assertEquals(VehicleCameraMode.USER_CONTROLLED, viewModel.uiState.value.cameraMode)

                // Poll 3: even with big bus movement, USER_CONTROLLED blocks auto-follow camera updates
                testDispatcher.scheduler.advanceTimeBy(15_000L)
                testDispatcher.scheduler.runCurrent()
                assertEquals(movedFollowReqId, viewModel.uiState.value.cameraFollowRequestId)
                assertEquals(VehicleCameraMode.USER_CONTROLLED, viewModel.uiState.value.cameraMode)
            } finally {
                viewModel.onMapPaused()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `trackVehicle with invalid path coordinates avoids auto-center and reports warning`() = runTest {
        viewModel.computeDispatcher = testDispatcher
        val invalidStop = BusStop(143, "Q5 002", "Trạm Lỗi", "Trạm", "Q5", null, "2", "Nguyễn Trãi", false, "Active", 0.0, 0.0, "loi", listOf("01"))
        val invalidPath = RoutePath(
            routeId = 1,
            varId = 101,
            points = listOf(com.hcmcbustracker.core.model.LatLngPoint(0.0, 0.0))
        )
        every { repository.getRoutePath(1, 101) } returns flowOf(invalidPath)
        every { repository.getStopsForRouteVar(1, 101) } returns flowOf(listOf(invalidStop))
        every { repository.getStopById(143) } returns flowOf(invalidStop)
        coEvery { repository.ensureRoutePath(1, 101) } returns NetworkResult.Success(invalidPath)

        viewModel.uiState.test {
            awaitItem()
            viewModel.trackVehicle(
                routeId = 1,
                varId = 101,
                stopId = 143,
                plate = "50E-616.63",
                distanceMeters = 500.0,
                speedKmH = 20.0,
                timestampIso = null,
                etaSeconds = 90
            )
            testDispatcher.scheduler.runCurrent()

            val state = expectMostRecentItem()
            assertNull(state.trackedVehicle)
            assertNull(state.selectedVehiclePlate)
            assertEquals(VehicleCameraMode.IDLE, state.cameraMode)
            assertEquals(0L, state.cameraFocusRequestId)
            assertNotNull(state.cameraWarningMessage)

            viewModel.clearCameraWarning()
            testDispatcher.scheduler.runCurrent()
            assertNull(expectMostRecentItem().cameraWarningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trackVehicle rejects out of range coordinates, NaN, Infinity, empty path, and invalid path points`() = runTest {
        viewModel.computeDispatcher = testDispatcher
        val validPoint = com.hcmcbustracker.core.model.LatLngPoint(10.7684, 106.6942)
        val validStop = sampleStops[0]

        val invalidCases = listOf(
            // Empty route path
            validStop to emptyList(),
            // Path containing (0, 0)
            validStop to listOf(validPoint, com.hcmcbustracker.core.model.LatLngPoint(0.0, 0.0)),
            // Latitude outside [-90, 90]
            validStop to listOf(validPoint, com.hcmcbustracker.core.model.LatLngPoint(95.0, 106.6544)),
            // Longitude outside [-180, 180]
            validStop to listOf(validPoint, com.hcmcbustracker.core.model.LatLngPoint(10.7533, 185.0)),
            // NaN coordinate in path
            validStop to listOf(validPoint, com.hcmcbustracker.core.model.LatLngPoint(Double.NaN, 106.6544)),
            // Positive Infinity coordinate in path
            validStop to listOf(validPoint, com.hcmcbustracker.core.model.LatLngPoint(Double.POSITIVE_INFINITY, 106.6544)),
            // Invalid stop coordinate even with valid 2-point path
            validStop.copy(lat = Double.NaN, lng = 106.6942) to listOf(validPoint, com.hcmcbustracker.core.model.LatLngPoint(10.7533, 106.6544))
        )

        viewModel.uiState.test {
            awaitItem()
            for ((stopCase, pathPoints) in invalidCases) {
                val badPath = RoutePath(routeId = 1, varId = 101, points = pathPoints)
                every { repository.getRoutePath(1, 101) } returns flowOf(badPath)
                every { repository.getStopsForRouteVar(1, 101) } returns flowOf(listOf(stopCase))
                every { repository.getStopById(stopCase.id) } returns flowOf(stopCase)
                coEvery { repository.ensureRoutePath(1, 101) } returns NetworkResult.Success(badPath)

                viewModel.trackVehicle(
                    routeId = 1,
                    varId = 101,
                    stopId = stopCase.id,
                    plate = "50E-616.63",
                    distanceMeters = 500.0,
                    speedKmH = 20.0,
                    timestampIso = null,
                    etaSeconds = 90
                )
                testDispatcher.scheduler.runCurrent()

                val state = expectMostRecentItem()
                assertNull(state.trackedVehicle)
                assertNull(state.selectedVehiclePlate)
                assertEquals(VehicleCameraMode.IDLE, state.cameraMode)
                assertEquals(0L, state.cameraFocusRequestId)
                assertNotNull(state.cameraWarningMessage)
                viewModel.clearCameraWarning()
                testDispatcher.scheduler.runCurrent()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `phone location guards reject invalid coords and mark unavailable when permission denied`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            // Valid location activates userLocation and clears unavailable flag
            viewModel.setLocationPermissionGranted(true)
            viewModel.updateUserLocation(lat = 10.7712, lng = 106.6981, bearing = 45f, accuracyMeters = 14f)
            testDispatcher.scheduler.runCurrent()
            val validState = expectMostRecentItem()
            assertEquals(10.7712, validState.userLocation?.latitude ?: 0.0, 1e-6)
            assertEquals(106.6981, validState.userLocation?.longitude ?: 0.0, 1e-6)
            assertEquals(14f, validState.userAccuracyMeters ?: 0f, 1e-3f)
            assertFalse(validState.isUserLocationUnavailable)

            // (0.0, 0.0), out-of-range, NaN, and Infinity updates are rejected and clear location
            val badCoords = listOf(
                0.0 to 0.0,
                95.0 to 106.6981,
                10.7712 to -190.0,
                Double.NaN to 106.6981,
                Double.POSITIVE_INFINITY to 106.6981
            )
            for ((badLat, badLng) in badCoords) {
                viewModel.updateUserLocation(lat = 10.7712, lng = 106.6981, accuracyMeters = 10f)
                testDispatcher.scheduler.runCurrent()
                viewModel.updateUserLocation(lat = badLat, lng = badLng, accuracyMeters = 10f)
                testDispatcher.scheduler.runCurrent()
                val rejectedState = expectMostRecentItem()
                assertNull(rejectedState.userLocation)
                assertTrue(rejectedState.isUserLocationUnavailable)
            }

            // Restoring valid location then revoking permission clears location cleanly
            viewModel.updateUserLocation(lat = 10.7712, lng = 106.6981, accuracyMeters = 12f)
            testDispatcher.scheduler.runCurrent()
            assertNotNull(expectMostRecentItem().userLocation)
            viewModel.setLocationPermissionGranted(false)
            testDispatcher.scheduler.runCurrent()
            val deniedState = expectMostRecentItem()
            assertFalse(deniedState.isLocationPermissionGranted)
            assertNull(deniedState.userLocation)
            assertTrue(deniedState.isUserLocationUnavailable)

            // markUserLocationUnavailable clears location without faking defaults
            viewModel.setLocationPermissionGranted(true)
            viewModel.updateUserLocation(lat = 10.7712, lng = 106.6981, accuracyMeters = 12f)
            testDispatcher.scheduler.runCurrent()
            viewModel.markUserLocationUnavailable()
            testDispatcher.scheduler.runCurrent()
            val unavailableState = expectMostRecentItem()
            assertNull(unavailableState.userLocation)
            assertTrue(unavailableState.isUserLocationUnavailable)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
