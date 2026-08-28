package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.AiExtraEnrichPort
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository

/**
 * 工具层数据访问依赖聚合。
 *
 * M3-T2 十个工具共享这些依赖，构造时注入（三端接线方在 DI 中组装真实实现；测试注入 Fake）。
 * 播放控制/当前上下文/AI 富化均经 [com.hmp.domain.agent.port] 端口，:shared 不反向依赖 shared-ui。
 */
data class ToolDependencies(
    val musicRepository: MusicRepository,
    val playlistRepository: PlaylistRepository,
    val nowPlayingContextProvider: NowPlayingContextProvider,
    val playbackCommandPort: PlaybackCommandPort,
    val enrichPort: AiExtraEnrichPort,
)