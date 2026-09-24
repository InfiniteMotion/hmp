package com.hmp.domain.agent.runtime.sub.hello

import com.hmp.domain.agent.card.zhName

import com.hmp.domain.agent.card.cardType
import com.hmp.domain.agent.card.detectTimePhase

import com.hmp.domain.agent.config.EngineDefaults
import com.hmp.domain.agent.runtime.JsonText
import com.hmp.domain.agent.runtime.sub.shared.SubAgent

import com.hmp.domain.agent.card.AnchorContent
import com.hmp.domain.agent.card.AnniversaryContent
import com.hmp.domain.agent.card.AnniversarySubtype
import com.hmp.domain.agent.card.CardPool
import com.hmp.domain.agent.card.DiscoverContent
import com.hmp.domain.agent.card.EnrichTrackingContent
import com.hmp.domain.agent.card.ForgottenContent
import com.hmp.domain.agent.card.GreetingContent
import com.hmp.domain.agent.card.GreetingType
import com.hmp.domain.agent.card.NarrativeContent
import com.hmp.domain.agent.card.NarrativeTimeRange
import com.hmp.domain.agent.card.RadioStatusContent
import com.hmp.domain.agent.runtime.sub.shared.RadioTrack
import com.hmp.domain.agent.card.RecommendContent
import com.hmp.domain.agent.card.RecommendItemRecord
import com.hmp.domain.agent.card.RecommendListPayload
import com.hmp.domain.agent.card.RecommendSource
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.SlideContent
import com.hmp.domain.agent.card.SlideType
import com.hmp.domain.agent.card.TimePhase

import com.hmp.data.database.HelloCardCache
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import com.hmp.data.database.HelloCardCacheDao
import com.hmp.data.database.HelloReportNarrativeDao
import com.hmp.data.database.HelloReportNarrativeEntity
import com.hmp.data.util.currentHour
import com.hmp.data.util.todayDateString
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.PresenceEvent
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.port.Capability
import com.hmp.domain.agent.port.CapabilityState
import com.hmp.domain.agent.runtime.StopSignal
import com.hmp.domain.agent.runtime.i18n.resolvePrompt
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.enum.LabelName
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.lyrics.LrcParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.hmp.data.database.currentTimeMillis
import com.hmp.platform.Volatile

/**
 * Hello 的**会话级可变状态** —— 跨轮演进、彼此关联的一组字段。
 *
 * 原先它们是 12 个散落的 `@Volatile private var`（声明区 36 行），于是：
 * "问候这一路到底有哪些状态"要靠数声明才知道。收进一处后，**本类就是这个 Agent 的
 * 会话状态清单** —— 新增字段请加在这里，不要再散回宿主类。
 *
 * ## 刻意**不**提供 `reset()`
 *
 * 曾一度加过 `reset()`（清空全部字段），但它**没有任何合法调用点**，故删除：
 * - `shutdown()` 里调它 = 改变行为契约 —— Hello 可被 `stopHello` / `startHello` 反复启停，
 *   清空 `recentGreetingTypes` / `greetingIndex` 会让"说话别重复"从零重来。
 * - `MasterAgent.clearAllMemory()` 清的是 `UserMemory`（画像三表），
 *   与本类的 `HelloMemory`（`hello_card_cache` 表）是**两个不同的存储**，互不覆盖。
 * - 反证：`recentGreetingTypes` / `recentGreetingContents` 本来就**特意镜像进 DAO + memory**
 *   （见 `HelloSubAgent` 内 GREETING 落盘注释，为的是 App 重启后不丢）。
 *   既然设计意图是"跨重启保留"，就没有"清空"这条路。
 *
 * ⇒ 若将来确要重置，**先找到真实触发场景**再加方法；无调用点的重置方法会腐烂成误导。
 *
 * 注意**不**含 `dailyRecommendCount` / `recommendListSize`：那两个来自 `EngineDefaults`，
 * 是配置常量（`val`），不是会演进的状态。判据同 3c：**状态要一起搬，配置不跟着走**。
 *
 * `@Volatile` 随字段一起搬 —— 它标的是字段本身，与宿主类无关。
 */
private class HelloSessionState {
    /** 上次检测到的播放曲目 ID（用于切歌 → GREETING 刷新检测） */
    @Volatile
    var lastTrackId: Long? = null

    /** 当前时段（minuteTickLoop 维护） */
    @Volatile
    var lastPhase: TimePhase? = null

    /** 门面问候轮换索引，@Volatile 避免 Dispatchers.Default 多线程 data race */
    @Volatile
    var greetingIndex: Int = 0

    @Volatile
    var agentPaused: Boolean = false

    // ═══ GREETING 限频与多样性 ═══

    /** 上次 GREETING 刷新时间戳 */
    @Volatile
    var lastGreetingRefreshAt: Long = 0L

    /** 上次 GREETING 关联的歌曲 ID（切歌联动去重） */
    @Volatile
    var lastGreetingSongId: Long? = null

    /** 最近 3 次 GREETING 类型（轮转惩罚） */
    val recentGreetingTypes: ArrayDeque<GreetingType> = ArrayDeque()

    /** 最近 5 次 GREETING 内容前 30 字（LLM 去重提示） */
    val recentGreetingContents: ArrayDeque<String> = ArrayDeque()

    /** 音乐纪念日缓存日期（同一天只查一次） */
    @Volatile
    var lastAnnivCheckDate: String? = null

    /** 音乐纪念日缓存结果 */
    @Volatile
    var cachedAnniversaryHint: String? = null

    /** GREETING 兜底定时上次检查时间 */
    @Volatile
    var lastGreetingPeriodicCheck: Long = 0L

    /** Fix1: 内存补跑守卫（DAO=null 时用这个标志，避免无限循环 push/pop） */
    @Volatile
    var todayCardsGenerated: Boolean = false
}

/**
 * W0 HelloSubAgent：门面副驾驶（F1-F6 铁则）。
 *
 * 职责：把"音乐库 + 最近行为 + 当前时段"变成用户想看的内容卡。
 * 三个工作协程：
 * ① collectPresenceEvents —— DjBlank（每次切歌 → 更新 GREETING）
 *                          —— AgentProgress(radio) → 更新/清除 RADIO_STATUS
 * ② dailyRefreshLoop —— 每日凌晨补跑 RECOMMEND / DISCOVER / FORGOTTEN / ANNIVERSARY + 报告叙事段
 * ③ minuteTickLoop —— 每分钟检测时段变化 → 更新 RECOMMEND；刷新 ANCHOR
 *
 * 暂停/恢复：Scheduler priority=HELLO(4) 永不暂停；StopSignal 桥接 Mutex。
 * SharedFlow 丢事件的主动补偿：
 * - runLoop 启动时从 DAO 恢复上次生成的卡
 * - runLoop 启动时主动 push 初始问候卡（兜底）
 *
 * MusicRepository 依赖：所有卡型生成都通过接口方法（接口返回空列表/零值不崩）。
 * enableLlm 动态启用：transport != null && enrichConfig?.isConfigured == true → LLM 生成，否则兜底模板。
 * 由 MasterAgent.updateAiConfig 热推送，用户改 AI 配置后 <100ms 生效。
 */
class HelloSubAgent internal constructor(
    agentId: String = "hello",
    contextBudget: AgentContextBudget,
    toolRegistryView: ToolRegistryView,
    /** 注入的 DAO（nullable 降级为内存卡池） */
    private val cardCacheDao: HelloCardCacheDao? = null,
    private val narrativeDao: HelloReportNarrativeDao? = null,
    private val musicRepository: MusicRepository? = null,
    private val presenceBus: PresenceBus? = null,
    private val nowPlayingProvider: NowPlayingContextProvider? = null,
    private val stopSignal: StopSignal? = null,
    /** AI 端点配置（热更新：MasterAgent.updateAiConfig 可动态替换） */
    private var enrichConfig: AiEndpointConfig? = null,
    /** 是否启用 LLM 生成（热更新：用户改配置后 MasterAgent 推新值） */
    private var enableLlm: Boolean = false,
    /** 电台查询代理（nullable；MasterAgent.queryRadioPlaylist 直接传进来，避免循环依赖） */
    private val radioPlaylistProvider: suspend () -> List<RadioTrack>? = { null },
    /**
     * 画像简报（契约 v3.8，MasterAgent.startHello 从 `userMemory.helloBriefing()` 取）：
     * 门面卡片与推荐的听众参考 —— 自带「仅供参考」口径，null = 冷启动无画像。
     */
    private val memoryBriefing: String? = null,
    /**
     * UserMemory 引用（契约 v3.4 + F9-T1）：报告叙事生成时取画像侧写片段（`factsRenderForNarrative`）。
     * nullable —— 画像链路静默跳过，不拖垮 HelloSubAgent。
     * 与 memoryBriefing 互补：memoryBriefing 是预渲染的摘录（LLM system prompt 注入用），
     * userMemory 是实时引用（报告叙事生成时按需取最新侧写）。
     */
    private val userMemory: com.hmp.domain.agent.profile.UserMemory? = null,
    // ── v1 F9-T2：prompt 多语言 + 用户覆盖（可选，默认 null 回落硬编码）──
    /** Agent 独立语言偏好："global" / "zh" / "en" / "auto"。null = 全部回落硬编码。 */
    private val promptPreferredLang: String? = null,
    /** 全局语言（preferredLang="global" 时使用）。 */
    private val globalReplyLanguage: String = "zh",
    /** 用户覆盖的 prompt Map（key → 用户写的 prompt 文本）。 */
    private val promptOverrides: Map<String, String> = emptyMap(),
    /** 统一 LLM 温度（null = 按卡型 typeTemperature 兜底）。 */
    private var llmTemperature: Float? = null,
) : SubAgent(agentId, contextBudget, toolRegistryView), Capability {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── 构造诊断日志 ──
    init {
        val cfg = enrichConfig
        HmpLog.i(LogTag.AgentHello) {
            "👋 HelloSubAgent created | enableLlm=$enableLlm | temp=${llmTemperature ?: "(per-card default)"} | " +
            "hasLLM=${cfg != null} | endpoint=${cfg?.endpoint?.take(40) ?: "(none)"} | " +
            "model=${cfg?.selectedModel?.take(30) ?: "(default)"} | hasKey=${cfg?.apiKey?.isNotBlank() == true} | " +
            "promptLang=${promptPreferredLang ?: "(global→$globalReplyLanguage)"} | " +
            "promptOverrides=${promptOverrides.size} keys"
        }
    }

    /** 卡片池（StateFlow 暴露给 UI collect） */
    private val cardPool = CardPool()

    /** 记忆协调层：Room 持久化 + 内存缓存，跨卡片/跨天/跨周去重 */
    private val memory = HelloMemory(cardCacheDao)

    /** UI collect 的 StateFlow */
    val cards: StateFlow<List<SlideCard>> get() = cardPool.cards

    // ═══ G6：每日推荐 / 私人推荐列表 ═══
    /** null=尚未生成（UI 视为"生成中"）；空 items=无数据（入口隐藏） */
    private val _dailyRecommendList = MutableStateFlow<RecommendListPayload?>(null)
    private val _privateRecommendList = MutableStateFlow<RecommendListPayload?>(null)
    val dailyRecommendList: StateFlow<RecommendListPayload?> get() = _dailyRecommendList
    val privateRecommendList: StateFlow<RecommendListPayload?> get() = _privateRecommendList

    /** 推荐列表目标条数 */
    private val recommendListSize: Int = EngineDefaults.HELLO_RECOMMEND_LIST_SIZE

    /** 会话级可变状态 —— 12 个跨轮演进字段收进一处（见 [HelloSessionState]）。 */
    private val session = HelloSessionState()

    /** 每日凌晨生成的推荐卡数量（配置常量，**不是**会话状态） */
    private val dailyRecommendCount: Int = EngineDefaults.HELLO_DAILY_RECOMMEND_COUNT

    // ═══ SubAgent.runLoop ═══

    override suspend fun runLoop() {
        HmpLog.i(LogTag.AgentHello) { "👋 runLoop start" }
        isActive = true
        runState = AgentRunState.RUNNING

        // ── 同步初始化（协程启动前先铺好初始状态，避免 race） ──
        // ① 从 Room 恢复今日记忆缓存
        runCatching { memory.loadToday() }
            .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 memory.loadToday failed (non-fatal)" } }

        // ② 从 DAO 恢复今日缓存卡（用 replace，保证顺序稳定）
        runCatching { initializeFromDao() }
            .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 initializeFromDao failed (non-fatal)" } }

        // ②.1 G6：恢复今日推荐列表（跨重启不重算）
        runCatching { restoreRecommendLists() }
            .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 restoreRecommendLists failed (non-fatal)" } }

        // ② 检查今日卡是否已生成（one-shot，同步跑完退出）
        runCatching { dailyRefreshLoop() }
            .onFailure { e -> HmpLog.e(LogTag.AgentHello, e) { "👋 dailyRefreshOnce failed" } }

        // ③ 立即 push 常驻卡 + 兜底 GREETING + 检查 Radio 状态
        runCatching { initializeAnchorCards() }
            .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 initializeAnchorCards failed (non-fatal)" } }

        // ── 协程启动（初始状态已铺好，PresenceEvent 不会 race） ──
        val presenceJob = scope.launch { collectPresenceEvents() }
        val tickJob = scope.launch { minuteTickLoop() }

        // runLoop 自身只负责暂停/恢复 + 优雅退出
        while (scope.isActive && isActive) {
            session.agentPaused = true
            stopSignal?.waitResume()
            session.agentPaused = false
            if (stopSignal?.shouldSoftStop() == true) break
            delay(500)
        }

        // 清理
        presenceJob.cancel()
        tickJob.cancel()
        cardPool.clear()
        runState = AgentRunState.PAUSED
        HmpLog.i(LogTag.AgentHello) { "👋 runLoop exited" }
    }

    override suspend fun shutdown() {
        isActive = false
        cardPool.clear()
        scope.cancel()                              // D3: 取消所有子协程（timer 等）
        super.shutdown()
        HmpLog.i(LogTag.AgentHello) { "👋 shutdown complete" }
    }

    // ═══ 工作协程 #1：PresenceBus 事件收集 ═══

    private suspend fun collectPresenceEvents() {
        val bus = presenceBus ?: run {
            HmpLog.w(LogTag.AgentHello) { "👋 presenceBus null, skip collectPresenceEvents" }
            return
        }
        HmpLog.i(LogTag.AgentHello) { "👋 collectPresenceEvents started" }
        bus.events.collect { event ->
            if (session.agentPaused) return@collect  // D2: runLoop 暂停期间跳过事件
            when (event) {
                is PresenceEvent.DjBlank -> {
                    HmpLog.d(LogTag.AgentHello) { "👋 DjBlank → maybe update GREETING" }
                    runCatching { refreshGreetingIfNeeded(30_000L, reason = "DjBlank") }
                        .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 DjBlank GREETING refresh failed" } }
                }
                // 注意：AgentProgress (RADIO_STATUS) 已拆到 UI 层直接订阅 radioState
                else -> { /* 其他事件忽略 */ }
            }
        }
    }

    // ═══ 启动检查：今日卡是否已生成 ═══
    //
    // 策略：
    //   - runLoop 启动时立即检查一次（覆盖绝大多数场景：App 每日开关）
    //   - 跨天兜底交给 minuteTickLoop（每分钟已在跑，加个日期变化检测即可）
    //   - 不再 while 循环，启动时跑一次就退出

    private suspend fun dailyRefreshLoop() {
        HmpLog.i(LogTag.AgentHello) { "👋 dailyRefreshLoop: one-shot check on start" }
        runCatching { checkAndRunDailyRefresh() }
            .onFailure { e -> HmpLog.e(LogTag.AgentHello, e) { "👋 dailyRefreshOnce failed" } }
        // 跑完即退出——跨天由 minuteTickLoop 兜底
    }

    /** 核心逻辑：检查今日是否需要跑 dailyRefreshOnce，需要则跑 */
    private suspend fun checkAndRunDailyRefresh() {
        val today = todayString()
        val dao = cardCacheDao
        val allTodayTypes: Set<String> = if (dao != null) {
            runCatching { dao.getLatestCardTypesByDate(today) }.getOrDefault(emptyList()).toSet()
        } else {
            emptySet()
        }
        val hasTodayCards = if (dao != null) {
            SlideType.RECOMMEND.name in allTodayTypes
                || SlideType.DISCOVER.name in allTodayTypes
                || SlideType.FORGOTTEN.name in allTodayTypes
                || SlideType.ANNIVERSARY.name in allTodayTypes
        } else {
            session.todayCardsGenerated
        }

        if (!hasTodayCards) {
            HmpLog.i(LogTag.AgentHello) { "👋 dailyRefresh: today=$today not yet generated → run" }
            runCatching { dailyRefreshOnce() }
                .onFailure { e -> HmpLog.e(LogTag.AgentHello, e) { "👋 dailyRefreshOnce failed" } }
            if (dao == null) {
                session.todayCardsGenerated = true
            }
        } else if (dao != null) {
            // G6：卡片已在，但两个推荐列表可能缺失（升级新增 / 上次生成失败）→ 只补列表，不重跑整批卡片
            val missingLists = RecommendSource.entries.filter { it.cardType() !in allTodayTypes }
            if (missingLists.isNotEmpty()) {
                HmpLog.i(LogTag.AgentHello) { "👋 dailyRefresh: cards exist but lists missing=$missingLists → refill" }
                missingLists.forEach { src ->
                    runCatching { refreshRecommendList(src) }
                        .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 refill recommend list $src failed" } }
                }
            }
        }
        // 报告叙事段也在此时检查
        runCatching { ensureReportNarrativeUpToDate() }
            .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 report narrative ensure failed" } }
    }

    /** 每日批量生成：RECOMMEND + DISCOVER + FORGOTTEN + ANNIVERSARY + 写入 DAO */
    private suspend fun dailyRefreshOnce() {
        val repo = musicRepository
        // B3: getMusicCount 轻量判空，不拉全曲库
        if (repo == null || runCatching { repo.getMusicCount().first() }.getOrDefault(0) <= 0) {
            HmpLog.w(LogTag.AgentHello) { "👋 dailyRefreshOnce: musicRepository null or library empty, skip" }
            return
        }

        val today = todayString()
        val allCards = mutableListOf<SlideCard>()

        // ① RECOMMEND（兜底版：按时段 label 取曲目）
        val currentPhase = detectTimePhase(currentHour())
        // B4: 同类型卡只 replace 第一张，避免 forEach replace 语义导致只剩最后一张
        val recommendCard = runCatching {
            generateRecommendCards(phase = currentPhase, count = dailyRecommendCount).firstOrNull()
        }.getOrNull()
        if (recommendCard != null) {
            cardPool.replace(recommendCard.type, recommendCard)
            allCards += recommendCard
        }

        // ② DISCOVER（兜底版：取 POP 标签）
        val discover = runCatching { generateDiscoverCard() }.getOrNull()
        if (discover != null) {
            cardPool.replace(discover.type, discover)
            allCards += discover
        }

        // ③ FORGOTTEN
        val forgotten = runCatching { checkForgotten() }.getOrNull()
        if (forgotten != null) {
            cardPool.replace(SlideType.FORGOTTEN, forgotten)
            allCards += forgotten
        } else {
            cardPool.setVisible(SlideType.FORGOTTEN, false)
        }

        // ④ ANNIVERSARY
        val anniversary = runCatching { checkAnniversary() }.getOrNull()
        if (anniversary != null) {
            cardPool.replace(SlideType.ANNIVERSARY, anniversary)
            allCards += anniversary
        } else {
            cardPool.setVisible(SlideType.ANNIVERSARY, false)
        }

        // ⑤ 写入 DAO（同日同类型删旧 + 插新；保留全量历史）+ 更新 memory 缓存
        runCatching {
            allCards.forEach { card ->
                val now = currentTimeMillis()
                val today = todayString()
                cardCacheDao?.deleteSameDaySameType(card.type.name, today)
                val cache = buildHelloCardCache(card, now, today, llmUsed = enableLlm)
                cardCacheDao?.insert(cache)
                memory.record(cache)  // 内存缓存同步更新（DAO=null 时也能工作）
            }
        }.onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 DAO insert failed (non-fatal)" } }

        // ⑥ G6：两个推荐列表（每日 / 私人）——种子 + 扩列，一次性产出
        runCatching { refreshRecommendList(RecommendSource.DAILY) }
            .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 refreshRecommendList DAILY failed" } }
        runCatching { refreshRecommendList(RecommendSource.PRIVATE) }
            .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 refreshRecommendList PRIVATE failed" } }

        HmpLog.i(LogTag.AgentHello) { "👋 dailyRefreshOnce: done, ${allCards.size} cards generated" }
    }

    // ═══ 工作协程 #3：每分钟 tick ═══

    private suspend fun minuteTickLoop() {
        HmpLog.i(LogTag.AgentHello) { "👋 minuteTickLoop started" }
        var lastDate = todayString()  // 跨天检测
        while (scope.isActive && isActive && !session.agentPaused) {
            delay(60_000L)

            // ① 跨天兜底：App 长驻到明天 → 补跑今日卡
            val now = todayString()
            if (now != lastDate) {
                HmpLog.i(LogTag.AgentHello) { "👋 minuteTickLoop: date changed $lastDate → $now, run dailyRefreshOnce" }
                lastDate = now
                runCatching { checkAndRunDailyRefresh() }
                    .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 date-change dailyRefresh failed" } }
            }

            // ② 检查时段变化
            val currentPhase = detectTimePhase(currentHour())
            if (currentPhase != session.lastPhase) {
                HmpLog.i(LogTag.AgentHello) { "👋 minuteTickLoop: phase changed ${session.lastPhase} → $currentPhase" }
                session.lastPhase = currentPhase
                // 时段变了 → 刷新 RECOMMEND（内存 + DAO 双写）
                runCatching { refreshRecommendCard(currentPhase) }
                    .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 phase-change RECOMMEND refresh failed" } }
                // 时段变了 → 必刷 GREETING（限频 0，跨变必过）
                runCatching { refreshGreetingIfNeeded(0L, reason = "phase-change") }
                    .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 phase-change GREETING refresh failed" } }
            }

            // ②.5 RECOMMEND 缺失重试：启动时 label 可能还没打上，导致 dailyRefreshOnce 里
            // RECOMMEND 生成失败。之后 label 打上了但不会再有人来触发 RECOMMEND 生成——
            // 所以这里每分钟检查一次，直到卡真正出现在 CardPool 里为止。
            if (!cardPool.containsType(SlideType.RECOMMEND)) {
                runCatching {
                    val phase = detectTimePhase(currentHour())
                    refreshRecommendCard(phase)
                }.onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 RECOMMEND missing-retry failed" } }
            }

            // ③ GREETING 切歌联动（5min 限频）
            runCatching {
                val ctx = nowPlayingProvider?.getNowPlaying()
                if (ctx?.isPlaying == true && ctx.currentMusicId != null && ctx.currentMusicId != session.lastTrackId) {
                    HmpLog.d(LogTag.AgentHello) { "👋 minuteTickLoop: track changed → try GREETING refresh" }
                    session.lastTrackId = ctx.currentMusicId
                    refreshGreetingIfNeeded(5 * 60_000L, reason = "track-change", songId = ctx.currentMusicId)
                }
            }.onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 track-change GREETING check failed" } }

            // ④ GREETING 兜底定时（每 2h 检查一次）
            runCatching {
                val nowMs = currentTimeMillis()
                if (nowMs - session.lastGreetingPeriodicCheck >= 2 * 60 * 60_000L) {
                    session.lastGreetingPeriodicCheck = nowMs
                    refreshGreetingIfNeeded(30 * 60_000L, reason = "periodic")
                }
            }.onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 periodic GREETING check failed" } }
            // 注意：ANCHOR 和 RADIO_STATUS 已拆到 UI 层直接订阅数据源，不再由 agent 管理
        }
    }

    // ═══ 初始化：从 DAO 恢复 + 常驻卡 push ═══

    /** SharedFlow 丢 DjBlank 的主动补偿 —— 只恢复今日缓存；用 replace 避免同类型重复 */
    private suspend fun initializeFromDao() {
        val dao = cardCacheDao ?: return
        val today = todayString()
        val restoreTypes = listOf(
            SlideType.RECOMMEND,
            SlideType.DISCOVER,
            SlideType.FORGOTTEN,
            SlideType.ANNIVERSARY,
        )
        var restored = 0
        for (type in restoreTypes) {
            // ✅ 只恢复今日生成的卡，避免昨日过时卡停留在 CardPool
            val cache = dao.getLatest(type.name, today) ?: continue
            val content = stringToCardContent(type, cache.cardContentJson)
            if (content != null) {
                cardPool.replace(type, SlideCard(SlideCard.newId(), type, content))
                restored++
            }
        }
        if (restored > 0) HmpLog.i(LogTag.AgentHello) { "👋 initializeFromDao: restored $restored today=$today cards" }
    }

    /** runLoop 启动时 push GREETING（ANCHOR/RADIO_STATUS 已拆到 UI 层直接订阅数据源） */
    private suspend fun initializeAnchorCards() {
        // 启动时直接生成一次 GREETING（允许，不受限频）
        runCatching { refreshGreetingIfNeeded(0L, reason = "startup") }
            .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 startup GREETING failed" } }
    }

    // ═══ 七种卡型生成（支持 LLM 热更新：enableLlm=true 时进 LLM 分支，否则走兜底模板） ═══

    // ──────────────────── GREETING：音乐百科小卡片 ────────────────────

    /** 限频守卫：时间间隔足够 + 歌曲没重复 → 执行刷新 */
    private suspend fun refreshGreetingIfNeeded(
        minIntervalMs: Long,
        reason: String,
        songId: Long? = null,
    ) {
        val now = currentTimeMillis()
        if (minIntervalMs > 0 && now - session.lastGreetingRefreshAt < minIntervalMs) {
            HmpLog.d(LogTag.AgentHello) { "👋 GREETING skip: too soon (interval=${minIntervalMs}ms, reason=$reason)" }
            return
        }
        if (songId != null && songId == session.lastGreetingSongId) {
            HmpLog.d(LogTag.AgentHello) { "👋 GREETING skip: same song ($songId)" }
            return
        }
        val content = generateGreeting()
        session.lastGreetingRefreshAt = now
        if (songId != null) session.lastGreetingSongId = songId
        cardPool.replace(
            SlideType.GREETING,
            SlideCard(SlideCard.newId(), SlideType.GREETING, content)
        )
        // GREETING 也写 DAO + memory：App 重启后 session.recentGreetingTypes 内存丢了，
        // 靠 Room 恢复 todayCache.greetingTypes 来避免重复同类型。
        runCatching {
            val card = SlideCard(SlideCard.newId(), SlideType.GREETING, content)
            val today = todayString()
            cardCacheDao?.deleteSameDaySameType(SlideType.GREETING.name, today)
            val cache = buildHelloCardCache(card, now, today, llmUsed = enableLlm)
            cardCacheDao?.insert(cache)
            memory.record(cache)
        }.onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 GREETING dao write failed (non-fatal)" } }
        HmpLog.i(LogTag.AgentHello) { "👋 GREETING refreshed: type=${content.type} reason=$reason" }
    }

    /** GREETING 核心生成：收集上下文 → 打分路由 → LLM/fallback → 多样性更新 */
    private suspend fun generateGreeting(): GreetingContent {
        val phase = detectTimePhase(currentHour())

        // ── 收集上下文 ──
        val nowPlaying = runCatching { nowPlayingProvider?.getNowPlaying() }.getOrNull()
        val musicId = nowPlaying?.currentMusicId
        val musicInfo = nowPlaying?.currentMusicInfo?.music
        val nowPlayingStr = if (musicInfo != null) "${musicInfo.title} — ${musicInfo.artist}" else null

        // 音乐纪念日（带缓存）
        val annivHint = checkMusicAnniversary()

        // ── 打分路由选类型 ──
        val (type, signalHint) = routeGreetingType(phase, musicInfo, annivHint)

        // ── 多样性防线：记录本次类型和内容 ──
        session.recentGreetingTypes.addLast(type)
        if (session.recentGreetingTypes.size > 3) session.recentGreetingTypes.removeFirst()

        val text = callHelloLlm(
            cardType = "GREETING",
            systemPrompt = typeSystemPrompt(type),
            userPrompt = buildGreetingPrompt(type, phase, nowPlayingStr, annivHint, signalHint),
            temperature = resolveTemperature(typeTemperature(type)),
        )
        val fromFallback = text == null
        val finalText = text ?: fallbackForType(type)
        if (finalText.isNotBlank()) {
            session.recentGreetingContents.addLast(finalText.take(30))
            if (session.recentGreetingContents.size > 5) session.recentGreetingContents.removeFirst()
        }

        return GreetingContent(
            text = finalText,
            fromFallback = fromFallback != false,
            phase = phase,
            type = type,
            contextHint = signalHint,
        )
    }

    /** 打分路由：收集信号 → 6 类型打分 → 惩罚 → 扰动 → 胜出 */
    private fun routeGreetingType(
        phase: TimePhase,
        nowPlayingMusic: com.hmp.domain.music.Music?,
        annivHint: String?,
    ): Pair<GreetingType, String?> {
        val scores = GreetingType.values().associateWith { 0.0 }.toMutableMap()

        // ① 当前播放歌曲（强信号）
        if (nowPlayingMusic != null) {
            HelloGreetingProfiles.TYPE_PREFS_NOW_PLAYING.forEach { (t, w) -> scores[t] = (scores[t] ?: 0.0) + w * 100.0 }
        }

        // ② 音乐纪念日（强信号）
        if (!annivHint.isNullOrBlank()) {
            HelloGreetingProfiles.TYPE_PREFS_ANNIV.forEach { (t, w) -> scores[t] = (scores[t] ?: 0.0) + w * 100.0 }
        }

        // ③ 时段（稳定信号）
        val phasePrefs = HelloGreetingProfiles.TYPE_PREFS_PHASE[phase]
        if (phasePrefs != null) {
            phasePrefs.forEach { (t, w) -> scores[t] = (scores[t] ?: 0.0) + w * 60.0 }
        }

        // ④ Top artist/genre（弱信号，暂不查 DB 避免额外开销）—— 留空

        // 惩罚：session.recentGreetingTypes 中出现过的类型降分
        val penaltyMap = mutableMapOf<GreetingType, Double>()
        session.recentGreetingTypes.forEachIndexed { idx, t ->
            val factor = when (idx) { 0 -> 0.4; 1 -> 0.6; else -> 0.8 }
            penaltyMap[t] = (penaltyMap[t] ?: 0.0) + factor
        }
        scores.forEach { (t, _) ->
            val penalty = penaltyMap[t] ?: 0.0
            val current = scores[t] ?: 0.0
            scores[t] = current * (1.0 - penalty * 0.5)
        }

        // 随机扰动（0.9-1.1）
        scores.forEach { (t, v) -> scores[t] = v * (0.9 + kotlin.random.Random.nextDouble() * 0.2) }

        val winner = scores.entries.maxByOrNull { it.value }?.key ?: GreetingType.QUOTE

        // 生成 contextHint
        val hint = buildString {
            if (nowPlayingMusic != null) append("来自「${nowPlayingMusic.title}」")
            if (!annivHint.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(annivHint)
            }
        }.ifBlank { null }

        return winner to hint
    }

    /** 音乐纪念日检查（带缓存：同一天只查一次） */
    private suspend fun checkMusicAnniversary(): String? {
        val today = todayString()
        if (session.lastAnnivCheckDate == today) return session.cachedAnniversaryHint
        session.lastAnnivCheckDate = today

        if (!enableLlm) {
            session.cachedAnniversaryHint = null
            return null
        }
        val parts = today.split("-")
        val month = parts.getOrNull(1)?.toIntOrNull() ?: run {
            session.cachedAnniversaryHint = null; return null
        }
        val day = parts.getOrNull(2)?.toIntOrNull() ?: run {
            session.cachedAnniversaryHint = null; return null
        }
        val prompt = """今天是 $month 月 $day 日。请判断音乐史上今天是否有重要事件发生。

返回 JSON：
{"hasEvent": true/false, "eventType": "诞辰/逝世/发行/成立/解散", "subject": "主体名称", "year": 年份}

要求：
- 必须是真实可查证的音乐事件，流行、古典、摇滚、爵士、华语乐坛都可以
- 如果不确定或没有，请返回 {"hasEvent": false}
- 不要编造，不确定就说没有"""
        val hint = callHelloLlm(
            cardType = null,  // ANNIVERSARY 不注入记忆：纪念日查询走 HelloMemory.isAnniversaryQueriedToday 独立短路（仅 dailyRefreshOnce 每日调一次），不依赖 cardType 注入
            systemPrompt = resolveIfConfigured("hello.greeting.history", "你是音乐史专家，擅长准确回忆具体日期的音乐事件。"),
            userPrompt = prompt,
            temperature = resolveTemperature(0.2f),
            postProcess = { raw ->
                // 先提取 JSON 块：处理 markdown fenced code / 前后夹杂额外文字
                val text = JsonText.extractJsonBlock(raw.trim())
                if (text.contains("\"hasEvent\"\\s*:\\s*true".toRegex())) {
                    val subject = "\"subject\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(text)?.groupValues?.get(1)
                    val eventType = "\"eventType\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(text)?.groupValues?.get(1)
                    if (subject != null) {
                        val typeZh = when (eventType) {
                            "诞辰" -> "诞辰"
                            "逝世" -> "逝世"
                            "发行" -> "专辑发行"
                            "成立" -> "乐队成立"
                            "解散" -> "乐队解散"
                            else -> "音乐事件"
                        }
                        "$month 月 $day 日 · $subject 的${typeZh}纪念日"
                    } else null
                } else null
            },
        )
        session.cachedAnniversaryHint = hint
        return hint
    }


    /** 按类型组装完整 LLM prompt */
    private fun buildGreetingPrompt(
        type: GreetingType,
        phase: TimePhase,
        nowPlayingStr: String?,
        annivHint: String?,
        signalHint: String?,
    ): String {
        val phaseZh = phase.zhName()
        val sb = StringBuilder()
        sb.appendLine("请生成一段中文音乐内容，类型：${type.zhName()}。")
        sb.appendLine("")

        // 上下文区
        sb.appendLine("当前上下文：")
        sb.appendLine("  时段：$phaseZh")
        if (!nowPlayingStr.isNullOrBlank()) sb.appendLine("  当前播放：$nowPlayingStr")
        if (!annivHint.isNullOrBlank()) sb.appendLine("  音乐事件：$annivHint")
        sb.appendLine("")

        // 去重提示
        if (session.recentGreetingContents.isNotEmpty()) {
            sb.appendLine("以下是你最近输出过的内容，请避开相同的主题和表达：")
            session.recentGreetingContents.forEachIndexed { i, content ->
                sb.appendLine("  ${i + 1}. $content...")
            }
            sb.appendLine("")
        }

        // 类型专属要求
        sb.appendLine(typePromptRequirement(type))
        sb.appendLine("")
        sb.appendLine("直接输出正文，不要标题、引号、前缀或解释。")
        return sb.toString()
    }

    /** 类型专属写作要求 */
    /** 类型专属写作要求（内容定义见 [HelloGreetingProfiles]）。 */
    private fun typePromptRequirement(type: GreetingType): String =
        HelloGreetingProfiles.of(type).requirement

    /** 类型专属 system prompt */
    /** 私有 helper：有 preferredLang 时走 resolvePrompt，否则回落硬编码。 */
    private fun resolveIfConfigured(key: String, fallback: String): String {
        val lang = promptPreferredLang ?: return fallback
        val resolved = resolvePrompt(key, lang, globalReplyLanguage, promptOverrides)
        return resolved.ifBlank { fallback }
    }

    /** 类型专属 system prompt（词表 key 与兜底文案见 [HelloGreetingProfiles]）。 */
    private fun typeSystemPrompt(type: GreetingType): String {
        val profile = HelloGreetingProfiles.of(type)
        return resolveIfConfigured(profile.promptKey, profile.systemFallback)
    }

    /** 统一 LLM 温度覆盖（config 驱动）：有值时覆盖所有卡型温度。 */
    private fun resolveTemperature(cardSpecific: Float): Float = llmTemperature ?: cardSpecific

    /** 类型专属 temperature（兜底，config 未设时生效）。 */
    private fun typeTemperature(type: GreetingType): Float =
        HelloGreetingProfiles.of(type).temperature

    /** 6 类型独立 fallback 模板库（LLM 不可用时的保底）—— 池子在 [HelloGreetingProfiles]，这里只做轮转。 */
    private fun fallbackForType(type: GreetingType): String {
        val list = HelloGreetingProfiles.of(type).fallbacks
        val idx = (session.greetingIndex++) % list.size
        return list[idx]
    }


    /** RECOMMEND：每日 + 时段切换，走 MusicRepository 接口 */
    private suspend fun generateRecommendCards(
        phase: TimePhase,
        count: Int,
    ): List<SlideCard> {
        val repo = musicRepository ?: return emptyList()
        val label = labelForPhase(phase) ?: return emptyList()

        val ids = runCatching { repo.getMusicIdListByType(label).take(count) }
            .getOrDefault(emptyList())
        if (ids.isEmpty()) return emptyList()

        val infos = runCatching { repo.getMusicInfoByIds(ids) }
            .getOrDefault(emptyList())
            .associate { it.music.id to it }

        return ids.map { id ->
            val info = infos[id]
            val durSec = (info?.music?.duration?.div(1000))?.toInt()?.coerceAtLeast(0) ?: 0
            // 组装歌曲上下文给 LLM
            val title = info?.music?.title ?: "推荐曲目"
            val artist = info?.music?.artist
            val playCount = info?.userInfo?.playCount ?: 0
            val lyricSummary = runCatching { fetchLyricSummary(id) }.getOrNull()
            val reason = reasonForTrack(
                label = label,
                phase = phase,
                trackTitle = title,
                artist = artist,
                playCount = playCount,
                lyricSummary = lyricSummary,
            )
            SlideCard(
                SlideCard.newId(),
                SlideType.RECOMMEND,
                RecommendContent(
                    trackId = id,
                    trackTitle = title,
                    reason = reason,
                    currentPhase = phase,
                    sourceLabel = label.name,
                    durationSec = durSec,
                ),
            )
        }
    }

    /** DISCOVER：每日凌晨；H2 兜底——取 getGlobalTopLabels 第一个非空 label；预取曲目详情存进 Content。 */
    private suspend fun generateDiscoverCard(): SlideCard? {
        val repo = musicRepository ?: return null

        // 先查活跃 label；空则兜底固定 POP/ROCK/CALM
        val labels = runCatching { repo.getGlobalTopLabels(5) }
            .getOrDefault(emptyList()) + listOf(LabelName.POP, LabelName.ROCK, LabelName.CALM)

        for (label in labels.distinct()) {
            val ids = runCatching { repo.getMusicIdListByType(label, limit = 3) }
                .getOrDefault(emptyList())
            if (ids.isNotEmpty()) {
                // 预取曲目详情 → UI 零延迟渲染
                val infos = runCatching { repo.getMusicInfoByIds(ids) }.getOrDefault(emptyList())
                    .associateBy { it.music.id }
                val orderedInfos = ids.mapNotNull { infos[it] }
                return SlideCard(
                    SlideCard.newId(),
                    SlideType.DISCOVER,
                    DiscoverContent(
                        target = label.name,
                        reason = reasonForDiscover(label),
                        trackIds = ids,
                        trackTitles = orderedInfos.map { it.music.title },
                        trackArtists = orderedInfos.map { it.music.artist },
                        trackDurations = orderedInfos.map { (it.music.duration / 1000).toInt().coerceAtLeast(0) },
                    ),
                )
            }
        }
        return null
    }

    /** FORGOTTEN：每日凌晨扫历史——优先 getForgottenTracks(30)，没有则 90 天；用真实 lastPlayed 算 daysSince。 */
    private suspend fun checkForgotten(): SlideCard? {
        val repo = musicRepository ?: return null
        val now = currentTimeMillis()
        val (id, lastPlayed) = runCatching {
            repo.getForgottenTracks(days = 30).firstOrNull()
                ?: repo.getForgottenTracks(days = 90).firstOrNull()
        }.getOrNull() ?: return null

        // 真实天数：从未播放过(lastPlayed=null) → 给 365+ 作为兜底
        val daysSince = lastPlayed?.let { ((now - it) / 86_400_000L).toInt().coerceAtLeast(30) } ?: 365

        val info = runCatching { repo.getMusicInfoByIds(listOf(id)).firstOrNull() }.getOrNull()
        val durSec = (info?.music?.duration?.div(1000))?.toInt()?.coerceAtLeast(0) ?: 0
        val playCount = runCatching { info?.userInfo?.playCount ?: 0 }.getOrDefault(0)
        val title = info?.music?.title ?: "遗忘曲目"
        val artist = info?.music?.artist
        val lyricSummary = runCatching { fetchLyricSummary(id) }.getOrNull()
        val emotionText = generateForgottenEmotionText(title, artist, daysSince, playCount, lyricSummary)
        return SlideCard(
            SlideCard.newId(),
            SlideType.FORGOTTEN,
            ForgottenContent(
                trackId = id,
                trackTitle = info?.music?.title ?: "遗忘曲目",
                daysSince = daysSince,
                playCount = playCount,
                emotionText = emotionText,
                durationSec = durSec,
            ),
        )
    }

    /** ANNIVERSARY：4 种子类型候选并行收集 + 加权选最高。
     *  修复：循环内比较 score 保留最高（原代码每次覆盖）；
     *  PLAY_MILESTONE 用 ±10 容差匹配最近的里程碑（原精确匹配会跳过一天内多次播放）；
     *  DURATION_MILESTONE 改为"已跨过的最高门槛"（原 ±1h 窗口语义错误，跨过即不可再命中）。 */
    private suspend fun checkAnniversary(): SlideCard? {
        val repo = musicRepository ?: return null
        val today = todayString()
        val now = currentTimeMillis()

        // ── 公共数据：一次查询，多处复用 ──
        val durationsByMusic = runCatching {
            repo.getAllMusicDurations().associateBy { it.musicId }
        }.getOrDefault(emptyMap())

        HmpLog.i(LogTag.AgentHello) { "👋 ANNIVERSARY check start today=$today durations=${durationsByMusic.size}" }

        // ── 收集 4 种候选 ──
        val candidates = mutableListOf<AnniversaryCandidate>()

        // A. FIRST_PLAY —— N 年前的今天首次播放过的歌
        runCatching { repo.getAnniversaryTracks(today).firstOrNull() }
            .getOrNull()?.let { (id, firstPlayedAt, thatDayPlays) ->
                val yearsAgo = ((now - firstPlayedAt) / (365.25 * 86_400_000L)).toInt()
                if (yearsAgo <= 0) return@let  // 数据异常 guard：0 或负年数无意义
                val score = 100 * yearsAgo
                HmpLog.i(LogTag.AgentHello) { "👋 ANNIVERSARY A: FIRST_PLAY id=$id years=$yearsAgo score=$score" }
                candidates += AnniversaryCandidate(
                    score = score,
                    buildContent = { buildFirstPlayContent(repo, id, firstPlayedAt, thatDayPlays, yearsAgo) },
                )
            } ?: HmpLog.d(LogTag.AgentHello) { "👋 ANNIVERSARY A: no FIRST_PLAY candidate today" }

        // D. PLAYLIST_CREATE —— N 年前的今天创建的歌单
        runCatching { repo.getAnniversaryPlaylists(today).firstOrNull() }
            .getOrNull()?.let { row ->
                val yearsAgo = ((now - row.createdAt) / (365.25 * 86_400_000L)).toInt()
                if (yearsAgo <= 0) return@let  // 同上 guard
                val score = 70 * yearsAgo
                candidates += AnniversaryCandidate(
                    score = score,
                    buildContent = { buildPlaylistCreateContent(row, yearsAgo) },
                )
            }

        // B. PLAY_MILESTONE —— 今天播放过的歌，累计播放数跨过某个里程碑
        runCatching {
            val playedToday = repo.getMusicIdsPlayedOn(today)
            val milestones = intArrayOf(100, 200, 500, 1000, 2000)
            var bestMilestone: AnniversaryCandidate? = null
            for (musicId in playedToday) {
                val totalPlays = durationsByMusic[musicId]?.playCount ?: continue
                // 找最近的里程碑（±10 容差覆盖"一天内多次播放跳过里程碑"场景）
                val nearest = milestones
                    .mapIndexed { i, m -> Triple(m, i, kotlin.math.abs(m - totalPlays)) }
                    .minByOrNull { it.third } ?: continue
                if (nearest.third > 10) continue  // 距离太远，不算"跨过"
                val info = repo.getMusicInfoByIds(listOf(musicId)).firstOrNull() ?: continue
                val idx = nearest.second
                val score = 80 * idx
                val totalMs = durationsByMusic[musicId]?.totalMs ?: 0L
                // ⚠️ P0 Bug 1 修复：比较 score 保留最高，而非每次覆盖
                if (bestMilestone == null || score > bestMilestone!!.score) {
                    bestMilestone = AnniversaryCandidate(
                        score = score,
                        buildContent = { buildPlayMilestoneContent(musicId, info.music.title, nearest.first, totalPlays, totalMs) },
                    )
                }
            }
            bestMilestone
        }.getOrNull()?.let { candidates += it }

        // C. DURATION_MILESTONE —— 所有歌里，已跨过最高时长门槛的那首
        runCatching {
            val thresholds = longArrayOf(10 * 3_600_000L, 50 * 3_600_000L, 100 * 3_600_000L, 200 * 3_600_000L)
            var bestDur: AnniversaryCandidate? = null
            for (row in durationsByMusic.values) {
                // ⚠️ P0 Bug 3 修复：找已跨过的最高门槛（lastOrNull { totalMs >= it }），
                //   而非"±1h 窗口"（一旦跨过门槛就永久排除）
                val crossed = thresholds.lastOrNull { row.totalMs >= it } ?: continue
                val idx = thresholds.indexOf(crossed)
                val score = 60 * idx
                if (score <= 0) continue  // 10h 门槛 score=0 太弱，跳过等更高门槛
                val info = repo.getMusicInfoByIds(listOf(row.musicId)).firstOrNull() ?: continue
                // ⚠️ P0 Bug 1 修复：比较 score 保留最高
                if (bestDur == null || score > bestDur!!.score) {
                    bestDur = AnniversaryCandidate(
                        score = score,
                        buildContent = { buildDurationMilestoneContent(row.musicId, info.music.title, crossed / 3_600_000L, row.playCount, row.totalMs) },
                    )
                }
            }
            bestDur
        }.getOrNull()?.let { candidates += it }

        HmpLog.i(LogTag.AgentHello) { "👋 ANNIVERSARY candidates count=${candidates.size} scores=${candidates.map { it.score }}" }

        if (candidates.isEmpty()) return null
        val best = candidates.maxByOrNull { it.score } ?: return null
        val content = best.buildContent()
        return SlideCard(SlideCard.newId(), SlideType.ANNIVERSARY, content)
    }

    /** ANNIVERSARY 候选中间类——延迟构建 Content（先算 score 再选最优）。 */
    private data class AnniversaryCandidate(
        val score: Int,
        val buildContent: suspend () -> AnniversaryContent,
    )

    // ── 4 种子类型构建函数 ──

    private suspend fun buildFirstPlayContent(
        repo: com.hmp.domain.music.MusicRepository,
        id: Long, firstPlayedAt: Long, thatDayPlays: Int, yearsAgo: Int,
    ): AnniversaryContent {
        val info = runCatching { repo.getMusicInfoByIds(listOf(id)).firstOrNull() }.getOrNull()
        val durSec = (info?.music?.duration?.div(1000))?.toInt()?.coerceAtLeast(0) ?: 0
        val totalPlays = info?.userInfo?.playCount ?: 0
        val emotionText = generateAnniversaryEmotionText(AnniversarySubtype.FIRST_PLAY, yearsAgo, totalPlays)
        val totalMs = runCatching { repo.getAllMusicDurations().find { it.musicId == id }?.totalMs ?: 0L }.getOrDefault(0L)
        return AnniversaryContent(
            subtype = AnniversarySubtype.FIRST_PLAY,
            trackId = id,
            trackTitle = info?.music?.title ?: "纪念日曲目",
            yearsAgo = yearsAgo,
            totalPlays = totalPlays,
            totalListenHours = (totalMs / 3_600_000f).roundToDecimal(1),
            specificDate = formatDate(firstPlayedAt, yearsAgo),
            thatDayPlays = thatDayPlays,
            emotionText = emotionText,
            durationSec = durSec,
        )
    }

    private suspend fun buildPlaylistCreateContent(
        row: com.hmp.domain.music.PlaylistAnniversaryRow, yearsAgo: Int,
    ): AnniversaryContent {
        val emotionText = generateAnniversaryEmotionText(AnniversarySubtype.PLAYLIST_CREATE, yearsAgo, row.playbackCount)
        return AnniversaryContent(
            subtype = AnniversarySubtype.PLAYLIST_CREATE,
            trackId = row.playlistId,
            trackTitle = row.playlistName,
            yearsAgo = yearsAgo,
            totalPlays = row.playbackCount,
            specificDate = formatDate(row.createdAt, yearsAgo),
            emotionText = emotionText,
            durationSec = 0,
        )
    }

    private suspend fun buildPlayMilestoneContent(
        musicId: Long, title: String, milestoneValue: Int, totalPlays: Int, totalMs: Long,
    ): AnniversaryContent {
        val emotionText = generateAnniversaryEmotionText(AnniversarySubtype.PLAY_MILESTONE, milestoneValue, totalPlays)
        return AnniversaryContent(
            subtype = AnniversarySubtype.PLAY_MILESTONE,
            trackId = musicId,
            trackTitle = title,
            milestoneValue = milestoneValue,
            totalPlays = totalPlays,
            totalListenHours = (totalMs / 3_600_000f).roundToDecimal(1),
            emotionText = emotionText,
            durationSec = 0,
        )
    }

    private suspend fun buildDurationMilestoneContent(
        musicId: Long, title: String, milestoneHrs: Long, totalPlays: Int, totalMs: Long,
    ): AnniversaryContent {
        val emotionText = generateAnniversaryEmotionText(AnniversarySubtype.DURATION_MILESTONE, milestoneHrs.toInt(), totalPlays)
        return AnniversaryContent(
            subtype = AnniversarySubtype.DURATION_MILESTONE,
            trackId = musicId,
            trackTitle = title,
            milestoneValue = milestoneHrs.toInt(),
            totalPlays = totalPlays,
            totalListenHours = (totalMs / 3_600_000f).roundToDecimal(1),
            emotionText = emotionText,
            durationSec = 0,
        )
    }

    private fun Float.roundToDecimal(decimals: Int): Float {
        var multiplier = 1f
        repeat(decimals) { multiplier *= 10f }
        return (this * multiplier).toInt() / multiplier
    }

    // ═══ 报告叙事段（H4） ═══

    /** 自适应报告叙事生成——根据日均听歌时长判断频率 + F9-T1 升级：多维度差异化 + 画像侧写引用 */
    suspend fun regenerateReportNarrative(timeRange: NarrativeTimeRange): HelloReportNarrativeEntity? {
        val repo = musicRepository ?: return null
        val dao = narrativeDao ?: return null

        // ① 按时间维度取对应的天数窗口
        val windowDays = rangeToDays(timeRange)
        val avgMinutes = runCatching { repo.getAvgDailyListeningMinutes(windowDays) }.getOrDefault(0f)
        val frequency = adaptiveFrequency(avgMinutes)
        val range = timeRange.name

        // 频率守卫：DAO 有未过期缓存就直接返回，不重复生成
        val existing = runCatching { dao.getLatest(range) }.getOrNull()
        if (existing != null && !isNarrativeExpired(existing, frequency)) {
            HmpLog.d(LogTag.AgentHello) { "👋 report[$range]: fresh cache (avgDaily=$avgMinutes, freq=$frequency), skip" }
            return existing
        }

        // ② 扩展取数：完整统计 + 画像侧写片段
        val analytics = runCatching { repo.getUserUsageAnalytics() }.getOrNull()
        val factsSnapshot = runCatching { userMemory?.factsRenderForNarrative() }.getOrNull()

        // ③ 生成叙事（先纯统计模板，画像侧写可选叠加，enableLlm 时 LLM 润色）
        val narrative = buildStatisticsNarrative(timeRange, avgMinutes, analytics, factsSnapshot)
        val entity = HelloReportNarrativeEntity(
            timeRange = range,
            narrative = narrative,
            generatedAt = currentTimeMillis(),
            avgDailyMinutes = avgMinutes,
        )
        runCatching { dao.insert(entity) }.onFailure { e ->
            HmpLog.w(LogTag.AgentHello, e) { "👋 report[$range] DAO insert failed (non-fatal)" }
        }
        HmpLog.i(LogTag.AgentHello) { "👋 report[$range] regenerated (avgDaily=${avgMinutes}min, freq=$frequency)" }
        return entity
    }

    /** NarrativeTimeRange → days 窗口（给 getAvgDailyListeningMinutes / 后续时段分布查询用） */
    private fun rangeToDays(range: NarrativeTimeRange): Int = when (range) {
        NarrativeTimeRange.DAY -> 1
        NarrativeTimeRange.WEEK -> 7
        NarrativeTimeRange.MONTH -> 30
        NarrativeTimeRange.YEAR -> 365
        NarrativeTimeRange.ALL -> 3650  // 10 年兜底（"全部"近似长期平均）
    }

    /** 确保所有时间维度的报告叙事段都是最新的（dailyRefreshLoop 末尾调） */
    private suspend fun ensureReportNarrativeUpToDate() {
        val ranges = listOf(
            NarrativeTimeRange.ALL,
            NarrativeTimeRange.DAY,
            NarrativeTimeRange.WEEK,
            NarrativeTimeRange.MONTH,
            NarrativeTimeRange.YEAR,
        )
        for (range in ranges) {
            runCatching { regenerateReportNarrative(range) }
                .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 report[$range] ensure failed (non-fatal)" } }
        }
    }

    /** 自适应频率：日均时长 → 更新频率（小时数） */
    private fun adaptiveFrequency(avgDailyMinutes: Float): Long = when {
        avgDailyMinutes <= 30f -> 24 * 7   // ≤30min → 周更新
        avgDailyMinutes <= 120f -> 24      // 30~120min → 日更新
        else -> 24                          // ≥120min → 日更新（高活跃也日更）
    }

    private fun isNarrativeExpired(entity: HelloReportNarrativeEntity, frequencyHours: Long): Boolean {
        val ageMs = currentTimeMillis() - entity.generatedAt
        return ageMs > frequencyHours * 3_600_000L
    }

    /**
     * F9-T1 报告叙事生成——统计解读 + 可选画像侧写引用（纯模板，无 LLM）。
     *
     * 边界：报告叙事 = 对"某段时间数据"的解读。画像侧写是**可选引用**（如"你偏好深夜"），
     * 不生成画像散文本身（那是 UserMemory.narrativeState 的职责）。
     * enableLlm=true 时可接 callHelloLlm 润色（H4 阶段补齐，当前模板兜底已可用）。
     */
    private suspend fun buildStatisticsNarrative(
        timeRange: NarrativeTimeRange,
        avgMinutes: Float,
        analytics: com.hmp.domain.setting.model.UserUsageAnalytics?,
        factsSnapshot: String?,
    ): String {
        val rangeLabel = timeRange.zhName()
        val sb = StringBuilder()

        // ① 时间维度差异化开场
        val (durationText, frequencyDesc) = timeRangeToLabel(timeRange, avgMinutes)
        sb.append("${rangeLabel}${durationText}")
        if (frequencyDesc != null) sb.append(frequencyDesc)
        sb.append("。")

        // ② 口味侧（仅 MONTH+：DAY/WEEK 窗口太短，全量 analytics 无法代表近期偏好）
        if (timeRange != NarrativeTimeRange.DAY && timeRange != NarrativeTimeRange.WEEK) {
            analytics?.topGenres?.takeIf { it.isNotEmpty() }?.firstOrNull()?.let { topGenre ->
                sb.append("主打")
                sb.append(topGenre.labelDisplayName)
                sb.append("。")
            }
        }

        // ③ 情绪侧（同上）
        if (timeRange != NarrativeTimeRange.DAY && timeRange != NarrativeTimeRange.WEEK) {
            analytics?.topMoods?.takeIf { it.isNotEmpty() }?.firstOrNull()?.let { topMood ->
                sb.append("情绪基调是")
                sb.append(topMood.labelDisplayName)
                sb.append("。")
            }
        }

        // ④ 行为侧（完播率 / 跳过率描述——完播率相对稳定，DAY 也可看）
        if (analytics != null && analytics.completionRate > 0f) {
            val completionDesc = when {
                analytics.completionRate >= 0.8f -> "听完率很高"
                analytics.completionRate >= 0.5f -> "听完率一般"
                else -> "经常切歌"
            }
            sb.append(completionDesc)
            sb.append("。")
        }

        // ⑤ 画像侧写引用（可选，截断到 200 字避免过长）
        factsSnapshot?.takeIf { it.isNotBlank() }?.let { facts ->
            val truncated = facts.take(200)
            sb.append("${truncated}")
            if (facts.length > 200) sb.append("…")
        }

        // enableLlm=true 时接 LLM 润色（H4 补齐，当前模板兜底已可用）
        return sb.toString()
    }

    /** 时间维度 → "听了 N 小时" + 频度描述 */
    private fun timeRangeToLabel(range: NarrativeTimeRange, avgMinutes: Float): Pair<String, String?> {
        val totalMinutes = when (range) {
            NarrativeTimeRange.DAY -> avgMinutes
            NarrativeTimeRange.WEEK -> avgMinutes * 7
            NarrativeTimeRange.MONTH -> avgMinutes * 30
            NarrativeTimeRange.YEAR -> avgMinutes * 365
            NarrativeTimeRange.ALL -> avgMinutes * 3650
        }
        val duration = formatTotalMinutes(totalMinutes.toLong())
        val freqDesc = when (range) {
            NarrativeTimeRange.DAY -> null  // "今日" + "听了 X 小时" 够了，不追加
            NarrativeTimeRange.WEEK -> if (avgMinutes > 0f) "日均 ${avgMinutes.toInt()} 分钟" else null
            NarrativeTimeRange.MONTH -> if (avgMinutes > 0f) "日均 ${avgMinutes.toInt()} 分钟" else null
            NarrativeTimeRange.YEAR -> if (avgMinutes > 0f) "日均 ${avgMinutes.toInt()} 分钟" else null
            NarrativeTimeRange.ALL -> if (avgMinutes > 0f) "日均 ${avgMinutes.toInt()} 分钟" else null
        }
        return duration to freqDesc
    }

    /** 总分钟数 → "听了 X 小时" / "听了 N 小时 M 分钟" */
    private fun formatTotalMinutes(totalMinutes: Long): String {
        val totalMinutes = totalMinutes.coerceAtLeast(0)
        return when {
            totalMinutes == 0L -> "几乎没听歌"
            totalMinutes < 60L -> "听了 ${totalMinutes} 分钟"
            totalMinutes < 600L -> "听了 ${totalMinutes / 60} 小时"
            else -> "听了 ${totalMinutes / 60} 小时"
        }
    }

    // ═══ 对外查询接口（MasterAgent / P5 报告页） ═══

    /** 取指定时间维度最新的报告叙事段（DAO 读，零阻塞） */
    suspend fun getReportNarrative(timeRange: NarrativeTimeRange): HelloReportNarrativeEntity? {
        val dao = narrativeDao ?: return null
        return runCatching { dao.getLatest(timeRange.name) }.getOrNull()
    }

    /**
     * 热更新 AI 配置——由 MasterAgent.updateAiConfig 推送。
     * enableLlm 字段立即生效：
     * - enableLlm=false → 后续所有卡生成自动走兜底模板
     * - enableLlm=true  → 后续生成自动进 LLM 分支
     *
     * 注意：已生成的卡不会自动重生成——下一个触发点（DjBlank/时段变化/跨天）会用新配置。
     */
    fun updateAiConfig(enableLlm: Boolean, enrichConfig: AiEndpointConfig?) {
        this.enableLlm = enableLlm
        this.enrichConfig = enrichConfig
        HmpLog.i(LogTag.AgentHello) {
            "🔄 updateAiConfig | enableLlm=$enableLlm | hasLLM=${enrichConfig != null} | " +
            "endpoint=${enrichConfig?.endpoint?.take(40) ?: "(none)"} | " +
            "model=${enrichConfig?.selectedModel?.take(30) ?: "(default)"} | " +
            "hasKey=${enrichConfig?.apiKey?.isNotBlank() == true}"
        }
    }

    // ═══ 工具方法 ═══

    /** 提取歌词文本：解析 LRC 去时间戳 → 去空行/重复。拿不到歌词或解析失败 → 返回 null。 */
    private suspend fun fetchLyricSummary(trackId: Long): String? {
        val repo = musicRepository ?: return null
        val raw = runCatching { repo.getMusicLyrics(trackId) }.getOrNull() ?: return null
        if (raw.isBlank()) return null
        val lines = runCatching { LrcParser.parse(raw) }.getOrNull()
            ?.map { it.originalText.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: return null
        if (lines.isEmpty()) return null
        return lines.joinToString("\n")
    }

    private fun labelForPhase(phase: TimePhase): LabelName? = when (phase) {
        TimePhase.NIGHT -> LabelName.SLEEP
        TimePhase.MORNING_COMMUTE -> LabelName.MORNING
        TimePhase.WORK -> LabelName.FOCUS
        TimePhase.LUNCH -> LabelName.RELAX
        TimePhase.EVENING_COMMUTE -> LabelName.DRIVING
        TimePhase.EVENING_LEISURE -> LabelName.CALM
        TimePhase.UNKNOWN -> null
    }

    /** TimePhase → 中文描述（给 LLM prompt 用） */
    private fun TimePhase.zhName(): String = when (this) {
        TimePhase.NIGHT -> "深夜"
        TimePhase.MORNING_COMMUTE -> "早高峰"
        TimePhase.WORK -> "工作时段"
        TimePhase.LUNCH -> "午休"
        TimePhase.EVENING_COMMUTE -> "晚高峰"
        TimePhase.EVENING_LEISURE -> "晚间休闲"
        TimePhase.UNKNOWN -> "随机时段"
    }

    /** RECOMMEND 推荐理由——结合歌曲信息 + 时段 + 歌词摘要，LLM 优先，兜底模板 */
    suspend fun reasonForTrack(
        label: LabelName,
        phase: TimePhase,
        trackTitle: String,
        artist: String?,
        playCount: Int,
        lyricSummary: String?,
    ): String {
        val phaseDesc = phase.zhName()
        val artistPart = if (!artist.isNullOrBlank()) "艺术家：$artist" else ""
        val historyPart = when {
            playCount <= 0 -> "用户从未播放过这首歌"
            playCount < 5 -> "用户仅播放过 $playCount 次"
            playCount < 20 -> "用户播放过 $playCount 次"
            else -> "用户曾循环播放 $playCount 次，是用户的心头好"
        }
        val prompt = buildString {
            appendLine("请为以下歌曲写一段完整的中文推荐语。")
            appendLine("")
            appendLine("歌曲信息：")
            appendLine("  标题：$trackTitle")
            if (artistPart.isNotBlank()) appendLine("  $artistPart")
            appendLine("  适合时段：$phaseDesc（${label.name}）")
            appendLine("  $historyPart")
            if (!lyricSummary.isNullOrBlank()) {
                appendLine("")
                appendLine("完整歌词：")
                lyricSummary.lines().forEach { appendLine("  $it") }
            }
            appendLine("")
            appendLine("写作要求：")
            appendLine("  · 篇幅约 300-500 字，完整段落，不要分行列点")
            appendLine("  · 从歌词中提取核心意象、情感基调或标志性语句作为切入点")
            appendLine("  · 结合当前时段场景（$phaseDesc），描述这首歌在此时此地能给听者带来什么")
            appendLine("  · 适当提及播放历史（$historyPart），让推荐有温度")
            appendLine("  · 语气像一个真正听过这首歌、懂你的朋友在认真推荐")
            appendLine("  · 开头可以用一句歌词或一个画面抓眼球，中间展开感受，结尾落在当下时段的收听建议上")
            appendLine("  · 直接输出正文，不要标题、引号、前缀或解释")
            appendLine("  · 如果记忆上下文提到某歌手或 label 近期已被其他卡覆盖，避免在推荐语中重复提及")
        }
        val llmReason = callHelloLlm(
            cardType = "RECOMMEND",
            systemPrompt = resolveIfConfigured("hello.recommend.full", "你是一个资深音乐评论人兼知心朋友，擅长结合歌词、场景和听众历史，写出有温度有画面感的中文推荐文字。"),
            userPrompt = prompt,
            temperature = resolveTemperature(0.6f),
        )
        if (!llmReason.isNullOrBlank()) return llmReason
        // 兜底（LLM 不可用 / 调用失败）—— 仍然结合歌曲名
        val artistSuffix = artist?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        return when (label) {
            LabelName.SLEEP -> "${trackTitle}$artistSuffix，深夜听这首很合适"
            LabelName.MORNING -> "${trackTitle}$artistSuffix，早晨提神来一首"
            LabelName.FOCUS -> "${trackTitle}$artistSuffix，工作时专注背景音"
            LabelName.RELAX -> "${trackTitle}$artistSuffix，放松一下吧"
            LabelName.DRIVING -> "${trackTitle}$artistSuffix，开车路上的陪伴"
            LabelName.CALM -> "${trackTitle}$artistSuffix，静静心"
            else -> "${trackTitle}$artistSuffix，这个时段听听不错"
        }
    }

    /**
     * 生成 RECOMMEND 卡并双写（CardPool + DAO）。
     * 公共入口：phase-change 刷新 / 缺失重试都走这里。
     * @return true=生成了新卡，false=没生成（时段 label 无匹配曲目）
     */
    private suspend fun refreshRecommendCard(phase: TimePhase): Boolean {
        val card = generateRecommendCards(phase = phase, count = dailyRecommendCount).firstOrNull()
            ?: return false
        cardPool.replace(card.type, card)
        runCatching {
            val now = currentTimeMillis()
            val today = todayString()
            cardCacheDao?.deleteSameDaySameType(SlideType.RECOMMEND.name, today)
            val cache = buildHelloCardCache(card, now, today, llmUsed = enableLlm)
            cardCacheDao?.insert(cache)
            memory.record(cache)
        }.onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 refreshRecommendCard: dao write failed" } }
        HmpLog.i(LogTag.AgentHello) { "👋 refreshRecommendCard: OK phase=$phase" }
        return true
    }

    // ═══════════════════════════════════════════════════════════════════
    // G6：每日推荐 / 私人推荐列表（种子 + "类似 radio"扩列，一次性产出）
    // ═══════════════════════════════════════════════════════════════════

    /** 生成 + 持久化 + 发射一个推荐列表。 */
    private suspend fun refreshRecommendList(source: RecommendSource) {
        val repo = musicRepository ?: return
        val built = runCatching { buildRecommendList(source, repo) }
            .onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 buildRecommendList failed source=$source" } }
            .getOrNull()

        if (built == null) {
            // 明确置空 payload：UI 据此区分「无数据」与「生成中（null）」
            emitRecommendList(
                RecommendListPayload(
                    source = source,
                    overview = "",
                    phase = null,
                    generatedAt = currentTimeMillis(),
                    generatedForDate = todayString(),
                    items = emptyList(),
                )
            )
            return
        }

        val (payload, cache) = built
        runCatching {
            cardCacheDao?.deleteSameDaySameType(payload.source.cardType(), payload.generatedForDate)
            cardCacheDao?.insert(cache)
            memory.record(cache)  // 并入 recommend* 桶（跨列表 / 跨卡去重）
        }.onFailure { e -> HmpLog.w(LogTag.AgentHello, e) { "👋 recommend list DAO write failed" } }
        emitRecommendList(payload)
        HmpLog.i(LogTag.AgentHello) { "👋 refreshRecommendList: source=$source items=${payload.items.size}" }
    }

    private fun emitRecommendList(payload: RecommendListPayload) {
        when (payload.source) {
            RecommendSource.DAILY -> _dailyRecommendList.value = payload
            RecommendSource.PRIVATE -> _privateRecommendList.value = payload
        }
    }

    /** 构建一个推荐列表：选种子 → 扩列 → 每首按语 → 总述 → 持久化实体 */
    private suspend fun buildRecommendList(
        source: RecommendSource,
        repo: com.hmp.domain.music.MusicRepository,
    ): Pair<RecommendListPayload, HelloCardCache>? {
        val phase = detectTimePhase(currentHour())
        val phaseLabel = labelForPhase(phase)

        // ① 选 1 首种子
        val seedId: Long = when (source) {
            // 每日：与滑动卡 RECOMMEND 同源（同 label 取第一首）
            RecommendSource.DAILY ->
                phaseLabel?.let { runCatching { repo.getMusicIdListByType(it, limit = 1).firstOrNull() }.getOrNull() }
            // 私人：收藏优先，其次近期高频播放
            RecommendSource.PRIVATE -> {
                val liked = runCatching { repo.getLikedMusicIds() }.getOrDefault(emptyList())
                liked.firstOrNull()
                    ?: runCatching { repo.getRecentPlayRate(limit = 5, days = 30).firstOrNull() }.getOrNull()
            }
        } ?: return null

        // ② 记忆排除集：昨日已推（跨天去重；记忆里是 String，转 Long 比较）
        val excluded: Set<Long> = runCatching { memory.getYesterdayRecommendSongIds() }
            .getOrDefault(emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        // ③ 扩列（种子本身由 getSimilarSongsByWeightedLabels 内部排除）
        val expanded = runCatching { repo.getSimilarSongsByWeightedLabels(seedId, limit = recommendListSize * 2) }
            .getOrDefault(emptyList())

        val ordered = LinkedHashSet<Long>()
        ordered += seedId  // 种子恒为首项
        expanded.forEach { if (it.music.id !in excluded) ordered += it.music.id }

        // 兜底补齐：扩列不足时用当前时段 label 列表填充
        if (ordered.size < recommendListSize && phaseLabel != null) {
            runCatching { repo.getMusicIdListByType(phaseLabel, limit = recommendListSize * 2) }
                .getOrDefault(emptyList())
                .forEach { if (ordered.size < recommendListSize && it !in excluded) ordered += it }
        }

        val finalIds = ordered.take(recommendListSize).toList()
        if (finalIds.isEmpty()) return null

        // ④ hydrate + 每首按语
        val infos = runCatching { repo.getMusicInfoByIds(finalIds) }
            .getOrDefault(emptyList())
            .associateBy { it.music.id }
        val reasonLabel = phaseLabel ?: LabelName.CALM
        val items = ArrayList<RecommendItemRecord>(finalIds.size)
        val artists = ArrayList<String>(finalIds.size)
        for (id in finalIds) {
            val info = infos[id] ?: continue
            val playCount = info.userInfo?.playCount ?: 0
            val lyric = runCatching { fetchLyricSummary(id) }.getOrNull()
            val reason = reasonForTrack(
                label = reasonLabel,
                phase = phase,
                trackTitle = info.music.title,
                artist = info.music.artist,
                playCount = playCount,
                lyricSummary = lyric,
            )
            items += RecommendItemRecord(trackId = id, reason = reason)
            info.music.artist?.takeIf { it.isNotBlank() }?.let { artists += it }
        }
        if (items.isEmpty()) return null

        // ⑤ 总述
        val overview = buildListOverview(source, phase, items.size)

        val now = currentTimeMillis()
        val payload = RecommendListPayload(
            source = source,
            overview = overview,
            phase = phase,
            generatedAt = now,
            generatedForDate = todayString(),
            items = items,
        )
        val cache = HelloCardCache(
            cardType = source.cardType(),
            cardContentJson = runCatching {
                json.encodeToString(RecommendListPayload.serializer(), payload)
            }.getOrElse { "" },
            generatedAt = now,
            generatedForDate = payload.generatedForDate,
            llmUsed = enableLlm,
            recommendSongIds = items.map { it.trackId.toString() },
            recommendArtists = artists.distinct(),
        )
        return payload to cache
    }

    /** 顶部总述：LLM 优先，模板兜底 */
    private suspend fun buildListOverview(source: RecommendSource, phase: TimePhase, count: Int): String {
        val phaseDesc = phase.zhName()
        val sourceDesc = when (source) {
            RecommendSource.DAILY -> "今天的每日推荐"
            RecommendSource.PRIVATE -> "根据你的收藏与收听做的私人推荐"
        }
        val llm = callHelloLlm(
            cardType = null,
            systemPrompt = resolveIfConfigured("hello.recommend.short", "你是一个懂音乐的朋友，用温暖简洁的中文写一句推荐开场白。"),
            userPrompt = "用一句话（≤40字）为「$sourceDesc」写个开场总述，当前时段：$phaseDesc，共 $count 首。直接返回中文句子。",
            temperature = resolveTemperature(0.7f),
        )
        if (!llm.isNullOrBlank()) return llm
        return when (source) {
            RecommendSource.DAILY -> "$phaseDesc，为你精选 $count 首"
            RecommendSource.PRIVATE -> "依你的收藏与收听，挑了 $count 首"
        }
    }

    /** 启动时从 DAO 恢复今日推荐列表（跨重启不重算） */
    private suspend fun restoreRecommendLists() {
        val dao = cardCacheDao ?: return
        val today = todayString()
        for (source in RecommendSource.entries) {
            val cache = runCatching { dao.getLatest(source.cardType(), today) }.getOrNull() ?: continue
            val payload = runCatching {
                json.decodeFromString(RecommendListPayload.serializer(), cache.cardContentJson)
            }.getOrNull() ?: continue
            emitRecommendList(payload)
        }
    }

    /** DISCOVER 发现理由——支持 LLM 个性化，兜底固定文案 */
    private suspend fun reasonForDiscover(label: LabelName): String {
        val llmReason = callHelloLlm(
            cardType = "DISCOVER",
            systemPrompt = resolveIfConfigured("hello.recommend.list", "你是一个懂音乐的朋友，负责用温暖简洁的中文推荐音乐。"),
            userPrompt = "用一句话（≤20字）推荐用户重新发现「${label.name}」风格的音乐，语气温暖。直接返回中文句子。",
            temperature = resolveTemperature(0.6f),
        )
        if (!llmReason.isNullOrBlank()) return llmReason
        // 兜底
        return when (label) {
            LabelName.POP -> "你好像很久没听 POP 了，来回顾一下"
            LabelName.ROCK -> "摇滚的能量，今天来点不一样的"
            LabelName.CALM -> "静静心，来几首轻的"
            LabelName.SLEEP -> "睡前放松一下"
            LabelName.MORNING -> "早晨也可以来点不一样的"
            else -> "你好像很久没听 ${label.name} 了"
        }
    }

    /** FORGOTTEN 情感文案——结合歌曲信息 + 遗忘时长 + 歌词，LLM 优先，兜底模板 */
    private suspend fun generateForgottenEmotionText(
        trackTitle: String,
        artist: String?,
        daysSince: Int,
        playCount: Int,
        lyricSummary: String?,
    ): String {
        val artistPart = if (!artist.isNullOrBlank()) "艺术家：$artist" else ""
        val historyPart = when {
            playCount <= 0 -> "用户从未播放过这首歌"
            playCount < 5 -> "用户仅播放过 $playCount 次"
            playCount < 20 -> "用户曾播放过 $playCount 次"
            else -> "用户曾循环播放 $playCount 次，是当时的心头好"
        }
        val timePart = when {
            daysSince >= 365 -> "超过一年没听了"
            daysSince >= 180 -> "半年多没听了"
            daysSince >= 90 -> "三个月没听了"
            else -> "${daysSince} 天没听了"
        }
        val prompt = buildString {
            appendLine("请生成一段中文怀旧文案，唤醒用户对一首老歌的记忆。")
            appendLine("")
            appendLine("歌曲信息：")
            appendLine("  标题：$trackTitle")
            if (artistPart.isNotBlank()) appendLine("  $artistPart")
            appendLine("  遗忘时长：$timePart")
            appendLine("  播放历史：$historyPart")
            if (!lyricSummary.isNullOrBlank()) {
                appendLine("")
                appendLine("完整歌词：")
                lyricSummary.lines().forEach { appendLine("  $it") }
            }
            appendLine("")
            appendLine("写作要求：")
            appendLine("  · 篇幅约 300-500 字，完整段落，不要分行列点")
            appendLine("  · 从歌词中提取最触动的几句或最核心的意象，以此为引子")
            appendLine("  · 结合遗忘时长（$timePart）和播放历史（$historyPart），勾勒出这首歌在用户人生中的位置")
            appendLine("  · 不要直接说「XX 天没听了」，要让时间跨度自然地体现在情绪和语气里")
            appendLine("  · 语气像一个温柔的老朋友，轻声提醒一段被遗忘的时光")
            appendLine("  · 开头从一句歌词或一个画面切入，中间展开回忆的质感，结尾轻轻落在「再听一次」的邀请上")
            appendLine("  · 直接输出正文，不要标题、引号、前缀或解释")
        }
        val llmText = callHelloLlm(
            cardType = "FORGOTTEN",
            systemPrompt = resolveIfConfigured("hello.forgotten.essay", "你是一个擅长写怀旧随笔的音乐人，能从一句歌词、一段旋律、一个时间跨度里，写出让人心头一暖的中文文字。"),
            userPrompt = prompt,
            temperature = resolveTemperature(0.7f),
        )
        if (!llmText.isNullOrBlank()) return llmText
        // 兜底——仍然结合歌曲名
        val artistSuffix = artist?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        return when {
            playCount > 0 && daysSince >= 180 -> "那年你循环了 $playCount 遍的「${trackTitle}」，还记得吗？"
            playCount > 0 -> "「${trackTitle}」$artistSuffix，你曾经播放过 $playCount 次"
            daysSince >= 90 -> "「${trackTitle}」$artistSuffix，好久没想起它了"
            else -> "「${trackTitle}」$artistSuffix，再听一次？"
        }
    }

    /** ANNIVERSARY 情感化文案——按 subtype 分 4 种 prompt + 兜底模板。 */
    private suspend fun generateAnniversaryEmotionText(
        subtype: com.hmp.domain.agent.card.AnniversarySubtype,
        value: Int,  // 含义随 subtype 变：yearsAgo / milestoneValue
        total: Int,  // 累计播放
    ): String {
        val prompt = when (subtype) {
            com.hmp.domain.agent.card.AnniversarySubtype.FIRST_PLAY ->
                "用一句话（≤25字）庆祝「${value}年前的今天」第一次收藏一首歌。累计播放 ${total} 次。温暖带感动。"
            com.hmp.domain.agent.card.AnniversarySubtype.PLAYLIST_CREATE ->
                "用一句话（≤25字）庆祝「${value}年前的今天」创建了一个歌单。累计被播放 ${total} 次。温暖。"
            com.hmp.domain.agent.card.AnniversarySubtype.PLAY_MILESTONE ->
                "用一句话（≤25字）庆祝一首歌累计播放达到第 ${value} 次。当前共 ${total} 次。有成就感。"
            com.hmp.domain.agent.card.AnniversarySubtype.DURATION_MILESTONE ->
                "用一句话（≤25字）庆祝一首歌累计听了 ${value} 小时。共播放 ${total} 次。有成就感。"
        }
        val llmText = callHelloLlm(
            cardType = null,  // 里程碑文案，不参与跨卡协调
            systemPrompt = resolveIfConfigured("hello.forgotten.card", "你是一个懂音乐的朋友，擅长用温暖简洁的文字唤起听众的回忆。"),
            userPrompt = prompt,
            temperature = resolveTemperature(0.6f),
        )
        if (!llmText.isNullOrBlank()) return llmText
        // 兜底模板
        return when (subtype) {
            com.hmp.domain.agent.card.AnniversarySubtype.FIRST_PLAY -> "第一次听到这首，已是 $value 年前"
            com.hmp.domain.agent.card.AnniversarySubtype.PLAYLIST_CREATE -> "$value 年前的今天建的这个歌单"
            com.hmp.domain.agent.card.AnniversarySubtype.PLAY_MILESTONE -> "第 $value 次循环，你值得"
            com.hmp.domain.agent.card.AnniversarySubtype.DURATION_MILESTONE -> "累计 $value 小时，这首歌陪你很久了"
        }
    }

    private fun todayString(): String = todayDateString()

    // ═══ Hello 卡片生成统一 LLM 调用入口 ═══
    //
    // 封装样板：enableLlm 检查 + cfg 检查 + memory context 注入 + runCatching + trim + clearHistory
    // 6 处 callLlmText 调用统一收口在此。
    //
    // @param cardType 卡片类型名，用于 memory.buildContextForCard 做跨卡协调。
    //                 传 null 表示不需要记忆注入（ANNIVERSARY 的 JSON 查询有短路拦截兜底）。
    // @param postProcess 可选后处理：LLM 原始输出 → 最终文本。默认 trim + 去中英文引号。
    // @return LLM 最终文本，或 null（LLM 不可用 / 调用失败 / 空返回）

    private suspend fun callHelloLlm(
        cardType: String? = null,
        systemPrompt: String,
        userPrompt: String,
        temperature: Float = 0.6f,
        postProcess: ((String) -> String?) = { it.trim().trim('\u0022', '\u300C', '\u300D', '\u201C', '\u201D') },
    ): String? {
        if (!enableLlm) return null
        val cfg = enrichConfig ?: return null
        // 画像简报（v3.8）统一追加到 system —— 自带「仅供参考」口径，是背景不是指令
        val systemWithMemory = memoryBriefing?.takeIf { it.isNotBlank() }
            ?.let { "$systemPrompt\n\n$it" } ?: systemPrompt
        val result = runCatching {
            val finalPrompt = runCatching {
                if (cardType != null) {
                    val ctx = memory.buildContextForCard(cardType)
                    if (ctx.isNotBlank()) "$userPrompt\n\n$ctx" else userPrompt
                } else userPrompt
            }.getOrDefault(userPrompt)
            val raw = contextBudget.callLlmText(
                config = cfg,
                systemPrompt = systemWithMemory,
                newMessages = listOf(LlmMessage(role = "user", content = finalPrompt)),
                temperature = temperature,
            ) ?: return@runCatching null
            postProcess(raw)
        }
        contextBudget.clearHistory()  // Hello 各任务独立，清 history 防串味（无论成功失败）
        return result.getOrNull()
    }

    // ═══ SlideContent JSON 序列化/反序列化 ═══

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun cardContentToString(content: SlideContent): String {
        return runCatching { json.encodeToString(SlideContent.serializer(), content) }
            .getOrElse { "" }
    }

    private fun stringToCardContent(type: SlideType, jsonStr: String): SlideContent? {
        if (jsonStr.isBlank()) return null
        return runCatching {
            when (type) {
                SlideType.ANCHOR -> json.decodeFromString(AnchorContent.serializer(), jsonStr)
                SlideType.RADIO_STATUS -> json.decodeFromString(RadioStatusContent.serializer(), jsonStr)
                SlideType.GREETING -> json.decodeFromString(GreetingContent.serializer(), jsonStr)
                SlideType.RECOMMEND -> json.decodeFromString(RecommendContent.serializer(), jsonStr)
                SlideType.DISCOVER -> json.decodeFromString(DiscoverContent.serializer(), jsonStr)
                SlideType.FORGOTTEN -> json.decodeFromString(ForgottenContent.serializer(), jsonStr)
                SlideType.ANNIVERSARY -> json.decodeFromString(AnniversaryContent.serializer(), jsonStr)
                SlideType.ENRICH_TRACKING -> json.decodeFromString(EnrichTrackingContent.serializer(), jsonStr)
                // 叙事卡不进卡池 DAO（直接读 hello_report_narrative 表），此处不参与反序列化
                SlideType.NARRATIVE -> json.decodeFromString(NarrativeContent.serializer(), jsonStr)
            }
        }.getOrNull()
    }

    /** 从 SlideCard 提取 HelloCardCache，含记忆协调所需的扩展字段。
     *  suspend：需按 trackId 反查歌手名（G7 方案 A，musicRepository 为 null 时优雅降级为空）。 */
    private suspend fun buildHelloCardCache(
        card: SlideCard,
        now: Long,
        today: String,
        llmUsed: Boolean,
    ): HelloCardCache {
        val c = card.content
        // 按 trackId 反查歌手名（RECOMMEND/FORGOTTEN/ANNIVERSARY 用）；repo 为 null 或查不到时返回 null
        suspend fun artistOf(trackId: Long): String? =
            musicRepository?.getMusicInfoByIds(listOf(trackId))
                ?.firstOrNull()?.music?.artist
                ?.takeIf { it.isNotBlank() }

        val recommendTrackId = (c as? RecommendContent)?.trackId
        val forgottenTrackId = (c as? ForgottenContent)?.trackId
        // PLAYLIST_CREATE 子类型 trackId=0，不是单曲，不反查 artist
        val anniversaryTrackId = (c as? AnniversaryContent)?.trackId?.takeIf { it != 0L }

        return HelloCardCache(
            cardType = card.type.name,
            cardContentJson = cardContentToString(c),
            generatedAt = now,
            generatedForDate = today,
            llmUsed = llmUsed,
            // 按卡片类型提取记忆字段
            recommendSongIds = recommendTrackId?.toString()?.let { listOf(it) },
            recommendArtists = recommendTrackId?.let { artistOf(it) }?.let { listOf(it) },
            recommendLabels = (c as? RecommendContent)?.currentPhase?.zhName()?.let { listOf(it) },
            greetingType = (c as? GreetingContent)?.type?.name,
            // GreetingContent 不绑定单曲（金句/歌词/冷知识卡），无 artist 可反查；
            // 要填需改模型 + LLM 生成吐 artist（G7 范围外），保持 null
            greetingMentionedArtists = null,
            discoverLabels = (c as? DiscoverContent)?.target?.let { listOf(it) },
            forgottenArtists = forgottenTrackId?.let { artistOf(it) }?.let { listOf(it) },
            forgottenSongIds = forgottenTrackId?.toString()?.let { listOf(it) },
            anniversaryArtist = anniversaryTrackId?.let { artistOf(it) },
            // 预留（G14 死列）：全仓无读取，保持 null
            anniversarySubject = null,
        )
    }

    companion object {
        // 空 companion —— 旧 DEFAULT_FALLBACK_GREETINGS 已删除，
        // 6 类型独立 fallback 模板库已外移到 HelloGreetingProfiles.of(type).fallbacks
    }

    /** 毫秒时间戳 → "MM.dd · N 年前" 格式。mmdd 为空时退化为 "N 年前"。 */
    private fun formatDate(ms: Long, yearsAgo: Int): String {
        val mmdd = runCatching { com.hmp.data.util.formatMmddFromMillis(ms) }.getOrDefault("")
        return if (mmdd.isBlank()) "${yearsAgo} 年前" else "$mmdd · ${yearsAgo} 年前"
    }

    // ── Capability 接口实现（F9-A0） ──

    override val capabilityName = "hello"

    /** HelloSubAgent 自动启动/停止——用 SubAgent 基类 runState 映射 */
    override val stateFlow: StateFlow<CapabilityState> = kotlinx.coroutines.flow.MutableStateFlow(
        CapabilityState(
            status = when (state()) {
                AgentRunState.RUNNING -> CapabilityState.Status.RUNNING
                else -> CapabilityState.Status.IDLE
            },
            detail = if (state() == AgentRunState.RUNNING) "运行中" else "未启动",
        )
    )
}

