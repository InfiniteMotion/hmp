package com.hearablemusic.player.ui.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.common.util.rememberPlatformHaptics
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.agent_capsule_desc
import com.hearablemusic.player.ui.generated.resources.player_d
import com.hearablemusic.player.ui.platform.HapticEffect
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 伙伴胶囊（任务书 M1-T2，设计总纲 5.2.1 三胶囊底栏的常驻锚点）。
 *
 * 行为：点按 = 回门面（设计总纲 2.2）；长按 600ms = 唤起轻量浮层（与播放页「对话」按钮同语义）。
 * 触觉：点按 TICK、长按 LONG_PRESS（对齐既有分级；设计文档的 GestureEnd 由 LONG_PRESS 承载）。
 *
 * 状态：位于门面（首页）时图标高亮（primary），其余页面常态 onSurface——与导航 tab 的选中语义一致。
 * 式样：与底栏其余两胶囊完全一致——36dp 圆角胶囊 + haze + 0.5dp 描边 +
 * 内容区 `.padding(8.dp).size(48.dp)` + 图标 24dp，恒定 48dp 体系（用户决策 2026-08-27）。
 * 形象占位：暂用「首页刷新」图标 `player_d`（尚无伙伴头像资源，M5 门面二期前替换）。
 *
 * @param selected 是否位于门面（首页）：图标高亮
 */
@Composable
fun CompanionCapsule(
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val haptic = rememberPlatformHaptics()
    val capsuleShape = RoundedCornerShape(36.dp)
    val container = if (hazeState != null) {
        MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        shape = capsuleShape,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .clip(capsuleShape)
            .then(
                if (hazeState != null) Modifier.hazeEffect(
                    state = hazeState,
                    style = hazeStyleForIntensity()
                ) else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            haptic.perform(HapticEffect.TICK)
                            onClick()
                        },
                        onLongPress = {
                            haptic.perform(HapticEffect.LONG_PRESS)
                            onLongPress()
                        },
                    )
                }
                .clip(capsuleShape),
            contentAlignment = Alignment.Center,
        ) {
            // 内容区与导航/播放胶囊同构：8dp 内边距 + 48dp 内容盒 + 图标 24dp（全局恒定 48dp 体系）
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.player_d),
                    contentDescription = stringResource(Res.string.agent_capsule_desc),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}