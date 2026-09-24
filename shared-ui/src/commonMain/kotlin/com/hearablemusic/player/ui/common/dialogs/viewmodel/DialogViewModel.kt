package com.hearablemusic.player.ui.common.dialogs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asUiText
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.UserInfo
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.RemoveFromLibraryUseCase
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.add_songs_to_playlist_dialog
import com.hearablemusic.player.ui.generated.resources.add_to_specific_playlist
import com.hearablemusic.player.ui.generated.resources.added_to_next_play
import com.hearablemusic.player.ui.generated.resources.added_to_playlist
import com.hearablemusic.player.ui.generated.resources.create_playlist_failed
import com.hearablemusic.player.ui.generated.resources.delete
import com.hearablemusic.player.ui.generated.resources.edit_music_tags
import com.hearablemusic.player.ui.generated.resources.forward_end_fill
import com.hearablemusic.player.ui.generated.resources.load_playlists_failed
import com.hearablemusic.player.ui.generated.resources.load_songs_failed
import com.hearablemusic.player.ui.generated.resources.music
import com.hearablemusic.player.ui.generated.resources.music_deleted
import com.hearablemusic.player.ui.generated.resources.no_playlists_available
import com.hearablemusic.player.ui.generated.resources.playlist_created
import com.hearablemusic.player.ui.generated.resources.playlist_name_empty
import com.hearablemusic.player.ui.generated.resources.playlist_name_exists
import com.hearablemusic.player.ui.generated.resources.playlist_name_too_long
import com.hearablemusic.player.ui.generated.resources.playlist_updated
import com.hearablemusic.player.ui.generated.resources.play_next
import com.hearablemusic.player.ui.generated.resources.plus_square
import com.hearablemusic.player.ui.generated.resources.remove_from_current_playlist
import com.hearablemusic.player.ui.generated.resources.removed_from_current_list
import com.hearablemusic.player.ui.generated.resources.rename
import com.hearablemusic.player.ui.generated.resources.select_playlist
import com.hearablemusic.player.ui.generated.resources.share
import com.hearablemusic.player.ui.generated.resources.title_song_detail
import com.hearablemusic.player.ui.generated.resources.trash
import com.hearablemusic.player.ui.generated.resources.update_playlist_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString

/**
 * 统一弹窗 ViewModel。
 *
 * - 文案经 CMP 挂起 getString 获取；歌单名校验为挂起函数，
 *   调用点需在协程内调用。
 * - 菜单项为 Triple<DrawableResource, UiText, () -> Unit>，
 *   由渲染端 Compose 解析。
 */
class DialogViewModel(
    private val playbackController: PlaybackController,
    private val getAllMusicUseCase: GetAllMusicUseCase,
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val settingsRepository: SettingsRepository,
    private val dialogManager: DialogManager,
    private val removeFromLibraryUseCase: RemoveFromLibraryUseCase
) : ViewModel() {

    // 统一弹窗状态
    private val _activeDialog = MutableStateFlow<DialogUiState?>(null)
    val activeDialog: StateFlow<DialogUiState?> = _activeDialog.asStateFlow()

    // 向后兼容：按类型导出子状态，供现有组件复用
    val musicDetailState: StateFlow<MusicDetailState?> = activeDialog
        .map { (it as? DialogUiState.MusicDetail)?.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val createPlaylistState: StateFlow<CreatePlaylistDialogState?> = activeDialog
        .map { (it as? DialogUiState.CreatePlaylist)?.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val musicPickerState: StateFlow<MusicPickerDialogState?> = activeDialog
        .map { (it as? DialogUiState.MusicPicker)?.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val playlistPickerState: StateFlow<PlaylistPickerDialogState?> = activeDialog
        .map { (it as? DialogUiState.PlaylistPicker)?.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Timer Dialog 状态
    private val _showTimerDialog = MutableStateFlow<TimerDialogConfig?>(null)
    val showTimerDialog: StateFlow<TimerDialogConfig?> = _showTimerDialog.asStateFlow()

    // 收藏状态
    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked

    private var onPlaylistCreated: ((Long) -> Unit)? = null
    private var existingPlaylistNames: Set<String> = emptySet()
    private var onMusicPickerConfirm: ((Set<Long>) -> Unit)? = null
    private var onPlaylistPickerConfirm: ((Playlist) -> Unit)? = null
    private var pendingCreatePlaylistStateForPicker: CreatePlaylistDialogState? = null
    private var createDialogMusicLookup: Map<Long, MusicInfo> = emptyMap()

    data class CreatePlaylistDialogState(
        val name: String = "",
        val description: String = "",
        val pinAfterCreate: Boolean = false,
        val selectedSongIds: Set<Long> = emptySet(),
        val nameError: String? = null,
        val submitError: String? = null,
        val isSubmitting: Boolean = false,
        val isEditing: Boolean = false,
        val editingPlaylistId: Long? = null
    ) {
        val canSubmit: Boolean
            get() = !isSubmitting && nameError == null && name.trim().isNotEmpty()
    }

    data class MusicPickerDialogState(
        val allMusic: List<MusicInfo>,
        val selectedIds: Set<Long>,
        val title: String
    )

    data class PlaylistPickerDialogState(
        val playlists: List<Playlist>,
        val title: String
    )

    data class TimerDialogConfig(
        val onConfirm: (Int) -> Unit,
        val onDismiss: () -> Unit = {}
    )

    sealed class DialogUiState {
        data class MusicDetail(val state: MusicDetailState) : DialogUiState()
        data class CreatePlaylist(val state: CreatePlaylistDialogState) : DialogUiState()
        data class MusicPicker(val state: MusicPickerDialogState) : DialogUiState()
        data class PlaylistPicker(val state: PlaylistPickerDialogState) : DialogUiState()
        data class Timer(val state: TimerDialogConfig) : DialogUiState()
    }

    // 显示音乐详情弹窗
    fun showMusicDetailDialog(musicInfo: MusicInfo, menuConfig: MusicDetailMenuConfig = MusicDetailMenuConfig()) {
        // 获取最新收藏状态
        viewModelScope.launch {
            val isLiked = playbackController.getCurrentLikedStatus(musicInfo.music.id)
            _isLiked.value = isLiked

            val updatedMusicInfo = musicInfo.copy(
                userInfo = musicInfo.userInfo?.copy(
                    liked = isLiked
                ) ?: UserInfo(
                    id = musicInfo.music.id,
                    liked = isLiked
                )
            )

            _activeDialog.value = DialogUiState.MusicDetail(
                MusicDetailState(
                    musicInfo = updatedMusicInfo,
                    isVisible = true,
                    menuConfig = menuConfig
                )
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
                liked = newLiked
            ) ?: UserInfo(
                id = musicInfo.music.id,
                liked = newLiked
            )
        )

        // 更新ViewModel状态
        updateMusicDetailState { it.copy(musicInfo = updatedMusicInfo) }
        _isLiked.value = newLiked

        // 调用播放服务更新收藏状态
        playbackController.updateMusicLikedStatus(musicInfo, newLiked)
    }

    // 播放音乐
    fun playMusic(onPlayComplete: () -> Unit) {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        viewModelScope.launch {
            playbackController.playWith(musicInfo)
            onPlayComplete()
        }
    }

    // 添加到播放列表
    fun addToPlaylist(onAddComplete: () -> Unit) {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        playbackController.addToPlaylist(musicInfo)
        onAddComplete()
    }

    // 分享音乐
    fun shareMusic() {
        val currentState = (_activeDialog.value as? DialogViewModel.DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        dialogManager.shareMusic(
            title = musicInfo.music.title,
            artist = musicInfo.music.artist,
            album = musicInfo.music.album,
            filePath = musicInfo.music.path
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

    // 移除音乐
    fun removeMusic() {
        // 这里可以添加移除逻辑
        dismissMusicDetailDialog()
    }

    // 获取菜单选项（资源引用由渲染端 Compose 解析）
    fun getMenuOptions(onComplete: () -> Unit): List<Triple<DrawableResource, UiText, () -> Unit>> {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return emptyList()
        val menuConfig = currentState.menuConfig
        val menuOptions = mutableListOf<Triple<DrawableResource, UiText, () -> Unit>>()
        // 音乐详情
        if (menuConfig.showViewDetail) {
            menuOptions.add(Triple(Res.drawable.music, Res.string.title_song_detail.asUiText()) {
                viewDetail()
            })
        }

        // 分享
        if (menuConfig.showShare) {
            menuOptions.add(Triple(Res.drawable.share, Res.string.share.asUiText()) { shareMusic() })
        }

        // 编辑标签
        if (menuConfig.showEditTags) {
            menuOptions.add(Triple(Res.drawable.rename, Res.string.edit_music_tags.asUiText()) {
                editTags()
            })
        }

        // 添加到指定音乐列表
        if (menuConfig.showAddToSpecificPlaylist) {
            menuOptions.add(Triple(Res.drawable.plus_square, Res.string.add_to_specific_playlist.asUiText()) {
                addToSpecificPlaylist(
                    onComplete
                )
            })
        }

        // 下一首播放
        if (menuConfig.showPlayNext) {
            menuOptions.add(Triple(Res.drawable.forward_end_fill, Res.string.play_next.asUiText()) { playNext() })
        }

        // 从当前列表移除
        if (menuConfig.showRemoveFromCurrentPlaylist) {
            menuOptions.add(Triple(Res.drawable.trash, Res.string.remove_from_current_playlist.asUiText()) {
                removeFromCurrentPlaylist(
                    onComplete
                )
            })
        }

        // 删除
        if (menuConfig.showDelete) {
            menuOptions.add(Triple(Res.drawable.trash, Res.string.delete.asUiText()) { deleteMusic(onComplete) })
        }
        return menuOptions
    }

    // 添加到指定音乐列表
    fun addToSpecificPlaylist(onComplete: () -> Unit) {
        // 显示播放列表选择弹窗
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        // 显示播放列表选择弹窗
        showPlaylistPickerDialog(musicInfo, onComplete)
    }

    // 显示播放列表选择弹窗
    private fun showPlaylistPickerDialog(musicInfo: MusicInfo, onComplete: () -> Unit) {
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
                    onComplete()
                    return@launch
                }

                // 显示播放列表选择弹窗
                showPlaylistPickerDialog(
                    playlists = playlists,
                    title = getString(Res.string.select_playlist),
                    onConfirm = { playlist ->
                        viewModelScope.launch {
                            // 将歌曲添加到选择的播放列表
                            managePlaylistUseCase.addToPlaylist(
                                playlistId = playlist.id,
                                musicId = musicInfo.music.id,
                                musicPath = musicInfo.music.path
                            )
                            dialogManager.showMessage(getString(Res.string.added_to_playlist))
                            onComplete()
                        }
                    }
                )
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
    fun removeFromCurrentPlaylist(onComplete: () -> Unit) {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        playbackController.removeFromPlaylist(musicInfo)
        viewModelScope.launch {
            dialogManager.showMessage(getString(Res.string.removed_from_current_list))
        }
        onComplete()
    }

    // 删除音乐
    fun deleteMusic(onComplete: () -> Unit) {
        val currentState = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        val musicInfo = currentState.musicInfo

        viewModelScope.launch {
            // 从当前播放列表中移除
            playbackController.removeFromPlaylist(musicInfo)
            // 使用标记删除法从音乐库中删除
            removeFromLibraryUseCase(listOf(musicInfo.music.id))
            dialogManager.showMessage(getString(Res.string.music_deleted))
            onComplete()
        }
    }

    fun showCreatePlaylistDialog(onCreated: (Long) -> Unit) {
        onPlaylistCreated = onCreated
        _activeDialog.value = DialogUiState.CreatePlaylist(CreatePlaylistDialogState())
        viewModelScope.launch {
            existingPlaylistNames = managePlaylistUseCase.getAllPlaylists()
                .map { it.name.trim().lowercase() }
                .toSet()
            updateCreatePlaylistName("")
        }
    }

    fun showEditPlaylistDialog(
        playlist: Playlist,
        onUpdated: (Long) -> Unit
    ) {
        onPlaylistCreated = onUpdated
        viewModelScope.launch {
            val allPlaylists = managePlaylistUseCase.getAllPlaylists()
            existingPlaylistNames = allPlaylists
                .filter { it.id != playlist.id }
                .map { it.name.trim().lowercase() }
                .toSet()

            _activeDialog.value = DialogUiState.CreatePlaylist(
                CreatePlaylistDialogState(
                    name = playlist.name,
                    description = playlist.description ?: "",
                    pinAfterCreate = playlist.isPinned,
                    isEditing = true,
                    editingPlaylistId = playlist.id
                )
            )
        }
    }

    fun dismissCreatePlaylistDialog() {
        if (_activeDialog.value is DialogUiState.CreatePlaylist) {
            _activeDialog.value = null
        }
        onPlaylistCreated = null
        createDialogMusicLookup = emptyMap()
    }

    fun updateCreatePlaylistName(input: String) {
        viewModelScope.launch {
            updateCreatePlaylistState {
                it.copy(
                    name = input,
                    nameError = validatePlaylistName(input),
                    submitError = null
                )
            }
        }
    }

    fun updateCreatePlaylistDescription(input: String) {
        updateCreatePlaylistState {
            it.copy(
                description = input,
                submitError = null
            )
        }
    }

    fun setCreatePlaylistPinned(pinned: Boolean) {
        updateCreatePlaylistState {
            it.copy(pinAfterCreate = pinned)
        }
    }

    fun setCreatePlaylistSelectedSongs(selectedIds: Set<Long>) {
        updateCreatePlaylistState {
            it.copy(
                selectedSongIds = selectedIds,
                submitError = null
            )
        }
    }

    fun onCreatePlaylistAddSongsClick() {
        val current = (_activeDialog.value as? DialogUiState.CreatePlaylist)?.state ?: return
        pendingCreatePlaylistStateForPicker = current
        viewModelScope.launch {
            try {
                val allMusic = getAllMusicUseCase("title", "ASC")
                createDialogMusicLookup = allMusic.associateBy { it.music.id }
                showMusicPickerDialog(
                    allMusic = allMusic,
                    selectedIds = current.selectedSongIds,
                    title = getString(Res.string.add_songs_to_playlist_dialog),
                    onConfirm = { selectedIds ->
                        _activeDialog.value = DialogUiState.CreatePlaylist(
                            current.copy(
                                selectedSongIds = selectedIds,
                                submitError = null
                            )
                        )
                    }
                )
            } catch (e: Exception) {
                dialogManager.showMessage(e.message ?: getString(Res.string.load_songs_failed))
            }
        }
    }

    fun showMusicPickerDialog(
        allMusic: List<MusicInfo>,
        selectedIds: Set<Long> = emptySet(),
        title: String,
        onConfirm: (Set<Long>) -> Unit
    ) {
        onMusicPickerConfirm = onConfirm
        _activeDialog.value = DialogUiState.MusicPicker(
            MusicPickerDialogState(
                allMusic = allMusic,
                selectedIds = selectedIds,
                title = title
            )
        )
    }

    fun confirmMusicPickerDialog(selectedIds: Set<Long>) {
        onMusicPickerConfirm?.invoke(selectedIds)
        dismissMusicPickerDialog()
    }

    fun dismissMusicPickerDialog() {
        if (_activeDialog.value is DialogUiState.MusicPicker) {
            val pendingCreateState = pendingCreatePlaylistStateForPicker
            _activeDialog.value = if (pendingCreateState != null) {
                DialogUiState.CreatePlaylist(pendingCreateState)
            } else {
                null
            }
        }
        pendingCreatePlaylistStateForPicker = null
        onMusicPickerConfirm = null
    }

    // 显示播放列表选择弹窗
    fun showPlaylistPickerDialog(
        playlists: List<Playlist>,
        title: String,
        onConfirm: (Playlist) -> Unit
    ) {
        onPlaylistPickerConfirm = onConfirm
        _activeDialog.value = DialogUiState.PlaylistPicker(
            PlaylistPickerDialogState(
                playlists = playlists,
                title = title
            )
        )
    }

    // 确认选择播放列表
    fun confirmPlaylistPickerDialog(playlist: Playlist) {
        onPlaylistPickerConfirm?.invoke(playlist)
        dismissPlaylistPickerDialog()
    }

    // 关闭播放列表选择弹窗
    fun dismissPlaylistPickerDialog() {
        if (_activeDialog.value is DialogUiState.PlaylistPicker) {
            _activeDialog.value = null
        }
        onPlaylistPickerConfirm = null
    }

    fun showTimerDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit = {}) {
        val config = TimerDialogConfig(onConfirm, onDismiss)
        _showTimerDialog.value = config
        _activeDialog.value = DialogUiState.Timer(config)
    }

    fun dismissTimerDialog() {
        _showTimerDialog.value = null
        if (_activeDialog.value is DialogUiState.Timer) {
            _activeDialog.value = null
        }
    }

    fun submitCreatePlaylist() {
        val current = (_activeDialog.value as? DialogUiState.CreatePlaylist)?.state ?: return
        viewModelScope.launch {
            val validationError = validatePlaylistName(current.name)
            if (validationError != null) {
                updateCreatePlaylistState { it.copy(nameError = validationError) }
                return@launch
            }

            updateCreatePlaylistState { it.copy(isSubmitting = true, submitError = null) }
            try {
                if (current.isEditing && current.editingPlaylistId != null) {
                    val id = current.editingPlaylistId
                    val trimmedName = current.name.trim()
                    managePlaylistUseCase.renamePlaylist(id, trimmedName)

                    val desc = current.description.trim().takeIf { it.isNotEmpty() }
                    managePlaylistUseCase.updatePlaylistDescription(id, desc)

                    managePlaylistUseCase.setPlaylistPinned(id, current.pinAfterCreate)

                    onPlaylistCreated?.invoke(id)
                    dialogManager.showMessage(getString(Res.string.playlist_updated))
                    dismissCreatePlaylistDialog()
                } else {
                    val id = managePlaylistUseCase.createPlaylist(current.name.trim())
                    val desc = current.description.trim().takeIf { it.isNotEmpty() }
                    if (desc != null) {
                        managePlaylistUseCase.updatePlaylistDescription(id, desc)
                    }
                    if (current.pinAfterCreate) {
                        managePlaylistUseCase.setPlaylistPinned(id, true)
                    }
                    addSelectedSongsToPlaylist(
                        playlistId = id,
                        selectedSongIds = current.selectedSongIds
                    )
                    onPlaylistCreated?.invoke(id)
                    dialogManager.showMessage(getString(Res.string.playlist_created))
                    dismissCreatePlaylistDialog()
                }
            } catch (e: Exception) {
                updateCreatePlaylistState {
                    it.copy(
                        isSubmitting = false,
                        submitError = e.message ?: if (current.isEditing) getString(Res.string.update_playlist_failed) else getString(Res.string.create_playlist_failed)
                    )
                }
            }
        }
    }

    private inline fun updateMusicDetailState(
        transform: (MusicDetailState) -> MusicDetailState
    ) {
        val current = (_activeDialog.value as? DialogUiState.MusicDetail)?.state ?: return
        _activeDialog.value = DialogUiState.MusicDetail(transform(current))
    }

    private inline fun updateCreatePlaylistState(
        transform: (CreatePlaylistDialogState) -> CreatePlaylistDialogState
    ) {
        val current = (_activeDialog.value as? DialogUiState.CreatePlaylist)?.state ?: return
        _activeDialog.value = DialogUiState.CreatePlaylist(transform(current))
    }

    private suspend fun validatePlaylistName(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return getString(Res.string.playlist_name_empty)
        }
        if (trimmed.length > 30) {
            return getString(Res.string.playlist_name_too_long)
        }
        if (existingPlaylistNames.contains(trimmed.lowercase())) {
            return getString(Res.string.playlist_name_exists)
        }
        return null
    }

    private suspend fun addSelectedSongsToPlaylist(
        playlistId: Long,
        selectedSongIds: Set<Long>
    ) {
        if (selectedSongIds.isEmpty()) return
        selectedSongIds.forEach { songId ->
            val path = createDialogMusicLookup[songId]?.music?.path
                ?: getAllMusicUseCase.getMusicById(songId)?.music?.path
            if (!path.isNullOrBlank()) {
                managePlaylistUseCase.addToPlaylist(
                    playlistId = playlistId,
                    musicId = songId,
                    musicPath = path
                )
            }
        }
    }

    // 音乐详情弹窗状态
    data class MusicDetailState(
        val musicInfo: MusicInfo,
        val isVisible: Boolean,
        val menuConfig: MusicDetailMenuConfig = MusicDetailMenuConfig()
    )

    // 音乐详情弹窗菜单配置
    data class MusicDetailMenuConfig(
        val showAddToSpecificPlaylist: Boolean = true,
        val showShare: Boolean = true,
        val showViewDetail: Boolean = true,
        val showEditTags: Boolean = true,
        val showPlayNext: Boolean = false,
        val showRemoveFromCurrentPlaylist: Boolean = false,
        val showDelete: Boolean = false
    )
}
