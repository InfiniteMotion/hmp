package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.LlmMessage

/** 终止原因（入审计）。 */
enum class TerminationReason { ANSWERED, CLOUD_QUOTA_EXHAUSTED, CONFIRM_TIMEOUT, FAILED }

/** 一次工具执行记录（供结果回填与审计）。 */
data class ToolExecutionRecord(
    val toolName: String,
    val outcome: String, // success / failed / refused / skipped
    val summary: String,
    /**
     * 结构化回传（可解析 JSON 文本）——工具把机器可读结果放在这里，`summary` 保持人读。
     * 例：播放队列替换后回传实际入队的曲目清单。无结构化结果时为 null。
     */
    val detail: String? = null,
)

/** Agent 任务结果。 */
data class AgentResult(
    val text: String,
    val stepsUsed: Int,
    val toolCalls: List<ToolExecutionRecord>,
    val terminatedBy: TerminationReason,
)

// 注：确认门三件套（ConfirmGate / ConfirmRequest / ConfirmOutcome）
// 已下沉到 `port/ConfirmGate.kt` —— 它们是跨层契约，不属于引擎目录。

/** 一次运行的外部上下文输入（供组装 LLM system prompt + 首轮注入）。 */
data class RunContextInput(
    val taskState: String? = null,
    val libraryListText: String? = null,
    val libraryOverviewText: String? = null,
    // —— 首轮注入（第一次对话就该到 agent 的内容）——
    val personaText: String? = null,
    val recognitionText: String? = null,
    val timeOfDayText: String? = null,
    val nowPlayingText: String? = null,
    val userTitle: String? = null,
    /**
     * 用户认识模块渲染出的画像块（自带「仅供参考」标题，见 `agent-profile.md` §7.1）。
     *
     * ⚠️ 它进的是 **system prompt**，因此**整场任务的每一轮都可见** ——
     * 这正是 C2 从"会话内不读画像"改为"读了但不作为指令"的原因（契约 §8）。
     */
    val userProfileText: String? = null,
    // —— 跨轮记忆：上一轮及以前的 user/assistant 文本消息（按时间正序，越新越靠后）——
    val history: List<LlmMessage> = emptyList(),
)
