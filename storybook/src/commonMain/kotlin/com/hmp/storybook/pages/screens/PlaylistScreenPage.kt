@file:OptIn(ExperimentalMaterial3Api::class)

package com.hmp.storybook.pages.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.PhoneFramePreview
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.LocalAppLanguage

@Composable
fun PlaylistScreenPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.playlistScreen(lang),
        description = if (lang == AppLanguage.ZH) {
            "HMP 播放列表页面，SubScreen 包装，可折叠 PlaylistHeader(最大折叠偏移 160.dp)，头部封面 280.dp/圆角 25.dp/阴影 15.dp，统计信息，ExtendedFloatingActionButton 播放全部"
        } else {
            "HMP Playlist screen with SubScreen wrapper, collapsible PlaylistHeader (max offset 160.dp), 280.dp cover with 25.dp radius and 15.dp shadow, stats, ExtendedFloatingActionButton"
        },
        onBack = onBack,
    ) {
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "播放列表预览" else "Playlist Preview",
            description = if (lang == AppLanguage.ZH) {
                "SubScreen(返回+标题) + PlaylistHeader(可折叠 160.dp) + 封面(280.dp, 25.dp圆角, 15.dp阴影) + 统计信息 + EFA 播放全部"
            } else {
                "SubScreen(back+title) + PlaylistHeader(collapsible 160.dp) + Cover(280.dp, 25.dp radius, 15.dp shadow) + Stats + EFA Play All"
            },
        ) {
            PhoneFramePreview {
                PlaylistScreenContent(lang)
            }
        }
    }
}

@Composable
private fun PlaylistScreenContent(lang: AppLanguage) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // SubScreen 包装: 返回按钮 + 标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = {}) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = if (lang == AppLanguage.ZH) "我的收藏" else "My Favorites",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {}) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More",
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // 可折叠 PlaylistHeader: 最大折叠偏移 160.dp
            // 头部封面: 280.dp, 圆角 25.dp, 阴影 15.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .shadow(
                        elevation = 15.dp,
                        shape = RoundedCornerShape(25.dp),
                    )
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                HDBlue,
                                Color(0xFF6C63FF),
                                Color(0xFFE040FB),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 封面上的播放图标
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 统计信息: 歌曲数/时长/播放次数, titleMedium + ExtraBold
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatItem(
                    value = "42",
                    label = if (lang == AppLanguage.ZH) "首歌曲" else "Songs",
                    lang = lang,
                )
                StatItem(
                    value = "2:35",
                    label = if (lang == AppLanguage.ZH) "总时长" else "Duration",
                    lang = lang,
                )
                StatItem(
                    value = "128",
                    label = if (lang == AppLanguage.ZH) "播放次数" else "Plays",
                    lang = lang,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ExtendedFloatingActionButton: 播放全部
            ExtendedFloatingActionButton(
                onClick = {},
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    if (lang == AppLanguage.ZH) "播放全部" else "Play All",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 列表操作: 随机播放/顺序播放
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = if (lang == AppLanguage.ZH) "随机播放" else "Shuffle",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (lang == AppLanguage.ZH) "随机播放" else "Shuffle",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = if (lang == AppLanguage.ZH) "顺序播放" else "Sequential",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (lang == AppLanguage.ZH) "顺序播放" else "Sequential",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 歌曲列表: 带序号，长按编辑，移动排序
            val songs = listOf(
                Triple("Bohemian Rhapsody", "Queen", "5:55"),
                Triple("Hotel California", "Eagles", "6:30"),
                Triple("Stairway to Heaven", "Led Zeppelin", "8:02"),
                Triple("Imagine", "John Lennon", "3:03"),
                Triple("Yesterday", "The Beatles", "2:05"),
                Triple("Comfortably Numb", "Pink Floyd", "6:23"),
            )
            songs.forEachIndexed { index, (title, artist, duration) ->
                PlaylistSongItem(index + 1, title, artist, duration, lang)
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    lang: AppLanguage,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistSongItem(
    index: Int,
    title: String,
    artist: String,
    duration: String,
    lang: AppLanguage,
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
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            HDBlue,
                            Color(0xFF6C63FF),
                        ),
                    ),
                ),
        )
        Spacer(modifier = Modifier.width(12.dp))
        // 歌曲信息
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 时长
        Text(
            text = duration,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        // 移动排序手柄
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = if (lang == AppLanguage.ZH) "移动排序" else "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
    }
}
