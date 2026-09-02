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

    // ── 便捷方法（有默认实现，调用 record）：给子 Agent 用，避免每次手写 AuditEntry ──

    /** M6-T1：电台启动审计。 */
    suspend fun logRadioStart(seedLabels: List<String>, trackCount: Int) {
        record(
            AuditEntry(
                tool = "radio.start",
                outcome = "success",
                reason = "seed=${seedLabels.joinToString(",")} tracks=$trackCount",
            )
        )
    }

    /** M6-T2：连跳触发电台重排审计。 */
    suspend fun logSkipReorder(consecutiveCount: Int, trackTitle: String?) {
        record(
            AuditEntry(
                tool = "radio.reorder",
                outcome = "success",
                reason = "consecutiveSkips=$consecutiveCount lastTrack=${trackTitle.orEmpty()}",
            )
        )
    }

    /** M6-T3：LLM 生成 DJ 衔接语审计。 */
    suspend fun logDjSegue(text: String, source: String = "llm") {
        record(
            AuditEntry(
                tool = "dj.segue",
                outcome = "success",
                reason = "source=$source text=${text.take(60)}",
            )
        )
    }
}