package com.hearablemusic.player.ui.playlist.viewmodel

import com.hmp.domain.playlist.Playlist

/** 「加入哪个歌单」弹窗的屏级状态：候选歌单 + 待加入的歌曲 id（确认后由发起屏自行落库）。 */
data class PlaylistPickerUiState(
    val playlists: List<Playlist>,
    val pendingSongIds: List<Long>,
)
