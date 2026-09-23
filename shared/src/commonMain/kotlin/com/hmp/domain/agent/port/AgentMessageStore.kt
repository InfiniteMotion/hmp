package com.hmp.domain.agent.port

/**
 * R-T5 会话消息持久化端口（agent_message 表，总纲 5.3「agent_message 是唯一事实」）。
 *
 * UI/引擎只描述「存什么/取什么」，不触碰 Room；实现由数据层 [AgentMessageStore] 落盘
 * （RoomAgentMessageStore → agent_message 表）。引擎单测可注内存 Fake。
 */
data class StoredAgentMessage(
    val sessionId: String,
    /** user / agent / system */
    val role: String,
    val content: String?,
    /** text / song / songlist / explain / confirm（驱动渲染厚度）。 */
    val renderHint: String? = null,
    val createdAt: Long,
)

interface AgentMessageStore {
    /** 返回最新会话 id；无历史则新建。调用方据此决定「续上一条会话」还是开新会话。 */
    suspend fun currentOrNewSessionId(): String

    /** 追加一条消息（fire-and-forget 可由调用方决定协程）。 */
    suspend fun append(message: StoredAgentMessage)

    /** 按会话取最近 [limit] 条（分页/回看）：时间正序截尾。 */
    suspend fun loadSession(sessionId: String, limit: Int): List<StoredAgentMessage>
}
