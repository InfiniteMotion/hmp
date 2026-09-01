package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.flow.first

// ---------------- playback_state (read/silent) ----------------

class GetNowPlayingContextTool(
    deps: ToolDependencies,
) : AgentTool {
    private val provider = deps.nowPlayingContextProvider

    override val name = ToolNames.PLAYBACK_STATE
    override val description = "获取当前播放会话快照：曲目/状态/位置\n只读，极低成本\n作为相似推荐/播放上下文的锚点"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        val ctx = provider.getNowPlaying()
        val info = ctx.currentMusicInfo
        return if (info != null) {
            ToolResult.success(
                "当前播放「${info.music.title} - ${info.music.artist}」" +
                    (if (ctx.isPlaying) "（播放中）" else "（暂停中）") +
                    " ${formatPositionMs(ctx.currentPositionMs)}/${formatPositionMs(ctx.durationMs)}",
            )
        } else {
            ToolResult.success("当前无播放曲目")
        }
    }
}

// ---------------- library_similar (read/silent) ----------------

class GetSimilarSongsTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository
    private val provider = deps.nowPlayingContextProvider

    override val name = ToolNames.LIBRARY_SIMILAR
    override val description = "基于当前/指定歌曲按风格权重推荐相似歌曲\n只读计算，低成本\n说明推荐理由时可引用其风格标签"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        LongParam(name = "music_id", description = "锚点歌曲ID；缺省自动取当前播放", required = false),
        IntParam(name = "limit", description = "返回条数", required = false, min = 1, max = 20, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val anchorId = args.optionalLong("music_id")
            ?: provider.getNowPlaying().currentMusicId
            ?: return ToolResult.failure("缺少锚点歌曲：请指定 music_id 或先有正在播放的歌曲")
        val limit = args.optionalInt("limit") ?: DEFAULT_RESULT_LIMIT
        val similar = repo.getSimilarSongsByWeightedLabels(anchorId, limit)
        if (similar.isEmpty()) return ToolResult.success("未找到与歌曲 $anchorId 足够相似的歌曲")
        return ToolResult.success("与「${anchorId}」相似的歌曲：\n${similar.summaryLines(limit)}")
    }
}

// ---------------- playback_control (write/confirm) ----------------
// 瞬时状态切换：pause/next/previous/seek_to/shuffle_on/off/repeat_*
// 不处理 play_by_id（改变当前播放对象）—— 由 playback_play_at 原子负责。

class PlaybackControlTool(
    deps: ToolDependencies,
) : AgentTool {
    private val port = deps.playbackCommandPort

    override val name = ToolNames.PLAYBACK_CONTROL
    override val description = "瞬时播放控制：pause/next/previous/seek/shuffle/repeat\n改变播放状态，属写操作\n只用于「暂停/下一首/上一首/跳转位置/切模式」\n需要播放某首指定曲目时用 playback_play_at"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        EnumParam(
            name = "command",
            allowed = listOf(
                "play", "pause", "next", "previous",
                "seek_to", "shuffle_on", "shuffle_off",
                "repeat_one", "repeat_all", "repeat_off",
            ),
            description = "播放控制指令（瞬时状态切换）",
        ),
        LongParam(name = "position_ms", description = "seek_to 的目标位置(毫秒)", required = false, min = 0),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val command = args.requireString("command")
        val built = buildInstantCommand(command, args)
            ?: return ToolResult.failure("指令「$command」缺少必要参数（seek_to 需 position_ms）")
        val (ok, text) = port.execute(built)
        return if (ok) ToolResult.success("已执行：${built.displayName}")
        else ToolResult.failure("播放控制失败：$text")
    }

    private fun buildInstantCommand(raw: String, args: ToolArgs): PlaybackCommand? = when (raw) {
        "play" -> PlaybackCommand.PLAY
        "pause" -> PlaybackCommand.PAUSE
        "next" -> PlaybackCommand.NEXT
        "previous" -> PlaybackCommand.PREVIOUS
        "seek_to" -> args.optionalLong("position_ms")?.let { PlaybackCommand.SEEK_TO(it) }
        "shuffle_on" -> PlaybackCommand.SHUFFLE_ON
        "shuffle_off" -> PlaybackCommand.SHUFFLE_OFF
        "repeat_one" -> PlaybackCommand.REPEAT_ONE_ON
        "repeat_all" -> PlaybackCommand.REPEAT_ALL_ON
        "repeat_off" -> PlaybackCommand.REPEAT_OFF
        else -> null
    }
}

// ---------------- playback_play_at (write/confirm) ----------------
// 指定曲目开始播放（改变当前播放对象，区别于瞬时状态切换）。

class PlaybackPlayAtTool(
    deps: ToolDependencies,
) : AgentTool {
    private val port = deps.playbackCommandPort

    override val name = ToolNames.PLAYBACK_PLAY_AT
    override val description = "播放指定曲目（替换当前播放对象）\n改变播放状态，属写操作\nmusic_id 填 library_search / library_similar 结果里的 id"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        LongParam(name = "music_id", description = "要播放的歌曲ID（取 library_search / library_similar 结果的 id）", min = 1),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val musicId = args.requireLong("music_id")
        val (ok, text) = port.execute(PlaybackCommand.PLAY_BY_ID(musicId))
        return if (ok) ToolResult.success("已切换播放曲目 $musicId")
        else ToolResult.failure("播放控制失败：$text")
    }
}
