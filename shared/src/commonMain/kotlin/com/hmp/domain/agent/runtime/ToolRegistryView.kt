package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.tool.AgentTool
import com.hmp.domain.agent.tool.ToolRegistry

/**
 * T1 基础设施：给 SubAgent 的 ToolRegistry 权限过滤视图。
 *
 * 设计铁则：每个 SubAgent 只能看到自己白名单里的工具，防止 Enrich 误调 playback_* 等越权操作。
 * - Enrich：library_* + song_*（不能调 playback_* / playlist_* / agent_*）
 * - Radio：playback_* + playlist_* + library_*（M6 定义，留配置位）
 * - Master：全量 27 原子工具 + SubAgent 管理工具
 *
 * 底层共用同一个 ToolRegistry 实例（IO 操作无需独立副本），仅暴露子集。
 */
class ToolRegistryView(
    private val delegate: ToolRegistry,
    /** 白名单：允许暴露的工具名前缀集合（如 setOf("library_", "song_tag_")） */
    private val allowedPrefixes: Set<String>,
) {
    /** 按前缀匹配判断工具是否可见 */
    fun isToolVisible(toolName: String): Boolean {
        return allowedPrefixes.any { prefix -> toolName.startsWith(prefix) }
    }

    /** 获取所有可见工具 */
    fun allTools(): List<AgentTool> {
        return delegate.all().filter { isToolVisible(it.name) }
    }

    /** 按名称查找（仅当在白名单内返回） */
    fun find(name: String): AgentTool? {
        if (!isToolVisible(name)) return null
        return delegate.find(name)
    }

    /** 可见工具的 LLM function-calling 声明（传给 SubAgent 的 LLM） */
    val llmSpecs: List<LlmToolSpec>
        get() = allTools().map { it.llmSpec }

    /** 注册预设配置（常见 Agent 场景的白名单快捷工厂） */
    companion object {
        /** Enrich SubAgent 视图：只能 library_* + song_* */
        fun enrich(delegate: ToolRegistry): ToolRegistryView = ToolRegistryView(
            delegate = delegate,
            allowedPrefixes = setOf(
                "library_",
                "song_tag_",
                "song_enrich_",
            )
        )

        /** Radio SubAgent 视图：playback_* + playlist_* + library_*（M6 填实现） */
        fun radio(delegate: ToolRegistry): ToolRegistryView = ToolRegistryView(
            delegate = delegate,
            allowedPrefixes = setOf(
                "playback_",
                "playlist_",
                "library_",
            )
        )

        /** Master Agent 视图：全量工具（所有前缀） */
        fun master(delegate: ToolRegistry): ToolRegistryView = ToolRegistryView(
            delegate = delegate,
            allowedPrefixes = setOf(
                "playback_",
                "playlist_",
                "library_",
                "song_tag_",
                "song_enrich_",
                "agent_",
                // SubAgent 管理工具（Master 的 LLM 通过这些管理子 Agent）
                "enrich_",
                "radio_",
            )
        )

        /** 自定义前缀（扩展用） */
        fun custom(delegate: ToolRegistry, prefixes: Set<String>): ToolRegistryView = ToolRegistryView(
            delegate = delegate,
            allowedPrefixes = prefixes,
        )

        /**
         * HelloSubAgent 等不需要任何工具的 Agent 使用——全空白名单，delegate 可传 null 占位。
         * 实际实现用一个不可见的空 ToolRegistry，避免 NPE。
         */
        fun empty(delegate: ToolRegistry?): ToolRegistryView = ToolRegistryView(
            delegate = delegate ?: emptyToolRegistry(),
            allowedPrefixes = emptySet(),
        )
    }
}

/** 内部用：ToolRegistry 空实现（HelloSubAgent 等不需要工具的场景）。 */
private fun emptyToolRegistry(): ToolRegistry = ToolRegistry(emptyList())
