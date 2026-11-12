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

/**
 * Synthetic coroutine-based performance comparison across 5, 10, and 20 routes at C = 1, 2, 3, 4.
 *
 * IMPORTANT: Uses fake deterministic delays on virtual TestCoroutineScheduler — NOT live EBMS requests.
 */
class PollingBenchmarkTest {

    data class BenchmarkRow(
        val routes: Int,
        val concurrencyCap: Int,
        val coalescingEnabled: Boolean,
        val cycleDurationMs: Long,
        val totalLogicalRequests: Int,
        val actualNetworkRequests: Int,
        val deduplicatedOrCached: Long,
        val peakInFlight: Int
    )

    @Test
    fun `synthetic benchmark compares sequential vs adaptive bounded concurrent polling with hub coalescing`() = runTest {
        val rows = mutableListOf<BenchmarkRow>()

        for (routeCount in listOf(5, 10, 20)) {
            // Model 5 checkpoints per route where 1 central hub stop (id=4223) & 1 corridor stop (id=1401)
            // are shared across routes, plus 3 route-specific stops.
            val checkpointMatrix = (1..routeCount).map { r ->
                listOf(4223, 1401, r * 100 + 1, r * 100 + 2, r * 100 + 3)
            }

            // Before: Sequential C=1 without shared-stop coalescing
            rows += runSyntheticSweep(
                routes = routeCount,
                checkpointMatrix = checkpointMatrix,
                cap = 1,
                coalesceSharedStops = false
            )

            // After: Bounded / Adaptive across C = 1, 2, 3, 4 with shared-stop coalescing
            for (c in 1..4) {
                rows += runSyntheticSweep(
                    routes = routeCount,
                    checkpointMatrix = checkpointMatrix,
                    cap = c,
                    coalesceSharedStops = true
                )
            }
        }

        // Verify coalescing saves substantial HTTP calls and C=4 reduces cycle time dramatically without exceeding C
        val seq20 = rows.first { it.routes == 20 && !it.coalescingEnabled }
        val adp20C4 = rows.first { it.routes == 20 && it.coalescingEnabled && it.concurrencyCap == 4 }

        assertEquals(100, seq20.actualNetworkRequests)
        assertTrue("Coalescing must reduce actual HTTP calls sharply (100 -> ${adp20C4.actualNetworkRequests})", adp20C4.actualNetworkRequests < seq20.actualNetworkRequests)
        assertTrue("C=4 adaptive cycle must be >3x faster than sequential", adp20C4.cycleDurationMs * 3 < seq20.cycleDurationMs)
        assertTrue("Peak in-flight must respect cap=4", adp20C4.peakInFlight <= 4)

        println("SYNTHETIC_BENCHMARK_TABLE_START")
        for (r in rows) {
            println(
                "routes=${r.routes},cap=${r.concurrencyCap},coalesce=${r.coalescingEnabled}," +
                    "cycleMs=${r.cycleDurationMs},logical=${r.totalLogicalRequests}," +
                    "actual=${r.actualNetworkRequests},saved=${r.deduplicatedOrCached},peak=${r.peakInFlight}"
            )
        }
        println("SYNTHETIC_BENCHMARK_TABLE_END")
    }

    private suspend fun kotlinx.coroutines.test.TestScope.runSyntheticSweep(
        routes: Int,
        checkpointMatrix: List<List<Int>>,
        cap: Int,
        coalesceSharedStops: Boolean
    ): BenchmarkRow {
        val scheduler = testScheduler
        val coordinator = GlobalPollingCoordinator(
            config = AdaptivePollingConfig(
                initialConcurrency = cap,
                minConcurrency = 1,
                maxConcurrency = cap,
                increaseSuccessWindow = 2,
                shortLivedCacheTtlMs = if (coalesceSharedStops) 5_000L else 0L
            ),
            nowMillis = { scheduler.currentTime + 1_000_000L },
            coalescerScope = backgroundScope,
            delayFn = { ms -> delay(ms) }
        )

        val actualRequests = AtomicInteger(0)
        val inFlightNow = AtomicInteger(0)
        val peakInFlight = AtomicInteger(0)
        val startVirtualMs = scheduler.currentTime

        val jobs = checkpointMatrix.flatMapIndexed { rIdx, stops ->
            stops.mapIndexed { sIdx, stopId ->
                async {
                    val key = if (coalesceSharedStops) {
                        PollingResourceKey("ebms_predict_by_stop_id", mapOf("stopId" to stopId.toString()))
                    } else {
                        PollingResourceKey("ebms_predict_by_stop_id", mapOf("stopId" to "${stopId}_${rIdx}_$sIdx"))
                    }
                    coordinator.executeCoalesced(key) {
                        val active = inFlightNow.incrementAndGet()
                        peakInFlight.updateAndGet { prev -> maxOf(prev, active) }
                        actualRequests.incrementAndGet()
                        delay(250L) // Simulated EBMS RTT
                        inFlightNow.decrementAndGet()
                        NetworkResult.Success("ok")
                    }
                }
            }
        }
        jobs.awaitAll()
        val endVirtualMs = scheduler.currentTime
        val diag = coordinator.diagnostics.value
        coordinator.coalescer.clearAll()

        return BenchmarkRow(
            routes = routes,
            concurrencyCap = cap,
            coalescingEnabled = coalesceSharedStops,
            cycleDurationMs = endVirtualMs - startVirtualMs,
            totalLogicalRequests = routes * 5,
            actualNetworkRequests = actualRequests.get(),
            deduplicatedOrCached = diag.deduplicatedRequestCount + diag.cacheHitCount,
            peakInFlight = peakInFlight.get()
        )
    }
}
