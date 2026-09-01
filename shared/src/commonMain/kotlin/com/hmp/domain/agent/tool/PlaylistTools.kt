package com.hmp.domain.agent.tool

import kotlinx.coroutines.flow.first

// ---------------- playlist_list (read/silent) ----------------

class PlaylistListTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.PLAYLIST_LIST
    override val description = "列出全部播放列表（名称/曲数/更新时间）\n只读，极低成本\n电台 diff 仲裁决策前先读现状"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        val playlists = deps.playlistRepository.getAllPlaylists()
        if (playlists.isEmpty()) return ToolResult.success("当前无任何播放列表")
        val lines = playlists.joinToString("\n") { p ->
            "id=${p.id} 「${p.name}」 ${p.songCount}首 更新=${p.updatedAt}"
        }
        return ToolResult.success("共 ${playlists.size} 个歌单：\n$lines")
    }
}

// ---------------- playlist_detail (read/silent) ----------------

class PlaylistDetailTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.PLAYLIST_DETAIL
    override val description = "获取某个播放列表的完整曲目列表（每首带 title/artist/id）\n只读，极低成本"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        LongParam(name = "playlist_id", description = "播放列表ID", min = 1),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val playlistId = args.requireLong("playlist_id")
        val meta = deps.playlistRepository.getPlaylistMeta(playlistId)
            ?: return ToolResult.failure("播放列表 $playlistId 不存在")
        val songs = deps.playlistRepository.getPlaylistById(playlistId)
        val lines = songs.take(50).joinToString("\n") { s ->
            "  ${s.music.title} - ${s.music.artist} (id=${s.music.id})"
        }
        return ToolResult.success(
            "「${meta.name}」 共 ${songs.size} 首：\n$lines" +
                if (songs.size > 50) "\n... (仅显示前 50 首)" else ""
        )
    }
}

// ---------------- playlist_create (write/confirm) ----------------

class PlaylistCreateTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.PLAYLIST_CREATE
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

// ---------------- playlist_rename (write/confirm) ----------------

class PlaylistRenameTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.PLAYLIST_RENAME
    override val description = "重命名播放列表\n写操作，改变歌单容器名称"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        LongParam(name = "playlist_id", description = "目标播放列表ID", min = 1),
        StringParam(name = "new_name", description = "新名称"),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val playlistId = args.requireLong("playlist_id")
        val newName = args.requireString("new_name")
        val meta = deps.playlistRepository.getPlaylistMeta(playlistId)
            ?: return ToolResult.failure("播放列表 $playlistId 不存在")
        deps.playlistRepository.renamePlaylist(playlistId, newName)
        return ToolResult.success("播放列表「${meta.name}」已重命名为「$newName」")
    }
}

// ---------------- playlist_delete (write/strong_confirm) ----------------
// 系统歌单保护：红心/最近/当前播放歌单不可删（与 ManagePlaylistUseCase 逻辑一致）。

class PlaylistDeleteTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.PLAYLIST_DELETE
    override val description = "永久删除播放列表\n写操作，会删除歌单容器与全部曲目关联\n系统歌单（红心/最近/当前）不可删除\n属于高风险操作，需双重确认"
    override val permissionLevel = ToolPermissionLevel.STRONG_CONFIRM
    override val params = listOf(
        LongParam(name = "playlist_id", description = "要删除的播放列表ID", min = 1),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val playlistId = args.requireLong("playlist_id")
        val meta = deps.playlistRepository.getPlaylistMeta(playlistId)
            ?: return ToolResult.failure("播放列表 $playlistId 不存在")
        // 系统歌单保护
        val currentId = deps.settingsRepository.getCurrentPlaylistId()
        val likedId = deps.settingsRepository.getLikedPlaylistId()
        val recentId = deps.settingsRepository.getRecentPlaylistId()
        if (playlistId == currentId || playlistId == likedId || playlistId == recentId) {
            return ToolResult.failure("系统歌单不可删除（红心/最近/当前播放歌单受保护）")
        }
        deps.playlistRepository.removePlaylistById(playlistId)
        return ToolResult.success("已删除播放列表「${meta.name}」")
    }
}

// ---------------- playlist_add_song (write/confirm) ----------------

class PlaylistAddSongTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.PLAYLIST_ADD_SONG
    override val description = "把指定歌曲加入播放列表\n写操作，修改歌单内容\n仅在用户明确要求加曲时使用；musicPath 由系统自动解析"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        LongParam(name = "playlist_id", description = "目标播放列表ID", min = 1),
        LongParam(name = "music_id", description = "要加入的歌曲ID", min = 1),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val playlistId = args.requireLong("playlist_id")
        val musicId = args.requireLong("music_id")
        val meta = deps.playlistRepository.getPlaylistMeta(playlistId)
            ?: return ToolResult.failure("播放列表 $playlistId 不存在")
        val info = deps.musicRepository.getMusicInfoById(musicId).first()
            ?: return ToolResult.failure("歌曲 $musicId 不存在")
        deps.playlistRepository.addToPlaylist(playlistId, musicId, info.music.path)
        return ToolResult.success("已把「${info.music.title}」加入「${meta.name}」")
    }
}

// ---------------- playlist_remove_song (write/confirm) ----------------

class PlaylistRemoveSongTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.PLAYLIST_REMOVE_SONG
    override val description = "从播放列表中移除指定歌曲\n写操作，修改歌单内容"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        LongParam(name = "playlist_id", description = "目标播放列表ID", min = 1),
        LongParam(name = "music_id", description = "要移除的歌曲ID", min = 1),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val playlistId = args.requireLong("playlist_id")
        val musicId = args.requireLong("music_id")
        val meta = deps.playlistRepository.getPlaylistMeta(playlistId)
            ?: return ToolResult.failure("播放列表 $playlistId 不存在")
        deps.playlistRepository.removeItemFromPlaylist(musicId, playlistId)
        val info = deps.musicRepository.getMusicInfoById(musicId).first()
        val label = info?.music?.title ?: "歌曲 $musicId"
        return ToolResult.success("已从「${meta.name}」移除「$label」")
    }
}

// ---------------- playlist_reorder (write/confirm) ----------------

class PlaylistReorderTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.PLAYLIST_REORDER
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
