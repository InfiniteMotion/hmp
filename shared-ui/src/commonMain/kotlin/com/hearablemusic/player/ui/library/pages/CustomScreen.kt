package com.hearablemusic.player.ui.library.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_BLUR_RADIUS
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_MATERIAL_PRESET
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_MODE
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_NOISE_FACTOR
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_TINT_ALPHA
import com.hearablemusic.player.ui.common.util.HAZE_MODE_CUSTOM
import com.hearablemusic.player.ui.common.util.HAZE_MODE_PRESET
import com.hearablemusic.player.ui.common.util.HazeMaterialPreset
import com.hearablemusic.player.ui.common.util.hazeMaterialPresetFromValue
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.haze_blur_radius_label
import com.hearablemusic.player.ui.generated.resources.haze_custom_params_title
import com.hearablemusic.player.ui.generated.resources.haze_intensity_desc
import com.hearablemusic.player.ui.generated.resources.haze_intensity_preview_subtitle
import com.hearablemusic.player.ui.generated.resources.haze_intensity_preview_title
import com.hearablemusic.player.ui.generated.resources.haze_intensity_title
import com.hearablemusic.player.ui.generated.resources.haze_intensity_value
import com.hearablemusic.player.ui.generated.resources.haze_mode_custom_desc
import com.hearablemusic.player.ui.generated.resources.haze_mode_custom_title
import com.hearablemusic.player.ui.generated.resources.haze_mode_preset_desc
import com.hearablemusic.player.ui.generated.resources.haze_mode_preset_title
import com.hearablemusic.player.ui.generated.resources.haze_noise_factor_label
import com.hearablemusic.player.ui.generated.resources.haze_preset_list_title
import com.hearablemusic.player.ui.generated.resources.haze_preset_regular
import com.hearablemusic.player.ui.generated.resources.haze_preset_thick
import com.hearablemusic.player.ui.generated.resources.haze_preset_thin
import com.hearablemusic.player.ui.generated.resources.haze_preset_ultra_thick
import com.hearablemusic.player.ui.generated.resources.haze_preset_ultra_thin
import com.hearablemusic.player.ui.generated.resources.haze_tint_alpha_label
import com.hearablemusic.player.ui.generated.resources.set_background_style
import com.hearablemusic.player.ui.generated.resources.set_theme_mode
import com.hearablemusic.player.ui.generated.resources.style_blur
import com.hearablemusic.player.ui.generated.resources.style_blur_desc
import com.hearablemusic.player.ui.generated.resources.style_fluid
import com.hearablemusic.player.ui.generated.resources.style_fluid_desc
import com.hearablemusic.player.ui.generated.resources.style_spots
import com.hearablemusic.player.ui.generated.resources.style_spots_desc
import com.hearablemusic.player.ui.generated.resources.theme_auto
import com.hearablemusic.player.ui.generated.resources.theme_customization
import com.hearablemusic.player.ui.generated.resources.theme_dark
import com.hearablemusic.player.ui.generated.resources.theme_light
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints

@Composable
fun CustomScreen(
    settingsViewModel: SettingsViewModel = activityViewModel(),
    navController: NavBackStack<NavKey>
) {
    val customMode by settingsViewModel.customMode.collectAsState("default")
    val backgroundStyle by settingsViewModel.backgroundStyle.collectAsState("FLUID")
    val hazeMode by settingsViewModel.hazeMode.collectAsState(DEFAULT_HAZE_MODE)
    val hazeMaterialPreset by settingsViewModel.hazeMaterialPreset.collectAsState(DEFAULT_HAZE_MATERIAL_PRESET)
    val hazeBlurRadius by settingsViewModel.hazeBlurRadius.collectAsState(DEFAULT_HAZE_BLUR_RADIUS)
    val hazeNoiseFactor by settingsViewModel.hazeNoiseFactor.collectAsState(DEFAULT_HAZE_NOISE_FACTOR)
    val hazeTintAlpha by settingsViewModel.hazeTintAlpha.collectAsState(DEFAULT_HAZE_TINT_ALPHA)
    
    CustomScreenContent(
        customMode = customMode,
        backgroundStyle = backgroundStyle,
        hazeMode = hazeMode,
        hazeMaterialPreset = hazeMaterialPreset,
        hazeBlurRadius = hazeBlurRadius,
        hazeNoiseFactor = hazeNoiseFactor,
        hazeTintAlpha = hazeTintAlpha,
        onBackClick = { navController.removeLastOrNull() },
        setCustomMode = settingsViewModel::saveCustomMode,
        setBackgroundStyle = settingsViewModel::saveBackgroundStyle,
        setHazeMode = settingsViewModel::saveHazeMode,
        applyHazeMaterialPreset = settingsViewModel::applyHazeMaterialPreset,
        setHazeBlurRadius = settingsViewModel::saveHazeBlurRadius,
        setHazeNoiseFactor = settingsViewModel::saveHazeNoiseFactor,
        setHazeTintAlpha = settingsViewModel::saveHazeTintAlpha
    )
}

@Composable
fun CustomScreenContent(
    customMode: String,
    backgroundStyle: String,
    hazeMode: String,
    hazeMaterialPreset: String,
    hazeBlurRadius: Float,
    hazeNoiseFactor: Float,
    hazeTintAlpha: Float,
    onBackClick: () -> Unit,
    setCustomMode: (String) -> Unit,
    setBackgroundStyle: (String) -> Unit,
    setHazeMode: (String) -> Unit,
    applyHazeMaterialPreset: (String, Float) -> Unit,
    setHazeBlurRadius: (Float) -> Unit,
    setHazeNoiseFactor: (Float) -> Unit,
    setHazeTintAlpha: (Float) -> Unit
) {
    val metrics = LocalHmpMetrics.current
    SubScreen(
        onBackClick = onBackClick,
        title = stringResource(Res.string.theme_customization)
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(metrics.section),
            verticalArrangement = Arrangement.spacedBy(metrics.section)
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.section)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.section)) {
                        SetThemeMode(customMode = customMode, setCustomMode = setCustomMode)
                        SetBackgroundStyle(backgroundStyle = backgroundStyle, setBackgroundStyle = setBackgroundStyle)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.section)) {
                        SetHazeIntensity(
                            hazeMode = hazeMode, hazeMaterialPreset = hazeMaterialPreset,
                            hazeBlurRadius = hazeBlurRadius, hazeNoiseFactor = hazeNoiseFactor,
                            hazeTintAlpha = hazeTintAlpha, setHazeMode = setHazeMode,
                            applyHazeMaterialPreset = applyHazeMaterialPreset,
                            setHazeBlurRadius = setHazeBlurRadius, setHazeNoiseFactor = setHazeNoiseFactor,
                            setHazeTintAlpha = setHazeTintAlpha
                        )
                    }
                }
            } else {
                SetThemeMode(customMode = customMode, setCustomMode = setCustomMode)
                SetBackgroundStyle(backgroundStyle = backgroundStyle, setBackgroundStyle = setBackgroundStyle)
                SetHazeIntensity(hazeMode = hazeMode, hazeMaterialPreset = hazeMaterialPreset, hazeBlurRadius = hazeBlurRadius, hazeNoiseFactor = hazeNoiseFactor, hazeTintAlpha = hazeTintAlpha, setHazeMode = setHazeMode, applyHazeMaterialPreset = applyHazeMaterialPreset, setHazeBlurRadius = setHazeBlurRadius, setHazeNoiseFactor = setHazeNoiseFactor, setHazeTintAlpha = setHazeTintAlpha)
            }
            Spacer(modifier = Modifier.height(metrics.row))
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun SetHazeIntensity(
    hazeMode: String,
    hazeMaterialPreset: String,
    hazeBlurRadius: Float,
    hazeNoiseFactor: Float,
    hazeTintAlpha: Float,
    setHazeMode: (String) -> Unit,
    applyHazeMaterialPreset: (String, Float) -> Unit,
    setHazeBlurRadius: (Float) -> Unit,
    setHazeNoiseFactor: (Float) -> Unit,
    setHazeTintAlpha: (Float) -> Unit
) {
    val metrics = LocalHmpMetrics.current
    val hazeState = rememberHazeState()
    val typo = LocalHmpTypo.current
    val selectedPreset = hazeMaterialPresetFromValue(hazeMaterialPreset)
    val activeIntensity = selectedPreset.intensity
    val percent = (activeIntensity * 100).toInt()
    val haptic = rememberHapticFeedback()

    TitleWidget(title = stringResource(Res.string.haze_intensity_title)) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.cozy, vertical = metrics.compact),
            verticalArrangement = Arrangement.spacedBy(metrics.compact)
        ) {
            Text(
                text = stringResource(Res.string.haze_intensity_desc),
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HazeSelectionOption(
                title = stringResource(Res.string.haze_mode_preset_title),
                description = stringResource(Res.string.haze_mode_preset_desc),
                isSelected = hazeMode == HAZE_MODE_PRESET,
                onClick = {
                    applyHazeMaterialPreset(selectedPreset.value, selectedPreset.intensity)
                    haptic.performClick()
                }
            )

            HazeSelectionOption(
                title = stringResource(Res.string.haze_mode_custom_title),
                description = stringResource(Res.string.haze_mode_custom_desc),
                isSelected = hazeMode == HAZE_MODE_CUSTOM,
                onClick = {
                    setHazeMode(HAZE_MODE_CUSTOM)
                    haptic.performClick()
                }
            )

            if (hazeMode == HAZE_MODE_PRESET) {
                Text(
                    text = stringResource(Res.string.haze_preset_list_title),
                    style = typo.body.medium(),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(verticalArrangement = Arrangement.spacedBy(metrics.compact)) {
                    HazeMaterialPreset.entries.forEach { preset ->
                        HazeSelectionOption(
                            title = stringResource(preset.labelResId()),
                            description = null,
                            isSelected = preset == selectedPreset,
                            onClick = {
                                applyHazeMaterialPreset(preset.value, preset.intensity)
                                haptic.performClick()
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.haze_custom_params_title),
                    style = typo.body.medium(),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = stringResource(Res.string.haze_blur_radius_label, hazeBlurRadius),
                    style = typo.body,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = hazeBlurRadius,
                    onValueChange = { value ->
                        setHazeBlurRadius(value)
                    },
                    valueRange = 0f..60f
                )

                val noisePercent = (hazeNoiseFactor * 100).toInt()
                Text(
                    text = stringResource(Res.string.haze_noise_factor_label, noisePercent),
                    style = typo.body,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = hazeNoiseFactor,
                    onValueChange = { value ->
                        setHazeNoiseFactor(value)
                    },
                    valueRange = 0f..1f
                )

                val tintPercent = (hazeTintAlpha * 100).toInt()
                Text(
                    text = stringResource(Res.string.haze_tint_alpha_label, tintPercent),
                    style = typo.body,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = hazeTintAlpha,
                    onValueChange = { value ->
                        setHazeTintAlpha(value)
                    },
                    valueRange = 0f..1f
                )
            }

            if (hazeMode == HAZE_MODE_PRESET) {
                Text(
                    text = stringResource(Res.string.haze_intensity_value, percent),
                    style = typo.body.medium(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LayoutConstraints.ListMaxHeight)
                    .clip(RoundedCornerShape(metrics.cozy))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                            )
                        )
                    )
                    .hazeSource(state = hazeState),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = metrics.base)
                        .clip(RoundedCornerShape(metrics.cozy))
                        .hazeEffect(
                            state = hazeState,
                            style = hazeStyleForIntensity()
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = metrics.base, vertical = metrics.base)
                    ) {
                        Text(
                            text = stringResource(Res.string.haze_intensity_preview_title),
                            style = typo.body.medium()
                        )
                        Text(
                            text = stringResource(Res.string.haze_intensity_preview_subtitle),
                            style = typo.footnote,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HazeSelectionOption(
    title: String,
    description: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(metrics.base),
        colors = CardDefaults.cardColors(
            containerColor = Transparent
        ),
        border = if (isSelected) BorderStroke(metrics.stroke.thin, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.base),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
            Spacer(modifier = Modifier.width(metrics.base))
            Column {
                Text(
                    text = title,
                    style = typo.body,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun HazeMaterialPreset.labelResId(): StringResource {
    return when (this) {
        HazeMaterialPreset.ULTRA_THIN -> Res.string.haze_preset_ultra_thin
        HazeMaterialPreset.THIN -> Res.string.haze_preset_thin
        HazeMaterialPreset.REGULAR -> Res.string.haze_preset_regular
        HazeMaterialPreset.THICK -> Res.string.haze_preset_thick
        HazeMaterialPreset.ULTRA_THICK -> Res.string.haze_preset_ultra_thick
    }
}

@Composable
fun SetThemeMode(
    customMode: String,
    setCustomMode: (String) -> Unit
){
    val metrics = LocalHmpMetrics.current
    TitleWidget(
        title = stringResource(Res.string.set_theme_mode),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.cozy),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val haptic = rememberHapticFeedback()
            ThemeModeButton(
                text = stringResource(Res.string.theme_light),
                isSelected = customMode == "light",
                onClick = {
                    setCustomMode("light")
                    haptic.performClick()
                }
            )
            ThemeModeButton(
                text = stringResource(Res.string.theme_dark),
                isSelected = customMode == "dark",
                onClick = {
                    setCustomMode("dark")
                    haptic.performClick()
                }
            )
            ThemeModeButton(
                text = stringResource(Res.string.theme_auto),
                isSelected = customMode == "default",
                onClick = {
                    setCustomMode("default")
                    haptic.performClick()
                }
            )
        }
    }
}

@Composable
fun ThemeModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
){
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(metrics.row),
        colors = ButtonDefaults.buttonColors(
            containerColor = Transparent,
        ),
        border = if(isSelected) BorderStroke(metrics.hair, color = MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .width(metrics.row)
    ) {
        Text(
            text = text,
            style = typo.body,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SetBackgroundStyle(
    backgroundStyle: String,
    setBackgroundStyle: (String) -> Unit
) {
    val metrics = LocalHmpMetrics.current
    TitleWidget(
        title = stringResource(Res.string.set_background_style),
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.cozy, vertical = metrics.compact),
            verticalArrangement = Arrangement.spacedBy(metrics.compact)
        ) {
            val haptic = rememberHapticFeedback()
            
            BackgroundStyleOption(
                title = stringResource(Res.string.style_fluid),
                description = stringResource(Res.string.style_fluid_desc),
                isSelected = backgroundStyle == "FLUID",
                onClick = {
                    setBackgroundStyle("FLUID")
                    haptic.performClick()
                }
            )
            
            BackgroundStyleOption(
                title = stringResource(Res.string.style_spots),
                description = stringResource(Res.string.style_spots_desc),
                isSelected = backgroundStyle == "SPOTS",
                onClick = {
                    setBackgroundStyle("SPOTS")
                    haptic.performClick()
                }
            )
            
            BackgroundStyleOption(
                title = stringResource(Res.string.style_blur),
                description = stringResource(Res.string.style_blur_desc),
                isSelected = backgroundStyle == "BLUR",
                onClick = {
                    setBackgroundStyle("BLUR")
                    haptic.performClick()
                }
            )
        }
    }
}

@Composable
fun BackgroundStyleOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(metrics.base),
        colors = CardDefaults.cardColors(
            containerColor = Transparent
        ),
        border = if (isSelected) BorderStroke(metrics.stroke.thin, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.cozy),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null // Handled by Card clickable
            )
            Spacer(modifier = Modifier.width(metrics.cozy))
            Column {
                Text(
                    text = title,
                    style = typo.subtitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
