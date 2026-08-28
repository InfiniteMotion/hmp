package com.hmp.domain.agent.port

/**
 * 审计事件产出（M4 起埋点：工具调用/许可裁决/云端修正/预算熔断/拒绝纪律）。
 *
 * 引擎平面只描述「要留什么痕」，不触碰 Room——具体持久化由数据层 [AuditLogPort] 实现
 * （RoomAuditLogAdapter 映射到 agent_audit_log 表）。这样引擎单测可注内存 Fake，三端可注入真实 DAO。
 */
data class AuditEntry(
    val tool: String,
    /** 结果：success / failed / rejected / revoked / superseded / circuit_break / budget_exhausted / skipped */
    val outcome: String,
    val reason: String? = null,
    /** 参数哈希（防篡改 + 可复现该次调用；M4 引擎对 tool 调用计算）。 */
    val argsHash: String? = null,
    val taskId: Long? = null,
)

/** 审计写入端口：引擎与数据层（Room agent_audit_log）的解耦缝。 */
interface AuditLogPort {
    suspend fun record(entry: AuditEntry)
}