package com.hearablemusic.player.ui.player.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.viewmodel.PaletteColors
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.ic_gallery_search_things
import com.hearablemusic.player.ui.generated.resources.no_lyrics
import com.hearablemusic.player.ui.player.viewmodel.LyricsPresentation
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.lyrics.CharTiming
import com.hmp.domain.lyrics.LyricLineData
import com.hmp.domain.lyrics.LyricsPresentationParams
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * 高级歌词组件 - 「静谧辉光」旗舰默认呈现
 *
 * 在逐字卡拉 OK 基础上优化：
 * - 背景透明：组件不绘制任何背景层，只渲染歌词文字本身；
 * - 距离层次：当前行与上下行（共三行）保持清晰，聚焦窗口外（distance >= 2）
 *   施加模糊，更远行透明度衰减；行与行之间不再做行级缩放，
 *   当前行与非当前行的尺寸差异仅来自「强调行字号」设置；
 * - 焦点字符放大（唱到即放大并保持，见 [KaraokeLyricText]）；
 * - 当前行光晕呼吸、当前字符放大/轻浮动（见 [KaraokeLyricText]）；
 * - 配色优先封面取色（[PaletteColors]），无封面时回退主题主色。
 */
@Composable
fun AdvancedLyrics(
    modifier: Modifier = Modifier,
    lyricsPresentation: LyricsPresentation?,
    currentPosition: Long,
    onSeek: (Long) -> Unit = {},
    originalTextSize: Int = 14,
    translatedTextSize: Int = 14,
    currentTimeTextSize: Int = 16,
    lineSpacing: Int = 6,
    karaokeEnabled: Boolean = true,
    isPlaying: Boolean = true,
    paletteColors: PaletteColors? = null,
    displayMode: DisplayMode = DisplayMode.DUAL,
    alignment: LyricsAlignment = LyricsAlignment.CENTER,
) {
    if (lyricsPresentation == null) {
        EmptyLyricsView(modifier)
        return
    }

    val timedLyrics = lyricsPresentation.lines
    val scrollState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    var containerHeightPx by remember { mutableIntStateOf(0) }

    val lazyColumnHorizontalAlignment = when (alignment) {
        LyricsAlignment.LEFT -> Alignment.Start
        LyricsAlignment.CENTER -> Alignment.CenterHorizontally
        LyricsAlignment.RIGHT -> Alignment.End
    }

    // 当前行由 VM 按播放位置算好（二分），渲染端不再调用 domain。
    val currentIndex = lyricsPresentation.currentIndex

    val lyricPalette = resolveLyricPalette(paletteColors)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height },
    ) {
        // 自动滚动到当前行，居中显示（contentPadding 已提供上下对称空间）
        LaunchedEffect(currentIndex, containerHeightPx) {
            if (currentIndex >= 0 && currentIndex < timedLyrics.size && containerHeightPx > 0) {
                scrollState.animateScrollToItem(
                    index = currentIndex,
                    scrollOffset = 0,
                )
            }
        }

        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = lazyColumnHorizontalAlignment,
            contentPadding = PaddingValues(vertical = with(density) { (containerHeightPx / 2).let { if (it > 0) it.toDp() else LayoutConstraints.PanelHeight } }),
        ) {
            itemsIndexed(
                items = timedLyrics,
                key = { index, item -> "${item.timestamp}_$index" },
            ) { index, lyricLine ->
                val isCurrent = index == currentIndex && currentIndex >= 0
                val lineEndMs = lyricsPresentation.lineEnds.getOrElse(index) { lyricLine.timestamp }
                AdvancedLyricItem(
                    lyricLine = lyricLine,
                    isCurrent = isCurrent,
                    distance = abs(index - currentIndex),
                    lyricPalette = lyricPalette,
                    karaokeEnabled = karaokeEnabled,
                    charTimings = lyricLine.charTimings,
                    translatedCharTimings = lyricLine.translatedCharTimings,
                    lineStartMs = lyricLine.timestamp,
                    lineEndMs = lineEndMs,
                    currentPosition = currentPosition,
                    isPlaying = isPlaying,
                    displayMode = displayMode,
                    originalTextSize = originalTextSize,
                    translatedTextSize = translatedTextSize,
                    currentTimeTextSize = currentTimeTextSize,
                    lineSpacing = lineSpacing,
                    alignment = alignment,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSeek(lyricLine.timestamp)
                    },
                )
            }
        }
    }
}

/** 当前行逐字配色（已唱/未唱/光晕） */
private data class LyricPalette(
    val sung: Color,
    val unsung: Color,
    val glow: Color,
)

/**
 * 解析「静谧辉光」配色：已唱高亮统一使用 [MaterialTheme.colorScheme.primary]
 * （与进度条同色，动态主题下已含封面取色）；光晕使用封面主色，无封面时回退主题主色。
 */
@Composable
private fun resolveLyricPalette(paletteColors: PaletteColors?): LyricPalette {
    val scheme = MaterialTheme.colorScheme
    val pal = paletteColors?.takeIf { it != PaletteColors() }
    return LyricPalette(
        sung = scheme.primary,
        unsung = scheme.onSurface.copy(alpha = 0.45f),
        glow = pal?.primary ?: scheme.primary,
    )
}

/**
 * 高级歌词项 - 「静谧辉光」层次渲染
 */
@Composable
private fun AdvancedLyricItem(
    lyricLine: LyricLineData,
    isCurrent: Boolean,
    distance: Int,
    lyricPalette: LyricPalette,
    karaokeEnabled: Boolean = true,
    charTimings: List<CharTiming> = emptyList(),
    translatedCharTimings: List<CharTiming> = emptyList(),
    lineStartMs: Long = 0L,
    lineEndMs: Long = 0L,
    currentPosition: Long = 0L,
    isPlaying: Boolean = true,
    displayMode: DisplayMode,
    originalTextSize: Int = 14,
    translatedTextSize: Int = 14,
    currentTimeTextSize: Int = 16,
    lineSpacing: Int = 6,
    alignment: LyricsAlignment = LyricsAlignment.CENTER,
    onClick: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    // 距离层次：行距透明度曲线驱动 alpha；聚焦窗口外（distance >= 2）模糊
    val targetAlpha = LyricsPresentationParams.distanceAlpha(distance)
    val blurRadius = LyricsPresentationParams.blurRadiusDp(distance)

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = LyricsPresentationParams.LINE_TRANSITION_MS),
        label = "LyricAlpha",
    )

    val translated = lyricLine.translatedText
    val lineSpacing = ((if (translated != null) 2 else 1) * lineSpacing).dp

    val textAlign = when (alignment) {
        LyricsAlignment.LEFT -> TextAlign.Start
        LyricsAlignment.CENTER -> TextAlign.Center
        LyricsAlignment.RIGHT -> TextAlign.End
    }

    val horizontalAlignment = when (alignment) {
        LyricsAlignment.LEFT -> Alignment.Start
        LyricsAlignment.CENTER -> Alignment.CenterHorizontally
        LyricsAlignment.RIGHT -> Alignment.End
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = metrics.block, vertical = lineSpacing)
            .then(
                if (!isCurrent && blurRadius > 0f) {
                    Modifier.blur(blurRadius.dp)
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                alpha = animatedAlpha
            }
            .clip(RoundedCornerShape(metrics.compact))
            .clickable { onClick() },
        color = Transparent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = metrics.cozy),
            horizontalAlignment = horizontalAlignment,
        ) {
            when (displayMode) {
                DisplayMode.LANG1 -> {
                    LyricText(
                        text = lyricLine.originalText,
                        isCurrent = isCurrent,
                        lyricPalette = lyricPalette,
                        karaokeEnabled = karaokeEnabled,
                        charTimings = charTimings,
                        lineStartMs = lineStartMs,
                        lineEndMs = lineEndMs,
                        currentPosition = currentPosition,
                        isPlaying = isPlaying,
                        textAlign = textAlign,
                        fontSize = originalTextSize.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                DisplayMode.LANG2 -> {
                    if (translated != null) {
                        LyricText(
                            text = translated,
                            isCurrent = isCurrent,
                            lyricPalette = lyricPalette,
                            karaokeEnabled = karaokeEnabled,
                            charTimings = translatedCharTimings,
                            lineStartMs = lineStartMs,
                            lineEndMs = lineEndMs,
                            currentPosition = currentPosition,
                            isPlaying = isPlaying,
                            textAlign = textAlign,
                            fontSize = translatedTextSize.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                    } else {
                        LyricText(
                            text = lyricLine.originalText,
                            isCurrent = isCurrent,
                            lyricPalette = lyricPalette,
                            karaokeEnabled = karaokeEnabled,
                            charTimings = charTimings,
                            lineStartMs = lineStartMs,
                            lineEndMs = lineEndMs,
                            currentPosition = currentPosition,
                            isPlaying = isPlaying,
                            textAlign = textAlign,
                            fontSize = originalTextSize.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                DisplayMode.DUAL -> {
                    if (translated == null) {
                        LyricText(
                            text = lyricLine.originalText,
                            isCurrent = isCurrent,
                            lyricPalette = lyricPalette,
                            karaokeEnabled = karaokeEnabled,
                            charTimings = charTimings,
                            lineStartMs = lineStartMs,
                            lineEndMs = lineEndMs,
                            currentPosition = currentPosition,
                            isPlaying = isPlaying,
                            textAlign = textAlign,
                            fontSize = if (isCurrent) currentTimeTextSize.sp else originalTextSize.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                    } else {
                        LyricText(
                            text = lyricLine.originalText,
                            isCurrent = isCurrent,
                            lyricPalette = lyricPalette,
                            karaokeEnabled = karaokeEnabled,
                            charTimings = charTimings,
                            lineStartMs = lineStartMs,
                            lineEndMs = lineEndMs,
                            currentPosition = currentPosition,
                            isPlaying = isPlaying,
                            textAlign = textAlign,
                            fontSize = if (isCurrent) currentTimeTextSize.sp else originalTextSize.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )

                        Spacer(modifier = Modifier.height(metrics.micro))
                        LyricText(
                            text = translated,
                            isCurrent = isCurrent,
                            lyricPalette = lyricPalette,
                            karaokeEnabled = karaokeEnabled,
                            charTimings = translatedCharTimings,
                            lineStartMs = lineStartMs,
                            lineEndMs = lineEndMs,
                            currentPosition = currentPosition,
                            isPlaying = isPlaying,
                            textAlign = textAlign,
                            fontSize = translatedTextSize.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 歌词文本：当前行且开启逐字时使用卡拉 OK 渐变（含光晕/字符放大），
 * 否则保持整行样式；当前行始终带柔光晕。
 */
@Composable
private fun LyricText(
    text: String,
    isCurrent: Boolean,
    lyricPalette: LyricPalette,
    karaokeEnabled: Boolean,
    charTimings: List<CharTiming>,
    lineStartMs: Long,
    lineEndMs: Long,
    currentPosition: Long,
    isPlaying: Boolean,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    if (isCurrent && karaokeEnabled && text.isNotBlank()) {
        KaraokeLyricText(
            text = text,
            currentPosition = currentPosition,
            isPlaying = isPlaying,
            charTimings = charTimings,
            lineStartMs = lineStartMs,
            lineEndMs = lineEndMs,
            sungColor = lyricPalette.sung,
            unsungColor = lyricPalette.unsung,
            glowColor = lyricPalette.glow,
            glowEnabled = true,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            modifier = modifier,
        )
    } else {
        val baseColor = if (isCurrent) lyricPalette.sung else MaterialTheme.colorScheme.onSurface
        Box(modifier = modifier) {
            if (isCurrent) {
                Text(
                    text = text,
                    color = lyricPalette.glow,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    textAlign = textAlign,
                    modifier = Modifier
                        .blur(LyricsPresentationParams.GLOW_BLUR_RADIUS_DP.dp)
                        .graphicsLayer {
                            alpha = LyricsPresentationParams.GLOW_ALPHA_MAX * 0.8f
                        },
                )
            }
            Text(
                text = text,
                color = baseColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = textAlign,
            )
        }
    }
}

/**
 * 空歌词视图
 */
@Composable
private fun EmptyLyricsView(modifier: Modifier = Modifier) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(metrics.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_gallery_search_things),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = metrics.cozy),
        )

        Text(
            text = stringResource(Res.string.no_lyrics),
            style = typo.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}
