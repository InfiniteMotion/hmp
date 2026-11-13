package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.TimeProvider
import com.hmp.test.fakes.FakeSystemConditions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentSchedulerTest {

    private fun timeProvider(): TimeProvider = { 1_700_000_000_000L }
    private fun reg(p: AgentPriority, id: String) = AgentRegistration(agentId = id, priority = p)

    @Test
    fun `Master 永远 RUNNING`() = runTest {
        val s = AgentScheduler(timeProvider(), FakeSystemConditions())
        assertEquals(AgentRunState.RUNNING, s.registerAgent(reg(AgentPriority.MASTER, "m")))
    }

    @Test
    fun `Hello 永远 RUNNING`() = runTest {
        val s = AgentScheduler(timeProvider(), FakeSystemConditions())
        assertEquals(AgentRunState.RUNNING, s.registerAgent(reg(AgentPriority.HELLO, "h")))
    }

    @Test
    fun `Radio 电量或 WiFi 满足才 RUNNING`() = runTest {
        // WiFi 连接 → RUNNING（即便电量低）
        val s1 = AgentScheduler(timeProvider(), FakeSystemConditions(batteryLevelValue = 0.1f, wifiConnected = true))
        assertEquals(AgentRunState.RUNNING, s1.registerAgent(reg(AgentPriority.RADIO, "r")))

        // 无 WiFi 且电量 < 20% → PAUSED
        val s2 = AgentScheduler(timeProvider(), FakeSystemConditions(batteryLevelValue = 0.1f, wifiConnected = false))
        assertEquals(AgentRunState.PAUSED, s2.registerAgent(reg(AgentPriority.RADIO, "r")))

        // 无 WiFi 但电量 ≥ 20% → RUNNING
        val s3 = AgentScheduler(timeProvider(), FakeSystemConditions(batteryLevelValue = 0.3f, wifiConnected = false))
        assertEquals(AgentRunState.RUNNING, s3.registerAgent(reg(AgentPriority.RADIO, "r")))
    }

    @Test
    fun `Enrich 三者全满足才 RUNNING`() = runTest {
        val ok = AgentScheduler(timeProvider(), FakeSystemConditions(batteryLevelValue = 0.6f, wifiConnected = true))
        assertEquals(AgentRunState.RUNNING, ok.registerAgent(reg(AgentPriority.ENRICH, "e")))

        // 电量 < 50% → PAUSED
        val lowBattery = AgentScheduler(timeProvider(), FakeSystemConditions(batteryLevelValue = 0.4f, wifiConnected = true))
        assertEquals(AgentRunState.PAUSED, lowBattery.registerAgent(reg(AgentPriority.ENRICH, "e")))

        // 无 WiFi → PAUSED
        val noWifi = AgentScheduler(timeProvider(), FakeSystemConditions(batteryLevelValue = 0.6f, wifiConnected = false))
        assertEquals(AgentRunState.PAUSED, noWifi.registerAgent(reg(AgentPriority.ENRICH, "e")))

        // 配额不再参与调度：Enrich 在电量/WiFi 都满足时就是 RUNNING（桶耗尽由停止信号让它优雅退出）
        val fullBattery = AgentScheduler(timeProvider(), FakeSystemConditions(batteryLevelValue = 0.9f, wifiConnected = true))
        assertEquals(AgentRunState.RUNNING, fullBattery.registerAgent(reg(AgentPriority.ENRICH, "e")))
    }

    @Test
    fun `unregister 后状态 UNREGISTERED`() = runTest {
        val s = AgentScheduler(timeProvider(), FakeSystemConditions())
        s.registerAgent(reg(AgentPriority.MASTER, "m"))
        s.unregisterAgent("m")
        assertEquals(AgentRunState.UNREGISTERED, s.getState("m"))
    }

    @Test
    fun `registeredAgentIds 返回已注册集合`() = runTest {
        val s = AgentScheduler(timeProvider(), FakeSystemConditions())
        s.registerAgent(reg(AgentPriority.MASTER, "a"))
        s.registerAgent(reg(AgentPriority.HELLO, "b"))
        assertTrue(s.registeredAgentIds().containsAll(listOf("a", "b")))
    }

    /** 阶段 0 判据 1：读写并发不能抛 CME，也不能读到半张表。 */
    @Test
    fun `并发注册注销与遍历不抛 CME`() = runTest {
        val s = AgentScheduler(timeProvider(), FakeSystemConditions())

        // 真实线程，50 个写协程同时注册/注销；读协程持续遍历同一张表。
        val writers = (1..50).map { i ->
            launch(Dispatchers.Default) {
                repeat(20) {
                    s.registerAgent(reg(AgentPriority.MASTER, "k$i"))
                    s.registeredAgentIds().forEach { requireNotNull(it) }
                    s.getState("k$i")
                    s.unregisterAgent("k$i")
                }
            }
        }
        val reader = launch(Dispatchers.Default) {
            repeat(3000) {
                s.registeredAgentIds().forEach { requireNotNull(it) }
                s.getState("k1")
            }
        }
        writers.joinAll()
        reader.join()

        assertTrue(s.registeredAgentIds().isEmpty(), "所有 key 都已移除，注册表必须回到空")
    }

    /** 系统条件在第 2 次读取时抛一次，模拟单轮仲裁异常。 */
    private class FlakyConditions : SystemConditions {
        @Volatile
        var calls = 0

        @Volatile
        var thrown = false

        override fun batteryLevel(): Float {
            calls++
            if (!thrown && calls >= 2) {
                thrown = true
                throw IllegalStateException("simulated arbitration failure")
            }
            return 0.9f
        }

        override fun isWifiConnected(): Boolean = true
    }

    /** 阶段 0 判据 2：单轮异常不该杀死整条仲裁循环。 */
    @Test
    fun `仲裁循环抛异常后仍能继续仲裁`() = runBlocking {
        val conditions = FlakyConditions()
        val s = AgentScheduler(timeProvider(), conditions)
        s.registerAgent(reg(AgentPriority.ENRICH, "e"))
        s.startArbitration()

        val reached = withTimeoutOrNull(5_000) {
            while (conditions.calls < 5) delay(50)
            true
        } ?: false
        s.stopArbitration()

        assertTrue(reached, "单轮仲裁异常不该让循环永久死亡；异常后仍应继续每秒仲裁")
    }

    @Test
    fun `注册 RUNNING Agent 触发 onResume`() = runTest {
        var resumed = false
        val s = AgentScheduler(timeProvider(), FakeSystemConditions())
        s.registerAgent(AgentRegistration(agentId = "m", priority = AgentPriority.MASTER, onResume = { resumed = true }))
        yield()
        yield()
        assertTrue(resumed, "RUNNING 注册应触发 onResume 回调")
    }
}
