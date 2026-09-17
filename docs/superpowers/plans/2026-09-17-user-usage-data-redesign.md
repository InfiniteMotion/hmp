# UserUsageDataScreen 双轴筛选重构 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 UserUsageDataScreen — 顶部累计画像极简（只留人格），下方维度 × 时间双轴筛选，DAO 层新增方案 B 窗口查询 SQL（零 Room 版本升级）。

**Architecture:** DAO 层新增 6 条带 `playedAt >= cutoff` 的窗口 SQL → Repository 暴露 5 个 Windowed 方法 → ViewModel 用 `combine(dimension, timeRange)` 收集，两轴交叉时一次 load 填满 WindowedBundle → UI 按 Dimension 枚举 when 分发渲染不同 Window 内容组件。

**Tech Stack:** Kotlin Multiplatform, Room (KMP), Koin, Compose Multiplatform, StateFlow

---

## 文件结构总览

| 操作 | 文件 | 职责 |
|---|---|---|
| **Modify** | `shared/.../database/PlaybackHistory.kt` | DAO + 4 条新 SQL + 数据类 |
| **Modify** | `shared/.../database/MusicLabel.kt` | DAO + 1 条 JOIN SQL |
| **Create** | `shared/.../domain/setting/model/WindowedUsageAnalytics.kt` | WindowedUsageAnalytics 数据类 + Dimension 枚举 + WindowedBundle |
| **Modify** | `shared/.../domain/music/MusicRepository.kt` | 接口新增 5 个 Windowed 方法签名 |
| **Modify** | `shared/.../repository/MusicRepositoryBase.kt` | 实现 5 个 Windowed 方法 |
| **Modify** | `shared-ui/.../viewmodel/UserUsageDataViewModel.kt` | 重构：去掉 UseCase 依赖，加 combine 收集，加 WindowedBundle |
| **Modify** | `shared-ui/.../pages/UserUsageDataScreen.kt` | 页面主结构重排，新增 DimensionSelector + 5 个 Window 内容组件 |
| **Modify** | 三端 UiKoinModule | 去掉 GetUserUsageDataUseCase 参数，收紧为 MusicRepository + MasterAgent |
| **Test** | `shared/.../desktopTest/...PlaybackAndListeningDaoTest.kt` | 新增 DAO 窗口查询单测 |

---

### Task 1: DAO 层 — PlaybackHistoryDao 新增窗口 SQL

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hmp/data/database/PlaybackHistory.kt`

**上下文：** 当前 PlaybackHistoryDao 已有 `getPlayRowsSince(sinceMs)` 和 `getHourlyDistribution(sinceMs)` 两个窗口查询先例，这次新增 4 条。

- [ ] **Step 1: 在 PlaybackHistory.kt 末尾追加 3 个 DAO 返回数据类**

在现有 `AnniversaryCandidateRow` 之后追加：

```kotlin
/** 窗口内播放统计（一次 SQL 返回总播放数 + 跳过数 + 完播率）。 */
data class WindowedCountRow(
    val total: Int,
    val skipped: Int,
    val completionRate: Double,
)

/** 窗口 Top 歌曲（带歌名歌手）。 */
data class TopSongRow(
    val musicId: Long,
    val title: String,
    val artist: String,
    val playCnt: Int,
)

/** 窗口内最近播放（带歌名歌手）。 */
data class RecentRow(
    val musicId: Long,
    val title: String,
    val artist: String,
    val playedAt: Long,
    val playDuration: Long,
    val isCompleted: Boolean,
)
```

- [ ] **Step 2: 在 PlaybackHistoryDao 接口的 `getHourlyDistribution` 之后追加 4 条新方法**

```kotlin
/** 窗口内总时长（毫秒）。 */
@Query("""
    SELECT COALESCE(SUM(playDuration), 0)
    FROM PlaybackHistory
    WHERE playedAt >= :cutoff
""")
suspend fun getTotalDurationSince(cutoff: Long): Long

/** 窗口内播放统计（总播放数 / 跳过数 / 完播率）。一次 SQL，Repository 内存算 skipRate。 */
@Query("""
    SELECT
      COUNT(*) AS total,
      SUM(CASE WHEN isCompleted THEN 0 ELSE 1 END) AS skipped,
      AVG(CASE WHEN isCompleted THEN 1.0 ELSE 0.0 END) AS completionRate
    FROM PlaybackHistory
    WHERE playedAt >= :cutoff
""")
suspend fun getWindowedPlaybackCount(cutoff: Long): WindowedCountRow

/** 窗口内 Top N 歌曲（LEFT JOIN music 拿歌名歌手）。 */
@Query("""
    SELECT h.musicId AS musicId, m.title AS title, m.artist AS artist,
           COUNT(*) AS playCnt
    FROM PlaybackHistory h
    LEFT JOIN music m ON m.id = h.musicId
    WHERE h.playedAt >= :cutoff
    GROUP BY h.musicId
    ORDER BY playCnt DESC
    LIMIT :limit
""")
suspend fun getTopSongsSince(cutoff: Long, limit: Int): List<TopSongRow>

/** 窗口内最近 N 条播放（LEFT JOIN music 拿歌名歌手）。 */
@Query("""
    SELECT h.musicId AS musicId, m.title AS title, m.artist AS artist,
           h.playedAt AS playedAt, h.playDuration AS playDuration,
           h.isCompleted AS isCompleted
    FROM PlaybackHistory h
    LEFT JOIN music m ON m.id = h.musicId
    WHERE h.playedAt >= :cutoff
    ORDER BY h.playedAt DESC
    LIMIT :limit
""")
suspend fun getRecentPlaybackSince(cutoff: Long, limit: Int): List<RecentRow>

/** 窗口内播放来源分布。 */
@Query("""
    SELECT source, COUNT(*) AS cnt
    FROM PlaybackHistory
    WHERE playedAt >= :cutoff AND source IS NOT NULL
    GROUP BY source
""")
suspend fun getSourceBreakdownSince(cutoff: Long): List<SourceBreakdownRow>
```

- [ ] **Step 3: SourceBreakdownRow 数据类追加到文件末尾**

```kotlin
/** 窗口内播放来源分布行。 */
data class SourceBreakdownRow(
    val source: String,
    val cnt: Int,
)
```

- [ ] **Step 4: Run DAO 测试编译验证**

```bash
.\gradlew.bat :shared:compileKotlinDesktop 2>&1 | Select-String "BUILD|FAIL|error" | Select-Object -Last 5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/hmp/data/database/PlaybackHistory.kt
git commit -m "feat(db): PlaybackHistoryDao 新增 5 条窗口查询 SQL + 3 个数据类"
```

---

### Task 2: DAO 层 — MusicLabelDao 新增窗口 JOIN 查询

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hmp/data/database/MusicLabel.kt`

- [ ] **Step 1: 在 MusicLabelDao 接口的 `deleteUserLabel` 之后追加新方法**

```kotlin
/**
 * 窗口内 Top N 标签（按窗口内播放次数加权）。
 * JOIN PlaybackHistory 只统计窗口内被播放过的标签，而不是全曲库所有标签的累计标记数。
 */
@Query("""
    SELECT l.label AS label, COUNT(h.id) AS cnt
    FROM musicLabel l
    INNER JOIN PlaybackHistory h ON h.musicId = l.musicId
    WHERE h.playedAt >= :cutoff AND l.type = :category
    GROUP BY l.label
    ORDER BY cnt DESC
    LIMIT :limit
""")
suspend fun getTopLabelsSince(cutoff: Long, category: LabelCategory, limit: Int): List<LabelCountPair>
```

复用已有的 `LabelCountPair(label: LabelName, cnt: Int)` 数据类，不用新建。

- [ ] **Step 2: Run 编译验证**

```bash
.\gradlew.bat :shared:compileKotlinDesktop 2>&1 | Select-String "BUILD|FAIL|error" | Select-Object -Last 5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/hmp/data/database/MusicLabel.kt
git commit -m "feat(db): MusicLabelDao 新增窗口 TopLabelsSince JOIN 查询"
```

---

### Task 3: Domain 层 — 模型类 + 枚举 + 扩展

**Files:**
- Create: `shared/src/commonMain/kotlin/com/hmp/domain/setting/model/WindowedUsageAnalytics.kt`

- [ ] **Step 1: 创建 WindowedUsageAnalytics.kt**

```kotlin
package com.hmp.domain.setting.model

/** 带时间窗口的统计结果 — 全部来自方案 B 的单条 SQL，零内存聚合。 */
data class WindowedUsageAnalytics(
    val totalListeningMinutes: Long,
    val completionRate: Float,
    val skipRate: Float,
    val totalPlayCount: Int,
    val totalSkipCount: Int,
)
```

- [ ] **Step 2: 在 SlideModels.kt（或附近已有 NarrativeTimeRange 定义处）新增扩展方法**

找到 `NarrativeTimeRange` 枚举定义的文件（之前搜索确认在 `shared/src/commonMain/kotlin/com/hmp/domain/agent/sub/SlideModels.kt`），在枚举旁边追加：

```kotlin
/** NarrativeTimeRange → Windowed 查询用的 days 参数。-1 表示"全部"（10 年前时间戳）。 */
fun NarrativeTimeRange.toDays(): Int = when (this) {
    NarrativeTimeRange.DAY -> 1
    NarrativeTimeRange.WEEK -> 7
    NarrativeTimeRange.MONTH -> 30
    NarrativeTimeRange.YEAR -> 365
    NarrativeTimeRange.ALL -> -1
}

/** days → sinceMs。days=-1 时返回 10 年前时间戳（近似全部）。 */
fun daysToCutoffMs(days: Int): Long = when (days) {
    -1 -> com.hmp.domain.agent.runtime.currentTimeMillis() - 3650L * 86_400_000L
    else -> com.hmp.domain.agent.runtime.currentTimeMillis() - days.toLong() * 86_400_000L
}
```

> 注：`currentTimeMillis()` 在 HMP 里是 expect/actual，已确认在 MasterAgent.kt 等文件中用的是 `com.hmp.domain.agent.runtime.currentTimeMillis()`。需要确认这个函数在 commonMain 可见，否则直接用 `System.currentTimeMillis()` 的 expect/actual 封装。

- [ ] **Step 3: Dimension 枚举放在哪里？**

Dimension 枚举定义在 ViewModel 同文件 `UserUsageDataViewModel.kt` 内部（是 ViewModel 的内部 enum），不需要单独文件。这样 UI 层通过 `UserUsageDataViewModel.Dimension.OVERVIEW` 引用，语义紧密。

- [ ] **Step 4: Run 编译验证**

```bash
.\gradlew.bat :shared:compileKotlinDesktop 2>&1 | Select-String "BUILD|FAIL|error" | Select-Object -Last 5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/hmp/domain/setting/model/WindowedUsageAnalytics.kt
git add shared/src/commonMain/kotlin/com/hmp/domain/agent/sub/SlideModels.kt
git commit -m "feat(domain): WindowedUsageAnalytics 数据类 + NarrativeTimeRange.toDays() + daysToCutoffMs()"
```

---

### Task 4: Repository 接口 + 实现

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hmp/domain/music/MusicRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/hmp/data/repository/MusicRepositoryBase.kt`

- [ ] **Step 1: MusicRepository 接口追加 5 个方法签名**

```kotlin
// ═══════════════════════════════════════════════════════════════
// 带时间窗口的统计查询（方案 B：维度拆分，每条独立 SQL）
// days=-1 表示"全部"
// ═══════════════════════════════════════════════════════════════

/** 窗口内总时长 / 完播率 / 跳过率 / 播放次数 / 跳过次数。 */
suspend fun getWindowedAnalytics(days: Int): com.hmp.domain.setting.model.WindowedUsageAnalytics

/** 窗口内播放来源分布（source → 次数）。 */
suspend fun getWindowedSourceBreakdown(days: Int): Map<String, Int>

/** 窗口内 Top N 标签（按播放次数加权）。category 为 GENRE/MOOD/SCENARIO。 */
suspend fun getWindowedTopLabels(
    days: Int,
    category: com.hmp.data.database.myenum.LabelCategory,
    limit: Int = 5
): List<com.hmp.domain.setting.model.LabelCountEntry>

/** 窗口内 Top N 歌曲（歌名 + 歌手 + 播放次数）。 */
suspend fun getWindowedTopSongs(days: Int, limit: Int = 5): List<com.hmp.domain.setting.model.TopPlayedEntry>

/** 窗口内最近 N 条播放（歌名 + 歌手 + 播放时间）。 */
suspend fun getWindowedRecentPlayback(days: Int, limit: Int = 10): List<com.hmp.domain.setting.model.RecentPlaybackEntry>
```

- [ ] **Step 2: MusicRepositoryBase 实现 5 个方法**

找到 `getHourlyDistribution` 的实现位置（应该已经有了，确认签名一致），在附近追加：

```kotlin
override suspend fun getWindowedAnalytics(days: Int): WindowedUsageAnalytics {
    val cutoff = daysToCutoffMs(days)
    val totalMs = playbackHistoryDao.getTotalDurationSince(cutoff)
    val countRow = playbackHistoryDao.getWindowedPlaybackCount(cutoff)
    val total = countRow.total
    val skipped = countRow.skipped
    val completionRate = countRow.completionRate.toFloat()
    val skipRate = if (total > 0) skipped.toFloat() / total else 0f
    return WindowedUsageAnalytics(
        totalListeningMinutes = totalMs / 60_000,
        completionRate = completionRate,
        skipRate = skipRate,
        totalPlayCount = total,
        totalSkipCount = skipped,
    )
}

override suspend fun getWindowedSourceBreakdown(days: Int): Map<String, Int> {
    val cutoff = daysToCutoffMs(days)
    return playbackHistoryDao.getSourceBreakdownSince(cutoff).associate { it.source to it.cnt }
}

override suspend fun getWindowedTopLabels(
    days: Int,
    category: LabelCategory,
    limit: Int
): List<LabelCountEntry> {
    val cutoff = daysToCutoffMs(days)
    val pairs = musicLabelDao.getTopLabelsSince(cutoff, category, limit)
    return pairs.map { LabelCountEntry(labelDisplayName = it.label.name, count = it.cnt) }
}

override suspend fun getWindowedTopSongs(days: Int, limit: Int): List<TopPlayedEntry> {
    val cutoff = daysToCutoffMs(days)
    return playbackHistoryDao.getTopSongsSince(cutoff, limit).map { row ->
        TopPlayedEntry(
            musicId = row.musicId,
            title = row.title ?: "",
            artist = row.artist ?: "",
            playCount = row.playCnt,
        )
    }
}

override suspend fun getWindowedRecentPlayback(days: Int, limit: Int): List<RecentPlaybackEntry> {
    val cutoff = daysToCutoffMs(days)
    return playbackHistoryDao.getRecentPlaybackSince(cutoff, limit).map { row ->
        RecentPlaybackEntry(
            musicId = row.musicId,
            title = row.title ?: "",
            artist = row.artist ?: "",
            playedAt = row.playedAt,
            playDuration = row.playDuration,
            isCompleted = row.isCompleted,
        )
    }
}
```

> 注意：Repository 实现里引用的 `LabelCountEntry` / `TopPlayedEntry` / `RecentPlaybackEntry` 都是 UserUsageAnalytics.kt 里定义的数据类，确认 import 正确。

- [ ] **Step 3: Run 编译验证（共享 + Android）**

```bash
.\gradlew.bat :shared:compileKotlinAndroid 2>&1 | Select-String "BUILD|FAIL|error" | Select-Object -Last 5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/hmp/domain/music/MusicRepository.kt
git add shared/src/commonMain/kotlin/com/hmp/data/repository/MusicRepositoryBase.kt
git commit -m "feat(repository): 新增 5 个 Windowed 窗口查询方法"
```

---

### Task 5: ViewModel 重构

**Files:**
- Modify: `shared-ui/src/commonMain/kotlin/com/hearablemusic/player/ui/settings/viewmodel/UserUsageDataViewModel.kt`

**上下文：** 当前 ViewModel 有 3 个 StateFlow（profileAnalytics / personality / reportBundle），collect 链是 init 里 loadAnalytics + loadPersonality + timeRange.collect { loadTimeSensitive }。重构后去掉 profileAnalytics，加 Dimension + WindowedBundle，combine 双轴。

- [ ] **Step 1: 重写 ViewModel 全部内容**

```kotlin
package com.hearablemusic.player.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.data.database.HelloReportNarrativeEntity
import com.hmp.data.database.myenum.LabelCategory
import com.hmp.domain.agent.profile.PersonalityCardComposer
import com.hmp.domain.agent.profile.ProfileNarrativeState
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.sub.NarrativeTimeRange
import com.hmp.domain.music.HourlyDistributionRow
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.LabelCountEntry
import com.hmp.domain.setting.model.RecentPlaybackEntry
import com.hmp.domain.setting.model.TopPlayedEntry
import com.hmp.domain.setting.model.WindowedUsageAnalytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 维度筛选枚举 — 决定下方内容区渲染什么组件。 */
enum class Dimension(val zhLabel: String) {
    OVERVIEW("概览"),
    TASTE("口味"),
    HOUR("时段"),
    RANK("排行"),
    RECENT("最近"),
}

/** WindowedBundle — 时间窗口 + 维度筛选交叉产出的所有数据。 */
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

class UserUsageDataViewModel(
    private val musicRepository: MusicRepository,
    private val masterAgent: MasterAgent,
) : ViewModel() {

    // ── ① 累计画像区：init 加载一次，只需要人格数据 ──
    private val _personality = MutableStateFlow<PersonalityBundle?>(null)
    val personality: StateFlow<PersonalityBundle?> = _personality.asStateFlow()

    // ── ② 筛选轴 ──
    private val _dimension = MutableStateFlow(Dimension.OVERVIEW)
    val dimension: StateFlow<Dimension> = _dimension.asStateFlow()
    fun selectDimension(d: Dimension) { _dimension.value = d }

    private val _timeRange = MutableStateFlow(NarrativeTimeRange.MONTH)
    val timeRange: StateFlow<NarrativeTimeRange> = _timeRange.asStateFlow()
    fun selectTimeRange(range: NarrativeTimeRange) { _timeRange.value = range }

    // ── ③ 时间窗口数据（两轴交叉） ──
    private val _windowed = MutableStateFlow(WindowedBundle())
    val windowed: StateFlow<WindowedBundle> = _windowed.asStateFlow()

    init {
        loadPersonality()
        viewModelScope.launch {
            combine(dimension, timeRange) { d, t -> d to t }.collect { (d, t) ->
                loadWindowed(d, t)
            }
        }
    }

    private suspend fun loadPersonality() {
        val card = runCatching { masterAgent.userMusicPersonalityCard() }.getOrNull()
        val narrative = runCatching { masterAgent.userNarrativeState() }.getOrNull()
        _personality.value = PersonalityBundle(card, narrative)
    }

    private suspend fun loadWindowed(dim: Dimension, range: NarrativeTimeRange) {
        val days = range.toDays()
        val repo = musicRepository

        when (dim) {
            Dimension.OVERVIEW -> {
                val analytics = runCatching { repo.getWindowedAnalytics(days) }.getOrNull()
                val source = runCatching { repo.getWindowedSourceBreakdown(days) }.getOrDefault(emptyMap())
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

    /** 兜底刷新（外部需要强制重拉时调用）。 */
    fun refresh() {
        viewModelScope.launch {
            loadPersonality()
            loadWindowed(dimension.value, timeRange.value)
        }
    }
}

data class PersonalityBundle(
    val card: PersonalityCardComposer.PersonalityCard?,
    val narrative: ProfileNarrativeState?,
)
```

**关键变更清单：**
- 去掉 `getUserUsageDataUseCase` 参数
- 去掉 `_profileAnalytics` / `profileAnalytics`
- 去掉 `_reportBundle` / `reportBundle`
- 去掉旧 `loadAnalytics()` / `loadTimeSensitive()` / `loadPersonality()` 三方法 → 新 `loadWindowed(dim, range)`
- 去掉 `musicRepository` 默认 null → 强制注入
- combine 双轴收集替换旧单轴 collect

- [ ] **Step 2: Run 编译验证**

```bash
.\gradlew.bat :shared-ui:compileKotlinDesktop 2>&1 | Select-String "BUILD|FAIL|error" | Select-Object -Last 10
```
如果有 error，修复 import 或类型不匹配。

- [ ] **Step 3: Commit**

```bash
git add shared-ui/src/commonMain/kotlin/com/hearablemusic/player/ui/settings/viewmodel/UserUsageDataViewModel.kt
git commit -m "feat(vm): UserUsageDataViewModel 重构 — combine 双轴收集 + WindowedBundle"
```

---

### Task 6: Koin DI 收紧（三端）

**Files:**
- Modify: `shared-ui/src/androidMain/kotlin/com/hearablemusic/player/ui/di/UiKoinModule.kt`
- Modify: `shared-ui/src/desktopMain/kotlin/com/hearablemusic/player/ui/di/DesktopUiKoinModule.kt`
- Modify: `shared-ui/src/iosMain/kotlin/com/hearablemusic/player/ui/di/IosUiKoinModule.kt`

- [ ] **Step 1: 三端统一改 UserUsageDataViewModel 注册**

当前（上一轮我们改成了这样）：
```kotlin
viewModel { UserUsageDataViewModel(get(), get<MasterAgent>(), get<MusicRepository>()) }
```

改成：
```kotlin
viewModel { UserUsageDataViewModel(
    get<com.hmp.domain.music.MusicRepository>(),
    get<com.hmp.domain.agent.runtime.MasterAgent>(),
) }
```

**注意参数顺序：** 新 ViewModel 构造函数签名是 `(MusicRepository, MasterAgent)`，不是 `(UseCase, MasterAgent, MusicRepository)`。三端统一。

- [ ] **Step 2: Run Android 编译验证**

```bash
.\gradlew.bat :android:app:assembleDebug 2>&1 | Select-String "BUILD|FAIL|error" | Select-Object -Last 5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared-ui/src/androidMain/kotlin/com/hearablemusic/player/ui/di/UiKoinModule.kt
git add shared-ui/src/desktopMain/kotlin/com/hearablemusic/player/ui/di/DesktopUiKoinModule.kt
git add shared-ui/src/iosMain/kotlin/com/hearablemusic/player/ui/di/IosUiKoinModule.kt
git commit -m "fix(di): UserUsageDataViewModel 三端 Koin 注册更新参数顺序"
```

---

### Task 7: UI 重排 — DimensionSelector + OverviewWindowContent + TasteWindowContent + RankingWindowContent + RecentWindowContent

**Files:**
- Modify: `shared-ui/src/commonMain/kotlin/com/hearablemusic/player/ui/settings/pages/UserUsageDataScreen.kt`

**上下文：** 当前文件 1241 行，已有组件：OverviewCard, TasteCard, RankingAndHistoryCard, RecentHistoryBlock, PersonalitySection, TimeRangeSwitcher, ReportNarrativeCard, HourlyChart, InsightPill, PlaySourcePieChart, RateProgressRow, LabelStackedBarWithLegend, UsageListItem, RecentPlaybackItem, SectionHeader。

**Plan：**
1. 保留 PersonalitySection（顶部累计画像用）
2. 保留 TimeRangeSwitcher（时间筛选轴）
3. 保留 HourlyChart（复用）
4. 保留 LabelStackedBarWithLegend / RateProgressRow / RecentPlaybackItem（子组件复用）
5. 新增 DimensionSelector（维度筛选轴）
6. 新增 OverviewWindowContent（叙事段 + 窗口 Overview + 来源饼图）
7. 新增 TasteWindowContent（窗口 Top 口味）
8. 新增 RankingWindowContent（窗口 Top5）
9. 新增 RecentWindowContent（窗口最近播放）
10. 页面主结构重排（Column → ProfileSection → Divider → DimensionSelector + TimeRangeSwitcher → when(dimension) 分发）
11. **旧 OverviewCard / ReportNarrativeCard / InsightPill / PlaySourcePieChart / RankingAndHistoryCard / RecentHistoryBlock / TasteCard 内部逻辑有价值的提取复用，其余直接删除**

- [ ] **Step 1: 新增 DimensionSelector（SegmentedControl 风格）**

放在 TimeRangeSwitcher 旁边，两个筛选轴上下排列。复用项目已有的 SegmentedControl 组件（如果有），没有就用 Row + 选中背景 + Text 实现简易版。

```kotlin
@Composable
private fun DimensionSelector(
    selected: Dimension,
    onSelect: (Dimension) -> Unit,
) {
    val dimens = LocalHMPDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
    ) {
        Dimension.values().forEach { dim ->
            val isSelected = dim == selected
            Surface(
                shape = RoundedCornerShape(dimens.corner.md),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onSelect(dim) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = dim.zhLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = dimens.spacing.sm),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
```

- [ ] **Step 2: 新增 OverviewWindowContent**

叙事段（自然语言）+ 窗口 Overview 大数字 + 完播率/跳过率进度条 + 播放来源饼图。合并旧 OverviewCard + ReportNarrativeCard + PlaySourcePieChart 的逻辑。

```kotlin
@Composable
private fun OverviewWindowContent(
    narrative: HelloReportNarrativeEntity?,
    analytics: WindowedUsageAnalytics?,
    sourceBreakdown: Map<String, Int>,
    rangeLabel: String,
) {
    val dimens = LocalHMPDimens.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spacing.lg)) {
        // 叙事段（如果有）
        narrative?.let { ReportNarrativeCard(text = it.narrative, rangeLabel = it.timeRange) }

        Spacer(modifier = Modifier.height(dimens.spacing.md))

        // 窗口大数字
        analytics?.let { a ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(dimens.corner.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            ) {
                Column(modifier = Modifier.padding(dimens.spacing.lg)) {
                    Text(
                        text = "${rangeLabel} 窗口统计",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(dimens.spacing.sm))
                    Text(
                        text = "听了 ${a.totalListeningMinutes} 分钟",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(dimens.spacing.md))
                    RateProgressRow(label = "完播率", rate = a.completionRate, valueLabel = "%.0f%%".format(a.completionRate * 100))
                    Spacer(modifier = Modifier.height(dimens.spacing.xs))
                    RateProgressRow(label = "跳过率", rate = a.skipRate, valueLabel = "%.0f%%".format(a.skipRate * 100))
                }
            }
        }

        // 播放来源饼图（非空才显示）
        if (sourceBreakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(dimens.spacing.md))
            PlaySourcePieChart(entries = sourceBreakdown.entries)
        }
    }
}
```

> PlaySourcePieChart 的旧实现可以直接复用（不删），因为它只是 UI 组件，数据源从 `getWindowedSourceBreakdown` 来。

- [ ] **Step 3: 新增 TasteWindowContent**

复用旧 TasteCard 内部的 `LabelStackedBarWithLegend` 三次（GENRE / MOOD / SCENARIO），各自一个小标题。

```kotlin
@Composable
private fun TasteWindowContent(
    topGenres: List<LabelCountEntry>,
    topMoods: List<LabelCountEntry>,
    topScenarios: List<LabelCountEntry>,
    rangeLabel: String,
) {
    val dimens = LocalHMPDimens.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spacing.lg)) {
        topGenres.takeIf { it.isNotEmpty() }?.let { entries ->
            SectionHeader(title = "流派（${rangeLabel}）")
            LabelStackedBarWithLegend(entries = entries)
            Spacer(modifier = Modifier.height(dimens.spacing.lg))
        }
        topMoods.takeIf { it.isNotEmpty() }?.let { entries ->
            SectionHeader(title = "情绪（${rangeLabel}）")
            LabelStackedBarWithLegend(entries = entries)
            Spacer(modifier = Modifier.height(dimens.spacing.lg))
        }
        topScenarios.takeIf { it.isNotEmpty() }?.let { entries ->
            SectionHeader(title = "场景（${rangeLabel}）")
            LabelStackedBarWithLegend(entries = entries)
        }
    }
}
```

- [ ] **Step 4: 新增 RankingWindowContent**

简单 Top5 列表。

```kotlin
@Composable
private fun RankingWindowContent(
    topSongs: List<TopPlayedEntry>,
    rangeLabel: String,
) {
    val dimens = LocalHMPDimens.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spacing.lg)) {
        SectionHeader(title = "Top 播放（${rangeLabel}）")
        topSongs.forEachIndexed { index, song ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = dimens.spacing.xs),
                shape = RoundedCornerShape(dimens.corner.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(dimens.spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(32.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${song.playCount} 次",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (topSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(dimens.spacing.xl), contentAlignment = Alignment.Center) {
                Text(
                    text = "该时间窗口内暂无播放数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

- [ ] **Step 5: 新增 RecentWindowContent**

复用旧 RecentPlaybackItem 组件。

```kotlin
@Composable
private fun RecentWindowContent(
    items: List<RecentPlaybackEntry>,
    rangeLabel: String,
) {
    val dimens = LocalHMPDimens.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spacing.lg)) {
        SectionHeader(title = "最近播放（${rangeLabel}）")
        items.forEach { entry ->
            RecentPlaybackItem(
                title = entry.title,
                artist = entry.artist,
                timestamp = entry.playedAt,
                isCompleted = entry.isCompleted,
            )
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(dimens.spacing.xl), contentAlignment = Alignment.Center) {
                Text(
                    text = "该时间窗口内暂无播放记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

- [ ] **Step 6: 页面主结构重排**

找到 `UserUsageDataScreen` 主函数（L94 附近），把 Column 内部从：

```kotlin
TimeRangeSwitcher(...)
reportBundle.narrative?.let { ReportNarrativeCard(...) }
OverviewCard(...)
HourlyChart(...)
TasteCard(...)
RankingAndHistoryCard(...)
RecentHistoryBlock(...)
```

改成：

```kotlin
// ═══════════ 固定置顶：累计画像（永不随筛选变）═══════════
personality?.let { ProfileSection(personality = it) }

HorizontalDivider(modifier = Modifier.padding(vertical = dimens.spacing.sm))

// ═══════════ 筛选轴 ═══════════
DimensionSelector(
    selected = dimension,
    onSelect = { viewModel.selectDimension(it) }
)
TimeRangeSwitcher(
    selected = timeRange,
    onSelect = { viewModel.selectTimeRange(it) }
)

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
    Dimension.HOUR -> {
        if (windowed.hourlyDistribution.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spacing.lg)) {
                SectionHeader(title = "时段分布（${timeRange.zhName()}）")
                HourlyChart(rows = windowed.hourlyDistribution)
            }
        }
    }
    Dimension.RANK -> RankingWindowContent(
        topSongs = windowed.topSongs,
        rangeLabel = timeRange.zhName(),
    )
    Dimension.RECENT -> RecentWindowContent(
        items = windowed.recentPlayback,
        rangeLabel = timeRange.zhName(),
    )
}
```

**注意 StateFlow collect：**
```kotlin
val dimension by viewModel.dimension.collectAsState()
val timeRange by viewModel.timeRange.collectAsState()
val windowed by viewModel.windowed.collectAsState()
val personality by viewModel.personality.collectAsState()
```

**去掉旧的：**
```kotlin
// 删除旧 analytics collect
val analyticsState by viewModel.uiState.collectAsState()
val reportBundle by viewModel.reportBundle.collectAsState()
```

- [ ] **Step 7: 删除旧 OverviewCard / ReportNarrativeCard / TasteCard / RankingAndHistoryCard / RecentHistoryBlock / InsightPill 的完整函数定义**

这些组件的 UI 逻辑已经被 Window 版组件覆盖或拆成子组件复用。但 **RateProgressRow / PlaySourcePieChart / SectionHeader / LabelStackedBarWithLegend / RecentPlaybackItem / HourlyChart 保留**——它们被新组件复用。

旧组件删除后文件行数应该从 1241 行降到约 800-900 行（新组件加了约 300 行，旧组件删了约 600 行）。

- [ ] **Step 8: Run 编译验证（Android + Desktop）**

```bash
.\gradlew.bat :android:app:assembleDebug 2>&1 | Select-String "BUILD|FAIL|error" | Select-Object -Last 10
```
Expected: BUILD SUCCESSFUL

如果有 composable 参数不匹配或 import 错误，逐个修复。

- [ ] **Step 9: Commit**

```bash
git add shared-ui/src/commonMain/kotlin/com/hearablemusic/player/ui/settings/pages/UserUsageDataScreen.kt
git commit -m "feat(ui): UserUsageDataScreen 重排 — DimensionSelector + 5 个 Window 内容组件"
```

---

### Task 8: 清理 + Build + Test + 实机验证

- [ ] **Step 1: 全量编译（Android + Desktop shared 模块）**

```bash
.\gradlew.bat compileAll 2>&1 | Select-String "BUILD|FAIL|error:" | Select-Object -Last 10
```
Expected: 所有模块 BUILD SUCCESSFUL

- [ ] **Step 2: desktopTest 全绿**

```bash
.\gradlew.bat :shared:desktopTest 2>&1 | Select-String "BUILD|FAIL|test|passed|failed" | Select-Object -Last 10
```
Expected: BUILD SUCCESSFUL, 0 failed tests

- [ ] **Step 3: Android 安装 + 实机验证**

```bash
.\gradlew.bat :android:app:installDebug 2>&1 | Select-String "BUILD" | Select-Object -Last 1
```
启动后手动验证：
1. ProfileSection（人格卡 + 画像叙事散文）可见
2. DimensionSelector（概览/口味/时段/排行/最近）5 个 Tab
3. TimeRangeSwitcher（日/周/月/年/全部）5 个选项
4. 切维度 Tab → 内容区正确切换（概览=叙事段+窗口统计+饼图，口味=三个 Label 分布，时段=柱图，排行=Top5，最近=最近播放列表）
5. 切时间 → 内容数字真实变化
6. 切到「全部 + 排行」→ 全曲库累计 Top5

- [ ] **Step 4: Commit 最终**

```bash
git add -A
git commit -m "feat: UserUsageDataScreen 双轴筛选重构 — DAO 方案 B 窗口查询"
```

---

## 自检（Spec 覆盖检查）

| Spec § | 对应 Task | 状态 |
|---|---|---|
| §3 页面结构 | Task 7 Step 6 | ✅ |
| §4 DAO SQL（方案 B） | Task 1 + Task 2 | ✅ |
| §5 Repository 层 | Task 4 | ✅ |
| §6 ViewModel 重构 | Task 5 | ✅ |
| §7 UI 重排 + 新组件 | Task 7 Step 1-6 | ✅ |
| §8 Koin DI 收紧 | Task 6 | ✅ |
| §9 改动汇总 | 每个 Task 的 Commit 步骤 | ✅ |
| §11 验收标准 | Task 8 Step 3 | ✅ |

**DAO 不碰 Room 版本号** — 所有 Task 都不改 AppDatabase.kt 的 `version = 8`，零 Migration。

**PersonalitySection 数据源不变** — 顶部累计画像只走 MasterAgent.userMemory，不走 Windowed 查询。
