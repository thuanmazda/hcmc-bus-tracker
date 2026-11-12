package com.hcmcbustracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hcmcbustracker.core.database.repository.BusRepositoryImpl
import com.hcmcbustracker.core.model.AdaptivePollingConfig
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.RouteVariantPollingTarget
import com.hcmcbustracker.core.model.RouteVehicleTelemetry
import com.hcmcbustracker.core.network.api.EbmsApiService
import com.hcmcbustracker.core.network.dto.ArrivalVehicleDto
import com.hcmcbustracker.core.network.dto.PredictionDto
import com.hcmcbustracker.core.network.polling.GlobalPollingCoordinator
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MultiRoutePollingTest {

    private lateinit var database: BusTrackerDatabase
    private lateinit var apiService: EbmsApiService
    private val json = Json { ignoreUnknownKeys = true }

    private fun makeStop(id: Int, name: String): BusStop {
        return BusStop(
            id = id,
            code = "Q1 $id",
            name = name,
            stopType = "Trạm",
            zone = "Q1",
            ward = null,
            addressNo = "1",
            street = "Lê Lợi",
            supportDisability = false,
            status = "Active",
            lat = 10.77 + id * 0.0001,
            lng = 106.70 + id * 0.0001,
            searchKeywords = name.lowercase(),
            routes = listOf("01", "44", "102")
        )
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BusTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        apiService = mockk()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `multi-route sweep coalesces shared hub stops and isolates route variants`() = runTest {
        var nowMs = 1_794_470_400_000L
        val coordinator = GlobalPollingCoordinator(
            config = AdaptivePollingConfig(
                initialConcurrency = 1,
                maxConcurrency = 4,
                increaseSuccessWindow = 2,
                shortLivedCacheTtlMs = 5_000L
            ),
            nowMillis = { nowMs },
            coalescerScope = backgroundScope,
            delayFn = { ms -> delay(ms) }
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BusRepositoryImpl(database, apiService, json, context, coordinator)

        val sharedHub = makeStop(4223, "Hàm Nghi")
        val r1Stop = makeStop(101, "Chợ Lớn")
        val r44Stop = makeStop(202, "Bình Quới")
        val r102Stop = makeStop(303, "Bến Xe Miền Tây")

        val callsByStop = ConcurrentHashMap<Int, AtomicInteger>()
        val freshIso = OffsetDateTime.now().minusSeconds(40).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        coEvery { apiService.getPredictionByStopId(any()) } coAnswers {
            val stopId = firstArg<Int>()
            callsByStop.computeIfAbsent(stopId) { AtomicInteger(0) }.incrementAndGet()
            delay(100L)
            listOf(
                PredictionDto(
                    routeId = 1, routeNo = "01", stopId = stopId, stopName = "Stop $stopId", varId = 1,
                    arrivals = listOf(ArrivalVehicleDto("50E61852", 120, 600.0, 22.0, freshIso, 0))
                ),
                PredictionDto(
                    routeId = 65, routeNo = "44", stopId = stopId, stopName = "Stop $stopId", varId = 130,
                    arrivals = listOf(ArrivalVehicleDto("50E20534", 240, 1400.0, 18.0, freshIso, 0))
                ),
                PredictionDto(
                    routeId = 13, routeNo = "102", stopId = stopId, stopName = "Stop $stopId", varId = 26,
                    arrivals = listOf(ArrivalVehicleDto("50E85118", 360, 2100.0, 25.0, freshIso, 0))
                )
            )
        }

        val targets = listOf(
            RouteVariantPollingTarget(1, 1, listOf(sharedHub, r1Stop)),
            RouteVariantPollingTarget(65, 130, listOf(sharedHub, r44Stop)),
            RouteVariantPollingTarget(13, 26, listOf(sharedHub, r102Stop))
        )

        val results = repo.fetchMultiRouteVehicles(targets, priorityRouteId = 1)
        assertEquals(3, results.size)
        // Shared hub stop 4223 MUST have been fetched ONLY ONCE across 3 routes!
        assertEquals(1, callsByStop[4223]?.get() ?: 0)
        assertEquals(1, (results[1 to 1] as NetworkResult.Success).data.vehicles.size)
        assertEquals("50E61852", (results[1 to 1] as NetworkResult.Success).data.vehicles[0].plateNumber)
        assertEquals("50E20534", (results[65 to 130] as NetworkResult.Success).data.vehicles[0].plateNumber)
        assertEquals("50E85118", (results[13 to 26] as NetworkResult.Success).data.vehicles[0].plateNumber)

        val diag = coordinator.diagnostics.value
        assertTrue(diag.totalRequestsExecuted == 4L) // 4223, 101, 202, 303
    }

    @Test
    fun `older delayed response never overwrites newer snapshot and 429 throttles safely`() = runTest {
        var nowMs = System.currentTimeMillis()
        val coordinator = GlobalPollingCoordinator(
            config = AdaptivePollingConfig(
                initialConcurrency = 2,
                maxConcurrency = 4,
                shortLivedCacheTtlMs = 0L
            ),
            nowMillis = { nowMs },
            nextRandomDouble = { 0.0 },
            coalescerScope = backgroundScope,
            delayFn = { ms -> nowMs += ms; delay(ms) }
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BusRepositoryImpl(database, apiService, json, context, coordinator)

        val stop = makeStop(4223, "Hàm Nghi")
        val newerIso = OffsetDateTime.now().minusSeconds(25).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val staleIso = OffsetDateTime.now().minusSeconds(95).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        var step = 0
        coEvery { apiService.getPredictionByStopId(4223) } coAnswers {
            when (step++) {
                0 -> listOf(
                    PredictionDto(
                        routeId = 1, routeNo = "01", stopId = 4223, stopName = "Hàm Nghi", varId = 1,
                        arrivals = listOf(ArrivalVehicleDto("50E61852", 90, 420.0, 20.0, newerIso, 0))
                    )
                )
                1 -> listOf(
                    PredictionDto(
                        routeId = 1, routeNo = "01", stopId = 4223, stopName = "Hàm Nghi", varId = 1,
                        arrivals = listOf(ArrivalVehicleDto("50E61852", 180, 990.0, 20.0, staleIso, 0))
                    )
                )
                else -> throw HttpException(
                    Response.error<Any>(429, "rate limit".toResponseBody("text/plain".toMediaType()))
                )
            }
        }

        val firstRes = repo.fetchRouteVariantVehicles(1, 1, listOf(stop))
        val firstVehicles = (firstRes as NetworkResult.Success).data.vehicles
        assertEquals(420.0, firstVehicles[0].rawDistanceMeters, 0.01)

        val prevMap: Map<String, RouteVehicleTelemetry> = mapOf("50E61852" to firstVehicles[0])
        val rollbackRes = repo.fetchRouteVariantVehicles(1, 1, listOf(stop), prevMap)
        val afterVehicles = (rollbackRes as NetworkResult.Success).data.vehicles
        // Older GPS timestamp 95s ago MUST be rejected in favor of 25s ago snapshot (420m preserved)
        assertEquals(420.0, afterVehicles[0].rawDistanceMeters, 0.01)

        // Third poll returns HTTP 429 -> drops concurrency to 1 & returns clean Error
        val rateLimitRes = repo.fetchRouteVariantVehicles(1, 1, listOf(stop), prevMap)
        assertTrue(rateLimitRes is NetworkResult.Error)
        assertEquals(429, (rateLimitRes as NetworkResult.Error).code)
        assertEquals(1, coordinator.concurrencyController.currentConcurrency)
    }
}
