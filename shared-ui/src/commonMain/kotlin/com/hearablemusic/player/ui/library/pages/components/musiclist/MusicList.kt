package com.hearablemusic.player.ui.library.pages.components.musiclist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hmp.domain.music.MusicInfo

/**
 * 增强版音乐列表根组件。根据 [config] 渲染头部、编辑栏、列表内容；内部持有 [LazyListState] 与 [MusicListState]。
 *
 * @param musicInfoList 数据源
 * @param config 统一配置（头部、单项、列表、编辑、索引、滚动条、当前播放、回调）
 * @param modifier 根 Modifier
 * @param isPlaying 是否正在播放（可用于将来在单项上显示播放图标等）
 */
@Composable
fun MusicList(
    musicInfoList: List<MusicInfo>,
    config: MusicListConfig,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    isPlaying: Boolean = false,
) {
    val metrics = LocalHmpMetrics.current
    val state = rememberMusicListState()
    val density = LocalDensity.current
    val currentIndex = config.currentPlaying.index
    val isCurrentPlaying: (Int) -> Boolean = { index -> currentIndex == index }

    LaunchedEffect(currentIndex, listState) {
        if (currentIndex != null &&
            currentIndex in musicInfoList.indices &&
            config.currentPlaying.autoScrollToCurrent
        ) {
            val offsetPx = config.currentPlaying.scrollOffsetForCenter?.let { dp ->
                with(density) { dp.toPx() }.toInt()
            } ?: 0
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = offsetPx,
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        val showEditToolbar = config.edit.enabled && state.isEditMode && config.edit.showToolbar
        AnimatedContent(
            targetState = showEditToolbar,
            transitionSpec = {
                fadeIn(animationSpec = HmpMotion.micro()) togetherWith fadeOut(animationSpec = HmpMotion.micro())
            },
            label = "editMode",
        ) { isEdit ->
            if (isEdit) {
                MusicListEditToolbar(
                    config = config.edit,
                    state = state,
                    allIds = musicInfoList.map { it.music.id }.toSet(),
                    callbacks = config.callbacks,
                    onExitEditMode = {
                        state.exitEditMode()
                        config.callbacks.onExitEditMode()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                MusicListHeader(
                    config = config.header,
                    modifier = Modifier.fillMaxWidth(),
                    showEditButton = config.edit.enabled,
                    onEditClick = {
                        state.enterEditMode()
                        config.callbacks.onEnterEditMode()
                    },
                    listCount = musicInfoList.size.takeIf { config.header !is HeaderConfig.None },
                )
            }
        }
        if (config.header !is HeaderConfig.None) {
            Spacer(modifier = Modifier.height(metrics.base))
        }
        // 索引条宽度，与 MusicListIndexStrip 一致，用于为列表预留右侧空间
        val indexStripWidth = metrics.section
        val showIndexStrip = config.indexJump.enabled && musicInfoList.size > 10
        val showScrollbar = config.scrollbar.enabled && !showIndexStrip

        Box(modifier = Modifier.fillMaxSize()) {
            MusicListContent(
                musicInfoList = musicInfoList,
                config = config,
                listState = listState,
                state = state,
                isCurrentPlaying = isCurrentPlaying,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showIndexStrip) {
                            Modifier.padding(end = indexStripWidth)
                        } else {
                            Modifier
                        },
                    ),
            )
            if (showScrollbar) {
                MusicListScrollbar(
                    listState = listState,
                    config = config.scrollbar,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxSize(),
                )
            }
            if (showIndexStrip) {
                MusicListIndexStrip(
                    musicInfoList = musicInfoList,
                    listState = listState,
                    config = config.indexJump,
                    currentPlayingIndex = config.currentPlaying.index,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(indexStripWidth)
                        .fillMaxHeight(),
                    columns = config.list.columns,
                )
            }
        }
    }
}

/**
 * 非懒加载版音乐列表。与 [MusicList] 共用同一套 [MusicListConfig]，但使用 Column 逐项 compose，
 * 不依赖 [LazyListState]，适用于嵌入已有滚动容器（如外层 LazyColumn/Column+verticalScroll）的场景。
 * 不展示滚动条与索引条（无列表滚动状态）。
 *
 * @param musicInfoList 数据源
 * @param config 统一配置（与 [MusicList] 相同）
 * @param modifier 根 Modifier
 * @param isPlaying 是否正在播放
 */
@Composable
fun FixedMusicList(
    musicInfoList: List<MusicInfo>,
    config: MusicListConfig,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
) {
    val metrics = LocalHmpMetrics.current
    val state = rememberMusicListState()
    val currentIndex = config.currentPlaying.index
    val isCurrentPlaying: (Int) -> Boolean = { index -> currentIndex == index }

    Column(modifier = modifier.fillMaxWidth()) {
        val showEditToolbar = config.edit.enabled && state.isEditMode && config.edit.showToolbar
        AnimatedContent(
            targetState = showEditToolbar,
            transitionSpec = {
                fadeIn(animationSpec = HmpMotion.micro()) togetherWith fadeOut(animationSpec = HmpMotion.micro())
            },
            label = "editMode",
        ) { isEdit ->
            if (isEdit) {
                MusicListEditToolbar(
                    config = config.edit,
                    state = state,
                    allIds = musicInfoList.map { it.music.id }.toSet(),
                    callbacks = config.callbacks,
                    onExitEditMode = {
                        state.exitEditMode()
                        config.callbacks.onExitEditMode()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                MusicListHeader(
                    config = config.header,
                    modifier = Modifier.fillMaxWidth(),
                    showEditButton = config.edit.enabled,
                    onEditClick = {
                        state.enterEditMode()
                        config.callbacks.onEnterEditMode()
                    },
                    listCount = musicInfoList.size.takeIf { config.header !is HeaderConfig.None },
                )
            }
        }
        Spacer(modifier = Modifier.height(metrics.base))
        Box(modifier = Modifier.fillMaxWidth()) {
            FixedMusicListContent(
                musicInfoList = musicInfoList,
                config = config,
                state = state,
                isCurrentPlaying = isCurrentPlaying,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
