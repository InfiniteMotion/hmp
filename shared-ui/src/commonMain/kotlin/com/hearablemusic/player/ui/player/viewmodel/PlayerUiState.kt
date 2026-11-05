package com.hearablemusic.player.ui.player.viewmodel

import androidx.compose.runtime.Stable
import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.music.MusicInfo

@Stable
data class PlayerUiState(
    val musicInfo: MusicInfo? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val remainingTime: Long? = null,
    val isLiked: Boolean = false,
    val lyrics: String? = null,
    val playlist: List<MusicInfo> = emptyList(),
    val currentIndex: Int = 0,
)
