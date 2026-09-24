package com.hearablemusic.player.ui.agent.chat
import com.hmp.domain.agent.tool.createBaseToolRegistry

import com.hmp.data.di.AGENT_CHAT
import com.hmp.data.di.AGENT_ENRICH
import com.hmp.data.di.AGENT_HELLO
import com.hmp.data.di.AGENT_RADIO
import com.hmp.domain.agent.config.EngineDefaults
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.SessionStore
import com.hmp.domain.agent.policy.TrustLedger
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.port.AgentKeepAlivePort
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.agent.tool.spec.ToolRegistry
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import com.hearablemusic.player.ui.agent.port.ControllerNowPlayingProvider
import com.hearablemusic.player.ui.agent.port.ControllerPlaybackCommandPort
import com.hearablemusic.player.ui.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

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
    // R-T3：真实播放/现在听端口
    single { ControllerNowPlayingProvider(get()) } bind NowPlayingContextProvider::class
    single { ControllerPlaybackCommandPort(get(), get(), get()) } bind PlaybackCommandPort::class

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
            // v3.6：画像归属 Master（masterAgent.userMemory），工具面从这里取同一份
            // 用 Provider lambda 延迟取值——打破 ToolDependencies → MasterAgent → ToolRegistry → ToolDependencies 静态循环
            userMemory = { get<MasterAgent>().userMemory },
        )
    }
    single { createBaseToolRegistry(get()) }

    // ChatAgentGateway 接口绑定到 MasterChatGateway（薄壳）
    single<ChatAgentGateway> {
        MasterChatGateway(
            masterAgent = get(),
            auditLog = get(),
            nowPlayingProvider = get(),
            musicRepository = get(),
            agentMessageStore = get(),
            // v3.6：画像归属 Master，Gateway 从 masterAgent.userMemory 取同一份
            userMemory = get<MasterAgent>().userMemory,
        )
    }
}