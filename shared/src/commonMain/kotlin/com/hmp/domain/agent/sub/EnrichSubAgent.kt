package com.hmp.domain.agent.sub

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.enrich.EnrichWorkUnit
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.PresenceEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.runtime.SchedulerStopSignal
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.model.DailyMusicInfo
import com.hmp.platform.Volatile
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
 * - F2：独立 AgentContextBudget(32K) + 独立 LlmTransport —— 富化管道完全内化。
 * - F3：暂停/恢复由 Scheduler 通过 SchedulerStopSignal 触发（priority=3）。
 * - F5：system prompt 由 Master 注入（F5：Enrich 不自演化角色）；用户 prompt 模板
 *   由 Enrich 自己在 companion 里定义——这是稳定的管道定义，不需要 Master 注入。
 * - F6：不知道全局状态（电量/网络/对话上下文），只管理自己的富化循环。
 */
class EnrichSubAgent(
    agentId: String = "enrich",
    contextBudget: AgentContextBudget,
    toolRegistryView: ToolRegistryView,
    /** Master 注入的富化 system prompt（F5：角色 + 任务参数，不变） */
    private val systemPrompt: String,
    /** 音乐库仓库（拉活 + 写 DB） */
    private val musicRepository: MusicRepository,
    /** 存在感总线（emit 进度） */
    private val presenceBus: PresenceBus? = null,
    /** LLM API 端点配置（null 表示开发模式跳过 LLM 调用） */
    private val enrichConfig: AiEndpointConfig? = null,
    /** 目标覆盖率（0.0 - 1.0） */
    @Volatile private var targetCoverage: Float = 0.9f,
    /** 停止/暂停信号（类型收紧：只接受 SchedulerStopSignal） */
    private val stopSignal: SchedulerStopSignal? = null,
) : SubAgent(agentId, contextBudget, toolRegistryView) {

    private val json = Json { ignoreUnknownKeys = true }

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

    /** 当前进度快照 */
    data class EnrichProgress(
        val processed: Int,
        val success: Int,
        val failed: Int,
        val currentUnitSize: Int,
        val state: AgentRunState,
    )

    /** Master 更新目标覆盖率（rescanEnrich 触发） */
    fun updateTarget(newTarget: Float) {
        targetCoverage = newTarget
        Logger.i("Agent.Enrich") { "[$agentId] targetCoverage updated to $newTarget" }
    }

    /** 查询当前进度（Master status 用） */
    fun getProgress(): EnrichProgress = EnrichProgress(
        processed = processedCount,
        success = successCount,
        failed = failCount,
        currentUnitSize = currentUnitSize,
        state = runState,
    )

    override suspend fun shutdown() {
        super.shutdown()
        Logger.i("Agent.Enrich") { "[$agentId] shutdown complete (processed=$processedCount success=$successCount failed=$failCount)" }
    }

    override suspend fun pause() {
        stopSignal?.onSchedulerPaused()
        runState = AgentRunState.PAUSED
        Logger.i("Agent.Enrich") { "[$agentId] manually paused" }
    }

    override suspend fun resume() {
        stopSignal?.onSchedulerResumed()
        runState = AgentRunState.RUNNING
        Logger.i("Agent.Enrich") { "[$agentId] manually resumed" }
    }

    // ===== 自循环 runLoop（内部状态机） =====

    /**
     * Enrich 自循环：fetchNextWorkUnit → chunk 拆分 → 每 chunk 6 轮编排 → 验收 → 自退出。
     */
    override suspend fun runLoop() {
        Logger.i("Agent.Enrich") { "[$agentId] runLoop start: target=${targetCoverage} config=${enrichConfig != null}" }
        isActive = true
        runState = AgentRunState.RUNNING

        // 预热缓存：同一歌手多个 chunk 只预热一次
        var preheatCache: Pair<String, String?>? = null  // artist → preheat text (nullable)

        while (isActive) {
            stopSignal?.waitResume()
            if (!isActive) break

            if (stopSignal?.shouldSoftStop() == true) {
                Logger.i("Agent.Enrich") { "[$agentId] stopSignal.shouldSoftStop() → exiting runLoop" }
                break
            }

            // ── 拉活：Repository 层按歌手聚合 + count DESC ──
            val workUnit = musicRepository.fetchNextEnrichWorkUnit()

            if (workUnit == null) {
                // ── 全部富化完了，验收 ──
                val health = musicRepository.getEnrichHealth()
                Logger.i("Agent.Enrich") { "[$agentId] health: coverage=${health.coverageRate}/${targetCoverage} enriched=${health.enrichedSongCount}/${health.totalSongCount}" }

                if (health.coverageRate >= targetCoverage) {
                    Logger.i("Agent.Enrich") { "[$agentId] target achieved (${health.coverageRate} >= ${targetCoverage}), self-exiting" }
                    break
                }

                // coverage 未达标但 unenriched 已清空 → 等一会儿再查
                Logger.w("Agent.Enrich") { "[$agentId] no unenriched but coverage ${health.coverageRate} < ${targetCoverage} — waiting" }
                repeat(20) {
                    if (!isActive) break
                    stopSignal?.waitResume()
                    if (stopSignal?.shouldSoftStop() == true) break
                    delay(500)
                }
                continue
            }

            currentUnitSize = workUnit.size

            when (workUnit) {
                is EnrichWorkUnit.ArtistGroup -> {
                    val artist = workUnit.artist
                    val allSongs = workUnit.songs

                    Logger.i("Agent.Enrich") { "[$agentId] ArtistGroup: '$artist' (${allSongs.size} songs)" }

                    // 超大歌手拆 chunk（≤ CHUNK_SPLIT_SIZE 首/块）
                    val chunks = allSongs.chunked(CHUNK_SPLIT_SIZE)
                    Logger.i("Agent.Enrich") { "[$agentId] '$artist' split into ${chunks.size} chunk(s): ${chunks.map { it.size }}" }

                    // 预热缓存：同一歌手只预热一次
                    val (cachedArtist, cachedText) = preheatCache ?: (null to null)
                    val preheatText = if (cachedArtist == artist && cachedText != null) {
                        Logger.i("Agent.Enrich") { "[$agentId] '$artist' using cached preheat" }
                        cachedText
                    } else {
                        val text = enrichConfig?.let { callAndLog(it, "Round 0 preheat", buildPreheatPrompt(artist)) }
                        if (text != null) {
                            preheatCache = artist to text
                        } else {
                            Logger.w("Agent.Enrich") { "[$agentId] '$artist' Round 0 preheat failed — will continue without preheat" }
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

                        Logger.i("Agent.Enrich") { "[$agentId] '$artist' chunk ${chunkIdx + 1}/${chunks.size} (${chunk.size} songs)" }

                        // 每个 chunk 开头注入预热（配对 user+assistant，保持历史对称）
                        if (preheatText != null) {
                            contextBudget.appendMessages(listOf(
                                LlmMessage(role = "user", content = buildPreheatPrompt(artist)),
                                LlmMessage(role = "assistant", content = preheatText),
                            ))
                        }

                        processChunk(chunk, artist, isMixed = false)

                        // chunk 结束后清 history，防止 chunk N-1 的输出污染 chunk N 的 LLM 上下文
                        clearHistory()
                    }

                    // 歌手处理完，清预热缓存（clearHistory 已在 chunk 循环内完成）
                    preheatCache = null
                }

                is EnrichWorkUnit.MixedGroup -> {
                    Logger.i("Agent.Enrich") { "[$agentId] MixedGroup (${workUnit.songs.size} songs)" }
                    // 混合组跳过 Round 0 —— 没有共同歌手可以预热
                    // 同样加 chunk 拆分保护（Repository 层 mixGroupSize 通常已 < CHUNK_SPLIT_SIZE，但兜底）
                    val chunks = workUnit.songs.chunked(CHUNK_SPLIT_SIZE)
                    for (chunk in chunks) {
                        if (!isActive) break
                        stopSignal?.waitResume()
                        if (stopSignal?.shouldSoftStop() == true) break

                        processChunk(chunk, GROUP_KEY_MIXED, isMixed = true)
                        clearHistory()
                    }
                }
            }
        }

        isActive = false
        runState = AgentRunState.UNREGISTERED
        Logger.i("Agent.Enrich") { "[$agentId] runLoop exited (processed=$processedCount success=$successCount failed=$failCount)" }
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
    private suspend fun processChunk(songs: List<MusicInfo>, groupKey: String, isMixed: Boolean) {
        val config = enrichConfig

        processedCount += songs.size

        if (config == null) {
            Logger.w("Agent.Enrich") { "[$agentId] No AiEndpointConfig, skipping chunk '$groupKey' (${songs.size} songs)" }
            failCount += songs.size
            return
        }

        Logger.i("Agent.Enrich") { "[$agentId] processing chunk '$groupKey' (${songs.size} songs, mixed=$isMixed)" }

        // ========== Round 1: 枚举批量 ==========
        val enumText = callAndLog(config, "Round 1 enum", buildEnumPrompt(songs, groupKey, isMixed))
        if (enumText == null) {
            Logger.e("Agent.Enrich") { "[$agentId] chunk '$groupKey' Round 1 failed — aborting chunk" }
            failCount += songs.size
            return
        }
        val enumMap = runCatching { parseEnumBatch(enumText, songs).toMutableMap() }
            .getOrElse { e ->
                Logger.e("Agent.Enrich", e) { "[$agentId] chunk '$groupKey' Round 1 parse failed: ${e.message}" }
                failCount += songs.size
                return
            }

        // ========== Round 1.5: 枚举自检 ==========
        // 失败不致命——只是少了一道安全网
        runCatching {
            val selfCheckText = callAndLog(config, "Round 1.5 enum self-check",
                buildEnumSelfCheckPrompt(songs, enumMap, groupKey, isMixed))
            if (selfCheckText != null) {
                val selfCheckPatchMap = parseEnumSelfCheckFullList(selfCheckText, songs)
                applyEnumReplacements(enumMap, selfCheckPatchMap, songs)
                Logger.i("Agent.Enrich") { "[$agentId] chunk '$groupKey' Round 1.5 patched ${selfCheckPatchMap.size} song(s)" }
            }
        }.onFailure { e ->
            Logger.w("Agent.Enrich", e) { "[$agentId] chunk '$groupKey' Round 1.5 self-check failed (non-fatal): ${e.message}" }
        }

        // ========== Round 2a: 自由文本 Easy（description + singerIntroduce） ==========
        val easyText = callAndLog(config, "Round 2a freeText-easy",
            buildFreeTextEasyPrompt(songs, enumMap, groupKey, isMixed))
        val easyMap = if (easyText != null) {
            runCatching { parseFreeTextEasyBatch(easyText, songs) }
                .getOrElse { e ->
                    Logger.e("Agent.Enrich", e) { "[$agentId] chunk '$groupKey' Round 2a parse failed: ${e.message}" }
                    emptyMap()
                }
        } else {
            Logger.w("Agent.Enrich") { "[$agentId] chunk '$groupKey' Round 2a call failed — description/singerIntroduce will be empty" }
            emptyMap()
        }

        // ========== Round 2b: 自由文本 Facts（极易编造，强化 -暂无） ==========
        val factsText = callAndLog(config, "Round 2b freeText-facts",
            buildFreeTextFactsPrompt(songs, enumMap, groupKey, isMixed))
        val factsMap = if (factsText != null) {
            runCatching { parseFreeTextFactsBatch(factsText, songs) }
                .getOrElse { e ->
                    Logger.e("Agent.Enrich", e) { "[$agentId] chunk '$groupKey' Round 2b parse failed: ${e.message}" }
                    emptyMap()
                }
        } else {
            Logger.w("Agent.Enrich") { "[$agentId] chunk '$groupKey' Round 2b call failed — facts fields will be empty/-暂无" }
            emptyMap()
        }

        // ========== Round 3: 总体反思 ==========
        // 先构造中间 DailyMusicInfo（枚举 + easy + facts），让反思轮能看到"完整"状态再 patch
        val draftResults = songs.associate { song ->
            val daily = mergeAll(
                enum = enumMap[song.music.id],
                easy = easyMap[song.music.id],
                facts = factsMap[song.music.id],
            )
            song.music.id to daily
        }.toMutableMap()

        runCatching {
            val reflectionText = callAndLog(config, "Round 3 reflection",
                buildReflectionPrompt(songs, draftResults, groupKey, isMixed))
            if (reflectionText != null) {
                val patches = parseReflectionPatches(reflectionText, songs)
                applyReflectionPatches(draftResults, patches, songs)
                Logger.i("Agent.Enrich") { "[$agentId] chunk '$groupKey' Round 3 patched ${patches.size} field(s)" }
            }
        }.onFailure { e ->
            Logger.w("Agent.Enrich", e) { "[$agentId] chunk '$groupKey' Round 3 reflection failed (non-fatal): ${e.message}" }
        }

        // ========== 最终写 DB ==========
        songs.forEach { song ->
            val daily = draftResults[song.music.id]
            if (daily != null && enumMap.containsKey(song.music.id)) {
                writeSongResult(song, daily)
            } else {
                failCount++
            }
        }

        presenceBus?.emit(PresenceEvent.AgentProgress(
            agentId = agentId,
            processed = processedCount,
            total = currentUnitSize,
        ))

        Logger.i("Agent.Enrich") { "[$agentId] chunk '$groupKey' done: success=$successCount fail=$failCount" }
    }

    /** 便捷封装：callLlmText + 日志。返回 null 表示失败。 */
    private suspend fun callAndLog(config: AiEndpointConfig, roundLabel: String, userPrompt: String): String? {
        val text = contextBudget.callLlmText(
            config = config,
            systemPrompt = systemPrompt,
            newMessages = listOf(LlmMessage(role = "user", content = userPrompt)),
        )
        if (text == null) {
            Logger.w("Agent.Enrich") { "[$agentId] $roundLabel → null" }
        }
        return text
    }

    /** 单首写 DB */
    private suspend fun writeSongResult(song: MusicInfo, dailyInfo: DailyMusicInfo) {
        musicRepository.insertMusicExtra(song.music.id, dailyInfo)
        writeLabelsFromDailyInfo(song.music.id, dailyInfo)
        successCount++
    }

    // ===== JSON 批量解析 =====

    /** Round 1 枚举批量 */
    private fun parseEnumBatch(text: String, songs: List<MusicInfo>): Map<Long, EnumOnlyResult> {
        val elements = extractJsonArrayElements(text)
        val result = mutableMapOf<Long, EnumOnlyResult>()
        for ((index, elementText) in elements.withIndex()) {
            if (index >= songs.size) break
            val song = songs[index]
            runCatching { json.decodeFromString<EnumOnlyResult>(elementText) }
                .onSuccess { result[song.music.id] = it }
                .onFailure { e ->
                    Logger.w("Agent.Enrich", e) { "[$agentId] song ${song.music.id} (${song.music.title}) Round 1 parse failed: ${e.message}" }
                }
        }
        return result
    }

    /** Round 1.5 枚举自检 —— LLM 返回完整枚举对象列表（全量 replace，不是 partial patch） */
    private fun parseEnumSelfCheckFullList(text: String, songs: List<MusicInfo>): Map<Long, EnumOnlyResult> {
        val elements = extractJsonArrayElements(text)
        val result = mutableMapOf<Long, EnumOnlyResult>()
        for ((index, elementText) in elements.withIndex()) {
            if (index >= songs.size) break
            val song = songs[index]
            runCatching { json.decodeFromString<EnumOnlyResult>(elementText) }
                .onSuccess { result[song.music.id] = it }
                .onFailure { e ->
                    Logger.w("Agent.Enrich", e) { "[$agentId] song ${song.music.id} Round 1.5 parse failed: ${e.message}" }
                }
        }
        return result
    }

    /** 把 Round 1.5 的全量 replace 应用到 enumMap（patch 非空字段才覆盖） */
    private fun applyEnumReplacements(
        enumMap: MutableMap<Long, EnumOnlyResult>,
        patchMap: Map<Long, EnumOnlyResult>,
        songs: List<MusicInfo>,
    ) {
        for (song in songs) {
            val id = song.music.id
            val original = enumMap[id] ?: continue
            val patch = patchMap[id] ?: continue
            // 枚举自检只应修正有问题的字段；覆盖策略：patch 非空就用 patch 的
            val merged = original.copy(
                genre = patch.genre.ifEmpty { original.genre },
                mood = patch.mood.ifEmpty { original.mood },
                scenario = patch.scenario.ifEmpty { original.scenario },
                language = patch.language.ifBlank { original.language },
                era = patch.era.ifBlank { original.era },
            )
            enumMap[id] = merged
        }
    }

    /** Round 2a 自由文本 Easy */
    private fun parseFreeTextEasyBatch(text: String, songs: List<MusicInfo>): Map<Long, FreeTextEasyResult> {
        val elements = extractJsonArrayElements(text)
        val result = mutableMapOf<Long, FreeTextEasyResult>()
        for ((index, elementText) in elements.withIndex()) {
            if (index >= songs.size) break
            val song = songs[index]
            runCatching { json.decodeFromString<FreeTextEasyResult>(elementText) }
                .onSuccess { result[song.music.id] = it }
                .onFailure { e ->
                    Logger.w("Agent.Enrich", e) { "[$agentId] song ${song.music.id} Round 2a parse failed: ${e.message}" }
                }
        }
        return result
    }

    /** Round 2b 自由文本 Facts */
    private fun parseFreeTextFactsBatch(text: String, songs: List<MusicInfo>): Map<Long, FreeTextFactsResult> {
        val elements = extractJsonArrayElements(text)
        val result = mutableMapOf<Long, FreeTextFactsResult>()
        for ((index, elementText) in elements.withIndex()) {
            if (index >= songs.size) break
            val song = songs[index]
            runCatching { json.decodeFromString<FreeTextFactsResult>(elementText) }
                .onSuccess { result[song.music.id] = it }
                .onFailure { e ->
                    Logger.w("Agent.Enrich", e) { "[$agentId] song ${song.music.id} Round 2b parse failed: ${e.message}" }
                }
        }
        return result
    }

    /** 合并 enum + easy + facts → 完整 DailyMusicInfo */
    private fun mergeAll(
        enum: EnumOnlyResult?,
        easy: FreeTextEasyResult?,
        facts: FreeTextFactsResult?,
    ): DailyMusicInfo {
        return DailyMusicInfo(
            genre = enum?.genre ?: emptyList(),
            mood = enum?.mood ?: emptyList(),
            scenario = enum?.scenario ?: emptyList(),
            language = enum?.language?.ifBlank { "UNKNOWN" } ?: "UNKNOWN",
            era = enum?.era?.ifBlank { "UNKNOWN" } ?: "UNKNOWN",
            description = easy?.description ?: "",
            singerIntroduce = easy?.singerIntroduce ?: "",
            rewards = facts?.rewards?.normalizeFacts() ?: "",
            lyric = facts?.lyric?.normalizeFacts() ?: "",
            backgroundIntroduce = facts?.backgroundIntroduce?.normalizeFacts() ?: "",
            relevantMusic = facts?.relevantMusic?.normalizeFacts() ?: "",
            errorInfo = "None",
        )
    }

    /** Round 3 总体反思 patch 列表 */
    private fun parseReflectionPatches(text: String, songs: List<MusicInfo>): List<ReflectionPatch> {
        val elements = extractJsonArrayElements(text)
        val result = mutableListOf<ReflectionPatch>()
        for (elementText in elements) {
            runCatching { json.decodeFromString<ReflectionPatch>(elementText) }
                .onSuccess {
                    // index 可能是 1-based（LLM 习惯），规整到 0-based
                    val idx = it.index - 1
                    if (idx in songs.indices) {
                        result.add(it.copy(index = idx))
                    }
                }
                .onFailure { e ->
                    Logger.w("Agent.Enrich", e) { "[$agentId] Round 3 patch parse failed: ${e.message}" }
                }
        }
        return result
    }

    /** 把 Round 3 patches 应用到 draftResults（按 index 找 song） */
    private fun applyReflectionPatches(
        draftResults: MutableMap<Long, DailyMusicInfo>,
        patches: List<ReflectionPatch>,
        songs: List<MusicInfo>,
    ) {
        for (patch in patches) {
            val song = songs.getOrNull(patch.index) ?: continue
            val id = song.music.id
            val current = draftResults[id] ?: continue
            draftResults[id] = applyPatch(current, patch)
        }
    }

    /** 单字段 patch 应用（通过 copy + when 分支，类型安全） */
    private fun applyPatch(current: DailyMusicInfo, patch: ReflectionPatch): DailyMusicInfo {
        val fix = patch.fix.trim()
        val isEmptyMarker = fix.isBlank() || EMPTY_FACT_MARKERS.any { fix.equals(it, ignoreCase = true) }
        return when (patch.field.lowercase()) {
            "genre" -> current.copy(genre = if (isEmptyMarker) emptyList() else fix.split(',').map { it.trim() }.filter { it.isNotBlank() })
            "mood" -> current.copy(mood = if (isEmptyMarker) emptyList() else fix.split(',').map { it.trim() }.filter { it.isNotBlank() })
            "scenario" -> current.copy(scenario = if (isEmptyMarker) emptyList() else fix.split(',').map { it.trim() }.filter { it.isNotBlank() })
            "language" -> current.copy(language = if (isEmptyMarker) "UNKNOWN" else fix.trim())
            "era" -> current.copy(era = if (isEmptyMarker) "UNKNOWN" else fix.trim())
            "description" -> current.copy(description = if (isEmptyMarker) "" else fix)
            "singerintroduce" -> current.copy(singerIntroduce = if (isEmptyMarker) "" else fix)
            "rewards" -> current.copy(rewards = if (isEmptyMarker) "" else fix)
            "lyric" -> current.copy(lyric = if (isEmptyMarker) "" else fix)
            "backgroundintroduce" -> current.copy(backgroundIntroduce = if (isEmptyMarker) "" else fix)
            "relevantmusic" -> current.copy(relevantMusic = if (isEmptyMarker) "" else fix)
            else -> {
                Logger.w("Agent.Enrich") { "[$agentId] Round 3 unknown patch field: ${patch.field} — ignoring" }
                current
            }
        }
    }

    private val EMPTY_FACT_MARKERS = setOf(
        "-暂无", "none", "null", "待定", "不详", "未知", "暂无相关信息",
        "tbd", "n/a", "unknown", "-", "--", "—",
    )

    /** "-暂无" / "NONE" / "null" 等 → 空字符串（事实字段归一化） */
    private fun String.normalizeFacts(): String {
        val trimmed = this.trim()
        if (trimmed.isBlank()) return ""
        if (EMPTY_FACT_MARKERS.any { trimmed.equals(it, ignoreCase = true) }) return ""
        return trimmed
    }

    // ===== JSON 工具 =====

    private fun extractJsonArrayElements(text: String): List<String> {
        val cleaned = extractJsonBlock(text).trim()
        val firstBracket = cleaned.indexOf('[')
        val lastBracket = cleaned.lastIndexOf(']')
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            val arrayContent = cleaned.substring(firstBracket + 1, lastBracket)
            return splitJsonObjects(arrayContent)
        }
        // 没有正确的数组包装 → 尝试兜底解析
        return when {
            cleaned.startsWith('{') -> listOf(cleaned)
            cleaned.contains('}') && cleaned.contains('{') -> splitJsonObjects(cleaned)
            else -> emptyList()
        }
    }

    private fun splitJsonObjects(content: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escapeNext = false
        val current = StringBuilder()

        for (char in content) {
            when {
                escapeNext -> {
                    current.append(char)
                    escapeNext = false
                }
                char == '\\' && inString -> {
                    current.append(char)
                    escapeNext = true
                }
                char == '"' -> {
                    inString = !inString
                    current.append(char)
                }
                !inString && char == '{' -> {
                    depth++
                    current.append(char)
                }
                !inString && char == '}' -> {
                    depth--
                    current.append(char)
                    if (depth == 0) {
                        result.add(current.toString().trim())
                        current.clear()
                    }
                }
                !inString && depth == 0 && char == ',' -> {
                    // 根级别逗号 —— 跳过
                }
                else -> current.append(char)
            }
        }
        return result
    }

    private fun extractJsonBlock(text: String): String {
        val codeBlockRegex = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""")
        val match = codeBlockRegex.find(text)
        if (match != null) return match.groupValues[1].trim()

        val firstBrace = text.indexOfAny(charArrayOf('{', '['))
        val lastBrace = text.lastIndexOfAny(charArrayOf('}', ']'))
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1)
        }
        return text.trim()
    }

    // ===== DB 写入辅助 =====

    private suspend fun writeLabelsFromDailyInfo(musicId: Long, info: DailyMusicInfo) {
        info.genre.forEach { addLabelSafe(musicId, LabelCategory.GENRE, it) }
        info.mood.forEach { addLabelSafe(musicId, LabelCategory.MOOD, it) }
        info.scenario.forEach { addLabelSafe(musicId, LabelCategory.SCENARIO, it) }
        info.language.takeIf { it.isNotBlank() }?.let { addLabelSafe(musicId, LabelCategory.LANGUAGE, it) }
        info.era.takeIf { it.isNotBlank() }?.let { addLabelSafe(musicId, LabelCategory.ERA, it) }
    }

    private suspend fun addLabelSafe(musicId: Long, category: LabelCategory, rawLabel: String) {
        val name = LabelName.Companion.match(rawLabel)
        if (name == null || name == LabelName.UNKNOWN) return
        musicRepository.addMusicLabel(MusicLabel(musicId, category, name))
    }

    private fun clearHistory() {
        contextBudget.clearHistory()
    }

    // ===== F5：prompt 定义（角色由 Master 注入；schema 由 Enrich 自己管） =====

    companion object {

        private const val GROUP_KEY_MIXED = "__mixed__"

        /** 超大歌手拆分阈值：单次 LLM call 最多处理这么多首 */
        private const val CHUNK_SPLIT_SIZE = 20

        private const val GENRE_CANDIDATES = """ROCK, POP, JAZZ, CLASSICAL, HIPHOP, ELECTRONIC, FOLK, RNB, METAL, COUNTRY, BLUES, REGGAE, PUNK, FUNK, SOUL, INDIE"""
        private const val MOOD_CANDIDATES = """HAPPY, SAD, ENERGETIC, CALM, ROMANTIC, ANGRY, LONELY, UPLIFTING, MYSTERIOUS, DARK, MELANCHOLY, HOPEFUL"""
        private const val SCENARIO_CANDIDATES = """WORKOUT, SLEEP, PARTY, DRIVING, STUDY, RELAX, DINNER, MEDITATION, FOCUS, TRAVEL, MORNING, NIGHT"""
        private const val LANGUAGE_CANDIDATES = """ENGLISH, CHINESE, JAPANESE, KOREAN, OTHERS"""
        private const val ERA_CANDIDATES = """SIXTIES, SEVENTIES, EIGHTIES, NINETIES, TWO_THOUSANDS, TWENTY_TENS, TWENTY_TWENTIES"""

        /**
         * Master 注入的 system prompt —— 角色定义 + 任务参数。
         * F5：Enrich 不自演化角色。
         */
        fun buildSystemPrompt(targetCoverage: Float): String = """
你是一位专业的音乐编辑，精通各类音乐风格、流派发展历史和艺术家背景。

你将对一组歌曲进行 5 轮渐进式富化：枚举标签 → 枚举自检 → 自由文本（易）→ 自由文本（难）→ 总体反思。所有返回的内容都用于 AI 标签生成，可能不完全准确。

⚠️ 核心约束：不确定就不要编造。编造的错误信息比空着更糟糕。
- 对歌手背景/奖项/歌词/创作背景，只有 100% 确定的才能写
- genre/mood 枚举如果拿不准就少标（不超过候选值的一半）
- language/era 拿不准就返回 UNKNOWN

Master 当前任务：
- 目标覆盖率：${(targetCoverage * 100).toInt()}%
""".trimIndent()

        // ---------- Round 0: 预热 ----------

        internal fun buildPreheatPrompt(artist: String): String = """
请用 2-3 段话介绍一下 "${artist}" 这位歌手/乐队：
- 音乐风格和流派定位
- 主要成就和代表性作品
- 风格演变或标志性的声音元素

这是后面分析 ${artist} 多首歌曲的共同背景，请尽量准确。如果这个歌手你不太熟悉，简短说你知道的就行，不要编造。
""".trimIndent()

        // ---------- Round 1: 枚举批量 ----------

        internal fun buildEnumPrompt(
            songs: List<MusicInfo>,
            groupKey: String,
            isMixedGroup: Boolean,
        ): String {
            val songsList = songs.mapIndexed { i, song ->
                "${i + 1}. ${song.music.title} — ${song.music.artist}"
            }.joinToString("\n")

            val groupContext = if (isMixedGroup) {
                "这是混合歌手组，每首歌独立分析即可。"
            } else {
                "这批 ${songs.size} 首都是 ${groupKey} 的歌曲（前面你已经介绍过这位歌手的背景），请确保 genre/mood 分布合理——既要有变化又整体符合 ${groupKey} 的风格定位。"
            }

            return """
$groupContext

现在为每首歌标注 5 个枚举字段。候选值：
- genre（选1-3个）：$GENRE_CANDIDATES
- mood（选2-4个）：$MOOD_CANDIDATES
- scenario（选1-3个）：$SCENARIO_CANDIDATES
- language（选1个）：$LANGUAGE_CANDIDATES
- era（选1个）：$ERA_CANDIDATES

规则：
1. genre 不要贪多，不确定的候选值不要加
2. language/era 拿不准就返回 UNKNOWN

歌曲列表：
$songsList

严格返回 JSON array，顺序一致：
[
  {"genre":["..."],"mood":["..."],"scenario":["..."],"language":"...","era":"..."},
  ...（共 ${songs.size} 个元素）
]

只返回 JSON，不要加任何解释或 markdown。
""".trimIndent()
        }

        // ---------- Round 1.5: 枚举自检 ----------

        internal fun buildEnumSelfCheckPrompt(
            songs: List<MusicInfo>,
            enumMap: Map<Long, EnumOnlyResult>,
            groupKey: String,
            isMixedGroup: Boolean,
        ): String {
            // 把 Round 1 结果以可读形式列出来
            val enumSummary = songs.mapIndexed { i, song ->
                val e = enumMap[song.music.id]
                "${i + 1}. ${song.music.title} → genre=${e?.genre?.joinToString("/") ?: "?"}, mood=${e?.mood?.joinToString("/") ?: "?"}, scenario=${e?.scenario?.joinToString("/") ?: "?"}, language=${e?.language ?: "?"}, era=${e?.era ?: "?"}"
            }.joinToString("\n")

            val checks = if (isMixedGroup) {
                """1. 每首歌的 language 是否跟歌手国籍/歌词语言大致匹配？
2. era 是否跟歌手出道年代大致匹配？"""
            } else {
                """1. genre/mood 分布是否跟你前面介绍的 ${groupKey} 的风格定位矛盾？
2. language 是否跟 ${groupKey} 的主要语言匹配？
3. era 分布是否合理——${groupKey} 活跃年代大致在哪个时期？有没有标到离谱的（比如 60 年代的歌手标了 2020s）？"""
            }

            return """
请回头检查你刚才为这 ${songs.size} 首歌标得枚举，有没有明显错误。

刚才的标注结果：
$enumSummary

请重点检查：
$checks

- 如果某首歌完全没问题，返回跟原来一样的就行
- 如果某首歌有问题，只修有问题的字段（比如 language 从 ENGLISH 改成 CHINESE）
- 不要动没问题的字段

严格返回 JSON array，顺序一致（即使只修了一首也要返回全部 ${songs.size} 个元素）：
[
  {"genre":["..."],"mood":["..."],"scenario":["..."],"language":"...","era":"..."},
  ...
]

只返回 JSON。
""".trimIndent()
        }

        // ---------- Round 2a: 自由文本 Easy ----------

        internal fun buildFreeTextEasyPrompt(
            songs: List<MusicInfo>,
            enumMap: Map<Long, EnumOnlyResult>,
            groupKey: String,
            isMixedGroup: Boolean,
        ): String {
            val songsWithContext = songs.mapIndexed { i, song ->
                val e = enumMap[song.music.id]
                "${i + 1}. ${song.music.title} — ${song.music.artist} [genre=${e?.genre?.joinToString(",") ?: "待补充"}, mood=${e?.mood?.joinToString(",") ?: "待补充"}]"
            }.joinToString("\n")

            return """
基于刚才的 genre/mood 判断，现在输出两个"相对容易"的自由文本字段。

歌曲列表：
$songsWithContext

字段说明：
- description: 1-2 句话描述歌曲主题和情感，参考上面的 genre/mood 来写
- singerIntroduce: 2-3 句话介绍歌手背景

${if (!isMixedGroup) "（前面你已经介绍过 ${groupKey} 的背景，可以基于那个来写 singerIntroduce）" else "（混合歌手组，每首歌的 singerIntroduce 请基于歌曲标题和风格合理推测）"}

严格返回 JSON array，顺序一致：
[
  {"description":"...","singerIntroduce":"..."},
  ...（共 ${songs.size} 个元素）
]

只返回 JSON，不要加任何解释或 markdown。
""".trimIndent()
        }

        // ---------- Round 2b: 自由文本 Facts ----------

        internal fun buildFreeTextFactsPrompt(
            songs: List<MusicInfo>,
            enumMap: Map<Long, EnumOnlyResult>,
            groupKey: String,
            isMixedGroup: Boolean,
        ): String {
            val songsList = songs.mapIndexed { i, song ->
                "${i + 1}. ${song.music.title} — ${song.music.artist}"
            }.joinToString("\n")

            return """
现在输出 4 个"容易编造"的事实字段。

⚠️ 核心规则：编造比空着更糟糕。只有你 100% 确定的内容才能写，否则返回 -暂无。

歌曲列表：
$songsList

字段说明：
- rewards: 歌手或歌曲获得的重要奖项，不确定返回 -暂无
- lyric: 1-2 句最广为人知的歌词，不确定返回 -暂无（不要编造歌词！）
- backgroundIntroduce: 创作背景灵感来源，不确定返回 -暂无
- relevantMusic: 相似歌曲（最多 3 首），逗号分隔字符串，不确定返回 -暂无

判断标准：
- rewards/lyric 你基本不可能 100% 确定 → 大概率全部返回 -暂无 是对的
- backgroundIntroduce/relevantMusic 如果能基于风格合理推测可以写，但不确定就 -暂无

严格返回 JSON array，顺序一致：
[
  {"rewards":"...","lyric":"...","backgroundIntroduce":"...","relevantMusic":"..."},
  ...（共 ${songs.size} 个元素）
]

只返回 JSON，不要加任何解释或 markdown。
""".trimIndent()
        }

        // ---------- Round 3: 总体反思 ----------

        internal fun buildReflectionPrompt(
            songs: List<MusicInfo>,
            draftResults: Map<Long, DailyMusicInfo>,
            groupKey: String,
            isMixedGroup: Boolean,
        ): String {
            val enumSummary = songs.mapIndexed { i, song ->
                val d = draftResults[song.music.id]
                "${i + 1}. ${song.music.title} — ${song.music.artist} → genre=${d?.genre?.joinToString("/") ?: "?"}, mood=${d?.mood?.joinToString("/") ?: "?"}, language=${d?.language ?: "?"}, era=${d?.era ?: "?"}"
            }.joinToString("\n")

            val textSummary = songs.mapIndexed { i, song ->
                val d = draftResults[song.music.id]
                "${i + 1}. description=\"${d?.description?.take(40) ?: ""}...\", singerIntroduce=\"${d?.singerIntroduce?.take(40) ?: ""}...\", lyric=${d?.lyric?.take(30) ?: "-暂无"}, rewards=${d?.rewards?.take(30) ?: "-暂无"}"
            }.joinToString("\n")

            val contextIntro = if (!isMixedGroup) {
                "前面你已经介绍过 ${groupKey} 的背景，请对比背景和标注是否矛盾。"
            } else {
                "这是混合歌手组，请独立检查每首歌。"
            }

            return """
最后做一次总体反思。$contextIntro

你为这 ${songs.size} 首歌完成了全部富化，下面是中间结果（枚举 + 自由文本）：

【枚举】
$enumSummary

【自由文本片段】
$textSummary

检查 5 类问题：
1. **枚举 vs 自由文本矛盾**：比如 mood=SAD 但 description 写"欢快的派对歌曲"
2. **预热知识 vs 枚举矛盾**：比如你说这是华语歌手但 language=ENGLISH；或者 era 标到歌手出道前
3. **同歌手分布异常**：genre/era 分布是否整体符合歌手风格（不要 5 首都标 POP 完全没变化）
4. **自由文本内部矛盾**：singerIntroduce 说"2018 出道新人"但 backgroundIntroduce 说"80 年代民歌运动"
5. **编造检测**：rewards/lyric/backgroundIntroduce 是不是看起来像编造？如果不确定应返回 -暂无

⚠️ 只返回需要修正的 patch，**没问题的字段别写**。patch 格式：
- index: 歌曲序号（从 1 开始）
- field: 要修的字段名（genre/mood/scenario/language/era/description/singerIntroduce/rewards/lyric/backgroundIntroduce/relevantMusic）
- fix: 修正后的值（枚举列表用逗号分隔字符串；自由文本直接文本；置空用 "-暂无"）

如果全部没问题，返回空数组 []

严格返回 JSON array：
[
  {"index":2,"field":"language","fix":"CHINESE"},
  {"index":4,"field":"description","fix":"修正后的描述文本..."},
  {"index":5,"field":"lyric","fix":"-暂无"}
]

只返回 JSON，不要加任何解释。
""".trimIndent()
        }
    }
}

// ===== 内部数据类 =====

/** Round 1 枚举字段结果 */
internal data class EnumOnlyResult(
    val genre: List<String>,
    val mood: List<String>,
    val scenario: List<String>,
    val language: String = "",
    val era: String = "",
)

/** Round 2a 自由文本 Easy —— description + singerIntroduce */
internal data class FreeTextEasyResult(
    val description: String = "",
    val singerIntroduce: String = "",
)

/** Round 2b 自由文本 Facts —— 极易编造的字段 */
internal data class FreeTextFactsResult(
    val rewards: String = "",
    val lyric: String = "",
    val backgroundIntroduce: String = "",
    val relevantMusic: String = "",
)

/** Round 3 总体反思 patch —— 只修单字段 */
@Serializable
internal data class ReflectionPatch(
    val index: Int,     // 歌曲在组内的序号（prompt 里从 1 开始；parse 后会规整为 0-based）
    val field: String,  // 字段名
    val fix: String,    // 修正值；"-暂无" 或 blank 表示置空
)
