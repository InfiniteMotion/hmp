package com.hearablemusic.player.ui.playlist.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.dialogs.CreatePlaylistDialog
import com.hearablemusic.player.ui.common.dialogs.MusicPickerDialog
import com.hearablemusic.player.ui.common.dialogs.PlaylistPickerDialog
import com.hearablemusic.player.ui.common.dialogs.base.InputDialog
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.text.resolveForEffect
import com.hearablemusic.player.ui.common.util.LoadState
import com.hearablemusic.player.ui.common.util.LocalAppHazeState
import com.hearablemusic.player.ui.common.util.LocalHazeRenderSettings
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.util.commonFormat
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.add_songs_to_playlist
import com.hearablemusic.player.ui.generated.resources.added_n_songs_to_playlist
import com.hearablemusic.player.ui.generated.resources.counts
import com.hearablemusic.player.ui.generated.resources.duration
import com.hearablemusic.player.ui.generated.resources.edit_list
import com.hearablemusic.player.ui.generated.resources.edit_playlist_description
import com.hearablemusic.player.ui.generated.resources.gearshape
import com.hearablemusic.player.ui.generated.resources.minutes_format
import com.hearablemusic.player.ui.generated.resources.play
import com.hearablemusic.player.ui.generated.resources.playlist_description_hint
import com.hearablemusic.player.ui.generated.resources.playlist_name_hint
import com.hearablemusic.player.ui.generated.resources.playlist_updated
import com.hearablemusic.player.ui.generated.resources.plus
import com.hearablemusic.player.ui.generated.resources.rename_playlist
import com.hearablemusic.player.ui.generated.resources.select_playlist
import com.hearablemusic.player.ui.library.pages.components.AlbumCover
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.playlistPresetMusicListConfig
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistEditorEffect
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistEditorViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistEffect
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistScreenViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistViewModel
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.Playlist
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.hearablemusic.player.ui.common.navigation.Routes as NavRoutes

@Composable
fun PlaylistScreen(
    navController: NavBackStack<NavKey>,
    playlistId: Long? = null,
    playlistName: String? = null,
    // 屏级 VM：选中歌单 / 弹窗 / 条目动作只服务本屏，随 entry 销毁
    screenViewModel: PlaylistScreenViewModel = koinViewModel(),
    editorViewModel: PlaylistEditorViewModel = koinViewModel(),
    // 跨屏数据 VM：自建歌单列表（加歌候选与排序都用它）
    playlistViewModel: PlaylistViewModel = activityViewModel(),
    // 7.5 P1：旧 VM 是 controller 的零逻辑包装，直接用平台门面（白名单允许）
    playbackController: PlaybackController = koinInject(),
    dialogViewModel: DialogViewModel = activityViewModel(),
    dialogManagerViewModel: DialogManagerViewModel = activityViewModel(),
) {
    val metrics = LocalHmpMetrics.current
    val haptic = rememberHapticFeedback()
    val dialogManager = dialogManagerViewModel.dialogManager
    val addSongsDialogTitle = stringResource(Res.string.add_songs_to_playlist)
    val selectPlaylistTitle = stringResource(Res.string.select_playlist)
    val addedNSongsMessageFormat = stringResource(Res.string.added_n_songs_to_playlist)

    // 7.5 P1：添加歌曲的候选框与完成提示走 VM 一次性效果（原回调链在 UI 里做过滤与 toast）
    LaunchedEffect(Unit) {
        screenViewModel.effect.collect { effect ->
            when (effect) {
                is PlaylistEffect.SongsAdded ->
                    dialogManager.showMessage(commonFormat(addedNSongsMessageFormat, effect.count))
            }
        }
    }

    // 唯一状态出口：歌单数据、屏级弹窗都在这里
    val uiState by screenViewModel.uiState.collectAsState()
    val editorState by editorViewModel.uiState.collectAsState()
    val playlistUpdatedMessage = stringResource(Res.string.playlist_updated)

    // 弹窗归屏：编辑结果回本屏刷新，不再经应用级 VM 的回调字段
    LaunchedEffect(Unit) {
        editorViewModel.effect.collect { effect ->
            when (effect) {
                is PlaylistEditorEffect.Saved -> {
                    screenViewModel.loadPlaylistById(effect.playlistId)
                    dialogManager.showMessage(playlistUpdatedMessage)
                }

                is PlaylistEditorEffect.Message -> dialogManager.showMessage(effect.text.resolveForEffect())
            }
        }
    }
    // 跨屏共享的播放态与自建歌单列表是投影白名单，不并进本屏状态
    val isPlaying by playbackController.isPlaying.collectAsState()
    val userCustomPlaylistsState by playlistViewModel.userCustomPlaylistsState.collectAsState()

    LaunchedEffect(playlistId, playlistName) {
        when {
            playlistId != null -> screenViewModel.loadPlaylistById(playlistId)
            playlistName != null -> screenViewModel.getSelectedPlaylist(playlistName)
        }
    }

    val playlist = (uiState.playlist as? LoadState.Success)?.data ?: emptyList()
    val userCustomPlaylists = (userCustomPlaylistsState as? LoadState.Success)?.data ?: emptyList()
    val playlistMeta = uiState.playlistMeta
    val selectedPlaylistId = uiState.selectedPlaylistId
    val isCustomPlaylist = uiState.isCustomPlaylist

    // 以下都是 UI 白名单：滚动 / 拖拽测量 / 动画派生值
    val musicListState = rememberLazyListState()
    val density = LocalDensity.current
    val maxHeaderCollapsePx = with(density) { LayoutConstraints.CoverHeightSmall.toPx() }
    val headerExpandDamping = 0.80f
    var headerCollapseOffsetPx by remember(selectedPlaylistId, uiState.playlistName) { mutableFloatStateOf(0f) }
    val isMusicListAtTop by remember {
        derivedStateOf {
            musicListState.firstVisibleItemIndex == 0 && musicListState.firstVisibleItemScrollOffset == 0
        }
    }
    val shouldCollapseHeader by remember(isCustomPlaylist) {
        derivedStateOf {
            isCustomPlaylist && headerCollapseOffsetPx > 1f
        }
    }

    LaunchedEffect(
        isCustomPlaylist,
        musicListState.firstVisibleItemIndex,
        musicListState.firstVisibleItemScrollOffset,
    ) {
        if (
            isCustomPlaylist &&
            (musicListState.firstVisibleItemIndex > 0 || musicListState.firstVisibleItemScrollOffset > 24) &&
            headerCollapseOffsetPx < maxHeaderCollapsePx
        ) {
            headerCollapseOffsetPx = maxHeaderCollapsePx
        }
    }
    val nestedScrollConnection = remember(isCustomPlaylist, maxHeaderCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isCustomPlaylist || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y < 0f && headerCollapseOffsetPx < maxHeaderCollapsePx) {
                    val consume = minOf(-available.y, maxHeaderCollapsePx - headerCollapseOffsetPx)
                    headerCollapseOffsetPx += consume
                    return Offset(x = 0f, y = -consume)
                }
                if (available.y > 0f && isMusicListAtTop && headerCollapseOffsetPx > 0f) {
                    val damped = available.y * headerExpandDamping
                    val consume = minOf(damped, headerCollapseOffsetPx)
                    headerCollapseOffsetPx -= consume
                    return Offset(x = 0f, y = consume)
                }
                return Offset.Zero
            }
        }
    }

    // 重命名 / 描述弹窗：可见性与输入初值由 VM 状态与歌单元数据派生
    InputDialog(
        visible = uiState.showRenameDialog && selectedPlaylistId != null,
        title = stringResource(Res.string.rename_playlist),
        hint = stringResource(Res.string.playlist_name_hint),
        initialValue = uiState.playlistName,
        onConfirm = screenViewModel::onRenameConfirmed,
        onDismiss = screenViewModel::onRenameDismissed,
    )
    InputDialog(
        visible = uiState.showDescriptionDialog && selectedPlaylistId != null,
        title = stringResource(Res.string.edit_playlist_description),
        hint = stringResource(Res.string.playlist_description_hint),
        initialValue = playlistMeta?.description.orEmpty(),
        singleLine = false,
        minLines = 2,
        maxLines = 4,
        onConfirm = screenViewModel::onDescriptionConfirmed,
        onDismiss = screenViewModel::onDescriptionDismissed,
    )

    if (editorState.visible) {
        CreatePlaylistDialog(
            state = editorState,
            onNameChange = editorViewModel::onNameChange,
            onDescriptionChange = editorViewModel::onDescriptionChange,
            onPinnedChange = editorViewModel::onPinnedChange,
            onAddSongs = editorViewModel::onAddSongsRequested,
            onSubmit = editorViewModel::onSubmit,
            onDismiss = editorViewModel::onDismiss,
            hazeState = LocalAppHazeState.current,
            hazeRenderSettings = LocalHazeRenderSettings.current,
        )
    }

    uiState.musicPicker?.let { picker ->
        MusicPickerDialog(
            allMusic = picker.allMusic,
            selectedIds = picker.selectedIds,
            title = addSongsDialogTitle,
            onConfirm = screenViewModel::onSongsPicked,
            onDismiss = screenViewModel::onMusicPickerDismissed,
            hazeState = LocalAppHazeState.current,
            hazeRenderSettings = LocalHazeRenderSettings.current,
        )
    }
    uiState.playlistPicker?.let { picker ->
        PlaylistPickerDialog(
            playlists = picker.playlists,
            title = selectPlaylistTitle,
            onDismiss = screenViewModel::onPlaylistPickerDismissed,
            onSelectPlaylist = { playlist -> screenViewModel.onPlaylistPicked(playlist.id) },
            hazeState = LocalAppHazeState.current,
            hazeRenderSettings = LocalHazeRenderSettings.current,
        )
    }

    val onShufflePlay: () -> Unit = {
        selectedPlaylistId?.let { screenViewModel.recordPlaylistPlay(it) }
        playbackController.addAllToPlaylistByShuffle(playlist)
        navController.add(NavRoutes.Player.Player)
    }
    val onOrderPlay: () -> Unit = {
        selectedPlaylistId?.let { screenViewModel.recordPlaylistPlay(it) }
        playbackController.addAllToPlaylistInOrder(playlist)
        navController.add(NavRoutes.Player.Player)
    }
    val onAddSongsClick: (() -> Unit)? = if (isCustomPlaylist && selectedPlaylistId != null) {
        { screenViewModel.onAddSongsRequested() }
    } else {
        null
    }

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = uiState.playlistName,
        trailingContent = if (isCustomPlaylist && playlistMeta != null) {
            {
                FilledIconButton(
                    onClick = {
                        haptic.performClick()
                        editorViewModel.onEditRequested(playlistMeta)
                    },
                    modifier = Modifier.size(metrics.block),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.gearshape),
                        contentDescription = stringResource(Res.string.edit_list),
                        modifier = Modifier.size(metrics.cozy),
                    )
                }
            }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection),
        ) {
            if (isCustomPlaylist && playlistMeta != null) {
                AnimatedVisibility(
                    visible = !shouldCollapseHeader,
                    enter = expandVertically(animationSpec = HmpMotion.standard()) + fadeIn(animationSpec = HmpMotion.micro()),
                    exit = shrinkVertically(animationSpec = HmpMotion.standard()) + fadeOut(animationSpec = HmpMotion.micro()),
                ) {
                    PlaylistHeader(meta = playlistMeta)
                }
            }

            val callbacks = object : MusicListCallbacksAdapter() {
                override fun onItemClick(musicInfo: MusicInfo, index: Int) {
                    haptic.performClick()
                    screenViewModel.onPlay(musicInfo)
                }
                override fun onMenuClick(musicInfo: MusicInfo) {
                    val menuConfig = DialogViewModel.MusicDetailMenuConfig(
                        showAddToSpecificPlaylist = true,
                        showShare = true,
                        showViewDetail = true,
                        showPlayNext = true,
                        showRemoveFromCurrentPlaylist = false,
                        showDelete = false,
                    )
                    dialogViewModel.showMusicDetailDialog(musicInfo, menuConfig)
                }
                override fun onRemoveFromPlaylist(musicInfo: MusicInfo) {
                    selectedPlaylistId?.let { pid -> screenViewModel.removeItemFromPlaylist(musicInfo.music.id, pid) }
                }
                override fun onRemove(musicInfo: MusicInfo) {
                    selectedPlaylistId?.let { pid -> screenViewModel.removeItemFromPlaylist(musicInfo.music.id, pid) }
                }
                override fun onBatchAddToPlaylist(selectedIds: Set<Long>) {
                    screenViewModel.onBatchAddToPlaylistRequested(selectedIds, userCustomPlaylists)
                }
                override fun onPinToTop(musicInfo: MusicInfo) {
                    val ids = listOf(musicInfo.music.id) + playlist.map { it.music.id }.filter { it != musicInfo.music.id }
                    selectedPlaylistId?.let { pid -> screenViewModel.reorderPlaylistItems(pid, ids) }
                }
            }
            val baseConfig = playlistPresetMusicListConfig(
                onOrderPlay = onOrderPlay,
                onShufflePlay = onShufflePlay,
                callbacks = callbacks,
                headerTrailing = if (onAddSongsClick != null) {
                    {
                        IconButton(
                            onClick = {
                                haptic.performClick()
                                onAddSongsClick()
                            },
                            modifier = Modifier.size(metrics.block),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.plus),
                                contentDescription = stringResource(Res.string.add_songs_to_playlist),
                                modifier = Modifier.size(metrics.section),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                } else {
                    null
                },
            )
            val config = baseConfig.copy(
                item = baseConfig.item.copy(
                    fullOptions = baseConfig.item.fullOptions?.copy(showRemoveButton = true)
                        ?: FullItemOptions(showPinButton = true, showRemoveButton = true, showMenuButton = true),
                ),
            )
            MusicList(
                musicInfoList = playlist,
                config = config,
                listState = musicListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                isPlaying = isPlaying,
            )
        }
    }
}

private const val HEADER_COVER_SIZE_DP = 280
private const val HEADER_CORNER_RADIUS_DP = 25

@Composable
private fun PlaylistStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.micro, Alignment.CenterVertically),
    ) {
        Text(
            text = value,
            style = typo.subtitle.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = typo.caption,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistHeader(
    meta: Playlist,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(metrics.section),
        colors = CardDefaults.cardColors(
            containerColor = Transparent,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.cozy),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(HEADER_COVER_SIZE_DP.dp)
                    .clip(RoundedCornerShape(HEADER_CORNER_RADIUS_DP.dp)),
            ) {
                AlbumCover(
                    uri = meta.coverUri,
                    size = HEADER_COVER_SIZE_DP.dp,
                    corner = metrics.section,
                    shadow = LayoutConstraints.ShadowElevation,
                )
            }

            Spacer(modifier = Modifier.height(metrics.cozy))

            val lastPlayedAt = meta.lastPlayedAt
            if (meta.songCount > 0 || meta.playCount > 0 || meta.totalDurationMs > 0 || lastPlayedAt != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (meta.songCount > 0) {
                        PlaylistStatItem(
                            label = stringResource(Res.string.counts),
                            value = meta.songCount.toString(),
                        )
                    }
                    if (meta.totalDurationMs > 0) {
                        val minutes = (meta.totalDurationMs / 1000 / 60).toInt()
                        PlaylistStatItem(
                            label = stringResource(Res.string.duration),
                            value = stringResource(Res.string.minutes_format, minutes),
                        )
                    }
                    if (meta.playCount > 0) {
                        PlaylistStatItem(
                            label = stringResource(Res.string.play),
                            value = meta.playCount.toString(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(metrics.cozy))

            val description = meta.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = typo.body,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = metrics.compact),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
