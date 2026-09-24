package com.hearablemusic.player.ui.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.usecase.GetDailyMusicRecommendationUseCase
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hearablemusic.player.ui.common.util.UiState
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.music_not_found
import com.hearablemusic.player.ui.generated.resources.unknown_error
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class SongDetailData(
    val musicInfo: MusicInfo,
    val labels: List<MusicLabel?>,
    val playbackHistory: List<PlaybackHistory> = emptyList()
)

class SongDetailViewModel(
    private val getDailyRecommendationUseCase: GetDailyMusicRecommendationUseCase,
    private val playbackHistoryUseCase: PlaybackHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<SongDetailData>>(UiState.Idle)
    val uiState: StateFlow<UiState<SongDetailData>> = _uiState.asStateFlow()

    private var currentMusicId: Long? = null

    fun loadSongDetail(musicId: Long) {
        currentMusicId = musicId
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val recommendation = getDailyRecommendationUseCase.getMusicWithExtraById(musicId)
                if (recommendation?.musicInfo != null) {
                    val historyFlow = playbackHistoryUseCase.getPlaybackHistory(musicId, 5)

                    historyFlow.collectLatest { history ->
                        _uiState.value = UiState.Success(
                            SongDetailData(
                                musicInfo = recommendation.musicInfo!!,
                                labels = recommendation.labels,
                                playbackHistory = history
                            )
                        )
                    }
                } else {
                    _uiState.value = UiState.Error(getString(Res.string.music_not_found))
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: getString(Res.string.unknown_error))
            }
        }
    }

    fun retry() {
        currentMusicId?.let { loadSongDetail(it) }
    }
}
