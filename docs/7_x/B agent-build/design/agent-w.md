# HMP Agent 设计 · W 阶段（Hello 呈现）

> **上游**：[`agent.md`](agent.md)（设计总纲，单一事实来源）｜[`agent-architecture.md`](agent-architecture.md)（架构详解）
> **轴别**：插叙（实施中新增的横切阶段，不占里程碑编号）
> **依赖链**：T + M6 + V → **W0** → W1 → W2
>
> 本文合并自原四份 W 文档（`agent-hello.md` / `agent-w1.md` / `agent-w2.md` / `agent-w-gaps.md`），
> 依次对应下面四节：W0 引擎侧 / W1 页面级 / W2 组件级 / W 缺口登记。

W 阶段解决一件事：**引擎侧能力已经齐了，但 UI 没接线，用户感知不到。** 三个 SubAgent 在发事件、卡片池已写好，缺的是让它们出现在屏幕上。

因此分三步推进，顺序不可调换：

| 步 | 范围 | 交付 |
|----|------|------|
| **W0** | 引擎侧 | `HelloSubAgent` 完整实现 + `AgentScheduler` 加 HELLO(4) + Radio/Enrich 运行态 emit 补齐 |
| **W1** | 页面级 | HomeScreen / AIScreen / ChatScreen / AgentMonitor / 听歌报告 五个页面接线 |
| **W2** | 组件级 | 锚点层 5 件 + 场景入口 6 件，共 11 个组件接线 |

**W0 是硬前置**：W1/W2 所有 UI 改造的数据源都在 W0，不能独立推进。

***

## W0 · 引擎侧（HelloSubAgent 完整实现）



#### 0. 前置改动（实现 HelloSubAgent 之前必须先做）

| # | 改动                               | 文件                  | 说明                                                                                                                                                                                     |
| - | -------------------------------- | ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | AgentScheduler 加 HELLO 枚举        | `AgentScheduler.kt` | `enum class AgentPriority` 新增 `HELLO(4)`；`decideState()` 新增 `AgentPriority.HELLO -> decideHelloState()`；暂停条件：**永不暂停**（token 未完全耗尽时 keep alive）。Hello 是 Master 之外最靠近用户的 SubAgent，不做激进省电 |
| 2 | RadioSubAgent emit AgentProgress | `RadioSubAgent.kt`  | `startRadio()` 成功后 `presenceBus?.emit(AgentProgress(agentId="radio", processed=0, total=targetCount))`；`stopRadio()` 时 `emit(AgentProgress(agentId="radio", processed=0, total=0))`    |
| 3 | MasterAgent 持有 PresenceBus       | `MasterAgent.kt`    | 当前 `chatPresenceBus` 已存在，HelloSubAgent 通过构造函数注入它来 collect DjBlank + AgentProgress                                                                                                      |

***

#### 1. 定位与职责边界

三个 SubAgent 同属 MasterAgent，定位不同：

| SubAgent          | 被动/主动                         | 感知用户          | 跑 batch loop      | 职责                                    |
| ----------------- | ----------------------------- | ------------- | ----------------- | ------------------------------------- |
| EnrichSubAgent    | 被动（Master 下令 startEnrich 才开始） | 否（不知道对话上下文）   | 是（自循环 Worker）     | 后台音乐富化                                |
| RadioSubAgent     | 被动（Master 下令 startRadio 才开始）  | 否（不自演化对话）     | 是（自循环续歌）          | AI 电台                                 |
| **HelloSubAgent** | **主动**（三个定时触发）                | **否（不接用户消息）** | **否（纯定时 + 事件响应）** | 门面副驾驶——把"音乐库 + 最近行为 + 当前时段"变成用户想看的内容卡 |

**F1-F6 铁则体现**：

- **F1**：Master 管生命周期（startHello / stopHello / pause / resume），Hello 管内部三个协程 + 卡片池管理

- **F2**：独立 AgentContextBudget(128K)——Hello 输出是面向用户的自然语言（问候/叙事/推荐理由），比 Enrich/Radio 更吃 token

- **F3**：暂停/恢复由 Scheduler 通过 StopSignal 触发（priority=HELLO=4，永不暂停）

- **F5**：system prompt 由 Master 注入（人格预设从 AgentPolicyConfig.Hello 读）

- **F6**：不知道电量/网络/富化进度，只观察 PresenceBus + MusicRepository + 当前播放状态

**与旧推荐引擎关系**：DailyHeroCard / HeartbeatSection 是纯算法组件（跳过率/收藏率加权），不感知时段、不感知行为模式、不解释为什么。HelloSubAgent 替代它们——输出不止推荐，还包括问候、歌手/风格探索、纪念日唤醒、习惯洞察。

***

#### 2. 生命周期与 MasterAgent 接线

#### MasterAgent.startHello() 完整模式

```kotlin
// 在 startEnrich / startRadio 之后，照着同样模式
// 关键区别：Hello 是唯一 Master 默认启动的 SubAgent（initialize() 末尾自动调）

@Volatile private var helloListenersStarted: Boolean = false

suspend fun startHello(): HelloSubAgent? {
    // ① 幂等守卫
    _subAgents["hello"]?.let { return it as HelloSubAgent }
    
    // ② 前置依赖 null check —— Hello 没有 Repository/播放上下文就跑不起来
    val repo = musicRepository ?: run {
        Logger.w("Agent.Master") { "[Master] musicRepository null, skip startHello" }
        return null
    }
    
    // ③ 构造 AgentContextBudget(128K) + ToolRegistryView（Hello 不需要任何工具，用 ToolRegistryView.empty()）
    val helloTransport = enrichConfig  // Hello 和 Radio/Enrich 共用 enrichConfig 端点
        ?: chatTransport
    
    val toolView = ToolRegistryView.empty(registry)
    val stopSignal = SchedulerStopSignal(tokenCounter)
    val helloSystemPrompt = DefaultCompanionProfiles.toSystemPrompt(agentPolicyConfig)
        ?: "你是 HMP 音乐播放器的 AI 伙伴"
    
    // ④ 实例化 HelloSubAgent → _subAgents["hello"]
    val helloAgent = HelloSubAgent(
        agentId = "hello",
        contextBudget = AgentContextBudget(128_000, helloTransport),
        toolRegistryView = toolView,
        systemPrompt = helloSystemPrompt,
        musicRepository = repo,
        presenceBus = chatPresenceBus,
        nowPlayingProvider = nowPlayingProvider,
        stopSignal = stopSignal,
        enrichConfig = enrichConfig,
    )
    _subAgents["hello"] = helloAgent
    
    // ⑤ 注册 Scheduler
    scheduler.registerAgent(
        AgentRegistration(
            agentId = "hello",
            priority = AgentPriority.HELLO,
            tokenUsagePerMin = 500L,  // Hello token 消耗极低（每分钟 tick 不调 LLM）
            onPause = { stopSignal.onSchedulerPaused() },
            onResume = { stopSignal.onSchedulerResumed() },
        )
    )
    
    // ⑥ 启动 runLoop
    scope.launch { helloAgent.runLoop() }
    
    Logger.i("Agent.Master") { "[Master] HelloSubAgent created" }
    return helloAgent
}

suspend fun stopHello() {
    (_subAgents["hello"] as? HelloSubAgent)?.shutdown()
    scheduler.unregisterAgent("hello")
    _subAgents.remove("hello")
}

// MasterAgent.initialize() 末尾：
//   musicRepository 不为 null → 自动 startHello()
//   musicRepository 为 null → startHello() 内部自己 return null，initialize 继续
```

#### SubAgent.runLoop 设计

SubAgent 基类要求实现 `abstract suspend fun runLoop()`。Enrich/Radio 的 runLoop 是 batch loop（while + delay + 处理）。HelloSubAgent 不做 batch loop，**runLoop 只做暂停/恢复信号等待 + 优雅退出**，三个工作协程在 runLoop 内 launch。

```kotlin
class HelloSubAgent(...) : SubAgent(agentId, contextBudget, toolRegistryView) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override suspend fun runLoop() {
        isActive = true
        runState = AgentRunState.RUNNING
        
        // ① 启动三个工作协程
        val presenceJob = scope.launch { collectPresenceEvents() }   // DjBlank + AgentProgress
        val dailyJob = scope.launch { dailyRefreshLoop() }
        val tickJob = scope.launch { minuteTickLoop() }
        
        // ② 从 DAO 恢复上次的卡（SharedFlow 丢的 DjBlank 我们主动补偿）
        initializeFromDao()
        
        // ③ 立即 push 常驻卡（正在听）—— 不等任何事件到来
        initializeAnchorCards()
        
        // ④ runLoop 自身只负责暂停/恢复 + 优雅退出
        while (scope.isActive && isActive) {
            stopSignal?.waitResume()
            if (stopSignal?.shouldSoftStop() == true) break
            delay(500)
        }
        
        // ⑤ 清理
        presenceJob.cancel()
        dailyJob.cancel()
        tickJob.cancel()
        runState = AgentRunState.PAUSED
    }
}
```

**SharedFlow 丢事件问题的解法**：PresenceBus.events 是 SharedFlow，不重放历史。如果 HelloSubAgent 晚于 DjBlank emit 才启动，之前的 DjBlank 会丢。解法是**两个主动补偿**：

- runLoop 启动时从 DAO 恢复上次生成的卡（RECOMMEND / DISCOVER / FORGOTTEN / ANNIVERSARY），让 HomeScreen 一进来就有内容

- runLoop 启动时主动 push 一张初始问候卡（从 MasterAgent.fallbackGreetings 取一条），不依赖 DjBlank 到来

**暂停/恢复**：Scheduler pause → StopSignal挂起 → runLoop while 循环挂起 → 三个协程继续活着但卡片池不再新 push。恢复时 → stopSignal.resume → runLoop 继续 while 循环。

***

#### 3. 三个工作协程详细设计

#### #1 PresenceBus 事件收集（`collectPresenceEvents`）

```kotlin
// 一个协程处理 PresenceBus 上所有 Hello 关心的事件
presenceBus.events.collect { event ->
    when (event) {
        is PresenceEvent.DjBlank -> {
            // DjBlank 每次切歌触发（RadioSubAgent.onTrackChanged emit；
            // MasterAgent 也可能在非电台场景手动 emit）
            val greeting = generateGreeting()
            cardPool.replace(SlideType.GREETING, SlideCard(GREETING, greeting, 10_000))
        }
        is PresenceEvent.AgentProgress -> {
            if (event.agentId != "radio") return@collect
            if (event.total == 0) {
                // Radio 停止 → pop 电台状态卡
                cardPool.popByType(SlideType.RADIO_STATUS)
            } else {
                // Radio 运行中 → replace 电台状态卡
                val nextTrack = MasterAgent.queryRadioPlaylist().firstOrNull()
                val status = RadioStatusContent(
                    targetCount = event.total,
                    nextTrackName = nextTrack?.title,
                )
                cardPool.replace(SlideType.RADIO_STATUS, SlideCard(RADIO_STATUS, status, 0))
            }
        }
        // 其他事件忽略
        else -> {}
    }
}
```

#### #2 每日凌晨定时（`dailyRefreshLoop`）

```kotlin
while (isActive) {
    // ① 补跑守卫：先查 "今天的 DAO 有没有" → 没有立即跑一次
    val today = LocalDate.now().toString()  // yyyy-MM-dd
    val hasTodayCards = dao.getLatestOfAnyDate("RECOMMEND")?.generatedForDate == today
    if (!hasTodayCards) {
        runCatching { dailyRefreshOnce() }
    }
    
    // ② 睡到明天凌晨
    val now = LocalDateTime.now()
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
    val delayMs = nextMidnight.toEpochMillis(ZoneOffset.UTC) - 
                  now.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
    delay(delayMs.coerceAtLeast(0L))
}
```

`dailyRefreshOnce()` 逻辑：

```kotlin
suspend fun dailyRefreshOnce() {
    // ① 批量生成推荐卡 + 探索卡（RECOMMEND / DISCOVER）
    val recommend = generateRecommendCards(count = dailyRecommendCount)
    val discover = generateDiscoverCards()
    (recommend + discover).forEach { cardPool.replace(it.type, it) }
    
    // ② 检查遗忘唤醒（FORGOTTEN）
    val forgotten = checkForgotten()
    if (forgotten != null) cardPool.replace(SlideType.FORGOTTEN, forgotten)
    else cardPool.popByType(SlideType.FORGOTTEN)
    
    // ③ 检查纪念日（ANNIVERSARY）
    val anniversary = checkAnniversary()
    if (anniversary != null) cardPool.replace(SlideType.ANNIVERSARY, anniversary)
    else cardPool.popByType(SlideType.ANNIVERSARY)
    
    // ④ 写入 DAO（供 P5 报告页 + 下次启动快速恢复）
    val today = LocalDate.now().toString()
    (recommend + discover + listOfNotNull(forgotten)).forEach { card ->
        dao.insert(HelloCardCache(
            cardType = card.type.name,
            cardContentJson = json.encodeToString(card.content),
            generatedAt = System.currentTimeMillis(),
            generatedForDate = today,
        ))
    }
}
```

#### #3 每分钟 tick（`minuteTickLoop`）

```kotlin
var lastPhase: TimePhase? = null

while (isActive) {
    delay(60_000)  // 每分钟
    
    // ① 检查时段变化
    val currentPhase = detectTimePhase()
    if (currentPhase != lastPhase) {
        lastPhase = currentPhase
        val phaseRecommend = generateRecommendCards(phase = currentPhase, count = dailyRecommendCount)
        phaseRecommend.forEach { cardPool.replace(it.type, it) }
    }
    
    // ② 刷新正在听锚定卡（playlist queue 可能有变化）
    refreshAnchorCard()
}
```

`detectTimePhase()` 逻辑：

```
当前小时  →  时段
0-6      →  NIGHT
7-9      →  MORNING_COMMUTE
9-12     →  WORK
12-14    →  LUNCH
14-18    →  WORK
18-20    →  EVENING_COMMUTE
20-23    →  EVENING_LEISURE
```

***

#### 4. 卡片池状态管理

#### 数据结构

```kotlin
class CardPool {
    private val _cards = MutableStateFlow<List<SlideCard>>(emptyList())
    val cards: StateFlow<List<SlideCard>> = _cards.asStateFlow()
    
    private val timers = mutableMapOf<String, CountDownTimer>()  // cardId → timer
    private val pausedCards = mutableSetOf<String>()              // 正在展开（暂停倒计时）的卡
}
```

#### 核心操作

```kotlin
/** 同类型只保留一张：先 pop 同类型旧卡 + 停它的 timer，再 push 新卡 */
fun replace(type: SlideType, card: SlideCard) {
    popByType(type)
    push(card)
}

/** push 到栈顶（列表头），同时启动倒计时 */
fun push(card: SlideCard) {
    _cards.update { listOf(card) + it }
    if (card.displayDurationMs > 0) {
        startTimer(card)
    }
}

/** 按类型 pop（replace 用 + Radio 停止时 popByType(RADIO_STATUS)） */
fun popByType(type: SlideType) {
    val removed = _cards.value.filter { it.type == type }
    removed.forEach { stopTimer(it.cardId) }
    _cards.update { it.filter { c -> c.type != type } }
}

/** 按 cardId pop（CountDownTimer 到期时调用） */
fun pop(cardId: String) {
    stopTimer(cardId)
    _cards.update { it.filter { c -> c.cardId != cardId } }
}

/** 卡展开时暂停倒计时，收起时恢复 */
fun pauseTimer(cardId: String) { ... }
fun resumeTimer(cardId: String) { ... }
```

**常驻卡**（displayDurationMs = 0）：不创建 timer。RunLoop 启动时 push ANCHOR；Radio 已在跑时 push RADIO\_STATUS。这两张永不消失（除非 Radio 停止 pop RADIO\_STATUS）。

**初始卡片**（runLoop 启动时立即 push）：

1. ANCHOR（正在听）——从 `nowPlayingProvider.getNowPlaying()` 读当前播放曲目
2. RADIO\_STATUS（如果 `MasterAgent.queryRadioState() != IDLE`）
3. RECOMMEND / DISCOVER / FORGOTTEN / ANNIVERSARY ——从 DAO 恢复（如果有今天之前生成的）
4. GREETING ——初始一张兜底问候（MasterAgent.fallbackGreetings 取一条）

***

#### 5. SlideCard 与 SlideContent 完整定义

```kotlin
// 卡的类型枚举
enum class SlideType {
    ANCHOR,         // 正在听（常驻）
    RADIO_STATUS,   // 电台运行态（0=常驻，Radio 停止时 pop）
    GREETING,       // 问候 + DJ 衔接语（统一 4s 轮播，原设计 10s 已关闭）
    RECOMMEND,      // 可解释推荐（统一 4s 轮播，原设计 15s 已关闭）
    DISCOVER,       // 歌手/风格探索（统一 4s 轮播，原设计 12s 已关闭）
    FORGOTTEN,      // 遗忘唤醒（统一 4s 轮播，原设计 12s 已关闭）
    ANNIVERSARY,    // 纪念日（统一 4s 轮播，原设计 15s 已关闭）
}

// 每种卡的内容 sealed interface
sealed interface SlideContent

data class AnchorContent(
    val trackTitle: String?,
    val artistName: String?,
    val bpm: Int?,
    val phase: TimePhase?,      // 当前时段（深夜/通勤等）
) : SlideContent

data class RadioStatusContent(
    val targetCount: Int,          // 备选曲目总数
    val nextTrackName: String?,    // 下一首曲目名
) : SlideContent

data class GreetingContent(
    val text: String,              // 问候语文本
    val fromFallback: Boolean,     // true = LLM 失败用了兜底
    val currentTrack: String?,     // 刚播完的曲目（用于 DJ 衔接）
) : SlideContent

data class RecommendContent(
    val trackId: Long,
    val trackTitle: String,
    val reason: String,            // LLM 生成的可解释理由
    val currentPhase: TimePhase,   // 对应哪个时段
) : SlideContent

data class DiscoverContent(
    val target: String,            // 歌手名 / 风格名
    val reason: String,            // LLM 生成的引导语
    val trackIds: List<Long>,      // 探索目标下的曲目（点击可播放）
) : SlideContent

data class ForgottenContent(
    val trackId: Long,
    val trackTitle: String,
    val daysSince: Int,            // 30 / 90
    val playCount: Int,            // 当年循环次数
) : SlideContent

data class AnniversaryContent(
    val trackId: Long,
    val trackTitle: String,
    val yearsAgo: Int,             // N 年前
    val totalPlays: Int,           // 首次播放后总共循环次数
) : SlideContent

// SlideCard 主数据类
data class SlideCard(
    val cardId: String,           // UUID，同类型卡唯一标识
    val type: SlideType,
    val content: SlideContent,
    val displayDurationMs: Long,   // 0 = 常驻
) {
    companion object {
        fun newId() = UUID.randomUUID().toString()
    }
}
```

***

#### 6. 七种卡型生成逻辑

#### ANCHOR（正在听，常驻）

- **来源**：`nowPlayingProvider.getNowPlaying()` → 读当前播放曲目

- **内容**：`AnchorContent(trackTitle, artistName, bpm, phase)`

- **刷新**：`minuteTickLoop` 每分钟刷新一次

#### RADIO\_STATUS（电台运行态，0=常驻）

- **来源**：RadioSubAgent emit `AgentProgress(agentId="radio", ...)` → `collectPresenceEvents` 处理

- **内容**：`RadioStatusContent(targetCount, nextTrackName)`

- **生命周期**：Radio 启动 → replace；Radio 停止（`total==0`）→ pop

#### GREETING（问候 + DJ 衔接语，10s）

- **触发**：DjBlank 每次切歌

- **生成**：

  1. LLM 生成自然语言问候（人格预设 + 当前时段 + 刚播完的曲目名）
  2. LLM 失败 → `MasterAgent.fallbackGreetings` 随机取一条

- **兜底**：无 LLM + 无 fallback → 硬编码「嗨，继续听歌？」

#### RECOMMEND（可解释推荐，统一 4s 轮播）

- **触发**：每日凌晨 + 时段切换

- **生成**：

  1. `musicRepository.getRecentSkipRate(days=7, limit=5)` → 反推不该推荐什么
  2. `musicRepository.getRecentFavorRate(days=7, limit=5)` → 正推该推荐什么
  3. 结合当前时段（`detectTimePhase()`）
  4. LLM 选一首 + 写一句可解释理由（15-20 字）

- **兜底**：无 LLM → `musicRepository.getRecentTracksByLabel(labelForPhase, limit=1)`

#### DISCOVER（歌手/风格探索，12s）

- **触发**：每日凌晨

- **生成**：

  1. `musicRepository.getGlobalTopLabels(limit=5)` → 选一个最近 30 天没听过的 label
  2. `musicRepository.getRecentTracksByLabel(label, limit=3)` → 取曲目
  3. LLM 写一句引导探索的话

- **兜底**：无 LLM → random 一个 label + 固定文案「你好像很久没听 X 了」

#### FORGOTTEN（遗忘唤醒，统一 4s 轮播）

- **触发**：每日凌晨扫历史

- **生成**：

  1. `musicRepository.getForgottenTracks(days=30)` → 有则用 30 天未播的；没有 `getForgottenTracks(days=90)`
  2. 有结果 → `SlideCard.replace(FORGOTTEN, ...)`；没有 → `popByType(FORGOTTEN)`
  3. 内容：`ForgottenContent(trackId, trackTitle, daysSince=30/90, playCount)`

- **兜底**：不需要 LLM

#### ANNIVERSARY（纪念日，15s）

- **触发**：每日凌晨扫历史

- **生成**：

  1. `musicRepository.getAnniversaryTracks(today=LocalDate.now())`
  2. 有结果 → `SlideCard.replace(ANNIVERSARY, ...)`；没有 → `popByType(ANNIVERSARY)`
  3. 内容：`AnniversaryContent(trackId, trackTitle, yearsAgo, totalPlays)`

- **兜底**：不需要 LLM

***

#### 7. 报告叙事段生成（P5 用）

#### 生成时机

```
用户日均听歌时长        →  报告叙事更新频率
─────────────────────────────────────────
≤ 30 分钟              →  周更新（低活跃，内容不够每日一更）
30 分钟 ~ 2 小时        →  日更新（正常活跃度）
≥ 2 小时               →  日更新（高活跃，内容多值得每日更新）
```

**自适应判断**：每天凌晨 `dailyRefreshOnce()` 末尾计算 `musicRepository.getAvgDailyListeningMinutes(days=30)` → 写入 DAO 的 `avgDailyMinutes` 字段。下次定时生成时读 DAO 判断频率。

#### 五个时间维度

| 维度 | 生成时机         | 条件                    |
| -- | ------------ | --------------------- |
| 全部 | 自适应频率（见上）    | DAO 有缓存直接显示，过期后台异步重生成 |
| 日  | 每天凌晨         | 当天有播放数据               |
| 周  | 每周一凌晨        | 上周有数据                 |
| 月  | 每月 1 号凌晨     | 上月有数据                 |
| 年  | 每年 1 月 1 号凌晨 | 上年有数据                 |

#### MasterAgent 对外接口（手动触发）

```kotlin
// P5 🔄 重新生成按钮调用
suspend fun regenerateReportNarrative(timeRange: NarrativeTimeRange): HelloReportNarrative
```

#### DAO 缓存策略

- 每次生成写入 DAO，带 `generatedAt` + `timeRange` + `avgDailyMinutes`（当时的日均，用于下次自适应判断）

- P5 报告页直接读 DAO 显示，不阻塞 UI

- DAO 过期（超过生成频率 × 2）时 → 后台异步触发重新生成，不打断用户看旧数据

***

#### 8. DAO 设计

#### 推荐卡缓存表

```kotlin
@Entity(tableName = "hello_card_cache")
data class HelloCardCache(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardType: String,           // RECOMMEND / DISCOVER / FORGOTTEN / ANNIVERSARY
    val cardContentJson: String,    // SlideContent 的 JSON 序列化（kotlinx.serialization）
    val generatedAt: Long,           // 时间戳
    val generatedForDate: String,    // yyyy-MM-dd，告诉 UI 这张卡是为哪一天生成的
)

@Dao
interface HelloCardCacheDao {
    @Query("SELECT * FROM hello_card_cache WHERE cardType = :type ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatestOfAnyDate(type: String): HelloCardCache?
    
    @Query("SELECT * FROM hello_card_cache WHERE cardType = :type AND generatedForDate = :date LIMIT 1")
    suspend fun getLatest(type: String, date: String): HelloCardCache?
    
    @Insert
    suspend fun insert(cache: HelloCardCache): Long
    
    @Query("DELETE FROM hello_card_cache WHERE cardType = :type")
    suspend fun deleteByType(type: String)
}
```

#### 报告叙事段表

```kotlin
@Entity(tableName = "hello_report_narrative")
data class HelloReportNarrative(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeRange: String,          // ALL / DAY / WEEK / MONTH / YEAR
    val narrative: String,          // 叙事段纯文本（后续可扩展为富文本 JSON）
    val generatedAt: Long,
    val avgDailyMinutes: Float?,    // 生成当时的日均听歌时长（用于下次自适应判断）
)

@Dao
interface HelloReportNarrativeDao {
    @Query("SELECT * FROM hello_report_narrative WHERE timeRange = :range ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatest(range: String): HelloReportNarrative?
    
    @Insert
    suspend fun insert(narrative: HelloReportNarrative): Long
    
    @Query("DELETE FROM hello_report_narrative WHERE timeRange = :range")
    suspend fun deleteByRange(range: String)
}
```

#### Room 迁移

AppDatabase 当前版本 3（`AppDatabase.kt L34`）。新增两张表 → 版本 4。

```kotlin
@Database(
    entities = [
        // ... 原有 entities ...,
        HelloCardCache::class,        // 新增
        HelloReportNarrative::class,   // 新增
    ],
    version = 4,
)
abstract class AppDatabase : RoomDatabase() {
    // Migration 3 → 4：新增两张表
    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS hello_card_cache (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    cardType TEXT NOT NULL,
                    cardContentJson TEXT NOT NULL,
                    generatedAt INTEGER NOT NULL,
                    generatedForDate TEXT NOT NULL
                )""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS hello_report_narrative (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timeRange TEXT NOT NULL,
                    narrative TEXT NOT NULL,
                    generatedAt INTEGER NOT NULL,
                    avgDailyMinutes REAL
                )""")
            }
        }
    }
}
```

或者开发期直接用 `fallbackToDestructiveMigration()`。正式发布用 Migration。

***

#### 9. MusicRepository 方法缺口（完整签名）

HelloSubAgent 需要以下 MusicRepository 方法——**当前不存在**，需补充到 `MusicRepository` 接口 + 各平台实现：

```kotlin
interface MusicRepository {
    // ... 现有方法 ...
    
    // ═══ HelloSubAgent 新增依赖 ═══
    
    /** 7 天内跳过率最高的 N 首歌 ID（用于 RECOMMEND 反推不该推荐什么） */
    suspend fun getRecentSkipRate(limit: Int, days: Int = 7): List<Long>
    
    /** 7 天内收藏率最高的 N 首歌 ID（用于 RECOMMEND 正推该推荐什么） */
    suspend fun getRecentFavorRate(limit: Int, days: Int = 7): List<Long>
    
    /** days 天内未播放的曲目 ID（30 / 90 天未播 → FORGOTTEN 卡） */
    suspend fun getForgottenTracks(days: Int): List<Long>
    
    /** N 年前的今天首次播放的曲目 ID（→ ANNIVERSARY 卡） */
    suspend fun getAnniversaryTracks(today: LocalDate): List<Long>
    
    /** 最近 days 天的日均听歌时长（分钟），用于 NARRATIVE 自适应频率判断 */
    suspend fun getAvgDailyListeningMinutes(days: Int = 30): Float
    
    /** 某风格 label 下最近播放过的 N 首歌 ID（DISCOVER 兜底 + RECOMMEND 时段匹配） */
    suspend fun getRecentTracksByLabel(label: LabelName, limit: Int): List<Long>
}
```

**RadioSubAgent stub 里已存在但标记为 TODO 的方法**（HelloSubAgent 可以复用 stub 或推动正式实现）：

```kotlin
// RadioSubAgent.kt L438-454
private suspend fun MusicRepository.getGlobalTopLabels(limit: Int): List<LabelName>  // stub → 返回 emptyList
private suspend fun MusicRepository.getMusicInfoByIds(ids: List<Long>): List<MusicInfo>  // stub → 全量过滤
```

HelloSubAgent DISCOVER 卡的兜底场景依赖 `getGlobalTopLabels`，所有卡型生成后查详情依赖 `getMusicInfoByIds`。建议把这两个从 `private` 提升为 `public` 接口方法，跟 HelloSubAgent 新增方法一起实现。

***

#### 10. 兜底策略

#### LLM 不可用时

| 卡型          | LLM 失败兜底                                                |
| ----------- | ------------------------------------------------------- |
| GREETING    | MasterAgent.fallbackGreetings 随机取一条 → 再失败硬编码「嗨，继续听歌？」   |
| RECOMMEND   | `getRecentTracksByLabel(labelForCurrentPhase, limit=1)` |
| DISCOVER    | random 一个 label + 固定文案「你好像很久没听 X 了」                     |
| FORGOTTEN   | 不需要 LLM，纯数据拼接                                           |
| ANNIVERSARY | 不需要 LLM，纯数据拼接                                           |
| NARRATIVE   | 不需要 LLM（首次实现纯统计数据，后续加 LLM 叙事生成）                         |

#### 曲库为空（`getAllMusicInfoAsList().isEmpty()`）

```kotlin
// HelloSubAgent.runLoop 里检测 → 跳过 dailyRefreshOnce() 和 minuteTickLoop 的业务逻辑
// 卡片池只保留 ANCHOR（null 也 push 一个空状态：「还没听歌，去扫扫描吧」）
// collectPresenceEvents 继续跑但 DjBlank / AgentProgress 不会有新事件
// RADIO_STATUS 永远不会有（Radio 无法启动）
```

#### 零播放历史（刚装 App 第一次启动）

```kotlin
// FORGOTTEN / ANNIVERSARY / RECOMMEND / DISCOVER 全部返回 null → 不入池
// 卡片池只有 ANCHOR + 一张初始兜底 GREETING
// DAO 为空 → initializeFromDao() 什么都不做
// dailyRefreshLoop 首次跑 → 也生成不出任何卡 → 但不会崩（runCatching 包住了）
// minuteTickLoop → detectTimePhase 正常 → 但 getRecentTracksByLabel 返回空 → RECOMMEND 不入池
// NARRATIVE 自适应判断日均 = 0 → 周更新频率 → 但第一次不生成任何内容
```

***

#### 11. 实现分期

| 阶段                   | 内容                                                                                                                                                                                                             | 预估改动                                                                                                                              |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| **H1 骨架**            | 前置 #1-3（Scheduler 加 HELLO 枚举 + Radio emit AgentProgress + MasterAgent 持有 PresenceBus）+ HelloSubAgent 类 + runLoop 骨架 + CardPool + SlideCard/SlideContent 完整模型 + startHello/stopHello 接线 + DAO 表实体 + Room 迁移 3→4 | 改：AgentScheduler.kt + RadioSubAgent.kt + MasterAgent.kt + AppDatabase.kt；新：HelloSubAgent.kt + CardPool.kt + SlideCard.kt + DAO 文件 |
| **H2 非 LLM 卡 + DAO** | `getForgottenTracks` + `getAnniversaryTracks` + `getRecentTracksByLabel` 实现；collectPresenceEvents + dailyRefreshLoop + minuteTickLoop 完整实现（FORGOTTEN / ANNIVERSARY / RECOMMEND 兜底版本）                           | MusicRepository 补 #3/#4/#6 + 推动 RadioSubAgent 两个 stub 正式实现                                                                        |
| **H3 LLM 卡**         | RECOMMEND + DISCOVER + GREETING 的 LLM 生成逻辑 + 所有卡类型的可解释理由                                                                                                                                                       | MusicRepository 补 #1/#2                                                                                                           |
| **H4 报告叙事段**         | `getAvgDailyListeningMinutes` + 自适应频率判断 + 五个时间维度生成 + DAO 缓存 + `regenerateReportNarrative` 对外接口                                                                                                                 | MusicRepository 补 #5                                                                                                              |
| **H5 集成验证**          | MasterAgent.initialize() 末尾自动 startHello()；三端编译 + desktopTest                                                                                                                                                  | <br />                                                                                                                            |

***

#### 附：HelloSubAgent 对外接口汇总

```kotlin
class HelloSubAgent(...) : SubAgent(...) {
    
    // === MasterAgent 调 ===
    // 生命周期由 MasterAgent.startHello() / stopHello() 外部管理
    override suspend fun pause()
    override suspend fun resume()
    override suspend fun shutdown()
    
    // === UI / MasterAgent collect ===
    val cards: StateFlow<List<SlideCard>>                          // HomeScreen 动态堆叠卡 collect
    val recommendSonglist: StateFlow<List<Long>>                   // HomeScreen 今日推荐歌单 collect
    fun getReportNarrative(timeRange: NarrativeTimeRange): HelloReportNarrative?  // P5 报告页读 DAO
    
    // === P5 🔄 重新生成按钮 ===
    suspend fun regenerateReportNarrative(timeRange: NarrativeTimeRange): HelloReportNarrative
    
    // === 引擎内部（internal 可见性）===
    internal fun handleDjBlank()                                  // MasterAgent 非电台场景手动调
    internal fun handleRadioStateChange(state: RadioState, nextTrackName: String?)  // RadioSubAgent 直接调
}
```



***

## W1 · 页面级改造

> **依赖**：T + M6 + V + **W0**（引擎侧 HelloSubAgent 完整实现）。
> **核心工作**：W0 把 HelloSubAgent 和 Radio/Enrich 的 emit 事件补齐，W1 把这些事件接到**页面级** UI 消费点。
>
> **W0 与 W1 的边界（顺序不可调换）**：
>
> - **W0 完成时**：引擎侧 `cards: StateFlow<List<SlideCard>>` + `presenceBus.events` 全部就绪，UI 拿到的是可 collect 的 StateFlow
> - **W1 完成时**：HomeScreen / P2 / P3 / P4 / P5 五个页面全部 collect 上这些 StateFlow，用户看到完整的 Agent 交互体验
> - **没有 W0 就没有 W1**：W1 所有 UI 改造依赖的数据源头都在 W0，不能独立推进

#### W1 · 页面级改造清单（5 个）

| #  | 类型      | 页面                                  | 路由                              | 当前形态                                                     |
| -- | ------- | ----------------------------------- | ------------------------------- | -------------------------------------------------------- |
| P1 | 🔴 整体改造 | HomeScreen → Agent 主交互页             | `Routes.Main.Home`              | 静态问候 + hero + 心动歌单                                       |
| P2 | 🔴 整体改造 | AIScreen → 伙伴设置页                    | `Routes.AI.AI`                  | AI 服务配置三 tab + LoadMusicExtraInfo + DailyRefreshSettings |
| P3 | 🟠 重大改造 | ChatScreen 对话页增强                    | `Routes.Companion.Chat`         | 五类气泡就绪                                                   |
| P4 | 🟠 整体升级 | AuditLogScreen → AgentMonitorScreen | `Routes.Settings.AgentMonitor`  | 280 行只读 ToolExecutionRecord 列表                           |
| P5 | 🟠 整体升级 | UserUsageDataScreen → 听歌报告页         | `Routes.UserData.UserUsageData` | 纯数字图表（Overview + Taste + Ranking + PieChart）             |

> **[W1 进度快照 2026-09-14]** P1 [已完成]（未提交 diff）；P3 [待增强]（基础落地，`ConfirmMatrixCard` 仍在对话流内未迁原生 Dialog、无 `TextDelta` 打字机）；P2 [待增强]（基础版已落地，五分区演进归 M7-T2）；P4 [未落地]（仍为 `AuditLogScreen`，依赖 W0#4 引擎统一运行态接口 `enrichStatusSummary()`）。区域③ 三目的地页面 = P3 对话 / P2 配置 / P4 看板。收尾序列：P3 → P2 → P4 → M7 报告 + 语音。详见 `../taskbook/README.md` §3 W 阶段进度快照。

***

#### P1 · HomeScreen → Agent 主交互页

#### 主区域

三个区域自上而下：

| # | 区域       | 形态                   | 内容                             | 备注                                  |
| - | -------- | -------------------- | ------------------------------ | ----------------------------------- |
| ① | 动态信息展示区  | 堆叠卡组件（VerticalPager 轮播） | HelloSubAgent 管理卡片生命周期         | 竖屏 3:2 卡片；月度叙事卡融入此处；上下轮播、所有卡同时可见（缩放/透明渐隐）+ 右侧指示点           |
| ② | 电台 + 推荐区 | 左 1:1 收音机卡 + 右侧两行歌单卡 | 📻收音机（开关）+ 🎵今日推荐歌单 + ❤️最近收藏歌单 | 收音机 1:1 正方形独立开关；右侧歌单卡（点击播放 · 箭头进详情） |
| ③ | 功能入口区    | 横向等宽卡片 Row           | 💬对话 / ⚙️配置 / 📊看板             | 固定高度                                |

**跨区域融入**：最近收藏（→ 区域②右下歌单卡）、月度叙事卡（→ 动态堆叠）
**砍掉的独立区域**：认识进度（无需独立展示）、独立问候卡 / 正在听卡（→ 动态堆叠）

#### 区域① · 动态信息展示区

**形态**：竖向 `VerticalPager` 上下轮播（2026-09-14 G4 关闭：维持 VerticalPager，原 z-axis overlay 堆叠形态作废）。所有卡同时可见（缩放 / 透明渐隐）+ 右侧指示点。HelloSubAgent 管理卡片生命周期（push / pop 决定卡池成员），UI 渲染 `VerticalPager`（`RotatingPersistentCards`）。

```
        ┌──────────────────────────────┐
top →   │ 💬 问候（10s，可展开暂停）      │ ← DjBlank → push 栈顶，覆盖常驻卡
        │ "深夜了，来一首慢慢的？"        │    到时 pop → 露出下一张
        ├──────────────────────────────┤
        │ 🎧 正在听（常驻）              │ ← duration=0，DjBlank 时被临时覆盖
        │ "深夜 23:15 · BPM 85"         │    问候卡退场后自动回到栈顶
        │ 🔽 为什么                     │
        ├──────────────────────────────┤
        │ 📻 电台运行中（常驻）          │ ← Radio 运行时在正在听下方
        │ "12 首备选 · 下首：搁浅"       │    Radio 停止 → pop
        ├──────────────────────────────┤
        │ 🎵 深夜适合听（15s）           │ ← 每日凌晨 push
        │ "上周同时段跳过 3 首快歌"       │    到时 pop
        └──────────────────────────────┘
```

**卡片生命周期**：

| 卡型          | 显示时长     | 触发源                                                    |
| ----------- | -------- | ------------------------------------------------------ |
| 正在听锚定卡      | 0（常驻）    | `PlaylistQueueViewModel.cards` 变化时刷新内容                 |
| 电台运行态       | 0（运行时常驻） | `RadioSubAgent` emit `AgentProgress` → push；stop → pop |
| 问候 + DJ 衔接语 | 统一 4s   | DjBlank 切歌 → push（替换列表中已有旧问候卡）                         |
| 可解释推荐       | 统一 4s   | 每日凌晨批量生成                                               |
| 歌手/风格探索     | 统一 4s   | 每日凌晨批量生成                                               |
| 遗忘唤醒        | 统一 4s   | 查 30/90 天未播曲目 → 有则 push                                |
| 纪念日         | 统一 4s   | 每日凌晨扫历史 → 有则 push                                      |

> ⚠️ **2026-09-14 G3 关闭**：原设计分卡时长（GREETING 10s / RECOMMEND 15s / DISCOVER 12s / FORGOTTEN 12s / ANNIVERSARY 15s）**不再实现**。所有卡统一 `AUTO_ROTATE_MS = 4000L` 全局轮播，不做按卡型差异化时长。`SlideType` 注释中的 10s/15s/12s 描述已同步回改为「统一 4s」（见本文 §W0）。

**可展开暂停**：展开状态下自动暂停倒计时，收起后恢复。展开后可看推荐理由、遗忘卡的循环次数、纪念日的详细统计。

**数据模型**：

```kotlin
data class SlideCard(
    val cardId: String,           // UUID，同类型卡唯一标识
    val type: SlideType,          // GREETING / RECOMMEND / DISCOVER / FORGOTTEN / ANNIVERSARY / RADIO_STATUS / ANCHOR
    val content: SlideContent,    // 各类型对应 content data class
    val displayDurationMs: Long,  // 0 = 常驻
)
```

**HelloSubAgent 管理规则**：

* 栈内同类型卡永远只保留一张：新卡用 `replace(type, card)` 替换旧卡，非同类型用 `push(card)` 追加

* DjBlank 切歌 → `replace(GREETING, 问候卡)`（替换已有旧问候卡）

* `RadioSubAgent` emit `AgentProgress` → `replace(RADIO_STATUS, 电台状态卡)`；stop → `pop(RADIO_STATUS)`

* 每日凌晨 → `replace(RECOMMEND/DISCOVER/ANNIVERSARY, 新卡)` 批量重算

* 每分钟 tick → 检查时段变化 → `replace(RECOMMEND, 适合当前时段的新卡)`

* 卡片到时 → `CountDownTimer` 触发 `pop(cardId)`（⚠️ G3 关闭后此计时已不再是分卡时长；非常驻卡的轮播由 `RotatingPersistentCards` 的 `AUTO_ROTATE_MS = 4000L` 统一驱动，卡池层不再按卡型维护独立倒计时）

#### 区域② · 电台 + 推荐区

**布局**：

```
┌──────────────┬─────────────────────────┐
│              │  🎵 今日推荐              │
│  📻 收音机   │  [歌单卡 · 箭头]          │
│  🔘 ON/OFF   │                         │
│  （1:1 卡）   ├─────────────────────────┤
│              │  ❤️ 最近收藏              │
│              │  [歌单卡 · 箭头]          │
└──────────────┴─────────────────────────┘
```

**交互**：

| 元素      | 行为                                                                                          |
| ------- | ------------------------------------------------------------------------------------------- |
| 收音机开关   | OFF → ON 调 `startRadio()`；ON → OFF 调 `stopRadio()`；运行态时旁显示「自动续歌中 · 下首：{nextTrackName}」（动态值） |
| 今日推荐歌单卡 | 点击 → 播放整个歌单；箭头 → 歌单详情页                                                                      |
| 最近收藏歌单卡 | 点击 → 播放整个歌单；箭头 → 歌单详情页                                                                      |

**数据源**：

| 子块   | 数据源                                                      | 兜底                                    |
| ---- | -------------------------------------------------------- | ------------------------------------- |
| 收音机  | `MasterAgent.isRadioActive()` StateFlow                  | 恒有                                    |
| 今日推荐 | HelloSubAgent 每日生成 → 存 DAO                               | DAO 为空时旧 `RecommendationEngine` 算 1 张 |
| 最近收藏 | `MusicRepository.getRecentFavorites(limit = 30)` → 聚合为歌单 | 收藏为空时隐藏此块                             |

#### 区域③ · 功能入口区

横排三张等宽卡片，与区域②歌单卡同视觉语言，固定高度。

| 卡片          | 路由                             | 目标页面                      |
| ----------- | ------------------------------ | ------------------------- |
| 💬 和伙伴聊聊    | `Routes.Companion.Chat`        | P3 ChatScreen 对话页         |
| ⚙️ Agent 配置 | `Routes.AI.AI`                 | P2 AIScreen 伙伴设置页         |
| 📊 Agent 看板 | `Routes.Settings.AgentMonitor` | P4 AgentMonitorScreen 监控页 |

***

#### P2 · AIScreen → 伙伴设置页

#### 整体结构

一个 SegmentedControl 五 Tab 切换：`[全局] [Master] [Hello] [Enrich] [Radio]`，每个 Tab 独立表单。

#### 配置项

| Tab                | 配置项                                                                                       |
| ------------------ | ----------------------------------------------------------------------------------------- |
| **全局**             | AI 接入方式（Free/Custom/Paid · 现有三 tab）、日 Token 配额滑杆、回复语言下拉、嗓音与耳朵（M7 gate）、清除所有推荐缓存           |
| **MasterAgent**    | 信任档位（三档+回拨）、alwaysAllow 重置、最大对话步数（3-15）、Temperature（0-2）                                  |
| **HelloSubAgent**  | 人格预设（知音/DJ/馆长）、健谈度/主动度/话题宽度滑杆、称呼习惯、每日推荐卡数量、问候卡时长、堆叠卡池上限、报告叙事生成频率（自动/每日/每周/每月）、Temperature |
| **EnrichSubAgent** | 信任档位（三档+回拨）、alwaysAllow 重置、批次大小、大歌手阈值、小歌手阈值、富化开关、Temperature                              |
| **RadioSubAgent**  | 信任档位（三档+回拨）、自动续歌开关、续歌模式（DJ\_BLANK/SILENT）、备选曲目数量、Temperature                              |

#### 移除的旧组件

| 组件                   | 原因                             | 迁移去向       |
| -------------------- | ------------------------------ | ---------- |
| LoadMusicExtraInfo   | 富化进度 / 控制全归 AgentMonitorScreen | P4 概览仪表板   |
| DailyRefreshSettings | HelloSubAgent 自行定时，无需用户配置      | Agent 引擎内部 |

***

#### P3 · ChatScreen 对话页增强

#### 改动清单

| # | 改动                              | 实现方向                                                                                                                                               |
| - | ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | 问候气泡                            | `ChatViewModel.init` 时自动插入 `TEXT` 消息，从 `MasterAgent.fallbackGreetings` 取                                                                           |
| 2 | Assistant 打字机                   | 引擎侧 `ReActLoop` emit `TextDelta` 流；UI 侧 Assistant 气泡 collect StateFlow 增量渲染 + **自动滚动联动**：气泡高度随 token 增加时自动滚到底部（throttle 60fps）；用户回看时暂停，滚回底部或发消息时恢复 |
| 3 | 回看暂停 auto-scroll                | 监听 `listState` 位置，不在底部时暂停自动滚；滚回底部或发消息时恢复                                                                                                           |
| 4 | `ConfirmMatrixCard` → 原生 Dialog | **从对话流分离**：确认请求弹阻塞式原生 Dialog，用户勾选提交后关闭；执行状态独立区域在对话气泡下方临时出现（进度 + 每项 ✓/...），执行完消失；回执并入 Assistant 打字机输出                                               |

#### Dialog 流程

```
Dialog 弹出：
  Assistant 执行到需确认的工具调用
    → Dialog 弹出（阻塞，必选）
    → 用户逐项勾选 + 点「照做」/「跳过全部」
    → Dialog 关闭 → 引擎开始执行

Dialog 内部结构：
  ┌──────────────────────────────┐
  │ 🔒 MasterAgent 请求执行      │
  │ 信任档位：建议                │
  ├──────────────────────────────┤
  │ ☐ 创建歌单「深夜专属」        │
  │ ☐ 将 5 首歌加入歌单            │
  │ ☐ 播放歌单                    │
  │                              │
  │ 信任档位下拉：[建议 ▼]        │
  │  ▸ 建议（每次确认）           │
  │  ▸ 代劳（自动执行，仍可中断）  │
  │  ▸ 静默（后台执行，不打扰）    │
  ├──────────────────────────────┤
  │         [跳过全部]  [照做]     │
  └──────────────────────────────┘

"跳过全部"语义：本次所有勾选项均跳过，不执行。
"信任档位下拉"：可当场提升/降低信任档位，影响后续该类工具是否需要确认。

执行状态提示（Dialog 关闭后出现，不阻塞）：
  ┌──────────────────────────┐
  │ Assistant 气泡（打字机输出） │
  ├──────────────────────────┤
  │ ● 正在执行 2/3 项...       │
  │   ✓ 创建歌单              │
  │   ... 播放中...           │
  │   ○ 待执行                │
  └──────────────────────────┘
  执行完 → 状态提示消失 → Assistant 继续打字机输出回执
```

#### 组件变更汇总

| 组件                | 之前             | 现在                        |
| ----------------- | -------------- | ------------------------- |
| ConfirmMatrixCard | 嵌在对话流底部的非模态悬浮卡 | 阻塞式原生 Dialog，与对话气泡完全分离    |
| 状态提示区域            | 不存在            | 独立区域，Dialog 关闭后临时出现，执行完消失 |

***

#### P4 · AgentMonitorScreen（原 AuditLogScreen）

#### 改名范围

| 项            | 之前                         | 现在                             |
| ------------ | -------------------------- | ------------------------------ |
| 路由           | `Routes.Settings.AuditLog` | `Routes.Settings.AgentMonitor` |
| Screen 文件    | `AuditLogScreen.kt`        | `AgentMonitorScreen.kt`        |
| ViewModel 文件 | `AuditLogViewModel.kt`     | `AgentMonitorViewModel.kt`     |
| Room 表       | `agent_audit_log`          | 不动                             |
| UI 标题        | "操作审计日志"                   | "Agent 监控"                     |

#### 整体结构

```
AgentMonitorScreen（SubScreen，标题 "Agent 监控"）

┌─────────────────────────────────┐
│ ① 概览仪表板（顶部固定）           │
│ ┌─ Token ──────┐ ┌─ Trust ─────┐ │
│ │ ▓▓▓▓▓░░░     │ │ Master: ACT │ │
│ │ 320K/500K 64%│ │ Enrich: SIL │ │
│ │ 熔断阈值 95%  │ │ Radio: SUG  │ │
│ └──────────────┘ └─────────────┘ │
│ ┌─ Agent 运行态 ────────────────┐ │
│ │ ● 电台运行中 · 自动续歌中       │ │
│ │ ● 富化已暂停 · 87%              │ │
│ └───────────────────────────────┘ │
├─────────────────────────────────┤
│ ② FilterRow（复用现有）            │
│ [全部] [电台] [跳过重排] [LLM]   │
│ [只看异常]  ← 新增                 │
├─────────────────────────────────┤
│ ③ 操作历史 LazyColumn（复用现有）   │
│ ┌──────────────────────────────┐ │
│ │ playlist.create ✓  [撤销]     │ │ ← 撤销按钮占位
│ │ radio.start ✓                │ │
│ │ radio.reorder ✓              │ │
│ └──────────────────────────────┘ │
└─────────────────────────────────┘
```

#### 概览仪表板数据源

| 模块              | 数据源                                                                 | 引擎侧改动                |
| --------------- | ------------------------------------------------------------------- | -------------------- |
| Token 消耗        | `GlobalTokenCounter.usedToday()` / `remainingToday()`               | 无                    |
| TrustLedger × 3 | `AgentPolicyConfig`（Master / Enrich / Radio）DataStore 读             | 无                    |
| Agent 运行态       | `MasterAgent.isRadioActive()` + `MasterAgent.enrichStatusSummary()` | 引擎侧统一运行态接口（见前置清单 #3） |

#### 撤销按钮

* 每条可撤销日志行右侧加 🔄 按钮 → STRONG\_CONFIRM 弹窗

* **边界判定**：检查 `argsHash`（已存在于 `AgentAuditLog.argsHash` 字段）与当前状态是否一致。不一致 → STRONG\_CONFIRM 追加文案"此操作之后数据已被修改，撤销可能造成歧义"

* **反向工具**：W 阶段不实现，用户确认后提示"反向撤销功能开发中"

#### "只看异常" Filter

* Filter 条件：`outcome IN (rejected, circuit_break, budget_exhausted)`

* 点击行展开显示 `reason` 字段（拒绝原因）

***

#### P5 · UserUsageDataScreen → 听歌报告页

#### 当前结构

```
UserUsageDataScreen
├── SegmentedControl（周/月/年）
├── OverviewCard（总时长/次数/歌曲数）
├── TasteCard（风格分布 Canvas）
├── RankingAndHistoryCard（排行 + 最近历史）
└── PlaySourcePieChart（播放来源饼图 Canvas）
```

#### 改造方向

```
SegmentedControl: [全部] [日] [周] [月] [年]   ← 「全部」默认选中

每个 tab 内容：
┌─────────────────────────────┐
│ 🤖 伙伴总结（Agent 叙事段）    │  ← 顶部新增，与 tab 时间范围联动
│ "这两年你深夜听了最多歌，周杰伦占 Top 3" │
│ 🔄 重新生成                  │
├─────────────────────────────┤
│ OverviewCard（时间范围内数据） │  ← 保留，数据范围与 tab 联动
├── TasteCard                 │
├── RankingAndHistoryCard     │
└── PlaySourcePieChart        │
```

#### 叙事段生成策略

**默认 HelloSubAgent 自适应**（用户可在 P2 HelloSubAgent Tab 覆盖）：

```
用户日均听歌时长        →  报告叙事更新频率
─────────────────────────────────────────
≤ 30 分钟              →  周更新（低活跃，内容不够每日一更）
30 分钟 ~ 2 小时        →  日更新（正常活跃度）
≥ 2 小时               →  日更新（高活跃，内容多值得每日更新）
```

**定时批量生成时机**：

| 维度 | 生成时机         | 条件                    |
| -- | ------------ | --------------------- |
| 全部 | 自适应频率更新      | DAO 有缓存直接显示，过期后台异步重生成 |
| 日  | 每天凌晨         | 当天有播放数据才生成            |
| 周  | 每周一凌晨        | 上周有数据才生成              |
| 月  | 每月 1 号凌晨     | 上月有数据才生成              |
| 年  | 每年 1 月 1 号凌晨 | 上年有数据才生成              |

**DAO 缓存**：每次生成写入 DAO，带 `generatedAt` + `timeRange`。报告页直接读 DAO 显示，不阻塞 UI；DAO 过期时**后台异步触发重新生成**（不打断用户看旧数据）。


***

## W2 · 组件级改造

#### W2 · 组件级改造清单（11 个）

#### 锚点层（agent.md §5.2，无引擎也可先行合入）

| #  | 组件                   | 当前状态                                                            | 引擎依赖                                       | W2 做什么                                                           |
| -- | -------------------- | --------------------------------------------------------------- | ------------------------------------------ | ---------------------------------------------------------------- |
| C1 | CompanionCapsule 徽标态 | 114 行骨架，只传了 onClick                                             | PresenceBus.CompanionBadge / AgentProgress | 加 BadgeOverlay composable + LaunchedEffect 收徽标状态 + 渲染（红点/脉冲圈/绿点） |
| C2 | AgentQuickSheet 轻量浮层 | 126 行骨架                                                         | ChatAgentGateway.pendingInput              | Esc/点外关闭 + CompanionCapsule.onLongPress 透传 + C 键唤起               |
| C3 | AgentNoticeBar 侧条    | 91 行骨架，showUndo 硬编码 false                                       | PresenceBus.NoticeAvailable + 反向工具         | showUndo 改 true + onUndo callback 接 ToolRegistry 反向工具            |
| C4 | 锚点手势矩阵               | CompanionCapsule detectTapGestures 声明了 onLongPress 但 AppRoot 没传 | —                                          | onTap / onLongPress / 播放页「对话」按钮 的完整手势映射表                         |
| C5 | C 键全局监听              | 没做                                                              | —                                          | AppRoot Scaffold onKeyEvent 拦截 C 键 → 唤起 AgentQuickSheet          |

#### 场景入口（agent.md §6 场景交互流 落点挂到既有页面）

| #   | 组件     | 挂载页面                                  | 当前状态                                                         | 引擎依赖                                                | W2 做什么                                                         |
| --- | ------ | ------------------------------------- | ------------------------------------------------------------ | --------------------------------------------------- | -------------------------------------------------------------- |
| C6  | 伙伴卡    | UserScreen                            | L256 profileCard → SettingsListCard，中间空着                     | CompanionProfile（未配置=引导态）                           | 在 profileCard 和 SettingsListCard 之间插伙伴卡（未配置=「去配置」→AIScreen P2） |
| C7  | 伙伴条带   | SearchScreen                          | L143-146 已有 `showIntentStrip` + `CommandLexicon.classify` 骨架 | ChatAgentGateway.pendingInput                       | 结果顶部两级漏斗：「交给伙伴」=输入作首条消息进会话 / 「只是搜索」=收起且本次会话同类输入不再弹             |
| C8  | 「对话」按钮 | PlayerScreen（PlaybackControlsButtons） | 主行三键 + 副行五键                                                  | CompanionCapsule 轻量浮层                               | 副行重排加「对话」按钮 → 唤起 AgentQuickSheet（播放页沉浸态不跳门面）                   |
| C9  | 「问歌词」钮 | LyricsScreen                          | 纯歌词滚动 + 翻译切换                                                 | MasterAgent.handleUserMessage（场景流 15）               | 歌词页加「问歌词」钮 → 调引擎生成翻译卡 + 典故 explain 卡                           |
| C10 | 歌单解释行  | PlaylistScreen                        | 纯歌曲列表                                                        | ToolExecutionRecord（区分 agent 创建 vs 用户手建）            | 伙伴生成歌单顶部加解释行（「小知按你的听歌习惯…」+「为什么」折叠）；用户手建歌单不加                    |
| C11 | 深读区块   | ArtistScreen / AlbumScreen            | 基础信息 + 歌曲列表                                                  | EnrichSubAgent 产出的 singerIntroduce / 风格标签图谱（场景流 14） | 既有信息之下插 agent 富化正文（「这个乐队的历史可以追溯到…」）+ 关联歌曲 / 播放统计               |



***

## W 缺口登记（G1-G16）

> 本清单产出时（2026-09-13）本分支刚经历一次代码回退，造成「新调用方 + 旧被调方」混杂、编译失败。
> 编译层修复后走读代码，登记了一批**「引擎已就绪、消费端缺位」**的遗留项。下表为最终全貌。

#### 全项状态

| 编号 | 待补项 | 优先级 | 归属层 | 状态 |
|------|--------|--------|--------|------|
| G1 | 电台曲目从未推入播放引擎 | P0 | 引擎 · Radio | ✅ 已落地 09-13 |
| G2 | 堆叠卡全部不可点（未传回调） | P0 | UI · P1 | ✅ 已落地 09-14 |
| G3 | 每卡停留时长未实现 | P1 | UI · P1 | ⊘ 关闭 —— 统一 4s 轮播，不做分卡时长 |
| G4 | 堆叠形态与设计不符 | P1 | UI · P1 | ⊘ 关闭 —— 维持 VerticalPager，不改 z 轴 overlay |
| G5 | 月度叙事卡无载体 | P1 | UI · P1 / P5 | ✅ 已落地 09-14（点击落点待 P5） |
| G6 | 推荐曲目接首页（双推荐页） | P1 | 引擎 + UI · P1 | ✅ 已落地 09-14（→ G6 设计规格） |
| G7 | HelloMemory 写端 6 字段恒 null | P1 | 引擎 · Hello | ✅ 已完成 09-14（补 4 留 2） |
| G8 | `radioMessageFlow()` 降级分支每次新建 | P2 | 引擎 · Master | ✅ 已完成 09-14 |
| G9 | 电台卡重建 key 缺 playlist | P2 | UI · 电台卡 | ✅ 误标更正 —— `4bf5765` 已修 |
| G10 | PAUSED 未释放堆叠锁 | P2 | UI · P1 | ✅ 已完成 09-14 |
| G11 | `RadioTrigger.RESUME` 未接线 | P2 | 引擎 · Master | ✅ 已完成 09-14 |
| G12 | `continueRadio()` 无调用点 | P2 | 引擎 · Radio | ✅ 已完成 09-14（标注冗余 API） |
| G13 | HelloMemory 无单测 | P2 | 测试 | ✅ 已完成 —— 补 `HelloMemoryTest.kt` |
| G14 | 注释 / 死列失准 | P3 | 引擎 · Hello | ✅ 已完成 09-14（注释级） |
| G15 | ENRICH_TRACKING 短按行为不一致 | P3 | UI · 组件 | ✅ 已完成 09-14（保留 no-op 一致） |
| G16 | LLM 端点本地留空 | 已澄清 | 配置 | — 非问题（CI 构建注入） |

#### 两条关闭决策（2026-09-14 产品口径修正）

三项按「新需求」关闭，不再按原设计执行：

| 项 | 决策 | 连带影响 |
|----|------|----------|
| **G4** | 维持 `VerticalPager` 上下轮播，**不改为 z 轴 overlay** | 原文「堆叠形态与设计不符」的判断作废；本文档 §W1 区域①形态已按新结论书写 |
| **G3** | 维持全局 `AUTO_ROTATE_MS = 4000L` 统一轮播，**不做分卡时长** | 原 GREETING 10s / RECOMMEND 15s / DISCOVER 12s 等不再实现 |
| **G2 长按** | 短按直接接入播放；**长按本次不做** | 手势骨架保留在代码中 |

#### 一处误标更正（2026-09-14 重新勘探）

**G9 原登记「电台卡重建 key 不含 playlist」属误判**：09-13 走读时工作树经历过一次代码回退（旧版 `HelloSlideCards` 无 `radioPlaylist` 变量），据此记为遗留项。经核对 `4bf5765` 真实代码，`LaunchedEffect(radioState, currentMusic, radioPlaylist)` 已含响应式 `radioPlaylist`，实为随电台卡重写一并修复。

> 教训：**登记缺口前先核对提交里的真实代码**，工作树的临时状态会误导判断。


***

© 2026 Hearable Music Player | Developed by WLYB
