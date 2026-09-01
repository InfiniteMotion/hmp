package com.hmp.domain.agent.runtime

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList

/**
 * 单次 LLM 调用执行器——采集 streamChat Flow 为结构化结果。
 *
 * 所有 Agent（Master 每轮循环、Enrich 批次、Radio 各阶段）都通过它调 LLM，
 * 统一错误处理 + 事件采集逻辑。不涉及多步循环编排（那是 ReActLoop 的事）。
 */
class LlmCallExecutor {

    /**
     * 执行一次 LLM 调用并采集结果。
     *
     * @param transport LLM 传输层（每个 Agent 独立实例）
     * @param config 端点配置（API Key / 模型等）
     * @param messages 完整消息列表（system + history + new user）
     * @param tools 可用工具的 LLM spec（传 null 表示纯对话无工具）
     * @param temperature 采样温度（对话 0.7 / 批量富化 0.3 / 严格裁决 0.1）
     */
    suspend fun call(
        transport: LlmTransport,
        config: AiEndpointConfig,
        messages: List<LlmMessage>,
        tools: List<LlmToolSpec>?,
        temperature: Float,
    ): CollectedLlmResult {
        val text = StringBuilder()
        val calls = mutableListOf<LlmEvent.ToolCall>()
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

        Logger.d("Agent.LlmCall") { "[LlmCall] text=${text.toString().take(79)}… toolCalls=${calls.size} failed=$failed" }

        return CollectedLlmResult(
            text = text.toString(),
            toolCalls = calls,
            failed = failed,
            failedMessage = failedMessage,
        )
    }
}

/** 单次 LLM 调用的结构化结果 */
data class CollectedLlmResult(
    /** LLM 的自然语言输出（逐 delta 拼接） */
    val text: String,
    /** LLM 触发的工具调用列表（分片已在传输层组装完毕） */
    val toolCalls: List<LlmEvent.ToolCall>,
    /** 是否异常结束（网络/HTTP/解析错误） */
    val failed: Boolean,
    /** 失败原因（failed=true 时有值） */
    val failedMessage: String? = null,
)
