package com.hmp.domain.agent.enrich

/**
 * 富化健康度快照（T2-T2，Master 启动时检测用）。
 *
 * coverageRate = 有 AI 标签的歌曲数 / 总歌曲数
 * Enrich SubAgent 的目标就是把 coverageRate 推到 targetCoverage（默认 0.9）。
 */
data class EnrichHealth(
    /** 有 AI 源标签（source in LLM/AGENT）的歌曲数 */
    val enrichedSongCount: Int,
    /** 总歌曲数（排除软删除） */
    val totalSongCount: Int,
    /** 有低置信度标签（confidence < 0.5）的歌曲数，需要重新富化 */
    val lowConfidenceCount: Int,
) {
    /** 覆盖率（0.0 ~ 1.0；totalSongCount=0 时返回 0.0） */
    val coverageRate: Float
        get() = if (totalSongCount == 0) 0f else enrichedSongCount.toFloat() / totalSongCount
}

/** 富化批次验收结果（Master 派活/验收循环用） */
data class EnrichBatchResult(
    /** 自 since 时间戳以来，Enrich 写库的成功次数 */
    val successCount: Int,
    /** 自 since 以来，Enrich 执行但无有效标签产出的歌曲数 */
    val failureCount: Int,
) {
    /** 成功率（分母=0 时返回 1.0，防止 NaN） */
    val successRate: Float
        get() = if (successCount + failureCount == 0) 1f else successCount.toFloat() / (successCount + failureCount)
}

/** Enrich 任务单（Master 创建 Enrich 时注入） */
data class EnrichTask(
    /** 目标覆盖率（默认 90%） */
    val targetCoverage: Float = 0.9f,
    /** 每批次最大歌曲数（平衡 LLM 窗口占用） */
    val maxBatchSize: Int = 20,
    /** 可接受的批次失败率阈值（超过则 Master 调整策略） */
    val acceptableFailureRate: Float = 0.1f,
)
