package com.hmp.domain.agent.infra

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PresenceBus（M4-T5）行为测试：伙伴唯一嗓音的广播通道。
 *
 * 契约：
 * - emit → 活跃订阅者按序全量收到
 * - 多消费点广播（侧条 / DJ 线各自独立收到）
 * - SharedFlow 无 replay：订阅前发射的事件不补发
 * - 五类事件的数据形状（data class 结构相等 + data object 单例）
 */
class PresenceBusTest {

    @Test
    fun emit_deliversToActiveSubscriberInOrder() = runTest {
        val bus = PresenceBus()
        val received = mutableListOf<PresenceEvent>()
        // UNDISPATCHED：订阅在首条 emit 之前生效（无 replay 的 SharedFlow 必须先订后发）
        // backgroundScope：随 runTest 结束自动取消，常驻收集不会拖出 UncompletedCoroutinesError
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect { received += it }
        }

        bus.emit(PresenceEvent.TaskProgress(phase = "thinking", active = true))
        bus.emit(PresenceEvent.CloudQuotaExhausted)
        bus.emit(PresenceEvent.DjBlank)
        bus.emit(PresenceEvent.SkipDetected(consecutiveCount = 3, trackTitle = "夜航"))
        bus.emit(PresenceEvent.AgentProgress(agentId = "enrich", processed = 5, total = 10))
        runCurrent()

        assertEquals(5, received.size, "五个事件按序全量送达")
        assertEquals(PresenceEvent.TaskProgress("thinking", true), received[0])
        assertEquals(PresenceEvent.CloudQuotaExhausted, received[1])
        assertEquals(PresenceEvent.DjBlank, received[2])
        assertEquals(PresenceEvent.SkipDetected(3, "夜航"), received[3])
        assertEquals(PresenceEvent.AgentProgress("enrich", 5, 10), received[4])
    }

    @Test
    fun emit_broadcastsToAllSubscribers() = runTest {
        val bus = PresenceBus()
        val sideBar = mutableListOf<PresenceEvent>()
        val djLine = mutableListOf<PresenceEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { bus.events.collect { sideBar += it } }
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { bus.events.collect { djLine += it } }

        bus.emit(PresenceEvent.DjBlank)
        bus.emit(PresenceEvent.AgentProgress("radio", 1, 4))
        runCurrent()

        assertEquals(2, sideBar.size, "消费点 A 独立收到广播")
        assertEquals(2, djLine.size, "消费点 B 独立收到广播")
    }

    @Test
    fun emit_beforeSubscription_isNotReplayed() = runTest {
        val bus = PresenceBus()
        bus.emit(PresenceEvent.DjBlank)   // 无订阅者 → 事件即发即弃

        val received = mutableListOf<PresenceEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect { received += it }
        }
        runCurrent()

        assertTrue(received.isEmpty(), "无 replay 语义：订阅前的历史事件不补发")
    }

    @Test
    fun dataObjects_areCanonicalSingletons() {
        // data object 的相等语义：任何拿到的实例都是同一个
        assertEquals(PresenceEvent.CloudQuotaExhausted, PresenceEvent.CloudQuotaExhausted)
        assertEquals(PresenceEvent.DjBlank, PresenceEvent.DjBlank)
    }
}
