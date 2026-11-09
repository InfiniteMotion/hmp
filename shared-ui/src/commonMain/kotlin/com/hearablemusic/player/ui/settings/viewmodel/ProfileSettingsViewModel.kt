package com.hearablemusic.player.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 个人资料页整屏状态（7.5 P1）。
 * 草稿态随屏级 VM 存活，不塞进全局 `SettingsViewModel` —— 后者是 activity 作用域、
 * 十余个散装出口，被 Player / Intro / User 多屏共享，屏级草稿放进去会泄漏。
 */
data class ProfileSettingsUiState(
    val avatarUri: String = "",
    val userName: String = "",
    /** 选图后的待确认预览；空串表示没有待确认改动 */
    val pendingAvatarUri: String = "",
    /** 改名输入框草稿 */
    val nameInput: String = "",
)

/** 一次性效果：toast 由组合点消费，VM 不持有 DialogManager / NavBackStack。 */
sealed interface ProfileSettingsEffect {
    data object AvatarChanged : ProfileSettingsEffect
    data object AvatarChangeCancelled : ProfileSettingsEffect
}

class ProfileSettingsViewModel(
    private val userSettingsUseCase: UserSettingsUseCase,
    private val platformServices: PlatformServices,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSettingsUiState())
    val uiState: StateFlow<ProfileSettingsUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ProfileSettingsEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<ProfileSettingsEffect> = _effect.asSharedFlow()

    init {
        // 头像用 UseCase 的响应式流（原 init 一次性读取：备份恢复后本页显示陈旧）
        viewModelScope.launch {
            userSettingsUseCase.avatarUri.collect { uri ->
                _uiState.update { it.copy(avatarUri = uri) }
            }
        }
        viewModelScope.launch {
            userSettingsUseCase.userName.collect { name ->
                _uiState.update { it.copy(userName = name) }
            }
        }
    }

    /** 选图是平台副作用，由 VM 发起；回调只把路径写回状态。 */
    fun onPickAvatar() {
        platformServices.filePicker.pickImage { path ->
            if (path != null) {
                _uiState.update { it.copy(pendingAvatarUri = path) }
            }
        }
    }

    fun onConfirmAvatar() {
        val uri = _uiState.value.pendingAvatarUri
        if (uri.isEmpty()) return
        viewModelScope.launch {
            userSettingsUseCase.saveAvatarUri(uri)
            _uiState.update { it.copy(avatarUri = uri, pendingAvatarUri = "") }
            _effect.emit(ProfileSettingsEffect.AvatarChanged)
        }
    }

    fun onCancelAvatar() {
        _uiState.update { it.copy(pendingAvatarUri = "") }
        _effect.tryEmit(ProfileSettingsEffect.AvatarChangeCancelled)
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(nameInput = value) }
    }

    fun onConfirmName() {
        val name = _uiState.value.nameInput
        viewModelScope.launch {
            userSettingsUseCase.saveUserName(name)
        }
    }
}
