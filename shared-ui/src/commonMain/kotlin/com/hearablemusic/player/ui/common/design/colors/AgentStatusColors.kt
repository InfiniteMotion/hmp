package com.hearablemusic.player.ui.common.design.colors

import androidx.compose.ui.graphics.Color

/**
 * Agent 状态色板（**低饱和**）。
 *
 * Material 原色（#4CAF50 / #F44336 等）饱和度高、"工程感"重；
 * 这里只让颜色承担"区分状态"的职责，层级交给字号 / 字重 / 灰阶。
 *
 * 从 `agent/AgentVisuals.kt` 迁入：它本来就是语义令牌，放在页面目录下反而
 * 会被设计 token 守门当成散落色值。
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
