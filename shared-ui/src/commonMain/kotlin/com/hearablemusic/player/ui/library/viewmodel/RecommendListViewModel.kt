package com.hearablemusic.player.ui.library.viewmodel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.agent.card.RecommendItem
import com.hmp.domain.agent.card.RecommendListPayload
import com.hmp.domain.agent.card.RecommendSource
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.music.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 推荐列表页整屏状态（7.5 P1）。
 *
 * `payload` 是 MasterAgent 的固定转发流投影；`items` 是它按 trackId 展开后的渲染数据。
 * 此前两步都在 composable 的 `produceState` 里做，UI 直接调 `MusicRepository`，
 * 现在收进 VM：payload 一来就 hydrate，UI 只订阅唯一出口。
 */
@Stable
data class RecommendListUiState(
    val payload: RecommendListPayload? = null,
    val items: List<RecommendItem> = emptyList(),
)

/** 推荐页一次性效果：入队/定位完成后由组合点导航到播放页。 */
sealed interface RecommendListEffect {
    data object NavigateToPlayer : RecommendListEffect
}

class RecommendListViewModel(
    private val masterAgent: MasterAgent,
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendListUiState())
    val uiState: StateFlow<RecommendListUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<RecommendListEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<RecommendListEffect> = _effect.asSharedFlow()

    private var collectJob: Job? = null

    /**
     * 导航参数由 Screen 在 `LaunchedEffect(source)` 里上送。
     * 不把 `source` 放进构造参数：Koin 的 graph verification 无法解析 `parametersOf` 提供的类型，
     * 会让三端 `GraphVerificationTest` 报 MissingKoinDefinitionException。
     */
    fun onSourceChanged(source: RecommendSource) {
        collectJob?.cancel()
        val payloadFlow = if (source == RecommendSource.DAILY) {
            masterAgent.dailyRecommendList
        } else {
            masterAgent.privateRecommendList
        }
        collectJob = viewModelScope.launch {
            payloadFlow.collect { payload ->
                _uiState.update {
                    it.copy(payload = payload, items = hydrate(payload))
                }
            }
        }
    }

    /**
     * 点单曲 / 播放全部：追加到现有队列（不清空）→ 定位播放 → 发导航效果。
     * 原来整段编排在 composable 的 `scope.launch` 里。
     */
    fun onPlayFrom(index: Int) {
        val items = _uiState.value.items
        if (items.isEmpty()) return
        viewModelScope.launch {
            items.forEach { playbackController.addToPlaylist(it.musicInfo) }
            playbackController.playAt(items[index.coerceIn(0, items.lastIndex)].musicInfo)
            _effect.emit(RecommendListEffect.NavigateToPlayer)
        }
    }

    /** 列表只持久化 id + 按语，这里按 id 批量取回曲目再拼回渲染条目。 */
    private suspend fun hydrate(payload: RecommendListPayload?): List<RecommendItem> {
        if (payload == null || payload.items.isEmpty()) return emptyList()
        val infos = runCatching { musicRepository.getMusicInfoByIds(payload.items.map { it.trackId }) }
            .getOrNull().orEmpty().associateBy { it.music.id }
        return payload.items.mapNotNull { rec ->
            infos[rec.trackId]?.let { RecommendItem(it, rec.reason, payload.source, payload.phase) }
        }
    }
}
