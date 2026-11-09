package com.hearablemusic.player.ui.player.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.clear
import com.hearablemusic.player.ui.library.pages.components.musiclist.CurrentPlayingConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.EditConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.HeaderConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemVariant
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.defaultMusicListConfig
import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * 播放器内嵌的播放列表区域：使用新版 musiclist，外观与行为与原来一致。
 * 展开/收起动画、展开时外层滚动、头部（清空 + 当前/总数）、固定高度列表。
 */
@Composable
fun PlaylistArea(
    expanded: Boolean,
    playlist: List<MusicInfo>,
    currentIndex: Int,
    scrollState: ScrollState,
    onClearPlaylist: () -> Unit,
    onPlayItem: (MusicInfo) -> Unit,
    onMoveToTop: (MusicInfo) -> Unit,
    onRemoveFromPlaylist: (MusicInfo) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val haptic = rememberHapticFeedback()
    val density = LocalDensity.current

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(320)
            val playlistHeightPx = with(density) { LayoutConstraints.PlaylistSheetHeight.toPx() }
            val targetScroll = (scrollState.value + playlistHeightPx).toInt()
            scrollState.animateScrollTo(
                value = targetScroll.coerceAtMost(scrollState.maxValue),
                animationSpec = HmpMotion.emphasis(),
            )
        }
    }

    val callbacks = object : MusicListCallbacksAdapter() {
        override fun onItemClick(musicInfo: MusicInfo, index: Int) {
            haptic.performClick()
            onPlayItem(musicInfo)
        }
        override fun onPinToTop(musicInfo: MusicInfo) {
            haptic.performConfirm()
            onMoveToTop(musicInfo)
        }
        override fun onRemove(musicInfo: MusicInfo) {
            haptic.performLightClick()
            onRemoveFromPlaylist(musicInfo)
        }
    }
    val config = defaultMusicListConfig(callbacks).copy(
        header = HeaderConfig.None,
        item = ItemConfig(
            showIndex = true,
            variant = ItemVariant.Full,
            fullOptions = FullItemOptions(
                showPinButton = true,
                showRemoveButton = true,
                showMenuButton = false,
            ),
        ),
        edit = EditConfig(enabled = false),
        currentPlaying = CurrentPlayingConfig(
            index = currentIndex.takeIf { it in playlist.indices },
            autoScrollToCurrent = true,
        ),
        contentPadding = PaddingValues(horizontal = metrics.cozy),
    )

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(HmpMotion.enter()) + fadeIn(HmpMotion.enter()),
        exit = shrinkVertically(HmpMotion.exit()) + fadeOut(HmpMotion.exit()),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = metrics.cozy, end = metrics.block, top = metrics.base, bottom = metrics.micro),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        haptic.performLightClick()
                        onClearPlaylist()
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.clear),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "${currentIndex + 1}/${playlist.size}",
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            MusicList(
                musicInfoList = playlist,
                config = config,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LayoutConstraints.PlaylistAreaHeight),
                isPlaying = true,
            )
        }
    }
}
