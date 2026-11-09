package com.hearablemusic.player.ui.library.viewmodel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.agent.card.RecommendListPayload
import com.hmp.domain.agent.card.RecommendSource
import com.hmp.domain.agent.quota.QuotaBucket
import com.hmp.domain.agent.quota.TokenQuotaGate
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.sub.radio.RadioCardState
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 首页（Home）整屏状态（7.5 P1 收尾：清屏级参数注入）。
 *
 * 原来 `HomeScreen` 里 `koinInject<MasterAgent>()` / `TokenQuotaGate` / `MusicRepository`
 * 三处直连业务对象，现已收进本 VM：两条推荐流与 hello 桶额度合成一个出口，
 * 按 id 查曲目的仓库调用也只在 VM 里发生。
 */
@Stable
data class HomeUiState(
    val dailyPayload: RecommendListPayload? = null,
    val privatePayload: RecommendListPayload? = null,
    val helloQuotaBlocked: Boolean = false,
    val radioState: RadioState? = null,
    val radioCard: RadioCardState = RadioCardState(theme = null, upcomingCount = 0, nextTitle = null, lastAdjust = null),
    val radioQuotaExhausted: Boolean = false,
)

/** 首页一次性效果（§4）：查询、入队、定位都在 VM，导航/提示经此发出。 */
sealed interface HomeEffect {
    data object NavigateToPlayer : HomeEffect

    /** 电台开启被额度拦截（判定在 VM，UI 只负责显示）。 */
    data object RadioQuotaExhausted : HomeEffect
}

class HomeViewModel(
    private val masterAgent: MasterAgent,
    quotaGate: TokenQuotaGate,
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _effect = MutableSharedFlow<HomeEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<HomeEffect> = _effect.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        masterAgent.dailyRecommendList,
        masterAgent.privateRecommendList,
        quotaGate.exhausted,
        masterAgent.radioState,
        masterAgent.radioCardState,
    ) { daily, privateList, exhausted, radio, radioCard ->
        HomeUiState(
            dailyPayload = daily,
            privatePayload = privateList,
            helloQuotaBlocked = QuotaBucket.HELLO in exhausted,
            radioState = radio,
            radioCard = radioCard,
            radioQuotaExhausted = QuotaBucket.RADIO in exhausted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /** 电台开关：关闭永远允许；开启时额度判定在 VM，拦截结果走 Effect。 */
    fun onToggleRadio(shouldStop: Boolean) {
        viewModelScope.launch {
            if (!shouldStop && uiState.value.radioQuotaExhausted) {
                _effect.emit(HomeEffect.RadioQuotaExhausted)
                return@launch
            }
            if (shouldStop) masterAgent.stopRadio() else masterAgent.startRadio()
        }
    }

    /**
     * 播放编排收进 VM（原来 UI 拿查询结果再逐个入队、定位、导航）：
     * 追加到现有队列尾部（addToPlaylist 内含整队列去重）再从首曲播起，成功后发导航效果。
     */
    fun onPlayRecommend(source: RecommendSource) {
        val payload = when (source) {
            RecommendSource.DAILY -> uiState.value.dailyPayload
            RecommendSource.PRIVATE -> uiState.value.privatePayload
        }
        val ids = payload?.items?.map { it.trackId }.orEmpty()
        if (ids.isEmpty()) return
        viewModelScope.launch { playAndNavigate(ids) }
    }

    fun onPlayCardTracks(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { playAndNavigate(ids) }
    }

    /** 按 id 批量取曲目：仅供本 VM 的播放编排使用（卡片补全已改由 HelloCardsViewModel 负责）。 */
    private suspend fun loadTracksByIds(ids: List<Long>): List<MusicInfo> {
        if (ids.isEmpty()) return emptyList()
        return runCatching { musicRepository.getMusicInfoByIds(ids) }.getOrNull().orEmpty()
    }

    private suspend fun playAndNavigate(ids: List<Long>) {
        val infos = loadTracksByIds(ids)
        if (infos.isNotEmpty()) {
            infos.forEach { playbackController.addToPlaylist(it) }
            playbackController.playAt(infos.first())
            _effect.emit(HomeEffect.NavigateToPlayer)
        }
    }
}
