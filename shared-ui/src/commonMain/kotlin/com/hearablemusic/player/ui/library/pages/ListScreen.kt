package com.hearablemusic.player.ui.library.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.hearablemusic.player.ui.common.components.Capsule
import com.hearablemusic.player.ui.common.components.SharedLabelIcon
import com.hearablemusic.player.ui.common.components.base.NewPlaylistButton
import com.hearablemusic.player.ui.common.design.colors.ColorTokens
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.pages.base.TabScreen
import com.hearablemusic.player.ui.common.util.UiState
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.util.iconRes
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.album_art_desc
import com.hearablemusic.player.ui.generated.resources.banner_daily_B
import com.hearablemusic.player.ui.generated.resources.banner_daily_BB
import com.hearablemusic.player.ui.generated.resources.banner_daily_C
import com.hearablemusic.player.ui.generated.resources.banner_daily_CC
import com.hearablemusic.player.ui.generated.resources.banner_daily_D
import com.hearablemusic.player.ui.generated.resources.banner_daily_DD
import com.hearablemusic.player.ui.generated.resources.banner_daily_GG
import com.hearablemusic.player.ui.generated.resources.explore
import com.hearablemusic.player.ui.generated.resources.manage_playlists
import com.hearablemusic.player.ui.generated.resources.minutes_format
import com.hearablemusic.player.ui.generated.resources.more
import com.hearablemusic.player.ui.generated.resources.music_note_list
import com.hearablemusic.player.ui.generated.resources.play_count_display
import com.hearablemusic.player.ui.generated.resources.songs_count
import com.hearablemusic.player.ui.generated.resources.suitable_for_now
import com.hearablemusic.player.ui.generated.resources.title_playlist
import com.hearablemusic.player.ui.generated.resources.user_custom_playlists
import com.hearablemusic.player.ui.library.pages.components.ListGroupName
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistViewModel
import com.hmp.domain.enum.LabelName
import com.hmp.domain.playlist.Playlist
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** HD 强调色板（统一定义在 ColorTokens）。 */
private val HDGreen = ColorTokens.HDGreen
private val HDBlue = ColorTokens.HDBlue
private val HDPurple = ColorTokens.HDPurple
private val HDOrange = ColorTokens.HDOrange

@Composable
fun ListScreen(
    playlistViewModel: PlaylistViewModel = activityViewModel(),
    dialogViewModel: DialogViewModel = activityViewModel(),
    navController: NavBackStack<NavKey>,
) {
    val genreList by playlistViewModel.genrePlaylistName.collectAsState()
    val moodList by playlistViewModel.moodPlaylistName.collectAsState()
    val scenarioList by playlistViewModel.scenarioPlaylistName.collectAsState()
    val languageList by playlistViewModel.languagePlaylistName.collectAsState()
    val eraList by playlistViewModel.eraPlaylistName.collectAsState()
    val userCustomPlaylistsState by playlistViewModel.userCustomPlaylistsState.collectAsState()
    val userCustomPlaylists = (userCustomPlaylistsState as? UiState.Success)?.data ?: emptyList()

    ListScreenContent(
        genreList = genreList,
        moodList = moodList,
        scenarioList = scenarioList,
        languageList = languageList,
        eraList = eraList,
        userCustomPlaylists = userCustomPlaylists,
        playlistViewModel = playlistViewModel,
        dialogViewModel = dialogViewModel,
        navController = navController,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListScreenContent(
    genreList: List<LabelName>,
    moodList: List<LabelName>,
    scenarioList: List<LabelName>,
    languageList: List<LabelName>,
    eraList: List<LabelName>,
    userCustomPlaylists: List<Playlist>,
    playlistViewModel: PlaylistViewModel,
    dialogViewModel: DialogViewModel,
    navController: NavBackStack<NavKey>,
) {
    val metrics = LocalHmpMetrics.current
    val haptic = rememberHapticFeedback()
    val sizeClass = LocalWindowSizeInfo.current.widthSizeClass
    val isLandscape = LocalWindowSizeInfo.current.isLandscape
    val typo = LocalHmpTypo.current
    val contentHorizontalPadding = when (sizeClass) {
        WindowWidthSizeClass.Expanded -> metrics.block
        WindowWidthSizeClass.Medium -> metrics.section
        WindowWidthSizeClass.Compact -> metrics.cozy
    }

    // 与列表管理页一致：置顶优先，再按最近播放、更新时间
    val sortedUserPlaylists = remember(userCustomPlaylists) {
        userCustomPlaylists.sortedWith(
            compareByDescending<Playlist> { it.isPinned }
                .thenByDescending { it.lastPlayedAt ?: 0L }
                .thenByDescending { it.updatedAt },
        )
    }

    TabScreen(
        title = stringResource(Res.string.title_playlist),
        hasSearchBotton = false,
        navController = navController,
        trailing = {
            NewPlaylistButton(
                onClick = {
                    dialogViewModel.showCreatePlaylistDialog { id ->
                        playlistViewModel.loadUserCustomPlaylists()
                        navController.add(Routes.Playlist.CustomPlaylist(id))
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.section),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 用户自定义播放列表
            Column {
                ListGroupName(
                    bannerNameF = stringResource(Res.string.user_custom_playlists),
                    bannerNameS = stringResource(Res.string.banner_daily_GG),
                    themeColor = HDPurple,
                    trailing = {
                        TextButton(
                            onClick = {
                                haptic.performClick()
                                navController.add(Routes.Playlist.UserPlaylistManage)
                            },
                        ) {
                            Text(
                                text = stringResource(Res.string.manage_playlists),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = contentHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
                ) {
                    items(sortedUserPlaylists) { playlist ->
                        UserListCard(
                            playlist = playlist,
                            onClick = {
                                haptic.performClick()
                                navController.add(Routes.Playlist.CustomPlaylist(playlist.id))
                            },
                            sizeClass = sizeClass,
                        )
                    }
                }
            }

            // 适用场景 (Scenario) - 沉浸推荐
            LabelListGroup(
                data = scenarioList,
                bannerNameF = stringResource(Res.string.banner_daily_D),
                bannerNameS = stringResource(Res.string.banner_daily_DD),
                themeColor = HDGreen,
            ) { list ->
                val scenarioListState = rememberLazyListState()
                val scenarioFlingBehavior = rememberSnapFlingBehavior(lazyListState = scenarioListState)

                LazyRow(
                    state = scenarioListState,
                    flingBehavior = scenarioFlingBehavior,
                    contentPadding = PaddingValues(horizontal = contentHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
                ) {
                    items(list) { label ->
                        ScenarioCard(
                            label = label,
                            onClick = {
                                haptic.performClick()
                                navController.add(Routes.Playlist.Playlist(label.name))
                            },
                            sizeClass = sizeClass,
                        )
                    }
                }
            }

            // 风格流派 (Genre) - 横向画廊
            LabelListGroup(
                data = genreList,
                bannerNameF = stringResource(Res.string.banner_daily_B),
                bannerNameS = stringResource(Res.string.banner_daily_BB),
                themeColor = HDBlue,
            ) { list ->
                val genreListState = rememberLazyListState()
                val genreFlingBehavior = rememberSnapFlingBehavior(lazyListState = genreListState)

                LazyRow(
                    state = genreListState,
                    flingBehavior = genreFlingBehavior,
                    contentPadding = PaddingValues(horizontal = contentHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
                ) {
                    items(list) { label ->
                        GenreCard(
                            label = label,
                            onClick = {
                                haptic.performClick()
                                navController.add(Routes.Playlist.Playlist(label.name))
                            },
                        )
                    }
                }
            }

            // 音乐情绪 (Mood) - 网格探索
            LabelListGroup(
                data = moodList,
                bannerNameF = stringResource(Res.string.banner_daily_C),
                bannerNameS = stringResource(Res.string.banner_daily_CC),
                themeColor = HDOrange,
            ) { list ->
                Box(modifier = Modifier.height(metrics.card)) {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = contentHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
                        verticalArrangement = Arrangement.spacedBy(metrics.cozy),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(list) { label ->
                            MoodCard(
                                label = label,
                                onClick = {
                                    haptic.performClick()
                                    navController.add(Routes.Playlist.Playlist(label.name))
                                },
                            )
                        }
                    }
                }
            }

            // 探索更多 (Language & Era) - 标签云
            LabelListGroup(
                data = languageList + eraList,
                bannerNameF = stringResource(Res.string.explore),
                bannerNameS = stringResource(Res.string.more),
                themeColor = HDPurple,
            ) { list ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
                    verticalArrangement = Arrangement.spacedBy(metrics.cozy),
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = contentHorizontalPadding),
                ) {
                    list.forEach { label ->
                        Box(
                            modifier = Modifier.clickable {
                                haptic.performClick()
                                navController.add(Routes.Playlist.Playlist(label.name))
                            },
                        ) {
                            Capsule(
                                text = label.name,
                                style = typo.body.medium(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(metrics.row))
        }
    }
}

// ================== 局部组件定义 ==================

@Composable
fun <T> LabelListGroup(
    data: List<T>,
    bannerNameF: String,
    bannerNameS: String,
    themeColor: Color,
    content: @Composable (List<T>) -> Unit,
) {
    if (data.isNotEmpty()) {
        Column {
            ListGroupName(
                bannerNameF = bannerNameF,
                bannerNameS = bannerNameS,
                themeColor = themeColor,
            )
            content(data)
        }
    }
}

@Composable
private fun GenreCard(
    label: LabelName,
    onClick: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Card(
        modifier = Modifier
            .width(metrics.card)
            .height(metrics.panel)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(metrics.cozy),
        elevation = CardDefaults.cardElevation(defaultElevation = metrics.micro),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景图
            SharedLabelIcon(
                iconRes = label.iconRes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Transparent,
                                Color.Black.copy(alpha = 0.7f),
                            ),
                        ),
                    ),
            )
            // 文字
            Text(
                text = label.name,
                style = typo.subtitle.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(metrics.cozy),
            )
        }
    }
}

@Composable
private fun MoodCard(
    label: LabelName,
    onClick: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    Card(
        modifier = Modifier
            .size(metrics.panel)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(metrics.cozy),
        elevation = CardDefaults.cardElevation(defaultElevation = metrics.micro),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SharedLabelIcon(
                iconRes = label.iconRes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ScenarioCard(
    label: LabelName,
    onClick: () -> Unit,
    sizeClass: WindowWidthSizeClass,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Card(
        modifier = Modifier
            .width(metrics.hero)
            .height(metrics.card)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(metrics.cozy),
        elevation = CardDefaults.cardElevation(defaultElevation = metrics.micro),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SharedLabelIcon(
                iconRes = label.iconRes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Transparent,
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(metrics.cozy),
            ) {
                Text(
                    text = label.name,
                    style = typo.subtitle.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(metrics.micro))
                Text(
                    text = stringResource(Res.string.suitable_for_now),
                    style = typo.footnote,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
fun UserListCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeClass: WindowWidthSizeClass,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Card(
        modifier = modifier
            .width(metrics.hero)
            .height(metrics.hero)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(metrics.cozy),
        elevation = CardDefaults.cardElevation(defaultElevation = metrics.micro),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (playlist.coverUri != null && playlist.coverUri!!.isNotBlank()) {
                AsyncImage(
                    model = playlist.coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.music_note_list),
                        contentDescription = null,
                        modifier = Modifier
                            .size(metrics.panel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Transparent,
                                0.5f to Transparent,
                                1.0f to Color.Black.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(metrics.cozy),
            ) {
                Text(
                    text = playlist.name,
                    style = typo.subtitle.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playlist.songCount > 0 || playlist.playCount > 0 || playlist.totalDurationMs > 0) {
                    val parts = mutableListOf<String>()
                    if (playlist.songCount > 0) {
                        parts.add(stringResource(Res.string.songs_count, playlist.songCount))
                    }
                    if (playlist.totalDurationMs > 0) {
                        val minutes = (playlist.totalDurationMs / 1000 / 60).toInt()
                        parts.add(stringResource(Res.string.minutes_format, minutes))
                    }
                    if (playlist.playCount > 0 && parts.size < 2) {
                        parts.add(stringResource(Res.string.play_count_display, playlist.playCount))
                    }
                    if (parts.isNotEmpty()) {
                        Text(
                            text = parts.take(2).joinToString(" · "),
                            style = typo.footnote,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = metrics.micro),
                        )
                    }
                }
            }
        }
    }
}
