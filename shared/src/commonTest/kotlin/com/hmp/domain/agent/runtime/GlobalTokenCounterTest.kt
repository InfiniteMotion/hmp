package com.hmp.domain.agent.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobalTokenCounterTest {

    @Test
    fun `recordTokens 累计用量与剩余`() {
        val c = GlobalTokenCounter(timeProvider = { 1L }, dailyTokenQuota = 1000)
        c.recordTokens(100)
        assertEquals(100L, c.usedToday())
        assertEquals(900L, c.remainingToday())
        assertEquals(100L, c.snapshot.value.used)
    }

    @Test
    fun `shouldStop 按阈值熔断`() {
        val c = GlobalTokenCounter(timeProvider = { 1L }, dailyTokenQuota = 1000)
        c.recordTokens(900) // 0.9 不 > 0.9
        assertFalse(c.shouldStop())
        c.recordTokens(60) // 960/1000 = 0.96 > 0.95
        assertTrue(c.shouldStop())
    }

    @Test
    fun `跨日自动归零`() {
        var day = 1L
        val c = GlobalTokenCounter(timeProvider = { day * 86_400_000L }, dailyTokenQuota = 1000)
        c.recordTokens(500)
        assertEquals(500L, c.usedToday())
        day = 2L
        assertEquals(0L, c.usedToday())
    }

    @Test
    fun `snapshot StateFlow 随记录刷新`() {
        val c = GlobalTokenCounter(timeProvider = { 1L }, dailyTokenQuota = 1000)
        c.recordTokens(250)
        assertEquals(250L, c.snapshot.value.used)
        assertEquals(0.25f, c.snapshot.value.rate)
    }
}
