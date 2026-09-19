package com.hmp.domain.agent.runtime

import com.hmp.data.database.TokenAggregateRow
import com.hmp.data.database.TokenLedgerDao
import com.hmp.data.database.TokenLedgerEntry
import com.hmp.data.database.TokenTimeBucket
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.FakeLlmTransport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [TokenLedgerDao] 替身：仅记录 insert，聚合查询返回空（单测只验证"写入"语义）。 */
private class FakeTokenLedgerDao : TokenLedgerDao {
    val inserted = mutableListOf<TokenLedgerEntry>()
    override suspend fun insert(entry: TokenLedgerEntry): Long {
        inserted += entry
        return inserted.size.toLong()
    }
    override suspend fun count(): Long = inserted.size.toLong()
    override suspend fun earliestMs(): Long? = inserted.minOfOrNull { it.createdAt }
    override suspend fun sumByAgent(sinceMs: Long): List<TokenAggregateRow> = emptyList()
    override suspend fun sumByEndpoint(sinceMs: Long): List<TokenAggregateRow> = emptyList()
    override suspend fun sumByHour(sinceMs: Long): List<TokenTimeBucket> = emptyList()
    override suspend fun sumByDay(sinceMs: Long): List<TokenTimeBucket> = emptyList()
    override suspend fun sumByHourForAgent(sinceMs: Long, agentId: String): List<TokenTimeBucket> = emptyList()
    override suspend fun sumTotal(): Long = inserted.sumOf { (it.promptTokens + it.completionTokens).toLong() }
    override suspend fun deleteAll() { inserted.clear() }
}

/**
 * F12-T1 / T3 / T4 计量与窗口护栏的确定性验证。
 *
 * 直接覆盖 taskbook 验收 S1（真值入账三处 + 分账维度）、S2（无 usage 兜底 + 失败不记）、
 * S4/S8/S9（超窗前置拒绝、不真正发请求、失败原因可区分）。
 */
class F12TokenMeteringTest {

    private fun meterWith(dao: FakeTokenLedgerDao): Pair<TokenMeter, GlobalTokenCounter> {
        val counter = GlobalTokenCounter(timeProvider = { 0L })
        val meter = TokenMeter(counter = counter, ledgerDao = dao, timeProvider = { 123L })
        return meter to counter
    }

    private val cfg = AiEndpointConfig(
        endpoint = "https://api.deepseek.com/v1",
        selectedModel = "deepseek-chat",
    )

    // S1：真值同时写三处（当日累加 + 明细账本），分账维度正确（host / model / agent）
    @Test
    fun recordsTrueUsageToLedgerAndCounter() = runBlocking {
        val dao = FakeTokenLedgerDao()
        val (meter, counter) = meterWith(dao)

        val entry = meter.record(
            agentId = TokenMeter.AGENT_MASTER,
            config = cfg,
            usage = LlmEvent.Usage(promptTokens = 100, completionTokens = 50, cachedTokens = 0),
            messages = emptyList(),
            outputText = "",
            failed = false,
        )

        // ① 当日累加（配额仲裁的输入）
        assertEquals(150L, counter.usedToday())
        // ② 明细账本新增一行
        assertEquals(1, dao.inserted.size)
        // 真值 + 分账维度
        assertTrue(entry?.measured == true)
        assertEquals(100, entry?.promptTokens)
        assertEquals(50, entry?.completionTokens)
        assertEquals("api.deepseek.com", entry?.endpointHost) // 只存 host，不存完整 URL
        assertEquals("deepseek-chat", entry?.model)
        assertEquals(TokenMeter.AGENT_MASTER, entry?.agentId)
    }

    // S2：拿不到 usage → 估算兜底 + 打标 measured=false；失败且无 usage → 不污染账本
    @Test
    fun fallsBackToEstimateAndSkipsFailedWithoutUsage() = runBlocking {
        val dao = FakeTokenLedgerDao()
        val (meter, counter) = meterWith(dao)

        val est = meter.record(
            agentId = TokenMeter.AGENT_ENRICH,
            config = cfg,
            usage = null,
            messages = listOf(LlmMessage(role = "user", content = "hello world")),
            outputText = "",
            failed = false,
        )
        assertTrue(est?.measured == false, "无 usage 时必须打标 measured=false")
        assertTrue((est?.promptTokens ?: 0) > 0, "估算兜底必须给出非空 promptTokens")
        assertEquals(1, dao.inserted.size)

        // 失败且无 usage：不应记账（没有消费凭据）
        val failed = meter.record(
            agentId = TokenMeter.AGENT_RADIO,
            config = cfg,
            usage = null,
            messages = emptyList(),
            outputText = "",
            failed = true,
        )
        assertNull(failed)
        assertEquals(1, dao.inserted.size) // 行数不变
    }

    // S4/S8/S9：超窗前置拒绝 —— 不真正发起请求；失败原因带 [超窗] 前缀，与"模型判 none"可区分
    @Test
    fun overWindowIsPreRejectedWithoutCallingTransport() = runBlocking {
        val transport = FakeLlmTransport(
            script = listOf(LlmEvent.TextDelta("hi"), LlmEvent.Completed),
        )
        val budget = AgentContextBudget(
            agentId = TokenMeter.AGENT_MASTER,
            maxContextTokens = 4, // 极小窗口，强制触发前置守卫
            llmClient = transport,
            tokenMeter = null,
        )

        val events = budget.callLlm(
            config = cfg,
            systemPrompt = "sys",
            newMessages = listOf(LlmMessage(role = "user", content = "hello")),
            tools = null,
            temperature = 0.3f,
        ).toList()

        // 从未真正调用传输层（护栏在前，不发出必失败的请求）
        assertTrue(transport.calls.isEmpty(), "超窗请求不应发出，实际调用了 ${transport.calls.size} 次")
        // 唯一终态是 Failed，且原因明确是超窗
        val failed = events.filterIsInstance<LlmEvent.Failed>()
        assertEquals(1, failed.size)
        assertTrue(
            failed.first().message.startsWith(AgentContextBudget.OVER_WINDOW_PREFIX),
            "失败原因必须带 [超窗] 前缀，实际: ${failed.first().message}",
        )
        // 不走通，不应出现 Completed
        assertTrue(events.none { it is LlmEvent.Completed })
    }
}
