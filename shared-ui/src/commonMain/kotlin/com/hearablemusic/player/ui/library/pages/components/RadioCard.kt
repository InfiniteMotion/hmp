package com.hearablemusic.player.ui.library.pages.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.util.HapticFeedbackHelper
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.headphones_fill
import com.hearablemusic.player.ui.generated.resources.play_fill
import com.hearablemusic.player.ui.generated.resources.stop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * 区域②左：1:1 收音机开关卡（控制为主）。
 *
 * 设计对齐 TitleWidget / User·Setting 页卡片：透明底 + `outlineVariant` 50% 描边 + `dimens.corner.md` 圆角
 * （运行中描边转主色 50%），无渐变/无 emoji；信息 = 状态标题 + 副文案 +（运行中）待播数。
 * 右上角一个纯圆形主色状态按钮（无文字），与整卡绑为一体（点击任意处都触发开关）、只反映状态。
 *
 * 状态感知路径：masterAgent.radioState（StateFlow，直接来自 RadioSubAgent）
 *   - PLAYING / BUILDING / PAUSED → ON；IDLE / null → OFF
 *   不绕 HelloSubAgent 的 cards StateFlow。
 *
 * 职责边界：本卡只管「开 / 关」两态和最小状态 —— **外部不区分停止与暂停**（内部暂停态由电台自管；
 * 重新开启时 `startRadio` 内置 `tryResumeRetained` 自动判断「续档」还是「新开」）。
 * 信息展示主力是 Hello 堆叠卡里的 RADIO_STATUS 卡（FamilyRadioStatusCard）：
 * 主播编排思路（lastAdjust）、待播数、下一首都在那边展示。
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
    val isBuilding = radioState is RadioState.BUILDING
    // 外部不区分停止 / 暂停：PLAYING / PAUSED / BUILDING 都算「已开启」（内部态由电台自管）
    val isActive = radioState is RadioState.PLAYING ||
        radioState is RadioState.PAUSED ||
        isBuilding

    // ✅ 动态：运行状态 + 队列
    // 不要用 helloAgent.cards 找 RADIO_STATUS —— 那张卡早已拆到 UI 层、只在
    // HelloSlideCards 里本地构建，从来没进过 cards 流，取出来恒为 null。
    val card by masterAgent.radioCardState.collectAsState()

    fun toggle() {
        haptic.performClick()
        scope.launch {
            if (isActive) {
                masterAgent.stopRadio()    // 已开启（运行 / 暂停 / 启动中）→ 关闭
            } else {
                masterAgent.startRadio()   // 空闲 → 开启（内部自动判断续档还是新开）
            }
        }
    }

    val title = when {
        isBuilding -> "电台启动中"
        isActive -> "电台已开启"
        else -> "电台"
    }
    val subtitle = when {
        isBuilding -> "AI 正在挑选曲目…"
        isActive -> card.theme?.let { "$it · 自动续歌中" } ?: "自动续歌中"
        else -> "点击开启自动续歌"
    }
    val actionLabel = when {
        isBuilding -> "启动中…"
        isActive -> "关闭电台"
        else -> "开启电台"
    }
    val actionIcon = if (isActive) Res.drawable.stop else Res.drawable.play_fill

    // 按钮仅反映状态（不是独立点击目标，点击统一由整卡处理）；配色统一走 primary
    val actionBg = if (isBuilding) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val actionFg = MaterialTheme.colorScheme.onPrimary

    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    HMPCard(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { toggle() },
        borderColor = borderColor,
        contentPadding = Modifier.padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 顶部：左 = 入口图标；右 = 圆形状态按钮（无文字，仅反映状态，非独立点击目标）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.headphones_fill),
                    contentDescription = null,
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(actionBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(actionIcon),
                        contentDescription = actionLabel,
                        tint = actionFg,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // 底部：状态文案
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isActive && card.upcomingCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "还有 ${card.upcomingCount} 首待播",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
