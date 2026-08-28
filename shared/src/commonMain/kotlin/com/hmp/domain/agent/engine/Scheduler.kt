package com.hmp.domain.agent.engine

import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.AuditLogPort

/** 触发器类型：用户召唤 / 外部事件 / 定时任务。 */
enum class TriggerType { CALL, EVENT, TIMER }

data class TriggerRequest(
    val type: TriggerType,
    /** 触发来源描述，用于审计。 */
    val source: String,
)

/**
 * M4-T2 触发与冷却：决定一个 agent 任务能否被启动。
 * 触发器类型：召唤（用户主动）/ 事件（外部事件）/ 定时（任务调度）。
 * 冷却：防止高频重入（尤其事件风暴）。
 */
class Scheduler(
    private val timeProvider: TimeProvider,
    private val auditLog: AuditLogPort,
    /** 各触发器类型的冷却时长（ms）。 */
    private val cooldownMs: Map<TriggerType, Long> = mapOf(
        TriggerType.CALL to EngineDefaults.CALL_COOLDOWN_MS,
        TriggerType.EVENT to EngineDefaults.EVENT_COOLDOWN_MS,
        TriggerType.TIMER to 0L,
    ),
) {
    /** 上次触发时间；null=从未触发（首次恒允许，防误拒）。 */
    private var lastFiredAt: Long? = null

    /**
     * 是否允许本次触发。满足给定类型冷却则允许并记录审计；否则拒绝并记审计。
     * 首次触发不受冷却约束（无历史间隔可比较）。
     */
    suspend fun tryFire(request: TriggerRequest): Boolean {
        val now = timeProvider()
        val cooldown = cooldownMs[request.type] ?: 0L
        val sinceLast = lastFiredAt?.let { now - it }
        val allowed = sinceLast == null || sinceLast >= cooldown
        if (allowed) lastFiredAt = now
        auditLog.record(
            AuditEntry(
                tool = "scheduler",
                outcome = if (allowed) "accepted" else "cooldown",
                reason = "trigger=${request.type} source=${request.source} sinceLast=${sinceLast ?: "first"}",
            )
        )
        return allowed
    }
}