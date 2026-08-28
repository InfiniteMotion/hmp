package com.hmp.domain.agent.tool

import kotlinx.coroutines.flow.first

// ---------------- createPlaylist (write/confirm) ----------------

class CreatePlaylistTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.CREATE_PLAYLIST
    override val description = "新建一个空白播放列表\n写操作，会改变用户歌单结构\n仅在用户明确要求建列表时使用"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        StringParam(name = "name", description = "播放列表名称"),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val name = args.requireString("name")
        val id = deps.playlistRepository.createPlaylist(name)
        return ToolResult.success("已创建播放列表「$name」(id=$id)")
    }
}

// ---------------- addToPlaylist (write/confirm) ----------------

class AddToPlaylistTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.ADD_TO_PLAYLIST
    override val description = "把指定歌曲加入播放列表\n写操作，修改歌单内容\n仅在用户明确要求加曲时使用；musicPath 由系统自动解析"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        LongParam(name = "playlist_id", description = "目标播放列表ID", min = 1),
        LongParam(name = "music_id", description = "要加入的歌曲ID", min = 1),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val playlistId = args.requireLong("playlist_id")
        val musicId = args.requireLong("music_id")

        if (deps.playlistRepository.getPlaylistMeta(playlistId) == null) {
            return ToolResult.failure("播放列表 $playlistId 不存在")
        }
        val info = deps.musicRepository.getMusicInfoById(musicId).first()
            ?: return ToolResult.failure("歌曲 $musicId 不存在")
        val path = info.music.path

        deps.playlistRepository.addToPlaylist(playlistId, musicId, path)
        return ToolResult.success("已把「${info.music.title}」加入播放列表 $playlistId")
    }
}

// ---------------- reorderPlaylist (write/confirm) ----------------

class ReorderPlaylistTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.REORDER_PLAYLIST
    override val description = "重排播放列表内歌曲顺序(需提供完整目标顺序ID列表)\n写操作，会调整歌单次序\n仅在强调排序调整时使用"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        LongParam(name = "playlist_id", description = "目标播放列表ID", min = 1),
        LongListParam(name = "ordered_music_ids", description = "重排后的歌曲ID完整顺序", maxItems = 200),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val playlistId = args.requireLong("playlist_id")
        val ordered = args.requireLongList("ordered_music_ids")
        deps.playlistRepository.reorderPlaylistItems(playlistId, ordered)
        return ToolResult.success("播放列表 $playlistId 已按 ${ordered.size} 首目标顺序重排")
    }
}