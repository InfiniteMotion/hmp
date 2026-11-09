package com.hearablemusic.player.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_BLUR_RADIUS
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_NOISE_FACTOR
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_TINT_ALPHA
import com.hearablemusic.player.ui.common.util.HAZE_MODE_CUSTOM
import com.hearablemusic.player.ui.common.util.HAZE_MODE_PRESET
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userSettingsUseCase: UserSettingsUseCase,
) : ViewModel() {

    // User Info
    val isFirstLaunch = userSettingsUseCase.isFirstLaunch
    val isLoadMusic = userSettingsUseCase.isLoadMusic
    val userName = userSettingsUseCase.userName
    val customMode = userSettingsUseCase.customMode
    val backgroundStyle = userSettingsUseCase.backgroundStyle
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), "FLUID")
    val hazeMode = userSettingsUseCase.hazeMode
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), HAZE_MODE_CUSTOM)
    val hazeMaterialPreset = userSettingsUseCase.hazeMaterialPreset
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), "regular")
    val hazeBlurRadius = userSettingsUseCase.hazeBlurRadius
        .stateIn(
            viewModelScope,
            SharingStarted.Companion.WhileSubscribed(5000),
            DEFAULT_HAZE_BLUR_RADIUS,
        )
    val hazeNoiseFactor = userSettingsUseCase.hazeNoiseFactor
        .stateIn(
            viewModelScope,
            SharingStarted.Companion.WhileSubscribed(5000),
            DEFAULT_HAZE_NOISE_FACTOR,
        )
    val hazeTintAlpha = userSettingsUseCase.hazeTintAlpha
        .stateIn(
            viewModelScope,
            SharingStarted.Companion.WhileSubscribed(5000),
            DEFAULT_HAZE_TINT_ALPHA,
        )
    val hazeIntensity = userSettingsUseCase.hazeIntensity
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), 0.6f)

    /** 直接订阅 UseCase 的共享镜像：个人资料页写入后本页自动刷新，不再各自缓存一份。 */
    val avatarUri: StateFlow<String> = userSettingsUseCase.avatarUri

    fun saveAvatarUri(uri: String) {
        viewModelScope.launch {
            userSettingsUseCase.saveAvatarUri(uri)
        }
    }

    fun saveUserName(name: String) {
        viewModelScope.launch {
            userSettingsUseCase.saveUserName(name)
        }
    }

    fun saveCustomMode(mode: String) {
        viewModelScope.launch {
            userSettingsUseCase.saveThemeMode(mode)
        }
    }

    fun saveBackgroundStyle(style: String) {
        viewModelScope.launch {
            userSettingsUseCase.saveBackgroundStyle(style)
        }
    }

    fun saveHazeMode(mode: String) {
        viewModelScope.launch {
            userSettingsUseCase.saveHazeMode(mode)
        }
    }

    fun saveHazeMaterialPreset(preset: String) {
        viewModelScope.launch {
            userSettingsUseCase.saveHazeMaterialPreset(preset)
        }
    }

    fun saveHazeBlurRadius(radius: Float) {
        viewModelScope.launch {
            userSettingsUseCase.saveHazeBlurRadius(radius)
        }
    }

    fun saveHazeNoiseFactor(noiseFactor: Float) {
        viewModelScope.launch {
            userSettingsUseCase.saveHazeNoiseFactor(noiseFactor)
        }
    }

    fun saveHazeTintAlpha(alpha: Float) {
        viewModelScope.launch {
            userSettingsUseCase.saveHazeTintAlpha(alpha)
        }
    }

    fun applyHazeMaterialPreset(preset: String, intensity: Float) {
        viewModelScope.launch {
            userSettingsUseCase.saveHazeMode(HAZE_MODE_PRESET)
            userSettingsUseCase.saveHazeMaterialPreset(preset)
            userSettingsUseCase.saveHazeIntensity(intensity)
        }
    }

    fun saveHazeIntensity(intensity: Float) {
        viewModelScope.launch {
            userSettingsUseCase.saveHazeMode(HAZE_MODE_CUSTOM)
            userSettingsUseCase.saveHazeIntensity(intensity)
        }
    }

    fun saveIsFirstLaunchStatus(status: Boolean) {
        viewModelScope.launch {
            userSettingsUseCase.saveIsFirstLaunch(status)
        }
    }

    fun saveIsLoadMusic(isLoad: Boolean) {
        viewModelScope.launch {
            userSettingsUseCase.saveIsLoadMusic(isLoad)
        }
    }

    init {
        // 冷启动补一次头像初值；备份恢复等绕过 UseCase 的写入也靠这次读取同步。
        // 放在 VM 而不是组合期：屏重进 / 横竖屏切换不再重复触发。
        viewModelScope.launch { userSettingsUseCase.getAvatarUri() }
    }
}
