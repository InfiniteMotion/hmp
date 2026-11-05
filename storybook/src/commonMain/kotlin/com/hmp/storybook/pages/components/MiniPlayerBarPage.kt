package com.hmp.storybook.pages.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
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

@Composable
fun MiniPlayerBarPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.miniPlayerBar(lang),
        description = if (lang == AppLanguage.ZH) {
            "全局悬浮迷你播放器，胶囊形设计，显示当前播放歌曲信息，支持播放/暂停、上一首/下一首控制"
        } else {
            "Global floating mini player with capsule design, showing current song info with play/pause and skip controls"
        },
        onBack = onBack,
    ) {
        // 播放中状态
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "播放中" else "Playing",
            description = if (lang == AppLanguage.ZH) "正在播放状态的迷你播放器，封面旋转动画" else "Mini player in playing state with rotating cover animation",
        ) {
            MiniPlayerBarPreview(
                title = "Bohemian Rhapsody",
                artist = "Queen",
                isPlaying = true,
                progress = 0.65f,
            )
        }

        // 暂停状态
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "已暂停" else "Paused",
            description = if (lang == AppLanguage.ZH) "暂停状态的迷你播放器，封面停止旋转" else "Mini player in paused state, cover rotation stopped",
        ) {
            MiniPlayerBarPreview(
                title = "Hotel California",
                artist = "Eagles",
                isPlaying = false,
                progress = 0.32f,
            )
        }

        // 长标题截断
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "长标题截断" else "Long Title Truncation",
            description = if (lang == AppLanguage.ZH) "超长标题和艺术家名称的截断处理" else "Truncation for long title and artist names",
        ) {
            MiniPlayerBarPreview(
                title = "This is a very long song title that should be truncated with ellipsis at the end",
                artist = "An Artist With A Very Long Name That Also Needs Truncation",
                isPlaying = true,
                progress = 0.15f,
            )
        }
    }
}

@Composable
private fun MiniPlayerBarPreview(
    title: String,
    artist: String,
    isPlaying: Boolean,
    progress: Float,
) {
    // 封面旋转动画：8秒一圈, LinearEasing
    val infiniteTransition = rememberInfiniteTransition(label = "coverRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(36.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                start = 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 16.dp,
            ),
        ) {
            // 顶部进度条
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 专辑封面缩略图：56.dp, 圆角 28.dp (圆形), 阴影 5.dp, 播放时旋转
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(5.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(HDBlue, Color(0xFF6C63FF)),
                            ),
                        )
                        .then(
                            if (isPlaying) Modifier.rotate(rotation) else Modifier,
                        ),
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 歌曲信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
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

                // 控制按钮：上一首 40.dp / 播放暂停 44.dp / 下一首 40.dp
                // 图标尺寸：22.dp / 26.dp / 22.dp
                IconButton(onClick = {}, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                FilledIconButton(
                    onClick = {},
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
