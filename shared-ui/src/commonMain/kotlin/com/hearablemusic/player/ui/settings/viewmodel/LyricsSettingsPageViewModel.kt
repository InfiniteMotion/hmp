package com.hearablemusic.player.ui.settings.viewmodel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.lyrics.LyricsComponent
import com.hmp.domain.lyrics.LyricsComponentConfig
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 歌词设置页整屏状态（7.5 P1）。
 *
 * 三份组件配置不是三个独立状态，而是同一份持久化的三个投影，收进一个 Map；
 * 配置流长期订阅，任何来源的写入（本页、独立歌词页快捷面板）都会回流到这一屏。
 */
@Stable
data class LyricsSettingsUiState(
    val selectedTab: LyricsComponent = LyricsComponent.PLAYER,
    val configs: Map<LyricsComponent, LyricsComponentConfig> = emptyMap(),
    val floatingEnabled: Boolean = false,
    val floatingSupported: Boolean = false,
) {
    val currentConfig: LyricsComponentConfig
        get() = configs[selectedTab] ?: LyricsComponentConfig.DEFAULT
}

/**
 * 歌词设置页屏级 VM。
 *
 * 与 [LyricsSettingsViewModel] 分工：后者是播放页 / 独立歌词页共用的生效配置投影，
 * 本 VM 只持有这一屏的编辑态。悬浮歌词开关与 overlay 权限都是平台副作用，
 * 由 VM 发起并在 VM 内消费回调，composable 不接触 UseCase 与 PlatformServices。
 */
class LyricsSettingsPageViewModel(
    private val lyricsSettingsUseCase: LyricsSettingsUseCase,
    private val platformServices: PlatformServices,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LyricsSettingsUiState(floatingSupported = platformServices.floatingLyrics.isSupported),
    )
    val uiState: StateFlow<LyricsSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                lyricsSettingsUseCase.getComponentConfig(LyricsComponent.PLAYER),
                lyricsSettingsUseCase.getComponentConfig(LyricsComponent.FULLSCREEN),
                lyricsSettingsUseCase.getComponentConfig(LyricsComponent.FLOATING),
            ) { player, fullscreen, floating ->
                mapOf(
                    LyricsComponent.PLAYER to player,
                    LyricsComponent.FULLSCREEN to fullscreen,
                    LyricsComponent.FLOATING to floating,
                )
            }.collect { configs -> _uiState.update { it.copy(configs = configs) } }
        }
        viewModelScope.launch {
            lyricsSettingsUseCase.floatingLyricsEnabled.collect { enabled ->
                _uiState.update { it.copy(floatingEnabled = enabled) }
            }
        }
    }

    fun onSelectTab(component: LyricsComponent) {
        if (component == LyricsComponent.FLOATING && !_uiState.value.floatingSupported) return
        _uiState.update { it.copy(selectedTab = component) }
    }

    /**
     * 配置编辑走 UseCase 带锁的读-改-写：连点加减号、或外部同时改配置都不会丢值，
     * 写回后由上面的配置流回流到 uiState，UI 不做乐观更新。
     */
    private fun updateCurrentConfig(transform: (LyricsComponentConfig) -> LyricsComponentConfig) {
        val component = _uiState.value.selectedTab
        viewModelScope.launch {
            lyricsSettingsUseCase.updateComponentConfig(component, transform)
        }
    }

    fun onDisplayModeChange(mode: DisplayMode) = updateCurrentConfig { it.copy(displayMode = mode) }

    fun onAlignmentChange(alignment: LyricsAlignment) = updateCurrentConfig { it.copy(alignment = alignment) }

    fun onOriginalTextSizeChange(value: Int) = updateCurrentConfig { it.copy(originalTextSize = value) }

    fun onTranslatedTextSizeChange(value: Int) = updateCurrentConfig { it.copy(translatedTextSize = value) }

    fun onCurrentTimeTextSizeChange(value: Int) = updateCurrentConfig { it.copy(currentTimeTextSize = value) }

    fun onLineSpacingChange(value: Int) = updateCurrentConfig { it.copy(lineSpacing = value) }

    fun onKaraokeChange(enabled: Boolean) = updateCurrentConfig { it.copy(karaokeEnabled = enabled) }

    fun onLinkedToChange(key: String?) = updateCurrentConfig { it.copy(linkedTo = key) }

    fun onResetCurrentComponent() {
        viewModelScope.launch {
            lyricsSettingsUseCase.resetComponentToDefault(_uiState.value.selectedTab)
        }
    }

    fun onFloatingLyricsToggle(enabled: Boolean) {
        if (!platformServices.floatingLyrics.isSupported) return
        if (!enabled) {
            stopFloatingLyrics()
            return
        }
        if (platformServices.permission.hasOverlayPermission()) {
            startFloatingLyrics()
        } else {
            platformServices.permission.requestOverlayPermission { granted ->
                if (granted) startFloatingLyrics() else stopFloatingLyrics()
            }
        }
    }

    private fun startFloatingLyrics() {
        viewModelScope.launch { lyricsSettingsUseCase.saveFloatingLyricsEnabled(true) }
        platformServices.floatingLyrics.start()
    }

    private fun stopFloatingLyrics() {
        viewModelScope.launch { lyricsSettingsUseCase.saveFloatingLyricsEnabled(false) }
        platformServices.floatingLyrics.stop()
    }
}
