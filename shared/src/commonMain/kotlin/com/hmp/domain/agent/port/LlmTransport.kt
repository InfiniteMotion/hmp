package com.hmp.domain.agent.port

import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * LLM 流式对话端口（设计总纲 6.1 端口平面）。
 *
 * 事件流契约（供 M4 AgentOrchestrator 消费）：
 * - [TextDelta]：增量文本（逐 chunk 流出）；
 * - [ToolCall]：完整工具调用（arguments 分片已在传输层组装完毕）；
 * - [Usage]：**端点返回的真实用量**（F12-T1）——可能出现在流末尾，也可能完全没有；
 * - [Completed]：正常结束（唯一终态之一）；[Failed]：异常结束（网络/HTTP/解析）。
 * 一个调用恰好以 [Completed] 或 [Failed] 收尾。
 */
sealed interface LlmEvent {
    data class TextDelta(val text: String) : LlmEvent
    data class ToolCall(val id: String, val name: String, val argumentsJson: String) : LlmEvent

    /**
     * 真实用量（F12-T1 新增的**唯一真值通道**）。
     *
     * 流式端点的行为差异（已核实）：
     * - 必须请求侧带 `stream_options.include_usage=true` 才会回（见 `OpenAiStyleRequest.streamOptions`）；
     * - 回来时是一个 **`choices` 为空的末尾 chunk** —— 消费侧若先判空 choices 再读，就会丢掉它；
     * - 部分 OpenAI 兼容端点**压根不回** → 消费侧回落估算并打标 `measured=false`。
     */
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val cachedTokens: Int = 0,
    ) : LlmEvent

    data class Failed(val message: String) : LlmEvent
    data object Completed : LlmEvent
}

/**
 * assistant 消息回传的工具调用（M4 引擎循环：模型发起 tool_call 后，工具执行结果以 role="tool"
 * 消息回传时，还须原样附带该调用的 assistant 消息——OpenAI 兼容协议的循环回传路径）。
 */
data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/** 对话消息（role: system/user/assistant/tool；tool 结果消息带 [toolCallId]；assistant 工具调用消息带 [toolCalls]）。 */
data class LlmMessage(
    val role: String,
    val content: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<LlmToolCall>? = null,
)

/** 工具声明（schema 由 M3 ToolSpec DSL 生成）。 */
data class LlmToolSpec(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject? = null,
)

interface LlmTransport {
    /**
     * 流式对话。`tools` 非空时启用 function-calling（请求带 tool_choice=auto，
     * 服务商不支持工具时仍可退回纯文本完成——由 M4 侧依据事件内容判定）。
     *
     * @param temperature 按任务档位传入（JSON/富化任务 0.2-0.4，对话任务可调高）。
     */
    suspend fun streamChat(
        config: AiEndpointConfig,
        messages: List<LlmMessage>,
        tools: List<LlmToolSpec>? = null,
        temperature: Float = 0.3f,
    ): Flow<LlmEvent>
}