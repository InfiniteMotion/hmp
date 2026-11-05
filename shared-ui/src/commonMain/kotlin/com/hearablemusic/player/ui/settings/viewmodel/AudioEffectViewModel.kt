package com.hearablemusic.player.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.setting.model.AudioEffectSettings
import kotlinx.coroutines.flow.StateFlow

class AudioEffectViewModel(
    private val musicController: PlaybackController,
) : ViewModel() {

    val audioEffectSettings: StateFlow<AudioEffectSettings> = musicController.audioEffectSettings
    val equalizerPresets: StateFlow<List<String>> = musicController.equalizerPresets
    val equalizerBandCount: StateFlow<Int> = musicController.equalizerBandCount
    val equalizerBandLevelRange: StateFlow<Pair<Int, Int>> = musicController.equalizerBandLevelRange
    val currentEqualizerBandLevels: StateFlow<FloatArray> = musicController.currentEqualizerBandLevels

    fun initializeAudioEffects() = musicController.initializeAudioEffects()
    fun setEqualizerPreset(preset: Int) = musicController.setEqualizerPreset(preset)
    fun setBassBoost(level: Int) = musicController.setBassBoost(level)
    fun setSurroundSound(enabled: Boolean) = musicController.setSurroundSound(enabled)
    fun setReverb(preset: Int) = musicController.setReverb(preset)
    fun setCustomEqualizer(bandLevels: FloatArray) = musicController.setCustomEqualizer(bandLevels)
}
