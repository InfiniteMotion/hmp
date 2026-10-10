package com.hearablemusic.player.ui.playlist.pages

import com.hearablemusic.player.ui.common.navigation.Routes as NavRoutes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.hearablemusic.player.ui.common.util.commonFormat
import com.hearablemusic.player.ui.common.util.activityViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.Playlist
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
import com.hearablemusic.player.ui.generated.resources.plus
import com.hearablemusic.player.ui.generated.resources.rename_playlist
import com.hearablemusic.player.ui.generated.resources.select_playlist
import com.hearablemusic.player.ui.generated.resources.song_added
import com.hearablemusic.player.ui.library.pages.components.AlbumCover
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.playlistPresetMusicListConfig
import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.common.dialogs.base.InputDialog
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.UiState
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaybackViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlaylistScreen(
    navController: NavBackStack<NavKey>,
    playlistId: Long? = null,
    playlistName: String? = null,
    playlistViewModel: PlaylistViewModel = activityViewModel(),
    playbackViewModel: PlaybackViewModel = activityViewModel(),
    playlistQueueViewModel: PlaylistQueueViewModel = activityViewModel(),
    dialogViewModel: DialogViewModel = activityViewModel(),
    dialogManagerViewModel: DialogManagerViewModel = activityViewModel(),
) {
    val dialogManager = dialogManagerViewModel.dialogManager
    LaunchedEffect(playlistId, playlistName) {
        when {
            playlistId != null -> {
                playlistViewModel.loadPlaylistById(playlistId)
            }
            playlistName != null -> {
                playlistViewModel.getSelectedPlaylist(playlistName)
            }
        }
    }
    val isPlaying by playbackViewModel.isPlaying.collectAsState()
    val uiState by playlistViewModel.playlistUiState.collectAsState()
    val playlistState = uiState.playlist
    val playlist = (playlistState as? UiState.Success)?.data ?: emptyList()
    val addedMessage = stringResource(Res.string.song_added)
    val addSongsDialogTitle = stringResource(Res.string.add_songs_to_playlist)

    PlaylistScreenContent(
        isPlaying = isPlaying,
        playlistName = uiState.playlistName,
        playlist = playlist,
        playlistMeta = uiState.playlistMeta,
        selectedPlaylistId = uiState.selectedPlaylistId,
        isCustomPlaylist = uiState.isCustomPlaylist,
        onBackClick = { navController.removeLastOrNull() },
        onShufflePlay = {
            uiState.selectedPlaylistId?.let { playlistViewModel.recordPlaylistPlay(it) }
            playlistQueueViewModel.addAllToPlaylistByShuffle(playlist)
            navController.add(NavRoutes.Player.Player)
        },
        onOrderPlay = {
            uiState.selectedPlaylistId?.let { playlistViewModel.recordPlaylistPlay(it) }
            playlistQueueViewModel.addAllToPlaylistInOrder(playlist)
            navController.add(NavRoutes.Player.Player)
        },
        playWith = playlistQueueViewModel::playWith,
        dialogViewModel = dialogViewModel,
        playlistViewModel = playlistViewModel,
        dialogManager = dialogManager,
        onRenamePlaylist = { id, newName -> playlistViewModel.renamePlaylist(id, newName) },
        onUpdateDescription = { id, desc -> playlistViewModel.updatePlaylistDescription(id, desc) },
        onRemoveFromPlaylist = { musicId, pid -> playlistViewModel.removeItemFromPlaylist(musicId, pid) },
        onReorder = { orderedIds ->
            uiState.selectedPlaylistId?.let { pid ->
                playlistViewModel.reorderPlaylistItems(pid, orderedIds)
            }
        },
        onAddSongsClick = if (uiState.isCustomPlaylist && uiState.selectedPlaylistId != null) {
            {
                val selectedPlaylistId = uiState.selectedPlaylistId!!
                val existingIds = playlist.map { it.music.id }.toSet()
                playlistViewModel.loadAllMusicForAddPicker { allMusic ->
                    val candidates = allMusic.filter { it.music.id !in existingIds }
                    dialogViewModel.showMusicPickerDialog(
                        allMusic = candidates,
                        selectedIds = emptySet(),
                        title = addSongsDialogTitle,
                        onConfirm = { selectedIds ->
                            // 不再按"path 是否为空"静默丢弃曲目（D3-15 收尾）：条目只存 id
                            playlistViewModel.addItemsToPlaylist(
                                playlistId = selectedPlaylistId,
                                musicIds = selectedIds.toList(),
                                onComplete = {
                                    if (selectedIds.isNotEmpty()) {
                                        dialogManager.showMessage(addedMessage)
                                    }
                                }
                            )
                        }
                    )
                }
            }
        } else null
    )
}

private const val HEADER_COVER_SIZE_DP = 280
private const val HEADER_CORNER_RADIUS_DP = 25

@Composable
fun PlaylistScreenContent(
    isPlaying: Boolean,
    playlistName: String,
    playlist: List<MusicInfo>,
    playlistMeta: Playlist?,
    selectedPlaylistId: Long?,
    isCustomPlaylist: Boolean,
    onBackClick: () -> Unit,
    onShufflePlay: () -> Unit,
    onOrderPlay: () -> Unit,
    playWith: suspend (MusicInfo) -> Unit,
    dialogViewModel: DialogViewModel,
    playlistViewModel: PlaylistViewModel,
    dialogManager: DialogManager,
    onRenamePlaylist: (Long, String) -> Unit,
    onUpdateDescription: (Long, String?) -> Unit,
    onRemoveFromPlaylist: (Long, Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onAddSongsClick: (() -> Unit)? = null
) {
    val haptic = rememberHapticFeedback()
    val userCustomPlaylistsState by playlistViewModel.userCustomPlaylistsState.collectAsState()
    val userCustomPlaylists = (userCustomPlaylistsState as? UiState.Success)?.data ?: emptyList()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(playlistName) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var descriptionValue by remember(playlistMeta?.description) {
        mutableStateOf(
            playlistMeta?.description ?: ""
        )
    }
    var isListEditMode by remember { mutableStateOf(false) }
    val musicListState = rememberLazyListState()
    val density = LocalDensity.current
    val maxHeaderCollapsePx = with(density) { 160.dp.toPx() }
    val headerExpandDamping = 0.80f
    var headerCollapseOffsetPx by remember(selectedPlaylistId, playlistName) { mutableFloatStateOf(0f) }
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

    // Keep header in collapsed state whenever the inner list has moved away from top.
    LaunchedEffect(
        isCustomPlaylist,
        musicListState.firstVisibleItemIndex,
        musicListState.firstVisibleItemScrollOffset
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

                // Upward drag: collapse header first, then let MusicList scroll.
                if (available.y < 0f && headerCollapseOffsetPx < maxHeaderCollapsePx) {
                    val consume = minOf(-available.y, maxHeaderCollapsePx - headerCollapseOffsetPx)
                    headerCollapseOffsetPx += consume
                    return Offset(x = 0f, y = -consume)
                }

                // Downward drag: when list at top, expand header first.
                if (available.y > 0f && isMusicListAtTop && headerCollapseOffsetPx > 0f) {
                    // Damping only: keep pull responsive but resistant.
                    val damped = available.y * headerExpandDamping
                    val consume = minOf(damped, headerCollapseOffsetPx)
                    headerCollapseOffsetPx -= consume
                    return Offset(x = 0f, y = consume)
                }

                return Offset.Zero
            }
        }
    }

    // 重命名歌单弹窗
    InputDialog(
        visible = showRenameDialog && selectedPlaylistId != null,
        title = stringResource(Res.string.rename_playlist),
        hint = stringResource(Res.string.playlist_name_hint),
        initialValue = renameValue,
        onConfirm = {
            val name = it.trim()
            if (name.isNotEmpty()) {
                selectedPlaylistId?.let { id ->
                    onRenamePlaylist(id, name)
                }
            }
            showRenameDialog = false
        },
        onDismiss = {
            showRenameDialog = false
        }
    )

    // 编辑歌单描述弹窗
    InputDialog(
        visible = showDescriptionDialog && selectedPlaylistId != null,
        title = stringResource(Res.string.edit_playlist_description),
        hint = stringResource(Res.string.playlist_description_hint),
        initialValue = descriptionValue,
        singleLine = false,
        minLines = 2,
        maxLines = 4,
        onConfirm = { input ->
            selectedPlaylistId?.let {
                onUpdateDescription(
                    it,
                    input.trim().ifEmpty { null }
                )
            }
            showDescriptionDialog = false
        },
        onDismiss = {
            showDescriptionDialog = false
        }
    )

    SubScreen(
        onBackClick = onBackClick,
        title = playlistName,
        trailingContent = if (isCustomPlaylist && playlistMeta != null) {
            {
                FilledIconButton(
                    onClick = {
                        haptic.performClick()
                        dialogViewModel.showEditPlaylistDialog(playlistMeta) { id ->
                            playlistViewModel.loadPlaylistById(id)
                            playlistViewModel.loadUserCustomPlaylists()
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.gearshape),
                        contentDescription = stringResource(Res.string.edit_list),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null
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
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                    exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(150))
                ) {
                    PlaylistHeader(
                        meta = playlistMeta
                    )
                }
            }

            val coroutineScope = rememberCoroutineScope()
            val selectPlaylistTitle = stringResource(Res.string.select_playlist)
            val addedNSongsMessageFormat = stringResource(Res.string.added_n_songs_to_playlist)
            val callbacks = object : MusicListCallbacksAdapter() {
                override fun onEnterEditMode() {
                    isListEditMode = true
                }
                override fun onExitEditMode() {
                    isListEditMode = false
                }
                override fun onItemClick(musicInfo: MusicInfo, index: Int) {
                    haptic.performClick()
                    coroutineScope.launch { playWith(musicInfo) }
                }
                override fun onMenuClick(musicInfo: MusicInfo) {
                    val menuConfig = DialogViewModel.MusicDetailMenuConfig(
                        showAddToSpecificPlaylist = true,
                        showShare = true,
                        showViewDetail = true,
                        showPlayNext = true,
                        showRemoveFromCurrentPlaylist = false,
                        showDelete = false
                    )
                    dialogViewModel.showMusicDetailDialog(musicInfo, menuConfig)
                }
                override fun onRemoveFromPlaylist(musicInfo: MusicInfo) {
                    selectedPlaylistId?.let { pid -> onRemoveFromPlaylist(musicInfo.music.id, pid) }
                }
                override fun onRemove(musicInfo: MusicInfo) {
                    selectedPlaylistId?.let { pid -> onRemoveFromPlaylist(musicInfo.music.id, pid) }
                }
                override fun onBatchAddToPlaylist(selectedIds: Set<Long>) {
                    val selectedMusicList = playlist.filter { it.music.id in selectedIds }
                    if (selectedMusicList.isNotEmpty()) {
                        // 显示播放列表选择弹窗
                        dialogViewModel.showPlaylistPickerDialog(
                            playlists = userCustomPlaylists,
                            title = selectPlaylistTitle,
                            onConfirm = { selectedPlaylist ->
                                // 批量添加歌曲到选择的播放列表
                                playlistViewModel.addItemsToPlaylist(
                                    playlistId = selectedPlaylist.id,
                                    musicIds = selectedMusicList.map { it.music.id },
                                    onComplete = {
                                        dialogManager.showMessage(commonFormat(addedNSongsMessageFormat, selectedMusicList.size))
                                    }
                                )
                            }
                        )
                    }
                }
                override fun onMoveUp(index: Int) {
                    if (index <= 0 || index >= playlist.size) return
                    val ids = playlist.map { it.music.id }.toMutableList()
                    ids[index] = ids[index - 1].also { ids[index - 1] = ids[index] }
                    onReorder(ids)
                }
                override fun onMoveDown(index: Int) {
                    if (index < 0 || index >= playlist.size - 1) return
                    val ids = playlist.map { it.music.id }.toMutableList()
                    ids[index] = ids[index + 1].also { ids[index + 1] = ids[index] }
                    onReorder(ids)
                }
                override fun onPinToTop(musicInfo: MusicInfo) {
                    val ids = listOf(musicInfo.music.id) + playlist.map { it.music.id }.filter { it != musicInfo.music.id }
                    onReorder(ids)
                }
            }
            val addSongsClick = onAddSongsClick
            val baseConfig = playlistPresetMusicListConfig(
                onOrderPlay = onOrderPlay,
                onShufflePlay = onShufflePlay,
                callbacks = callbacks,
                headerTrailing = if (addSongsClick != null) {
                    {
                        IconButton(
                            onClick = {
                                haptic.performClick()
                                addSongsClick()
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.plus),
                                contentDescription = stringResource(Res.string.add_songs_to_playlist),
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                } else null,
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

@Composable
private fun PlaylistStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistHeader(
    meta: Playlist
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cover Image
            Box(
                modifier = Modifier
                    .size(HEADER_COVER_SIZE_DP.dp)
                    .clip(RoundedCornerShape(HEADER_CORNER_RADIUS_DP.dp))
            ) {
                AlbumCover(
                    uri = meta.coverUri,
                    size = HEADER_COVER_SIZE_DP.dp,
                    corner = 25.dp,
                    shadow = 15.dp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Row
            val lastPlayedAt = meta.lastPlayedAt
            if (meta.songCount > 0 || meta.playCount > 0 || meta.totalDurationMs > 0 || lastPlayedAt != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (meta.songCount > 0) {
                        PlaylistStatItem(
                            label = stringResource(Res.string.counts),
                            value = meta.songCount.toString()
                        )
                    }
                    if (meta.totalDurationMs > 0) {
                        val minutes = (meta.totalDurationMs / 1000 / 60).toInt()
                        PlaylistStatItem(
                            label = stringResource(Res.string.duration),
                            value = stringResource(Res.string.minutes_format, minutes)
                        )
                    }
                    if (meta.playCount > 0) {
                        PlaylistStatItem(
                            label = stringResource(Res.string.play),
                            value = meta.playCount.toString()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            val description = meta.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

