package com.hcmcbustracker.core.network.polling

import com.hcmcbustracker.core.model.ArrivalVehicle
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.Prediction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SharedStopCoalescerTest {

    private fun predWithTimestamp(gpsTs: Long, plate: String = "50E61663"): List<Prediction> {
        return listOf(
            Prediction(
                routeId = 1,
                routeNo = "01",
                routeName = "Bến Thành - Chợ Lớn",
                stopId = 4223,
                stopName = "Hàm Nghi",
                varId = 1,
                destinationName = "Chợ Lớn",
                vehicles = listOf(
                    ArrivalVehicle(
                        plateNumber = plate,
                        etaSeconds = 120,
                        distanceMeters = 500.0,
                        speedKmH = 22.0,
                        timestampIso = java.time.Instant.ofEpochMilli(gpsTs).toString(),
                        status = 0
                    )
                )
            )
        )
    }

    @Test
    fun `two concurrent identical requests coalesce into a single execution`() = runTest {
        var now = 1_000_000L
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coalescer = SharedStopCoalescer(
            shortCacheTtlMs = 5_000L,
            nowMillis = { now },
            parentScope = TestScope(dispatcher)
        )

        val executions = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val key = PollingResourceKey("ebms_predict_by_stop_id", mapOf("stopId" to "4223"))

        val first = async {
            coalescer.execute(key) {
                executions.incrementAndGet()
                gate.await()
                NetworkResult.Success(predWithTimestamp(900_000L))
            }
        }
        testScheduler.runCurrent()

        val second = async {
            coalescer.execute(key) {
                executions.incrementAndGet()
                NetworkResult.Success(predWithTimestamp(900_000L))
            }
        }
        testScheduler.runCurrent()

        gate.complete(Unit)
        val r1 = first.await()
        val r2 = second.await()

        assertEquals(1, executions.get())
        assertFalse(r1.wasDeduplicated)
        assertTrue(r2.wasDeduplicated)
        assertEquals(0, coalescer.inFlightCount())
        coalescer.clearAll()
    }

    @Test
    fun `different scope keys never coalesce and expired TTL triggers fresh request`() = runTest {
        var now = 1_000_000L
        val coalescer = SharedStopCoalescer(
            shortCacheTtlMs = 5_000L,
            nowMillis = { now },
            parentScope = backgroundScope
        )
        val calls = AtomicInteger(0)

        val keyStop4223 = PollingResourceKey("ebms_predict_by_stop_id", mapOf("stopId" to "4223"))
        val keyStop1401 = PollingResourceKey("ebms_predict_by_stop_id", mapOf("stopId" to "1401"))
        val keyVar1 = PollingResourceKey("ebms_predict_next_stops", mapOf("routeId" to "1", "varId" to "1", "stopId" to "4223"))
        val keyVar2 = PollingResourceKey("ebms_predict_next_stops", mapOf("routeId" to "1", "varId" to "2", "stopId" to "4223"))

        coalescer.execute(keyStop4223) { calls.incrementAndGet(); NetworkResult.Success(predWithTimestamp(900_000L)) }
        coalescer.execute(keyStop1401) { calls.incrementAndGet(); NetworkResult.Success(predWithTimestamp(900_000L)) }
        coalescer.execute(keyVar1) { calls.incrementAndGet(); NetworkResult.Success(predWithTimestamp(900_000L)) }
        coalescer.execute(keyVar2) { calls.incrementAndGet(); NetworkResult.Success(predWithTimestamp(900_000L)) }
        assertEquals(4, calls.get())

        // Within 5s TTL without bypass -> hits short-lived cache
        now += 2_000L
        val cacheHit = coalescer.execute(keyStop4223) {
            calls.incrementAndGet()
            NetworkResult.Success(predWithTimestamp(900_000L))
        }
        assertTrue(cacheHit.wasCacheHit)
        assertEquals(4, calls.get())

        // After 5s TTL expires -> executes a fresh HTTP call cleanly
        now += 4_000L
        val freshAfterExpiry = coalescer.execute(keyStop4223) {
            calls.incrementAndGet()
            NetworkResult.Success(predWithTimestamp(950_000L))
        }
        assertFalse(freshAfterExpiry.wasCacheHit)
        assertEquals(5, calls.get())
    }

    @Test
    fun `stale bypass response never overwrites newer cached gps timestamp`() = runTest {
        var now = 1_000_000L
        val coalescer = SharedStopCoalescer(
            shortCacheTtlMs = 5_000L,
            nowMillis = { now },
            parentScope = backgroundScope
        )
        val key = PollingResourceKey("ebms_predict_by_stop_id", mapOf("stopId" to "4223"))

        // Newer snapshot (dts = 960_000) cached first
        coalescer.execute(key, bypassCache = true) {
            NetworkResult.Success(predWithTimestamp(960_000L))
        }

        // Forced call returning an OLDER delayed dts = 910_000 must NOT replace newer cache entry
        now += 1_000L
        coalescer.execute(key, bypassCache = true) {
            NetworkResult.Success(predWithTimestamp(910_000L))
        }

        val cached = coalescer.execute(key, bypassCache = false) {
            NetworkResult.Success(predWithTimestamp(0L))
        }
        assertTrue(cached.wasCacheHit)
        assertEquals(960_000L, cached.result.getOrNull()?.first()?.vehicles?.first()?.gpsTimestampMillis)
    }

    @Test
    fun `cancelling all waiters cancels underlying request and avoids leaked in-flight jobs`() = runTest {
        val coalescer = SharedStopCoalescer(
            shortCacheTtlMs = 5_000L,
            parentScope = backgroundScope
        )
        val started = CompletableDeferred<Unit>()
        val cancelledFlag = CompletableDeferred<Boolean>()
        val key = PollingResourceKey("ebms_predict_by_stop_id", mapOf("stopId" to "4223"))

        val job1 = launch {
            coalescer.execute(key) {
                started.complete(Unit)
                try {
                    CompletableDeferred<NetworkResult<List<Prediction>>>().await()
                } catch (t: Throwable) {
                    cancelledFlag.complete(true)
                    throw t
                }
            }
        }
        started.await()
        job1.cancel()
        job1.join()

        assertTrue(cancelledFlag.await())
        assertEquals(0, coalescer.inFlightCount())
    }
}
