package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.PresenceEvent
import com.hmp.domain.agent.policy.AgentPolicy
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.port.ConfirmOutcome
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.agent.port.ToolPermissionLevel
import com.hmp.domain.agent.tool.spec.AgentTool
import com.hmp.domain.agent.tool.spec.ToolRegistry
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.FakeAuditLogPort
import com.hmp.test.fakes.FakeConfirmGate
import com.hmp.test.fakes.FakeStopSignal
import com.hmp.test.fakes.RecordingTool
import com.hmp.test.fakes.perTurnTransport
import com.hmp.test.fakes.testConfig
import com.hmp.test.fakes.userMsg
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReActLoopTest {

    private val config = testConfig()
    private val systemPrompt = "sys"
    private val input = listOf(userMsg("hi"))
    private val policy = AgentPolicy.master()

    private fun loop(
        policyGuard: PolicyGuard? = null,
        auditLog: com.hmp.domain.agent.port.AuditLogPort? = null,
        presenceBus: PresenceBus? = null,
        stopSignal: StopSignal? = null,
        tokenMeter: TokenMeter? = null,
    ) = ReActLoop(
        temperature = 0.7f,
        policyGuard = policyGuard,
        confirmGate = null,
        auditLog = auditLog,
        presenceBus = presenceBus,
        stopSignal = stopSignal,
        tokenMeter = tokenMeter,
    )

    private fun registry(vararg tools: AgentTool) = ToolRegistry(tools.toList())

    /** 第一轮返回工具调用，第二轮永不返回 —— 用于在「工具已执行、循环未结束」时取消。 */
    private class HangingAfterFirstTransport : LlmTransport {
        var calls = 0
            private set

        override suspend fun streamChat(
            config: AiEndpointConfig,
            messages: List<LlmMessage>,
            tools: List<LlmToolSpec>?,
            temperature: Float,
        ): Flow<LlmEvent> = flow {
            calls++
            if (calls == 1) {
                emit(LlmEvent.ToolCall("c1", "recording_tool", "{}"))
                emit(LlmEvent.Completed)
            } else {
                awaitCancellation()
            }
        }
    }

    @Test
    fun `取消时仍回调已执行的工具（供落摘要）`() = runTest {
        var captured: List<ToolExecutionRecord>? = null
        val transport = HangingAfterFirstTransport()
        val job = launch {
            loop().run(
                policy,
                transport,
                config,
                systemPrompt,
                input,
                registry = registry(RecordingTool()),
                onRunFinished = { captured = it },
            )
        }
        runCurrent()
        runCurrent()
        assertTrue(transport.calls >= 2, "第一轮工具应已执行完并进入第二轮")

        job.cancelAndJoin()

        assertEquals(
            listOf("recording_tool"),
            captured?.map { it.toolName },
            "取消后也必须把已执行的工具交出来（NonCancellable 写库的前提）",
        )
    }

    @Test
    fun `确认超时 → 跳过工具并带明确原因结束本轮`() = runTest {
        val policy = AgentPolicy.master(confirmGate = FakeConfirmGate { ConfirmOutcome.Timeout })
        val tool = RecordingTool(name = "strong_tool", permissionLevel = ToolPermissionLevel.STRONG_CONFIRM)
        val transport = perTurnTransport(
            listOf(LlmEvent.ToolCall("c1", "strong_tool", "{}"), LlmEvent.Completed),
            listOf(LlmEvent.TextDelta("不应到达"), LlmEvent.Completed),
        )

        val result = loop(policyGuard = PolicyGuard(FakeAuditLogPort()))
            .run(policy, transport, config, systemPrompt, input, registry = registry(tool))

        assertEquals(TerminationReason.CONFIRM_TIMEOUT, result.terminatedBy)
        assertTrue(result.text.contains("确认超时"), "要给用户和模型一个明确原因")
        assertEquals(0, tool.receivedArgs.size, "超时的工具不能被执行")
    }

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

    /** 用户对工具点「总是允许」的脚本：CONFIRM 级工具 + 全 AllowAlways 的确认门。 */
    private fun allowAlwaysScenario() = AgentPolicy.master(
        confirmGate = FakeConfirmGate { ConfirmOutcome.AllowAlways },
    )

    private fun strongToolTurns() = perTurnTransport(
        listOf(LlmEvent.ToolCall("c1", "strong_tool", "{}"), LlmEvent.Completed),
        listOf(LlmEvent.TextDelta("done"), LlmEvent.Completed),
    )

    private fun strongToolRegistry() = registry(
        RecordingTool(name = "strong_tool", permissionLevel = ToolPermissionLevel.STRONG_CONFIRM),
    )

    @Test
    fun `勾了总是允许 → 立即触发保存回调并写入白名单`() = runTest {
        var saved = 0
        val p = allowAlwaysScenario()
        val result = loop(policyGuard = PolicyGuard(FakeAuditLogPort())).run(
            p,
            strongToolTurns(),
            config,
            systemPrompt,
            input,
            registry = strongToolRegistry(),
            onAlwaysAllowChanged = { saved++ },
        )
        assertEquals(TerminationReason.ANSWERED, result.terminatedBy)
        assertEquals(1, saved, "保存回调必须与白名单写入同一步发生，而不是等循环结束")
        assertTrue(p.config.alwaysAllow.contains("strong_tool"))
    }

    @Test
    fun `循环中途中断 → 保存已发生且思考态仍复位`() = runTest {
        val bus = PresenceBus()
        val events = mutableListOf<PresenceEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect { events += it }
        }
        var saved = 0
        val p = allowAlwaysScenario()
        val outcome = runCatching {
            loop(policyGuard = PolicyGuard(FakeAuditLogPort()), presenceBus = bus).run(
                p,
                strongToolTurns(),
                config,
                systemPrompt,
                input,
                registry = strongToolRegistry(),
                onAlwaysAllowChanged = {
                    saved++
                    error("模拟中途取消 / 异常中断")
                },
            )
        }
        runCurrent()
        assertTrue(outcome.isFailure, "保存回调里抛异常应中断循环")
        assertEquals(1, saved, "用户取消 / 异常前，勾选已经落盘")
        assertTrue(p.config.alwaysAllow.contains("strong_tool"))
        assertTrue(
            events.any { it is PresenceEvent.TaskProgress && it.phase == "idle" && !it.active },
            "中断也必须把「思考中」复位",
        )
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
        runCurrent() // 泵一次调度，让收集者消费缓冲中的事件
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
            override suspend fun sumByAgentForMode(sinceMs: Long, mode: String): List<com.hmp.data.database.TokenAggregateRow> = listOf(
                com.hmp.data.database.TokenAggregateRow(
                    label = "master",
                    promptTokens = 40_000_000L,
                    completionTokens = 0L,
                    totalTokens = 40_000_000L,
                    estimatedRows = 0L,
                    calls = 1L,
                ),
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
