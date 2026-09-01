package com.hmp.domain.agent.runtime

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolCall
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.policy.AgentPolicy
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.setting.model.AiEndpointConfig

/**
 * ReAct 多步 LLM 循环执行器——MasterAgent 对话 / Radio 第 3 轮 diff 仲裁共用。
 *
 * 层次 3：循环编排 → 组合 LlmCallExecutor（层次 1）+ ToolCallExecutor（层次 2）+ 熔断。
 *
 * 为什么不是所有 Agent 都用？EnrichSubAgent 批次只调一次 LLM，不需要多步循环——
 * 它自己编排 LlmCallExecutor + ToolCallExecutor（trackMessages=false 批次模式）。
 * ReActLoop 是"可选组件"，只有需要 LLM 自主驱动多步决策的场景才组装它。
 *
 * @param stepBudget 最大步数（Master 对话 8，Radio 仲裁 4）
 * @param temperature 采样温度（对话 0.7，仲裁 0.1，Enrich 0.3）
 * @param tokenCounter 日配额熔断（可共享）；null 表示不查日配额
 * @param policyGuard 许可护栏；null 时跳过（全自动）
 * @param confirmGate 危险操作确认门；null 时跳过（全自动）
 * @param auditLog 审计日志；null 时跳过（不审计）
 * @param presenceBus 存在感总线；null 时跳过（不发 thinking/idle 事件）
 * @param stopSignal 停止信号；null 时只查 stepBudget + tokenCounter
 */
class ReActLoop(
    private val stepBudget: Int = 8,
    private val temperature: Float = 0.7f,
    private val tokenCounter: GlobalTokenCounter? = null,
    private val policyGuard: PolicyGuard? = null,
    private val confirmGate: ConfirmGate? = null,
    private val auditLog: AuditLogPort? = null,
    private val presenceBus: PresenceBus? = null,
    private val stopSignal: StopSignal? = null,
    private val llmCall: LlmCallExecutor = LlmCallExecutor(),
) {

    // ===== 入口 =====

    /**
     * 跑一次 ReAct 多步循环。
     *
     * @param transport LLM 传输层（每个 Agent 独立实例）
     * @param config LLM 端点配置
     * @param systemPrompt system 提示（由外部组装好传入——ReActLoop 不管 prompt 怎么拼）
     * @param inputMessages 初始 user 消息
     * @param history 多轮历史（对话场景有；批次场景空列表）
     * @param registry 完整工具集（ReAct 循环需要 LLM 自主选工具）
     * @param taskId 审计用的任务 ID
     */
    suspend fun run(
        agentPolicy: AgentPolicy,
        transport: LlmTransport,
        config: AiEndpointConfig,
        systemPrompt: String,
        inputMessages: List<LlmMessage>,
        history: List<LlmMessage> = emptyList(),
        registry: ToolRegistry,
        taskId: Long? = null,
        /** 对话结束后回调一次——用于持久化 alwaysAllow 等配置变化 */
        onSessionComplete: (suspend () -> Unit)? = null,
    ): com.hmp.domain.agent.runtime.AgentResult {
        val messages = mutableListOf(
            LlmMessage(role = "system", content = systemPrompt),
        ).apply {
            addAll(history)
            addAll(inputMessages)
        }

        val toolLog = mutableListOf<ToolExecutionRecord>()
        var steps = 0
        var finalText = ""
        var terminated = com.hmp.domain.agent.runtime.TerminationReason.ANSWERED

        Logger.i("Agent.ReActLoop") { "ReActLoop start (task=$taskId): input=$inputMessages history=${history.size} budget=$stepBudget" }

        // ToolCallExecutor 无状态——registry 在本次 run() 内不变，只 new 一次
        // confirmGate 不再传：batchDecideApprovals 从 agentPolicy.confirmGate 拿
        val toolExecutor = ToolCallExecutor(
            registry = registry,
            policyGuard = policyGuard,
            auditLog = auditLog,
            trackMessages = true,  // ReAct 多步循环必需 tool result 回传给 LLM
        )

        while (steps < stepBudget) {
            // 双重熔断：外部 stopSignal + tokenCounter
            if (stopSignal?.shouldSoftStop() == true || tokenCounter?.shouldStop() == true) {
                terminated = if (tokenCounter?.shouldStop() == true)
                    com.hmp.domain.agent.runtime.TerminationReason.CLOUD_QUOTA_EXHAUSTED
                else
                    com.hmp.domain.agent.runtime.TerminationReason.FAILED
                Logger.w("Agent.ReActLoop") { "step $steps: soft stop (tokenQuota=${tokenCounter?.shouldStop()}, stopSignal=${stopSignal?.shouldSoftStop()})" }
                auditLog?.record(AuditEntry(
                    tool = "react.loop", outcome = "budget_exhausted",
                    reason = "soft stop at step $steps", taskId = taskId,
                ))
                break
            }

            presenceBus?.emit(com.hmp.domain.agent.infra.PresenceEvent.TaskProgress(phase = "thinking", active = true))
            steps++

            Logger.d("Agent.ReActLoop") { "step $steps: calling LLM (tools=${registry.allLlmSpecs.size})" }
            val turn = llmCall.call(
                transport = transport,
                config = config,
                messages = messages,
                tools = registry.allLlmSpecs,
                temperature = temperature,
            )
            Logger.d("Agent.ReActLoop") { "step $steps: text=${turn.text.take(119)}… toolCalls=${turn.toolCalls.map { it.name }} failed=${turn.failed}" }

            // Token 估算（LlmCallExecutor 负责采集，这里统一累加到共享配额）
            tokenCounter?.recordTokens(estimateTokens(messages, turn))

            if (turn.failed) {
                terminated = com.hmp.domain.agent.runtime.TerminationReason.FAILED
                finalText = "（对话中断，请稍后再试）"
                auditLog?.record(AuditEntry(
                    tool = "react.loop", outcome = "failed",
                    reason = "LLM 流式错误; steps=$steps msg=${turn.failedMessage}", taskId = taskId,
                ))
                break
            }

            if (turn.toolCalls.isEmpty()) {
                // LLM 给了最终回答 → ReAct 循环结束
                finalText = turn.text
                terminated = com.hmp.domain.agent.runtime.TerminationReason.ANSWERED
                break
            }

            // 回传：assistant tool_calls 消息
            messages += LlmMessage(
                role = "assistant",
                content = turn.text.ifBlank { null },
                toolCalls = turn.toolCalls.map { LlmToolCall(it.id, it.name, it.argumentsJson) },
            )

            // 统一执行：ToolCallExecutor（有权限确认 + 审计 + 回传 messages；已在 run() 开头创建复用）
            // onAllowAlways 回调直接 addAll 到 agentPolicy.config.alwaysAllow（MutableSet，原地改即时生效）
            val approvals = toolExecutor.batchDecideApprovals(agentPolicy, turn.toolCalls, taskId) { names ->
                agentPolicy.config.alwaysAllow.addAll(names)
            }
            for (tc in turn.toolCalls) {
                toolLog += toolExecutor.executeOne(tc, messages, taskId, approvals[tc.id])
            }

            if (steps >= stepBudget) {
                terminated = com.hmp.domain.agent.runtime.TerminationReason.STEP_BUDGET_EXHAUSTED
                Logger.w("Agent.ReActLoop") { "step $steps: step budget exhausted ($steps/$stepBudget)" }
                auditLog?.record(AuditEntry(
                    tool = "react.loop", outcome = "circuit_break",
                    reason = "步数预算耗尽 steps=$steps/$stepBudget", taskId = taskId,
                ))
                break
            }
        }

        presenceBus?.emit(com.hmp.domain.agent.infra.PresenceEvent.TaskProgress(phase = "idle", active = false))

        // 持久化钩子：对话结束时统一刷一次配置（alwaysAllow 变化 + trustLevel 变化由 TrustLedger.onChange 各自触发）
        onSessionComplete?.invoke()

        val finalTextOut = when (terminated) {
            com.hmp.domain.agent.runtime.TerminationReason.ANSWERED -> finalText
            com.hmp.domain.agent.runtime.TerminationReason.STEP_BUDGET_EXHAUSTED ->
                finalText.ifBlank { "（已尽力而为，但步骤较多，为了稳健我停止了。可以让我继续。）" }
            else -> finalText.ifBlank { "（对话中断，请稍后再试）" }
        }

        return com.hmp.domain.agent.runtime.AgentResult(
            text = finalTextOut,
            stepsUsed = steps,
            toolCalls = toolLog,
            terminatedBy = terminated,
        ).also {
            Logger.i("Agent.ReActLoop") { "ReActLoop done: terminated=$terminated steps=$steps tools=${it.toolCalls.size}" }
        }
    }

    // ===== Token 估算 =====

    /**
     * 估算一次 LLM 调用的 token 消耗。
     * 粗略估算（中文 ~1.5 char/token），比硬编码 1500 更准。
     * 精确 token 需要 LLM API 返回 usage 字段——未来 LlmTransport 升级后可以接真实值。
     */
    private fun estimateTokens(
        messages: List<LlmMessage>,
        result: CollectedLlmResult,
    ): Long {
        var total = 0L
        messages.forEach { msg ->
            total += msg.content?.length?.times(0.7)?.toLong() ?: 0L
            msg.toolCalls?.forEach { tc ->
                total += tc.argumentsJson.length.times(0.7).toLong()
            }
        }
        total += result.text.length.times(0.7).toLong()
        result.toolCalls.forEach { tc ->
            total += tc.argumentsJson.length.times(0.7).toLong()
        }
        return total.coerceAtLeast(200L)  // 保底 200 token（防止极端场景低估）
    }
}
