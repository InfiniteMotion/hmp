package com.hearablemusic.player.ui.common.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.common.util.rememberPlatformHaptics
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.house
import com.hearablemusic.player.ui.generated.resources.house_fill
import com.hearablemusic.player.ui.generated.resources.library_settings
import com.hearablemusic.player.ui.generated.resources.list_bullet
import com.hearablemusic.player.ui.generated.resources.music_fill
import com.hearablemusic.player.ui.generated.resources.pause
import com.hearablemusic.player.ui.generated.resources.pause_desc
import com.hearablemusic.player.ui.generated.resources.person
import com.hearablemusic.player.ui.generated.resources.person_filled_viewfinder
import com.hearablemusic.player.ui.generated.resources.play_desc
import com.hearablemusic.player.ui.generated.resources.play_fill
import com.hearablemusic.player.ui.generated.resources.square_fill_grid_2x2
import com.hearablemusic.player.ui.generated.resources.square_grid_2x2
import com.hearablemusic.player.ui.generated.resources.tab_gallery
import com.hearablemusic.player.ui.generated.resources.tab_home
import com.hearablemusic.player.ui.generated.resources.tab_list
import com.hearablemusic.player.ui.generated.resources.tab_user
import com.hearablemusic.player.ui.library.pages.components.AlbumCover
import com.hearablemusic.player.ui.platform.HapticEffect
import com.hearablemusic.player.ui.platform.HapticService
import com.hmp.domain.music.MusicInfo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.pow

/** 底部融合栏状态 */
enum class FusionBarState {
    /** 默认：左侧导航展开（4 Tab），右侧播放折叠（仅圆形封面） */
    NavigationExpanded,
    /** 左侧导航折叠（单图标），右侧播放展开（封面 + 信息 + 控制） */
    PlaybackExpanded
}

private data class BottomTabItem(
    val label: StringResource,
    val selectedIcon: DrawableResource,
    val unselectedIcon: DrawableResource
)

/**
 * 底部导航 Tab（三胶囊底栏：门面页第 0 页无 Tab → 页 N 对应 tab N-1）。
 * 设计总纲 2.2：bottomTabs 去 Home，门面是伙伴的家（点按伙伴胶囊进入），不再是并列 Tab。
 */
private val bottomTabs = listOf(
    BottomTabItem(Res.string.tab_gallery, Res.drawable.square_fill_grid_2x2, Res.drawable.square_grid_2x2),
    BottomTabItem(Res.string.tab_list, Res.drawable.list_bullet, Res.drawable.list_bullet),
    BottomTabItem(Res.string.tab_user, Res.drawable.person_filled_viewfinder, Res.drawable.person)
)

/** 页索引 → Tab 索引：页 0（门面）无对应 Tab（-1 = 三 Tab 均不高亮）。internal 供单测（M1-T1）。 */
internal fun tabIndexForPage(page: Int): Int = page - 1

@Composable
fun BottomFusionBar(
    musicInfo: MusicInfo?,
    isPlaying: Boolean,
    progress: Float,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    showNavText: Boolean = true,
    showNavCapsule: Boolean = true,
    maxWidth: Dp? = null,
    onCompanionClick: () -> Unit = {},
    onCompanionLongPress: () -> Unit = {},
) {
    val haptic = rememberPlatformHaptics()
    // 初始态取首次组合值，后续由 LaunchedEffect 校正（review 2026-08-28：var→val，该值不参与赋值）
    val initialFusionState = if (showNavCapsule) FusionBarState.NavigationExpanded else FusionBarState.PlaybackExpanded
    var fusionState by remember { mutableStateOf(initialFusionState) }
    var timerKey by remember { mutableIntStateOf(0) }
    val hasMusic = musicInfo != null

    // 胶囊数量或播放状态变化时立即切换
    LaunchedEffect(showNavCapsule, hasMusic, isPlaying) {
        fusionState = if (!showNavCapsule || (hasMusic && isPlaying)) {
            FusionBarState.PlaybackExpanded
        } else {
            FusionBarState.NavigationExpanded
        }
        if (showNavCapsule && hasMusic) timerKey++
    }

    // 切换 Tab 页面时展开导航胶囊（仅双胶囊模式）
    LaunchedEffect(selectedTabIndex) {
        if (showNavCapsule) {
            fusionState = FusionBarState.NavigationExpanded
        }
    }

    // 用户手动交互后 5 秒无操作回到默认态（仅双胶囊模式）
    LaunchedEffect(fusionState, timerKey) {
        if (!showNavCapsule) return@LaunchedEffect
        delay(5_000)
        fusionState = if (hasMusic && isPlaying) {
            FusionBarState.PlaybackExpanded
        } else {
            FusionBarState.NavigationExpanded
        }
    }

    val resetTimer: () -> Unit = { timerKey++ }

    val transitionSpec: AnimatedContentTransitionScope<FusionBarState>.() -> ContentTransform = {
        (
            fadeIn(animationSpec = tween(200)) +
            scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f))
        ).togetherWith(
            fadeOut(animationSpec = tween(150)) +
            scaleOut(targetScale = 0.92f, animationSpec = tween(150))
        ).using(SizeTransform(clip = false))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
            .padding(horizontal = 12.dp) // 宽度压缩（设计总纲 2.2.1：外边距 16→12dp）
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), // 压缩：间距 12→8dp
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── 伙伴胶囊（常驻锚点：点按=门面 长按=浮层）──
        CompanionCapsule(
            selected = tabIndexForPage(selectedTabIndex) < 0, // 门面页（第 0 页）时图标高亮
            onClick = {
                haptic.perform(HapticEffect.TICK)
                resetTimer()
                onCompanionClick()
            },
            onLongPress = {
                haptic.perform(HapticEffect.LONG_PRESS)
                resetTimer()
                onCompanionLongPress()
            },
            hazeState = hazeState,
            // 全局恒定为 48dp 体系，无动态缩放（用户决策 2026-08-27）
        )

        // ── 左侧胶囊：导航 ──
        val capsuleShape = RoundedCornerShape(36.dp)
        Card(
            shape = capsuleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (hazeState != null) {
                    MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .clip(capsuleShape)
                .then(
                    // 折叠态：点击整个胶囊回到展开态
                    if (fusionState == FusionBarState.PlaybackExpanded) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.perform(HapticEffect.TICK)
                            fusionState = FusionBarState.NavigationExpanded
                            resetTimer()
                        }
                    } else Modifier
                )
                .then(
                    if (hazeState != null) Modifier.hazeEffect(
                        state = hazeState,
                        style = hazeStyleForIntensity()
                    ) else Modifier
                )
        ) {
            AnimatedVisibility(
                visible = showNavCapsule,
                enter = expandHorizontally(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkHorizontally(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
            ) {
            AnimatedContent(
                targetState = fusionState,
                transitionSpec = transitionSpec,
                label = "NavCapsule"
            ) { state ->
                when (state) {
                    FusionBarState.NavigationExpanded ->
                        NavigationExpandedContent(tabIndexForPage(selectedTabIndex), onTabSelected, haptic, showNavText)
                    FusionBarState.PlaybackExpanded ->
                        NavigationCollapsedContent(
                            tabIndex = tabIndexForPage(selectedTabIndex)
                        )
                }
            }
            }
        }

        // ── 右侧胶囊：播放控制（仅在有音乐时显示）──
        if (hasMusic) {
            Card(
                shape = capsuleShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (hazeState != null) {
                        MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                border = BorderStroke(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .clip(capsuleShape)
                    .then(
                        // 折叠态：点击整个胶囊展开
                        if (fusionState == FusionBarState.NavigationExpanded) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.perform(HapticEffect.TICK)
                                fusionState = FusionBarState.PlaybackExpanded
                                resetTimer()
                            }
                        } else Modifier
                    )
                    .then(
                        if (hazeState != null) Modifier.hazeEffect(
                            state = hazeState,
                            style = hazeStyleForIntensity()
                        ) else Modifier
                    )
            ) {
                AnimatedContent(
                    targetState = fusionState,
                    transitionSpec = transitionSpec,
                    label = "PlaybackCapsule"
                ) { state ->
                    when (state) {
                        FusionBarState.NavigationExpanded ->
                            PlaybackCollapsedContent(
                                musicInfo = musicInfo,
                                isPlaying = isPlaying
                            )
                        FusionBarState.PlaybackExpanded ->
                            PlaybackExpandedContent(
                                musicInfo = musicInfo,
                                isPlaying = isPlaying,
                                onPlayPause = {
                                    resetTimer()
                                    onPlayPause()
                                },
                                onPrev = {
                                    resetTimer()
                                    onPrev()
                                },
                                onNext = {
                                    resetTimer()
                                    onNext()
                                },
                                onOpenPlayer = {
                                    resetTimer()
                                    onOpenPlayer()
                                },
                                onResetTimer = { resetTimer() },
                                haptic = haptic
                            )
                    }
                }
            }
        }
    }
}

// ── 导航展开内容 ──

@Composable
private fun NavigationExpandedContent(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    haptic: HapticService,
    showNavText: Boolean = true
) {
    Row(
        modifier = Modifier
            .padding(8.dp) // 与其他胶囊一致的围绕 padding：总高 8+48+8=64dp，与其他胶囊严格等高
            .height(48.dp), // 内容区恒定 48dp，图标 24dp 垂直居中（上下各 12dp 空隙）
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bottomTabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.perform(HapticEffect.TICK)
                        onTabSelected(index)
                    }
                    .fillMaxHeight() // 点击区占满 48dp 行高（原 vertical padding 由行高接管）
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(if (isSelected) tab.selectedIcon else tab.unselectedIcon),
                    contentDescription = stringResource(tab.label),
                    tint = contentColor,
                    modifier = Modifier.size(24.dp) // 宽度压缩：图标 28→24dp
                )
                if (showNavText) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(tab.label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = contentColor
                    )
                }
            }
        }
    }
}

// ── 导航折叠内容 ──

@Composable
private fun NavigationCollapsedContent(tabIndex: Int) {
    // 门面页（tabIndex < 0，第 0 页无对应 Tab）：显示「音乐库」图标且不高亮（常态 onSurface）；
    // 其余页：显示当前 Tab 选中图标并高亮（primary）
    val isFacePage = tabIndex < 0
    val iconRes: DrawableResource = if (isFacePage) {
        Res.drawable.music_fill
    } else {
        bottomTabs[tabIndex].selectedIcon
    }
    val iconDesc: StringResource = if (isFacePage) {
        Res.string.library_settings
    } else {
        bottomTabs[tabIndex].label
    }
    val tint = if (isFacePage) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(48.dp), // 全局 48dp 体系：折叠图标区恒定
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(iconDesc),
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ── 播放折叠内容（仅圆形封面）──

@Composable
private fun PlaybackCollapsedContent(
    musicInfo: MusicInfo,
    isPlaying: Boolean
) {
    val coverRotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            val current = coverRotation.value % 360f
            val remaining = 360f - current
            val durationMillis = ((remaining / 360f) * 8000f).toInt().coerceAtLeast(1)
            coverRotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing)
            )
            coverRotation.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .padding(8.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AlbumCover(
            uri = musicInfo.music.albumArtUri,
            size = 48.dp, // 全局恒定 48dp 体系：播放折叠封面与其余胶囊一致
            corner = 24.dp,
            shadow = 3.dp,
            modifier = Modifier.graphicsLayer {
                rotationZ = coverRotation.value
            }
        )
    }
}

// ── 播放展开内容 ──

@Composable
private fun PlaybackExpandedContent(
    musicInfo: MusicInfo,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    onResetTimer: () -> Unit,
    haptic: HapticService
) {
    val coverRotation = remember { Animatable(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var capsuleWidth by remember { mutableIntStateOf(0) }
    val animatedOffset by animateFloatAsState(dragOffset, animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f))
    val thresholdPx = (capsuleWidth * 0.25f).coerceAtLeast(1f)
    val thresholdReached = kotlin.math.abs(animatedOffset) >= thresholdPx
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            val current = coverRotation.value % 360f
            val remaining = 360f - current
            val durationMillis = ((remaining / 360f) * 8000f).toInt().coerceAtLeast(1)
            coverRotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing)
            )
            coverRotation.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .onSizeChanged { capsuleWidth = it.width }
            .clip(RoundedCornerShape(36.dp))
            .pointerInput(thresholdPx) {
                var wasPastThreshold = false
                detectHorizontalDragGestures(
                    onDragStart = { onResetTimer() },
                    onDragEnd = {
                        if (dragOffset >= thresholdPx) {
                            onPrev()
                        } else if (dragOffset <= -thresholdPx) {
                            onNext()
                        }
                        onResetTimer()
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        onResetTimer()
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffset += dragAmount
                        onResetTimer()
                        val pastThreshold = kotlin.math.abs(dragOffset) >= thresholdPx
                        if (pastThreshold && !wasPastThreshold) {
                            haptic.perform(HapticEffect.TICK)
                        }
                        wasPastThreshold = pastThreshold
                    }
                )
            }
    ) {
        // 左滑指示渐变（右边缘露出）
        if (animatedOffset < -10f) {
            val linearIntensity = (kotlin.math.abs(animatedOffset) / thresholdPx).coerceIn(0f, 1f)
            val visualIntensity = linearIntensity.pow(0.4f)
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(visualIntensity)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                1f to primaryColor.copy(alpha = linearIntensity * 0.4f)
                            )
                        )
                )
            }
        }

        // 右滑指示渐变（左边缘露出）
        if (animatedOffset > 10f) {
            val linearIntensity = (animatedOffset / thresholdPx).coerceIn(0f, 1f)
            val visualIntensity = linearIntensity.pow(0.4f)
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(visualIntensity)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                0f to primaryColor.copy(alpha = linearIntensity * 0.4f),
                                1f to Color.Transparent
                            )
                        )
                )
            }
        }

        // 主内容层
        Row(
            modifier = Modifier
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面 + 播放暂停叠加
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.perform(HapticEffect.TICK)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                AlbumCover(
                    uri = musicInfo.music.albumArtUri,
                    size = 48.dp, // 播放展开（三胶囊共存）紧凑体系：封面统一 48dp，与伙伴/导航协调
                    corner = 24.dp,
                    shadow = 4.dp,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = coverRotation.value
                    }
                )
                Icon(
                    painter = painterResource(
                        if (isPlaying) Res.drawable.pause else Res.drawable.play_fill
                    ),
                    contentDescription = if (isPlaying) stringResource(Res.string.pause_desc) else stringResource(Res.string.play_desc),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp) // 宽度压缩：控制图标 28→24dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 歌名 + 歌手（点击进入播放器）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 12.dp) // 宽度压缩：歌名列左右留白 16→12dp
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.perform(HapticEffect.TICK)
                        onOpenPlayer()
                    }
            ) {
                Text(
                    text = musicInfo.music.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = musicInfo.music.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
