package com.hearablemusic.player.ui.library.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass
import com.hearablemusic.player.ui.common.pages.base.TabScreen
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.generating_heartbeat_playlist
import com.hearablemusic.player.ui.generated.resources.go_to_ai_config
import com.hearablemusic.player.ui.generated.resources.media_center
import com.hearablemusic.player.ui.generated.resources.music_note_list
import com.hearablemusic.player.ui.generated.resources.no_data_loaded
import com.hearablemusic.player.ui.generated.resources.play_desc
import com.hearablemusic.player.ui.generated.resources.play_heartbeat_playlist
import com.hearablemusic.player.ui.generated.resources.player_d
import com.hearablemusic.player.ui.generated.resources.processing_ai_recommendation
import com.hearablemusic.player.ui.generated.resources.refresh_daily_recommendation
import com.hearablemusic.player.ui.generated.resources.relaunch_or_config_ai
import com.hearablemusic.player.ui.generated.resources.title_home
import com.hearablemusic.player.ui.generated.resources.today_heartbeat_playlist
import com.hearablemusic.player.ui.generated.resources.today_recommendation
import com.hearablemusic.player.ui.library.pages.components.musiclist.CurrentPlayingConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.EditConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.FixedMusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.HeaderConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemVariant
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.defaultMusicListConfig
import com.hearablemusic.player.ui.player.viewmodel.PlaybackViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import com.hearablemusic.player.ui.settings.viewmodel.RecommendationViewModel
import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.launch
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.navigation.Routes as NavRoutes
import com.hmp.domain.agent.persona.DefaultCompanionProfiles
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(
    recommendationViewModel: RecommendationViewModel = activityViewModel(),
    playbackViewModel: PlaybackViewModel = activityViewModel(),
    playlistQueueViewModel: PlaylistQueueViewModel = activityViewModel(),
    dialogViewModel: DialogViewModel = activityViewModel(),
    navController: NavBackStack<NavKey>
) {
    val scope = rememberCoroutineScope()
    val dailyMusic by recommendationViewModel.dailyMusic.collectAsState(null)
    val isProcessingExtraInfo by recommendationViewModel.isProcessingExtraInfo.collectAsState()
    val currentPlayingMusic by playlistQueueViewModel.currentPlayingMusic.collectAsState(null)
    val haptic = rememberHapticFeedback()
    val isPlaying by playbackViewModel.isPlaying.collectAsState()
    val isLandscape = LocalWindowSizeInfo.current.isLandscape
    val sizeClass = LocalWindowSizeInfo.current.widthSizeClass

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            TabScreen(
                title = stringResource(Res.string.title_home),
                trailing = {
                    IconButton(
                        onClick = {
                            haptic.performClick()
                            recommendationViewModel.refreshDailyMusicInfo()
                        }
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.player_d),
                            contentDescription = stringResource(Res.string.refresh_daily_recommendation),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            ) {
                if (dailyMusic == null && !isProcessingExtraInfo) {
                    // ── 空状态：无数据且未在补全 ──
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.no_data_loaded),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(Res.string.relaunch_or_config_ai),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { navController.add(NavRoutes.AI.AI) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(Res.string.go_to_ai_config))
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    } // Box
                } else if (dailyMusic == null && isProcessingExtraInfo) {
                    // ── 处理中状态：AI 正在补全音乐信息 ──
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.processing_ai_recommendation),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator()
                        }
                    }
                } else if (isLandscape) {
                    // ── Expanded: 横向布局 ──
                    key(dailyMusic!!.music.id) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            // 左侧：标题 + HeroCard
                            Column(modifier = Modifier.weight(0.4f)) {
                                Text(
                                    text = stringResource(Res.string.today_recommendation),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                                DailyHeroCard(
                                    music = dailyMusic!!,
                                    onPlay = {
                                        haptic.performClick()
                                        scope.launch {
                                            playlistQueueViewModel.playWith(dailyMusic!!)
                                            navController.add(NavRoutes.Player.Player)
                                        }
                                    },
                                    onDetail = {
                                        haptic.performClick()
                                        navController.add(NavRoutes.Library.SongDetail(dailyMusic!!.music.id))
                                    },
                                    sizeClass = sizeClass,
                                )
                            }
                            // 右侧：标题 + 推荐列表
                            Column(modifier = Modifier.weight(0.6f)) {
                                HeartbeatSection(
                                    recommendationViewModel = recommendationViewModel,
                                    playlistQueueViewModel = playlistQueueViewModel,
                                    dialogViewModel = dialogViewModel,
                                    currentPlayingMusic = currentPlayingMusic,
                                    isPlaying = isPlaying,
                                    haptic = haptic,
                                    scope = scope,
                                    navController = navController
                                )
                            }
                        }
                    }
                } else {
                    // ── Compact / Medium: 纵向堆叠 ──
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp)
                    ) {
                        // 门面二期：问候区 + 对话触发钮（「和伙伴聊聊」→ 独立对话页）
                        CompanionGreetingSection(navController)
                        Text(
                            text = stringResource(Res.string.today_recommendation),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        DailyHeroCard(
                            music = dailyMusic!!,
                            onPlay = {
                                haptic.performClick()
                                scope.launch {
                                    playlistQueueViewModel.playWith(dailyMusic!!)
                                    navController.add(NavRoutes.Player.Player)
                                }
                            },
                            onDetail = {
                                haptic.performClick()
                                navController.add(NavRoutes.Library.SongDetail(dailyMusic!!.music.id))
                            },
                            sizeClass = sizeClass,
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        HeartbeatSection(
                            recommendationViewModel = recommendationViewModel,
                            playlistQueueViewModel = playlistQueueViewModel,
                            dialogViewModel = dialogViewModel,
                            currentPlayingMusic = currentPlayingMusic,
                            isPlaying = isPlaying,
                            haptic = haptic,
                            scope = scope,
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartbeatSection(
    recommendationViewModel: RecommendationViewModel,
    playlistQueueViewModel: PlaylistQueueViewModel,
    dialogViewModel: DialogViewModel,
    currentPlayingMusic: MusicInfo?,
    isPlaying: Boolean,
    haptic: com.hearablemusic.player.ui.common.util.HapticFeedbackHelper,
    scope: kotlinx.coroutines.CoroutineScope,
    navController: NavBackStack<NavKey>
) {
    val heartbeatList by recommendationViewModel.heartbeatList.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.today_heartbeat_playlist),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        if (heartbeatList.isNotEmpty()) {
            FilledIconButton(
                onClick = {
                    playlistQueueViewModel.clearPlaylist()
                    playlistQueueViewModel.addAllToPlaylistInOrder(heartbeatList)
                    playlistQueueViewModel.playWith(heartbeatList.first())
                    navController.add(NavRoutes.Player.Player)
                },
                modifier = Modifier.size(24.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.music_note_list),
                    contentDescription = stringResource(Res.string.play_heartbeat_playlist),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (heartbeatList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.generating_heartbeat_playlist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val currentPlayingIndex = heartbeatList.indexOfFirst {
            it.music.id == currentPlayingMusic?.music?.id
        }.takeIf { it >= 0 }
        val callbacks = object : MusicListCallbacksAdapter() {
            override fun onItemClick(musicInfo: MusicInfo, index: Int) {
                haptic.performClick()
                scope.launch {
                    playlistQueueViewModel.playWith(musicInfo)
                }
            }
            override fun onMenuClick(musicInfo: MusicInfo) {
                val menuConfig = DialogViewModel.MusicDetailMenuConfig(
                    showAddToSpecificPlaylist = true,
                    showShare = true,
                    showViewDetail = true,
                    showPlayNext = false,
                    showRemoveFromCurrentPlaylist = false,
                    showDelete = false
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
                    showMenuButton = true
                ),
            ),
            edit = EditConfig(enabled = false),
            currentPlaying = CurrentPlayingConfig(
                index = currentPlayingIndex,
                autoScrollToCurrent = false
            ),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            FixedMusicList(
                musicInfoList = heartbeatList,
                config = config,
                modifier = Modifier.fillMaxWidth(),
                isPlaying = isPlaying,
            )
        }
    }
}



/** 门面二期：伙伴问候区 + 「和伙伴聊聊」触发钮（总纲 5.2.3）。 */
@Composable
private fun CompanionGreetingSection(navController: NavBackStack<NavKey>) {
    val profile = DefaultCompanionProfiles.DEFAULT
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = profile.greeting,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { navController.add(NavRoutes.Companion.Chat) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("和伙伴聊聊", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun DailyHeroCard(
    music: MusicInfo,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    sizeClass: WindowWidthSizeClass,
) {
    val dimens = LocalHMPDimens.current
    val aspectRatioMod = when (sizeClass) {
        WindowWidthSizeClass.Compact -> Modifier.aspectRatio(1f)
        WindowWidthSizeClass.Medium -> Modifier.aspectRatio(2f)
        WindowWidthSizeClass.Expanded -> Modifier
    }
    Card(
        modifier = Modifier
            .padding(horizontal = dimens.spacing.xl, vertical = dimens.spacing.md)
            .fillMaxWidth()
            .then(aspectRatioMod)
            .clickable(onClick = onDetail),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = music.music.albumArtUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimens.spacing.lg)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        text = music.music.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = music.music.artist,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledIconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(dimens.component.xs),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.media_center),
                        contentDescription = stringResource(Res.string.play_desc),
                        modifier = Modifier.size(dimens.icon.lg)
                    )
                }
            }
        }
    }
}