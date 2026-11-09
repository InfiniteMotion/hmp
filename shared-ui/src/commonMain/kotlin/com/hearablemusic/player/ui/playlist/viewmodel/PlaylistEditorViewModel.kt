package com.hearablemusic.player.ui.playlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asUiText
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.create_playlist_failed
import com.hearablemusic.player.ui.generated.resources.load_songs_failed
import com.hearablemusic.player.ui.generated.resources.update_playlist_failed
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistNameError
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 创建歌单时叠在编辑弹窗之上的选歌层。 */
data class MusicPickerUiState(
    val allMusic: List<MusicInfo>,
    val selectedIds: Set<Long>,
)

data class PlaylistEditorUiState(
    val visible: Boolean = false,
    val name: String = "",
    val description: String = "",
    val pinAfterCreate: Boolean = false,
    val selectedSongIds: Set<Long> = emptySet(),
    val nameError: PlaylistNameError? = null,
    val submitError: UiText? = null,
    val isSubmitting: Boolean = false,
    val isEditing: Boolean = false,
    val editingPlaylistId: Long? = null,
    val musicPicker: MusicPickerUiState? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && nameError == null && name.trim().isNotEmpty()
}

/** 弹窗结果：新建/编辑成功只回一个 id，由调用屏决定导航还是刷新；失败提示走 [Message]。 */
sealed interface PlaylistEditorEffect {
    data class Saved(val playlistId: Long) : PlaylistEditorEffect
    data class Message(val text: UiText) : PlaylistEditorEffect
}

/**
 * 新建 / 编辑歌单弹窗的屏级 VM（7.5 复审）。
 *
 * 弹窗归屏：列表 / 管理 / 歌单详情三个入口屏各持一个实例，确认结果以 [Saved] 交回该屏，
 * 不再把回调 lambda 存进应用级 `DialogViewModel`。名称查重、落库与叠层选歌都在这里。
 */
class PlaylistEditorViewModel(
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val getAllMusicUseCase: GetAllMusicUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistEditorUiState())
    val uiState: StateFlow<PlaylistEditorUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<PlaylistEditorEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<PlaylistEditorEffect> = _effect.asSharedFlow()

    private var nameValidationJob: Job? = null

    fun onCreateRequested() {
        nameValidationJob?.cancel()
        _uiState.value = PlaylistEditorUiState(visible = true)
    }

    fun onEditRequested(playlist: Playlist) {
        nameValidationJob?.cancel()
        _uiState.value = PlaylistEditorUiState(
            visible = true,
            name = playlist.name,
            description = playlist.description.orEmpty(),
            pinAfterCreate = playlist.isPinned,
            isEditing = true,
            editingPlaylistId = playlist.id,
        )
    }

    fun onDismiss() {
        nameValidationJob?.cancel()
        _uiState.value = PlaylistEditorUiState()
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameError = null, submitError = null) }
        nameValidationJob?.cancel()
        nameValidationJob = viewModelScope.launch {
            delay(NAME_VALIDATION_DEBOUNCE_MS)
            val state = _uiState.value
            val error = managePlaylistUseCase.validateName(value, state.editingPlaylistId)
            // 期间又敲了新字符就丢弃旧结果
            _uiState.update { if (it.name == value) it.copy(nameError = error) else it }
        }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value, submitError = null) }
    }

    fun onPinnedChange(value: Boolean) {
        _uiState.update { it.copy(pinAfterCreate = value) }
    }

    fun onAddSongsRequested() {
        viewModelScope.launch {
            try {
                val allMusic = getAllMusicUseCase(MusicSortKey.TITLE, SortDirection.ASC)
                _uiState.update { it.copy(musicPicker = MusicPickerUiState(allMusic, it.selectedSongIds)) }
            } catch (e: Exception) {
                _effect.emit(
                    PlaylistEditorEffect.Message(
                        e.message?.let(UiText::Raw) ?: Res.string.load_songs_failed.asUiText(),
                    ),
                )
            }
        }
    }

    fun onSongsPicked(ids: Set<Long>) {
        _uiState.update { it.copy(selectedSongIds = ids, musicPicker = null) }
    }

    fun onMusicPickerDismissed() {
        _uiState.update { it.copy(musicPicker = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.visible || state.isSubmitting) return
        viewModelScope.launch {
            val nameError = runCatching {
                managePlaylistUseCase.validateName(state.name, state.editingPlaylistId)
            }.getOrNull()
            if (nameError != null) {
                _uiState.update { it.copy(nameError = nameError) }
                return@launch
            }

            _uiState.update { it.copy(isSubmitting = true, submitError = null) }
            try {
                val id = if (state.isEditing && state.editingPlaylistId != null) {
                    val editingId = state.editingPlaylistId
                    managePlaylistUseCase.renamePlaylist(editingId, state.name.trim())
                    val desc = state.description.trim().takeIf { it.isNotEmpty() }
                    managePlaylistUseCase.updatePlaylistDescription(editingId, desc)
                    managePlaylistUseCase.setPlaylistPinned(editingId, state.pinAfterCreate)
                    editingId
                } else {
                    val createdId = managePlaylistUseCase.createPlaylist(state.name.trim())
                    val desc = state.description.trim().takeIf { it.isNotEmpty() }
                    if (desc != null) {
                        managePlaylistUseCase.updatePlaylistDescription(createdId, desc)
                    }
                    if (state.pinAfterCreate) {
                        managePlaylistUseCase.setPlaylistPinned(createdId, true)
                    }
                    state.selectedSongIds.forEach { songId ->
                        managePlaylistUseCase.addToPlaylist(createdId, songId)
                    }
                    createdId
                }
                onDismiss()
                _effect.emit(PlaylistEditorEffect.Saved(id))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submitError = e.message?.let(UiText::Raw)
                            ?: if (state.isEditing) {
                                Res.string.update_playlist_failed.asUiText()
                            } else {
                                Res.string.create_playlist_failed.asUiText()
                            },
                    )
                }
            }
        }
    }

    companion object {
        /** 与输入框查重同口径：每个按键都查一次仓库没有意义。 */
        const val NAME_VALIDATION_DEBOUNCE_MS = 250L
    }
}
