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

        when (dim) {
            Dimension.OVERVIEW -> {
                val analytics = runCatching { repo.getWindowedAnalytics(days) }.getOrNull()
                val source = runCatching { repo.getWindowedSourceBreakdown(days) }.getOrDefault(emptyMap())
                _windowed.value = WindowedBundle(
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
