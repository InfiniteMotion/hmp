package com.hearablemusic.player.ui.common.dialogs.controller
import com.hearablemusic.player.ui.generated.resources.Res
import org.jetbrains.compose.resources.getString
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.confirm_agree
import com.hearablemusic.player.ui.generated.resources.confirm_allow
import com.hearablemusic.player.ui.generated.resources.confirm_deny
import com.hearablemusic.player.ui.generated.resources.confirm_do
import com.hearablemusic.player.ui.generated.resources.confirm_message
import com.hearablemusic.player.ui.generated.resources.confirm_message_sensitive
import com.hearablemusic.player.ui.generated.resources.confirm_title_normal
import com.hearablemusic.player.ui.generated.resources.confirm_title_sensitive
import com.hearablemusic.player.ui.generated.resources.confirm_title_strong

import com.hmp.domain.agent.port.ConfirmDecision
import com.hmp.domain.agent.port.ConfirmStep
import com.hmp.domain.agent.port.ConfirmGate
import com.hmp.domain.agent.port.ConfirmOutcome
import com.hmp.domain.agent.port.ConfirmRequest
import com.hmp.domain.agent.port.ToolPermissionLevel

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

    private suspend fun buildStepsFor(req: ConfirmRequest): List<ConfirmStep> = when (req.permissionLevel) {
        ToolPermissionLevel.STRONG_CONFIRM -> listOf(
            ConfirmStep(
                title = getString(Res.string.confirm_title_strong),
                message = getString(Res.string.confirm_message, req.toolName, req.argsSummary),
                confirmLabel = getString(Res.string.confirm_agree),
                denyLabel = getString(Res.string.cancel),
            ),
            ConfirmStep(
                title = getString(Res.string.confirm_title_sensitive),
                message = getString(Res.string.confirm_message_sensitive),
                confirmLabel = getString(Res.string.confirm_do),
                denyLabel = getString(Res.string.cancel),
            ),
        )
        else -> listOf(
            ConfirmStep(
                title = getString(Res.string.confirm_title_normal),
                message = getString(Res.string.confirm_message, req.toolName, req.argsSummary),
                confirmLabel = getString(Res.string.confirm_allow),
                denyLabel = getString(Res.string.confirm_deny),
            )
        )
    }
}
