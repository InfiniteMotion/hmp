package com.hmp.domain.setting.model

/** 带时间窗口的统计结果 — 全部来自方案 B 的单条 SQL，零内存聚合。 */
data class WindowedUsageAnalytics(
    val totalListeningMinutes: Long,
    val completionRate: Float,
    val skipRate: Float,
    val totalPlayCount: Int,
    val totalSkipCount: Int,
)
