package com.hmp.domain.setting.model

data class AudioEffectPreset(
    val id: Int,
    val name: String,
    val description: String,
)

data class AudioEffectSettings(
    val equalizerPreset: Int = 0,
    val bassBoostLevel: Int = 0,
    val isSurroundSoundEnabled: Boolean = false,
    val reverbPreset: Int = 0,
    val customEqualizerLevels: FloatArray = floatArrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AudioEffectSettings

        if (equalizerPreset != other.equalizerPreset) return false
        if (bassBoostLevel != other.bassBoostLevel) return false
        if (isSurroundSoundEnabled != other.isSurroundSoundEnabled) return false
        if (reverbPreset != other.reverbPreset) return false
        if (!customEqualizerLevels.contentEquals(other.customEqualizerLevels)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = equalizerPreset
        result = 31 * result + bassBoostLevel
        result = 31 * result + isSurroundSoundEnabled.hashCode()
        result = 31 * result + reverbPreset
        result = 31 * result + customEqualizerLevels.contentHashCode()
        return result
    }
}

data class EqualizerBand(
    val bandIndex: Int,
    val frequency: Int,
    val level: Float,
    val minLevel: Int,
    val maxLevel: Int,
)
