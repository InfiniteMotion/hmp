package com.hearablemusic.player.ui.playlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.playlist.renameWithError
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistNameError
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 管理页五态状态机的单一载体：待删/待改名对象同时表达弹窗可见性。 */
data class PlaylistManageUiState(
    val isEditMode: Boolean = false,
    val pendingDelete: Playlist? = null,
    val pendingRename: Playlist? = null,
    val renameValue: String = "",
    val renameError: PlaylistNameError? = null,
)

/**
 * 歌单管理页的屏级 VM（绑定 `Routes.Playlist.UserPlaylistManage` 的 entry）。
 *
 * 列表数据（自建歌单 + 系统歌单 id）仍读 `PlaylistViewModel` 的跨屏投影；
 * 本 VM 只持「编辑模式 / 待删 / 待改名 / 草稿 / 校验错误」这一组交互态，
 * 随 entry 销毁，不再需要 `onManageScreenLeft()`。
 */
class PlaylistManageViewModel(
    private val managePlaylistUseCase: ManagePlaylistUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistManageUiState())
    val uiState: StateFlow<PlaylistManageUiState> = _uiState.asStateFlow()

    fun onToggleEditMode() {
        _uiState.update { it.copy(isEditMode = !it.isEditMode) }
    }

    // ── 删除 ───────────────────────────────────────────────────────────
    fun onDeleteRequested(playlist: Playlist) {
        _uiState.update { it.copy(pendingDelete = playlist) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /**
     * 删除歌单。
     *
     * 系统歌单由 `ManagePlaylistUseCase` 返回 `false`（D3-17②：不抛异常，抛过一次
     * Android 删「心动」就崩）；管理页本就不列系统歌单（D3-17①），这里只剩 DB 失败
     * 这一条 —— 列表不变就是结果，日志留证据，不再把整份列表状态改写成 Error。
     */
    fun onDeleteConfirmed() {
        val target = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = runCatching { managePlaylistUseCase.removePlaylistById(target.id) }
            when {
                result.isFailure ->
                    HmpLog.w(LogTag.UiCommon) { "删除歌单 ${target.id} 失败：${result.exceptionOrNull()?.message}" }

                result.getOrNull() == false ->
                    HmpLog.w(LogTag.UiCommon) { "删除歌单 ${target.id} 被拒：系统歌单不可删除" }
            }
        }
    }

    // ── 改名 ───────────────────────────────────────────────────────────
    fun onRenameRequested(playlist: Playlist) {
        _uiState.update {
            it.copy(pendingRename = playlist, renameValue = playlist.name, renameError = null)
        }
    }

    fun onRenameValueChange(value: String) {
        _uiState.update { it.copy(renameValue = value, renameError = null) }
    }

    /** dismiss 一次清掉三态：待改名对象、输入草稿、上次的校验错误。 */
    fun onRenameDismissed() {
        _uiState.update {
            it.copy(pendingRename = null, renameValue = "", renameError = null)
        }
    }

    fun onRenameConfirmed() {
        val state = _uiState.value
        val target = state.pendingRename ?: return
        viewModelScope.launch {
            val rejected = managePlaylistUseCase.renameWithError(target.id, state.renameValue)
            if (rejected == null) {
                _uiState.update {
                    it.copy(pendingRename = null, renameValue = "", renameError = null)
                }
            } else {
                _uiState.update { it.copy(renameError = rejected) }
            }
        }
    }

    // ── 置顶 ───────────────────────────────────────────────────────────
    fun onSetPinned(id: Long, isPinned: Boolean) {
        viewModelScope.launch {
            managePlaylistUseCase.setPlaylistPinned(id, isPinned)
        }
    }
}
