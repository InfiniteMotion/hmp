package com.hearablemusic.player.ui.library.pages.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.sub.RadioState
import com.hmp.domain.agent.sub.SlideType
import com.hearablemusic.player.ui.common.util.HapticFeedbackHelper
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.headphones_fill
import com.hearablemusic.player.ui.generated.resources.play_fill
import com.hearablemusic.player.ui.generated.resources.pause
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * 区域②左：1:1 收音机开关卡。
 *
 * 状态感知路径：masterAgent.radioState（StateFlow，直接来自 RadioSubAgent）
 *   - PLAYING / BUILDING → ON
 *   - IDLE / null → OFF
 *   不绕 HelloSubAgent 的 cards StateFlow。
 *
 * 丰富展示信息（targetCount / nextTrackName）是 HelloSubAgent 门面增强产出，
 * 可选从 helloAgent.cards 里的 RADIO_STATUS 卡拿——拿不到也不影响状态判断。
 */
@Composable
fun RadioCard(
    modifier: Modifier = Modifier,
    masterAgent: MasterAgent = koinInject(),
    haptic: HapticFeedbackHelper = rememberHapticFeedback(),
) {
    val scope = rememberCoroutineScope()

    // ✅ 主路径：直接 collect MasterAgent 暴露的 radioState StateFlow
    val radioState by masterAgent.radioState.collectAsState()
    val isActive = radioState is RadioState.PLAYING || radioState is RadioState.BUILDING
    val isBuilding = radioState is RadioState.BUILDING

    // 丰富信息（可选）：从 HelloSubAgent.cards 拿 RADIO_STATUS 卡的门面内容
    val helloAgent = masterAgent.helloAgent()
    val radioStatusContent = if (helloAgent != null) {
        val cards by helloAgent.cards.collectAsState(initial = emptyList())
        cards.firstOrNull { it.type == SlideType.RADIO_STATUS }
            ?.content as? com.hmp.domain.agent.sub.RadioStatusContent
    } else null

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable {
                haptic.performClick()
                scope.launch {
                    if (isActive) masterAgent.stopRadio() else masterAgent.startRadio()
                }
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 8.dp else 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (isActive) MaterialTheme.colorScheme.primaryContainer
                                           else MaterialTheme.colorScheme.surface,
                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                           else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        )
                    )
                )
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start,
            ) {
                // 顶部：图标 + 标签
                Icon(
                    painter = painterResource(Res.drawable.headphones_fill),
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 中部：状态文案
                Column {
                    Text(
                        text = when {
                            isBuilding -> "📻 电台启动中..."
                            isActive -> "📻 电台运行中"
                            else -> "📻 电台"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (isBuilding) {
                        Text(
                            text = "AI 正在挑选曲目...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    } else if (isActive && radioStatusContent != null) {
                        Text(
                            text = "自动续歌中 · 共 ${radioStatusContent.targetCount!!} 首备选",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        radioStatusContent.nextTrackTitle?.let {
                            Text(
                                text = "下首：$it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (isActive) {
                        // 电台运行中但 HelloSubAgent 没推送门面卡 → 降级显示
                        Text(
                            text = "自动续歌中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    } else {
                        Text(
                            text = "点击开启自动续歌",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 底部：开关按钮
                FilledIconButton(
                    onClick = {
                        haptic.performClick()
                        scope.launch {
                            if (isActive) masterAgent.stopRadio() else masterAgent.startRadio()
                        }
                    },
                    enabled = !isBuilding,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.primary,
                        contentColor = if (isActive) MaterialTheme.colorScheme.onError
                                       else MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        painter = painterResource(
                            if (isActive) Res.drawable.pause else Res.drawable.play_fill
                        ),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
