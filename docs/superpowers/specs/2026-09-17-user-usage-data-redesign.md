# UserUsageDataScreen 双轴筛选重构

**日期**：2026-09-17
**状态**：已批准，待实现
**关联任务**：F9-T1（听歌报告 + 遗忘唤醒）

---

## 1. 问题背景

当前 `UserUsageDataScreen`（1241 行，18 个组件函数）存在三个系统性问题：

### 1.1 数据源分裂
6 个 UI 块中只有 2 个（报告叙事段、时段柱图）是真·时间窗口数据；其余 4 个（OverviewCard、TasteCard、RankingAndHistoryCard、RecentHistoryBlock）消费 `getUserUsageAnalytics()` 的**全量累计**数据。用户切"本周"时只有叙事段和柱图变化，其他数字纹丝不动，但 OverviewCard 标签还写着"本周"——**UI 宣称的时间范围和数据真实时间范围矛盾**。

### 1.2 叙事和数字重复说一件事
ReportNarrativeCard 说"本月听了 60 小时"，OverviewCard 也显示"听了 38472 分钟"——前者是时间窗口版，后者是累计版，两句话同屏但数字矛盾。

### 1.3 职责边界模糊
人格区（画像叙事 + 人格卡）、口味分布、Top5 排行、播放来源饼图、最近播放——这 5 块要么是"长期画像"（不应该跟随时间切换），要么是"时间敏感统计"（应该真的随窗口变），但当前全混在一个可滚动 Column 里，一个时间切换器覆盖全部，导致：
- 长期画像卡被动重渲染（浪费）
- 时间敏感卡拿到假数据（骗用户）

### 1.4 DAO 层缺口
Repository 接口只有一个 `getUserUsageAnalytics()` 全量方法，没有任何窗口版聚合方法。HelloSubAgent 里 `buildStatisticsNarrative` 接的 analytics 参数也是全量的——所以叙事段说"本月主打摇滚"其实是**全曲库累计主打摇滚**，只有时长是真窗口（通过 `getAvgDailyListeningMinutes(days)`）。

---

## 2. 目标与非目标

### 目标
- 页面结构：**累计画像固定置顶 + 下方双轴筛选**（维度 × 时间）
- DAO 层新增窗口版聚合 SQL（**方案 B：维度拆分，每条独立 SQL**）
- ViewModel 重构成 combine 双轴收集，一次 load 填满 WindowedBundle
- UI 组件按"长期画像" vs "时间窗口"彻底分离
- Room **版本号不变**，零 Migration，零 schema hash 变化

### 非目标
- 不做 Room 索引优化（数据量 < 2 万条，SQLite 默认够快）
- 不做 LLM 润色叙事段（当前模板兜底已可用）
- 不做播放来源饼图的维度切换（饼图是概览的子集，不独立成维度）

---

## 3. 页面结构

```
┌─────────────────────────────────────────┐
│ 累计画像（固定置顶，不参与任何筛选）        │
│   PersonalitySection — 人格卡 + 画像叙事散文 │
├─────────────────────────────────────────┤
│ 维度: [概览] [口味] [时段] [排行] [最近]  │
│ 时间: [日] [周] [月] [年] [全部]          │
├─────────────────────────────────────────┤
│ 内容区（两轴交叉决定渲染）                 │
│   概览 + 月 → 月度叙事段 + 月度 Overview   │
│   口味 + 周 → 本周 Top 流派/情绪/场景     │
│   时段 + 年 → 年度时段柱图                │
│   排行 + 月 → 月度 Top5 歌曲              │
│   最近 + 周 → 本周最近 N 条播放           │
└─────────────────────────────────────────┘
```

### 3.1 Dimension 枚举（5 个值）

| 枚举值 | zhLabel | WindowedBundle 消费字段 |
|---|---|---|
| OVERVIEW | 概览 | narrative + analytics + sourceBreakdown |
| TASTE | 口味 | topGenres + topMoods + topScenarios |
| HOUR | 时段 | hourlyDistribution |
| RANK | 排行 | topSongs |
| RECENT | 最近 | recentPlayback |

---

## 4. DAO 层新增（方案 B，零 Room 版本升级）

### 4.1 PlaybackHistoryDao 新增 5 条 SQL

#### ① 窗口总时长（毫秒 → Repository 转分钟）
```sql
SELECT COALESCE(SUM(playDuration), 0) FROM PlaybackHistory WHERE playedAt >= :cutoff
```

#### ② 窗口完播率（SQLite 没有直接 BOOL→FLOAT 转换，用 CASE WHEN）
```sql
SELECT AVG(CASE WHEN isCompleted THEN 1.0 ELSE 0.0 END) FROM PlaybackHistory WHERE playedAt >= :cutoff
```
返回 Double，Repository 转 Float。

#### ③ 窗口跳过率
```sql
SELECT COUNT(*) FROM PlaybackHistory WHERE playedAt >= :cutoff AND isCompleted = 0
-- 然后 总跳过数 / 总播放数 = skipRate（Repository 内存算，避免一条 SQL 变复杂）
```
或者直接一条：
```sql
SELECT
  COUNT(*) AS total,
  SUM(CASE WHEN isCompleted THEN 0 ELSE 1 END) AS skipped,
  AVG(CASE WHEN isCompleted THEN 1.0 ELSE 0.0 END) AS completionRate
FROM PlaybackHistory WHERE playedAt >= :cutoff
```
返回一个数据类 `WindowedCountRow(total, skipped, completionRate)`。

#### ④ 窗口 Top 歌曲（带歌名歌手，LEFT JOIN music）
```sql
SELECT h.musicId, m.title, m.artist, COUNT(*) AS playCnt
FROM PlaybackHistory h
LEFT JOIN music m ON m.id = h.musicId
WHERE h.playedAt >= :cutoff
GROUP BY h.musicId
ORDER BY playCnt DESC
LIMIT :limit
```
返回数据类 `TopSongRow(musicId, title, artist, playCnt)`。

#### ⑤ 窗口最近 N 条播放（带歌名歌手）
```sql
SELECT h.musicId, m.title, m.artist, h.playedAt, h.playDuration, h.isCompleted
FROM PlaybackHistory h
LEFT JOIN music m ON m.id = h.musicId
WHERE h.playedAt >= :cutoff
ORDER BY h.playedAt DESC
LIMIT :limit
```
返回数据类与现有 `RecentPlaybackEntry` 对齐。

#### ⑥ 窗口播放来源分布
```sql
SELECT source, COUNT(*) AS cnt
FROM PlaybackHistory
WHERE playedAt >= :cutoff AND source IS NOT NULL
GROUP BY source
```
返回 `List<SourceBreakdownRow(source, cnt)>`。

### 4.2 MusicLabelDao 新增 1 条 SQL

#### ⑦ 窗口 Top N 标签（按播放次数加权）
```sql
SELECT l.label, COUNT(h.id) AS cnt
FROM musicLabel l
INNER JOIN PlaybackHistory h ON h.musicId = l.musicId
WHERE h.playedAt >= :cutoff AND l.type = :category
GROUP BY l.label
ORDER BY cnt DESC
LIMIT :limit
```
参数 `category` 是 `LabelCategory` 枚举（GENRE / MOOD / SCENARIO），Repository 层调 3 次拿三个维度。

### 4.3 已有窗口 SQL（直接复用，不动）

| SQL | 文件 | 行号 |
|---|---|---|
| `getPlayRowsSince(sinceMs)` | PlaybackHistory.kt | L84-92 |
| `getHourlyDistribution(sinceMs)` | PlaybackHistory.kt | L112-121 |

### 4.4 WindowedUsageAnalytics 数据类

```kotlin
/** 带时间窗口的统计结果 — 全部来自方案 B 的单条 SQL，零内存聚合 */
data class WindowedUsageAnalytics(
    val totalListeningMinutes: Long,
    val completionRate: Float,     // 0.0 - 1.0
    val skipRate: Float,           // 0.0 - 1.0
    val totalPlayCount: Int,
    val totalSkipCount: Int,
)
```

### 4.5 Days → sinceMs 转换

```kotlin
/** days=-1 表示"全部"，传 10 年前的时间戳近似 */
fun daysToCutoffMs(days: Int): Long = when (days) {
    -1 -> currentTimeMillis() - 3650 * 86_400_000L
    else -> currentTimeMillis() - days * 86_400_000L
}
```

Room **版本号 8 → 保持 8 不变**。所有新增都是 `@Query` 方法，表结构零改动，零 Migration，用户零感知。

---

## 5. Repository 层

### 5.1 接口新增方法

挂在 `MusicRepository` 接口上：

```kotlin
// 概览
suspend fun getWindowedAnalytics(days: Int): WindowedUsageAnalytics
suspend fun getWindowedSourceBreakdown(days: Int): Map<String, Int>

// 口味
suspend fun getWindowedTopLabels(days: Int, category: LabelCategory, limit: Int = 5): List<LabelCountEntry>

// 排行
suspend fun getWindowedTopSongs(days: Int, limit: Int = 5): List<TopPlayedEntry>

// 最近
suspend fun getWindowedRecentPlayback(days: Int, limit: Int = 10): List<RecentPlaybackEntry>
```

### 5.2 实现位置

三端都继承 `MusicRepositoryBase` 并各自实现接口。但因为 DAO 是 expect/actual，实际每个平台只需要把接口上的方法签名直接透传到对应的 DAO 查询。

**MusicRepositoryBase（commonMain）** 已经是 `abstract` 类，三端各自的 Repository 类（AndroidMusicRepository / DesktopMusicRepository / IosMusicRepository）继承它。新增方法在 Base 里提供实现（因为 DAO 接口是跨平台一致的），三端 override 逻辑相同。

### 5.3 旧方法保留

- `getUserUsageAnalytics()` — **本页面不再消费**（累计画像区只需要人格数据，窗口数据用新的 Windowed 系列方法）。保留不删，可能其他页面（如首页、设置里的"累计使用时长"）仍在使用
- `getHourlyDistribution(windowDays: Int)` — 已存在，WindowedBundle.HOUR 直接复用

---

## 6. ViewModel 层重构

### 6.1 新结构

```kotlin
class UserUsageDataViewModel(
    private val musicRepository: MusicRepository,
    private val masterAgent: MasterAgent,    // 强制注入，无默认值
) : ViewModel() {

    // ── ① 累计画像区：init 加载一次，只需要人格数据 ──
    private val _personality = MutableStateFlow<PersonalityBundle?>(null)
    val personality: StateFlow<PersonalityBundle?> = _personality.asStateFlow()

    // ── ② 筛选轴 ──
    private val _dimension = MutableStateFlow(Dimension.OVERVIEW)
    val dimension: StateFlow<Dimension> = _dimension.asStateFlow()

    private val _timeRange = MutableStateFlow(NarrativeTimeRange.MONTH)
    val timeRange: StateFlow<NarrativeTimeRange> = _timeRange.asStateFlow()

    // ── ③ 时间窗口数据（两轴交叉） ──
    private val _windowed = MutableStateFlow(WindowedBundle())
    val windowed: StateFlow<WindowedBundle> = _windowed.asStateFlow()

    init {
        loadProfile()
        viewModelScope.launch {
            combine(dimension, timeRange) { d, t -> d to t }.collect { (d, t) ->
                loadWindowed(d, t)
            }
        }
    }
}
```

### 6.2 WindowedBundle

```kotlin
data class WindowedBundle(
    val narrative: HelloReportNarrativeEntity? = null,
    val analytics: WindowedUsageAnalytics? = null,
    val sourceBreakdown: Map<String, Int> = emptyMap(),
    val topGenres: List<LabelCountEntry> = emptyList(),
    val topMoods: List<LabelCountEntry> = emptyList(),
    val topScenarios: List<LabelCountEntry> = emptyList(),
    val hourlyDistribution: List<HourlyDistributionRow> = emptyList(),
    val topSongs: List<TopPlayedEntry> = emptyList(),
    val recentPlayback: List<RecentPlaybackEntry> = emptyList(),
)
```

### 6.3 Dimension 枚举

```kotlin
enum class Dimension(val zhLabel: String, val iconRes: String) {
    OVERVIEW("概览", "📊"),
    TASTE("口味", "🎨"),
    HOUR("时段", "🕐"),
    RANK("排行", "🏆"),
    RECENT("最近", "🎵"),
}
```

### 6.4 Dimension × TimeRange → days 映射

```kotlin
fun NarrativeTimeRange.toDays(): Int = when (this) {
    NarrativeTimeRange.DAY -> 1
    NarrativeTimeRange.WEEK -> 7
    NarrativeTimeRange.MONTH -> 30
    NarrativeTimeRange.YEAR -> 365
    NarrativeTimeRange.ALL -> -1  // -1 = 全部
}
```

### 6.5 loadWindowed 实现

```kotlin
private suspend fun loadWindowed(dim: Dimension, range: NarrativeTimeRange) {
    val days = range.toDays()
    val repo = musicRepository

    when (dim) {
        Dimension.OVERVIEW -> {
            // ① 窗口统计
            val analytics = runCatching { repo.getWindowedAnalytics(days) }.getOrNull()
            val source = runCatching { repo.getWindowedSourceBreakdown(days) }.getOrDefault(emptyMap())
            // ② 叙事段：先读 DAO 缓存，再异步 regenerate 刷新
            val cached = runCatching { masterAgent.getReportNarrative(range) }.getOrNull()
            val fresh = runCatching { masterAgent.regenerateReportNarrative(range) }.getOrNull()
            _windowed.value = WindowedBundle(
                narrative = fresh ?: cached,
                analytics = analytics,
                sourceBreakdown = source,
            )
        }
        Dimension.TASTE -> {
            val topGenres = runCatching { repo.getWindowedTopLabels(days, LabelCategory.GENRE) }.getOrDefault(emptyList())
            val topMoods = runCatching { repo.getWindowedTopLabels(days, LabelCategory.MOOD) }.getOrDefault(emptyList())
            val topScenarios = runCatching { repo.getWindowedTopLabels(days, LabelCategory.SCENARIO) }.getOrDefault(emptyList())
            _windowed.value = WindowedBundle(topGenres = topGenres, topMoods = topMoods, topScenarios = topScenarios)
        }
        Dimension.HOUR -> {
            val hourly = runCatching { repo.getHourlyDistribution(days) }.getOrDefault(emptyList())
            _windowed.value = WindowedBundle(hourlyDistribution = hourly)
        }
        Dimension.RANK -> {
            val songs = runCatching { repo.getWindowedTopSongs(days) }.getOrDefault(emptyList())
            _windowed.value = WindowedBundle(topSongs = songs)
        }
        Dimension.RECENT -> {
            val items = runCatching { repo.getWindowedRecentPlayback(days) }.getOrDefault(emptyList())
            _windowed.value = WindowedBundle(recentPlayback = items)
        }
    }
}
```

### 6.6 关键设计决策

1. **OVERVIEW 特殊处理**：同时要 narrative（走 HelloSubAgent regenerate）+ analytics（走 Repository 窗口查询）+ sourceBreakdown（走 Repository 窗口查询）。三个查询可以并发用 `async`，但当前规模串行够了。
2. **TASTE 一次 3 个查询**：GENRE / MOOD / SCENARIO 是独立的 DAO 调用，串行。
3. **DAO 调用全部 runCatching**：避免某个查询失败导致整个 WindowedBundle 不渲染——窗口部分 fail，页面仍然有其他数据。

---

## 7. UI 层重排

### 7.1 页面主结构

```kotlin
Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {

    // ═══════════ 固定置顶：累计画像（永不随筛选变）═══════════
    ProfileSection(personality)

    HorizontalDivider()

    // ═══════════ 筛选轴 ═══════════
    DimensionSelector(selected = dimension, onSelect = { viewModel.dimension.value = it })
    TimeRangeSwitcher(selected = timeRange, onSelect = { viewModel.timeRange.value = it })

    // ═══════════ 内容区（WindowedBundle 按维度分发）═══════════
    when (dimension) {
        Dimension.OVERVIEW -> OverviewWindowContent(
            narrative = windowed.narrative,
            analytics = windowed.analytics,
            sourceBreakdown = windowed.sourceBreakdown,
            rangeLabel = timeRange.zhName(),
        )
        Dimension.TASTE -> TasteWindowContent(
            topGenres = windowed.topGenres,
            topMoods = windowed.topMoods,
            topScenarios = windowed.topScenarios,
            rangeLabel = timeRange.zhName(),
        )
        Dimension.HOUR -> HourlyChart(rows = windowed.hourlyDistribution)
        Dimension.RANK -> RankingWindowContent(
            topSongs = windowed.topSongs,
            rangeLabel = timeRange.zhName(),
        )
        Dimension.RECENT -> RecentWindowContent(
            items = windowed.recentPlayback,
            rangeLabel = timeRange.zhName(),
        )
    }
}
```

### 7.2 ProfileSection — 累计画像区（极简：只留人格）

```kotlin
@Composable
private fun ProfileSection(personality: PersonalityBundle?) {
    personality?.let { PersonalitySection(bundle = it) }
}
```

**只有一个组件**：`PersonalitySection`（人格卡 + 画像叙事散文）。全量 TasteCard / RankingAndHistoryCard 从顶部去掉，下沉到下方 OVERVIEW Tab 选"全部"时间范围时自然展示（窗口版本 = 全量版本）。

**好处**：顶部只保留一个语义——"这是你长期的画像"，不跟下面的时间窗口内容抢注意力。全量数据（口味分布、Top5 排行、播放来源饼图）统一由下方 OVERVIEW + 全部 承担，用户想看时自然能找到。

### 7.3 DimensionSelector — 新组件

SegmentedControl 风格，5 个 Tab。与现有 `TimeRangeSwitcher` 样式一致。

### 7.4 OverviewWindowContent — 概览内容

合并叙事段 + 窗口 OverviewCard + 播放来源饼图。叙事段作为 OverviewCard 的自然语言标题，OverviewCard 的数字是真窗口数据（总时长、完播率、跳过率），饼图在底部。

**不再有重复说同一件事的两张卡**。

### 7.5 TasteWindowContent — 口味窗口版

与现有 `TasteCard` UI 样式一致（横向堆积条 + 图例），但数据源换成 `WindowedBundle.topGenres/topMoods/topScenarios`。复用组件内部的 `LabelStackedBarWithLegend`。

### 7.6 RankingWindowContent — 排行窗口版

简单 Top5 列表，歌名 + 歌手 + 播放次数。横向 ListItem 样式。数据来自 `getWindowedTopSongs(days)`。

### 7.7 RecentWindowContent — 最近窗口版

复用现有 `RecentHistoryBlock` 组件 UI，数据源换成 `getWindowedRecentPlayback(days)`。

### 7.8 删除/废弃的旧组件

| 组件 | 原因 |
|---|---|
| 旧 `OverviewCard`（时间骗子版） | 叙事段和数字合并进 `OverviewWindowContent` |
| 旧 `ReportNarrativeCard`（独立卡版） | 合并进 `OverviewWindowContent` |
| 旧 `InsightPill` | 合并进 `OverviewWindowContent` |
| 旧 `PlaySourcePieChart`（独立版） | 合并进 `OverviewWindowContent` |

保留可复用的子组件：`LabelStackedBarWithLegend`、`RateProgressRow`、`HourlyChart`、`PersonalitySection`（顶部累计画像用）、`RecentPlaybackItem`。

旧 `TasteCard` / `RankingAndHistoryCard` / `RecentHistoryBlock` / `PlaySourcePieChart` / `OverviewCard` 的组件内部逻辑可以**被 Window 版组件复用**（比如 `TasteWindowContent` 复用 `LabelStackedBarWithLegend`），但它们自身不再作为独立卡出现在页面上。

---

## 8. Koin DI 收紧

三端 `UserUsageDataViewModel` 注册去掉默认 null 值，强制注入：

```kotlin
// 之前
viewModel { UserUsageDataViewModel(get()) }

// 之后
viewModel { UserUsageDataViewModel(
    get<com.hmp.domain.music.MusicRepository>(),
    get<com.hmp.domain.agent.runtime.MasterAgent>(),
) }
```

**重要**：GetUserUsageDataUseCase 依赖链要检查——之前 ViewModel 第一个参数是 `getUserUsageDataUseCase`，现在换成 `musicRepository` + `masterAgent`。需要确认 ViewModel 不再依赖 UseCase，直接用 Repository。

---

## 9. 改动汇总

| 层 | 文件 | 新增 | 修改 | 删除 |
|---|---|---|---|---|
| **Room DAO** | `PlaybackHistory.kt` | 4 条 SQL + 3 个数据类 | 0 | 0 |
| **Room DAO** | `MusicLabel.kt` | 1 条 SQL | 0 | 0 |
| **Room Schema** | `AppDatabase.kt` | 0 | 版本号保持 8，零 Migration | 0 |
| **Repository 接口** | `MusicRepository.kt` | 5 个方法签名 | 0 | 0 |
| **Repository 实现** | `MusicRepositoryBase.kt` | 5 个方法实现 | 0 | 0 |
| **Domain 模型** | 新建 `WindowedUsageAnalytics.kt` | 2 个数据类 | 0 | 0 |
| **ViewModel** | `UserUsageDataViewModel.kt` | Dimension 枚举 + WindowedBundle | 重构 collect 链 + 加载逻辑 | 旧 getAnalytics/loadTimeSensitive/loadPersonality 拆分；去掉 profileAnalytics |
| **UI** | `UserUsageDataScreen.kt` | DimensionSelector + 5 个 Window 内容组件 | 页面主结构重排 | 旧 OverviewCard / ReportNarrativeCard / InsightPill / 独立饼图 |
| **DI** | 三端 Koin 注册 | 0 | 参数收紧（去掉可选 null） | 可选参数 |

---

## 10. 风险与缓解

| 风险 | 缓解 |
|---|---|
| Room 新增 SQL 写错 | 每个 SQL 用 `runCatching` 包裹，DAO 层返回 null 或 emptyList，不 crash |
| 旧全量组件被误删导致累计画像区白屏 | ProfileSection 只依赖 `PersonalityBundle`（来自 MasterAgent.userMemory），不走全量 analytics；PersonalitySection 组件和其数据源不受影响 |
| ViewModel combine 循环触发（dimension/timeRange 同时变） | `combine` 是 StateFlow 标准操作符，每次任一轴变都 emit，但只 emit 当前值组合，不循环 |
| Room expect/actual SQL 端不一致 | DAO 接口定义在 commonMain，三端 impl 共享同一份 SQL（方案 B 的 SQL 不涉及平台差异） |

---

## 11. 验收标准

- [ ] Android Debug 构建成功，**Room schema hash 不变**（导出 schema 对比）
- [ ] desktopTest 全绿，新增 DAO SQL 有单测覆盖
- [ ] 启动无 crash，ProfileSection（累计画像）立即可见
- [ ] 切换维度筛选（概览→口味→时段→排行→最近）内容正确切换
- [ ] 切换时间筛选（日→周→月→年→全部）内容真实变化（数字不同）
- [ ] 切到日 + 排行，看到的是今天播放最多的歌（不是累计 Top5）
- [ ] 切到全部 + 排行，应该是全曲库累计 Top5（窗口版 days=-1 ≈ 全量版）
- [ ] OverviewWindowContent 数字和叙事段文本一致（叙事说"本月听了 60 小时"，OverviewCard 显示的窗口总时长 ≈ 3600 分钟）
- [ ] 三端 Koin 注入无 NoDefinitionFoundException（收紧后必须两个依赖都注册）
