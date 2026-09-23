package com.hmp.domain.agent.port

/**
 * 确认门契约 —— 引擎与 UI 确认卡片流之间的接口（F9）。
 *
 * **为什么在 port**：确认门是**跨层契约**，被 `policy/`（TrustLedger 判定后请求确认）、
 * `runtime/`（ToolCallExecutor 执行前挂起）、`shared-ui`（对话框实现）三方引用。
 * 若留在 `runtime/`，会形成 `policy → runtime` 反向依赖（策略层不该依赖引擎目录）。
 *
 * 本文件只放确认相关的三个类型，它们是配套的一套协议：
 * [ConfirmGate]（门）· [ConfirmRequest]（请求）· [ConfirmOutcome]（决策）。
 */

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
