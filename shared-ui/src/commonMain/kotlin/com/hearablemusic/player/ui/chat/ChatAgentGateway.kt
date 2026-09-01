package com.hearablemusic.player.ui.chat

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.runtime.ConfirmGate
import com.hmp.domain.agent.runtime.ConfirmOutcome
import com.hmp.domain.agent.runtime.ConfirmRequest
import com.hmp.domain.agent.runtime.LibraryOverview
import com.hmp.domain.agent.runtime.RunContextInput
import com.hmp.domain.agent.runtime.TerminationReason
import com.hmp.domain.agent.runtime.ToolExecutionRecord
import com.hmp.domain.agent.persona.DefaultCompanionProfiles
import com.hmp.domain.agent.port.AgentMessageStore
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.runtime.ContextAssembler
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hearablemusic.player.ui.platform.currentTimeMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * 对话页 UI 面向 Agent 的接缝事件（真实网关经 MasterChatGateway → MasterAgent 产出；单测注入 Fake 网关）。
 */
sealed interface ChatAgentEvent {
    /** 引擎请求确认：携带本轮全部待确认项，UI 据此渲染确认卡并令 [ConfirmBridge.submit] 恢复引擎。 */
    data class NeedConfirm(val turnId: String, val requests: List<ConfirmRequest>) : ChatAgentEvent
    /** 单条工具执行回执（写审计成功后送达）。 */
    data class ToolExecuted(val record: ToolExecutionRecord) : ChatAgentEvent
    /** 单轮结束：最终答复文本 + 全量执行记录 + 终止原因。 */
    data class Finished(
        val text: String,
        val toolCalls: List<ToolExecutionRecord>,
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
    private val pending = LinkedHashMap<String, CompletableDeferred<List<ConfirmOutcome>>>()
    private var counter = 0
    /** 向 UI 请求确认的回调（由网关在 flow 生产者上下文中注入，可 emit）。 */
    var onRequest: (suspend (String, List<ConfirmRequest>) -> Unit)? = null

    /** 引擎侧：登记一批待确认项并挂起，直到 [submit] 提供同序决策列表。 */
    suspend fun awaitApprovals(requests: List<ConfirmRequest>): List<ConfirmOutcome> {
        val turnId = "confirm_${counter++}"
        val deferred = CompletableDeferred<List<ConfirmOutcome>>()
        pending[turnId] = deferred
        Logger.i("Agent.Gateway") { "confirm await: turn=$turnId items=${requests.size} waiting..." }
        onRequest?.invoke(turnId, requests)
        return deferred.await()
    }

    /**
     * UI 侧：提交决策列表，恢复对应 [turnId] 的挂起。
     * @return true 该批次仍待确认（成功投递）；false 该批次已关闭（竞态/已取消）。
     */
    fun submit(turnId: String, outcomes: List<ConfirmOutcome>): Boolean =
        pending.remove(turnId)?.let {
            Logger.i("Agent.Gateway") { "confirm submit: turn=$turnId outcomes=$outcomes" }
            it.complete(outcomes)
        } ?: false.also { Logger.w("Agent.Gateway") { "confirm submit: turn=$turnId 已关闭/取消" } }
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

// ═══════════════════════════════════════════════════════════════════════
// T 阶段整合：MasterChatGateway —— 薄壳，对话能力由 MasterAgent 直接提供
// 不创建 ToolRegistry / PolicyGuard / Transport / ContextBudget 等
// —— 全从注入的 masterAgent 拿（已在 Koin 里构造好，ToolRegistry 含 5 个 enrich_*）
// ═══════════════════════════════════════════════════════════════════════

/**
 * 唯一网关：对话能力由 MasterAgent.handleUserMessage() 提供。
 *
 * Gateway 只做 UI 侧的上下文组装（首轮/跨轮记忆合并）+ ConfirmGate 桥接；
 * 全链路 Token 统一走 GlobalTokenCounter（MasterAgent 内部），
 * ToolRegistry 含 27 基础 + 5 enrich_* 工具（MasterAgent.init{} 自动注册）。
 */
class MasterChatGateway(
    private val masterAgent: MasterAgent,
    private val auditLog: com.hmp.domain.agent.port.AuditLogPort,
    private val nowPlayingProvider: com.hmp.domain.agent.port.NowPlayingContextProvider,
    private val musicRepository: MusicRepository,
    private val agentMessageStore: com.hmp.domain.agent.port.AgentMessageStore,
) : ChatAgentGateway {

    override fun run(
        input: String,
        config: AiEndpointConfig,
        bridge: ConfirmBridge,
        ctx: RunContextInput,
    ): Flow<ChatAgentEvent> = kotlinx.coroutines.flow.flow {
        bridge.onRequest = { turnId, requests -> emit(ChatAgentEvent.NeedConfirm(turnId, requests)) }
        val confirmGate = ConfirmGate { requests -> bridge.awaitApprovals(requests) }
        try {
            val effectiveCtx = mergeFirstTurnContext(ctx, input)
            val result = masterAgent.handleUserMessage(input, config, effectiveCtx, confirmGate)
            result.toolCalls.forEach { emit(ChatAgentEvent.ToolExecuted(it)) }
            emit(ChatAgentEvent.Finished(result.text, result.toolCalls, result.terminatedBy))
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            emit(ChatAgentEvent.Failed(e.message ?: "对话失败"))
        }
    }

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

    private suspend fun buildHistory(input: String): List<LlmMessage> {
        val sid = agentMessageStore.currentOrNewSessionId()
        val mapped = agentMessageStore.loadSession(sid, 30).mapNotNull { m ->
            val role = when (m.role) {
                "user" -> "user"
                "agent", "assistant" -> "assistant"
                else -> null
            } ?: return@mapNotNull null
            LlmMessage(role = role, content = m.content)
        }
        val deduped = if (mapped.lastOrNull()?.role == "user" && mapped.lastOrNull()?.content == input) mapped.dropLast(1) else mapped
        Logger.i("Agent.Gateway") { "history loaded: ${deduped.size} 条（含去重 ${mapped.size - deduped.size}）" }
        return deduped
    }

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
        val hour = ((currentTimeMillis() / 3_600_000L) % 24).toInt()
        val overviewText = ContextAssembler.buildLibraryOverview(buildLibraryOverview())
        Logger.i("Agent.Gateway") { "first-turn ctx: persona=${DefaultCompanionProfiles.DEFAULT.personaName} nowPlaying=${now.currentMusicInfo?.music?.title ?: "无"} recognized=$known/$total" }
        return RunContextInput(
            personaText = DefaultCompanionProfiles.DEFAULT.personaPrompt,
            recognitionText = recognitionText,
            timeOfDayText = ContextAssembler.buildTimeOfDay(hour),
            nowPlayingText = nowPlayingText,
            libraryOverviewText = overviewText,
        )
    }

    private suspend fun buildLibraryOverview(): LibraryOverview {
        val total = musicRepository.getMusicCount().first()
        val analytics = musicRepository.getUserUsageAnalytics()
        return LibraryOverview(
            totalCount = total,
            topGenres = analytics.topGenres.map { it.labelDisplayName to it.count },
            topPlayedSongs = analytics.topPlayedSongs.map { it.title to it.artist },
        )
    }
}
