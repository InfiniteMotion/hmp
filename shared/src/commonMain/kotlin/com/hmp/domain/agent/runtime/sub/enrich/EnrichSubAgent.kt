package com.hmp.domain.agent.runtime.sub.enrich

import com.hmp.domain.agent.config.EngineDefaults
import com.hmp.domain.agent.runtime.sub.shared.SubAgent

import com.hmp.domain.agent.enrich.EnrichWorkUnit
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.PresenceEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.port.Capability
import com.hmp.domain.agent.port.CapabilityState
import com.hmp.domain.agent.runtime.SchedulerStopSignal
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.platform.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 富化进度快照 —— **UI 契约**（F13：从 [EnrichSubAgent] 内部类上提为顶层公开数据类）。
 *
 * 为什么提出来：`HelloSlideCards`（shared-ui）要渲染进度，它需要的是**数据形状**，
 * 而不是 [EnrichSubAgent] 这个 internal 的 Agent 本体。留在内部类里会让整个
 * `EnrichSubAgent` 被迫 public（"可见性簇"效应）。
 * 提出来后 `MasterAgent.enrichProgressState()` 只暴露本类型，Agent 本体得以 internal。
 */
data class EnrichProgress(
    val processed: Int,
    val success: Int,
    val failed: Int,
    val currentUnitSize: Int,
    val state: AgentRunState,
    val currentArtist: String?,
    val chunkIndex: Int,
    val chunkTotal: Int,
    val phase: String,
) {
    companion object {
        /**
         * 未注册态的空进度 —— **领域层唯一的"空进度"定义**。
         *
         * ⚠️ 2026-09-20 之前这条 `-` 字面上有两份：`_progressState` 初值（本文件）与
         * UI 的 `HelloCardStack`（masterAgent 为 null 时的兜底）。**9 个位置参数**意味着：
         * 调整字段顺序两边都照样编译、只是静默错位；新增字段则可能只改一处。
         * 收敛到这里之后，生产方与消费方不可能再各自演化。
         */
        val IDLE = EnrichProgress(
            processed = 0,
            success = 0,
            failed = 0,
            currentUnitSize = 0,
            state = AgentRunState.UNREGISTERED,
            currentArtist = null,
            chunkIndex = 0,
            chunkTotal = 0,
            phase = "idle",
        )
    }
}

/**
 * EnrichSubAgent v2：自给自足的后台富化 Worker —— Agent 编排者。
 *
 * v2 核心变化：
 * - 批次策略：Repository 层按歌手聚合 + count DESC → 返回 EnrichWorkUnit
 *   - ArtistGroup（大歌手 ≥3 首）→ 单独组 + 预热 + 超大歌手拆 chunk（≤20 首/块）
 *   - MixedGroup（小歌手累计 ≥10 首）→ 批量，跳过 Round 0
 * - 6 轮编排：预热 → 枚举 → 枚举自检 → 自由文本易 → 自由文本难 → 总体反思
 * - contextBudget 多轮累积 history（批次内保留，批次末 clear）
 * - 预热缓存：同一歌手多个 chunk 只预热一次
 *
 * 设计铁则（对齐 RadioSubAgent 自循环模式）：
 * - F1：Master 决策外部生命周期（start/stop/pause/resume），Enrich 决策内部循环。
 * - F2：独立 AgentContextBudget + 独立 LlmTransport —— 富化管道完全内化。
 * - F3：暂停/恢复由 Scheduler 通过 SchedulerStopSignal 触发（priority=3）。
 * - F5：system prompt 由 Master 注入（F5：Enrich 不自演化角色）；用户 prompt 模板
 *   由 Enrich 自己在 companion 里定义——这是稳定的管道定义，不需要 Master 注入。
 * - F6：不知道全局状态（电量/网络/对话上下文），只管理自己的富化循环。
 */
internal class EnrichSubAgent(
    agentId: String = "enrich",
    contextBudget: AgentContextBudget,
    toolRegistryView: ToolRegistryView,
    /** Master 注入的富化 system prompt（F5：角色 + 任务参数，不变） */
    private val systemPrompt: String,
    /** 音乐库仓库（拉活 + 写 DB） */
    private val musicRepository: MusicRepository,
    /** 存在感总线（emit 进度） */
    private val presenceBus: PresenceBus? = null,
    /** LLM API 端点配置（热更新：MasterAgent.updateAiConfig 可动态替换） */
    private var enrichConfig: AiEndpointConfig? = null,
    /** 目标覆盖率（0.0 - 1.0） */
    @Volatile private var targetCoverage: Float = EngineDefaults.ENRICH_TARGET_COVERAGE,
    /** 停止/暂停信号（类型收紧：只接受 SchedulerStopSignal） */
    private val stopSignal: SchedulerStopSignal? = null,
) : SubAgent(agentId, contextBudget, toolRegistryView), Capability {

    // ── 构造诊断日志 ──
    init {
        val cfg = enrichConfig
        HmpLog.i(LogTag.AgentEnrich) {
            "📚 EnrichSubAgent created | targetCoverage=$targetCoverage | " +
            "hasLLM=${cfg != null} | endpoint=${cfg?.endpoint?.take(40) ?: "(none)"} | " +
            "model=${cfg?.selectedModel?.take(30) ?: "(default)"} | hasKey=${cfg?.apiKey?.isNotBlank() == true} | " +
            "systemPromptLen=${systemPrompt.length}"
        }
    }

    // Capability 接口实现需要一个独立 scope（SubAgent 基类不提供）
    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /** 累计处理歌曲数（已启动 LLM call 的歌曲总数） */
    @Volatile
    private var processedCount: Int = 0

    /** 成功富化歌曲数（DB 写入成功） */
    @Volatile
    private var successCount: Int = 0

    /** 失败歌曲数（LLM call 失败 + parse 失败） */
    @Volatile
    private var failCount: Int = 0

    /** 当前工作单元总大小（getProgress 对外暴露） */
    @Volatile
    private var currentUnitSize: Int = 0

    /** 当前处理的 artist 名（混合组为 GROUP_KEY_MIXED） */
    @Volatile
    private var currentArtist: String? = null

    /** 当前 chunk 在本 workUnit 中的序号（1-based） */
    @Volatile
    private var chunkIndex: Int = 0

    /** 当前 workUnit 总共拆成多少个 chunk */
    @Volatile
    private var chunkTotal: Int = 0

    /** 当前阶段文本（给 UI 展示） */
    @Volatile
    private var currentPhase: String = "idle"

    /** UI 订阅的进度 StateFlow（每次 chunk 完成更新） */
    private val _progressState = MutableStateFlow(
        EnrichProgress.IDLE
    )
    val progressState: StateFlow<EnrichProgress> = _progressState.asStateFlow()

    private fun updateProgressState() {
        _progressState.value = getProgress()
    }

    /** Master 更新目标覆盖率（rescanEnrich 触发） */
    fun updateTarget(newTarget: Float) {
        targetCoverage = newTarget
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] targetCoverage updated to $newTarget" }
    }

    /** 查询当前进度（Master status 用） */
    fun getProgress(): EnrichProgress = EnrichProgress(
        processed = processedCount,
        success = successCount,
        failed = failCount,
        currentUnitSize = currentUnitSize,
        state = runState,
        currentArtist = currentArtist,
        chunkIndex = chunkIndex,
        chunkTotal = chunkTotal,
        phase = currentPhase,
    )

    override suspend fun shutdown() {
        super.shutdown()
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] shutdown complete (processed=$processedCount success=$successCount failed=$failCount)" }
    }

    override suspend fun pause() {
        stopSignal?.onSchedulerPaused()
        runState = AgentRunState.PAUSED
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] manually paused" }
    }

    override suspend fun resume() {
        stopSignal?.onSchedulerResumed()
        runState = AgentRunState.RUNNING
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] manually resumed" }
    }

    // ===== 自循环 runLoop（内部状态机） =====

    /**
     * Enrich 自循环：fetchNextWorkUnit → chunk 拆分 → 每 chunk 6 轮编排 → 验收 → 自退出。
     */
    override suspend fun runLoop() {
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] runLoop start: target=${targetCoverage} config=${enrichConfig != null}" }
        isActive = true
        runState = AgentRunState.RUNNING
        currentPhase = "拉活工作单元"
        updateProgressState()

        // 预热缓存：同一歌手多个 chunk 只预热一次
        var preheatCache: Pair<String, String?>? = null  // artist → preheat text (nullable)
        // 连续 chunk 失败计数——用来检测网络/API 不可用，避免光速死循环
        var consecutiveChunkFails = 0

        while (isActive) {
            stopSignal?.waitResume()
            if (!isActive) break

            if (stopSignal?.shouldSoftStop() == true) {
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] stopSignal.shouldSoftStop() → exiting runLoop" }
                break
            }

            // ── 拉活：Repository 层按歌手聚合 + count DESC ──
            val workUnit = musicRepository.fetchNextEnrichWorkUnit()

            if (workUnit == null) {
                // ── 全部富化完了，验收 ──
                val health = musicRepository.getEnrichHealth()
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] health: coverage=${health.coverageRate}/${targetCoverage} enriched=${health.enrichedSongCount}/${health.totalSongCount}" }

                if (health.coverageRate >= targetCoverage) {
                    HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] target achieved (${health.coverageRate} >= ${targetCoverage}), self-exiting" }
                    break
                }

                // coverage 未达标但 unenriched 已清空 → 等一会儿再查
                HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] no unenriched but coverage ${health.coverageRate} < ${targetCoverage} — waiting" }
                currentPhase = "等待更多歌曲"
                updateProgressState()
                repeat(20) {
                    if (!isActive) break
                    stopSignal?.waitResume()
                    if (stopSignal?.shouldSoftStop() == true) break
                    delay(500)
                }
                continue
            }

            currentUnitSize = workUnit.size
            var anyChunkSucceeded = false

            when (workUnit) {
                is EnrichWorkUnit.ArtistGroup -> {
                    val artist = workUnit.artist
                    val allSongs = workUnit.songs

                    HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] ArtistGroup: '$artist' (${allSongs.size} songs)" }

                    // 超大歌手拆 chunk（≤ CHUNK_SPLIT_SIZE 首/块）
                    val chunks = allSongs.chunked(CHUNK_SPLIT_SIZE)
                    HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] '$artist' split into ${chunks.size} chunk(s): ${chunks.map { it.size }}" }

                    // 设置 workUnit 级追踪字段
                    currentArtist = artist
                    chunkTotal = chunks.size
                    chunkIndex = 0
                    currentPhase = "Round 0 预热"

                    // 预热缓存：同一歌手只预热一次
                    val (cachedArtist, cachedText) = preheatCache ?: (null to null)
                    val preheatText = if (cachedArtist == artist && cachedText != null) {
                        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] '$artist' using cached preheat" }
                        cachedText
                    } else {
                        val text = enrichConfig?.let { callAndLog(it, "Round 0 preheat", EnrichPrompts.buildPreheatPrompt(artist)) }
                        if (text != null) {
                            preheatCache = artist to text
                        } else {
                            HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] '$artist' Round 0 preheat failed — will continue without preheat" }
                        }
                        text
                    }

                    // Round 0 的 callLlm 会把 user(preheatPrompt) 残留到 history → 清掉，
                    // 每个 chunk 开头会重新注入完整的预热对话对
                    clearHistory()

                    // 每个 chunk 独立 5 轮，history 只在 chunk 内累积（chunk 间清 history，预热缓存重新注入）
                    for ((chunkIdx, chunk) in chunks.withIndex()) {
                        if (!isActive) break
                        stopSignal?.waitResume()
                        if (stopSignal?.shouldSoftStop() == true) break

                        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] '$artist' chunk ${chunkIdx + 1}/${chunks.size} (${chunk.size} songs)" }
                        chunkIndex = chunkIdx + 1
                        currentPhase = "Round 1 枚举中"
                        updateProgressState()

                        // 每个 chunk 开头注入预热（配对 user+assistant，保持历史对称）
                        if (preheatText != null) {
                            contextBudget.appendMessages(listOf(
                                LlmMessage(role = "user", content = EnrichPrompts.buildPreheatPrompt(artist)),
                                LlmMessage(role = "assistant", content = preheatText),
                            ))
                        }

                        val chunkOk = processChunk(chunk, artist, isMixed = false)
                        if (chunkOk) anyChunkSucceeded = true else consecutiveChunkFails++

                        // chunk 结束后清 history，防止 chunk N-1 的输出污染 chunk N 的 LLM 上下文
                        clearHistory()
                    }

                    // 歌手处理完，清预热缓存（clearHistory 已在 chunk 循环内完成）
                    preheatCache = null
                }

                is EnrichWorkUnit.MixedGroup -> {
                    HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] MixedGroup (${workUnit.songs.size} songs)" }
                    // 混合组跳过 Round 0 —— 没有共同歌手可以预热
                    // 同样加 chunk 拆分保护（Repository 层 mixGroupSize 通常已 < CHUNK_SPLIT_SIZE，但兜底）
                    val chunks = workUnit.songs.chunked(CHUNK_SPLIT_SIZE)
                    currentArtist = GROUP_KEY_MIXED
                    chunkTotal = chunks.size
                    chunkIndex = 0
                    for ((chunkIdx, chunk) in chunks.withIndex()) {
                        if (!isActive) break
                        stopSignal?.waitResume()
                        if (stopSignal?.shouldSoftStop() == true) break

                        chunkIndex = chunkIdx + 1
                        currentPhase = "Round 1 枚举中"
                        updateProgressState()
                        val chunkOk = processChunk(chunk, GROUP_KEY_MIXED, isMixed = true)
                        if (chunkOk) anyChunkSucceeded = true else consecutiveChunkFails++
                        clearHistory()
                    }
                }
            }

            if (anyChunkSucceeded) consecutiveChunkFails = 0

            // ── 连续失败保护：LLM/网络不可用时最多重试 5 轮就退避 15s ──
            if (consecutiveChunkFails >= 5) {
                HmpLog.e(LogTag.AgentEnrich) { "📚 [$agentId] consecutiveChunkFails=$consecutiveChunkFails (LLM/API unreachable) → backoff 15s" }
                currentPhase = "网络异常退避中"
                updateProgressState()
                consecutiveChunkFails = 0
                repeat(30) {
                    if (!isActive) break
                    stopSignal?.waitResume()
                    if (stopSignal?.shouldSoftStop() == true) break
                    delay(500)
                }
            } else if (!anyChunkSucceeded) {
                // 有失败但没到阈值 → 小退避 2s 防止光速重试
                delay(2000)
            }
        }

        isActive = false
        runState = AgentRunState.UNREGISTERED
        currentArtist = null
        chunkIndex = 0
        chunkTotal = 0
        currentPhase = "完成"
        updateProgressState()
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] runLoop exited (processed=$processedCount success=$successCount failed=$failCount)" }
    }

    // ===== 核心编排：processChunk（每 chunk 5 轮 LLM call，预热已在 runLoop 处理） =====

    /**
     * 处理一个 chunk（最多 CHUNK_SPLIT_SIZE 首歌），5 轮编排：
     *   Round 1:   枚举批量 genre/mood/scenario/language/era
     *   Round 1.5: 枚举自检（修 distribution 异常 / language vs 歌手国籍冲突）
     *   Round 2a:  自由文本 Easy —— description + singerIntroduce
     *   Round 2b:  自由文本 Facts —— rewards/lyric/backgroundIntroduce/relevantMusic（极易编造）
     *   Round 3:   总体反思 —— 跨轮次全局 patch
     *   → applyPatches → 批量写 DB
     *
     * 预热（Round 0）已在 runLoop 里统一注入到 contextBudget history（配对 user+assistant）。
     */
    private suspend fun processChunk(songs: List<MusicInfo>, groupKey: String, isMixed: Boolean): Boolean {
        val config = enrichConfig

        processedCount += songs.size

        if (config == null) {
            HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] No AiEndpointConfig, skipping chunk '$groupKey' (${songs.size} songs)" }
            failCount += songs.size
            return false
        }

        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] processing chunk '$groupKey' (${songs.size} songs, mixed=$isMixed)" }

        // ========== Round 1: 枚举批量 ==========
        currentPhase = "Round 1 枚举中"
        updateProgressState()
        val enumText = callAndLog(config, "Round 1 enum", EnrichPrompts.buildEnumPrompt(songs, groupKey, isMixed))
        if (enumText == null) {
            HmpLog.e(LogTag.AgentEnrich) { "📚 [$agentId] chunk '$groupKey' Round 1 failed — aborting chunk" }
            failCount += songs.size
            return false
        }
        val enumMap = runCatching { EnrichResponseParser.parseEnumBatch(enumText, songs, agentId).toMutableMap() }
            .getOrElse { e ->
                HmpLog.e(LogTag.AgentEnrich, e) { "📚 [$agentId] chunk '$groupKey' Round 1 parse failed: ${e.message}" }
                failCount += songs.size
                return false
            }

        // ========== Round 1.5: 枚举自检 ==========
        // 失败不致命——只是少了一道安全网
        currentPhase = "Round 1.5 自检中"
        updateProgressState()
        runCatching {
            val selfCheckText = callAndLog(config, "Round 1.5 enum self-check",
                EnrichPrompts.buildEnumSelfCheckPrompt(songs, enumMap, groupKey, isMixed))
            if (selfCheckText != null) {
                val selfCheckPatchMap = EnrichResponseParser.parseEnumSelfCheckFullList(selfCheckText, songs, agentId)
                EnrichResponseParser.applyEnumReplacements(enumMap, selfCheckPatchMap, songs)
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] chunk '$groupKey' Round 1.5 patched ${selfCheckPatchMap.size} song(s)" }
            }
        }.onFailure { e ->
            HmpLog.w(LogTag.AgentEnrich, e) { "📚 [$agentId] chunk '$groupKey' Round 1.5 self-check failed (non-fatal): ${e.message}" }
        }

        // ========== Round 2a: 自由文本 Easy（description + singerIntroduce） ==========
        currentPhase = "Round 2a 自由文本"
        updateProgressState()
        val easyText = callAndLog(config, "Round 2a freeText-easy",
            EnrichPrompts.buildFreeTextEasyPrompt(songs, enumMap, groupKey, isMixed))
        val easyMap = if (easyText != null) {
            runCatching { EnrichResponseParser.parseFreeTextEasyBatch(easyText, songs, agentId) }
                .getOrElse { e ->
                    HmpLog.e(LogTag.AgentEnrich, e) { "📚 [$agentId] chunk '$groupKey' Round 2a parse failed: ${e.message}" }
                    emptyMap()
                }
        } else {
            HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] chunk '$groupKey' Round 2a call failed — description/singerIntroduce will be empty" }
            emptyMap()
        }

        // ========== Round 2b: 自由文本 Facts（极易编造，强化 -暂无） ==========
        currentPhase = "Round 2b Facts"
        updateProgressState()
        val factsText = callAndLog(config, "Round 2b freeText-facts",
            EnrichPrompts.buildFreeTextFactsPrompt(songs, enumMap, groupKey, isMixed))
        val factsMap = if (factsText != null) {
            runCatching { EnrichResponseParser.parseFreeTextFactsBatch(factsText, songs, agentId) }
                .getOrElse { e ->
                    HmpLog.e(LogTag.AgentEnrich, e) { "📚 [$agentId] chunk '$groupKey' Round 2b parse failed: ${e.message}" }
                    emptyMap()
                }
        } else {
            HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] chunk '$groupKey' Round 2b call failed — facts fields will be empty/-暂无" }
            emptyMap()
        }

        // ========== Round 3: 总体反思 ==========
        currentPhase = "Round 3 反思"
        updateProgressState()
        // 先构造中间 EnrichDraft（枚举 + easy + facts），让反思轮能看到"完整"状态再 patch
        val draftResults = songs.associate { song ->
            val draft = EnrichResponseParser.mergeAll(
                enum = enumMap[song.music.id],
                easy = easyMap[song.music.id],
                facts = factsMap[song.music.id],
            )
            song.music.id to draft
        }.toMutableMap()

        runCatching {
            val reflectionText = callAndLog(config, "Round 3 reflection",
                EnrichPrompts.buildReflectionPrompt(songs, draftResults, groupKey, isMixed))
            if (reflectionText != null) {
                val patches = EnrichResponseParser.parseReflectionPatches(reflectionText, songs, agentId)
                EnrichResponseParser.applyReflectionPatches(draftResults, patches, songs)
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] chunk '$groupKey' Round 3 patched ${patches.size} field(s)" }
            }
        }.onFailure { e ->
            HmpLog.w(LogTag.AgentEnrich, e) { "📚 [$agentId] chunk '$groupKey' Round 3 reflection failed (non-fatal): ${e.message}" }
        }

        // ========== 最终写 DB ==========
        currentPhase = "写入数据库"
        updateProgressState()
        var chunkSuccess = 0
        var chunkFail = 0
        songs.forEach { song ->
            val draft = draftResults[song.music.id]
            val hasEnum = enumMap.containsKey(song.music.id)
            if (draft != null && hasEnum) {
                writeSongResult(song, draft)
                chunkSuccess++
                HmpLog.d(LogTag.AgentEnrich) { "📚 [$agentId] WRITE OK: id=${song.music.id} title=${song.music.title.take(20)} genre=${draft.genre.take(2)} language=${draft.language}" }
            } else {
                chunkFail++
                failCount++
                HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] SKIP: id=${song.music.id} title=${song.music.title.take(20)} reason=draft=${draft != null} hasEnum=$hasEnum" }
            }
        }

        presenceBus?.emit(PresenceEvent.AgentProgress(
            agentId = agentId,
            processed = processedCount,
            total = currentUnitSize,
        ))

        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] chunk '$groupKey' done: chunkSuccess=$chunkSuccess chunkFail=$chunkFail runningTotal success=$successCount fail=$failCount" }
        updateProgressState()
        return chunkSuccess > 0 || chunkFail == 0
    }

    /** 便捷封装：callLlmText + 日志。返回 null 表示失败。 */
    private suspend fun callAndLog(config: AiEndpointConfig, roundLabel: String, userPrompt: String): String? {
        val text = contextBudget.callLlmText(
            config = config,
            systemPrompt = systemPrompt,
            newMessages = listOf(LlmMessage(role = "user", content = userPrompt)),
        )
        if (text == null) {
            HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] $roundLabel → null (call failed or timeout)" }
        } else {
            HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] $roundLabel raw(${text.length}): ${text.take(600)}" }
        }
        return text
    }

    /**
     * 单首写 DB —— 一次富化产出**分两路落库**：
     * 6 个文本字段 → musicExtra 表（[EnrichDraft.toExtraTexts]）；
     * 5 个结构化字段 → labels 表（[writeLabelsFromDraft]）。
     */
    private suspend fun writeSongResult(song: MusicInfo, draft: EnrichDraft) {
        musicRepository.updateMusicExtraTexts(song.music.id, draft.toExtraTexts())
        writeLabelsFromDraft(song.music.id, draft)
        successCount++
    }


    // ===== DB 写入辅助 =====

    private suspend fun writeLabelsFromDraft(musicId: Long, draft: EnrichDraft) {
        draft.genre.forEach { addLabelSafe(musicId, LabelCategory.GENRE, it) }
        draft.mood.forEach { addLabelSafe(musicId, LabelCategory.MOOD, it) }
        draft.scenario.forEach { addLabelSafe(musicId, LabelCategory.SCENARIO, it) }
        draft.language.takeIf { it.isNotBlank() }?.let { addLabelSafe(musicId, LabelCategory.LANGUAGE, it) }
        draft.era.takeIf { it.isNotBlank() }?.let { addLabelSafe(musicId, LabelCategory.ERA, it) }
    }

    private suspend fun addLabelSafe(musicId: Long, category: LabelCategory, rawLabel: String) {
        val name = LabelName.Companion.match(rawLabel)
        if (name == null || name == LabelName.UNKNOWN) return
        musicRepository.addMusicLabel(MusicLabel(musicId, category, name))
    }

    private fun clearHistory() {
        contextBudget.clearHistory()
    }

    // ===== 编排自身的参数 =====
    // prompt 词表已外移到 EnrichPrompts；模型输出的 wire 类型与解析见 EnrichResponseParser。

    companion object {

        private const val GROUP_KEY_MIXED = "__mixed__"

        /** 超大歌手拆分阈值：单次 LLM call 最多处理这么多首 */
        private const val CHUNK_SPLIT_SIZE = 20
    }

    /** 热更新 AI 配置——由 MasterAgent.updateAiConfig 推送。下次 chunk 处理时用新 config。 */
    fun updateAiConfig(enrichConfig: AiEndpointConfig?) {
        this.enrichConfig = enrichConfig
        HmpLog.i(LogTag.AgentEnrich) {
            "🔄 updateAiConfig | hasLLM=${enrichConfig != null} | " +
            "endpoint=${enrichConfig?.endpoint?.take(40) ?: "(none)"} | " +
            "model=${enrichConfig?.selectedModel?.take(30) ?: "(default)"} | " +
            "hasKey=${enrichConfig?.apiKey?.isNotBlank() == true}"
        }
    }

    // ── Capability 接口实现（F9-A0） ──

    override val capabilityName = "enrich"

    /** progressState → CapabilityState 统一映射 */
    override val stateFlow: StateFlow<CapabilityState> = _progressState.map { progress ->
        val status = when (progress.state) {
            AgentRunState.RUNNING -> CapabilityState.Status.RUNNING
            AgentRunState.PAUSED -> CapabilityState.Status.PAUSED
            AgentRunState.UNREGISTERED -> CapabilityState.Status.IDLE
        }
        CapabilityState(
            status = status,
            detail = "${progress.phase} ${progress.processed}/${progress.currentUnitSize}",
            raw = progress,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = CapabilityState(CapabilityState.Status.IDLE, detail = "idle"),
    )
}

