package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.runtime.Capability
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

    /**
     * F9-A0：MasterAgent 构造完后调用，注册依赖 Capability Map 的工具。
     * Capability Map 从 MasterAgent.capabilities 派生（_subAgents 中实现 Capability 的实例）。
     */
    fun bindCapabilityTools(capabilitiesProvider: () -> Map<String, Capability>) {
        register(
            CapabilityStatusTool(capabilitiesProvider),
        )
    }

    companion object {
        /**
         * 构造基础工具集（34 个实例 = 26 主域 + 8 DJ）。
         * 主域：Playback 3 + Playlist CRUD 5 + Playlist 曲目 3 + Library 4 + Song 标签 1 + Batch B 10。
         * DJ 组（dj_*）只出现在 ToolRegistryView.radio() 白名单——Master 视图按前缀过滤后看不到。
         *
         * MasterAgent SubAgent 生命周期（enrich 系列 / radio 系列）已重构为内建意图路由，
         * 不再作为 LLM 工具注册——由 MasterAgent.handleUserMessage() 直接识别并调原生方法。
         */
        fun create(deps: ToolDependencies): ToolRegistry {
            val baseTools = listOf(
                // ── Playback ──
                GetNowPlayingContextTool(deps),   // playback_state
                PlaybackControlTool(deps),        // playback_control
                PlaybackPlayAtTool(deps),         // playback_play_at

                // ── Playlist 实体 CRUD ──
                PlaylistListTool(deps),           // playlist_list
                PlaylistDetailTool(deps),         // playlist_detail
                PlaylistCreateTool(deps),         // playlist_create
                PlaylistRenameTool(deps),         // playlist_rename
                PlaylistDeleteTool(deps),         // playlist_delete

                // ── Playlist 曲目管理 ──
                PlaylistAddSongTool(deps),        // playlist_add_song
                PlaylistRemoveSongTool(deps),     // playlist_remove_song
                PlaylistReorderTool(deps),        // playlist_reorder

                // ── Library ──
                SearchLibraryTool(deps),          // library_search
                GetSimilarSongsTool(deps),        // library_similar
                GetListenStatsTool(deps),         // library_stats
                GetRecentHistoryTool(deps),       // library_recent_history

                // ── 用户认识模块（画像）：1 只读 + 2 写入（CONFIRM）──
                ProfileReadTool(deps),            // profile_read
                ProfileNoteTool(deps),            // profile_note
                ProfileForgetTool(deps),          // profile_forget

                // ── Song 标签 ──
                SongTagsGetTool(deps),            // song_tags_get
                // song_enrich_llm 已移除：EnrichSubAgent 富化管道内化到自循环
                // ── Batch B ──
                PlaybackEnqueueTool(deps),       // playback_enqueue
                LibraryTagsTool(deps),           // library_tags
                LibrarySongsByTagTool(deps),     // library_songs_by_tag
                LibrarySongsByArtistTool(deps),  // library_songs_by_artist
                LibrarySongsByAlbumTool(deps),   // library_songs_by_album
                LibraryArtistsTool(deps),        // library_artists
                LibraryAlbumsTool(deps),         // library_albums
                AgentBudgetTool(deps),           // agent_budget
                SongTagUserAddTool(deps),        // song_tag_user_add
                SongTagUserRemoveTool(deps),      // song_tag_user_remove
                // Dj 工具已在 F9-A0 删除——电台控制权完全收归 RadioSubAgent runLoop
            )
            return ToolRegistry(baseTools)
        }
    }
}

/**
 * F9-A0：查询所有后台能力（电台 / 富化 / 门面）的当前状态。
 *
 * 依赖 Capability Map（MasterAgent 构造完后动态绑定），
 * 让 LLM 在对话中能看到 BUILDING / PAUSED 等 SubAgent 内部状态
 * （之前 playback_state 只查播放器，看不到电台正在启动中）。
 */
class CapabilityStatusTool(
    private val capabilitiesProvider: () -> Map<String, Capability>,
) : AgentTool {
    override val name = ToolNames.CAPABILITY_STATUS
    override val description = "查询后台能力（电台 / 富化 / 门面）的当前状态。不传 name 返回全部能力的状态摘要"
    override val params = listOf(
        StringParam(name = "name", description = "能力名：radio / enrich / hello（可选，不传返回全部）", required = false),
    )
    override val permissionLevel = ToolPermissionLevel.SILENT

    override suspend fun run(args: ToolArgs): ToolResult {
        val capabilities = capabilitiesProvider()
        if (capabilities.isEmpty()) {
            return ToolResult.success("当前没有运行中的后台能力")
        }

        val target = args.optionalString("name")
        val targets = if (target != null) {
            listOfNotNull(capabilities[target])
        } else {
            capabilities.values.toList()
        }

        if (targets.isEmpty()) {
            return ToolResult.failure("未找到能力：$target，可用能力：${capabilities.keys.joinToString()}")
        }

        val summary = targets.joinToString("\n") { cap ->
            val state = cap.stateFlow.value
            val status = state.status.name.lowercase()
            val detail = if (state.detail.isNotEmpty()) " — ${state.detail}" else ""
            "${cap.capabilityName}: $status$detail"
        }
        return ToolResult.success(summary)
    }
}
