package com.hmp.domain.agent.runtime

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.setting.model.AiEndpointConfig

/**
 * T1 基础设施：每个 Agent 独立的 LLM 上下文窗口管理器。
 *
 * 设计铁则 F2：每个 Agent 对应一份独立 AgentContextBudget，绑定独立 LlmTransport 实例。
 * - 管自己 LLM 窗口的 token 估算 + 历史消息自动压缩
 * - 不涉及全局日配额（那是 GlobalTokenCounter 的事）
 * - 不涉及运行仲裁（那是 AgentScheduler 的事）
 *
 * 当消息累计估算 token 超过窗口 85% 阈值时，自动触发历史压缩：
 * 调用轻量 LLM 把早期消息序列压缩成一条摘要，保持上下文不爆。
 */
class AgentContextBudget(
    /** Agent 唯一标识（如 "master" / "enrich" / "radio"） */
    val agentId: String,
    /** 该 Agent 的 LLM 窗口 token 上限（Master 128K，Enrich 32K，Radio 64K） */
    val maxContextTokens: Int,
    /** 绑定的独立 LlmTransport 实例（与其他 Agent 物理隔离；暴露供 LlmCallExecutor 复用） */
    val llmClient: LlmTransport,
    /** 窗口占用触发压缩的阈值（默认 85%） */
    private val compressionThreshold: Float = 0.85f,
    /** 压缩时保留的最新消息条数（不压缩，直接保留） */
    private val recentMessagesToKeep: Int = 6,
) {
    private val estimatedHistory = mutableListOf<LlmMessage>()
    private var estimatedTokenCount: Int = 0

    /** 当前估算上下文 token 数 */
    fun currentEstimatedTokens(): Int = estimatedTokenCount

    /** 窗口使用率（0.0 ~ 1.0+） */
    fun windowUsage(): Float = estimatedTokenCount.toFloat() / maxContextTokens

    /** 窗口是否接近上限（触发压缩） */
    fun needsCompression(): Boolean = windowUsage() >= compressionThreshold

    /**
     * 向上下文追加消息（追加到历史，用于后续 token 估算和压缩决策）。
     * @param message 要追加的 LlmMessage
     * @return 如果追加后触发了压缩，返回压缩摘要；否则返回 null
     */
    fun appendMessage(message: LlmMessage): String? {
        estimatedHistory.add(message)
        estimatedTokenCount += estimateMessageTokens(message)

        return if (needsCompression()) {
            compressHistory()
        } else {
            null
        }
    }

    /** 批量追加消息（不触发逐次压缩，最后统一判断一次） */
    fun appendMessages(messages: List<LlmMessage>): String? {
        estimatedHistory.addAll(messages)
        estimatedTokenCount += messages.sumOf { estimateMessageTokens(it) }
        return if (needsCompression()) compressHistory() else null
    }

    /**
     * 执行一次 LLM 调用（完整走 AgentContextBudget）：
     * 在 appendMessages 后检查窗口，必要时先压缩再调用。
     * 返回的 messages 是已经过压缩处理的版本。
     */
    suspend fun callLlm(
        config: AiEndpointConfig,
        systemPrompt: String,
        newMessages: List<LlmMessage>,
        tools: List<LlmToolSpec>? = null,
        temperature: Float = 0.3f,
    ): kotlinx.coroutines.flow.Flow<com.hmp.domain.agent.port.LlmEvent> {
        appendMessages(newMessages)                    // newMessages 进 estimatedHistory
        val messages = buildMessages(systemPrompt)     // 从 estimatedHistory 构建（已包含 newMessages）
        return llmClient.streamChat(
            config = config,
            messages = messages,
            tools = tools,
            temperature = temperature,
        )
    }

    /**
     * 非流式便捷调用：收集流式输出拼接成完整文本。
     *
     * 适用于一次性 JSON 生成、富化管道等不需要流式打字机效果的场景。
     * 返回 null 表示 LLM 调用失败（网络、超时、HTTP 错误等）。
     */
    suspend fun callLlmText(
        config: AiEndpointConfig,
        systemPrompt: String,
        newMessages: List<LlmMessage>,
        tools: List<LlmToolSpec>? = null,
        temperature: Float = 0.3f,
    ): String? {
        val flow = callLlm(config, systemPrompt, newMessages, tools, temperature)
        val textBuffer = StringBuilder()
        var failedMessage: String? = null
        flow.collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> textBuffer.append(event.text)
                is LlmEvent.Failed -> failedMessage = event.message
                is LlmEvent.Completed -> Unit
                is LlmEvent.ToolCall -> Unit // 非流式场景忽略 tool calls
            }
        }
        return if (failedMessage != null) {
            Logger.e("Agent.ContextBudget") { "[$agentId] callLlmText failed: $failedMessage" }
            null
        } else {
            textBuffer.toString()
        }
    }

    /**
     * 构建 LLM 调用的完整 messages 列表（system + 压缩历史 + 本次新消息）。
     * 新消息已在 callLlm 里通过 appendMessages 进了 estimatedHistory，
     * 这里直接从历史构建——避免重复。
     */
    fun buildMessages(systemPrompt: String): List<LlmMessage> {
        val result = mutableListOf<LlmMessage>()
        result.add(LlmMessage(role = "system", content = systemPrompt))
        result.addAll(estimatedHistory.takeLast(recentMessagesToKeep))
        return result
    }

    /** 释放 LLM 客户端（SubAgent shutdown 时调用） */
    fun releaseLlmClient() {
        // LlmTransport 无关闭接口（纯接口 + Flow），此处清理历史即可
        estimatedHistory.clear()
        estimatedTokenCount = 0
    }

    /** 清空历史（新会话） */
    fun clearHistory() {
        estimatedHistory.clear()
        estimatedTokenCount = 0
    }

    /** 历史压缩：把早期消息压缩成一条摘要，保留最近 N 条 */
    private fun compressHistory(): String {
        // 简单策略：保留最近 recentMessagesToKeep 条，之前的全部丢弃
        // 未来可用轻量 LLM 做摘要压缩
        val keepFrom = (estimatedHistory.size - recentMessagesToKeep).coerceAtLeast(0)
        val discarded = estimatedHistory.subList(0, keepFrom).toList()
        val kept = estimatedHistory.drop(keepFrom)
        estimatedHistory.clear()
        estimatedHistory.addAll(kept)
        estimatedTokenCount = estimatedHistory.sumOf { estimateMessageTokens(it) }

        val summary = "[历史压缩] 丢弃 ${discarded.size} 条早期消息，保留最近 ${estimatedHistory.size} 条"
        Logger.i("Agent.ContextBudget") { "[$agentId] $summary (${estimatedTokenCount}/${maxContextTokens} tokens)" }
        return summary
    }

    /** 粗略 token 估算（中文 ~1.5 token/char，英文 ~0.25 token/word，这里用保守上限） */
    private fun estimateMessageTokens(msg: LlmMessage): Int {
        var tokens = 4 // 每条消息固定开销（role + 结构）
        msg.content?.let { content ->
            // 中文偏多：char 数 × 0.7 保守估算
            tokens += (content.length * 0.7).toInt()
        }
        msg.toolCalls?.forEach { tc ->
            tokens += (tc.argumentsJson.length * 0.7).toInt()
        }
        msg.toolCallId?.let { tokens += 4 }
        return tokens
    }
}

