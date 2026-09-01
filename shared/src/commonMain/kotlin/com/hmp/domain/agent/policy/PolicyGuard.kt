package com.hmp.domain.agent.policy

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
    /** 需要用户确认（确认流弹窗）。 */
    data object RequireConfirm : PermissionDecision
    /** 系统/策略层面主动拒绝（Agent 身份门），语义区别于 RequireConfirm（等用户决策）。 */
    data object Deny : PermissionDecision
}

/**
 * M4-T2 许可护栏——**三阶段裁决**（公式化，全 Int 比较）：
 *
 * Phase 0（最高优先级：用户主动决策的白名单）
 *   AgentPolicy.isAlwaysAllow(toolName) 命中 → AllowSilent
 *
 * Phase 1（Agent 身份门——硬编码安全红线，不可覆盖）
 *   level.ordinal > AGENT_MAX_LEVEL[role] → Deny
 *
 * Phase 2（工具风险 + per-Agent trustLevel）
 *   SILENT → AllowSilent
 *   NOTIFY → AllowWithNotify
 *   CONFIRM → trustLevel >= 1(ACT) → AllowWithNotify；trustLevel=0(SUGGEST) → RequireConfirm
 *   STRONG_CONFIRM → 始终 RequireConfirm（硬规则，不随信任松绑）
 */
class PolicyGuard(
    private val auditLog: AuditLogPort,
) {
    suspend fun decide(
        agentPolicy: AgentPolicy,
        toolName: String,
        level: ToolPermissionLevel,
        taskId: Long?,
    ): PermissionDecision {
        // ═══ Phase 0: 用户主动设的 alwaysAllow（最高优先级）═══
        if (agentPolicy.isAlwaysAllow(toolName)) {
            auditLog.record(AuditEntry(
                tool = toolName, outcome = "allowed_silent",
                reason = "Phase0-alwaysAllow: role=${agentPolicy.role}", taskId = taskId,
            ))
            return PermissionDecision.AllowSilent
        }

        // ═══ Phase 1: Agent 身份门（硬编码安全红线）═══
        if (!agentPolicy.canAccess(level)) {
            val reason = "Phase1-deny: role=${agentPolicy.role} maxLevel=${agentPolicy.maxLevel} toolLevel=${level.ordinal}"
            auditLog.record(AuditEntry(
                tool = toolName, outcome = "denied",
                reason = reason, taskId = taskId,
            ))
            return PermissionDecision.Deny
        }

        // ═══ Phase 2: 工具风险 + per-Agent trustLevel ═══
        val trust = agentPolicy.config.trustLevel
        val decision = when (level) {
            ToolPermissionLevel.SILENT -> PermissionDecision.AllowSilent
            ToolPermissionLevel.NOTIFY -> PermissionDecision.AllowWithNotify
            ToolPermissionLevel.CONFIRM -> if (trust >= TrustLevel.ACT) PermissionDecision.AllowWithNotify else PermissionDecision.RequireConfirm
            ToolPermissionLevel.STRONG_CONFIRM -> PermissionDecision.RequireConfirm
        }

        val outcome = when (decision) {
            PermissionDecision.AllowSilent -> "allowed_silent"
            PermissionDecision.AllowWithNotify -> "allowed_notify"
            PermissionDecision.RequireConfirm -> "pending_confirm"
            PermissionDecision.Deny -> "denied"
        }
        auditLog.record(
            AuditEntry(
                tool = toolName,
                outcome = outcome,
                reason = "Phase2: level=${level.ordinal} role=${agentPolicy.role} trust=$trust → ${decision::class.simpleName}",
                taskId = taskId,
            )
        )
        return decision
    }
}
