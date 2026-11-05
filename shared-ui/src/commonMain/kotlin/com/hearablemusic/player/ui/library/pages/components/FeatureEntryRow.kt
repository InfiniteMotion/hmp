package com.hearablemusic.player.ui.library.pages.components
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.util.HapticFeedbackHelper
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.feature_agent_config
import com.hearablemusic.player.ui.generated.resources.feature_agent_monitor
import com.hearablemusic.player.ui.generated.resources.feature_chat
import com.hearablemusic.player.ui.generated.resources.gearshape
import com.hearablemusic.player.ui.generated.resources.list_bullet_circle
import com.hearablemusic.player.ui.generated.resources.person_filled_viewfinder
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.hearablemusic.player.ui.common.navigation.Routes as NavRoutes

/**
 * 区域③：功能入口区 —— 横排等宽三卡（只保留图标 + 标题；标题 titleSmall）。
 *
 * | 聊聊  | 配置  | 看板  |
 *  Companion  AI.AI    Settings
 *  .Chat      .AI      .AgentMonitor
 */
@Composable
fun FeatureEntryRow(
    navController: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
    haptic: HapticFeedbackHelper = rememberHapticFeedback(),
) {
    val metrics = LocalHmpMetrics.current
    Row(
        modifier = modifier
            .fillMaxWidth(),
        // 同级卡间距统一 20dp（与 HomeScreen 区域②的卡间 20dp 一致）
        horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
    ) {
        FeatureEntryCard(
            modifier = Modifier.weight(1f),
            iconResource = Res.drawable.person_filled_viewfinder,
            title = stringResource(Res.string.feature_chat),
            onClick = {
                haptic.performClick()
                navController.add(NavRoutes.Companion.Chat)
            },
        )
        FeatureEntryCard(
            modifier = Modifier.weight(1f),
            iconResource = Res.drawable.gearshape,
            title = stringResource(Res.string.feature_agent_config),
            onClick = {
                haptic.performClick()
                navController.add(NavRoutes.AI.AI)
            },
        )
        FeatureEntryCard(
            modifier = Modifier.weight(1f),
            iconResource = Res.drawable.list_bullet_circle,
            title = stringResource(Res.string.feature_agent_monitor),
            onClick = {
                haptic.performClick()
                navController.add(NavRoutes.Settings.AgentMonitor)
            },
        )
    }
}

/** 单张功能入口卡（只保留图标 + 标题） */
@Composable
private fun FeatureEntryCard(
    modifier: Modifier = Modifier,
    iconResource: org.jetbrains.compose.resources.DrawableResource,
    title: String,
    onClick: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    HMPCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .aspectRatio(1.2f),
        contentPadding = Modifier.padding(vertical = metrics.cozy, horizontal = metrics.base),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconResource),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(metrics.compact))
            Text(
                text = title,
                style = typo.body.medium(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
