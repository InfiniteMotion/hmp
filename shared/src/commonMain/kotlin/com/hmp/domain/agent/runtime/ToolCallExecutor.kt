package com.hmp.domain.agent.runtime

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.policy.AgentPolicy
import com.hmp.domain.agent.policy.PermissionDecision
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.tool.ToolRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * 工具调用执行器——统一权限确认 + 参数解析 + 执行 + 审计 + 可选回传 messages。
 *
 * Agent 构造时按自己的需求选参数：
 * - Master 对话：ToolCallExecutor(registry, policyGuard, confirmGate, auditLog, trackMessages=true)
 *   → 有权限确认 + 审计 + 回传（ReAct 多步循环需要 tool result 回传给 LLM）
 * - Radio diff 仲裁：ToolCallExecutor(registry, trackMessages=true)
 *   → 有回传（ReAct 局部循环）但可能无审计
 *
 * 拆自 MasterAgent 的 decideChatApprovals() + executeChatOne() 两个私有方法。
 */
class ToolCallExecutor(
    private val registry: ToolRegistry,
    private val policyGuard: PolicyGuard? = null,
    private val confirmGate: ConfirmGate? = null,
    private val auditLog: AuditLogPort? = null,
    /** true = 执行后把 role="tool" 结果回传到 messages（ReAct 多步循环必需） */
    private val trackMessages: Boolean = true,
    private val json: Json = defaultJson,
) {

    // ===== 批量权限确认 =====

    /**
     * 批量处理 toolCalls 的权限裁决（三阶段：Phase0 alwaysAllow → Phase1 Agent 身份门 → Phase2 TrustTier）。
     * AllowSilent/AllowWithNotify 自动通过 / RequireConfirm 弹 UI / Deny 直接否决。
     *
     * @param agentPolicy Agent 身份 + per-Agent Config + per-Agent ConfirmGate
     * @param onAllowAlways 回调：用户对某些工具点了"永远允许"，上层负责更新 agentPolicy.config.alwaysAllow 并持久化
     * @return Map<toolCallId, approved> — 每个 toolCall 的权限结果（true=允许，false=否决）
     */
    suspend fun batchDecideApprovals(
        agentPolicy: AgentPolicy,
        toolCalls: List<LlmEvent.ToolCall>,
        taskId: Long? = null,
        onAllowAlways: (Set<String>) -> Unit = {},
    ): Map<String, Boolean> {
        val policyGuard = policyGuard
        val confirmGate = agentPolicy.confirmGate

        if (policyGuard == null) {
            // 无 policyGuard 时（比如 DirectToolExecutor 路径 / 测试注入）——全自动通过
            return toolCalls.associate { it.id to true }
        }

        val approvals = mutableMapOf<String, Boolean>()
        val pending = mutableListOf<LlmEvent.ToolCall>()

        for (tc in toolCalls) {
            val tool = registry.find(tc.name) ?: continue
            val decision = policyGuard.decide(agentPolicy, tc.name, tool.permissionLevel, taskId)
            when (decision) {
                is PermissionDecision.AllowSilent,
                is PermissionDecision.AllowWithNotify -> approvals[tc.id] = true
                is PermissionDecision.RequireConfirm -> pending += tc
                is PermissionDecision.Deny -> approvals[tc.id] = false  // Phase1 Agent 身份门拒 / Phase2 策略拒
            }
        }

        if (pending.isNotEmpty()) {
            if (confirmGate == null) {
                // 后台 Agent 无 confirmGate → RequireConfirm 级工具直接 Denied（安全兜底）
                Logger.w("Agent.Tool") { "no confirmGate for agent=${agentPolicy.role}, denying ${pending.size} pending toolCalls" }
                pending.forEach { approvals[it.id] = false }
            } else {
                Logger.i("Agent.Tool") { "confirm request: ${pending.map { it.name }} (${pending.size} 项)" }
                val outcomes = confirmGate.request(
                    pending.map { tc ->
                        ConfirmRequest(
                            toolName = tc.name,
                            argsSummary = summarizeArgs(tc.argumentsJson),
                            permissionLevel = registry.find(tc.name)!!.permissionLevel,
                        )
                    }
                )
                Logger.d("Agent.Tool") { "confirm outcomes=$outcomes" }

                val trustLedger = agentPolicy.trustLedger
                val alwaysAllowCandidates = mutableSetOf<String>()
                pending.forEachIndexed { i, tc ->
                    when (outcomes.getOrElse(i) { ConfirmOutcome.Deny }) {
                        ConfirmOutcome.AllowOnce -> {
                            approvals[tc.id] = true
                            // AllowOnce 不改变 trustLevel——单次确认无论多少次都不应累积信任
                        }
                        ConfirmOutcome.AllowAlways -> {
                            approvals[tc.id] = true
                            alwaysAllowCandidates += tc.name
                            trustLedger?.onActionAccepted(tc.name)  // 永远允许 → 触发信任累加
                        }
                        ConfirmOutcome.Deny -> {
                            approvals[tc.id] = false
                            trustLedger?.onActionRejected(tc.name)  // 拒绝 → 回拨信任
                        }
                    }
                }
                if (alwaysAllowCandidates.isNotEmpty()) {
                    onAllowAlways(alwaysAllowCandidates)
                }
            }
        }

        return approvals
    }

    // ===== 执行单个 toolCall =====

    /**
     * 执行一个 toolCall。
     *
     * @param tc LLM 返回的工具调用事件
     * @param messages 回传目标——trackMessages=true 时 append role="tool" 结果；null 时跳过
     * @param taskId 审计用的任务 ID
     * @param approved 权限裁决结果——null=跳过权限检查全自动执行；false=用户否决直接跳过
     */
    suspend fun executeOne(
        tc: LlmEvent.ToolCall,
        messages: MutableList<LlmMessage>? = null,
        taskId: Long? = null,
        approved: Boolean? = null,
    ): ToolExecutionRecord {
        val tool = registry.find(tc.name)
        if (tool == null) {
            auditLog?.record(AuditEntry(
                tool = tc.name, outcome = "skipped",
                reason = "未知工具，跳过", taskId = taskId,
            ))
            messages?.appendToolResult(tc, "（未知工具，已跳过）")
            return ToolExecutionRecord(tc.name, "skipped", "未知工具")
        }

        if (approved != null && !approved) {
            auditLog?.record(AuditEntry(
                tool = tc.name, outcome = "refused",
                reason = "用户否决", argsHash = argsHash(tc.name, tc.argumentsJson),
                taskId = taskId,
            ))
            messages?.appendToolResult(tc, "（用户拒绝执行，已跳过）")
            return ToolExecutionRecord(tc.name, "refused", "用户拒绝")
        }

        return try {
            val args = parseArgsStrict(tc, json)
            val result = registry.executeTool(tc.name, args)
            auditLog?.record(AuditEntry(
                tool = tc.name,
                outcome = if (result.success) "success" else "failed",
                reason = result.failureReason ?: result.summary,
                argsHash = argsHash(tc.name, tc.argumentsJson),
                taskId = taskId,
            ))
            messages?.appendToolResult(tc, result.summary)
            ToolExecutionRecord(tc.name, if (result.success) "success" else "failed", result.summary)
        } catch (e: Exception) {
            auditLog?.record(AuditEntry(
                tool = tc.name, outcome = "failed",
                reason = "执行异常: ${e.message}",
                argsHash = argsHash(tc.name, tc.argumentsJson),
                taskId = taskId,
            ))
            messages?.appendToolResult(tc, "（执行失败，已跳过）")
            ToolExecutionRecord(tc.name, "failed", "执行异常")
        }
    }

    // ===== 工具方法 =====

    private fun summarizeArgs(args: String): String = args.take(80)
    private fun argsHash(tool: String, args: String): String =
        (tool.hashCode() * 31 + args.hashCode()).toUInt().toString(16)

    private fun MutableList<LlmMessage>.appendToolResult(
        tc: LlmEvent.ToolCall,
        content: String,
    ) {
        if (!trackMessages) return
        this += LlmMessage(
            role = "tool",
            toolCallId = tc.id,
            content = content,
        )
    }

    companion object {
        private val defaultJson = Json { ignoreUnknownKeys = true }

        /** 严格解析 arguments → JsonObject；失败抛（ToolCallExecutor 用，上层 try-catch） */
        internal fun parseArgsStrict(
            tc: LlmEvent.ToolCall,
            json: Json = defaultJson,
        ): kotlinx.serialization.json.JsonObject = json.parseToJsonElement(tc.argumentsJson).jsonObject

        /** 容错解析 arguments → JsonObject；非 object 时包 {"raw":"..."}；异常返回 null（DirectToolExecutor 用） */
        internal fun parseArgsSafe(
            tc: LlmEvent.ToolCall,
            json: Json = defaultJson,
        ): kotlinx.serialization.json.JsonObject? = runCatching {
            json.parseToJsonElement(tc.argumentsJson).let {
                if (it is kotlinx.serialization.json.JsonObject) it
                else kotlinx.serialization.json.buildJsonObject {
                    put("raw", kotlinx.serialization.json.JsonPrimitive(tc.argumentsJson))
                }
            }
        }.getOrNull()
    }
}
