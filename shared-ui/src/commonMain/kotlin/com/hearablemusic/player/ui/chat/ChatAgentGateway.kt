package com.hearablemusic.player.ui.chat

import com.hmp.domain.agent.engine.AgentLog
import com.hmp.domain.agent.engine.AgentOrchestrator
import com.hmp.domain.agent.engine.ConfirmGate
import com.hmp.domain.agent.engine.ConfirmRequest
import com.hmp.domain.agent.engine.ContextAssembler
import com.hmp.domain.agent.engine.ContextBudget
import com.hmp.domain.agent.engine.LibraryOverview
import com.hmp.domain.agent.engine.PolicyGuard
import com.hmp.domain.agent.engine.PresenceBus
import com.hmp.domain.agent.engine.RunContextInput
import com.hmp.domain.agent.engine.SessionStore
import com.hmp.domain.agent.engine.TerminationReason
import com.hmp.domain.agent.engine.TrustLedger
import com.hmp.domain.agent.persona.DefaultCompanionProfiles
import com.hmp.domain.agent.port.AgentMessageStore
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hearablemusic.player.ui.platform.currentTimeMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * M5-T1 对话页 UI 面向引擎的接缝事件（真实网关经 AgentOrchestrator 产出；单测注入 Fake 网关）。
 */
sealed interface ChatAgentEvent {
    /** 引擎请求确认：携带本轮全部待确认项，UI 据此渲染确认卡并令 [ConfirmBridge.submit] 恢复引擎。 */
    data class NeedConfirm(val turnId: String, val requests: List<ConfirmRequest>) : ChatAgentEvent
    /** 单条工具执行回执（写审计成功后送达）。 */
    data class ToolExecuted(val record: com.hmp.domain.agent.engine.ToolExecutionRecord) : ChatAgentEvent
    /** 单轮结束：最终答复文本 + 全量执行记录 + 终止原因。 */
    data class Finished(
        val text: String,
        val toolCalls: List<com.hmp.domain.agent.engine.ToolExecutionRecord>,
        val terminatedBy: TerminationReason,
    ) : ChatAgentEvent

    data class Failed(val message: String) : ChatAgentEvent
}

/**
 * 确认桥：引擎侧 suspend ConfirmGate 与 UI 之间的握手。
 *
 * 引擎在 [ConfirmGate.request] 中挂起等待用户勾选；本桥经 [onRequest] 向 UI 抛
 * [ChatAgentEvent.NeedConfirm]，UI 勾选后调 [submit] 用批准列表恢复引擎。
 * [CompletableDeferred] 跨协程线程安全，客服 ContinuousFlow/VM 均可安全调用。
 */
class ConfirmBridge {
    private val pending = LinkedHashMap<String, CompletableDeferred<List<Boolean>>>()
    private var counter = 0
    /** 向 UI 请求确认的回调（由网关在 flow 生产者上下文中注入，可 emit）。 */
    var onRequest: (suspend (String, List<ConfirmRequest>) -> Unit)? = null

    /** 引擎侧：登记一批待确认项并挂起，直到 [submit] 提供同序批准列表。 */
    suspend fun awaitApprovals(requests: List<ConfirmRequest>): List<Boolean> {
        val turnId = "confirm_${counter++}"
        val deferred = CompletableDeferred<List<Boolean>>()
        pending[turnId] = deferred
        AgentLog.i("confirm await: turn=$turnId items=${requests.size} waiting...")
        onRequest?.invoke(turnId, requests)
        return deferred.await()
    }

    /**
     * UI 侧：提交批准列表，恢复对应 [turnId] 的挂起。
     * @return true 该批次仍待确认（成功投递）；false 该批次已关闭（竞态/已取消）。
     */
    fun submit(turnId: String, approvals: List<Boolean>): Boolean =
        pending.remove(turnId)?.let {
            AgentLog.i("confirm submit: turn=$turnId approvals=$approvals")
            it.complete(approvals)
        } ?: false.also { AgentLog.w("confirm submit: turn=$turnId 已关闭/取消") }
}

/** 对话引擎接缝（M5-T1）：把一次用户输入换算为面向 UI 的事件流。 */
interface ChatAgentGateway {
    /**
     * 启动一轮对话。
     * @param input 用户输入
     * @param config 生效 AI 端点配置（FREE 内置 / CUSTOM）
     * @param bridge 确认桥（本轮引擎 — UI 握手）；NeedConfirm 事件出现时 UI 用其 [ConfirmBridge.submit] 恢复者
     * @return 事件流；消费协程取消即取消引擎（步数预算熔断以 cancel 实现）
     */
    fun run(
        input: String,
        config: AiEndpointConfig,
        bridge: ConfirmBridge,
        ctx: RunContextInput = RunContextInput(),
    ): Flow<ChatAgentEvent>
}

/**
 * 真实网关：每轮装配 [AgentOrchestrator]（注入的传输/工具依赖/审计），桥接确认门到 [ConfirmBridge]。
 *
 * 与 M4 测试的约定一致：信任账本/护栏/预算/会话/存在感在该轮内一次性创建（M5 对话页首航线，
 * 跨会话持久化的信任阶梯/云端额度留 M6 存在感接线时上浮）。
 */
class OrchestratorChatGateway(
    private val transport: LlmTransport,
    private val toolDeps: ToolDependencies,
    private val auditLog: AuditLogPort,
    private val dailyCloudQuota: Int,
    private val nowPlayingProvider: NowPlayingContextProvider,
    private val musicRepository: MusicRepository,
    private val agentMessageStore: AgentMessageStore,
) : ChatAgentGateway {

    override fun run(
        input: String,
        config: AiEndpointConfig,
        bridge: ConfirmBridge,
        ctx: RunContextInput,
    ): Flow<ChatAgentEvent> = flow {
        val registry = ToolRegistry.create(toolDeps)
        val ledger = TrustLedger()
        val policy = PolicyGuard(ledger, auditLog)
        val budget = ContextBudget({ timeMillis() }, dailyCloudQuota = dailyCloudQuota)
        val session = SessionStore({ timeMillis() })
        val presence = PresenceBus()
        bridge.onRequest = { turnId, requests -> emit(ChatAgentEvent.NeedConfirm(turnId, requests)) }
        val confirmGate = ConfirmGate { requests -> bridge.awaitApprovals(requests) }
        val orchestrator = AgentOrchestrator(
            transport = transport,
            registry = registry,
            policyGuard = policy,
            contextBudget = budget,
            sessionStore = session,
            presenceBus = presence,
            auditLog = auditLog,
            confirmGate = confirmGate,
        )
        try {
            val effectiveCtx = mergeFirstTurnContext(ctx, input)
            val result = orchestrator.run(input, config, effectiveCtx)
            result.toolCalls.forEach { emit(ChatAgentEvent.ToolExecuted(it)) }
            emit(ChatAgentEvent.Finished(result.text, result.toolCalls, result.terminatedBy))
        } catch (ce: CancellationException) {
            throw ce // 预算熔断 / 用户取消经 cancel 实现，避免误判失败
        } catch (e: Exception) {
            emit(ChatAgentEvent.Failed(e.message ?: "对话失败"))
        }
    }

    /** R-T1 接缝：把首轮上下文（人格/当前曲目/时段/曲库概况/认识进度）+ 跨轮记忆合并进调用方传入的 ctx。 */
    private suspend fun mergeFirstTurnContext(ctx: RunContextInput, input: String): RunContextInput {
        val first = buildFirstTurnContext()
        return ctx.copy(
            personaText = ctx.personaText ?: first.personaText,
            recognitionText = ctx.recognitionText ?: first.recognitionText,
            timeOfDayText = ctx.timeOfDayText ?: first.timeOfDayText,
            nowPlayingText = ctx.nowPlayingText ?: first.nowPlayingText,
            libraryOverviewText = ctx.libraryOverviewText ?: first.libraryOverviewText,
            history = ctx.history.ifEmpty { buildHistory(input) },
        )
    }

    /** 跨轮记忆：加载最近会话的先前 user/assistant 文本消息（去重当前输入，避免与本次 userMessage 重复）。 */
    private suspend fun buildHistory(input: String): List<LlmMessage> {
        val sid = agentMessageStore.currentOrNewSessionId()
        val mapped = agentMessageStore.loadSession(sid, HISTORY_LIMIT).mapNotNull { m ->
            val role = when (m.role) {
                "user" -> "user"
                "agent", "assistant" -> "assistant"
                else -> null
            } ?: return@mapNotNull null
            LlmMessage(role = role, content = m.content)
        }
        // 当前输入已被持久化 → 若历史末尾恰是该 user 文本，则去掉（避免与 userMessage 重复）
        val deduped =
            if (mapped.lastOrNull()?.role == "user" && mapped.lastOrNull()?.content == input) mapped.dropLast(1) else mapped
        AgentLog.i("history loaded: ${deduped.size} 条（含去重 ${mapped.size - deduped.size}）")
        return deduped
    }

    private companion object {
        const val HISTORY_LIMIT = 30
    }

    /** 从真实数据装配首轮上下文（R-T1）。 */
    private suspend fun buildFirstTurnContext(): RunContextInput {
        val now = nowPlayingProvider.getNowPlaying()
        val nowPlayingText = ContextAssembler.buildNowPlaying(
            currentTitle = now.currentMusicInfo?.music?.title,
            currentArtist = now.currentMusicInfo?.music?.artist,
            isPlaying = now.isPlaying,
            currentPositionMs = now.currentPositionMs,
            durationMs = now.durationMs,
        )
        val total = musicRepository.getMusicCount().first()
        val known = musicRepository.getMusicWithExtraCount().first()
        val recognitionText = ContextAssembler.buildRecognitionProgress(known, total)
        // v0 近似（UTC 小时）；本地时段待平台化（PlatformTime 仅提供 UTC 毫秒）
        val hour = ((currentTimeMillis() / 3_600_000L) % 24).toInt()
        val overviewText = ContextAssembler.buildLibraryOverview(buildLibraryOverview())
        AgentLog.i("first-turn ctx: persona=${DefaultCompanionProfiles.DEFAULT.personaName} nowPlaying=${now.currentMusicInfo?.music?.title ?: "无"} recognized=$known/$total")
        return RunContextInput(
            personaText = DefaultCompanionProfiles.DEFAULT.personaPrompt,
            recognitionText = recognitionText,
            timeOfDayText = ContextAssembler.buildTimeOfDay(hour),
            nowPlayingText = nowPlayingText,
            libraryOverviewText = overviewText,
        )
    }

    /** 曲库概况（概览法，v0：规模 + 常听 + 流派 top；语言/年代分布待数据源补全）。 */
    private suspend fun buildLibraryOverview(): LibraryOverview {
        val total = musicRepository.getMusicCount().first()
        val analytics = musicRepository.getUserUsageAnalytics()
        return LibraryOverview(
            totalCount = total,
            topGenres = analytics.topGenres.map { it.labelDisplayName to it.count },
            topPlayedSongs = analytics.topPlayedSongs.map { it.title to it.artist },
        )
    }

    private fun timeMillis(): Long = currentTimeMillis()
}