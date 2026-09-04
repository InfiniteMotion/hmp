package com.hearablemusic.player.ui.library.pages.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.util.HapticFeedbackHelper
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.chevron_right
import com.hearablemusic.player.ui.generated.resources.play_fill
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * 通用歌单入口卡。
 *
 * 区域②右：今日推荐 / 最近收藏 两张同结构卡。
 *
 * @param icon 顶部图标（可选）
 * @param title 主标题（带 emoji 前缀）
 * @param subtitle 副文案（如 "30 首" 或 "今日心动"）
 * @param count 曲目数量（为 0 时显示空态文案）
 * @param onClickPlay 点击播放全部（suspend）
 * @param onClickDetails 箭头点击 → 歌单详情页
 */
@Composable
fun PlaylistEntryCard(
    icon: DrawableResource?,
    title: String,
    subtitle: String?,
    count: Int,
    onClickPlay: suspend () -> Unit,
    onClickDetails: () -> Unit,
    modifier: Modifier = Modifier,
    haptic: HapticFeedbackHelper = rememberHapticFeedback(),
) {
    val scope = rememberCoroutineScope()
    val isEmpty = count == 0

    Card(
        modifier = modifier
            .clickable(enabled = !isEmpty) {
                haptic.performClick()
                scope.launch { onClickPlay() }
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEmpty) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isEmpty) 2.dp else 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (isEmpty) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                       else MaterialTheme.colorScheme.surface,
                            if (isEmpty) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                       else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        )
                    )
                )
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 左侧 icon
                if (icon != null) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = if (isEmpty) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                               else MaterialTheme.colorScheme.primary,
                    )
                }

                // 中部：标题 + 副文案
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (isEmpty) "暂无数据" else title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isEmpty) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                               else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!isEmpty) {
                        subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "$count 首",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 右侧：播放按钮 + 箭头（非空时）
                if (!isEmpty) {
                    FilledIconButton(
                        onClick = {
                            haptic.performClick()
                            scope.launch { onClickPlay() }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(painter = painterResource(Res.drawable.play_fill), contentDescription = null)
                    }

                    IconButtonWithNoText(
                        onClick = {
                            haptic.performClick()
                            onClickDetails()
                        }
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** FeatureEntryRow 内部共用的纯 icon 按钮 */
@Composable
private fun IconButtonWithNoText(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
    ) { content() }
}
