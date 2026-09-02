package com.hearablemusic.player.ui.common.dialogs.controller

import com.hmp.domain.agent.port.ConfirmDecision
import com.hmp.domain.agent.port.ConfirmStep
import com.hmp.domain.agent.runtime.ConfirmGate
import com.hmp.domain.agent.runtime.ConfirmOutcome
import com.hmp.domain.agent.runtime.ConfirmRequest
import com.hmp.domain.agent.tool.ToolPermissionLevel

/**
 * 把 UI 层 DialogManager 适配到 shared 层 ConfirmGate——
 * 让 Agent 引擎的 PolicyGuard → confirmGate.request() 链路在 UI 上弹出 AlertDialog。
 *
 * - CONFIRM / NOTIFY → 1 步单确认
 * - STRONG_CONFIRM   → 2 步双确认链（第一步 Agent 自报意图，第二步系统权限确认）
 *
 * 每个 ConfirmRequest 独立弹链（当前实现：逐项串行弹；后续可批量聚合）。
 */
class DialogConfirmGateAdapter(
    private val dialogManager: DialogManager,
) : ConfirmGate {

    override suspend fun request(requests: List<ConfirmRequest>): List<ConfirmOutcome> {
        return requests.map { req ->
            val steps = buildStepsFor(req)
            when (val decision = dialogManager.requestConfirm(steps)) {
                ConfirmDecision.Approved -> ConfirmOutcome.AllowOnce
                ConfirmDecision.Denied -> ConfirmOutcome.Deny
                ConfirmDecision.Cancelled -> ConfirmOutcome.Deny  // dismiss = 视同拒绝
            }
        }
    }

    private fun buildStepsFor(req: ConfirmRequest): List<ConfirmStep> = when (req.permissionLevel) {
        ToolPermissionLevel.STRONG_CONFIRM -> listOf(
            ConfirmStep(
                title = "AI 想做什么？",
                message = "Agent 想执行「${req.toolName}」：${req.argsSummary}",
                confirmLabel = "我同意",
                denyLabel = "取消",
            ),
            ConfirmStep(
                title = "⚠️ 敏感操作确认",
                message = "这是高风险操作。确认执行吗？",
                confirmLabel = "确认执行",
                denyLabel = "取消",
            ),
        )
        else -> listOf(
            ConfirmStep(
                title = "AI 请求执行操作",
                message = "Agent 想执行「${req.toolName}」：${req.argsSummary}",
                confirmLabel = "允许",
                denyLabel = "拒绝",
            )
        )
    }
}
