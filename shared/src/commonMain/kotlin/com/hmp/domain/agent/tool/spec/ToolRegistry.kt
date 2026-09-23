package com.hmp.domain.agent.tool.spec

import com.hmp.domain.agent.port.LlmToolSpec
import kotlinx.serialization.json.JsonObject

/**
 * 工具不存在。 */
class ToolNotFoundException(name: String) : Exception("未知工具：$name")

/**
 * 工具注册表（S 阶段——5 能力域 17 原子工具）。
 *
 * 按名路由到具体工具，暴露全部工具的 [LlmToolSpec] 供 M4 下发 function-calling。
 *
 * 回填语义：
 * - **强制回填**：工具返回的 [ToolResult.summary] 在成功时被强制非空——补充空结果也返回
 *   「未命中/无数据」的摘要，防止模型幻觉型假成功（不把「空」当「找到了」）；
 * - **失败入审计**：[ToolResult.failureReason] 在失败时携带，M4 策略层据此写 audit_log；
 *   Registry 自身不落审计（职责在 M4），此处仅保证契约成立（失败必有 failureReason）。
 *
 * 纯协议类型：只依赖 [AgentTool] / [ToolResult] / [ToolParamError]，不认识任何具体工具。
 * 具体工具集的装配在 `tool/ToolCatalog.kt`（impl 层），避免 spec → impl 反向依赖。
 */
class ToolRegistry(
    tools: List<AgentTool>,
) {
    private val byName: MutableMap<String, AgentTool> = tools.associateBy { it.name }.toMutableMap()

    init {
        require(tools.map { it.name }.distinct().size == tools.size) { "工具名不能重复" }
    }

    /**
     * 动态注册工具（供 MasterAgent 在拿到 ToolRegistry 后自行注册 enrich_* 等专属工具）。
     *
     * @throws IllegalArgumentException 如果注册的工具名与现有工具重复。
     */
    fun register(vararg tools: AgentTool) {
        tools.forEach { t ->
            require(!byName.containsKey(t.name)) { "工具名不能重复：${t.name} 已存在" }
            byName[t.name] = t
        }
    }

    fun all(): List<AgentTool> = byName.values.sortedBy { it.name }

    fun find(name: String): AgentTool? = byName[name]

    /** 全部工具的 function-calling 声明（M4 调用 [LlmTransport.streamChat] 的 tools 参数）。 */
    val allLlmSpecs: List<LlmToolSpec>
        get() = all().map { it.llmSpec }

    /** 按名执行并校验参数；未知工具抛 [ToolNotFoundException]，参数非法转 [ToolResult.failure](不中断，M4 留审计)。 */
    suspend fun executeTool(name: String, arguments: JsonObject): ToolResult {
        val tool = byName[name] ?: throw ToolNotFoundException(name)
        return try {
            tool.execute(arguments)
        } catch (e: ToolParamError) {
            // 参数越界/缺失 → 工具层失败并携带明确原因（供审计）
            ToolResult.failure(e.message ?: "参数校验失败")
        }
    }
}
