package com.hmp.domain.agent.config

/**
 * 引擎出厂默认参数（v0 代码即配置 → v1 退居为出厂回落值）。
 *
 * 所有数值在此集中定义，DataStore AgentPolicyConfig 读不到对应字段时回落这里。
 * 新增参数必须先在此定义默认值，再去对应消费方改配置读取逻辑。
 *
 * **分层位置（2026-09-20）**：本对象是**被 policy 与 runtime 共同消费的下层常量表**，
 * 故与同为「配置」的 [ResolvedAgentConfig] / [RuntimeParams] 一起放在 `config/` 叶子包。
 * 放在 `runtime/` 会让 `policy/AgentPolicy.kt`（消费 [defaultTemperatureFor]）与
 * `runtime/` 形成**双向依赖环**。
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

    // ── 出厂默认 System Prompt ──
    // 说明：真实的出厂默认文本 live 在多语言表 [L10N_PROMPTS]（runtime/i18n/Lang.kt），
    // 由 resolvePrompt() 按 preferredLang 选取；用户覆盖存于 AgentPolicyConfig.promptOverrides。
    // 此处曾有一份 DEFAULT_PROMPTS 单语副本 + FORBIDDEN_PLACEHOLDER_PATTERNS，
    // 两者均无消费方且 key 与实际不符，已删除以免误导。

    // ── 按 role 返回默认温度──
    /** role 取值："master" / "hello" / "enrich" / "radio" / 其他 → 0.7f */
    fun defaultTemperatureFor(role: String): Float = when (role) {
        "master" -> TEMPERATURE_MASTER
        "hello" -> TEMPERATURE_HELLO
        "enrich" -> TEMPERATURE_ENRICH
        "radio" -> TEMPERATURE_RADIO
        else -> TEMPERATURE_MASTER
    }
}

// 注：原 `defaultTrustLevelFor(role)` 已删除（2026-09-20）——全仓零调用方，
// 且它是本包内**唯一**引用 `policy.TrustLevel` 的地方（反向边）。信任档位的出厂
// 默认值 live 在 `policy` 侧（`AgentPolicyConfig.trustLevel` 的默认值 + `TrustLevel` 语义）。

