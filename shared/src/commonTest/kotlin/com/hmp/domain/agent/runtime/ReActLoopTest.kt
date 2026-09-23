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
        tokenCounter: GlobalTokenCounter? = null,
        policyGuard: PolicyGuard? = null,
        auditLog: com.hmp.domain.agent.port.AuditLogPort? = null,
        presenceBus: PresenceBus? = null,
        stopSignal: StopSignal? = null,
        tokenMeter: TokenMeter? = null,
    ) = ReActLoop(
        stepBudget = stepBudget,
        temperature = 0.7f,
        tokenCounter = tokenCounter,
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
    fun `云端配额耗尽 → CLOUD_QUOTA_EXHAUSTED`() = runTest {
        val timeProvider: com.hmp.domain.agent.port.TimeProvider = { 1_700_000_000_000L }
        val counter = GlobalTokenCounter(timeProvider, dailyTokenQuota = 1000)
        counter.recordTokens(1000) // usageRate = 1.0 > 0.95
        assertTrue(counter.shouldStop())
        val transport = perTurnTransport(listOf(LlmEvent.TextDelta("x"), LlmEvent.Completed))
        val result = loop(tokenCounter = counter).run(policy, transport, config, systemPrompt, input, registry = registry(RecordingTool()))
        assertEquals(TerminationReason.CLOUD_QUOTA_EXHAUSTED, result.terminatedBy)
        assertEquals(0, result.stepsUsed)
    }

    @Test
    fun `停止信号软停 → FAILED`() = runTest {
        val transport = perTurnTransport(listOf(LlmEvent.TextDelta("x"), LlmEvent.Completed))
        val result = loop(stopSignal = FakeStopSignal(softStop = true)).run(
            policy, transport, config, systemPrompt, input, registry = registry(RecordingTool()),
        )
        assertEquals(TerminationReason.FAILED, result.terminatedBy)
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
}
