package com.hearablemusic.player.ui.library.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.base.LoadStateContent
import com.hearablemusic.player.ui.common.components.base.PlayControlButtonTwo
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.library.pages.components.musiclist.CurrentPlayingConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.EditConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.HeaderConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemVariant
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.defaultMusicListConfig
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.playlist.viewmodel.ArtistAlbumViewModel
import com.hmp.domain.music.MusicInfo
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ArtistScreen(
    navController: NavBackStack<NavKey>,
    artistName: String,
    artistAlbumViewModel: ArtistAlbumViewModel = koinViewModel(),
    // 7.5 P1：旧 VM 是 controller 的零逻辑包装，直接用平台门面（白名单允许）
    playbackController: PlaybackController = koinInject(),
    dialogViewModel: DialogViewModel = activityViewModel(),
) {
    val haptic = rememberHapticFeedback()

    LaunchedEffect(artistName) {
        artistAlbumViewModel.getSelectedArtistMusicList(artistName)
    }
    // 唯一状态出口；播放态与当前曲目是组合根投影白名单
    val uiState by artistAlbumViewModel.artistUiState.collectAsState()
    val isPlaying by playbackController.isPlaying.collectAsState()
    val currentPlayingMusic by playbackController.currentPlayingMusic.collectAsState(null)

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = uiState.title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth(),
        ) {
            LoadStateContent(
                state = uiState.musicList,
                modifier = Modifier.weight(1f),
            ) { artistMusicList ->
                val currentPlayingIndex = artistMusicList.indexOfFirst { it.music.id == currentPlayingMusic?.music?.id }.takeIf { it >= 0 }
                val callbacks = object : MusicListCallbacksAdapter() {
                    override fun onItemClick(musicInfo: MusicInfo, index: Int) {
                        haptic.performClick()
                        artistAlbumViewModel.onPlay(musicInfo)
                    }
                    override fun onAddToPlaylist(musicInfo: MusicInfo) {
                        playbackController.addToPlaylist(musicInfo)
                    }
                    override fun onMenuClick(musicInfo: MusicInfo) {
                        dialogViewModel.showMusicDetailDialog(musicInfo)
                    }
                }
                val config = defaultMusicListConfig(callbacks).copy(
                    header = HeaderConfig.None,
                    item = ItemConfig(
                        variant = ItemVariant.Full,
                        showIndex = true,
                        fullOptions = FullItemOptions(
                            showPinButton = false,
                            showRemoveButton = false,
                            showMenuButton = true,
                            showAddToPlaylistInMenu = true,
                        ),
                    ),
                    edit = EditConfig(enabled = false),
                    currentPlaying = CurrentPlayingConfig(
                        index = currentPlayingIndex,
                        autoScrollToCurrent = false,
                    ),
                )
                PlayControlButtonTwo(
                    onShufflePlay = {
                        playbackController.addAllToPlaylistByShuffle(artistMusicList)
                        navController.add(Routes.Player.Player)
                    },
                    onOrderPlay = {
                        playbackController.addAllToPlaylistInOrder(artistMusicList)
                        navController.add(Routes.Player.Player)
                    },
                )
                MusicList(
                    musicInfoList = artistMusicList,
                    config = config,
                    modifier = Modifier.fillMaxWidth(),
                    isPlaying = isPlaying,
                )
            }
        }
    }
}
