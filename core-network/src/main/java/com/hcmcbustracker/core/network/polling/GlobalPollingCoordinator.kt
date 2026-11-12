package com.hcmcbustracker.core.network.polling

import com.hcmcbustracker.core.model.AdaptivePollingConfig
import com.hcmcbustracker.core.model.EndpointCapability
import com.hcmcbustracker.core.model.EndpointCapabilityRegistry
import com.hcmcbustracker.core.model.GpsSourceAdapter
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.PollingDiagnosticsSnapshot
import com.hcmcbustracker.core.model.Prediction
import com.hcmcbustracker.core.network.api.EbmsApiService
import com.hcmcbustracker.core.network.mapper.toDomain
import com.hcmcbustracker.core.network.util.SafeApiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.random.Random

/**
 * Process-wide polling coordinator enforcing:
 * - Global dynamic concurrency limit controlled by AIMD ([1..4]).
 * - Shared-stop request deduplication + short-lived freshness-checked caching.
 * - Cooldown & HTTP 429 Retry-After enforcement across callers.
 * - Cooperative structured cancellation when screens/routes stop observing.
 * - Safe, non-secret telemetry snapshot updates ([PollingDiagnosticsSnapshot]).
 */
@Singleton
class GlobalPollingCoordinator(
    val config: AdaptivePollingConfig = AdaptivePollingConfig.DEFAULT,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    nextRandomDouble: () -> Double = { Random.Default.nextDouble() },
    coalescerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val delayFn: suspend (Long) -> Unit = { ms -> delay(ms) }
) {
    @Inject
    constructor() : this(config = AdaptivePollingConfig.DEFAULT)

    val concurrencyController = AdaptiveConcurrencyController(
        config = config,
        nowMillis = nowMillis,
        nextRandomDouble = nextRandomDouble
    )

    val coalescer = SharedStopCoalescer(
        shortCacheTtlMs = config.shortLivedCacheTtlMs,
        nowMillis = nowMillis,
        parentScope = coalescerScope
    )

    private val permitMutex = Mutex()
    private val waitQueue = ArrayDeque<Waiter>()
    private var activePermits = 0

    private val metricsLock = Any()
    private var deduplicatedCount: Long = 0L
    private var cacheHitCount: Long = 0L
    private var retryCount: Long = 0L
    private var totalExecutedCount: Long = 0L
    private var lastCycleDurationMs: Long = 0L
    private var lastProcessingDelayMs: Long = 0L
    private var minGpsAgeSec: Long? = null
    private var medianGpsAgeSec: Long? = null
    private var maxGpsAgeSec: Long? = null
    private var newDtsPct: Double = 0.0

    private val _diagnostics = MutableStateFlow(PollingDiagnosticsSnapshot())
    val diagnostics: StateFlow<PollingDiagnosticsSnapshot> = _diagnostics.asStateFlow()

    private class Waiter(val signal: CompletableDeferred<Unit>)

    /**
     * Executes an endpoint call through the coalescer + global dynamic AIMD concurrency gate.
     */
    suspend fun <T> executeCoalesced(
        key: PollingResourceKey,
        bypassCache: Boolean = false,
        respectCooldown: Boolean = true,
        block: suspend () -> NetworkResult<T>
    ): NetworkResult<T> {
        val coalesced = coalescer.execute(
            key = key,
            bypassCache = bypassCache
        ) {
            if (respectCooldown) {
                val waitCooldown = concurrencyController.remainingCooldownMs(nowMillis())
                if (waitCooldown > 0L) {
                    delayFn(waitCooldown)
                }
            }

            acquireDynamicPermit()
            val reqStart = nowMillis()
            try {
                val outcome = block()
                val reqEnd = nowMillis()
                val rtt = max(0L, reqEnd - reqStart)
                recordControllerResult(outcome, rtt)
                synchronized(metricsLock) {
                    totalExecutedCount++
                }
                outcome
            } finally {
                releaseDynamicPermit()
            }
        }

        if (coalesced.wasDeduplicated) {
            synchronized(metricsLock) { deduplicatedCount++ }
        }
        if (coalesced.wasCacheHit) {
            synchronized(metricsLock) { cacheHitCount++ }
        }
        publishDiagnostics()
        return coalesced.result
    }

    /**
     * Adapts [EbmsApiService] into a lightweight [GpsSourceAdapter] backed by this coordinator.
     */
    fun createEbmsPredictionAdapter(apiService: EbmsApiService): GpsSourceAdapter {
        val cap = EndpointCapabilityRegistry.resolve(EndpointCapabilityRegistry.ENDPOINT_PREDICT_BY_STOP_ID)
        return object : GpsSourceAdapter {
            override val capability: EndpointCapability = cap

            override suspend fun fetchStopObservations(stopId: Int): NetworkResult<List<Prediction>> {
                val key = PollingResourceKey(
                    endpointId = cap.endpointId,
                    scopeParams = mapOf("stopId" to stopId.toString())
                )
                return executeCoalesced(key = key) {
                    val reqStart = nowMillis()
                    val res = SafeApiCall.execute(timeoutMs = config.requestTimeoutMs) {
                        apiService.getPredictionByStopId(stopId)
                    }
                    val recvAt = nowMillis()
                    res.map { dtos ->
                        dtos.map {
                            it.toDomain(
                                receivedTimeMillis = recvAt,
                                requestStartedTimeMillis = reqStart,
                                predictionReceivedTimeMillis = nowMillis()
                            )
                        }
                    }
                }
            }
        }
    }

    fun recordCycleMetrics(
        cycleDurationMs: Long,
        processingDelayMs: Long,
        gpsAgesSeconds: List<Long>,
        newDtsCount: Int,
        totalComparedCount: Int
    ) {
        synchronized(metricsLock) {
            lastCycleDurationMs = max(0L, cycleDurationMs)
            lastProcessingDelayMs = max(0L, processingDelayMs)
            if (gpsAgesSeconds.isNotEmpty()) {
                val sorted = gpsAgesSeconds.filter { it >= 0L }.sorted()
                if (sorted.isNotEmpty()) {
                    minGpsAgeSec = sorted.first()
                    medianGpsAgeSec = sorted[sorted.size / 2]
                    maxGpsAgeSec = sorted.last()
                }
            }
            newDtsPct = if (totalComparedCount > 0) {
                (newDtsCount.toDouble() / totalComparedCount.toDouble()) * 100.0
            } else {
                0.0
            }
        }
        publishDiagnostics()
    }

    fun recordRetryScheduled() {
        synchronized(metricsLock) { retryCount++ }
        publishDiagnostics()
    }

    private suspend fun acquireDynamicPermit() {
        var waiter: Waiter? = null
        val acquiredImmediately = permitMutex.withLock {
            val limit = concurrencyController.currentConcurrency
            if (activePermits < limit) {
                activePermits++
                true
            } else {
                waiter = Waiter(CompletableDeferred())
                waitQueue.addLast(waiter!!)
                false
            }
        }
        publishDiagnostics()
        if (acquiredImmediately) return

        val myWaiter = waiter!!
        try {
            myWaiter.signal.await()
        } catch (ce: CancellationException) {
            val shouldReleaseHandoff = permitMutex.withLock {
                val removed = waitQueue.remove(myWaiter)
                !removed && myWaiter.signal.isCompleted
            }
            if (shouldReleaseHandoff) {
                releaseDynamicPermit()
            } else {
                publishDiagnostics()
            }
            throw ce
        }
    }

    private suspend fun releaseDynamicPermit() {
        permitMutex.withLock {
            activePermits = max(0, activePermits - 1)
            val limit = concurrencyController.currentConcurrency
            while (activePermits < limit && waitQueue.isNotEmpty()) {
                val next = waitQueue.removeFirst()
                if (!next.signal.isCompleted) {
                    activePermits++
                    next.signal.complete(Unit)
                }
            }
        }
        publishDiagnostics()
    }

    private fun <T> recordControllerResult(result: NetworkResult<T>, rttMs: Long) {
        val outcome = when (result) {
            is NetworkResult.Success -> PollAttemptOutcome.Success(rttMs)
            is NetworkResult.Error -> when (result.code) {
                429 -> PollAttemptOutcome.RateLimited(result.retryAfterSeconds, rttMs)
                408 -> PollAttemptOutcome.Timeout(rttMs)
                in 500..599 -> PollAttemptOutcome.ServerError(result.code ?: 500, rttMs)
                else -> PollAttemptOutcome.NetworkFailure(rttMs)
            }
            is NetworkResult.Loading -> PollAttemptOutcome.Success(rttMs)
        }
        concurrencyController.recordOutcome(outcome)
    }

    private fun publishDiagnostics() {
        val ctrlStats = concurrencyController.snapshotStats(nowMillis())
        val (inFlight, queueLen) = runCatching {
            if (permitMutex.tryLock()) {
                try {
                    activePermits to waitQueue.size
                } finally {
                    permitMutex.unlock()
                }
            } else {
                _diagnostics.value.requestsInFlight to _diagnostics.value.requestQueueLength
            }
        }.getOrElse { _diagnostics.value.requestsInFlight to _diagnostics.value.requestQueueLength }

        _diagnostics.value = synchronized(metricsLock) {
            PollingDiagnosticsSnapshot(
                currentConcurrency = ctrlStats.currentConcurrency,
                maxConcurrency = ctrlStats.maxConcurrency,
                requestsInFlight = inFlight,
                requestQueueLength = queueLen,
                lastHttpRttMs = ctrlStats.lastHttpRttMs,
                avgHttpRttMs = ctrlStats.avgHttpRttMs,
                http429Count = ctrlStats.http429Count,
                http5xxCount = ctrlStats.http5xxCount,
                timeoutCount = ctrlStats.timeoutCount,
                retryCount = retryCount,
                deduplicatedRequestCount = deduplicatedCount,
                cacheHitCount = cacheHitCount,
                totalRequestsExecuted = totalExecutedCount,
                lastCycleDurationMs = lastCycleDurationMs,
                lastProcessingDelayMs = lastProcessingDelayMs,
                minGpsAgeSeconds = minGpsAgeSec,
                medianGpsAgeSeconds = medianGpsAgeSec,
                maxGpsAgeSeconds = maxGpsAgeSec,
                newDtsPercentage = newDtsPct,
                isInCooldown = ctrlStats.isInCooldown
            )
        }
    }
}
