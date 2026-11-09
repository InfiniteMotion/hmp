package com.hearablemusic.player.ui.common.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

const val DEFAULT_HAZE_INTENSITY: Float = 0.6f
const val HAZE_MODE_CUSTOM = "custom"
const val HAZE_MODE_PRESET = "preset"
const val DEFAULT_HAZE_MODE = HAZE_MODE_CUSTOM
const val DEFAULT_HAZE_MATERIAL_PRESET = "regular"
const val DEFAULT_HAZE_BLUR_RADIUS: Float = 20f
const val DEFAULT_HAZE_NOISE_FACTOR: Float = 0.15f
const val DEFAULT_HAZE_TINT_ALPHA: Float = 0.22f

enum class HazeMaterialPreset(val value: String, val intensity: Float) {
    ULTRA_THIN("ultra_thin", 0.08f),
    THIN("thin", 0.25f),
    REGULAR("regular", 0.50f),
    THICK("thick", 0.75f),
    ULTRA_THICK("ultra_thick", 1.0f),
}

fun hazeMaterialPresetFromValue(value: String): HazeMaterialPreset = HazeMaterialPreset.entries.firstOrNull { it.value == value } ?: HazeMaterialPreset.REGULAR

data class HazeRenderSettings(
    val mode: String = DEFAULT_HAZE_MODE,
    val preset: String = DEFAULT_HAZE_MATERIAL_PRESET,
    val intensity: Float = DEFAULT_HAZE_INTENSITY,
    val blurRadius: Float = DEFAULT_HAZE_BLUR_RADIUS,
    val noiseFactor: Float = DEFAULT_HAZE_NOISE_FACTOR,
    val tintAlpha: Float = DEFAULT_HAZE_TINT_ALPHA,
)

val LocalHazeRenderSettings = staticCompositionLocalOf { HazeRenderSettings() }

/** AppRoot 的背景采样源。屏内自持的弹窗读取它来保留毛玻璃；未提供时为 null（走不透明回退）。 */
val LocalAppHazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun ProvideHazeRenderSettings(
    settings: HazeRenderSettings,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalHazeRenderSettings provides settings, content = content)
}

fun normalizeHazeIntensity(intensity: Float): Float = intensity.coerceIn(0f, 1f)

private fun resolveEffectiveIntensity(settings: HazeRenderSettings): Float {
    if (settings.mode == HAZE_MODE_PRESET) {
        return hazeMaterialPresetFromValue(settings.preset).intensity
    }
    return normalizeHazeIntensity(settings.intensity)
}

@Composable
fun hazeTintAlpha(): Float {
    val settings = LocalHazeRenderSettings.current
    if (settings.mode == HAZE_MODE_CUSTOM) {
        return settings.tintAlpha.coerceIn(0f, 1f)
    }
    val normalized = resolveEffectiveIntensity(settings)
    return 0.08f + normalized * 0.24f
}

@Composable
@OptIn(ExperimentalHazeMaterialsApi::class)
fun hazeStyleForIntensity(): HazeStyle {
    val settings = LocalHazeRenderSettings.current
    if (settings.mode == HAZE_MODE_CUSTOM) {
        val tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = settings.tintAlpha.coerceIn(0f, 1f)))
        return HazeMaterials.regular().copy(
            backgroundColor = MaterialTheme.colorScheme.surface,
            tints = listOf(tint),
            blurRadius = settings.blurRadius.coerceAtLeast(0f).dp,
            noiseFactor = settings.noiseFactor.coerceIn(0f, 1f),
            fallbackTint = tint,
        )
    }

    return when (resolveEffectiveIntensity(settings)) {
        in 0f..0.15f -> HazeMaterials.ultraThin()
        in 0.15f..0.4f -> HazeMaterials.thin()
        in 0.4f..0.65f -> HazeMaterials.regular()
        in 0.65f..0.9f -> HazeMaterials.thick()
        else -> HazeMaterials.ultraThick()
    }
}
