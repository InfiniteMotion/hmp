package com.hmp.data.network

import com.hmp.data.network.dto.OpenAiAssistantToolCall
import com.hmp.data.network.dto.OpenAiFunctionCall
import com.hmp.data.network.dto.OpenAiFunctionSpec
import com.hmp.data.network.dto.OpenAiMessage
import com.hmp.data.network.dto.OpenAiStyleRequest
import com.hmp.data.network.dto.OpenAiTool
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** 流式调用失败（HTTP 非 2xx 等），由 [OpenAiLlmTransport] 转 Failed 事件。 */
class LlmStreamException(message: String) : Exception(message)

/**
 * OpenAI 兼容协议的 [LlmTransport] 实现（设计总纲 6.1 端口平面 IV 层）。
 *
 * 职责：领域消息/工具 → OpenAI 报文（tools + tool_choice=auto），SSE 流 → [LlmEvent]。
 * 工具调用按 index 分片组装（arguments 分片跨 chunk 到达），`finish_reason=tool_calls`
 * 或流结束兜底时一次性发出完整 [LlmEvent.ToolCall]。
 */
class OpenAiLlmTransport(
    private val adapter: OpenAiCompatibleAdapter,
    private val json: Json,
    /** 流式调用超时（毫秒）：端点建连后 hang 住（半开连接）时兜底熔断；M4 双层预算的传输层下限。
     *  `timeoutMillis <= 0` 表示禁用（单测用：runTest 虚拟时间会 auto-advance 触发虚拟超时，须关掉）。 */
    private val timeoutMillis: Long = DEFAULT_STREAM_TIMEOUT_MILLIS,
) : LlmTransport {

    companion object {
        /** 默认流超时 120s：单轮流式对话（含长回答）的合理上限，工具循环每轮独立计时 */
        const val DEFAULT_STREAM_TIMEOUT_MILLIS: Long = 120_000
    }

    override suspend fun streamChat(
        config: AiEndpointConfig,
        messages: List<LlmMessage>,
        tools: List<LlmToolSpec>?,
        temperature: Float,
    ): Flow<LlmEvent> = flow {
        val request = OpenAiStyleRequest(
            model = config.selectedModel,
            messages = messages.map { m ->
                OpenAiMessage(
                    role = m.role,
                    content = m.content,
                    toolCallId = m.toolCallId,
                    toolCalls = m.toolCalls?.map { tc ->
                        OpenAiAssistantToolCall(
                            id = tc.id,
                            function = OpenAiFunctionCall(name = tc.name, arguments = tc.argumentsJson),
                        )
                    },
                )
            },
            temperature = temperature,
            tools = tools?.map { spec ->
                OpenAiTool(function = OpenAiFunctionSpec(name = spec.name, description = spec.description, parameters = spec.parameters))
            },
            toolChoice = if (tools.isNullOrEmpty()) null else JsonPrimitive("auto"),
            stream = true,
        )

        val toolAccumulators = mutableMapOf<Int, ToolCallAccumulator>()
        suspend fun collectStream() {
            adapter.streamChatCompletion(config, request).collect { chunk ->
                val choice = chunk.choices?.firstOrNull() ?: return@collect
                val delta = choice.delta ?: return@collect

                delta.content?.takeIf { it.isNotEmpty() }?.let { emit(LlmEvent.TextDelta(it)) }

                delta.toolCalls?.forEach { tc ->
                    val acc = toolAccumulators.getOrPut(tc.index ?: 0) { ToolCallAccumulator() }
                    tc.id?.let { acc.id = it }
                    tc.function?.name?.let { acc.name = it }
                    tc.function?.arguments?.let { acc.arguments.append(it) }
                }

                if (choice.finishReason == "tool_calls") {
                    flushToolCalls(toolAccumulators, this@flow)
                    toolAccumulators.clear()
                }
            }
        }
        try {
            if (timeoutMillis > 0) {
                withTimeout(timeoutMillis) { collectStream() }
            } else {
                collectStream()
            }
            // 兜底：部分端点不发送 finish_reason=tool_calls，流结束时补发
            flushToolCalls(toolAccumulators, this)
            emit(LlmEvent.Completed)
        } catch (e: TimeoutCancellationException) {
            // 超时属于业务失败：转唯一终态 Failed（注意顺序——它是 CancellationException 子类）
            emit(LlmEvent.Failed("LLM stream timed out after ${timeoutMillis}ms"))
        } catch (e: CancellationException) {
            // 协程取消向上传播（M4 预算熔断可能以 cancel 实现），不误转 Failed
            throw e
        } catch (e: Exception) {
            emit(LlmEvent.Failed(e.message ?: "LLM stream failed"))
        }
    }

    private suspend fun flushToolCalls(
        accumulators: Map<Int, ToolCallAccumulator>,
        collector: FlowCollector<LlmEvent>,
    ) {
        accumulators.entries.forEachIndexed { n, (_, acc) ->
            val name = acc.name
            if (!name.isNullOrBlank()) {
                collector.emit(
                    LlmEvent.ToolCall(
                        // 部分怪癖端点不带 id：生成稳定 fallback（M4 回传 tool_call_id 需要 non-blank）
                        id = acc.id?.takeIf { it.isNotBlank() } ?: "call_$n",
                        name = name,
                        argumentsJson = acc.arguments.toString(),
                    )
                )
            }
        }
    }

    private class ToolCallAccumulator {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }
}