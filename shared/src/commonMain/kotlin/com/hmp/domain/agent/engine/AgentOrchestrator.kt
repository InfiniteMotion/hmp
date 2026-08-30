package com.hmp.domain.agent.engine

import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolCall
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.persona.DefaultCompanionProfiles
import com.hmp.domain.agent.tool.AgentTool
import com.hmp.domain.agent.tool.ToolPermissionLevel
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** 终止原因（熔断为何终止，入审计）。 */
enum class TerminationReason { ANSWERED, STEP_BUDGET_EXHAUSTED, CLOUD_QUOTA_EXHAUSTED, FAILED }

/** 一次工具执行记录（供结果回填与审计）。 */
data class ToolExecutionRecord(
    val toolName: String,
    val outcome: String, // success / failed / refused / skipped
    val summary: String,
)

/** Agent 任务结果（M4-T1）。 */
data class AgentResult(
    val text: String,
    val stepsUsed: Int,
    val toolCalls: List<ToolExecutionRecord>,
    val terminatedBy: TerminationReason,
)

/**
 * 一次待确认的工具调用（M5-T4 确认卡片：一次 turn 的多项确认聚合展示、逐项勾选）。
 * @param toolName 工具名（供卡片展示与审计）
 * @param argsSummary 参数摘要（供用户判断）
 * @param permissionLevel 许可级（CONFIRM / STRONG_CONFIRM）
 */
data class ConfirmRequest(
    val toolName: String,
    val argsSummary: String,
    val permissionLevel: ToolPermissionLevel,
)

/**
 * 确认门（M5 确认卡片流实现；M4 测试注入脚本化门：全通过 / 全否决 / 按工具）。
 * 批量语义：一次 turn 可能触发多项需要确认的工具，聚合为 [requests] 一次性请求，
 * 返回与传入**同序**的批准列表；`false`=该项跳过（拒绝纪律：本次会话不纠缠，不报错）。
 */
fun interface ConfirmGate {
    suspend fun request(requests: List<ConfirmRequest>): List<Boolean>
}

/** 一次运行的外部上下文输入（供 ContextBudget 三层组装 + R-T1 首轮注入）。 */
data class RunContextInput(
    val taskState: String? = null,
    val libraryListText: String? = null,
    val libraryOverviewText: String? = null,
    // —— R-T1 首轮注入（第一次对话就该到 agent 的内容）——
    val personaText: String? = null,
    val recognitionText: String? = null,
    val timeOfDayText: String? = null,
    val nowPlayingText: String? = null,
    val userTitle: String? = null,
    // —— 跨轮记忆：上一轮及以前的 user/assistant 文本消息（按时间正序，越新越靠后）——
    val history: List<LlmMessage> = emptyList(),
)

/**
 * M4-T1 AgentOrchestrator —— 引擎唯一循环。
 *
 * 循环契约（总纲 6.1 + M2 review 协议形状）：
 * `tool_call → 执行 → 回传 → 循环至回答`。
 * - 回传路径 = assistant `tool_calls` 消息（原样回传给模型）+ `role="tool"` 结果消息（带 tool_call_id）。
 * - 步数预算 8（每次 LLM 调用 1 步）→ 硬熔断 + 审计 `circuit_break`。
 * - 云端额度（ContextBudget）耗尽 → 本地兜底（审计 `budget_exhausted` + presence `CloudQuotaExhausted`）。
 * - 许可：写工具经 PolicyGuard 裁决，RequireConfirm → 走 ConfirmGate；拒绝 → 审计 `refused` + 跳过（不纠缠）。
 * - 所有工具执行/裁决/熔断均写审计。
 */
class AgentOrchestrator(
    private val transport: LlmTransport,
    private val registry: ToolRegistry,
    private val policyGuard: PolicyGuard,
    private val contextBudget: ContextBudget,
    private val sessionStore: SessionStore,
    private val presenceBus: PresenceBus,
    private val auditLog: AuditLogPort,
    private val confirmGate: ConfirmGate,
    private val stepBudget: Int = EngineDefaults.STEP_BUDGET,
    private val json: Json = Json,
) {
    suspend fun run(
        userMessage: String,
        config: AiEndpointConfig,
        ctx: RunContextInput = RunContextInput(),
    ): AgentResult {
        sessionStore.startNewSession()
        val taskId = sessionStore.currentSessionId()
        val systemPrompt = buildSystemPrompt(ctx)
        val messages = mutableListOf(
            LlmMessage(role = "system", content = systemPrompt),
        ).apply {
            // 跨轮记忆：先前对话（user/assistant 文本）接在 system 之后、本次 user 之前
            addAll(ctx.history)
            add(LlmMessage(role = "user", content = userMessage))
        }

        AgentLog.i("run start (task=$taskId): input=${AgentLog.truncate(userMessage)} history=${ctx.history.size} steps_budget=$stepBudget")

        val toolLog = mutableListOf<ToolExecutionRecord>()
        var steps = 0
        var finalText = ""
        var terminated = TerminationReason.ANSWERED

        while (steps < stepBudget) {
            // 云端额度：耗尽 → 本地兜底熔断
            if (!contextBudget.spendCloudCall()) {
                terminated = TerminationReason.CLOUD_QUOTA_EXHAUSTED
                AgentLog.w("step $steps: cloud quota exhausted -> local fallback")
                presenceBus.emit(PresenceEvent.CloudQuotaExhausted)
                auditLog.record(AuditEntry(tool = "orchestrator", outcome = "budget_exhausted",
                    reason = "单日云端额度耗尽，已本地兜底", taskId = taskId))
                break
            }
            presenceBus.emit(PresenceEvent.TaskProgress(phase = "thinking", active = true))
            steps++

            AgentLog.d("step $steps: calling LLM (tools=${registry.allLlmSpecs.size})")
            val (assistantText, toolCalls, failed) = collectTurn(messages, config)
            AgentLog.d("step $steps: text=${AgentLog.truncate(assistantText)} toolCalls=${toolCalls.map { it.name }} failed=$failed")
            if (failed) {
                terminated = TerminationReason.FAILED
                finalText = "（对话中断，请稍后再试）"
                auditLog.record(AuditEntry(tool = "orchestrator", outcome = "failed",
                    reason = "LLM 流式错误；steps=$steps", taskId = taskId))
                break
            }
            if (toolCalls.isEmpty()) {
                finalText = assistantText
                terminated = TerminationReason.ANSWERED
                break
            }

            // 回传：assistant tool_calls 消息（原样回传）+ 每项 role="tool" 结果
            messages += LlmMessage(
                role = "assistant",
                content = assistantText.ifBlank { null },
                toolCalls = toolCalls.map { LlmToolCall(it.id, it.name, it.argumentsJson) },
            )
            // 批量确认：本轮所有 RequireConfirm 聚合一次请求，得同序批准列表后再逐项执行
            val approvals = decideApprovals(toolCalls, taskId)
            for (tc in toolCalls) {
                toolLog += executeOne(tc, messages, taskId, approved = approvals[tc.id])
            }

            // 步数预算用尽但尚有工具调用已回传、尚未拿到最终回答 → 硬熔断
            if (steps >= stepBudget) {
                terminated = TerminationReason.STEP_BUDGET_EXHAUSTED
                AgentLog.w("step $steps: step budget exhausted ($steps/$stepBudget)")
                auditLog.record(AuditEntry(tool = "orchestrator", outcome = "circuit_break",
                    reason = "步数预算耗尽 steps=$steps/${stepBudget}（尚有工具已执行但未获最终回答）",
                    taskId = taskId))
                break
            }
        }

        presenceBus.emit(PresenceEvent.TaskProgress(phase = "idle", active = false))

        val finalTextOut = when (terminated) {
            TerminationReason.ANSWERED -> finalText
            TerminationReason.STEP_BUDGET_EXHAUSTED ->
                finalText.ifBlank { "（已尽力而为，但步骤较多，为了稳健我停止了。可以让我继续。）" }
            else -> finalText
        }

        return AgentResult(
            text = finalTextOut,
            stepsUsed = steps,
            toolCalls = toolLog,
            terminatedBy = terminated,
        ).also {
            AgentLog.i("run done: terminated=$terminated steps=$steps tools=${it.toolCalls.size} text=${AgentLog.truncate(finalTextOut)}")
        }
    }

    /** 单次 LLM 调用：收集文本增量 + 工具调用列表；失败时 failed=true。 */
    private suspend fun collectTurn(
        messages: List<LlmMessage>,
        config: AiEndpointConfig,
    ): Triple<String, List<LlmEvent.ToolCall>, Boolean> {
        val text = StringBuilder()
        val calls = mutableListOf<LlmEvent.ToolCall>()
        var failed = false
        try {
            transport.streamChat(
                config = config,
                messages = messages,
                tools = registry.allLlmSpecs,
            ).toList().forEach { e ->
                when (e) {
                    is LlmEvent.TextDelta -> text.append(e.text)
                    is LlmEvent.ToolCall -> calls += e
                    is LlmEvent.Failed -> failed = true
                    LlmEvent.Completed -> Unit
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce // 预算熔断以 cancel 实现，向上传递不误判失败
        } catch (e: Exception) {
            failed = true
        }
        return Triple(text.toString(), calls, failed)
    }

    /**
     * 批量确认：对本轮所有需确认（RequireConfirm）的工具聚合一次请求。
     * - 裁决（PolicyGuard）含审计副作用，在此对**每个工具**执行一次。
     * - 将 RequireConfirm 的工具收集为 [ConfirmRequest]，一次性交给 [ConfirmGate]，
     *   得到同序批准列表。
     * - 返回按 toolCallId 索引的批准表（`true`=执行）；需确认但未获批准 → `false`；
     *   无需确认的工具不在表中（executeOne 以 null 视作直接执行）。
     */
    private suspend fun decideApprovals(
        toolCalls: List<LlmEvent.ToolCall>,
        taskId: Long?,
    ): Map<String, Boolean> {
        val approvals = mutableMapOf<String, Boolean>()
        val pending = mutableListOf<LlmEvent.ToolCall>()
        for (tc in toolCalls) {
            val tool = registry.find(tc.name) ?: continue // 未知工具由 executeOne 处理为 skipped
            val decision = policyGuard.decide(tc.name, tool.permissionLevel, taskId)
            if (decision is PermissionDecision.RequireConfirm) pending += tc
        }
        if (pending.isNotEmpty()) {
            AgentLog.i("confirm request: ${pending.map { it.name }} (${pending.size} 项)")
            val granted = confirmGate.request(
                pending.map { ConfirmRequest(it.name, summarizeArgs(it.argumentsJson), registry.find(it.name)!!.permissionLevel) }
            )
            AgentLog.d("confirm granted=${granted}")
            pending.forEachIndexed { i, tc -> approvals[tc.id] = granted.getOrElse(i) { false } }
        }
        return approvals
    }

    /** 执行单个工具调用：裁决(在 decideApprovals 已做)→按批准执行→回填 role="tool" 消息，并审计。 */
    private suspend fun executeOne(
        tc: LlmEvent.ToolCall,
        messages: MutableList<LlmMessage>,
        taskId: Long?,
        approved: Boolean?,
    ): ToolExecutionRecord {
        val tool = registry.find(tc.name)
        if (tool == null) {
            auditLog.record(AuditEntry(tool = tc.name, outcome = "skipped",
                reason = "未知工具，跳过", taskId = taskId))
            messages += LlmMessage(role = "tool", toolCallId = tc.id, content = "（未知工具，已跳过）")
            return ToolExecutionRecord(tc.name, "skipped", "未知工具")
        }

        // approved != null → 本轮该工具需要确认；false=用户否决（拒绝纪律：不纠缠，跳过不报错）
        if (approved != null && !approved) {
            auditLog.record(AuditEntry(tool = tc.name, outcome = "refused",
                reason = "用户否决，会话内不纠缠", argsHash = hashCode(tc.name, tc.argumentsJson), taskId = taskId))
            messages += LlmMessage(role = "tool", toolCallId = tc.id, content = "（用户拒绝执行，已跳过）")
            return ToolExecutionRecord(tc.name, "refused", "用户拒绝")
        }

        val outcome = try {
            val args = json.parseToJsonElement(tc.argumentsJson).jsonObject
            val result = registry.executeTool(tc.name, args)
            auditLog.record(AuditEntry(tool = tc.name, outcome = if (result.success) "success" else "failed",
                reason = result.failureReason ?: result.summary,
                argsHash = hashCode(tc.name, tc.argumentsJson), taskId = taskId))
            messages += LlmMessage(role = "tool", toolCallId = tc.id, content = result.summary)
            ToolExecutionRecord(tc.name, if (result.success) "success" else "failed", result.summary)
        } catch (e: Exception) {
            auditLog.record(AuditEntry(tool = tc.name, outcome = "failed",
                reason = "执行异常: ${e.message}", argsHash = hashCode(tc.name, tc.argumentsJson), taskId = taskId))
            messages += LlmMessage(role = "tool", toolCallId = tc.id, content = "（执行失败，已跳过）")
            ToolExecutionRecord(tc.name, "failed", "执行异常")
        }
        return outcome
    }

    private fun buildSystemPrompt(ctx: RunContextInput): String {
        val a = contextBudget.assemble(
            taskState = ctx.taskState,
            libraryListText = ctx.libraryListText,
            libraryOverviewText = ctx.libraryOverviewText,
            newToolResult = null,
        )
        // R-T1 首轮注入：人格/称呼 + 当前曲目 + 时段 + 曲库概况 + 认识进度
        val firstTurn = ContextAssembler.assembleFirstTurnBlock(
            personaText = ctx.personaText ?: DefaultCompanionProfiles.DEFAULT.personaPrompt,
            libraryOverview = a.library,
            recognition = ctx.recognitionText,
            timeOfDay = ctx.timeOfDayText,
            nowPlaying = ctx.nowPlayingText,
            userTitle = ctx.userTitle,
        )
        return buildString {
            append(firstTurn.trim())
            append("\n\n可调用工具来检索曲库、管理歌单或控制播放。用中文简洁回应。")
            a.taskState?.let { append("\n【当前任务】\n").append(it) }
        }
    }

    private fun summarizeArgs(json: String): String = json.take(80)

    private fun hashCode(tool: String, args: String): String =
        (tool.hashCode() * 31 + args.hashCode()).toUInt().toString(16)
}