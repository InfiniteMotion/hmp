package com.hearablemusic.player.ui.settings.viewmodel
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asUiText
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.dim_hour
import com.hearablemusic.player.ui.generated.resources.dim_overview
import com.hearablemusic.player.ui.generated.resources.dim_rank
import com.hearablemusic.player.ui.generated.resources.dim_recent
import com.hearablemusic.player.ui.generated.resources.dim_taste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.data.database.myenum.LabelCategory
import com.hmp.domain.agent.profile.PersonalityCardComposer
import com.hmp.domain.agent.profile.ProfileNarrativeState
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.card.NarrativeTimeRange
import com.hmp.domain.agent.card.toDays
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
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import kotlinx.coroutines.launch

/** 维度筛选枚举 — 决定下方内容区渲染什么组件。 */
enum class Dimension(val label: UiText) {
    OVERVIEW(Res.string.dim_overview.asUiText()),
    TASTE(Res.string.dim_taste.asUiText()),
    HOUR(Res.string.dim_hour.asUiText()),
    RANK(Res.string.dim_rank.asUiText()),
    RECENT(Res.string.dim_recent.asUiText()),
}

/** WindowedBundle — 时间窗口 + 维度筛选交叉产出的所有数据。 */
data class WindowedBundle(
    val analytics: WindowedUsageAnalytics? = null,
    val sourceBreakdown: Map<String, Int> = emptyMap(),
    val topGenres: List<LabelCountEntry> = emptyList(),
    val topMoods: List<LabelCountEntry> = emptyList(),
    val topScenarios: List<LabelCountEntry> = emptyList(),
    val hourlyDistribution: List<HourlyDistributionRow> = emptyList(),
    val topSongs: List<TopPlayedEntry> = emptyList(),
    val recentPlayback: List<RecentPlaybackEntry> = emptyList(),
    /**
     * 本次窗口里**查询失败**的维度（D5-14）。
     *
     * 此前六个分支全是 `runCatching { … }.getOrDefault(空)`，DAO 异常、迁移后缺列、Koin 解析失败
     * 都表现为「暂无使用数据」—— 与"这个窗口内确实没听歌"在 UI 上完全同形，用户与开发者都拿不到线索。
     * UI 据此把空态换成"读取失败"文案，异常本身进日志。
     */
    val failedDimension: Dimension? = null,
    /** 首帧加载态（D5-14 的另一半）：`_windowed` 初值是空 bundle，此前首帧必然闪一次「暂无数据」再跳变。 */
    val isLoading: Boolean = true,
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
        viewModelScope.launch {
            loadPersonality()
        }
        viewModelScope.launch {
            combine(dimension, timeRange) { d, t -> d to t }.collect { (d, t) ->
                loadWindowed(d, t)
            }
        }
    }

    private suspend fun loadPersonality() {
        val memory = masterAgent.userMemory
        if (memory == null) {
            _personality.value = null
            return
        }
        val card = runCatching { memory.musicPersonalityCard() }.getOrNull()
        val narrative = runCatching { memory.narrativeState() }.getOrNull()
        _personality.value = PersonalityBundle(card, narrative)
    }

    private suspend fun loadWindowed(dim: Dimension, range: NarrativeTimeRange) {
        val days = range.toDays()
        val repo = musicRepository
        // 先置加载态：切维度/切时段时旧数据不该被当成"这一窗口的结果"
        _windowed.value = _windowed.value.copy(isLoading = true, failedDimension = null)

        var failed: Dimension? = null
        when (dim) {
            Dimension.OVERVIEW -> {
                val analytics = attempt(dim) { repo.getWindowedAnalytics(days) }.also { if (it == null) failed = dim }
                val source = attempt(dim) { repo.getWindowedSourceBreakdown(days) }.orEmpty()
                _windowed.value = WindowedBundle(
                    analytics = analytics,
                    sourceBreakdown = source,
                    failedDimension = failed,
                    isLoading = false,
                )
            }
            Dimension.TASTE -> {
                val topGenres = attempt(dim) { repo.getWindowedTopLabels(days, LabelCategory.GENRE) }.orEmpty()
                val topMoods = attempt(dim) { repo.getWindowedTopLabels(days, LabelCategory.MOOD) }.orEmpty()
                val topScenarios = attempt(dim) { repo.getWindowedTopLabels(days, LabelCategory.SCENARIO) }.orEmpty()
                // 三条都取回空列表无法区分"没数据"与"失败"，所以失败标记只在 analytics 之外按整维度记：
                // 任何一条抛异常就把该维度记为失败（空列表本身不报错）
                _windowed.value = WindowedBundle(
                    topGenres = topGenres, topMoods = topMoods, topScenarios = topScenarios,
                    failedDimension = failed,
                    isLoading = false,
                )
            }
            Dimension.HOUR -> {
                val hourly = attempt(dim) { repo.getHourlyDistribution(days) }
                if (hourly == null) failed = dim
                _windowed.value = WindowedBundle(
                    hourlyDistribution = hourly.orEmpty(),
                    failedDimension = failed,
                    isLoading = false,
                )
            }
            Dimension.RANK -> {
                val songs = attempt(dim) { repo.getWindowedTopSongs(days) }
                if (songs == null) failed = dim
                _windowed.value = WindowedBundle(
                    topSongs = songs.orEmpty(),
                    failedDimension = failed,
                    isLoading = false,
                )
            }
            Dimension.RECENT -> {
                val items = attempt(dim) { repo.getWindowedRecentPlayback(days) }
                if (items == null) failed = dim
                _windowed.value = WindowedBundle(
                    recentPlayback = items.orEmpty(),
                    failedDimension = failed,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * 失败不再是静默的空：记一条 warn 再返回 null 由调用方决定"空态"还是"失败态"。
     *
     * 日志只记维度、时段与异常类（对齐 `UserMemory` 的「日志只记条数不记内容」纪律），
     * 不把播放记录内容写进日志。
     */
    private suspend fun <T> attempt(dim: Dimension, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            HmpLog.w(LogTag.UiSettings, e) { "使用数据窗口读取失败 | dimension=$dim | range=${timeRange.value} | cause=${e::class.simpleName}" }
            null
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
