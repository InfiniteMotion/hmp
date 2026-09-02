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
 * MasterAgent 作为唯一大脑，统一对话能力（handleUserMessage + 内建意图路由）和
 * 后台 SubAgent 管理（enrichTaskLoop + Scheduler）。
 *
 * SubAgent 生命周期管理（enrich pause/resume/status、radio start/stop）
 * 已重构为 MasterAgent.handleUserMessage() 内建意图路由——不经过 LLM、不注册为工具。
 *
 * Koin 依赖顺序：
 * ToolDependencies → ToolRegistry（27 基础工具，无 enrich_*）
 *                 → MasterAgent（持有 ToolRegistry + chatTransport + ...）
 *                 → MasterChatGateway（薄壳，调 masterAgent.handleUserMessage）
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
            // Radio 电台依赖（M6-T1）
            playbackPort = get(),
            nowPlayingProvider = get(),
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
 * [AiExtraEnrichPort] 生产实现。
 *
 * @deprecated 富化管道已内化到 EnrichSubAgent（走 contextBudget.callLlmText），
 *   不再通过此 Port。保留仅为编译兼容，返回 Result.failure。
 */
@Deprecated("富化管道已内化到 EnrichSubAgent，此 Port 不再使用")
class MusicServiceEnrichPort(
    private val musicRepository: MusicRepository,
    private val userSettings: UserSettingsUseCase,
) : AiExtraEnrichPort {
    override suspend fun enrich(title: String, artist: String): Result<com.hmp.domain.setting.model.DailyMusicInfo> {
        return Result.failure(IllegalStateException("MusicServiceEnrichPort is deprecated — enrich pipeline moved to EnrichSubAgent"))
    }
}