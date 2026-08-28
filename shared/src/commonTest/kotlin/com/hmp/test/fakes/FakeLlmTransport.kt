package com.hmp.test.fakes

import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 脚本化 LlmTransport 替身（任务书 M2-T4，M3/M4/M6 消费）。
 *
 * 脚本语义：逐条发射 [script]；用 `LlmEvent.Failed` 模拟中断/超时；
 * [failOnCall] 模拟调用即失败（HTTP 错误路径）。
 * 每次调用记录 [calls]，供断言请求内容（messages/tools/temperature）。
 */
class FakeLlmTransport(
    private val script: List<LlmEvent>,
    private val failOnCall: Boolean = false,
) : LlmTransport {

    data class CallRecord(
        val config: AiEndpointConfig,
        val messages: List<LlmMessage>,
        val tools: List<LlmToolSpec>?,
        val temperature: Float,
    )

    val calls = mutableListOf<CallRecord>()

    override suspend fun streamChat(
        config: AiEndpointConfig,
        messages: List<LlmMessage>,
        tools: List<LlmToolSpec>?,
        temperature: Float,
    ): Flow<LlmEvent> = flow {
        calls += CallRecord(config, messages, tools, temperature)
        if (failOnCall) {
            emit(LlmEvent.Failed("scripted failure"))
        } else {
            script.forEach { emit(it) }
        }
    }
}