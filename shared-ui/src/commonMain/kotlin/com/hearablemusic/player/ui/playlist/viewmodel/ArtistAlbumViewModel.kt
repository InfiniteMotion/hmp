package com.hearablemusic.player.ui.playlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.util.UiState
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
    private val _selectedArtistMusicListState = MutableStateFlow<UiState<List<MusicInfo>>>(UiState.Idle)
    val selectedArtistMusicListState: StateFlow<UiState<List<MusicInfo>>> = _selectedArtistMusicListState

    private val _selectedAlbumName = MutableStateFlow("")
    val selectedAlbumName: StateFlow<String> = _selectedAlbumName
    private val _selectedAlbumMusicListState = MutableStateFlow<UiState<List<MusicInfo>>>(UiState.Idle)
    val selectedAlbumMusicListState: StateFlow<UiState<List<MusicInfo>>> = _selectedAlbumMusicListState

    private var artistJob: Job? = null
    private var albumJob: Job? = null

    fun getSelectedArtistMusicList(artistName: String) {
        artistJob?.cancel()
        _selectedArtistName.value = artistName
        _selectedArtistMusicListState.value = UiState.Loading
        artistJob = viewModelScope.launch {
            try {
                val musicList = getAllMusicUseCase.getMusicListByArtist(artistName)
                _selectedArtistMusicListState.value = if (musicList.isEmpty()) {
                    UiState.Empty
                } else {
                    UiState.Success(musicList)
                }
            } catch (e: Exception) {
                _selectedArtistMusicListState.value = UiState.Error(e.message ?: "Failed to load artist music")
            }
        }
    }

    fun getSelectedAlbumMusicList(albumName: String) {
        albumJob?.cancel()
        _selectedAlbumName.value = albumName
        _selectedAlbumMusicListState.value = UiState.Loading
        albumJob = viewModelScope.launch {
            try {
                val musicList = getAllMusicUseCase.getMusicListByAlbum(albumName)
                _selectedAlbumMusicListState.value = if (musicList.isEmpty()) {
                    UiState.Empty
                } else {
                    UiState.Success(musicList)
                }
            } catch (e: Exception) {
                _selectedAlbumMusicListState.value = UiState.Error(e.message ?: "Failed to load album music")
            }
        }
    }
}
