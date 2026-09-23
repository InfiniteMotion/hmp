package com.hearablemusic.player.ui.agent
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.Composable
import com.hearablemusic.player.ui.generated.resources.agent_status_idle
import com.hearablemusic.player.ui.generated.resources.agent_status_building
import com.hearablemusic.player.ui.generated.resources.agent_status_running
import com.hearablemusic.player.ui.generated.resources.agent_status_paused
import com.hearablemusic.player.ui.generated.resources.agent_status_completed
import com.hearablemusic.player.ui.generated.resources.agent_status_error

import androidx.compose.ui.graphics.Color
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.enrich
import com.hearablemusic.player.ui.generated.resources.hello
import com.hearablemusic.player.ui.generated.resources.master
import com.hearablemusic.player.ui.generated.resources.radiowaves
import com.hmp.domain.agent.port.CapabilityState
import org.jetbrains.compose.resources.DrawableResource

/**
 * Agent 视觉令牌（看板 AgentMonitorScreen 与设置页 AIScreen **共用同一份**）。
 *
 * 存在的理由：图标映射若在两个页面各存一份，改图标时必然只改一处、另一处悄悄过期。
 * 所有 Agent 相关的图形标识 / 状态色 / 状态文案都必须从这里取。
 */

/**
 * 各 Agent 的图标（composeResources/drawable 下的 vector，fillColor 为纯黑，由 tint 着色）。
 * radio 用项目原有的 `radiowaves`；master / enrich / hello 为新增资源。
 */
val AGENT_ICONS: Map<String, DrawableResource> = mapOf(
    "master" to Res.drawable.master,
    "radio" to Res.drawable.radiowaves,
    "enrich" to Res.drawable.enrich,
    "hello" to Res.drawable.hello,
)

/** 按 role 取图标；未知 role 返回 null（调用方用等高空白占位，不要让布局抖动）。 */
fun agentIcon(role: String): DrawableResource? = AGENT_ICONS[role]

/**
 * 状态色板（**低饱和**）。
 * Material 原色（#4CAF50 / #F44336 等）饱和度高、"工程感"重；
 * 这里只让颜色承担"区分状态"的职责，层级交给字号 / 字重 / 灰阶。
 */
object AgentStatusColors {
    val Idle = Color(0xFF9AA0A6)
    val Building = Color(0xFFC08A2E)
    val Running = Color(0xFF4F7F63)
    val Paused = Color(0xFF5B7C99)
    val Completed = Color(0xFF6F5F86)
    val Error = Color(0xFFB5544A)

    /**
     * 日志级别 Warn（琥珀色）—— 与「状态色」是两套语义：
     * 上面六色描述 capability 状态，本色描述日志条目级别（`Severity.Warn`）。
     * 收在此处是因为二者都靠颜色传达"需要留意"的程度，散在页面里写死会各自漂移
     * （F14-T3 ④ 就是看板日志弹窗内联 `Color(0xFFB26A00)` 的散点）。
     */
    val Warn = Color(0xFFB26A00)
}

fun agentStatusColor(status: CapabilityState.Status?): Color = when (status) {
    CapabilityState.Status.IDLE -> AgentStatusColors.Idle
    CapabilityState.Status.BUILDING -> AgentStatusColors.Building
    CapabilityState.Status.RUNNING -> AgentStatusColors.Running
    CapabilityState.Status.PAUSED -> AgentStatusColors.Paused
    CapabilityState.Status.COMPLETED -> AgentStatusColors.Completed
    CapabilityState.Status.ERROR -> AgentStatusColors.Error
    null -> AgentStatusColors.Idle
}

/** 状态文案（**纯文字，不带 emoji**）。 */
@Composable
fun agentStatusLabel(state: CapabilityState?): String = state?.let { s ->
    when (s.status) {
        CapabilityState.Status.IDLE -> stringResource(Res.string.agent_status_idle)
        CapabilityState.Status.BUILDING -> stringResource(Res.string.agent_status_building)
        CapabilityState.Status.RUNNING -> stringResource(Res.string.agent_status_running)
        CapabilityState.Status.PAUSED -> stringResource(Res.string.agent_status_paused)
        CapabilityState.Status.COMPLETED -> stringResource(Res.string.agent_status_completed)
        CapabilityState.Status.ERROR -> stringResource(Res.string.agent_status_error)
    }
} ?: "—"

/**
 * 「当前在做什么」= CapabilityState.detail。
 * 与状态文案相同时视为**无增量**（例如 Hello 的 detail 就是「运行中」，状态行已写）→ 返回 null。
 */
@Composable
fun agentDetailOf(state: CapabilityState?): String? =
    state?.detail?.takeIf { it.isNotBlank() && it != agentStatusLabel(state) }
