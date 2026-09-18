package com.hmp.domain.agent.runtime

import kotlinx.serialization.Serializable

/**
 * 全局 Agent 配置——不属于任何 Agent role 的参数。
 *
 * 存储在独立 DataStore key（"agent_global"），与 per-role AgentPolicyConfig 平行。
 * 所有字段有代码默认值，DataStore 为空时全部回落默认 —— 老用户零迁移。
 */
@Serializable
data class GlobalAgentConfig(
    /** 选中的预设人格 id："zhin" / "dj" / "curator"。null = 直接使用 personaOverride。 */
    val selectedPersonaId: String? = "zhin",
    /** 日 Token 配额（全局，跨所有 Agent 共享）。 */
    val dailyTokenQuota: Int = 500_000,
    /** 回复语言："zh" / "en" / "auto"。 */
    val replyLanguage: String = "zh",
    /** 嗓音与耳朵开关（M7 gate，默认关闭）。 */
    val voiceEnabled: Boolean = false,
) {
    companion object {
        /** DataStore key 常量。 */
        const val DATASTORE_KEY = "agent_global"
    }
}
