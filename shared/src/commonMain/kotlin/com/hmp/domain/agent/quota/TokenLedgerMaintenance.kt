package com.hmp.domain.agent.quota

import com.hmp.data.database.TokenAggregateRow
import com.hmp.data.database.TokenLedgerDao
import com.hmp.data.database.currentTimeMillis
import com.hmp.domain.agent.port.TimeProvider

/**
 * Token 账本的维护口（`D6-05`）。
 *
 * 只做两件事，**不自动清理**（与 [com.hmp.data.database.TokenLedgerEntry] 的"永久保留"注记一致）：
 *
 * 1. **窗口化取数**：监控页的"总用量"与"按 Agent 分账"走**同一个** `sinceMs`。
 *    此前总用量用 `sumTotal()`（全历史、无 `WHERE`），分账用 `sumByAgent(0L)` ——
 *    两个口径不一致，且都会随账本增长退化为全表扫。
 * 2. **手动清空**：返回"将删除多少条、最早一条是什么时候"，供 UI 二次确认后再删。
 *
 * 清空是用户显式动作，故不设保留窗口、不加定时任务；本类只保证
 * "先报数、后删除"两步在同一次调用里完成，避免 UI 拿旧计数去确认。
 */
class TokenLedgerMaintenance(
    private val dao: TokenLedgerDao,
    private val timeProvider: TimeProvider = { currentTimeMillis() },
) {

    /** 监控页统一窗口起点（默认近 30 天）。 */
    fun windowStart(nowMs: Long = timeProvider()): Long = nowMs - WINDOW_MS

    /**
     * 读取一次监控页快照。
     *
     * `totalTokens` / `agentRows` 是**窗口内**数据；`totalRows` / `earliestMs` 是**全账本**数据，
     * 后者只用于清空确认文案（"将删除 N 条、自 X 起"），不参与用量展示。
     */
    suspend fun snapshot(nowMs: Long = timeProvider()): TokenLedgerSnapshot {
        val since = windowStart(nowMs)
        return TokenLedgerSnapshot(
            windowStartMs = since,
            totalTokens = dao.sumTotalSince(since),
            agentRows = dao.sumByAgent(since),
            totalRows = dao.count(),
            earliestMs = dao.earliestMs(),
        )
    }

    /** 手动清空的预检 + 执行：先取计数与最早时间，再整表删除。 */
    suspend fun clearAll(): TokenLedgerClear {
        val rows = dao.count()
        val earliest = dao.earliestMs()
        dao.deleteAll()
        return TokenLedgerClear(rows = rows, earliestMs = earliest)
    }

    companion object {
        /** 监控页窗口（天）。30 天足够看出趋势，又不会把全历史拉进聚合。 */
        const val WINDOW_DAYS: Int = 30

        const val WINDOW_MS: Long = WINDOW_DAYS * 24L * 60L * 60L * 1000L
    }
}

/** 监控页一次取数的结果。[windowStartMs] 同时是两条查询的 `sinceMs`，用于断言口径一致。 */
data class TokenLedgerSnapshot(
    val windowStartMs: Long,
    val totalTokens: Long,
    val agentRows: List<TokenAggregateRow>,
    val totalRows: Long,
    val earliestMs: Long?,
)

/** 清空前的账面数字。 */
data class TokenLedgerClear(
    val rows: Long,
    val earliestMs: Long?,
)
