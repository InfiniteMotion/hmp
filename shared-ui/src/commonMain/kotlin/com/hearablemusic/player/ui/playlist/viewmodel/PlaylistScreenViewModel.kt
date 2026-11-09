package com.hearablemusic.player.ui.playlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.util.LoadState
import com.hearablemusic.player.ui.common.util.nowEpochMillis
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.playlist.renameWithError
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.MusicLabelUseCase
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistScreenUiState(
    val playlistName: String = "",
    val playlist: LoadState<List<MusicInfo>> = LoadState.Idle,
    val playlistMeta: Playlist? = null,
    val selectedPlaylistId: Long? = null,
    val isCustomPlaylist: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showDescriptionDialog: Boolean = false,
    val musicPicker: MusicPickerUiState? = null,
    val playlistPicker: PlaylistPickerUiState? = null,
)

/** 歌单详情屏的一次性效果（§4：候选弹框数据与「已添加」提示不用 StateFlow<Result?> + clear）。 */
sealed interface PlaylistEffect {
    data class SongsAdded(val count: Int) : PlaylistEffect
}

/**
 * 歌单详情屏的屏级 VM（绑定 `Routes.Playlist.Playlist` / `CustomPlaylist` 的 entry）。
 *
 * 选中歌单、弹窗可见性、条目增删排序都只服务本屏，随 entry 一起销毁 ——
 * 不再需要 `onPlaylistScreenLeft()` 这类「离开时手动清」的补丁。
 * 列表刷新靠两处：条目流（`getMusicInfoInPlaylist` 本身是 Flow）与歌单元数据（每次写后重取）。
 */
class PlaylistScreenViewModel(
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val musicLabelUseCase: MusicLabelUseCase,
    private val settingsRepository: SettingsRepository,
    private val getAllMusicUseCase: GetAllMusicUseCase,
    private val playbackController: PlaybackController,
) : ViewModel() {

    /** 播放：suspend 调用归 VM（不再借组合 scope，离屏即取消）。 */
    fun onPlay(musicInfo: MusicInfo) {
        viewModelScope.launch { playbackController.playWith(musicInfo) }
    }

    private val _selectedPlaylistName = MutableStateFlow("")
    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    private val _selectedPlaylistMeta = MutableStateFlow<Playlist?>(null)
    private val _isCustomPlaylist = MutableStateFlow(false)
    private val _selectedPlaylistState = MutableStateFlow<LoadState<List<MusicInfo>>>(LoadState.Idle)

    /** 屏级交互态；与数据态合并成唯一出口 [uiState]。 */
    private data class ScreenInteraction(
        val showRenameDialog: Boolean = false,
        val showDescriptionDialog: Boolean = false,
        val musicPicker: MusicPickerUiState? = null,
        val playlistPicker: PlaylistPickerUiState? = null,
    )

    private val _interaction = MutableStateFlow(ScreenInteraction())

    private val dataPart = combine(
        _selectedPlaylistName,
        _selectedPlaylistState,
        _selectedPlaylistMeta,
        _selectedPlaylistId,
        _isCustomPlaylist,
    ) { name, playlistState, meta, id, isCustom ->
        PlaylistScreenUiState(
            playlistName = name,
            playlist = playlistState,
            playlistMeta = meta,
            selectedPlaylistId = id,
            isCustomPlaylist = isCustom,
        )
    }

    /** 本屏唯一状态出口：散装数据源全部是私有实现。 */
    val uiState: StateFlow<PlaylistScreenUiState> = combine(
        dataPart,
        _interaction,
    ) { base, interaction ->
        base.copy(
            showRenameDialog = interaction.showRenameDialog,
            showDescriptionDialog = interaction.showDescriptionDialog,
            musicPicker = interaction.musicPicker,
            playlistPicker = interaction.playlistPicker,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PlaylistScreenUiState(),
    )

    private val _effect = MutableSharedFlow<PlaylistEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<PlaylistEffect> = _effect.asSharedFlow()

    // ── 弹窗 ───────────────────────────────────────────────────────────
    fun onRenameRequested() {
        _interaction.update { it.copy(showRenameDialog = true) }
    }

    fun onRenameDismissed() {
        _interaction.update { it.copy(showRenameDialog = false) }
    }

    fun onRenameConfirmed(name: String) {
        val id = _selectedPlaylistId.value ?: return
        _interaction.update { it.copy(showRenameDialog = false) }
        if (name.isBlank()) return
        viewModelScope.launch {
            val trimmed = name.trim()
            val rejected = managePlaylistUseCase.renameWithError(id, trimmed)
            if (rejected != null) return@launch
            if (_selectedPlaylistId.value == id) {
                _selectedPlaylistName.value = trimmed
                refreshSelectedPlaylistMeta()
            }
        }
    }

    fun onDescriptionRequested() {
        _interaction.update { it.copy(showDescriptionDialog = true) }
    }

    fun onDescriptionDismissed() {
        _interaction.update { it.copy(showDescriptionDialog = false) }
    }

    fun onDescriptionConfirmed(text: String) {
        val id = _selectedPlaylistId.value ?: return
        _interaction.update { it.copy(showDescriptionDialog = false) }
        viewModelScope.launch {
            managePlaylistUseCase.updatePlaylistDescription(id, text.trim().ifEmpty { null })
            if (id == _selectedPlaylistId.value) refreshSelectedPlaylistMeta()
        }
    }

    // ── 加载 ───────────────────────────────────────────────────────────
    private var currentPlaylistJob: Job? = null

    fun loadPlaylistById(playlistId: Long) {
        currentPlaylistJob?.cancel()
        _selectedPlaylistId.value = playlistId
        _selectedPlaylistState.value = LoadState.Loading

        currentPlaylistJob = viewModelScope.launch {
            val meta = managePlaylistUseCase.getPlaylistMeta(playlistId)
            _selectedPlaylistMeta.value = meta
            _selectedPlaylistName.value = meta?.name ?: ""
            val currentId = settingsRepository.getCurrentPlaylistId()
            val likedId = settingsRepository.getLikedPlaylistId()
            val recentId = settingsRepository.getRecentPlaylistId()
            _isCustomPlaylist.value = playlistId != currentId && playlistId != likedId && playlistId != recentId

            managePlaylistUseCase.getMusicInfoInPlaylist(playlistId)
                .catch {
                    _selectedPlaylistState.value = LoadState.Error(it.message ?: "Failed to load playlist")
                }
                .collect {
                    if (_selectedPlaylistId.value == playlistId) {
                        _selectedPlaylistState.value = if (it.isEmpty()) {
                            LoadState.Empty
                        } else {
                            LoadState.Success(it)
                        }
                    }
                }
        }
    }

    /**
     * 按 [LabelName] 枚举名（`"ROCK"` / `"POP"` …）加载标签页。
     *
     * `label` 的唯一来源是 `Routes.Playlist.Playlist(label.name)`（`ListScreen` 的标签卡片）；
     * 系统歌单（默认 / 心动 / 最近播放）走的是 `Routes.Playlist.CustomPlaylist(id)` → [loadPlaylistById]。
     * 所以这里**不按歌单名反查 id** —— 那三个中文资源键分支从未命中，属 D3-16 记录的死代码，已删除。
     */
    fun getSelectedPlaylist(label: String) {
        currentPlaylistJob?.cancel()
        // 同步清掉「选中的自建歌单」：否则新标签已上屏、trailing/删除态仍指向上一个 id 一帧。
        _selectedPlaylistId.value = null
        _selectedPlaylistMeta.value = null
        _isCustomPlaylist.value = false
        _selectedPlaylistName.value = label
        _selectedPlaylistState.value = LoadState.Loading
        currentPlaylistJob = viewModelScope.launch {
            val labelEnum = LabelName.match(label)
            if (labelEnum != null) {
                try {
                    val musicList = musicLabelUseCase.getMusicListByLabel(labelEnum)
                    _selectedPlaylistState.value = if (musicList.isEmpty()) {
                        LoadState.Empty
                    } else {
                        LoadState.Success(musicList)
                    }
                } catch (e: Exception) {
                    _selectedPlaylistState.value = LoadState.Error(e.message ?: "Failed to load playlist")
                }
            } else {
                _selectedPlaylistState.value = LoadState.Empty
            }
        }
    }

    private fun refreshSelectedPlaylistMeta() {
        val id = _selectedPlaylistId.value ?: return
        viewModelScope.launch {
            val meta = managePlaylistUseCase.getPlaylistMeta(id)
            _selectedPlaylistMeta.value = meta
            meta?.name?.let { _selectedPlaylistName.value = it }
        }
    }

    // ── 条目动作 ───────────────────────────────────────────────────────
    fun removeItemFromPlaylist(musicId: Long, playlistId: Long) {
        viewModelScope.launch {
            managePlaylistUseCase.removeItemFromPlaylist(musicId, playlistId)
            if (playlistId == _selectedPlaylistId.value) refreshSelectedPlaylistMeta()
        }
    }

    /**
     * 7.5 P1：「添加歌曲」入口 —— 全量加载、按现有曲目过滤出候选，都在 VM 完成；
     * UI 只负责弹框展示与上送选中结果（原来过滤逻辑写在回调闭包里）。
     */
    fun onAddSongsRequested() {
        viewModelScope.launch {
            try {
                val allMusic = getAllMusicUseCase(MusicSortKey.TITLE, SortDirection.ASC)
                val existingIds = (_selectedPlaylistState.value as? LoadState.Success)
                    ?.data?.map { it.music.id }?.toSet().orEmpty()
                _interaction.update {
                    it.copy(
                        musicPicker = MusicPickerUiState(
                            allMusic = allMusic.filter { music -> music.music.id !in existingIds },
                            selectedIds = emptySet(),
                        ),
                    )
                }
            } catch (e: Exception) {
                HmpLog.w(LogTag.UiCommon) { "添加歌曲候选加载失败：${e.message}" }
            }
        }
    }

    fun onSongsPicked(ids: Set<Long>) {
        val playlistId = _selectedPlaylistId.value ?: run {
            onMusicPickerDismissed()
            return
        }
        _interaction.update { it.copy(musicPicker = null) }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { managePlaylistUseCase.addToPlaylist(playlistId, it) }
            if (playlistId == _selectedPlaylistId.value) refreshSelectedPlaylistMeta()
            _effect.emit(PlaylistEffect.SongsAdded(ids.size))
        }
    }

    fun onMusicPickerDismissed() {
        _interaction.update { it.copy(musicPicker = null) }
    }

    fun onBatchAddToPlaylistRequested(ids: Set<Long>, playlists: List<Playlist>) {
        if (ids.isEmpty() || playlists.isEmpty()) return
        _interaction.update { it.copy(playlistPicker = PlaylistPickerUiState(playlists, ids.toList())) }
    }

    fun onPlaylistPickerDismissed() {
        _interaction.update { it.copy(playlistPicker = null) }
    }

    fun onPlaylistPicked(playlistId: Long) {
        val pending = _interaction.value.playlistPicker ?: return
        _interaction.update { it.copy(playlistPicker = null) }
        viewModelScope.launch {
            pending.pendingSongIds.forEach { managePlaylistUseCase.addToPlaylist(playlistId, it) }
            _effect.emit(PlaylistEffect.SongsAdded(pending.pendingSongIds.size))
        }
    }

    /**
     * 重排。返回值不能被丢掉（D3-04）：仓库只在"入参正好覆盖当前可见曲目"时才动手。
     *
     * UI 传的本来就是完整列表，所以正常路径不会失败；会失败的是列表在两次读取之间被并发改过
     * ——那种情况下静默返回就是"用户排完发现顺序没动"，所以至少要留下一条日志。
     * （提示文案要 13 语种的资源键，不是一包里顺手该加的。）
     */
    fun reorderPlaylistItems(playlistId: Long, orderedMusicIds: List<Long>) {
        viewModelScope.launch {
            val applied = managePlaylistUseCase.reorderPlaylistItems(playlistId, orderedMusicIds)
            if (!applied) {
                HmpLog.w(LogTag.UiCommon) { "重排被拒：入参与歌单 $playlistId 当前可见曲目不一致，未做任何改动" }
                return@launch
            }
            if (playlistId == _selectedPlaylistId.value) refreshSelectedPlaylistMeta()
        }
    }

    fun recordPlaylistPlay(playlistId: Long) {
        viewModelScope.launch {
            managePlaylistUseCase.incrementPlaylistPlayCount(playlistId)
            managePlaylistUseCase.setPlaylistLastPlayedAt(playlistId, nowEpochMillis())
            if (playlistId == _selectedPlaylistId.value) refreshSelectedPlaylistMeta()
        }
    }
}
