package com.hmp.domain.setting.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AudioEffectSettingsTest {

    @Test
    fun defaultValues() {
        val settings = AudioEffectSettings()
        assertEquals(0, settings.equalizerPreset)
        assertEquals(0, settings.bassBoostLevel)
        assertEquals(false, settings.isSurroundSoundEnabled)
        assertEquals(0, settings.reverbPreset)
        assertTrue(settings.customEqualizerLevels.isEmpty())
    }

    @Test
    fun customValues() {
        val levels = floatArrayOf(1.0f, 2.0f, 3.0f)
        val settings = AudioEffectSettings(
            equalizerPreset = 2,
            bassBoostLevel = 5,
            isSurroundSoundEnabled = true,
            reverbPreset = 3,
            customEqualizerLevels = levels,
        )
        assertEquals(2, settings.equalizerPreset)
        assertEquals(5, settings.bassBoostLevel)
        assertEquals(true, settings.isSurroundSoundEnabled)
        assertEquals(3, settings.reverbPreset)
        assertEquals(3, settings.customEqualizerLevels.size)
    }

    @Test
    fun equals_sameValues_returnsTrue() {
        val a = AudioEffectSettings(equalizerPreset = 1, bassBoostLevel = 2)
        val b = AudioEffectSettings(equalizerPreset = 1, bassBoostLevel = 2)
        assertEquals(a, b)
    }

    @Test
    fun equals_differentValues_returnsFalse() {
        val a = AudioEffectSettings(equalizerPreset = 1)
        val b = AudioEffectSettings(equalizerPreset = 2)
        assertNotEquals(a, b)
    }

    @Test
    fun equals_sameLevels_returnsTrue() {
        val levels = floatArrayOf(1.0f, 2.0f)
        val a = AudioEffectSettings(customEqualizerLevels = levels)
        val b = AudioEffectSettings(customEqualizerLevels = floatArrayOf(1.0f, 2.0f))
        assertEquals(a, b)
    }

    @Test
    fun equals_differentLevels_returnsFalse() {
        val a = AudioEffectSettings(customEqualizerLevels = floatArrayOf(1.0f))
        val b = AudioEffectSettings(customEqualizerLevels = floatArrayOf(2.0f))
        assertNotEquals(a, b)
    }

    @Test
    fun hashCode_sameValues_sameHash() {
        val a = AudioEffectSettings(equalizerPreset = 1, bassBoostLevel = 2, isSurroundSoundEnabled = true)
        val b = AudioEffectSettings(equalizerPreset = 1, bassBoostLevel = 2, isSurroundSoundEnabled = true)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun equals_sameObject_returnsTrue() {
        val a = AudioEffectSettings()
        assertEquals(a, a)
    }

    @Test
    fun equals_null_returnsFalse() {
        val a = AudioEffectSettings()
        assertNotEquals<Any?>(a, null)
    }

    @Test
    fun equals_differentSurroundSound_returnsFalse() {
        val a = AudioEffectSettings(isSurroundSoundEnabled = true)
        val b = AudioEffectSettings(isSurroundSoundEnabled = false)
        assertNotEquals(a, b)
    }
}

class AudioEffectPresetTest {

    @Test
    fun construction() {
        val preset = AudioEffectPreset(id = 1, name = "Rock", description = "Rock preset")
        assertEquals(1, preset.id)
        assertEquals("Rock", preset.name)
        assertEquals("Rock preset", preset.description)
    }
}

class EqualizerBandTest {

    @Test
    fun construction() {
        val band = EqualizerBand(
            bandIndex = 0,
            frequency = 60,
            level = 3.5f,
            minLevel = -12,
            maxLevel = 12,
        )
        assertEquals(0, band.bandIndex)
        assertEquals(60, band.frequency)
        assertEquals(3.5f, band.level)
        assertEquals(-12, band.minLevel)
        assertEquals(12, band.maxLevel)
    }
}
