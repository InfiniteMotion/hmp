package com.hearablemusic.player.player.di

import com.hearablemusic.player.player.controller.MusicController
import com.hearablemusic.player.player.keepalive.AndroidAgentKeepAlivePort
import com.hmp.domain.agent.port.AgentKeepAlivePort
import com.hmp.domain.agent.port.PlaybackObservationBus
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val playerModule = module {
    single {
        MusicController(androidContext(), get(), get(), get(), get(), get()).also {
            // 观测面：把 shared 侧的总线注入控制器，由其在会话结算点回调。
            // 用属性注入而非构造参数 —— 控制器已有 6 个依赖，且既有测试按 6 参构造。
            it.playbackObserver = get<PlaybackObservationBus>()
        }
    }

    // F11-L2：agent 保活端口（Android）—— MasterAgent 据其保持进程存活
    single<AgentKeepAlivePort> { AndroidAgentKeepAlivePort(androidContext()) }
}
