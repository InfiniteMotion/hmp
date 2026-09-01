package com.hmp.domain.agent.runtime

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.infra.PresenceBus
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
import com.hmp.domain.agent.sub.SubAgent
import com.hmp.domain.agent.tool.MasterAgentFacade
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * Master Agent —— 唯一大脑（设计铁则 F1）。
 *
 * 职责：
 * ① **对话能力**（面向用户）：handleUserMessage() —— 原 AgentOrchestrator.run() 循环，
 *    多轮 LLM 对话 + tool_result 回传 + PolicyGuard 许可门 + ConfirmGate 批量确认 + 审计。
 *    ToolRegistry 全 32 工具（27 基础 + 5 enrich_* ← chatTransport/chatToolRegistry 注入时自动生效）。
 * ② **后台管理**（面向 SubAgent）：enrichTaskLoop（轻量协程，不用 LLM） + enrich_* 工具 +
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

    // ── Agent 配置持久化（可选；null 则 config 只在 MasterAgent 实例存活期间有效） ──
    /** AgentPolicyConfig DataStore 读写（per-Agent 信任档位 + 永远允许白名单） */
    private val settingsRepo: com.hmp.domain.setting.SettingsRepository? = null,
) : MasterAgentFacade {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // MasterAgent 构造时自动注册 enrich_* 专属工具（打破 Koin 循环依赖：
    // ToolRegistry.create() 只注册 27 基础工具，enrich_* 由 Master 自己补）
    init {
        chatToolRegistry?.let { registry ->
            registry.register(
                com.hmp.domain.agent.tool.EnrichStartTool(this),
                com.hmp.domain.agent.tool.EnrichPauseTool(this),
                com.hmp.domain.agent.tool.EnrichResumeTool(this),
                com.hmp.domain.agent.tool.EnrichStatusTool(this),
                com.hmp.domain.agent.tool.EnrichRescanTool(this),
            )
            Logger.i("Agent.Master") { "[Master] enriched chat ToolRegistry with 5 enrich_* tools (total=${registry.all().size})" }
        }
    }

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
                        "song_tags_get", "song_enrich_llm", "song_tag_user_add", "song_tag_user_remove",
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
     * - "enrich" → EnrichSubAgent（T 阶段完整实现）
     * - "radio"  → RadioSubAgent（**预留给 M6 实现**；T 阶段只占位，Scheduler priority=2 已预留）
     */
    private val _subAgents = mutableMapOf<String, com.hmp.domain.agent.sub.SubAgent>()
    val subAgents: Map<String, com.hmp.domain.agent.sub.SubAgent> get() = _subAgents.toMap()

    /** Enrich 当前任务单（null = 无富化任务在跑）。
     * @Volatile：enrichTaskLoop 与 rescanEnrich 分属不同协程，保证 targetCoverage 更新可见 */
    @Volatile
    private var enrichTask: EnrichTask? = null

    /** 歌曲富化失败重派计数（songId → 已重派次数；超过 [MAX_ENRICH_RETRIES] 次不再重派） */
    private val enrichRetryCounts = mutableMapOf<Long, Int>()

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
     * 创建并启动 EnrichSubAgent（F1：只有 Master 能调）。
     *
     * 完整链路：
     * 1. 创建 AgentContextBudget(32K) + ToolRegistryView.enrich()（共用 chatToolRegistry 底层实例）
     * 2. 实例化 EnrichSubAgent → 放进 _subAgents["enrich"]
     * 3. 注册 Scheduler priority=3（onPause/onResume 桥接到 enrich.suspendCoroutine/resumeCoroutine）
     * 4. 启动 enrichAgent.runLoop() + enrichTaskLoop()
     *
     * @param enrichTransport 可选覆盖构造函数的 enrichTransport（initialize() 调用时传参用）
     */
    suspend fun startEnrich(task: EnrichTask, enrichTransport: LlmTransport? = null) {
        if (_subAgents.containsKey("enrich")) {
            Logger.w("Agent.Master") { "[Master] Enrich already exists, skip create" }
            return
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

        this.enrichTask = task

        // ① 创建 Enrich 的独立 AgentContextBudget（32K，批量模型省 token）
        val contextBudget = AgentContextBudget(
            agentId = "enrich",
            maxContextTokens = 32_000,
            llmClient = effectiveEnrichTransport,
        )

        // ② 权限过滤视图：Enrich 只能 library_* + song_*
        val toolView = ToolRegistryView.enrich(registry)

        // ③ 构造 system prompt（F5：Master 注入，Enrich 不自演化）
        val systemPrompt = com.hmp.domain.agent.sub.EnrichSubAgent.buildSystemPrompt(
            taskDescription = "达到 ${(task.targetCoverage * 100).toInt()}% 覆盖率，分批处理 ${task.maxBatchSize} 首/批",
            maxBatchSize = task.maxBatchSize,
        )

        // ④ 创建 SchedulerStopSignal——桥接 Scheduler pause/resume ↔ Enrich runLoop 的 waitResume()
        val enrichStopSignal = SchedulerStopSignal(tokenCounter)

        // ④-2 准备 Enrich 的 AgentPolicy（per-Agent 独立权限包）
        val enrichTrustLedger = TrustLedger(enrichPolicyConfig, onChange = { persistEnrichPolicyAsync() })
        val enrichAgentPolicy = AgentPolicy.enrich(config = enrichPolicyConfig, trustLedger = enrichTrustLedger)

        // ⑤ 实例化 EnrichSubAgent（v7.1 P0-②：接 ToolCallExecutor + AgentPolicy.enrich()，不再 DirectToolExecutor 裸跑）
        val enrichAgent = com.hmp.domain.agent.sub.EnrichSubAgent(
            agentId = "enrich",
            contextBudget = contextBudget,
            toolRegistryView = toolView,
            toolRegistry = registry,  // 底层完整 ToolRegistry（ToolCallExecutor 需要）
            systemPrompt = systemPrompt,
            musicRepository = repo,
            presenceBus = chatPresenceBus,
            enrichConfig = enrichConfig,
            maxBatchSize = task.maxBatchSize,
            temperature = 0.3f,
            stopSignal = enrichStopSignal,
            policyGuard = chatPolicyGuard,       // P0-②：权限裁决器（Phase0 alwaysAllow 全部命中）
            agentPolicy = enrichAgentPolicy,     // P0-②：per-Agent 权限包
            auditLog = chatAuditLog,             // P0-②：审计日志
        )
        _subAgents["enrich"] = enrichAgent

        // ⑥ 注册到 Scheduler（priority=3）——回调直接桥接 StopSignal，不再走 suspendCoroutine/resumeCoroutine
        scheduler.registerAgent(
            AgentRegistration(
                agentId = "enrich",
                priority = AgentPriority.ENRICH,
                tokenUsagePerMin = 1_000L,
                onPause = { enrichStopSignal.onSchedulerPaused() },
                onResume = { enrichStopSignal.onSchedulerResumed() },
            )
        )

        // ⑦ 启动两个循环：
        //    - enrichAgent.runLoop()  ← 被动执行器，等 batchChannel；Scheduler pause 时 Mutex 挂起
        //    - enrichTaskLoop()       ← Master 派活/验收循环，轻量协程（每轮读 enrichTask 字段，rescan 即时生效）
        scope.launch { enrichAgent.runLoop() }
        scope.launch { enrichTaskLoop() }

        Logger.i("Agent.Master") { "[Master] EnrichSubAgent created (batch=${task.maxBatchSize}, targetCoverage=${task.targetCoverage}, config=${enrichConfig != null})" }
    }

    /** Master 下令销毁 Enrich（F1：只有 Master 能下令） */
    suspend fun stopEnrich() {
        _subAgents["enrich"]?.let { enrich ->
            enrich.shutdown()
            scheduler.unregisterAgent("enrich")
            _subAgents.remove("enrich")
            enrichTask = null
            enrichRetryCounts.clear()  // 重置重试计数，下次 startEnrich 重新累计
            Logger.i("Agent.Master") { "[Master] Enrich stopped" }
        }
    }

    /**
     * Master 的派活/验收循环（F4：轻量协程，不用 LLM）。
     *
     * 职责：
     * - 【派发】决定下一批 → enrichAgent.assignBatch(batch)
     * - 【验收】等 5s → 查 DB 实际结果
     * - 达成 targetCoverage → stopEnrich()
     * - 失败批次 → 重派（每首歌最多重派 [MAX_ENRICH_RETRIES] 次，超过视为永久失败放弃）
     *
     * 注意：每轮迭代从 [enrichTask] 字段读取当前 task（而非启动时的参数快照）——
     * rescanEnrich() 更新 targetCoverage 后下一轮即生效。
     */
    private suspend fun enrichTaskLoop() {
        val enrichAgent = _subAgents["enrich"] as? com.hmp.domain.agent.sub.EnrichSubAgent ?: run {
            Logger.e("Agent.Master") { "[Master] enrichTaskLoop: EnrichSubAgent not in _subAgents, abort" }
            return
        }
        Logger.i("Agent.Master") { "[Master] enrichTaskLoop start: target=${enrichTask?.targetCoverage} batch=${enrichTask?.maxBatchSize}" }
        var lastCheckTime = timeProvider()

        while (scope.isActive && _subAgents.containsKey("enrich")) {
            val task = enrichTask ?: break  // task 被清空（stopEnrich 并发触发）→ 退出
            // 【派发】决定下一批
            val nextBatch = musicRepository?.getUnenrichedSongs(task.maxBatchSize) ?: emptyList()

            if (nextBatch.isEmpty()) {
                // 没有新待富化的 → 检查目标是否达成
                val health = musicRepository?.getEnrichHealth() ?: EnrichHealth(0, 0, 0)
                Logger.i("Agent.Master") { "[Master] enrich health: coverage=${health.coverageRate}/${task.targetCoverage} enriched=${health.enrichedSongCount}/${health.totalSongCount}" }

                if (health.coverageRate >= task.targetCoverage) {
                    // ✅ 验收通过 → 下令销毁 Enrich
                    Logger.i("Agent.Master") { "[Master] enrich target achieved (${health.coverageRate} >= ${task.targetCoverage}), shutdown Enrich" }
                    stopEnrich()
                    return
                } else {
                    // 可能之前失败了 → 重派失败批次（每首歌最多重派 MAX_ENRICH_RETRIES 次）
                    val failed = musicRepository?.getFailedEnrichSongs(task.maxBatchSize)
                        ?.filter { (enrichRetryCounts[it.music.id] ?: 0) < MAX_ENRICH_RETRIES }
                        ?: emptyList()
                    if (failed.isNotEmpty()) {
                        failed.forEach { song ->
                            enrichRetryCounts[song.music.id] = (enrichRetryCounts[song.music.id] ?: 0) + 1
                        }
                        Logger.i("Agent.Master") { "[Master] retrying ${failed.size} failed songs (retries=${failed.map { enrichRetryCounts[it.music.id] }})" }
                        enrichAgent.assignBatch(failed)
                    } else {
                        Logger.w("Agent.Master") { "[Master] no unenriched and no failed but coverage ${health.coverageRate} < target ${task.targetCoverage} — waiting 10s" }
                        delay(10_000)
                    }
                }
            } else {
                // 有新批次 → 派给 Enrich
                Logger.i("Agent.Master") { "[Master] dispatching batch of ${nextBatch.size} songs to Enrich" }
                enrichAgent.assignBatch(nextBatch)
            }

            // 【验收】等 5s → 查 DB 实际结果
            delay(5_000)
            val results = musicRepository?.getRecentEnrichResults(since = lastCheckTime)
            if (results != null) {
                Logger.i("Agent.Master") { "[Master] enrich batch result: success=${results.successCount} failure=${results.failureCount} rate=${results.successRate}" }
            }
            lastCheckTime = timeProvider()
        }
    }

    /** 当前 SubAgent 状态查询（同步返回；Master 的 LLM 通过 master_query_sub_agents 工具调用） */
    fun querySubAgents(): Map<String, String> {
        return _subAgents.mapValues { (_, agent) -> agent.state().name } +
            mapOf(
                "scheduler_state" to scheduler.registeredAgentIds().joinToString(","),
                "token_usage" to "${tokenCounter.usedToday()}/${tokenCounter.dailyTokenQuota}",
            )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MasterAgentFacade 实现（T 阶段 enrich_* 工具反向调 Master 的桥接）
    // ═══════════════════════════════════════════════════════════════════════

    override fun isEnrichActive(): Boolean = _subAgents.containsKey("enrich") && enrichTask != null

    override suspend fun enrichStatusSummary(): Map<String, String> {
        val health = musicRepository?.getEnrichHealth()
        val schedulerEnrichState = scheduler.getState("enrich").name
        return buildMap {
            put("active", isEnrichActive().toString())
            put("scheduler_state", schedulerEnrichState)
            put("token_remaining", "${tokenCounter.remainingToday()}")
            if (enrichTask != null) put("target_coverage", enrichTask!!.targetCoverage.toString())
            if (health != null) {
                put("enriched_count", "${health.enrichedSongCount}")
                put("total_count", "${health.totalSongCount}")
                put("coverage_rate", health.coverageRate.toString())
                put("low_confidence_count", "${health.lowConfidenceCount}")
            }
        }
    }

    override suspend fun startEnrich(targetCoverage: Float?) {
        val task = EnrichTask(targetCoverage = targetCoverage ?: 0.9f)
        startEnrich(task)  // 不传 enrichTransport，用构造函数注入的 enrichTransport 退化链
    }

    override suspend fun pauseEnrich() {
        _subAgents["enrich"]?.pause()
        Logger.i("Agent.Master") { "[Master] enrich paused via facade" }
    }

    override suspend fun resumeEnrich() {
        _subAgents["enrich"]?.resume()
        Logger.i("Agent.Master") { "[Master] enrich resumed via facade" }
    }

    override suspend fun rescanEnrich(newTarget: Float?) {
        if (!isEnrichActive()) {
            // 没在跑 → 直接 startEnrich
            startEnrich(newTarget)
            return
        }
        // 已在跑 → 更新 task 并让 enrichTaskLoop 重新扫描
        val updatedTask = enrichTask?.copy(targetCoverage = newTarget ?: enrichTask!!.targetCoverage)
            ?: EnrichTask(targetCoverage = newTarget ?: 0.9f)
        enrichTask = updatedTask
        Logger.i("Agent.Master") { "[Master] enrich rescan triggered, new target=${updatedTask.targetCoverage}" }
    }

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

    companion object {
        /** 单首歌富化失败最大重派次数（首次派发不算重派；超过视为永久失败放弃） */
        const val MAX_ENRICH_RETRIES = 3
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
