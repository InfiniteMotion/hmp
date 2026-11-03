package com.hearablemusic.player.ui.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hearablemusic.player.ui.platform.PlaybackController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 播放队列 ViewModel。
 *
 * 2026-11-03：智能歌单生成器退役 —— 生成/权重保存职责已删除，
 * 这里只剩"对当前播放队列的操作"。生成由 Hello 的每日两单与电台承担。
 */
class PlaylistQueueViewModel(
    private val playbackController: PlaybackController,
) : ViewModel() {

    val currentPlaylist: StateFlow<List<MusicInfo>> = playbackController.currentPlaylist
    val currentIndex: StateFlow<Int> = playbackController.currentIndex
    val currentPlayingMusic: StateFlow<MusicInfo?> = playbackController.currentPlayingMusic
    val likeStatus: StateFlow<Boolean> = playbackController.likeStatus
    val currentMusicLabels: StateFlow<List<MusicLabel?>> = playbackController.currentMusicLabels
    val currentMusicLyrics: StateFlow<String?> = playbackController.currentMusicLyrics

    fun playWith(musicInfo: MusicInfo) = viewModelScope.launch { playbackController.playWith(musicInfo) }
    fun playAt(musicInfo: MusicInfo) = playbackController.playAt(musicInfo)
    fun addToPlaylist(musicInfo: MusicInfo) = playbackController.addToPlaylist(musicInfo)
    fun removeFromPlaylist(musicInfo: MusicInfo) = playbackController.removeFromPlaylist(musicInfo)
    fun moveToTop(musicInfo: MusicInfo) = playbackController.moveToTop(musicInfo)
    fun clearPlaylist() = playbackController.clearPlaylist()
    fun addAllToPlaylistInOrder(playlist: List<MusicInfo>) = playbackController.addAllToPlaylistInOrder(playlist)
    fun addAllToPlaylistByShuffle(playlist: List<MusicInfo>) = playbackController.addAllToPlaylistByShuffle(playlist)
    fun playHeartMode() = playbackController.playHeartMode()
    fun updateMusicLikedStatus(musicInfo: MusicInfo, liked: Boolean) = playbackController.updateMusicLikedStatus(musicInfo, liked)
    fun getLikedStatus(musicId: Long) = playbackController.getLikedStatus(musicId)
    fun getMusicLabels(musicId: Long) = playbackController.getMusicLabels(musicId)
    fun getMusicLyrics(musicId: Long) = playbackController.getMusicLyrics(musicId)
}
