package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.AiExtraEnrichPort
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository

/**
 * 工具层数据访问依赖聚合（S 阶段，支持 5 能力域 19 个原子工具）。
 *
 * 三端接线方在 DI 中组装真实实现；测试注入 Fake。
 * 播放控制/当前上下文/AI 富化均经 [com.hmp.domain.agent.port] 端口，:shared 不反向依赖 shared-ui。
 */
data class ToolDependencies(
    val musicRepository: MusicRepository,
    val playlistRepository: PlaylistRepository,
    val settingsRepository: SettingsRepository,
    val nowPlayingContextProvider: NowPlayingContextProvider,
    val playbackCommandPort: PlaybackCommandPort,
    val enrichPort: AiExtraEnrichPort,
    /**
     * 用户认识模块（画像）—— 可空 Provider：默认 `{ null }`，测试与旧装配路径不传也能编译，
     * 画像工具在缺省时返回"模块未启用"，而不是崩。
     * 用 Provider 是为了打破 Koin 循环依赖：ToolDependencies → MasterAgent → ToolRegistry → ToolDependencies。
     */
    val userMemory: () -> com.hmp.domain.agent.profile.UserMemory? = { null },
)
