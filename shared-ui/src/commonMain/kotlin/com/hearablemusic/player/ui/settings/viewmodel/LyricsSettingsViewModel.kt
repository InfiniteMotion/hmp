package com.hearablemusic.player.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.config.LyricsConfig
import com.hmp.domain.lyrics.LyricsComponent
import com.hmp.domain.lyrics.LyricsComponentConfig
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 歌词渲染配置（播放页 / 独立歌词页共用）。
 *
 * **D4-01 之后这里只有一条读路径**：per-component 配置 → `LyricsConfigResolver`。
 * 以前播放页读的是 legacy 扁平键（`lyricsOriginalTextSize` 等），而设置页写的是新模型，
 * 于是"设置里改了字号、播放页没反应"——两套存储各写各的。legacy 读取与写入已从用例里删掉。
 *
 * 页内快捷面板（独立歌词页底部）写的是 **FULLSCREEN** 组件；播放页只读 **PLAYER**。
 */
class LyricsSettingsViewModel(
    private val lyricsSettingsUseCase: LyricsSettingsUseCase,
) : ViewModel() {

    /** 播放页内联歌词的生效配置（已完成 `linkedTo` 链解析）。 */
    val playerLyricsConfig: StateFlow<LyricsConfig> =
        lyricsSettingsUseCase.configFlow(LyricsComponent.PLAYER)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricsConfig())

    /** 独立歌词页（全屏）的生效配置。 */
    val fullscreenLyricsConfig: StateFlow<LyricsConfig> =
        lyricsSettingsUseCase.configFlow(LyricsComponent.FULLSCREEN)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricsConfig())

    /**
     * 改全屏歌词的单个字段。读-改-写，不清空同组件的其它字段（含 `linkedTo`）。
     */
    fun updateFullscreenConfig(transform: (LyricsComponentConfig) -> LyricsComponentConfig) {
        viewModelScope.launch {
            lyricsSettingsUseCase.updateComponentConfig(LyricsComponent.FULLSCREEN, transform)
        }
    }

    // 7.5 P1：具名动作 —— reducer（copy）归 VM，UI 只上送值，不再自己决定下一状态。
    fun onKaraokeEnabledChange(value: Boolean) = updateFullscreenConfig { it.copy(karaokeEnabled = value) }

    fun onOriginalTextSizeChange(value: Int) = updateFullscreenConfig { it.copy(originalTextSize = value) }

    fun onTranslatedTextSizeChange(value: Int) = updateFullscreenConfig { it.copy(translatedTextSize = value) }

    fun onCurrentTimeTextSizeChange(value: Int) = updateFullscreenConfig { it.copy(currentTimeTextSize = value) }

    fun onLineSpacingChange(value: Int) = updateFullscreenConfig { it.copy(lineSpacing = value) }

    fun onDisplayModeChange(value: com.hmp.domain.config.DisplayMode) = updateFullscreenConfig { it.copy(displayMode = value) }

    fun onAlignmentChange(value: com.hmp.domain.config.LyricsAlignment) = updateFullscreenConfig { it.copy(alignment = value) }
}
