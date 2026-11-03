package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.runtime.sub.shared.SubAgent
import com.hmp.domain.agent.tool.spec.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D6-04 判据：注册表在多线程并发写/读下不得结构损坏。
 *
 * 旧实现是裸 `MutableMap`：三个 `start*` 各持自己的 lifecycle mutex，却写同一张表；
 * 读侧还在任意线程直接读。这里用 50 个协程 × 20 轮 put/remove + 每轮快照读来压它 ——
 * 退化回 MutableMap 时典型表现是 `ConcurrentModificationException` 或读到半张表。
 */
class SubAgentRegistryTest {

    private fun agent(id: String): SubAgent = object : SubAgent(
        agentId = id,
        contextBudget = AgentContextBudget(agentId = id, llmClient = null),
        toolRegistryView = ToolRegistryView(ToolRegistry(emptyList()), emptySet()),
    ) {
        override suspend fun runLoop() = Unit
    }

    @Test
    fun concurrentPutsAndRemoves_neverCorruptTheSnapshot() = runTest {
        val registry = SubAgentRegistry()

        val writers = (1..50).map { i ->
            launch(Dispatchers.Default) {
                repeat(20) {
                    registry.put("k$i", agent("k$i"))
                    // 读侧并发看：快照里的每个值都必须非空（半张表会在这里露出来）
                    registry.entries.values.forEach { requireNotNull(it) }
                    registry.remove("k$i")
                }
            }
        }
        writers.joinAll()

        assertTrue(registry.entries.isEmpty(), "所有 key 都已移除，注册表必须回到空")
    }

    @Test
    fun clearNow_hidesEverythingImmediately() = runTest {
        val registry = SubAgentRegistry()
        registry.put("radio", agent("radio"))
        registry.put("hello", agent("hello"))

        registry.clearNow()

        assertEquals(0, registry.entries.size)
        assertEquals(null, registry["radio"])
    }

    @Test
    fun putAndRemove_areSerialized_lastWriterWins() = runTest {
        val registry = SubAgentRegistry()
        val first = agent("first")
        val second = agent("second")

        registry.put("radio", first)
        registry.put("radio", second)

        assertTrue(registry["radio"] === second, "同 key 后写覆盖前写")
        assertTrue(registry.remove("radio") === second)
        assertEquals(null, registry.remove("radio"), "重复 remove 返回 null，不抛")
    }
}
