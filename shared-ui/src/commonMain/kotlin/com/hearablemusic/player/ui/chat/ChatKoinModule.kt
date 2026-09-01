package com.hearablemusic.player.ui.chat

import com.hmp.domain.agent.runtime.EngineDefaults
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.SessionStore
import com.hmp.domain.agent.policy.TrustLedger
import com.hmp.domain.agent.port.AiExtraEnrichPort
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import com.hearablemusic.player.ui.platform.currentTimeMillis
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * M5-T2 对话引擎接缝的三端共享 Koin 模块。
 *
 * T 阶段整合后：MasterAgent 作为唯一大脑统一对话能力（handleUserMessage）和
 * 后台 SubAgent 管理（enrichTaskLoop + enrich_* 工具 + Scheduler）。
 *
 * Koin 依赖顺序：
 * MasterAgent → ToolDependencies（无 masterAgentFacade 了，循环依赖已打破）
 *             → ToolRegistry → （MasterAgent.init 自动注册 enrich_*）
 *             → MasterChatGateway（薄壳，调 masterAgent.handleUserMessage）
 *
 * LlmTransport / AuditLogPort / AgentMessageStore 等由各平台模块注册。
 */
val chatGatewayModule = module {
    single { ChatEntryBroker() }
    single { MusicServiceEnrichPort(get(), get()) } bind AiExtraEnrichPort::class
    // R-T3：真实播放/现在听端口
    single { ControllerNowPlayingProvider(get()) } bind NowPlayingContextProvider::class
    single { ControllerPlaybackCommandPort(get(), get()) } bind PlaybackCommandPort::class

    // ── T 阶段整合：MasterAgent 作为唯一大脑 ──
    // TrustLedger / AgentPolicyConfig 不再走 Koin——MasterAgent.handleUserMessage 里创建 per-Agent 独立实例
    single { PolicyGuard(get()) }  // 只传 AuditLogPort
    single { SessionStore({ currentTimeMillis() }) }
    single { PresenceBus() }
    single {
        ToolDependencies(
            musicRepository = get(),
            playlistRepository = get(),
            settingsRepository = get(),
            nowPlayingContextProvider = get(),
            playbackCommandPort = get(),
            enrichPort = get(),
        )
    }
    single { ToolRegistry.create(get()) }

    // MasterAgent（对话 + 后台管理 + enrich_* 自动注册）
    single {
        val settingsRepo = get<SettingsRepository>()
        // 取一次当前 AI 端点配置（runBlocking：仅 Koin 初始化时阻塞一次，启动后配置变更需后续动态更新）
        val enrichConfig = kotlinx.coroutines.runBlocking {
            runCatching { settingsRepo.getActiveAiConfig() }.getOrNull()
        }
        MasterAgent(
            timeProvider = { currentTimeMillis() },
            tokenCounter = com.hmp.domain.agent.runtime.GlobalTokenCounter({ currentTimeMillis() }),
            musicRepository = get(),
            // 对话依赖
            chatTransport = get(),
            chatToolRegistry = get(),
            chatPolicyGuard = get(),
            chatAuditLog = get(),
            chatSessionStore = get(),
            chatPresenceBus = get(),
            stepBudget = EngineDefaults.STEP_BUDGET,
            // Enrich 后台依赖
            enrichConfig = enrichConfig,
            // Agent 配置持久化（trustLevel + alwaysAllow DataStore 读写）
            settingsRepo = settingsRepo,
        )
    }

    // ChatAgentGateway 接口绑定到 MasterChatGateway（薄壳）
    single<ChatAgentGateway> {
        MasterChatGateway(
            masterAgent = get(),
            auditLog = get(),
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