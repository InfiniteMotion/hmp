package com.hmp.domain.agent.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ToolSpecTest {
    private val json = Json { prettyPrint = false }

    @Test
    fun `string parameter with empty is allowed`() {
        val params = listOf(
            StringParam(name = "text", required = true, description = "输入文本", allowEmpty = true),
            StringParam(name = "nonempty", required = true, description = "不为空", allowEmpty = false),
        )
        val schema = parametersSchema(params)
        assertTrue(schema.toString().contains("\"type\":\"string\""), "应为 string 类型")

        val args = parseArgs(
            buildJsonObject {
                put("text", JsonPrimitive(""))
                put("nonempty", JsonPrimitive("ok"))
            }, params
        )
        assertEquals("", args.requireString("text"))
        assertEquals("ok", args.requireString("nonempty"))
    }

    @Test
    fun `empty string on nonempty required throws`() {
        val params = listOf(StringParam(name = "x", required = true, allowEmpty = false))
        assertFailsWith<ToolParamError> {
            parseArgs(buildJsonObject { put("x", JsonPrimitive("")) }, params)
        }
    }

    @Test
    fun `int parameter respects bounds clamping is off`() {
        val params = listOf(IntParam(name = "count", description = "数目", min = 1, max = 10, clamp = false))
        // good
        val good = parseArgs(buildJsonObject { put("count", JsonPrimitive(5)) }, params)
        assertEquals(5, good.requireInt("count"))
        // too low
        assertFailsWith<ToolParamError> {
            parseArgs(buildJsonObject { put("count", JsonPrimitive(0)) }, params)
        }
        // too high
        assertFailsWith<ToolParamError> {
            parseArgs(buildJsonObject { put("count", JsonPrimitive(11)) }, params)
        }
    }

    @Test
    fun `int parameter with clamp on truncates out of range into bounds`() {
        val params = listOf(IntParam(name = "limit", min = 1, max = 20, clamp = true))
        // 未越界原样
        val ok = parseArgs(buildJsonObject { put("limit", JsonPrimitive(5)) }, params)
        assertEquals(5, ok.requireInt("limit"))
        // 低于下限 → 截断到 min
        val low = parseArgs(buildJsonObject { put("limit", JsonPrimitive(-3)) }, params)
        assertEquals(1, low.requireInt("limit"))
        // 高于上限 → 截断到 max
        val high = parseArgs(buildJsonObject { put("limit", JsonPrimitive(999)) }, params)
        assertEquals(20, high.requireInt("limit"))
    }

    @Test
    fun `long parameter with clamp on truncates out of range into bounds`() {
        val params = listOf(LongParam(name = "ms", min = 0, max = 300_000L, clamp = true))
        val high = parseArgs(buildJsonObject { put("ms", JsonPrimitive(9_999_999L)) }, params)
        assertEquals(300_000L, high.requireLong("ms"))
    }

    @Test
    fun `enum parameter must be on allowed list`() {
        val params = listOf(EnumParam(name = "command", allowed = listOf("play", "pause"), description = "指令"))
        // good
        val good = parseArgs(buildJsonObject { put("command", JsonPrimitive("play")) }, params)
        assertEquals("play", good.requireString("command"))
        // bad
        assertFailsWith<ToolParamError> {
            parseArgs(buildJsonObject { put("command", JsonPrimitive("next")) }, params)
        }
    }

    @Test
    fun `required missing throws`() {
        val params = listOf(
            StringParam(name = "req", required = true),
            StringParam(name = "opt", required = false),
        )
        assertFailsWith<ToolParamError> {
            parseArgs(buildJsonObject { }, params)
        }
    }

    @Test
    fun `optional null ok`() {
        val params = listOf(
            StringParam(name = "opt", required = false),
            IntParam(name = "optInt", required = false),
        )
        val args = parseArgs(buildJsonObject { }, params)
        assertNull(args.optionalString("opt"))
        assertNull(args.optionalInt("optInt"))
    }

    @Test
    fun `stringList parameter returns correct elements`() {
        val params = listOf(StringListParam(name = "tags", maxItems = 5))
        val args = parseArgs(
            json.parseToJsonElement("""{"tags":["a","b","c"]}""") as JsonObject,
            params
        )
        assertEquals(listOf("a", "b", "c"), args.requireStringList("tags"))
    }

    @Test
    fun `schema generation matches parameter set name title`() {
        val params = listOf(
            IntParam(name = "limit", description = "返回条数", min = 1, max = 20),
            StringParam(name = "query", description = "搜索关键词"),
        )
        val schema = parametersSchema(params)
        val properties = schema["properties"] as? JsonObject
        checkNotNull(properties)

        val limitObj = properties["limit"] as JsonObject
        assertEquals("integer", (limitObj["type"] as JsonPrimitive).content)
        assertEquals(1, (limitObj["minimum"] as JsonPrimitive).int)
        assertEquals(20, (limitObj["maximum"] as JsonPrimitive).int)
        assertEquals("返回条数", (limitObj["description"] as JsonPrimitive).content)

        val queryObj = properties["query"] as JsonObject
        assertEquals("string", (queryObj["type"] as JsonPrimitive).content)
        assertEquals("搜索关键词", (queryObj["description"] as JsonPrimitive).content)

        val required = (schema["required"] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as JsonPrimitive).content }
            ?: emptyList()
        assertTrue(required.containsAll(listOf("limit", "query")))
    }

    @Test
    fun `tool llm spec correctly generates parameters json`() {
        val tool = object : AgentTool {
            override val name: String = "test_tool"
            override val description: String = "test desc"
            override val params: List<ToolParam> = listOf(
                StringParam(name = "arg1", description = "first"),
                IntParam(name = "arg2", required = false, min = 0),
            )
            override val permissionLevel: ToolPermissionLevel = ToolPermissionLevel.SILENT
            override suspend fun run(args: ToolArgs): ToolResult = ToolResult.success("ok")
        }

        val spec = tool.llmSpec
        assertEquals("test_tool", spec.name)
        assertEquals("test desc", spec.description)
        val paramsJson = requireNotNull(spec.parameters)
        val required = (paramsJson["required"] as kotlinx.serialization.json.JsonArray)
            .map { (it as JsonPrimitive).content }.toSet()
        assertEquals(setOf("arg1"), required)
        assertFalse(required.contains("arg2"), "非必填不在 required")
    }

    @Test
    fun `success result summary nonempty enforced by API`() {
        val ok = ToolResult.success("摘要", "detail")
        assertTrue(ok.success)
        assertEquals("摘要", ok.summary)
        assertEquals("detail", ok.detail)
        assertNull(ok.failureReason)
    }

    @Test
    fun `failure result has failureReason and success false`() {
        val err = ToolResult.failure("参数错误")
        assertFalse(err.success)
        assertEquals("执行失败：参数错误", err.summary)
        assertEquals("参数错误", err.failureReason)
    }
}