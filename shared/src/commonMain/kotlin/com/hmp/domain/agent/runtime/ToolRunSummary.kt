package com.hmp.domain.agent.runtime

/** 摘要里最多列几个工具，避免提示词膨胀（一轮通常 1-3 个）。 */
private const val MAX_TOOLS_IN_SUMMARY = 6

/**
 * R3 后半（2026-11-13）：把本轮已执行工具折成一条给下一轮模型看的提醒；空列表返回 null。
 *
 * 只用于**防重复执行**，不重放参数与结果全文 —— 历史窗口只有 6 条，
 * 完整 tool 轮次会把用户消息挤掉（方案 B）。
 */
internal fun buildToolRunSummary(toolLog: List<ToolExecutionRecord>): String? {
    if (toolLog.isEmpty()) return null
    val listed = toolLog.takeLast(MAX_TOOLS_IN_SUMMARY)
        .joinToString("、") { "${it.toolName}(${it.outcome})" }
    return "上一轮已执行工具：$listed。若用户是在确认或追问，不要重复执行同样的写操作。"
}
