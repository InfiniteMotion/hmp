package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.TimeProvider

import com.hmp.platform.Volatile
import com.hmp.platform.Synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
/**
 * 看板 / 诊断用的不可变 Token 快照。
 *
 * **F13：为什么它 public 而 [GlobalTokenCounter] 是 internal** ——
 * 可见性看「跨不跨模块」：`AgentMonitorScreen`（shared-ui）要渲染用量，所以*这个数据形状*
 * 必须公开；但它不需要拿到计数器本身。UI 只经 `MasterAgent.tokenUsage` 读快照，
 * 于是引擎计数器得以收敛为 internal。把数据形状与写入能力分开，是"抽门面"的典型形态。
 */
data class TokenSnapshot(
    val used: Long,
    val quota: Long,
) {
    val remaining: Long = (quota - used).coerceAtLeast(0)

    val rate: Float = if (quota > 0) (used.toFloat() / quota).coerceIn(0f, 1f) else 0f
}

internal class GlobalTokenCounter(
    private val timeProvider: TimeProvider,
    /** 全局 Token 日配额上限（Scheduler 仲裁用）。默认保守值 500K token/天 */
    val dailyTokenQuota: Int = DEFAULT_DAILY_TOKEN_QUOTA,
) {
    @Volatile
    private var lastQuotaDay: Long = -1L
    private var tokensUsedToday: Long = 0L

    // UI 订阅用：每次消耗/跨日时发布最新快照，供看板实时渲染。
    private val _snapshot =
        MutableStateFlow(TokenSnapshot(used = 0L, quota = dailyTokenQuota.toLong()))
    val snapshot: StateFlow<TokenSnapshot> = _snapshot.asStateFlow()

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
        publishSnapshot()
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
            publishSnapshot()
        }
    }

    @Synchronized
    private fun publishSnapshot() {
        _snapshot.value = TokenSnapshot(used = tokensUsedToday, quota = dailyTokenQuota.toLong())
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
