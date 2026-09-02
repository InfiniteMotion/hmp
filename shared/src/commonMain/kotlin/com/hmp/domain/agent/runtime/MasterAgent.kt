package com.hmp.domain.agent.runtime

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.PresenceEvent
import com.hmp.domain.agent.infra.SessionStore
import com.hmp.domain.agent.policy.AgentPolicy
import com.hmp.domain.agent.policy.AgentPolicyConfig
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.policy.TrustLedger
import com.hmp.domain.agent.enrich.EnrichTask
import com.hmp.domain.agent.enrich.EnrichHealth
import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolCall
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.persona.DefaultCompanionProfiles
import com.hmp.domain.agent.sub.EnrichSubAgent
import com.hmp.domain.agent.sub.RadioSubAgent
import com.hmp.domain.agent.sub.SubAgent
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
class MasterAgent(
    /** 时间来源（毫秒） */
    private val timeProvider: () -> Long,
    /** 全局 Token 日配额计数器 */
    val tokenCounter: GlobalTokenCounter = GlobalTokenCounter(timeProvider),
    /** 系统条件提供者（电量/网络） */
    val systemConditions: SystemConditions = DefaultSystemConditions(),
    /** 音乐库仓库（富化健康度查询 + enrich 批次派发） */
    private val musicRepository: MusicRepository? = null,

    // ── 对话能力依赖（T 阶段整合，默认 null 表示不启用对话） ──
    /** 对话 LLM 传输实例 */
    private val chatTransport: LlmTransport? = null,
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
    /** 步数预算（硬熔断） */
    private val stepBudget: Int = EngineDefaults.STEP_BUDGET,

    // ── Enrich 后台能力依赖（默认 null；startEnrich 时可单独传入覆盖） ──
    /** EnrichSubAgent 的独立 LLM 传输实例（null 则退化用 chatTransport） */
    private val enrichTransport: LlmTransport? = null,
    /** Enrich LLM API 端点配置（null 表示开发模式跳过 LLM 调用） */
    private val enrichConfig: AiEndpointConfig? = null,

    // ── Radio 电台能力依赖（M6-T1；默认 null，startRadio 时需要非 null） ──
    /** 播放控制端口（电台续歌 SILENT） */
    private val playbackPort: com.hmp.domain.agent.port.PlaybackCommandPort? = null,
    /** 当前播放上下文提供者（电台种子自动提取） */
    private val nowPlayingProvider: com.hmp.domain.agent.port.NowPlayingContextProvider? = null,

    // ── Agent 配置持久化（可选；null 则 config 只在 MasterAgent 实例存活期间有效） ──
    /** AgentPolicyConfig DataStore 读写（per-Agent 信任档位 + 永远允许白名单） */
    private val settingsRepo: com.hmp.domain.setting.SettingsRepository? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ═══ M6-T2/M6-T3：Radio 事件监听状态 ═══
    /** 连续跳过计数（MasterAgent skip event 监听协程维护） */
    @Volatile private var consecutiveSkipCount: Int = 0
    /** 标记 Radio 事件监听是否已启动（避免多次 startRadio 重复 launch） */
    @Volatile private var radioListenersStarted: Boolean = false
    /** 门面问候轮换索引（M6-T3c LLM 不可用时轮退） */
    private var greetingIndex: Int = 0

    /** 全局唯一调度器（F3） */
    val scheduler = AgentScheduler(timeProvider, tokenCounter, systemConditions)

    // ═══ per-Agent 持久化配置（AgentPolicyConfig：2 字段 trustLevel + alwaysAllow）═══
    // init 块里从 DataStore 读（runBlocking，因为 init 块不是 suspend）；
    // 没有 settingsRepo 就用默认值（首次启动）
    /** Master 对话 Agent 的信任配置 */
    private val masterPolicyConfig: AgentPolicyConfig
    /** Enrich 后台 Agent 的信任配置 */
    private val enrichPolicyConfig: AgentPolicyConfig

    init {
        // 从 DataStore 读 Master 配置（没有则默认 SUGGEST + 空白名单）
        masterPolicyConfig = runBlocking {
            settingsRepo?.getAgentPolicyConfig("master") ?: AgentPolicyConfig()
        }
        // 从 DataStore 读 Enrich 配置（没有则默认 SILENT + alwaysAllow 全部自身可见工具）
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
    }

    // ── 持久化辅助方法（桥接 TrustLedger.onChange 回调到 DataStore suspend 写入）─
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

    /** SubAgent 注册表（F1：Master 持有，子Agent 不能自注册）。
     *
     * 预留键位：
     * - "enrich" → EnrichSubAgent（自循环 Worker）
     * - "radio"  → RadioSubAgent（自循环 Worker，M6）
     */
    private val _subAgents = mutableMapOf<String, com.hmp.domain.agent.sub.SubAgent>()
    val subAgents: Map<String, com.hmp.domain.agent.sub.SubAgent> get() = _subAgents.toMap()

    // ===== 生命周期 =====

    /**
     * Master 初始化（应用启动时调用）：
     * ① 启动 Scheduler 仲裁循环
     * ② 注册自己（priority=1，永不暂停）
     * ③ 检测富化健康度 → 决定是否创建 Enrich
     */
    suspend fun initialize(enrichTransport: LlmTransport? = null) {
        Logger.i("Agent.Master") { "[Master] initialize start" }

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
        Logger.i("Agent.Master") { "[Master] registered with Scheduler priority=1" }

        // ③ 检测富化健康度 → 决定是否创建 Enrich
        musicRepository?.let { repo ->
            val health = repo.getEnrichHealth()
            Logger.i("Agent.Master") { "[Master] enrich health: ${health.enrichedSongCount}/${health.totalSongCount} coverage=${health.coverageRate} lowConf=${health.lowConfidenceCount}" }

            val defaultTarget = 0.9f // 默认 90% 覆盖率
            if (health.coverageRate < defaultTarget) {
                Logger.i("Agent.Master") { "[Master] coverage ${health.coverageRate} < target $defaultTarget -> creating Enrich" }
                startEnrich(EnrichTask(targetCoverage = defaultTarget), enrichTransport)
            } else {
                Logger.i("Agent.Master") { "[Master] coverage ${health.coverageRate} >= target $defaultTarget -> skip Enrich" }
            }
        } ?: Logger.w("Agent.Master") { "[Master] MusicRepository null, skipping enrich health check" }
    }

    /** 应用销毁时调用，清理所有 SubAgent */
    suspend fun shutdown() {
        Logger.i("Agent.Master") { "[Master] shutdown" }
        scheduler.stopArbitration()
        _subAgents.values.forEach { it.shutdown() }
        _subAgents.clear()
    }

    // ===== Enrich 管理（F1：Master 唯一决策） =====

    /**
     * 创建并启动 EnrichSubAgent（F1：只有 Master 能下令）。
     *
     * EnrichSubAgent 是自循环 Worker——内部拉活/处理/验收/重试全自己管，
     * Master 只负责外部生命周期：创建/注册 Scheduler/启动 runLoop。
     *
     * 完整链路：
     * 1. 创建 AgentContextBudget(32K) + SchedulerStopSignal
     * 2. 实例化 EnrichSubAgent → 放进 _subAgents["enrich"]
     * 3. 注册 Scheduler priority=3
     * 4. scope.launch { enrichAgent.runLoop() } —— 只启动一个协程
     *
     * 幂等：如果已有 inactive 实例，先 stopEnrich() 清理再重建。
     *
     * @param task 任务单（含 targetCoverage / maxBatchSize）
     */
    suspend fun startEnrich(task: EnrichTask, enrichTransport: LlmTransport? = null) {
        // 幂等清理：已有 inactive 实例 → 先清理
        _subAgents["enrich"]?.let { existing ->
            if (existing !is com.hmp.domain.agent.sub.EnrichSubAgent) {
                Logger.w("Agent.Master") { "[Master] existing enrich is not EnrichSubAgent, force cleanup" }
                stopEnrich()
            } else {
                // 活跃中 → 跳过
                Logger.w("Agent.Master") { "[Master] Enrich already active, skip create" }
                return
            }
        }

        val repo = musicRepository
        val registry = chatToolRegistry
        if (repo == null || registry == null) {
            Logger.e("Agent.Master") { "[Master] Cannot start Enrich: musicRepository=${repo != null} chatToolRegistry=${registry != null}" }
            return
        }

        // 优先用传入的 enrichTransport，退化到构造函数的 enrichTransport，最后退化到 chatTransport
        val effectiveEnrichTransport = enrichTransport ?: this.enrichTransport ?: chatTransport
        if (effectiveEnrichTransport == null) {
            Logger.e("Agent.Master") { "[Master] No LlmTransport available for Enrich; aborting startEnrich" }
            return
        }

        // ① 创建 Enrich 的独立 AgentContextBudget（32K）
        val contextBudget = AgentContextBudget(
            agentId = "enrich",
            maxContextTokens = 32_000,
            llmClient = effectiveEnrichTransport,
        )

        // ② 创建 SchedulerStopSignal——桥接 Scheduler pause/resume ↔ Enrich runLoop 的 waitResume()
        val enrichStopSignal = SchedulerStopSignal(tokenCounter)

        // ③ 构造 system prompt（F5：Master 注入，Enrich 不自演化角色）
        val systemPrompt = com.hmp.domain.agent.sub.EnrichSubAgent.buildSystemPrompt(
            task.targetCoverage,
        )

        // ④ 构造 ToolRegistryView（基类 SubAgent 需要；Enrich 自循环不用 tools，但 F2 铁则保留）
        val toolView = ToolRegistryView.enrich(registry)

        // ⑤ 实例化 EnrichSubAgent（自循环 Worker，管道完全内化——prompt 和 LLM 调用都在 Enrich 内部）
        val enrichAgent = com.hmp.domain.agent.sub.EnrichSubAgent(
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

        // ⑦ 启动 runLoop（Enrich 自管理内部循环，不需要 Master enrichTaskLoop）
        scope.launch { enrichAgent.runLoop() }

        Logger.i("Agent.Master") { "[Master] EnrichSubAgent created (batch=${task.maxBatchSize}, targetCoverage=${task.targetCoverage}, config=${enrichConfig != null})" }
    }

    /** Master 下令销毁 Enrich（F1：只有 Master 能下令） */
    suspend fun stopEnrich() {
        _subAgents["enrich"]?.let { enrich ->
            enrich.shutdown()
            scheduler.unregisterAgent("enrich")
            _subAgents.remove("enrich")
            Logger.i("Agent.Master") { "[Master] Enrich stopped" }
        }
    }

    // ===== Radio 电台管理（M6-T1 · F1：Master 唯一决策） =====

    /**
     * 创建并启动 RadioSubAgent（F1：只有 Master 能调）。
     *
     * 链路（照着 startEnrich 模式）：
     * 1. 创建 AgentContextBudget(64K) + ToolRegistryView.radio()
     * 2. 实例化 RadioSubAgent → 放进 _subAgents["radio"]
     * 3. 注册 Scheduler priority=2（桥接 StopSignal）
     * 4. 启动 radioAgent.runLoop()
     * 5. 立即调 startRadio(seed) 跑第一轮协作 → 返回本地保底队列
     *
     * @param seed 用户种子（null = 自动从 nowPlaying 提取）
     * @return 本地保底队列（立即返回，零等待开听）；Radio 未创建成功时返回 emptyList()
     */
    suspend fun startRadio(seed: String? = null): List<com.hmp.domain.agent.sub.RadioTrack> {
        if (_subAgents.containsKey("radio")) {
            Logger.w("Agent.Master") { "[Master] Radio already exists, delegating to existing" }
            val radio = _subAgents["radio"] as? com.hmp.domain.agent.sub.RadioSubAgent
            return radio?.startRadio(seed) ?: emptyList()
        }

        val repo = musicRepository
        val registry = chatToolRegistry
        val playback = playbackPort
        val nowPlaying = nowPlayingProvider
        if (repo == null || registry == null || playback == null || nowPlaying == null) {
            Logger.e("Agent.Master") { "[Master] Cannot start Radio: deps missing repo=${repo != null} registry=${registry != null} playback=${playback != null} nowPlaying=${nowPlaying != null}" }
            return emptyList()
        }

        // ① AgentContextBudget(64K)——电台决策比 Enrich 复杂但比 Master 对话轻
        val radioTransport = chatTransport  // 电台复用 chatTransport（可后续独立出来）
        if (radioTransport == null) {
            Logger.e("Agent.Master") { "[Master] No LlmTransport available for Radio; aborting startRadio" }
            return emptyList()
        }
        val contextBudget = AgentContextBudget(
            agentId = "radio",
            maxContextTokens = 64_000,
            llmClient = radioTransport,
        )

        // ② 权限过滤视图：Radio 可碰所有工具（MASTER 级，因为电台是 Master 发起的）
        val toolView = ToolRegistryView.radio(registry)

        // ③ StopSignal——桥接 Scheduler pause/resume
        val radioStopSignal = SchedulerStopSignal(tokenCounter)

        // ④ 实例化 RadioSubAgent
        val radioAgent = com.hmp.domain.agent.sub.RadioSubAgent(
            agentId = "radio",
            contextBudget = contextBudget,
            toolRegistryView = toolView,
            toolRegistry = registry,
            musicRepository = repo,
            playbackPort = playback,
            nowPlayingProvider = nowPlaying,
            presenceBus = chatPresenceBus,
            auditLog = chatAuditLog,
            radioConfig = enrichConfig,  // 暂复用 enrichConfig（同端点），后续可独立
            targetCount = 12,
            stopSignal = radioStopSignal,
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

        // ⑥ 启动 runLoop（后台协程）
        scope.launch { radioAgent.runLoop() }

        // ⑥-2 启动 Radio 事件监听协程（M6-T2 skip 感知 + M6-T3 DjBlank → 衔接语）
        setupRadioEventListeners()

        // ⑦ 立即调 startRadio → 本地保底队列（同步返回给 ChatAgentGateway 渲染 songlist 卡）
        val tracks = radioAgent.startRadio(seed)
        Logger.i("Agent.Master") { "[Master] RadioSubAgent created + started (targetCount=12, tracks=${tracks.size})" }
        return tracks
    }

    /** Master 下令停电台 */
    suspend fun stopRadio() {
        _subAgents["radio"]?.let { radio ->
            (radio as? com.hmp.domain.agent.sub.RadioSubAgent)?.stopRadio()
            radio.shutdown()
            scheduler.unregisterAgent("radio")
            _subAgents.remove("radio")
            Logger.i("Agent.Master") { "[Master] Radio stopped" }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // M6-T2 跳过感知重排 + M6-T3 DJ 衔接预生成
    // ═══════════════════════════════════════════════════════════════

    /** 门面问候轮换列表（M6-T3c：LLM 不可用时退化为硬编码轮换） */
    private val fallbackGreetings = listOf(
        "下一首也不错哦～",
        "继续享受音乐吧！",
        "听听这首怎么样？",
        "为你选了一首好歌",
        "这首特别适合此刻",
    )

    /**
     * 启动 Radio 事件监听协程：skip events + DjBlank events。
     * 幂等（radioListenersStarted 守卫），避免多次 startRadio 重复 launch。
     */
    private fun setupRadioEventListeners() {
        if (radioListenersStarted) return
        radioListenersStarted = true

        // ── M6-T2a：skip 事件监听 ──
        val port = playbackPort
        val presence = chatPresenceBus
        if (port != null && presence != null) {
            scope.launch {
                Logger.i("Agent.Master") { "[Master] skipEvents listener started" }
                port.skipEvents.collectLatest { skippedTitle ->
                    consecutiveSkipCount++
                    Logger.i("Agent.Master") { "[Master] skip detected: '$skippedTitle' consecutive=$consecutiveSkipCount" }

                    // 通知 PresenceBus
                    presence.emit(PresenceEvent.SkipDetected(consecutiveSkipCount, skippedTitle))

                    if (consecutiveSkipCount >= 2) {
                        // 连跳 2+ 首 → 重排
                        Logger.i("Agent.Master") { "[Master] consecutive skip threshold reached (≥2) → reorder" }
                        chatAuditLog?.logSkipReorder(consecutiveSkipCount, skippedTitle)
                        consecutiveSkipCount = 0  // 重置，重排完成后再累积
                        val radio = _subAgents["radio"] as? RadioSubAgent
                        if (radio != null) {
                            runCatching { radio.reorder(emptyList()) }
                                .onFailure { e -> Logger.e("Agent.Master", e) { "reorder failed" } }
                        }
                    }
                }
            }
        }

        // ── M6-T3：DjBlank 事件监听 → LLM 生成衔接语 / 门面问候轮换 ──
        if (port != null && presence != null) {
            scope.launch {
                Logger.i("Agent.Master") { "[Master] DjBlank listener started (trackChangeEvents)" }
                port.trackChangeEvents.collectLatest { newTitle ->
                    Logger.i("Agent.Master") { "[Master] trackChange → emit DjBlank for \"$newTitle\"" }
                    presence.emit(PresenceEvent.DjBlank)
                    handleDjBlank()
                }
            }
        }
    }

    /**
     * M6-T3b：处理 DjBlank → 生成衔接语（LLM 可用）或门面问候（LLM 不可用）。
     * 结果 emit PresenceEvent.NoticeAvailable → UI 侧条展示 4s。
     */
    private suspend fun handleDjBlank() {
        val presence = chatPresenceBus ?: return
        val (greeting, source) = runCatching { generateDjSegue() }.fold(
            onSuccess = { it to "llm" },
            onFailure = { e ->
                Logger.w("Agent.Master", e) { "DjBlank LLM failed, using fallback greeting" }
                nextFallbackGreeting() to "fallback"
            }
        )
        chatAuditLog?.logDjSegue(greeting, source)
        Logger.i("Agent.Master") { "[Master] DjBlank → emit NoticeAvailable: '$greeting'" }
        presence.emit(PresenceEvent.NoticeAvailable(greeting))
    }

    /**
     * M6-T3b：LLM 纯文本调用生成 15-20 字中文衔接语。
     * 无 radioConfig / LLM 调用失败时抛异常 → handleDjBlank 降级到 fallback。
     */
    private suspend fun generateDjSegue(): String {
        val transport = chatTransport ?: error("no LLM transport")
        val config = enrichConfig ?: error("no LLM config")

        val systemPrompt = "你是音乐电台的温和 DJ。每次切歌时说一句简短自然的中文衔接语，15-20 字。" +
            "例如：「接下来这首是来自周杰伦的晴天」「换个风格，这首比较安静」。不要说多余的。"

        val turn = LlmCallExecutor().call(
            transport = transport,
            config = config,
            messages = listOf(
                LlmMessage(role = "system", content = systemPrompt),
                LlmMessage(role = "user", content = "现在请说一句衔接语，15-20 字。"),
            ),
            tools = null,
            temperature = 0.7f,
        )

        if (turn.failed) error(turn.failedMessage ?: "LLM failed")
        val text = turn.text.trim()
        if (text.isBlank()) error("empty LLM response")
        // 截取合理长度（避免 LLM 吐太长）
        return text.take(40)
    }

    /** M6-T3c：门面问候轮换。 */
    private fun nextFallbackGreeting(): String {
        val greetings = fallbackGreetings
        val g = greetings[greetingIndex % greetings.size]
        greetingIndex++
        return g
    }

    /** 查询电台状态（供 MasterChatGateway/ChatViewModel 轮询） */
    fun queryRadioState(): com.hmp.domain.agent.sub.RadioState? =
        (_subAgents["radio"] as? com.hmp.domain.agent.sub.RadioSubAgent)?.queryState()

    fun queryRadioPlaylist(): List<com.hmp.domain.agent.sub.RadioTrack>? =
        (_subAgents["radio"] as? com.hmp.domain.agent.sub.RadioSubAgent)?.queryPlaylist()

    // ═══ SubAgent 状态查询 & 原生生命周期方法 ═══

    /** 当前 SubAgent 状态查询（同步返回）。 */
    fun querySubAgents(): Map<String, String> {
        return _subAgents.mapValues { (_, agent) -> agent.state().name } +
            mapOf(
                "scheduler_state" to scheduler.registeredAgentIds().joinToString(","),
                "token_usage" to "${tokenCounter.usedToday()}/${tokenCounter.dailyTokenQuota}",
            )
    }

    /** 富化进程是否活跃（已启动未 shutdown）。 */
    fun isEnrichActive(): Boolean {
        val enrich = _subAgents["enrich"] as? com.hmp.domain.agent.sub.EnrichSubAgent ?: return false
        return enrich.state() == com.hmp.domain.agent.runtime.AgentRunState.RUNNING
    }

    /** 富化当前状态的摘要（覆盖率、Scheduler 状态、Token 剩余、进度）。 */
    suspend fun enrichStatusSummary(): Map<String, String> {
        val health = musicRepository?.getEnrichHealth()
        val schedulerEnrichState = scheduler.getState("enrich").name
        val enrich = _subAgents["enrich"] as? com.hmp.domain.agent.sub.EnrichSubAgent
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
        val task = EnrichTask(targetCoverage = targetCoverage ?: 0.9f)
        startEnrich(task)
    }

    /** 暂停富化进程（Scheduler pause，进程保活但不处理新批次）。 */
    suspend fun pauseEnrich() {
        _subAgents["enrich"]?.pause()
        Logger.i("Agent.Master") { "[Master] enrich paused" }
    }

    /** 恢复富化进程。 */
    suspend fun resumeEnrich() {
        _subAgents["enrich"]?.resume()
        Logger.i("Agent.Master") { "[Master] enrich resumed" }
    }

    /** 重新扫描未覆盖歌曲并重置覆盖率目标。 */
    suspend fun rescanEnrich(newTarget: Float?) {
        if (!isEnrichActive()) {
            // 没在跑 → 直接 startEnrich
            startEnrich(newTarget)
            return
        }
        // 已在跑 → 更新 EnrichSubAgent 内部 targetCoverage（runLoop 下轮生效）
        val enrich = _subAgents["enrich"] as? com.hmp.domain.agent.sub.EnrichSubAgent
        val target = newTarget ?: 0.9f
        enrich?.updateTarget(target)
        Logger.i("Agent.Master") { "[Master] enrich rescan triggered, new target=$target" }
    }

    // ═══ 内建意图路由（SubAgent 生命周期原生暴露，不经过工具层）═══

    /**
     * 确定性意图匹配——MasterAgent 作为唯一大脑，直接识别"开电台/停电台/暂停富化"等意图，
     * 跳过 LLM ReActLoop，直接调 SubAgent 原生生命周期方法。
     *
     * 返回 null 表示未命中，由调用方继续走正常 LLM 对话。
     */
    private suspend fun builtinIntent(input: String): AgentResult? {
        val lower = input.trim().lowercase()

        // ── 电台停止 ──
        val stopRadioTriggers = listOf("停电台", "关电台", "停止电台", "stop radio", "stop radio", "停止播放电台")
        if (stopRadioTriggers.any { lower.contains(it) } || (lower == "停" && isRadioActive())) {
            stopRadio()
            Logger.i("Agent.Master") { "[Master] builtin: stopRadio" }
            return AgentResult(
                text = "电台已停止。",
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.ANSWERED,
                intentHandled = "radio_stop",
            )
        }

        // ── 电台启动 ──
        if (isRadioIntent(lower)) {
            val seed = extractSeed(input)
            val tracks = runCatching { startRadio(seed) }
                .onFailure { Logger.e("Agent.Master", it) { "[Master] builtin: startRadio failed" } }
                .getOrNull()
            val summary = if (tracks != null && tracks.isNotEmpty()) {
                Logger.i("Agent.Master") { "[Master] builtin: startRadio seed=\"$seed\" tracks=${tracks.size}" }
                "电台启动了${if (!seed.isNullOrBlank()) "，种子「$seed」" else ""}，为你选了 ${tracks.size} 首。"
            } else {
                Logger.w("Agent.Master") { "[Master] builtin: startRadio returned empty for seed=\"$seed\"" }
                "电台没有找到足够的曲目，换个描述试试？"
            }
            return AgentResult(
                text = summary,
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.ANSWERED,
                intentHandled = "radio_start",
            )
        }

        // ── 富化启动 ──
        val startTriggers = listOf("开始富化", "启动富化", "开始识别", "启动识别", "start enrich", "run enrich", "富化一下")
        if (startTriggers.any { lower.contains(it) }) {
            if (isEnrichActive()) {
                return AgentResult(
                    text = "富化已经在跑了，说「重扫」可以重置覆盖率目标。",
                    stepsUsed = 0, toolCalls = emptyList(),
                    terminatedBy = TerminationReason.ANSWERED,
                    intentHandled = "enrich_start",
                )
            }
            startEnrich(null)
            Logger.i("Agent.Master") { "[Master] builtin: startEnrich" }
            return AgentResult(
                text = "好的，富化已启动。后台自动扫描未覆盖歌曲补充标签，说「富化状态」可以看进度。",
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.ANSWERED,
                intentHandled = "enrich_start",
            )
        }

        // ── 富化停止（完全移除，区别于 pause 的调度暂停）──
        val stopTriggers = listOf("停止富化", "关掉富化", "stop enrich", "关闭富化")
        if (stopTriggers.any { lower.contains(it) }) {
            if (!isEnrichActive()) {
                return AgentResult(
                    text = "富化没在跑，无需停止。",
                    stepsUsed = 0, toolCalls = emptyList(),
                    terminatedBy = TerminationReason.ANSWERED,
                    intentHandled = "enrich_stop",
                )
            }
            stopEnrich()
            Logger.i("Agent.Master") { "[Master] builtin: stopEnrich" }
            return AgentResult(
                text = "富化已停止。",
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.ANSWERED,
                intentHandled = "enrich_stop",
            )
        }

        // ── 富化暂停 ──
        val pauseTriggers = listOf("暂停富化", "暂停识别", "pause enrich", "停一下富化")
        if (pauseTriggers.any { lower.contains(it) }) {
            if (!isEnrichActive()) {
                return AgentResult(
                    text = "富化没在跑，不用暂停。",
                    stepsUsed = 0, toolCalls = emptyList(),
                    terminatedBy = TerminationReason.ANSWERED,
                    intentHandled = "enrich_pause",
                )
            }
            pauseEnrich()
            Logger.i("Agent.Master") { "[Master] builtin: pauseEnrich" }
            return AgentResult(
                text = "好的，富化已暂停。",
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.ANSWERED,
                intentHandled = "enrich_pause",
            )
        }

        // ── 富化恢复 ──
        val resumeTriggers = listOf("恢复富化", "继续富化", "恢复识别", "resume enrich", "继续识别")
        if (resumeTriggers.any { lower.contains(it) }) {
            if (!isEnrichActive()) {
                return AgentResult(
                    text = "富化没在跑，直接说「开始富化」就行。",
                    stepsUsed = 0, toolCalls = emptyList(),
                    terminatedBy = TerminationReason.ANSWERED,
                    intentHandled = "enrich_resume",
                )
            }
            resumeEnrich()
            Logger.i("Agent.Master") { "[Master] builtin: resumeEnrich" }
            return AgentResult(
                text = "好的，富化继续。",
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.ANSWERED,
                intentHandled = "enrich_resume",
            )
        }

        // ── 富化重扫 ──
        val rescanTriggers = listOf("重扫", "重新富化", "重扫未覆盖", "rescan enrich", "重置富化", "重新识别")
        if (rescanTriggers.any { lower.contains(it) }) {
            rescanEnrich(null)
            Logger.i("Agent.Master") { "[Master] builtin: rescanEnrich" }
            return AgentResult(
                text = "好的，正在重新扫描未覆盖的歌曲。",
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.ANSWERED,
                intentHandled = "enrich_rescan",
            )
        }

        // ── 富化状态查询 ──
        val statusTriggers = listOf("富化状态", "识别进度", "enrich status", "富化进度", "enrich状态")
        if (statusTriggers.any { lower.contains(it) }) {
            val summary = enrichStatusSummary()
            val text = if (summary["active"] == "true") {
                "富化正在运行中· ${summary["coverage_rate"] ?: "?"}% 覆盖· 配额剩余 ${summary["token_remaining"] ?: "?"}。"
            } else {
                "富化未启动，说「开始富化」就能开起来。"
            }
            return AgentResult(
                text = text,
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.ANSWERED,
                intentHandled = "enrich_status",
            )
        }

        // ── 未命中 → 交给 LLM ──
        return null
    }

    /** 电台意图判定（确定性关键词匹配，Gateway.extractRadioSeed 搬入 MasterAgent）。 */
    private fun isRadioIntent(lower: String): Boolean {
        val strongTriggers = listOf("电台", "radio", "dj", "打碟")
        val weakTriggers = listOf("来一段", "来一首", "开个", "开个电台", "放个", "放一首", "整个", "来个", "放段", "来点")
        val styleHints = listOf(
            "摇滚", "爵士", "古典", "民谣", "电子", "流行", "嘻哈", "rnb", "蓝调", "金属",
            "朋克", "雷鬼", "乡村", "灵魂", "放克", "说唱", "edm", "house", "techno",
            "迪斯科", "后摇", "日摇", "韩流", "轻音乐", "纯音乐", "钢琴曲",
            "深夜", "夜晚", "深夜电台", "工作", "学习", "专注", "放松", "运动", "跑步",
            "开车", "通勤", "咖啡馆", "雨天", "清晨", "早晨", "下午茶", "助眠", "睡觉",
            "情歌", "伤感", "治愈", "欢快", "激情", "安静", "冥想",
            "rock", "jazz", "classical", "folk", "pop", "hiphop", "hip-hop", "blues",
            "metal", "punk", "reggae", "soul", "funk", "rap", "study", "focus", "chill",
            "workout", "sleep", "relax", "love songs", "instrumental",
        )
        val hasStrong = strongTriggers.any { lower.contains(it) }
        val hasWeakWithStyle = weakTriggers.any { lower.contains(it) } && styleHints.any { lower.contains(it.lowercase()) }
        return hasStrong || hasWeakWithStyle
    }

    /** 从电台意图输入中提取种子（风格词优先，空串=自动从 nowPlaying 提取）。 */
    private fun extractSeed(input: String): String? {
        val lower = input.trim().lowercase()
        val strongTriggers = listOf("电台", "radio", "dj", "打碟")
        val weakTriggers = listOf("来一段", "来一首", "开个", "开个电台", "放个", "放一首", "整个", "来个", "放段", "来点")
        val styleHints = listOf(
            "摇滚", "爵士", "古典", "民谣", "电子", "流行", "嘻哈", "rnb", "蓝调", "金属",
            "朋克", "雷鬼", "乡村", "灵魂", "放克", "说唱", "edm", "house", "techno",
            "迪斯科", "后摇", "日摇", "韩流", "轻音乐", "纯音乐", "钢琴曲",
            "深夜", "夜晚", "深夜电台", "工作", "学习", "专注", "放松", "运动", "跑步",
            "开车", "通勤", "咖啡馆", "雨天", "清晨", "早晨", "下午茶", "助眠", "睡觉",
            "情歌", "伤感", "治愈", "欢快", "激情", "安静", "冥想",
            "rock", "jazz", "classical", "folk", "pop", "hiphop", "hip-hop", "blues",
            "metal", "punk", "reggae", "soul", "funk", "rap", "study", "focus", "chill",
            "workout", "sleep", "relax", "love songs", "instrumental",
        )
        val styleInInput = styleHints.firstOrNull { input.contains(it, ignoreCase = true) }
        if (styleInInput != null) return styleInInput

        var seed = input.trim()
        (strongTriggers + weakTriggers).forEach { t -> seed = seed.replace(t, "", ignoreCase = true) }
        styleHints.forEach { s -> seed = seed.replace(s, "", ignoreCase = true) }
        seed = seed.trim().trim('，', '。', '？', '!', '！', '?', '、', ' ', '　')

        return when {
            seed.isNotBlank() -> seed
            isRadioIntent(lower) -> ""  // 强触发词但无种子 → 自动提取
            else -> null
        }
    }

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
        // ═══ 内建意图路由（MasterAgent 作为唯一大脑，先做确定性意图匹配）═══
        // 命中则直接调 SubAgent 原生生命周期方法返回，不走 LLM ReActLoop。
        // Gateway 看到 intentHandled 字段决定 UI 层渲染（如电台 songlist bubble）。
        builtinIntent(userMessage)?.let { return it }

        val transport = chatTransport
            ?: return AgentResult(
                text = "（对话能力未启用：chatTransport 未注入）",
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.FAILED,
            )
        val registry = chatToolRegistry
            ?: return AgentResult(
                text = "（对话能力未启用：chatToolRegistry 未注入）",
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.FAILED,
            )
        val policyGuard = chatPolicyGuard
            ?: return AgentResult(
                text = "（对话能力未启用：chatPolicyGuard 未注入）",
                stepsUsed = 0, toolCalls = emptyList(),
                terminatedBy = TerminationReason.FAILED,
            )
        val taskId = chatSessionStore?.apply { startNewSession() }?.currentSessionId()
        val systemPrompt = buildChatSystemPrompt(ctx)

        Logger.i("Agent.Master") { "handleUserMessage start (task=$taskId): input=${userMessage.take(119)}… history=${ctx.history.size} steps_budget=$stepBudget" }

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
        )
        return loop.run(
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
    }

    /** SystemPrompt 组装（原 AgentOrchestrator.buildSystemPrompt + 旧 ContextBudget.assemble） */
    private fun buildChatSystemPrompt(ctx: RunContextInput): String {
        val firstTurn = ContextAssembler.assembleFirstTurnBlock(
            personaText = ctx.personaText ?: DefaultCompanionProfiles.DEFAULT.personaPrompt,
            libraryOverview = ctx.libraryOverviewText,
            recognition = ctx.recognitionText,
            timeOfDay = ctx.timeOfDayText,
            nowPlaying = ctx.nowPlayingText,
            userTitle = ctx.userTitle,
        )
        return buildString {
            append(firstTurn.trim())
            append("\n\n可调用工具来检索曲库、管理歌单或控制播放。用中文简洁回应。")
            ctx.taskState?.let { append("\n【当前任务】\n").append(it) }
        }
    }

}

/**
 * 默认系统条件（乐观假设：总是有 WiFi + 电量充足，Scheduler 不会 pause 任何 Agent）。
 * 实际平台层（Android/iOS/Desktop）会注入真实实现。
 */
class DefaultSystemConditions : SystemConditions {
    override fun batteryLevel(): Float = 1.0f
    override fun isWifiConnected(): Boolean = true
}
