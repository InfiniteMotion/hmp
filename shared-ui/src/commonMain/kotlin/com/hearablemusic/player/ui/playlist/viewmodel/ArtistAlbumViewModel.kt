package com.hearablemusic.player.ui.playlist.viewmodel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.util.LoadState
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 艺人 / 专辑浏览页整屏状态（7.5 P1）：标题与异步列表收进单一出口。 */
@Stable
data class ArtistAlbumUiState(
    val title: String = "",
    val musicList: LoadState<List<MusicInfo>> = LoadState.Idle,
)

/**
 * 艺术家/专辑浏览页的页面级 ViewModel。
 * 从 PlaylistViewModel 中拆出，ArtistScreen 与 AlbumScreen 各自持有独立实例。
 *
 * 7.5 P1：原来的 4 个散装 StateFlow 收成 [artistUiState] / [albumUiState] 两个聚合出口，
 * 每屏只订阅自己那一个；拆成两个而不是共用一个，是为了不依赖"两屏实例必然隔离"这一前提。
 */
class ArtistAlbumViewModel(
    private val getAllMusicUseCase: GetAllMusicUseCase,
    private val playbackController: PlaybackController,
) : ViewModel() {

    /** 播放：suspend 调用归 VM（不再借组合 scope，离屏即取消）。 */
    fun onPlay(musicInfo: MusicInfo) {
        viewModelScope.launch { playbackController.playWith(musicInfo) }
    }

    private val _selectedArtistName = MutableStateFlow("")
    private val _selectedArtistMusicListState = MutableStateFlow<LoadState<List<MusicInfo>>>(LoadState.Idle)
    private val _selectedAlbumName = MutableStateFlow("")
    private val _selectedAlbumMusicListState = MutableStateFlow<LoadState<List<MusicInfo>>>(LoadState.Idle)

    val artistUiState: StateFlow<ArtistAlbumUiState> = combine(
        _selectedArtistName,
        _selectedArtistMusicListState,
    ) { name, musicList ->
        ArtistAlbumUiState(title = name, musicList = musicList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistAlbumUiState())

    val albumUiState: StateFlow<ArtistAlbumUiState> = combine(
        _selectedAlbumName,
        _selectedAlbumMusicListState,
    ) { name, musicList ->
        ArtistAlbumUiState(title = name, musicList = musicList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistAlbumUiState())

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
