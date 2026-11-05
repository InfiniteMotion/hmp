package com.hmp.storybook.pages.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.LocalAppLanguage

/**
 * AlbumCover 典型调用参数展示
 */
@Composable
fun AlbumCoverPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.albumCover(lang),
        description = if (lang == AppLanguage.ZH) {
            "专辑封面组件 (AlbumCover)，展示各场景下的典型调用参数"
        } else {
            "Album cover component (AlbumCover) with typical parameters for each usage scenario"
        },
        onBack = onBack,
    ) {
        // MiniPlayerBar 封面
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "MiniPlayerBar 封面" else "MiniPlayerBar Cover",
            description = if (lang == AppLanguage.ZH) {
                "size=56.dp, corner=28.dp, shadow=5.dp"
            } else {
                "size=56.dp, corner=28.dp, shadow=5.dp"
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AlbumCoverPreview(
                    size = 56.dp,
                    cornerRadius = 28.dp,
                    shadow = 5.dp,
                    label = if (lang == AppLanguage.ZH) "播放中" else "Playing",
                )
                AlbumCoverPreview(
                    size = 56.dp,
                    cornerRadius = 28.dp,
                    shadow = 5.dp,
                    label = if (lang == AppLanguage.ZH) "暂停" else "Paused",
                )
            }
        }

        // MusicListItem Full/Gallery 封面
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "MusicListItem Full/Gallery 封面" else "MusicListItem Full/Gallery Cover",
            description = if (lang == AppLanguage.ZH) {
                "size=56.dp, corner=10.dp, shadow=3.dp"
            } else {
                "size=56.dp, corner=10.dp, shadow=3.dp"
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AlbumCoverPreview(
                    size = 56.dp,
                    cornerRadius = 10.dp,
                    shadow = 3.dp,
                    label = "Full",
                )
                AlbumCoverPreview(
                    size = 56.dp,
                    cornerRadius = 10.dp,
                    shadow = 3.dp,
                    label = "Gallery",
                )
                AlbumCoverPreview(
                    size = 56.dp,
                    cornerRadius = 10.dp,
                    shadow = 3.dp,
                    label = "Full",
                )
            }
        }

        // PlayContent 封面页
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "PlayContent 封面页" else "PlayContent Cover Page",
            description = if (lang == AppLanguage.ZH) {
                "size=300.dp, corner=20.dp, shadow=10.dp"
            } else {
                "size=300.dp, corner=20.dp, shadow=10.dp"
            },
        ) {
            AlbumCoverPreview(
                size = 300.dp,
                cornerRadius = 20.dp,
                shadow = 10.dp,
                label = if (lang == AppLanguage.ZH) "播放页封面" else "Player Cover",
            )
        }

        // PlaylistHeader 封面
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "PlaylistHeader 封面" else "PlaylistHeader Cover",
            description = if (lang == AppLanguage.ZH) {
                "size=280.dp, corner=25.dp, shadow=15.dp"
            } else {
                "size=280.dp, corner=25.dp, shadow=15.dp"
            },
        ) {
            AlbumCoverPreview(
                size = 280.dp,
                cornerRadius = 25.dp,
                shadow = 15.dp,
                label = if (lang == AppLanguage.ZH) "歌单封面" else "Playlist Cover",
            )
        }

        // Crossfade 动画过渡说明
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "Crossfade 动画过渡" else "Crossfade Animation Transition",
            description = if (lang == AppLanguage.ZH) {
                "封面切换时使用 Crossfade 动画实现平滑过渡效果，避免突兀的图片跳变"
            } else {
                "Crossfade animation is used for smooth cover transitions, avoiding abrupt image changes"
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 模拟 Crossfade 前后状态
                AlbumCoverPreview(
                    size = 80.dp,
                    cornerRadius = 10.dp,
                    shadow = 3.dp,
                    gradientColors = listOf(HDBlue, Color(0xFF6C63FF)),
                    label = if (lang == AppLanguage.ZH) "切换前" else "Before",
                )
                Text(
                    text = "->",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AlbumCoverPreview(
                    size = 80.dp,
                    cornerRadius = 10.dp,
                    shadow = 3.dp,
                    gradientColors = listOf(Color(0xFFFF6B35), Color(0xFFF7C59F)),
                    label = if (lang == AppLanguage.ZH) "切换后" else "After",
                )
                Text(
                    text = if (lang == AppLanguage.ZH) "Crossfade" else "Crossfade",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AlbumCoverPreview(
    size: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    shadow: androidx.compose.ui.unit.Dp = 0.dp,
    gradientColors: List<Color> = listOf(HDBlue, Color(0xFF6C63FF)),
    label: String = "",
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(shadow, RoundedCornerShape(cornerRadius))
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.linearGradient(colors = gradientColors),
                ),
        ) {
            if (size >= 80.dp) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(size * 0.3f)
                        .align(Alignment.Center),
                )
            }
        }
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
