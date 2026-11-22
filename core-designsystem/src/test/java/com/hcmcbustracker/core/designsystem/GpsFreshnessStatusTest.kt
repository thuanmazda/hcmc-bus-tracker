package com.hcmcbustracker.core.designsystem

import com.hcmcbustracker.core.designsystem.component.GpsFreshnessStatus
import com.hcmcbustracker.core.designsystem.theme.StatusDelayed
import com.hcmcbustracker.core.designsystem.theme.StatusFresh
import com.hcmcbustracker.core.designsystem.theme.StatusSlightDelay
import com.hcmcbustracker.core.designsystem.theme.StatusStale
import org.junit.Assert.assertEquals
import org.junit.Test

class GpsFreshnessStatusTest {

    @Test
    fun nullOrNegativeTimestamp_classifiedAsStale() {
        assertEquals(GpsFreshnessStatus.STALE, GpsFreshnessStatus.fromTimestamp(null))
        assertEquals(GpsFreshnessStatus.STALE, GpsFreshnessStatus.fromTimestamp(0L))
        assertEquals(GpsFreshnessStatus.STALE, GpsFreshnessStatus.fromTimestamp(-100L))
    }

    @Test
    fun underOneMinute_classifiedAsFresh() {
        val now = 1_000_000L
        val sample = now - 30_000L // 30s ago
        assertEquals(GpsFreshnessStatus.FRESH, GpsFreshnessStatus.fromTimestamp(sample, now))
        assertEquals("Vừa cập nhật", GpsFreshnessStatus.FRESH.label)
        assertEquals(StatusFresh, GpsFreshnessStatus.FRESH.color)
    }

    @Test
    fun betweenOneAndTwoMinutes_classifiedAsSlightDelay() {
        val now = 1_000_000L
        val sample = now - 90_000L // 90s ago
        assertEquals(GpsFreshnessStatus.SLIGHT_DELAY, GpsFreshnessStatus.fromTimestamp(sample, now))
        assertEquals("Trễ 1-2 phút", GpsFreshnessStatus.SLIGHT_DELAY.label)
        assertEquals(StatusSlightDelay, GpsFreshnessStatus.SLIGHT_DELAY.color)
    }

    @Test
    fun betweenTwoAndFiveMinutes_classifiedAsDelayed() {
        val now = 1_000_000L
        val sample = now - 180_000L // 3 min ago
        assertEquals(GpsFreshnessStatus.DELAYED, GpsFreshnessStatus.fromTimestamp(sample, now))
        assertEquals("Trễ > 2 phút", GpsFreshnessStatus.DELAYED.label)
        assertEquals(StatusDelayed, GpsFreshnessStatus.DELAYED.color)
    }

    @Test
    fun overFiveMinutes_classifiedAsStale() {
        val now = 1_000_000L
        val sample = now - 400_000L // > 6 min ago
        assertEquals(GpsFreshnessStatus.STALE, GpsFreshnessStatus.fromTimestamp(sample, now))
        assertEquals("Mất tín hiệu", GpsFreshnessStatus.STALE.label)
        assertEquals(StatusStale, GpsFreshnessStatus.STALE.color)
    }
}
