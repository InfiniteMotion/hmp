package com.hearablemusic.player.ui.library.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.pages.base.TabScreen
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.magnifyingglass
import com.hearablemusic.player.ui.generated.resources.music_note_list
import com.hearablemusic.player.ui.generated.resources.search_placeholder
import com.hearablemusic.player.ui.library.pages.components.FeatureEntryRow
import com.hearablemusic.player.ui.library.pages.components.HelloSlideCardStack
import com.hearablemusic.player.ui.library.pages.components.PlaylistEntryCard
import com.hearablemusic.player.ui.library.pages.components.RadioCard
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import com.hearablemusic.player.ui.settings.viewmodel.RecommendationViewModel
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.navigation.Routes as NavRoutes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(
    recommendationViewModel: RecommendationViewModel = activityViewModel(),
    playlistQueueViewModel: PlaylistQueueViewModel = activityViewModel(),
    navController: NavBackStack<NavKey>
) {
    val isLandscape = LocalWindowSizeInfo.current.isLandscape
    val heartbeatList by recommendationViewModel.heartbeatList.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            TabScreen(showHeader = false) {
                if (isLandscape) {
                    // ═══════════════════════════════════════
                    // Expanded 横向布局 — 左右两栏
                    // 左栏：HelloSlideCardStack（独立展示区）
                    // 右栏：搜索框 + 区域② + 区域③
                    // ═══════════════════════════════════════
                    Row(
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        // ── 左栏：HelloSlideCardStack ──
                        HelloSlideCardStack(
                            modifier = Modifier
                                .weight(0.8f)
                                .fillMaxHeight().padding(bottom = 80.dp),
                        )

                        // ── 右栏：搜索框 + 推荐 + 探索 ──
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // 搜索框
                            HomeSearchBar(onClick = { navController.add(NavRoutes.Library.Search) })
                            Spacer(modifier = Modifier.height(32.dp))

                            // 区域② 为你推荐
                            Text(
                                text = "为你推荐",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().height(320.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                RadioCard(
                                    modifier = Modifier.fillMaxHeight(),
                                )
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    PlaylistEntryCard(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        icon = Res.drawable.music_note_list,
                                        title = "🎵 今日推荐",
                                        subtitle = "AI 为你精选",
                                        count = heartbeatList.size,
                                        onClickPlay = {
                                            if (heartbeatList.isNotEmpty()) {
                                                playlistQueueViewModel.clearPlaylist()
                                                playlistQueueViewModel.addAllToPlaylistInOrder(heartbeatList)
                                                playlistQueueViewModel.playWith(heartbeatList.first())
                                                navController.add(NavRoutes.Player.Player)
                                            }
                                        },
                                        onClickDetails = {
                                            navController.add(NavRoutes.Playlist.Playlist("今日推荐"))
                                        },
                                    )
                                    PlaylistEntryCard(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        icon = Res.drawable.music_note_list,
                                        title = "❤️ 最近收藏",
                                        subtitle = "WIP · 后续接 MusicRepository",
                                        count = heartbeatList.size, // 批次 B 占位
                                        onClickPlay = {
                                            if (heartbeatList.isNotEmpty()) {
                                                playlistQueueViewModel.clearPlaylist()
                                                playlistQueueViewModel.addAllToPlaylistInOrder(heartbeatList)
                                                playlistQueueViewModel.playWith(heartbeatList.first())
                                                navController.add(NavRoutes.Player.Player)
                                            }
                                        },
                                        onClickDetails = {
                                            navController.add(NavRoutes.Playlist.Playlist("最近收藏"))
                                        },
                                    )
                                }
                            }

                            // 区域③ 探索
                            Text(
                                text = "探索",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            FeatureEntryRow(navController = navController)
                        }
                    }
                } else {
                    // ═══════════════════════════════════════
                    // Compact / Medium 纵向布局
                    // ① HelloSlideCardStack (16:9)
                    // ② Row { RadioCard(1:1); Column { 今日推荐; 最近收藏 } }
                    // ③ FeatureEntryRow
                    // ═══════════════════════════════════════
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        // 搜索框
                        HomeSearchBar(onClick = { navController.add(NavRoutes.Library.Search) })

                        // 区域①
                        HelloSlideCardStack(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(10f / 9f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // 区域② 快速播放
                        Text(
                            text = "为你推荐",
                            modifier = Modifier,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            RadioCard(
                                modifier = Modifier.fillMaxHeight(),
                            )
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                PlaylistEntryCard(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    icon = Res.drawable.music_note_list,
                                    title = "🎵 今日推荐",
                                    subtitle = "AI 为你精选",
                                    count = heartbeatList.size,
                                    onClickPlay = {
                                        if (heartbeatList.isNotEmpty()) {
                                            playlistQueueViewModel.clearPlaylist()
                                            playlistQueueViewModel.addAllToPlaylistInOrder(heartbeatList)
                                            playlistQueueViewModel.playWith(heartbeatList.first())
                                            navController.add(NavRoutes.Player.Player)
                                        }
                                    },
                                    onClickDetails = {
                                        navController.add(NavRoutes.Playlist.Playlist("今日推荐"))
                                    },
                                )
                                PlaylistEntryCard(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    icon = Res.drawable.music_note_list,
                                    title = "❤️ 最近收藏",
                                    subtitle = "WIP · 后续接 MusicRepository",
                                    count = heartbeatList.size, // 批次 B 占位
                                    onClickPlay = {
                                        if (heartbeatList.isNotEmpty()) {
                                            playlistQueueViewModel.clearPlaylist()
                                            playlistQueueViewModel.addAllToPlaylistInOrder(heartbeatList)
                                            playlistQueueViewModel.playWith(heartbeatList.first())
                                            navController.add(NavRoutes.Player.Player)
                                        }
                                    },
                                    onClickDetails = {
                                        navController.add(NavRoutes.Playlist.Playlist("最近收藏"))
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        FeatureEntryRow(navController = navController)
                    }
                }
            }
        }
    }
}

/**
 * 首页搜索框：surfaceVariant 圆角容器 + 🔍 图标 + placeholder 文字，点击跳转 SearchScreen。
 * 样式与 ChatScreen 输入框保持一致（同 surfaceVariant 背景 + RoundedCornerShape）。
 */
@Composable
private fun HomeSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.magnifyingglass),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(Res.string.search_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
