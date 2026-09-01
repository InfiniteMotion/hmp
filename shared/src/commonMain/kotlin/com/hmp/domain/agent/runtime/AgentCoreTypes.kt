package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.tool.ToolPermissionLevel

/** 终止原因（熔断为何终止，入审计）。 */
enum class TerminationReason { ANSWERED, STEP_BUDGET_EXHAUSTED, CLOUD_QUOTA_EXHAUSTED, FAILED }

/** 一次工具执行记录（供结果回填与审计）。 */
data class ToolExecutionRecord(
    val toolName: String,
    val outcome: String, // success / failed / refused / skipped
    val summary: String,
)

/** Agent 任务结果。 */
data class AgentResult(
    val text: String,
    val stepsUsed: Int,
    val toolCalls: List<ToolExecutionRecord>,
    val terminatedBy: TerminationReason,
)

/**
 * 一次待确认的工具调用（确认卡片：一次 turn 的多项确认聚合展示、逐项勾选）。
 * @param toolName 工具名（供卡片展示与审计）
 * @param argsSummary 参数摘要（供用户判断）
 * @param permissionLevel 许可级（CONFIRM / STRONG_CONFIRM）
 */
data class ConfirmRequest(
    val toolName: String,
    val argsSummary: String,
    val permissionLevel: ToolPermissionLevel,
)

/**
 * 用户对单条确认请求的决策（三种，无 Deny Always）：
 * - AllowOnce  — 本次允许执行，不写入白名单
 * - AllowAlways — 本次允许执行 + 写入此 Agent 的 alwaysAllow 白名单（以后自动跳过确认）
 * - Deny       — 本次拒绝执行（仅当次，不永久拉黑；如需永久拉黑，用户可到设置页手动配置）
 */
sealed interface ConfirmOutcome {
    data object AllowOnce : ConfirmOutcome
    data object AllowAlways : ConfirmOutcome
    data object Deny : ConfirmOutcome
}

/**
 * 确认门（确认卡片流实现；测试注入脚本化门：全通过 / 全否决 / 按工具）。
 * 批量语义：一次 turn 可能触发多项需要确认的工具，聚合为 [requests] 一次性请求，
 * 返回与传入**同序**的决策列表；Deny=该项本次跳过（拒绝纪律：本次会话不纠缠，不报错）。
 */
fun interface ConfirmGate {
    suspend fun request(requests: List<ConfirmRequest>): List<ConfirmOutcome>
}

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
    // —— 跨轮记忆：上一轮及以前的 user/assistant 文本消息（按时间正序，越新越靠后）——
    val history: List<LlmMessage> = emptyList(),
)
