package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.LlmToolSpec
import kotlinx.serialization.json.JsonObject

/**
 * 工具不存在。 */
class ToolNotFoundException(name: String) : Exception("未知工具：$name")

/**
 * 工具注册表（M3-T2）：按名路由到具体工具，暴露全部工具的 [LlmToolSpec] 供 M4 下发 function-calling。
 *
 * M3-T3 回填语义：
 * - **强制回填**：工具返回的 [ToolResult.summary] 在成功时被强制非空——补充空结果也返回
 *   「未命中/无数据」的摘要，防止模型幻觉型假成功（不把「空」当「找到了」）；
 * - **失败入审计**：[ToolResult.failureReason] 在失败时携带，M4 策略层据此写 audit_log；
 *   Registry 自身不落审计（职责在 M4），此处仅保证契约成立（失败必有 failureReason）。
 */
class ToolRegistry(
    tools: List<AgentTool>,
) {
    private val byName: Map<String, AgentTool> = tools.associateBy { it.name }

    init {
        require(tools.map { it.name }.distinct().size == tools.size) { "工具名不能重复" }
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

    companion object {
        /** 构造完整工具集（含全部十项）。 */
        fun create(deps: ToolDependencies): ToolRegistry = ToolRegistry(
            listOf(
                SearchLibraryTool(deps),
                GetListenStatsTool(deps),
                GetRecentHistoryTool(deps),
                GetNowPlayingContextTool(deps),
                GetSimilarSongsTool(deps),
                GetMusicExtraTool(deps),
                EnrichSongTool(deps),
                CreatePlaylistTool(deps),
                AddToPlaylistTool(deps),
                ReorderPlaylistTool(deps),
                ControlPlaybackTool(deps),
            )
        )
    }
}