# HMP W0 · 引擎侧 HelloSubAgent 完整实现方案

> W0 是 W1-W2 的前置条件。引擎侧先于页面侧落地。
> `agent-w1.md` 的依赖链：T + M6 + V → **W0** → W1 → W2。
> 本文档只覆盖 HelloSubAgent + W0 范围 1-3 三项改动；W0 范围 4（EnrichSubAgent 统一运行态接口）在 `agent-w1.md` P4 章节。

***

## 0. 前置改动（实现 HelloSubAgent 之前必须先做）

| # | 改动                               | 文件                  | 说明                                                                                                                                                                                     |
| - | -------------------------------- | ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | AgentScheduler 加 HELLO 枚举        | `AgentScheduler.kt` | `enum class AgentPriority` 新增 `HELLO(4)`；`decideState()` 新增 `AgentPriority.HELLO -> decideHelloState()`；暂停条件：**永不暂停**（token 未完全耗尽时 keep alive）。Hello 是 Master 之外最靠近用户的 SubAgent，不做激进省电 |
| 2 | RadioSubAgent emit AgentProgress | `RadioSubAgent.kt`  | `startRadio()` 成功后 `presenceBus?.emit(AgentProgress(agentId="radio", processed=0, total=targetCount))`；`stopRadio()` 时 `emit(AgentProgress(agentId="radio", processed=0, total=0))`    |
| 3 | MasterAgent 持有 PresenceBus       | `MasterAgent.kt`    | 当前 `chatPresenceBus` 已存在，HelloSubAgent 通过构造函数注入它来 collect DjBlank + AgentProgress                                                                                                      |

***

## 1. 定位与职责边界

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

## 2. 生命周期与 MasterAgent 接线

### MasterAgent.startHello() 完整模式

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

### SubAgent.runLoop 设计

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

## 3. 三个工作协程详细设计

### #1 PresenceBus 事件收集（`collectPresenceEvents`）

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

### #2 每日凌晨定时（`dailyRefreshLoop`）

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

### #3 每分钟 tick（`minuteTickLoop`）

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

## 4. 卡片池状态管理

### 数据结构

```kotlin
class CardPool {
    private val _cards = MutableStateFlow<List<SlideCard>>(emptyList())
    val cards: StateFlow<List<SlideCard>> = _cards.asStateFlow()
    
    private val timers = mutableMapOf<String, CountDownTimer>()  // cardId → timer
    private val pausedCards = mutableSetOf<String>()              // 正在展开（暂停倒计时）的卡
}
```

### 核心操作

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

## 5. SlideCard 与 SlideContent 完整定义

```kotlin
// 卡的类型枚举
enum class SlideType {
    ANCHOR,         // 正在听（常驻）
    RADIO_STATUS,   // 电台运行态（0=常驻，Radio 停止时 pop）
    GREETING,       // 问候 + DJ 衔接语（10s）
    RECOMMEND,      // 可解释推荐（15s）
    DISCOVER,       // 歌手/风格探索（12s）
    FORGOTTEN,      // 遗忘唤醒（12s）
    ANNIVERSARY,    // 纪念日（15s）
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

## 6. 七种卡型生成逻辑

### ANCHOR（正在听，常驻）

- **来源**：`nowPlayingProvider.getNowPlaying()` → 读当前播放曲目

- **内容**：`AnchorContent(trackTitle, artistName, bpm, phase)`

- **刷新**：`minuteTickLoop` 每分钟刷新一次

### RADIO\_STATUS（电台运行态，0=常驻）

- **来源**：RadioSubAgent emit `AgentProgress(agentId="radio", ...)` → `collectPresenceEvents` 处理

- **内容**：`RadioStatusContent(targetCount, nextTrackName)`

- **生命周期**：Radio 启动 → replace；Radio 停止（`total==0`）→ pop

### GREETING（问候 + DJ 衔接语，10s）

- **触发**：DjBlank 每次切歌

- **生成**：

  1. LLM 生成自然语言问候（人格预设 + 当前时段 + 刚播完的曲目名）
  2. LLM 失败 → `MasterAgent.fallbackGreetings` 随机取一条

- **兜底**：无 LLM + 无 fallback → 硬编码「嗨，继续听歌？」

### RECOMMEND（可解释推荐，15s）

- **触发**：每日凌晨 + 时段切换

- **生成**：

  1. `musicRepository.getRecentSkipRate(days=7, limit=5)` → 反推不该推荐什么
  2. `musicRepository.getRecentFavorRate(days=7, limit=5)` → 正推该推荐什么
  3. 结合当前时段（`detectTimePhase()`）
  4. LLM 选一首 + 写一句可解释理由（15-20 字）

- **兜底**：无 LLM → `musicRepository.getRecentTracksByLabel(labelForPhase, limit=1)`

### DISCOVER（歌手/风格探索，12s）

- **触发**：每日凌晨

- **生成**：

  1. `musicRepository.getGlobalTopLabels(limit=5)` → 选一个最近 30 天没听过的 label
  2. `musicRepository.getRecentTracksByLabel(label, limit=3)` → 取曲目
  3. LLM 写一句引导探索的话

- **兜底**：无 LLM → random 一个 label + 固定文案「你好像很久没听 X 了」

### FORGOTTEN（遗忘唤醒，12s）

- **触发**：每日凌晨扫历史

- **生成**：

  1. `musicRepository.getForgottenTracks(days=30)` → 有则用 30 天未播的；没有 `getForgottenTracks(days=90)`
  2. 有结果 → `SlideCard.replace(FORGOTTEN, ...)`；没有 → `popByType(FORGOTTEN)`
  3. 内容：`ForgottenContent(trackId, trackTitle, daysSince=30/90, playCount)`

- **兜底**：不需要 LLM

### ANNIVERSARY（纪念日，15s）

- **触发**：每日凌晨扫历史

- **生成**：

  1. `musicRepository.getAnniversaryTracks(today=LocalDate.now())`
  2. 有结果 → `SlideCard.replace(ANNIVERSARY, ...)`；没有 → `popByType(ANNIVERSARY)`
  3. 内容：`AnniversaryContent(trackId, trackTitle, yearsAgo, totalPlays)`

- **兜底**：不需要 LLM

***

## 7. 报告叙事段生成（P5 用）

### 生成时机

```
用户日均听歌时长        →  报告叙事更新频率
─────────────────────────────────────────
≤ 30 分钟              →  周更新（低活跃，内容不够每日一更）
30 分钟 ~ 2 小时        →  日更新（正常活跃度）
≥ 2 小时               →  日更新（高活跃，内容多值得每日更新）
```

**自适应判断**：每天凌晨 `dailyRefreshOnce()` 末尾计算 `musicRepository.getAvgDailyListeningMinutes(days=30)` → 写入 DAO 的 `avgDailyMinutes` 字段。下次定时生成时读 DAO 判断频率。

### 五个时间维度

| 维度 | 生成时机         | 条件                    |
| -- | ------------ | --------------------- |
| 全部 | 自适应频率（见上）    | DAO 有缓存直接显示，过期后台异步重生成 |
| 日  | 每天凌晨         | 当天有播放数据               |
| 周  | 每周一凌晨        | 上周有数据                 |
| 月  | 每月 1 号凌晨     | 上月有数据                 |
| 年  | 每年 1 月 1 号凌晨 | 上年有数据                 |

### MasterAgent 对外接口（手动触发）

```kotlin
// P5 🔄 重新生成按钮调用
suspend fun regenerateReportNarrative(timeRange: NarrativeTimeRange): HelloReportNarrative
```

### DAO 缓存策略

- 每次生成写入 DAO，带 `generatedAt` + `timeRange` + `avgDailyMinutes`（当时的日均，用于下次自适应判断）

- P5 报告页直接读 DAO 显示，不阻塞 UI

- DAO 过期（超过生成频率 × 2）时 → 后台异步触发重新生成，不打断用户看旧数据

***

## 8. DAO 设计

### 推荐卡缓存表

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

### 报告叙事段表

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

### Room 迁移

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

## 9. MusicRepository 方法缺口（完整签名）

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

## 10. 兜底策略

### LLM 不可用时

| 卡型          | LLM 失败兜底                                                |
| ----------- | ------------------------------------------------------- |
| GREETING    | MasterAgent.fallbackGreetings 随机取一条 → 再失败硬编码「嗨，继续听歌？」   |
| RECOMMEND   | `getRecentTracksByLabel(labelForCurrentPhase, limit=1)` |
| DISCOVER    | random 一个 label + 固定文案「你好像很久没听 X 了」                     |
| FORGOTTEN   | 不需要 LLM，纯数据拼接                                           |
| ANNIVERSARY | 不需要 LLM，纯数据拼接                                           |
| NARRATIVE   | 不需要 LLM（首次实现纯统计数据，后续加 LLM 叙事生成）                         |

### 曲库为空（`getAllMusicInfoAsList().isEmpty()`）

```kotlin
// HelloSubAgent.runLoop 里检测 → 跳过 dailyRefreshOnce() 和 minuteTickLoop 的业务逻辑
// 卡片池只保留 ANCHOR（null 也 push 一个空状态：「还没听歌，去扫扫描吧」）
// collectPresenceEvents 继续跑但 DjBlank / AgentProgress 不会有新事件
// RADIO_STATUS 永远不会有（Radio 无法启动）
```

### 零播放历史（刚装 App 第一次启动）

```kotlin
// FORGOTTEN / ANNIVERSARY / RECOMMEND / DISCOVER 全部返回 null → 不入池
// 卡片池只有 ANCHOR + 一张初始兜底 GREETING
// DAO 为空 → initializeFromDao() 什么都不做
// dailyRefreshLoop 首次跑 → 也生成不出任何卡 → 但不会崩（runCatching 包住了）
// minuteTickLoop → detectTimePhase 正常 → 但 getRecentTracksByLabel 返回空 → RECOMMEND 不入池
// NARRATIVE 自适应判断日均 = 0 → 周更新频率 → 但第一次不生成任何内容
```

***

## 11. 实现分期

| 阶段                   | 内容                                                                                                                                                                                                             | 预估改动                                                                                                                              |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| **H1 骨架**            | 前置 #1-3（Scheduler 加 HELLO 枚举 + Radio emit AgentProgress + MasterAgent 持有 PresenceBus）+ HelloSubAgent 类 + runLoop 骨架 + CardPool + SlideCard/SlideContent 完整模型 + startHello/stopHello 接线 + DAO 表实体 + Room 迁移 3→4 | 改：AgentScheduler.kt + RadioSubAgent.kt + MasterAgent.kt + AppDatabase.kt；新：HelloSubAgent.kt + CardPool.kt + SlideCard.kt + DAO 文件 |
| **H2 非 LLM 卡 + DAO** | `getForgottenTracks` + `getAnniversaryTracks` + `getRecentTracksByLabel` 实现；collectPresenceEvents + dailyRefreshLoop + minuteTickLoop 完整实现（FORGOTTEN / ANNIVERSARY / RECOMMEND 兜底版本）                           | MusicRepository 补 #3/#4/#6 + 推动 RadioSubAgent 两个 stub 正式实现                                                                        |
| **H3 LLM 卡**         | RECOMMEND + DISCOVER + GREETING 的 LLM 生成逻辑 + 所有卡类型的可解释理由                                                                                                                                                       | MusicRepository 补 #1/#2                                                                                                           |
| **H4 报告叙事段**         | `getAvgDailyListeningMinutes` + 自适应频率判断 + 五个时间维度生成 + DAO 缓存 + `regenerateReportNarrative` 对外接口                                                                                                                 | MusicRepository 补 #5                                                                                                              |
| **H5 集成验证**          | MasterAgent.initialize() 末尾自动 startHello()；三端编译 + desktopTest                                                                                                                                                  | <br />                                                                                                                            |

***

## 附：HelloSubAgent 对外接口汇总

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

