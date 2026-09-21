package com.hmp.data.network.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ========== OpenAI Compatible Request/Response ==========

@Serializable
data class OpenAiStyleRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    /** 始终序列化：默认值也应出现在报文中（服务端默认 1.0 与调用方任务档位契约不一致）。 */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val temperature: Float = 0.7f,
    @SerialName("response_format")
    val responseFormat: Map<String, String>? = null,
    /** function-calling 工具定义（M2-T1，B1 协议扩展）；null 时不序列化，保持 5 家服务商兼容 */
    val tools: List<OpenAiTool>? = null,
    /** tool_choice：字符串（"auto"/"none"）或函数引用对象；tools 为空时省略 */
    @SerialName("tool_choice")
    val toolChoice: JsonElement? = null,
    /** 流式响应开关（SSE）：流式任务必传 true，否则端点返回普通 JSON 而非 SSE（审查修复） */
    @SerialName("stream")
    val stream: Boolean? = null,
    /**
     * 流式用量回传（F12-T1）：OpenAI 兼容端点**必须显式索要**才会在末尾发 usage chunk。
     * 不带此字段时流式响应永无 usage → 计量只能靠估算。
     */
    @SerialName("stream_options")
    val streamOptions: OpenAiStreamOptions? = null,
)

/** 流式选项（`stream_options`）。 */
@Serializable
data class OpenAiStreamOptions(
    @SerialName("include_usage")
    val includeUsage: Boolean = true,
)

@Serializable
data class OpenAiMessage(
    val role: String,
    /** 文本内容；assistant 工具调用消息的 content 可为 null（OpenAI 规范允许省略/空） */
    val content: String? = null,
    /** function-calling 工具结果消息（role="tool"）时必填 */
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    /** assistant 消息的工具调用数组（M4 引擎循环回传路径：工具执行后须原样回传该数组） */
    @SerialName("tool_calls")
    val toolCalls: List<OpenAiAssistantToolCall>? = null,
)

/** assistant 消息携带的工具调用（调用形状，区别于声明形状 [OpenAiTool]）。 */
@Serializable
data class OpenAiAssistantToolCall(
    val id: String,
    /** 始终序列化："function"（部分服务商依赖 type 字段路由） */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "function",
    val function: OpenAiFunctionCall,
)

/** 工具调用载荷：name/arguments 必填（arguments 为 JSON 字符串，非结构化对象）。 */
@Serializable
data class OpenAiFunctionCall(
    val name: String,
    val arguments: String,
)

/** function-calling 工具声明（OpenAI 兼容 shape：{"type":"function","function":{name,description,parameters}}）。 */
@Serializable
data class OpenAiTool(
    /** 始终序列化："function"（部分服务商依赖 type 字段路由） */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "function",
    val function: OpenAiFunctionSpec,
)

@Serializable
data class OpenAiFunctionSpec(
    val name: String,
    val description: String? = null,
    /** JSON Schema 对象（由 ToolSpec DSL 生成，见 M3） */
    val parameters: JsonObject? = null,
)

@Serializable
data class OpenAiStyleResponse(
    val id: String? = null,
    val choices: List<OpenAiChoice>? = null,
    val usage: OpenAiUsage? = null
)

@Serializable
data class OpenAiChoice(
    val message: OpenAiMessage? = null,
    /** OpenAI 端点发 snake_case；缺 SerialName 时解码恒为 null（review 测试发现 2026-08-28） */
    @SerialName("finish_reason")
    val finishReason: String? = null
)

// ========== OpenAI Compatible SSE Stream ==========

/** SSE 流式 chunk（chat/completions stream=true）：delta 承载文本/工具调用的增量。 */
@Serializable
data class OpenAiStreamChunk(
    val id: String? = null,
    val choices: List<OpenAiStreamChoice>? = null,
    /**
     * 用量（F12-T1）：请求带 `stream_options.include_usage=true` 时，末尾会有一个
     * **`choices` 为空**的 chunk 携带此字段。
     *
     * ⚠️ 消费侧必须先读 `usage` 再判 `choices` 是否为空 —— 顺序反了就永远收不到用量。
     */
    val usage: OpenAiUsage? = null,
)

@Serializable
data class OpenAiStreamChoice(
    val delta: OpenAiStreamDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class OpenAiStreamDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<OpenAiToolCallDelta>? = null,
)

/** 工具调用增量：arguments 按 index 分片到达（由 LlmTransport 层做分片组装）。 */
@Serializable
data class OpenAiToolCallDelta(
    val index: Int? = null,
    val id: String? = null,
    val type: String? = null,
    val function: OpenAiFunctionDelta? = null,
)

@Serializable
data class OpenAiFunctionDelta(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0,
    /** 提示缓存明细（OpenAI 形状）；端点不带时为 null → cachedTokens = 0。 */
    @SerialName("prompt_tokens_details")
    val promptTokensDetails: OpenAiPromptTokensDetails? = null,
) {
    /** 命中提示缓存的 token 数（各家形状不一，取不到的按 0 记，不猜）。 */
    val cachedTokens: Int get() = promptTokensDetails?.cachedTokens ?: 0
}

@Serializable
data class OpenAiPromptTokensDetails(
    @SerialName("cached_tokens")
    val cachedTokens: Int = 0,
)

// ========== Models List Response ==========

@Serializable
data class ModelsResponse(
    val data: List<ModelItem>? = null
)

@Serializable
data class ModelItem(
    val id: String,
    @SerialName("owned_by")
    val ownedBy: String? = null
)

// ========== Music Info Response ==========

@Serializable
data class MusicInfoResponse(
    val genre: List<String> = emptyList(),
    val mood: List<String> = emptyList(),
    val scenario: List<String> = emptyList(),
    val language: String = "",
    val era: String = "",
    val rewards: String = "",
    val lyric: String = "",
    val singerIntroduce: String = "",
    val backgroundIntroduce: String = "",
    val description: String = "",
    val relevantMusic: String = "",
    val errorInfo: String = ""
)
