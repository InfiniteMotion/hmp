package com.hmp.domain.agent.engine

import com.hmp.domain.agent.port.LlmMessage

/**
 * 会话状态（M4-T3 SessionStore）：一次 agent 对话期间的消息历史、任务状态、确认挂起。
 *
 * 文本模式（AgentOrchestrator）在客户端组装上下文 —— SessionStore 承载对话侧状态；
 * 语音模式（VoiceSessionController）以快照注入并时长监护（M7）。
 */
class SessionStore(
    private val timeProvider: TimeProvider,
) {
    private var currentId: Long? = null
    private var messages = mutableListOf<LlmMessage>()
    private var pendingConfirmTask: PendingConfirm? = null
    private var createdAt: Long = 0L

    /** 当前会话是否活跃（有 id 即活跃）。 */
    fun isActive(): Boolean = currentId != null

    fun startNewSession(): Long {
        currentId = currentId?.plus(1) ?: 1L
        messages = mutableListOf()
        pendingConfirmTask = null
        createdAt = timeProvider()
        return currentId!!
    }

    fun currentSessionId(): Long? = currentId

    fun append(vararg msg: LlmMessage) { messages.addAll(msg) }

    fun history(): List<LlmMessage> = messages.toList()

    /** 记录一条挂起确认（M5 确认卡片流的引擎侧语义）。 */
    fun setPendingConfirm(confirm: PendingConfirm) { pendingConfirmTask = confirm }
    fun takePendingConfirm(): PendingConfirm? = pendingConfirmTask?.also { pendingConfirmTask = null }

    fun clear() {
        currentId = null
        messages = mutableListOf()
        pendingConfirmTask = null
    }
}

/** 挂起中的写确认项（M5-T4 逐项确认）。 */
data class PendingConfirm(
    val toolName: String,
    val argsSummary: String,
    val propose: String,
)