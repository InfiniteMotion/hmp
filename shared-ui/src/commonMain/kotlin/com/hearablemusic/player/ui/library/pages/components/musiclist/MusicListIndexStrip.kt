package com.hearablemusic.player.ui.library.pages.components.musiclist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmp.domain.music.MusicInfo
import com.hearablemusic.player.ui.common.util.rememberPlatformHaptics
import com.hearablemusic.player.ui.platform.HapticEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 索引条上下留白 */
private val IndexStripVerticalPadding = 72.dp

/**
 * 索引条：全高度，上下各 [IndexStripVerticalPadding]。支持 Letter 模式（A–Z #）与 Anchor 模式（数值/时间锚点）。
 * 支持点击与滑动：点击或滑动到某字母/锚点时列表滚动至对应项，当前选中项随滑动高亮。
 */
@Composable
internal fun MusicListIndexStrip(
    musicInfoList: List<MusicInfo>,
    listState: LazyListState,
    config: IndexJumpConfig,
    currentPlayingIndex: Int?,
    modifier: Modifier = Modifier,
    columns: Int = 1,
) {
    val scope = rememberCoroutineScope()
    var firstVisibleIndex by remember { mutableStateOf(listState.firstVisibleItemIndex) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisibleIndex = it }
    }
    val flatFirstVisible = firstVisibleIndex * columns
    var stripSize by remember { mutableStateOf(IntSize.Zero) }
    var lastScrollJob by remember { mutableStateOf<Job?>(null) }
    val haptic = rememberPlatformHaptics()
    val density = LocalDensity.current
    val verticalPaddingPx = with(density) { IndexStripVerticalPadding.roundToPx() }

    fun scrollToIndexAnimated(idx: Int) {
        lastScrollJob?.cancel()
        lastScrollJob = scope.launch {
            listState.animateScrollToItem(idx / columns)
            lastScrollJob = null
        }
    }

    if (config.isAnchorMode) {
        // 用「列表中每项的 id 顺序」作为 key，确保升降序切换时锚点会重新计算
        val listOrderKey = musicInfoList.map { it.music.id }
        val (rawLabels, rawMap) = remember(listOrderKey) { config.smartAnchor!!(musicInfoList) }
        // 降序时反转锚点条：从上到下与列表首项（大/长）对应
        val (anchorLabels, anchorToIndexMap) = if (config.orderType.isDescending && rawLabels.isNotEmpty()) {
            val rev = rawLabels.reversed()
            val n = rawLabels.size
            val remap = (0 until n).associate { i -> i to (rawMap[n - 1 - i] ?: 0) }
            Pair(rev, remap)
        } else {
            Pair(rawLabels, rawMap)
        }
        val currentAnchorIndex = remember(anchorToIndexMap, flatFirstVisible, anchorLabels) {
            anchorLabels.indices
                .mapNotNull { i -> anchorToIndexMap[i]?.let { i to it } }
                .filter { it.second <= flatFirstVisible }
                .maxByOrNull { it.second }
                ?.first
        }
        var dragAnchorIndex by remember { mutableStateOf<Int?>(null) }
        val highlightAnchorIndex = dragAnchorIndex ?: currentAnchorIndex

        Box(
            modifier = modifier
                .width(24.dp)
                .fillMaxHeight()
                .onSizeChanged { stripSize = it }
                .pointerInput(anchorLabels, anchorToIndexMap, stripSize, verticalPaddingPx) {
                    if (stripSize.height <= 0 || anchorLabels.isEmpty()) return@pointerInput
                    val contentTop = verticalPaddingPx.toFloat()
                    val contentHeight = (stripSize.height - 2 * verticalPaddingPx).toFloat().coerceAtLeast(1f)
                    fun anchorIndexForY(y: Float): Int {
                        val t = ((y - contentTop) / contentHeight * anchorLabels.size).toInt()
                        return t.coerceIn(0, anchorLabels.lastIndex)
                    }
                    detectDragGestures(
                        onDragStart = { offset ->
                            val i = anchorIndexForY(offset.y)
                            dragAnchorIndex = i
                            anchorToIndexMap[i]?.let { idx ->
                                haptic.perform(HapticEffect.TICK)
                                scrollToIndexAnimated(idx)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val i = anchorIndexForY(change.position.y)
                            if (i != dragAnchorIndex) {
                                dragAnchorIndex = i
                                anchorToIndexMap[i]?.let { idx ->
                                    haptic.perform(HapticEffect.TICK)
                                    scrollToIndexAnimated(idx)
                                }
                            }
                        },
                        onDragEnd = { dragAnchorIndex = null },
                        onDragCancel = { dragAnchorIndex = null },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = IndexStripVerticalPadding)
                    .width(24.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                anchorLabels.forEachIndexed { anchorIndex, label ->
                    val listIndex = anchorToIndexMap[anchorIndex]
                    val isCurrent = anchorIndex == highlightAnchorIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(20.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                enabled = listIndex != null,
                                onClick = {
                                    listIndex?.let {
                                        haptic.perform(HapticEffect.TICK)
                                        scrollToIndexAnimated(it)
                                    }
                                },
                            )
                            .semantics {
                                contentDescription = "Jump to $label"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        CircleShape,
                                    ),
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            },
                            maxLines = 1,
                            modifier = Modifier.clip(CircleShape),
                        )
                    }
                }
            }
            // 底部留白可点，保证最后一项可选中
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(IndexStripVerticalPadding)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        enabled = anchorLabels.isNotEmpty(),
                        onClick = {
                            if (anchorLabels.isNotEmpty()) {
                                anchorToIndexMap[anchorLabels.lastIndex]?.let {
                                    haptic.perform(HapticEffect.TICK)
                                    scrollToIndexAnimated(it)
                                }
                            }
                        },
                    ),
            )
        }
    } else {
        val letterToIndexMap = remember(musicInfoList) { config.letterToIndex(musicInfoList) }
        val letters = config.letters
        val currentLetter = remember(letterToIndexMap, flatFirstVisible, letters) {
            letters
                .mapNotNull { letter -> letterToIndexMap[letter]?.let { letter to it } }
                .filter { it.second <= flatFirstVisible }
                .maxByOrNull { it.second }
                ?.first
        }
        var dragLetter by remember { mutableStateOf<Char?>(null) }
        val highlightLetter = dragLetter ?: currentLetter

        Box(
            modifier = modifier
                .width(24.dp)
                .fillMaxHeight()
                .onSizeChanged { stripSize = it }
                .pointerInput(letters, letterToIndexMap, stripSize, verticalPaddingPx) {
                    if (stripSize.height <= 0 || letters.isEmpty()) return@pointerInput
                    val contentTop = verticalPaddingPx.toFloat()
                    val contentHeight = (stripSize.height - 2 * verticalPaddingPx).toFloat().coerceAtLeast(1f)
                    fun letterIndexForY(y: Float): Int {
                        val t = ((y - contentTop) / contentHeight * letters.size).toInt()
                        return t.coerceIn(0, letters.lastIndex)
                    }
                    detectDragGestures(
                        onDragStart = { offset ->
                            val letter = letters[letterIndexForY(offset.y)]
                            dragLetter = letter
                            letterToIndexMap[letter]?.let { idx ->
                                haptic.perform(HapticEffect.TICK)
                                scrollToIndexAnimated(idx)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val letter = letters[letterIndexForY(change.position.y)]
                            if (letter != dragLetter) {
                                dragLetter = letter
                                letterToIndexMap[letter]?.let { idx ->
                                    haptic.perform(HapticEffect.TICK)
                                    scrollToIndexAnimated(idx)
                                }
                            }
                        },
                        onDragEnd = { dragLetter = null },
                        onDragCancel = { dragLetter = null },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = IndexStripVerticalPadding)
                    .width(24.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                letters.forEach { letter ->
                    val index = letterToIndexMap[letter]
                    val isCurrent = letter == highlightLetter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(20.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                enabled = index != null,
                                onClick = {
                                    index?.let {
                                        haptic.perform(HapticEffect.TICK)
                                        scrollToIndexAnimated(it)
                                    }
                                },
                            )
                            .semantics {
                                contentDescription = "Jump to $letter"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        CircleShape,
                                    ),
                            )
                        }
                        Text(
                            text = letter.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.clip(CircleShape),
                        )
                    }
                }
            }
            // 底部留白可点，保证最后一项可选中
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(IndexStripVerticalPadding)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        enabled = letters.isNotEmpty(),
                        onClick = {
                            letters.lastOrNull()?.let { letter ->
                                letterToIndexMap[letter]?.let {
                                    haptic.perform(HapticEffect.TICK)
                                    scrollToIndexAnimated(it)
                                }
                            }
                        },
                    ),
            )
        }
    }
}
