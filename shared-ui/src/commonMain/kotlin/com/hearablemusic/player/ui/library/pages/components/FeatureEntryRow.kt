package com.hearablemusic.player.ui.library.pages.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.navigation.Routes as NavRoutes
import com.hearablemusic.player.ui.common.util.HapticFeedbackHelper
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.gearshape
import com.hearablemusic.player.ui.generated.resources.list_bullet_circle
import com.hearablemusic.player.ui.generated.resources.person_filled_viewfinder
import org.jetbrains.compose.resources.painterResource

/**
 * 区域③：功能入口区 —— 横排等宽三卡。
 *
 * | 💬 聊聊  | ⚙️ 配置  | 📊 看板  |
 *  Companion  AI.AI    Settings
 *  .Chat      .AI      .AgentMonitor
 */
@Composable
fun FeatureEntryRow(
    navController: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
    haptic: HapticFeedbackHelper = rememberHapticFeedback(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        FeatureEntryCard(
            modifier = Modifier.weight(1f),
            emoji = "💬",
            title = "和伙伴聊聊",
            subtitle = "对话 + 陪伴",
            onClick = {
                haptic.performClick()
                navController.add(NavRoutes.Companion.Chat)
            }
        )
        FeatureEntryCard(
            modifier = Modifier.weight(1f),
            iconResource = Res.drawable.gearshape,
            title = "Agent 配置",
            subtitle = "伙伴性格 + 策略",
            onClick = {
                haptic.performClick()
                navController.add(NavRoutes.AI.AI)
            }
        )
        FeatureEntryCard(
            modifier = Modifier.weight(1f),
            iconResource = Res.drawable.list_bullet_circle,
            title = "Agent 看板",
            subtitle = "运行态 + 撤销",
            onClick = {
                haptic.performClick()
                navController.add(NavRoutes.Settings.AgentMonitor)
            }
        )
    }
}

/** 单张功能入口卡 */
@Composable
private fun FeatureEntryCard(
    modifier: Modifier = Modifier,
    emoji: String? = null,
    iconResource: org.jetbrains.compose.resources.DrawableResource? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (emoji != null) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.headlineMedium,
                )
            } else if (iconResource != null) {
                Icon(
                    painter = painterResource(iconResource),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
