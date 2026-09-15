package com.hmp.domain.agent.sub

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.PlaybackObservationBus
import com.hmp.domain.agent.port.PauseEvent
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.CommandSource
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.TrackSettledEvent
import com.hmp.domain.agent.port.currentLocalMoment
import com.hmp.domain.agent.port.dayPart
import com.hmp.domain.agent.port.describe
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.runtime.LlmCallExecutor
import com.hmp.domain.agent.runtime.StopSignal
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.hmp.platform.Volatile

/**
 * RadioSubAgent：AI 电台执行器（spec 驱动 —— `docs/7_x/B agent-build/design/agent-radio.md`）。
 *
 * ═══ 核心原则 ═══
 *
 *   忠实记录用户播放操作 → 整合上下文 → 交 LLM 判断 → 串行执行。
 *   没有规则引擎：不推断「喜不喜欢」，只记录「怎么结束的 + 播了多久」。
 *
 * ═══ Start 流程（§7.0） ═══
 *
 *   ① 短时重开且队列/主题没变 → 复用上一段对话（tryResumeRetained，省掉最贵的组装）
 *   ② 已有在播曲目 → 以它为种子，只在它之后接队列（startAfterCurrent，不打断播放）
 *   ③ 无在播 → 本地算法秒开推队列起播（唯一一次改变播放状态）
 *   ④ 注入人格 + 曲库视图 + 情境 + 本地队列 → LLM 返回 musicId 列表
 *   ⑤ 替换「当前播放曲之后」的队列；在播那首不动；结果过期（关闭/暂停）则丢弃
 *
 * ═══ 运行期（§7） ═══
 *
 *   决策内核是 RadioSession（一次电台 = 一段对话）：
 *   观测面事件 → 合并窗口 → LLM 三选一（none/append/replace）→ 串行执行。
 *   模型失败 / 输出不可解析 → 沉默；队列见底且模型没动作 → 本地补歌保底出声。
 *
 * ═══ 世代守卫 ═══
 *
 *   模型调用可能很慢（实测 36 秒）。关闭/暂停一次 generation +1，
 *   返回后世代不匹配的结果一律丢弃 —— 判据是语义上的过期，不是时间上的超时。
 *
 * ═══ 与 Master 的分工 ═══
 *
 *   播放控制（PAUSE / PLAY / 停电台）由 MasterAgent 发；
 *   电台内部编排（秒开、尾部替换、补歌）由自己发，一律 CommandSource.AGENT_INTERNAL。
 *
 * ═══ C1 存在感 ═══
 *
 *   完全静默：模型生成的任何文字只进日志（reason），用户可见的只有
 *   RadioCardState（固定主题 + 动态待播数/下首/最近调整）与固定文案的短时消息。
 */
class RadioSubAgent(
    agentId: String = "radio",
    contextBudget: AgentContextBudget,
    toolRegistryView: ToolRegistryView,
    private val musicRepository: MusicRepository,
    private val playbackPort: PlaybackCommandPort,
    private val nowPlayingProvider: NowPlayingContextProvider,
    private val presenceBus: PresenceBus? = null,
    private val auditLog: AuditLogPort? = null,
    /** 观测面总线：编排动作期间用它静默，避免自己的操作被当成用户行为 */
    private val observationBus: PlaybackObservationBus? = null,
    /** 上一段电台对话（进程内，不落盘）。命中复用条件时直接续上，省掉完整上下文组装 */
    private val retained: RadioConversation? = null,
    /**
     * 用户绕过电台直接操作播放器（在电台暂停期间点了播放）时的回调。
     * MasterAgent 用它收摊 —— 退出电台但**不暂停音乐**（用户已经明确要听）。
     */
    private val onUserTookOver: (suspend () -> Unit)? = null,
    /** AI 端点配置（热更新：MasterAgent.updateAiConfig 可动态替换） */
    private var radioConfig: AiEndpointConfig? = null,
    private val targetCount: Int = 12,
    private val stopSignal: StopSignal? = null,
) : SubAgent(agentId, contextBudget, toolRegistryView) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 世代计数：**每关闭/暂停一次 +1**。
     *
     * 模型调用可能很慢（实测 36 秒）。若在等待期间用户关闭或暂停了电台，
     * 那个结果就已经过期了 —— 拿它去改队列等于"关掉之后还在动"。
     *
     * 所以用世代做守卫：发起调用前记下当前世代，返回后比对，变了就**丢弃结果**。
     * 这比给调用加超时更合理：超时会连「慢但有效」的结果一起扔掉，
     * 而丢弃只扔「已经过期」的 —— 判据是语义上的，不是时间上的。
     */
    @Volatile private var generation = 0

    /** 当前世代是否仍是发起调用时的那个。false = 结果已过期，应丢弃。 */
    private fun isCurrent(g: Int): Boolean = g == generation

    /**
     * 启动期间（BUILDING，最典型是正在等模型返回队列）用户按了暂停。
     *
     * 这个窗口里状态还不是 PLAYING，走不了常规 pauseByUser 路径 —— 但暂停语义不变：
     * 在途的模型结果同样要作废（generation 已 +1），并且收口时状态要落 PAUSED
     * 而不是 PLAYING，否则「暂停后电台还在转」与卡片状态自相矛盾。
     */
    @Volatile private var pausedDuringBuilding = false

    /**
     * 切歌过渡的瞬时暂停去抖。
     *
     * 播放器切歌（下一曲/上一曲/PLAY_BY_ID）会先停旧曲再起新曲，中间发一对
     * `PauseEvent(!resumed)` + `PauseEvent(resumed)` —— 真机日志验证过
     * （21:05:56 切歌 → 24ms 后误暂停 → 97ms 后误判"用户接管"直接退出电台）。
     *
     * 所以「暂停」不能看到事件就信：先挂起，等 [PAUSE_DEBOUNCE_MS] 后
     * **问播放器现在到底在不在播**（权威来源），真的停着才算用户暂停。
     * 窗口内来了 resumed（切歌过渡的正常收尾）→ 取消，当无事发生。
     */
    @Volatile private var pendingPauseCheck: Job? = null

    /** UI 观察电台运行态。所有赋值点同步更新此 StateFlow。 */
    private val _radioState = MutableStateFlow<RadioState>(RadioState.IDLE)
    val radioState: StateFlow<RadioState> = _radioState

    /**
     * 电台短时消息流（W 阶段）——UI 的 RADIO_STATUS 卡右侧实时显示，
     * emit 后 [RADIO_MESSAGE_TTL_MS] 自动清空（对齐侧条 4s 退场节奏）。
     */
    private val _messageFlow = MutableStateFlow<RadioMessage?>(null)
    val messageFlow: StateFlow<RadioMessage?> = _messageFlow

    /**
     * 当前队列。
     *
     * 对外以 StateFlow 暴露：RadioCard 是 agent 唯一的存在面（C1），必须实时反映队列变化 ——
     * 模型换批、本地补歌之后卡片上的「备选数 / 下首」要跟着变。
     * 对内仍按普通属性读写，这样既有赋值点一行都不用改。
     */
    private val _playlist = MutableStateFlow<List<RadioTrack>>(emptyList())
    val playlist: StateFlow<List<RadioTrack>> = _playlist.asStateFlow()

    private var currentPlaylist: List<RadioTrack>
        get() = _playlist.value
        set(value) { _playlist.value = value }

    /** 最近一次队列调整（固定文案 + 数字，绝不出现模型生成的文字）。null = 本档还没调过。 */
    private val _lastAdjust = MutableStateFlow<String?>(null)
    /** 本档主题（用户种子）；整档不变 —— 卡片上的「固定信息」。null = 自动电台 */
    private val _theme = MutableStateFlow<String?>(null)

    /**
     * 卡片的完整状态：**固定信息**与**动态信息**分开摆，UI 各取所需。
     *
     * - 固定：[RadioCardState.theme]（整档不变，开播时确定）
     * - 动态：待播数 / 下首 / 最近调整（随播放与队列调整变化）
     */
    val cardState: StateFlow<RadioCardState> = combine(_playlist, _theme, _lastAdjust) { list, theme, adjust ->
        RadioCardState(
            theme = theme,
            upcomingCount = (list.size - 1).coerceAtLeast(0),
            nextTitle = list.getOrNull(1)?.title,
            lastAdjust = adjust,
        )
        // 用 Eagerly 而非 WhileSubscribed：无人订阅时状态也必须保持最新，
        // 否则「先取值再订阅」的调用方（含测试）会拿到初始值。
    }.stateIn(scope, SharingStarted.Eagerly, RadioCardState(null, 0, null, null))
    @Volatile private var seed: String? = null
    /** 暴露给 UI：电台主题（用户 seed / 提取的标签关键词，null=自动电台） */
    val stationTheme: String? get() = seed?.takeIf { it.isNotBlank() }

    /** 本档种子标签（每轮曲库候选池按它筛选）；开播时确定，复用路径继承上一档 */
    @Volatile private var lastSeedLabels: List<LabelName> = emptyList()

    /** 本次电台会话已播放曲目数（onTrackPlayed 累计，续歌阈值判断用） */
    @Volatile private var playedCount: Int = 0

    /** 最后一次启动的触发来源（诊断/审计用） */
    @Volatile private var lastTrigger: RadioTrigger = RadioTrigger.HOME_CLICK

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
     * 开电台：本地秒开 + 模型定队列（spec §7.0）。
     *
     * 复用分支命中时直接续上上一段对话；否则本地算法先推队列起播（唯一一次
     * 改变播放状态），再把曲库视图摆给模型要一个最优队列，替换「当前曲之后」。
     * 模型失败 / 无端点 / 结果过期 → 保留本地队列（已在播，不中断）。
     *
     * @param seed 用户给的种子（"来点蓝调"）；null = 从 nowPlaying 自动提取
     * @param trigger 触发来源（首页卡 / 对话 / 连跳重排 / 恢复），参与审计与种子兜底
     * @param chatContext 对话上下文（trigger=CHAT_INPUT 且 seed 为空时的种子兜底）
     * @return 最终队列（模型换过就返回换后的，否则为本地保底）
     */
    suspend fun startRadio(
        seed: String? = null,
        trigger: RadioTrigger = RadioTrigger.HOME_CLICK,
        chatContext: String? = null,
    ): List<RadioTrack> {
        // ── 复用分支：短时内重开、且播放器里还是上次那些歌 → 续上对话 ──
        // 跳过「本地队列重建 + 曲库视图注入 + 模型定队列」这一整段最贵的组装。
        if (tryResumeRetained(seed)) return currentPlaylist

        // ── 已有在播曲目 → 以它为种子，只在它**之后**接队列 ──
        // 曾经不管三七二十一先 PLAY_BY_ID 本地第一首，会把用户正在听的歌顶掉。
        // 用户没通过对话指定类型时，当前这首就是最好的种子（`extractSeedLabels` 第②步）。
        val current = runCatching { nowPlayingProvider.getNowPlaying() }.getOrNull()
        if (current?.currentMusicId != null) {
            return startAfterCurrent(seed, trigger, chatContext, current)
        }
        // 诊断：走到这里意味着"判断为没有在播曲目"。留一行日志，
        // 下次再出现"顶掉当前曲"就能一眼看出是拿不到还是真的没在播。
        Logger.i(RADIO_TRACE_TAG) {
            "[开播] 判定为无在播曲目（id=${current?.currentMusicId}, isPlaying=${current?.isPlaying}, " +
                "队列=${current?.queueIds?.size ?: 0} 首）→ 从零起播"
        }

        Logger.i("Agent.Radio") { "startRadio(seed=$seed, trigger=$trigger)" }
        this.seed = seed
        this.lastTrigger = trigger
        this.playedCount = 0
        _theme.value = seed?.takeIf { it.isNotBlank() }   // 固定信息：本档主题
        _lastAdjust.value = null                          // 新的一档，清空上次的调整痕迹
        // 对话触发且没给 seed → 用对话原文兜底做关键词提取（否则交给 nowPlaying 自动提取）
        val seedInput = seed?.takeIf { it.isNotBlank() }
            ?: chatContext?.takeIf { it.isNotBlank() && trigger == RadioTrigger.CHAT_INPUT }
        _radioState.value = RadioState.BUILDING(
            progressPercent = 10, actionText = "AI 正在理解你的喜好...", targetCount = targetCount,
        )
        presenceBus?.emit(com.hmp.domain.agent.infra.PresenceEvent.CompanionBadge(visible = true, label = "电台"))

        // ── A 段｜种子 + 本地保底（零阻塞，必返回） ──
        val seedLabels = extractSeedLabels(seedInput)
        lastSeedLabels = seedLabels
        _radioState.value = RadioState.BUILDING(
            progressPercent = 30, actionText = "从曲库里筛选好歌...", targetCount = targetCount,
        )
        val local = buildLocalFallback(seedLabels)
        Logger.i("Agent.Radio") { "startRadio: local fallback ${local.size} tracks, seedLabels=${seedLabels.map { it.name }}" }

        // ── A 段｜秒开：本地保底立即推入播放引擎（AGENT_INTERNAL，不算用户跳过） ──
        val instantPlay = pushLocalQueueForInstantPlay(local)
        currentPlayingId = local.firstOrNull()?.musicId
        Logger.i(RADIO_TRACE_TAG) {
            "[开播] 本地队列 ${local.size} 首已推入并起播（首播=${local.firstOrNull()?.title}，pushOk=$instantPlay）"
        }

        // ── B 段｜把上下文摆给模型，让它直接给队列（§7.0：不是让它自己拿工具查库） ──
        //    这一次调用的完整往返就是本档对话的第 1 轮（spec §7.2：一段对话贯到底）
        var opening: OpeningTurn? = null
        if (radioConfig != null && contextBudget.llmClient != null && local.isNotEmpty()) {
            _radioState.value = RadioState.BUILDING(
                progressPercent = 50, actionText = "AI 正在排歌...", targetCount = targetCount,
            )
            val g = generation
            opening = askOpeningWithRetry(seedInput, seedLabels, local, g, playing = local.firstOrNull())
        } else {
            Logger.i("Agent.Radio") { "startRadio: no LLM config / no client / empty local → local only" }
        }

        // ── C/D 段｜收口 ──
        _radioState.value = RadioState.BUILDING(
            progressPercent = 95, actionText = "整理播放列表...", targetCount = targetCount,
        )
        val arbitrated = diffArbitration(local)
        this.currentPlaylist = arbitrated
        _radioState.value = if (pausedDuringBuilding) {
            // 启动期间用户按了暂停 → 收口落 PAUSED（在途模型结果已被 generation 守卫丢弃）
            pausedDuringBuilding = false
            RadioState.PAUSED(
                currentCount = arbitrated.size,
                targetCount = arbitrated.size.coerceAtLeast(targetCount),
            )
        } else {
            RadioState.PLAYING(
                currentCount = arbitrated.size, targetCount = arbitrated.size.coerceAtLeast(targetCount),
            )
        }

        // ── ⑤ 用模型给的队列替换「当前播放曲之后」的部分；在播那首不动 ──
        //    失败 / 无端点 → 什么都不做，本地队列留着（它已经在播了，这就是正确行为）
        val resolved = opening?.ids?.let {
            resolveTracks(it, excludeMusicId = currentPlayingId, whys = opening.whys)
        }
        if (!resolved.isNullOrEmpty()) {
            applyQueueAfterCurrent(resolved.map { it.musicId })
            // 队列已被替换，同步本地镜像（在播那首 + 新的后续）；seedWhy 同 B 路径落头部
            val playing = arbitrated.firstOrNull()
            val playingWithWhy = opening?.seedWhy?.let { w -> playing?.copy(why = w) } ?: playing
            this.currentPlaylist = listOfNotNull(playingWithWhy) + resolved
        }

        // ── 开一段对话：本次电台 = 一次对话（§7.1 D1），开播往返即第 1 轮 ──
        startSession(
            openingSource = if (resolved.isNullOrEmpty()) "本地保底" else "模型定队列",
            openingMessages = opening?.messages,
            openingReason = opening?.reason,
        )

        auditLog?.logRadioStart(seedLabels.map { it.name }, arbitrated.size)
        stationTheme?.let { emitRadioMessage(RadioMessage.ThemeChanged(it)) }
        Logger.i("Agent.Radio") {
            "startRadio: done → ${this.currentPlaylist.size} tracks (queue=${if (resolved.isNullOrEmpty()) "local" else "model"})"
        }
        // 返回最终队列（模型换过就返回换后的），供 UI 渲染
        return this.currentPlaylist
    }

    /**
     * A 段秒开：第一首 `PLAY_BY_ID` 起播，其余 `ADD_TO_QUEUE` 排队。
     *
     * 全部按 [CommandSource.AGENT_INTERNAL] 下发 —— 这是 Agent 自己的编排动作，
     * 不能让 MasterAgent 当成"用户跳过"（否则连跳计数被自己推高 → 重排死循环）。
     *
     * @return 首曲是否成功起播（失败时仍继续排队，ReAct 段的 REPLACE_QUEUE 会兜住）
     */
    private suspend fun pushLocalQueueForInstantPlay(local: List<RadioTrack>): Boolean {
        val first = local.firstOrNull() ?: return false

        // ── 旧队列残留清扫（开播前提：无在播曲时播放器队列归电台管） ──
        // playWith 是「追加 + 跳播」不清队列：若旧播放列表非空（上一轮播放/电台的残留），
        // PLAY_BY_ID 会把电台第一首追加到旧队列**尾部**，旧残留赖在队列前段 ——
        // 电台队列走完后会接着放残留歌。从零起播路径先清一次。
        val staleQueue = runCatching { nowPlayingProvider.getNowPlaying() }.getOrNull()
            ?.queueIds.orEmpty()
        if (staleQueue.isNotEmpty()) {
            val clearOk = runCatching {
                playbackPort.execute(PlaybackCommand.SKIP_ALL, CommandSource.AGENT_INTERNAL).first
            }.getOrDefault(false)
            Logger.i(RADIO_TRACE_TAG) {
                "[开播] 清扫旧队列残留 ${staleQueue.size} 首（ok=$clearOk）→ [${
                    staleQueue.joinToString(",").take(120)
                }]"
            }
        }

        val playOk = runCatching {
            playbackPort.execute(PlaybackCommand.PLAY_BY_ID(first.musicId), CommandSource.AGENT_INTERNAL).first
        }.getOrDefault(false)
        local.drop(1).take(targetCount).forEach { track ->
            runCatching {
                playbackPort.execute(PlaybackCommand.ADD_TO_QUEUE(track.musicId), CommandSource.AGENT_INTERNAL)
            }.onFailure { e -> Logger.w("Agent.Radio", e) { "enqueue failed: ${track.title}" } }
        }
        // 执行后检验：追加不是「发了就算」——回读播放器队列确认真落地
        verifyQueueAfterAppend(local.take(targetCount).map { it.musicId })
        return playOk
    }

    /** Master 下令停电台 */
    suspend fun stopRadio() {
        Logger.i("Agent.Radio") { "stopRadio()" }
        generation++   // 此后到达的模型结果一律作废
        _radioState.value = RadioState.IDLE
        currentPlaylist = emptyList()
        playedCount = 0
        currentPlayingId = null
        _messageFlow.value = null
        _theme.value = null
        _lastAdjust.value = null
        seed = null                     // 新的一档不要继承旧主题
        pausedDuringBuilding = false
        cancelPendingPauseCheck()       // 电台已关，挂起的暂停核查作废
        stopSession()   // 关闭即丢弃整段对话（C2 只做会话内）
        presenceBus?.emit(com.hmp.domain.agent.infra.PresenceEvent.CompanionBadge(visible = false))
        // 注：RADIO_STATUS 卡已由 UI 层直接订阅 radioState，IDLE 即自动隐藏，无需再发事件
    }

    /**
     * Master 下令暂停电台（UI 收音机卡在 PLAYING 态点击）：
     * 播放引擎的 PAUSE 由 MasterAgent 负责，SubAgent 只切内部状态并保住 playlist。
     */
    suspend fun pauseRadio() {
        val current = _radioState.value
        if (current !is RadioState.PLAYING) {
            Logger.w("Agent.Radio") { "pauseRadio: not PLAYING (state=$current), skip" }
            return
        }
        generation++   // 暂停后到达的模型结果也作废（用户已经离开这一档）
        cancelPendingPauseCheck()
        runState = AgentRunState.PAUSED
        _radioState.value = RadioState.PAUSED(
            currentCount = currentPlaylist.size,
            targetCount = current.targetCount,
        )
        Logger.i("Agent.Radio") { "pauseRadio → PAUSED (playlist=${currentPlaylist.size})" }
    }

    /** Master 下令恢复电台（UI 收音机卡在 PAUSED 态点击）——播放引擎 PLAY 由 MasterAgent 负责 */
    suspend fun resumeRadio() {
        val current = _radioState.value
        if (current !is RadioState.PAUSED) {
            Logger.w("Agent.Radio") { "resumeRadio: not PAUSED (state=$current), skip" }
            return
        }
        runState = AgentRunState.RUNNING
        _radioState.value = RadioState.PLAYING(
            currentCount = currentPlaylist.size,
            targetCount = current.targetCount.coerceAtLeast(currentPlaylist.size),
        )
        Logger.i("Agent.Radio") { "resumeRadio → PLAYING (playlist=${currentPlaylist.size})" }
    }

    /**
     * 续歌（遗留 API，已被取代）：从当前 playlist 后半段挑歌继续播。
     *
     * ⚠️ 此 API 当前无调用点——它只把本地镜像列表重新切一刀，并不真正推进播放队列，
     * 队列见底时实际补歌由 G1(`refillQueue`) 经 PlaybackObservation 的 QueueLow 事件负责。
     * 保留仅供 R-Phase 3「回合化」重做参考（见 docs/7_x/B agent-build/design/agent-radio.md 附录 A.2 决策轨迹）。
     */
    @Deprecated(
        message = "续歌由 refillQueue 经 PlaybackObservation.QueueLow 负责，此 API 仅保留供 R-Phase 3 回合化重做",
        level = DeprecationLevel.WARNING,
    )
    suspend fun continueRadio(): List<RadioTrack> {
        if (_radioState.value !is RadioState.PLAYING) {
            Logger.w("Agent.Radio") { "continueRadio: radio not PLAYING (state=${_radioState.value}), skip" }
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

    fun queryState(): RadioState = _radioState.value
    fun queryPlaylist(): List<RadioTrack> = currentPlaylist

    /** 供测试 / 审计观察：决策内核事实账本里记了几条暂停（验证瞬时暂停是否被滤掉）。 */
    internal fun debugRecordedPauseCount(): Int = session?.recordedPauseCount() ?: 0

    /**
     * 队列剩余估算（已建队列数 − 已播数）—— Refill 回合的本地观测量。
     *
     * 注：`playedCount` 只统计经 Agent 命令触发的切歌（`trackChangeEvents`），因此是**粗估值**；
     * IM1 观测层落地后应由控制器的队列信号替代，这里只是让 Refill 在信号层到位前可用。
     */
    fun remainingHint(): Int = (currentPlaylist.size - playedCount).coerceAtLeast(0)

    // ═══════════════════════════════════════════════════════════════
    // 观测面接入 + 决策内核（spec §7）
    // ═══════════════════════════════════════════════════════════════

    /** 当前在播曲目 id —— 「不许动它」这条硬约束靠它落实。 */
    @Volatile private var currentPlayingId: Long? = null

    private var session: RadioSession? = null

    /** MasterAgent 转发：一首歌的结算（听完了 / 被切走了 + 播了多久）。 */
    fun onTrackSettled(event: TrackSettledEvent) {
        // 这条是「观测面到底通没通」的唯一证据 —— Android 埋点在 MusicController 的结算点，
        // 没有它就说明控制器没 emit 或总线没接上。
        Logger.i(RADIO_TRACE_TAG) {
            "[观测] 《${event.title}》(id=${event.musicId}) ${event.outcome}" +
                " 已播 ${(event.playedRatio * 100).toInt()}%（${event.playedMs / 1000}s / ${event.totalMs / 1000}s）"
        }
        session?.onTrackSettled(event)
    }

    /**
     * MasterAgent 转发：暂停 / 继续。
     *
     * 这里不只是"进上下文"，还驱动电台的暂停状态机：
     * - 电台运行中，用户在播放器里暂停 → **电台跟着暂停**（对话保留，可从电台开关恢复）
     * - 电台已暂停，用户又直接点了播放 → **退出电台**（用户接管了，我们不再插手）
     *
     * 事实过滤（spec §7.2）：切歌过渡的瞬时 pause/resumed **成对丢弃、不进事实流** ——
     * 只有去抖核查确认的真实暂停才转发给决策内核，否则每轮上下文里都会混进
     * "暂停/继续 N 次"的噪声（真机日志验证过模型会拿它当判断依据）。
     */
    fun onPause(event: PauseEvent) {
        // 默认转发；被去抖吞掉（瞬时对）或与状态机合并的场景置 null
        var forward: PauseEvent? = event
        when (_radioState.value) {
            is RadioState.PLAYING -> {
                if (event.resumed) {
                    if (cancelPendingPauseCheck()) forward = null   // 瞬时对的收尾，成对丢弃
                } else {
                    schedulePauseCheck(event, wasBuilding = false)
                    forward = null   // 先挂着，核查确认后才进事实
                }
            }
            is RadioState.PAUSED -> if (event.resumed) {
                cancelPendingPauseCheck()
                exitBecauseUserTookOver()
            }
            is RadioState.BUILDING -> {
                // 启动中（正在等模型返回）：暂停同样作废在途结果，并记住收口时落 PAUSED
                if (event.resumed) {
                    if (cancelPendingPauseCheck()) forward = null
                    pausedDuringBuilding = false
                } else {
                    schedulePauseCheck(event, wasBuilding = true)
                    forward = null
                }
            }
            else -> forward = null   // 未运行，忽略
        }
        forward?.let { session?.onPause(it) }
    }

    /**
     * 挂起一次暂停核查（切歌过渡去抖）：等窗口过后问播放器——
     * 真的还停着才认（PLAYING → pauseByUser / BUILDING → 作废在途结果并落 PAUSED），
     * 确认后才把原始事件转发进决策内核的事实账本。
     */
    private fun schedulePauseCheck(event: PauseEvent, wasBuilding: Boolean) {
        pendingPauseCheck?.cancel()
        pendingPauseCheck = scope.launch {
            delay(PAUSE_DEBOUNCE_MS)
            val stillPaused = runCatching {
                !nowPlayingProvider.getNowPlaying().isPlaying
            }.getOrDefault(false)
            if (!stillPaused) {
                Logger.i(RADIO_TRACE_TAG) { "[电台] 暂停核查：窗口后仍在播 → 切歌过渡的瞬时暂停，忽略" }
                return@launch
            }
            if (wasBuilding) {
                generation++
                pausedDuringBuilding = true
                Logger.i(RADIO_TRACE_TAG) { "[电台] 启动期间用户暂停 → 在途结果作废，收口后进入 PAUSED" }
            } else {
                pauseByUser()
            }
            session?.onPause(event)   // 真暂停才进事实流
        }
    }

    /** @return 是否真的取消了一个挂起的核查（true = 调用方应把配套事件成对丢弃） */
    private fun cancelPendingPauseCheck(): Boolean {
        val had = pendingPauseCheck != null
        pendingPauseCheck?.cancel()
        pendingPauseCheck = null
        return had
    }

    /**
     * 起声确认：发 PLAY 之后过 [AUDIBLE_CHECK_DELAY_MS] 问播放器"真的出声了吗"。
     *
     * 「暂停态补 PLAY」有个盲区：当前曲停在**曲目末端**（上一档最后一首放完后关的电台），
     * PLAY 恢复一个已到末尾的曲目是无声的 —— 状态 PLAYING、日志全对，就是没声音
     * （真机踩坑：点电台不响，且每次都如此）。确认无声就 PLAY_BY_ID 把当前曲重新起播
     * （这首从头放，好过死寂；队列接排不变）。
     *
     * 两道护栏：
     * - generation 过期（这 800ms 内电台被关闭/暂停）→ 不再补刀；
     * - 有挂起的暂停核查（用户刚按了暂停，去抖窗口还没裁决）→ 不和用户抢。
     */
    private suspend fun ensureAudible(trackId: Long) {
        val g = generation
        delay(AUDIBLE_CHECK_DELAY_MS)
        if (!isCurrent(g)) return
        if (pendingPauseCheck != null) return
        val playing = runCatching { nowPlayingProvider.getNowPlaying().isPlaying }.getOrDefault(true)
        if (playing) return
        Logger.i(RADIO_TRACE_TAG) {
            "[开播] PLAY 后仍未出声（当前曲可能停在曲目末端）→ PLAY_BY_ID 重新起播 id=$trackId"
        }
        runCatching {
            playbackPort.execute(PlaybackCommand.PLAY_BY_ID(trackId), CommandSource.AGENT_INTERNAL)
        }.onFailure { e -> Logger.w("Agent.Radio", e) { "audible fallback PLAY_BY_ID failed" } }
    }

    /** 用户在播放器里暂停 → 电台同步暂停（保留对话，等用户从电台开关恢复）。 */
    private fun pauseByUser() {
        Logger.i(RADIO_TRACE_TAG) { "[电台] 用户在播放器暂停 → 电台同步暂停（对话保留）" }
        scope.launch { pauseRadio() }
    }

    /**
     * 用户在电台暂停期间直接点了播放 → 用户接管，退出电台。
     * 注意**不暂停音乐**：用户已经明确要听了。
     */
    private fun exitBecauseUserTookOver() {
        Logger.i(RADIO_TRACE_TAG) { "[电台] 用户直接继续播放 → 退出电台，交还控制权" }
        scope.launch { onUserTookOver?.invoke() }
    }

    /** MasterAgent 转发：队列见底。 */
    fun onQueueLow(remaining: Int) {
        Logger.i(RADIO_TRACE_TAG) { "[队列] 见底 remaining=$remaining → 触发判断" }
        session?.onQueueLow(remaining)
    }

    /**
     * 尝试复用上一段对话。
     *
     * 主判断是**情境连续性**（默认复用，出现断裂证据才新开 —— spec §7.1）：
     * 1. 有保留的对话
     * 2. 情境没换代：时段（DayPart）与开播时相同；超过 [REUSE_BACKSTOP_MS] 兜底作废
     * 3. 用户这次没给**不同的** seed —— 换主题说明是新的一档，
     *    沿用旧对话会让模型拿旧主题聊新意图
     * 4. 播放器当前曲目仍在当时那份队列里、队列指纹一致 —— 说明队列没被换成别的东西
     *
     * @param incomingSeed 本次 startRadio 收到的用户种子（可为 null = 自动电台）
     * @return true = 已复用（调用方直接返回当前队列，不要再走组装）
     */
    private suspend fun tryResumeRetained(incomingSeed: String?): Boolean {
        val r = retained ?: return false
        if (session != null) return false            // 已经在播，谈不上复用
        if (r.messages.isEmpty() || r.playlist.isEmpty()) return false

        val ageMs = nowMonotonicMs() - r.closedAtMs
        if (ageMs < 0 || ageMs > REUSE_BACKSTOP_MS) {
            Logger.i(RADIO_TRACE_TAG) { "[复用] 跳过：距上次关闭 ${ageMs / 60_000} 分钟，超兜底窗口" }
            return false
        }

        // ①-b 情境换代：时段/日期变了 = 另一场节目（早班 vs 夜班），死板的 30 分钟窗口替代品
        //      （主判断是情境连续性，时间只兜底 —— 见 spec §7.1）
        val nowDayPart = runCatching { currentLocalMoment().dayPart() }.getOrNull()
        if (r.dayPart != null && nowDayPart != null && nowDayPart != r.dayPart) {
            Logger.i(RADIO_TRACE_TAG) {
                "[复用] 跳过：情境换代（开播时=${r.dayPart.label}，现在=${nowDayPart.label}）→ 新的一场"
            }
            return false
        }

        // ② 用户指定了新主题 → 新的一档，旧对话作废
        if (!incomingSeed.isNullOrBlank() && incomingSeed != r.seed) {
            Logger.i(RADIO_TRACE_TAG) {
                "[复用] 跳过：用户指定了新主题「$incomingSeed」（上一档是「${r.seed ?: "自动"}」）"
            }
            return false
        }

        val now = runCatching { nowPlayingProvider.getNowPlaying() }.getOrNull()
        val playingId = now?.currentMusicId

        // ③-a 当前曲目必须还在当时的队列里
        if (playingId == null || r.playlist.none { it.musicId == playingId }) {
            Logger.i(RADIO_TRACE_TAG) { "[复用] 跳过：当前曲目已经不是上次那份队列里的歌" }
            return false
        }
        // ③-b 队列本身也必须没被动过（用户可能换了歌单，而新歌单里恰好也有这首）
        //      拿不到队列指纹时退化为只靠 ③-a。
        val queue = now.queueIds
        if (queue.isNotEmpty() && queue != r.playlist.map { it.musicId }) {
            Logger.i(RADIO_TRACE_TAG) {
                "[复用] 跳过：播放列表已变（${r.playlist.size} 首 → ${queue.size} 首）"
            }
            return false
        }

        // ── 复用：恢复状态，重新起消费协程，对话原样续上 ──
        currentPlaylist = r.playlist
        currentPlayingId = r.playlist.firstOrNull { it.musicId == playingId }?.musicId
        playedCount = r.playlist.indexOfFirst { it.musicId == playingId }.coerceAtLeast(0)
        // 主题优先取本次 seed（与 r.seed 相同或为空才走到这里），兜底用上一档的
        _theme.value = incomingSeed?.takeIf { it.isNotBlank() } ?: r.seed?.takeIf { it.isNotBlank() }
        seed = incomingSeed?.takeIf { it.isNotBlank() } ?: r.seed?.takeIf { it.isNotBlank() }
        lastSeedLabels = runCatching { extractSeedLabels(seed) }.getOrDefault(emptyList())
        _radioState.value = RadioState.PLAYING(
            currentCount = currentPlaylist.size,
            targetCount = currentPlaylist.size.coerceAtLeast(targetCount),
        )

        startSession(openingSource = "复用上次对话", openingMessages = r.messages)
        session?.noteExecuted("恢复播放（距上次关闭 ${ageMs / 60_000} 分钟）")

        runCatching {
            playbackPort.execute(PlaybackCommand.PLAY, CommandSource.AGENT_INTERNAL)
        }.onFailure { e -> Logger.w("Agent.Radio", e) { "resume play failed" } }
        ensureAudible(playingId)

        Logger.i(RADIO_TRACE_TAG) {
            "[复用] 续上上次对话（${r.messages.size} 条消息，队列 ${currentPlaylist.size} 首）→ 省掉曲库视图组装"
        }
        return true
    }

    /**
     * 已有在播曲目时的开播路径：**当前这首继续放**，只在它之后接队列。
     *
     * 与「从零起播」的区别是不发 `PLAY_BY_ID` —— 那会把用户正在听的歌顶掉。
     * 秒开承诺在这里自动满足：本来就有声音，不需要等。
     *
     * 种子优先级（`extractSeedLabels` 已实现）：
     * ① 用户通过对话指定的类型 → ② 当前在播曲目的标签 → ③ 曲库高频标签
     */
    private suspend fun startAfterCurrent(
        seed: String?,
        trigger: RadioTrigger,
        chatContext: String?,
        current: com.hmp.domain.agent.port.NowPlayingContext,
    ): List<RadioTrack> {
        val currentId = current.currentMusicId!!
        Logger.i("Agent.Radio") { "startAfterCurrent(seed=$seed, current=$currentId)" }
        this.seed = seed
        this.lastTrigger = trigger
        this.playedCount = 0
        this.currentPlayingId = currentId
        _theme.value = seed?.takeIf { it.isNotBlank() }
        _lastAdjust.value = null

        _radioState.value = RadioState.BUILDING(
            progressPercent = 20, actionText = "接着这首往下排...", targetCount = targetCount,
        )

        val seedInput = seed?.takeIf { it.isNotBlank() }
            ?: chatContext?.takeIf { it.isNotBlank() && trigger == RadioTrigger.CHAT_INPUT }
        val seedLabels = extractSeedLabels(seedInput)
        lastSeedLabels = seedLabels
        Logger.i(RADIO_TRACE_TAG) {
            "[开播] 以在播曲目 $currentId 为起点（种子=${seedInput ?: "当前曲目标签"}）→ 不打断播放"
        }

        // 队列头 = 当前在播曲目（本地镜像用；不会真的下发）
        val head = current.currentMusicInfo?.let { musicInfoToRadioTrack(it, "在播·电台起点", RadioTrackSource.LOCAL) }
            ?: RadioTrack(currentId, "", "", "在播")
        val local = buildLocalFallback(seedLabels).filter { it.musicId != currentId }.take(targetCount)
        currentPlaylist = listOf(head) + local

        // 「点开即播」兜底：有当前曲目 ≠ 正在出声。上次关闭电台只暂停了播放，
        // 这时重开若没命中复用（换了主题/超窗口/队列被换过），就会走到这里 ——
        // 只接队列不按播放，电台"开着"却没有声音。所以暂停态必须补一次 PLAY
        // （AGENT_INTERNAL：恢复当前曲，不切歌、不动进度）。
        if (!current.isPlaying) {
            Logger.i(RADIO_TRACE_TAG) { "[开播] 当前曲目处于暂停态 → 补 PLAY 起声（不切歌）" }
            runCatching {
                playbackPort.execute(PlaybackCommand.PLAY, CommandSource.AGENT_INTERNAL)
            }.onFailure { e -> Logger.w("Agent.Radio", e) { "resume play failed" } }
            ensureAudible(currentId)
        }

        // 本地队列先接上（尾部替换，不动当前曲）；模型失败时它就在那儿了
        if (local.isNotEmpty()) applyQueueAfterCurrent(local.map { it.musicId })
        _radioState.value = RadioState.PLAYING(
            currentCount = currentPlaylist.size,
            targetCount = currentPlaylist.size.coerceAtLeast(targetCount),
        )

        // 模型定队列（失败/无端点/结果过期 → 保持上面的本地队列）；往返即对话第 1 轮
        val opening = if (radioConfig != null && contextBudget.llmClient != null && local.isNotEmpty()) {
            val g = generation
            askOpeningWithRetry(seedInput, seedLabels, local, g, playing = head)
        } else null
        val resolved = opening?.ids?.let {
            resolveTracks(it, excludeMusicId = currentId, whys = opening.whys)
        }
        if (!resolved.isNullOrEmpty()) {
            applyQueueAfterCurrent(resolved.map { it.musicId })
            // 模型为在播曲（电台起点）写的按语落镜像头部 —— 占位 why 会被 UI 的
            // hostWhy() 过滤，听众看不到起点那首的按语（真机 00:39 反馈）
            val headWithWhy = opening?.seedWhy?.let { head.copy(why = it) } ?: head
            currentPlaylist = listOf(headWithWhy) + resolved
        }

        startSession(
            openingSource = if (resolved.isNullOrEmpty()) "本地保底" else "模型定队列",
            openingMessages = opening?.messages,
            openingReason = opening?.reason,
        )
        return currentPlaylist
    }

    /** 导出当前对话快照，供下次开电台时复用（MasterAgent 在 stopRadio 时保存）。 */
    fun exportConversation(): RadioConversation? {
        val msgs = session?.exportMessages().orEmpty()
        if (msgs.isEmpty() || currentPlaylist.isEmpty()) return null
        return RadioConversation(
            messages = msgs,
            playlist = currentPlaylist,
            playingId = currentPlayingId,
            closedAtMs = nowMonotonicMs(),
            seed = this.seed,
            // 记下开播时段：复用判断用「情境换代」替代死板的 30 分钟窗口（spec §7.1）
            dayPart = runCatching { currentLocalMoment().dayPart() }.getOrNull(),
        )
    }

    /**
     * 开一段对话（一次电台 = 一次对话，关闭即丢弃 —— 除非命中复用）。
     *
     * @param openingMessages 非空则作为起始历史（开播往返 = 第 1 轮），否则只放 system
     * @param openingReason 开场编排思路，进节目档案
     */
    private fun startSession(
        openingSource: String,
        openingMessages: List<LlmMessage>? = null,
        openingReason: String? = null,
    ) {
        session?.close()
        val s = RadioSession(
            targetCount = targetCount,
            theme = seed?.takeIf { it.isNotBlank() },
            judge = { messages -> askJudge(messages) },
            onVerdict = { g, verdict -> applyVerdict(g, verdict) },
            generation = { generation },
            fallbackRefill = { refillQueue() },
            candidates = { buildCandidateBlock() },
            snapshot = { contextSnapshot() },
            onTurn = { verdict ->
                Logger.i("Agent.Radio") {
                    "turn → ${verdict?.action ?: "SILENT"} (reason=${verdict?.reason?.take(60)})"
                }
            },
        )
        if (openingMessages.isNullOrEmpty()) s.begin() else s.resumeFrom(openingMessages)
        s.noteIntent(openingReason)
        s.noteExecuted("开播：$openingSource")
        s.start(scope)
        session = s
        Logger.i(RADIO_TRACE_TAG) {
            "[会话] 开启（队列来源=$openingSource，端点=${if (radioConfig != null) "有" else "无"}）"
        }
    }

    private fun stopSession() {
        session?.close()
        session = null
        Logger.i(RADIO_TRACE_TAG) { "[会话] 关闭，本档对话丢弃" }
    }

    /**
     * 本地补歌：**真的把新曲目推进播放队列**（S4 要求队列见底时不出现空档）。
     *
     * 这里不用 `continueRadio()` —— 它只是把本地镜像列表重新切一刀，
     * 播放器里的队列并没有变长，队列见底时它实际上什么都不补。
     *
     * @param requested 模型要求的补歌数（null/越界 = 按 [targetCount] 补满）
     */
    private suspend fun refillQueue(requested: Int? = null): Int {
        val have = currentPlaylist.map { it.musicId }.toSet()
        val labels = runCatching { extractSeedLabels(seed) }.getOrDefault(emptyList())
        var fresh = buildLocalFallback(labels)
            .filter { it.musicId !in have && it.musicId != currentPlayingId }

        if (fresh.isEmpty()) {
            // 标签池挖不出新歌 → 退化到全库，仍然避开已排过的
            fresh = runCatching { musicRepository.getAllMusicInfoAsList("play_count", "desc") }
                .getOrDefault(emptyList())
                .filter { it.music.id !in have && it.music.id != currentPlayingId }
                .take(targetCount)
                .map { musicInfoToRadioTrack(it, "补充曲目", RadioTrackSource.LOCAL) }
        }

        val take = fresh.take(requested ?: targetCount)
        take.forEach { t ->
            runCatching {
                playbackPort.execute(PlaybackCommand.ADD_TO_QUEUE(t.musicId), CommandSource.AGENT_INTERNAL)
            }.onFailure { e -> Logger.w("Agent.Radio", e) { "refill enqueue failed: ${t.title}" } }
        }
        if (take.isNotEmpty()) {
            currentPlaylist = currentPlaylist + take
            _lastAdjust.value = "已补 ${take.size} 首"
            Logger.i(RADIO_TRACE_TAG) { "[补歌] +${take.size} 首（队列共 ${currentPlaylist.size}）" }
            // 执行后检验：回读播放器队列，确认补的歌真的进了队列
            verifyQueueAfterAppend(take.map { it.musicId })
        } else {
            Logger.w(RADIO_TRACE_TAG) { "[补歌] 曲库里挖不出新歌，队列没有变长" }
        }
        return take.size
    }

    /**
     * 追加类操作（ADD_TO_QUEUE）的执行后检验：回读播放器队列确认真落地。
     *
     * REPLACE 有 [verifyQueueAfterReplace]，追加此前完全裸奔 —— 端口返回 true 只代表
     * 指令送达，id 解析不到时曲目会被静默丢弃（真机 23:26 实锤过 REPLACE 丢歌）。
     * 检查两点：① 每个 id 都在播放器队列里；② 相对顺序与预期一致。
     * 播放器不报队列（queueIds 为空）时无从检验，静默跳过（快照层会退回镜像）。
     */
    private suspend fun verifyQueueAfterAppend(expected: List<Long>) {
        if (expected.isEmpty()) return
        val now = runCatching { nowPlayingProvider.getNowPlaying() }.getOrNull() ?: return
        val queue = now.queueIds
        if (queue.isEmpty()) return
        val missing = expected.filter { it !in queue.toSet() }
        if (missing.isNotEmpty()) {
            Logger.w(RADIO_TRACE_TAG) {
                "[检验] 追加未完全生效：预期 +${expected.size} 首，缺失 [${missing.joinToString(",")}]"
            }
            return
        }
        // 顺序检查：expected 作为子序列按序出现在播放器队列中
        var cursor = 0
        for (id in queue) {
            if (cursor < expected.size && id == expected[cursor]) cursor++
        }
        if (cursor != expected.size) {
            Logger.w(RADIO_TRACE_TAG) {
                "[检验] 追加顺序异常：预期 [${expected.joinToString(",")}]，播放器实际 [${queue.joinToString(",")}]"
            }
        }
    }

    /** 决策内核的判断调用：纯对话、无工具（C5）。 */
    private suspend fun askJudge(messages: List<LlmMessage>): String? {
        val transport = contextBudget.llmClient ?: return null
        val config = radioConfig ?: return null
        val res = LlmCallExecutor().call(
            transport = transport,
            config = config,
            messages = messages,
            tools = null,          // 判断阶段不给工具：它只能三选一，不能自己去改队列
            temperature = 0.2f,
        )
        if (res.failed) {
            // 失败原因必须上 RadioTrace：此前 failedMessage 被吞掉，
            // 真机上"等了半天没反应"根本查不出是网络/HTTP/解析哪一层挂了
            Logger.w(RADIO_TRACE_TAG) {
                "[裁决] 模型调用失败：${res.failedMessage?.take(200) ?: "未知原因"}"
            }
            return null
        }
        return res.text
    }

    /**
     * 系统状态快照。
     *
     * **在播曲目必须问播放器**（`nowPlayingProvider`），不能靠自己维护的 `playedCount` 推算 ——
     * 真机验证过：换批走 `AGENT_INTERNAL`，不 emit 切歌事件，计数永不增长，
     * 导致模型连续多轮拿到的「在播」都是开播那一首，「不可替换」也就保护错了对象。
     */
    internal suspend fun contextSnapshot(): RadioContextSnapshot {
        val now = runCatching { nowPlayingProvider.getNowPlaying() }.getOrNull()
        val playingId = now?.currentMusicId
        val playingTitle = now?.currentMusicInfo?.music?.title

        // 以在播曲目在队列中的位置为准；拿不到就退回本地计数
        val idx = when {
            playingId != null -> currentPlaylist.indexOfFirst { it.musicId == playingId }
            else -> -1
        }
        val base = idx.takeIf { it >= 0 } ?: playedCount

        // 队列真值来源：播放器报得出队列就以播放器为准（镜像可能滞后或被外部改动），
        // 否则退回本地镜像。真机 22:57「模型列表好像没换上」的排查盲区：
        // 快照此前只读镜像 —— 镜像说换了 ≠ 播放器换了，模型不能被镜像欺骗。
        val playerQueue = now?.queueIds.orEmpty()
        val upcoming: List<RadioQueueEntry>
        val fullRemaining: Int
        if (playerQueue.isNotEmpty() && playingId != null) {
            val pIdx = playerQueue.indexOf(playingId)
            val after = if (pIdx >= 0) playerQueue.drop(pIdx + 1) else playerQueue
            val mirrorAfter = currentPlaylist.drop(base + 1).map { it.musicId }
            if (mirrorAfter.isNotEmpty() &&
                after.take(SNAPSHOT_QUEUE_VISIBLE) != mirrorAfter.take(SNAPSHOT_QUEUE_VISIBLE)
            ) {
                Logger.w(RADIO_TRACE_TAG) {
                    "[镜像] 播放器队列与本地镜像不一致（播放器在播之后 ${after.size} 首 / 镜像 ${mirrorAfter.size} 首）→ 快照以播放器为准"
                }
            }
            upcoming = after.take(SNAPSHOT_QUEUE_VISIBLE).map { id ->
                RadioQueueEntry(id, currentPlaylist.firstOrNull { it.musicId == id }?.title ?: "（id $id）")
            }
            fullRemaining = after.size
        } else {
            val mirrorAfter = currentPlaylist.drop(base + 1)
            upcoming = mirrorAfter.take(SNAPSHOT_QUEUE_VISIBLE)
                .map { RadioQueueEntry(it.musicId, it.title) }
            fullRemaining = mirrorAfter.size
        }

        return RadioContextSnapshot(
            playingTitle = playingTitle ?: currentPlaylist.getOrNull(base)?.title,
            playingMusicId = playingId ?: currentPlayingId,
            queueRemaining = fullRemaining,
            upcoming = upcoming,
        )
    }

    /**
     * 执行判定结果。
     *
     * @param g 提问时刻的世代快照（per-turn，RadioSession 每次 askModel 时取）——
     *   只有「该轮在途期间」发生的关闭/暂停才作废这个结果
     * @return true = 已执行；false = 已过期被丢弃（期间被关闭/暂停）
     */
    private suspend fun applyVerdict(g: Int, verdict: RadioVerdict): Boolean {
        if (!isCurrent(g)) return false
        // 主播的编排思路（reason）上卡片 —— 结构化字段渲染，不是自由拼句（spec §3.3）
        val intent = verdict.reason?.takeIf { it.isNotBlank() }?.take(CARD_INTENT_MAX)
        when (verdict.action) {
            RadioAction.NONE -> Unit      // 沉默是默认答案
            RadioAction.APPEND -> {
                // 模型给出的 count 生效（prompt 里承诺过）；越界/缺失回落 targetCount
                val n = refillQueue(requested = verdict.appendCount.takeIf { it in 1..targetCount })
                if (intent != null) _lastAdjust.value = intent
                session?.noteExecuted("补歌 $n 首")
            }

            RadioAction.REPLACE -> {
                val tracks = resolveTracks(
                    verdict.musicIds,
                    excludeMusicId = currentPlayingId,
                    whys = verdict.whys,
                )
                if (tracks.isEmpty()) {
                    Logger.w("Agent.Radio") { "replace: 模型给的 id 全部无效，保持原队列" }
                    session?.noteExecuted("换批失败（id 无效，队列未动）")
                    return true
                }
                applyQueueAfterCurrent(tracks.map { it.musicId })
                // 队列已换，同步本地镜像（在播那首 + 新的后续），否则 remainingHint 一直是旧值
                val playing = currentPlaylist.getOrNull(playedCount)
                currentPlaylist = listOfNotNull(playing) + tracks
                _lastAdjust.value = intent ?: "已换一批"
                session?.noteExecuted("换批（${tracks.size} 首）")
            }
        }
        return true
    }

    /**
     * 替换「当前播放曲之后」的队列 —— 在播那首**不动**。
     *
     * 端口实现会**只移除当前播放曲之后的条目**并追加这些 id，
     * 当前曲与它之前的一切都不碰，因此播放和 position 都不受影响。
     *
     * @param musicIds 当前播放曲**之后**的曲目，不含在播那首
     */
    private suspend fun applyQueueAfterCurrent(musicIds: List<Long>) {
        if (musicIds.isEmpty()) return
        Logger.i(RADIO_TRACE_TAG) {
            "[执行] 替换当前之后的队列：${musicIds.size} 首 [${musicIds.joinToString(",")}]" +
                "（在播 ${currentPlayingId ?: "无"} 不触碰）"
        }
        // 这次执行会让播放器内部再走一次切歌，产生「已播 0% 被切走」的假结算 ——
        // 0% 对模型是最强的负反馈信号，必须先静默再动手。
        observationBus?.muteFor(ORCHESTRATION_MUTE_MS)
        runCatching {
            playbackPort.execute(
                PlaybackCommand.REPLACE_QUEUE(musicIds),
                CommandSource.AGENT_INTERNAL,   // 自家人操作，不能被当成用户意图
            )
        }.onFailure { e ->
            Logger.w("Agent.Radio", e) { "REPLACE_QUEUE failed" }
        }.onSuccess { (ok, msg) ->
            // 成功标志此前被丢弃：端口返回 false（如部分 id 未入队）时完全无感
            if (!ok) Logger.w(RADIO_TRACE_TAG) { "[执行] REPLACE_QUEUE 报告未完全生效：$msg" }
        }
        verifyQueueAfterReplace(musicIds)
    }

    /**
     * 替换后回读播放器队列验证真伪 —— **镜像说换了 ≠ 播放器真的换了**。
     *
     * 真机 22:57「模型列表好像没换上」无法从日志判真伪的教训：当时所有痕迹
     * （trace 数量、快照队列）都来自本地镜像，播放器实际收到什么无人知晓。
     */
    private suspend fun verifyQueueAfterReplace(expected: List<Long>) {
        val now = runCatching { nowPlayingProvider.getNowPlaying() }.getOrNull() ?: return
        val queue = now.queueIds
        if (queue.isEmpty()) return   // 播放器不报队列 → 无从回读，快照层会退回镜像
        val wanted = expected.filter { it != now.currentMusicId }
        val pIdx = now.currentMusicId?.let { queue.indexOf(it) } ?: -1
        val after = if (pIdx >= 0) queue.drop(pIdx + 1) else queue
        if (after.take(wanted.size) != wanted) {
            Logger.w(RADIO_TRACE_TAG) {
                "[执行] 队列替换未生效？预期在播之后 [${wanted.joinToString(",")}]" +
                    "，播放器实际 [${after.joinToString(",")}]"
            }
        }
    }

    /**
     * 把模型给的 id 落成曲目：**逐条校验，幻觉 id 直接丢**。
     * [excludeMusicId]（在播那首）会被剔除 —— 硬约束，不信任模型一定遵守。
     * [whys] 与 [ids] 按位置对应的主播按语——按 id 对齐落库（模型可能多给/少给，防御式取交集）。
     */
    private suspend fun resolveTracks(
        ids: List<Long>,
        excludeMusicId: Long?,
        whys: List<String> = emptyList(),
    ): List<RadioTrack> {
        val wanted = ids.distinct().filter { it != excludeMusicId }.take(targetCount)
        if (wanted.isEmpty()) return emptyList()
        // 按位置对齐 id → why（长度不齐时截到公共部分，缺失的歌回退占位文案）
        val whyById = ids.take(whys.size).zip(whys) { id, why -> id to why }.toMap()
        val infos = runCatching { musicRepository.getMusicInfoByIds(wanted) }.getOrDefault(emptyList())
        val byId = infos.associateBy { it.music.id }
        val resolved = wanted.mapNotNull { id ->
            byId[id]?.let { info ->
                musicInfoToRadioTrack(info, whyById[id] ?: "电台续播", RadioTrackSource.LOCAL)
            }
        }
        val hallucinated = wanted.size - resolved.size
        if (hallucinated > 0) {
            Logger.w(RADIO_TRACE_TAG) { "[校验] 模型给了 $hallucinated 个库里不存在的 id，已丢弃" }
        }
        if (excludeMusicId != null && ids.contains(excludeMusicId)) {
            Logger.i(RADIO_TRACE_TAG) { "[约束] 模型把在播曲目 $excludeMusicId 也列进来了 → 已剔除" }
        }
        return resolved
    }

    // ── Start：模型定队列（§7.0） ──

    /** 曲库视图：按 token 预算自适应（§7.0.2 D5），库大时用本地标签粗过滤。 */
    private data class LibraryView(
        val lines: List<String>,
        val shownCount: Int,
        val totalCount: Int,
        val filtered: Boolean,
    )

    private suspend fun buildLibraryView(
        seedLabels: List<LabelName>,
        local: List<RadioTrack>,
        /** 覆盖默认 token 预算行数（turn 候选池用更小的池子） */
        rows: Int? = null,
    ): LibraryView {
        val total = runCatching { musicRepository.getMusicCount().first() }.getOrDefault(0)
        // 强制包含：本地算法出的这批 + 在播曲目，否则本地认为最好的可能被启发式筛掉
        val mustHave = (local.map { it.musicId } + listOfNotNull(currentPlayingId)).distinct()
        val budget = rows ?: (LIBRARY_TOKEN_BUDGET / TOKENS_PER_TRACK).coerceAtLeast(MIN_LIBRARY_ROWS)

        val ids = if (total <= budget) {
            runCatching { musicRepository.getAllMusicInfoAsList("play_count", "desc") }
                .getOrDefault(emptyList())
                .map { it.music.id }
        } else {
            // 标签粗过滤：本地队列同标签 → 种子命中 → 高频标签
            val acc = LinkedHashSet<Long>(mustHave)
            val labelPool = seedLabels + runCatching {
                LabelCategory.entries.flatMap { c ->
                    runCatching { musicRepository.getLabelNamesByType(c).first() }.getOrDefault(emptyList())
                }
            }.getOrDefault(emptyList())
            for (label in labelPool) {
                if (acc.size >= budget) break
                val hits = runCatching { musicRepository.getMusicIdListByType(label, budget) }
                    .getOrDefault(emptyList())
                for (id in hits) {
                    acc += id
                    if (acc.size >= budget) break
                }
            }
            acc.toList()
        }

        val infos = runCatching { musicRepository.getMusicInfoByIds(ids.take(budget)) }
            .getOrDefault(emptyList())
        val lines = infos.map { info ->
            val id = info.music.id
            val mark = if (id in mustHave) "★" else " "
            "$mark $id | ${info.music.title} | ${info.music.artist}"
        }
        return LibraryView(lines, lines.size, total, filtered = total > budget)
    }

    /**
     * turn 轮的曲库候选池（spec §7.2）：replace/append 的 id 只能从这里挑。
     * 按本档种子标签筛选（复用曲库视图的过滤逻辑），池子比开播小得多，控制每轮 token。
     */
    private suspend fun buildCandidateBlock(): String {
        if (radioConfig == null || contextBudget.llmClient == null) return ""
        val view = buildLibraryView(lastSeedLabels, currentPlaylist, rows = TURN_CANDIDATE_ROWS)
        if (view.lines.isEmpty()) return ""
        return buildString {
            appendLine("## 曲库候选（musicIds 只能从这里挑）")
            view.lines.forEach(::appendLine)
        }
    }

    /**
     * 开播调用（对话第 1 轮，spec §7.2）：把曲库视图摆给模型要最优队列。
     *
     * @return 完整往返消息（system + user + assistant）——**不再用完即弃**，
     *         直接作为 [RadioSession] 的起始历史；失败返回 null（→ 保留本地队列）
     */
    private data class OpeningTurn(
        val messages: List<LlmMessage>,
        val ids: List<Long>?,
        val reason: String?,
        /** 与 ids 按位置对应的每首歌主播按语（模型没给就为空） */
        val whys: List<String> = emptyList(),
        /** 为正在播曲（电台起点）写的按语（开播特有；模型没给就为 null） */
        val seedWhy: String? = null,
    )

    private suspend fun askOpeningQueue(
        seedInput: String?,
        seedLabels: List<LabelName>,
        local: List<RadioTrack>,
        playing: RadioTrack?,
    ): OpeningTurn? {
        val view = buildLibraryView(seedLabels, local)
        val messages = listOf(
            LlmMessage(role = "system", content = RadioSession.systemPrompt(targetCount)),
            LlmMessage(role = "user", content = renderOpeningContext(seedInput, seedLabels, local, view, playing)),
        )
        val raw = askJudge(messages)
        if (raw.isNullOrBlank()) {
            Logger.w(RADIO_TRACE_TAG) { "[开播] 模型没回（调用失败/无端点）→ 保持本地队列" }
            return null
        }
        val ids = RadioSession.parseMusicIds(raw)
        val reason = RadioSession.parseOpeningReason(raw)
        val whys = RadioSession.parseOpeningWhys(raw)
        val seedWhy = RadioSession.parseOpeningSeedWhy(raw)
        val valid = if (ids == null) 0 else resolveTracks(ids, excludeMusicId = currentPlayingId, whys = whys).size
        Logger.i(RADIO_TRACE_TAG) {
            "[开播] 曲库视图 ${view.shownCount}/${view.totalCount}${if (view.filtered) "（已按标签过滤）" else "（全量）"}" +
                " → 模型返回 ${ids?.size ?: 0} 个 id，有效 $valid 个" +
                "${if (ids != null && valid < ids.size) "，丢弃 ${ids.size - valid} 个（幻觉 id 或在播曲目）" else ""}" +
                "，按语 ${whys.size} 条"
        }
        Logger.d(RADIO_TRACE_TAG) { "[开播] 模型原始回复：${raw.trim().take(300)}" }
        // 即使 id 不可用也把往返留在历史里 —— 下一轮的节目档案会摆出真实队列，模型自己能看到偏差
        return OpeningTurn(
            messages = messages + LlmMessage(role = "assistant", content = raw.trim()),
            ids = ids,
            reason = reason,
            whys = whys,
            seedWhy = seedWhy,
        )
    }

    /**
     * 开播调用 + 一次重试（spec §7.2 的兜底加固）。
     *
     * 开播是本档节目最重要的一次模型调用，而它恰恰是冷启动后第一发 ——
     * 网络抖动/端点瞬时 5xx 很常见，直接落本地保底会让用户"等了半天 AI 没排歌"。
     * 所以失败立即重试一次；再失败才认输。失败与"结果过期"在 RadioTrace 里分开说清。
     *
     * @param g 开播时快照的 generation —— 重试等待期间被关闭/暂停则整段作废
     */
    private suspend fun askOpeningWithRetry(
        seedInput: String?,
        seedLabels: List<LabelName>,
        local: List<RadioTrack>,
        g: Int,
        playing: RadioTrack?,
    ): OpeningTurn? {
        var lastFailure: String? = null
        repeat(OPENING_ATTEMPTS) { attempt ->
            // CancellationException 不是"调用失败"：协程被取消（调用方作用域销毁/电台关闭）
            // 必须立即上抛，吞掉它去重试 = 在已取消的协程里继续跑，delay 处再炸一次
            val result = try {
                Result.success(askOpeningQueue(seedInput, seedLabels, local, playing))
            } catch (ce: CancellationException) {
                Logger.i(RADIO_TRACE_TAG) { "[开播] 开播协程被取消（${ce.message ?: "cancelled"}）→ 中止开播" }
                throw ce
            } catch (e: Throwable) {
                Result.failure(e)
            }
            val turn = result.getOrNull()
            if (turn != null) {
                if (!isCurrent(g)) {
                    Logger.i(RADIO_TRACE_TAG) { "[开播] 结果已过期（期间电台被关闭/暂停）→ 丢弃" }
                    return null
                }
                return turn
            }
            lastFailure = result.exceptionOrNull()?.message ?: "模型空回复（调用失败或无端点）"
            if (attempt < OPENING_ATTEMPTS - 1) {
                Logger.w(RADIO_TRACE_TAG) {
                    "[开播] 第 ${attempt + 1} 次调用失败（${lastFailure?.take(120)}）→ ${OPENING_RETRY_DELAY_MS / 1000}s 后重试"
                }
                delay(OPENING_RETRY_DELAY_MS)
                if (!isCurrent(g)) {
                    Logger.i(RADIO_TRACE_TAG) { "[开播] 重试等待期间已过期（期间电台被关闭/暂停）→ 放弃" }
                    return null
                }
            }
        }
        Logger.w(RADIO_TRACE_TAG) { "[开播] 模型 $OPENING_ATTEMPTS 连败（${lastFailure?.take(120)}）→ 保持本地队列" }
        return null
    }

    private fun renderOpeningContext(
        seedInput: String?,
        seedLabels: List<LabelName>,
        local: List<RadioTrack>,
        view: LibraryView,
        playing: RadioTrack?,
    ): String = buildString {
        appendLine("## 开启情境（这是本档节目的第 1 轮）")
        appendLine("- 时刻：${currentLocalMoment().describe()}")
        appendLine("- 触发：${lastTrigger}")
        appendLine("- 用户说：${seedInput?.takeIf { it.isNotBlank() } ?: "（无，自动电台）"}")
        appendLine("- 本地提取的风格标签：${seedLabels.joinToString("、") { it.name }.ifBlank { "（无）" }}")

        appendLine()
        if (playing != null) {
            // 统一渲染：A 路径（从零起播）的 local[0] 就是在播首，B 路径（接播）的 local
            // 全部接在在播曲之后 —— 两种情况都把「正在播」单独摆出来，避免模型把锚点
            // 认错（真机 22:57 事故：B 路径曾把 local[0] 标成「在播·不可动」，模型把
            // 编排锚点定在了一首根本不会响的歌上，reason 承接《喜欢你》，实际在播《唯一》）。
            val afterPlaying = if (local.firstOrNull()?.musicId == playing.musicId) local.drop(1) else local
            appendLine("## 正在播（电台起点，不可替换，你的编排要承接它）")
            appendLine("- ${playing.musicId} | ${playing.title.ifBlank { "（未知）" }} | ${playing.artist.ifBlank { "（未知艺人）" }}")
            appendLine()
            appendLine("## 本地保底队列（接在正在播曲目之后，可整体替换）")
            afterPlaying.forEachIndexed { i, t ->
                appendLine("${i + 1}. ${t.musicId} | ${t.title} | ${t.artist}")
            }
        } else {
            // 没有在播曲目且 local 为空等边缘情况：只给曲库视图
            appendLine("## 本地保底队列（空）")
        }

        appendLine()
        appendLine(
            if (view.filtered) "## 曲库（共 ${view.totalCount} 首，以下 ${view.shownCount} 首为按标签筛出的候选）"
            else "## 曲库（共 ${view.totalCount} 首，全量）"
        )
        view.lines.forEach(::appendLine)

        appendLine()
        appendLine("请给出开场编排：最多 $targetCount 个 musicId（不含在播那首），并附一句 reason 说明编排思路。")
        appendLine("输出：{\"musicIds\":[…],\"reason\":\"…\"}")
    }

    // ── M6-T2 跳过感知重排 ──────────────────────────────────

    /**
     * MasterAgent 调用：用户连跳 2+ 首触发重排。
     * - emit ReorderSkipped 短时消息（UI RADIO_STATUS 卡右侧显示"跳过 N 首，正在重选..."）
     * - 清空播放队列（playbackPort.execute(SKIP_ALL)）
     * - 用当前 seed 重新跑一轮 startRadio
     *
     * @param skippedTitles 用户连跳的曲目标题（Master 从 skipEvents 快照传入，仅用于计数/日志）
     */
    suspend fun reorder(skippedTitles: List<String> = emptyList()): List<RadioTrack> {
        Logger.i("Agent.Radio") { "reorder triggered: skippedTitles=$skippedTitles" }
        _radioState.value = RadioState.BUILDING(progressPercent = 10, actionText = "正在重新为你选歌...", targetCount = targetCount)
        emitRadioMessage(RadioMessage.ReorderSkipped(skippedTitles.size))
        // ① 清空旧播放队列（AGENT_INTERNAL：自家人操作，不能算用户跳过，
        //    否则 MasterAgent 的 consecutiveSkipCount 会被自己触发的重排再次推高 → 死循环）
        //    注意 clearPlaylist 只清队列**不停播**：当前曲继续放，
        //    所以 startRadio 会走 startAfterCurrent 分支，以当前曲为种子在它之后续排。
        runCatching { playbackPort.execute(PlaybackCommand.SKIP_ALL, CommandSource.AGENT_INTERNAL) }
        // ② 沿用当前 seed 重新构建（seed 为空时 extractSeedLabels 会回落到 nowPlaying / 全局标签）
        val tracks = startRadio(seed = this.seed, trigger = RadioTrigger.SKIP_REORDER)
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

    /**
     * MasterAgent 的 trackChangeEvents 监听回调：累计本次电台已播曲目数。
     * 播到一半（targetCount/2）时 emit TrackContinuing 短时消息，提示即将续歌。
     */
    fun onTrackPlayed(title: String) {
        playedCount++
        // 只在标题唯一命中时才反查 musicId —— 重名歌猜不得，
        // 猜错了「不许动在播」就保护错了对象；拿不准就留着旧值，
        // 真正的权威来源是 contextSnapshot 里问播放器。
        val matches = currentPlaylist.filter { it.title == title }
        if (matches.size == 1) currentPlayingId = matches.first().musicId
        Logger.i("Agent.Radio") { "onTrackPlayed: '$title' (playedCount=$playedCount/$targetCount)" }
        val half = (targetCount / 2).coerceAtLeast(1)
        if (playedCount == half) {
            emitRadioMessage(RadioMessage.TrackContinuing(title))
        }
    }

    /** UI 层可清空短时消息（如卡片折叠时） */
    fun clearMessage() {
        _messageFlow.value = null
    }

    /** emit 短时消息 → [RADIO_MESSAGE_TTL_MS] 后自动清空（对齐侧条 4s 退场） */
    private fun emitRadioMessage(message: RadioMessage) {
        _messageFlow.value = message
        scope.launch {
            delay(RADIO_MESSAGE_TTL_MS)
            if (_messageFlow.value == message) _messageFlow.value = null
        }
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

        // 命中标签数降序 → 取 top targetCount
        //（曾经取 targetCount*2「给 LLM diff 留空间」，那是旧 ReAct 时代的需要；
        //  现在模型直接给完整队列，多推一倍只会让 REPLACE_QUEUE 时清掉的更多、编排噪声更大）
        val candidateIds = score.entries
            .sortedByDescending { it.value }
            .take(targetCount)
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
            artist = info.music.artist,
            why = why,
            source = source,
        )

    // ═══════════════════════════════════════════════════════════════
    // 收口保险（去重 + 截 targetCount）
    // ═══════════════════════════════════════════════════════════════

    private fun diffArbitration(tracks: List<RadioTrack>): List<RadioTrack> {
        val seen = mutableSetOf<Long>()
        return tracks.filter { seen.add(it.musicId) }.take(targetCount)
    }

    /** 热更新 AI 配置——由 MasterAgent.updateAiConfig 推送。下次 startRadio/continueRadio 时用新 config。 */
    fun updateAiConfig(radioConfig: AiEndpointConfig?) {
        this.radioConfig = radioConfig
        Logger.i("Agent.Radio") { "updateAiConfig: config=${radioConfig != null}" }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 电台数据模型
// ═══════════════════════════════════════════════════════════════════

sealed class RadioState {
    /** 电台空闲（未启动 / 已停止） */
    data object IDLE : RadioState()

    /** 电台正在构建 playlist（进度真实数据来自 RadioSubAgent 内部步骤） */
    data class BUILDING(
        /** 进度百分比 0-100（阶段性跳变，非实时） */
        val progressPercent: Int = 0,
        /** 当前动作文字（如"AI 正在理解你的喜好..."、"LLM 补充..."） */
        val actionText: String = "正在初始化...",
        /** 目标曲目数 */
        val targetCount: Int = 8,
        /** 已生成曲目数（BUILDING 阶段通常 0，PLAYING 阶段等于 currentCount） */
        val currentCount: Int = 0,
    ) : RadioState()

    /** 电台播放中 */
    data class PLAYING(
        /** 当前 playlist 曲目数 */
        val currentCount: Int,
        /** 目标曲目数（通常等于 currentCount） */
        val targetCount: Int,
    ) : RadioState()

    /** 电台已暂停（用户点了收音机卡；playlist 与当前曲目保留，恢复即续播） */
    data class PAUSED(
        /** 暂停时 playlist 曲目数 */
        val currentCount: Int = 0,
        /** 暂停时的目标曲目数 */
        val targetCount: Int = 0,
    ) : RadioState()
}

/**
 * 电台卡片的展示状态（C1 + §3.3 2026-09-13 决议）。
 *
 * 刻意分成两段 —— UI 不该自己去拼「哪些会变」：
 * - **固定**：整档不变，开播那一刻确定
 * - **动态**：随播放与队列调整变化
 *
 * [lastAdjust] 可以是主播的编排思路（结构化 reason 字段的渲染，单句截断）——
 * 这是 C1 的唯一松动出口；仍不允许拼接其他模型文字或长篇文案。
 */
data class RadioCardState(
    /** 【固定】本档主题（用户种子），null = 自动电台 */
    val theme: String?,
    /** 【动态】还有几首待播（不含在播那首） */
    val upcomingCount: Int,
    /** 【动态】下一首曲名 */
    val nextTitle: String?,
    /** 【动态】最近一次调整：主播编排思路或固定文案（「已换一批」/「已补 5 首」），null = 本档还没调过 */
    val lastAdjust: String?,
)

data class RadioTrack(
    val musicId: Long,
    val title: String,
    /** 歌手（RADIO_STATUS 卡 ANCHOR 区显示用；缺省空串表示未知） */
    val artist: String = "",
    val why: String,
    val source: RadioTrackSource = RadioTrackSource.LOCAL,
)

enum class RadioTrackSource { LOCAL, CLOUD }

/**
 * 电台启动来源（W 阶段）——用于审计与种子兜底策略区分。
 */
enum class RadioTrigger {
    /** 首页收音机卡 / 播放页点击（默认） */
    HOME_CLICK,

    /** 对话内触发（"来点电台"）——chatContext 可兜底做种子 */
    CHAT_INPUT,

    /** 连跳 2+ 首触发的重排重建 */
    SKIP_REORDER,

    /** 暂停后恢复 */
    RESUME,
    ;

    companion object {
        /** 从对话原文推断触发来源（恢复类措辞 → RESUME，其余一律 CHAT_INPUT） */
        fun fromChatInput(input: String): RadioTrigger {
            val lower = input.lowercase()
            val resumeWords = listOf("恢复", "继续", "接着", "resume", "continue")
            return if (resumeWords.any { it in lower }) RESUME else CHAT_INPUT
        }
    }
}

/**
 * 电台短时消息（W 阶段）——UI 的 RADIO_STATUS 卡右侧实时提示，4s 自动退场。
 */
sealed class RadioMessage {
    /** 连跳触发重排："跳过 N 首，正在重选..." */
    data class ReorderSkipped(val count: Int) : RadioMessage()

    /** LLM 富化阶段："AI 正在优化歌单..." */
    data object EnrichOptimizing : RadioMessage()

    /** 续歌提示："续播「曲名」" */
    data class TrackContinuing(val title: String) : RadioMessage()

    /** 主题就绪："「蓝调」已就绪" */
    data class ThemeChanged(val theme: String) : RadioMessage()
}

/** 电台短时消息存活时长（与 AgentNoticeBar 侧条 4s 退场一致） */
private const val RADIO_MESSAGE_TTL_MS = 4_000L

/** 切歌过渡暂停去抖窗口：切歌的 pause→play 对通常在 100ms 内，800ms 足够越过它 */
private const val PAUSE_DEBOUNCE_MS = 800L

/** 开播模型调用重试：冷启动第一发最怕瞬时抖动，失败重试 1 次（共 2 发） */
private const val OPENING_ATTEMPTS = 2

/** 开播重试间隔：给网络/端点一点恢复时间，秒开队列已经在响，不差这两秒 */
private const val OPENING_RETRY_DELAY_MS = 2_000L

/** 起声确认窗口：PLAY 发出后等引擎起播（真机起播 <500ms），仍无声则判 PLAY 无效 */
private const val AUDIBLE_CHECK_DELAY_MS = 800L

// ═══════════════════════════════════════════════════════════════════
// 曲库视图预算（spec §7.0.2 D5）
// 曲库规模不可预知，因此不写死「多少首以上就怎样」，而是按 token 预算连续降级：
//   可容纳行数 = 预算 ÷ 每行 token；库 ≤ 行数 → 全量，否则用本地标签粗过滤到行数。
// ═══════════════════════════════════════════════════════════════════

/** 给「曲库清单」的 token 预算（不含 system 与队列部分）。 */
private const val LIBRARY_TOKEN_BUDGET = 6_000

/** 一行「id | 标题 | 艺术家」的大致 token 数（中文按 1 字 ≈ 1 token 粗估）。 */
private const val TOKENS_PER_TRACK = 28

/** 预算再小也至少给这些行，否则模型没得挑。 */
private const val MIN_LIBRARY_ROWS = 40

/** turn 轮曲库候选池的行数上限（开播才给全量/大池，turn 用小池控制 token）。 */
private const val TURN_CANDIDATE_ROWS = 40

/** 快照里「在播之后」队列明细的上限（节目档案按它渲染 id+标题）。 */
private const val SNAPSHOT_QUEUE_VISIBLE = 15

/** 主播编排思路写上卡片时的截断长度（单句，不是文案）。 */
private const val CARD_INTENT_MAX = 40

/** 编排动作的观测静默时长。
 *
 * 换批（REPLACE_QUEUE）会让播放器内部再走一次切歌流程，产生一条「已播 0% 被切走」的假结算，
 * 必须盖掉。2000ms 足够覆盖连锁反应；宁可长一点也不让假信号漏进去。
 */
internal const val ORCHESTRATION_MUTE_MS = 2_000L

// ═══════════════════════════════════════════════════════════════════
// MusicRepository 扩展（已提升到 MusicRepository 接口）
// getGlobalTopLabels / getMusicInfoByIds —— 接口方法直接调
// ═══════════════════════════════════════════════════════════════════
