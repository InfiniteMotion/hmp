package com.hearablemusic.player.ui.chat

import com.hmp.data.di.AGENT_CHAT
import com.hmp.data.di.AGENT_ENRICH
import com.hmp.data.di.AGENT_HELLO
import com.hmp.data.di.AGENT_RADIO
import com.hmp.domain.agent.runtime.EngineDefaults
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.SessionStore
import com.hmp.domain.agent.policy.TrustLedger
import com.hmp.domain.agent.port.AiExtraEnrichPort
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.port.AgentKeepAlivePort
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.UserSettingsUseCase
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
    single { MusicServiceEnrichPort(get(), get()) } bind AiExtraEnrichPort::class
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
            enrichPort = get(),
            // v3.6：画像归属 Master（masterAgent.userMemory），工具面从这里取同一份
            // 用 Provider lambda 延迟取值——打破 ToolDependencies → MasterAgent → ToolRegistry → ToolDependencies 静态循环
            userMemory = { get<MasterAgent>().userMemory },
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
        // 方案 B：4 个独立 Transport 实例（共享同一个 HttpClient 连接池）
        val chatTransport = get<LlmTransport>(named(AGENT_CHAT))
        val enrichTransport = get<LlmTransport>(named(AGENT_ENRICH))
        val helloTransport = get<LlmTransport>(named(AGENT_HELLO))
        val radioTransport = get<LlmTransport>(named(AGENT_RADIO))
        MasterAgent(
            timeProvider = { currentTimeMillis() },
            // F12-T1：注入共享单例，确保计量与配额熔断读同一份计数（meter 同 bean）
            tokenCounter = get(),
            tokenMeter = get(),
            musicRepository = get(),
            // 对话依赖
            chatTransport = chatTransport,
            chatToolRegistry = get(),
            chatPolicyGuard = get(),
            chatAuditLog = get(),
            chatSessionStore = get(),
            chatPresenceBus = get(),
            observationBus = get(),
            // F11-L1/L2：agent 保活端口（Android 有实现；iOS/Desktop 未提供 → null，静默跳过）
            keepAlivePort = getOrNull(),
            stepBudget = EngineDefaults.STEP_BUDGET,
            // Enrich 后台依赖（独立 Transport）
            enrichTransport = enrichTransport,
            enrichConfig = enrichConfig,
            // Radio 电台依赖（独立 Transport）
            radioTransport = radioTransport,
            playbackPort = get(),
            nowPlayingProvider = get(),
            // Hello 门面依赖（独立 Transport）
            helloTransport = helloTransport,
            // W0: HelloSubAgent 持久化 DAO（启用则卡片池 + 报告叙事段落 Room；不注入自动降级内存）
            helloCardCacheDao = get(),
            helloReportNarrativeDao = get(),
            // Agent 配置持久化（trustLevel + alwaysAllow DataStore 读写）
            settingsRepo = settingsRepo,
            // 用户认识模块（画像）—— v3.6 起归属 Master：传三 DAO 由其内部装配，
            // 上面的 ToolDependencies / 下面的 Gateway 都从 `masterAgent.userMemory` 取同一份
            userProfileEvidenceDao = get(),
            userProfilePortraitDao = get(),
            userProfileNarrativeDao = get(),
        ).also { master ->
            // F9-A0: bindCapabilityTools 已移到 MasterAgent.initialize() 里
            // （避免在 Koin single 创建过程中再 get<ToolRegistry>() 引发循环依赖）
            master.lifecycleScope.launch {
                runCatching { master.initialize() }
                    .onFailure { e -> HmpLog.w(LogTag.AgentMaster, e) { "🤖 initialize failed | non-fatal | reason=${e.message}" } }
            }
            // AI 配置热监听：**生效配置真变**时才推给 MasterAgent（只在真变时重载）。
            //
            // ⚠️ 必须 `distinctUntilChanged()`：两个上游都派生自全局单例 `dataStore.data`，
            // 任何无关写入都会让它重发 —— 例如播放进度持久化（`saveCurrentPosition`，数秒一次）。
            // 少了这一步，会在后台反复触发 `updateAiConfig` → 日志刷屏 + 无谓的 per-Agent 配置重载。
            master.lifecycleScope.launch {
                // 上游各自去重：无关写入时连 transform（含 CUSTOM 模式的 AES 解密）都不再触发；
                // 末端再去重一层，确保只在"生效配置"真变时才重载。
                kotlinx.coroutines.flow.combine(
                    settingsRepo.aiAccessMode.distinctUntilChanged(),
                    settingsRepo.customAiConfig.distinctUntilChanged(),
                ) { _, _ ->
                    runCatching { settingsRepo.getActiveAiConfig() }.getOrNull()
                }
                    .distinctUntilChanged()
                    .collect { activeConfig ->
                        master.updateAiConfig()
                        HmpLog.i(LogTag.AgentMaster) {
                            "🤖 AI config changed → reloaded (endpoint=${activeConfig?.endpoint?.take(40) ?: "(none)"}, model=${activeConfig?.selectedModel ?: "-"})"
                        }
                    }
            }
        }
    }

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