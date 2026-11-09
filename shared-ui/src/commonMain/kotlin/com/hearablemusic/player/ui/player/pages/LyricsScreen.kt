package com.hearablemusic.player.ui.player.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import com.hearablemusic.player.ui.common.components.SegmentedOption
import com.hearablemusic.player.ui.common.components.VerticalSegmentedControl
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.util.nowEpochMillis
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.align_center
import com.hearablemusic.player.ui.generated.resources.align_left
import com.hearablemusic.player.ui.generated.resources.align_right
import com.hearablemusic.player.ui.generated.resources.back
import com.hearablemusic.player.ui.generated.resources.bilingual
import com.hearablemusic.player.ui.generated.resources.decrease
import com.hearablemusic.player.ui.generated.resources.highlight_line
import com.hearablemusic.player.ui.generated.resources.ic_gallery_search_things
import com.hearablemusic.player.ui.generated.resources.increase
import com.hearablemusic.player.ui.generated.resources.karaoke_lyrics
import com.hearablemusic.player.ui.generated.resources.line_spacing
import com.hearablemusic.player.ui.generated.resources.minus
import com.hearablemusic.player.ui.generated.resources.more_settings
import com.hearablemusic.player.ui.generated.resources.original_text
import com.hearablemusic.player.ui.generated.resources.plus
import com.hearablemusic.player.ui.generated.resources.text_aligncenter
import com.hearablemusic.player.ui.generated.resources.text_alignleft
import com.hearablemusic.player.ui.generated.resources.text_alignright
import com.hearablemusic.player.ui.generated.resources.translate
import com.hearablemusic.player.ui.generated.resources.translate_c2e
import com.hearablemusic.player.ui.generated.resources.translate_e2c
import com.hearablemusic.player.ui.generated.resources.translated_text
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.platform.rememberStatusBarsController
import com.hearablemusic.player.ui.player.viewmodel.PlayerScreenViewModel
import com.hearablemusic.player.ui.settings.viewmodel.LyricsSettingsViewModel
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * 独立歌词页面
 * 提供全屏歌词展示和实时参数调节功能
 */
@Composable
fun LyricsScreen(
    // 7.5 P1：旧 VM 是 controller 的零逻辑包装，这里直接用平台门面（白名单允许）
    playbackController: PlaybackController = koinInject(),
    lyricsSettingsViewModel: LyricsSettingsViewModel = koinViewModel(),
    onNavigateToSettings: () -> Unit = {},
) {
    val metrics = LocalHmpMetrics.current
    // 播放页常驻：歌词页与播放页共用同一个应用级 VM 的单一出口，不再各自 collect 四条播放流。
    val playerViewModel: PlayerScreenViewModel = activityViewModel()
    val playerUiState by playerViewModel.uiState.collectAsState()
    val lyricsPresentation = playerUiState.player.lyrics
    val currentPosition = playerUiState.player.currentPosition
    val duration = playerUiState.player.duration
    val isPlaying = playerUiState.player.isPlaying

    // 歌词参数（D4-01：读 FULLSCREEN 组件配置，与设置页同一份数据）
    val lyricsConfig by lyricsSettingsViewModel.fullscreenLyricsConfig.collectAsState()
    val originalTextSize = lyricsConfig.originalTextSize
    val translatedTextSize = lyricsConfig.translatedTextSize
    val currentTimeTextSize = lyricsConfig.currentTimeTextSize
    val lineSpacing = lyricsConfig.lineSpacing
    val displayMode = lyricsConfig.displayMode
    val alignment = lyricsConfig.alignment
    val karaokeEnabled = lyricsConfig.karaokeEnabled
    // 应用级实例：与 AppRoot 共用，避免 entry 级 VM 重复订阅播放流、各自取色
    val themeViewModel: ThemeViewModel = activityViewModel()
    val paletteColors by themeViewModel.paletteColors.collectAsState()

    var isSettingsPanelVisible by remember { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(nowEpochMillis()) }
    val haptic = rememberHapticFeedback()
    val statusBars = rememberStatusBarsController()

    // 桌面悬停态（对齐旧桌面版行为）：鼠标在页面上时控件常显，离开后走自动隐藏计时；
    // 触屏端 isHovered 恒为 false，行为与纯触屏逻辑完全一致
    val hoverInteractionSource = remember { MutableInteractionSource() }
    val isHovered by hoverInteractionSource.collectIsHoveredAsState()

    // 进度跟踪归播放器生命周期所有（playOrResume / playCurrentTrack / resume 内部已启动，
    // pause / release 内部会停止）。UI 不再手动 start：否则 onDispose 无配对 stop，
    // 且 Player 与 Lyrics 同时存活时 UI 侧 stop 会停掉另一方。
    DisposableEffect(Unit) {
        onDispose {
            // 退出时恢复底部播放栏和状态栏
            playbackController.setMiniPlayerVisible(true)
            statusBars?.show()
        }
    }

    // 自动隐藏逻辑：5秒无操作自动隐藏（悬停/设置面板打开时保持常显）
    LaunchedEffect(lastInteractionTime, isSettingsPanelVisible, isHovered) {
        if (isSettingsPanelVisible || isHovered) {
            // 设置面板打开或鼠标悬停时，始终显示控件
            isControlsVisible = true
            statusBars?.show()
        } else {
            // 设置面板关闭时，5秒后隐藏
            isControlsVisible = true
            statusBars?.show()
            delay(5000L)
            isControlsVisible = false
            statusBars?.hide()
        }
    }

    // 同步控制 MiniPlayerBar 的显示隐藏
    LaunchedEffect(isControlsVisible) {
        playbackController.setMiniPlayerVisible(isControlsVisible)
    }

    val isLandscape = LocalWindowSizeInfo.current.isLandscape
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(hoverInteractionSource)
            .pointerInput(Unit) {
                // 点击屏幕切换显示/隐藏状态
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // 任何交互都重置隐藏计时器并显示控件
                        lastInteractionTime = nowEpochMillis()
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // 歌词展示区域
            AdvancedLyrics(
                modifier = if (!isSettingsPanelVisible) Modifier.weight(1f) else Modifier.padding(bottom = if (isLandscape) metrics.card else metrics.hero),
                lyricsPresentation = lyricsPresentation,
                currentPosition = currentPosition,
                onSeek = {
                    lastInteractionTime = nowEpochMillis()
                    playbackController.seekTo(it)
                },
                originalTextSize = originalTextSize,
                translatedTextSize = translatedTextSize,
                currentTimeTextSize = currentTimeTextSize,
                lineSpacing = lineSpacing,
                karaokeEnabled = karaokeEnabled,
                paletteColors = paletteColors,
                isPlaying = isPlaying,
                displayMode = displayMode,
                alignment = alignment,
            )
        }
        // 设置面板（固定在底部）
        AnimatedVisibility(
            visible = isSettingsPanelVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (isLandscape) Modifier.widthIn(max = LayoutConstraints.HeightBreakpointMedium) else Modifier.fillMaxWidth())
                .padding(bottom = if (isLandscape) metrics.block else metrics.panel),
            enter = fadeIn(HmpMotion.enter()) + slideInVertically(HmpMotion.enter()) { it },
            exit = fadeOut(HmpMotion.exit()) + slideOutVertically(HmpMotion.exit()) { it },
        ) {
            LyricsSettingsPanel(
                originalTextSize = originalTextSize,
                translatedTextSize = translatedTextSize,
                currentTimeTextSize = currentTimeTextSize,
                lineSpacing = lineSpacing,
                displayMode = displayMode,
                alignment = alignment,
                karaokeEnabled = karaokeEnabled,
                onKaraokeEnabledChange = lyricsSettingsViewModel::onKaraokeEnabledChange,
                onOriginalTextSizeChange = lyricsSettingsViewModel::onOriginalTextSizeChange,
                onTranslatedTextSizeChange = lyricsSettingsViewModel::onTranslatedTextSizeChange,
                onCurrentTimeTextSizeChange = lyricsSettingsViewModel::onCurrentTimeTextSizeChange,
                onLineSpacingChange = lyricsSettingsViewModel::onLineSpacingChange,
                onDisplayModeChange = lyricsSettingsViewModel::onDisplayModeChange,
                onAlignmentChange = lyricsSettingsViewModel::onAlignmentChange,
                onNavigateToSettings = onNavigateToSettings,
            )
        }

        // 设置按钮（右下角）
        AnimatedVisibility(
            visible = isControlsVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = metrics.row)
                .padding(metrics.cozy),
            enter = fadeIn(HmpMotion.enter()),
            exit = fadeOut(HmpMotion.exit()),
        ) {
            IconButton(
                onClick = {
                    haptic.performLightClick()
                    isSettingsPanelVisible = !isSettingsPanelVisible
                    lastInteractionTime = nowEpochMillis()
                },
                modifier = Modifier.size(metrics.block),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_gallery_search_things),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = stringResource(Res.string.back),
                    modifier = Modifier.size(metrics.section),
                )
            }
        }
    }
}

/**
 * 歌词参数设置面板
 */
@Composable
private fun LyricsSettingsPanel(
    originalTextSize: Int,
    translatedTextSize: Int,
    currentTimeTextSize: Int,
    lineSpacing: Int,
    displayMode: DisplayMode,
    alignment: LyricsAlignment,
    karaokeEnabled: Boolean,
    onKaraokeEnabledChange: (Boolean) -> Unit,
    onOriginalTextSizeChange: (Int) -> Unit,
    onTranslatedTextSizeChange: (Int) -> Unit,
    onCurrentTimeTextSizeChange: (Int) -> Unit,
    onLineSpacingChange: (Int) -> Unit,
    onDisplayModeChange: (DisplayMode) -> Unit,
    onAlignmentChange: (LyricsAlignment) -> Unit,
    onNavigateToSettings: () -> Unit = {},
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val haptic = rememberHapticFeedback()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.section)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = metrics.base, horizontal = metrics.cozy),
            verticalArrangement = Arrangement.spacedBy(metrics.compact),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.compact),
            ) {
                VerticalSegmentedControl(
                    modifier = Modifier.weight(1f),
                    options = listOf(
                        SegmentedOption(id = "LANG1", label = stringResource(Res.string.original_text), icon = painterResource(Res.drawable.translate_c2e)),
                        SegmentedOption(id = "LANG2", label = stringResource(Res.string.translated_text), icon = painterResource(Res.drawable.translate_e2c)),
                        SegmentedOption(id = "DUAL", label = stringResource(Res.string.bilingual), icon = painterResource(Res.drawable.translate)),
                    ),
                    selectedOption = displayMode.name,
                    onOptionSelected = { name ->
                        haptic.performLightClick()
                        onDisplayModeChange(DisplayMode.valueOf(name))
                    },
                )
                VerticalSegmentedControl(
                    modifier = Modifier.weight(1f),
                    options = listOf(
                        SegmentedOption(id = "LEFT", label = stringResource(Res.string.align_left), icon = painterResource(Res.drawable.text_alignleft)),
                        SegmentedOption(id = "CENTER", label = stringResource(Res.string.align_center), icon = painterResource(Res.drawable.text_aligncenter)),
                        SegmentedOption(id = "RIGHT", label = stringResource(Res.string.align_right), icon = painterResource(Res.drawable.text_alignright)),
                    ),
                    selectedOption = alignment.name,
                    onOptionSelected = { name ->
                        haptic.performLightClick()
                        onAlignmentChange(LyricsAlignment.valueOf(name))
                    },
                )
                if (displayMode == DisplayMode.DUAL || displayMode == DisplayMode.LANG1) {
                    // 原文字体大小
                    SizeControl(
                        modifier = Modifier.weight(1f),
                        label = stringResource(Res.string.original_text),
                        value = originalTextSize,
                        minValue = 10,
                        maxValue = 28,
                        onValueChange = onOriginalTextSizeChange,
                    )
                }
                if (displayMode == DisplayMode.DUAL || displayMode == DisplayMode.LANG2) {
                    // 译文字体大小
                    SizeControl(
                        modifier = Modifier.weight(1f),
                        label = stringResource(Res.string.translated_text),
                        value = translatedTextSize,
                        minValue = 10,
                        maxValue = 28,
                        onValueChange = onTranslatedTextSizeChange,
                    )
                }
                // 当前行字体大小
                SizeControl(
                    modifier = Modifier.weight(1f),
                    label = stringResource(Res.string.highlight_line),
                    value = currentTimeTextSize,
                    minValue = 14,
                    maxValue = 32,
                    onValueChange = onCurrentTimeTextSizeChange,
                )
                // 行间距
                SizeControl(
                    modifier = Modifier.weight(1f),
                    label = stringResource(Res.string.line_spacing),
                    value = lineSpacing,
                    minValue = 2,
                    maxValue = 20,
                    onValueChange = onLineSpacingChange,
                )

                // 更多设置入口
                TextButton(
                    onClick = {
                        haptic.performLightClick()
                        onNavigateToSettings()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(Res.string.more_settings),
                        style = typo.footnote.medium(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 逐字显示开关
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(metrics.cozy),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = metrics.cozy, vertical = metrics.compact),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.karaoke_lyrics),
                        style = typo.body.medium(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = karaokeEnabled,
                        onCheckedChange = onKaraokeEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 数字调节块组件
 * 包含属性标题和调节块，后者包括当前值与 up/down 按钮
 */
@Composable
private fun SizeControl(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val haptic = rememberHapticFeedback()
    Column(
        modifier = modifier
            .border(width = metrics.stroke.thin, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(metrics.cozy)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.padding(metrics.compact),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(metrics.section) // 按钮整体尺寸
                    .clip(RoundedCornerShape(metrics.micro)) // 圆角设为尺寸的一半，形成正圆
                    .border(
                        width = metrics.stroke.thin,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(metrics.micro), // 边框圆角与外层保持一致
                    )
                    .clickable(
                        onClick = {
                            haptic.performLightClick()
                            if (value < maxValue) {
                                onValueChange(value + 1)
                            }
                        },
                        interactionSource = remember { MutableInteractionSource() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.plus),
                    contentDescription = stringResource(Res.string.increase),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(metrics.cozy), // 图标尺寸适配按钮
                )
            }
            Column(
                modifier = Modifier.padding(metrics.compact),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label.toCharArray().joinToString("\n"),
                    style = typo.body.medium(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(metrics.section) // 按钮整体尺寸
                    .clip(RoundedCornerShape(metrics.micro)) // 圆角设为尺寸的一半，形成正圆
                    .border(
                        width = metrics.stroke.thin,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(metrics.micro), // 边框圆角与外层保持一致
                    )
                    .clickable(
                        onClick = {
                            haptic.performLightClick()
                            if (value > minValue) {
                                onValueChange(value - 1)
                            }
                        },
                        interactionSource = remember { MutableInteractionSource() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.minus),
                    contentDescription = stringResource(Res.string.decrease),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(metrics.cozy), // 图标尺寸适配按钮
                )
            }
        }
    }
}
