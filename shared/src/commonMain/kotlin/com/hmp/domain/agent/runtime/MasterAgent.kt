package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.config.EngineDefaults
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import com.hmp.domain.agent.infra.PresenceEvent
import com.hmp.domain.agent.infra.SessionStore
import com.hmp.domain.agent.policy.AgentPolicy
import com.hmp.domain.agent.policy.AgentPolicyConfig
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.policy.TrustLedger
import com.hmp.domain.agent.policy.TrustLevel
import com.hmp.domain.agent.enrich.EnrichTask
import com.hmp.domain.agent.enrich.EnrichHealth
import com.hmp.domain.agent.runtime.i18n.resolvePrompt
import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.Capability
import com.hmp.domain.agent.port.CapabilityState
import com.hmp.domain.agent.port.ConfirmGate
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolCall
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.port.AgentKeepAlivePort
import com.hmp.domain.agent.port.KeepAliveReason
import com.hmp.domain.agent.port.PlaybackObservationBus
import com.hmp.domain.agent.persona.DefaultCompanionProfiles
import com.hmp.domain.agent.runtime.sub.enrich.EnrichSubAgent
import com.hmp.domain.agent.runtime.sub.hello.HelloSubAgent
import com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent
import com.hmp.domain.agent.runtime.sub.radio.RadioTrigger
import com.hmp.domain.agent.runtime.sub.shared.SubAgent
import com.hmp.domain.agent.tool.bindCapabilityTools
import com.hmp.domain.agent.tool.spec.ToolRegistry
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import com.hmp.platform.Volatile

/**
 * Master Agent —— 唯一大脑（设计铁则 F1）。
 *
 * 职责：
 * ① **对话能力**（面向用户）：handleUserMessage() —— 原 AgentOrchestrator.run() 循环，
 *    多轮 LLM 对话 + tool_result 回传 + PolicyGuard 许可门 + ConfirmGate 批量确认 + 审计。
 *    ToolRegistry 全 32 工具（27 基础 + 5 enrich_* ← chatTransport/chatToolRegistry 注入时自动生效）。
 * ② **后台管理**（面向 SubAgent）：startEnrich/stopEnrich/pauseEnrich/resumeEnrich/rescanEnrich ——
 *    Master 只管外部生命周期，SubAgent 自管内部循环（Enrich 拉活/处理/验收/重试、Radio 生成 playlist/续歌）。
 *    AgentScheduler 注册 + SubAgent 注册表。
 * ③ **全局基础设施**：AgentScheduler（priority 仲裁 pause/resume）+ GlobalTokenCounter（日配额）。
 *
 * 与原 AgentOrchestrator 的关系：
 * AgentOrchestrator.run() 的循环逻辑**完整搬进** handleUserMessage()，AgentOrchestrator 类保留作为
 * 兼容薄壳（内部委托给 MasterAgent），现有 ChatAgentGateway 测试零修改。
 */
class MasterAgent internal constructor(
    /** 时间来源（毫秒） */
    private val timeProvider: () -> Long,
    /** 全局 Token 日配额计数器 */
    internal val tokenCounter: GlobalTokenCounter = GlobalTokenCounter(timeProvider),
    /**
     * F12-T1：唯一记账口。一次 LLM 调用写三处 —— 当日累加（tokenCounter）·
     * 窗口占用（各 AgentContextBudget 自行更新）· 明细账本（token_ledger）。
     * null = 不记账（老装配 / 测试）。
     */
    private val tokenMeter: TokenMeter? = null,
    /** 系统条件提供者（电量/网络） */
    private val systemConditions: SystemConditions = DefaultSystemConditions(),
    /** 音乐库仓库（富化健康度查询 + enrich 批次派发） */
    private val musicRepository: MusicRepository? = null,

    // ── 对话能力依赖（T 阶段整合，默认 null 表示不启用对话） ──
    /** 对话 LLM 传输实例（热更新：startHello 后用户改配置要能生效） */
    private var chatTransport: LlmTransport? = null,
    /** 对话工具注册表（完整 32 工具：27 基础 + 5 enrich_* 专属） */
    private val chatToolRegistry: ToolRegistry? = null,
    /** 对话许可护栏（PolicyGuard + TrustLedger） */
    private val chatPolicyGuard: PolicyGuard? = null,
    /** 审计日志端口 */
    private val chatAuditLog: AuditLogPort? = null,
    /** 会话存储 */
    private val chatSessionStore: SessionStore? = null,
    /** 存在感总线 */
    private val chatPresenceBus: PresenceBus? = null,
    /** 观测面总线：转给电台，用于编排动作期间静默（避免自己的操作被当成用户行为） */
    private val observationBus: PlaybackObservationBus? = null,
    /**
     * 保活端口（F11-L1）—— agent 运行时向平台声明"我需要留在后台"。
     * null（测试 / Desktop / 未接线）→ 全部调用静默跳过。见 `design/agent-lifecycle.md`。
     */
    private val keepAlivePort: AgentKeepAlivePort? = null,
    /** 步数预算（硬熔断） */
    private val stepBudget: Int = EngineDefaults.STEP_BUDGET,

    // ── Enrich 后台能力依赖（默认 null；startEnrich 时可单独传入覆盖） ──
    /** EnrichSubAgent 的独立 LLM 传输实例（方案 B：每 Agent 一 Transport） */
    private val enrichTransport: LlmTransport? = null,
    // ── Per-Agent LLM Endpoint Config（独立端点：每 Agent 可不同 model/key） ──
    /** 全局默认 LLM 端点（向后兼容：不传 per-Agent config 时全部用它） */
    private val defaultLlmConfig: AiEndpointConfig? = null,
    /** Master chat 端点 */
    private var chatConfig: AiEndpointConfig? = defaultLlmConfig,
    /** Enrich 端点 */
    private var enrichConfig: AiEndpointConfig? = defaultLlmConfig,
    /** Hello 端点 */
    private var helloConfig: AiEndpointConfig? = defaultLlmConfig,
    /** Radio 端点 */
    private var radioConfig: AiEndpointConfig? = defaultLlmConfig,

    // ── Radio 电台能力依赖（M6-T1；默认 null，startRadio 时需要非 null） ──
    /** RadioSubAgent 的独立 LLM 传输实例（方案 B：每 Agent 一 Transport） */
    private val radioTransport: LlmTransport? = null,
    /** 播放控制端口（电台续歌 SILENT） */
    private val playbackPort: com.hmp.domain.agent.port.PlaybackCommandPort? = null,
    /** 当前播放上下文提供者（电台种子自动提取） */
    private val nowPlayingProvider: com.hmp.domain.agent.port.NowPlayingContextProvider? = null,

    // ── Hello 门面副驾驶能力依赖 ──
    /** HelloSubAgent 的独立 LLM 传输实例（方案 B：每 Agent 一 Transport） */
    private val helloTransport: LlmTransport? = null,

    // ── Agent 配置持久化（可选；null 则 config 只在 MasterAgent 实例存活期间有效） ──
    /** AgentPolicyConfig DataStore 读写（per-Agent 信任档位 + 永远允许白名单） */
    private val settingsRepo: com.hmp.domain.setting.SettingsRepository? = null,

    // ── W0: HelloSubAgent 持久化 DAO（可选；null 降级内存卡池 + 内存报告叙事） ──
    private val helloCardCacheDao: com.hmp.data.database.HelloCardCacheDao? = null,
    private val helloReportNarrativeDao: com.hmp.data.database.HelloReportNarrativeDao? = null,

    // ── 用户认识模块（画像）—— Master 的常驻子系统（契约 v3.6 §4.6）──
    /**
     * v3.6 起画像**归属 Master**：不再单独注册 Koin 单例、不再以"可空引用"挂靠，
     * 三个 DAO 由装配方注入、实例在这里构建，经 [userMemory] 对外暴露。
     * 依赖不齐（musicRepository 或 DAO 缺位，测试/旧装配路径）则为 null ——
     * 所有画像链路对 null 静默跳过，画像失败永不拖垮 Master。
     */
    private val userProfileEvidenceDao: com.hmp.data.database.UserProfileEvidenceDao? = null,
    private val userProfilePortraitDao: com.hmp.data.database.UserProfilePortraitDao? = null,
    private val userProfileNarrativeDao: com.hmp.data.database.UserProfileNarrativeDao? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 用户认识模块（画像）—— Master 的**常驻子系统**（契约 v3.6 §4.6）。
     *
     * 规则与存储全在 [UserMemory]；Master 只负责两条 LLM 链路（叙事重生成、
     * 对话抽取）的发起与 system prompt 注入的搬运。UI / 工具 / Gateway 统一从
     * 这里取（`masterAgent.userMemory`），不再有第二条注入路径。
     */
    val userMemory: com.hmp.domain.agent.profile.UserMemory? = run {
        val repo = musicRepository
        val evidence = userProfileEvidenceDao
        val portrait = userProfilePortraitDao
        if (repo != null && evidence != null && portrait != null) {
            com.hmp.domain.agent.profile.UserMemory(
                musicRepository = repo,
                evidenceDao = evidence,
                portraitDao = portrait,
                narrativeDao = userProfileNarrativeDao,
                auditLog = chatAuditLog,
                timeProvider = timeProvider,
            )
        } else {
            null
        }
    }

    // ═══ M6-T2/M6-T3：Radio 事件监听状态 ═══
    /**
     * 队列见底阈值：低于此值触发补歌。
     *
     * 见 `docs/7_x/B agent-build/design/agent-radio.md` §7.5（D2）—— 补歌是**保底出声**，不走模型判断：
     * 无端点时直接本地补，有端点时第 3 步接入 judge 后先问风格、失败退回本地。
     * 当前决策内核尚未落地，因此这里只有本地补歌一条路径。
     */
    private val queueLowThreshold = 2
    // ═══ F11：运行时保活诉求（agent 活跃 → 平台保持进程存活）═══
    /**
     * 电台保活诉求是否已声明。**幂等守卫**——只在真变化时通知端口，
     * 避免电台每次队列刷新都打一次平台调用。
     */
    @Volatile private var radioKeepAliveActive = false

    /**
     * 声明 / 撤销电台保活诉求（F11-L1）。
     *
     * 电台会话活跃 = 进程必须留在后台（含"等模型出队列"的无音频窗口）。
     * 端口为 null 时静默跳过，异常不外抛（保活失败不该拖垮电台）。
     */
    private fun updateRadioKeepAlive(active: Boolean) {
        if (radioKeepAliveActive == active) return
        radioKeepAliveActive = active
        runCatching { keepAlivePort?.setKeepAlive(KeepAliveReason.RADIO_ACTIVE, active) }
            .onFailure { e -> HmpLog.w(LogTag.AgentMaster, e) { "🤖 keepAlive port failed (non-fatal)" } }
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] radio keep-alive=$active" }
    }

    /** 标记 Radio 事件监听是否已启动（避免多次 startRadio 重复 launch） */
    @Volatile private var radioListenersStarted: Boolean = false
    /** 观测面 trackSettled listener 协程引用（stopRadio 时 cancel） */
    private var radioSettledListenerJob: kotlinx.coroutines.Job? = null
    /** 观测面 pauseEvents listener 协程引用（stopRadio 时 cancel） */
    private var radioPauseListenerJob: kotlinx.coroutines.Job? = null
    /** trackChangeEvents (DjBlank) listener 协程引用（stopRadio 时 cancel） */
    private var radioTrackChangeListenerJob: kotlinx.coroutines.Job? = null

    /** radio 运行时，RadioSubAgent.radioState 的值会被转发到这里。固定实例（UI 层 remember 缓存的是同一个）。 */
    private val _radioState = MutableStateFlow<com.hmp.domain.agent.runtime.sub.radio.RadioState?>(null)
    val radioState: StateFlow<com.hmp.domain.agent.runtime.sub.radio.RadioState?> = _radioState
    /**
     * 电台卡片状态（C1：卡片是 agent 唯一的存在面）。固定信息与动态信息已由 RadioSubAgent 分开。
     *
     * 之前 RadioCard 去 HelloSubAgent 的 cards 里找 RADIO_STATUS，而那张卡早已拆到 UI 层、
     * 且只在 HelloSlideCards 里本地构建，从来没进过 cards 流 —— 那两行是死代码。
     */
    private val _radioCardState = MutableStateFlow(com.hmp.domain.agent.runtime.sub.radio.RadioCardState(null, 0, null, null))
    val radioCardState: StateFlow<com.hmp.domain.agent.runtime.sub.radio.RadioCardState> = _radioCardState
    /** 卡片状态转发协程（stopRadio 时 cancel）。 */
    private var radioCardStateForwarderJob: kotlinx.coroutines.Job? = null

    /**
     * 电台镜像队列的固定转发流。RadioSubAgent 每次开播都新建实例，UI 层 remember 缓存的
     * 必须是这个固定 StateFlow——直接透传子代理的 playlist 会缓存到旧实例/空流，
     * LLM 返回落 seedWhy/whys 后卡片永远收不到（真机已踩）。
     */
    private val _radioPlaylist = MutableStateFlow<List<com.hmp.domain.agent.runtime.sub.shared.RadioTrack>>(emptyList())
    val radioPlaylist: StateFlow<List<com.hmp.domain.agent.runtime.sub.shared.RadioTrack>> = _radioPlaylist
    /** 镜像队列转发协程（stopRadio 时 cancel）。 */
    private var radioPlaylistForwarderJob: kotlinx.coroutines.Job? = null

    /**
     * G6：每日推荐 / 私人推荐列表的**固定转发流**。
     *
     * HelloSubAgent 是 Koin 初始化时异步 startHello 出来的，UI 首帧拿子代理的流可能为 null；
     * 且 stopHello/重建会换新实例。所以 UI 必须记住这个固定 StateFlow，而不是透传子代理的流
     * ——与 `_radioPlaylist` 同一教训（直接透传会缓存到空流/旧实例）。
     */
    private val _dailyRecommendList =
        MutableStateFlow<com.hmp.domain.agent.card.RecommendListPayload?>(null)
    val dailyRecommendList: StateFlow<com.hmp.domain.agent.card.RecommendListPayload?> = _dailyRecommendList
    private val _privateRecommendList =
        MutableStateFlow<com.hmp.domain.agent.card.RecommendListPayload?>(null)
    val privateRecommendList: StateFlow<com.hmp.domain.agent.card.RecommendListPayload?> = _privateRecommendList
    /** 推荐列表转发协程（stopHello 时 cancel）。 */
    private var recommendListForwarderJob: kotlinx.coroutines.Job? = null
    /**
     * 上一段电台对话（**进程内，不落盘**）。
     *
     * 电台子代理每次开电台都新建实例，所以这份快照只能放在这里。
     * 命中复用条件时直接续上对话，省掉曲库视图那次最贵的上下文组装。
     * 仅内存、且情境换代（时段变化）或超过 REUSE_BACKSTOP_MS 兜底作废 —— 不违反 C2（不写长期偏好）。
     */
    private var retainedRadio: com.hmp.domain.agent.runtime.sub.radio.RadioConversation? = null
    /** 转发协程——把 RadioSubAgent.radioState → _radioState。stopRadio 时 cancel。 */
    private var radioStateForwarderJob: kotlinx.coroutines.Job? = null

    /** 全局唯一调度器（F3） */
    internal val scheduler = AgentScheduler(timeProvider, tokenCounter, systemConditions)

    /**
     * 看板用：今日 Token 用量快照（门面）。
     *
     * **F13**：UI 只需要「用了多少 / 配额多少」这个**数据形状**，不需要引擎计数器本身。
     * 暴露这一条即可把 `tokenCounter`（及 [GlobalTokenCounter]）收敛为 internal。
     */
    val tokenUsage: kotlinx.coroutines.flow.StateFlow<TokenSnapshot> get() = tokenCounter.snapshot

    // ═══ per-Agent 持久化配置（AgentPolicyConfig v0→v1：从 2 字段扩展到 6 字段）═══
    // init 块里从 DataStore 读（runBlocking，因为 init 块不是 suspend）；
    // 没有 settingsRepo 就用默认值（首次启动）。
    // v1 新增：globalAgentConfig（全局）+ radio/hello policy config（之前不存在 key）
    /** Master 对话 Agent 的完整配置（trustLevel + alwaysAllow + temperature + runtimeParams + promptOverrides + preferredLang + enabled） */
    private var masterPolicyConfig: AgentPolicyConfig
    /** Enrich 后台 Agent 的完整配置 */
    private var enrichPolicyConfig: AgentPolicyConfig
    /** Radio SubAgent 的完整配置（v1 新增） */
    private var radioPolicyConfig: AgentPolicyConfig
    /** Hello SubAgent 的完整配置（v1 新增） */
    private var helloPolicyConfig: AgentPolicyConfig
    /** 全局 Agent 配置（人格选择 / 配额 / 语言 / 语音开关） */
    private var globalAgentConfig: GlobalAgentConfig

    init {
        masterPolicyConfig = runBlocking {
            settingsRepo?.getAgentPolicyConfig("master") ?: AgentPolicyConfig()
        }
        enrichPolicyConfig = runBlocking {
            settingsRepo?.getAgentPolicyConfig("enrich")
                ?: AgentPolicyConfig(
                    trustLevel = com.hmp.domain.agent.policy.TrustLevel.SILENT,
                    alwaysAllow = mutableSetOf(
                        "library_search", "library_similar", "library_stats", "library_recent_history",
                        "library_tags", "library_songs_by_tag", "library_songs_by_artist", "library_songs_by_album",
                        "library_artists", "library_albums",
                        "song_tags_get", "song_tag_user_add", "song_tag_user_remove",
                    ),
                )
        }
        radioPolicyConfig = runBlocking {
            settingsRepo?.getAgentPolicyConfig("radio") ?: AgentPolicyConfig()
        }
        helloPolicyConfig = runBlocking {
            settingsRepo?.getAgentPolicyConfig("hello") ?: AgentPolicyConfig()
        }
        globalAgentConfig = runBlocking {
            settingsRepo?.getGlobalAgentConfig() ?: GlobalAgentConfig()
        }
    }

    // ── 持久化辅助方法 ─
    private fun persistMasterPolicyAsync() {
        val repo = settingsRepo ?: return
        scope.launch { repo.saveAgentPolicyConfig("master", masterPolicyConfig) }
    }
    private fun persistEnrichPolicyAsync() {
        val repo = settingsRepo ?: return
        scope.launch { repo.saveAgentPolicyConfig("enrich", enrichPolicyConfig) }
    }
    private suspend fun persistMasterPolicy() {
        settingsRepo?.saveAgentPolicyConfig("master", masterPolicyConfig)
    }
    private suspend fun persistEnrichPolicy() {
        settingsRepo?.saveAgentPolicyConfig("enrich", enrichPolicyConfig)
    }

    // ── 公开 getter（供 UI 层读配置）──
    fun getGlobalAgentConfig(): GlobalAgentConfig = globalAgentConfig
    fun getAgentPolicyConfig(role: String): AgentPolicyConfig = when (role) {
        "master" -> masterPolicyConfig
        "enrich" -> enrichPolicyConfig
        "radio" -> radioPolicyConfig
        "hello" -> helloPolicyConfig
        else -> AgentPolicyConfig()
    }

    // ── 公开 setter（供 UI 层写配置回 DataStore）──
    /** 把修改后的 AgentPolicyConfig 写回 DataStore + 更新 MasterAgent 内部持有引用。 */
    suspend fun saveAgentPolicyConfig(role: String, config: AgentPolicyConfig) {
        when (role) {
            "master" -> masterPolicyConfig = config
            "enrich" -> enrichPolicyConfig = config
            "radio" -> radioPolicyConfig = config
            "hello" -> helloPolicyConfig = config
        }
        settingsRepo?.saveAgentPolicyConfig(role, config)
    }

    /** 更新 globalAgentConfig 并写回 DataStore。 */
    suspend fun saveGlobalAgentConfig(config: GlobalAgentConfig) {
        // 内存引用同步更新（避免下次 init 前 getter 仍返回旧值）
        globalAgentConfig = config
        // 记忆开关实时生效：关闭时立即清空已存记忆、打开时恢复读取
        userMemory?.memoryEnabled = config.memoryEnabled
        if (!config.memoryEnabled) userMemory?.clear()
        settingsRepo?.saveGlobalAgentConfig(config)
    }

    /** SubAgent 注册表（F1：Master 持有，子Agent 不能自注册）。
     *
     * 预留键位：
     * - "enrich" → EnrichSubAgent（自循环 Worker）
     * - "radio"  → RadioSubAgent（自循环 Worker，M6）
     */
    private val _subAgents = mutableMapOf<String, com.hmp.domain.agent.runtime.sub.shared.SubAgent>()
    internal val subAgents: Map<String, com.hmp.domain.agent.runtime.sub.shared.SubAgent> get() = _subAgents.toMap()

    /** F9-A0：所有实现了 Capability 接口的 SubAgent —— Tool 层和 UI 层从这里读统一状态 */
    internal val capabilities: Map<String, Capability> get() =
        _subAgents.values.filterIsInstance<Capability>().associateBy { it.capabilityName }

    /** F9-A0：按名字取 Capability（工具层和 Koin 注入用） */
    fun capability(name: String): Capability? =
        _subAgents[name] as? Capability

    // ── SubAgent 生命周期保护 ──
    /** startEnrich/stopEnrich 的 Mutex——防止并发创建多个 EnrichSubAgent */
    private val enrichLifecycleMutex = Mutex()
    /** Enrich runLoop 协程 Job 跟踪——旧实例完全退出后才能启动新的 */
    private var enrichRunLoopJob: kotlinx.coroutines.Job? = null
    /** Hello 生命周期保护（同上） */
    private val helloLifecycleMutex = Mutex()
    private var helloRunLoopJob: kotlinx.coroutines.Job? = null
    /** Radio 生命周期保护（同上） */
    private val radioLifecycleMutex = Mutex()
    private var radioRunLoopJob: kotlinx.coroutines.Job? = null

    // ===== 生命周期 =====

    /** 内部生命周期作用域——供同一模块内的装配代码（`SharedModules` 的 `.also`）launch 协程用。
     *  shutdown() 时会 cancel 此 scope，防止热监听协程泄漏。
     *
     *  F13：装配已归属 `:shared`，故本属性收为 internal（UI 不再需要引擎生命周期作用域）。 */
    internal val lifecycleScope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())

    /**
     * 库变更通知（契约 §4.1.4 事件驱动，v3.6 收口）。
     *
     * UI 层（LibraryViewModel）只发「库变了」这个**命令**，不直接触碰 [userMemory] 的
     * 内部方法 —— 统一走 [ensureProfileReady] 让 Master 协调完整的画像重建链路
     * （曲库侧写 → 行为侧写 → 叙事过期检查）。fire-and-forget：画像失败不影响库操作本身。
     */
    fun onLibraryMutated() {
        scope.launch { ensureProfileReady(forceBehavior = true, source = "library_mutated") }
    }

    /**
     * 对话首轮上下文组装时的画像预刷新（F9-T1）。
     *
     * ChatAgentGateway.buildFirstTurnContext() 需要**同步**拿到最新画像（先刷新再读），
     * 所以这个入口是 suspend 阻塞版本，不是 fire-and-forget。
     * 内部走 [ensureProfileReady] 完整链路，但**不传 forceBehavior**（20h 闸门自然起作用，
     * 避免每次对话都重跑行为建模）。
     */
    suspend fun ensureProfileReadyForFirstTurn() {
        ensureProfileReady(source = "first_turn")
    }

    /**
     * 画像链路统一入口（F9-T1 冷启动收口）。
     *
     * 任何需要"确保画像就绪"的场景（库变更 / 对话结束 / 应用启动）都走这里，
     * 外部不直接调 [UserMemory] 的 refresh* 方法。
     *
     * 内部协调顺序：
     * 1. refreshFromLibrary   —— 曲库侧写（幂等：没变化零写）
     * 2. refreshBehavior      —— 行为侧写（默认 20h 闸门；force=true 可破）
     * 3. maybeRegenerateNarrative —— 叙事过期检查（有 AI endpoint + factsHash 变了才跑）
     */
    private suspend fun ensureProfileReady(forceBehavior: Boolean = false, source: String = "unknown") {
        val profile = userMemory ?: run {
            HmpLog.w(LogTag.AgentMaster) { "🤖 ensureProfileReady($source): userMemory null, skip" }
            return
        }
        runCatching {
            val libCount = profile.refreshFromLibrary()
            val behCount = profile.refreshBehavior(force = forceBehavior)
            HmpLog.i(LogTag.AgentMaster) { "🤖 ensureProfileReady($source): lib=$libCount, beh=$behCount, forceBeh=$forceBehavior" }
            // 有侧写数据才检查叙事（没有就不浪费 AI 调用）
            if (profile.currentPortraits().isNotEmpty()) {
                maybeRegenerateNarrativeSync()
            }
        }.onFailure { e ->
            HmpLog.w(LogTag.AgentMaster, e) { "🤖 ensureProfileReady($source) failed (non-fatal)" }
        }
    }

    /**
     * Master 初始化（应用启动时调用）：
     * ① 启动 Scheduler 仲裁循环
     * ② 注册自己（priority=1，永不暂停）
     * ③ 检测富化健康度 → 决定是否创建 Enrich
     */
    internal suspend fun initialize(enrichTransport: LlmTransport? = null) {
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] initialize start" }

        // ── 加载 Per-Agent LLM Endpoint Config（null = 跟随全局） ──
        settingsRepo?.let { repo ->
            val globalConfig = repo.getActiveAiConfig()
            chatConfig = repo.getAgentEndpointConfig("master")?.takeIf { it.isConfigured } ?: globalConfig
            enrichConfig = repo.getAgentEndpointConfig("enrich")?.takeIf { it.isConfigured } ?: globalConfig
            radioConfig = repo.getAgentEndpointConfig("radio")?.takeIf { it.isConfigured } ?: globalConfig
            helloConfig = repo.getAgentEndpointConfig("hello")?.takeIf { it.isConfigured } ?: globalConfig
            HmpLog.i(LogTag.AgentMaster) {
                "🤖 [Master] per-Agent config loaded: chat=${chatConfig != null}, enrich=${enrichConfig != null}, radio=${radioConfig != null}, hello=${helloConfig != null}"
            }
        } ?: run {
            HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] settingsRepo null → all per-Agent configs stay null (defer to SubAgent defaults)" }
        }

        // 记忆开关：启动时同步给 UserMemory（关闭则不自动建模/读取）
        userMemory?.memoryEnabled = globalAgentConfig.memoryEnabled

        // F9-A0：绑定 CapabilityStatusTool 到 ToolRegistry
        // 使用 chatToolRegistry 字段（Koin 构造时注入），避免在 Koin .also 块里再 get() 引发循环
        chatToolRegistry?.bindCapabilityTools { capabilities }
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] CapabilityStatusTool bound (capabilities=${capabilities.keys})" }

        // ① 启动 Scheduler
        scheduler.startArbitration()

        // ② 注册自己（永不暂停）
        scheduler.registerAgent(
            AgentRegistration(
                agentId = "master",
                priority = AgentPriority.MASTER,
                tokenUsagePerMin = 2_000L,
                onPause = { /* Master 永不暂停 */ },
                onResume = { /* Master 永不暂停 */ },
            )
        )
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] registered with Scheduler priority=1" }

        // ③ 检测富化健康度 → 决定是否创建 Enrich
        musicRepository?.let { repo ->
            val health = repo.getEnrichHealth()
            HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] enrich health: ${health.enrichedSongCount}/${health.totalSongCount} coverage=${health.coverageRate} lowConf=${health.lowConfidenceCount}" }

            val defaultTarget = enrichPolicyConfig.resolvedFor("enrich").runtimeParams.targetCoverage
            if (health.coverageRate < defaultTarget) {
                HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] coverage ${health.coverageRate} < target $defaultTarget -> creating Enrich" }
                startEnrich(EnrichTask(targetCoverage = defaultTarget), enrichTransport)
            } else {
                HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] coverage ${health.coverageRate} >= target $defaultTarget -> skip Enrich" }
            }
        } ?: HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] MusicRepository null, skipping enrich health check" }

        // ④ W0: 自动启动 HelloSubAgent（门面副驾驶，Master 默认启动的唯一 SubAgent）
        if (musicRepository != null) {
            runCatching { startHello() }
                .onSuccess { hello ->
                    if (hello != null) HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] HelloSubAgent auto-started (cards available)" }
                    else HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] startHello returned null (missing deps?)" }
                }
                .onFailure { e -> HmpLog.w(LogTag.AgentMaster, e) { "🤖 [Master] startHello failed (non-fatal)" } }
        } else {
            HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] MusicRepository null, skip auto startHello" }
        }

        // ⑤ F9-T1 冷启动：首次画像建模（曲库侧写 + 行为侧写 + 叙事过期检查）。
        // fire-and-forget：ensureProfileReady 内部自带 runCatching，失败不拖垮 initialize。
        scope.launch { ensureProfileReady(source = "initialize") }
    }

    /** 应用销毁时调用，清理所有 SubAgent（suspend 版本——内部调 scheduler/SubAgent.shutdown 需协程） */
    internal suspend fun shutdown() {
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] shutdown" }
        scheduler.stopArbitration()
        _subAgents.values.forEach { it.shutdown() }
        cancelAllRunLoopJobs()
        _subAgents.clear()
        lifecycleScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] shutdown complete" }
    }

    /**
     * 应用生命周期绑定用的非 suspend 清理。
     * 各平台终止入口（Android onTerminate / Desktop close / iOS deinit）可直接调，
     * JVM shutdown hook 里用 runBlocking 包一下也可以。
     * 核心逻辑就是 cancel 所有 runLoop Job —— runLoop 里的 while(isActive) 会自然退出。
     */
    fun close() {
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] close (non-suspend)" }
        cancelAllRunLoopJobs()
        _subAgents.clear()
        lifecycleScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] close complete" }
    }

    private fun cancelAllRunLoopJobs() {
        enrichRunLoopJob?.cancel()
        enrichRunLoopJob = null
        radioRunLoopJob?.cancel()
        radioRunLoopJob = null
        helloRunLoopJob?.cancel()
        helloRunLoopJob = null
    }

    // ===== Enrich 管理（F1：Master 唯一决策） =====

    /**
     * 创建并启动 EnrichSubAgent（F1：只有 Master 能下令）。
     *
     * EnrichSubAgent 是自循环 Worker——内部拉活/处理/验收/重试全自己管，
     * Master 只负责外部生命周期：创建/注册 Scheduler/启动 runLoop。
     *
     * 完整链路：
     * 1. 创建 AgentContextBudget（窗口全 Agent 统一 64K，见 EngineDefaults.AGENT_CONTEXT_WINDOW）+ SchedulerStopSignal
     * 2. 实例化 EnrichSubAgent → 放进 _subAgents["enrich"]
     * 3. 注册 Scheduler priority=3
     * 4. scope.launch { enrichAgent.runLoop() } —— 只启动一个协程
     *
     * 幂等：如果已有 inactive 实例，先 stopEnrich() 清理再重建。
     *
     * @param task 任务单（含 targetCoverage / maxBatchSize）
     */
    internal suspend fun startEnrich(task: EnrichTask, enrichTransport: LlmTransport? = null) {
        enrichLifecycleMutex.withLock {
            // 等待旧 runLoop 协程完全退出（如果有），再创建新实例
            enrichRunLoopJob?.join()
            enrichRunLoopJob = null

            // 幂等检查：已有活跃实例 → 跳过
            _subAgents["enrich"]?.let { existing ->
                if (existing !is com.hmp.domain.agent.runtime.sub.enrich.EnrichSubAgent) {
                    HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] existing enrich is not EnrichSubAgent, force cleanup" }
                    stopEnrich()
                } else if (existing.state() == com.hmp.domain.agent.runtime.AgentRunState.RUNNING) {
                    HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] Enrich already active, skip create" }
                    return
                } else {
                    // 旧实例非活跃但未清理干净 → 先停再重建
                    stopEnrich()
                }
            }

            if (!enrichPolicyConfig.enabled) {
                HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] Enrich 已关闭，跳过启动" }
                return
            }

            val repo = musicRepository
            val registry = chatToolRegistry
            if (repo == null || registry == null) {
                HmpLog.e(LogTag.AgentMaster) { "🤖 [Master] Cannot start Enrich: musicRepository=${repo != null} chatToolRegistry=${registry != null}" }
                return
            }

            // 优先用 startEnrich() 传入参数，否则用构造函数的 enrichTransport（独立 Agent Transport）
            val effectiveEnrichTransport = enrichTransport ?: this.enrichTransport
            if (effectiveEnrichTransport == null) {
                HmpLog.e(LogTag.AgentMaster) { "🤖 [Master] No LlmTransport for Enrich — enrichTransport not injected in MasterAgent ctor" }
                return
            }

            // ① 创建 Enrich 的独立 AgentContextBudget
            val contextBudget = AgentContextBudget(
                agentId = "enrich",
                llmClient = effectiveEnrichTransport,
                tokenMeter = tokenMeter,
            )

            // ② 创建 SchedulerStopSignal——桥接 Scheduler pause/resume ↔ Enrich runLoop 的 waitResume()
            val enrichStopSignal = SchedulerStopSignal(tokenCounter)

            // ③ 构造 system prompt（F5：Master 注入，Enrich 不自演化角色）
            val systemPrompt = com.hmp.domain.agent.runtime.sub.enrich.EnrichPrompts.buildSystemPrompt(
                targetCoverage = task.targetCoverage,
                preferredLang = enrichPolicyConfig.preferredLang,
                globalReplyLanguage = globalAgentConfig.replyLanguage,
                userOverrides = enrichPolicyConfig.promptOverrides,
            )

            // ④ 构造 ToolRegistryView（基类 SubAgent 需要；Enrich 自循环不用 tools，但 F2 铁则保留）
            val toolView = ToolRegistryView.enrich(registry)

            // ⑤ 实例化 EnrichSubAgent（自循环 Worker，管道完全内化——prompt 和 LLM 调用都在 Enrich 内部）
            val enrichAgent = com.hmp.domain.agent.runtime.sub.enrich.EnrichSubAgent(
                agentId = "enrich",
                contextBudget = contextBudget,
                toolRegistryView = toolView,
                systemPrompt = systemPrompt,
                musicRepository = repo,
                presenceBus = chatPresenceBus,
                enrichConfig = enrichConfig,
                targetCoverage = task.targetCoverage,
                stopSignal = enrichStopSignal,
            )
            _subAgents["enrich"] = enrichAgent

            // ⑥ 注册到 Scheduler（priority=3）
            scheduler.registerAgent(
                AgentRegistration(
                    agentId = "enrich",
                    priority = AgentPriority.ENRICH,
                    tokenUsagePerMin = 1_000L,
                    onPause = { enrichStopSignal.onSchedulerPaused() },
                    onResume = { enrichStopSignal.onSchedulerResumed() },
                )
            )

            // ⑦ 启动 runLoop（跟踪 Job——旧 Job join 完才会到这里，所以一定是单协程）
            enrichRunLoopJob = scope.launch { enrichAgent.runLoop() }

            HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] EnrichSubAgent created (batch=${task.maxBatchSize}, targetCoverage=${task.targetCoverage}, config=${enrichConfig != null})" }
        }
    }

    /** Master 下令销毁 Enrich（F1：只有 Master 能下令） */
    suspend fun stopEnrich() {
        enrichLifecycleMutex.withLock {
            _subAgents["enrich"]?.let { enrich ->
                enrich.shutdown()
                scheduler.unregisterAgent("enrich")
                _subAgents.remove("enrich")
                HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] Enrich stopped" }
            }
            enrichRunLoopJob?.cancel()
            enrichRunLoopJob = null
        }
    }

    // ===== Radio 电台管理（M6-T1 · F1：Master 唯一决策） =====

    /**
     * 创建并启动 RadioSubAgent（F1：只有 Master 能调）。
     *
     * 链路（照着 startEnrich 模式）：
     * 1. 创建 AgentContextBudget + ToolRegistryView.radio()
     * 2. 实例化 RadioSubAgent → 放进 _subAgents["radio"]
     * 3. 注册 Scheduler priority=2（桥接 StopSignal）
     * 4. 启动 radioAgent.runLoop()
     * 5. 立即调 startRadio(seed) 跑第一轮协作 → 返回本地保底队列
     *
     * @param seed 用户种子（null = 自动从 nowPlaying 提取）
     * @return 本地保底队列（立即返回，零等待开听）；Radio 未创建成功时返回 emptyList()
     */
    suspend fun startRadio(
        seed: String? = null,
        trigger: RadioTrigger = RadioTrigger.HOME_CLICK,
        chatContext: String? = null,
    ): List<com.hmp.domain.agent.runtime.sub.shared.RadioTrack> {
        return radioLifecycleMutex.withLock {
            // 等待旧 runLoop 协程完全退出
            radioRunLoopJob?.join()
            radioRunLoopJob = null

            // 幂等：已有活跃实例 → 委托给它
            _subAgents["radio"]?.let { existing ->
                val radio = existing as? com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent
                if (radio != null && radio.state() == com.hmp.domain.agent.runtime.AgentRunState.RUNNING) {
                    HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] Radio already active, delegating to existing" }
                    return@withLock radio.startRadio(seed, trigger, chatContext)
                }
                // 非活跃实例 → 先停再重建
                stopRadio()
            }

            if (!radioPolicyConfig.enabled) {
                HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] Radio 已关闭，跳过启动" }
                return emptyList()
            }

            val repo = musicRepository
            val registry = chatToolRegistry
            val playback = playbackPort
            val nowPlaying = nowPlayingProvider
            if (repo == null || registry == null || playback == null || nowPlaying == null) {
                HmpLog.e(LogTag.AgentMaster) { "🤖 [Master] Cannot start Radio: deps missing repo=${repo != null} registry=${registry != null} playback=${playback != null} nowPlaying=${nowPlaying != null}" }
                return emptyList()
            }

            // ① AgentContextBudget——电台决策比 Enrich 复杂但比 Master 对话轻
            val effectiveRadioTransport = radioTransport
            if (effectiveRadioTransport == null) {
                HmpLog.e(LogTag.AgentMaster) { "🤖 [Master] No LlmTransport for Radio — radioTransport not injected in MasterAgent ctor" }
                return emptyList()
            }
            val contextBudget = AgentContextBudget(
                agentId = "radio",
                llmClient = effectiveRadioTransport,
                tokenMeter = tokenMeter,
            )

            // ② 权限过滤视图：Radio 可碰所有工具（MASTER 级，因为电台是 Master 发起的）
            val toolView = ToolRegistryView.radio(registry)

            // ③ StopSignal——桥接 Scheduler pause/resume
            val radioStopSignal = SchedulerStopSignal(tokenCounter)

            // ④ 实例化 RadioSubAgent
            val radioAgent = com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent(
                agentId = "radio",
                contextBudget = contextBudget,
                toolRegistryView = toolView,
                musicRepository = repo,
                playbackPort = playback,
                nowPlayingProvider = nowPlaying,
                presenceBus = chatPresenceBus,
                auditLog = chatAuditLog,
                observationBus = observationBus,
                retained = retainedRadio,
                // 注：原 onUserTookOver（暂停后用户播放 → 退电台）已废除 ——
                // 用户的播放控制不退出电台，见 RadioSubAgent.onPause / agent-radio.md C3
                radioConfig = this.radioConfig,
                targetCount = radioPolicyConfig.resolvedFor("radio").runtimeParams.targetCount,
                defaultTemperature = radioPolicyConfig.resolvedFor("radio").temperature,
                stopSignal = radioStopSignal,
                // 画像简报（v3.8）：时段/口味/听法/探索的摘录，供选种与编排参考
                memoryBriefing = userMemory?.radioBriefing(),
                // F9-T2：prompt 多语言 + 用户覆盖
                promptPreferredLang = radioPolicyConfig.preferredLang,
                globalReplyLanguage = globalAgentConfig.replyLanguage,
                promptOverrides = radioPolicyConfig.promptOverrides.toMap(),
            )
            _subAgents["radio"] = radioAgent

            // ⑤ 注册到 Scheduler（priority=2，比 Master 低、比 Enrich 高）
            scheduler.registerAgent(
                AgentRegistration(
                    agentId = "radio",
                    priority = AgentPriority.RADIO,
                    tokenUsagePerMin = 1_500L,
                    onPause = { radioStopSignal.onSchedulerPaused() },
                    onResume = { radioStopSignal.onSchedulerResumed() },
                )
            )

            // ⑥ 启动 runLoop（跟踪 Job）
            radioRunLoopJob = scope.launch { radioAgent.runLoop() }

            // ⑥-2 启动 Radio 事件监听协程（M6-T2 skip 感知 + M6-T3 DjBlank → 门面问候）
            setupRadioEventListeners()

            // ⑥-3 转发 RadioSubAgent.radioState → 固定的 _radioState（UI 层 remember 缓存这个固定实例）
            radioStateForwarderJob?.cancel()
            radioStateForwarderJob = scope.launch {
                radioAgent.radioState.collect { _radioState.value = it }
            }

            // ⑥-4 转发卡片状态 → RadioCard（固定信息 + 动态信息，见 RadioCardState）
            radioCardStateForwarderJob?.cancel()
            radioCardStateForwarderJob = scope.launch {
                radioAgent.cardState.collect { _radioCardState.value = it }
            }

            // ⑥-5 转发镜像队列 → 固定 _radioPlaylist。LLM 返回后 seedWhy/whys 落镜像
            //     只动 playlist，radioState/currentMusic 都不变——UI 的 RADIO_STATUS 卡
            //     以此流作为重建 key，否则按语停留在本地占位快照直到切歌
            radioPlaylistForwarderJob?.cancel()
            radioPlaylistForwarderJob = scope.launch {
                radioAgent.playlist.collect { _radioPlaylist.value = it }
            }

            // ⑦ 立即调 startRadio → 本地保底队列（同步返回给 ChatAgentGateway 渲染 songlist 卡）
            //    **开播整段跑在 Master 自己的 scope 里**：真机踩过 —— RadioCard 用
            //    rememberCoroutineScope.launch 调进来，用户切页导致组合树销毁，
            //    开播协程（正在等 LLM，实测 10~36s）被 "rememberCoroutineScope left the
            //    composition" 取消 → startSession 永远没执行 → session=null，
            //    电台"开着"但决策内核已死，之后的观测全部无人消费。
            //    async 在 Master scope、await 在调用方：UI 消失只是拿不到返回值，
            //    节目照常开播。await 撞上调用方取消时抛 CE，由调用方协程自行收场。
            val tracks = scope.async { radioAgent.startRadio(seed, trigger, chatContext) }.await()
            // F11-L1：电台会话已活跃 → 声明保活（含等模型的无音频窗口）
            updateRadioKeepAlive(true)
            HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] RadioSubAgent created + started (targetCount=12, tracks=${tracks.size})" }
            return tracks
        }
    }

    /** Master 下令停电台（用户点电台开关 / 明确说"停电台"）—— **唯一终态入口** */
    suspend fun stopRadio() = stopRadioInternal()

    /**
     * 收摊（**唯一终态路径**：手动关闭电台）。
     *
     * 2026-09-19 修订：原 `pausePlayback` 参数的第二态（"只退电台、不动播放"）曾服务于
     * "用户接管"路径；该路径已废除（**用户的播放控制不退出电台**，见 `agent-radio.md` C3）。
     * 故移除参数，收摊时**一律暂停播放**（只暂停、不清空播放队列 —— 关闭电台 ≠ 清空播放列表）。
     */
    private suspend fun stopRadioInternal() {
        // ⓪ **先暂停，再收尾** —— 必须在拿锁之前。
        //    startRadio 持有同一把 radioLifecycleMutex，而它要等模型返回（实测可达几十秒）。
        //    暂停若排在锁内，用户点停止后会一直等到模型回来才有反应，看起来就像"停不掉"。
        if (_radioState.value != null) {
            muteObservation()
            playbackPort?.execute(
                com.hmp.domain.agent.port.PlaybackCommand.PAUSE,
                com.hmp.domain.agent.port.CommandSource.AGENT_INTERNAL,
            )
        }

        radioLifecycleMutex.withLock {
            // ① 先 cancel 信号采集协程（防止电台已关但信号还在往决策环投递）
            radioSettledListenerJob?.cancel()
            radioSettledListenerJob = null
            radioPauseListenerJob?.cancel()
            radioPauseListenerJob = null
            radioTrackChangeListenerJob?.cancel()
            radioTrackChangeListenerJob = null
            radioListenersStarted = false   // 重置守卫，下次 startRadio 会重新注册
            // ② 停 SubAgent
            _subAgents["radio"]?.let { radio ->
                // 关闭电台只暂停、不清播放列表，所以对话往往还有效 —— 存下来供下次复用
                retainedRadio = (radio as? com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent)?.exportConversation()
                (radio as? com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent)?.stopRadio()
                radio.shutdown()
                scheduler.unregisterAgent("radio")
                _subAgents.remove("radio")
                HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] Radio stopped" }
            }
            // F11-L1：电台会话已结束 → 撤销保活诉求
            updateRadioKeepAlive(false)
            // ③ 播放已在 ⓪ 暂停（只暂停、不清空播放队列 —— 关闭电台不等于清空播放列表）。
            //    这里再补一次：若 ⓪ 时状态还没就绪（极端时序），确保最终是暂停态。
            muteObservation()
            playbackPort?.execute(com.hmp.domain.agent.port.PlaybackCommand.PAUSE, com.hmp.domain.agent.port.CommandSource.AGENT_INTERNAL)
            radioRunLoopJob?.cancel()
            radioRunLoopJob = null
            // ④ 停转发协程 + 清固定 _radioState（UI 层据此隐藏 RADIO_STATUS 卡）
            radioStateForwarderJob?.cancel()
            radioStateForwarderJob = null
            radioCardStateForwarderJob?.cancel()
            radioCardStateForwarderJob = null
            radioPlaylistForwarderJob?.cancel()
            radioPlaylistForwarderJob = null
            _radioCardState.value = com.hmp.domain.agent.runtime.sub.radio.RadioCardState(null, 0, null, null)
            _radioPlaylist.value = emptyList()
            _radioState.value = null
        }
    }

    /** 编排动作前静默观测，避免自己的播放指令被当成用户行为。 */
    private fun muteObservation() {
        observationBus?.muteFor(com.hmp.domain.agent.runtime.sub.radio.ORCHESTRATION_MUTE_MS)
    }

    /** Master 下令暂停电台：播放引擎 PAUSE → SubAgent 内部状态切 PAUSED（runLoop + playlist 保留） */
    internal suspend fun pauseRadio() {
        val radio = _subAgents["radio"] as? com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent ?: run {
            HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] pauseRadio: no radio agent" }
            return
        }
        // 先停播放引擎（RadioSubAgent 已剥离播放控制）
        muteObservation()
        playbackPort?.execute(com.hmp.domain.agent.port.PlaybackCommand.PAUSE, com.hmp.domain.agent.port.CommandSource.AGENT_INTERNAL)
        radio.pauseRadio()
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] Radio paused" }
    }

    /** Master 下令恢复电台：先启播放引擎 → SubAgent 内部状态切 PLAYING */
    internal suspend fun resumeRadio() {
        val radio = _subAgents["radio"] as? com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent ?: run {
            HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] resumeRadio: no radio agent" }
            return
        }
        // 先启播放引擎（RadioSubAgent 已剥离播放控制）
        // 必须静默：否则这次 PLAY 会被观测面当成"用户在电台暂停期间自己点了播放"→ 立刻退出电台
        muteObservation()
        playbackPort?.execute(com.hmp.domain.agent.port.PlaybackCommand.PLAY, com.hmp.domain.agent.port.CommandSource.AGENT_INTERNAL)
        radio.resumeRadio()
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] Radio resumed" }
    }

    // ===== Hello 门面副驾驶管理（W0 · F1：Master 唯一决策） =====

    /**
     * 创建并启动 HelloSubAgent（F1：只有 Master 能调）。
     *
     * Hello 是唯一 Master 默认启动的 SubAgent——initialize() 末尾自动调。
     *
     * 链路（照着 startRadio 模式，简化版）：
     * 1. 创建 AgentContextBudget + ToolRegistryView.empty()（Hello 不调工具）
     * 2. 实例化 HelloSubAgent → 放进 _subAgents["hello"]
     * 3. 注册 Scheduler priority=HELLO(4)（永不暂停）
     * 4. 启动 helloAgent.runLoop()
     *
     * H1 骨架：先跑非 LLM 版本（enableLlm=false）。H3 再补 LLM 生成。
     */
    internal suspend fun startHello(): HelloSubAgent? {
        return helloLifecycleMutex.withLock {
            // 等待旧 runLoop 协程完全退出
            helloRunLoopJob?.join()
            helloRunLoopJob = null

            // ① 幂等守卫：活跃实例 → 跳过
            _subAgents["hello"]?.let { existing ->
                val hello = existing as? HelloSubAgent
                if (hello != null && hello.state() != com.hmp.domain.agent.runtime.AgentRunState.UNREGISTERED) {
                    HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] Hello already active, skip create" }
                    return@withLock hello
                }
                // 非活跃实例 → 先停再重建
                stopHello()
            }

            if (!helloPolicyConfig.enabled) {
                HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] Hello 已关闭，跳过启动" }
                return@withLock null
            }

            // ② 前置依赖 null check —— Hello 没有 Repository/播放上下文就跑不起来
            val repo = musicRepository ?: run {
                HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] musicRepository null, skip startHello" }
                return@withLock null
            }

            // ③ 构造 AgentContextBudget + ToolRegistryView.empty()
            // helloTransport 独立于 chatTransport（方案 B：每 Agent 一 Transport）
            val toolView = ToolRegistryView.empty(chatToolRegistry)
            val stopSignal = SchedulerStopSignal(tokenCounter)
            val helloSystemPrompt = DefaultCompanionProfiles.DEFAULT.personaPrompt

            // ④ 实例化 HelloSubAgent → _subAgents["hello"]
            val enableLlm = helloTransport != null && helloConfig?.isConfigured == true
            val helloAgent = HelloSubAgent(
                agentId = "hello",
                contextBudget = AgentContextBudget(
                    agentId = "hello",
                    llmClient = helloTransport,
                    tokenMeter = tokenMeter,
                ),
                toolRegistryView = toolView,
                cardCacheDao = helloCardCacheDao,
                narrativeDao = helloReportNarrativeDao,
                musicRepository = repo,
                presenceBus = chatPresenceBus,
                nowPlayingProvider = nowPlayingProvider,
                stopSignal = stopSignal,
                enrichConfig = helloConfig,
                enableLlm = enableLlm,
                radioPlaylistProvider = { queryRadioPlaylist() },
                // 画像简报（v3.8）：曲库/口味/探索的摘录，供推荐与 DISCOVER 参考
                memoryBriefing = userMemory?.helloBriefing(),
                // UserMemory 引用（F9-T1）：报告叙事生成时按需取最新侧写
                userMemory = userMemory,
                // F9-T2：prompt 多语言 + 用户覆盖
                promptPreferredLang = helloPolicyConfig.preferredLang,
                globalReplyLanguage = globalAgentConfig.replyLanguage,
                promptOverrides = helloPolicyConfig.promptOverrides.toMap(),
                llmTemperature = helloPolicyConfig.temperature,  // null = 回落 typeTemperature 按卡型兜底
            )
            _subAgents["hello"] = helloAgent

            // ⑤ 注册 Scheduler（priority=HELLO=4，永不暂停）
            scheduler.registerAgent(
                AgentRegistration(
                    agentId = "hello",
                    priority = AgentPriority.HELLO,
                    tokenUsagePerMin = 500L,  // Hello token 消耗极低（每分钟 tick 不调 LLM）
                    onPause = { stopSignal.onSchedulerPaused() },
                    onResume = { stopSignal.onSchedulerResumed() },
                )
            )

            // ⑥ 启动 runLoop（跟踪 Job）
            helloRunLoopJob = scope.launch { helloAgent.runLoop() }

            // ⑦ G6：把子代理的两个推荐列表流镜像到 MasterAgent 的固定 StateFlow
            recommendListForwarderJob?.cancel()
            recommendListForwarderJob = scope.launch {
                launch { helloAgent.dailyRecommendList.collect { _dailyRecommendList.value = it } }
                launch { helloAgent.privateRecommendList.collect { _privateRecommendList.value = it } }
            }

            HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] HelloSubAgent created (llm=${helloTransport != null})" }
            return helloAgent
        }
    }

    /** Master 下令销毁 Hello */
    internal suspend fun stopHello() {
        helloLifecycleMutex.withLock {
            _subAgents["hello"]?.let { hello ->
                hello.shutdown()
                scheduler.unregisterAgent("hello")
                _subAgents.remove("hello")
                HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] Hello stopped" }
            }
            helloRunLoopJob?.cancel()
            helloRunLoopJob = null

            // G6：停止推荐列表转发并清空（避免 UI 停留在上一个实例的陈旧数据）
            recommendListForwarderJob?.cancel()
            recommendListForwarderJob = null
            _dailyRecommendList.value = null
            _privateRecommendList.value = null
        }
    }

    // ═══ 热更新 AI 配置 ═══

    /**
     * 热更新 AI 配置——用户在设置页改完 API Key/端点后调此方法。
     * 从 SettingsRepository 读 per-Agent config（null = 跟随全局），推给各 SubAgent。
     */
    suspend fun updateAiConfig() {
        val globalConfig = settingsRepo?.getActiveAiConfig()
        // 读 per-Agent 覆盖（null = 跟随全局）
        val perAgent = mapOf(
            "master" to settingsRepo?.getAgentEndpointConfig("master"),
            "enrich" to settingsRepo?.getAgentEndpointConfig("enrich"),
            "radio" to settingsRepo?.getAgentEndpointConfig("radio"),
            "hello" to settingsRepo?.getAgentEndpointConfig("hello"),
        )
        chatConfig = perAgent["master"]?.takeIf { it.isConfigured } ?: globalConfig
        enrichConfig = perAgent["enrich"]?.takeIf { it.isConfigured } ?: globalConfig
        radioConfig = perAgent["radio"]?.takeIf { it.isConfigured } ?: globalConfig
        helloConfig = perAgent["hello"]?.takeIf { it.isConfigured } ?: globalConfig

        HmpLog.i(LogTag.AgentMaster) {
            "🤖 [Master] updateAiConfig: chat=${chatConfig != null}, enrich=${enrichConfig != null}, radio=${radioConfig != null}, hello=${helloConfig != null}"
        }

        // 推给所有已启动的 SubAgent
        (_subAgents["hello"] as? HelloSubAgent)?.updateAiConfig(
            enableLlm = helloTransport != null && helloConfig?.isConfigured == true,
            enrichConfig = helloConfig,
        )
        (_subAgents["enrich"] as? com.hmp.domain.agent.runtime.sub.enrich.EnrichSubAgent)?.updateAiConfig(enrichConfig)
        (_subAgents["radio"] as? com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent)?.updateAiConfig(radioConfig)
    }

    // ═══════════════════════════════════════════════════════════════
    // M6-T2 跳过感知重排 + M6-T3 DJ 衔接预生成
    // ═══════════════════════════════════════════════════════════════

    /**
     * 启动 Radio 事件采集协程。
     *
     * **当前是过渡形态**：决策内核（`docs/7_x/B agent-build/design/agent-radio.md` §7）尚未落地，
     * 观测面（`TrackSettled` 事件流）也还没接，因此这里只保留两件不依赖判断的事：
     *
     * 1. 切歌 → 通知 Hello 门面 + 累计电台已播曲目（供 `remainingHint` 算队列余量）
     * 2. 队列见底 → 本地补歌（§7.5 D2 的「无 judge」分支，保底出声）
     *
     * 跳过事件暂时只记日志 —— 它需要「已播比例」才有判断价值，
     * 而现有 `skipEvents` 只有曲名（见 spec §2.1.1），等观测面落地后再接。
     *
     * 幂等（radioListenersStarted 守卫），避免多次 startRadio 重复 launch。
     */
    private fun setupRadioEventListeners() {
        if (radioListenersStarted) return
        radioListenersStarted = true

        val port = playbackPort
        val presence = chatPresenceBus
        if (port == null || presence == null) return

        // ── 观测面 ①：一首歌的结算（**带播放进度**）→ 决策内核 ──
        radioSettledListenerJob = scope.launch {
            HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] trackSettled → 电台决策内核（追踪看 RadioTrace）" }
            port.trackSettled.collect { event ->   // collect（不用 collectLatest，结算事件不能丢）
                (_subAgents["radio"] as? RadioSubAgent)?.onTrackSettled(event)
            }
        }

        // ── 观测面 ②：暂停 / 继续（不触发判断，只进上下文） ──
        radioPauseListenerJob = scope.launch {
            HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] pauseEvents → 电台决策内核（仅入上下文）" }
            port.pauseEvents.collect { event ->
                (_subAgents["radio"] as? RadioSubAgent)?.onPause(event)
            }
        }

        // ── 切歌：门面通知 + 累计已播 + 队列见底提醒 ──
        radioTrackChangeListenerJob = scope.launch {
            HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] trackChangeEvents → 门面 + 队列见底提醒" }
            port.trackChangeEvents.collectLatest { newTitle ->
                presence.emit(PresenceEvent.DjBlank)   // Hello 门面消费（保持原语义）
                val radio = _subAgents["radio"] as? RadioSubAgent
                radio?.onTrackPlayed(newTitle)

                // 队列见底 = "现在值得看一眼"，交给决策内核（§7.5 D2：有端点问模型，没有就本地补）
                val remaining = radio?.remainingHint() ?: return@collectLatest
                if (remaining <= queueLowThreshold) radio?.onQueueLow(remaining)
            }
        }
    }

    /** 查询电台状态（供 MasterChatGateway/ChatViewModel 轮询） */
    fun queryRadioState(): com.hmp.domain.agent.runtime.sub.radio.RadioState? =
        (_subAgents["radio"] as? com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent)?.queryState()

    fun queryRadioPlaylist(): List<com.hmp.domain.agent.runtime.sub.shared.RadioTrack>? =
        (_subAgents["radio"] as? com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent)?.queryPlaylist()

    /**
     * 电台镜像队列的响应式流——LLM 返回后 seedWhy/whys 落镜像只更新这个 Flow，
     * 不伴随 radioState 或 currentMusic 变化；UI 层必须 collect 它才能在
     * 模型回复到达时重建 RADIO_STATUS 卡（否则种子按语/下一首按语会一直停留在
     * 本地占位快照，直到切歌）。
     *
     * 注意：返回的是 Master 层固定转发流 [_radioPlaylist]，**不是**子代理的 playlist——
     * RadioSubAgent 每次开播都新建，透传会被 UI 的 remember 缓存成死流。
     */
    fun radioPlaylistFlow(): kotlinx.coroutines.flow.StateFlow<List<com.hmp.domain.agent.runtime.sub.shared.RadioTrack>> =
        _radioPlaylist

    /** 电台主题（用户 seed 或自动提取的风格关键词，null=自动电台） */
    fun queryRadioStationTheme(): String? =
        (_subAgents["radio"] as? com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent)?.stationTheme

    /** 电台未就绪时的稳定空消息流——缓存实例，避免每次调用新建导致 UI 以实例为 key 重复订阅（G8） */
    private val _emptyRadioMessageFlow =
        kotlinx.coroutines.flow.MutableStateFlow<com.hmp.domain.agent.runtime.sub.radio.RadioMessage?>(null)

    /** 电台短时消息流——emit 后 4s 自动消失，UI 层 collect 后直接渲染在 RADIO_STATUS 卡右侧 */
    fun radioMessageFlow(): kotlinx.coroutines.flow.StateFlow<com.hmp.domain.agent.runtime.sub.radio.RadioMessage?> =
        (_subAgents["radio"] as? com.hmp.domain.agent.runtime.sub.radio.RadioSubAgent)?.messageFlow
            ?: _emptyRadioMessageFlow

    /**
     * Hello 卡片流（**只读门面**）。
     *
     * 为什么不让 UI 直接拿 [helloAgent]：那会把 `HelloSubAgent` 这个**实现类型**
     * 暴露给 UI —— UI 只需要"看到卡片"，不需要"操作 Hello"。
     * 与 [tokenUsage] 同理：**把数据形状与写入能力分开**（F13 抽门面范式）。
     */
    val helloCards: kotlinx.coroutines.flow.StateFlow<List<com.hmp.domain.agent.card.SlideCard>>?
        get() = helloAgent()?.cards

    /** Hello 实例本身 —— **引擎内部用**（报告叙事段、卡片快照）。UI 走 [helloCards]。 */
    internal fun helloAgent(): HelloSubAgent? = _subAgents["hello"] as? HelloSubAgent

    // G6 的 dailyRecommendList / privateRecommendList 已改为类字段（固定转发流，见上方 _dailyRecommendList），
    // 不再是返回 nullable 的函数——UI 首帧即可拿到稳定 StateFlow。

    /** Enrich 进度 StateFlow（UI 层 EnrichTracking 卡用）。无 Enrich 时返回 null。 */
    fun enrichProgressState(): kotlinx.coroutines.flow.StateFlow<com.hmp.domain.agent.runtime.sub.enrich.EnrichProgress>? =
        (_subAgents["enrich"] as? com.hmp.domain.agent.runtime.sub.enrich.EnrichSubAgent)?.progressState

    /** UI 层轮询播放快照（ANCHOR 卡直接读）*/
    suspend fun queryNowPlayingContext(): com.hmp.domain.agent.port.NowPlayingContext? =
        nowPlayingProvider?.runCatching { getNowPlaying() }?.getOrNull()

    /** Hello 当前卡片列表快照（同步返回，测试/日志用） */
    internal fun queryHelloCards(): List<com.hmp.domain.agent.card.SlideCard>? =
        helloAgent()?.cards?.value

    // ═══ W0 报告叙事段对外接口（P5 收听报告页调用） ═══

    /** P5 🔄 重新生成按钮调——Hello 还没启动返回 null */
    internal suspend fun regenerateReportNarrative(
        timeRange: com.hmp.domain.agent.card.NarrativeTimeRange
    ): com.hmp.data.database.HelloReportNarrativeEntity? {
        val hello = helloAgent() ?: return null
        return runCatching { hello.regenerateReportNarrative(timeRange) }.getOrNull()
    }

    /** P5 页面加载时读 DAO——零阻塞；直接查 Master 自持有的 DAO，不依赖 HelloSubAgent 是否启动 */
    suspend fun getReportNarrative(
        timeRange: com.hmp.domain.agent.card.NarrativeTimeRange
    ): com.hmp.data.database.HelloReportNarrativeEntity? {
        val dao = helloReportNarrativeDao ?: return null
        return runCatching { dao.getLatest(timeRange.name) }.getOrNull()
    }

    // ═══ SubAgent 状态查询 & 原生生命周期方法 ═══

    /** 当前 SubAgent 状态查询（同步返回）。 */
    internal fun querySubAgents(): Map<String, String> {
        return _subAgents.mapValues { (_, agent) -> agent.state().name } +
            mapOf(
                "scheduler_state" to scheduler.registeredAgentIds().joinToString(","),
                "token_usage" to "${tokenCounter.usedToday()}/${tokenCounter.dailyTokenQuota}",
            )
    }

    /** 富化进程是否活跃（已启动未 shutdown）。 */
    internal fun isEnrichActive(): Boolean {
        val enrich = _subAgents["enrich"] as? com.hmp.domain.agent.runtime.sub.enrich.EnrichSubAgent ?: return false
        return enrich.state() == com.hmp.domain.agent.runtime.AgentRunState.RUNNING
    }

    /** 富化当前状态的摘要（覆盖率、Scheduler 状态、Token 剩余、进度）。 */
    internal suspend fun enrichStatusSummary(): Map<String, String> {
        val health = musicRepository?.getEnrichHealth()
        val schedulerEnrichState = scheduler.getState("enrich").name
        val enrich = _subAgents["enrich"] as? com.hmp.domain.agent.runtime.sub.enrich.EnrichSubAgent
        val progress = enrich?.getProgress()
        return buildMap {
            put("active", isEnrichActive().toString())
            put("scheduler_state", schedulerEnrichState)
            put("token_remaining", "${tokenCounter.remainingToday()}")
            put("processed", progress?.processed?.toString() ?: "0")
            put("success", progress?.success?.toString() ?: "0")
            put("failed", progress?.failed?.toString() ?: "0")
            if (health != null) {
                put("enriched_count", "${health.enrichedSongCount}")
                put("total_count", "${health.totalSongCount}")
                put("coverage_rate", health.coverageRate.toString())
                put("low_confidence_count", "${health.lowConfidenceCount}")
            }
        }
    }

    /** 启动富化流程（指定目标覆盖率，默认 0.9）。 */
    suspend fun startEnrich(targetCoverage: Float?) {
        val task = EnrichTask(targetCoverage = targetCoverage ?: enrichPolicyConfig.resolvedFor("enrich").runtimeParams.targetCoverage)
        startEnrich(task)
    }

    /** 暂停富化进程（Scheduler pause，进程保活但不处理新批次）。 */
    suspend fun pauseEnrich() {
        _subAgents["enrich"]?.pause()
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] enrich paused" }
    }

    /** 恢复富化进程。 */
    suspend fun resumeEnrich() {
        _subAgents["enrich"]?.resume()
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] enrich resumed" }
    }

    /** 重新扫描未覆盖歌曲并重置覆盖率目标。 */
    internal suspend fun rescanEnrich(newTarget: Float?) {
        if (!isEnrichActive()) {
            // 没在跑 → 直接 startEnrich
            startEnrich(newTarget)
            return
        }
        // 已在跑 → 更新 EnrichSubAgent 内部 targetCoverage（runLoop 下轮生效）
        val enrich = _subAgents["enrich"] as? com.hmp.domain.agent.runtime.sub.enrich.EnrichSubAgent
        val target = newTarget ?: 0.9f
        enrich?.updateTarget(target)
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] enrich rescan triggered, new target=$target" }
    }

    // ═══ 内建意图路由（SubAgent 生命周期原生暴露，不经过工具层）═══

    // ═══════════════════════════════════════════════════════════════════════
    // 内建意图表（确定性路由）
    // ═══════════════════════════════════════════════════════════════════════
    //
    // 原先是一串 171 行的 if 分支，9 个分支**同形**：
    //   命中 →（可选前置校验）→ 执行动作 → 写日志 → 返回固定形状的 AgentResult。
    // 表驱动之后：加一条意图 = 加一条表项，不必再抄一遍 20 行样板；
    // 而「顺序敏感」这件事也从"读一遍 171 行才知道"变成"看这张表的次序"。
    //
    // ⚠️ **表的次序即正确性**：「停电台」同时命中 STOP_RADIO 与强触发词「电台」，
    // 所以 radio_stop 必须排在 radio_start 之前 —— 否则用户说"停"会被当成"开"去重建队列。
    // 这条约束由 ChatIntentRulesTest 守着
    // （stopRadioInput_alsoLooksLikeRadioIntent_soOrderIsTheOnlyGuard）。
    //
    // 判定规则本身在 [ChatIntentRules]（8 张触发词表 + 匹配函数）；这里只负责编排响应。

    /** 一条内建意图：怎么认、认到之后做什么、回什么话。 */
    private class BuiltinIntent(
        /** `intentHandled` 值，同时用作成功日志名。 */
        val id: BuiltinIntentId,
        /** 命中判定。`lower` 已 trim + lowercase；`raw` 是原文，供需要原大小写的规则使用。 */
        val match: (lower: String, raw: String) -> Boolean,
        /** 前置校验：返回非 null 即**拦截** —— 不执行动作、不打成功日志，直接把该结果回给用户。 */
        val guard: (suspend () -> AgentResult?)? = null,
        /** 执行动作并给出回复文案。 */
        val run: suspend (lower: String, raw: String) -> String,
    )

    private val builtinIntents: List<BuiltinIntent> = listOf(
        // ── 电台停止 ──
        BuiltinIntent(
            id = BuiltinIntentId.RADIO_STOP,
            match = { lower, _ -> ChatIntentRules.isStopRadio(lower) || (lower == "停" && isRadioActive()) },
            run = { _, _ ->
                stopRadio()
                "电台已停止。"
            },
        ),

        // ── 电台恢复（须先于启动分支：否则"继续电台"会被 isRadioIntent 吞掉走 startRadio 重建）──
        BuiltinIntent(
            id = BuiltinIntentId.RADIO_RESUME,
            match = { _, raw -> RadioTrigger.fromChatInput(raw) == RadioTrigger.RESUME },
            guard = {
                val radio = _subAgents["radio"] as? RadioSubAgent
                val paused = radio?.queryState() is com.hmp.domain.agent.runtime.sub.radio.RadioState.PAUSED
                if (paused) null
                else answered(BuiltinIntentId.RADIO_RESUME_NOOP, "电台当前没有在暂停，不用恢复。")
            },
            run = { _, _ ->
                resumeRadio()
                "电台继续播放了。"
            },
        ),

        // ── 电台启动 ──
        BuiltinIntent(
            id = BuiltinIntentId.RADIO_START,
            match = { lower, _ -> ChatIntentRules.isRadioIntent(lower) },
            run = { _, raw ->
                val seed = ChatIntentRules.extractSeed(raw)
                val trigger = RadioTrigger.fromChatInput(raw)
                val tracks = runCatching { startRadio(seed, trigger) }
                    .onFailure { HmpLog.e(LogTag.AgentMaster, it) { "🤖 [Master] builtin: startRadio failed" } }
                    .getOrNull()
                if (tracks != null && tracks.isNotEmpty()) {
                    HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] builtin: startRadio seed=\"$seed\" tracks=${tracks.size}" }
                    "电台启动了${if (!seed.isNullOrBlank()) "，种子「$seed」" else ""}，为你选了 ${tracks.size} 首。"
                } else {
                    HmpLog.w(LogTag.AgentMaster) { "🤖 [Master] builtin: startRadio returned empty for seed=\"$seed\"" }
                    "电台没有找到足够的曲目，换个描述试试？"
                }
            },
        ),

        // ── 富化启动 ──
        BuiltinIntent(
            id = BuiltinIntentId.ENRICH_START,
            match = { lower, _ -> ChatIntentRules.isStartEnrich(lower) },
            guard = {
                if (isEnrichActive()) {
                    answered(BuiltinIntentId.ENRICH_START, "富化已经在跑了，说「重扫」可以重置覆盖率目标。")
                } else null
            },
            run = { _, _ ->
                startEnrich(null)
                "好的，富化已启动。后台自动扫描未覆盖歌曲补充标签，说「富化状态」可以看进度。"
            },
        ),

        // ── 富化停止（完全移除，区别于 pause 的调度暂停）──
        BuiltinIntent(
            id = BuiltinIntentId.ENRICH_STOP,
            match = { lower, _ -> ChatIntentRules.isStopEnrich(lower) },
            guard = { if (!isEnrichActive()) answered(BuiltinIntentId.ENRICH_STOP, "富化没在跑，无需停止。") else null },
            run = { _, _ ->
                stopEnrich()
                "富化已停止。"
            },
        ),

        // ── 富化暂停 ──
        BuiltinIntent(
            id = BuiltinIntentId.ENRICH_PAUSE,
            match = { lower, _ -> ChatIntentRules.isPauseEnrich(lower) },
            guard = { if (!isEnrichActive()) answered(BuiltinIntentId.ENRICH_PAUSE, "富化没在跑，不用暂停。") else null },
            run = { _, _ ->
                pauseEnrich()
                "好的，富化已暂停。"
            },
        ),

        // ── 富化恢复 ──
        BuiltinIntent(
            id = BuiltinIntentId.ENRICH_RESUME,
            match = { lower, _ -> ChatIntentRules.isResumeEnrich(lower) },
            guard = { if (!isEnrichActive()) answered(BuiltinIntentId.ENRICH_RESUME, "富化没在跑，直接说「开始富化」就行。") else null },
            run = { _, _ ->
                resumeEnrich()
                "好的，富化继续。"
            },
        ),

        // ── 富化重扫 ──
        BuiltinIntent(
            id = BuiltinIntentId.ENRICH_RESCAN,
            match = { lower, _ -> ChatIntentRules.isRescanEnrich(lower) },
            run = { _, _ ->
                rescanEnrich(null)
                "好的，正在重新扫描未覆盖的歌曲。"
            },
        ),

        // ── 富化状态查询 ──
        BuiltinIntent(
            id = BuiltinIntentId.ENRICH_STATUS,
            match = { lower, _ -> ChatIntentRules.isEnrichStatus(lower) },
            run = { _, _ ->
                val summary = enrichStatusSummary()
                if (summary["active"] == "true") {
                    "富化正在运行中· ${summary["coverage_rate"] ?: "?"}% 覆盖· 配额剩余 ${summary["token_remaining"] ?: "?"}。"
                } else {
                    "富化未启动，说「开始富化」就能开起来。"
                }
            },
        ),
    )

    /**
     * 确定性意图匹配——MasterAgent 作为唯一大脑，直接识别"开电台/停电台/暂停富化"等意图，
     * 跳过 LLM ReActLoop，直接调 SubAgent 原生生命周期方法。
     *
     * 返回 null 表示未命中，由调用方继续走正常 LLM 对话。
     */
    private suspend fun builtinIntent(input: String): AgentResult? {
        val lower = input.trim().lowercase()
        val intent = builtinIntents.firstOrNull { it.match(lower, input) } ?: return null
        intent.guard?.invoke()?.let { return it }
        val text = intent.run(lower, input)
        HmpLog.i(LogTag.AgentMaster) { "🤖 [Master] builtin: ${intent.id}" }
        return answered(intent.id, text)
    }

    /** 内建意图的统一返回形状：直接回复用户，未经 LLM、无工具调用、不消耗步数。 */
    private fun answered(intent: BuiltinIntentId, text: String) = AgentResult(
        text = text,
        stepsUsed = 0,
        toolCalls = emptyList(),
        terminatedBy = TerminationReason.ANSWERED,
        intentHandled = intent,
    )

    /** 「能力未就绪」的统一返回：不抛异常，把原因原样交给 UI 展示。 */
    private fun unavailable(text: String) = AgentResult(
        text = text,
        stepsUsed = 0,
        toolCalls = emptyList(),
        terminatedBy = TerminationReason.FAILED,
    )

    /** 电台是否在运行。 */
    private fun isRadioActive(): Boolean = queryRadioState() != null

    // ═══════════════════════════════════════════════════════════════════════
    // ① 对话能力（Master 作为唯一大脑的对话接口）
    //
    // 原 AgentOrchestrator.run() 循环逻辑完整搬入——多轮 tool_result 回传、
    // PolicyGuard 许可门、ConfirmGate 批量确认、审计、步数熔断。
    // ToolRegistry 自动带 enrich_* 工具（因为 chatDeps.masterAgentFacade = this）。
    // GlobalTokenCounter 统一统计对话 + 后台。
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Master 对话入口（原 AgentOrchestrator.run() 循环）。
     *
     * 对话依赖未注入时返回 FAILED AgentResult（不抛异常），方便 UI 层展示。
     */
    suspend fun handleUserMessage(
        userMessage: String,
        config: AiEndpointConfig,
        ctx: RunContextInput = RunContextInput(),
        confirmGate: ConfirmGate? = null,
    ): AgentResult {
        if (!masterPolicyConfig.enabled) {
            return unavailable("（MasterAgent 已关闭：在 Agent 设置中重新启用）")
        }
        // ═══ 内建意图路由（MasterAgent 作为唯一大脑，先做确定性意图匹配）═══
        // 命中则直接调 SubAgent 原生生命周期方法返回，不走 LLM ReActLoop。
        // Gateway 看到 intentHandled 字段决定 UI 层渲染（如电台 songlist bubble）。
        builtinIntent(userMessage)?.let { return it }

        val transport = chatTransport
            ?: return unavailable("（对话能力未启用：chatTransport 未注入）")
        val registry = chatToolRegistry
            ?: return unavailable("（对话能力未启用：chatToolRegistry 未注入）")
        val policyGuard = chatPolicyGuard
            ?: return unavailable("（对话能力未启用：chatPolicyGuard 未注入）")
        val taskId = chatSessionStore?.apply { startNewSession() }?.currentSessionId()
        val systemPrompt = buildChatSystemPrompt(ctx)

        // 画像叙事的维护（契约 v3.5 §7.4）：侧写指纹与落库叙事不一致即过期，
        // 异步重生成（不阻塞本轮对话；本轮用旧叙事或无叙事，下一轮换新）。
        // F9-T1: 无参版本，内部从 settingsRepo.getActiveAiConfig() 取 config。
        maybeRegenerateNarrative()

        HmpLog.i(LogTag.AgentMaster) { "🤖 handleUserMessage start (task=$taskId): input=${userMessage.take(119)}… history=${ctx.history.size} steps_budget=$stepBudget" }

        // ═══ per-Agent AgentPolicy：复用 MasterAgent 实例字段 masterPolicyConfig ═══
        // TrustLedger 每次新建但持有同一个 config 引用 → 用户确认累积的信任跨对话保留
        val trustLedger = TrustLedger(masterPolicyConfig, onChange = { persistMasterPolicyAsync() })
        val agentPolicy = AgentPolicy.master(
            config = masterPolicyConfig,
            trustLedger = trustLedger,
            confirmGate = confirmGate,
        )

        // ReActLoop 组合了 LlmCallExecutor + ToolCallExecutor + 熔断——
        // 循环编排从 MasterAgent 剥离为可复用组件（未来 Radio 第 3 轮 diff 仲裁也用）
        val loop = ReActLoop(
            stepBudget = stepBudget,
            tokenCounter = tokenCounter,
            policyGuard = policyGuard,
            auditLog = chatAuditLog,
            presenceBus = chatPresenceBus,
            stopSignal = AlwaysRunningStopSignal(tokenCounter),  // Master 永不暂停
            agentId = TokenMeter.AGENT_MASTER,
            tokenMeter = tokenMeter,
        )
        val result = loop.run(
            agentPolicy = agentPolicy,
            transport = transport,
            config = config,
            systemPrompt = systemPrompt,
            inputMessages = listOf(LlmMessage(role = "user", content = userMessage)),
            history = ctx.history,
            registry = registry,
            taskId = taskId,
            onSessionComplete = { persistMasterPolicy() },
        )
        // 对话结束 → 抽取音乐偏好写 T2_DIALOGUE（契约 §10.1「会话事件」行）。
        // 异步、非致命：抽取失败不影响本次对话结果；线索闸门不过则零 LLM 成本。
        maybeExtractDialogue(userMessage, transport, config, taskId?.toString())
        return result
    }

    /**
     * 画像叙事的过期检查与重生成 —— **suspend 核心**（契约 v3.5 §7.4）。
     *
     * MasterAgent 是这条维护链的发起方（它持有 transport 与 config）；画像内容的
     * 生成闸门与落库都在 [com.hmp.domain.agent.profile.UserMemory]。
     *
     * 闸门：profile.factsFingerprint 没变（叙事已经是最新）→ 直接 return，零 LLM 成本。
     * 失败：runCatching 吞掉，静默保留旧叙事（宁旧勿假）。
     *
     * 注意：是 **suspend 函数**，不自己 scope.launch —— 调用方已经在协程里时可以直接调，
     * 避免双重 launch（见 fire-and-forget 版本 [maybeRegenerateNarrative]）。
     */
    private suspend fun maybeRegenerateNarrativeSync() {
        val profile = userMemory ?: return
        val transport = chatTransport ?: return
        runCatching {
            val settings = settingsRepo ?: return@runCatching
            val config = settings.getActiveAiConfig()
            if (!config.isConfigured) return@runCatching
            val fingerprint = profile.factsFingerprint()
            val state = profile.narrativeState()
            if (state != null && state.factsHash == fingerprint) return@runCatching
            val facts = profile.factsRenderForNarrative() ?: return@runCatching
            val reply = LlmCallExecutor.call(
                transport = transport,
                config = config,
                messages = com.hmp.domain.agent.profile.ProfileNarrative.buildMessages(facts),
                tools = null,
                temperature = 0.4f,
                agentId = TokenMeter.AGENT_PROFILE,
                meter = tokenMeter,
            )
            if (!reply.failed) {
                profile.updateNarrative(reply.text, fingerprint)
                HmpLog.i(LogTag.AgentMaster) { "🤖 profile narrative regenerated (fingerprint updated)" }
            }
        }.onFailure { e ->
            HmpLog.w(LogTag.AgentMaster, e) { "🤖 narrative regeneration failed (non-fatal, keep old)" }
        }
    }

    /**
     * 画像叙事的 fire-and-forget 包装（给 handleUserMessage 末尾这种"不想阻塞对话"的场景用）。
     * 内部直接 [scope.launch] 一层，核心逻辑走 [maybeRegenerateNarrativeSync]。
     */
    private fun maybeRegenerateNarrative() {
        scope.launch { maybeRegenerateNarrativeSync() }
    }

    /** 对话结束后的偏好抽取（T0b）。全部 runCatching —— 画像失败不得拖垮对话。 */
    private fun maybeExtractDialogue(userMessage: String, transport: LlmTransport, config: AiEndpointConfig, sessionId: String?) {
        val profile = userMemory ?: return
        if (!com.hmp.domain.agent.profile.DialogueExtractor.shouldExtract(userMessage)) return
        scope.launch {
            runCatching {
                val reply = LlmCallExecutor.call(
                    transport = transport,
                    config = config,
                    messages = com.hmp.domain.agent.profile.DialogueExtractor.buildMessages(userMessage),
                    tools = null,
                    temperature = 0.2f,
                    agentId = TokenMeter.AGENT_PROFILE,
                    meter = tokenMeter,
                )
                if (!reply.failed) {
                    val extractions = com.hmp.domain.agent.profile.DialogueExtractor.parse(reply.text)
                    if (extractions.isNotEmpty()) {
                        profile.ingestDialogueEvidence(extractions, sessionId)
                        HmpLog.i(LogTag.AgentMaster) { "🤖 dialogue extraction: ${extractions.size} item(s) written" }
                    }
                }
            }.onFailure { e ->
                HmpLog.w(LogTag.AgentMaster, e) { "🤖 dialogue extraction failed (non-fatal)" }
            }
        }
    }

    // ═══ F9-T2 设置页公开接口 ═══════════════════════════════════════════
    //
    // 设计原则：
    // - 信任档位的 UI 操作必须修改 MasterAgent 内部持有的同一个 AgentPolicyConfig
    //   对象，否则下次 handleUserMessage 时 TrustLedger 读到的还是旧值
    // - 记忆相关操作委托给 userMemory（已 nullable 暴露）

    /** 读 Master Agent 当前信任档位（UI 展示用） */
    internal fun getMasterTrustLevel(): Int = masterPolicyConfig.trustLevel

    /**
     * 设置 Master Agent 信任档位（UI 手动调整用）。
     * 同时更新内部 config + 持久化到 DataStore，保证内外同步。
     */
    internal fun setMasterTrustLevel(target: Int) {
        val newLevel = target.coerceIn(TrustLevel.SUGGEST, TrustLevel.SILENT)
        if (masterPolicyConfig.trustLevel != newLevel) {
            masterPolicyConfig.trustLevel = newLevel
            persistMasterPolicyAsync()
            HmpLog.i(LogTag.AgentMaster) { "🤖 trustLevel set to $newLevel by settings UI" }
        }
    }

    /** 重置 Master Agent 的 alwaysAllow 工具白名单（设置页「清除所有总是允许」按钮） */
    internal fun resetMasterAlwaysAllow() {
        if (masterPolicyConfig.alwaysAllow.isNotEmpty()) {
            masterPolicyConfig.alwaysAllow.clear()
            persistMasterPolicyAsync()
            HmpLog.i(LogTag.AgentMaster) { "🤖 alwaysAllow reset by settings UI" }
        }
    }

    /** 当前 alwaysAllow 白名单快照（UI 显示用） */
    internal fun getMasterAlwaysAllow(): Set<String> = masterPolicyConfig.alwaysAllow.toSet()

    /**
     * 清除全部画像（设置页「关闭个性化」按钮）。
     * userMemory 可能为 null（DAO 未注入），此时静默跳过。
     */
    suspend fun clearAllMemory() {
        userMemory?.clear()
    }

    /**
     * 否决一条侧写（设置页「我不喜欢这样被理解」）。
     * @return true 成功，false 侧写类型 ID 无效或 userMemory 未注入
     */
    suspend fun forgetPortrait(typeId: String): Boolean {
        val mem = userMemory ?: return false
        return mem.forgetPortrait(typeId, sessionId = "settings")
    }

    /** 手动记录一条用户显式偏好（设置页「补充偏好」） */
    internal suspend fun notePreference(predicate: String, value: String): Boolean {
        val mem = userMemory ?: return false
        return mem.noteStatedPreference(predicate, value, sessionId = "settings")
    }

    // ═══ End F9-T2 ════════════════════════════════════════════════════

    /** SystemPrompt 组装（原 AgentOrchestrator.buildSystemPrompt + 旧 ContextBudget.assemble） */
    private fun buildChatSystemPrompt(ctx: RunContextInput): String {
        // 1. 从 L10N_PROMPTS + 用户覆盖解析基础 prompt
        val basePrompt = resolvePrompt(
            key = "chat.system",
            preferredLang = masterPolicyConfig.preferredLang,
            globalReplyLanguage = globalAgentConfig.replyLanguage,
            userOverrides = masterPolicyConfig.promptOverrides,
        )

        // 2. 组装动态区块（独立字符串，用于占位符替换）
        val personaBlock = buildString {
            append(ctx.personaText ?: DefaultCompanionProfiles.DEFAULT.personaPrompt)
            ctx.userTitle?.takeIf { it.isNotBlank() }?.let { append("\n称呼为「$it」。") }
            ctx.userProfileText?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it.trim()) }
            ctx.recognitionText?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it.trim()) }
            ctx.timeOfDayText?.takeIf { it.isNotBlank() }?.let { append("\n\n当前时段：$it") }
        }.trim()
        val libraryOverview = ctx.libraryOverviewText?.trim().orEmpty()
        val nowPlaying = ctx.nowPlayingText?.trim().orEmpty()
        val taskStatus = ctx.taskState?.trim().orEmpty()

        // 3. 替换占位符
        return basePrompt
            .replace("{{persona_block}}", personaBlock)
            .replace("{{library_overview}}", libraryOverview)
            .replace("{{now_playing}}", nowPlaying)
            .replace("{{task_status}}", taskStatus)
            .trim()
    }

}

/**
 * 默认系统条件（乐观假设：总是有 WiFi + 电量充足，Scheduler 不会 pause 任何 Agent）。
 * 实际平台层（Android/iOS/Desktop）会注入真实实现。
 */
internal class DefaultSystemConditions : SystemConditions {
    override fun batteryLevel(): Float = 1.0f
    override fun isWifiConnected(): Boolean = true
}
