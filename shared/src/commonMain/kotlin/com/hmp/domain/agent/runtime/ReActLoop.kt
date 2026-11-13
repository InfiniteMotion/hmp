package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.policy.AgentPolicy
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.ConfirmGate
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolCall
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.tool.spec.ToolRegistry
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

/**
 * ReAct 多步 LLM 循环执行器（目前只服务 MasterAgent 主对话）。
 *
 * 层次 3：循环编排 → 组合 LlmCallExecutor（层次 1）+ ToolCallExecutor（层次 2）。
 *
 * 为什么不是所有 Agent 都用？EnrichSubAgent 批次只调一次 LLM，不需要多步循环——
 * 它自己编排 LlmCallExecutor + ToolCallExecutor（trackMessages=false 批次模式）。
 * ReActLoop 是"可选组件"，只有需要 LLM 自主驱动多步决策的场景才组装它。
 *
 * 退出条件（2026-11-11 起不再有步数上限）：
 * - LLM 不再返回工具调用（给出最终回答）→ ANSWERED
 * - LLM 调用失败 → FAILED
 * - 配额闸门在请求前置拒绝 → CLOUD_QUOTA_EXHAUSTED
 * - 外部 StopSignal 软停止 → CLOUD_QUOTA_EXHAUSTED
 * 循环收敛依赖模型给出最终回答或 token 配额耗尽；**没有步数硬兜底**，
 * 因此 PAID / 未开启自定义限额的 CUSTOM 模式下，若模型持续返回工具调用，循环不会自行终止。
 *
 * 配置持久化（2026-11-11）：用户对工具点「总是允许」时经 [onAlwaysAllowChanged] 立即落盘，
 * 不再等循环正常结束；[run] 的 `finally` 只负责把「思考中」复位，取消 / 异常同样生效。
 *
 * @param temperature 采样温度（对话 0.7）
 * @param policyGuard 许可护栏；null 时跳过（全自动）
 * @param confirmGate 危险操作确认门；null 时跳过（全自动）
 * @param auditLog 审计日志；null 时跳过（不审计）
 * @param presenceBus 存在感总线；null 时跳过（不发 thinking/idle 事件）
 * @param stopSignal 停止信号
 */
internal class ReActLoop(
    private val temperature: Float = 0.7f,
    private val policyGuard: PolicyGuard? = null,
    private val confirmGate: ConfirmGate? = null,
    private val auditLog: AuditLogPort? = null,
    private val presenceBus: PresenceBus? = null,
    private val stopSignal: StopSignal? = null,
    /** F12-T1：记账身份（本循环目前只服务 Master 对话） */
    private val agentId: String = TokenMeter.AGENT_MASTER,
    /** F12-T1：唯一记账口 —— 每步的用量在此收口（真值优先、估算兜底打标） */
    private val tokenMeter: TokenMeter? = null,
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
     * @param onAlwaysAllowChanged 用户勾选「总是允许」时立即回调；上层负责把策略快照落盘。
     *   该回调不依赖循环正常结束，取消 / 中断时此前的变化也已保存。
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
        onAlwaysAllowChanged: (() -> Unit)? = null,
        /**
         * 本轮已执行工具（R3 后半）：**正常结束 / 取消 / 异常都会回调一次**，
         * 供上层落一条「上一轮干过什么」的摘要，避免下一轮重复执行写工具。
         */
        onRunFinished: (suspend (List<ToolExecutionRecord>) -> Unit)? = null,
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

        HmpLog.i(LogTag.AgentReActLoop) { "🔁 ReActLoop start (task=$taskId): input=$inputMessages history=${history.size}" }

        // ToolCallExecutor 无状态——registry 在本次 run() 内不变，只 new 一次
        // confirmGate 不再传：batchDecideApprovals 从 agentPolicy.confirmGate 拿
        val toolExecutor = ToolCallExecutor(
            registry = registry,
            policyGuard = policyGuard,
            auditLog = auditLog,
            trackMessages = true, // ReAct 多步循环必需 tool result 回传给 LLM
        )

        try {
            while (true) {
                // 软停止：外部 stopSignal（子 Agent 的桶耗尽 / 外部 shutdown）
                if (stopSignal?.shouldSoftStop() == true) {
                    terminated = com.hmp.domain.agent.runtime.TerminationReason.CLOUD_QUOTA_EXHAUSTED
                    HmpLog.w(LogTag.AgentReActLoop) { "🔁 step $steps: soft stop (stopSignal=true)" }
                    auditLog?.record(
                        AuditEntry(
                            tool = "react.loop",
                            outcome = "budget_exhausted",
                            reason = "soft stop at step $steps",
                            taskId = taskId,
                        ),
                    )
                    break
                }

                presenceBus?.emit(com.hmp.domain.agent.infra.PresenceEvent.TaskProgress(phase = "thinking", active = true))
                steps++

                HmpLog.d(LogTag.AgentReActLoop) { "🔁 step $steps: calling LLM (tools=${registry.allLlmSpecs.size})" }
                val turn = LlmCallExecutor.call(
                    transport = transport,
                    config = config,
                    messages = messages,
                    tools = registry.allLlmSpecs,
                    temperature = temperature,
                    agentId = agentId,
                    meter = tokenMeter,
                    taskId = taskId?.toString(),
                )
                HmpLog.d(LogTag.AgentReActLoop) { "🔁 step $steps: text=${turn.text.take(119)}… toolCalls=${turn.toolCalls.map { it.name }} failed=${turn.failed}" }

                // Token 计量（F12-T1）已在 LlmCallExecutor 内经 TokenMeter 收口 ——
                // 这里**不再**调用 recordTokens：旧实现在此用估算值累加，是"记账只有一处且为估算"的根源。

                // 配额耗尽：请求根本没发出去（闸门在 LlmCallExecutor 里前置拦截），要给出可行动的话术，
                // 而不是让用户看到"对话中断"去重试。
                if (turn.quotaExhausted) {
                    terminated = com.hmp.domain.agent.runtime.TerminationReason.CLOUD_QUOTA_EXHAUSTED
                    finalText = "（AI 用量额度已用完，可在设置里配置自己的 API Key 或调整用量上限）"
                    auditLog?.record(
                        AuditEntry(
                            tool = "react.loop",
                            outcome = "denied",
                            reason = "quota_exhausted; steps=$steps",
                            taskId = taskId,
                        ),
                    )
                    break
                }

                if (turn.failed) {
                    terminated = com.hmp.domain.agent.runtime.TerminationReason.FAILED
                    finalText = "（对话中断，请稍后再试）"
                    auditLog?.record(
                        AuditEntry(
                            tool = "react.loop",
                            outcome = "failed",
                            reason = "LLM 流式错误; steps=$steps msg=${turn.failedMessage}",
                            taskId = taskId,
                        ),
                    )
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
                // 用户勾「总是允许」→ 写内存白名单 + 立即落盘（不依赖循环正常结束）
                val approvals = toolExecutor.batchDecideApprovals(agentPolicy, turn.toolCalls, taskId) { names ->
                    agentPolicy.config.alwaysAllow.addAll(names)
                    onAlwaysAllowChanged?.invoke()
                }
                var confirmTimedOut = false
                for (tc in turn.toolCalls) {
                    val record = toolExecutor.executeOne(
                        tc,
                        messages,
                        taskId,
                        approvals.approvals[tc.id],
                        timedOut = tc.id in approvals.timedOut,
                    )
                    toolLog += record
                    // 确认超时：跳过剩余工具，带着明确原因结束本轮（2026-11-13）
                    if (record.outcome == "timeout") {
                        confirmTimedOut = true
                        break
                    }
                }
                if (confirmTimedOut) {
                    terminated = com.hmp.domain.agent.runtime.TerminationReason.CONFIRM_TIMEOUT
                    HmpLog.w(LogTag.AgentReActLoop) { "🔁 step $steps: confirm timeout → end run" }
                    break
                }
            }
        } finally {
            // 取消 / 异常 / 正常结束都会走到这里：把「思考中」复位，不依赖上层通知。
            // 配置持久化不在此处（见 onAlwaysAllowChanged），避免把活对象留到最后一刻才写。
            presenceBus?.emit(com.hmp.domain.agent.infra.PresenceEvent.TaskProgress(phase = "idle", active = false))
            if (onRunFinished != null) {
                // 取消时协程已 cancelled，普通 suspend 会直接抛 CancellationException；
                // 必须 NonCancellable 才能把「已执行的工具」写进库（这正是取消路径的判据）。
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    runCatching { onRunFinished(toolLog.toList()) }
                }
            }
        }

        val finalTextOut = when (terminated) {
            com.hmp.domain.agent.runtime.TerminationReason.ANSWERED -> finalText

            com.hmp.domain.agent.runtime.TerminationReason.CONFIRM_TIMEOUT ->
                finalText.ifBlank { "（确认超时，本轮对话已结束。需要的话请重新发起操作。）" }

            else -> finalText.ifBlank { "（对话中断，请稍后再试）" }
        }

        return com.hmp.domain.agent.runtime.AgentResult(
            text = finalTextOut,
            stepsUsed = steps,
            toolCalls = toolLog,
            terminatedBy = terminated,
        ).also {
            HmpLog.i(LogTag.AgentReActLoop) { "🔁 ReActLoop done: terminated=$terminated steps=$steps tools=${it.toolCalls.size}" }
        }
    }

    // ===== Token 估算 =====
    //
    // F12-T1 起本类**不再自带估算**：计量统一由 `TokenMeter` 收口
    // （真值来自 `LlmEvent.Usage`，估算仅作兜底并打标 `measured=false`）。
    // 旧实现这里有一份 `length × 0.7` 的估算副本，与 `AgentContextBudget` 的那份重复 ——
    // 两处各写一遍正是"口径漂移"的来源，已收敛为唯一实现。
}
