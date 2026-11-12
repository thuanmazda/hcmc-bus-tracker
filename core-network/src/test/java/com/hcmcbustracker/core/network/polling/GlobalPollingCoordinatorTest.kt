package com.hcmcbustracker.core.network.polling

import com.hcmcbustracker.core.model.AdaptivePollingConfig
import com.hcmcbustracker.core.model.NetworkResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GlobalPollingCoordinatorTest {

    @Test
    fun `enforces dynamic global concurrency cap across concurrent stops and never exceeds C=4`() = runTest {
        var now = 1_000_000L
        val coordinator = GlobalPollingCoordinator(
            config = AdaptivePollingConfig(
                initialConcurrency = 1,
                maxConcurrency = 4,
                increaseSuccessWindow = 3,
                shortLivedCacheTtlMs = 0L
            ),
            nowMillis = { now },
            nextRandomDouble = { 0.5 },
            coalescerScope = backgroundScope,
            delayFn = { ms -> delay(ms) }
        )

        val currentInFlight = AtomicInteger(0)
        val peakInFlight = AtomicInteger(0)

        // Dispatch 12 unique stops concurrently
        val jobs = (1..12).map { stopId ->
            async {
                coordinator.executeCoalesced(
                    PollingResourceKey("ebms_predict_by_stop_id", mapOf("stopId" to stopId.toString()))
                ) {
                    val active = currentInFlight.incrementAndGet()
                    peakInFlight.updateAndGet { prev -> maxOf(prev, active) }
                    now += 150L
                    delay(150L)
                    currentInFlight.decrementAndGet()
                    NetworkResult.Success("ok-$stopId")
                }
            }
        }
        jobs.awaitAll()

        assertEquals(0, currentInFlight.get())
        assertTrue("Peak in-flight should climb > 1 during sustained healthy runs", peakInFlight.get() >= 2)
        assertTrue("Peak in-flight must NEVER exceed maxConcurrency=4", peakInFlight.get() <= 4)
        assertEquals(12L, coordinator.diagnostics.value.totalRequestsExecuted)
        assertEquals(0, coordinator.diagnostics.value.requestsInFlight)
    }

    @Test
    fun `http 429 drops concurrency and respects Retry-After delay across shared coordinator`() = runTest {
        var now = 1_000_000L
        val recordedDelays = mutableListOf<Long>()
        val coordinator = GlobalPollingCoordinator(
            config = AdaptivePollingConfig(
                initialConcurrency = 1,
                increaseSuccessWindow = 2,
                backoffJitterFraction = 0.20,
                shortLivedCacheTtlMs = 0L
            ),
            nowMillis = { now },
            nextRandomDouble = { 0.0 },
            coalescerScope = backgroundScope,
            delayFn = { ms ->
                recordedDelays.add(ms)
                now += ms
                delay(ms)
            }
        )

        // Ramp concurrency to 3
        repeat(4) { i ->
            coordinator.executeCoalesced(PollingResourceKey("ep", mapOf("k" to "$i"))) {
                NetworkResult.Success("ok")
            }
        }
        assertEquals(3, coordinator.concurrencyController.currentConcurrency)

        // Trigger HTTP 429 with Retry-After = 10s
        coordinator.executeCoalesced(PollingResourceKey("ep", mapOf("k" to "rate"))) {
            NetworkResult.Error(message = "429", code = 429, retryAfterSeconds = 10L)
        }
        assertEquals(1, coordinator.concurrencyController.currentConcurrency)
        assertEquals(1L, coordinator.diagnostics.value.http429Count)

        // Next call while inside cooldown waits the Retry-After window instead of hammering immediately
        coordinator.executeCoalesced(PollingResourceKey("ep", mapOf("k" to "next"))) {
            NetworkResult.Success("recovered")
        }
        assertTrue(recordedDelays.any { it >= 10_000L })
    }

    @Test
    fun `shared-stop coalescing deduplicates identical checkpoints in multi-route sweeps`() = runTest {
        val now = 1_000_000L
        val coordinator = GlobalPollingCoordinator(
            config = AdaptivePollingConfig(initialConcurrency = 2, shortLivedCacheTtlMs = 5_000L),
            nowMillis = { now },
            coalescerScope = backgroundScope,
            delayFn = { ms -> delay(ms) }
        )

        val actualHttpCalls = AtomicInteger(0)
        // Simulate 3 routes (01, 44, 102) all sampling shared hub stop 4223 simultaneously
        val results = listOf("route_01", "route_44", "route_102").map { _ ->
            async {
                coordinator.executeCoalesced(
                    PollingResourceKey("ebms_predict_by_stop_id", mapOf("stopId" to "4223"))
                ) {
                    actualHttpCalls.incrementAndGet()
                    delay(100L)
                    NetworkResult.Success("shared_stop_payload")
                }
            }
        }.awaitAll()

        assertEquals(3, results.size)
        assertTrue(results.all { it.isSuccess })
        assertEquals(1, actualHttpCalls.get())
        assertEquals(2L, coordinator.diagnostics.value.deduplicatedRequestCount)
    }
}
