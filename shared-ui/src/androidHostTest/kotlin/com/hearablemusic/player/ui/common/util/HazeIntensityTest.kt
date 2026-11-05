package com.hearablemusic.player.ui.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HazeIntensityTest {

    // ===== HazeMaterialPreset =====

    @Test
    fun hazeMaterialPreset_allHaveUniqueValues() {
        val values = HazeMaterialPreset.entries.map { it.value }
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun hazeMaterialPreset_allHavePositiveIntensity() {
        for (preset in HazeMaterialPreset.entries) {
            assertTrue("${preset.name} intensity should be > 0", preset.intensity > 0f)
        }
    }

    @Test
    fun hazeMaterialPreset_ultraThin_intensity() {
        assertEquals(0.08f, HazeMaterialPreset.ULTRA_THIN.intensity, 0.001f)
    }

    @Test
    fun hazeMaterialPreset_thick_intensity() {
        assertEquals(0.75f, HazeMaterialPreset.THICK.intensity, 0.001f)
    }

    @Test
    fun hazeMaterialPreset_ultraThick_intensity() {
        assertEquals(1.0f, HazeMaterialPreset.ULTRA_THICK.intensity, 0.001f)
    }

    // ===== hazeMaterialPresetFromValue =====

    @Test
    fun hazeMaterialPresetFromValue_valid_returnsCorrect() {
        assertEquals(HazeMaterialPreset.REGULAR, hazeMaterialPresetFromValue("regular"))
        assertEquals(HazeMaterialPreset.THIN, hazeMaterialPresetFromValue("thin"))
        assertEquals(HazeMaterialPreset.THICK, hazeMaterialPresetFromValue("thick"))
    }

    @Test
    fun hazeMaterialPresetFromValue_invalid_returnsRegular() {
        assertEquals(HazeMaterialPreset.REGULAR, hazeMaterialPresetFromValue("unknown"))
        assertEquals(HazeMaterialPreset.REGULAR, hazeMaterialPresetFromValue(""))
    }

    // ===== normalizeHazeIntensity =====

    @Test
    fun normalizeHazeIntensity_withinRange_returnsSame() {
        assertEquals(0.5f, normalizeHazeIntensity(0.5f), 0.001f)
    }

    @Test
    fun normalizeHazeIntensity_belowZero_clampsToZero() {
        assertEquals(0f, normalizeHazeIntensity(-0.5f), 0.001f)
    }

    @Test
    fun normalizeHazeIntensity_aboveOne_clampsToOne() {
        assertEquals(1f, normalizeHazeIntensity(1.5f), 0.001f)
    }

    @Test
    fun normalizeHazeIntensity_zero_returnsZero() {
        assertEquals(0f, normalizeHazeIntensity(0f), 0.001f)
    }

    @Test
    fun normalizeHazeIntensity_one_returnsOne() {
        assertEquals(1f, normalizeHazeIntensity(1f), 0.001f)
    }

    // ===== HazeRenderSettings =====

    @Test
    fun hazeRenderSettings_defaults() {
        val settings = HazeRenderSettings()
        assertEquals(HAZE_MODE_CUSTOM, settings.mode)
        assertEquals(DEFAULT_HAZE_MATERIAL_PRESET, settings.preset)
        assertEquals(DEFAULT_HAZE_INTENSITY, settings.intensity, 0.001f)
        assertEquals(DEFAULT_HAZE_BLUR_RADIUS, settings.blurRadius, 0.001f)
        assertEquals(DEFAULT_HAZE_NOISE_FACTOR, settings.noiseFactor, 0.001f)
        assertEquals(DEFAULT_HAZE_TINT_ALPHA, settings.tintAlpha, 0.001f)
    }

    @Test
    fun hazeRenderSettings_customValues() {
        val settings = HazeRenderSettings(
            mode = "preset",
            preset = "thick",
            intensity = 0.8f,
            blurRadius = 30f,
            noiseFactor = 0.2f,
            tintAlpha = 0.3f,
        )
        assertEquals("preset", settings.mode)
        assertEquals("thick", settings.preset)
        assertEquals(0.8f, settings.intensity, 0.001f)
    }

    // ===== Constants =====

    @Test
    fun constants_haveExpectedValues() {
        assertEquals("custom", HAZE_MODE_CUSTOM)
        assertEquals("preset", HAZE_MODE_PRESET)
        assertEquals(HAZE_MODE_CUSTOM, DEFAULT_HAZE_MODE)
        assertEquals("regular", DEFAULT_HAZE_MATERIAL_PRESET)
        assertEquals(20f, DEFAULT_HAZE_BLUR_RADIUS, 0.001f)
        assertEquals(0.15f, DEFAULT_HAZE_NOISE_FACTOR, 0.001f)
        assertEquals(0.22f, DEFAULT_HAZE_TINT_ALPHA, 0.001f)
    }
}
