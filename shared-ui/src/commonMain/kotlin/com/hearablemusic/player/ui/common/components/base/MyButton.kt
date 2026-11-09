package com.hearablemusic.player.ui.common.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.back_button_desc
import com.hearablemusic.player.ui.generated.resources.chevron_left
import com.hearablemusic.player.ui.generated.resources.magnifyingglass
import com.hearablemusic.player.ui.generated.resources.new_playlist_desc
import com.hearablemusic.player.ui.generated.resources.order_play
import com.hearablemusic.player.ui.generated.resources.order_play_button_desc
import com.hearablemusic.player.ui.generated.resources.plus
import com.hearablemusic.player.ui.generated.resources.search_button_desc
import com.hearablemusic.player.ui.generated.resources.shuffle
import com.hearablemusic.player.ui.generated.resources.shuffle_play_button_desc
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayControlButtonTwo(
    onShufflePlay: () -> Unit,
    onOrderPlay: () -> Unit,
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
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(
                onClick = {
                    haptic.performConfirm()
                    onShufflePlay()
                },
                modifier = Modifier
                    .size(metrics.block),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.shuffle),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(Res.string.shuffle_play_button_desc),
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
                    .size(metrics.block),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.order_play),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(Res.string.order_play_button_desc),
                    modifier = Modifier.size(metrics.section),
                )
            }
        }
    }
}

@Composable
fun BackButton(
    onClick: () -> Unit,
) {
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
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            painter = painterResource(Res.drawable.chevron_left),
            contentDescription = stringResource(Res.string.back_button_desc),
            modifier = Modifier.size(metrics.section),
        )
    }
}

@Composable
fun SearchButton(
    onClick: () -> Unit,
) {
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
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            painter = painterResource(Res.drawable.magnifyingglass),
            contentDescription = stringResource(Res.string.search_button_desc),
            modifier = Modifier.size(metrics.cozy),
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
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            painter = painterResource(Res.drawable.plus),
            contentDescription = stringResource(Res.string.new_playlist_desc),
            modifier = Modifier.size(metrics.cozy),
        )
    }
}

/**
 * 播放列表生成组合按钮组件
 * 提供智能播放列表生成功能入口，采用两排组合按钮布局
 * 第一排：偏好选择（多选一）
 * 第二排：算法选择（二选一）+ 生成按钮
 */
