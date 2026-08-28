package com.hmp.domain.agent.engine

/**
 * 上下文组装结果（M4-T3）：三层都已被剪裁到预算内，可直接拼进 system/user 提示。
 */
data class ContextAssembly(
    /** 任务状态（截断到预算）。 */
    val taskState: String?,
    /** 曲库概况：清单 或 概览（两级切换后）。 */
    val library: String?,
    /** 工具结果滚动保留（最新在前，越界淘汰最旧）。 */
    val toolResults: List<String>,
)

/**
 * M4-T3 上下文组装器 + 云端额度（双层预算之「云额度」）。
 *
 * 三层配额：
 * 1. 任务状态：固定小配额（当前任务目标/进度）。
 * 2. 曲库概况：清单 ↔ 概览两级切换——清单超预算时降级为概览（去尾），防 token 失控。
 * 3. 工具结果：滚动淘汰，只保留最近 N 条（防模型被陈旧结果带偏）。
 *
 * 云端额度（跨任务，防电台与高频对话的日成本累积）：单日上限，跨任务共享；
 * 额度耗尽 → 上层触发本地兜底（能力受限但永不失能）。
 */
class ContextBudget(
    private val timeProvider: TimeProvider,
    private val maxTaskStateChars: Int = EngineDefaults.MAX_TASK_STATE_CHARS,
    private val maxLibraryListChars: Int = EngineDefaults.MAX_LIBRARY_LIST_CHARS,
    private val maxToolResultsKept: Int = EngineDefaults.MAX_TOOL_RESULTS_KEPT,
    private val dailyCloudQuota: Int = EngineDefaults.DAILY_CLOUD_QUOTA,
) {
    private var lastQuotaDay: Long = -1L
    private var cloudUsedToday = 0

    fun assemble(
        taskState: String?,
        libraryListText: String?,
        libraryOverviewText: String?,
        newToolResult: String?,
    ): ContextAssembly {
        val task = taskState?.truncate(maxTaskStateChars)
        val library = when {
            libraryListText != null &&
                libraryListText.length <= maxLibraryListChars -> libraryListText
            else -> libraryOverviewText?.truncate(maxLibraryListChars)
        }
        // 最新结果前插，越界淘汰最旧
        val kept = (listOfNotNull(newToolResult?.truncate(maxLibraryListChars)) + resultsBuffer)
            .filter { it.isNotBlank() }
            .take(maxToolResultsKept)
        resultsBuffer = kept
        return ContextAssembly(taskState = task, library = library, toolResults = kept)
    }

    /** 能否消耗一次云端调用（未超单日额度）。 */
    fun canSpendCloudCall(): Boolean {
        rollDayIfNeeded()
        return cloudUsedToday < dailyCloudQuota
    }

    /** 尝试消耗一次云端调用；返回 true=允许（且已扣减）。 */
    fun spendCloudCall(): Boolean {
        if (!canSpendCloudCall()) return false
        cloudUsedToday++
        return true
    }

    private fun rollDayIfNeeded() {
        val day = timeProvider() / 86_400_000L
        if (day != lastQuotaDay) {
            lastQuotaDay = day
            cloudUsedToday = 0
        }
    }

    private var resultsBuffer = emptyList<String>()

    private fun String.truncate(max: Int): String =
        if (length <= max) this else take(max - 3) + "…"
}