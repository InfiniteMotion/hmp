package com.hmp.domain.agent.policy

import com.hmp.domain.agent.config.EngineDefaults
import com.hmp.domain.agent.config.ResolvedAgentConfig
import com.hmp.domain.agent.config.RuntimeParams
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.ConfirmGate
import com.hmp.domain.agent.port.ToolPermissionLevel

/**
 * Agent 身份角色——编译期常量，三档角色对应硬编码的 maxLevel（不可覆盖）。
 *
 * MASTER → maxLevel=3 (STRONG_CONFIRM，所有级别可碰)
 * RADIO  → maxLevel=1 (NOTIFY，后台静默，不碰 CONFIRM+)
 * ENRICH → maxLevel=1 (同上)
 * HELLO  → maxLevel=1 (同上，门面呈现)
 */
enum class AgentRole { MASTER, RADIO, ENRICH, HELLO }

/** Agent 身份门硬编码：role → 允许访问的最高 ToolPermissionLevel ordinal */
private val AGENT_MAX_LEVEL: Map<AgentRole, Int> = mapOf(
    AgentRole.MASTER to ToolPermissionLevel.entries.last().ordinal,  // STRONG_CONFIRM = 3
    AgentRole.RADIO  to ToolPermissionLevel.NOTIFY.ordinal,           // 1
    AgentRole.ENRICH to ToolPermissionLevel.NOTIFY.ordinal,           // 1
    AgentRole.HELLO  to ToolPermissionLevel.NOTIFY.ordinal,           // 1
)

/**
 * per-Agent 独立的可持久化配置——用户参与配置管理的载体。
 *
 * v0 只有 trustLevel + alwaysAllow（2 字段，权限相关）。
 * v1 扩展为四组字段：
 *   ① 权限组（原字段）：trustLevel / alwaysAllow — PolicyGuard 消费
 *   ② 行为组（新增）：temperature / runtimeParams — LLM 调用参数
 *   ③ Prompt 组（新增）：promptOverrides — 覆盖默认 system prompt
 *   ④ 语言组（新增）：preferredLang — 每个 Agent 独立的 prompt/回复语言
 *   ⑤ 启用开关（新增）：enabled — 是否启用该 Agent
 *
 * DataStore 存储策略：每个字段一个 key（key-value 模式）。新字段的 key 首次不存在时
 * 读回来是 null，resolvedFor() 自然回落 EngineDefaults 默认值 —— **老用户零迁移**。
 *
 * 没有 allowFromOverride——Agent 身份门是硬编码安全红线，不暴露给用户。
 */
data class AgentPolicyConfig(
    // ── ① 权限组（原字段，不变）──
    var trustLevel: Int = TrustLevel.SUGGEST,
    val alwaysAllow: MutableSet<String> = mutableSetOf(),
    // ── ② 行为组（新增）──
    /** LLM 采样温度（null = 回落 EngineDefaults.defaultTemperatureFor(role)）。 */
    var temperature: Float? = null,
    /** 每个 Agent 专属数值参数（targetCount / targetCoverage / stepBudget …）。
     *  用 Map<String, String> 统一承载，DataStore 存 JSON 字符串。
     *  解析时由 RuntimeParams.resolve(role, this) 回落默认。 */
    val runtimeParams: MutableMap<String, String> = mutableMapOf(),
    // ── ③ Prompt 组（新增）──
    /** system prompt 覆盖。key 约定：chat.system / enrich.system / radio.host / hello.greeting.* */
    val promptOverrides: MutableMap<String, String> = mutableMapOf(),
    // ── ④ 语言组（新增）──
    /** 每个 Agent 独立的 prompt/回复语言偏好。
     *  "global" = 跟随 GlobalAgentConfig.replyLanguage；"zh"/"en"/"auto" = 各自生效。 */
    var preferredLang: String = "global",
    // ── ⑤ 启用开关（新增）──
    /** 是否启用该 Agent。关闭后 MasterAgent 在派发时会跳过此 Agent（不调用、不调度）。 */
    var enabled: Boolean = true,
) {
    /** 序列化 snapshot（持久化时调用）。 */
    fun snapshot() = AgentPolicyConfig(
        trustLevel = trustLevel,
        alwaysAllow = alwaysAllow.toMutableSet(),
        temperature = temperature,
        runtimeParams = runtimeParams.toMutableMap(),
        promptOverrides = promptOverrides.toMutableMap(),
        preferredLang = preferredLang,
        enabled = enabled,
    )

    /**
     * 出厂回落版本：把所有 null/空值替换成代码默认。
     * role 字符串取值："master" / "hello" / "enrich" / "radio"
     *
     * 注意：preferredLang="global" 不在此展开——留给 `runtime/i18n` 的 resolvePrompt()
     * 根据 GlobalAgentConfig 或系统语言统一解析，避免 resolvedFor 跨模块依赖。
     */
    fun resolvedFor(role: String): ResolvedAgentConfig {
        return ResolvedAgentConfig(
            role = role,
            trustLevel = trustLevel,
            alwaysAllow = alwaysAllow.toSet(),
            temperature = temperature ?: EngineDefaults.defaultTemperatureFor(role),
            runtimeParams = RuntimeParams.resolve(role, runtimeParams),
            promptOverrides = promptOverrides.toMap(),
            preferredLang = preferredLang,  // 原样传递，"global" 留给 resolvePrompt 展开
        )
    }
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

        fun hello(
            config: AgentPolicyConfig = AgentPolicyConfig(),
            trustLedger: TrustLedger? = null,
        ) = AgentPolicy(AgentRole.HELLO, config, trustLedger, confirmGate = null)
    }
}
