package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList

/**
 * 单次 LLM 调用执行器——采集 streamChat Flow 为结构化结果。
 *
 * 所有 Agent（Master 每轮循环、Enrich 批次、Radio 各阶段）都通过它调 LLM，
 * 统一错误处理 + 事件采集逻辑。不涉及多步循环编排（那是 ReActLoop 的事）。
 *
 * **无状态 ⇒ 声明为 `object`**：它只有局部变量，先前却写成 `class`，于是 4 个调用点
 * 各自 `LlmCallExecutor()`。无状态对象反复实例化既无意义，也让"它到底有没有状态"
 * 变得可疑；改成单例后调用点无法再各持一份，也没有"某一份被配错"的可能。
 *
 * 身份与记账仍由**参数**传入（`agentId` / `meter`）：同一次调用属于哪个 Agent 是真实语义
 * （master / radio / profile），无法从调用方类型推断。需要自动带上身份时走
 * [AgentContextBudget.callOnce]。
 */
internal object LlmCallExecutor {

    /**
     * 执行一次 LLM 调用并采集结果。
     *
     * @param transport LLM 传输层（每个 Agent 独立实例）
     * @param config 端点配置（API Key / 模型等）
     * @param messages 完整消息列表（system + history + new user）
     * @param tools 可用工具的 LLM spec（传 null 表示纯对话无工具）
     * @param temperature 采样温度（对话 0.7 / 批量富化 0.3 / 严格裁决 0.1）
     * @param agentId F12-T1：记账身份（master / radio / profile …）
     * @param meter F12-T1：唯一记账口；null = 不记账
     */
    suspend fun call(
        transport: LlmTransport,
        config: AiEndpointConfig,
        messages: List<LlmMessage>,
        tools: List<LlmToolSpec>?,
        temperature: Float,
        agentId: String = TokenMeter.AGENT_MASTER,
        meter: TokenMeter? = null,
        taskId: String? = null,
    ): CollectedLlmResult {
        val text = StringBuilder()
        val calls = mutableListOf<LlmEvent.ToolCall>()
        var usage: LlmEvent.Usage? = null
        var failed = false
        var failedMessage: String? = null

        try {
            transport.streamChat(
                config = config,
                messages = messages,
                tools = tools,
                temperature = temperature,
            ).toList().forEach { e ->
                when (e) {
                    is LlmEvent.TextDelta -> text.append(e.text)
                    is LlmEvent.ToolCall -> calls += e
                    is LlmEvent.Usage -> usage = e
                    is LlmEvent.Failed -> {
                        failed = true
                        failedMessage = e.message
                    }
                    LlmEvent.Completed -> Unit
                }
            }
        } catch (ce: CancellationException) {
            throw ce  // 协程取消不吞，让上层处理
        } catch (e: Exception) {
            failed = true
            failedMessage = e.message
        }

        // F12-T1 计量收口：真值优先（measured=true），拿不到则估算兜底并打标 measured=false。
        // 注意：**无 usage 的失败调用不记账**（TokenMeter 内部判断）——没有消费凭据时不污染账本。
        meter?.record(
            agentId = agentId,
            config = config,
            usage = usage,
            messages = messages,
            outputText = text.toString(),
            failed = failed,
            taskId = taskId,
        )

        HmpLog.d(LogTag.AgentLlmCall) { "🧠 [LlmCall] text=${text.toString().take(79)}… toolCalls=${calls.size} failed=$failed" }

        return CollectedLlmResult(
            text = text.toString(),
            toolCalls = calls,
            failed = failed,
            failedMessage = failedMessage,
        )
    }
}

/** 单次 LLM 调用的结构化结果 */
internal data class CollectedLlmResult(
    /** LLM 的自然语言输出（逐 delta 拼接） */
    val text: String,
    /** LLM 触发的工具调用列表（分片已在传输层组装完毕） */
    val toolCalls: List<LlmEvent.ToolCall>,
    /** 是否异常结束（网络/HTTP/解析错误） */
    val failed: Boolean,
    /** 失败原因（failed=true 时有值） */
    val failedMessage: String? = null,
)
