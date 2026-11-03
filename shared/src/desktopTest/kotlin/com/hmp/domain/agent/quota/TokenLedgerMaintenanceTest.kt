package com.hmp.domain.agent.quota

import com.hmp.data.database.TokenAggregateRow
import com.hmp.data.database.TokenLedgerDao
import com.hmp.data.database.TokenLedgerEntry
import com.hmp.data.database.TokenTimeBucket
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D6-05 判据：监控页取数走窗口、手动清空真的清空。
 *
 * 用替身而不是真库：这两件事要断言的是**调用参数**（`sinceMs > 0`）与
 * "先报数后删除"的顺序，真库只能看到结果看不到口径。
 */
class TokenLedgerMaintenanceTest {

    private val now = 1_790_000_000_000L
    private val dayMs = 24L * 60L * 60L * 1000L

    private fun entry(at: Long, agent: String = "master", tokens: Int = 100) = TokenLedgerEntry(
        createdAt = at,
        agentId = agent,
        endpointHost = "api.example.com",
        model = "m",
        promptTokens = tokens,
        completionTokens = 0,
        cachedTokens = 0,
        measured = true,
    )

    @Test
    fun snapshot_usesOneWindowForTotalAndAgentRows() = runTest {
        val dao = FakeLedgerDao(
            listOf(
                entry(now - dayMs, agent = "master", tokens = 100),      // 窗口内
                entry(now - 40 * dayMs, agent = "radio", tokens = 999),  // 窗口外
            ),
        )
        val maintenance = TokenLedgerMaintenance(dao) { now }

        val snap = maintenance.snapshot()

        assertEquals(now - TokenLedgerMaintenance.WINDOW_MS, snap.windowStartMs, "窗口起点应为 30 天前")
        assertTrue(snap.windowStartMs > 0, "给 DAO 的 sinceMs 必须 > 0（不再是 0L 全表扫）")
        assertEquals(dao.lastSumSince, dao.lastSumTotalSince, "总用量与分账必须用同一个 sinceMs")
        assertEquals(snap.windowStartMs, dao.lastSumTotalSince)
        assertEquals(100L, snap.totalTokens, "窗口外那条不计入总用量")
        assertEquals(listOf("master"), snap.agentRows.map { it.label })
        assertEquals(2L, snap.totalRows, "清空确认用的计数仍是全账本")
    }

    @Test
    fun clearAll_reportsBeforeDeleting_thenEmpties() = runTest {
        val dao = FakeLedgerDao(
            listOf(
                entry(now - 5 * dayMs, agent = "master"),
                entry(now - dayMs, agent = "hello"),
            ),
        )
        val maintenance = TokenLedgerMaintenance(dao) { now }

        val clear = maintenance.clearAll()

        assertEquals(2L, clear.rows)
        assertEquals(now - 5 * dayMs, clear.earliestMs, "最早时间取自删除之前")
        assertEquals(0L, dao.count(), "清空后账本为空")
        assertEquals(1, dao.deleteCalls)
    }

    @Test
    fun windowStart_isThirtyDays() {
        val maintenance = TokenLedgerMaintenance(FakeLedgerDao(emptyList())) { now }
        assertEquals(30, TokenLedgerMaintenance.WINDOW_DAYS)
        assertEquals(now - 30 * dayMs, maintenance.windowStart())
    }
}

/** 只实现本用例消费的语义，其余聚合返回空 —— 断言点在调用参数与删除顺序。 */
private class FakeLedgerDao(initial: List<TokenLedgerEntry>) : TokenLedgerDao {

    val rows = initial.toMutableList()
    var lastSumSince: Long = Long.MIN_VALUE
    var lastSumTotalSince: Long = Long.MIN_VALUE
    var deleteCalls: Int = 0

    override suspend fun insert(entry: TokenLedgerEntry): Long {
        rows += entry
        return rows.size.toLong()
    }

    override suspend fun count(): Long = rows.size.toLong()

    override suspend fun earliestMs(): Long? = rows.minOfOrNull { it.createdAt }

    override suspend fun sumByAgent(sinceMs: Long): List<TokenAggregateRow> {
        lastSumSince = sinceMs
        return rows.filter { it.createdAt >= sinceMs }
            .groupBy { it.agentId }
            .map { (agent, list) ->
                val total = list.sumOf { it.promptTokens + it.completionTokens }.toLong()
                TokenAggregateRow(
                    label = agent,
                    promptTokens = list.sumOf { it.promptTokens }.toLong(),
                    completionTokens = list.sumOf { it.completionTokens }.toLong(),
                    totalTokens = total,
                    estimatedRows = list.count { !it.measured }.toLong(),
                    calls = list.size.toLong(),
                )
            }
            .sortedByDescending { it.totalTokens }
    }

    override suspend fun sumByAgentForMode(sinceMs: Long, mode: String): List<TokenAggregateRow> = emptyList()

    override suspend fun sumByEndpoint(sinceMs: Long): List<TokenAggregateRow> = emptyList()

    override suspend fun sumByHour(sinceMs: Long): List<TokenTimeBucket> = emptyList()

    override suspend fun sumByDay(sinceMs: Long): List<TokenTimeBucket> = emptyList()

    override suspend fun sumByHourForAgent(sinceMs: Long, agentId: String): List<TokenTimeBucket> = emptyList()

    override suspend fun sumTotal(): Long = rows.sumOf { it.promptTokens + it.completionTokens }.toLong()

    override suspend fun sumTotalSince(sinceMs: Long): Long {
        lastSumTotalSince = sinceMs
        return rows.filter { it.createdAt >= sinceMs }
            .sumOf { it.promptTokens + it.completionTokens }
            .toLong()
    }

    override suspend fun deleteAll() {
        deleteCalls++
        rows.clear()
    }
}
