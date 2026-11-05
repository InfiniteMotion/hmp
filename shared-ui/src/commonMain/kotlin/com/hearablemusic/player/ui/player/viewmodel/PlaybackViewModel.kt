package com.hearablemusic.player.ui.player.viewmodel

import androidx.lifecycle.ViewModel
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.flow.StateFlow

/** 播放控制薄包装：转发 [PlaybackController] 冻结接口的状态流。 */
class PlaybackViewModel(
    private val playbackController: PlaybackController,
) : ViewModel() {

    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying
    val currentPosition: StateFlow<Long> = playbackController.currentPosition
    val duration: StateFlow<Long> = playbackController.duration
    val playbackMode: StateFlow<PlaybackMode> = playbackController.playbackMode
    val currentIndex: StateFlow<Int> = playbackController.currentIndex
    val currentPlayingMusic: StateFlow<MusicInfo?> = playbackController.currentPlayingMusic
    val timerRemaining: StateFlow<Long?> = playbackController.timerRemaining
    val isMiniPlayerVisible: StateFlow<Boolean> = playbackController.isMiniPlayerVisible

    fun playOrResume() = playbackController.playOrResume()
    fun pauseMusic() = playbackController.pauseMusic()
    fun playNext() = playbackController.playNext()
    fun playPrevious() = playbackController.playPrevious()
    fun seekTo(position: Long) = playbackController.seekTo(position)
    fun togglePlaybackModeByOrder() = playbackController.togglePlaybackModeByOrder()
    fun startTimer(minutes: Int) = playbackController.startTimer(minutes)
    fun cancelTimer() = playbackController.cancelTimer()
    fun startProgressTracking() = playbackController.startProgressTracking()
    fun stopProgressTracking() = playbackController.stopProgressTracking()
    fun setMiniPlayerVisible(visible: Boolean) = playbackController.setMiniPlayerVisible(visible)
}
