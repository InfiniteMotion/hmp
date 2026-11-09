package com.hearablemusic.player.ui.library.pages
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.agent.chat.ChatEntryBroker
import com.hearablemusic.player.ui.common.components.base.HMPTextField
import com.hearablemusic.player.ui.common.components.base.LoadStateContent
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.magnifyingglass
import com.hearablemusic.player.ui.generated.resources.no_results_found
import com.hearablemusic.player.ui.generated.resources.search
import com.hearablemusic.player.ui.generated.resources.search_empty_ask
import com.hearablemusic.player.ui.generated.resources.search_hint
import com.hearablemusic.player.ui.generated.resources.search_intent_ask
import com.hearablemusic.player.ui.generated.resources.search_intent_hint
import com.hearablemusic.player.ui.generated.resources.search_intent_just_search
import com.hearablemusic.player.ui.generated.resources.search_placeholder
import com.hearablemusic.player.ui.library.pages.components.musiclist.CurrentPlayingConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.EditConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.HeaderConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemVariant
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.defaultMusicListConfig
import com.hearablemusic.player.ui.library.viewmodel.SearchEffect
import com.hearablemusic.player.ui.library.viewmodel.SearchViewModel
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.music.MusicInfo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.hearablemusic.player.ui.common.navigation.Routes as NavRoutes

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = koinViewModel(),
    // 7.5 P1：旧 VM 是 controller 的零逻辑包装，直接用平台门面（白名单允许）
    playbackController: PlaybackController = koinInject(),
    dialogViewModel: DialogViewModel = activityViewModel(),
    navController: NavBackStack<NavKey>,
    chatEntryBroker: ChatEntryBroker = koinInject(),
) {
    val metrics = LocalHmpMetrics.current
    val haptic = rememberHapticFeedback()
    val typo = LocalHmpTypo.current

    // 唯一状态出口：输入框、伙伴条带偏好与异步结果都在这里
    val uiState by searchViewModel.uiState.collectAsState()
    // 跨屏共享的播放态属于组合根投影白名单，不并进本屏状态
    val isPlaying by playbackController.isPlaying.collectAsState()
    val currentPlayingMusic by playbackController.currentPlayingMusic.collectAsState(null)

    // 一次性效果：交给伙伴的导航由组合点消费，VM 不持有 NavBackStack / ChatEntryBroker
    LaunchedEffect(Unit) {
        searchViewModel.effect.collect { effect ->
            when (effect) {
                is SearchEffect.TalkToCompanion -> {
                    chatEntryBroker.pendingInput.value = effect.query
                    navController.add(NavRoutes.Companion.Chat)
                }
            }
        }
    }

    // 两级漏斗第一级未命中 + 意图特征 → 伙伴条带（交给伙伴 / 只是搜索）
    // 拒绝纪律：用户点「只是搜索」后，本次会话同类输入不再弹（总纲 5.3 厚度3）
    val searchQuery = uiState.query
    val isIntent = uiState.isIntent && uiState.showIntentStrip

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = stringResource(Res.string.search),
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(top = metrics.cozy),
            verticalArrangement = Arrangement.spacedBy(metrics.section),
        ) {
            HMPTextField(
                value = searchQuery,
                onValueChange = searchViewModel::onQueryChange,
                placeholder = stringResource(Res.string.search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.cozy),
                leadingContent = {
                    Icon(
                        painter = painterResource(Res.drawable.magnifyingglass),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(Res.string.search),
                        modifier = Modifier.padding(end = metrics.compact).size(metrics.cozy),
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
            )
            if (isIntent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = metrics.cozy),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.compact),
                ) {
                    Text(
                        text = stringResource(Res.string.search_intent_hint),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { searchViewModel.onTalkToCompanion(searchQuery) }) {
                        Text(stringResource(Res.string.search_intent_ask), color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = searchViewModel::onDismissIntentStrip) {
                        Text(stringResource(Res.string.search_intent_just_search), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (searchQuery.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.magnifyingglass),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(Res.string.search),
                        modifier = Modifier.size(metrics.row),
                    )
                    Text(
                        text = stringResource(Res.string.search_placeholder),
                        style = typo.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = metrics.cozy),
                    )
                    Text(
                        text = stringResource(Res.string.search_hint),
                        style = typo.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = metrics.compact, start = metrics.block, end = metrics.block),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LoadStateContent(
                    state = uiState.results,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onEmpty = {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.no_results_found),
                                style = typo.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { searchViewModel.onTalkToCompanion(searchQuery) }) {
                                Text(stringResource(Res.string.search_empty_ask), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                ) { searchResults ->
                    val currentPlayingIndex = searchResults.indexOfFirst { it.music.id == currentPlayingMusic?.music?.id }.takeIf { it >= 0 }
                    val callbacks = object : MusicListCallbacksAdapter() {
                        override fun onItemClick(musicInfo: MusicInfo, index: Int) {
                            haptic.performClick()
                            searchViewModel.onPlay(musicInfo)
                        }
                        override fun onAddToPlaylist(musicInfo: MusicInfo) {
                            playbackController.addToPlaylist(musicInfo)
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
                            autoScrollToCurrent = false,
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
