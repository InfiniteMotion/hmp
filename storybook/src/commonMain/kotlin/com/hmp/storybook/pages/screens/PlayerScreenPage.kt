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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun PlayerScreenPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.playerScreen(lang),
        description = if (lang == AppLanguage.ZH) {
            "HMP 播放器页面，PlayerHeader 居中返回按钮，MusicInfo displayMedium 标题，SeekBar 4.dp 轨道，PlaybackControls 72.dp 主按钮，DotPager 三页切换"
        } else {
            "HMP Player screen with centered PlayerHeader, displayMedium MusicInfo, 4.dp SeekBar track, 72.dp PlaybackControls, DotPager with 3 pages"
        },
        onBack = onBack,
    ) {
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "播放器预览" else "Player Preview",
            description = if (lang == AppLanguage.ZH) {
                "PlayerHeader(居中返回 32.dp) + MusicInfo(displayMedium) + SeekBar(4.dp) + PlaybackControls(72.dp) + DotPager(3页)"
            } else {
                "PlayerHeader(centered back 32.dp) + MusicInfo(displayMedium) + SeekBar(4.dp) + PlaybackControls(72.dp) + DotPager(3 pages)"
            },
        ) {
            PhoneFramePreview {
                PlayerScreenContent(lang)
            }
        }
    }
}

@Composable
private fun PlayerScreenContent(lang: AppLanguage) {
    var progress by remember { mutableFloatStateOf(0.65f) }
    var currentPage by remember { mutableIntStateOf(1) } // 默认显示封面页

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                ),
            )
            .padding(20.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // PlayerHeader: 居中返回按钮 (32.dp, chevron_down 图标 24.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 居中的向下返回按钮
                FilledIconButton(
                    onClick = {},
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        Icons.Filled.QueueMusic, // chevron_down 替代图标
                        contentDescription = if (lang == AppLanguage.ZH) "返回" else "Back",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // DotPager: 三页切换 (技术信息 / 封面 / 歌词)
            // 页面指示器
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                repeat(3) { index ->
                    val selected = currentPage == index
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (selected) 24.dp else 8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                },
                            ),
                    )
                }
            }

            // 当前页面内容
            when (currentPage) {
                0 -> {
                    // 技术信息页
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (lang == AppLanguage.ZH) "技术信息" else "Tech Info",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "FLAC 24bit/96kHz",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "44100 Hz / Stereo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bitrate: 2116 kbps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "File Size: 89.2 MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                1 -> {
                    // 封面页
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(HDBlue, Color(0xFF6C63FF), Color(0xFFE040FB)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(80.dp),
                        )
                    }
                }

                2 -> {
                    // 歌词页
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (lang == AppLanguage.ZH) "歌词" else "Lyrics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Is this the real life?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Is this just fantasy?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Caught in a landslide",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No escape from reality",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // MusicInfo: displayMedium 标题, titleMedium 艺术家/专辑
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Bohemian Rhapsody",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Queen",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "A Night at the Opera",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SeekBar: 4.dp 轨道, primary 颜色, 透明 4.dp 滑块
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progress,
                    onValueChange = { progress = it },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier.height(4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "3:49",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "5:55",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // PlaybackControlsButtons: 主控制 72.dp 按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 上一曲
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // 播放/暂停 - 主控制按钮 72.dp
                FilledIconButton(
                    onClick = {},
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = "Pause",
                        modifier = Modifier.size(36.dp),
                    )
                }
                // 下一曲
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 辅助控制: 播放模式/收藏/心动/定时/列表
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 播放模式 (顺序播放)
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = if (lang == AppLanguage.ZH) "播放模式" else "Play Mode",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 收藏
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.FavoriteBorder,
                        contentDescription = if (lang == AppLanguage.ZH) "收藏" else "Favorite",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 心动
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = if (lang == AppLanguage.ZH) "心动" else "Like",
                        modifier = Modifier.size(22.dp),
                        tint = Color(0xFFFF4081),
                    )
                }
                // 定时
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = if (lang == AppLanguage.ZH) "定时" else "Sleep Timer",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 列表
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = if (lang == AppLanguage.ZH) "列表" else "Queue",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
