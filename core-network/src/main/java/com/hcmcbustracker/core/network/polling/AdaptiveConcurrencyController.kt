package com.hcmcbustracker.core.network.polling

import com.hcmcbustracker.core.model.AdaptivePollingConfig
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Outcome category used by [AdaptiveConcurrencyController] to adjust concurrency via AIMD.
 */
sealed class PollAttemptOutcome {
    data class Success(val rttMs: Long) : PollAttemptOutcome()
    data class RateLimited(val retryAfterSeconds: Long?, val rttMs: Long) : PollAttemptOutcome()
    data class ServerError(val httpCode: Int, val rttMs: Long) : PollAttemptOutcome()
    data class Timeout(val rttMs: Long) : PollAttemptOutcome()
    data class NetworkFailure(val rttMs: Long) : PollAttemptOutcome()
}

/**
 * Additive-Increase / Multiplicative-Decrease (AIMD) concurrency controller.
 *
 * Guarantees:
 * - Starts conservatively at [AdaptivePollingConfig.initialConcurrency] (default = 1).
 * - Never exceeds [AdaptivePollingConfig.maxConcurrency] (default = 4) or drops below 1.
 * - Never increases concurrency after a single success or during active cooldown/backoff.
 * - Halves concurrency immediately on HTTP 429 and honors Retry-After + jitter.
 */
class AdaptiveConcurrencyController(
    val config: AdaptivePollingConfig = AdaptivePollingConfig.DEFAULT,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val nextRandomDouble: () -> Double = { Random.Default.nextDouble() }
) {
    private val lock = Any()
    private val recentErrors = ArrayDeque<Boolean>(config.errorRateWindowSize + 1)

    private var _currentConcurrency: Int = config.initialConcurrency
    private var consecutiveHealthyCount: Int = 0
    private var consecutiveFailureCount: Int = 0
    private var cooldownUntilMillis: Long = 0L

    private var lastRttMs: Long = 0L
    private var rttSumMs: Long = 0L
    private var rttCount: Long = 0L
    private var _http429Count: Long = 0L
    private var _http5xxCount: Long = 0L
    private var _timeoutCount: Long = 0L

    val currentConcurrency: Int
        get() = synchronized(lock) { _currentConcurrency }

    fun isInCooldown(nowMs: Long = nowMillis()): Boolean = synchronized(lock) {
        nowMs < cooldownUntilMillis
    }

    fun remainingCooldownMs(nowMs: Long = nowMillis()): Long = synchronized(lock) {
        max(0L, cooldownUntilMillis - nowMs)
    }

    fun recordOutcome(outcome: PollAttemptOutcome): Int = synchronized(lock) {
        val now = nowMillis()
        when (outcome) {
            is PollAttemptOutcome.Success -> {
                recordRttLocked(outcome.rttMs)
                pushWindowLocked(isError = false)
                consecutiveFailureCount = 0

                val inCooldown = now < cooldownUntilMillis
                val isRttHealthy = outcome.rttMs <= config.rttSpikeThresholdMs
                val errorRate = windowErrorRateLocked()

                if (!inCooldown && isRttHealthy && errorRate < config.errorRateDowngradeThreshold) {
                    consecutiveHealthyCount++
                    if (consecutiveHealthyCount >= config.increaseSuccessWindow &&
                        _currentConcurrency < config.maxConcurrency
                    ) {
                        _currentConcurrency = min(config.maxConcurrency, _currentConcurrency + 1)
                        consecutiveHealthyCount = 0
                    }
                } else if (!isRttHealthy) {
                    // Sustained/sudden RTT spike prevents ramp-up and gently pulls concurrency back
                    consecutiveHealthyCount = 0
                    if (_currentConcurrency > config.minConcurrency && recentErrors.size >= 3) {
                        _currentConcurrency = max(config.minConcurrency, _currentConcurrency - 1)
                    }
                }
            }

            is PollAttemptOutcome.RateLimited -> {
                recordRttLocked(outcome.rttMs)
                pushWindowLocked(isError = true)
                _http429Count++
                consecutiveHealthyCount = 0
                consecutiveFailureCount++

                // Multiplicative decrease on explicit rate-limit signal
                _currentConcurrency = max(config.minConcurrency, _currentConcurrency / 2)

                val baseRetryMs = ((outcome.retryAfterSeconds ?: (config.default429BackoffMs / 1000L)) * 1000L)
                    .coerceIn(config.min429BackoffMs, config.max429BackoffMs)
                val positiveJitterMs = (baseRetryMs * config.backoffJitterFraction * nextRandomDouble().coerceIn(0.0, 1.0)).roundToLong()
                cooldownUntilMillis = max(cooldownUntilMillis, now + baseRetryMs + positiveJitterMs)
            }

            is PollAttemptOutcome.ServerError -> {
                recordRttLocked(outcome.rttMs)
                pushWindowLocked(isError = true)
                if (outcome.httpCode in 500..599) {
                    _http5xxCount++
                }
                handleTransientDegradationLocked(now, severe = outcome.httpCode == 503)
            }

            is PollAttemptOutcome.Timeout -> {
                recordRttLocked(outcome.rttMs)
                pushWindowLocked(isError = true)
                _timeoutCount++
                handleTransientDegradationLocked(now, severe = consecutiveFailureCount >= 1)
            }

            is PollAttemptOutcome.NetworkFailure -> {
                recordRttLocked(outcome.rttMs)
                pushWindowLocked(isError = true)
                handleTransientDegradationLocked(now, severe = false)
            }
        }
        _currentConcurrency
    }

    private fun handleTransientDegradationLocked(now: Long, severe: Boolean) {
        consecutiveHealthyCount = 0
        consecutiveFailureCount++
        val shouldStepDown = severe ||
            windowErrorRateLocked() >= config.errorRateDowngradeThreshold ||
            consecutiveFailureCount >= 2

        if (shouldStepDown) {
            _currentConcurrency = if (severe && _currentConcurrency > 2) {
                max(config.minConcurrency, _currentConcurrency / 2)
            } else {
                max(config.minConcurrency, _currentConcurrency - 1)
            }
        }

        val expFactor = 1L shl min(4, consecutiveFailureCount - 1)
        val rawBackoff = (config.baseRetryBackoffMs * expFactor).coerceAtMost(config.maxRetryBackoffMs)
        val jitterMultiplier = 1.0 + ((nextRandomDouble().coerceIn(0.0, 1.0) * 2.0 - 1.0) * config.backoffJitterFraction)
        val backoffMs = (rawBackoff * jitterMultiplier).roundToLong().coerceIn(
            config.baseRetryBackoffMs / 2L,
            config.maxRetryBackoffMs
        )
        cooldownUntilMillis = max(cooldownUntilMillis, now + backoffMs)
    }

    /**
     * Computes jittered backoff interval for caller poll loops honoring optional Retry-After.
     */
    fun computeNextPollIntervalMs(
        basePollIntervalMs: Long,
        consecutiveFailures: Int,
        retryAfterSeconds: Long? = null
    ): Long {
        if (retryAfterSeconds != null) {
            val clamped = (retryAfterSeconds * 1000L).coerceIn(config.min429BackoffMs, config.max429BackoffMs)
            val jitter = (clamped * config.backoffJitterFraction * nextRandomDouble().coerceIn(0.0, 1.0)).roundToLong()
            return (clamped + jitter).coerceAtMost(config.max429BackoffMs)
        }
        if (consecutiveFailures <= 0) {
            return basePollIntervalMs.coerceIn(config.baseRetryBackoffMs, config.maxRetryBackoffMs)
        }
        val factor = 1L shl min(3, consecutiveFailures - 1)
        val raw = (basePollIntervalMs * factor).coerceIn(config.baseRetryBackoffMs, config.maxRetryBackoffMs)
        val jitter = ((nextRandomDouble().coerceIn(0.0, 1.0) * 2.0 - 1.0) * config.backoffJitterFraction * raw).roundToLong()
        return (raw + jitter).coerceIn(config.baseRetryBackoffMs, config.maxRetryBackoffMs)
    }

    fun snapshotStats(nowMs: Long = nowMillis()): ControllerStats = synchronized(lock) {
        ControllerStats(
            currentConcurrency = _currentConcurrency,
            maxConcurrency = config.maxConcurrency,
            lastHttpRttMs = lastRttMs,
            avgHttpRttMs = if (rttCount > 0L) rttSumMs / rttCount else 0L,
            http429Count = _http429Count,
            http5xxCount = _http5xxCount,
            timeoutCount = _timeoutCount,
            isInCooldown = nowMs < cooldownUntilMillis
        )
    }

    private fun recordRttLocked(rttMs: Long) {
        val safeRtt = max(0L, rttMs)
        lastRttMs = safeRtt
        rttSumMs += safeRtt
        rttCount++
    }

    private fun pushWindowLocked(isError: Boolean) {
        if (recentErrors.size >= config.errorRateWindowSize) {
            recentErrors.removeFirst()
        }
        recentErrors.addLast(isError)
    }

    private fun windowErrorRateLocked(): Double {
        if (recentErrors.isEmpty()) return 0.0
        val errors = recentErrors.count { it }
        return errors.toDouble() / recentErrors.size.toDouble()
    }

    data class ControllerStats(
        val currentConcurrency: Int,
        val maxConcurrency: Int,
        val lastHttpRttMs: Long,
        val avgHttpRttMs: Long,
        val http429Count: Long,
        val http5xxCount: Long,
        val timeoutCount: Long,
        val isInCooldown: Boolean
    )
}
