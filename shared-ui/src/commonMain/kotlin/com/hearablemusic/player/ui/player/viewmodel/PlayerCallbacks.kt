package com.hearablemusic.player.ui.player.viewmodel

import com.hmp.domain.music.MusicInfo

interface PlayerCallbacks {
    fun onSeek(position: Long)
    fun onPlayPause()
    fun onNext()
    fun onPrevious()
    fun onPlaybackModeChange()
    fun onFavorite()
    fun onShowTimerDialog()
    fun onHeartMode()
    fun onArtistClick(artistName: String)
    fun onClearPlaylist()
    fun onPlayItem(musicInfo: MusicInfo)
    fun onMoveToTop(musicInfo: MusicInfo)
    fun onRemoveFromPlaylist(musicInfo: MusicInfo)
    fun onBackClick()
}
