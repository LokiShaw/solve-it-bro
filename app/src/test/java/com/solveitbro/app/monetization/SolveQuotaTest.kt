package com.solveitbro.app.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolveQuotaTest {
    private class MemoryStore : QuotaStore {
        override var dayKey = Long.MIN_VALUE
        override var used = 0
    }

    private var day = 100L
    private var subscribed = false
    private val quota = SolveQuota(MemoryStore(), { subscribed }, freeDailyLimit = 3, today = { day })

    @Test
    fun `free tier allows the daily limit then blocks`() {
        repeat(3) { assertTrue(quota.tryConsume()) }
        assertFalse(quota.tryConsume())
        assertEquals(SolveQuota.Status.Remaining(0), quota.status())
    }

    @Test
    fun `limit resets on a new day`() {
        repeat(3) { quota.tryConsume() }
        day += 1
        assertEquals(SolveQuota.Status.Remaining(3), quota.status())
        assertTrue(quota.tryConsume())
    }

    @Test
    fun `rewarded bonus gives back a solve`() {
        repeat(3) { quota.tryConsume() }
        quota.grantBonus()
        assertTrue(quota.tryConsume())
        assertFalse(quota.tryConsume())
    }

    @Test
    fun `subscribers are unlimited`() {
        subscribed = true
        repeat(10) { assertTrue(quota.tryConsume()) }
        assertEquals(SolveQuota.Status.Unlimited, quota.status())
    }
}
