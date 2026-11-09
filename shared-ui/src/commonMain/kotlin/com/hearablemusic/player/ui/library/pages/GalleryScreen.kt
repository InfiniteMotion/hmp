package com.hearablemusic.player.ui.library.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.dialogs.PlaylistPickerDialog
import com.hearablemusic.player.ui.common.dialogs.base.ConfirmDialog
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.pages.base.TabScreen
import com.hearablemusic.player.ui.common.util.LoadState
import com.hearablemusic.player.ui.common.util.LocalAppHazeState
import com.hearablemusic.player.ui.common.util.LocalHazeRenderSettings
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.util.commonFormat
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.added_n_songs_to_playlist
import com.hearablemusic.player.ui.generated.resources.confirm_batch_remove_from_library
import com.hearablemusic.player.ui.generated.resources.confirm_remove_from_library
import com.hearablemusic.player.ui.generated.resources.select_playlist
import com.hearablemusic.player.ui.generated.resources.title_gallery
import com.hearablemusic.player.ui.library.pages.components.musiclist.CurrentPlayingConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.EditConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.GalleryItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.HeaderConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemVariant
import com.hearablemusic.player.ui.library.pages.components.musiclist.ListConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.galleryPresetMusicListConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.indexJumpConfigForOrderBy
import com.hearablemusic.player.ui.library.viewmodel.GalleryEffect
import com.hearablemusic.player.ui.library.viewmodel.GalleryViewModel
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistViewModel
import com.hmp.domain.music.MusicInfo
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GalleryScreen(
    // 屏级 VM：列表 / 排序 / 两条确认链都归本屏，entry 随 Tabs 常驻
    galleryViewModel: GalleryViewModel = koinViewModel(),
    // 7.5 P1：旧 VM 是 controller 的零逻辑包装，直接用平台门面（白名单允许）
    playbackController: PlaybackController = koinInject(),
    dialogViewModel: DialogViewModel = activityViewModel(),
    playlistViewModel: PlaylistViewModel = activityViewModel(),
    dialogManagerViewModel: DialogManagerViewModel = activityViewModel(),
    navController: NavBackStack<NavKey>,
) {
    val isPlaying by playbackController.isPlaying.collectAsState()
    val uiState by galleryViewModel.uiState.collectAsState()
    val musicInfoList = uiState.musicList
    val currentPlayingMusic by playbackController.currentPlayingMusic.collectAsState()
    val selectedGenre = uiState.orderBy
    val selectedOrder = uiState.orderType
    val userCustomPlaylistsState by playlistViewModel.userCustomPlaylistsState.collectAsState()
    val userCustomPlaylists = (userCustomPlaylistsState as? LoadState.Success)?.data ?: emptyList()
    val currentPlayingIndex = musicInfoList.indexOfFirst { it.music.id == currentPlayingMusic?.music?.id }.takeIf { it >= 0 }
    val dialogManager = dialogManagerViewModel.dialogManager

    val onPlay: (MusicInfo) -> Unit = galleryViewModel::onPlay
    val addToPlaylist = playbackController::addToPlaylist
    val onFavorite = playbackController::updateMusicLikedStatus
    val onDetail: (MusicInfo) -> Unit = { navController.add(Routes.Library.SongDetail(it.music.id)) }
    val onShufflePlay: () -> Unit = {
        playbackController.addAllToPlaylistByShuffle(musicInfoList)
        navController.add(Routes.Player.Player)
    }
    val onOrderPlay: () -> Unit = {
        playbackController.addAllToPlaylistInOrder(musicInfoList)
        navController.add(Routes.Player.Player)
    }
    val onFilterGenreChange = galleryViewModel::onOrderByChanged
    val onFilterOrderChange = galleryViewModel::onOrderTypeChanged
    val metrics = LocalHmpMetrics.current
    val haptic = rememberHapticFeedback()
    val isLandscape = LocalWindowSizeInfo.current.isLandscape

    val selectPlaylistTitle = stringResource(Res.string.select_playlist)
    val addedNSongsMessageFormat = stringResource(Res.string.added_n_songs_to_playlist)

    // 批量加入歌单完成后由本屏提示（结果不回写 VM 状态）
    LaunchedEffect(Unit) {
        galleryViewModel.effect.collect { effect ->
            when (effect) {
                is GalleryEffect.SongsAdded ->
                    dialogManager.showMessage(commonFormat(addedNSongsMessageFormat, effect.count))
            }
        }
    }

    val callbacks = object : MusicListCallbacksAdapter() {
        override fun onItemClick(musicInfo: MusicInfo, index: Int) {
            haptic.performClick()
            onPlay(musicInfo)
        }
        override fun onMenuClick(musicInfo: MusicInfo) {
            val menuConfig = DialogViewModel.MusicDetailMenuConfig(
                showAddToSpecificPlaylist = true,
                showShare = true,
                showViewDetail = true,
                showPlayNext = true,
                showRemoveFromCurrentPlaylist = false,
                showDelete = true,
            )
            dialogViewModel.showMusicDetailDialog(musicInfo, menuConfig)
        }
        override fun onBatchAddToPlaylist(selectedIds: Set<Long>) {
            galleryViewModel.onBatchAddToPlaylistRequested(selectedIds, userCustomPlaylists)
        }
        override fun onBatchDelete(selectedIds: Set<Long>) {
            if (selectedIds.isEmpty()) return
            galleryViewModel.onBatchRemoveRequested(selectedIds)
        }
    }
    val config = galleryPresetMusicListConfig(callbacks).copy(
        header = HeaderConfig.Full(
            selectedGenre = selectedGenre,
            selectedOrder = selectedOrder,
            onFilterGenreChange = onFilterGenreChange,
            onFilterOrderChange = onFilterOrderChange,
            onOrderPlay = onOrderPlay,
            onShufflePlay = onShufflePlay,
            singleRowFilter = isLandscape,
        ),
        item = ItemConfig(
            showIndex = true,
            showCheckbox = true,
            variant = ItemVariant.Gallery,
            galleryOptions = GalleryItemOptions(
                showPinButton = false,
                showRemoveButton = false,
                showMenuButton = true,
            ),
        ),
        list = ListConfig(
            enableLongPressToEnterEdit = true,
            bottomSpacerHeight = metrics.row,
            columns = if (isLandscape) 2 else 1,
        ),
        edit = EditConfig(enabled = true),
        indexJump = indexJumpConfigForOrderBy(selectedGenre, selectedOrder),
        currentPlaying = CurrentPlayingConfig(
            index = currentPlayingIndex,
            autoScrollToCurrent = false,
        ),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            TabScreen(
                title = stringResource(Res.string.title_gallery),
                onSearchClick = { navController.add(Routes.Library.Search) },
            ) {
                Column {
                    key(uiState.deleteToken) {
                        MusicList(
                            musicInfoList = musicInfoList,
                            config = config,
                            modifier = Modifier.fillMaxSize(),
                            isPlaying = isPlaying,
                        )
                    }
                }
            }
        }
    }

    // 单项移除确认
    ConfirmDialog(
        visible = uiState.pendingRemove != null,
        title = stringResource(Res.string.confirm_remove_from_library),
        message = "",
        onConfirm = galleryViewModel::onRemoveConfirmed,
        onDismiss = galleryViewModel::onRemoveDismissed,
    )

    // 批量移除确认
    uiState.pendingBatchRemove?.let { batchIds ->
        val count = batchIds.size
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.confirm_batch_remove_from_library, count),
            message = "",
            onConfirm = galleryViewModel::onBatchRemoveConfirmed,
            onDismiss = galleryViewModel::onBatchRemoveDismissed,
        )
    }

    uiState.playlistPicker?.let { picker ->
        PlaylistPickerDialog(
            playlists = picker.playlists,
            title = selectPlaylistTitle,
            onDismiss = galleryViewModel::onPlaylistPickerDismissed,
            onSelectPlaylist = { playlist -> galleryViewModel.onPlaylistPicked(playlist.id) },
            hazeState = LocalAppHazeState.current,
            hazeRenderSettings = LocalHazeRenderSettings.current,
        )
    }
}
