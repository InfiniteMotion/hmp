package com.hearablemusic.player.ui.playlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.util.LoadState
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 艺术家/专辑浏览页的页面级 ViewModel。
 * 从 PlaylistViewModel 中拆出，ArtistScreen 与 AlbumScreen 各自持有独立实例。
 */
class ArtistAlbumViewModel(
    private val getAllMusicUseCase: GetAllMusicUseCase,
) : ViewModel() {

    private val _selectedArtistName = MutableStateFlow("")
    val selectedArtistName: StateFlow<String> = _selectedArtistName
    private val _selectedArtistMusicListState = MutableStateFlow<LoadState<List<MusicInfo>>>(LoadState.Idle)
    val selectedArtistMusicListState: StateFlow<LoadState<List<MusicInfo>>> = _selectedArtistMusicListState

    private val _selectedAlbumName = MutableStateFlow("")
    val selectedAlbumName: StateFlow<String> = _selectedAlbumName
    private val _selectedAlbumMusicListState = MutableStateFlow<LoadState<List<MusicInfo>>>(LoadState.Idle)
    val selectedAlbumMusicListState: StateFlow<LoadState<List<MusicInfo>>> = _selectedAlbumMusicListState

    private var artistJob: Job? = null
    private var albumJob: Job? = null

    fun getSelectedArtistMusicList(artistName: String) {
        artistJob?.cancel()
        _selectedArtistName.value = artistName
        _selectedArtistMusicListState.value = LoadState.Loading
        artistJob = viewModelScope.launch {
            try {
                val musicList = getAllMusicUseCase.getMusicListByArtist(artistName)
                _selectedArtistMusicListState.value = if (musicList.isEmpty()) {
                    LoadState.Empty
                } else {
                    LoadState.Success(musicList)
                }
            } catch (e: Exception) {
                _selectedArtistMusicListState.value = LoadState.Error(e.message ?: "Failed to load artist music")
            }
        }
    }

    fun getSelectedAlbumMusicList(albumName: String) {
        albumJob?.cancel()
        _selectedAlbumName.value = albumName
        _selectedAlbumMusicListState.value = LoadState.Loading
        albumJob = viewModelScope.launch {
            try {
                val musicList = getAllMusicUseCase.getMusicListByAlbum(albumName)
                _selectedAlbumMusicListState.value = if (musicList.isEmpty()) {
                    LoadState.Empty
                } else {
                    LoadState.Success(musicList)
                }
            } catch (e: Exception) {
                _selectedAlbumMusicListState.value = LoadState.Error(e.message ?: "Failed to load album music")
            }
        }
    }
}
