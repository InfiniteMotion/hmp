package com.hmp.data.network

import com.hmp.data.network.dto.OpenAiAssistantToolCall
import com.hmp.data.network.dto.OpenAiFunctionCall
import com.hmp.data.network.dto.OpenAiFunctionSpec
import com.hmp.data.network.dto.OpenAiMessage
import com.hmp.data.network.dto.OpenAiStyleRequest
import com.hmp.data.network.dto.OpenAiStyleResponse
import com.hmp.data.network.dto.OpenAiTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 协议 DTO 序列化形状测试（任务书 M2-T1 + M2-T3）：
 * tools/tool_choice 的形状与省略规则、temperature 修正档位。
 */
class OpenAiRequestDtoTest {

    private val json = Json

    @Test
    fun requestWithTools_serializesFunctionShape() {
        val encoded = json.encodeToString(
            OpenAiStyleRequest(
                model = "m",
                messages = listOf(OpenAiMessage(role = "user", content = "hi")),
                temperature = 0.3f,
                tools = listOf(
                    OpenAiTool(
                        function = OpenAiFunctionSpec(
                            name = "searchLibrary",
                            description = "搜索曲库",
                            parameters = JsonObject(mapOf("type" to JsonPrimitive("object"))),
                        )
                    )
                ),
                toolChoice = JsonPrimitive("auto"),
            )
        )

        assertTrue(encoded.contains("\"tools\""), "应序列化 tools")
        assertTrue(encoded.contains("\"type\":\"function\""), "工具类型应为 function")
        assertTrue(encoded.contains("\"name\":\"searchLibrary\""))
        assertTrue(encoded.contains("\"description\":\"搜索曲库\""))
        assertTrue(encoded.contains("\"parameters\":{\"type\":\"object\"}"))
        assertTrue(encoded.contains("\"tool_choice\":\"auto\""))
        assertTrue(encoded.contains("\"temperature\":0.3"), "JSON 任务默认档 0.3（M2-T3 修正）")
    }

    @Test
    fun requestWithoutTools_omitsToolsAndToolChoice() {
        val encoded = json.encodeToString(
            OpenAiStyleRequest(model = "m", messages = listOf(OpenAiMessage("user", "hi")))
        )

        assertFalse(encoded.contains("\"tools\""))
        assertFalse(encoded.contains("tool_choice"))
    }

    @Test
    fun toolResultMessage_carriesToolCallId() {
        val encoded = json.encodeToString(
            OpenAiStyleRequest(
                model = "m",
                messages = listOf(
                    OpenAiMessage(role = "tool", content = "{\"ok\":true}", toolCallId = "call_1"),
                )
            )
        )

        assertTrue(encoded.contains("\"tool_call_id\":\"call_1\""))
    }

    @Test
    fun assistantToolCallsMessage_serializesCallShape() {
        // M4 引擎循环回传路径：assistant 消息原样带 tool_calls 数组（review 补齐 2026-08-28）
        val encoded = json.encodeToString(
            OpenAiStyleRequest(
                model = "m",
                messages = listOf(
                    OpenAiMessage(
                        role = "assistant",
                        content = null,
                        toolCalls = listOf(
                            OpenAiAssistantToolCall(
                                id = "call_7",
                                function = OpenAiFunctionCall(name = "searchLibrary", arguments = "{\"query\":\"摇滚\"}"),
                            )
                        ),
                    ),
                    OpenAiMessage(role = "tool", content = "[]", toolCallId = "call_7"),
                )
            )
        )

        assertTrue(encoded.contains("\"tool_calls\":[{\"id\":\"call_7\""), "assistant 消息应携带 tool_calls 数组")
        assertTrue(encoded.contains("\"type\":\"function\""), "调用类型应为 function")
        assertTrue(encoded.contains("\"name\":\"searchLibrary\""))
        assertTrue(encoded.contains("\"arguments\":\"{\\\"query\\\":\\\"摇滚\\\"}\""), "arguments 为 JSON 字符串")
        // content=null 等于默认值被省略（kotlinx 默认策略，OpenAI 规范允许 content 缺省）
        assertFalse(encoded.contains("\"content\":null"), "content 为 null 时应整体省略而非输出 null")
        assertTrue(encoded.contains("\"tool_call_id\":\"call_7\""), "工具结果消息回传 tool_call_id")
    }

    @Test
    fun responseMessage_withToolCalls_decodes() {
        // 非流式响应中 message 带 tool_calls 也应可解码（既有 DTO 缺该字段会在严格端点上炸）
        val decoded = json.decodeFromString<OpenAiStyleResponse>(
            """
            {"choices":[{"message":{"role":"assistant","tool_calls":[
                {"id":"c1","type":"function","function":{"name":"searchLibrary","arguments":"{}"}}
            ]},"finish_reason":"tool_calls"}]}
            """.trimIndent()
        )

        val call = decoded.choices!!.single().message!!.toolCalls!!.single()
        assertEquals("c1", call.id)
        assertEquals("function", call.type)
        assertEquals("searchLibrary", call.function.name)
        assertEquals("{}", call.function.arguments)
    }

    @Test
    fun temperatureDefaultInDto_isLegacySafe() {
        // DTO 默认 0.7 保持既有调用兼容；显式档位由调用方传入（M2-T3）
        val encoded = json.encodeToString(
            OpenAiStyleRequest(model = "m", messages = listOf(OpenAiMessage("user", "hi")))
        )
        assertTrue(encoded.contains("\"temperature\":0.7"))
    }

    @Test
    fun nonStreamingRequest_omitsStreamField() {
        // callChatApi（非流式，富化管道）不传 stream——端点返回普通 JSON
        val encoded = json.encodeToString(
            OpenAiStyleRequest(model = "m", messages = listOf(OpenAiMessage("user", "hi")))
        )
        assertFalse(encoded.contains("\"stream\""))
    }

    @Test
    fun streamingRequest_carriesStreamTrue() {
        val encoded = json.encodeToString(
            OpenAiStyleRequest(model = "m", messages = listOf(OpenAiMessage("user", "hi")), stream = true)
        )
        assertTrue(encoded.contains("\"stream\":true"))
    }

    @Test
    fun temperature_roundTripsFloats() {
        val encoded = json.encodeToString(
            OpenAiStyleRequest(model = "m", messages = listOf(OpenAiMessage("user", "hi")), temperature = 0.25f)
        )
        val decoded = json.decodeFromString<OpenAiStyleRequest>(encoded)
        assertEquals(0.25f, decoded.temperature)
    }
}