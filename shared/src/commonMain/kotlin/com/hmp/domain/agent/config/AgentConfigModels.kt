package com.hmp.domain.agent.config

/**
 * Agent 的**配置数据模型** —— 从 [EngineDefaults]（常量配置）中拆出，
 * 让「配置值」与「承载配置的结构」各归其位。
 *
 * 本文件只放不可变数据类，无逻辑（[RuntimeParams.resolve] 除外，它是纯粹的 Map 解析）。
 *
 * **分层位置（2026-09-20）**：与 [EngineDefaults] 同属 `config/` 叶子包。
 * 生产者是 `policy/AgentPolicyConfig.resolvedFor()`、消费者是 `runtime/`（MasterAgent 等）
 * —— 两边都要用到它，故**必须位于两者之下**；留在 `runtime/` 会造成 `policy → runtime` 反向边。
 */

/**
 * 解析后的不可变快照——SubAgent 构造时取一次。
 * 运行时需要读最新值时（trustLevel / runtimeParams 数值型），
 * 由 MasterAgent 重新调 AgentPolicyConfig.resolvedFor()。
 */
data class ResolvedAgentConfig(
    val role: String,
    val trustLevel: Int,
    val alwaysAllow: Set<String>,
    val temperature: Float,
    val runtimeParams: RuntimeParams,
    val promptOverrides: Map<String, String>,
    /** 解析后的最终语言偏好（已展开 "global" → GlobalAgentConfig.replyLanguage 或系统默认）。 */
    val preferredLang: String = "zh",
)

/**
 * 每个 Agent 的专属数值参数集。
 *
 * resolve(role, overrides) 从 AgentPolicyConfig.runtimeParams Map 解析，
 * 缺失的 key 回落 [EngineDefaults] 的硬编码默认值。
 * 这样 runtimeParams Map 只存用户覆盖的值（diff），Map 为空 = 全部默认。
 */
data class RuntimeParams(
    // Master
    val stepBudget: Int = 8,
    // Hello
    val dailyRecommendCount: Int = 1,
    val recommendListSize: Int = 8,
    // Enrich
    val targetCoverage: Float = 0.9f,
    // Radio
    val targetCount: Int = 12,
    val autoRenew: Boolean = true,
) {
    companion object {
        /** 按 role 解析 runtimeParams Map → RuntimeParams，缺失 key 回落硬编码默认。 */
        fun resolve(role: String, overrides: Map<String, String>): RuntimeParams = when (role) {
            "master" -> RuntimeParams(
                stepBudget = overrides["stepBudget"]?.toIntOrNull()?.coerceIn(3, 15) ?: EngineDefaults.STEP_BUDGET,
            )
            "hello" -> RuntimeParams(
                dailyRecommendCount = overrides["dailyRecommendCount"]?.toIntOrNull()?.coerceIn(1, 5) ?: 1,
                recommendListSize = overrides["recommendListSize"]?.toIntOrNull()?.coerceIn(5, 20) ?: 8,
            )
            "enrich" -> RuntimeParams(
                targetCoverage = overrides["targetCoverage"]?.toFloatOrNull()?.coerceIn(0.5f, 1.0f) ?: EngineDefaults.ENRICH_TARGET_COVERAGE,
            )
            "radio" -> RuntimeParams(
                targetCount = overrides["targetCount"]?.toIntOrNull()?.coerceIn(8, 30) ?: EngineDefaults.RADIO_TARGET_COUNT,
                autoRenew = overrides["autoRenew"]?.toBooleanStrictOrNull() ?: EngineDefaults.RADIO_AUTO_RENEW,
            )
            else -> RuntimeParams()
        }
    }
}
