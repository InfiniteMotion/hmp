package com.hmp.domain.agent.runtime

/**
 * 时间来源（毫秒）。引擎平面保持纯 domain：所有需要"当前时间"的组件注入此函数，
 * 生产走 DI（绑 data 层 currentTimeMillis），测试注入脚本化递增时钟。
 */
typealias TimeProvider = () -> Long

/**
 * 引擎出厂默认参数（v0 代码即配置 → v1 退居为出厂回落值）。
 *
 * 所有数值在此集中定义，DataStore AgentPolicyConfig 读不到对应字段时回落这里。
 * 新增参数必须先在此定义默认值，再去对应消费方改配置读取逻辑。
 */
object EngineDefaults {

    // ── Master Agent ──
    /** 单任务步数预算（总纲 7.1）——每次 LLM 调用 + 其工具执行合计耗尽即熔断。 */
    const val STEP_BUDGET = 8
    /** Master LLM 采样温度（对话需要创造性，温度偏高）。 */
    const val TEMPERATURE_MASTER = 0.7f

    // ── Hello SubAgent ──
    /** Hello 各卡型平均温度（问候/推荐/发现需要自然感）。 */
    const val TEMPERATURE_HELLO = 0.6f
    /** 每日推荐卡数量（默认 1，F9-T1 报告叙事轮播）。 */
    const val HELLO_DAILY_RECOMMEND_COUNT = 1
    /** 推荐歌单长度（默认 8 首）。 */
    const val HELLO_RECOMMEND_LIST_SIZE = 8

    // ── Enrich SubAgent ──
    /** 信任阶梯升级阈值：同类写动作连续隐式接受 N 次升一档。 */
    const val TRUST_ESCALATION_THRESHOLD = 3
    /** 单日云端调用额度（防电台与高频对话日成本累积）。 */
    const val DAILY_CLOUD_QUOTA = 100
    /** 富化目标覆盖率（EnrichSubAgent 跑到此值自退出）。 */
    const val ENRICH_TARGET_COVERAGE = 0.9f
    /** Enrich LLM 采样温度（标签分类任务，温度低保证稳定 JSON）。 */
    const val TEMPERATURE_ENRICH = 0.4f

    // ── Radio SubAgent ──
    /** Radio 目标曲目数（buildLocalFallback + LLM enrich 都以此为基准）。 */
    const val RADIO_TARGET_COUNT = 12
    /** Radio LLM 采样温度（编排任务，温度低保证稳定 JSON）。 */
    const val TEMPERATURE_RADIO = 0.2f
    /** Radio 默认自动续歌开关。 */
    const val RADIO_AUTO_RENEW = true

    // ── 全局护栏（不暴露给用户）──
    const val CALL_COOLDOWN_MS = 5_000L
    const val EVENT_COOLDOWN_MS = 30_000L
    const val MAX_TASK_STATE_CHARS = 600
    const val MAX_LIBRARY_LIST_CHARS = 1200
    const val MAX_TOOL_RESULTS_KEPT = 6

    // ── 上下文窗口（F12-T3）──
    /**
     * 全部 Agent 统一的**上下文窗口假设值**。
     *
     * **固定常量，不探测、不建内置表、不支持用户覆盖**（`design/agent-token.md` T3 / D3）。
     * 理由：用户基数最大的两类官方端点（OpenAI / DeepSeek）**都不返回窗口**，
     * 动态获取不可能全覆盖；与其做"探测 + 多级回退"却仍留空洞，不如规定下限 + 固定假设。
     *
     * **使用前提**：用户配置的端点须支持 **≥ 64K**（低于此不在支持范围）。
     * 该前提**无法在配置时校验**（拿不到窗口）→ 因此超窗必须**显式可辨**
     * （见 `AgentContextBudget` 的前置守卫：超窗失败原因与"模型判 none"可区分）。
     *
     * 实测本仓 prompt 峰值仅 1–3 万 token（开播曲库块 ~6,000 + 每轮候选 40 行 ~1,120），
     * 故 64K 相对峰值有 2–6 倍余量 —— 前置守卫正常情况下不触发，它是安全网而非主机制。
     */
    const val AGENT_CONTEXT_WINDOW: Int = 64_000

    /** 前置守卫的安全系数：prompt 估算超过 `窗口 × 此值` 即触发降级/拒绝。 */
    const val CONTEXT_WINDOW_SAFETY_FACTOR: Float = 0.9f

    /**
     * 上下文组装时保留的最近消息条数（**唯一内部常量**）。
     *
     * 收敛自两处散落魔数（F12-T4）：`AgentContextBudget` 的 6 与
     * `ChatAgentGateway.buildHistory` 的 30。**不上升为设置项** ——
     * 它是内部组装策略，不是用户偏好。
     */
    const val HISTORY_KEEP_COUNT: Int = 6

    // ── 出厂默认 System Prompt 文本（给 UI 护栏参照，引擎实际运行走代码组装函数）──
    /**
     * key 约定：chat.system / enrich.system / radio.dj / hello.greeting
     * 这些文本不直接注入 LLM——引擎里的 buildChatSystemPrompt() 等函数还是会执行。
     * 用户编辑的内容通过 promptOverrides 存，运行时追加到代码组装的 prompt 末尾。
     */
    val DEFAULT_PROMPTS: Map<String, String> = mapOf(
        "chat.system" to "（由运行时代码动态组装：人格段落 + 曲库概览 + 用户画像 + 当前时间 + 正在播放 + 任务状态）",
        "enrich.system" to """
你是一位专业的音乐编辑，精通各类音乐风格、流派发展历史和艺术家背景。
你将对一组歌曲进行渐进式富化：枚举标签 → 自由文本 → 总体反思。
⚠️ 核心约束：不确定就不要编造。编造的错误信息比空着更糟糕。
- 对歌手背景/奖项/歌词/创作背景，只有 100% 确定的才能写
- genre/mood 枚举如果拿不准就少标（不超过候选值的一半）
- language/era 拿不准就返回 UNKNOWN
""".trimIndent(),
        "radio.dj" to "你是音乐电台的温和 DJ。每次切歌时说一句简短自然的中文衔接语，15-20 字。",
        "hello.greeting" to """
你是用户的音乐伙伴。根据用户画像和正在播放的歌曲，用一句话自然地打招呼，15-25 字。
语气符合人设（知音/DJ/馆长）。
""".trimIndent(),
    )

    /** 用户追加的 prompt 中如果出现这些占位符，护栏会警告。
     *  因为引擎的 prompt 组装用的是 Kotlin 代码（${变量} 插值），不是 {{占位符}}。 */
    val FORBIDDEN_PLACEHOLDER_PATTERNS: List<Regex> = listOf(
        Regex("""\{\{[a-zA-Z_]+\}\}"""),  // {{占位符}}
    )

    // ── 按 role 返回默认温度──
    /** role 取值："master" / "hello" / "enrich" / "radio" / 其他 → 0.7f */
    fun defaultTemperatureFor(role: String): Float = when (role) {
        "master" -> TEMPERATURE_MASTER
        "hello" -> TEMPERATURE_HELLO
        "enrich" -> TEMPERATURE_ENRICH
        "radio" -> TEMPERATURE_RADIO
        else -> TEMPERATURE_MASTER
    }

    // ── 按 role 返回默认信任档位（仅 master/enrich 有意义）──
    /** master 默认 SUGGEST（谨慎），enrich 默认 SILENT（纯后台）。 */
    fun defaultTrustLevelFor(role: String): Int = when (role) {
        "enrich" -> com.hmp.domain.agent.policy.TrustLevel.SILENT
        else -> com.hmp.domain.agent.policy.TrustLevel.SUGGEST
    }
}

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
    val persona: com.hmp.domain.agent.persona.CompanionProfile?,
    /** 解析后的最终语言偏好（已展开 "global" → GlobalAgentConfig.replyLanguage 或系统默认）。 */
    val preferredLang: String = "zh",
)

/**
 * 每个 Agent 的专属数值参数集。
 *
 * resolve(role, overrides) 从 AgentPolicyConfig.runtimeParams Map 解析，
 * 缺失的 key 回落 EngineDefaults 的硬编码默认值。
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
