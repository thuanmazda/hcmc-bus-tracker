package com.hcmcbustracker.core.network.polling

import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.Prediction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Strongly typed deduplication key including endpoint ID and all response-affecting parameters.
 */
data class PollingResourceKey(
    val endpointId: String,
    val scopeParams: Map<String, String>
)

data class CoalescedExecutionResult<T>(
    val result: NetworkResult<T>,
    val wasDeduplicated: Boolean = false,
    val wasCacheHit: Boolean = false
)

/**
 * Coordinates in-flight request deduplication and optional short-lived freshness-checked caching
 * for shared EBMS stop checkpoints.
 *
 * Priority order:
 * 1. In-flight deduplication (multiple concurrent callers await the exact same HTTP execution;
 *    underlying execution cancels only if ALL waiting callers cancel).
 * 2. Freshness-guarded short cache (only returns Success results strictly within TTL and NEVER
 *    replaces newer GPS timestamps).
 */
class SharedStopCoalescer(
    private val shortCacheTtlMs: Long = 5_000L,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    parentScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val executionScope = parentScope
    private val mutex = Mutex()
    private val inFlight = LinkedHashMap<PollingResourceKey, InFlightEntry<Any?>>()
    private val shortCache = LinkedHashMap<PollingResourceKey, CachedEntry<Any?>>()

    private class InFlightEntry<T>(
        val job: Job,
        val deferred: Deferred<NetworkResult<T>>,
        val waiters: AtomicInteger = AtomicInteger(1)
    )

    private data class CachedEntry<T>(
        val result: NetworkResult.Success<T>,
        val cachedAtMillis: Long,
        val maxGpsTimestampMillis: Long?
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> execute(
        key: PollingResourceKey,
        bypassCache: Boolean = false,
        maxGpsTimestampExtractor: (T) -> Long? = { extractMaxGpsTimestamp(it) },
        block: suspend () -> NetworkResult<T>
    ): CoalescedExecutionResult<T> {
        var joinedExisting = false
        var entryToAwait: InFlightEntry<Any?>? = null

        mutex.withLock {
            val now = nowMillis()
            val existingInFlight = inFlight[key]
            if (existingInFlight != null && existingInFlight.job.isActive) {
                existingInFlight.waiters.incrementAndGet()
                entryToAwait = existingInFlight
                joinedExisting = true
            } else {
                if (!bypassCache && shortCacheTtlMs > 0L) {
                    val cached = shortCache[key]
                    if (cached != null && (now - cached.cachedAtMillis) in 0L..shortCacheTtlMs) {
                        val typed = cached.result as NetworkResult.Success<T>
                        return CoalescedExecutionResult(
                            result = typed,
                            wasDeduplicated = false,
                            wasCacheHit = true
                        )
                    } else if (cached != null) {
                        shortCache.remove(key)
                    }
                }

                val taskJob = SupervisorJob(executionScope.coroutineContext[Job])
                val deferred = executionScope.async(taskJob) {
                    @Suppress("UNCHECKED_CAST")
                    block() as NetworkResult<Any?>
                }
                val newEntry = InFlightEntry<Any?>(taskJob, deferred)
                inFlight[key] = newEntry
                entryToAwait = newEntry
            }
        }

        val entry = entryToAwait!!
        val outcome = try {
            entry.deferred.await()
        } catch (ce: CancellationException) {
            cleanupOnWaiterExit(key, entry, cancelIfNoWaiters = true)
            throw ce
        } catch (t: Throwable) {
            cleanupOnWaiterExit(key, entry, cancelIfNoWaiters = true)
            throw t
        }

        val result = outcome as NetworkResult<T>
        cleanupOnWaiterExit(key, entry, cancelIfNoWaiters = false)

        if (!joinedExisting && result is NetworkResult.Success<T> && shortCacheTtlMs > 0L) {
            mutex.withLock {
                val now = nowMillis()
                val newMaxGps = maxGpsTimestampExtractor(result.data)
                val existingCached = shortCache[key]
                val existingMaxGps = existingCached?.maxGpsTimestampMillis
                // Never overwrite cache entry if it holds strictly newer GPS timestamp
                val shouldUpdateCache = existingCached == null ||
                    existingMaxGps == null ||
                    (newMaxGps != null && newMaxGps >= existingMaxGps)
                if (shouldUpdateCache) {
                    @Suppress("UNCHECKED_CAST")
                    shortCache[key] = CachedEntry(
                        result = result as NetworkResult.Success<Any?>,
                        cachedAtMillis = now,
                        maxGpsTimestampMillis = newMaxGps ?: existingMaxGps
                    )
                }
            }
        }

        return CoalescedExecutionResult(
            result = result,
            wasDeduplicated = joinedExisting,
            wasCacheHit = false
        )
    }

    private suspend fun cleanupOnWaiterExit(
        key: PollingResourceKey,
        entry: InFlightEntry<Any?>,
        cancelIfNoWaiters: Boolean
    ) {
        val remaining = entry.waiters.decrementAndGet()
        if (remaining <= 0) {
            mutex.withLock {
                if (inFlight[key] === entry) {
                    inFlight.remove(key)
                }
            }
            if (cancelIfNoWaiters && entry.job.isActive) {
                entry.job.cancel()
            }
        }
    }

    suspend fun clearAll() {
        val toCancel = mutex.withLock {
            shortCache.clear()
            val list = inFlight.values.toList()
            inFlight.clear()
            list
        }
        for (entry in toCancel) {
            entry.job.cancel()
        }
    }

    suspend fun clearCacheOnly() {
        mutex.withLock {
            shortCache.clear()
        }
    }

    suspend fun inFlightCount(): Int = mutex.withLock { inFlight.size }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun extractMaxGpsTimestamp(payload: Any?): Long? {
            val list = payload as? List<*> ?: return null
            var maxTs: Long? = null
            for (item in list) {
                val pred = item as? Prediction ?: continue
                for (v in pred.vehicles) {
                    val ts = v.gpsTimestampMillis ?: continue
                    if (maxTs == null || ts > maxTs) {
                        maxTs = ts
                    }
                }
            }
            return maxTs
        }
    }
}
