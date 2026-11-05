package com.hearablemusic.player.ui.library.pages.components.musiclist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.util.rememberPlatformHaptics
import com.hearablemusic.player.ui.platform.HapticEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 垂直滚动条：轨道 + 拇指，与 [listState] 联动；支持拖动拇指滚动列表。
 */
@Composable
internal fun MusicListScrollbar(
    listState: LazyListState,
    config: ScrollbarConfig,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalHmpMetrics.current
    val density = LocalDensity.current
    val widthPx = with(density) { config.width.toPx() }
    val thumbMinPx = with(density) { config.thumbMinHeight.toPx() }
    val cornerRadiusPx = with(density) { config.cornerRadius.toPx() }
    val trackColor = config.trackColor ?: Color.Black.copy(alpha = 0.1f)
    val thumbColor = config.thumbColor ?: Color.Black.copy(alpha = 0.4f)
    val scope = rememberCoroutineScope()
    val haptic = rememberPlatformHaptics()
    var lastScrollJob by remember { mutableStateOf<Job?>(null) }

    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount.coerceAtLeast(1)
    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    val visibleCount = layoutInfo.visibleItemsInfo.size
    val thumbTopRatio = firstVisible.toFloat() / totalItems
    val thumbHeightRatio = (visibleCount.toFloat() / totalItems).coerceIn(0.1f, 1f)

    Box(
        modifier = modifier
            .width(config.width + metrics.compact)
            .fillMaxHeight()
            .pointerInput(listState) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val trackHeight = size.height
                    if (trackHeight <= 0f) return@detectDragGestures
                    val total = listState.layoutInfo.totalItemsCount.coerceAtLeast(1)
                    val currentFirst = listState.firstVisibleItemIndex
                    val moveRatio = dragAmount.y / trackHeight
                    val deltaIndex = (moveRatio * total).toInt()
                    val targetIndex = (currentFirst + deltaIndex).coerceIn(0, total - 1)
                    if (targetIndex != currentFirst) {
                        haptic.perform(HapticEffect.TICK)
                        lastScrollJob?.cancel()
                        lastScrollJob = scope.launch {
                            listState.animateScrollToItem(targetIndex)
                            lastScrollJob = null
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Canvas(
            modifier = Modifier
                .width(config.width)
                .fillMaxHeight(),
        ) {
            val h = size.height
            val w = size.width
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(cornerRadiusPx),
            )
            val thumbH = (h * thumbHeightRatio).coerceAtLeast(thumbMinPx)
            val thumbY = (h - thumbH) * thumbTopRatio.coerceIn(0f, 1f)
            val thumbCornerPx = minOf(w, thumbH) / 2f
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(0f, thumbY),
                size = Size(w, thumbH),
                cornerRadius = CornerRadius(thumbCornerPx),
            )
        }
    }
}
