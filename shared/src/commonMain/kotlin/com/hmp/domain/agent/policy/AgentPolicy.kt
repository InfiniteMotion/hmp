package com.hmp.domain.agent.policy

import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.runtime.ConfirmGate
import com.hmp.domain.agent.tool.ToolPermissionLevel

/**
 * Agent 身份角色——编译期常量，三档角色对应硬编码的 maxLevel（不可覆盖）。
 *
 * MASTER → maxLevel=3 (STRONG_CONFIRM，所有级别可碰)
 * RADIO  → maxLevel=1 (NOTIFY，后台静默，不碰 CONFIRM+)
 * ENRICH → maxLevel=1 (同上)
 */
enum class AgentRole { MASTER, RADIO, ENRICH }

/** Agent 身份门硬编码：role → 允许访问的最高 ToolPermissionLevel ordinal */
private val AGENT_MAX_LEVEL: Map<AgentRole, Int> = mapOf(
    AgentRole.MASTER to ToolPermissionLevel.entries.last().ordinal,  // STRONG_CONFIRM = 3
    AgentRole.RADIO  to ToolPermissionLevel.NOTIFY.ordinal,           // 1
    AgentRole.ENRICH to ToolPermissionLevel.NOTIFY.ordinal,           // 1
)

/**
 * per-Agent 独立的可持久化策略配置——用户参与权限管理的载体。
 *
 * 只有两个动态字段，全是用户可设置/可自动变化的：
 *   trustLevel  — 信任等级 (0=谨慎, 1=代劳, 2=静默)，设置页手动切 / TrustLedger 自动升降档
 *   alwaysAllow — 工具级白名单（ConfirmGate "Allow Always" 写入；Phase0 最高优先级）
 *
 * 没有 allowFromOverride——Agent 身份门是硬编码安全红线，不暴露给用户。
 */
data class AgentPolicyConfig(
    var trustLevel: Int = TrustLevel.SUGGEST,
    val alwaysAllow: MutableSet<String> = mutableSetOf(),
) {
    /** 序列化 snapshot（持久化时调用，存 immutableSetOf） */
    fun snapshot() = AgentPolicyConfig(
        trustLevel = trustLevel,
        alwaysAllow = alwaysAllow.toMutableSet(),
    )
}

/**
 * 每个 Agent 的完整权限包——PolicyGuard.decide() 的输入。
 *
 * trustLedger 由外部创建（MasterAgent/未来 RadioAgent），驱动 config.trustLevel 自动升降档；
 * ConfirmGate 仅 MASTER 角色配，后台 Agent 不配。
 */
data class AgentPolicy(
    val role: AgentRole,
    val config: AgentPolicyConfig,
    val trustLedger: TrustLedger? = null,  // 驱动 config.trustLevel 自动升降档；run() 过程中动态回调
    val confirmGate: ConfirmGate? = null,
) {
    /** 此 Agent 允许访问的最高 ToolPermissionLevel ordinal（Phase 1 Agent 身份门） */
    val maxLevel: Int get() = AGENT_MAX_LEVEL[role]!!

    /** Phase 1：此 Agent 是否有权访问指定级别的工具 */
    fun canAccess(level: ToolPermissionLevel): Boolean = level.ordinal <= maxLevel

    /** Phase 0：工具是否在永远允许白名单里（最高优先级） */
    fun isAlwaysAllow(toolName: String): Boolean = toolName in config.alwaysAllow

    companion object {
        fun master(
            config: AgentPolicyConfig = AgentPolicyConfig(),
            trustLedger: TrustLedger? = null,
            confirmGate: ConfirmGate? = null,
        ) = AgentPolicy(AgentRole.MASTER, config, trustLedger, confirmGate)

        fun radio(
            config: AgentPolicyConfig = AgentPolicyConfig(),
            trustLedger: TrustLedger? = null,
        ) = AgentPolicy(AgentRole.RADIO, config, trustLedger, confirmGate = null)

        fun enrich(
            config: AgentPolicyConfig = AgentPolicyConfig(),
            trustLedger: TrustLedger? = null,
        ) = AgentPolicy(AgentRole.ENRICH, config, trustLedger, confirmGate = null)
    }
}
