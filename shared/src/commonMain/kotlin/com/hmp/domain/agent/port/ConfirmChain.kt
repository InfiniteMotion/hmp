package com.hmp.domain.agent.port

/**
 * 确认链单步（弹窗内容）。
 * STRONG_CONFIRM 至少两步：第一步 Agent 自报意图，第二步系统问权限。
 */
data class ConfirmStep(
    val title: String,
    val message: String,
    val confirmLabel: String = "确认",
    val denyLabel: String = "取消",
)

/** 用户对整条确认链的最终决策。 */
sealed interface ConfirmDecision {
    /** 全部步骤确认通过。 */
    data object Approved : ConfirmDecision
    /** 某一步主动拒绝（整条链中断 → 返回 Denied）。 */
    data object Denied : ConfirmDecision
    /** 弹窗被关闭或系统取消。 */
    data object Cancelled : ConfirmDecision
}
