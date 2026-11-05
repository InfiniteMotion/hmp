@file:OptIn(ExperimentalMaterial3Api::class)

package com.hmp.storybook.pages.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.PhoneFramePreview
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.LocalAppLanguage

@Composable
fun HomeScreenPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.homeScreen(lang),
        description = if (lang == AppLanguage.ZH) {
            "HMP 首页，TabScreen 包装，包含每日推荐 DailyHeroCard、心动歌单 FixedMusicList 和一键播放入口"
        } else {
            "HMP Home screen wrapped in TabScreen, with DailyHeroCard, FixedMusicList favorites and one-tap play"
        },
        onBack = onBack,
    ) {
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "首页预览" else "Home Preview",
            description = if (lang == AppLanguage.ZH) {
                "TabScreen 包装，displayLarge 标题，右上角刷新按钮，底部 NavigationBar 4 Tab"
            } else {
                "TabScreen wrapper, displayLarge title, refresh button, bottom NavigationBar with 4 tabs"
            },
        ) {
            PhoneFramePreview {
                HomeScreenContent(lang)
            }
        }
    }
}

@Composable
private fun HomeScreenContent(lang: AppLanguage) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            // TabScreen 顶部栏: displayLarge 标题 + 右上角刷新按钮
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (lang == AppLanguage.ZH) "发现音乐" else "Discover",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    // 右上角刷新按钮
                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = if (lang == AppLanguage.ZH) "刷新" else "Refresh",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        },
        bottomBar = {
            // 底部 NavigationBar: 4 个 Tab (Home/Gallery/List/User) 使用 TabPageIndicator 样式
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                val items = listOf(
                    Triple(Icons.Filled.Home, if (lang == AppLanguage.ZH) "首页" else "Home", 0),
                    Triple(Icons.Filled.Image, if (lang == AppLanguage.ZH) "图库" else "Gallery", 1),
                    Triple(Icons.Filled.LibraryMusic, if (lang == AppLanguage.ZH) "列表" else "List", 2),
                    Triple(Icons.Filled.Person, if (lang == AppLanguage.ZH) "我的" else "User", 3),
                )
                items.forEach { (icon, label, index) ->
                    NavigationBarItem(
                        icon = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(if (selectedTab == index) 28.dp else 24.dp),
                                    tint = if (selectedTab == index) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                // TabPageIndicator: 选中时底部显示指示器圆点
                                if (selectedTab == index) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp, 4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(modifier = Modifier.size(4.dp, 4.dp))
                                }
                            }
                        },
                        label = {
                            Text(
                                label,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 每日推荐: DailyHeroCard 组件 (正方形, RoundedCornerShape(28.dp), 底部渐变遮罩)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    HDBlue,
                                    Color(0xFF1A237E),
                                    Color(0xFF4A148C),
                                ),
                            ),
                        ),
                ) {
                    // 底部渐变遮罩
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f),
                                    ),
                                ),
                            ),
                    )
                    // 文字内容
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp),
                    ) {
                        Text(
                            text = if (lang == AppLanguage.ZH) "每日推荐" else "Daily Pick",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Clair de Lune",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Claude Debussy",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    // 右下角播放按钮
                    FilledIconButton(
                        onClick = {},
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                            .size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = if (lang == AppLanguage.ZH) "播放" else "Play",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // 心动歌单: FixedMusicList, ItemVariant.Full, 带序号
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (lang == AppLanguage.ZH) "心动歌单" else "Favorites",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // 一键播放歌单: FilledIconButton, 24.dp, primary 背景
                FilledIconButton(
                    onClick = {},
                    modifier = Modifier.size(24.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = if (lang == AppLanguage.ZH) "一键播放" else "Play All",
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (lang == AppLanguage.ZH) "一键播放" else "Play All",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }

            // FixedMusicList: ItemVariant.Full 带序号
            val favoriteSongs = listOf(
                Triple("Bohemian Rhapsody", "Queen", "5:55"),
                Triple("Hotel California", "Eagles", "6:30"),
                Triple("Stairway to Heaven", "Led Zeppelin", "8:02"),
                Triple("Imagine", "John Lennon", "3:03"),
                Triple("Yesterday", "The Beatles", "2:05"),
            )
            favoriteSongs.forEachIndexed { index, (title, artist, duration) ->
                FavoriteMusicItem(index + 1, title, artist, duration)
            }
        }
    }
}

@Composable
private fun FavoriteMusicItem(
    index: Int,
    title: String,
    artist: String,
    duration: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        // 序号
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp),
        )
        // 封面缩略图
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(HDBlue, Color(0xFF6C63FF)),
                    ),
                ),
        )
        Spacer(modifier = Modifier.width(12.dp))
        // 歌曲信息 (ItemVariant.Full)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = duration,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
