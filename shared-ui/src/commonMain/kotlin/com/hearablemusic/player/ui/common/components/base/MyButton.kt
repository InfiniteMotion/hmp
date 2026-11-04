package com.hearablemusic.player.ui.common.components.base

import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import com.hearablemusic.player.ui.generated.resources.lightbulb
import com.hearablemusic.player.ui.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import com.hearablemusic.player.ui.generated.resources.back_button_desc
import com.hearablemusic.player.ui.generated.resources.chevron_left
import com.hearablemusic.player.ui.generated.resources.magnifyingglass
import com.hearablemusic.player.ui.generated.resources.new_playlist_desc
import com.hearablemusic.player.ui.generated.resources.order
import com.hearablemusic.player.ui.generated.resources.order_asc
import com.hearablemusic.player.ui.generated.resources.order_desc
import com.hearablemusic.player.ui.generated.resources.order_play
import com.hearablemusic.player.ui.generated.resources.plus
import com.hearablemusic.player.ui.generated.resources.search_button_desc
import com.hearablemusic.player.ui.generated.resources.shuffle
import com.hearablemusic.player.ui.generated.resources.slider_vertical_3
import com.hearablemusic.player.ui.generated.resources.sort
import com.hearablemusic.player.ui.generated.resources.sort_add_time
import com.hearablemusic.player.ui.generated.resources.sort_artist
import com.hearablemusic.player.ui.generated.resources.sort_duration
import com.hearablemusic.player.ui.generated.resources.sort_play_count
import com.hearablemusic.player.ui.generated.resources.sort_size
import com.hearablemusic.player.ui.generated.resources.sort_title

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.animation.HmpMotion

@Composable
fun PlayControlButtonOne(
    selectedGenre: MusicSortKey,
    selectedOrder: SortDirection,
    onFilterGenreChange: (MusicSortKey) -> Unit,
    onFilterOrderChange: (SortDirection) -> Unit,
    onOrderPlay: () -> Unit,
    onShufflePlay: () -> Unit
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = {
                    haptic.performClick()
                    expanded = !expanded
                },
                modifier = Modifier
                    .size(metrics.block)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.slider_vertical_3),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "select Button",
                    modifier = Modifier.size(metrics.section),
                )
            }
            IconButton(
                onClick = {
                    onOrderPlay()
                },
                modifier = Modifier
                    .size(metrics.block)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.order_play),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "order play Button",
                    modifier = Modifier.size(metrics.section),
                )
            }
            IconButton(
                onClick = {
                    haptic.performConfirm()
                    onShufflePlay()
                },
                modifier = Modifier
                    .size(metrics.block)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.shuffle),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "shuffle play Button",
                    modifier = Modifier.size(metrics.section),
                )
            }
            IconButton(
                onClick = {
                    haptic.performLightClick()
                },
                modifier = Modifier
                    .size(metrics.block)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.lightbulb),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "self play Button",
                    modifier = Modifier.size(metrics.section),
                )
            }
        }
        // 可隐藏的内容块
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = HmpMotion.enter()) +
                    fadeIn(animationSpec = HmpMotion.enter()),
            exit = shrinkVertically(animationSpec = HmpMotion.exit()) +
                    fadeOut(animationSpec = HmpMotion.exit())
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.cozy),
                color = Transparent,
            ) {
                val genres = listOf(
                    // 键是枚举（D2-08）：这里从前写 "fileSize" / "date"，
                    // 而三端仓库的 when 不认这两个串 —— 点"按文件大小排序"实际按 id 排。
                    stringResource(Res.string.sort_title) to MusicSortKey.TITLE,
                    stringResource(Res.string.sort_artist) to MusicSortKey.ARTIST,
                    stringResource(Res.string.sort_duration) to MusicSortKey.DURATION,
                    stringResource(Res.string.sort_size) to MusicSortKey.FILE_SIZE,
                    stringResource(Res.string.sort_play_count) to MusicSortKey.PLAY_COUNT,
                    stringResource(Res.string.sort_add_time) to MusicSortKey.DATE
                )
                val orders = listOf(
                    stringResource(Res.string.order_asc) to SortDirection.ASC,
                    stringResource(Res.string.order_desc) to SortDirection.DESC
                )
                Column(
                    modifier = Modifier.padding(metrics.compact),
                    verticalArrangement = Arrangement.spacedBy(metrics.base)
                ) {
                    Spacer(modifier = Modifier.height(metrics.cozy))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(metrics.compact)
                    ) {
                        Text(
                            text = stringResource(Res.string.sort),
                            style = typo.subtitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = metrics.compact)
                        )
                        FlowRow(
                            maxItemsInEachRow = 3,
                            verticalArrangement = Arrangement.spacedBy(metrics.micro),
                            horizontalArrangement = Arrangement.spacedBy(metrics.micro),
                        ) {
                            genres.forEach { (genre,eGenre) ->
                                FilterChip(
                                    selected = selectedGenre == eGenre,
                                    onClick = {
                                        haptic.performLightClick()
                                        onFilterGenreChange(eGenre)
                                    },
                                    label = { Text(text = genre, style = typo.body.medium()) },
                                    // 选中状态样式
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(metrics.compact)
                    ) {
                        Text(
                            text = stringResource(Res.string.order),
                            style = typo.subtitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = metrics.compact)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(metrics.micro),
                        ) {
                            orders.forEach{ (order,eOrder) ->
                                FilterChip(
                                    selected = selectedOrder == eOrder,
                                    onClick = {
                                        haptic.performLightClick()
                                        onFilterOrderChange(eOrder)
                                    },
                                    label = { Text(text = order, style = typo.body.medium()) },
                                    // 选中状态样式
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
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

@Composable
fun PlayControlButtonTwo(
    onShufflePlay: () -> Unit,
    onOrderPlay: () -> Unit
) {
    val metrics = LocalHmpMetrics.current
    val haptic = rememberHapticFeedback()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = metrics.section),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                    onClick = {
                        haptic.performConfirm()
                        onShufflePlay()
                    },
            modifier = Modifier
                .size(metrics.block)
            ) {
            Icon(
                painter = painterResource(Res.drawable.shuffle),
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = "shuffle play Button",
                modifier = Modifier.size(metrics.section),
            )
            }
            Spacer(modifier = Modifier.width(LayoutConstraints.CoverHeightSmall))
            IconButton(
                onClick = {
                    haptic.performConfirm()
                    onOrderPlay()
                },
                modifier = Modifier
                    .size(metrics.block)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.order_play),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "order play Button",
                    modifier = Modifier.size(metrics.section),
                )
            }
        }
    }
}



@Composable
fun BackButton(
    onClick: () -> Unit
){
    val metrics = LocalHmpMetrics.current
    val haptic = rememberHapticFeedback()
    FilledIconButton(
        onClick = {
            haptic.performClick()
            onClick()
        },
        modifier = Modifier
            .size(metrics.block), // Larger touch target
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            painter = painterResource(Res.drawable.chevron_left),
            contentDescription = stringResource(Res.string.back_button_desc),
            modifier = Modifier.size(metrics.section)
        )
    }
}

@Composable
fun SearchButton(
    navController: NavBackStack<NavKey>
){
    val metrics = LocalHmpMetrics.current
    val haptic = rememberHapticFeedback()
    FilledIconButton(
        onClick = {
            haptic.performClick()
            navController.add(Routes.Library.Search)
        },
        modifier = Modifier
            .size(metrics.block), // Larger touch target
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            painter = painterResource(Res.drawable.magnifyingglass),
            contentDescription = stringResource(Res.string.search_button_desc),
            modifier = Modifier.size(metrics.cozy)
        )
    }
}

@Composable
fun NewPlaylistButton(onClick: () -> Unit) {
    val metrics = LocalHmpMetrics.current
    val haptic = rememberHapticFeedback()
    FilledIconButton(
        onClick = {
            haptic.performClick()
            onClick()
        },
        modifier = Modifier.size(metrics.block),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            painter = painterResource(Res.drawable.plus),
            contentDescription = stringResource(Res.string.new_playlist_desc),
            modifier = Modifier.size(metrics.cozy)
        )
    }
}

/**
 * 播放列表生成组合按钮组件
 * 提供智能播放列表生成功能入口，采用两排组合按钮布局
 * 第一排：偏好选择（多选一）
 * 第二排：算法选择（二选一）+ 生成按钮
 */
