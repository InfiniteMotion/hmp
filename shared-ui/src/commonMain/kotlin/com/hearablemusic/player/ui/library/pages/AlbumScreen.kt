package com.hearablemusic.player.ui.library.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.base.PlayControlButtonTwo
import com.hearablemusic.player.ui.common.components.base.UiStateContent
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.UiState
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
import com.hearablemusic.player.ui.player.viewmodel.PlaybackViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.ArtistAlbumViewModel
import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlbumScreen(
    navController: NavBackStack<NavKey>,
    albumName: String,
    artistAlbumViewModel: ArtistAlbumViewModel = koinViewModel(),
    playbackViewModel: PlaybackViewModel = activityViewModel(),
    playlistQueueViewModel: PlaylistQueueViewModel = activityViewModel(),
    dialogViewModel: DialogViewModel = activityViewModel(),
) {
    LaunchedEffect(albumName) {
        artistAlbumViewModel.getSelectedAlbumMusicList(albumName)
    }
    val isPlaying by playbackViewModel.isPlaying.collectAsState()
    val displayAlbumName by artistAlbumViewModel.selectedAlbumName.collectAsState()
    val albumMusicListState by artistAlbumViewModel.selectedAlbumMusicListState.collectAsState()
    val currentPlayingMusic by playlistQueueViewModel.currentPlayingMusic.collectAsState(null)
    AlbumScreenContent(
        isPlaying = isPlaying,
        albumName = displayAlbumName,
        albumMusicListState = albumMusicListState,
        currentPlayingMusic = currentPlayingMusic,
        onBackClick = { navController.removeLastOrNull() },
        onShufflePlay = { musicList ->
            playlistQueueViewModel.addAllToPlaylistByShuffle(musicList)
            navController.add(Routes.Player.Player)
        },
        onOrderPlay = { musicList ->
            playlistQueueViewModel.addAllToPlaylistInOrder(musicList)
            navController.add(Routes.Player.Player)
        },
        playWith = playlistQueueViewModel::playWith,
        addToPlaylist = playlistQueueViewModel::addToPlaylist,
        onShowMusicDetailDialog = dialogViewModel::showMusicDetailDialog,
    )
}

@Composable
fun AlbumScreenContent(
    isPlaying: Boolean,
    albumName: String,
    albumMusicListState: UiState<List<MusicInfo>>,
    currentPlayingMusic: MusicInfo?,
    onBackClick: () -> Unit,
    onShufflePlay: (List<MusicInfo>) -> Unit,
    onOrderPlay: (List<MusicInfo>) -> Unit,
    playWith: suspend (MusicInfo) -> Unit,
    addToPlaylist: (MusicInfo) -> Unit,
    onShowMusicDetailDialog: (MusicInfo) -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    SubScreen(
        onBackClick = onBackClick,
        title = albumName,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth(),
        ) {
            UiStateContent(
                state = albumMusicListState,
                modifier = Modifier.weight(1f),
            ) { albumMusicList ->
                val currentPlayingIndex = albumMusicList.indexOfFirst { it.music.id == currentPlayingMusic?.music?.id }.takeIf { it >= 0 }
                val callbacks = object : MusicListCallbacksAdapter() {
                    override fun onItemClick(musicInfo: MusicInfo, index: Int) {
                        haptic.performClick()
                        scope.launch { playWith(musicInfo) }
                    }
                    override fun onAddToPlaylist(musicInfo: MusicInfo) {
                        addToPlaylist(musicInfo)
                    }
                    override fun onMenuClick(musicInfo: MusicInfo) {
                        onShowMusicDetailDialog(musicInfo)
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
                    onShufflePlay = { onShufflePlay(albumMusicList) },
                    onOrderPlay = { onOrderPlay(albumMusicList) },
                )
                MusicList(
                    musicInfoList = albumMusicList,
                    config = config,
                    modifier = Modifier.fillMaxWidth(),
                    isPlaying = isPlaying,
                )
            }
        }
    }
}
