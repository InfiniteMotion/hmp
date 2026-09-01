package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.LlmToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * M3-T1 工具声明 DSL。
 *
 * [ToolParam] 是参数定义的**单一来源**：同一份描述符同时驱动
 * 1) OpenAI function-calling 的 JSON schema 生成（[AgentTool.parametersSchema]），
 * 2) 运行时参数校验（[parseArgs]）。
 *
 * 该「单源」设计保证 schema 与校验器不漂移（M3-T1 验收：schema↔校验防漂移单测）。
 */

/** 工具许可级别（与 M4 PolicyGuard 四级对齐；M3 仅在描述上标注成本预期）。 */
enum class ToolPermissionLevel { SILENT, NOTIFY, CONFIRM, STRONG_CONFIRM }

/** 参数类型描述符：同时生成 schema 片段与校验语义。 */
sealed interface ToolParam {
    val name: String
    val required: Boolean
    val description: String?
    /** 该参数的 JSON-schema 片段（不含外层 key；重复嵌套的参数对象不使用）。 */
    val schemaValue: JsonObject
}

class StringParam(
    override val name: String,
    override val required: Boolean = true,
    override val description: String? = null,
    val allowEmpty: Boolean = false,
) : ToolParam {
    override val schemaValue: JsonObject = buildJsonObject {
        put("type", "string")
        if (!description.isNullOrBlank()) put("description", description)
        put("title", name)
    }
}

class IntParam(
    override val name: String,
    override val required: Boolean = true,
    override val description: String? = null,
    val min: Int? = null,
    val max: Int? = null,
    /** 越界取值策略：超出时是否截断到边界（默认拒绝抛 [ToolParamError]）。 */
    val clamp: Boolean = false,
) : ToolParam {
    override val schemaValue: JsonObject = buildJsonObject {
        put("type", "integer")
        if (!description.isNullOrBlank()) put("description", description)
        put("title", name)
        if (min != null) put("minimum", min)
        if (max != null) put("maximum", max)
    }
}

class LongParam(
    override val name: String,
    override val required: Boolean = true,
    override val description: String? = null,
    val min: Long? = null,
    val max: Long? = null,
    val clamp: Boolean = false,
) : ToolParam {
    override val schemaValue: JsonObject = buildJsonObject {
        put("type", "integer")
        if (!description.isNullOrBlank()) put("description", description)
        put("title", name)
        if (min != null) put("minimum", min)
        if (max != null) put("maximum", max)
    }
}

class BoolParam(
    override val name: String,
    override val required: Boolean = true,
    override val description: String? = null,
) : ToolParam {
    override val schemaValue: JsonObject = buildJsonObject {
        put("type", "boolean")
        if (!description.isNullOrBlank()) put("description", description)
        put("title", name)
    }
}

class EnumParam(
    override val name: String,
    val allowed: List<String>,
    override val required: Boolean = true,
    override val description: String? = null,
) : ToolParam {
    init {
        require(allowed.isNotEmpty()) { "EnumParam '$name' 必须至少有一个允许值" }
    }
    override val schemaValue: JsonObject = buildJsonObject {
        put("type", "string")
        if (!description.isNullOrBlank()) put("description", description)
        put("title", name)
        put("enum", JsonArray(allowed.map(::JsonPrimitive)))
    }
}

class StringListParam(
    override val name: String,
    override val required: Boolean = true,
    override val description: String? = null,
    val maxItems: Int? = null,
) : ToolParam {
    override val schemaValue: JsonObject = buildJsonObject {
        put("type", "array")
        put("items", buildJsonObject { put("type", "string") })
        if (!description.isNullOrBlank()) put("description", description)
        put("title", name)
        if (maxItems != null) put("maxItems", maxItems)
    }
}

class LongListParam(
    override val name: String,
    override val required: Boolean = true,
    override val description: String? = null,
    val maxItems: Int? = null,
) : ToolParam {
    override val schemaValue: JsonObject = buildJsonObject {
        put("type", "array")
        put("items", buildJsonObject { put("type", "integer") })
        if (!description.isNullOrBlank()) put("description", description)
        put("title", name)
        if (maxItems != null) put("maxItems", maxItems)
    }
}

/** 参数校验/解析失败（缺必填、类型不符、枚举越界、空串）。 */
class ToolParamError(message: String) : Exception(message)

/** 已校验通过的参数访问器：提供类型化 getter（二次断言，防御非法 Json 形态）。 */
class ToolArgs internal constructor(
    private val values: Map<String, JsonElement>,
) {
    fun requireString(name: String): String =
        requirePrimitive(name).content // 空串是否允许已由 parseArgs 按描述符 allowEmpty 预校验

    fun optionalString(name: String): String? =
        optionalPrimitive(name)?.content?.takeIf { it.isNotEmpty() }

    fun requireInt(name: String): Int =
        requirePrimitive(name).content.toIntOrNull()
            ?: throw ToolParamError("参数 '$name' 应为核心整数，实际: '${requirePrimitive(name).content}'")

    fun optionalInt(name: String): Int? =
        optionalPrimitive(name)?.content?.toIntOrNull()

    fun requireLong(name: String): Long =
        requirePrimitive(name).content.toLongOrNull()
            ?: throw ToolParamError("参数 '$name' 应为整数，实际: '${requirePrimitive(name).content}'")

    fun optionalLong(name: String): Long? =
        optionalPrimitive(name)?.content?.toLongOrNull()

    fun optionalFloat(name: String): Float? =
        optionalPrimitive(name)?.content?.toFloatOrNull()

    fun requireBool(name: String): Boolean =
        requirePrimitive(name).content.toBooleanStrictOrNull()
            ?: throw ToolParamError("参数 '$name' 应为布尔，实际: '${requirePrimitive(name).content}'")

    fun requireStringList(name: String): List<String> {
        val elem = values[name] ?: throw ToolParamError("缺少必需参数 '$name'")
        return (elem as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as? JsonPrimitive)?.content ?: throw ToolParamError("参数 '$name' 的元素应为字符串") }
            ?: throw ToolParamError("参数 '$name' 应为字符串数组")
    }

    fun requireLongList(name: String): List<Long> {
        val elem = values[name] ?: throw ToolParamError("缺少必需参数 '$name'")
        return (elem as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as? JsonPrimitive)?.content?.toLongOrNull() ?: throw ToolParamError("参数 '$name' 的元素应为整数") }
            ?: throw ToolParamError("参数 '$name' 应为整数数组")
    }

    private fun requirePrimitive(name: String): JsonPrimitive =
        optionalPrimitive(name) ?: throw ToolParamError("缺少必需参数 '$name'")

    private fun optionalPrimitive(name: String): JsonPrimitive? =
        values[name] as? JsonPrimitive
}

/**
 * 根据参数描述符校验并解析 JSON 参数。
 * 依次校验：未知参数（静默忽略，避免破坏性施展）、必填、类型（经访问器）、枚举白名单、空串、边界。
 * 返回 [ToolArgs] 访问器供工具读取类型化值。
 */
fun parseArgs(arguments: JsonObject, params: List<ToolParam>): ToolArgs {
    // 必填检查
    val requiredSet = params.filter { it.required }.map { it.name }.toSet()
    for (r in requiredSet) {
        if (!arguments.containsKey(r) || arguments[r] is JsonNull) {
            throw ToolParamError("缺少必需参数 '$r'")
        }
    }

    // 枚举白名单 & 边界预校验（schema 约束的非类型检查部分）
    params.forEach { p ->
        val raw = arguments[p.name] ?: return@forEach
        if (raw is JsonNull) return@forEach

        when (p) {
            is EnumParam -> {
                val v = (raw as? JsonPrimitive)?.content
                    ?: throw ToolParamError("参数 '${p.name}' 应为字符串枚举值")
                if (v !in p.allowed) {
                    throw ToolParamError("参数 '${p.name}' 的取值 '$v' 不在允许范围内 ${p.allowed}")
                }
            }
            is StringParam -> {
                val v = (raw as? JsonPrimitive)?.content
                    ?: throw ToolParamError("参数 '${p.name}' 应为字符串")
                if (!p.allowEmpty && v.isEmpty()) {
                    throw ToolParamError("参数 '${p.name}' 不能为空字符串")
                }
            }
            is IntParam -> {
                val v = (raw as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: throw ToolParamError("参数 '${p.name}' 应为整数")
                if (!p.clamp && ((p.min != null && v < p.min) || (p.max != null && v > p.max))) {
                    throw ToolParamError("参数 '${p.name}' 取值 $v 超出范围 [${p.min ?: "-∞"}, ${p.max ?: "+∞"}]")
                }
            }
            is LongParam -> {
                val v = (raw as? JsonPrimitive)?.content?.toLongOrNull()
                    ?: throw ToolParamError("参数 '${p.name}' 应为整数")
                if (!p.clamp && ((p.min != null && v < p.min) || (p.max != null && v > p.max))) {
                    throw ToolParamError("参数 '${p.name}' 取值 $v 超出范围 [${p.min ?: "-∞"}, ${p.max ?: "+∞"}]")
                }
            }
            else -> Unit // Bool / List 类型经访问器二次校验
        }
    }

    // 截断（clamp）：越界且 clamp=true → 修正值；修正后的 map 交给 ToolArgs
    val corrected = arguments.toMutableMap()
    params.forEach { p ->
        when (p) {
            is IntParam -> clampInto(
                p, arguments, corrected,
                from = { it.toIntOrNull()?.toLong() }, toJson = { JsonPrimitive(it.toInt()) },
            )
            is LongParam -> clampInto(
                p, arguments, corrected,
                from = { it.toLongOrNull() }, toJson = { JsonPrimitive(it) },
            )
            else -> Unit
        }
    }

    return ToolArgs(corrected)
}

/** clamp 核心：越界时把修正值写进 corrected map。 */
private inline fun clampInto(
    p: ToolParam,
    raw: Map<String, JsonElement>,
    corrected: MutableMap<String, JsonElement>,
    from: (String) -> Long?,
    toJson: (Long) -> JsonElement,
) {
    if (!when (p) { is IntParam -> p.clamp; is LongParam -> p.clamp; else -> false }) return
    val low = when (p) { is IntParam -> p.min?.toLong(); is LongParam -> p.min; else -> null }
    val high = when (p) { is IntParam -> p.max?.toLong(); is LongParam -> p.max; else -> null }
    val cur = (raw[p.name] as? JsonPrimitive)?.content?.let(from) ?: return
    val clamped = when {
        low != null && cur < low -> low
        high != null && cur > high -> high
        else -> cur
    }
    if (clamped != cur) corrected[p.name] = toJson(clamped)
}

/** 生成 OpenAI function-calling 的 object schema（供 [LlmToolSpec.parameters]）。 */
fun parametersSchema(params: List<ToolParam>): JsonObject {
    val required = params.filter { it.required }.map { it.name }
    return buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            params.forEach { put(it.name, it.schemaValue) }
        })
        if (required.isNotEmpty()) put("required", JsonArray(required.map(::JsonPrimitive)))
    }
}

/**
 * Agent 工具接口。
 *
 * [description] 采用三段式（任务书 M3-T2）：行为约束 ≤50 字 / 成本提示 / 替代指引，用换行分隔。
 * [execute] 返回 [ToolResult]——M3-T3 回填语义要求成功时 [ToolResult.summary] 必须非空（强制回填上下文），
 * 失败时置 [ToolResult.success]=false 并写 [ToolResult.failureReason]（供 M4 入审计）。
 */
interface AgentTool {
    val name: String
    val description: String
    val params: List<ToolParam>
    val permissionLevel: ToolPermissionLevel

    val parametersSchema: JsonObject
        get() = parametersSchema(params)

    val llmSpec: LlmToolSpec
        get() = LlmToolSpec(name = name, description = description, parameters = parametersSchema)

    /** 执行前统一入口：先 [parseArgs] 校验，再交由具体逻辑。 */
    suspend fun execute(arguments: JsonObject): ToolResult {
        val args = parseArgs(arguments, params)
        return run(args)
    }

    suspend fun run(args: ToolArgs): ToolResult
}

/** 工具执行结果（M3-T3 回填语义载体）。 */
data class ToolResult(
    val success: Boolean,
    /** 成功时的人类可读摘要，强制非空→回填上下文；失败时为错误简述。 */
    val summary: String,
    /** 成功时的详细补充（可选，如热结果 JSON），失败时为 null。 */
    val detail: String? = null,
    /** 失败原因（M4 策略层写审计；仅 [success]=false 时有意义）。 */
    val failureReason: String? = null,
) {
    companion object {
        fun success(summary: String, detail: String? = null) = ToolResult(
            success = true, summary = summary, detail = detail,
        )

        fun failure(reason: String) = ToolResult(
            success = false, summary = "执行失败：$reason", failureReason = reason,
        )
    }
}