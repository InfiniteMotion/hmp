package com.hearablemusic.player.ui.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.design.metrics.HMPMetricTokens
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.audio_effects_settings
import com.hearablemusic.player.ui.generated.resources.bass_boost
import com.hearablemusic.player.ui.generated.resources.church
import com.hearablemusic.player.ui.generated.resources.close
import com.hearablemusic.player.ui.generated.resources.custom_equalizer
import com.hearablemusic.player.ui.generated.resources.hall
import com.hearablemusic.player.ui.generated.resources.large_room
import com.hearablemusic.player.ui.generated.resources.preset_equalizer
import com.hearablemusic.player.ui.generated.resources.reset_all
import com.hearablemusic.player.ui.generated.resources.reverb
import com.hearablemusic.player.ui.generated.resources.small_room
import com.hearablemusic.player.ui.generated.resources.surround_sound
import com.hearablemusic.player.ui.settings.viewmodel.AudioEffectViewModel
import com.hmp.domain.setting.model.AudioEffectSettings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

@Composable
fun AudioEffectsScreen(
    viewModel: AudioEffectViewModel = koinViewModel(),
    navController: NavBackStack<NavKey>,
) {
    val audioEffectSettings by viewModel.audioEffectSettings.collectAsState()
    val equalizerPresets by viewModel.equalizerPresets.collectAsState()
    val equalizerBandCount by viewModel.equalizerBandCount.collectAsState()
    val equalizerBandLevelRange by viewModel.equalizerBandLevelRange.collectAsState()
    val currentEqualizerBandLevels by viewModel.currentEqualizerBandLevels.collectAsState()

    // 初始化音效状态
    LaunchedEffect(Unit) {
        viewModel.initializeAudioEffects()
    }

    AudioEffectsScreenContent(
        audioEffectSettings = audioEffectSettings,
        equalizerPresets = equalizerPresets,
        equalizerBandCount = equalizerBandCount,
        equalizerBandLevelRange = equalizerBandLevelRange,
        currentEqualizerBandLevels = currentEqualizerBandLevels,
        onBackClick = { navController.removeLastOrNull() },
        onSetEqualizerPreset = viewModel::setEqualizerPreset,
        onSetBassBoost = viewModel::setBassBoost,
        onSetSurroundSound = viewModel::setSurroundSound,
        onSetReverb = viewModel::setReverb,
        onSetCustomEqualizer = viewModel::setCustomEqualizer,
    )
}

@Composable
fun AudioEffectsScreenContent(
    audioEffectSettings: AudioEffectSettings,
    equalizerPresets: List<String>,
    equalizerBandCount: Int,
    equalizerBandLevelRange: Pair<Int, Int>,
    currentEqualizerBandLevels: FloatArray,
    onBackClick: () -> Unit,
    onSetEqualizerPreset: (Int) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onSetSurroundSound: (Boolean) -> Unit,
    onSetReverb: (Int) -> Unit,
    onSetCustomEqualizer: (FloatArray) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    // 使用SubScreen模板
    SubScreen(
        onBackClick = onBackClick,
        title = stringResource(Res.string.audio_effects_settings),
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(metrics.section),
            verticalArrangement = Arrangement.spacedBy(metrics.section),
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.section),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.section)) {
                        TitleWidget(
                            title = stringResource(Res.string.preset_equalizer),
                        ) {
                            EqualizerPresetSelector(
                                presets = equalizerPresets,
                                currentPreset = audioEffectSettings.equalizerPreset,
                                onPresetSelected = onSetEqualizerPreset,
                            )
                        }

                        TitleWidget(
                            title = stringResource(Res.string.audio_effects_settings),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(metrics.cozy),
                                verticalArrangement = Arrangement.spacedBy(metrics.cozy),
                            ) {
                                BassBoostSlider(
                                    currentLevel = audioEffectSettings.bassBoostLevel,
                                    onLevelChanged = onSetBassBoost,
                                )
                                SurroundSoundToggle(
                                    isEnabled = audioEffectSettings.isSurroundSoundEnabled,
                                    onToggle = onSetSurroundSound,
                                )
                                ReverbSettings(
                                    currentPreset = audioEffectSettings.reverbPreset,
                                    onPresetChanged = onSetReverb,
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.section)) {
                        TitleWidget(
                            title = stringResource(Res.string.custom_equalizer),
                        ) {
                            CustomEqualizer(
                                bandCount = equalizerBandCount,
                                bandLevelRange = equalizerBandLevelRange,
                                currentBandLevels = currentEqualizerBandLevels,
                                onBandLevelChanged = { index, level ->
                                    val newLevels = currentEqualizerBandLevels.copyOf()
                                    newLevels[index] = level
                                    onSetCustomEqualizer(newLevels)
                                },
                                onResetAll = {
                                    // 重置所有频段到0
                                    val resetLevels = FloatArray(equalizerBandCount)
                                    onSetCustomEqualizer(resetLevels)
                                },
                            )
                        }
                    }
                }
            } else {
                TitleWidget(title = stringResource(Res.string.preset_equalizer)) {
                    EqualizerPresetSelector(presets = equalizerPresets, currentPreset = audioEffectSettings.equalizerPreset, onPresetSelected = onSetEqualizerPreset)
                }
                TitleWidget(title = stringResource(Res.string.audio_effects_settings)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(metrics.cozy), verticalArrangement = Arrangement.spacedBy(metrics.cozy)) {
                        BassBoostSlider(currentLevel = audioEffectSettings.bassBoostLevel, onLevelChanged = onSetBassBoost)
                        SurroundSoundToggle(isEnabled = audioEffectSettings.isSurroundSoundEnabled, onToggle = onSetSurroundSound)
                        ReverbSettings(currentPreset = audioEffectSettings.reverbPreset, onPresetChanged = onSetReverb)
                    }
                }
                TitleWidget(title = stringResource(Res.string.custom_equalizer)) {
                    CustomEqualizer(
                        bandCount = equalizerBandCount,
                        bandLevelRange = equalizerBandLevelRange,
                        currentBandLevels = currentEqualizerBandLevels,
                        onBandLevelChanged = { index, level ->
                            val n = currentEqualizerBandLevels.copyOf()
                            n[index] = level
                            onSetCustomEqualizer(n)
                        },
                        onResetAll = {
                            val r = FloatArray(equalizerBandCount)
                            onSetCustomEqualizer(r)
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(metrics.row))
        }
    }
}

// 音效预设选择器组件
@Composable
fun EqualizerPresetSelector(
    presets: List<String>,
    currentPreset: Int,
    onPresetSelected: (Int) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(metrics.cozy),
        verticalArrangement = Arrangement.spacedBy(metrics.cozy),
    ) {
        // 将预设列表分成两行
        val rows = presets.chunked(2)
        rows.forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
            ) {
                rowPresets.forEachIndexed { rowIndex, preset ->
                    val globalIndex = rows.indexOf(rowPresets) * 2 + rowIndex
                    val isSelected = globalIndex == currentPreset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(metrics.base))
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Transparent,
                                shape = RoundedCornerShape(metrics.base),
                            )
                            .clickable {
                                onPresetSelected(globalIndex)
                            }
                            .padding(metrics.cozy),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = preset,
                            style = typo.body,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// 低音增强滑块组件
@Composable
fun BassBoostSlider(
    currentLevel: Int,
    onLevelChanged: (Int) -> Unit,
) {
    val typo = LocalHmpTypo.current
    var sliderValue by remember { mutableFloatStateOf(currentLevel.toFloat()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.bass_boost),
                style = typo.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$currentLevel%",
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                sliderValue = newValue
                onLevelChanged(newValue.toInt())
            },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// 环绕声开关组件
@Composable
fun SurroundSoundToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onToggle(!isEnabled)
            }
            .padding(metrics.cozy),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.surround_sound),
            style = typo.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Checkbox(
            checked = isEnabled,
            onCheckedChange = onToggle,
        )
    }
}

// 混响设置组件
@Composable
fun ReverbSettings(
    currentPreset: Int,
    onPresetChanged: (Int) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val reverbPresets = listOf(stringResource(Res.string.close), stringResource(Res.string.small_room), stringResource(Res.string.large_room), stringResource(Res.string.hall), stringResource(Res.string.church))

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.reverb),
            style = typo.body,
            modifier = Modifier.padding(bottom = metrics.compact),
            color = MaterialTheme.colorScheme.onSurface,
        )
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(metrics.base),
        ) {
            // 将混响预设分成两行，第一行3个，第二行2个
            val firstRow = reverbPresets.take(3)
            val secondRow = reverbPresets.drop(3)

            // 第一行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.base),
            ) {
                firstRow.forEachIndexed { index, preset ->
                    val isSelected = index == currentPreset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(metrics.compact))
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Transparent,
                                shape = RoundedCornerShape(metrics.compact),
                            )
                            .clickable {
                                onPresetChanged(index)
                            }
                            .padding(metrics.base),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = preset,
                            style = typo.footnote,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // 第二行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.base),
            ) {
                secondRow.forEachIndexed { index, preset ->
                    val globalIndex = 3 + index
                    val isSelected = globalIndex == currentPreset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(metrics.compact))
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Transparent,
                                shape = RoundedCornerShape(metrics.compact),
                            )
                            .clickable {
                                onPresetChanged(globalIndex)
                            }
                            .padding(metrics.base),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = preset,
                            style = typo.footnote,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// 自定义均衡器调节组件
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomEqualizer(
    bandCount: Int,
    bandLevelRange: Pair<Int, Int>,
    currentBandLevels: FloatArray,
    onBandLevelChanged: (Int, Float) -> Unit,
    onResetAll: () -> Unit = {},
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val haptic = rememberHapticFeedback()
    // 频段标签（单位：Hz）
    val frequencyLabels = when (bandCount) {
        5 -> listOf("60", "230", "910", "3.6k", "14k")
        else -> (1..bandCount).map { "$it" }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(metrics.cozy),
        verticalArrangement = Arrangement.spacedBy(metrics.cozy),
    ) {
        // 均衡器滑块区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.compact),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            for (i in 0 until bandCount) {
                EqualizerBand(
                    frequencyLabel = frequencyLabels.getOrElse(i) { "${i + 1}" },
                    currentLevel = currentBandLevels.getOrElse(i) { 0f },
                    levelRange = bandLevelRange,
                    onLevelChanged = { level -> onBandLevelChanged(i, level) },
                )
            }
        }
        // 重置全部按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(metrics.compact))
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(metrics.compact),
                    )
                    .clickable {
                        haptic.performClick()
                        onResetAll()
                    }
                    .padding(horizontal = metrics.base, vertical = metrics.compact),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.reset_all),
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// 单个频段滑块组件
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBand(
    frequencyLabel: String,
    currentLevel: Float,
    levelRange: Pair<Int, Int>,
    onLevelChanged: (Float) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val haptic = rememberHapticFeedback()
    var sliderValue by remember(currentLevel) { mutableFloatStateOf(currentLevel) }
    val normalizedValue = (sliderValue - levelRange.first) / (levelRange.second - levelRange.first)
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.compact),
        modifier = Modifier.padding(horizontal = metrics.micro),
    ) {
        // 数值显示
        Text(
            text = if (sliderValue >= 0) "+${sliderValue.toInt()}" else "${sliderValue.toInt()}",
            style = typo.caption,
            color = if (sliderValue > 0) {
                MaterialTheme.colorScheme.primary
            } else if (sliderValue < 0) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(bottom = metrics.cozy),
        )

        // 垂直滑块（使用自定义手势处理）
        Box(
            modifier = Modifier
                .size(metrics.bar, metrics.card)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            haptic.performDragStart()
                        },
                        onDragEnd = {
                            isDragging = false
                            haptic.performGestureEnd()
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val trackHeightPx = with(density) { HMPMetricTokens.Compact.card.toPx() }
                            val delta = -dragAmount / trackHeightPx * (levelRange.second - levelRange.first)
                            val newValue = (sliderValue + delta).coerceIn(
                                levelRange.first.toFloat(),
                                levelRange.second.toFloat(),
                            )
                            sliderValue = newValue
                            onLevelChanged(newValue)
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        haptic.performClick()
                        val trackHeightPx = with(density) { HMPMetricTokens.Compact.card.toPx() }
                        val normalizedPosition = 1f - (offset.y / trackHeightPx).coerceIn(0f, 1f)
                        val newValue = levelRange.first + normalizedPosition * (levelRange.second - levelRange.first)
                        sliderValue = newValue.coerceIn(
                            levelRange.first.toFloat(),
                            levelRange.second.toFloat(),
                        )
                        onLevelChanged(sliderValue)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 背景轨道
            Box(
                modifier = Modifier
                    .size(metrics.compact, metrics.card)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(metrics.micro),
                    ),
            )

            // 活动轨道（从中心到当前位置）
            Box(
                modifier = Modifier
                    .size(HMPMetricTokens.Compact.compact, (HMPMetricTokens.Compact.card * abs(normalizedValue - 0.5f) * 2).coerceAtLeast(0.dp))
                    .align(
                        if (sliderValue >= 0) {
                            Alignment.TopCenter
                        } else {
                            Alignment.BottomCenter
                        },
                    )
                    .background(
                        color = if (sliderValue > 0) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        } else if (sliderValue < 0) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        } else {
                            Transparent
                        },
                        shape = RoundedCornerShape(metrics.micro),
                    ),
            )

            // 中心线（0dB位置）
            Box(
                modifier = Modifier
                    .size(metrics.cozy, metrics.hair)
                    .align(Alignment.Center)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    ),
            )

            // 滑块 thumb
            // normalizedValue: 0(最小值) -> 底部, 0.5(0dB) -> 中心, 1(最大值) -> 顶部
            // 轨道高度200dp，从中心Alignment.Center开始计算偏移
            // 偏移范围: -100dp(顶部) 到 +100dp(底部)
            val thumbOffsetY = with(density) {
                (LayoutConstraints.EqualizerBaseOffset - HMPMetricTokens.Compact.card * normalizedValue).toPx()
            }
            Box(
                modifier = Modifier
                    .size(metrics.section, metrics.section)
                    .align(Alignment.Center)
                    .offset(y = with(density) { thumbOffsetY.toDp() })
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(metrics.micro),
                    ),
            )
        }

        // 频段标签
        Text(
            text = frequencyLabel,
            style = typo.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = metrics.cozy),
        )
    }
}
