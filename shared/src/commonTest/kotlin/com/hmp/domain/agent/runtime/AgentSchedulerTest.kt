package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.TimeProvider
import com.hmp.test.fakes.FakeSystemConditions
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentSchedulerTest {

    private fun timeProvider(): TimeProvider = { 1_700_000_000_000L }
    private fun counter() = GlobalTokenCounter(timeProvider(), dailyTokenQuota = 500_000)
    private fun reg(p: AgentPriority, id: String) = AgentRegistration(agentId = id, priority = p)

    @Test
    fun `Master 永远 RUNNING`() = runTest {
        val s = AgentScheduler(timeProvider(), counter(), FakeSystemConditions())
        assertEquals(AgentRunState.RUNNING, s.registerAgent(reg(AgentPriority.MASTER, "m")))
    }

    @Test
    fun `Hello 永远 RUNNING`() = runTest {
        val s = AgentScheduler(timeProvider(), counter(), FakeSystemConditions())
        assertEquals(AgentRunState.RUNNING, s.registerAgent(reg(AgentPriority.HELLO, "h")))
    }

    @Test
    fun `Radio 电量或 WiFi 满足才 RUNNING`() = runTest {
        // WiFi 连接 → RUNNING（即便电量低）
        val s1 = AgentScheduler(timeProvider(), counter(), FakeSystemConditions(batteryLevelValue = 0.1f, wifiConnected = true))
        assertEquals(AgentRunState.RUNNING, s1.registerAgent(reg(AgentPriority.RADIO, "r")))

        // 无 WiFi 且电量 < 20% → PAUSED
        val s2 = AgentScheduler(timeProvider(), counter(), FakeSystemConditions(batteryLevelValue = 0.1f, wifiConnected = false))
        assertEquals(AgentRunState.PAUSED, s2.registerAgent(reg(AgentPriority.RADIO, "r")))

        // 无 WiFi 但电量 ≥ 20% → RUNNING
        val s3 = AgentScheduler(timeProvider(), counter(), FakeSystemConditions(batteryLevelValue = 0.3f, wifiConnected = false))
        assertEquals(AgentRunState.RUNNING, s3.registerAgent(reg(AgentPriority.RADIO, "r")))
    }

    @Test
    fun `Enrich 三者全满足才 RUNNING`() = runTest {
        val ok = AgentScheduler(timeProvider(), counter(), FakeSystemConditions(batteryLevelValue = 0.6f, wifiConnected = true))
        assertEquals(AgentRunState.RUNNING, ok.registerAgent(reg(AgentPriority.ENRICH, "e")))

        // 电量 < 50% → PAUSED
        val lowBattery = AgentScheduler(timeProvider(), counter(), FakeSystemConditions(batteryLevelValue = 0.4f, wifiConnected = true))
        assertEquals(AgentRunState.PAUSED, lowBattery.registerAgent(reg(AgentPriority.ENRICH, "e")))

        // 无 WiFi → PAUSED
        val noWifi = AgentScheduler(timeProvider(), counter(), FakeSystemConditions(batteryLevelValue = 0.6f, wifiConnected = false))
        assertEquals(AgentRunState.PAUSED, noWifi.registerAgent(reg(AgentPriority.ENRICH, "e")))

        // 配额耗尽 → PAUSED
        val c2 = GlobalTokenCounter(timeProvider(), dailyTokenQuota = 1000)
        c2.recordTokens(1000)
        val quotaOut = AgentScheduler(timeProvider(), c2, FakeSystemConditions(batteryLevelValue = 0.6f, wifiConnected = true))
        assertEquals(AgentRunState.PAUSED, quotaOut.registerAgent(reg(AgentPriority.ENRICH, "e")))
    }

    @Test
    fun `unregister 后状态 UNREGISTERED`() = runTest {
        val s = AgentScheduler(timeProvider(), counter(), FakeSystemConditions())
        s.registerAgent(reg(AgentPriority.MASTER, "m"))
        s.unregisterAgent("m")
        assertEquals(AgentRunState.UNREGISTERED, s.getState("m"))
    }

    @Test
    fun `registeredAgentIds 返回已注册集合`() = runTest {
        val s = AgentScheduler(timeProvider(), counter(), FakeSystemConditions())
        s.registerAgent(reg(AgentPriority.MASTER, "a"))
        s.registerAgent(reg(AgentPriority.HELLO, "b"))
        assertTrue(s.registeredAgentIds().containsAll(listOf("a", "b")))
    }

    @Test
    fun `注册 RUNNING Agent 触发 onResume`() = runTest {
        var resumed = false
        val s = AgentScheduler(timeProvider(), counter(), FakeSystemConditions())
        s.registerAgent(AgentRegistration(agentId = "m", priority = AgentPriority.MASTER, onResume = { resumed = true }))
        yield(); yield()
        assertTrue(resumed, "RUNNING 注册应触发 onResume 回调")
    }
}
