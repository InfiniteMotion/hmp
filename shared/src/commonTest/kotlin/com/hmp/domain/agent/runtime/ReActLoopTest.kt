package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.PresenceEvent
import com.hmp.domain.agent.policy.AgentPolicy
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.tool.spec.ToolRegistry
import com.hmp.domain.agent.tool.spec.AgentTool
import com.hmp.test.fakes.FakeStopSignal
import com.hmp.test.fakes.RecordingTool
import com.hmp.test.fakes.perTurnTransport
import com.hmp.test.fakes.testConfig
import com.hmp.test.fakes.userMsg
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReActLoopTest {

    private val config = testConfig()
    private val systemPrompt = "sys"
    private val input = listOf(userMsg("hi"))
    private val policy = AgentPolicy.master()

    private fun loop(
        stepBudget: Int = 8,
        policyGuard: PolicyGuard? = null,
        auditLog: com.hmp.domain.agent.port.AuditLogPort? = null,
        presenceBus: PresenceBus? = null,
        stopSignal: StopSignal? = null,
        tokenMeter: TokenMeter? = null,
    ) = ReActLoop(
        stepBudget = stepBudget,
        temperature = 0.7f,
        policyGuard = policyGuard,
        confirmGate = null,
        auditLog = auditLog,
        presenceBus = presenceBus,
        stopSignal = stopSignal,
        tokenMeter = tokenMeter,
    )

    private fun registry(vararg tools: AgentTool) = ToolRegistry(tools.toList())

    @Test
    fun `单轮直接回答 → ANSWERED`() = runTest {
        val transport = perTurnTransport(listOf(LlmEvent.TextDelta("你好"), LlmEvent.Completed))
        val result = loop().run(policy, transport, config, systemPrompt, input, registry = registry(RecordingTool()))
        assertEquals(TerminationReason.ANSWERED, result.terminatedBy)
        assertEquals("你好", result.text)
        assertEquals(1, result.stepsUsed)
        assertTrue(result.toolCalls.isEmpty())
    }

    @Test
    fun `一轮 tool_call + 二轮回答 → ANSWERED 且工具被执行回传`() = runTest {
        val recording = RecordingTool()
        val transport = perTurnTransport(
            listOf(LlmEvent.ToolCall("c1", "recording_tool", "{}"), LlmEvent.Completed),
            listOf(LlmEvent.TextDelta("done"), LlmEvent.Completed),
        )
        val result = loop().run(policy, transport, config, systemPrompt, input, registry = registry(recording))
        assertEquals(TerminationReason.ANSWERED, result.terminatedBy)
        assertEquals("done", result.text)
        assertEquals(2, result.stepsUsed)
        assertEquals(1, result.toolCalls.size)
        assertEquals("success", result.toolCalls.first().outcome)
        assertEquals(1, recording.receivedArgs.size)
    }

    @Test
    fun `步数预算耗尽 → STEP_BUDGET_EXHAUSTED`() = runTest {
        val transport = perTurnTransport(
            listOf(LlmEvent.ToolCall("c1", "recording_tool", "{}"), LlmEvent.Completed),
            listOf(LlmEvent.TextDelta("x"), LlmEvent.Completed),
        )
        val result = loop(stepBudget = 1).run(policy, transport, config, systemPrompt, input, registry = registry(RecordingTool()))
        assertEquals(TerminationReason.STEP_BUDGET_EXHAUSTED, result.terminatedBy)
        assertEquals(1, result.stepsUsed)
    }

    @Test
    fun `停止信号软停 → CLOUD_QUOTA_EXHAUSTED`() = runTest {
        // 全局日熔断已删除：子 Agent 的桶耗尽经 StopSignal 传进来，Master 侧只看 quotaExhausted
        val transport = perTurnTransport(listOf(LlmEvent.TextDelta("x"), LlmEvent.Completed))
        val result = loop(stopSignal = FakeStopSignal(softStop = true))
            .run(policy, transport, config, systemPrompt, input, registry = registry(RecordingTool()))
        assertEquals(TerminationReason.CLOUD_QUOTA_EXHAUSTED, result.terminatedBy)
        assertEquals(0, result.stepsUsed)
    }

    @Test
    fun `LLM 流式失败 → FAILED 并给降级文案`() = runTest {
        val transport = perTurnTransport(listOf(LlmEvent.Failed("boom"), LlmEvent.Completed))
        val result = loop().run(policy, transport, config, systemPrompt, input, registry = registry(RecordingTool()))
        assertEquals(TerminationReason.FAILED, result.terminatedBy)
        assertTrue(result.text.isNotBlank())
    }

    @Test
    fun `onSessionComplete 在结束前被回调一次`() = runTest {
        var called = 0
        val transport = perTurnTransport(listOf(LlmEvent.TextDelta("hi"), LlmEvent.Completed))
        loop().run(
            policy, transport, config, systemPrompt, input,
            registry = registry(RecordingTool()),
            onSessionComplete = { called++ },
        )
        assertEquals(1, called)
    }

    @Test
    fun `PresenceBus 发出 thinking 与 idle 进度事件`() = runTest {
        val bus = PresenceBus()
        val events = mutableListOf<PresenceEvent>()
        // UNDISPATCHED：订阅在 run() 首次 emit 前同步生效；backgroundScope 结束时自动取消
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect { events += it }
        }
        val transport = perTurnTransport(listOf(LlmEvent.TextDelta("hi"), LlmEvent.Completed))
        loop(presenceBus = bus).run(policy, transport, config, systemPrompt, input, registry = registry(RecordingTool()))
        runCurrent()   // 泵一次调度，让收集者消费缓冲中的事件
        assertTrue(events.any { it is PresenceEvent.TaskProgress && it.phase == "thinking" }, "应发出 thinking 进度事件")
        assertTrue(events.any { it is PresenceEvent.TaskProgress && it.phase == "idle" }, "应发出 idle 进度事件")
    }

    /**
     * T3 判据：免费桶用完后，Master 的对话**不得发出请求**，且给的是可行动话术。
     */
    @Test
    fun quotaExhausted_stopsBeforeTransport_andSaysWhy() = runTest {
        val dao = object : com.hmp.data.database.TokenLedgerDao {
            override suspend fun insert(entry: com.hmp.data.database.TokenLedgerEntry): Long = 0L
            override suspend fun count(): Long = 1L
            override suspend fun earliestMs(): Long? = null
            override suspend fun sumByAgent(sinceMs: Long): List<com.hmp.data.database.TokenAggregateRow> = emptyList()
            override suspend fun sumByAgentForMode(sinceMs: Long, mode: String): List<com.hmp.data.database.TokenAggregateRow> =
                listOf(
                    com.hmp.data.database.TokenAggregateRow(
                        label = "master",
                        promptTokens = 40_000_000L,
                        completionTokens = 0L,
                        totalTokens = 40_000_000L,
                        estimatedRows = 0L,
                        calls = 1L,
                    )
                )
            override suspend fun sumByEndpoint(sinceMs: Long): List<com.hmp.data.database.TokenAggregateRow> = emptyList()
            override suspend fun sumByHour(sinceMs: Long): List<com.hmp.data.database.TokenTimeBucket> = emptyList()
            override suspend fun sumByDay(sinceMs: Long): List<com.hmp.data.database.TokenTimeBucket> = emptyList()
            override suspend fun sumByHourForAgent(sinceMs: Long, agentId: String): List<com.hmp.data.database.TokenTimeBucket> = emptyList()
            override suspend fun sumTotal(): Long = 40_000_000L
            override suspend fun sumTotalSince(sinceMs: Long): Long = sumTotal()
            override suspend fun deleteAll() {}
        }
        val gate = com.hmp.domain.agent.quota.TokenQuotaGate(
            ledgerDao = dao,
            settingsRepository = com.hmp.test.fakes.FakeSettingsRepository(),
            timeProvider = { 0L },
        )
        val meter = TokenMeter(ledgerDao = dao, timeProvider = { 0L }, quotaGate = gate)

        val transport = perTurnTransport(listOf(LlmEvent.TextDelta("不该被调用"), LlmEvent.Completed)) as com.hmp.test.fakes.FakeLlmTransport
        val result = loop(tokenMeter = meter).run(policy, transport, config, systemPrompt, input, registry = registry())

        assertTrue(transport.calls.isEmpty(), "配额耗尽时不得发出任何 LLM 请求")
        assertTrue(result.text.contains("额度"), "应给用户可行动的话术，而不是\"对话中断\"")
    }
}
