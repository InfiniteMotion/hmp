package com.hearablemusic.player.player.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

class MusicNotificationReceiver : BroadcastReceiver() {
    @OptIn(UnstableApi::class)
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val serviceIntent = Intent(context, MusicPlayService::class.java).apply {
            this.action = action
        }
        HmpLog.d(LogTag.PlayerService) { "📡 接收到操作: $action" }
        
        // API 26+ 需要使用 startForegroundService
        try {
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            HmpLog.e(LogTag.PlayerService) { "📡 启动服务失败: ${e.message}" }
        }
    }
}
