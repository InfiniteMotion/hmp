package com.hmp.storybook.pages.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.LocalAppLanguage

/**
 * MusicListItem 的三种变体
 */
private enum class MusicRowVariant {
    FULL, // 高度 80.dp, 封面 56.dp, 圆角 10.dp, headlineSmall + labelSmall
    COMPACT, // 高度 64.dp, 封面 48.dp, 圆角 8.dp, bodyMedium + bodySmall
    GALLERY, // 高度 80.dp, 封面 56.dp, 圆角 10.dp, headlineSmall + labelSmall
}

@Composable
fun MusicRowPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.musicRow(lang),
        description = if (lang == AppLanguage.ZH) {
            "歌曲列表行组件 (MusicListItem)，展示 Full / Compact / Gallery 三种变体"
        } else {
            "Song list row component (MusicListItem) with Full / Compact / Gallery variants"
        },
        onBack = onBack,
    ) {
        // Full 变体
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "Full 变体" else "Full Variant",
            description = if (lang == AppLanguage.ZH) {
                "高度 80.dp, 封面 56.dp, 圆角 10.dp, headlineSmall + labelSmall"
            } else {
                "Height 80.dp, cover 56.dp, corner 10.dp, headlineSmall + labelSmall"
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MusicRowPreview(
                    variant = MusicRowVariant.FULL,
                    title = "Bohemian Rhapsody",
                    artist = "Queen",
                    index = 1,
                    isCurrentPlaying = false,
                )
                MusicRowPreview(
                    variant = MusicRowVariant.FULL,
                    title = "Hotel California",
                    artist = "Eagles",
                    index = 2,
                    isCurrentPlaying = true,
                )
                MusicRowPreview(
                    variant = MusicRowVariant.FULL,
                    title = "Stairway to Heaven",
                    artist = "Led Zeppelin",
                    index = 3,
                    isCurrentPlaying = false,
                )
            }
        }

        // Compact 变体
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "Compact 变体" else "Compact Variant",
            description = if (lang == AppLanguage.ZH) {
                "高度 64.dp, 封面 48.dp, 圆角 8.dp, bodyMedium + bodySmall"
            } else {
                "Height 64.dp, cover 48.dp, corner 8.dp, bodyMedium + bodySmall"
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MusicRowPreview(
                    variant = MusicRowVariant.COMPACT,
                    title = "Blinding Lights",
                    artist = "The Weeknd",
                    index = 1,
                    isCurrentPlaying = false,
                )
                MusicRowPreview(
                    variant = MusicRowVariant.COMPACT,
                    title = "Shape of You",
                    artist = "Ed Sheeran",
                    index = 2,
                    isCurrentPlaying = true,
                )
                MusicRowPreview(
                    variant = MusicRowVariant.COMPACT,
                    title = "Bad Guy",
                    artist = "Billie Eilish",
                    index = 3,
                    isCurrentPlaying = false,
                )
            }
        }

        // Gallery 变体
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "Gallery 变体" else "Gallery Variant",
            description = if (lang == AppLanguage.ZH) {
                "高度 80.dp, 封面 56.dp, 圆角 10.dp, headlineSmall + labelSmall"
            } else {
                "Height 80.dp, cover 56.dp, corner 10.dp, headlineSmall + labelSmall"
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MusicRowPreview(
                    variant = MusicRowVariant.GALLERY,
                    title = "Yesterday",
                    artist = "The Beatles",
                    index = 1,
                    isCurrentPlaying = false,
                )
                MusicRowPreview(
                    variant = MusicRowVariant.GALLERY,
                    title = "Imagine",
                    artist = "John Lennon",
                    index = 2,
                    isCurrentPlaying = true,
                )
            }
        }

        // 长文本截断
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "长文本截断" else "Long Text Truncation",
            description = if (lang == AppLanguage.ZH) "超长歌名和艺术家名称的截断处理" else "Truncation for long title and artist names",
        ) {
            MusicRowPreview(
                variant = MusicRowVariant.FULL,
                title = "This Is A Very Long Song Title That Should Be Truncated With Ellipsis",
                artist = "An Artist With A Very Long Name",
                index = 1,
                isCurrentPlaying = false,
            )
        }
    }
}

@Composable
private fun MusicRowPreview(
    variant: MusicRowVariant,
    title: String,
    artist: String,
    index: Int,
    isCurrentPlaying: Boolean,
) {
    // 根据变体确定尺寸参数
    val rowHeight = when (variant) {
        MusicRowVariant.FULL, MusicRowVariant.GALLERY -> 80.dp
        MusicRowVariant.COMPACT -> 64.dp
    }
    val coverSize = when (variant) {
        MusicRowVariant.FULL, MusicRowVariant.GALLERY -> 56.dp
        MusicRowVariant.COMPACT -> 48.dp
    }
    val coverCorner = when (variant) {
        MusicRowVariant.FULL, MusicRowVariant.GALLERY -> 10.dp
        MusicRowVariant.COMPACT -> 8.dp
    }

    // 当前播放高亮颜色
    val titleColor = if (isCurrentPlaying) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val indexColor = if (isCurrentPlaying) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .padding(horizontal = 8.dp),
        ) {
            // 序号：宽度 28.dp
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelSmall,
                color = indexColor,
                fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(28.dp),
            )

            // 专辑封面
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .clip(RoundedCornerShape(coverCorner))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(HDBlue, Color(0xFF6C63FF)),
                        ),
                    ),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                when (variant) {
                    MusicRowVariant.FULL, MusicRowVariant.GALLERY -> {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    MusicRowVariant.COMPACT -> {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 更多按钮：32.dp
            IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
