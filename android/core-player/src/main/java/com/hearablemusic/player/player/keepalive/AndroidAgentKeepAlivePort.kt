package com.hearablemusic.player.player.keepalive

import android.content.Context
import android.content.Intent
import android.os.Build
import com.hmp.domain.agent.port.AgentKeepAlivePort
import com.hmp.domain.agent.port.KeepAliveReason
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import com.hearablemusic.player.player.service.MusicPlayService

/**
 * Android 侧 agent 保活实现（F11-L2）。
 *
 * 把 domain 层的保活诉求转成对播放前台服务的指令：服务据此在
 * 「音频在播 **或** agent 保活」时保持前台，令进程在电台活跃期间
 * （**含"等模型出队列"的无音频窗口**）不被系统回收。
 *
 * 背景与根因见 `docs/7_x/B agent-build/design/agent-lifecycle.md`。
 *
 * 说明：保活/撤销通常由用户手势在**前台**触发（开/关电台），满足 Android 12+
 * 的前台服务启动限制；后台触发失败时静默降级（保持现状，偏向"多保护"一侧）。
 */
class AndroidAgentKeepAlivePort(private val context: Context) : AgentKeepAlivePort {

    override fun setKeepAlive(reason: KeepAliveReason, active: Boolean) {
        runCatching {
            val intent = Intent(context, MusicPlayService::class.java).apply {
                action = MusicPlayService.ACTION_AGENT_KEEPALIVE
                putExtra(MusicPlayService.EXTRA_AGENT_ACTIVE, active)
            }
            if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 声明保活：拉起前台服务（进程受保护）
                context.startForegroundService(intent)
            } else {
                // 撤销保活 / 旧版本：普通投递（服务内部据状态决定是否撤前台）
                context.startService(intent)
            }
            HmpLog.i(LogTag.PlayerService) { "📡 AgentKeepAlive($reason) → active=$active" }
        }.onFailure { e ->
            HmpLog.w(LogTag.PlayerService, e) { "📡 AgentKeepAlive($reason, active=$active) failed (non-fatal)" }
        }
    }
}
