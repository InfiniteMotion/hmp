package com.hmp.data.database

import com.hmp.domain.agent.port.AgentMessageStore
import com.hmp.domain.agent.port.StoredAgentMessage

/**
 * R-T5 会话消息持久化实现：把 [StoredAgentMessage] 落到 Room 的 agent_message 表。
 * 时间戳取自平台 currentTimeMillis；session_id 按消息归属推导（无独立会话表）。
 */
class RoomAgentMessageStore(
    private val dao: AgentMessageDao,
) : AgentMessageStore {

    override suspend fun currentOrNewSessionId(): String {
        val latest = dao.getSessions().firstOrNull()
        return latest ?: "session_${currentTimeMillis()}"
    }

    override suspend fun append(message: StoredAgentMessage) {
        dao.insert(
            AgentMessage(
                sessionId = message.sessionId,
                role = message.role,
                content = message.content,
                renderHint = message.renderHint,
                createdAt = message.createdAt,
            )
        )
    }

    override suspend fun loadSession(sessionId: String, limit: Int): List<StoredAgentMessage> =
        dao.getBySession(sessionId)
            .takeLast(limit.coerceAtLeast(0))
            .map {
                StoredAgentMessage(
                    sessionId = it.sessionId,
                    role = it.role,
                    content = it.content,
                    renderHint = it.renderHint,
                    createdAt = it.createdAt,
                )
            }
}
