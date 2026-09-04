package com.hmp.domain.agent.sub

import co.touchlab.kermit.Logger
import com.hmp.data.database.HelloCardCache
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
import com.hmp.domain.agent.runtime.StopSignal
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.hmp.data.database.currentTimeMillis
import com.hmp.platform.Volatile

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
class HelloSubAgent(
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
) : SubAgent(agentId, contextBudget, toolRegistryView) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 卡片池（StateFlow 暴露给 UI collect） */
    private val cardPool = CardPool()

    /** UI collect 的 StateFlow */
    val cards: StateFlow<List<SlideCard>> get() = cardPool.cards

    /** 上次检测到的播放曲目 ID（用于切歌 → GREETING 刷新检测） */
    @Volatile private var lastTrackId: Long? = null

    /** 每日凌晨生成的推荐卡数量 */
    private val dailyRecommendCount = 1

    /** 当前时段（minuteTickLoop 维护） */
    @Volatile
    private var lastPhase: TimePhase? = null

    /** 门面问候轮换索引 */
    /** 门面问候轮换索引，@Volatile 避免 Dispatchers.Default 多线程 data race */
    @Volatile
    private var greetingIndex: Int = 0
    @Volatile
    private var agentPaused: Boolean = false

    // ═══ GREETING 限频与多样性 ═══
    /** 上次 GREETING 刷新时间戳 */
    @Volatile private var lastGreetingRefreshAt: Long = 0L
    /** 上次 GREETING 关联的歌曲 ID（切歌联动去重） */
    @Volatile private var lastGreetingSongId: Long? = null
    /** 最近 3 次 GREETING 类型（轮转惩罚） */
    private val recentGreetingTypes: ArrayDeque<GreetingType> = ArrayDeque()
    /** 最近 5 次 GREETING 内容前 30 字（LLM 去重提示） */
    private val recentGreetingContents: ArrayDeque<String> = ArrayDeque()
    /** 音乐纪念日缓存日期（同一天只查一次） */
    @Volatile private var lastAnnivCheckDate: String? = null
    /** 音乐纪念日缓存结果 */
    @Volatile private var cachedAnniversaryHint: String? = null
    /** GREETING 兜底定时上次检查时间 */
    @Volatile private var lastGreetingPeriodicCheck: Long = 0L

    /** Fix1: 内存补跑守卫（DAO=null 时用这个标志，避免无限循环 push/pop） */
    @Volatile
    private var todayCardsGenerated: Boolean = false

    // ═══ SubAgent.runLoop ═══

    override suspend fun runLoop() {
        Logger.i("Agent.Hello") { "runLoop start" }
        isActive = true
        runState = AgentRunState.RUNNING

        // ── 同步初始化（协程启动前先铺好初始状态，避免 race） ──
        // ① 从 DAO 恢复今日缓存卡（用 replace，保证顺序稳定）
        runCatching { initializeFromDao() }
            .onFailure { e -> Logger.w("Agent.Hello", e) { "initializeFromDao failed (non-fatal)" } }

        // ② 检查今日卡是否已生成（one-shot，同步跑完退出）
        runCatching { dailyRefreshLoop() }
            .onFailure { e -> Logger.e("Agent.Hello", e) { "dailyRefreshOnce failed" } }

        // ③ 立即 push 常驻卡 + 兜底 GREETING + 检查 Radio 状态
        runCatching { initializeAnchorCards() }
            .onFailure { e -> Logger.w("Agent.Hello", e) { "initializeAnchorCards failed (non-fatal)" } }

        // ── 协程启动（初始状态已铺好，PresenceEvent 不会 race） ──
        val presenceJob = scope.launch { collectPresenceEvents() }
        val tickJob = scope.launch { minuteTickLoop() }

        // runLoop 自身只负责暂停/恢复 + 优雅退出
        while (scope.isActive && isActive) {
            agentPaused = true
            stopSignal?.waitResume()
            agentPaused = false
            if (stopSignal?.shouldSoftStop() == true) break
            delay(500)
        }

        // 清理
        presenceJob.cancel()
        tickJob.cancel()
        cardPool.clear()
        runState = AgentRunState.PAUSED
        Logger.i("Agent.Hello") { "runLoop exited" }
    }

    override suspend fun shutdown() {
        isActive = false
        cardPool.clear()
        scope.cancel()                              // D3: 取消所有子协程（timer 等）
        super.shutdown()
        Logger.i("Agent.Hello") { "shutdown complete" }
    }

    // ═══ 工作协程 #1：PresenceBus 事件收集 ═══

    private suspend fun collectPresenceEvents() {
        val bus = presenceBus ?: run {
            Logger.w("Agent.Hello") { "presenceBus null, skip collectPresenceEvents" }
            return
        }
        Logger.i("Agent.Hello") { "collectPresenceEvents started" }
        bus.events.collect { event ->
            if (agentPaused) return@collect  // D2: runLoop 暂停期间跳过事件
            when (event) {
                is PresenceEvent.DjBlank -> {
                    Logger.d("Agent.Hello") { "DjBlank → maybe update GREETING" }
                    runCatching { refreshGreetingIfNeeded(30_000L, reason = "DjBlank") }
                        .onFailure { e -> Logger.w("Agent.Hello", e) { "DjBlank GREETING refresh failed" } }
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
        Logger.i("Agent.Hello") { "dailyRefreshLoop: one-shot check on start" }
        runCatching { checkAndRunDailyRefresh() }
            .onFailure { e -> Logger.e("Agent.Hello", e) { "dailyRefreshOnce failed" } }
        // 跑完即退出——跨天由 minuteTickLoop 兜底
    }

    /** 核心逻辑：检查今日是否需要跑 dailyRefreshOnce，需要则跑 */
    private suspend fun checkAndRunDailyRefresh() {
        val today = todayString()
        val hasTodayCards = cardCacheDao?.let { dao ->
            val allTodayTypes = runCatching {
                dao.getLatestCardTypesByDate(today)
            }.getOrDefault(emptySet())
            SlideType.RECOMMEND.name in allTodayTypes
                || SlideType.DISCOVER.name in allTodayTypes
                || SlideType.FORGOTTEN.name in allTodayTypes
                || SlideType.ANNIVERSARY.name in allTodayTypes
        } ?: todayCardsGenerated

        if (!hasTodayCards) {
            Logger.i("Agent.Hello") { "dailyRefresh: today=$today not yet generated → run" }
            runCatching { dailyRefreshOnce() }
                .onFailure { e -> Logger.e("Agent.Hello", e) { "dailyRefreshOnce failed" } }
            if (cardCacheDao == null) {
                todayCardsGenerated = true
            }
        }
        // 报告叙事段也在此时检查
        runCatching { ensureReportNarrativeUpToDate() }
            .onFailure { e -> Logger.w("Agent.Hello", e) { "report narrative ensure failed" } }
    }

    /** 每日批量生成：RECOMMEND + DISCOVER + FORGOTTEN + ANNIVERSARY + 写入 DAO */
    private suspend fun dailyRefreshOnce() {
        val repo = musicRepository
        // B3: getMusicCount 轻量判空，不拉全曲库
        if (repo == null || runCatching { repo.getMusicCount().first() }.getOrDefault(0) <= 0) {
            Logger.w("Agent.Hello") { "dailyRefreshOnce: musicRepository null or library empty, skip" }
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

        // ⑤ 写入 DAO（先删同类型旧行，避免 append 导致脏数据残留；没生成的类型也要清）
        runCatching {
            val generatedTypes = allCards.map { it.type.name }.toSet()
            // 先清掉"已知类型但本次没生成"的旧数据
            listOf("RECOMMEND", "DISCOVER", "FORGOTTEN", "ANNIVERSARY").forEach { type ->
                if (type !in generatedTypes) cardCacheDao?.deleteByType(type)
            }
            // 再删 + 插本次生成的
            allCards.forEach { card ->
                cardCacheDao?.deleteByType(card.type.name)
                cardCacheDao?.insert(
                    HelloCardCache(
                        cardType = card.type.name,
                        cardContentJson = cardContentToString(card.content),
                        generatedAt = currentTimeMillis(),
                        generatedForDate = today,
                    )
                )
            }
        }.onFailure { e -> Logger.w("Agent.Hello", e) { "DAO insert failed (non-fatal)" } }

        Logger.i("Agent.Hello") { "dailyRefreshOnce: done, ${allCards.size} cards generated" }
    }

    // ═══ 工作协程 #3：每分钟 tick ═══

    private suspend fun minuteTickLoop() {
        Logger.i("Agent.Hello") { "minuteTickLoop started" }
        var lastDate = todayString()  // 跨天检测
        while (scope.isActive && isActive && !agentPaused) {
            delay(60_000L)

            // ① 跨天兜底：App 长驻到明天 → 补跑今日卡
            val now = todayString()
            if (now != lastDate) {
                Logger.i("Agent.Hello") { "minuteTickLoop: date changed $lastDate → $now, run dailyRefreshOnce" }
                lastDate = now
                runCatching { checkAndRunDailyRefresh() }
                    .onFailure { e -> Logger.w("Agent.Hello", e) { "date-change dailyRefresh failed" } }
            }

            // ② 检查时段变化
            val currentPhase = detectTimePhase(currentHour())
            if (currentPhase != lastPhase) {
                Logger.i("Agent.Hello") { "minuteTickLoop: phase changed ${lastPhase} → $currentPhase" }
                lastPhase = currentPhase
                // 时段变了 → 刷新 RECOMMEND（内存 + DAO 双写）
                runCatching { refreshRecommendCard(currentPhase) }
                    .onFailure { e -> Logger.w("Agent.Hello", e) { "phase-change RECOMMEND refresh failed" } }
                // 时段变了 → 必刷 GREETING（限频 0，跨变必过）
                runCatching { refreshGreetingIfNeeded(0L, reason = "phase-change") }
                    .onFailure { e -> Logger.w("Agent.Hello", e) { "phase-change GREETING refresh failed" } }
            }

            // ②.5 RECOMMEND 缺失重试：启动时 label 可能还没打上，导致 dailyRefreshOnce 里
            // RECOMMEND 生成失败。之后 label 打上了但不会再有人来触发 RECOMMEND 生成——
            // 所以这里每分钟检查一次，直到卡真正出现在 CardPool 里为止。
            if (!cardPool.containsType(SlideType.RECOMMEND)) {
                runCatching {
                    val phase = detectTimePhase(currentHour())
                    refreshRecommendCard(phase)
                }.onFailure { e -> Logger.w("Agent.Hello", e) { "RECOMMEND missing-retry failed" } }
            }

            // ③ GREETING 切歌联动（5min 限频）
            runCatching {
                val ctx = nowPlayingProvider?.getNowPlaying()
                if (ctx?.isPlaying == true && ctx.currentMusicId != null && ctx.currentMusicId != lastTrackId) {
                    Logger.d("Agent.Hello") { "minuteTickLoop: track changed → try GREETING refresh" }
                    lastTrackId = ctx.currentMusicId
                    refreshGreetingIfNeeded(5 * 60_000L, reason = "track-change", songId = ctx.currentMusicId)
                }
            }.onFailure { e -> Logger.w("Agent.Hello", e) { "track-change GREETING check failed" } }

            // ④ GREETING 兜底定时（每 2h 检查一次）
            runCatching {
                val nowMs = currentTimeMillis()
                if (nowMs - lastGreetingPeriodicCheck >= 2 * 60 * 60_000L) {
                    lastGreetingPeriodicCheck = nowMs
                    refreshGreetingIfNeeded(30 * 60_000L, reason = "periodic")
                }
            }.onFailure { e -> Logger.w("Agent.Hello", e) { "periodic GREETING check failed" } }
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
        if (restored > 0) Logger.i("Agent.Hello") { "initializeFromDao: restored $restored today=$today cards" }
    }

    /** runLoop 启动时 push GREETING（ANCHOR/RADIO_STATUS 已拆到 UI 层直接订阅数据源） */
    private suspend fun initializeAnchorCards() {
        // 启动时直接生成一次 GREETING（允许，不受限频）
        runCatching { refreshGreetingIfNeeded(0L, reason = "startup") }
            .onFailure { e -> Logger.w("Agent.Hello", e) { "startup GREETING failed" } }
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
        if (minIntervalMs > 0 && now - lastGreetingRefreshAt < minIntervalMs) {
            Logger.d("Agent.Hello") { "GREETING skip: too soon (interval=${minIntervalMs}ms, reason=$reason)" }
            return
        }
        if (songId != null && songId == lastGreetingSongId) {
            Logger.d("Agent.Hello") { "GREETING skip: same song ($songId)" }
            return
        }
        val content = generateGreeting()
        lastGreetingRefreshAt = now
        if (songId != null) lastGreetingSongId = songId
        cardPool.replace(
            SlideType.GREETING,
            SlideCard(SlideCard.newId(), SlideType.GREETING, content)
        )
        Logger.i("Agent.Hello") { "GREETING refreshed: type=${content.type} reason=$reason" }
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
        recentGreetingTypes.addLast(type)
        if (recentGreetingTypes.size > 3) recentGreetingTypes.removeFirst()

        val (text, fromFallback) = (if (enableLlm) {
            runCatching {
                val cfg = enrichConfig ?: return@runCatching null
                val prompt = buildGreetingPrompt(type, phase, nowPlayingStr, annivHint, signalHint)
                val temp = typeTemperature(type)
                contextBudget.callLlmText(
                    config = cfg,
                    systemPrompt = typeSystemPrompt(type),
                    newMessages = listOf(LlmMessage(role = "user", content = prompt)),
                    temperature = temp,
                )?.trim()?.let { text ->
                    text.trim('\u0022', '\u300C', '\u300D', '\u201C', '\u201D')
                }
            }.getOrNull()?.let { it to false }
        } else null) ?: (null to true)

        val finalText = text ?: fallbackForType(type)
        if (finalText.isNotBlank()) {
            recentGreetingContents.addLast(finalText.take(30))
            if (recentGreetingContents.size > 5) recentGreetingContents.removeFirst()
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
            TYPE_PREFS_NOW_PLAYING.forEach { (t, w) -> scores.merge(t, w * 100.0) { a, b -> a + b } }
        }

        // ② 音乐纪念日（强信号）
        if (!annivHint.isNullOrBlank()) {
            TYPE_PREFS_ANNIV.forEach { (t, w) -> scores.merge(t, w * 100.0) { a, b -> a + b } }
        }

        // ③ 时段（稳定信号）
        val phasePrefs = TYPE_PREFS_PHASE[phase]
        if (phasePrefs != null) {
            phasePrefs.forEach { (t, w) -> scores.merge(t, w * 60.0) { a, b -> a + b } }
        }

        // ④ Top artist/genre（弱信号，暂不查 DB 避免额外开销）—— 留空

        // 惩罚：recentGreetingTypes 中出现过的类型降分
        val penaltyMap = mutableMapOf<GreetingType, Double>()
        recentGreetingTypes.forEachIndexed { idx, t ->
            val factor = when (idx) { 0 -> 0.4; 1 -> 0.6; else -> 0.8 }
            penaltyMap[t] = (penaltyMap[t] ?: 0.0) + factor
        }
        scores.forEach { (t, _) ->
            val penalty = penaltyMap[t] ?: 0.0
            val current = scores[t] ?: 0.0
            scores[t] = current * (1.0 - penalty * 0.5)
        }

        // 随机扰动（0.9-1.1）
        scores.forEach { (t, v) -> scores[t] = v * (0.9 + Math.random() * 0.2) }

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
        if (lastAnnivCheckDate == today) return cachedAnniversaryHint
        lastAnnivCheckDate = today

        if (!enableLlm) {
            cachedAnniversaryHint = null
            return null
        }
        val hint = runCatching {
            val cfg = enrichConfig ?: return@runCatching null
            val parts = today.split("-")
            if (parts.size < 3) return@runCatching null
            val month = parts[1].toIntOrNull() ?: return@runCatching null
            val day = parts[2].toIntOrNull() ?: return@runCatching null
            val prompt = """今天是 $month 月 $day 日。请判断音乐史上今天是否有重要事件发生。

返回 JSON：
{"hasEvent": true/false, "eventType": "诞辰/逝世/发行/成立/解散", "subject": "主体名称", "year": 年份}

要求：
- 必须是真实可查证的音乐事件，流行、古典、摇滚、爵士、华语乐坛都可以
- 如果不确定或没有，请返回 {"hasEvent": false}
- 不要编造，不确定就说没有"""
            contextBudget.callLlmText(
                config = cfg,
                systemPrompt = "你是音乐史专家，擅长准确回忆具体日期的音乐事件。",
                newMessages = listOf(LlmMessage(role = "user", content = prompt)),
                temperature = 0.2f,
            )?.trim()?.let { text ->
                // 简单解析 hasEvent + subject
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
            }
        }.getOrNull()
        cachedAnniversaryHint = hint
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
        if (recentGreetingContents.isNotEmpty()) {
            sb.appendLine("以下是你最近输出过的内容，请避开相同的主题和表达：")
            recentGreetingContents.forEachIndexed { i, content ->
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
    private fun typePromptRequirement(type: GreetingType): String = when (type) {
        GreetingType.QUOTE -> """
写作要求：
  · 一句话，20-50 字
  · 关于音乐、声音、旋律的哲思或诗意表达
  · 可以引用真实音乐家的话（但不确定就虚构一句自然的）
  · 有画面感，适合当前时段的情绪"""
        GreetingType.LYRIC_GOLD -> """
写作要求：
  · 一句或两句歌词金句，20-60 字
  · 如果有当前播放的歌曲，优先从这首歌里挑标志性歌词
  · 没有当前播放就选一句广为人知的华语或经典歌词
  · 附上出处（歌曲名/歌手）"""
        GreetingType.FACT -> """
写作要求：
  · 一段冷知识，80-150 字
  · 关于音乐、乐器、音乐家、录音技术的有趣事实
  · 要求准确，不确定的内容请说明「根据公开资料」
  · 语言轻松有趣，像朋友分享一个小秘密"""
        GreetingType.STORY -> """
写作要求：
  · 一段故事，100-200 字
  · 关于某首歌、某张专辑、某次经典演出背后的故事
  · 如果有当前播放的歌曲，优先讲这首歌的故事
  · 有细节、有温度，像在讲一个你亲历的八卦"""
        GreetingType.ARTIST -> """
写作要求：
  · 一段轶事，80-150 字
  · 关于某位音乐家不为人知的小故事
  · 可以是古典大师、流行歌手、摇滚传奇
  · 轻松幽默，展现音乐家鲜活有趣的一面"""
        GreetingType.LISTEN -> """
写作要求：
  · 一段听歌引导，50-100 字
  · 结合当前时段和场景，给一个具体的听歌建议
  · 比如「深夜戴上耳机听 XXX」「开车时放 XXX 很搭」
  · 语气亲切自然，像朋友推荐"""
    }

    /** 类型专属 system prompt */
    private fun typeSystemPrompt(type: GreetingType): String = when (type) {
        GreetingType.QUOTE -> "你是一个热爱音乐的诗人，擅长写出触动人心的音乐名句。"
        GreetingType.LYRIC_GOLD -> "你是一个记歌词的音乐达人，对华语流行和经典歌曲的歌词了如指掌。"
        GreetingType.FACT -> "你是一个音乐史爱好者，了解关于音乐的各种有趣冷知识，追求准确。"
        GreetingType.STORY -> "你是一个音乐故事讲述者，擅长挖掘歌曲背后不为人知的故事。"
        GreetingType.ARTIST -> "你是一个音乐圈的八卦大王，知道各种音乐家的有趣轶事。"
        GreetingType.LISTEN -> "你是一个懂场景的音乐 DJ，总能在对的时间推荐对的歌。"
    }

    /** 类型专属 temperature */
    private fun typeTemperature(type: GreetingType): Float = when (type) {
        GreetingType.QUOTE -> 0.8f       // 需要文学创造性
        GreetingType.FACT -> 0.4f         // 需要事实准确
        GreetingType.LYRIC_GOLD -> 0.6f
        GreetingType.STORY -> 0.7f
        GreetingType.ARTIST -> 0.6f
        GreetingType.LISTEN -> 0.5f
    }

    /** 6 类型独立 fallback 模板库（LLM 不可用时的保底） */
    private fun fallbackForType(type: GreetingType): String {
        val list = when (type) {
            GreetingType.QUOTE -> listOf(
                "音乐是情绪的翻译官",
                "好音乐让沉默也变得动听",
                "旋律是通往记忆的捷径",
                "一首歌，一个时刻，一段人生",
                "音乐不会说谎",
                "有些情绪，只能唱出来",
                "耳朵里的世界，心里的远方",
                "一首歌的时间，足够想你",
                "旋律不记得词，但记得你",
                "没有不会过时的歌，只有过时的心情",
            )
            GreetingType.LYRIC_GOLD -> listOf(
                "「故事的小黄花，从出生那年就飘着」——周杰伦《晴天》",
                "「后来，我总算学会了如何去爱」——刘若英《后来》",
                "「对这个世界如果你有太多的抱怨」——周杰伦《稻香》",
                "「我怀念的是无话不说」——孙燕姿《我怀念的》",
                "「朋友一生一起走」——周华健《朋友》",
                "「可惜不是你，陪我到最后」——梁静茹《可惜不是你》",
                "「十年之前，我不认识你」——陈奕迅《十年》",
                "「那些年错过的大雨」——胡夏《那些年》",
                "「如果我们不曾相遇」——五月天《后来的我们》",
                "「阴天快乐」——陈奕迅《阴天快乐》",
            )
            GreetingType.FACT -> listOf(
                "莫扎特 3 岁就能弹钢琴，5 岁开始作曲，是史上最年轻的作曲家之一",
                "贝多芬在听力衰退后，用牙咬木棍贴在钢琴上来感受振动继续创作",
                "史上最畅销专辑是迈克尔·杰克逊的《Thriller》，全球销量超 6600 万张",
                "吉他的六根弦从低到高分别是 EADGBE，这个标准调弦法从 18 世纪沿用至今",
                "古典音乐中，小提琴的四根弦 GDAE 可以追溯到 16 世纪的文艺复兴时期",
                "录音史上第一张商业唱片发行于 1895 年，由爱迪生的留声机录制",
                "巴赫的《平均律钢琴曲集》被称为音乐界的《旧约》，是每个学钢琴的人的必弹曲目",
                "摇滚乐的诞生通常被认为是 1951 年，由 Chuck Berry 的《Johnny B. Goode》开启",
                "爵士音乐起源于 20 世纪初的美国新奥尔良，融合了布鲁斯和拉格泰姆",
                "华语流行史上销量最高的专辑是邓丽君的《Teresa Teng》系列，全球销量超 5000 万张",
            )
            GreetingType.STORY -> listOf(
                "周杰伦《晴天》里唱到的秋千，真实存在于他当年就读的淡江中学，现在已经成了歌迷打卡点",
                "陈奕迅《十年》的 MV 里有一个镜头是陈奕迅在便利店门口唱歌，那家店就在香港九龙，至今还在营业",
                "五月天《温柔》是鼓手冠佑在深夜失眠时写的，当时他只是随便弹了几个和弦，没想到成了经典",
                "孙燕姿《遇见》最初是为电影《向左走，向右走》创作的主题曲，电影上映后这首歌反而比电影更红",
                "Beyond《光辉岁月》是黄家驹为纪念南非前总统曼德拉而创作的，表达对平等和自由的追求",
                "王菲《红豆》里「还没为你把红豆，熬成缠绵的伤口」这句歌词，是林夕在失恋期间写的",
                "周杰伦《稻香》的 MV 全部在乡下拍摄，周杰伦说这首歌想表达的是「回归简单的快乐」",
                "梁静茹《勇气》的 MV 女主角是桂纶镁，这是她第一次拍 MV，后来她因为这部 MV 被导演选中拍电影",
                "五月天《倔强》最初不是专辑主打歌，是歌迷在网络上自发投票选出来的热门曲目",
                "邓丽君《月亮代表我的心》并不是她原唱，她把这首歌从一首不为人知的老歌唱成了华语经典",
            )
            GreetingType.ARTIST -> listOf(
                "贝多芬脾气暴躁，有一次演出时他发脾气把钢琴盖砸坏了，但观众们反而更欣赏他的真性情",
                "莫扎特生前曾写过一首《G 大调弦乐四重奏》送给一位理发师，因为那位理发师帮他剪了一个很帅的发型",
                "巴赫每天工作 14 小时，不仅作曲还在教堂担任管风琴师，他一生写了超过 1000 部作品",
                "迈克尔·杰克逊有一次排练时不小心被舞台上的烟火烧伤了头发，但他坚持完成了演出",
                "周杰伦高中时因为成绩不好被老师批评，后来他把这段经历写成了《听妈妈的话》",
                "邓丽君会说五种语言，除了中文和粤语，她还会英语、日语和法语",
                "阿黛尔曾经因为太胖被音乐学院拒绝，后来她用实力证明了自己不需要靠外形",
                "五月天主唱阿信的原名是陈信宏，他「阿信」这个昵称来自日本漫画《阿信》的女主角",
                "陈奕迅有严重的舞台焦虑症，每次上台前都会紧张到手心出汗，但一开口唱歌就完全投入",
                "王菲的女儿窦靖童现在也成了歌手，母女俩曾经同台演出过《因为爱情》",
            )
            GreetingType.LISTEN -> listOf(
                "深夜戴上耳机听一首轻音乐，会让你发现白天没注意到的细节",
                "开车跑长途时试试放 jazz，比摇滚更能让你保持清醒又放松",
                "下雨天适合听带点忧伤的歌，窗外的雨声和旋律会形成奇妙的和声",
                "加班累了，放一首 80 年代的老歌，旋律里藏着你小时候的温度",
                "清晨起床后听一首节奏明快的歌，比咖啡更能唤醒你",
                "散步时试试听纯音乐，没有歌词的干扰，你会更注意周围的声音",
                "写代码或看书时放 Lo-fi 或 ambient，有背景声但不打扰思考",
                "洗澡时听一首你喜欢的歌，浴室的回声会让它听起来像演唱会现场",
                "做饭时放一首轻快的歌，切菜都会变得更有节奏感",
                "睡前 10 分钟听一首慢歌，比刷手机更能帮你放松入睡",
            )
        }
        val idx = (greetingIndex++) % list.size
        return list[idx]
    }

    // ──────────────────── GREETING 类型偏好常量 ────────────────────

    /** 信号 → 类型偏好（权重 0-1，×100 加到打分） */
    private val TYPE_PREFS_NOW_PLAYING: Map<GreetingType, Double> = mapOf(
        GreetingType.LYRIC_GOLD to 0.9, GreetingType.STORY to 0.8, GreetingType.ARTIST to 0.7,
        GreetingType.FACT to 0.5, GreetingType.LISTEN to 0.4, GreetingType.QUOTE to 0.3,
    )
    private val TYPE_PREFS_ANNIV: Map<GreetingType, Double> = mapOf(
        GreetingType.FACT to 0.9, GreetingType.STORY to 0.8, GreetingType.ARTIST to 0.7,
        GreetingType.QUOTE to 0.5, GreetingType.LISTEN to 0.2, GreetingType.LYRIC_GOLD to 0.1,
    )
    private val TYPE_PREFS_PHASE: Map<TimePhase, Map<GreetingType, Double>> = mapOf(
        TimePhase.NIGHT to mapOf(
            GreetingType.QUOTE to 0.9, GreetingType.STORY to 0.7, GreetingType.LISTEN to 0.7,
            GreetingType.FACT to 0.6, GreetingType.LYRIC_GOLD to 0.4, GreetingType.ARTIST to 0.3,
        ),
        TimePhase.MORNING_COMMUTE to mapOf(
            GreetingType.LISTEN to 0.9, GreetingType.QUOTE to 0.6, GreetingType.FACT to 0.5,
        ),
        TimePhase.WORK to mapOf(
            GreetingType.FACT to 0.8, GreetingType.QUOTE to 0.6, GreetingType.LISTEN to 0.5,
        ),
        TimePhase.LUNCH to mapOf(
            GreetingType.LYRIC_GOLD to 0.8, GreetingType.QUOTE to 0.7, GreetingType.LISTEN to 0.6,
        ),
        TimePhase.EVENING_COMMUTE to mapOf(
            GreetingType.LISTEN to 0.9, GreetingType.STORY to 0.6, GreetingType.FACT to 0.5,
        ),
        TimePhase.EVENING_LEISURE to mapOf(
            GreetingType.STORY to 0.9, GreetingType.ARTIST to 0.7, GreetingType.LYRIC_GOLD to 0.6,
        ),
    )

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

        Logger.i("Agent.Hello") { "ANNIVERSARY check start today=$today durations=${durationsByMusic.size}" }

        // ── 收集 4 种候选 ──
        val candidates = mutableListOf<AnniversaryCandidate>()

        // A. FIRST_PLAY —— N 年前的今天首次播放过的歌
        runCatching { repo.getAnniversaryTracks(today).firstOrNull() }
            .getOrNull()?.let { (id, firstPlayedAt, thatDayPlays) ->
                val yearsAgo = ((now - firstPlayedAt) / (365.25 * 86_400_000L)).toInt()
                if (yearsAgo <= 0) return@let  // 数据异常 guard：0 或负年数无意义
                val score = 100 * yearsAgo
                Logger.i("Agent.Hello") { "ANNIVERSARY A: FIRST_PLAY id=$id years=$yearsAgo score=$score" }
                candidates += AnniversaryCandidate(
                    score = score,
                    buildContent = { buildFirstPlayContent(repo, id, firstPlayedAt, thatDayPlays, yearsAgo) },
                )
            } ?: Logger.d("Agent.Hello") { "ANNIVERSARY A: no FIRST_PLAY candidate today" }

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

        Logger.i("Agent.Hello") { "ANNIVERSARY candidates count=${candidates.size} scores=${candidates.map { it.score }}" }

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

    /** 自适应报告叙事生成——根据日均听歌时长判断频率 */
    suspend fun regenerateReportNarrative(timeRange: NarrativeTimeRange): HelloReportNarrativeEntity? {
        val repo = musicRepository ?: return null
        val dao = narrativeDao ?: return null

        val avgMinutes = runCatching { repo.getAvgDailyListeningMinutes(30) }.getOrDefault(0f)
        val frequency = adaptiveFrequency(avgMinutes)
        val range = timeRange.name

        // 频率守卫：DAO 有未过期缓存就直接返回，不重复生成
        val existing = runCatching { dao.getLatest(range) }.getOrNull()
        if (existing != null && !isNarrativeExpired(existing, frequency)) {
            Logger.d("Agent.Hello") { "report[$range]: fresh cache (avgDaily=$avgMinutes, freq=$frequency), skip" }
            return existing
        }

        // H2 骨架：生成极简统计叙事（后续 H4 补 LLM + 完整统计维度）
        val narrative = buildStatisticsNarrative(timeRange, avgMinutes)
        val entity = HelloReportNarrativeEntity(
            timeRange = range,
            narrative = narrative,
            generatedAt = currentTimeMillis(),
            avgDailyMinutes = avgMinutes,
        )
        runCatching { dao.insert(entity) }.onFailure { e ->
            Logger.w("Agent.Hello", e) { "report[$range] DAO insert failed (non-fatal)" }
        }
        Logger.i("Agent.Hello") { "report[$range] regenerated (avgDaily=${avgMinutes}min, freq=$frequency)" }
        return entity
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
                .onFailure { e -> Logger.w("Agent.Hello", e) { "report[$range] ensure failed (non-fatal)" } }
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

    /** H2 骨架极简统计叙事——后续 H4 补 LLM 生成 */
    private fun buildStatisticsNarrative(timeRange: NarrativeTimeRange, avgMinutes: Float): String {
        val activeDesc = when {
            avgMinutes <= 15f -> "最近听的不多"
            avgMinutes <= 30f -> "每天都有在听"
            avgMinutes <= 120f -> "音乐陪伴还不错"
            else -> "音乐成了你生活的背景"
        }
        return "（$activeDesc）日均听歌 ${avgMinutes.toInt()} 分钟。"
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
        Logger.i("Agent.Hello") { "updateAiConfig: enableLlm=$enableLlm, config=${enrichConfig != null}" }
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
        if (enableLlm) {
            val llmReason = runCatching {
                val cfg = enrichConfig ?: return@runCatching null
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
                }
                contextBudget.callLlmText(
                    config = cfg,
                    systemPrompt = "你是一个资深音乐评论人兼知心朋友，擅长结合歌词、场景和听众历史，写出有温度有画面感的中文推荐文字。",
                    newMessages = listOf(LlmMessage(role = "user", content = prompt)),
                    temperature = 0.6f,
                )?.trim()?.let { text ->
                    text.trim('\u0022', '\u300C', '\u300D', '\u201C', '\u201D')
                }
            }.getOrNull()
            if (!llmReason.isNullOrBlank()) return llmReason
        }
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
            cardCacheDao?.deleteByType(SlideType.RECOMMEND.name)
            cardCacheDao?.insert(
                HelloCardCache(
                    cardType = SlideType.RECOMMEND.name,
                    cardContentJson = cardContentToString(card.content),
                    generatedAt = currentTimeMillis(),
                    generatedForDate = todayString(),
                )
            )
        }.onFailure { e -> Logger.w("Agent.Hello", e) { "refreshRecommendCard: dao write failed" } }
        Logger.i("Agent.Hello") { "refreshRecommendCard: OK phase=$phase" }
        return true
    }

    /** DISCOVER 发现理由——支持 LLM 个性化，兜底固定文案 */
    private suspend fun reasonForDiscover(label: LabelName): String {
        if (enableLlm) {
            val llmReason = runCatching {
                val cfg = enrichConfig ?: return@runCatching null
                val prompt = "用一句话（≤20字）推荐用户重新发现「${label.name}」风格的音乐，语气温暖。直接返回中文句子。"
                contextBudget.callLlmText(
                    config = cfg,
                    systemPrompt = "你是一个懂音乐的朋友，负责用温暖简洁的中文推荐音乐。",
                    newMessages = listOf(LlmMessage(role = "user", content = prompt)),
                    temperature = 0.6f,
                )?.trim()?.let { text ->
                    text.trim('\u0022', '\u300C', '\u300D', '\u201C', '\u201D')
                }
            }.getOrNull()
            if (!llmReason.isNullOrBlank()) return llmReason
        }
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
        if (enableLlm) {
            val llmText = runCatching {
                val cfg = enrichConfig ?: return@runCatching null
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
                contextBudget.callLlmText(
                    config = cfg,
                    systemPrompt = "你是一个擅长写怀旧随笔的音乐人，能从一句歌词、一段旋律、一个时间跨度里，写出让人心头一暖的中文文字。",
                    newMessages = listOf(LlmMessage(role = "user", content = prompt)),
                    temperature = 0.7f,
                )?.trim()?.let { text ->
                    text.trim('\u0022', '\u300C', '\u300D', '\u201C', '\u201D')
                }
            }.getOrNull()
            if (!llmText.isNullOrBlank()) return llmText
        }
        // 兜底——仍然结合歌曲名
        val artistSuffix = artist?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        return when {
            playCount > 0 && daysSince >= 180 -> "那年你循环了 $playCount 遍的「${trackTitle}」，还记得吗？"
            playCount > 0 -> "「${trackTitle}」$artistSuffix，你曾经播放过 $playCount 次"
            daysSince >= 90 -> "「${trackTitle}」$artistSuffix，好久没想起它了"
            else -> "「${trackTitle}」$artistSuffix，再听一次？"
        }
    }

    /** ANNIVERSARY 情感文案——支持 LLM，兜底固定模板 */
    /** ANNIVERSARY 情感化文案——按 subtype 分 4 种 prompt + 兜底模板。 */
    private suspend fun generateAnniversaryEmotionText(
        subtype: com.hmp.domain.agent.sub.AnniversarySubtype,
        value: Int,  // 含义随 subtype 变：yearsAgo / milestoneValue
        total: Int,  // 累计播放
    ): String {
        // LLM 分支
        if (enableLlm) {
            val llmText = runCatching {
                val cfg = enrichConfig ?: return@runCatching null
                val prompt = when (subtype) {
                    com.hmp.domain.agent.sub.AnniversarySubtype.FIRST_PLAY ->
                        "用一句话（≤25字）庆祝「${value}年前的今天」第一次收藏一首歌。累计播放 ${total} 次。温暖带感动。"
                    com.hmp.domain.agent.sub.AnniversarySubtype.PLAYLIST_CREATE ->
                        "用一句话（≤25字）庆祝「${value}年前的今天」创建了一个歌单。累计被播放 ${total} 次。温暖。"
                    com.hmp.domain.agent.sub.AnniversarySubtype.PLAY_MILESTONE ->
                        "用一句话（≤25字）庆祝一首歌累计播放达到第 ${value} 次。当前共 ${total} 次。有成就感。"
                    com.hmp.domain.agent.sub.AnniversarySubtype.DURATION_MILESTONE ->
                        "用一句话（≤25字）庆祝一首歌累计听了 ${value} 小时。共播放 ${total} 次。有成就感。"
                }
                contextBudget.callLlmText(
                    config = cfg,
                    systemPrompt = "你是一个懂音乐的朋友，擅长用温暖简洁的文字唤起听众的回忆。",
                    newMessages = listOf(LlmMessage(role = "user", content = prompt)),
                    temperature = 0.6f,
                )?.trim()?.let { text ->
                    text.trim('\u0022', '\u300C', '\u300D', '\u201C', '\u201D')
                }
            }.getOrNull()
            if (!llmText.isNullOrBlank()) return llmText
        }
        // 兜底模板
        return when (subtype) {
            com.hmp.domain.agent.sub.AnniversarySubtype.FIRST_PLAY -> "第一次听到这首，已是 $value 年前"
            com.hmp.domain.agent.sub.AnniversarySubtype.PLAYLIST_CREATE -> "$value 年前的今天建的这个歌单"
            com.hmp.domain.agent.sub.AnniversarySubtype.PLAY_MILESTONE -> "第 $value 次循环，你值得"
            com.hmp.domain.agent.sub.AnniversarySubtype.DURATION_MILESTONE -> "累计 $value 小时，这首歌陪你很久了"
        }
    }

    private fun todayString(): String = todayDateString()

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
            }
        }.getOrNull()
    }

    companion object {
        // 空 companion —— 旧 DEFAULT_FALLBACK_GREETINGS 已删除，
        // 6 类型独立 fallback 模板库已内嵌在 fallbackForType() 方法中
    }

    /** 毫秒时间戳 → "MM.dd · N 年前" 格式。mmdd 为空时退化为 "N 年前"。 */
    private fun formatDate(ms: Long, yearsAgo: Int): String {
        val mmdd = runCatching { com.hmp.data.util.formatMmddFromMillis(ms) }.getOrDefault("")
        return if (mmdd.isBlank()) "${yearsAgo} 年前" else "$mmdd · ${yearsAgo} 年前"
    }
}

