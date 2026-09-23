package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.testConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmCallExecutorTest {

    private val config: AiEndpointConfig = testConfig()

    @Test
    fun `拼接多段 TextDelta 为完整文本`() = runTest {
        val transport = scriptTransport(
            LlmEvent.TextDelta("你好"),
            LlmEvent.TextDelta("世界"),
            LlmEvent.Completed,
        )
        val result = LlmCallExecutor.call(transport, config, messages(), tools = null, temperature = 0.7f)
        assertEquals("你好世界", result.text)
        assertFalse(result.failed)
        assertTrue(result.toolCalls.isEmpty())
    }

    @Test
    fun `收集 ToolCall 事件`() = runTest {
        val transport = scriptTransport(
            LlmEvent.ToolCall("c1", "some_tool", "{}"),
            LlmEvent.Completed,
        )
        val result = LlmCallExecutor.call(transport, config, messages(), tools = null, temperature = 0.7f)
        assertEquals(1, result.toolCalls.size)
        assertEquals("some_tool", result.toolCalls.first().name)
    }

    @Test
    fun `Failed 事件置 failed 与原因`() = runTest {
        val transport = scriptTransport(
            LlmEvent.Failed("boom"),
            LlmEvent.Completed,
        )
        val result = LlmCallExecutor.call(transport, config, messages(), tools = null, temperature = 0.7f)
        assertTrue(result.failed)
        assertEquals("boom", result.failedMessage)
    }

    @Test
    fun `Usage 真值写入 TokenMeter 并累加到 GlobalTokenCounter`() = runTest {
        val timeProvider: com.hmp.domain.agent.port.TimeProvider = { 1_700_000_000_000L }
        val counter = GlobalTokenCounter(timeProvider, dailyTokenQuota = 100_000)
        val meter = TokenMeter(counter, ledgerDao = null, timeProvider = timeProvider)

        val transport = scriptTransport(
            LlmEvent.Usage(promptTokens = 10, completionTokens = 5),
            LlmEvent.Completed,
        )
        LlmCallExecutor.call(
            transport, config, messages(), tools = null, temperature = 0.7f,
            meter = meter, taskId = "t1",
        )
        assertEquals(15L, counter.usedToday())
    }

    @Test
    fun `无 usage 的失败调用不污染账本`() = runTest {
        val timeProvider: com.hmp.domain.agent.port.TimeProvider = { 1_700_000_000_000L }
        val counter = GlobalTokenCounter(timeProvider, dailyTokenQuota = 100_000)
        val meter = TokenMeter(counter, ledgerDao = null, timeProvider = timeProvider)

        val transport = scriptTransport(LlmEvent.Failed("x"), LlmEvent.Completed)
        LlmCallExecutor.call(
            transport, config, messages(), tools = null, temperature = 0.7f,
            meter = meter, taskId = "t2",
        )
        assertEquals(0L, counter.usedToday())
    }

    @Test
    fun `传输层抛异常被吞并为 failed`() = runTest {
        val transport = object : LlmTransport {
            override suspend fun streamChat(
                config: AiEndpointConfig,
                messages: List<LlmMessage>,
                tools: List<LlmToolSpec>?,
                temperature: Float,
            ): Flow<LlmEvent> = flow {
                emit(LlmEvent.TextDelta("partial"))
                throw RuntimeException("stream broke")
            }
        }
        val result = LlmCallExecutor.call(transport, config, messages(), tools = null, temperature = 0.7f)
        assertTrue(result.failed)
        // collect 语义：流中途抛异常 → toList() 直接抛出被兜底，之前 emit 的 partial 文本全部丢弃
        assertEquals("", result.text)
        assertEquals("stream broke", result.failedMessage)
    }

    @Test
    fun `CancellationException 不被吞并`() = runTest {
        val transport = object : LlmTransport {
            override suspend fun streamChat(
                config: AiEndpointConfig,
                messages: List<LlmMessage>,
                tools: List<LlmToolSpec>?,
                temperature: Float,
            ): Flow<LlmEvent> = flow {
                throw CancellationException("cancelled")
            }
        }
        assertFailsWith<CancellationException> {
            LlmCallExecutor.call(transport, config, messages(), tools = null, temperature = 0.7f)
        }
    }

    private fun messages(): List<LlmMessage> = listOf(LlmMessage(role = "user", content = "hi"))

    private fun scriptTransport(vararg events: LlmEvent): LlmTransport =
        object : LlmTransport {
            override suspend fun streamChat(
                config: AiEndpointConfig,
                messages: List<LlmMessage>,
                tools: List<LlmToolSpec>?,
                temperature: Float,
            ): Flow<LlmEvent> = flow { events.forEach { emit(it) } }
        }
}
