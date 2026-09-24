package com.hmp.data.database

import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.AuditLogPort

/**
 * 审计适配器：把引擎产出的 [AuditEntry] 落到 Room 的 agent_audit_log 表。
 * 时间戳取自平台 currentTimeMillis（引擎保持纯 domain，不反向依赖本数据层）。
 */
class RoomAuditLogAdapter(
    private val dao: AgentAuditLogDao,
) : AuditLogPort {
    override suspend fun record(entry: AuditEntry) {
        dao.insert(
            AgentAuditLog(
                taskId = entry.taskId,
                tool = entry.tool,
                argsHash = entry.argsHash,
                outcome = entry.outcome,
                reason = entry.reason,
                createdAt = currentTimeMillis(),
            )
        )
    }
}