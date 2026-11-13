package com.hmp.domain.agent.port

/**
 * R3 后半（2026-11-13）：把持久化的会话消息投影成下一轮的 LLM 历史。
 *
 * - `tool_summary` 单独提出来，转成一条 **system** 消息放在最前（不占对话窗口）——
 *   让模型知道上一轮已经执行过哪些工具，避免重复执行写操作。
 * - 其余只保留 user / agent(assistant)，其它 role 丢弃（与既有行为一致）。
 * - 摘要放最前而非末尾：`buildHistory()` 末尾的「与当前输入去重」逻辑依赖最后一条是 user。
 */
fun buildHistoryMessages(messages: List<StoredAgentMessage>, keep: Int): List<LlmMessage> {
    val summary = messages.lastOrNull { it.renderHint == TOOL_SUMMARY_RENDER_HINT }
        ?.content
        ?.takeIf { it.isNotBlank() }
    val dialogue = messages
        .filter { it.renderHint != TOOL_SUMMARY_RENDER_HINT }
        .takeLast(keep.coerceAtLeast(0))
        .mapNotNull { m ->
            val role = when (m.role) {
                "user" -> "user"
                "agent", "assistant" -> "assistant"
                else -> null
            } ?: return@mapNotNull null
            LlmMessage(role = role, content = m.content)
        }
    return if (summary != null) listOf(LlmMessage(role = "system", content = summary)) + dialogue else dialogue
}
