package com.hmp.domain.agent.quota

import com.hmp.data.database.TokenAggregateRow
import com.hmp.data.database.TokenLedgerDao
import com.hmp.data.database.TokenLedgerEntry
import com.hmp.data.database.TokenTimeBucket
import com.hmp.domain.agent.runtime.GlobalAgentConfig
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T2 判据：免费 100M 总额 + 四桶硬顶（40/25/25/10）、自定义日限额、重启不归零。
 *
 * 种子数据直接喂聚合行（不可能真插 4000 万行），闸门只看 `SUM` 结果。
 */
class TokenQuotaGateTest {

    private class SeededDao(private var rows: List<TokenAggregateRow>) : TokenLedgerDao {
        override suspend fun insert(entry: TokenLedgerEntry): Long = 0L
        override suspend fun count(): Long = rows.sumOf { it.calls }
        override suspend fun earliestMs(): Long? = null
        override suspend fun sumByAgent(sinceMs: Long): List<TokenAggregateRow> = rows
        override suspend fun sumByAgentForMode(sinceMs: Long, mode: String): List<TokenAggregateRow> = rows
        override suspend fun sumByEndpoint(sinceMs: Long): List<TokenAggregateRow> = emptyList()
        override suspend fun sumByHour(sinceMs: Long): List<TokenTimeBucket> = emptyList()
        override suspend fun sumByDay(sinceMs: Long): List<TokenTimeBucket> = emptyList()
        override suspend fun sumByHourForAgent(sinceMs: Long, agentId: String): List<TokenTimeBucket> = emptyList()
        override suspend fun sumTotal(): Long = rows.sumOf { it.totalTokens }
        override suspend fun sumTotalSince(sinceMs: Long): Long = sumTotal()
        override suspend fun deleteAll() {
            rows = emptyList()
        }
    }

    private fun row(agent: String, total: Long) = TokenAggregateRow(
        label = agent,
        promptTokens = total,
        completionTokens = 0,
        totalTokens = total,
        estimatedRows = 0,
        calls = 1,
    )

    private fun gate(rows: List<TokenAggregateRow>, settings: FakeSettingsRepository = FakeSettingsRepository()) = TokenQuotaGate(
        ledgerDao = SeededDao(rows),
        settingsRepository = settings,
        timeProvider = { 1_000_000L },
    )

    @Test
    fun free_bucketCap_blocksOnlyThatBucket_andProfileCountsAsMaster() = runTest {
        val gate = gate(listOf(row("master", 40_000_000L)))

        assertIs<QuotaDecision.Denied>(gate.check("master"))
        assertIs<QuotaDecision.Denied>(gate.check("profile"), "画像记进 master 桶")
        assertIs<QuotaDecision.Allowed>(gate.check("radio"))
        assertIs<QuotaDecision.Allowed>(gate.check("enrich"))
        assertIs<QuotaDecision.Allowed>(gate.check("hello"))
    }

    @Test
    fun free_totalCap_blocksEveryBucket() = runTest {
        // 四桶都未到各自上限，但总和已经到 100M —— 总池优先
        val gate = gate(
            listOf(
                row("master", 39_000_000L),
                row("radio", 24_000_000L),
                row("enrich", 24_000_000L),
                row("hello", 13_000_000L),
            ),
        )

        listOf("master", "radio", "enrich", "hello").forEach { agent ->
            val decision = gate.check(agent)
            assertIs<QuotaDecision.Denied>(decision, "$agent 应被总池拦下")
            assertEquals(QuotaDecision.Denied.Scope.TOTAL, decision.scope)
        }
    }

    @Test
    fun free_usageIsSeededFromLedger_newInstanceStillSeesIt() = runTest {
        // "重启"：新实例不共享内存，只能靠账本回填
        val rows = listOf(row("master", 40_000_000L))

        assertIs<QuotaDecision.Denied>(gate(rows).check("master"))
        assertIs<QuotaDecision.Denied>(gate(rows).check("master"))
    }

    @Test
    fun record_tripsTheBucketCap_withoutNewSeed() = runTest {
        val settings = FakeSettingsRepository()
        settings.saveAiAccessMode(AiAccessMode.CUSTOM)
        settings.saveGlobalAgentConfig(
            GlobalAgentConfig(tokenLimitsEnabled = true, perAgentDailyTokenQuota = mapOf("master" to 100L)),
        )
        val gate = gate(emptyList(), settings)

        assertIs<QuotaDecision.Allowed>(gate.check("master"))
        gate.record("master", 60L)
        assertIs<QuotaDecision.Allowed>(gate.check("master"))
        gate.record("master", 40L)
        assertIs<QuotaDecision.Denied>(gate.check("master"))
    }

    @Test
    fun custom_limitsDisabled_neverBlocks() = runTest {
        val settings = FakeSettingsRepository()
        settings.saveAiAccessMode(AiAccessMode.CUSTOM)
        settings.saveGlobalAgentConfig(
            GlobalAgentConfig(tokenLimitsEnabled = false, perAgentDailyTokenQuota = mapOf("master" to 1L)),
        )
        val gate = gate(listOf(row("master", 999_999_999L)), settings)

        assertIs<QuotaDecision.Allowed>(gate.check("master"))
    }

    @Test
    fun custom_globalDailyCap_blocksAllBuckets() = runTest {
        val settings = FakeSettingsRepository()
        settings.saveAiAccessMode(AiAccessMode.CUSTOM)
        settings.saveGlobalAgentConfig(
            GlobalAgentConfig(tokenLimitsEnabled = true, dailyTokenQuota = 1_000),
        )
        val gate = gate(listOf(row("radio", 700L), row("master", 300L)), settings)

        val decision = gate.check("enrich")
        assertIs<QuotaDecision.Denied>(decision)
        assertEquals(QuotaDecision.Denied.Scope.TOTAL, decision.scope)
    }

    @Test
    fun custom_windowIsDaily_yesterdaysUsageDoesNotCount() = runTest {
        val settings = FakeSettingsRepository()
        settings.saveAiAccessMode(AiAccessMode.CUSTOM)
        settings.saveGlobalAgentConfig(GlobalAgentConfig(tokenLimitsEnabled = true, dailyTokenQuota = 1_000))

        // 全历史 1500（超日额），今天窗口内 0 —— 若实现忽略窗口，这里会被误拦
        val dao = object : TokenLedgerDao {
            override suspend fun insert(entry: TokenLedgerEntry): Long = 0L
            override suspend fun count(): Long = 1L
            override suspend fun earliestMs(): Long? = null
            override suspend fun sumByAgent(sinceMs: Long): List<TokenAggregateRow> = emptyList()
            override suspend fun sumByAgentForMode(sinceMs: Long, mode: String): List<TokenAggregateRow> = if (sinceMs == 0L) listOf(row("master", 1_500L)) else emptyList()
            override suspend fun sumByEndpoint(sinceMs: Long): List<TokenAggregateRow> = emptyList()
            override suspend fun sumByHour(sinceMs: Long): List<TokenTimeBucket> = emptyList()
            override suspend fun sumByDay(sinceMs: Long): List<TokenTimeBucket> = emptyList()
            override suspend fun sumByHourForAgent(sinceMs: Long, agentId: String): List<TokenTimeBucket> = emptyList()
            override suspend fun sumTotal(): Long = 1_500L
            override suspend fun sumTotalSince(sinceMs: Long): Long = sumTotal()
            override suspend fun deleteAll() {}
        }
        val gate = TokenQuotaGate(dao, settings, timeProvider = { 86_400_000L * 3 })

        assertIs<QuotaDecision.Allowed>(gate.check("master"))
    }
}
