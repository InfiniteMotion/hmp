package com.hmp.domain.agent.runtime.sub.enrich

import com.hmp.domain.agent.config.EngineDefaults
import com.hmp.domain.agent.enrich.EnrichWorkUnit
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.PresenceEvent
import com.hmp.domain.agent.port.Capability
import com.hmp.domain.agent.port.CapabilityState
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.runtime.SchedulerStopSignal
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.domain.agent.runtime.sub.shared.SubAgent
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import com.hmp.platform.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 富化进度快照 —— **UI 契约**（F13：从 [EnrichSubAgent] 内部类上提为顶层公开数据类）。
 *
 * 为什么提出来：`HelloSlideCards`（shared-ui）要渲染进度，它需要的是**数据形状**，
 * 而不是 [EnrichSubAgent] 这个 internal 的 Agent 本体。留在内部类里会让整个
 * `EnrichSubAgent` 被迫 public（"可见性簇"效应）。
 * 提出来后 `MasterAgent.enrichProgressState()` 只暴露本类型，Agent 本体得以 internal。
 */
/** 一次富化运行的终态；只由 runLoop 的 finally 写入。 */
enum class EnrichOutcome { COMPLETED, QUOTA_EXHAUSTED, CANCELLED, FAILED }

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
    /** 本次 runLoop 的终态；运行中恒为 null。 */
    val outcome: EnrichOutcome? = null,
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
    @Volatile private var systemPrompt: String,
    /** 音乐库仓库（拉活 + 写 DB） */
    private val musicRepository: MusicRepository,
    /** 存在感总线（emit 进度） */
    private val presenceBus: PresenceBus? = null,
    /** LLM API 端点配置（热更新：MasterAgent.updateAiConfig 可动态替换） */
    @Volatile private var enrichConfig: AiEndpointConfig? = null,
    /** 目标覆盖率（0.0 - 1.0） */
    @Volatile private var targetCoverage: Float = EngineDefaults.ENRICH_TARGET_COVERAGE,
    /**
     * 当前凭据模式（T5 分流）：
     * - `FREE` / `PAID` → **单首随机 + 精简提示词**（花的是内置 Key 的额度，省 token 优先）；
     * - `CUSTOM` → **保留原有批量 6 轮完整流程**（用户自己的 Key，质量优先）。
     * 默认 CUSTOM：老装配 / 测试不传时行为不变。
     */
    private val accessModeProvider: suspend () -> com.hmp.domain.setting.model.AiAccessMode = {
        com.hmp.domain.setting.model.AiAccessMode.CUSTOM
    },
    /** 停止/暂停信号（类型收紧：只接受 SchedulerStopSignal） */
    private val stopSignal: SchedulerStopSignal? = null,
) : SubAgent(agentId, contextBudget, toolRegistryView),
    Capability {

    // ── 构造诊断日志 ──
    init {
        // 单一初值：与 EnrichProgress.IDLE 的 state 对齐（基类默认 PAUSED 只适用于已注册实例）。
        runState = AgentRunState.UNREGISTERED
        val cfg = enrichConfig
        HmpLog.i(LogTag.AgentEnrich) {
            "📚 EnrichSubAgent created | targetCoverage=$targetCoverage | " +
                "hasLLM=${cfg != null} | endpoint=${cfg?.endpoint?.take(40) ?: "(none)"} | " +
                "model=${cfg?.selectedModel?.take(30) ?: "(default)"} | hasKey=${cfg?.apiKey?.isNotBlank() == true} | " +
                "systemPromptLen=${systemPrompt.length}"
        }
    }

    // Capability 接口实现需要一个独立 scope；shutdown() 会取消它
    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /** 唯一的运行快照出口：进度 / 生命周期 / 当前单元都在这一份里。 */
    private val _progressState = MutableStateFlow(EnrichProgress.IDLE)
    val progressState: StateFlow<EnrichProgress> = _progressState.asStateFlow()

    /** 生命周期迁移；进度字段由运行循环写入同一份快照。 */
    private fun transition(state: AgentRunState, phase: String? = null) {
        runState = state
        _progressState.update { p -> p.copy(state = state, phase = phase ?: p.phase) }
    }

    private inline fun updateProgress(transform: (EnrichProgress) -> EnrichProgress) {
        _progressState.update(transform)
    }

    /** Master 更新目标覆盖率（rescanEnrich 触发） */
    fun updateTarget(newTarget: Float) {
        targetCoverage = newTarget
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] targetCoverage updated to $newTarget" }
    }

    /**
     * 开始新一次运行（单例语义，2026-11-13）：实例常驻，`start` 只重置**运行态**。
     *
     * 清掉上一次的终态与进度计数，换成本次的目标 / prompt / 端点配置；
     * **刻意不清 `contextBudget` 的 history** —— Enrich 拥有长期记忆（用户决议），
     * 跨运行累积的上下文交给预算内的压缩机制处理。
     */
    fun beginRun(targetCoverage: Float, systemPrompt: String, config: AiEndpointConfig?) {
        this.targetCoverage = targetCoverage
        this.systemPrompt = systemPrompt
        this.enrichConfig = config
        isActive = false
        runState = AgentRunState.UNREGISTERED
        // 上一次若是暂停态，门闩要复位，否则新一轮第一轮拉活就挂在 waitResume() 上。
        stopSignal?.resetForNewRun()
        _progressState.value = EnrichProgress.IDLE
        HmpLog.i(LogTag.AgentEnrich) {
            "📚 [$agentId] beginRun | target=$targetCoverage history=${contextBudget.historySize()} hasLLM=${config != null}"
        }
    }

    /** 查询当前进度（与 [progressState] 同源）。 */
    fun getProgress(): EnrichProgress = _progressState.value

    override suspend fun shutdown() {
        super.shutdown()
        scope.cancel()
        val p = _progressState.value
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] shutdown complete (processed=${p.processed} success=${p.success} failed=${p.failed})" }
    }

    override suspend fun pause() {
        if (!isActive) return
        stopSignal?.onSchedulerPaused()
        transition(AgentRunState.PAUSED)
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] manually paused" }
    }

    override suspend fun resume() {
        if (!isActive) return
        stopSignal?.onSchedulerResumed()
        transition(AgentRunState.RUNNING)
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] manually resumed" }
    }

    /** Scheduler 暂停只走 StopSignal；这里补一份对外可见的状态快照。 */
    internal fun markSchedulerPaused() {
        if (isActive) transition(AgentRunState.PAUSED)
    }

    internal fun markSchedulerResumed() {
        if (isActive) transition(AgentRunState.RUNNING)
    }

    // ===== 自循环 runLoop（内部状态机） =====

    /**
     * Enrich 自循环：fetchNextWorkUnit → chunk 拆分 → 每 chunk 6 轮编排 → 验收 → 自退出。
     */

    /**
     * 入口分流（T5）：免费/付费（内置 Key）走省 token 的单首路径；自定义端点保留完整批量流程。
     *
     * 模式在**进入循环时**读一次 —— 运行中切换凭据模式需要重启 Enrich；
     * Master 的 startEnrich 是幂等的，设置页切模式后由上层重启即可。
     */
    override suspend fun runLoop() {
        // 终态由 finally 唯一写入；三种退出路径各有明确死法，不再全落成「完成」。
        var outcome = EnrichOutcome.COMPLETED
        try {
            isActive = true
            transition(AgentRunState.RUNNING)
            val mode = runCatching { accessModeProvider() }
                .getOrDefault(com.hmp.domain.setting.model.AiAccessMode.CUSTOM)
            if (mode == com.hmp.domain.setting.model.AiAccessMode.CUSTOM) {
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] mode=CUSTOM → batch(6-round) pipeline" }
                runBatchLoop()
            } else {
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] mode=$mode → single-song compact pipeline" }
                runSingleSongLoop()
            }
        } catch (ce: CancellationException) {
            outcome = EnrichOutcome.CANCELLED
            throw ce
        } catch (t: Throwable) {
            outcome = EnrichOutcome.FAILED
            throw t
        } finally {
            finish(outcome)
        }
    }

    /** 正常退出 / 取消 / 异常共用同一条收尾，保证终态必达。 */
    private fun finish(rawOutcome: EnrichOutcome) {
        // shouldSoftStop() 退出时额度判断仍为 true（额度没变），据此把「被赶出来」
        // 与「达标干完」分开；纯函数抽出便于单测，不需要新增字段。
        val outcome = resolveOutcome(rawOutcome, stopSignal?.shouldSoftStop() == true)
        isActive = false
        runState = AgentRunState.UNREGISTERED
        _progressState.update {
            it.copy(
                state = AgentRunState.UNREGISTERED,
                outcome = outcome,
                phase = when (outcome) {
                    EnrichOutcome.COMPLETED -> "完成"
                    EnrichOutcome.QUOTA_EXHAUSTED -> "额度已用完，恢复后自动继续"
                    EnrichOutcome.CANCELLED -> "已停止"
                    EnrichOutcome.FAILED -> "失败"
                },
                currentArtist = null,
                chunkIndex = 0,
                chunkTotal = 0,
            )
        }
        val p = _progressState.value
        HmpLog.i(LogTag.AgentEnrich) {
            "📚 [$agentId] runLoop exited (outcome=$outcome processed=${p.processed} success=${p.success} failed=${p.failed})"
        }
    }

    private suspend fun runBatchLoop() {
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] runLoop start: target=$targetCoverage config=${enrichConfig != null}" }
        updateProgress { it.copy(phase = "拉活工作单元") }

        // 预热缓存：同一歌手多个 chunk 只预热一次
        var preheatCache: Pair<String, String?>? = null // artist → preheat text (nullable)
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
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] health: coverage=${health.coverageRate}/$targetCoverage enriched=${health.enrichedSongCount}/${health.totalSongCount}" }

                if (health.coverageRate >= targetCoverage) {
                    HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] target achieved (${health.coverageRate} >= $targetCoverage), self-exiting" }
                    break
                }

                // coverage 未达标但 unenriched 已清空 → 等一会儿再查
                HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] no unenriched but coverage ${health.coverageRate} < $targetCoverage — waiting" }
                updateProgress { it.copy(phase = "等待更多歌曲") }
                repeat(20) {
                    if (!isActive) break
                    stopSignal?.waitResume()
                    if (stopSignal?.shouldSoftStop() == true) break
                    delay(500)
                }
                continue
            }

            updateProgress { it.copy(currentUnitSize = workUnit.size) }
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
                    updateProgress {
                        it.copy(currentArtist = artist, chunkTotal = chunks.size, chunkIndex = 0, phase = "Round 0 预热")
                    }

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
                        updateProgress { it.copy(chunkIndex = chunkIdx + 1, phase = "Round 1 枚举中") }

                        // 每个 chunk 开头注入预热（配对 user+assistant，保持历史对称）
                        if (preheatText != null) {
                            contextBudget.appendMessages(
                                listOf(
                                    LlmMessage(role = "user", content = EnrichPrompts.buildPreheatPrompt(artist)),
                                    LlmMessage(role = "assistant", content = preheatText),
                                ),
                            )
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
                    updateProgress {
                        it.copy(currentArtist = GROUP_KEY_MIXED, chunkTotal = chunks.size, chunkIndex = 0)
                    }
                    for ((chunkIdx, chunk) in chunks.withIndex()) {
                        if (!isActive) break
                        stopSignal?.waitResume()
                        if (stopSignal?.shouldSoftStop() == true) break

                        updateProgress { it.copy(chunkIndex = chunkIdx + 1, phase = "Round 1 枚举中") }
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
                updateProgress { it.copy(phase = "网络异常退避中") }
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
    }

    /**
     * 单首循环（FREE / PAID）：每次从 50 首候选池随机取一首，1 次精简请求。
     *
     * 与 [runBatchLoop] 的差别只有"取几首 + 几次请求"：落库路径完全相同。
     */
    private suspend fun runSingleSongLoop() {
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] single-song loop start: target=$targetCoverage config=${enrichConfig != null}" }
        updateProgress { it.copy(phase = "单首富化中") }

        var consecutiveFails = 0

        while (isActive) {
            stopSignal?.waitResume()
            if (!isActive) break
            if (stopSignal?.shouldSoftStop() == true) {
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] stopSignal.shouldSoftStop() → exiting single-song loop" }
                break
            }

            val pool = musicRepository.getUnenrichedSongs(RANDOM_POOL_SIZE)
            if (pool.isEmpty()) {
                val health = musicRepository.getEnrichHealth()
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] health: coverage=${health.coverageRate}/$targetCoverage enriched=${health.enrichedSongCount}/${health.totalSongCount}" }
                if (health.coverageRate >= targetCoverage) {
                    HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] target achieved, self-exiting" }
                    break
                }
                updateProgress { it.copy(phase = "等待更多歌曲") }
                repeat(20) {
                    if (!isActive) break
                    stopSignal?.waitResume()
                    if (stopSignal?.shouldSoftStop() == true) break
                    delay(500)
                }
                continue
            }

            val song = pool.random()
            updateProgress {
                it.copy(currentArtist = song.music.artist, currentUnitSize = 1, chunkIndex = 1, chunkTotal = 1)
            }

            val ok = processSingleSong(song)
            consecutiveFails = if (ok) 0 else consecutiveFails + 1

            if (consecutiveFails >= 5) {
                HmpLog.e(LogTag.AgentEnrich) { "📚 [$agentId] consecutiveFails=$consecutiveFails → backoff 15s" }
                updateProgress { it.copy(phase = "网络异常退避中") }
                consecutiveFails = 0
                repeat(30) {
                    if (!isActive) break
                    stopSignal?.waitResume()
                    if (stopSignal?.shouldSoftStop() == true) break
                    delay(500)
                }
            } else if (!ok) {
                delay(2000)
            } else {
                delay(200)
            }
        }
    }

    /** 单首富化：1 次精简请求 → 解析 → 两路落库。 */
    private suspend fun processSingleSong(song: MusicInfo): Boolean {
        updateProgress { it.copy(processed = it.processed + 1) }
        val config = enrichConfig
        if (config == null) {
            HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] No AiEndpointConfig, skipping \"${song.music.title}\"" }
            updateProgress { it.copy(failed = it.failed + 1) }
            return false
        }
        val text = callAndLog(
            config = config,
            roundLabel = "single \"${song.music.title}\"",
            userPrompt = EnrichPrompts.buildSingleSongPrompt(song),
            systemOverride = EnrichPrompts.SINGLE_SONG_SYSTEM_PROMPT,
        ) ?: run {
            updateProgress { it.copy(failed = it.failed + 1) }
            return false
        }

        val draft = EnrichResponseParser.parseSingleSong(text, agentId)
        if (draft == null) {
            updateProgress { it.copy(failed = it.failed + 1) }
            return false
        }

        writeSongResult(song, draft)
        return true
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

        updateProgress { it.copy(processed = it.processed + songs.size) }

        if (config == null) {
            HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] No AiEndpointConfig, skipping chunk '$groupKey' (${songs.size} songs)" }
            updateProgress { it.copy(failed = it.failed + songs.size) }
            return false
        }

        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] processing chunk '$groupKey' (${songs.size} songs, mixed=$isMixed)" }

        // ========== Round 1: 枚举批量 ==========
        updateProgress { it.copy(phase = "Round 1 枚举中") }
        val enumText = callAndLog(config, "Round 1 enum", EnrichPrompts.buildEnumPrompt(songs, groupKey, isMixed))
        if (enumText == null) {
            HmpLog.e(LogTag.AgentEnrich) { "📚 [$agentId] chunk '$groupKey' Round 1 failed — aborting chunk" }
            updateProgress { it.copy(failed = it.failed + songs.size) }
            return false
        }
        val enumMap = runCatching { EnrichResponseParser.parseEnumBatch(enumText, songs, agentId).toMutableMap() }
            .getOrElse { e ->
                HmpLog.e(LogTag.AgentEnrich, e) { "📚 [$agentId] chunk '$groupKey' Round 1 parse failed: ${e.message}" }
                updateProgress { it.copy(failed = it.failed + songs.size) }
                return false
            }

        // ========== Round 1.5: 枚举自检 ==========
        // 失败不致命——只是少了一道安全网
        updateProgress { it.copy(phase = "Round 1.5 自检中") }
        runCatching {
            val selfCheckText = callAndLog(
                config,
                "Round 1.5 enum self-check",
                EnrichPrompts.buildEnumSelfCheckPrompt(songs, enumMap, groupKey, isMixed),
            )
            if (selfCheckText != null) {
                val selfCheckPatchMap = EnrichResponseParser.parseEnumSelfCheckFullList(selfCheckText, songs, agentId)
                EnrichResponseParser.applyEnumReplacements(enumMap, selfCheckPatchMap, songs)
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] chunk '$groupKey' Round 1.5 patched ${selfCheckPatchMap.size} song(s)" }
            }
        }.onFailure { e ->
            HmpLog.w(LogTag.AgentEnrich, e) { "📚 [$agentId] chunk '$groupKey' Round 1.5 self-check failed (non-fatal): ${e.message}" }
        }

        // ========== Round 2a: 自由文本 Easy（description + singerIntroduce） ==========
        updateProgress { it.copy(phase = "Round 2a 自由文本") }
        val easyText = callAndLog(
            config,
            "Round 2a freeText-easy",
            EnrichPrompts.buildFreeTextEasyPrompt(songs, enumMap, groupKey, isMixed),
        )
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
        updateProgress { it.copy(phase = "Round 2b Facts") }
        val factsText = callAndLog(
            config,
            "Round 2b freeText-facts",
            EnrichPrompts.buildFreeTextFactsPrompt(songs, enumMap, groupKey, isMixed),
        )
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
        updateProgress { it.copy(phase = "Round 3 反思") }
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
            val reflectionText = callAndLog(
                config,
                "Round 3 reflection",
                EnrichPrompts.buildReflectionPrompt(songs, draftResults, groupKey, isMixed),
            )
            if (reflectionText != null) {
                val patches = EnrichResponseParser.parseReflectionPatches(reflectionText, songs, agentId)
                EnrichResponseParser.applyReflectionPatches(draftResults, patches, songs)
                HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] chunk '$groupKey' Round 3 patched ${patches.size} field(s)" }
            }
        }.onFailure { e ->
            HmpLog.w(LogTag.AgentEnrich, e) { "📚 [$agentId] chunk '$groupKey' Round 3 reflection failed (non-fatal): ${e.message}" }
        }

        // ========== 最终写 DB ==========
        updateProgress { it.copy(phase = "写入数据库") }
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
                updateProgress { it.copy(failed = it.failed + 1) }
                HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] SKIP: id=${song.music.id} title=${song.music.title.take(20)} reason=draft=${draft != null} hasEnum=$hasEnum" }
            }
        }

        val progress = _progressState.value
        presenceBus?.emit(
            PresenceEvent.AgentProgress(
                agentId = agentId,
                processed = progress.processed,
                total = progress.currentUnitSize,
            ),
        )

        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] chunk '$groupKey' done: chunkSuccess=$chunkSuccess chunkFail=$chunkFail runningTotal success=${progress.success} fail=${progress.failed}" }
        return chunkSuccess > 0 || chunkFail == 0
    }

    /** 便捷封装：callLlmText + 日志。返回 null 表示失败。 */
    private suspend fun callAndLog(
        config: AiEndpointConfig,
        roundLabel: String,
        userPrompt: String,
        systemOverride: String? = null,
    ): String? {
        val text = contextBudget.callLlmText(
            config = config,
            systemPrompt = systemOverride ?: systemPrompt,
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
        updateProgress { it.copy(success = it.success + 1) }
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

        /** 单首随机：一次取一页候选再随机挑，避免 ORDER BY RANDOM() 全表排序。 */
        private const val RANDOM_POOL_SIZE = 50

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

    /** progressState → CapabilityState 单一映射（映射逻辑抽成纯函数，便于单测终态）。 */
    override val stateFlow: StateFlow<CapabilityState> = _progressState
        .map { it.toCapabilityState() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = CapabilityState(CapabilityState.Status.IDLE, detail = "idle"),
        )
}

/**
 * 死法归一：只有「正常跑完且额度分桶已耗尽」才算 QUOTA_EXHAUSTED。
 * 取消 / 异常优先，不受额度影响。
 */
internal fun resolveOutcome(raw: EnrichOutcome, softStop: Boolean): EnrichOutcome = if (raw == EnrichOutcome.COMPLETED && softStop) EnrichOutcome.QUOTA_EXHAUSTED else raw

/** 进度 → 对外只读状态。COMPLETED / ERROR 的文案与颜色 UI 早已备好，过去从没人写过。 */
internal fun EnrichProgress.toCapabilityState(): CapabilityState {
    val status = when (outcome) {
        EnrichOutcome.COMPLETED -> CapabilityState.Status.COMPLETED

        // 不是错误、也不是完成：暂时跑不了，detail 里写明「额度已用完，恢复后自动继续」。
        EnrichOutcome.QUOTA_EXHAUSTED -> CapabilityState.Status.PAUSED

        EnrichOutcome.FAILED -> CapabilityState.Status.ERROR

        EnrichOutcome.CANCELLED -> CapabilityState.Status.IDLE

        null -> when (state) {
            AgentRunState.RUNNING -> CapabilityState.Status.RUNNING
            AgentRunState.PAUSED -> CapabilityState.Status.PAUSED
            AgentRunState.UNREGISTERED -> CapabilityState.Status.IDLE
        }
    }
    return CapabilityState(
        status = status,
        detail = if (chunkTotal > 0) "$phase $chunkIndex/$chunkTotal" else phase,
        raw = this,
    )
}
