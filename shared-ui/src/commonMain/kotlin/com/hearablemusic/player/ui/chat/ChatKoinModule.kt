package com.hearablemusic.player.ui.chat

import com.hmp.domain.agent.engine.EngineDefaults
import com.hmp.domain.agent.port.AiExtraEnrichPort
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * M5-T2 对话引擎接缝的三端共享 Koin 模块。
 *
 * 本模块放置 [ChatAgentGateway] 及其引擎侧依赖的接线（与平台桥无关，可跨 android/desktop/ios 复用）。
 * - [LlmTransport]：openai 兼容流式传输（:shared 注册）。
 * - [AiExtraEnrichPort]：真实实现，用当前生效 AI 配置 + MusicRepository 富化单曲。
 * - 播放上下文 / 播放指令两端口目前仅有 Fake 占位（M4 引擎测试同款）；真实播放桥在 M6
 *   存在感接线时替换。audit 适配器（RoomAuditLogAdapter）由各平台平台模块注册。
 */
val chatGatewayModule = module {
    single { ChatEntryBroker() }
    single { MusicServiceEnrichPort(get(), get()) } bind AiExtraEnrichPort::class
    // R-T3：真实播放/现在听端口（复用 PlaybackController 桥，替换 M5 的 Fake 占位）
    single { ControllerNowPlayingProvider(get()) } bind NowPlayingContextProvider::class
    single { ControllerPlaybackCommandPort(get(), get()) } bind PlaybackCommandPort::class
    single {
        ToolDependencies(
            musicRepository = get(),
            playlistRepository = get(),
            nowPlayingContextProvider = get(),
            playbackCommandPort = get(),
            enrichPort = get(),
        )
    }
    single<ChatAgentGateway> {
        OrchestratorChatGateway(
            transport = get(),
            toolDeps = get(),
            auditLog = get(),
            dailyCloudQuota = EngineDefaults.DAILY_CLOUD_QUOTA,
            nowPlayingProvider = get(),
            musicRepository = get(),
            agentMessageStore = get(),
        )
    }
}

/**
 * [AiExtraEnrichPort] 生产实现：按接线方注入的 UserSettingsUseCase 取当前生效 AI 端点，
 * 调 MusicRepository 完成单曲富化（对应 AiExtraEnrichPort 文档规定的接线方式）。
 */
class MusicServiceEnrichPort(
    private val musicRepository: MusicRepository,
    private val userSettings: UserSettingsUseCase,
) : AiExtraEnrichPort {
    override suspend fun enrich(title: String, artist: String): Result<com.hmp.domain.setting.model.DailyMusicInfo> {
        val config = userSettings.getActiveAiConfig()
        return musicRepository.fetchMusicExtraInfoWithProvider(config, title, artist)
    }
}