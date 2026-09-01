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
     * T 阶段新增：MasterAgent 对工具层的窄接口（可空）。
     * - chatbot 模式（无 Master）：null → ToolRegistry 不注册 enrich_* 工具
     * - Master 模式：MasterAgent 实现此接口并注入 → ToolRegistry 额外注册 5 个 enrich_* 工具
     */
    val masterAgentFacade: MasterAgentFacade? = null,
)
