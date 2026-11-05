package com.hearablemusic.player.ui.player.pages

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.hmp.domain.lyrics.CharTiming
import com.hmp.domain.lyrics.KaraokePositionInterpolator
import com.hmp.domain.lyrics.LyricsPresentationParams
import com.hmp.domain.lyrics.expandCharTimings
import com.hmp.domain.lyrics.findKaraokeCharFocus
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/**
 * 卡拉 OK 逐字歌词文本 v3（「静谧辉光」）：
 *
 * 逐字符渲染：每个字符是独立节点（[FlowRow] 自动换行），
 * - 每字符节点内两层：底层 [unsungColor] 全文，上层 [sungColor] 文本按该字符
 *   自己的时间片段从左到右裁剪（逐字点亮，位置由 [expandCharTimings] 与文本对齐）；
 * - 「焦点字符放大」直接作用在原字符节点上（graphicsLayer 缩放该节点，
 *   连同其填充层一起放大），不再叠加独立字符副本；唱到即平滑放大
 *   （[animateFloatAsState]，时长见 [LyricsPresentationParams.EMPHASIS_TRANSITION_MS]），
 *   放大后保持、不再缩小（seek 回退时随焦点回退缩小）；
 *   已唱字符与焦点字符同用高亮主题色 [sungColor]；
 * - 光晕：整行文字模糊副本 + 呼吸动画（[glowEnabled]）；
 * - 位置/焦点/光晕相位均为可变 state，仅在 graphicsLayer/drawWithContent 绘制阶段读取，
 *   字符切换（低频）才触发小范围重组，不逐帧重组。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KaraokeLyricText(
    text: String,
    currentPosition: Long,
    isPlaying: Boolean,
    charTimings: List<CharTiming>,
    lineStartMs: Long,
    lineEndMs: Long,
    sungColor: Color,
    unsungColor: Color,
    glowColor: Color,
    glowEnabled: Boolean,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // 播放位置（逐帧外推，绘制阶段读取）
    val positionMs = remember(charTimings, lineStartMs, lineEndMs) {
        mutableLongStateOf(currentPosition)
    }
    val focusIndex = remember(charTimings, lineStartMs, lineEndMs) {
        mutableIntStateOf(0)
    }
    // 光晕正弦相位（弧度，绘制阶段读取）
    val glowPhase = remember { mutableFloatStateOf(0f) }

    // 主循环：位置/字符焦点外推。每次采样 / 播放状态 / 行变化时重置外推基准。
    LaunchedEffect(charTimings, lineStartMs, lineEndMs, currentPosition, isPlaying) {
        var lastPosition = currentPosition
        val initialFocus = findKaraokeCharFocus(charTimings, lineStartMs, lineEndMs, lastPosition)
        positionMs.longValue = lastPosition
        focusIndex.intValue = initialFocus.index
        var lastNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                if (isPlaying) {
                    lastPosition = KaraokePositionInterpolator.interpolatePosition(
                        lastPositionMs = lastPosition,
                        lastTimestampNanos = lastNanos,
                        nowNanos = now,
                        isPlaying = true,
                    )
                }
                lastNanos = now
                positionMs.longValue = lastPosition
                val focus = findKaraokeCharFocus(charTimings, lineStartMs, lineEndMs, lastPosition)
                focusIndex.intValue = focus.index
            }
        }
    }

    // 光晕相位循环：与采样解耦，仅播放中推进、暂停冻结；
    // 不随 currentPosition 采样重启，避免呼吸动画每 100ms 归零。
    LaunchedEffect(isPlaying) {
        var lastNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val deltaNanos = (now - lastNanos).coerceAtLeast(0L)
                if (isPlaying) {
                    val deltaSec = deltaNanos / 1_000_000_000f
                    glowPhase.floatValue += deltaSec * (2f * PI.toFloat() / (LyricsPresentationParams.GLOW_PERIOD_MS / 1000f))
                }
                lastNanos = now
            }
        }
    }

    // 逐字符时间展开（与文本对齐；空白字符为 null）
    val expanded = remember(text, charTimings) {
        expandCharTimings(text, charTimings)
    }
    // 每个字符所属的片段索引（与 expandCharTimings 同规则；空白字符为 -1）
    val segmentIndexOfChar = remember(text, charTimings) {
        val arr = IntArray(text.length) { -1 }
        var seg = 0
        var remaining = 0
        for (i in text.indices) {
            if (text[i].isWhitespace()) continue
            if (seg >= charTimings.size) continue
            if (remaining == 0) {
                remaining = charTimings[seg].text.count { !it.isWhitespace() }.coerceAtLeast(1)
            }
            arr[i] = seg
            remaining--
            if (remaining == 0) seg++
        }
        arr
    }

    val arrangement = when (textAlign) {
        TextAlign.Start -> Arrangement.Start
        TextAlign.Center -> Arrangement.Center
        TextAlign.End -> Arrangement.End
        else -> Arrangement.Start
    }

    Box(modifier = modifier) {
        // 柔光副本（整行，呼吸动画）
        if (glowEnabled) {
            Text(
                text = text,
                color = glowColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .blur(LyricsPresentationParams.GLOW_BLUR_RADIUS_DP.dp)
                    .graphicsLayer {
                        alpha = LyricsPresentationParams.glowAlpha(
                            (sin(glowPhase.floatValue) * 0.5f + 0.5f),
                        )
                    },
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = arrangement,
            verticalArrangement = Arrangement.Center,
        ) {
            expanded.forEachIndexed { i, timing ->
                val char = text[i].toString()
                // 唱到即放大并保持：片段索引 <= 当前焦点即进入放大态
                val isSung = segmentIndexOfChar[i] in 0..focusIndex.intValue
                // 防拥挤边距：左右各预留放大余量（相对字号），放大后不挤压相邻字符
                val charMargin = with(density) {
                    (fontSize.toPx() * LyricsPresentationParams.EMPHASIS_MARGIN_FACTOR).toDp()
                }
                // 平滑放大：仅在绘制阶段读取动画值，避免逐帧重组
                val animatedScale = animateFloatAsState(
                    targetValue = if (isSung) LyricsPresentationParams.CHAR_EMPHASIS_SCALE else 1f,
                    animationSpec = tween(
                        durationMillis = LyricsPresentationParams.EMPHASIS_TRANSITION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                    label = "CharEmphasisScale",
                )
                // 用画布变换原位放大（不产生图层、绘制阶段读动画值），放大后保持
                Box(
                    modifier = Modifier
                        .padding(horizontal = charMargin)
                        .drawWithContent {
                            val s = animatedScale.value
                            if (s != 1f) {
                                scale(
                                    scaleX = s,
                                    scaleY = s,
                                    pivot = Offset(size.width / 2f, size.height / 2f),
                                ) {
                                    this@drawWithContent.drawContent()
                                }
                            } else {
                                this@drawWithContent.drawContent()
                            }
                        },
                ) {
                    Text(
                        text = char,
                        color = unsungColor,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                    )
                    if (timing != null) {
                        val start = timing.startMs
                        val end = timing.endMs.coerceAtLeast(start + 1)
                        Text(
                            text = char,
                            color = sungColor,
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            modifier = Modifier.drawWithContent {
                                val pos = positionMs.longValue
                                val t = when {
                                    pos <= start -> 0f
                                    pos >= end -> 1f
                                    else -> (pos - start).toFloat() / (end - start)
                                }
                                clipRect(right = size.width * t) {
                                    this@drawWithContent.drawContent()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
