package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.ToolPermissionLevel
import com.hmp.domain.agent.port.Capability
import com.hmp.domain.agent.tool.spec.AgentTool
import com.hmp.domain.agent.tool.spec.StringParam
import com.hmp.domain.agent.tool.spec.ToolArgs
import com.hmp.domain.agent.tool.spec.ToolNames
import com.hmp.domain.agent.tool.spec.ToolRegistry
import com.hmp.domain.agent.tool.spec.ToolResult

/**
 * 具体工具集的装配根（composition root）。
 *
 * 这里是 spec（协议）与 impl（8 个 `*Tools.kt`）的唯一交汇点：
 * spec 层不认识任何具体工具，装配职责集中在本文件，保证依赖方向恒为 impl → spec。
 */

/**
 * 构造基础工具集（34 个实例 = 26 主域 + 8 DJ）。
 * 主域：Playback 3 + Playlist CRUD 5 + Playlist 曲目 3 + Library 4 + Song 标签 1 + Batch B 10。
 * DJ 组（dj_*）只出现在 ToolRegistryView.radio() 白名单——Master 视图按前缀过滤后看不到。
 *
 * MasterAgent SubAgent 生命周期（enrich 系列 / radio 系列）已重构为内建意图路由，
 * 不再作为 LLM 工具注册——由 MasterAgent.handleUserMessage() 直接识别并调原生方法。
 */
fun createBaseToolRegistry(deps: ToolDependencies): ToolRegistry {
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

/**
 * F9-A0：把 [CapabilityStatusTool] 绑到既有 registry（MasterAgent 构造完后调用）。
 *
 * **F13 修正**：本函数原先定义在 `tool/spec/ToolRegistry.kt` 里作为成员方法，
 * 于是协议文件 `new` 了实现类 → 造出 `tool/spec → tool` **反向边**，
 * 与该目录"spec 不认识任何具体工具"的设计意图直接冲突（也是全仓唯一的反向边）。
 * 移到这里后依赖方向恢复为恒定的 `tool → tool/spec`。
 */
fun ToolRegistry.bindCapabilityTools(capabilitiesProvider: () -> Map<String, Capability>) {
    register(CapabilityStatusTool(capabilitiesProvider))
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
