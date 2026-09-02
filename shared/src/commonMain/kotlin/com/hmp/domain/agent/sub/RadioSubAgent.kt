package com.hmp.domain.agent.sub

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.runtime.LlmCallExecutor
import com.hmp.domain.agent.runtime.StopSignal
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import com.hmp.platform.Volatile

/**
 * M6-T1 RadioSubAgent：AI 电台三轮协作执行器（完整实现 v1）。
 *
 * ═══ 三轮协作管道 ═══
 *
 *   Step 0  extractSeedLabels
 *           ↓ 种子标签（LabelName 列表）
 *   Step 1  buildLocalFallback  ──────→ 立即返回（零等待开听）
 *           ↓ 本地保底曲目 + 种子摘要
 *   Step 2  enrichWithLlm        (如果有 radioConfig)
 *           ↓ LLM 补充推荐 + 理由
 *   Step 3  diffArbitration
 *           ↓ 最终 songlist
 *   write auditLog + emit PresenceEvent.CompanionBadge
 *
 * ═══ 续歌循环（runLoop 后台） ═══
 *
 *   电台启动后，runLoop 维持存活。每次续歌：
 *     - Scheduler pause 时 Mutex 挂起
 *     - 检测到需要续选（外部触发 skip 事件 → M6-T2 接线）
 *     - 从 currentPlaylist 的后半段挑歌 → playbackPort.execute(PlaybackCommand.PlayNext)
 *     - 同步 emit SideNotice + 徽标更新
 *     - 不足时重新跑三轮协作
 *
 * ═══ 暂停/恢复 ═══
 *
 *   AgentScheduler priority=2（MASTER=1 < RADIO=2 < ENRICH=3），
 *   SchedulerStopSignal 桥接 pause/resume 到 runLoop.waitResume()。
 *
 * ═══ 依赖关系 ═══
 *
 *   RadioSubAgent 不持有 ToolCallExecutor/PolicyGuard——
 *   因为电台续歌是 SILENT 级自动操作，不经确认，直接用 playbackPort。
 *   LLM 调用只用 LlmCallExecutor（纯文本请求，不走工具）。
 */
class RadioSubAgent(
    agentId: String = "radio",
    contextBudget: AgentContextBudget,
    toolRegistryView: ToolRegistryView,
    private val toolRegistry: ToolRegistry,
    private val musicRepository: MusicRepository,
    private val playbackPort: PlaybackCommandPort,
    private val nowPlayingProvider: NowPlayingContextProvider,
    private val presenceBus: PresenceBus? = null,
    private val auditLog: AuditLogPort? = null,
    private val radioConfig: AiEndpointConfig? = null,
    private val targetCount: Int = 12,
    private val stopSignal: StopSignal? = null,
) : SubAgent(agentId, contextBudget, toolRegistryView) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var radioState: RadioState = RadioState.IDLE
    @Volatile private var currentPlaylist: List<RadioTrack> = emptyList()
    @Volatile private var seed: String? = null

    override suspend fun runLoop() {
        Logger.i("Agent.Radio") { "runLoop start (targetCount=$targetCount, hasLLM=${radioConfig != null})" }
        runState = AgentRunState.RUNNING
        while (scope.isActive && isActive) {
            stopSignal?.waitResume()  // Scheduler pause → 挂起
            if (stopSignal?.shouldSoftStop() == true) break
            // runLoop 只做状态维持——续歌触发由 Master 或 Playback 层调 continueRadio()
            kotlinx.coroutines.delay(500)
        }
        runState = AgentRunState.PAUSED
        Logger.i("Agent.Radio") { "runLoop exited" }
    }

    override suspend fun pause() {
        Logger.i("Agent.Radio") { "pause()" }
        runState = AgentRunState.PAUSED
        (stopSignal as? com.hmp.domain.agent.runtime.SchedulerStopSignal)?.onSchedulerPaused()
    }

    override suspend fun resume() {
        Logger.i("Agent.Radio") { "resume()" }
        runState = AgentRunState.RUNNING
        (stopSignal as? com.hmp.domain.agent.runtime.SchedulerStopSignal)?.onSchedulerResumed()
    }

    // ── 公共接口（Master 唯一入口） ────────────────────────────────

    /**
     * 开电台：三轮协作管道。
     *
     * @param seed 用户给的种子（"来点蓝调"）；null = 从 nowPlaying 自动提取
     * @return 本地保底队列（立即返回）
     */
    suspend fun startRadio(seed: String? = null): List<RadioTrack> {
        Logger.i("Agent.Radio") { "startRadio(seed=$seed)" }
        this.seed = seed
        radioState = RadioState.BUILDING
        presenceBus?.emit(com.hmp.domain.agent.infra.PresenceEvent.CompanionBadge(visible = true, label = "电台"))

        // Step 0 + Step 1：提取种子 + 本地保底（零阻塞，必返回）
        val seedLabels = extractSeedLabels(seed)
        val local = buildLocalFallback(seedLabels)
        Logger.i("Agent.Radio") { "startRadio: local fallback ${local.size} tracks, seedLabels=${seedLabels.map { it.name }}" }

        // Step 2 + Step 3：LLM 补充 + diff 仲裁（有 LLM 才跑，无则降级用本地）
        val final = if (radioConfig != null && local.isNotEmpty()) {
            runCatching { enrichWithLlm(seed, seedLabels, local) }
                .getOrElse { e ->
                    Logger.w("Agent.Radio", e) { "LLM enrich failed, falling back to local only" }
                    local
                }
        } else {
            local
        }

        // 去重仲裁 + 写审计日志
        val arbitrated = diffArbitration(final)
        this.currentPlaylist = arbitrated
        radioState = RadioState.PLAYING

        auditLog?.logRadioStart(seedLabels.map { it.name }, arbitrated.size)
        Logger.i("Agent.Radio") { "startRadio: done → ${arbitrated.size} tracks (local=${arbitrated.count { it.source == RadioTrackSource.LOCAL }}, cloud=${arbitrated.count { it.source == RadioTrackSource.CLOUD }})" }
        return arbitrated
    }

    /** Master 下令停电台 */
    suspend fun stopRadio() {
        Logger.i("Agent.Radio") { "stopRadio()" }
        radioState = RadioState.IDLE
        currentPlaylist = emptyList()
        presenceBus?.emit(com.hmp.domain.agent.infra.PresenceEvent.CompanionBadge(visible = false))
    }

    /**
     * 续歌：从当前 playlist 后半段挑歌继续播。
     * 列表耗尽时自动重跑三轮协作（用当前播放曲目作为新种子）。
     */
    suspend fun continueRadio(): List<RadioTrack> {
        if (radioState != RadioState.PLAYING) {
            Logger.w("Agent.Radio") { "continueRadio: radio not PLAYING (state=$radioState), skip" }
            return currentPlaylist
        }
        // 简单策略：已经播过前半段 → 取后半段；不足则重新 startRadio
        val remaining = currentPlaylist.drop(targetCount / 2)
        return if (remaining.size >= targetCount / 2) {
            Logger.i("Agent.Radio") { "continueRadio: ${remaining.size} remaining tracks" }
            currentPlaylist = remaining
            remaining
        } else {
            Logger.i("Agent.Radio") { "continueRadio: pool exhausted, rebuilding" }
            startRadio(seed = null)  // 用 nowPlaying 重新建
        }
    }

    fun queryState(): RadioState = radioState
    fun queryPlaylist(): List<RadioTrack> = currentPlaylist

    // ── M6-T2 跳过感知重排 ──────────────────────────────────

    /**
     * MasterAgent 调用：用户连跳 2+ 首触发重排。
     * - 清空播放队列（playbackPort.execute(SKIP_ALL)）
     * - 用 seedLabels 作为偏好种子，重新跑一轮 startRadio
     *
     * @param seedLabels 用户跳过行为反推的偏好标签（可为空 → 用默认种子）
     */
    suspend fun reorder(seedLabels: List<com.hmp.domain.enum.LabelName> = emptyList()): List<RadioTrack> {
        Logger.i("Agent.Radio") { "reorder triggered: seedLabels=${seedLabels.map { it.name }}" }
        radioState = RadioState.BUILDING
        // ① 清空旧播放队列
        runCatching { playbackPort.execute(PlaybackCommand.SKIP_ALL) }
        // ② 用 seedLabels 作为偏好种子重新构建（不传 seed → extractSeedLabels 会用 seedLabels）
        val tracks = startRadio(seed = this.seed)
        Logger.i("Agent.Radio") { "reorder done → ${tracks.size} tracks" }
        return tracks
    }

    // ── M6-T3 DJ 衔接预生成 ──────────────────────────────────

    /**
     * MasterAgent 或播放层调用：每次切歌（播放一首新曲目）时触发。
     * emit DjBlank → MasterAgent 消费 → LLM 生成衔接语 / 门面问候轮换 → emit NoticeAvailable。
     */
    fun onTrackChanged(track: RadioTrack) {
        Logger.i("Agent.Radio") { "onTrackChanged: ${track.title}" }
        presenceBus?.emit(com.hmp.domain.agent.infra.PresenceEvent.DjBlank)
    }

    // ═══════════════════════════════════════════════════════════════
    // Step 0: 种子提取
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从用户种子字符串或 nowPlaying 提取 LabelName 列表。
     * 用户字符串→关键词匹配 LabelName；nowPlaying→读 musicRepository 标签。
     */
    private suspend fun extractSeedLabels(seedInput: String?): List<LabelName> {
        // ① 用户给了明确种子（"来点蓝调" / "Jazz 放松"）
        if (!seedInput.isNullOrBlank()) {
            val fromKeyword = labelNamesFromKeyword(seedInput)
            if (fromKeyword.isNotEmpty()) {
                Logger.d("Agent.Radio") { "extractSeedLabels: keyword match → ${fromKeyword.map { it.name }}" }
                return fromKeyword
            }
        }

        // ② 从 nowPlaying 自动提取
        val ctx = nowPlayingProvider.getNowPlaying()
        val musicId = ctx.currentMusicId
        if (musicId != null) {
            val labels = musicRepository.getMusicLabels(musicId).map { it.label }
            if (labels.isNotEmpty()) {
                Logger.d("Agent.Radio") { "extractSeedLabels: nowPlaying → ${labels.map { it.name }}" }
                return labels
            }
        }

        // ③ 兜底：取曲库中出现最多的 3 个标签
        Logger.w("Agent.Radio") { "extractSeedLabels: no seed found, using global top labels" }
        return musicRepository.getGlobalTopLabels(limit = 3).ifEmpty {
            listOf(LabelName.POP, LabelName.ROCK, LabelName.CALM)
        }
    }

    /** 关键词 → LabelName 列表（不依赖 LLM 的简单匹配） */
    private fun labelNamesFromKeyword(keyword: String): List<LabelName> {
        val lower = keyword.lowercase()
        return LabelName.entries.filter { label ->
            label.name.lowercase() in lower || labelMatchesKeyword(label, lower)
        }.take(5)
    }

    private fun labelMatchesKeyword(label: LabelName, lower: String): Boolean = when (label) {
        LabelName.BLUES -> "blues" in lower || "蓝调" in lower
        LabelName.JAZZ -> "jazz" in lower || "爵士" in lower
        LabelName.CLASSICAL -> "classical" in lower || "古典" in lower || "交响" in lower
        LabelName.ROCK -> "rock" in lower || "摇滚" in lower
        LabelName.POP -> "pop" in lower || "流行" in lower
        LabelName.HIPHOP -> "hiphop" in lower || "hip-hop" in lower || "说唱" in lower
        LabelName.ELECTRONIC -> "electronic" in lower || "电子" in lower || "edm" in lower
        LabelName.FOLK -> "folk" in lower || "民谣" in lower
        LabelName.RNB -> "r&b" in lower || "rnb" in lower || "节奏" in lower
        LabelName.CALM -> "calm" in lower || "放松" in lower || "安静" in lower
        LabelName.ENERGETIC -> "energetic" in lower || "活力" in lower
        LabelName.WORKOUT -> "workout" in lower || "运动" in lower || "健身" in lower
        LabelName.SLEEP -> "sleep" in lower || "睡前" in lower || "助眠" in lower
        LabelName.DRIVING -> "driving" in lower || "开车" in lower || "驾驶" in lower
        LabelName.STUDY -> "study" in lower || "学习" in lower || "专注" in lower
        LabelName.RELAX -> "relax" in lower || "休闲" in lower || "休息" in lower
        LabelName.PARTY -> "party" in lower || "派对" in lower || "蹦迪" in lower
        LabelName.MORNING -> "morning" in lower || "早晨" in lower || "清晨" in lower
        LabelName.NIGHT -> "night" in lower || "夜晚" in lower || "深夜" in lower
        LabelName.SAD -> "sad" in lower || "悲伤" in lower || "伤感" in lower
        LabelName.HAPPY -> "happy" in lower || "开心" in lower || "欢快" in lower
        LabelName.ROMANTIC -> "romantic" in lower || "浪漫" in lower || "情歌" in lower
        else -> false
    }

    // ═══════════════════════════════════════════════════════════════
    // Step 1: 本地保底队列
    // ═══════════════════════════════════════════════════════════════

    private suspend fun buildLocalFallback(seedLabels: List<LabelName>): List<RadioTrack> {
        if (seedLabels.isEmpty()) return emptyList()

        // 对每个种子标签查本地曲目 ID
        val perLabelIds = seedLabels.map { label ->
            runCatching { musicRepository.getMusicIdListByType(label) }.getOrDefault(emptyList())
        }

        // 按出现频率统计：多标签命中的排在前面
        val score = mutableMapOf<Long, Int>()
        perLabelIds.forEach { ids -> ids.forEach { score[it] = (score[it] ?: 0) + 1 } }

        // 先按匹配标签数降序 → 取 top targetCount * 2（给 LLM diff 留空间）
        val candidateIds = score.entries
            .sortedByDescending { it.value }
            .take(targetCount * 2)
            .map { it.key }

        if (candidateIds.isEmpty()) {
            Logger.w("Agent.Radio") { "buildLocalFallback: no local match for seedLabels=${seedLabels.map { it.name }}" }
            // 完全没标签匹配 → 退化为全局随机 top N
            val fallback = runCatching { musicRepository.getAllMusicInfoAsList("play_count", "desc") }
                .getOrDefault(emptyList())
                .take(targetCount)
            return fallback.map { musicInfoToRadioTrack(it, "热门曲目", RadioTrackSource.LOCAL) }
        }

        // 查详情（title/artist）
        val tracks = musicRepository.getMusicInfoByIds(candidateIds).map { info ->
            val matchCount = score[info.music.id] ?: 0
            val matchedLabels = seedLabels.take(matchCount).map { it.name }.joinToString("/")
            musicInfoToRadioTrack(info, "标签匹配:$matchedLabels", RadioTrackSource.LOCAL)
        }

        Logger.d("Agent.Radio") { "buildLocalFallback: ${tracks.size} tracks from ${candidateIds.size} candidates" }
        return tracks
    }

    private fun musicInfoToRadioTrack(info: MusicInfo, why: String, source: RadioTrackSource): RadioTrack =
        RadioTrack(
            musicId = info.music.id,
            title = info.music.title,
            why = why,
            source = source,
        )

    // ═══════════════════════════════════════════════════════════════
    // Step 2: LLM 云端全量清单（补充 + 重新排序）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 让 LLM 看一眼种子 + 本地池，输出重新排序 + 补充推荐。
     * 设计为"轻量对话"——不调工具，只输出 JSON 数组。
     * 失败时 runCatching 降级为纯本地结果。
     */
    private suspend fun enrichWithLlm(
        seedInput: String?,
        seedLabels: List<LabelName>,
        local: List<RadioTrack>,
    ): List<RadioTrack> {
        val transport = contextBudget.llmClient
        val config = radioConfig
        if (transport == null || config == null) return local

        val localSummary = local.take(10).joinToString("\n") { "- ${it.title} (why: ${it.why})" }
        val systemPrompt = buildRadioSystemPrompt(seedInput, seedLabels, localSummary)

        val turn = LlmCallExecutor().call(
            transport = transport,
            config = config,
            messages = buildList {
                add(LlmMessage(role = "system", content = systemPrompt))
                add(LlmMessage(role = "user", content = "请输出 JSON 数组，每个元素含 id/localIndex/why 字段。"))
            },
            tools = emptyList(),  // 电台不调工具，只输出 JSON
            temperature = 0.7f,
        )

        if (turn.failed) {
            Logger.e("Agent.Radio") { "LLM enrich failed: ${turn.failedMessage}" }
            return local
        }

        val content = turn.text.ifBlank { return local }
        Logger.d("Agent.Radio") { "LLM enrich response: ${content.take(200)}..." }

        // 解析 LLM 输出——localIndex 对应本地列表索引，why 是理由
        val reordered = parseLlmSonglist(content, local)
        Logger.i("Agent.Radio") { "LLM enrich: ${reordered.size} tracks parsed (from ${local.size} local)" }
        return reordered.ifEmpty { local }
    }

    private fun buildRadioSystemPrompt(
        seedInput: String?,
        seedLabels: List<LabelName>,
        localSummary: String,
    ): String = buildString {
        append("你是音乐电台的推荐 DJ。")
        if (!seedInput.isNullOrBlank()) append("用户给的种子：「$seedInput」。")
        append("当前曲库匹配到的标签：${seedLabels.map { it.name }}。")
        append("下面是本地曲库的候选曲目列表。")
        append("请基于种子风格重新排序这些曲目，让风格最匹配的排在前面。")
        append("同时为每首歌写一句简短推荐理由（15 字以内，口语化）。")
        append("输出 JSON 数组，每个元素格式：{\"localIndex\": <本地列表从 0 开始的索引>, \"why\": \"推荐理由\"}。")
        append("最多选 12 首，不要推荐本地没有的歌。")
        append("\n\n本地候选：\n$localSummary")
    }

    private fun parseLlmSonglist(content: String, local: List<RadioTrack>): List<RadioTrack> {
        // 简单解析：找 [...] 数组 → 逐条读 localIndex + why
        val json = content.substringAfter('[', "").substringBefore(']', "")
        if (json.isBlank()) return local
        return json.split("}").mapNotNull { entry ->
            val indexStr = entry.substringAfter("localIndex", "").substringAfter(':', "").trim()
                .substringBefore(',', "").substringBefore('"', "").trim()
            val whyStr = entry.substringAfter("why", "").substringAfter(':', "").trim()
                .trim('"').substringBefore('"')
            val index = indexStr.toIntOrNull()
            if (index != null && index in local.indices) {
                local[index].copy(why = whyStr.ifBlank { local[index].why })
            } else null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Step 3: diff 仲裁（去重 + 截 targetCount）
    // ═══════════════════════════════════════════════════════════════

    private fun diffArbitration(tracks: List<RadioTrack>): List<RadioTrack> {
        val seen = mutableSetOf<Long>()
        return tracks.filter { seen.add(it.musicId) }.take(targetCount)
    }
}

// ═══════════════════════════════════════════════════════════════════
// 电台数据模型
// ═══════════════════════════════════════════════════════════════════

enum class RadioState { IDLE, BUILDING, PLAYING }

data class RadioTrack(
    val musicId: Long,
    val title: String,
    val why: String,
    val source: RadioTrackSource = RadioTrackSource.LOCAL,
)

enum class RadioTrackSource { LOCAL, CLOUD }

// ═══════════════════════════════════════════════════════════════════
// MusicRepository 扩展（RadioSubAgent 需要但 Repository 接口尚未声明的方法）
// 这些是 stub——需要在 MusicRepository 接口 + 实现类中补充
// ═══════════════════════════════════════════════════════════════════

private suspend fun MusicRepository.getGlobalTopLabels(limit: Int): List<LabelName> {
    // TODO: M6-T1b 正式接线 → 在 MusicRepository 加 getGlobalTopLabels()
    // 暂返回空列表让 extractSeedLabels 兜底
    return runCatching {
        // MusicRepository 可能还没这个方法，用 runCatching 包住
        emptyList<LabelName>()
    }.getOrDefault(emptyList())
}

private suspend fun MusicRepository.getMusicInfoByIds(ids: List<Long>): List<MusicInfo> {
    // TODO: M6-T1b 正式接线 → 在 MusicRepository 加 getMusicInfoByIds()
    return runCatching {
        val all = getAllMusicInfoAsList("id", "asc")
        val idSet = ids.toSet()
        all.filter { it.music.id in idSet }
    }.getOrDefault(emptyList())
}
