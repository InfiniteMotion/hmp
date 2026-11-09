package com.hearablemusic.player.ui.common.dialogs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.added_to_next_play
import com.hearablemusic.player.ui.generated.resources.added_to_playlist
import com.hearablemusic.player.ui.generated.resources.load_playlists_failed
import com.hearablemusic.player.ui.generated.resources.music_deleted
import com.hearablemusic.player.ui.generated.resources.no_playlists_available
import com.hearablemusic.player.ui.generated.resources.removed_from_current_list
import com.hearablemusic.player.ui.generated.resources.select_playlist
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.UserInfo
import com.hmp.domain.music.usecase.RemoveFromLibraryUseCase
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * 统一弹窗 ViewModel。
 *
 * - 文案经 CMP 挂起 getString 获取；歌单名校验为挂起函数，
 *   调用点需在协程内调用。
 * - 音乐详情菜单在渲染端按 menuConfig 组装，VM 不再吐 Compose 资源与 lambda。
 */
class DialogViewModel(
    private val playbackController: PlaybackController,
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val settingsRepository: SettingsRepository,
    private val dialogManager: DialogManager,
    private val removeFromLibraryUseCase: RemoveFromLibraryUseCase,
) : ViewModel() {

    // 统一弹窗状态：唯一出口。子状态由消费端从 activeDialog 解构（不再派生 4 条重复 StateFlow）。
    private val _activeDialog = MutableStateFlow<DialogUiState?>(null)
    val activeDialog: StateFlow<DialogUiState?> = _activeDialog.asStateFlow()

    private var pendingMusicForPlaylist: MusicInfo? = null

    data class PlaylistPickerDialogState(
        val playlists: List<Playlist>,
        val title: String,
    )

    sealed class DialogUiState {
        data class MusicDetail(val state: MusicDetailState) : DialogUiState()
        data class PlaylistPicker(val state: PlaylistPickerDialogState) : DialogUiState()
        data object Timer : DialogUiState()
    }

    // 显示音乐详情弹窗
    fun showMusicDetailDialog(musicInfo: MusicInfo, menuConfig: MusicDetailMenuConfig = MusicDetailMenuConfig()) {
        // 获取最新收藏状态
        viewModelScope.launch {
            val isLiked = playbackController.getCurrentLikedStatus(musicInfo.music.id)

            val updatedMusicInfo = musicInfo.copy(
                userInfo = musicInfo.userInfo?.copy(
                    liked = isLiked,
                ) ?: UserInfo(
                    id = musicInfo.music.id,
                    liked = isLiked,
                ),
            )

            _activeDialog.value = DialogUiState.MusicDetail(
                MusicDetailState(
                    musicInfo = updatedMusicInfo,
                    isVisible = true,
                    menuConfig = menuConfig,
                ),
            )
        }
    }

    // 关闭音乐详情弹窗
    fun dismissMusicDetailDialog() {
        if (_activeDialog.value is DialogUiState.MusicDetail) {
            _activeDialog.value = null
        }
    }

    // 切换收藏状态
    fun toggleFavorite() {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        val currentLiked = musicInfo.userInfo?.liked ?: false
        val newLiked = !currentLiked

        // 更新本地状态
        val updatedMusicInfo = musicInfo.copy(
            userInfo = musicInfo.userInfo?.copy(
                liked = newLiked,
            ) ?: UserInfo(
                id = musicInfo.music.id,
                liked = newLiked,
            ),
        )

        // 更新ViewModel状态（收藏位只在 musicInfo 上，不另存副本）
        updateMusicDetailState { it.copy(musicInfo = updatedMusicInfo) }

        // 调用播放服务更新收藏状态
        playbackController.updateMusicLikedStatus(musicInfo, newLiked)
    }

    // 播放音乐（播完由 VM 关掉弹窗，调用方不再拼「dismiss + onDismiss」）
    fun playMusic() {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        viewModelScope.launch {
            playbackController.playWith(musicInfo)
            dismissMusicDetailDialog()
        }
    }

    // 分享音乐
    fun shareMusic() {
        val currentState = (_activeDialog.value as? DialogViewModel.DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        dialogManager.shareMusic(
            title = musicInfo.music.title,
            artist = musicInfo.music.artist,
            album = musicInfo.music.album,
            filePath = musicInfo.music.path,
        )
        dismissMusicDetailDialog()
    }

    // 查看详情
    fun viewDetail() {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo
        dialogManager.showDialog(DialogEvent.NavRequest(Routes.Library.SongDetail(musicInfo.music.id)))
        dismissMusicDetailDialog()
    }

    // 编辑标签
    fun editTags() {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo
        dialogManager.showDialog(DialogEvent.NavRequest(Routes.Library.EditMusicTags(musicInfo.music.id)))
        dismissMusicDetailDialog()
    }

    // 艺术家 / 专辑：与详情页、编辑页共用同一条 NavRequest 出口（不再让 UI 持 RouteNavigator）
    fun viewArtist() {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        dialogManager.showDialog(DialogEvent.NavRequest(Routes.Library.Artist(currentState.musicInfo.music.artist)))
        dismissMusicDetailDialog()
    }

    fun viewAlbum() {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        dialogManager.showDialog(DialogEvent.NavRequest(Routes.Library.Album(currentState.musicInfo.music.album)))
        dismissMusicDetailDialog()
    }

    // 添加到指定音乐列表：弹窗链路内部消化，调用方不需要 continuation
    fun addToSpecificPlaylist() {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        showPlaylistPickerForMusic(currentState.musicInfo)
    }

    // 显示播放列表选择弹窗
    private fun showPlaylistPickerForMusic(musicInfo: MusicInfo) {
        viewModelScope.launch {
            try {
                // 获取所有用户自定义播放列表（排除系统播放列表）
                val allPlaylists = managePlaylistUseCase.getAllPlaylists()
                val currentId = settingsRepository.getCurrentPlaylistId()
                val likedId = settingsRepository.getLikedPlaylistId()
                val recentId = settingsRepository.getRecentPlaylistId()
                val systemIds = setOfNotNull(currentId, likedId, recentId)
                val playlists = allPlaylists.filter { it.id !in systemIds }

                if (playlists.isEmpty()) {
                    dialogManager.showMessage(getString(Res.string.no_playlists_available))
                    return@launch
                }

                // 显示播放列表选择弹窗（详情弹窗自己完成落库）
                showPlaylistPickerForMusic(playlists = playlists, musicInfo = musicInfo)
            } catch (e: Exception) {
                dialogManager.showMessage(e.message ?: getString(Res.string.load_playlists_failed))
            }
        }
    }

    // 下一首播放
    fun playNext() {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        playbackController.addToNextPlay(musicInfo)
        viewModelScope.launch {
            dialogManager.showMessage(getString(Res.string.added_to_next_play))
        }
        dismissMusicDetailDialog()
    }

    // 从当前列表移除
    fun removeFromCurrentPlaylist() {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        playbackController.removeFromPlaylist(musicInfo)
        viewModelScope.launch {
            dialogManager.showMessage(getString(Res.string.removed_from_current_list))
        }
        dismissMusicDetailDialog()
    }

    // 删除音乐
    fun deleteMusic() {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        viewModelScope.launch {
            // 从当前播放列表中移除
            playbackController.removeFromPlaylist(musicInfo)
            // 使用标记删除法从音乐库中删除
            removeFromLibraryUseCase(listOf(musicInfo.music.id))
            dialogManager.showMessage(getString(Res.string.music_deleted))
            dismissMusicDetailDialog()
        }
    }

    /** 音乐详情「加入歌单」：全局弹窗自己完成选歌单→落库，不向调用屏借回调。 */
    private suspend fun showPlaylistPickerForMusic(playlists: List<Playlist>, musicInfo: MusicInfo) {
        pendingMusicForPlaylist = musicInfo
        _activeDialog.value = DialogUiState.PlaylistPicker(
            PlaylistPickerDialogState(
                playlists = playlists,
                title = getString(Res.string.select_playlist),
            ),
        )
    }

    fun confirmPlaylistPickerDialog(playlist: Playlist) {
        val musicInfo = pendingMusicForPlaylist
        pendingMusicForPlaylist = null
        if (_activeDialog.value is DialogUiState.PlaylistPicker) {
            _activeDialog.value = null
        }
        if (musicInfo == null) return
        viewModelScope.launch {
            managePlaylistUseCase.addToPlaylist(playlistId = playlist.id, musicId = musicInfo.music.id)
            dialogManager.showMessage(getString(Res.string.added_to_playlist))
        }
    }

    fun dismissPlaylistPickerDialog() {
        pendingMusicForPlaylist = null
        if (_activeDialog.value is DialogUiState.PlaylistPicker) {
            _activeDialog.value = null
        }
    }

    /** 定时器弹窗不再夹带 lambda：确认动作由 VM 直接落到播放控制器。 */
    fun showTimerDialog() {
        _activeDialog.value = DialogUiState.Timer
    }

    fun confirmTimerDialog(minutes: Int) {
        playbackController.startTimer(minutes)
        dismissTimerDialog()
    }

    fun dismissTimerDialog() {
        if (_activeDialog.value is DialogUiState.Timer) {
            _activeDialog.value = null
        }
    }

    private inline fun updateMusicDetailState(
        transform: (MusicDetailState) -> MusicDetailState,
    ) {
        val current = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        _activeDialog.value = DialogUiState.MusicDetail(transform(current))
    }

    data class MusicDetailState(
        val musicInfo: MusicInfo,
        val isVisible: Boolean,
        val menuConfig: MusicDetailMenuConfig = MusicDetailMenuConfig(),
    )

    // 音乐详情弹窗菜单配置
    data class MusicDetailMenuConfig(
        val showAddToSpecificPlaylist: Boolean = true,
        val showShare: Boolean = true,
        val showViewDetail: Boolean = true,
        val showEditTags: Boolean = true,
        val showPlayNext: Boolean = false,
        val showRemoveFromCurrentPlaylist: Boolean = false,
        val showDelete: Boolean = false,
    )
}
