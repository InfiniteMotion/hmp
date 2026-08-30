package com.hearablemusic.player.ui.library.pages

import com.hearablemusic.player.ui.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import com.hearablemusic.player.ui.generated.resources.magnifyingglass
import com.hearablemusic.player.ui.generated.resources.no_results_found
import com.hearablemusic.player.ui.generated.resources.search
import com.hearablemusic.player.ui.generated.resources.search_hint
import com.hearablemusic.player.ui.generated.resources.search_placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import com.hearablemusic.player.ui.chat.ChatEntryBroker
import com.hearablemusic.player.ui.common.navigation.Routes as NavRoutes
import com.hmp.domain.agent.funnel.CommandLexicon
import com.hmp.domain.agent.funnel.FunnelResult
import com.hearablemusic.player.ui.common.util.activityViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.hmp.domain.music.MusicInfo
import com.hearablemusic.player.ui.common.components.base.UiStateContent
import com.hearablemusic.player.ui.library.pages.components.musiclist.CurrentPlayingConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.EditConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.HeaderConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemVariant
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.defaultMusicListConfig
import kotlinx.coroutines.launch
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.common.util.UiState
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaybackViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import com.hearablemusic.player.ui.library.viewmodel.SearchViewModel

import com.hearablemusic.player.ui.common.pages.base.SubScreen

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = koinViewModel(),
    playbackViewModel: PlaybackViewModel = activityViewModel(),
    playlistQueueViewModel: PlaylistQueueViewModel = activityViewModel(),
    dialogViewModel: DialogViewModel = activityViewModel(),
    navController: NavBackStack<NavKey>,
    chatEntryBroker: ChatEntryBroker = koinInject(),
){
    val isPlaying by playbackViewModel.isPlaying.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchState by searchViewModel.searchState.collectAsState()

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            searchViewModel.searchMusic(searchQuery)
        }
    }

    val currentPlayingMusic by playlistQueueViewModel.currentPlayingMusic.collectAsState(null)
    SearchScreenContent(
        isPlaying = isPlaying,
        searchQuery = searchQuery,
        searchState = searchState,
        currentPlayingMusic = currentPlayingMusic,
        onSearchQueryChange = {
            searchQuery = it
            if (it.isNotEmpty()) {
                searchViewModel.searchMusic(it)
            }
        },
        onBackClick = { navController.removeLastOrNull() },
        playWith = playlistQueueViewModel::playWith,
        addToPlaylist = playlistQueueViewModel::addToPlaylist,
        onShowMusicDetailDialog = { musicInfo ->
            val menuConfig = DialogViewModel.MusicDetailMenuConfig(
                showAddToSpecificPlaylist = true,
                showShare = true,
                showViewDetail = true,
                showPlayNext = true,
                showRemoveFromCurrentPlaylist = false,
                showDelete = false
            )
            dialogViewModel.showMusicDetailDialog(musicInfo, menuConfig)
        },
        onRetry = { searchViewModel.searchMusic(searchQuery) },
        onTalkToCompanion = { q ->
            chatEntryBroker.pendingInput.value = q
            navController.add(NavRoutes.Companion.Chat)
        },
    )
}

@Composable
fun SearchScreenContent(
    isPlaying: Boolean,
    searchQuery: String,
    searchState: UiState<List<MusicInfo>>,
    currentPlayingMusic: MusicInfo?,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    playWith: suspend (MusicInfo) -> Unit,
    addToPlaylist: (MusicInfo) -> Unit,
    onShowMusicDetailDialog: (MusicInfo) -> Unit,
    onRetry: () -> Unit,
    onTalkToCompanion: (String) -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    // 拒绝纪律：用户点「只是搜索」后，本次会话同类输入不再弹伙伴条带（总纲 5.3 厚度3）
    var showIntentStrip by rememberSaveable { mutableStateOf(true) }
    val isIntent = searchQuery.isNotEmpty() &&
        showIntentStrip &&
        CommandLexicon.classify(searchQuery) is FunnelResult.Upgrade
    SubScreen(
        onBackClick = onBackClick,
        title = stringResource(Res.string.search)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = {
                    Text(
                        stringResource(Res.string.search_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.magnifyingglass),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(Res.string.search)
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Transparent,
                    unfocusedIndicatorColor = Transparent,
                    disabledIndicatorColor = Transparent
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            // 两级漏斗第一级未命中 + 意图特征 → 伙伴条带（交给伙伴 / 只是搜索）
            if (isIntent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "听起来像想让伙伴帮你",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onTalkToCompanion(searchQuery) }) {
                        Text("交给伙伴", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { showIntentStrip = false }) {
                        Text("只是搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (searchQuery.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.magnifyingglass),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(Res.string.search),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = stringResource(Res.string.search_placeholder),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = stringResource(Res.string.search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                UiStateContent(
                    state = searchState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onEmpty = {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.no_results_found),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { onTalkToCompanion(searchQuery) }) {
                                Text("问问伙伴？", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                ) { searchResults ->
                val currentPlayingIndex = searchResults.indexOfFirst { it.music.id == currentPlayingMusic?.music?.id }.takeIf { it >= 0 }
                val callbacks = object : MusicListCallbacksAdapter() {
                    override fun onItemClick(musicInfo: MusicInfo, index: Int) {
                        haptic.performClick()
                        scope.launch { playWith(musicInfo) }
                    }
                    override fun onAddToPlaylist(musicInfo: MusicInfo) { addToPlaylist(musicInfo) }
                    override fun onMenuClick(musicInfo: MusicInfo) { onShowMusicDetailDialog(musicInfo) }
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
                            showAddToPlaylistInMenu = false,
                        ),
                    ),
                    edit = EditConfig(enabled = false),
                    currentPlaying = CurrentPlayingConfig(
                        index = currentPlayingIndex,
                        autoScrollToCurrent = false
                    ),
                )
                    MusicList(
                        musicInfoList = searchResults,
                        config = config,
                        modifier = Modifier.fillMaxWidth(),
                        isPlaying = isPlaying,
                    )
            }
            }
        }
    }
}