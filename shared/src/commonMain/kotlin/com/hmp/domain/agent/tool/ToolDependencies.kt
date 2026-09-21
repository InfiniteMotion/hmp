package com.hmp.domain.agent.tool
import com.hmp.domain.agent.tool.spec.ToolRegistry

import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository

/**
 * 工具层数据访问依赖聚合（S 阶段，支持 5 能力域 19 个原子工具）。
 *
 * 三端接线方在 DI 中组装真实实现；测试注入 Fake。
 * 播放控制/当前上下文经 [com.hmp.domain.agent.port] 端口，:shared 不反向依赖 shared-ui。
 *
 * ⚠️ 2026-09-20 移除 `enrichPort: AiExtraEnrichPort` —— 它是 M3 `enrichSong` 工具的依赖，
 * 富化管道内化到 `EnrichSubAgent`（走 `AgentContextBudget.callLlmText`）后该工具撤销，
 * 端口随之成为**零生产调用者的死链**：只剩 UI 侧一个 `@Deprecated` 桩（实现直接
 * `Result.failure`）在 Koin 里占着位。连同接口、桩、Fake 一并删除。
 */
data class ToolDependencies(
    val musicRepository: MusicRepository,
    val playlistRepository: PlaylistRepository,
    val settingsRepository: SettingsRepository,
    val nowPlayingContextProvider: NowPlayingContextProvider,
    val playbackCommandPort: PlaybackCommandPort,
    /**
     * 用户认识模块（画像）—— 可空 Provider：默认 `{ null }`，测试与旧装配路径不传也能编译，
     * 画像工具在缺省时返回"模块未启用"，而不是崩。
     * 用 Provider 是为了打破 Koin 循环依赖：ToolDependencies → MasterAgent → ToolRegistry → ToolDependencies。
     */
    val userMemory: () -> com.hmp.domain.agent.profile.UserMemory? = { null },
)
