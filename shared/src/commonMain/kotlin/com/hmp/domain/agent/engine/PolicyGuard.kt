package com.hmp.domain.agent.engine

import com.hmp.domain.agent.tool.ToolPermissionLevel
import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.AuditLogPort

/**
 * 许可裁决结果（PolicyGuard）。
 */
sealed interface PermissionDecision {
    /** 无需用户确认，可直接执行。 */
    data object AllowSilent : PermissionDecision
    /** 执行后需发通知（不阻挡用户交互）。 */
    data object AllowWithNotify : PermissionDecision
    /** 需要用户确认（确认流弹窗，M5-T4）。 */
    data object RequireConfirm : PermissionDecision
}

/**
 * M4-T2 许可护栏：工具四级许可分级 + TrustLedger 信任阶梯 → 裁决是否执行/是否需要确认。
 *
 * 四级与总纲对齐：
 * 1. SILENT → SILENT 始终允许静默；高位档位（≥ tier）降级为 SILENT 可省通知。
 * 2. NOTIFY → 允许执行，必须事后通知；高位档位（ACT+）不降级。
 * 3. CONFIRM → 需要用户确认；低位 SUGGEST 必须确认，ACT 可选提醒，SILENT 降级到 NOTIFY。
 * 4. STRONG_CONFIRM → 始终需要强确认（删歌单/改 ID3 等破坏数据动作，不随信任松绑）。
 *
 * 裁决后写入审计日志（M4 横切要求：所有裁决必须留痕）。
 */
class PolicyGuard(
    private val trustLedger: TrustLedger,
    private val auditLog: AuditLogPort,
) {
    /** 对当前工具做许可裁决，写入审计并返回决策。 */
    suspend fun decide(
        toolName: String,
        level: ToolPermissionLevel,
        taskId: Long?,
    ): PermissionDecision {
        val tier = trustLedger.tier
        val decision = when (level) {
            ToolPermissionLevel.SILENT -> PermissionDecision.AllowSilent
            ToolPermissionLevel.NOTIFY -> PermissionDecision.AllowWithNotify
            ToolPermissionLevel.CONFIRM -> when (tier) {
                TrustTier.SUGGEST -> PermissionDecision.RequireConfirm
                TrustTier.ACT -> PermissionDecision.AllowWithNotify
                TrustTier.SILENT -> PermissionDecision.AllowWithNotify
            }
            ToolPermissionLevel.STRONG_CONFIRM -> PermissionDecision.RequireConfirm
        }

        val outcome = when (decision) {
            PermissionDecision.AllowSilent -> "allowed_silent"
            PermissionDecision.AllowWithNotify -> "allowed_notify"
            PermissionDecision.RequireConfirm -> "pending_confirm"
        }
        auditLog.record(
            AuditEntry(
                tool = "policy_guard",
                outcome = outcome,
                reason = "tool=$toolName level=$level tier=${tier.name} → ${decision::class.simpleName}",
                taskId = taskId,
            )
        )
        return decision
    }
}