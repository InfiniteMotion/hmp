package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.CapabilitySnapshot
import com.hmp.domain.agent.port.CapabilityState
import com.hmp.domain.agent.port.toSnapshot
import com.hmp.domain.agent.tool.spec.ToolArgs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * C 类收口（2026-11-13）：对外只交 `CapabilitySnapshot`，不交 `Capability` / `SubAgent` 实例。
 *
 * 「UI 拿不到可写入口」由类型系统保证（快照没有任何方法）；这里固定住两件事：
 * 内部状态到快照的映射不丢信息、也不带 `raw`；工具层读的是快照字段而不是活实例。
 */
class CapabilityStatusToolTest {

    private val snapshots = mapOf(
        "radio" to CapabilitySnapshot("radio", CapabilityState.Status.RUNNING, "正在播放，共 8 首"),
        "enrich" to CapabilitySnapshot("enrich", CapabilityState.Status.PAUSED, "电量不足"),
    )

    @Test
    fun `状态映射保留 status 与 detail，丢弃 raw`() {
        val state = CapabilityState(CapabilityState.Status.ERROR, "boom", raw = Any())

        val snapshot = state.toSnapshot("enrich")

        assertEquals("enrich", snapshot.name)
        assertEquals(CapabilityState.Status.ERROR, snapshot.status)
        assertEquals("boom", snapshot.detail)
    }

    @Test
    fun `不传 name 返回全部能力摘要`() = runTest {
        val result = CapabilityStatusTool { snapshots }.run(ToolArgs(emptyMap()))

        assertTrue(result.success)
        assertTrue(result.summary.contains("radio: running"))
        assertTrue(result.summary.contains("enrich: paused"))
        assertTrue(result.summary.contains("电量不足"))
    }

    @Test
    fun `传 name 只返回该能力`() = runTest {
        val result = CapabilityStatusTool { snapshots }
            .run(ToolArgs(mapOf("name" to JsonPrimitive("radio"))))

        assertTrue(result.success)
        assertTrue(result.summary.contains("radio"))
        assertFalse(result.summary.contains("enrich"))
    }

    @Test
    fun `未知 name 返回失败并列出可用能力`() = runTest {
        val result = CapabilityStatusTool { snapshots }
            .run(ToolArgs(mapOf("name" to JsonPrimitive("hello"))))

        assertFalse(result.success)
        assertTrue(result.summary.contains("未找到能力"))
        assertTrue(result.summary.contains("radio"))
    }

    @Test
    fun `没有能力时返回空态文案`() = runTest {
        val result = CapabilityStatusTool { emptyMap() }.run(ToolArgs(emptyMap()))

        assertTrue(result.success)
        assertTrue(result.summary.contains("没有运行中的后台能力"))
    }
}
