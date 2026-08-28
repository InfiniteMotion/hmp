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
 * 脚本语义：
 * - [script]：每轮调用都重放同一序列（单轮场景，如单次对话/一次工具调用后的校验）。
 * - [perTurnScript]：按轮次逐轮消费（tool-loop 多轮场景——第 1 轮发 tool_call、第 2 轮给最终回答），
 *   越界时重放最后一轮（防意外耗尽崩溃）。
 * 用 `LlmEvent.Failed` 模拟中断/超时；[failOnCall] 模拟调用即失败（HTTP 错误路径）。
 * 每次调用记录 [calls]，供断言请求内容（messages/tools/temperature）。
 */
class FakeLlmTransport(
    private val script: List<LlmEvent> = emptyList(),
    private val perTurnScript: List<List<LlmEvent>> = emptyList(),
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
        val index = calls.size
        calls += CallRecord(config, messages, tools, temperature)
        if (failOnCall) {
            emit(LlmEvent.Failed("scripted failure"))
        } else if (perTurnScript.isNotEmpty()) {
            val turn = perTurnScript[minOf(index, perTurnScript.lastIndex)]
            turn.forEach { emit(it) }
        } else {
            script.forEach { emit(it) }
        }
    }
}