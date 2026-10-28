package com.hearablemusic.player.ui.library.pages.components.musiclist

import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.*
import com.hearablemusic.player.ui.common.design.animation.AnimationTokens
import com.hearablemusic.player.ui.common.util.rememberPlatformHaptics
import com.hearablemusic.player.ui.platform.HapticEffect

/** 头部/编辑栏统一高度与 padding，保证切换时无高度变化 */
private val HeaderRowPadding = 12.dp
private val HeaderRowVerticalPadding = 12.dp
private val HeaderRowBottomPadding = 4.dp
/** 头部行内容高度（与 IconButton 一致），总高度 = VerticalPadding + ContentHeight + BottomPadding */
private val HeaderRowContentHeight = 32.dp
private val HeaderBarTotalHeight = HeaderRowVerticalPadding + HeaderRowContentHeight + HeaderRowBottomPadding
/** 数字 label 固定宽度，避免数量变化时布局偏移 */
private val CountLabelWidth = 32.dp

/**
 * 根据 [HeaderConfig] 渲染头部：None / Simple（顺序+随机）/ Full（排序筛选+播放）/ Custom。
 * 当 [showEditButton] 为 true 时显示编辑按钮；[listCount] 非 null 时显示列表数量（仅数字）。元素等间距分布。
 */
@Composable
internal fun MusicListHeader(
    config: HeaderConfig,
    modifier: Modifier = Modifier,
    showEditButton: Boolean = false,
    onEditClick: () -> Unit = {},
    listCount: Int? = null,
) {
    when (config) {
        is HeaderConfig.None -> { }
        is HeaderConfig.Simple -> SimpleHeader(
            onOrderPlay = config.onOrderPlay,
            onShufflePlay = config.onShufflePlay,
            showEditButton = showEditButton,
            onEditClick = onEditClick,
            listCount = listCount,
            trailing = config.trailing,
            modifier = modifier,
        )
        is HeaderConfig.Full -> FullHeader(
            selectedGenre = config.selectedGenre,
            selectedOrder = config.selectedOrder,
            onFilterGenreChange = config.onFilterGenreChange,
            onFilterOrderChange = config.onFilterOrderChange,
            onOrderPlay = config.onOrderPlay,
            onShufflePlay = config.onShufflePlay,
            showEditButton = showEditButton,
            onEditClick = onEditClick,
            listCount = listCount,
            modifier = modifier,
            singleRowFilter = config.singleRowFilter,
        )
        is HeaderConfig.Custom -> config.content()
    }
}

@Composable
private fun SimpleHeader(
    onOrderPlay: () -> Unit,
    onShufflePlay: () -> Unit,
    showEditButton: Boolean,
    onEditClick: () -> Unit,
    listCount: Int?,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberPlatformHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HeaderBarTotalHeight)
            .padding(horizontal = HeaderRowPadding)
            .padding(top = HeaderRowVerticalPadding, bottom = HeaderRowBottomPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (listCount != null) {
            Text(
                text = "$listCount",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.width(CountLabelWidth),
            )
        }
        if (trailing != null) trailing()
        IconButton(
            onClick = {
                haptic.perform(HapticEffect.VIRTUAL_KEY)
                onOrderPlay()
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.order_play),
                contentDescription = stringResource(Res.string.order_play_desc),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(
            onClick = {
                haptic.perform(HapticEffect.CONFIRM)
                onShufflePlay()
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.shuffle),
                contentDescription = stringResource(Res.string.shuffle_desc),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (showEditButton) {
            IconButton(
                onClick = {
                    haptic.perform(HapticEffect.VIRTUAL_KEY)
                    onEditClick()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.rectangle_on_rectangle),
                    contentDescription = stringResource(Res.string.edit_desc),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FullHeader(
    selectedGenre: MusicSortKey,
    selectedOrder: SortDirection,
    onFilterGenreChange: (MusicSortKey) -> Unit,
    onFilterOrderChange: (SortDirection) -> Unit,
    onOrderPlay: () -> Unit,
    onShufflePlay: () -> Unit,
    showEditButton: Boolean,
    onEditClick: () -> Unit,
    listCount: Int?,
    modifier: Modifier = Modifier,
    singleRowFilter: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberPlatformHaptics()
    val transparent = Color.Transparent

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderBarTotalHeight)
                .padding(horizontal = HeaderRowPadding)
                .padding(top = HeaderRowVerticalPadding, bottom = HeaderRowBottomPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (listCount != null) {
                Text(
                    text = "$listCount",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.width(CountLabelWidth),
                )
            }
            IconButton(
                onClick = {
                    haptic.perform(HapticEffect.VIRTUAL_KEY)
                    expanded = !expanded
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.slider_vertical_3),
                    contentDescription = stringResource(Res.string.expand_sort_desc),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = {
                    haptic.perform(HapticEffect.VIRTUAL_KEY)
                    onOrderPlay()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.order_play),
                    contentDescription = stringResource(Res.string.order_play_desc),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = {
                    haptic.perform(HapticEffect.CONFIRM)
                    onShufflePlay()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.shuffle),
                    contentDescription = stringResource(Res.string.shuffle_desc),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (showEditButton) {
                IconButton(
                    onClick = {
                        haptic.perform(HapticEffect.VIRTUAL_KEY)
                        onEditClick()
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.rectangle_on_rectangle),
                        contentDescription = stringResource(Res.string.edit_desc),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(durationMillis = 300, easing = AnimationTokens.EASE_OUT)) +
                fadeIn(animationSpec = tween(durationMillis = 300, easing = AnimationTokens.EASE_OUT)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = 300, easing = AnimationTokens.EASE_IN)) +
                fadeOut(animationSpec = tween(durationMillis = 300, easing = AnimationTokens.EASE_IN)),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                color = transparent,
            ) {
                val genres = listOf(
                    // 键是枚举（D2-08）：这里从前写 "fileSize" / "date"，
                    // 而三端仓库的 when 不认这两个串 —— 点"按文件大小排序"实际按 id 排。
                    stringResource(Res.string.sort_title) to MusicSortKey.TITLE,
                    stringResource(Res.string.sort_artist) to MusicSortKey.ARTIST,
                    stringResource(Res.string.sort_duration) to MusicSortKey.DURATION,
                    stringResource(Res.string.sort_size) to MusicSortKey.FILE_SIZE,
                    stringResource(Res.string.sort_play_count) to MusicSortKey.PLAY_COUNT,
                    stringResource(Res.string.sort_add_time) to MusicSortKey.DATE,
                )
                val orders = listOf(
                    stringResource(Res.string.order_asc) to SortDirection.ASC,
                    stringResource(Res.string.order_desc) to SortDirection.DESC,
                )
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.sort),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        FlowRow(
                            maxItemsInEachRow = 3,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            genres.forEach { (label, value) ->
                                FilterChip(
                                    selected = selectedGenre == value,
                                    onClick = {
                                        haptic.perform(HapticEffect.TICK)
                                        onFilterGenreChange(value)
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.order),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            orders.forEach { (label, value) ->
                                FilterChip(
                                    selected = selectedOrder == value,
                                    onClick = {
                                        haptic.perform(HapticEffect.TICK)
                                        onFilterOrderChange(value)
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
