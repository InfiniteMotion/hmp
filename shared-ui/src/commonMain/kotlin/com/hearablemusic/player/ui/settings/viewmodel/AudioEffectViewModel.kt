package com.hearablemusic.player.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.setting.model.AudioEffectSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 音效设置页整屏状态（7.5 P1）：播放控制器上的 5 个散装出口收成唯一出口。 */
// 注：bandLevels 是 FloatArray，equals 退化为引用比较，@Stable 的稳定承诺不成立 —— 故意不标。
data class AudioEffectsUiState(
    val settings: AudioEffectSettings,
    val presets: List<String>,
    val bandCount: Int,
    val bandLevelRange: Pair<Int, Int>,
    val bandLevels: FloatArray,
)

class AudioEffectViewModel(
    private val musicController: PlaybackController,
) : ViewModel() {

    /** 5 个源都是 StateFlow，首帧直接用它们的当前值，不需要另造默认状态。 */
    val uiState: StateFlow<AudioEffectsUiState> = combine(
        musicController.audioEffectSettings,
        musicController.equalizerPresets,
        musicController.equalizerBandCount,
        musicController.equalizerBandLevelRange,
        musicController.currentEqualizerBandLevels,
    ) { settings, presets, bandCount, range, levels ->
        AudioEffectsUiState(
            settings = settings,
            presets = presets,
            bandCount = bandCount,
            bandLevelRange = range,
            bandLevels = levels,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AudioEffectsUiState(
            settings = musicController.audioEffectSettings.value,
            presets = musicController.equalizerPresets.value,
            bandCount = musicController.equalizerBandCount.value,
            bandLevelRange = musicController.equalizerBandLevelRange.value,
            bandLevels = musicController.currentEqualizerBandLevels.value,
        ),
    )

    fun onInitializeAudioEffects() = musicController.initializeAudioEffects()
    fun setEqualizerPreset(preset: Int) = musicController.setEqualizerPreset(preset)
    fun setBassBoost(level: Int) = musicController.setBassBoost(level)
    fun setSurroundSound(enabled: Boolean) = musicController.setSurroundSound(enabled)
    fun setReverb(preset: Int) = musicController.setReverb(preset)

    /** 单频段变更：数组拷贝与越界保护都在 VM，UI 只上送 (index, level)。 */
    fun onBandLevelChanged(index: Int, level: Float) {
        val current = uiState.value.bandLevels
        if (index !in current.indices) return
        val next = current.copyOf()
        next[index] = level
        musicController.setCustomEqualizer(next)
    }

    fun onResetEqualizer() {
        musicController.setCustomEqualizer(FloatArray(uiState.value.bandCount))
    }
}
