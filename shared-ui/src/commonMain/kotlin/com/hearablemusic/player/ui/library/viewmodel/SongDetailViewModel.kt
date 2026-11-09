package com.hearablemusic.player.ui.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.util.LoadState
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.music_not_found
import com.hearablemusic.player.ui.generated.resources.unknown_error
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.usecase.GetDailyMusicRecommendationUseCase
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class SongDetailData(
    val musicInfo: MusicInfo,
    val labels: List<MusicLabel?>,
    val playbackHistory: List<PlaybackHistory> = emptyList(),
)

/** 详情页 Tab 的 id。放在 VM 侧，状态默认值与渲染分支共用同一份常量。 */
object SongDetailSection {
    const val USER = "user"
    const val INTRO = "intro"
    const val LYRICS = "lyrics"
}

/**
 * 详情页整屏状态（7.5 P1）。[detail] 是一次加载的结果，只作字段；
 * [selectedSection] 是原 composable 里的 `rememberSaveable`，收进 VM 后随 VM 跨配置存活。
 */
data class SongDetailUiState(
    val detail: LoadState<SongDetailData> = LoadState.Idle,
    val selectedSection: String = SongDetailSection.INTRO,
)

class SongDetailViewModel(
    private val getDailyRecommendationUseCase: GetDailyMusicRecommendationUseCase,
    private val playbackHistoryUseCase: PlaybackHistoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongDetailUiState())
    val uiState: StateFlow<SongDetailUiState> = _uiState.asStateFlow()

    private var currentMusicId: Long? = null

    /** 当前曲目的常驻历史订阅。切歌 / 重试前必须取消，否则旧曲的历史会写进新曲的 detail。 */
    private var detailJob: Job? = null

    fun loadSongDetail(musicId: Long) {
        currentMusicId = musicId
        detailJob?.cancel()
        _uiState.update { it.copy(detail = LoadState.Loading) }

        detailJob = viewModelScope.launch {
            try {
                val recommendation = getDailyRecommendationUseCase.getMusicWithExtraById(musicId)
                if (recommendation?.musicInfo != null) {
                    val historyFlow = playbackHistoryUseCase.getPlaybackHistory(musicId, 5)

                    // 播放历史是持续订阅的流：每次只更新 Success 里的数据，不重发 Loading，
                    // 否则页面会跟着历史变化反复闪加载态。见 UdfConventions.md §2。
                    historyFlow.collectLatest { history ->
                        _uiState.update {
                            it.copy(
                                detail = LoadState.Success(
                                    SongDetailData(
                                        musicInfo = recommendation.musicInfo!!,
                                        labels = recommendation.labels,
                                        playbackHistory = history,
                                    ),
                                ),
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(detail = LoadState.Error(getString(Res.string.music_not_found)))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        detail = LoadState.Error(e.message ?: getString(Res.string.unknown_error)),
                    )
                }
            }
        }
    }

    /** Tab 切换是用户动作，只上送，不返回业务结果。 */
    fun onSectionSelected(section: String) {
        _uiState.update { it.copy(selectedSection = section) }
    }

    fun onRetry() {
        currentMusicId?.let { loadSongDetail(it) }
    }
}
