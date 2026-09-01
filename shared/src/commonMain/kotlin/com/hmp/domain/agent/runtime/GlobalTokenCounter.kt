package com.hmp.domain.agent.runtime

/**
 * T1 基础设施：全局唯一 Token 消耗计数器。
 *
 * 与 AgentContextBudget（每 Agent 独立，管自己 LLM 窗口 + 历史压缩）分离：
 * 这个类只管「全局当日 Token 总量」，供 AgentScheduler 做日配额仲裁（纯规则零 LLM）。
 *
 * 设计铁则 F2：不涉及单个 Agent 的上下文管理，只做全局日统计。
 *
 * 线程安全：所有方法 @Synchronized，因为 Scheduler 仲裁循环（每秒 1 次）和
 * handleUserMessage 对话循环会并发调用。
 */
class GlobalTokenCounter(
    private val timeProvider: TimeProvider,
    /** 全局 Token 日配额上限（Scheduler 仲裁用）。默认保守值 500K token/天 */
    val dailyTokenQuota: Int = DEFAULT_DAILY_TOKEN_QUOTA,
) {
    @Volatile
    private var lastQuotaDay: Long = -1L
    private var tokensUsedToday: Long = 0L

    /** 今日已消耗 Token 总量 */
    @Synchronized
    fun usedToday(): Long {
        rollDayIfNeeded()
        return tokensUsedToday
    }

    /** 今日剩余配额 */
    fun remainingToday(): Long = (dailyTokenQuota - usedToday()).coerceAtLeast(0)

    /** 今日消耗率（0.0 ~ 1.0+），Scheduler 仲裁用 */
    fun usageRate(): Float = usedToday().toFloat() / dailyTokenQuota

    /** 记录一次 LLM 调用的 Token 消耗（输入 + 输出合计） */
    @Synchronized
    fun recordTokens(count: Long) {
        rollDayIfNeeded()
        tokensUsedToday += count
    }

    /**
     * 检查配额是否充足（不扣减）。用于熔断判断：usageRate > threshold 就停。
     * 语义上是"我们应该停止做更多 LLM 调用"而不是"尝试预留额度"。
     */
    @Synchronized
    fun shouldStop(threshold: Float = REACT_STOP_THRESHOLD): Boolean {
        rollDayIfNeeded()
        return tokensUsedToday.toFloat() / dailyTokenQuota > threshold
    }

    @Synchronized
    private fun rollDayIfNeeded() {
        val day = timeProvider() / 86_400_000L
        if (day != lastQuotaDay) {
            lastQuotaDay = day
            tokensUsedToday = 0L
        }
    }

    companion object {
        /** 默认全局 Token 日配额：500K token（约 100 次标准对话 × 5K token） */
        const val DEFAULT_DAILY_TOKEN_QUOTA: Int = 500_000
        /** ReActLoop 硬熔断阈值（95% = 真的停） */
        const val REACT_STOP_THRESHOLD: Float = 0.95f
        /** Scheduler Enrich pause 阈值（90% = 提前 pause 储备） */
        const val SCHEDULER_PAUSE_THRESHOLD: Float = 0.9f
    }
}
