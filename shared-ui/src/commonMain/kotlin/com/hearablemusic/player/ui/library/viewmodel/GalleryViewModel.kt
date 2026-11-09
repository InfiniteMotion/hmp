package com.hearablemusic.player.ui.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistPickerUiState
import com.hmp.domain.agent.port.LibraryMutatedNotifier
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.RemoveFromLibraryUseCase
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 音乐库首页（Gallery）的整屏状态（7.5 屏级 VM）。
 *
 * 列表与排序都归本屏所有：曲库是 Room 的可观察查询，删除 / 恢复 / 扫描落库
 * 由数据层推回来，屏级 VM 不再「写完手动重查一次」。
 * `pendingRemove` / `pendingBatchRemove` 两组确认链合成「有值即显示」的单一字段；
 * `deleteToken` 只做列表重建的动画 key（批量删除后强制重放退场动画）。
 */
data class GalleryUiState(
    val musicList: List<MusicInfo> = emptyList(),
    val orderBy: MusicSortKey = MusicSortKey.TITLE,
    val orderType: SortDirection = SortDirection.ASC,
    val pendingRemove: MusicInfo? = null,
    val pendingBatchRemove: Set<Long>? = null,
    val deleteToken: Int = 0,
    val playlistPicker: PlaylistPickerUiState? = null,
)

/** 屏级一次性效果：批量加入歌单完成后由组合点弹提示。 */
sealed interface GalleryEffect {
    data class SongsAdded(val count: Int) : GalleryEffect
}

/**
 * 曲库首页的屏级 ViewModel（绑定 Tabs entry，四个 Tab 常驻是设计前提）。
 *
 * 删除动作也在这里：它只服务本屏的两条确认链，且写完不需要任何人手动刷新
 * —— `observeAllMusic` 会把新列表推回来。
 */
class GalleryViewModel(
    private val getAllMusicUseCase: GetAllMusicUseCase,
    private val removeFromLibraryUseCase: RemoveFromLibraryUseCase,
    private val libraryMutated: LibraryMutatedNotifier,
    private val playbackController: PlaybackController,
    private val managePlaylistUseCase: ManagePlaylistUseCase,
) : ViewModel() {

    private val _effect = MutableSharedFlow<GalleryEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<GalleryEffect> = _effect.asSharedFlow()

    private val _orderBy = MutableStateFlow(MusicSortKey.TITLE)
    private val _orderType = MutableStateFlow(SortDirection.ASC)

    private data class Confirmations(
        val pendingRemove: MusicInfo? = null,
        val pendingBatchRemove: Set<Long>? = null,
        val deleteToken: Int = 0,
        val playlistPicker: PlaylistPickerUiState? = null,
    )

    private val _confirmations = MutableStateFlow(Confirmations())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sortedMusic: StateFlow<List<MusicInfo>> = combine(_orderBy, _orderType) { key, direction ->
        key to direction
    }.flatMapLatest { (key, direction) ->
        getAllMusicUseCase.observeAllMusic(key, direction)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<GalleryUiState> = combine(
        _orderBy,
        _orderType,
        sortedMusic,
        _confirmations,
    ) { key, direction, music, confirmations ->
        GalleryUiState(
            musicList = music,
            orderBy = key,
            orderType = direction,
            pendingRemove = confirmations.pendingRemove,
            pendingBatchRemove = confirmations.pendingBatchRemove,
            deleteToken = confirmations.deleteToken,
            playlistPicker = confirmations.playlistPicker,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GalleryUiState())

    /** 播放：suspend 调用归 VM（不再借组合 scope，离屏即取消）。 */
    fun onPlay(musicInfo: MusicInfo) {
        viewModelScope.launch { playbackController.playWith(musicInfo) }
    }

    /** 排序键与方向是原子动作：查询由 [sortedMusic] 自己重启，UI 不再分两步。 */
    fun onOrderByChanged(orderBy: MusicSortKey) {
        _orderBy.value = orderBy
    }

    fun onOrderTypeChanged(orderType: SortDirection) {
        _orderType.value = orderType
    }

    fun onRemoveRequested(info: MusicInfo) {
        _confirmations.update { it.copy(pendingRemove = info) }
    }

    fun onRemoveDismissed() {
        _confirmations.update { it.copy(pendingRemove = null) }
    }

    fun onRemoveConfirmed() {
        val info = _confirmations.value.pendingRemove ?: return
        _confirmations.update { it.copy(pendingRemove = null) }
        removeTracks(listOf(info.music.id))
    }

    fun onBatchRemoveRequested(ids: Set<Long>) {
        if (ids.isEmpty()) return
        _confirmations.update { it.copy(pendingBatchRemove = ids) }
    }

    fun onBatchRemoveDismissed() {
        _confirmations.update { it.copy(pendingBatchRemove = null) }
    }

    fun onBatchRemoveConfirmed() {
        val ids = _confirmations.value.pendingBatchRemove ?: return
        _confirmations.update { it.copy(pendingBatchRemove = null, deleteToken = it.deleteToken + 1) }
        removeTracks(ids.toList())
    }

    /** 批量加入歌单：候选来自调用屏（它已订阅自建歌单投影），确认后由本 VM 落库。 */
    fun onBatchAddToPlaylistRequested(ids: Set<Long>, playlists: List<Playlist>) {
        if (ids.isEmpty() || playlists.isEmpty()) return
        _confirmations.update { it.copy(playlistPicker = PlaylistPickerUiState(playlists, ids.toList())) }
    }

    fun onPlaylistPickerDismissed() {
        _confirmations.update { it.copy(playlistPicker = null) }
    }

    fun onPlaylistPicked(playlistId: Long) {
        val pending = _confirmations.value.playlistPicker ?: return
        _confirmations.update { it.copy(playlistPicker = null) }
        viewModelScope.launch {
            pending.pendingSongIds.forEach { songId -> managePlaylistUseCase.addToPlaylist(playlistId, songId) }
            _effect.emit(GalleryEffect.SongsAdded(pending.pendingSongIds.size))
        }
    }

    private fun removeTracks(ids: List<Long>) {
        viewModelScope.launch {
            removeFromLibraryUseCase(ids)
            // 库变了就重建画像；列表本身由 Room 回流，不需要手动重查
            libraryMutated.onLibraryMutated()
        }
    }
}
