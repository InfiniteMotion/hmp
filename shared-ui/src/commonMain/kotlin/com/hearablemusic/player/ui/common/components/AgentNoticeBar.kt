package com.hearablemusic.player.ui.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import kotlinx.coroutines.delay

/**
 * R-T6 / M5-T3 AgentNoticeBar —— 伙伴通知侧条（总纲 5.4）。
 *
 * 底栏上方 haze 侧条：4s 自动退场；撤销点按=回滚（触觉 Confirm）。
 * 与系统 [MessageToast] 两条通道不混用；同时刻至多一条（由上层持有单条 notice）。
 * 撤销窗口过期≠不可逆：过期后由上层把入口转入审计页。
 *
 * 说明：本组件采用 surface 半透明 + 0.5dp 描边 + 36dp 圆角，与组件语言一致；
 * 因宿主需显式提供 HazeState，此处不强依赖 Haze（交互语言三要素中的毛玻璃由宿主/未来接线补齐）。
 */
data class AgentNotice(
    val id: Long,
    val message: String,
    val undoLabel: String = "撤销",
    val showUndo: Boolean = true,
)

@Composable
fun AgentNoticeBar(
    notice: AgentNotice?,
    onUndo: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 2 },
    ) {
        notice?.let { n ->
            val shape = RoundedCornerShape(36.dp)
            val haptic = rememberHapticFeedback()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), shape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = n.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (n.showUndo && onUndo != null) {
                    TextButton(onClick = { haptic.performConfirm(); onUndo() }) {
                        Text(n.undoLabel, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // 4s 自动退场（撤销窗口过期 → 上层转审计页）
            LaunchedEffect(n.id) {
                delay(4000)
                onDismiss()
            }
        }
    }
}
