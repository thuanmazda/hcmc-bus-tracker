package com.hcmcbustracker.core.network.polling

import com.hcmcbustracker.core.model.AdaptivePollingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveConcurrencyControllerTest {

    @Test
    fun `initializes at C=1 and ramps up conservatively without exceeding C=4`() {
        var now = 1_000_000L
        val config = AdaptivePollingConfig(
            initialConcurrency = 1,
            minConcurrency = 1,
            maxConcurrency = 4,
            increaseSuccessWindow = 4,
            rttSpikeThresholdMs = 1_500L
        )
        val controller = AdaptiveConcurrencyController(
            config = config,
            nowMillis = { now },
            nextRandomDouble = { 0.5 }
        )

        assertEquals(1, controller.currentConcurrency)

        // A single success MUST NOT immediately increase concurrency
        controller.recordOutcome(PollAttemptOutcome.Success(rttMs = 200L))
        assertEquals(1, controller.currentConcurrency)

        // Complete first healthy window (4 successes) -> climbs 1 -> 2
        repeat(3) { controller.recordOutcome(PollAttemptOutcome.Success(rttMs = 220L)) }
        assertEquals(2, controller.currentConcurrency)

        // Second healthy window -> 2 -> 3
        repeat(4) { controller.recordOutcome(PollAttemptOutcome.Success(rttMs = 210L)) }
        assertEquals(3, controller.currentConcurrency)

        // Third healthy window -> 3 -> 4
        repeat(4) { controller.recordOutcome(PollAttemptOutcome.Success(rttMs = 240L)) }
        assertEquals(4, controller.currentConcurrency)

        // Many more healthy calls -> capped strictly at 4
        repeat(20) { controller.recordOutcome(PollAttemptOutcome.Success(rttMs = 180L)) }
        assertEquals(4, controller.currentConcurrency)
    }

    @Test
    fun `http 429 halves concurrency, enforces Retry-After with jitter, and blocks ramp-up during cooldown`() {
        var now = 1_000_000L
        val config = AdaptivePollingConfig(
            initialConcurrency = 1,
            increaseSuccessWindow = 2,
            backoffJitterFraction = 0.20,
            min429BackoffMs = 5_000L,
            max429BackoffMs = 300_000L
        )
        val controller = AdaptiveConcurrencyController(
            config = config,
            nowMillis = { now },
            nextRandomDouble = { 0.5 } // half of +20% jitter -> +10%
        )

        // Ramp to 4
        repeat(6) { controller.recordOutcome(PollAttemptOutcome.Success(200L)) }
        assertEquals(4, controller.currentConcurrency)

        // Hit HTTP 429 with Retry-After = 20s
        val newLimit = controller.recordOutcome(PollAttemptOutcome.RateLimited(retryAfterSeconds = 20L, rttMs = 350L))
        assertEquals(2, newLimit)
        assertTrue(controller.isInCooldown(now))
        // Expected cooldown: 20_000ms + 0.5 * 0.20 * 20_000 = 22_000ms
        assertEquals(22_000L, controller.remainingCooldownMs(now))

        // Successes during active cooldown MUST NOT raise concurrency
        repeat(6) { controller.recordOutcome(PollAttemptOutcome.Success(150L)) }
        assertEquals(2, controller.currentConcurrency)

        // Advance past cooldown -> healthy window now climbs safely 2 -> 3
        now += 23_000L
        assertFalse(controller.isInCooldown(now))
        repeat(2) { controller.recordOutcome(PollAttemptOutcome.Success(150L)) }
        assertEquals(3, controller.currentConcurrency)
    }

    @Test
    fun `repeated errors do not allow concurrency creep and backoff grows exponentially with jitter`() {
        var now = 1_000_000L
        val controller = AdaptiveConcurrencyController(
            config = AdaptivePollingConfig(
                initialConcurrency = 1,
                increaseSuccessWindow = 2,
                baseRetryBackoffMs = 10_000L,
                maxRetryBackoffMs = 120_000L,
                backoffJitterFraction = 0.20
            ),
            nowMillis = { now },
            nextRandomDouble = { 1.0 } // +20% upper jitter bound
        )

        repeat(6) { controller.recordOutcome(PollAttemptOutcome.Success(180L)) }
        assertEquals(4, controller.currentConcurrency)

        // HTTP 503 lowers concurrency immediately
        controller.recordOutcome(PollAttemptOutcome.ServerError(httpCode = 503, rttMs = 400L))
        assertTrue(controller.currentConcurrency <= 2)

        // Timeout further degrades cleanly down to floor 1
        controller.recordOutcome(PollAttemptOutcome.Timeout(rttMs = 8_000L))
        assertEquals(1, controller.currentConcurrency)

        // Verify exponential backoff with jitter clamping
        val delay1 = controller.computeNextPollIntervalMs(basePollIntervalMs = 15_000L, consecutiveFailures = 1)
        val delay2 = controller.computeNextPollIntervalMs(basePollIntervalMs = 15_000L, consecutiveFailures = 2)
        val delay3 = controller.computeNextPollIntervalMs(basePollIntervalMs = 15_000L, consecutiveFailures = 5)
        assertTrue(delay2 > delay1)
        assertTrue(delay3 <= 120_000L)

        // Retry-After overrides exponential interval and enforces jitter
        val retryAfterDelay = controller.computeNextPollIntervalMs(
            basePollIntervalMs = 15_000L,
            consecutiveFailures = 1,
            retryAfterSeconds = 45L
        )
        assertEquals(54_000L, retryAfterDelay) // 45s * 1.20
    }
}
