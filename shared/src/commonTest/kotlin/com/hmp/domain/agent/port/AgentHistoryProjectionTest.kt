package com.hmp.domain.agent.port

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** R3 后半：摘要进 history、不占对话窗口、UI 不渲染（投影只服务模型）。 */
class AgentHistoryProjectionTest {

    private fun msg(role: String, content: String, hint: String? = null, at: Long = 0L) = StoredAgentMessage("s", role, content, hint, at)

    @Test
    fun `tool_summary 转成最前的 system 消息且不占对话窗口`() {
        val messages = (1..6).map { msg(if (it % 2 == 0) "user" else "agent", "m$it", at = it.toLong()) } +
            msg("agent", "上一轮已执行工具：playlist.add(success)", TOOL_SUMMARY_RENDER_HINT, at = 99)

        val out = buildHistoryMessages(messages, keep = 6)

        assertEquals(7, out.size, "6 条对话 + 1 条摘要，摘要不占窗口")
        assertEquals("system", out.first().role)
        assertTrue(out.first().content.orEmpty().contains("playlist.add"))
        assertEquals("assistant", out[1].role, "m1 是 agent 消息")
        assertEquals("m1", out[1].content)
    }

    @Test
    fun `没有摘要时行为不变`() {
        val out = buildHistoryMessages(listOf(msg("user", "hi"), msg("agent", "yo")), keep = 6)
        assertEquals(2, out.size)
        assertEquals("user", out[0].role)
        assertEquals("assistant", out[1].role)
    }

    @Test
    fun `非 user-agent 角色仍被丢弃`() {
        val out = buildHistoryMessages(listOf(msg("user", "hi"), msg("system", "x"), msg("tool", "y")), keep = 6)
        assertEquals(1, out.size)
        assertEquals("user", out[0].role)
    }
}
