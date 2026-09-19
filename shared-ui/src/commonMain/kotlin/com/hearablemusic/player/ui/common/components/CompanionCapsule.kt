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
import com.hearablemusic.player.ui.generated.resources.agent_capsule_radio_desc
import com.hearablemusic.player.ui.generated.resources.player_d
import com.hearablemusic.player.ui.generated.resources.radiowaves
import com.hearablemusic.player.ui.platform.HapticEffect
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 伙伴胶囊（任务书 M1-T2，设计总纲 5.2.1 三胶囊底栏的常驻锚点）。
 *
 * 行为：点按 = 回门面（设计总纲 2.2）；长按 600ms = **直接进对话页**（与播放页「对话」按钮同语义；
 * 原为唤起轻量浮层，该层已于 2026-09-19 移除）。电台开启时长按让位给**电台控制台**，见下。
 * 触觉：点按 TICK、长按 LONG_PRESS（对齐既有分级；设计文档的 GestureEnd 由 LONG_PRESS 承载）。
 *
 * 状态：位于门面（首页）时图标高亮（primary），其余页面常态 onSurface——与导航 tab 的选中语义一致。
 * 式样：与底栏其余两胶囊完全一致——36dp 圆角胶囊 + haze + 0.5dp 描边 +
 * 内容区 `.padding(8.dp).size(48.dp)` + 图标 24dp，恒定 48dp 体系（用户决策 2026-08-27）。
 * 形象占位：暂用「首页刷新」图标 `player_d`（尚无伙伴头像资源，M5 门面二期前替换）。
 *
 * **电台态（2026-09-19）**：电台开启期间，本胶囊同时充当**电台的常驻状态位**——
 * 图标换成 `radiowaves` 并恒定高亮（primary），让"伙伴在这里、且电台正在播"一眼可见。
 * 长按语义随之让位给电台（**当前实现**：直接停止电台）。
 * ⚠️ 该语义正在改：关闭是终态动作，不该由一个长按直接完成 ——
 * 已设计为**长按唤起「电台控制台」全屏面板**，关闭动作移入面板内确认。
 * 见 `docs/7_x/B agent-build/design/agent-radio-console.md`（待实施）。
 * 点按语义不变（仍是回门面）：它是导航锚点，不能因电台而被夺走。
 *
 * @param selected 是否位于门面（首页）：图标高亮
 * @param radioActive 电台是否已开启（PLAYING / PAUSED / BUILDING）：换电台图标并恒定高亮
 */
@Composable
fun CompanionCapsule(
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    radioActive: Boolean = false,
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
                    painter = painterResource(
                        // 电台开启 → `radiowaves`（"正在播送"的状态语感，比静态的耳机标识更贴合"电台活着"；
                        // RadioCard 的入口身份仍用 headphones_fill，二者是"状态"与"身份"的分工）
                        if (radioActive) Res.drawable.radiowaves else Res.drawable.player_d
                    ),
                    contentDescription = stringResource(
                        if (radioActive) Res.string.agent_capsule_radio_desc
                        else Res.string.agent_capsule_desc
                    ),
                    // 电台态恒定高亮：它此刻代表"正在播的电台"，优先于门面选中态
                    tint = if (radioActive || selected) {
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