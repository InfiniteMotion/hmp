package com.hmp.test.fakes

import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.AuditLogPort

/** 内存审计替身（M4 引擎测试）：记录所有条目供断言，不落库。 */
class FakeAuditLogPort : AuditLogPort {
    val entries = mutableListOf<AuditEntry>()
    override suspend fun record(entry: AuditEntry) { entries += entry }

    fun byTool(tool: String): List<AuditEntry> = entries.filter { it.tool == tool }
    fun outcomes(tool: String): List<String> = byTool(tool).map { it.outcome }
}